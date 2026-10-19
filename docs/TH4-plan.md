# Kế hoạch TH4 cho project ngân hàng

## Mục tiêu và cách đọc

Áp dụng phần bài tập lớn của TH4.docx vào ngân hàng; không xây thêm Patient/Appointment Service. Bản đồ dưới ghi lại yêu cầu để agent không phải đọc lại DOCX. Đây là kế hoạch công việc, không phải xác nhận project đã đạt.

Mỗi lượt dùng [progress](TH4-progress.md) → đúng file task. Chỉ đọc phần quyết định chung này khi task cần. Prompt dùng lại: [agent-next-task](agent-next-task.md). Quy tắc thực thi ở [AGENTS.md](../AGENTS.md).

## Bản đồ yêu cầu TH4

| Yêu cầu và trọng số gợi ý của đề | Hiện trạng khi thiết lập | Task chứng minh/hoàn thiện |
| --- | --- | --- |
| Thiết kế DB/Entity, thuộc tính, kiểu, PK, ràng buộc, bắt buộc/tùy chọn — 1.5 | Có entity; thiếu ERD và bảng sở hữu | T02, T08 |
| Spring Boot → MySQL/PostgreSQL, API tạo được bản ghi thật — 1.5 | Có datasource MySQL/JPA; chưa chạy xác minh | T01, T02, T08 |
| Controller → Service → Repository/JPA → DB — 1.5 | Account/Transfer có; HistoryController gọi repository | T05, T06 |
| CRUD, query/filter và pagination từ DB — 1.0 | Có tạo/đọc/query method; thiếu API CRUD đầy đủ và Pageable | T04, T05 |
| Transaction nhiều thao tác, thất bại phải rollback trong DB cục bộ — 1.0 | LoanService có @Transactional; chưa test rollback | T07 |
| Service-to-service qua REST/JSON — 2.0 | Hiện có Kafka; chưa có REST client nghiệp vụ | T03, T08 |
| Error handling: ít nhất 400/404/503 — 0.5 | Có handler 400/404; cần kiểm tra mapping và bổ sung 503 | T03, T04, T08 |
| Thiết kế áp dụng bài tập lớn — 1.0 | Chưa có tài liệu nộp theo 10 mục | T08 |

Yêu cầu bắt buộc đi kèm: ít nhất ba service nghiệp vụ; mỗi service có 1–2 entity hoặc hơn theo dữ liệu thực sự sở hữu; database riêng theo service; không đọc DB của service khác, không FK vật lý xuyên DB. Có validation, mô tả request/response và ba tình huống resource tồn tại, không tồn tại, service ngừng hoạt động. Chứng minh bằng API + SQL/log SQL, không chỉ bằng annotation.

Nếu giảng viên yêu cầu cả phần A trên lớp, project ngân hàng không thay thế được Patient Service/Appointment Service; cần xác nhận phạm vi nộp riêng. Không tự mở rộng repo sang phòng khám.

## Quyết định chung

- Giữ Java 17, Spring Boot, Maven, MySQL và cấu trúc module hiện tại. Không thêm parent/aggregator POM chỉ để chạy CI.
- Ba service sở hữu dữ liệu là Account (users/accounts/loans), Transfer (transactions), History (transaction_history). Notification, Gateway, Discovery không cần entity giả để đủ số lượng.
- Giữ Kafka cho luồng chuyển tiền. Thêm REST Transfer → Account để kiểm tra hai tài khoản trước khi tạo giao dịch; REST không thực hiện trừ/cộng tiền.
- Database riêng có thể cùng một MySQL server; mỗi service có schema/database và user riêng, chỉ được quyền trên DB mình. Không reset volume, drop DB hay đổi dữ liệu cũ trong setup.
- Giữ các endpoint/response hiện tại mà frontend đang gọi. Endpoint mới phải được T01 chốt; thay đổi contract cũ cần cập nhật caller và bằng chứng tương thích.
- @Transactional chỉ bảo vệ transaction DB cục bộ; Kafka/REST không tự tham gia distributed transaction. Không tuyên bố DB+event atomic chỉ nhờ annotation.
- Chỉ dùng dữ liệu giả cho kiểm thử/bằng chứng; không lưu mật khẩu, OTP, token hoặc thông tin khách hàng thật trong docs/log nộp.
- T01 phải chốt REST timeout/address, API CRUD an toàn, phân trang/filter, identifier lịch sử, chiến lược dữ liệu cũ và môi trường test ngay trong các file task tương ứng. Chưa chốt thì task đó `Sẵn sàng: Không`; không để agent sau đoán.

