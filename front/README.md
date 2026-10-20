# Reestr Task frontend

The frontend is connected to the real microservice API. It has no mock data and
never calls the broken `GET /api/user/profile` endpoint.

## Local launch

1. Start the backend stack from the repository root:

   ```powershell
   docker compose up --build
   ```

   The API gateway must be available at `http://localhost:8082`.

2. In this directory install and run the frontend:

   ```powershell
   npm.cmd install
   npm.cmd run dev
   ```

3. Open `http://localhost:5173`.

Vite proxies all `/api/*` calls to the gateway, so local development does not
need cross-origin browser configuration. Authentication uses HttpOnly cookies;
the frontend always sends `credentials: 'include'` and does not store tokens.

## Docker

The root `docker-compose.yml` builds this directory into the `front` service: a
multi-stage image that runs `npm ci` and `npm run build` on Node 22 and serves
`dist/` with nginx on `http://localhost:3000`. `nginx.conf` falls back to
`index.html` for client-side routes and proxies `/api/*` to `api-gateway:8082`
on the same origin, so the HttpOnly cookies work without CORS. Leave
`VITE_API_BASE_URL` empty for that setup; pass it as a build argument only when
the API lives on a different public origin.

## Deployment configuration

When the frontend is not served through the Vite proxy, set the public gateway
URL before building:

```env
VITE_API_BASE_URL=https://api.example.uz
```

The gateway must allow the deployed frontend origin with CORS and credentials.

## Backend login

The backend does not contain built-in administrator credentials. The first
`SUPER_ADMIN` is provisioned from deployment environment variables documented
in the root `README.md`; obtain the one-time credential through the project's
secure operational channel.
