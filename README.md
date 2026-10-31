### How to run project
```
1. git clone https://github.com/anhpd-glinteco/banking-microservices.git
2. cd banking-microservices
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

## TH4 submission

Start with [the ten-section design and reproduction commands](docs/data-and-service-design.md) and [the evidence index](evidence/README.md). Run section 9 from the repository root with Java 17, Maven, Node 22, PowerShell 7 and Docker available. The isolated fixture uses synthetic data and does not run SMTP.

Evidence collected on 2026-10-19/20: 46 unit/web tests, 8 MySQL integration tests and 41 E2E checks passed. Packaging is not a new runtime test. Browser UI and remote CI were not run. Security and DB/Kafka atomicity limitations remain in section 10.

## Project origin

Based on a source snapshot of [sonnn128/bank-system-microservice](https://github.com/sonnn128/bank-system-microservice), commit `b44d77e`, continued for coursework and learning. Existing package names are retained to reflect that origin.

This publication starts a new Git history to avoid carrying embedded SMTP credentials from the source history. Supply your own credentials locally. Previously exposed credentials must be revoked by their owner; deleting them here does not revoke them.

No project license file was present in the imported snapshot. No new license is asserted for the original work; confirm the original author's terms before redistribution or other reuse.