## Thứ tự task

| ID | Công việc | Phụ thuộc | File |
| --- | --- | --- | --- |
| T01 | Baseline và chốt contract | Không | [T01](tasks/T01-baseline-contracts.md) |
| T02 | Database riêng và bảo toàn dữ liệu | T01 | [T02](tasks/T02-database-ownership.md) |
| T03 | REST giữa service và lỗi 404/503 | T01 | [T03](tasks/T03-rest-integration.md) |
| T04 | Validation và lỗi 400 | T03 | [T04](tasks/T04-validation.md) |
| T05 | CRUD, query và phân trang | T04 | [T05](tasks/T05-crud-pagination.md) |
| T06 | Lịch sử đúng tài khoản và phân lớp | T02 | [T06](tasks/T06-history.md) |
| T07 | Kiểm thử transaction và rollback | T04, T05 | [T07](tasks/T07-transactions.md) |
| T08 | Kiểm thử tích hợp và tài liệu nộp | T05, T06, T07 | [T08](tasks/T08-evidence-delivery.md) |

### Thứ tự wave mặc định

Wave 0: T01 (coordinator chốt contract/baseline). Wave A: T02 + T03 (hai worktree độc lập: config/database so với Transfer REST). Wave B: T04 + T06 (Account/Transfer validation so với History); chỉ bắt đầu sau khi A được review và hợp nhất. Wave C: T05 (API Account). Wave D: T07 (transaction Account). Wave E: T08 (tích hợp toàn hệ thống và tài liệu). Chỉ tối đa hai agent implement đồng thời; wave phụ thuộc phải nhận branch tích hợp đã có đủ kết quả wave trước. Nếu ownership/file thực tế giao nhau, chạy các phần đó tuần tự trong wave; ưu tiên tính đúng hơn tốc độ. Chỉ coordinator ghi progress, kiểm tra diff/test và chuyển trạng thái.

Mỗi worktree task cần branch riêng từ cùng branch công việc; agent được giao file ownership trong brief, không ghi progress và trả handoff ngắn kèm commit/diff. Coordinator review phạm vi/contract, tích hợp theo DAG trong worktree tổng hợp, resolve/re-test conflict, rồi dispatch wave kế. Không auto-merge/push vào `main`. Nếu isolation không sẵn có, giảm concurrency và chạy tuần tự; không chạy writers đồng thời trong checkout chung.

## CI và lệnh kiểm tra

Workflow [ci.yml](../.github/workflows/ci.yml) chạy push/PR/manual, quyền contents:read, không deploy/secret. Backend dùng Temurin 17, từng job cài bank-common rồi verify service; sáu job độc lập để lỗi một service không che kết quả service khác. Frontend dùng Node 22, npm ci/lint/build. Báo số test từ XML Surefire/Failsafe; chưa có test chỉ là cảnh báo, không phải đạt TH4.

Từ repo root, dùng Maven đã cài (không có wrapper/aggregator):

```powershell
mvn -B -ntp -f bank-common/pom.xml install
mvn -B -ntp -f account-service/pom.xml verify
mvn -B -ntp -f transfer-service/pom.xml verify
mvn -B -ntp -f transaction-history-service/pom.xml verify
mvn -B -ntp -f notification-service/pom.xml verify
mvn -B -ntp -f api-gateway/pom.xml verify
mvn -B -ntp -f discovery-service/pom.xml verify
npm ci --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
```

Trong PowerShell phải kiểm tra `$LASTEXITCODE` sau từng lệnh; dừng chuỗi phụ thuộc khi lỗi. Test integration cần MySQL/Kafka/Redis theo contract T01; CI đã có job MySQL integration; Kafka end-to-end chạy riêng, remote CI chưa chạy. Lỗi baseline phải sửa trong T01 hoặc ghi BLOCKED, không bỏ gate CI.

Tham chiếu cấu hình action: [setup-java v4](https://github.com/actions/setup-java/blob/v4/README.md), [setup-node v4](https://github.com/actions/setup-node/blob/v4/README.md), [checkout v4](https://github.com/actions/checkout/blob/v4/README.md).
