# T07 — Kiểm thử transaction và rollback

Phụ thuộc: T04, T05
Sẵn sàng: Có

Wave D, tuần tự sau T05; không chạy song song T05 vì cùng sở hữu AccountService,
LoanService/entity/repository/test. Có thể chạy đồng thời T06 đã xong nếu các file
History và Account tách biệt.

## Mục tiêu và phạm vi

Chứng minh một nghiệp vụ ghi nhiều dữ liệu trong Account Service rollback đúng khi lỗi. Ưu tiên approveLoan/makeRepayment đã có @Transactional; chỉ sửa root cause nếu test phát hiện lỗi. Đọc caller, repository, xử lý exception và Kafka producer trước khi sửa.

## Quyết định

Test đi qua Spring proxy và DB thật trong môi trường cô lập; không gọi new Service trực tiếp rồi coi annotation đã hoạt động. Snapshot trước/sau từ transaction đọc độc lập, không để transaction của test che mất lỗi commit. Không thêm endpoint gây lỗi vào production.

### Contract chốt tại T01

- Nghiệp vụ approveLoan qua Spring proxy: loan PENDING → ACTIVE, account.balance tăng loanAmount, approvedAt/disbursedAt được đặt. Đây là hai ghi Account DB, không Kafka trong method; không dùng saga handler đang catch Exception làm chứng minh rollback.
- Tái sử dụng spring-boot-starter-test, Testcontainers junit-jupiter/mysql 1.21.4 và Maven Failsafe 3.2.5 profile mysql-it từ T05. Quyết định T05 thay phiên bản Boot mặc định/1.19.8 vì Docker Engine 29 từ chối API cũ; bằng chứng và upstream đã ghi tại T05. Test *IT chỉ chạy khi -Pmysql-it, không disabledWithoutDocker; Docker thiếu phải FAIL/NOT RUN rõ, không silently skip. Unit/web test vẫn chạy mặc định.
- MySQLContainer mysql:8.0 DB th4_account_test/user test, password giả do test tạo; @DynamicPropertySource datasource, ddl-auto create-drop chỉ test container, Eureka và Kafka listener auto-startup tắt; mock producer/OTP nếu cần để không kết nối Redis/Kafka. Không dùng DB_HOST của người dùng, không bind existing volume.
- LoanTransactionIT không @Transactional ở class/method test; seed trong TransactionTemplate riêng, call bean Spring injected, đọc kết quả bằng transaction mới sau khi method hoàn tất (clear persistence context). Snapshot balance/status/remainingAmount/approvedAt/disbursedAt.
- Gây lỗi sau write đầu bằng @SpyBean LoanRepository.save: trong answer gọi EntityManager.flush() để account UPDATE thực sự đến MySQL rồi throw runtime exception chỉ trên loan fixture; không gọi loan save thật. assert account SQL update xảy ra nhưng balance/status/timestamps đều như snapshot sau rollback. Không thêm endpoint/cờ fail production. Happy path assert balance tăng đúng và ACTIVE, mọi trường còn lại hợp lệ.
- Missing loan/account và invalid status trả exception không đổi dữ liệu. Test makeRepayment positive/zero/negative theo validation T04 nếu service cần bảo vệ caller ngoài HTTP. Kafka catch/DB-event consistency ngoài transaction local ghi rõ rủi ro, không sửa thành outbox trong T07.
- Lệnh: mvn -B -ntp -f bank-common/pom.xml install; mvn -B -ntp -f account-service/pom.xml verify; mvn -B -ntp -f account-service/pom.xml -Pmysql-it verify. Failsafe report phải có >0 test, 0 failed/errors/skipped cho gate; không coi default verify bỏ profile là bằng chứng rollback.

## Nghiệm thu và kiểm tra

