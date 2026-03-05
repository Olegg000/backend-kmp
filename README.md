# PGK Food Backend: Bootstrap and DB Init

## Why DB can look empty after init

- `sql-scripts/01-init.sql` is production-safe and does not create demo/test users.
- `data.sql` is intentionally empty.
- PostgreSQL scripts mounted into `/docker-entrypoint-initdb.d` run only on first creation of `pg_data` volume.

## Required env for first admin login

Set these values in `.env` (or server env) before `docker compose up`:

- `JWT_SECRET`
- `APP_BOOTSTRAP_ADMIN_ENABLED=true`
- `BOOTSTRAP_ADMIN_LOGIN`
- `BOOTSTRAP_ADMIN_PASSWORD`

Optional profile/name fields:

- `SPRING_PROFILES_ACTIVE` (default `it-postgres`)
- `BOOTSTRAP_ADMIN_NAME`
- `BOOTSTRAP_ADMIN_SURNAME`
- `BOOTSTRAP_ADMIN_FATHER_NAME`
- `BOOTSTRAP_ADMIN_GROUP_NAME`

Use `.env.example` as a template.

## Test mode demo seed

When `APP_TEST_MODE_ENABLED=true`, backend runs deterministic demo seeding on startup:

- creates/updates legacy demo accounts;
- sets password `password` for all demo logins;
- seeds current + previous work week for menus/permissions;
- seeds `demo_` transactions for QR/scanner checks;
- ensures demo curator is linked to `Group-101` (without removing existing curator links in DB).

Demo logins:

- `admin`
- `chef_main`
- `registrator`
- `curator_Group-101`
- `stud_Group-101_1`
- `stud_Group-101_2`
- `stud_Group-101_3`
- `stud_Group-101_4`
- `stud_Group-101_5`
- `stud_Group-102_1`
- `stud_Group-102_2`

Enable in `.env`:

```bash
APP_TEST_MODE_ENABLED=true
```

## Start / check

```bash
docker compose config | sed -n '/app:/,/ports:/p'
docker compose up -d --build app
docker compose logs --tail=200 app | grep -E "Bootstrap admin (created|already exists|skipped)"
```

## Validate admin exists in DB

```bash
docker compose exec postgres psql -U postgres -d pgk_food -c \
"select login, account_status from users where login='admin';"
```

## Validate login API

```bash
curl -sS -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"<strong_password>"}'
```

## Reset init scripts after schema/data changes (destructive)

```bash
docker compose down -v
docker compose up -d --build
```

Do this only with a backup when data matters.
