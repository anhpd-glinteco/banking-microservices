# Database ownership and fixture migration

| Service | Database | User | Tables |
| --- | --- | --- | --- |
| Account | bank_account | account_app | users, accounts, loans |
| Transfer | bank_transfer | transfer_app | transactions |
| History | bank_history | history_app | transaction_history |

Each service uses its own `ACCOUNT_DB_*`, `TRANSFER_DB_*`, or `HISTORY_DB_*` variables. `DB_HOST`/`DB_PORT` identify the shared MySQL server. The application defaults name/user to the table above, but passwords are required without a default. Generic `DB_NAME`, `DB_USER`, `DB_PASSWORD` no longer select a shared schema. Setting `SPRING_DATASOURCE_*` externally overrides Spring configuration, so do not use old deployment overrides. `ACCOUNT_SERVICE_URL` in Transfer/History compose points to `http://account-service:8081`.

`compose.yml` starts the full stack. `compose.dev.yml` intentionally starts only MySQL/Kafka/Redis infrastructure; run Java services locally with their individual variables. `docker-compose.yml` is the legacy full-stack variant. All three require `MYSQL_ROOT_PASSWORD`. Existing MySQL data mounts remain unchanged. The obsolete init mounts are removed: migration is an explicit step on both fresh and existing volumes, never an automatic entrypoint side effect. MySQL's initialization password variables do not change the password of an existing volume.

## Safety and prerequisites

PowerShell 7, MySQL 8 client `mysql`/`mysqldump` on PATH, and a **dedicated, disposable MySQL 8 fixture** are required for the scripts. Use a loopback port above 3306, for example 13306. `-FixtureOnly` attests that this is synthetic data; the host/port guard reduces mistakes but cannot prove a server is a fixture. Never use these scripts on an existing user database, and never use real customer information. No script reads `.env`, drops a database, truncates a table, overwrites an existing dump, or revokes existing privileges.

Stop **all writers and Kafka consumers** before taking the initial snapshot; keep them stopped through copy, verification and restoration. A single-transaction dump is not a snapshot shared across multiple separate dump commands. Source before/after fingerprints detect changes but cannot prevent a concurrent writer. Keep services stopped until the copied target is verified. On failure the destination may be partially created; retain it for inspection and use a new isolated fixture rather than rerunning against it or deleting it automatically.

The scripts fix schema/user/table names to the ownership table and source `bank`, restore `bank_restore`. Environment name/user overrides are not supported by these migration scripts. A future real-data migration needs separate operator authorization, reviewed backup/restore steps, quiescence and maintenance planning; it has **not** been performed here.

The root credential is read from `TH4_MYSQL_ADMIN_PASSWORD`, service passwords from `ACCOUNT_DB_PASSWORD`, `TRANSFER_DB_PASSWORD`, `HISTORY_DB_PASSWORD`. Set them in the shell through your normal secure environment mechanism; do not put them in command history or screenshots. MySQL receives `MYSQL_PWD` only in its child-process environment, never a password command-line argument. Error output is sanitized because SQL errors may contain literals. Generated dumps contain **all fixture fields, including fake password fields**; keep them local and exclude them from evidence/Git.

## Provision and run with a fresh fixture

Set the four password variables above to fixture-only values matching the service configuration. Start just an isolated MySQL server on 127.0.0.1:13306; do not reuse `data/mysql`. Provision before starting the Java services:

```powershell
pwsh -NoProfile -File scripts/db/provision.ps1 -HostName 127.0.0.1 -Port 13306 -FixtureOnly
```

Provisioning uses `CREATE DATABASE IF NOT EXISTS`, `CREATE USER IF NOT EXISTS` and schema-scoped grants, so it works for a fresh volume and an existing fixture volume. The app permissions are SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES on its schema only, supporting Hibernate's existing `ddl-auto: update`; there are no cross-schema FKs. Existing users are accepted only with USAGE or grants scoped to their own schema and no other host variants; unexpected grants fail without changing/revoking them. Existing passwords are never replaced; a failed login means the operator must supply the already-correct password or provision a new fixture.

