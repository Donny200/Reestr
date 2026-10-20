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
12. [Изменения от 15.10.2026: автоперевод, MFA, очистка](#12-изменения-от-15102026-автоперевод-mfa-очистка)
13. [Изменения от 16.10.2026: отчёты Excel/PDF, вопросы посетителей, памятка, удаление сравнения](#13-изменения-от-16102026-отчёты-excelpdf-вопросы-посетителей-памятка-удаление-сравнения)
14. [Изменения от 16.10.2026 (2): страница поддержки с постоянной перепиской](#14-изменения-от-16102026-2-страница-поддержки-с-постоянной-перепиской)

---

## 1. Краткий обзор

ReestrGov — это реестр государственных услуг («функций»), предоставляемых государственными организациями. Граждане просматривают публичный многоязычный каталог организаций и услуг без входа в систему: структурированные инструкции, контакты организаций, пошаговый подбор услуги, сохранение услуг в браузере, печать и PDF-памятка по услуге, отправка ссылки, страница «Поддержка» с постоянной личной перепиской (без учётной записи; в ней же — сообщения о неверной информации). Сотрудники входят в административную консоль для управления организациями, учётными записями сотрудников, ролями и правами, языками интерфейса и переводами, а также для ведения редакционного процесса карточек услуг (черновик, проверка, публикация, деактивация, повторная активация) с возможностью машинного перевода через Azure Translator.

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
| `reference-service` | 8084 (только внутри Compose) | Справочные данные: регионы, словари переводов интерфейса для каждого языка и ключи переводов с отчётами о покрытии и пробелах. Кэширует запросы с помощью Caffeine. | `reference_service_db` |
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
| identity-service | `/api/auth` (login, refresh, logout, me, change-password, `password-reset/options`, `password-reset/request`, `password-reset/complete`), `/api/user` (вкл. `POST /{id}/password-reset` только для SUPER_ADMIN), `/api/admin/moderators`, `/api/admin/org-admins`, `/api/organizations` (вкл. `/{id}/verify`), `/api/public/organizations`, `/api/roles` (вкл. `/assign`, `/{id}/permissions`), `/api/permissions`, `/api/languages` (вкл. `/catalog`) |
| reference-service | `/api/regions`, `/api/interface-translations/{languageCode}` (а также `/exact`, `/missing`, `/coverage`), `/api/translation-keys` |
| function-catalog-service | `/api/functions` (публичный список и детали, `/admin`, `/{id}/admin`, `/pending-review`, `/{id}/submit-for-review`, `/{id}/withdraw`, `/{id}/publish`, `/{id}/reject`, `/{id}/reactivate`, `POST /{id}/revisions`, `DELETE /{id}/revision`, `/{id}/requirements`, `/{id}/translations`, `/{id}/translate`, `/{id}/verify`, `/{id}/audit`, `/import`, `/translation-capabilities`, публичный `GET /{id}/guide.pdf`), `/api/functions/categories`, `/api/reports` (анонимный `POST`, `GET`, `GET /{id}`, `GET /{id}/history`, `PUT /{id}/status`, `GET /export?format=CSV\|XLSX\|PDF`), `/api/analytics` (анонимный `POST /events`, `GET /summary`, `/engagement`, `/engagement/export`, `/report/export?format=XLSX\|PDF`, `/quality-queue`, `/quality-queue/export`, `/reminders`, `POST /reminders/{id}/acknowledge`), `/api/help` (посетитель по HttpOnly-cookie или учётной записи: `GET /options`, `GET /support`, `/support/updates`, `/support/unread`, `POST /support/messages`, `POST /support/read`, `PUT /support/contact`, `POST /support/recovery-code`, `POST /support/recover`, `DELETE /support/session`; для сотрудников `GET /inbox`, `/inbox/unread`, `/inbox/{id}`, `POST /inbox/{id}/read`, `POST /inbox/{id}/replies`, `PUT /inbox/{id}/status`, `GET /inbox/{id}/candidates`, `PUT /inbox/{id}/assignee`) |

Каждый сервис предоставляет Swagger UI по адресу `http://localhost:<port>/swagger` и документ OpenAPI по адресу `/v3/api-docs`.

### 2.4 Фронтенд-приложение

SPA находится в `front/` и организовано по функциональным областям:

- `src/components/ui` — дизайн-система (Button, Card, DataTable, Field, Input, Select, Modal, DropdownMenu, Tooltip, Badge, Skeleton, Toolbar, ThemeToggle), построенная на токенах Tailwind, примитивах Radix и class-variance-authority.
- `src/features/*` содержит хуки TanStack Query, схемы zod и диалоги для каждого домена (functions, organizations, staff, roles, legacy users, languages, reference).
- `src/pages` содержит публичные страницы (`/`, `/organizations/:id`, `/functions/:id`, `/finder`, `/saved`, `/support`; старые `/help` и `/help/conversation` перенаправляют на `/support`), `/login` и административную консоль (`/admin`, `/admin/functions`, `/admin/functions/new`, `/admin/functions/:id`, `/admin/reports`, `/admin/questions`, `/admin/organization-dashboard`, `/admin/organizations`, `/admin/moderators`, `/admin/org-admins`, `/admin/roles`, `/admin/languages`, `/admin/users`, `/settings/security`).
- `src/contexts` предоставляет аутентификацию, тему (светлая, тёмная, системная) и i18n. Словарь UI загружается из reference-service для каждой локали, и каждая подпись разрешается через `t('key', 'fallback')`.
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
| `MFA_ENABLED` | нет (по умолчанию `false`) | identity-service | Второй фактор (TOTP) для `SUPER_ADMIN` и `ORG_ADMIN`. **В этом окружении отключён конфигурацией**: при `false` код не запрашивается, ключ не нужен, таблицы и данные enrollment сохраняются. `true` включает MFA снова; см. раздел 12 и [docs/mfa-sessions-and-assignments.md](docs/mfa-sessions-and-assignments.md) |
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
| `AZURE_TRANSLATOR_ENDPOINT` | нет | function-catalog-service | Endpoint Azure Translator (например `https://api.cognitive.microsofttranslator.com`). Пока любое из трёх значений пусто, автоматический перевод не выполняется: содержимое сохраняется и ждёт в очереди, редактор видит «Автоматический перевод недоступен» |
| `AZURE_TRANSLATOR_KEY` | нет | function-catalog-service | Ключ ресурса Azure Translator. Читается только из окружения/`.env`, никогда не логируется и не попадает в ответы API |
| `AZURE_TRANSLATOR_REGION` | нет | function-catalog-service | Регион ресурса Azure Translator |
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
npm run lint         # ESLint, zero warnings expected
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
- **Второй фактор и сессии.** Второй фактор отключён конфигурацией (`MFA_ENABLED=false` по умолчанию в `application.yaml`, `docker-compose.yml` и `.env.example`); код, миграция `V5` и таблицы сохранены, включение описано в разделе 12. При `MFA_ENABLED=true` `SUPER_ADMIN` и `ORG_ADMIN` после пароля подтверждают вход TOTP-кодом (или кодом восстановления); до этого браузер держит только одноразовый challenge-cookie с путём `/api/auth/mfa`, а access/refresh не выдаются. Секреты шифруются `MFA_ENCRYPTION_KEY`, коды восстановления хранятся как хэши. Пользователь видит и завершает свои сессии (`/api/auth/sessions`), `SUPER_ADMIN` — сессии любого пользователя с аудитом; access-токен несёт `sid` и отклоняется identity-service сразу после отзыва (см. [docs/mfa-sessions-and-assignments.md](docs/mfa-sessions-and-assignments.md)).
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
| reference-service | Модульные тесты и тесты веб-слоя | нет |
| function-catalog-service | Модульные тесты, `CatalogPostgresTest`, `CatalogEnhancementsPostgresTest`, `ReportWorkflowPostgresTest`, `OrganizationInsightsPostgresTest`, `OrganizationDashboardPostgresTest`, `FunctionRevisionPostgresTest`, `PublicHistoryAssignmentFeedbackPostgresTest`, `MachineTranslationPostgresTest`, `OrganizationReportExportPostgresTest`, `HelpConversationPostgresTest`, `ServiceGuidePostgresTest` (Testcontainers PostgreSQL 16) и `AdminFunctionsBrowserIT` (браузерный сценарий Playwright против реального Spring-контекста) | Docker daemon |
| api-gateway | `ClientAddressFilterTest` (доверенные прокси, перезапись `X-Real-IP`, разбор CIDR) | нет |
| eureka-server | Тестов нет; `mvn test` только компилирует модуль | нет |

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
(cd identity-service && mvn -q test)
(cd reference-service && mvn -q test)
(cd function-catalog-service && mvn -q test)                                           # needs Docker
(cd function-catalog-service && mvn -q test -Dtest='!*PostgresTest,!AdminFunctionsBrowserIT')   # without Docker
```

`function-catalog-service/src/test/resources/docker-java.properties` фиксирует версию Docker API 1.44 для клиента Testcontainers, поскольку Docker Engine 29 требует её.

### Фронтенд

```shell
cd front
npx playwright install chromium   # once
npm run test                      # UI suite against route mocks (all specs listed in playwright.config.ts, incl. checklist, support and exports)
npm run test:backend              # real-backend suite, driven by AdminFunctionsBrowserIT
```

UI-набор запускает Vite на порту 5183 внутри процесса (`tests/start-vite.mjs`) и мокирует каждый вызов `/api/**` в `tests/fixtures.ts`, загружая словарь UI из миграций reference-service, чтобы подписи совпадали с production. Набор с реальным бэкендом требует `CATALOG_TEST_URL` и `CATALOG_TEST_JWT`, которые предоставляет Java-интеграционный тест.

### Критерии готовности

```shell
cd front && npm run typecheck && npm run lint && npm run build && npm run test
```

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
- Каждая видимая пользователю строка разрешается через `t('key', 'English fallback')` и засевается в миграции reference-service для всех поддерживаемых языков.
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
├── reference-service/           Regions, interface translations, translation keys
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
- `docs/machine-translation.md` описывает фоновую очередь автоматического перевода (Azure Translator), правила замены переводов, повторы и backfill.
- `front/README.md` содержит заметки по развёртыванию, специфичные для фронтенда.

---

## 12. Изменения от 15.10.2026: автоперевод, MFA, очистка

### Что изменилось

**Автоматический перевод (function-catalog-service, front).** Раньше Azure вызывался только по клику редактора или при
переходе на страницу карточки сразу после создания; импорт, сиды и API оставляли карточки без переводов, а сиды
обращались к Azure при каждом запуске для жёстко заданных `en`/`uz`. Теперь:

- создание и изменение карточки (`POST/PUT /api/functions`), CSV-импорт, редакционные сиды и категории ставят запись
  в очередь `translation_jobs` (миграция `V9__translation_jobs.sql`, колонка `function_categories.source_language`);
- фоновый воркер (`catalog.translation.*`: первый тик через 30 с, далее каждые 20 с, по 5 задач) переводит название,
  описание, все поля инструкций и названия категорий **во все активные языки** из identity-service
  (`GET /api/languages`), исключая исходный язык; жёстко заданного списка языков больше нет;
- заполняются только отсутствующие переводы; ручные и уже существующие машинные переводы не заменяются. Машинный
  перевод заменяется только когда редактор меняет исходный текст поля или нажимает «Перегенерировать»;
- оригинал сохраняется всегда; при тайм-ауте, 429, 5xx или недоступности identity-service задача ждёт с отсрочкой
  (1 мин → 5 мин → 15 мин → 1 ч → 6 ч), после 8 попыток — `FAILED` с кнопкой «Поставить в очередь снова»
  (`POST /api/functions/{id}/translation-job`). Перезапуск сервиса не создаёт повторных вызовов провайдера;
- `POST /api/functions/translations/backfill?organizationId=&limit=` — ограниченный, идемпотентный backfill только для
  черновиков с недостающими языками; при старте массовых вызовов Azure нет;
- редактор карточки показывает состояние задачи (в очереди / выполняется / завершена / не удалась / пропущена),
  следующую попытку, число попыток и заметку, и опрашивает запись каждые 10 с, пока задача открыта;
- один HTTP-запрос к Azure на набор недостающих языков (до 25 текстов), 429 отличается от прочих ошибок.
  Подробности: [docs/machine-translation.md](docs/machine-translation.md).

**MFA (identity-service).** Второй фактор отключён конфигурацией: `MFA_ENABLED` по умолчанию `false` в
`MfaProperties`, `application.yaml`, `docker-compose.yml` и `.env.example`. При отключённом MFA ключ шифрования не
требуется, сервис стартует без него, вход выдаёт полную сессию сразу после пароля, `GET /api/auth/mfa` отвечает
`enabled=false`. Код MFA, миграция `V5__mfa_and_sessions.sql`, таблицы `user_mfa`, `mfa_recovery_codes`,
`mfa_challenges`, `security_audit_events` и уже существующие enrollment не удалялись.

**Очистка.** Удалены из репозитория `.section3-git/` (21 файл), `.section3-work/` (315 файлов), `.section3.index`,
`section3.patch` и `.vscode/` (одна настройка Postman). Это архивный снимок более ранней, не применённой итерации
безопасности (интроспекция токенов в шлюзе, CSRF-фильтры): ни один файл не участвовал в сборке, ссылок из кода нет,
worktree или вложенных `.git` не было, патч не накладывается на текущий код. Уникальное содержимое остаётся в истории
Git (коммиты `fb80438`, `e4435bf`): `git show fb80438:.section3-work/<путь>`. `frontend-pages-api.json` оставлен —
на него ссылается `identity-service/API_USER_MIGRATION.md`. Оптимизация: `ActiveLanguageClient` больше не роняет
административные ответы, когда identity-service не зарегистрирован в Eureka (использует кэш/пустой результат).

### Как запустить

1. `cp .env.example .env`, заполните `POSTGRES_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_SUPER_ADMIN_*`
   (раздел 4). `MFA_ENABLED=false` уже стоит; ключ MFA не нужен.
2. Для автоперевода впишите `AZURE_TRANSLATOR_ENDPOINT`, `AZURE_TRANSLATOR_KEY`, `AZURE_TRANSLATOR_REGION` из портала
   Azure (ресурс Translator). `.env` игнорируется Git; значения не должны попадать в коммиты, логи и тикеты.
3. `docker compose up --build -d` (раздел 5) или запуск из IDE (раздел 6). Существующие тома не трогаются: Flyway
   применяет `V9` каталога и `V9` reference-service поверх текущих данных.
4. После старта воркер начнёт переводить новые/изменённые карточки. Для старых черновиков без переводов выполните
   один раз от имени `SUPER_ADMIN` (или редактора с `FUNCTIONS_EDIT` + `FUNCTIONS_TRANSLATIONS_EDIT`):
   `POST /api/functions/translations/backfill?limit=50` — повторяйте, пока `queued` не станет 0.
5. Чтобы включить MFA снова: `MFA_ENABLED=true` и `MFA_ENCRYPTION_KEY` = тот же ключ, которым были зашифрованы
   существующие enrollment (иначе их нельзя расшифровать; сначала сбросьте их через `POST /api/user/{id}/mfa/reset`
   или удалите строки `user_mfa`). Для новой установки: `openssl rand -base64 32`. При необходимости задайте
   `MFA_ENROLLMENT_DEADLINE`; затем пересоздайте только identity-service: `docker compose up -d --build identity-service`.

### Проверки, выполненные 15.10.2026

| Проверка | До изменений | После изменений |
| --- | --- | --- |
| `identity-service` `mvn test` | 156 тестов, 0 ошибок | 156 тестов, 0 ошибок |
| `reference-service` `mvn test` | 24, 0 ошибок | 24, 0 ошибок |
| `function-catalog-service` `mvn test` (Testcontainers PostgreSQL 16) | 206, 0 ошибок | 215, 0 ошибок (новые: `MachineTranslationPostgresTest` ×8, пакетный запрос и 429 в `AzureTranslationClientTest`) |
| `api-gateway`, `eureka-server` `mvn test` | компиляция, тестов нет | компиляция, тестов нет |
| `front`: `npm run typecheck`, `npm run lint` | без ошибок | без ошибок, 0 предупреждений |
| `front`: `npm run build` | успешно | успешно |
| `front`: `npm run test` (Playwright, Chromium) | 56 сценариев | 58 сценариев, 0 ошибок |
| `docker compose config` | — | валиден (`MFA_ENABLED=false`, `CATALOG_TRANSLATION_ENABLED` передаётся в каталог) |

Живой запуск (jar-файлы eureka-server, identity-service и function-catalog-service против одноразового контейнера
PostgreSQL 16 на порту 55432, без касания существующих томов; контейнер удалён после проверки):

- identity-service стартовал **без `MFA_ENCRYPTION_KEY`**, Flyway дошёл до `V5`; вход `SUPER_ADMIN` вернул 200 и cookie
  сессии (не 202 с challenge), `/api/auth/me` → `mfa.required=false`, `/api/auth/mfa` → `enabled=false`; смена
  обязательного пароля прошла;
- function-catalog-service применил `V9`, воркер поставил в очередь 8 редакционных черновиков и 7 категорий и перевёл
  их в `en`/`uz` (активные языки identity-service: `en`, `ru`, `uz`; исходный `ru` исключён). Первая попытка не
  удалась из-за ошибки самой заглушки (она не читала chunked-тело запроса), после чего воркер, как и задумано,
  повторил задачи через 1 минуту и завершил их;
- созданная через API карточка (`en`, с инструкциями): ручной перевод названия на `ru`, добавленный до тика воркера,
  сохранён как `human`; `uz` и остальные поля заполнены как `machine` двумя запросами к провайдеру (1 и 4 текста);
  запись аудита `system:machine-translation`; `machineTranslation.status=DONE` в административном ответе;
- карточка, для которой провайдер отвечает 429: `status=PENDING`, `attempts=1`, следующая попытка через 5 минут,
  `lastError` «rate limit»; `POST …/translation-job` → 202 и обнулённый счётчик; `GET /api/functions/{id}` черновика —
  404, без полей перевода; ключ провайдера в логах не встречается.

**Ограничение.** Провайдером в живом запуске служила локальная заглушка протокола Azure Translator
(`/translate?api-version=3.0`), потому что настоящих учётных данных в окружении и `.env` нет. Реальный вызов Azure не
проверялся. Чтобы проверить его, задайте три переменные `AZURE_TRANSLATOR_*`, создайте один тестовый черновик и
убедитесь через `GET /api/functions/{id}/admin`, что `machineTranslation.status=DONE`, а `missingLanguages` пуст; затем
удалите черновик или оставьте его неопубликованным.

### Оставшиеся ручные шаги

- Заполнить `AZURE_TRANSLATOR_ENDPOINT`, `AZURE_TRANSLATOR_KEY`, `AZURE_TRANSLATOR_REGION` в `.env` рабочего окружения
  и пересоздать function-catalog-service (`docker compose up -d --build function-catalog-service`).
- Запустить backfill для существующих черновиков (п. 4 выше).
- Проверить, что в CI/на сервере после `git pull` выполняется `docker compose up --build`, чтобы применились новые
  миграции и образы.
- Решить, когда включать MFA в production, и сохранить ключ шифрования в менеджере секретов.

### Продолжение от 15.10.2026: сборка, безопасность, миграции

**Причина сбоя сборки.** Коммиты `fb61744`/`132cbd2` включили изменённые файлы (`OrgFunctionService`,
`OrgFunctionController`, `FunctionAutoTranslationController` и др.), которые ссылаются на `TranslationTarget`,
`MachineTranslationQueue`, `MachineTranslationSummary`, `TranslationBackfillResponse`, но 14 новых файлов очереди
перевода (сущности, репозиторий, сервисы, миграции `V9`, тест, документ) остались неотслеживаемыми. Чистая сборка из
Git не находила символы. Файлы добавлены в индекс (`git add`); их нужно закоммитить вместе с остальными изменениями.

**Безопасность.**
- Вход: лимит на клиента и на учётную запись (`app.login.*`, см. раздел 7), 429 с общим сообщением.
- `POST /api/user/{id}/mfa/reset` и `POST /api/user/{id}/password-reset` больше не принимают собственный id.
- Шлюз: `ClientAddressFilter` перезаписывает `X-Real-IP` для всех вызывающих, кроме `GATEWAY_TRUSTED_PROXIES`.
- Compose: Eureka, identity, reference и каталог больше не публикуются на хосте; шлюз и PostgreSQL — только на
  `127.0.0.1`; сеть `172.28.0.0/16` со статическим адресом nginx `172.28.0.10`.
- `MFA_ENABLED=false` остаётся значением по умолчанию; код и миграция `V5` сохранены.

**Миграции.**
- identity-service `V6__baseline_schema_and_indexes.sql`: `V1`–`V5` были обёрнуты в `to_regclass()` и на пустой базе
  ничего не делали, схему создавал Hibernate без индексов. `V6` создаёт все таблицы `IF NOT EXISTS` (с теми же
  именами ограничений, что генерирует Hibernate) и безусловно создаёт индексы (`refresh_tokens_session_idx`,
  `refresh_tokens_user_idx`, `security_audit_events_user_idx`, `password_reset_tokens_user_idx`, `mfa_*_user_idx`,
  `users_role_idx`, `user_organizations_organization_idx`, индексы по `expires_at`). Проверено на одноразовых базах:
  обновление базы, созданной старой версией (таблицы есть, индексов нет) добавило 11 индексов и сохранило данные;
  пустая база получила таблицы и индексы без дублирующих ограничений.
- function-catalog-service `V9__translation_jobs.sql` (очередь перевода, `function_categories.source_language`) и
  `V10__list_indexes.sql` (`audit_logs(entity_type, entity_id, performed_at)`, `org_functions(organization_id)`,
  `engagement_daily_counts(activity_date)`).

**Производительность.** Списки пользователей identity-service загружают роль, права и организации одним запросом
(`@EntityGraph`) вместо N+1; `GET /api/user` и `GET /api/functions/admin` принимают необязательные `page`/`size`
(ответ — та же страница-массив плюс заголовок `X-Total-Count`); без параметров контракт не изменился.

**Инициализация баз.** `init-multiple-dbs.sh` заменён на `init-multiple-dbs.sql`: на bind-mount с macOS образ
PostgreSQL не мог выполнить скрипт (`bad interpreter: Permission denied`), и базы не создавались; SQL-файл не требует
прав на выполнение. Для уже инициализированного тома без баз см. раздел 9.

**Проверки, выполненные 15.10.2026 (после изменений).**

| Проверка | Результат |
| --- | --- |
| `identity-service` `mvn test` | 161 тестов, 0 ошибок (новые: `LoginProtectionTest` ×3, `LoginAttemptGuardTest`, самозащита в `MfaServiceTest`/`PasswordResetServiceTest`) |
| `function-catalog-service` `mvn test` (Testcontainers PostgreSQL 16) | 216 тестов, 0 ошибок (новый: постраничный список в `MachineTranslationPostgresTest`) |
| `reference-service` `mvn test` | 24, 0 ошибок |
| `api-gateway` `mvn test` | 3 (`ClientAddressFilterTest`), 0 ошибок |
| `eureka-server` `mvn test` | компиляция, тестов нет |
| `front`: typecheck, lint, build, Playwright | без ошибок, 58 сценариев |
| Миграция identity `V6` | одноразовая база, созданная старой версией (таблицы без индексов): `V6` добавила 11 индексов, данные сохранены; пустая база: таблицы и индексы созданы, ограничения без дублей |
| `docker compose config` | валиден |
| `docker compose up --build -d` (одноразовые секреты через переменные окружения, не через `.env`) | 7 контейнеров запущены; `/healthz`, `/api/public/organizations`, `/api/functions`, вход `SUPER_ADMIN` через nginx — 200; identity `V1`–`V6`, каталог `V1`–`V10`, reference `V9` применены; индексы на месте; 15 задач перевода в `PENDING` (ключи Azure не заданы), содержимое сохранено |
| Защита входа через nginx | 10 неверных паролей → `401`, 11-й → `429` |
| Подмена `X-Real-IP` напрямую в шлюз (`127.0.0.1:8082`, 21 разный адрес и e-mail) | 20 × `401`, затем `429`: шлюз подставил реальный адрес, лимит на клиента сработал |
| Опубликованные порты | только `3000`, `127.0.0.1:8082`, `127.0.0.1:5433`; Eureka и сервисы недоступны с хоста |

На этой машине порт `127.0.0.1:5433` был занят устаревшей привязкой Docker Desktop (ни один процесс и контейнер его
не держал), поэтому для проверки PostgreSQL временно публиковался на `127.0.0.1:55436` через override-файл вне
репозитория; `docker-compose.yml` не менялся. Том `reestrgov_pgdata` создан этой проверкой (ранее томов проекта на
машине не было) и сохранён; контейнеры остановлены `docker compose down` без `-v`. Учётные данные Azure Translator в
окружении по-прежнему отсутствуют: живой вызов Azure не выполнялся, очередь ждёт `AZURE_TRANSLATOR_ENDPOINT`,
`AZURE_TRANSLATOR_KEY`, `AZURE_TRANSLATOR_REGION`.

---

## 13. Изменения от 16.10.2026: отчёты Excel/PDF, вопросы посетителей, памятка, удаление сравнения

Подробности, права, конфигурация и сценарий ручной проверки: [docs/visitor-questions-guides-exports.md](docs/visitor-questions-guides-exports.md).

**Что добавлено.**

- **Отчёты в Excel и PDF.** Панель организации выгружает книгу `.xlsx` (листы «Сводка», «Активность по услугам»,
  «Активность по времени», «Проблемы качества», «Сообщения посетителей») и печатный PDF с теми же фильтрами (период,
  организация, категория); экран сообщений посетителей — Excel/PDF рядом с прежним CSV. Только реально собираемые
  данные; права и ограничение по организации проверяются на бэкенде; контакты посетителей, тексты сообщений и данные
  сотрудников не выгружаются. Новые зависимости function-catalog-service: Apache POI 5.4.1, OpenPDF 2.0.5
  (+ `openpdf-fonts-extra` для кириллицы), `spring-boot-starter-mail`.
- **Вопросы посетителей без учётной записи.** `/help` и кнопка «Задать вопрос» на странице услуги; вопрос об услуге
  попадает в очередь её организации, общий — к `SUPER_ADMIN`. Посетитель читает ответ по личной ссылке
  `/help/conversation#<токен>` (256 бит, в базе только хэш, не по id). Входящие для сотрудников — `/admin/questions`
  со статусами «Новый», «В работе», «Отвечен», «Закрыт» и назначением ответственного. Это не онлайн-чат; срок ответа не
  обещается. E-mail необязателен и используется, только если настроена доставка.
- **PDF-памятка по услуге** на языке интерфейса (`GET /api/functions/{id}/guide.pdf?lang=…`, без входа) с чек-листом
  подготовки; пустые разделы опускаются, внешняя ссылка не требуется. Печать страницы сохранена.
- **Сравнение услуг удалено**: кнопки, страница и маршрут `/compare`, хранилище, ссылки, подписи и тесты. Сохранённые
  услуги, чек-лист, печать, публичный каталог и аналитика не затронуты.

**Миграции (аддитивные).** function-catalog `V11` (таблицы `help_conversations`, `help_messages`), identity `V7`
(права `VISITOR_QUESTIONS_VIEW`/`VISITOR_QUESTIONS_REPLY` для существующих ролей), reference `V10` (подписи en/ru/uz/fr и
удаление строк `compare.*`/`nav.compare`). Таблицы и тома не удаляются.

**Новая конфигурация (необязательная).** `HELP_MAIL_ENABLED`, `HELP_MAIL_FROM` плюс уже существующие `PUBLIC_BASE_URL`
и `SPRING_MAIL_*` (теперь передаются и в function-catalog-service, см. `docker-compose.yml` и `.env.example`).
Маршрут шлюза `function-catalog-service` дополнен `/api/help/**`.

**Как запустить и проверить.** Как обычно (раздел 5): `docker compose up --build -d`; миграции применяются при старте.
Тесты: `(cd function-catalog-service && mvn test)` (нужен Docker), `(cd identity-service && mvn test)`,
`(cd reference-service && mvn test)`, `(cd api-gateway && mvn test)`, `cd front && npm run typecheck && npm run lint &&
npm run build && npm run test`.

**Проверки, выполненные 16.10.2026.**

| Проверка | Результат |
| --- | --- |
| `function-catalog-service` `mvn test` (Testcontainers PostgreSQL 16) | 237 тестов, 0 ошибок (новые: `OrganizationReportExportPostgresTest` ×7, `HelpConversationPostgresTest` ×7, `ServiceGuidePostgresTest` ×5, `HelpAccessTokensTest` ×3) |
| `identity-service` `mvn test` | 161, 0 ошибок (`DataInitializerTest` обновлён под новые права) |
| `reference-service` / `api-gateway` `mvn test` | 24 / 3, 0 ошибок |
| `front`: typecheck, lint, build, Playwright UI | без ошибок, 66 сценариев (новые `checklist.spec.ts` с проверкой удаления сравнения, `visitor-help.spec.ts`, `exports.spec.ts`) |
| Миграции на одноразовой PostgreSQL 16 | reference `V1`–`V10` применены, строк `compare.*` не осталось; identity `V7` идемпотентна и выдаёт права существующим ролям |
| Стек `docker compose` (отдельный проект `reestrgov-verify`, одноразовые секреты в окружении, порт PostgreSQL переопределён файлом вне репозитория из-за занятого `127.0.0.1:5433`) | миграции каталога `V11`, identity `V7`, reference `V10` применены; через nginx: публичная карточка и PDF-памятка на русском без входа, вопрос посетителя → ответ `SUPER_ADMIN` виден по токену, сотрудник другой организации получает `403`, Excel (русские листы) и PDF выгружаются, чужая организация → `403`, аноним → `403`, `/compare` → 404. Остановлен `docker compose down` без `-v` |

Ограничения: e-mail-уведомления проверены только тестами с подменой отправителя — реальный SMTP в окружении не
настроен; SMS-канала в проекте нет. Срок ответа на вопросы не настраивается и посетителям не обещается.

---

## 14. Изменения от 16.10.2026 (2): страница поддержки с постоянной перепиской

Форма разового вопроса с личной ссылкой (раздел 13) заменена страницей **«Поддержка»** (`/support`, пункт меню с
счётчиком непрочитанных ответов): у каждого посетителя одна личная переписка, в которой он пишет со временем, видит
ответы и время сообщений. Подробности: [docs/support-chat.md](docs/support-chat.md).

- **Без учётной записи.** Анонимный посетитель получает при первом сообщении непрозрачный случайный секрет в cookie
  `reestr_support` (HttpOnly, SameSite=Strict, путь `/api/help`, `Secure` при `APP_COOKIE_SECURE=true`); в базе
  только SHA-256. IP-адрес и «отпечаток» браузера идентичностью не служат. Вошедший пользователь ведёт переписку от
  своей учётной записи. Необязательный код восстановления (хранится хэш) открывает переписку в другом браузере.
- **Маршрутизация.** «Спросить об этой услуге» открывает ту же переписку с услугой в качестве темы; сообщение уходит
  организации, отвечающей за услугу, общие сообщения — в центральную поддержку (`SUPER_ADMIN`). «Сообщить о проблеме»
  пишет в ту же переписку категоризированное сообщение и одновременно создаёт запись в «Сообщениях посетителей» для
  редакционной проверки; отдельного канала нет.
- **Сотрудники.** «Входящие поддержки» (`/admin/questions`): фильтры по статусу, непрочитанному, организации,
  ответственному, сортировка по последнему сообщению или непрочитанному; статусы «Открыт», «В работе», «Отвечен»,
  «Закрыт», повторное открытие; назначение ответственного; контакт посетителя; счётчик в меню.
- **Уведомления.** Состояние «прочитано» хранится на сервере. Механизма push в проекте нет, поэтому клиенты опрашивают
  сервер: страница поддержки и открытая переписка у сотрудника — каждые 10 с, счётчики — каждые 45 с, список входящих —
  20 с, и только пока вкладка видима. E-mail — только по согласию посетителя и при настроенном SMTP.
- **Миграции.** function-catalog `V12` (таблицы `help_guests`, `help_guest_sessions`; новые столбцы в
  `help_conversations` и `help_messages`; `access_token_hash` стал необязательным), reference `V11` (подписи en/ru/uz/fr,
  удаление неиспользуемых `help.*`/`nav.help`). Ничего не удаляется из данных переписки.
- **Совместимость.** `POST /api/reports` оставлен для API-клиентов, но интерфейс им больше не пользуется. Переписки,
  созданные прежней формой (по токену), остаются у сотрудников; посетитель открыть их больше не может.

