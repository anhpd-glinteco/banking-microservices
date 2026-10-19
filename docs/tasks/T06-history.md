# T06 — Lịch sử đúng tài khoản và phân lớp

Phụ thuộc: T02
Sẵn sàng: Có

Wave B (sau Wave A): có thể chạy song song với T04 khi T02 đã review/hợp nhất;
task sở hữu History service,
controller, repository, entity, consumer/event mapping và caller history. Không sửa
Account, Transfer hoặc common error handler. Chỉ mở Wave B khi T03, T02 đã review/
hợp nhất; mở Wave C khi T04 cũng đã review/hợp nhất.

## Mục tiêu và phạm vi

Sửa HistoryController hiện nhận accountId nhưng trả findAll; thêm service layer và query repository đúng tài khoản. Đọc HistoryLog, HistoryConsumer, event common, repository và frontend fetchHistory cùng mọi nơi gọi nó. Không truy cập Account DB trực tiếp.

## Quyết định

Kết quả chỉ gồm event/giao dịch thuộc tài khoản được yêu cầu; không trả dữ liệu của tài khoản khác. Giữ contract frontend hoặc cập nhật đầy đủ caller theo quyết định T01. DB history cũ phải được xử lý rõ, không silently bỏ hoặc gán sai chủ sở hữu. Lọc tài khoản không thay thế authentication/authorization.

### Contract chốt tại T01

- Event fromAccountId/toAccountId/accountId chứa accountNumber String (TransferService và AccountService producer chứng minh), trong khi Home truyền account.id numeric cho History. API mới GET /api/history/number/{accountNumber} trả200 List<HistoryLog> giữ array/raw eventData shape frontend; unknown/no history →[]. Chưa phân trang history trong task; T05 minh chứng pagination User tại DB.
- Giữ legacy GET /api/history/{accountId}: accountId Long >0, gọi GET Account /api/accounts/{id} rồi dùng accountNumber, không ép ID thành number. Unknown ID→404; Account lỗi/timeout/malformed→503 an toàn. URL/timeout cùng T03: ACCOUNT_SERVICE_URL/default localhost:8081, connect2000/read3000ms. History tự sở hữu client/error advice 400/404/503, không sửa common handler. T02 cung cấp env compose, T06 được thêm property config History nếu cần bàn giao coordinator.
- Frontend History gọi fetchHistory(accountNumber) tới đường dẫn /number/{encodeURIComponent(number)}, guard number thay guard numeric ID; mọi Home caller vẫn có đủ number. Giữ mapping raw payload để phân biệt credit/debit; lỗi không trả dữ liệu account khác.
- HistoryLog thêm nullable fromAccountNumber/toAccountNumber và eventId; index từng accountNumber và transactionId. Giữ id/transactionId/eventType/eventData/timestamp cũ. Event có from/to dùng đúng chuỗi; COMPENSATION chỉ fromAccountId; event accountId thuộc duy nhất account đó nếu sau này nhận banking.account (không tự mở topic trong task). TransferFailedEvent thiếu owner: resolve từ TRANSACTION_CREATED/MONEY_DEDUCTED/COMPLETED cùng transactionId đã lưu; event đến trước thì giữ unresolved và resolve khi counterpart đến, không suy đoán từ reason.
- Chỉ topic banking.transaction hiện tại, không archive OTP. eventType lấy payload nếu có; fallback nhận dạng cấu trúc: status SUCCESS→TRANSFER_COMPLETED, reason không from→TRANSFER_FAILED, reason+from→COMPENSATION; transaction created/money deducted không eventType không thể phân biệt thì giữ KafkaEvent. Không thay payload gốc hoặc suy đoán status.
- Deduplicate nonblank event id bằng unique nullable eventId (legacy không có id giữ null); duplicate delivery no-op, không dedup transactionId vì nhiều event thuộc một giao dịch. Save lỗi phải throw để Kafka retry; không nuốt exception hoặc log raw payload/PII.
- Legacy backfill chạy rõ ràng trên bản sao: parse eventData, điền owners/type/id theo cùng mapper; collision id giữ tất cả legacy record, chỉ một record canonical có eventId, số còn lại giữ null và báo count; unresolved/malformed giữ nguyên raw + NULL owner, thống kê/report, không trả bằng fallback findAll. Với failure đã có transactionId resolve owners từ record có owner. Không sửa/xóa nguồn thật tự động. Query owner trực tiếp OR EXISTS record cùng nonblank transactionId có owner cho failed event unresolved, đảm bảo event không owner không lọt.
- Tests fixture account numbers TH4-A/TH4-B/TH4-C, hai chiều và không history; raw Kafka mapping, failed out-of-order, duplicate ID, malformed/legacy, number "00123" khác ID123, legacy REST404/503; SQL query chỉ theo owner. GET trực tiếp http://localhost:8084/api/history/number/TH4-A/B đối chiếu history DB. Authentication/authorization hiện chưa có; filter không được mô tả như access control.

