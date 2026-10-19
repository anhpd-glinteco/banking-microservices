### How to run project
```
1. git clone <repo>
2. cd <repo>
3. cp .env.example .env
4. Provision the service databases explicitly before starting Java services:
   see docs/database-migration.md (fixture rehearsal; no real-data migration is automatic).
5. docker compose --env-file .env up -d
```

```
bank-common
1. discovery
2. api-gw
3. account
4. notification
5. transtraction
6. transfer

```
mvn -B -ntp -f bank-common/pom.xml install

TH4 database ownership, required environment variables, backup/copy/restore guards and
checks: [docs/database-migration.md](docs/database-migration.md).
