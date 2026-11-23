## Nộp bài thực hành 6

Bản này dùng luồng chuyển tiền trên mốc `ba92b99`. Transfer và History gọi Account qua URL được cấu hình, trong môi trường TH6 tắt Eureka.

- [Thiết kế tích hợp](integration/integration-design.md) và [hợp đồng API](integration/api-contract.md)
- [Collection Postman](integration/postman/integration-tests.json) và [kết quả kiểm thử](integration/test-results.md)

Từ thư mục source đã giải nén, dùng Bash trên Linux hoặc WSL có Java 17, Maven, Docker Engine với Compose, OpenSSL và Python 3:

```bash
bash scripts/th6.sh run
bash scripts/th6.sh status <run-id>
bash scripts/th6.sh stop <run-id>
```

Lệnh `run` tạo khóa JWT và tài khoản giả, dựng fixture, rồi dùng image Newman chạy ba thư mục trong collection. Account, Transfer và History lần lượt dùng cổng loopback 28081, 28082 và 28084. `TH6_ACCOUNT_PORT`, `TH6_TRANSFER_PORT` và `TH6_HISTORY_PORT` có thể đổi trước khi tạo lượt chạy mới. Các cổng MySQL, Kafka và Redis tương ứng là 33306, 59092 và 36379, có thể đổi qua `TH6_MYSQL_PORT`, `TH6_KAFKA_PORT` và `TH6_REDIS_PORT`. Mỗi lượt có Compose project và volume riêng. Lệnh `stop` giữ volume.

Khi chạy thủ công trong Postman, đặt `username`, `password`, `sender`, `recipient` và `missing` theo tài khoản giả. Dùng Collection Runner cho thư mục **01 Success** để thực thi bước chờ trạng thái và lịch sử. Chạy **02 Missing account** khi Account còn hoạt động và **03 Provider unavailable** sau khi dừng Account của fixture. Hai thư mục lỗi dùng JWT do thư mục đầu lấy.

Account được để ở trạng thái dừng sau ca 503. Lệnh `bash scripts/th6.sh test <run-id>` khởi động lại Account trước khi chạy lại bộ kiểm thử.

---

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