MySQL database grants treat `_` as a wildcard. Provisioning deliberately grants on escaped names such as `` `bank\_account`.* `` rather than allowing similarly named databases. See [MySQL GRANT documentation](https://dev.mysql.com/doc/refman/8.0/en/grant.html). The fixture uses MySQL's default `partial_revokes=OFF`; do not enable `partial_revokes` for this fixture because it changes escaped grant-name semantics.

For local Java processes, set `DB_HOST=127.0.0.1`, `DB_PORT=13306` and each service's password; name/user defaults are already correct. Set local Kafka/Redis/Eureka addresses as appropriate. For Compose, use a separate synthetic env file based on `.env.example`, set `MYSQL_HOST_PORT=13306`, start MySQL first, provision it explicitly, then start services. Internal service `DB_PORT` stays 3306. **Use a dedicated disposable checkout/directory with no existing data mounts**; merely changing the Compose project name does not isolate bind mounts such as `./data/mysql`. Never run this rehearsal from the user's populated checkout. Compose passes host `mysql`/port 3306 internally and never supplies a shared bank password to services. Provisioning a real local deployment is intentionally not automatic.

## Copy a synthetic legacy bank and prove restoration

Prepare the original five tables in `bank` on the same isolated server using the current pre-migration schema and fake records. Include at least two account balances, a loan and a transaction/history record. Target service databases must contain **zero tables**, including no Hibernate-generated empty tables; stop services before provisioning/copy. Provision does not create tables.

```powershell
pwsh -NoProfile -File scripts/db/copy-legacy.ps1 -HostName 127.0.0.1 -Port 13306 -FixtureOnly -OutputDirectory D:/Codex/outputs/bank-th4/db-fixture-run-001
pwsh -NoProfile -File scripts/db/verify.ps1 -HostName 127.0.0.1 -Port 13306 -FixtureOnly
pwsh -NoProfile -File scripts/db/restore-fixture.ps1 -HostName 127.0.0.1 -Port 13306 -FixtureOnly -BackupDirectory D:/Codex/outputs/bank-th4/db-fixture-run-001
```

Check `$LASTEXITCODE` after **every** command and stop immediately on failure. Choose a new output directory per run. Full `bank.sql` is generated using `--single-transaction --routines --triggers`; the per-service dumps copy schema/data including PKs and the local accounts→users FK. No source row is updated. This fixture has no custom stored routines or cross-schema triggers; do not extrapolate this procedure to a legacy database with them without reviewing its SQL.

`before.json` contains counts, ordered column names and SHA-256 fingerprints, not row values. Each row fingerprint includes every column in PK order: this compares IDs, account numbers, exact DECIMAL balances/loan amounts/transaction amounts, statuses and raw history JSON. The source is checked again after copying. `verify.ps1` compares each target against source and connects as each service user: own-table SELECT succeeds; SELECT and no-op UPDATE against both other services must fail with MySQL access-denied codes 1044 or 1142 (other errors do not pass). `restore-fixture.ps1` imports only the generated full fixture dump into an empty `bank_restore` and checks the original manifest. The backup directory must be trusted output from this copy script, kept unchanged; never supply downloaded/untrusted SQL, because MySQL import executes its contents as the fixture administrator. This is a restore rehearsal, not an overwrite of `bank`.

After verification, start the apps and create records through the API, then run read-only SQL against the owning schema to prove persistence. An API-created account belongs in `bank_account.accounts`, Transfer records in `bank_transfer.transactions`, History in `bank_history.transaction_history`. SQL/API evidence should select IDs, counts and synthetic financial fields; never collect passwords/OTP.

## Checks available without a server

```powershell
pwsh -NoProfile -File scripts/db/self-check.ps1
docker compose --env-file D:/Codex/work/bank-th4-t02-config.env -f compose.yml config --quiet
docker compose --env-file D:/Codex/work/bank-th4-t02-config.env -f compose.dev.yml config --quiet
docker compose --env-file D:/Codex/work/bank-th4-t02-config.env -f docker-compose.yml config --quiet
```

The env file above must be synthetic, copied from `.env.example`; it must not be the user's `.env`. `config --quiet` validates configuration only. Self-check exercises fixture/schema guards, SQL password escaping, non-empty destination rejection and snapshot mismatch; it does **not** prove MySQL accepts the SQL or migration/persistence/privilege isolation works. Docker unavailable means those database checks remain **NOT RUN**, and T02 cannot be marked DONE.