## Nghiệm thu và kiểm tra

- Fixture ít nhất hai tài khoản, giao dịch hai chiều, tài khoản không có lịch sử; assert không lẫn dữ liệu.
- Test mapping payload Kafka và xử lý dữ liệu cũ theo contract; không làm mất trường sự kiện cần truy vết.
- Cài bank-common, `mvn -B -ntp -f transaction-history-service/pom.xml verify`; frontend lint/build nếu thay caller.
- Chạy GET thật với hai tài khoản giả, đối chiếu DB; ghi endpoint cụ thể từ T01 tại đây.
- Ghi bằng chứng, giới hạn phân quyền còn lại và bước đầu T07 vào progress.

## Implementation và bằng chứng (2026-10-19)

- Đã thêm query theo owner, service/controller legacy resolve qua Account REST (404/503), mapper giữ raw `eventData`, dedup theo event id, resolve failure đến trước/sau counterpart, backfill có opt-in và thống kê unresolved/malformed/duplicate. Frontend caller chuyển sang account number; không sửa Account/Transfer/common.
- Test service: sau hai test bổ sung (eventType fallback, DB save failure propagate), coordinator chạy `transaction-history-service/pom.xml verify` trên branch tích hợp bằng JDK 17/Maven portable và cache D:; **8 tests pass, 0 failures/errors/skips**, exit 0. Surefire summary: `D:\Codex\outputs\bank-th4\T06-history-integration-surefire.txt`. Lần chạy WSL trước có 6 tests pass; test Windows xác nhận cả hai assertion cuối.
- Frontend: `npm ci` exit 0; `npm run lint` exit 0 (0 errors, 6 hook warnings); `GOMAXPROCS=2 npm run build` exit 0. Build log: `D:\Codex\outputs\bank-th4\T06-frontend-build.log`. npm ghi nhận 25 advisory audit hiện trạng; không nâng dependency.
- Runtime trên MySQL/Kafka giả lập riêng: backfill báo `updated=6, duplicateIds=1, unresolved=1, malformed=1`; API cho TH4-A/B/C lần lượt 6/5/0 rows, số account `00123` và legacy id 123 đều trả đúng 1 row, legacy id không tồn tại trả 404, Account REST lỗi trả 503. Đối chiếu DB/API: `D:\Codex\outputs\bank-th4\T06-db-counts.txt`, `T06-api-ownership.json`, `T06-history-A-final.json`, `T06-history-B-final.json`, `T06-history-C-final.json`, `T06-history-D-final.json`. Kafka synthetic event được lưu với event id, owners và raw payload; `T06-kafka-consumer-result.json`.
- Dịch vụ History và Account stub đã dừng bằng script chỉ nhắm đúng process của T06 (`D:\Codex\outputs\bank-th4\T06-cleanup.sh`); không xóa schema/volume/fixture. Giới hạn còn lại: chưa có authn/authz; kết quả API filter không phải access control. Coordinator đã review diff và ghi T06 DONE; WSL hiện không khởi động được, nhưng không còn validation nào của T06 cần Docker.
