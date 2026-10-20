# ReestrGov

**Корпоративная платформа государственного реестра и сервисного шлюза**

![Java 17](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud 2025.0](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-6DB33F?logo=spring&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-6DB33F?logo=spring&logoColor=white)
![Netflix Eureka](https://img.shields.io/badge/Netflix-Eureka-E50914?logo=netflix&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?logo=flyway&logoColor=white)
![React 18](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![Vite 8](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![TypeScript 5](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Tailwind CSS 3.4](https://img.shields.io/badge/Tailwind%20CSS-3.4-06B6D4?logo=tailwindcss&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose%20v2-2496ED?logo=docker&logoColor=white)
![Nginx 1.27](https://img.shields.io/badge/Nginx-1.27-009639?logo=nginx&logoColor=white)

## Содержание

1. [Краткий обзор](#1-краткий-обзор)
2. [Архитектура системы](#2-архитектура-системы)
3. [Предварительные требования](#3-предварительные-требования)
4. [Конфигурация окружения](#4-конфигурация-окружения)
5. [Быстрый старт с Docker Compose](#5-быстрый-старт-с-docker-compose)
6. [Локальная разработка без Docker](#6-локальная-разработка-без-docker)
7. [Модель безопасности](#7-модель-безопасности)
8. [Тестирование](#8-тестирование)
9. [Устранение неполадок](#9-устранение-неполадок)
10. [Стандарты чистого кода и участия в разработке](#10-стандарты-чистого-кода-и-участия-в-разработке)
11. [Структура репозитория](#11-структура-репозитория)
12. [Локализация и перевод](#12-локализация-и-перевод)
13. [Развёртывание изменений локализации и backfill](#13-развёртывание-изменений-локализации-и-backfill)

---

## 1. Краткий обзор

ReestrGov — это реестр государственных услуг («функций»), предоставляемых государственными организациями. Граждане просматривают публичный многоязычный каталог организаций и услуг без входа в систему: структурированные инструкции, контакты организаций, пошаговый подбор услуги, сохранение услуг в браузере, печать и PDF-памятка по услуге, отправка ссылки, страница «Поддержка» с постоянной личной перепиской (без учётной записи; в ней же — сообщения о неверной информации). Сотрудники входят в административную консоль для управления организациями, учётными записями сотрудников, ролями и правами, языками интерфейса и переводами, а также для ведения редакционного процесса карточек услуг (черновик, проверка, публикация, деактивация, повторная активация) с возможностью машинного перевода через Azure Translator.

Весь интерфейс (публичный сайт, вход, консоль, ошибки, уведомления, подписи доступности, документы Excel/PDF) берёт тексты из одного каталога ключей с проверенными переводами на английский, русский, узбекский (латиница) и французский; языки, добавленные позже, дозаполняются машинным переводом в фоне. Как это устроено — в [разделе 12](#12-локализация-и-перевод) и [docs/localization.md](docs/localization.md).

Платформа представляет собой набор микросервисов Spring Boot за Spring Cloud Gateway, зарегистрированных в реестре сервисов Netflix Eureka, каждый из которых владеет собственной базой данных PostgreSQL и собственной историей миграций Flyway. Одностраничное приложение на React, собранное с помощью Vite и обслуживаемое Nginx, обращается к шлюзу через same-origin прокси `/api/`, благодаря чему HttpOnly-cookie аутентификации работают без какой-либо настройки CORS в браузере.

| Слой | Технология |
| --- | --- |
| Среда выполнения бэкенда | Java 17, Spring Boot 3.5.4, Spring Cloud 2025.0.0 |
| Обнаружение сервисов и маршрутизация | Netflix Eureka, Spring Cloud Gateway (WebFlux), Spring Cloud LoadBalancer |
| Безопасность | Spring Security 6, stateless JWT на HMAC-SHA256 (jjwt 0.12.6), HttpOnly-cookie, BCrypt |
| Хранение данных | PostgreSQL 16, Spring Data JPA, Flyway, кэш Caffeine (reference-service) |
| Документация API | springdoc-openapi (Swagger UI по адресу `/swagger` в каждом сервисе) |
| Фронтенд | React 18, Vite 8, TypeScript 5 (strict), Tailwind CSS 3.4, Radix UI, TanStack Query и Table, react-hook-form, zod, framer-motion |
| Тестирование фронтенда | Playwright (UI-набор с мокированными маршрутами и набор с реальным бэкендом) |
| Тестирование бэкенда | JUnit 5, Spring Boot Test, H2 (тесты identity), Testcontainers и браузерные IT на Playwright (function catalog) |
| Упаковка | Многостадийные Dockerfile, кэш-монтирования BuildKit, непривилегированные пользователи среды выполнения, Nginx 1.27 для SPA |

---

## 2. Архитектура системы

```
                 ┌──────────────────────────────────────────────────────────┐
  Browser ──────►│  front  (Nginx 1.27, host port 3000)                     │
                 │  static SPA  +  /api/*  ───proxy───►  api-gateway:8082   │
                 └──────────────────────────────┬───────────────────────────┘
                                                │
                 ┌──────────────────────────────▼───────────────────────────┐
                 │  api-gateway  (Spring Cloud Gateway, port 8082)          │
                 │  path routing via lb:// + Eureka, global CORS            │
                 └───────┬──────────────────────┬────────────────────┬──────┘
                         │                      │                    │
     /api/regions/**     │   /api/functions/**  │   /api/** (rest)   │
     /api/interface-     │                      │                    │
       translations/**   │                      │                    │
     /api/translation-   │                      │                    │
       keys/**           ▼                      ▼                    ▼
   ┌─────────────────────────┐ ┌──────────────────────────┐ ┌──────────────────────┐
   │ reference-service :8084 │ │ function-catalog :8085   │ │ identity-service     │
   │ regions, UI dictionary, │ │ service cards, editorial │ │ :8081                │
   │ translation keys        │ │ workflow, categories,    │ │ auth, users, roles,  │
   │                         │ │ audit, Azure translate   │ │ permissions, orgs,   │
   │ reference_service_db    │ │ function_catalog_db      │ │ languages            │
   └─────────────────────────┘ └──────────────────────────┘ │ adlita_project1      │
                         ▲                      ▲           └──────────┬───────────┘
                         │                      │                      │
                         └───── register / discover ───────────────────┘
                                                │
                                 ┌──────────────▼──────────────┐
                                 │ eureka-server :8761         │
                                 └─────────────────────────────┘

                       PostgreSQL 16  (container 5432, host 5433, volume pgdata)
```

### 2.1 Каталог сервисов

| Модуль | Порт | Роль | Хранилище данных |
| --- | --- | --- | --- |
| `eureka-server` | 8761 (только внутри Compose) | Реестр сервисов и обнаружение. Не регистрирует сам себя; режим самосохранения отключён для быстрой локальной работы. | нет |
| `api-gateway` | 8082 (на хосте только `127.0.0.1`) | Единая точка входа для SPA. Маршрутизирует по пути к идентификаторам сервисов `lb://`, разрешаемым через Eureka, применяет глобальный CORS для dev-источников (`localhost:3000`, `localhost:5173`) с учётными данными и устраняет дублирование CORS-заголовков ответа. | нет |
| `identity-service` | 8081 (только внутри Compose) | Аутентификация (вход, обновление токена, выход, смена пароля), выпуск JWT, управление пользователями и сотрудниками, организации, роли, права и реестр языков интерфейса. Создаёт первого `SUPER_ADMIN`. | `adlita_project1` |
| `reference-service` | 8084 (только внутри Compose) | Справочные данные: регионы и словари интерфейса. При старте синхронизирует каталог `src/main/resources/i18n/interface/*.json` с базой (ручные правки не заменяются), отдаёт словарь каждого языка (Caffeine + ETag), считает полноту переводов и в фоне заполняет недостающие тексты активных языков через Azure Translator. | `reference_service_db` |
| `function-catalog-service` | 8085 (только внутри Compose) | Публичный и административный каталог услуг («функций»), категории, редакционный процесс с правами, журнал аудита, импорт CSV, ручные переводы и переводы через Azure. | `function_catalog_db` |
| `front` | 3000 → 80 | SPA на Vite/React, обслуживаемое Nginx с gzip, неизменяемым кэшированием ресурсов, SPA-фолбэком, `/healthz` и same-origin обратным прокси `/api/` на `api-gateway:8082`. | нет |
| `postgres` | `127.0.0.1:5433` → 5432 | PostgreSQL 16 с init-скриптом, который создаёт три базы данных при первом запуске. | том `pgdata` |

### 2.2 Маршрутизация шлюза

Маршруты объявлены в `api-gateway/src/main/resources/application.yml` и обрабатываются сверху вниз. Маршрут identity является catch-all, поэтому новые сервисы необходимо добавлять выше него.

| Порядок | ID маршрута | Предикаты пути | Цель |
| --- | --- | --- | --- |
| 1 | `reference-service` | `/api/regions/**`, `/api/interface-translations/**`, `/api/translation-keys/**` | `lb://reference-service` |
| 2 | `function-catalog-service` | `/api/functions/**`, `/api/reports/**`, `/api/analytics/**`, `/api/feedback/**`, `/api/help/**` | `lb://function-catalog-service` |
| 3 | `identity-service` | `/api/**` | `lb://identity-service` |

Шлюз намеренно сделан тонким. Проверка JWT и авторизация выполняются внутри каждого нижестоящего сервиса, а не на шлюзе, и ограничение частоты запросов не настроено. Если для развёртывания требуется проверка токена на уровне шлюза или ограничитель частоты запросов, добавьте `GlobalFilter` или фильтр `RequestRateLimiter` в модуль шлюза и задокументируйте изменение здесь.

### 2.3 Обзор REST-интерфейса

| Сервис | Базовые пути |
| --- | --- |
| identity-service | `/api/auth` (login, refresh, logout, me, change-password, `password-reset/options`, `password-reset/request`, `password-reset/complete`), `/api/user` (вкл. `POST /{id}/password-reset` только для SUPER_ADMIN), `/api/admin/moderators`, `/api/admin/org-admins`, `/api/organizations` (вкл. `/{id}/verify` и `PUT /{id}/translations` — ручные переводы названия и описания), `/api/public/organizations`, `/api/roles` (вкл. `/assign`, `/{id}/permissions`), `/api/permissions`, `/api/languages` (вкл. `/catalog`) |
| reference-service | `/api/regions`, `/api/interface-translations/{languageCode}` (а также `/exact`, `/missing`, `/coverage`; `PUT` и `DELETE /{languageCode}/{key}` для ручных правок), `GET /api/interface-translations/overview` (полнота и состояние машинного перевода всех активных языков), `POST /api/interface-translations/{languageCode}/machine-translation` (поставить язык в очередь / повторить), `/api/translation-keys` |
| function-catalog-service | `/api/functions` (публичный список и детали, `/admin`, `/{id}/admin`, `/pending-review`, `/{id}/submit-for-review`, `/{id}/withdraw`, `/{id}/publish`, `/{id}/reject`, `/{id}/reactivate`, `POST /{id}/revisions`, `DELETE /{id}/revision`, `/{id}/requirements`, `/{id}/translations`, `/{id}/translate`, `/{id}/verify`, `/{id}/audit`, `/import`, `/translation-capabilities`, `/{id}/translation-job`, `POST /translations/backfill?includePublished=&organizationId=&limit=`, публичный `GET /{id}/guide.pdf`), `/api/functions/categories`, `/api/reports` (анонимный `POST`, `GET`, `GET /{id}`, `GET /{id}/history`, `PUT /{id}/status`, `GET /export?format=CSV\|XLSX\|PDF`), `/api/analytics` (анонимный `POST /events`, `GET /summary`, `/engagement`, `/engagement/export`, `/report/export?format=XLSX\|PDF`, `/quality-queue`, `/quality-queue/export`, `/reminders`, `POST /reminders/{id}/acknowledge`), `/api/help` (посетитель по HttpOnly-cookie или учётной записи: `GET /options`, `GET /support`, `/support/updates`, `/support/unread`, `POST /support/messages`, `POST /support/read`, `PUT /support/contact`, `POST /support/recovery-code`, `POST /support/recover`, `DELETE /support/session`; для сотрудников `GET /inbox`, `/inbox/unread`, `/inbox/{id}`, `POST /inbox/{id}/read`, `POST /inbox/{id}/replies`, `PUT /inbox/{id}/status`, `GET /inbox/{id}/candidates`, `PUT /inbox/{id}/assignee`) |

Каждый сервис предоставляет Swagger UI по адресу `http://localhost:<port>/swagger` и документ OpenAPI по адресу `/v3/api-docs`.

### 2.4 Фронтенд-приложение

SPA находится в `front/` и организовано по функциональным областям:

- `src/components/ui` — дизайн-система (Button, Card, DataTable, Field, Input, Select, Modal, DropdownMenu, Tooltip, Badge, Skeleton, Toolbar, ThemeToggle), построенная на токенах Tailwind, примитивах Radix и class-variance-authority.
- `src/features/*` содержит хуки TanStack Query, схемы zod и диалоги для каждого домена (functions, organizations, staff, roles, legacy users, languages, reference).
- `src/pages` содержит публичные страницы (`/`, `/organizations/:id`, `/functions/:id`, `/finder`, `/saved`, `/support`; старые `/help` и `/help/conversation` перенаправляют на `/support`), `/login` и административную консоль (`/admin`, `/admin/functions`, `/admin/functions/new`, `/admin/functions/:id`, `/admin/reports`, `/admin/questions`, `/admin/organization-dashboard`, `/admin/organizations`, `/admin/moderators`, `/admin/org-admins`, `/admin/roles`, `/admin/languages`, `/admin/users`, `/settings/security`).
- `src/contexts` предоставляет аутентификацию, тему (светлая, тёмная, системная) и i18n. Словарь UI загружается из reference-service для выбранной локали (последний полученный словарь хранится в `localStorage` и показывается сразу при следующем визите), каждая видимая строка разрешается через `t('key')` с интерполяцией `{name}` и формами множественного числа CLDR; язык, направление письма (`dir="rtl"`), даты, числа и названия языков переключаются одновременно, когда словарь получен. `npm run i18n:check` запрещает строки в обход `t()`.
- `src/services/http.ts` — единственный HTTP-клиент. Он всегда отправляет `credentials: 'include'`, никогда не хранит токены, сопоставляет `fieldErrors` бэкенда с ошибками формы и выполняет единственное одновременное обновление токена при `401`.

---

## 3. Предварительные требования

| Инструмент | Версия | Для чего нужен |
| --- | --- | --- |
| Docker Desktop (или Docker Engine) с Compose v2 | Docker 24+, Compose 2.20+ | Запуск полного стека; кэш-монтирования BuildKit в Dockerfile |
| Node.js | `^20.19.0 \|\| >=22.12.0` (см. `engines` в `front/package.json`) | Локальная разработка фронтенда и тесты Playwright |
| JDK | 17 (Temurin или Microsoft Build of OpenJDK) | Локальная разработка бэкенда и сборки Maven |
| Maven | 3.9+ | Локальные сборки бэкенда (`mvn`) |
| Клиент PostgreSQL (опционально) | `psql` 16 | Просмотр баз данных на `localhost:5433` |

Docker Engine 29 и новее требует Docker API 1.44; тесты function-catalog фиксируют эту версию для клиента Testcontainers (см. [Тестирование](#8-тестирование)).

---

## 4. Конфигурация окружения

Все секреты передаются через окружение. Ничего чувствительного не коммитится; `.gitignore` исключает `.env`, `.env.*`, PEM-ключи и хранилища ключей, сохраняя при этом `.env.example`.

Создайте файл один раз:

```shell
cp .env.example .env
```

Docker Compose читает корневой `.env` автоматически. Сервисы, запущенные из IDE или через Maven, также читают его, поскольку каждый сервис объявляет `spring.config.import: optional:file:.env[.properties],optional:file:../.env[.properties]`.

### 4.1 Переменные

| Переменная | Обязательна | Кем используется | Описание |
| --- | --- | --- | --- |
| `POSTGRES_USER` | нет (по умолчанию `postgres`) | postgres, все сервисы с данными | Суперпользователь PostgreSQL, используемый init-скриптом и сервисами |
| `POSTGRES_PASSWORD` | **да** | postgres, все сервисы с данными | Пароль для `POSTGRES_USER`. Compose отказывается запускаться без него |
| `JWT_SECRET` | **да** | identity, reference, function-catalog | Общий ключ HMAC-SHA256. Должен быть не короче 32 байт (256 бит). Сгенерируйте с помощью `openssl rand -base64 48` |
| `MFA_ENABLED` | нет (по умолчанию `false`) | identity-service | Второй фактор (TOTP) для `SUPER_ADMIN` и `ORG_ADMIN`. **В этом окружении отключён конфигурацией**: при `false` код не запрашивается, ключ не нужен, таблицы и данные enrollment сохраняются. `true` включает MFA снова вместе с **тем же** `MFA_ENCRYPTION_KEY`, которым зашифрованы существующие enrollment; см. [docs/mfa-sessions-and-assignments.md](docs/mfa-sessions-and-assignments.md) |
| `MFA_ENCRYPTION_KEY` | только при `MFA_ENABLED=true` | identity-service | Base64-ключ в 32 байта (`openssl rand -base64 32`) для шифрования секретов TOTP; без него включённый MFA не даёт identity-service стартовать. При повторном включении нужен **тот же** ключ, которым зашифрованы существующие enrollment. Не используйте `JWT_SECRET` повторно |
| `MFA_ENROLLMENT_DEADLINE` | нет | identity-service | Срок в ISO-8601, до которого существующие администраторы могут входить без второго фактора (только при включённом MFA) |
| `APP_COOKIE_SECURE` | **да** | identity-service | `false` для локального HTTP-стека за same-origin прокси Nginx, `true` для любого HTTPS-развёртывания |
| `APP_COOKIE_SAME_SITE` | **да** | identity-service | `Lax` для same-origin конфигураций, `None` (вместе с `APP_COOKIE_SECURE=true`), когда SPA обслуживается с другого источника |
| `BOOTSTRAP_SUPER_ADMIN_EMAIL` | только при первом запуске | identity-service | E-mail начального `SUPER_ADMIN`. Обязателен, пока в базе identity нет пользователя с `ROLE_SUPER_ADMIN` |
| `BOOTSTRAP_SUPER_ADMIN_PASSWORD` | только при первом запуске | identity-service | Начальный пароль, сразу хэшируется BCrypt и помечается `mustChangePassword`. Удалите после первого входа |
| `PUBLIC_BASE_URL` | нет | identity-service, function-catalog-service | Публичный адрес SPA (например `https://reestr.example`); используется для ссылок в письмах (сброс пароля, уведомление об ответе поддержки) и в колонтитуле PDF-памятки |
| `PASSWORD_RESET_MAIL_ENABLED` | нет | identity-service | `true` включает самостоятельный сброс пароля («Забыли пароль?»). Требует `SPRING_MAIL_HOST`, `PASSWORD_RESET_MAIL_FROM` и `PUBLIC_BASE_URL`; без них ссылка не показывается, а `POST /api/auth/password-reset/request` отвечает 503 |
| `PASSWORD_RESET_MAIL_FROM` | нет | identity-service | Адрес отправителя писем со ссылкой сброса |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | нет | identity-service, function-catalog-service | SMTP-сервер для писем сброса пароля и уведомлений об ответах поддержки (стандартные свойства Spring `spring.mail.*`) |
| `HELP_MAIL_ENABLED` | нет (по умолчанию `false`) | function-catalog-service | `true` разрешает необязательное (по согласию посетителя) e-mail-уведомление «поддержка ответила». Действует только вместе с `HELP_MAIL_FROM`, `PUBLIC_BASE_URL` и `SPRING_MAIL_HOST`; иначе работают только счётчик непрочитанного и уведомление на сайте |
| `HELP_MAIL_FROM` | нет | function-catalog-service | Адрес отправителя уведомлений поддержки |
| `APP_COOKIE_SECURE` (для каталога) | **да** (в Compose) | function-catalog-service | Флаг `Secure` cookie гостя поддержки `reestr_support`; локально по HTTP — `false`, при HTTPS — `true` |
| `PASSWORD_RESET_TOKEN_TTL` | нет | identity-service | Срок действия одноразовой ссылки сброса, ISO-8601 (по умолчанию `PT60M`) |
| `AZURE_TRANSLATOR_ENDPOINT` | нет | function-catalog-service, reference-service | Endpoint Azure Translator (например `https://api.cognitive.microsofttranslator.com`). Пока любое из трёх значений пусто, автоматический перевод не выполняется: содержимое сохраняется и ждёт в очереди, интерфейс показывает недостающие тексты на английском, редактор видит «Автоматический перевод недоступен» |
| `AZURE_TRANSLATOR_KEY` | нет | function-catalog-service, reference-service | Ключ ресурса Azure Translator. Читается только из окружения/`.env`, передаётся только в заголовке запроса к Azure, никогда не логируется, не сохраняется в базе и не попадает в ответы API |
| `AZURE_TRANSLATOR_REGION` | нет | function-catalog-service, reference-service | Регион ресурса Azure Translator |
| `INTERFACE_TRANSLATION_ENABLED` | нет (по умолчанию `true`) | reference-service | `false` приостанавливает фоновое заполнение словарей интерфейса; проверенный каталог применяется при старте в любом случае |
| `SPRING_JPA_SHOW_SQL` | нет (по умолчанию `false`) | reference-service | Логирование SQL для отладки |
| `GATEWAY_TRUSTED_PROXIES` | нет (по умолчанию `172.28.0.10/32`, статический адрес nginx в Compose) | api-gateway | IP или CIDR обратных прокси, которым разрешено передавать `X-Real-IP`/`X-Forwarded-For`. Остальным вызывающим шлюз подставляет их реальный адрес, поэтому лимиты на клиента нельзя обойти подменой заголовка |
| `CATALOG_TRANSLATION_ENABLED` | нет (по умолчанию `true`) | function-catalog-service | `false` приостанавливает фоновый воркер перевода; задачи копятся в `translation_jobs` и выполняются после включения (см. [docs/machine-translation.md](docs/machine-translation.md)) |
| `IDENTITY_DB_PASSWORD` | нет | identity-service вне Compose | Переопределяет `POSTGRES_PASSWORD` для базы identity |
| `REFERENCE_DB_PASSWORD` | нет | reference-service вне Compose | Переопределяет `POSTGRES_PASSWORD` для базы reference |
| `FUNCTION_CATALOG_DB_PASSWORD` | нет | function-catalog-service вне Compose | Переопределяет `POSTGRES_PASSWORD` для базы каталога |
| `VITE_API_BASE_URL` | нет | front (аргумент сборки) | Публичный источник шлюза, когда SPA не обслуживается за прокси `/api` Nginx или Vite. Оставьте пустым для Compose |
| `APP_DEMO_USERS_ENABLED`, `DEMO_SUPER_ADMIN_EMAIL`, `DEMO_SUPER_ADMIN_PASSWORD` | нет | identity-service, только dev/test | Создание демо-аккаунтов по явному включению. Никогда не включайте в production |

Стандартные свойства Spring, такие как `SPRING_DATASOURCE_URL` и `SPRING_DATASOURCE_USERNAME`, могут переопределять несекретные значения по умолчанию. Compose задаёт их для каждого сервиса, как показано ниже.

### 4.2 Порты и URI баз данных

| Сервис | Порт контейнера | Порт хоста | Источник данных внутри Compose | Источник данных вне Compose (по умолчанию в `application.yml`) |
| --- | --- | --- | --- | --- |
| postgres | 5432 | `127.0.0.1:5433` | н/д | н/д |
| eureka-server | 8761 | не публикуется | н/д | н/д |
| api-gateway | 8082 | `127.0.0.1:8082` | н/д | н/д |
| identity-service | 8081 | не публикуется | `jdbc:postgresql://postgres:5432/adlita_project1` | `jdbc:postgresql://localhost:5433/adlita_project1` |
| reference-service | 8084 | не публикуется | `jdbc:postgresql://postgres:5432/reference_service_db` | `jdbc:postgresql://localhost:5433/reference_service_db` |
| function-catalog-service | 8085 | не публикуется | `jdbc:postgresql://postgres:5432/function_catalog_db` | `jdbc:postgresql://localhost:5433/function_catalog_db` |
| front | 80 | 3000 | н/д | Dev-сервер Vite на 5173 проксирует `/api` на `localhost:8082` |

Внутри Compose каждый Spring-сервис обнаруживает реестр через `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka`; вне Compose значение по умолчанию — `http://localhost:8761/eureka`. Compose создаёт сеть `172.28.0.0/16`, в которой nginx (`front`) имеет статический адрес `172.28.0.10` — единственный доверенный источник заголовка `X-Real-IP` для шлюза. Бэкенды и Eureka не публикуются на хосте: все запросы идут `nginx → api-gateway → сервис`.

### 4.3 Пример `.env`

```dotenv
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change-me-locally

# openssl rand -base64 48
JWT_SECRET=REPLACE_WITH_A_RANDOM_BASE64_STRING_OF_AT_LEAST_32_BYTES

APP_COOKIE_SECURE=false
APP_COOKIE_SAME_SITE=Lax

BOOTSTRAP_SUPER_ADMIN_EMAIL=admin@example.uz
BOOTSTRAP_SUPER_ADMIN_PASSWORD=REPLACE_WITH_A_LONG_RANDOM_INITIAL_PASSWORD

# MFA отключён; для включения задайте true и ТОТ ЖЕ ключ, которым зашифрованы существующие enrollment
MFA_ENABLED=false
MFA_ENCRYPTION_KEY=
MFA_ENROLLMENT_DEADLINE=

# Azure Translator: пустые значения = перевод ждёт в очереди; заполните из портала Azure, не коммитьте
AZURE_TRANSLATOR_ENDPOINT=
AZURE_TRANSLATOR_KEY=
AZURE_TRANSLATOR_REGION=
CATALOG_TRANSLATION_ENABLED=true
INTERFACE_TRANSLATION_ENABLED=true
```

---

## 5. Быстрый старт с Docker Compose

### Шаг 1: клонирование и настройка

```shell
git clone <repository-url> ReestrGov
cd ReestrGov
cp .env.example .env
openssl rand -base64 48          # paste the output into JWT_SECRET
```

Заполните `POSTGRES_PASSWORD`, `JWT_SECRET`, `APP_COOKIE_SECURE`, `APP_COOKIE_SAME_SITE` и, для первого запуска, оба значения `BOOTSTRAP_SUPER_ADMIN_*`.

### Шаг 2: чистая сборка

```shell
docker compose down --remove-orphans
docker compose build --no-cache
```

Каждый Java-образ собирается в две стадии: `maven:3.9-eclipse-temurin-17` разрешает плагины и зависимости в кэш-монтирование BuildKit (`/root/.m2`), затем упаковывает jar с `-DskipTests`; стадия выполнения — `eclipse-temurin:17-jre`, запускаемая от непривилегированного пользователя `app`. Образ фронтенда выполняет `npm ci` и `npm run build` на `node:22-alpine` и копирует `dist/` в `nginx:1.27-alpine`. Последующие сборки переиспользуют кэш, если не указан `--no-cache`.

### Шаг 3: запуск стека

```shell
docker compose up -d
```

Порядок запуска обеспечивается `depends_on`: сервисы с данными ждут проверки работоспособности PostgreSQL (`pg_isready`), шлюз ждёт сервисы, а фронтенд ждёт шлюз. У каждого контейнера задано `restart: unless-stopped`.

### Шаг 4: проверка работоспособности и логов

```shell
docker compose ps
docker compose logs -f                       # everything
docker compose logs -f identity-service      # one service
curl -s http://localhost:3000/healthz        # nginx -> "ok"
curl -s http://localhost:3000/api/public/organizations   # JSON through nginx and the gateway
docker compose exec api-gateway wget -qO- http://eureka-server:8761/eureka/apps | grep -o '<app>[A-Z-]*' # registry, internal only
```

| URL | Что вы должны увидеть |
| --- | --- |
| `http://localhost:3000` | Публичный каталог; `/login` открывает административную консоль |
| `http://localhost:3000/api/public/organizations` | JSON через nginx и шлюз |
| `http://127.0.0.1:8082/api/public/organizations` | JSON напрямую через шлюз (только с этого хоста; для dev-сервера Vite) |

Eureka и Swagger сервисов с хоста недоступны: реестр не имеет аутентификации и живёт только во внутренней сети Compose, а порты сервисов не публикуются. Для Swagger запустите нужный сервис из IDE (раздел 6) или временно пробросьте порт через `docker compose exec`.

Войдите под учётной записью bootstrap-администратора. Учётная запись обязана сменить пароль, прежде чем любой другой вызов бэкенда будет выполнен; после этого удалите `BOOTSTRAP_SUPER_ADMIN_PASSWORD` из `.env`.

### Повседневные команды

```shell
docker compose up --build -d front identity-service   # rebuild and restart selected services
docker compose restart api-gateway                     # restart without rebuilding
docker compose down                                    # stop and remove containers, keep the database
docker compose down -v --remove-orphans                # also delete the pgdata volume (destroys all data)
docker builder prune -f                                # free BuildKit cache
docker compose config                                  # validate the merged Compose file
```

---

## 6. Локальная разработка без Docker

### 6.1 База данных

Используйте только PostgreSQL из Compose:

```shell
docker compose up -d postgres
```

Она опубликована на `localhost:5433` и содержит `adlita_project1`, `reference_service_db` и `function_catalog_db`. Нативный PostgreSQL на 5432 — это отдельный экземпляр, который не используется конфигурацией по умолчанию.

### 6.2 Бэкенд-сервисы

Установите `JAVA_HOME` на JDK 17 и запустите сервисы из корня репозитория, каждый в своём терминале, в следующем порядке:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS example

(cd eureka-server && mvn clean spring-boot:run)
(cd identity-service && mvn clean spring-boot:run)
(cd reference-service && mvn clean spring-boot:run)
(cd function-catalog-service && mvn clean spring-boot:run)
(cd api-gateway && mvn clean spring-boot:run)
```

Каждый сервис читает корневой `.env` через `spring.config.import`, подключается к `localhost:5433` и регистрируется в Eureka на `localhost:8761`. Корневой `pom.xml` является агрегатором, поэтому `mvn -q -DskipTests package` из корня собирает все пять модулей.

IntelliJ IDEA: откройте корневой `pom.xml` как Maven-проект, установите SDK проекта на 17 и запустите классы `*Application`. В окне инструмента **Database** подключитесь к `localhost:5433` с `POSTGRES_USER` и `POSTGRES_PASSWORD`; храните пароль в хранилище паролей IDEA, а не в JDBC URL.

### 6.3 Фронтенд

```shell
cd front
npm install
npm run dev          # http://localhost:5173, /api proxied to http://localhost:8082
```

Vite проксирует `/api` на шлюз, поэтому cookie являются same-origin и настройка CORS не требуется. Шлюз и identity-service уже разрешают `http://localhost:5173` с учётными данными для прямых вызовов.

Другие скрипты:

```shell
npm run typecheck    # tsc for the app and for the Playwright tests
npm run lint         # ESLint (zero warnings) + the static localization check
npm run i18n:check   # only the localization check: catalog completeness, keys, hard-coded text
npm run build        # production bundle into dist/
npm run preview      # serve dist/ locally
```

---

## 7. Модель безопасности

- **Stateless JWT с HttpOnly-cookie.** `POST /api/auth/login` устанавливает `accessToken` (24 ч, путь `/`) и `refreshToken` (7 дней, путь `/api/auth`) как HttpOnly-cookie. SPA никогда не читает и не хранит токены. `POST /api/auth/refresh` ротирует refresh-токен; `POST /api/auth/logout` отзывает его.
- **Один общий HMAC-ключ.** identity-service подписывает токены ключом `JWT_SECRET`; reference-service и function-catalog-service проверяют их локально тем же ключом, поэтому сетевой вызов на каждый запрос не требуется.
- **Обязательная смена пароля.** Подписанный токен содержит `mustChangePassword`. identity-service ограничивает такие сессии профилем, сменой пароля, выходом и входом; остальные сервисы отклоняют токены, у которых этот флаг равен true или отсутствует. Успешная смена выдаёт новые cookie.
- **Отключённые пользователи отклоняются** на уровне JWT-фильтра, даже если у них есть действительный токен.
- **Защита входа от перебора.** `POST /api/auth/login` ограничен на клиента (20 попыток за 15 минут по `X-Real-IP`) и на учётную запись (10 неверных паролей за 15 минут по введённому e-mail, независимо от того, существует ли аккаунт, чтобы ответ не раскрывал его наличие); превышение даёт 429 с общим сообщением, успешный вход сбрасывает счётчик. Настройки: `app.login.*` в `application.yaml`.
- **Администратор не может обойти собственную защиту.** `POST /api/user/{id}/mfa/reset` и `POST /api/user/{id}/password-reset` отвергают собственный id вызывающего: свой второй фактор отключается только с паролем и кодом, свой пароль меняется только через текущий пароль, поэтому перехваченная сессия `SUPER_ADMIN` не даёт постоянного захвата аккаунта.
- **Адрес клиента нельзя подменить.** Шлюз (`ClientAddressFilter`) оставляет `X-Real-IP`/`X-Forwarded-For` только от доверенных прокси (`GATEWAY_TRUSTED_PROXIES`, в Compose — статический адрес nginx) и для всех остальных подставляет реальный адрес соединения; сервисы и Eureka не публикуются на хосте, поэтому лимиты на клиента (вход, сброс пароля, MFA, сообщения, отзывы) нельзя обойти прямым обращением.
- **Второй фактор и сессии.** Второй фактор отключён конфигурацией (`MFA_ENABLED=false` по умолчанию в `application.yaml`, `docker-compose.yml` и `.env.example`); код, миграция `V5` и таблицы сохранены, включение описано в [docs/mfa-sessions-and-assignments.md](docs/mfa-sessions-and-assignments.md). При `MFA_ENABLED=true` `SUPER_ADMIN` и `ORG_ADMIN` после пароля подтверждают вход TOTP-кодом (или кодом восстановления); до этого браузер держит только одноразовый challenge-cookie с путём `/api/auth/mfa`, а access/refresh не выдаются. Секреты шифруются `MFA_ENCRYPTION_KEY`, коды восстановления хранятся как хэши. Пользователь видит и завершает свои сессии (`/api/auth/sessions`), `SUPER_ADMIN` — сессии любого пользователя с аудитом; access-токен несёт `sid` и отклоняется identity-service сразу после отзыва (см. [docs/mfa-sessions-and-assignments.md](docs/mfa-sessions-and-assignments.md)).
- **Сброс пароля без раскрытия паролей.** Пароли хранятся только как BCrypt-хэши; ни один endpoint не показывает, не генерирует и не логирует пароль. `SUPER_ADMIN` вызывает `POST /api/user/{id}/password-reset` и получает одноразовую ссылку ровно один раз (в базе остаётся только SHA-256 хэш токена, инициатор, цель и время); пользователь задаёт новый пароль через `/reset-password?token=…`. Токен случайный (256 бит), одноразовый, живёт `PASSWORD_RESET_TOKEN_TTL`, новый сброс аннулирует предыдущие. Успешный сброс (и обычная смена пароля) записывает `credentials_updated_at`: refresh-токены отзываются, а access-токены, выпущенные раньше, отклоняются. «Забыли пароль?» появляется только при настроенной SMTP-доставке (`PASSWORD_RESET_MAIL_ENABLED`), ответ не раскрывает существование учётной записи; запросы и попытки ввода токена ограничены по частоте (на клиента по `X-Real-IP` и глобально).
- **RBAC.** Роли владеют правами; редакционные endpoints используют `@PreAuthorize("hasAuthority(...)")`, например `FUNCTIONS_REVIEW`, `FUNCTIONS_PUBLISH`, `FUNCTIONS_REACTIVATE`, `FUNCTION_CATEGORIES_MANAGE` и `AUDIT_VIEW`. Администраторы организаций ограничены своими организациями. Сообщения посетителей читаются с `REPORTS_VIEW` или `ORG_REPORTS_MANAGE`, а их статус меняется с `ORG_REPORTS_MANAGE` или `REPORTS_VIEW` + `REPORTS_MANAGE`. Панель организации, очередь качества, напоминания и выгрузки (CSV, Excel, PDF) требуют `ORG_ANALYTICS_VIEW`; выгрузки повторяют фильтры и ограничение по организации экрана и не содержат контактов посетителей, текстов сообщений и данных сотрудников. Входящие поддержки читаются с `VISITOR_QUESTIONS_VIEW` (только переписка своих организаций; центральная — только глобальные сотрудники), ответ, назначение и смена статуса требуют ещё `VISITOR_QUESTIONS_REPLY`; контакт посетителя виден только сотрудникам с правом ответа. Все проверки и ограничение по организации выполняются на бэкенде (см. [docs/organization-analytics.md](docs/organization-analytics.md)). Проверку карточки услуги отмечает `FUNCTIONS_REVIEW`, проверку организации — `ORGANIZATIONS_EDIT` или `ORGANIZATIONS_EDIT_OWN` для своей организации; отметка означает внутреннюю проверку организацией (с указанием сотрудника и даты), а не официальное подтверждение, и не требует внешней ссылки. Опубликованная карточка редактируется только через черновую редакцию (`POST /api/functions/{id}/revisions`, `FUNCTIONS_EDIT` в пределах своей организации; `SUPER_ADMIN` и `FUNCTIONS_MANAGE_ANY_ORGANIZATION` — для любой), публичная версия меняется только при публикации редакции рецензентом (см. [docs/function-editorial-workflow.md](docs/function-editorial-workflow.md)).
- **Публичные endpoints.** Анонимный доступ ограничен `GET /api/public/**`, `GET /api/languages`, `GET /api/languages/catalog`, `GET /api/regions/**`, `GET /api/interface-translations/{language}`, публичными чтениями `GET /api/functions/**` (варианты admin, pending-review и audit исключены), анонимной отправкой `POST /api/reports`, `POST /api/analytics/events` и `POST /api/feedback`, PDF-памяткой `GET /api/functions/{id}/guide.pdf`, перепиской поддержки посетителя (`/api/help/options`, `/api/help/support/**` — доступ только к собственной переписке по HttpOnly SameSite=Strict cookie гостя или по учётной записи; номер переписки доступа не даёт; запросы с чужим Origin отклоняются), точками входа аутентификации и Swagger. Всё остальное требует аутентификации. Отправка сообщений ограничена по частоте в function-catalog-service (на клиента по заголовку `X-Real-IP`, глобально и на один объект в сутки) и защищена скрытым полем-ловушкой; события активности ограничены на клиента и глобально и хранятся только как дневные счётчики; IP-адреса не сохраняются. Порт шлюза публикуется только на `127.0.0.1`, и даже прямой вызов не может подменить `X-Real-IP`: шлюз перезаписывает его для всех, кроме доверенных прокси.
- **Никаких встроенных учётных данных.** Первый администратор берётся из `BOOTSTRAP_SUPER_ADMIN_EMAIL` и `BOOTSTRAP_SUPER_ADMIN_PASSWORD`; identity-service отказывается запускаться без них, пока не существует ни одного `SUPER_ADMIN`. Демо-аккаунты существуют только при `APP_DEMO_USERS_ENABLED=true` в непродакшен-профиле.
- **Ротация секретов.** При изменении `JWT_SECRET` или пароля базы данных переразверните все три сервиса с данными вместе и отзовите действующие refresh-сессии (`UPDATE refresh_tokens SET revoked = TRUE WHERE revoked = FALSE;` в базе identity), чтобы старый refresh-токен не мог выпустить заново подписанный access-токен.

Перед каждым коммитом проверяйте, что ни один файл с секретами не отслеживается:

```shell
git ls-files | grep -E '(^|/)\.env($|\.)|\.(pem|key|p12|pfx|jks)$'
```

---

## 8. Тестирование

### Бэкенд

| Модуль | Что запускается | Инфраструктура |
| --- | --- | --- |
| identity-service | Модульные и `@SpringBootTest` наборы с профилем `test` (`application-test.yaml`): H2 в памяти в режиме PostgreSQL, Flyway отключён, Eureka отключена; `LoginProtectionTest`/`LoginAttemptGuardTest` проверяют защиту входа | нет |
| reference-service | Модульные тесты и тесты веб-слоя (сопоставление BCP 47 → Azure, защита плейсхолдеров, клиент Azure, безопасность endpoints) и `InterfaceTranslationPostgresTest` (Testcontainers PostgreSQL 16: миграции, синхронизация каталога, сохранение ручных правок, полнота, очередь машинного перевода, повторы, неподдерживаемые языки, ETag) | Docker daemon |
| function-catalog-service | Модульные тесты, `CatalogPostgresTest`, `CatalogEnhancementsPostgresTest`, `ReportWorkflowPostgresTest`, `OrganizationInsightsPostgresTest`, `OrganizationDashboardPostgresTest`, `FunctionRevisionPostgresTest`, `PublicHistoryAssignmentFeedbackPostgresTest`, `MachineTranslationPostgresTest`, `OrganizationReportExportPostgresTest`, `HelpConversationPostgresTest`, `ServiceGuidePostgresTest` (Testcontainers PostgreSQL 16) и `AdminFunctionsBrowserIT` (браузерный сценарий Playwright против реального Spring-контекста) | Docker daemon |
| api-gateway | `ClientAddressFilterTest` (доверенные прокси, перезапись `X-Real-IP`, разбор CIDR) | нет |
| eureka-server | Тестов нет; `mvn test` только компилирует модуль | нет |

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
(cd identity-service && mvn -q test)
(cd reference-service && mvn -q test)                                                   # needs Docker
(cd reference-service && mvn -q test -Dtest='!*PostgresTest')                           # without Docker
(cd function-catalog-service && mvn -q test)                                           # needs Docker
(cd function-catalog-service && mvn -q test -Dtest='!*PostgresTest,!AdminFunctionsBrowserIT')   # without Docker
```

`src/test/resources/docker-java.properties` в function-catalog-service и reference-service фиксирует версию Docker API 1.44 для клиента Testcontainers, поскольку Docker Engine 29 требует её.

### Фронтенд

```shell
cd front
npx playwright install chromium   # once
npm run test                      # UI suite against route mocks (all specs listed in playwright.config.ts, incl. checklist, support and exports)
npm run test:backend              # real-backend suite, driven by AdminFunctionsBrowserIT
```

UI-набор запускает Vite на порту 5183 внутри процесса (`tests/start-vite.mjs`) и мокирует каждый вызов `/api/**` в `tests/fixtures.ts`, отдавая словари из каталога reference-service (`src/main/resources/i18n/interface`), как это делает сервер. `tests/localization.spec.ts` переключает главную страницу и панель администратора между английским и французским, обходит 23 публичные и административные страницы на `fr`, `ru` и `uz` и падает, если на экране остался английский текст каталога; там же проверяются варианты языка, RTL, гонка медленного словаря, ошибка загрузки, сохранение выбора, формы множественного числа и сама статическая проверка. Набор с реальным бэкендом требует `CATALOG_TEST_URL` и `CATALOG_TEST_JWT`, которые предоставляет Java-интеграционный тест.

### Критерии готовности

```shell
cd front && npm run typecheck && npm run lint && npm run build && npm run test
```

Известно на момент последней проверки: 7 сценариев в `checklist.spec.ts`, `feedback-history.spec.ts` и `public-catalog.spec.ts` падают и на исходном коммите `6d2b4a5` (неоднозначные локаторы: печатный лист подготовки дублирует текст страницы); к локализации они не относятся. Сценарий `support.spec.ts:22` нестабилен и на исходном коммите (2 падения из 5 повторов).

вместе с зелёными прогонами Maven-тестов для изменённых сервисов.

---

## 9. Устранение неполадок

### Проверка работоспособности PostgreSQL никогда не проходит или сервисы не могут подключиться

- Симптом: `dependency failed to start: container postgres is unhealthy` или повторяющиеся `Connection refused` в логе сервиса.
- Проверка работоспособности выполняет `pg_isready -h 127.0.0.1 -U $POSTGRES_USER -d postgres` каждые 5 с со стартовым периодом 10 с и 12 повторами. На медленных дисках первая инициализация (создание трёх баз данных) может превысить это окно. Выполните `docker compose logs postgres`; если init-скрипт всё ещё выполняется, подождите и снова выполните `docker compose up -d`.
- Если в логе указано `FATAL: password authentication failed`, том `pgdata` был инициализирован с другим `POSTGRES_PASSWORD`. Либо восстановите старое значение, либо сбросьте базу данных (см. ниже).
- Базы создаёт `init-multiple-dbs.sql` только при первой инициализации пустого тома `pgdata` (SQL-файл не требует прав на выполнение, в отличие от прежнего `init-multiple-dbs.sh`, который на bind-mount с macOS/Windows падал с `bad interpreter: Permission denied`). Если том уже существует, а баз нет, создайте их вручную: `docker compose exec postgres psql -U postgres -c 'CREATE DATABASE adlita_project1' -c 'CREATE DATABASE reference_service_db' -c 'CREATE DATABASE function_catalog_db'`.
- Порт `5433` уже занят: измените хостовую сторону сопоставления портов `postgres` в `docker-compose.yml` и URL `localhost:5433` в трёх файлах `application.yml` для локальных запусков.
- Исчерпание пула соединений (`HikariPool ... Connection is not available`) обычно означает, что сервис перезапускается в цикле, пока PostgreSQL ещё прогревается. Проверьте счётчики перезапусков в `docker compose ps` и сначала исправьте исходную ошибку в этом сервисе.

### Разрешение зависимостей Maven внутри Docker завершается ошибкой (401 Unauthorized)

- Симптом: `docker compose build` завершается ошибкой на шаге `mvn dependency:go-offline` с `401 Unauthorized` от `maven.pkg.github.com` или `repo.gradle.org`.
- Причина: родительский POM Flyway объявляет дополнительные репозитории для своих опциональных модулей. `dependency:go-offline` пытается разрешить *каждый* опциональный и транзитивный артефакт, включая те, что существуют только в этих приватных репозиториях, и роняет всю сборку.
- Решение, применённое во всех пяти Dockerfile: слой прогрева теперь `mvn -B -q -Dsilent=true dependency:resolve-plugins dependency:resolve || true`. Он разрешает только то, что действительно нужно проекту, терпимо относится к отсутствующим артефактам, а реальный шаг `mvn clean package -DskipTests` подтягивает всё недостающее из Maven Central. Оба шага используют общее кэш-монтирование BuildKit `/root/.m2`, поэтому пересборки остаются быстрыми.
- Если сборка всё ещё зависает на загрузке зависимостей, выполните `docker builder prune -f` и пересоберите с `--no-cache`; повреждённое кэш-монтирование может хранить наполовину загруженный артефакт.

### Сброс баз данных

```shell
docker compose down -v --remove-orphans
docker compose up -d
```

> **Предупреждение.** `-v` удаляет том `pgdata`, а значит, каждую организацию, пользователя, роль, перевод и карточку услуги. identity-service при следующем запуске снова потребует `BOOTSTRAP_SUPER_ADMIN_EMAIL` и `BOOTSTRAP_SUPER_ADMIN_PASSWORD`. Чтобы изменить только сопоставление портов или переменную окружения, используйте вместо этого `docker compose up -d --no-deps <service>`.

### Другие распространённые проблемы

| Симптом | Причина и решение |
| --- | --- |
| `JWT_SECRET is required` или `POSTGRES_PASSWORD is required` при `docker compose up` | Корневой `.env` отсутствует или пуст. Compose использует защиту `${VAR:?message}` для обязательных значений. |
| identity-service завершается с `No SUPER_ADMIN exists. Set environment variable BOOTSTRAP_SUPER_ADMIN_...` | Свежая база identity. Укажите обе bootstrap-переменные для первого запуска. |
| Вход успешен, но следующий запрос возвращает `401` | Несоответствие политики cookie. На обычном HTTP используйте `APP_COOKIE_SECURE=false` и `APP_COOKIE_SAME_SITE=Lax`; на HTTPS с другого источника используйте `true` и `None`. |
| Шлюз возвращает `503 Service Unavailable` сразу после запуска | Регистрация в Eureka и обновление клиентского кэша занимают до 30 с. Подождите, пока сервис не покажет `UP` на `http://localhost:8761`. |
| `JWT_SECRET` отклонён как слишком короткий | Ключ HMAC-SHA256 должен быть не короче 32 байт. Используйте `openssl rand -base64 48`. |
| Testcontainers завершается ошибкой `client version 1.xx is too old` | Docker Engine 29 требует API 1.44. Файл свойств в `function-catalog-service/src/test/resources` уже фиксирует её; убедитесь, что тесты его подхватывают (запуски с `-Dtest` тоже его читают). |
| `npm run dev` завершается с предупреждением engine | Используйте Node `^20.19.0 \|\| >=22.12.0`. Vite 8 не поддерживает более старые выпуски Node 20. |
| Playwright не находит Chromium | Выполните `npx playwright install chromium` внутри `front/`. |
| Flyway `Migration checksum mismatch` | Был отредактирован уже применённый файл миграции. Никогда не изменяйте применённые миграции; вместо этого добавьте новый `V<n>__*.sql` или восстановите локальную базу с помощью `docker compose down -v`. |
| Кнопка автоматического перевода отсутствует в редакторе | `AZURE_TRANSLATOR_*` пусто. Endpoint возможностей сообщает `available: false`, и UI скрывает действие намеренно. |
| Часть интерфейса осталась на английском | Ключ отсутствует в словаре языка. «Языки» в консоли показывает полноту и пример недостающих ключей; `GET /api/interface-translations/{lang}/missing` перечисляет их. Для языков каталога (en/ru/uz/fr) добавьте перевод в `reference-service/src/main/resources/i18n/interface/<lang>.json`; для остальных включите Azure или впишите перевод через `PUT /api/interface-translations/{lang}`. |
| Язык помечен «Не поддерживается Azure Translator» | У Azure нет кода для этого тега (например `uz-Cyrl`, `az-Cyrl`). Это не ошибка: переводы этого языка вводятся вручную, остальные языки переводятся как обычно. |
| `npm run lint` пишет `hard-coded text …` или `… is not in the interface catalog` | Новая строка выведена мимо `t()` или ключ не добавлен в каталог. Добавьте ключ во все четыре файла каталога (или пометьте технический литерал комментарием `i18n-exempt`). |

---

## 10. Стандарты чистого кода и участия в разработке

**Общие**

- Код — это документация: никаких строчных или блочных комментариев, никакого закомментированного кода, никаких TODO-маркеров. Выражайте намерение через именование и небольшие методы. Повествование ведут README и `docs/`.
- Ноль мёртвого кода: неиспользуемые классы, методы, импорты, props, CSS-классы и feature-флаги удаляются в том же изменении, которое делает их неиспользуемыми.
- Одна ответственность на класс, хук или компонент. Общее поведение выносится, а не копируется (например, `menuStyles.ts` для поверхностей popover, `utils/filters.ts` для фильтров по статусу).
- Каждое изменение поставляется со своими тестами и оставляет `typecheck`, `lint`, `build`, наборы Playwright и Maven зелёными.

**Spring Boot**

- Возможности Java 17: records для DTO и value-объектов, `var` только там, где тип очевиден, switch-выражения, текстовые блоки для SQL в тестах.
- Внедрение через конструктор с помощью Lombok `@RequiredArgsConstructor`; никакого внедрения в поля, никакого `@Autowired`.
- Контроллеры тонкие и валидируют ввод с помощью Jakarta Bean Validation; бизнес-правила живут в сервисах; персистентность остаётся за репозиториями Spring Data.
- Ошибки преобразуются единым `GlobalExceptionHandler` в каждом сервисе в стабильную JSON-форму с `message` и, для ошибок валидации, картой `fieldErrors`, которую фронтенд сопоставляет с полями формы.
- Безопасность stateless: никаких сессий, CSRF отключён для модели cookie + JWT, каждый защищённый endpoint объявляет свою authority.
- Миграции Flyway только добавляются. Применённые файлы миграций никогда не редактируются (контрольные суммы проверяются); изменения схемы получают новый версионированный скрипт.
- Конфигурация вынесена наружу через плейсхолдеры `application.yml`, привязанные к переменным окружения; у секретов никогда нет значений по умолчанию.

**React / TypeScript**

- Включены `strict`, `noUnusedLocals`, `noUnusedParameters` и `noFallthroughCasesInSwitch`; `any` не используется.
- ESLint (`eslint:recommended`, `@typescript-eslint/recommended`, `react-hooks`, `react-refresh`) должен сообщать ноль предупреждений. Экспорты, не являющиеся компонентами, живут в собственных модулях, чтобы Fast Refresh продолжал работать.
- Компоненты — функциональные компоненты с явными интерфейсами props; общий UI живёт в `src/components/ui` и стилизуется дизайн-токенами Tailwind (`bg-surface`, `text-content-muted`, `border-line`, `rounded-control`) вместо сырых цветов палитры.
- Интерактивные оверлеи (селекты, меню, тултипы) строятся на примитивах Radix для навигации с клавиатуры, управления фокусом и ARIA; нативный `<select>` не используется.
- Серверное состояние проходит через TanStack Query с ключами, определёнными в `features/queryKeys.ts`; формы используют react-hook-form со схемами zod из `lib/validation.ts`; серверные ошибки полей применяются с помощью `lib/forms.ts`.
- Каждая видимая пользователю строка разрешается через `t('key')` (счётные тексты — `t('key', { count })`, подстановки — `{name}`); ключ и тексты на en/ru/uz/fr добавляются в `reference-service/src/main/resources/i18n/interface/*.json`, а не в миграции. Необязательный второй аргумент-фолбэк должен совпадать с английским текстом каталога — это проверяет `npm run lint`.
- Доступность — часть критериев готовности: подписанные элементы управления, сводки `aria-live`, возврат фокуса после диалогов, поддержка reduced-motion.

**Git**

- Небольшие сфокусированные коммиты в повелительном наклонении с префиксом области, когда это уместно (`feat(admin-functions): ...`, `fix(identity): ...`, `chore: ...`).
- Никогда не коммитьте `.env`, ключи или результаты сборки; запускайте проверку отслеживания секретов из раздела [Модель безопасности](#7-модель-безопасности) перед push.
- Открывайте pull request'ы в ветку `doniyor` и включайте команды проверки, которые вы запускали.

---

## 11. Структура репозитория

```
ReestrGov/
├── api-gateway/                 Spring Cloud Gateway (routing, CORS)
├── eureka-server/               Netflix Eureka registry
├── identity-service/            Auth, users, roles, permissions, organizations, languages
├── reference-service/           Regions, interface dictionary (i18n/interface/*.json catalog), coverage, interface machine translation
├── function-catalog-service/    Service catalog, editorial workflow, categories, audit, translation
├── front/                       React 18 + Vite 8 SPA, Nginx image, Playwright tests
├── docs/                        Functional specifications (admin functions UI, editorial workflow, machine translation, MFA)
├── docker-compose.yml           Full stack: postgres, eureka, 3 services, gateway, front
├── init-multiple-dbs.sql         Creates the three databases on first PostgreSQL start
├── .env.example                 Template for the local environment file
├── pom.xml                      Maven aggregator for the five Java modules
├── frontend-pages-api.json      Historical page-to-endpoint map used during frontend planning
└── README.md
```

Дополнительное чтение:

- `docs/admin-functions-ui.md` описывает административные экраны карточек услуг.
- `docs/function-editorial-workflow.md` описывает редакционный конечный автомат и его права.
- `docs/public-catalog-enhancements.md` описывает структурированные инструкции, контакты организаций, подбор услуг, сообщения об ошибках, проверку по официальному источнику, сохранение, печать и отправку ссылок.
- `docs/public-search-and-visitor-tools.md` описывает многоязычный поиск (правила нормализации и транслитерации), чек-лист подготовки (сравнение услуг удалено 16.10.2026), публичную историю изменений и отзывы «Была ли информация полезной?».
- `docs/mfa-sessions-and-assignments.md` описывает второй фактор, управление сессиями, ответственных сотрудников карточек и право `FUNCTIONS_ASSIGN`.
- `docs/localization.md` описывает локализацию интерфейса: каталог, синхронизацию, полноту, машинный перевод словарей и сопоставление BCP 47 с кодами Azure.
- `docs/machine-translation.md` описывает фоновую очередь автоматического перевода содержимого (Azure Translator), правила замены переводов, повторы и backfill, включая опубликованные карточки.
- `docs/visitor-questions-guides-exports.md`, `docs/support-chat.md`, `docs/organization-analytics.md` описывают выгрузки Excel/PDF, памятку, страницу поддержки и панель организации.
- `front/README.md` содержит заметки по развёртыванию, специфичные для фронтенда.

---

## 12. Локализация и перевод

Подробное описание (форматы, правила приоритета, API, примеры) — в [docs/localization.md](docs/localization.md) и
[docs/machine-translation.md](docs/machine-translation.md). Кратко:

### 12.1 Тексты интерфейса

| Часть | Где | Что делает |
| --- | --- | --- |
| Каталог (единственный источник ключей) | `reference-service/src/main/resources/i18n/interface/{en,ru,uz,fr}.json` | Плоские объекты `ключ → текст`. `en.json` определяет набор ключей и английский исходный текст; остальные файлы — проверенные переводы. Счётные тексты — группы `ключ_one`/`ключ_other` (+ `_few`/`_many`, если язык их требует) |
| Синхронизация | `InterfaceCatalogSynchronizer` (при старте reference-service, одна транзакция под advisory-lock PostgreSQL) | Добавляет новые ключи и тексты, обновляет тексты каталога, заменяет машинные переводы проверенными, выводит из оборота ключи, удалённые из каталога. **Ручные правки (`source=HUMAN`) не меняются никогда.** Строки, существовавшие до появления происхождения, становятся `SEED`, если совпадают с каталогом, иначе `HUMAN` |
| Происхождение | колонки `translations.source` (`SEED`/`HUMAN`/`MACHINE`), `source_hash`, `updated_at` (миграция `V12`) | `source_hash` — SHA-256 английского текста, для которого написан перевод: при изменении английского машинный перевод считается устаревшим и переводится заново, а ручной помечается «требует проверки» |
| Выдача | `GET /api/interface-translations/{lang}` | Английская основа + каждый уровень тега (`fr-FR` → `fr`, `fr-FR`). Кэш Caffeine, ETag/`Cache-Control: no-cache` (повторная загрузка — 304), gzip |
| Фронтенд | `src/contexts/I18nContext.tsx`, `src/lib/i18n/*` | `t(key)`, `t(key, { count, name })`; язык, `lang`, `dir`, заголовок страницы, даты/числа (Intl) переключаются атомарно, когда словарь получен; медленный ответ не перекрывает более поздний выбор; при ошибке остаётся язык на экране; словарь кэшируется в памяти и `localStorage` |
| Полнота | «Языки» в консоли, `GET /api/interface-translations/overview`, `/{lang}/coverage`, `/{lang}/missing` | Для каждого активного языка: сколько текстов переведено (с учётом родительского языка), кем (люди/каталог/машина), сколько ещё на английском, устаревших машинных и ручных на проверку, код Azure и состояние задачи |
| Машинный перевод словарей | `InterfaceMachineTranslationService` + таблица `interface_translation_jobs` | Раз в 15 минут ставит в очередь активные языки с пробелами; воркер (каждые 2 минуты) переводит только недостающие/устаревшие машинные тексты пакетами до 100 текстов / 20 000 символов, сохраняет каждый пакет сразу (`source=MACHINE`), повторяет при сбое (1 → 5 → 15 → 60 мин → 6 ч, при 429 не меньше 5 мин), после `max-attempts` — `FAILED` до явного повтора кнопкой «Повторить» или `POST /api/interface-translations/{lang}/machine-translation`. Плейсхолдеры `{…}`, названия продукта, URL и e-mail защищены (`notranslate`); ответ с изменённым плейсхолдером отбрасывается. Никогда не вызывается при загрузке страницы |

### 12.2 Содержимое из базы

| Сущность / поле | Перевод | Как заполняется |
| --- | --- | --- |
| Услуга: название, описание, шесть полей инструкций | да (`*_translations`, `{код: {text, source}}`) | Ручной редактор или фоновый машинный перевод черновиков; опубликованная карточка — через черновую редакцию (`backfill?includePublished=true` создаёт её сам) |
| Категория: название | да | Ручной ввод или фоновый машинный перевод |
| Организация: официальное название, описание | да, **только вручную** (`PUT /api/organizations/{id}/translations`, «Переводы» в списке организаций) | Официальные названия не переводятся машиной |
| Права и группы прав | ключи каталога `permission.<CODE>`, `permission.category.*` | Код права показывается как идентификатор |
| Сообщения об ошибках сервисов | английский текст сервера показывается только в английском интерфейсе | Другие языки получают переведённое сообщение по коду ответа |
| Не переводятся | старое поле `requirements`, детали документов (срок, примечания), ссылки, телефоны, адреса, имена людей, тексты посетителей и сотрудников в поддержке, коды и идентификаторы | — |

Карточки, созданные до настройки Azure, уже стоят в очереди (`PENDING`) и переводятся после её настройки; остальное
ставит в очередь backfill (раздел 13).

### 12.3 BCP 47 и коды Azure Translator

Приложение хранит полный тег BCP 47 (identity-service — канонический регистр, `fr-FR`; переводы содержимого — в
нижнем регистре, `fr-fr`, как и раньше). Только для запроса к Azure тег сопоставляется с кодом провайдера
(`AzureLanguageCodes`, одинаковый класс в reference-service и function-catalog-service), и только если язык есть в
актуальном списке Azure (`GET /languages?scope=translation`, без ключа, кэш 24 ч, при недоступности — встроенный
список):

| Тег приложения | Код Azure |
| --- | --- |
| `fr`, `fr-FR`, `fr-BE` / `fr-CA` | `fr` / `fr-CA` |
| `pt`, `pt-BR` / `pt-PT`, `pt-AO` | `pt` / `pt-PT` |
| `zh`, `zh-CN`, `zh-Hans` / `zh-TW`, `zh-HK`, `zh-Hant` | `zh-Hans` / `zh-Hant` |
| `sr`, `sr-Cyrl` / `sr-Latn` | `sr-Cyrl` / `sr-Latn` |
| `mn` / `mn-Mong` | `mn-Cyrl` / `mn-Mong` |
| `uz`, `uz-Latn`, `uz-UZ` | `uz` |
| `uz-Cyrl`, `uz-Arab`, `az-Cyrl`, `pa-Arab`, `bs-Cyrl`, `nn` … | **не поддерживается** (Azure переводит на этот язык только в другой письменности) |
| `tl`, `no`, `ckb`, `ku` | `fil`, `nb`, `ku` (центральный курдский), `kmr` (курманджи) |

Неподдерживаемые языки показываются в консоли и в заметке задачи и никогда не мешают переводу на поддерживаемые: в
одном запросе к Azure только поддерживаемые коды, одинаковые коды (`fr` и `fr-FR`) объединяются, а если Azure всё
же отвечает 400 на запрос с несколькими языками, языки переводятся по одному и отказавший помечается
неподдерживаемым.

### 12.4 Как добавить или включить язык

1. **Язык с проверенным переводом (как en/ru/uz/fr):** создайте `reference-service/src/main/resources/i18n/interface/<тег>.json`
   со всеми ключами `en.json` (и формами множественного числа языка), выполните `cd front && npm run i18n:check`,
   пересоберите reference-service и добавьте язык в консоли («Языки» → поиск → «Добавить»).
2. **Любой другой язык:** добавьте его в консоли. Пока переводов нет, недостающие тексты показываются на английском;
   при настроенном Azure они заполняются в фоне (в течение ~15 минут или сразу кнопкой «Перевести недостающие
   тексты»), затем их можно исправить через `PUT /api/interface-translations/{тег}` — правка становится `HUMAN` и
   больше не заменяется. Если язык не поддерживается Azure, консоль сообщает об этом.
3. **Региональный вариант** (`fr-CA`, `pt-BR`) наследует родительский язык: переводить нужно только отличия.
4. **Новый ключ в коде:** используйте `t('новый.ключ')`, добавьте ключ во все четыре файла каталога, запустите
   `npm run lint`. После развёртывания синхронизатор добавит его, а машинный перевод дозаполнит остальные активные
   языки.

---

## 13. Развёртывание изменений локализации и backfill

Изменения только добавляют: миграция reference-service `V12` (колонки происхождения, таблица задач, деактивация
устаревших ключей) и никаких изменений схемы в других сервисах. Данные не удаляются.

1. `.env` (не коммитится): при желании заполните `AZURE_TRANSLATOR_ENDPOINT`, `AZURE_TRANSLATOR_KEY`,
   `AZURE_TRANSLATOR_REGION` — теперь их читают и function-catalog-service, и reference-service (`docker-compose.yml`
   передаёт их обоим). `INTERFACE_TRANSLATION_ENABLED=true` по умолчанию.
2. Пересоберите и перезапустите: `docker compose up -d --build reference-service function-catalog-service identity-service front`.
   При старте reference-service применит `V12` и синхронизирует каталог; в логе появится строка
   `Interface catalog synchronized: …` (сколько добавлено, обновлено и сколько прежних значений сохранено как ручные).
3. Откройте «Языки» в консоли: каждый активный язык должен показывать полноту; en/ru/uz/fr — 100 %.
4. Содержимое: от имени `SUPER_ADMIN` (или с `FUNCTIONS_EDIT` + `FUNCTIONS_TRANSLATIONS_EDIT`) нажмите «Поставить
   недостающие переводы в очередь» на странице услуг (или `POST /api/functions/translations/backfill?limit=50`;
   с `includePublished=true` для опубликованных карточек создаются черновые редакции). Повторяйте, пока ответ не
   перестанет сообщать о новых записях. Вызов идемпотентен и сам Azure не вызывает.
5. Переведённые черновые редакции опубликованных карточек проходят обычную проверку и публикацию; опубликованный
   текст до этого не меняется.
6. Фронтенд на Netlify: `npm run build` в `front/` как раньше; дополнительных переменных не требуется.

Пауза: `INTERFACE_TRANSLATION_ENABLED=false` и `CATALOG_TRANSLATION_ENABLED=false` приостанавливают фоновый перевод
без потери очереди. Откат: Прежняя версия reference-service продолжит работать с новыми колонками (они допускают
`NULL`), а машинные переводы останутся обычными строками словаря.

