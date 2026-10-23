# Build context: the repository root (docker-compose.yml), so the interface catalog that reference-service seeds its
# database from is bundled as the frontend's offline fallback. Ignore rules: front/Dockerfile.dockerignore.
FROM node:22-alpine AS build
WORKDIR /app/front
COPY front/package.json front/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY reference-service/src/main/resources/i18n/interface /app/reference-service/src/main/resources/i18n/interface
COPY front/ ./
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY front/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/front/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/healthz > /dev/null || exit 1
