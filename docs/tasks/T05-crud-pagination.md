# T05 — CRUD, query và phân trang

Phụ thuộc: T04
Sẵn sàng: Có

Wave C sau khi T04 đã review/hợp nhất. T05 sở hữu Account API/DTO/service/
repository và caller UI tương ứng; không sửa module History. T06 chạy xong ở Wave B.
Nếu frontend file UI bị task khác cùng chạm, tuần tự hóa phần đó.

## Mục tiêu và phạm vi

Hoàn thiện CRUD cơ bản và tìm kiếm/lọc/phân trang cho resource ngân hàng được T01 chọn để minh chứng TH4. Controller gọi service, service gọi repository; query và pagination chạy tại DB. Đọc caller frontend trước khi thêm/đổi API.

## Quyết định

Giữ endpoint đơn tài khoản hiện tại và response caller cũ. Không cho sửa số dư/trạng thái giao dịch tùy ý qua generic CRUD; không xóa tài khoản đang có khoản vay/giao dịch liên quan bằng cascade. Dùng Spring Data Page/Pageable; sort chỉ theo field đã cho phép.

### Contract chốt tại T01

- Chọn User profile thuộc Account làm resource CRUD minh chứng. Lý do: xóa Account đòi kiểm tra dữ liệu giao dịch xuyên service và có race; User chưa có bất kỳ account nào không thể mang số dư/loan/transfer, FK nội bộ accounts.user_id bảo vệ đồng thời. Không tạo CRUD tài khoản hoặc khoản vay có thể sửa trạng thái/số dư.
- Giữ POST /api/accounts/users hiện có, 200; request username,email,password bắt buộc nonblank (username/email max255, email đúng format, password max255), fullName/phone/identificationNumber optional max255. Không log request. Dùng DTO response profile {id,username,email,fullName,phone,identificationNumber,createdAt,updatedAt}; password không xuất ra HTTP. Existing frontend chỉ export createUser nhưng không có caller dùng response password; register-with-otp không thuộc CRUD này.
- GET /api/accounts/users/{id} →200 profile, PATCH cùng path →200, DELETE cùng path →204. PATCH DTO chỉ fullName/phone, required nonblank fullName max255, phone optional max255; username/email/password/ID không đổi. ID >0; missing 404. Không cascade delete account hoặc loan. DELETE transaction kiểm tra exists account theo userId; có bất kỳ account →409; FK violation khi race →409 an toàn; không xóa record financial nào.
- GET /api/accounts/users?page=0&size=20&username=<exact>&email=<exact>&sort=id,asc →200 Spring Data Page<profile>. Filter optional exact match, kết hợp AND, nonblank/max255 khi truyền. Query tại DB. page 0..2147483647, size 1..100; sort duy nhất id,username,createdAt + asc/desc; thêm id cùng chiều làm tie-break khi sort khác id. Invalid parameter trả400, không clamp; page cuối/rỗng content=[], totalElements đúng.
- Controller → AccountService → UserRepository Pageable; DTO không chứa JPA User/password. Không cần UI mới vì không có caller CRUD hiện hữu; giữ GET /api/accounts/{id} và GET /number/{number} nguyên contract.
- Tests: UserProfileCrudTest (web/service validation/404/409), UserProfileRepositoryIT MySQL Testcontainers với fixture hai user, filter và SQL LIMIT/OFFSET; UserDeletionIT assert user có account không mất dữ liệu, user trống xóa được, không có cascade. T05 thiết lập profile mysql-it/Failsafe/Testcontainers theo contract T07 để các test *IT chạy tại task này; T07 tái sử dụng, không đợi T07 mới có bằng chứng. Module Account sở hữu test/POM. Không mock DB làm bằng chứng pagination/deletion.

## Nghiệm thu và kiểm tra

- Test tạo/đọc/sửa/xóa hợp lệ theo contract và từ chối thao tác mất dữ liệu; ID không có trả 404.
- Test filter đúng dữ liệu; page đầu/cuối/rỗng; size/page/sort không hợp lệ trả 400; thứ tự ổn định.
- Có log SQL hoặc DB query chứng minh filter/pagination không thực hiện trên findAll trong RAM.
- Cài bank-common và `mvn -B -ntp -f account-service/pom.xml verify` (thêm service được T01 chọn nếu khác Account). Chạy frontend lint/build nếu có caller đổi.
- Ghi API được thêm, bằng chứng và bước đầu T06 vào progress.

## Quyết định triển khai bổ sung (2026-10-19)

- CRUD dùng DTO riêng trong Account; giữ response đăng ký OTP và mọi endpoint Account/Loan cũ ngoài phạm vi. Frontend chỉ export `createUser`, không có caller CRUD phải sửa.
- `UserRepository` dùng fragment `UserProfileQueries` trả `Page<User>` và nhận `Pageable`. Native SQL parameter hóa filter, LIMIT/OFFSET; sort chỉ ghép cột từ whitelist. Lý do: JPA `setFirstResult(int)` không chứa offset của page=2147483647,size=100; native offset long giữ nguyên contract, không clamp. DB count giữ totalElements đúng cả page rỗng.
- FK `accounts.user_id` giữ nguyên không cascade. DELETE precheck và flush trong transaction; lỗi FK do race được đổi thành DataIntegrityException/409, không trả SQL ra HTTP.
- Runtime Docker Engine 29.9.1 từ chối Docker API 1.32 của Testcontainers theo Boot mặc định (r2 verify FAIL, 2 IT errors, không skip). Override test-only `testcontainers.version=1.21.4` theo bản vá upstream: https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4 . Không hạ minimum API Docker, không thay fixture volume. T07 tái sử dụng bản này thay phiên bản mặc định contract cũ.
- MySQL test dùng container `mysql:8.0` độc lập, create-drop chỉ tại container; tắt Eureka/Kafka listener, mock producer/OTP. `DirtiesContext AFTER_CLASS` tránh Spring giữ datasource của container đã dừng giữa các IT class.

## Bằng chứng nghiệm thu task

- WSL `bank-th4`, Java 17, Maven 3.9.9; Maven cache `/mnt/d/Codex/work/bank-th4-m2`.
- `mvn -B -ntp -Dmaven.repo.local=/mnt/d/Codex/work/bank-th4-m2 -f bank-common/pom.xml install`: exit 0, BUILD SUCCESS (`D:/Codex/outputs/bank-th4/t05-common.log`).
- `mvn -B -ntp -Dmaven.repo.local=/mnt/d/Codex/work/bank-th4-m2 -f account-service/pom.xml -Pmysql-it verify`: exit 0, 11 unit/web tests + 2 MySQL IT, 0 failures/errors/skipped (`t05-verify-r3.log`, Failsafe XML tại `t05-reports/` cùng output folder). SQL có exact filter, ORDER BY created_at/id, LIMIT/OFFSET tại MySQL; test page max int không overflow.
- MySQL IT bao phủ CRUD, missing IDs, filter riêng/AND, trang đầu/cuối/rỗng, totalElements, tie-break cùng createdAt; DELETE chặn user có account và loan, giữ balance/data, FK thật từ chối delete trực tiếp, user trống xóa được. Web/service test bao phủ 400/404/409 và chuyển race FK thành safe conflict.
- Không đổi frontend caller nên không chạy lint/build trong T05. Không sửa FK/cascade, fixture hiện hữu, schema production hoặc endpoint tài chính.
- Coordinator cần review/cherry-pick và chạy lại gate trên branch tích hợp trước khi cập nhật DONE; bước sau T07 dùng lại `mysql-it` và `MysqlIntegrationSupport`.
