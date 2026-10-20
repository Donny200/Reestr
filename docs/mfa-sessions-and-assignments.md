# Второй фактор, сессии и ответственные сотрудники

## Что найдено в проекте и какие решения приняты

- Вход: `POST /api/auth/login` проверяет пароль и сразу ставит cookie `accessToken` (JWT HS256, по умолчанию 24 часа)
  и `refreshToken` (случайный токен, в базе только SHA-256, ротация при `POST /api/auth/refresh`). Установленного
  механизма MFA не было. Выбран TOTP (RFC 6238: SHA-1, 6 цифр, 30 секунд, окно ±1 шаг) без новых зависимостей,
  совместимый с обычными приложениями-аутентификаторами.
- Аудита в identity-service не было (только логирование). Добавлена закрытая таблица `security_audit_events`;
  секреты, коды и токены в неё никогда не попадают.
- Downstream-сервисы (каталог, справочник) проверяют JWT без обращения к identity-service. Поэтому отзыв сессии
  действует в identity-service немедленно (claim `sid` проверяется по базе), а в остальных сервисах — после истечения
  access-токена; это тот же предел, что и у существующей инвалидации по `credentials_updated_at`.
  Для production рекомендуется уменьшить `jwt.expiration`.

## Поток входа

1. Пароль верен → `MfaService.decide(user)`:
   - у пользователя подтверждён TOTP → ответ `202 {mfaRequired: true, enrollmentRequired: false}`;
   - роль из `app.mfa.required-roles` (по умолчанию `ROLE_SUPER_ADMIN`, `ROLE_ORG_ADMIN`), TOTP не настроен и
     срок `app.mfa.enrollment-deadline` пуст или прошёл → `202 {mfaRequired: true, enrollmentRequired: true}`;
   - иначе — полная сессия, как раньше.
2. В случае 202 браузер получает только cookie `mfaChallenge` (httpOnly, путь `/api/auth/mfa`, 5 минут,
   в базе SHA-256 в `mfa_challenges`, одноразовый, не JWT). Access- и refresh-cookie очищаются; challenge не принимается
   ни одним другим endpoint и не распознаётся фильтрами JWT.
3. `POST /api/auth/mfa/verify {code}` — код приложения или код восстановления. Только после успеха вызывается
   `SessionCookies.start` и выдаются access/refresh. Для `enrollmentRequired` сначала `POST /api/auth/mfa/enroll` и
   `POST /api/auth/mfa/confirm {code}` с тем же challenge; подтверждение одновременно стартует сессию и возвращает коды восстановления один раз.
4. Пользователь с `mustChangePassword` после второго фактора получает прежний ограниченный токен без refresh.

## Хранение и защита

| Таблица | Содержимое |
|---|---|
| `user_mfa` | `secret_ciphertext` — секрет, зашифрованный AES-256-GCM ключом `MFA_ENCRYPTION_KEY` (префикс `v1:` для ротации), `confirmed_at`, `last_used_step` |
| `mfa_recovery_codes` | только SHA-256 кодов, `used_at` |
| `mfa_challenges` | SHA-256 challenge, назначение (`VERIFY`/`ENROLL`), попытки, срок, `used_at` |
| `security_audit_events` | `MFA_ENROLLED`, `MFA_DISABLED`, `MFA_RECOVERY_CODE_USED`, `MFA_RECOVERY_CODES_REGENERATED`, `MFA_ADMIN_RESET`, `MFA_VERIFICATION_FAILED`, `SESSION_REVOKED`, `SESSIONS_REVOKED`, `SESSIONS_REVOKED_BY_ADMIN` — без секретов и кодов |

- Секрет возвращается ровно один раз при `enroll` (вместе с `otpauth://` ссылкой) и больше нигде: ни в ответах,
  ни в логах, ни в аудите. QR-код не генерируется (новая зависимость не ставилась); показывается ключ и ссылка для приложения.
- Каждый TOTP-код принимается не более одного раза (`last_used_step`); код восстановления — один раз, после чего все
  существующие сессии пользователя отзываются (`MFA_RECOVERY`).
- Лимиты `app.mfa.*`: 10 попыток за 15 минут на адрес и столько же на учётную запись, 5 ошибок на один challenge,
  глобально 1 000 в час; превышение — 429 с `Retry-After`.
- `POST /api/auth/mfa/disable` и `POST /api/auth/mfa/recovery-codes` требуют пароль и действующий код.
- SUPER_ADMIN: `GET /api/user/{id}/mfa` (только состояние), `POST /api/user/{id}/mfa/reset` (удаляет второй фактор,
  отзывает сессии, аудит с id администратора), `GET /api/user/{id}/security-events`. Чужой секрет или коды недоступны никому.
- Список `/api/user` и `/api/auth/me` содержат `mfa {required, enrolled, enrollmentDeadline, enrollmentOverdue}`;
  страница «Пользователи» показывает, кто ещё не настроил второй фактор, а админ-панель — баннер с напоминанием.

## Сессии

- Сессия — цепочка строк `refresh_tokens` с общим `session_id`; ротация сохраняет id. Добавлены `user_agent`
  (обрезанный), `last_used_at`, `revoked_at`, `revoked_reason`. Старые строки получают `session_id` миграцией `V5`.
- `GET /api/auth/sessions` — свои активные сессии (`sessionId`, даты, user agent, `current`), без токенов.
  `DELETE /api/auth/sessions/{sessionId}`, `POST /api/auth/sessions/revoke-others`.