- Happy path ghi đủ hai thay đổi; lỗi sau write đầu không giữ bất kỳ thay đổi cục bộ nào.
- Resource không có/đầu vào sai không thay đổi DB; kiểm tra lại sau khi transaction kết thúc.
- Cài bank-common, `mvn -B -ntp -f account-service/pom.xml verify`; chạy integration test theo lệnh T01 ghi cụ thể ở đây.
- Nếu test phát hiện lỗi, chứng minh fail trước và pass sau sửa; giữ validation/error handling hiện có.
- Ghi số test, DB test đã dùng, giới hạn DB/event consistency và bước đầu T08 vào progress.

## Triển khai T07 (2026-10-19)

- Chỉ thêm `LoanTransactionIT`; giữ nguyên production LoanService, repository, controller và caller frontend sau khi kiểm tra đường gọi approveLoan/makeRepayment. Hai method đang có transaction và validation số tiền phù hợp.
- Sáu test gọi bean Spring injected, xác nhận AOP proxy và không có outer test transaction. Seed/read dùng TransactionTemplate REQUIRES_NEW; snapshot clear persistence context, gồm balance/status/remainingAmount/approvedAt/disbursedAt.
- Fault injection chỉ tại spy test LoanRepository.save của đúng fixture: flush EntityManager, đọc balance bằng JdbcTemplate trong cùng transaction để chứng minh MySQL thấy account UPDATE, rồi throw. Transaction đọc độc lập sau exception phải bằng snapshot trước cho mọi field.
- Bao phủ approve happy path, missing loan/account, invalid status không đổi dữ liệu; repayment zero/negative không đổi dữ liệu, positive partial và full trả đúng remaining/status/balance.
- Giới hạn: đây là atomicity transaction cục bộ Account DB, không chứng minh Kafka saga/DB-event consistency. AccountKafkaProducer và các saga handler catch Exception vẫn có rủi ro mất/khác trạng thái event; outbox/idempotency nằm ngoài T07.

- Lần MySQL đầu phát hiện lỗi kỳ vọng test: Spring repository exception translation đổi IllegalStateException thành InvalidDataAccessApiUsageException. Đổi duy nhất exception inject sang lớp RuntimeException riêng `FixtureFailure` để phân biệt đúng lỗi fixture; giữ nguyên flush/JDBC/snapshot assertions. Không phải lỗi rollback production.

## Bằng chứng nghiệm thu task

Chạy trong WSL `bank-th4`, Java 17, Maven 3.9.9; cache `/mnt/d/Codex/work/bank-th4-m2`:

- `mvn -B -ntp -Dmaven.repo.local=/mnt/d/Codex/work/bank-th4-m2 -f bank-common/pom.xml install`: exit 0, BUILD SUCCESS, log `D:/Codex/outputs/bank-th4/T07-common.log`.
- `mvn -B -ntp -Dmaven.repo.local=/mnt/d/Codex/work/bank-th4-m2 -f account-service/pom.xml verify`: exit 0, 11 unit/web tests, 0 failures/errors/skipped; log `T07-default-verify.log` cùng output folder.
- `mvn -B -ntp -Dmaven.repo.local=/mnt/d/Codex/work/bank-th4-m2 -f account-service/pom.xml -Pmysql-it verify`: lần cuối exit 0, 11 unit/web + 8 IT (LoanTransactionIT 6, T05 2), 0 failures/errors/skipped; log `T07-mysql-verify-r2.log`, XML Failsafe tại `T07-reports/` cùng output folder. Log SQL có UPDATE accounts/loans; test callback xác nhận balance đã tăng trong MySQL trước throw, snapshot đọc transaction mới sau rollback không đổi cả năm trường.
- Không đổi production code hoặc frontend nên không chạy frontend trong T07. Không dùng fixture volume cũ; mọi cleanup chỉ vào MySQL container riêng.
- Coordinator review/cherry-pick và chạy gate trên branch tích hợp trước khi chuyển DONE; tiếp theo T08 kiểm chứng E2E và tổng hợp tài liệu.
