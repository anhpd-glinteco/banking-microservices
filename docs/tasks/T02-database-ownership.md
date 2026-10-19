# T02 — Database riêng và bảo toàn dữ liệu

Phụ thuộc: T01
Sẵn sàng: Có

Nhóm song song sau T01: Wave A, cùng T03. Sở hữu `application.yml` từng service,
cả ba compose file, `.env.example`, và docs DB/migration. Không sửa Java service,
DTO/controller hoặc CI. Nếu một quyết định T01 buộc overlap ownership, chạy tuần tự.

## Mục tiêu và phạm vi

Account, Transfer, History có DB/user riêng, vẫn chạy được bằng cấu hình local và các compose được hỗ trợ. Phạm vi gồm application.yml, compose.yml, compose.dev.yml, docker-compose.yml, .env.example và tài liệu khởi chạy liên quan; rà soát Dockerfile/caller config trước khi đổi. Không đổi Kafka contract hay logic chuyển tiền.

## Quyết định

Giữ một MySQL server nếu đủ; tách DB và quyền, không tạo FK xuyên DB. Schema và quyền mới phải tạo được cả với volume mới và volume đã có; init script chỉ chạy lần đầu không giải quyết được trường hợp sau. Không xóa volume hay ghi đè DB bank cũ.

### Contract chốt tại T01

- Một MySQL 8.0 server; DB/user: `bank_account`/`account_app`, `bank_transfer`/`transfer_app`, `bank_history`/`history_app`. Chỉ GRANT quyền trên schema tương ứng (không global quyền, không cross-schema FK). Account sở hữu users/accounts/loans; Transfer transactions; History transaction_history.
- Config dùng DB_HOST/DB_PORT dùng chung, còn ACCOUNT_DB_NAME/USER/PASSWORD, TRANSFER_DB_NAME/USER/PASSWORD, HISTORY_DB_NAME/USER/PASSWORD riêng. Tên DB/user mặc định như trên, password không có default production; .env.example chỉ giá trị giả. Không dùng DB_NAME/DB_USER chung để vô tình quay lại bank.
- Cả ba compose phải có cùng mapping datasource; build ba service dùng context repo root và Dockerfile tương ứng vì COPY bank-common. Giữ nguyên volume cũ. Thêm SQL/provision script quản trị chạy rõ ràng trên cả volume mới/cũ; không phụ thuộc riêng /docker-entrypoint-initdb.d. Password được đưa qua biến môi trường/secret input, không log.
- Tạo docs/database-migration.md và scripts/db/provision.ps1, copy-legacy.ps1, verify.ps1, restore-fixture.ps1 (MySQL CLI/mysql dump; password bằng MYSQL_PWD chỉ ở process con). Chạy mỗi script qua pwsh -NoProfile -File scripts/db/<script>.ps1 -HostName <test-host> -Port <test-port> -FixtureOnly; không nhận tên DB bất kỳ ngoài contract/fixture, reject nếu không xác nhận fixture. Quiesce writers trước snapshot; mysqldump --single-transaction --routines --triggers bank vào đường dẫn do operator chỉ định. Copy dữ liệu và schema theo bảng sở hữu, giữ PK và accounts.user_id; không DROP/UPDATE nguồn bank. Chặn đích không rỗng thay vì overwrite. Đối chiếu count và tập PK từng bảng; balance theo accountNumber, loanAmount/remainingAmount theo loan id, amount/status theo transaction id, history raw payload. SQL DECIMAL phải đối chiếu chính xác, không dùng float.
- Test fixture bank cũ trong MySQL cô lập, snapshot nguồn trước/sau, copy rồi restore dump vào schema bank_restore riêng và đối chiếu lại; không thử restore trên nguồn thật. Dữ liệu thật cần ủy quyền riêng; chưa chạy không được ghi đã migration.
- SQL kiểm quyền: mỗi user SELECT bảng mình thành công; SELECT và UPDATE bảng bất kỳ của hai schema khác đều bị access denied. Lưu count/PK/số dư trước-sau cùng exit code; không lưu password.
- Config REST do T03/T06 cần: ACCOUNT_SERVICE_URL=http://account-service:8081 trong compose; local default http://localhost:8081. Test compose dùng env giả và file env riêng, không đọc .env người dùng. Lệnh: `docker compose --env-file <env-gia> -f <compose.yml|compose.dev.yml|docker-compose.yml> config --quiet`.

## Nghiệm thu và kiểm tra

- Config mỗi service trỏ DB riêng; user service không SELECT/UPDATE được bảng service khác.
- Test môi trường sạch: tạo qua API và SELECT đúng DB thấy bản ghi tương ứng.
- Test trên bản sao dữ liệu giả dạng bank cũ: chuyển dữ liệu, đối chiếu count/PK/số dư và phục hồi thành công; không thay đổi nguồn gốc.
- `docker compose -f compose.yml config --quiet`, lặp lại cho compose.dev.yml và docker-compose.yml với env giả theo contract; không in secret.
- `mvn -B -ntp -f bank-common/pom.xml install`; verify Account/Transfer/History sau thay đổi. Chạy SQL quyền truy cập và persistence theo lệnh cụ thể T01 bổ sung vào đây.
- Ghi kết quả, đường dẫn hướng dẫn migration và bước đầu T03 vào progress.
