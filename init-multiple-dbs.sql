-- Runs once, on the first start of an empty pgdata volume (postgres image entrypoint executes *.sql files here).
-- A plain SQL file needs no execute permission, which bind mounts from macOS/Windows hosts do not reliably provide.
CREATE DATABASE adlita_project1;
CREATE DATABASE reference_service_db;
CREATE DATABASE function_catalog_db;
