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
- Thêm spring-boot-starter-test và Testcontainers junit-jupiter/mysql, versions theo Boot 3.3.4 dependency management (nếu thiếu dùng BOM Testcontainers 1.19.8), Maven Failsafe 3.2.5 profile mysql-it. Test *IT chỉ chạy khi -Pmysql-it, không disabledWithoutDocker; Docker thiếu phải FAIL/NOT RUN rõ, không silently skip. Unit/web test vẫn chạy mặc định.
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
