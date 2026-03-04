-- Production-safe init script: no demo/test accounts.
-- Super-admin is created by application bootstrap initializer from env:
-- BOOTSTRAP_ADMIN_LOGIN / BOOTSTRAP_ADMIN_PASSWORD.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
