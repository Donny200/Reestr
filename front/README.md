# Reestr Task frontend

React 18 + TypeScript (strict) + Vite 8 single-page application for the public service catalogue and the staff
console. It talks only to the real microservice API through `/api/*`, keeps no mock data, and never stores tokens:
authentication uses HttpOnly cookies and every request is sent with `credentials: 'include'`.

## Local development

1. Start the backend from the repository root (`docker compose up --build`). The API gateway must answer on
   `http://localhost:8082`.
2. In this directory:

   ```shell
   npm install
   npm run dev        # http://localhost:5173, /api proxied to http://localhost:8082
   ```

Vite proxies `/api/*` to the gateway, so cookies are same-origin and no CORS setup is needed.

| Script | What it does |
| --- | --- |
| `npm run dev` | Vite dev server on port 5173 |
| `npm run typecheck` | `tsc` for the app and for the Playwright tests |
| `npm run lint` | ESLint (zero warnings) followed by the localization check |
| `npm run i18n:check` | Localization check only (see below) |
| `npm run build` | Production bundle in `dist/` |
| `npm run test` | Playwright UI suite against mocked API routes (Chromium; `npx playwright install chromium` once) |
| `npm run test:backend` | Real-backend suite, driven by `AdminFunctionsBrowserIT` in function-catalog-service |

The UI suite starts Vite in-process on port 5183 (`tests/start-vite.mjs`). `tests/fixtures.ts` mocks every `/api/**`
call and serves the interface dictionaries from the same catalog files the backend uses, so labels in tests match
production.

## Localization

All visible text goes through the i18n context. Nothing user-visible is hard-coded.

- **Keys and texts** live in one place: `../reference-service/src/main/resources/i18n/interface/{en,ru,uz,fr}.json`.
  `en.json` defines the keys and the English source text. reference-service applies the catalog to its database on
  start-up, never overwriting translations edited by people, and serves
  `GET /api/interface-translations/{locale}` (the English base merged with each level of the tag, e.g. `fr` then
  `fr-FR`).
- **Lookups:** `const { t } = useI18n();`. Use `t('home.catalogTitle')` for plain text and
  `t('support.replyFromOrganization', { organization })` for placeholders. For counted text, use
  `t('home.resultsCount', { count })`, which picks the CLDR plural form `home.resultsCount_one` / `_few` / `_many` /
  `_other` for the current locale. An optional English fallback (`t(key, 'English')`) must equal the catalog text.
  A missing key shows the fallback, otherwise the key itself.
- **Switching** (`LanguageSwitcher`, `setLocale`) changes everything together once the new dictionary has arrived:
  labels, content language, `<html lang>` and `dir` (RTL for Arabic-script, Hebrew, etc.), the document title and
  description, and `Intl` formatting of dates, numbers, units, weekdays and language names. A late response for an
  earlier choice is ignored, and a failed request keeps the language already on screen. The choice is stored in
  `localStorage` (`reestr-task-locale`). The last dictionary per locale is stored too, so it renders immediately on
  the next visit and is then revalidated with the server's ETag.
- **Content** (service, category and organization names, instructions) uses `localizedText`. It falls back from
  `fr-FR` to `fr`, but never across scripts (`uz-Cyrl` never shows Latin Uzbek), and finally to the source text.
  Category names on cards and filters come from `useCategoryNames`.
- **Server messages** from the services are English. `errorMessage` shows them only in the English interface; other
  languages get the translated message for the HTTP status.
- **Adding text:** write `t('area.newKey')`, add the key to all four catalog files (counted texts get `_one`/`_other`,
  plus `_few`/`_many` in `ru.json` and `_many` in `fr.json`), then run `npm run lint`.
- **Adding a language:** add it under Admin → Languages. Missing texts show in English until they are translated.
  When Azure Translator is configured on the server they are machine-translated in the background, and the Languages
  page shows coverage, the Azure code, and a retry button. A curated language is a new `<tag>.json` in the catalog.
  See `../docs/localization.md`.

### Localization check

`scripts/check-i18n.mjs` fails when any of the following is true:

- a catalog file misses a key, a placeholder, or a plural form its language needs;
- `t('…')` or a server document (`labels.get("…")` in Java) uses a key that does not exist;
- an inline fallback differs from the catalog;
- user-visible text bypasses `t()`: JSX text, `aria-*`, `title`, `placeholder`, `alt`, `label`, toast and confirm
  messages, or column headers;
- a catalog key is no longer referenced.

Mark technical literals that must stay as written with an `i18n-exempt` comment. `tests/localization.spec.ts` runs
the check as a test and also walks the public and admin pages in French, Russian and Uzbek, failing if any English
catalog text remains on screen.

## Docker

The root `docker-compose.yml` builds this directory into the `front` service. It is a multi-stage image: `npm ci` and
`npm run build` run on Node 22, and `dist/` is served by nginx on `http://localhost:3000`. `nginx.conf` falls back to
`index.html` for client-side routes, gzips JSON and assets, and proxies `/api/*` to `api-gateway:8082` on the same
origin. Leave `VITE_API_BASE_URL` empty for that setup.

## Deployment configuration

When the frontend is not served behind the Vite or nginx proxy (for example on Netlify, see `public/_redirects`), set
the public gateway URL before building:

```env
VITE_API_BASE_URL=https://api.example.uz
```

The gateway must allow the deployed origin with CORS and credentials. The frontend needs no translation settings:
Azure Translator credentials exist only on the backend and are never sent to the browser.

## Backend login

The backend has no built-in administrator credentials. The first `SUPER_ADMIN` comes from deployment environment
variables documented in the root `README.md`; get the one-time credential through the project's secure operational
channel.
