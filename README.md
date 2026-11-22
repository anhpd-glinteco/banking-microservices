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

## TH4 submission

Start with [the ten-section design and reproduction commands](docs/data-and-service-design.md) and [the evidence index](evidence/README.md). Run section 9 from the repository root with Java 17, Maven, Node 22, PowerShell 7 and Docker available. The isolated fixture uses synthetic data and does not run SMTP.

Evidence collected on 2026-10-19/20: 46 unit/web tests, 8 MySQL integration tests and 41 E2E checks passed. Packaging is not a new runtime test. Browser UI and remote CI were not run. Security and DB/Kafka atomicity limitations remain in section 10.

## TH7 service discovery

Transfer and History resolve Account through Eureka and Spring Cloud LoadBalancer. The isolated TH7 fixture starts two Account instances and runs discovery, failover, authorization, transfer/history, and dependency-outage checks. See [the architecture, scenarios, and run guide](docs/th7-service-discovery.md).

Run `bash scripts/th7.sh run` from the repository in a WSL/Linux shell with Docker Compose, Java 17, Maven, OpenSSL, and Python 3 available. The runner creates a private fixture ID and prints the matching `stop` command; `stop` preserves the fixture's data volumes.

## TH8 deployment

From Linux/WSL with Docker Compose, Bash and OpenSSL:

```sh
bash scripts/th8.sh init demo01
bash scripts/th8.sh up demo01
```

Open [the frontend](http://127.0.0.1:25173). The Eureka dashboard is on
port 28761 and the sandbox mailbox is on port 28025. See the
[operations guide](docs/th8/operation-guide.md),
[deployment design](docs/th8/deployment-design.md), and
[acceptance cases](docs/th8/deployment-test-cases.md).