- SUPER_ADMIN: `GET /api/user/{id}/sessions`, `DELETE /api/user/{id}/sessions[/{sessionId}]` с аудитом.
  Администраторы организаций сессий других пользователей не видят (проверка в `SessionService`, не только в `@PreAuthorize`).
- Все refresh-токены отзываются при: сбросе пароля (было), смене пароля, использовании кода восстановления,
  сбросе MFA администратором, деактивации учётной записи (`UserService`, `AdminUserService`, `ModeratorService`), явном отзыве.

## Защита входа и собственного аккаунта

- `POST /api/auth/login` ограничен на клиента (`app.login.per-client-limit`, 20 за 15 минут по `X-Real-IP`) и на
  учётную запись (`app.login.per-account-limit`, 10 неверных паролей за 15 минут по введённому e-mail). Счётчик ведётся
  для любого введённого адреса, существует аккаунт или нет, поэтому 429 не раскрывает наличие аккаунта; успешный вход
  сбрасывает счётчик. Счётчики живут в памяти экземпляра (`LoginRateLimiter`, `LoginAttemptGuard`).
- `MfaService.adminReset` и `PasswordResetService.initiateByAdmin` отвергают собственный id вызывающего: даже
  перехваченная сессия `SUPER_ADMIN` не может снять свой второй фактор без пароля и кода или выпустить себе ссылку
  сброса пароля.
- Шлюз перезаписывает `X-Real-IP` для всех, кроме доверенных прокси (`GATEWAY_TRUSTED_PROXIES`), а identity-service
  не публикуется на хосте, поэтому лимиты нельзя обойти подменой заголовка.

## Настройки развёртывания

| Переменная | Значение |
|---|---|
| `MFA_ENABLED` | **`false` по умолчанию** (отключено конфигурацией для этого окружения): второй фактор не запрашивается и не предлагается, ключ не нужен, таблицы `user_mfa`, `mfa_recovery_codes`, `mfa_challenges`, `security_audit_events` и миграция `V5` остаются на месте, данные enrollment не удаляются. `true` включает MFA снова |
| `MFA_ENCRYPTION_KEY` | обязательна только при `MFA_ENABLED=true`: base64 32 байт (`openssl rand -base64 32`). При отсутствии identity-service не стартует с понятной ошибкой. При повторном включении укажите **тот же** ключ, которым были зашифрованы существующие enrollment, иначе их нельзя расшифровать и пользователям придётся пройти регистрацию заново (сначала сбросьте их через `POST /api/user/{id}/mfa/reset`) |
| `MFA_ENROLLMENT_DEADLINE` | ISO-8601 момент. До него существующие администраторы входят по паролю и видят напоминание; после — enrollment при входе. Пусто = требовать сразу (блокировки не происходит: пользователь настраивает второй фактор прямо при входе) |
| `MFA_ISSUER` | имя в приложении-аутентификаторе, по умолчанию `ReestrGov` |

`docker-compose.yml` и `.env.example` обновлены. Тесты identity используют фиксированный ключ из `application-test.yaml`.

## Ответственный сотрудник карточки

- `org_functions`: `assigned_user_id`, `assigned_user_email`, `assigned_user_name`, `assigned_at` (снимок, чтобы не
  запрашивать identity-service на каждую строку); `quality_reminders.assigned_user_id`.
- Новое permission `FUNCTIONS_ASSIGN` (категория Function Management). SUPER_ADMIN получает его существующим
  механизмом; `ORG_ADMIN` — только при первичной инициализации роли. **В существующих установках назначьте его роли
  ORG_ADMIN (или нужной пользовательской роли) на странице «Роли»** — установленные гранты автоматически не меняются.
- API (`/api/functions`): `PUT /{id}/assignee {userId}`, `DELETE /{id}/assignee`, `GET /{id}/assignee/candidates`
  (`FUNCTIONS_ASSIGN`), `GET /assignments?organizationId&state` (`FUNCTIONS_ASSIGN`, `FUNCTIONS_VIEW` или
  `ORG_ANALYTICS_VIEW`). Область организаций берётся из JWT; чужая организация — 403.
- Кандидаты — только активные пользователи организации карточки по данным identity-service
  (`GET /api/organizations/{id}/members`, доступен SUPER_ADMIN, `FUNCTIONS_MANAGE_ANY_ORGANIZATION` и сотрудникам этой
  организации; запрос идёт с cookie вызывающего, так что identity-service применяет собственную проверку). Назначение на
  черновую редакцию — 409: ответственность живёт на опубликованной строке и не копируется в редакции.
- Аудит: `ASSIGNEE_CHANGE` в `audit_logs` («nobody -> #7 (email)»).
- Напоминания: ежедневный скан переносит `assigned_user_id` карточки в напоминание; `GET /api/analytics/reminders?assignee=me`
  и флажок «Только мои» на панели. Сообщения посетителей: `GET /api/reports?assignee=me`. Сводка панели содержит
  `assignedToMe {services, openReminders, openReports}`. Без ответственного напоминания видны всей организации, как раньше.
- Деактивация или потеря доступа: обзор `GET /api/functions/assignments` сверяет каждого ответственного со списком
  активных членов организации и помечает карточку `NEEDS_REASSIGNMENT`; панель показывает такие карточки первыми и
  предлагает выбрать нового человека. Никто не переназначается автоматически. Если identity-service недоступен,
  состояние `UNKNOWN` и предупреждение (`membersChecked=false`).
