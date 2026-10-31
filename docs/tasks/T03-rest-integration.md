# T03 — REST giữa Transfer và Account

Phụ thuộc: T01
Sẵn sàng: Có

Nhóm song song sau T01: Wave A, cùng T02. Sở hữu REST client/controller/service,
exception riêng và test của Transfer; được bỏ đúng hai Kafka exception import trong AccountService để endpoint hiện có trả 404/409 theo common handler; không sửa datasource/compose/env hay nghiệp vụ Account,
History hay progress. Cấu hình dùng property hiện có/default T01 chốt; nếu cần đổi
file config do T02 sở hữu, bàn giao yêu cầu cho coordinator và merge sau T02.

## Mục tiêu và phạm vi

Transfer gọi REST Account để kiểm tra tài khoản nguồn/đích trước khi tạo giao dịch mới. Đọc TransferService/controller/DTO, AccountController, common handler, config địa chỉ và frontend service; dùng khả năng HTTP đã có trong Spring, không thêm framework client nếu không cần.

## Quyết định

Giữ Kafka cho debit/credit; REST chỉ đọc tài khoản từ Account DB. Tài khoản không tồn tại trả 404; timeout/kết nối lỗi/service tắt trả 503 có message an toàn, không lộ stack trace. Các lỗi này không lưu transaction mới và không phát event. Không coi REST check là bảo đảm tài khoản/số dư không đổi sau đó.

### Contract chốt tại T01

- Endpoint Account hiện có: GET /api/accounts/number/{accountNumber}, 200 AccountDTO {id,userId,accountNumber,accountName,balance}; số tài khoản là chuỗi, giữ leading zero. Client chỉ dùng number để xác minh, không dùng Account DB.
- Spring RestClient với SimpleClientHttpRequestFactory; base URL property account.service.url nhận ACCOUNT_SERVICE_URL, default http://localhost:8081; compose http://account-service:8081. Connect timeout 2000ms, read timeout 3000ms. Encode URI path qua URI template, không nối dữ liệu chưa encode.
- Account 404 → Transfer 404 ResourceNotFoundException; timeout, connection refusal, 5xx, HTTP khác, response null/sai number/sai JSON → 503 với message cố định “Account service unavailable”. Không chuyển message/body upstream vào response. Bỏ đúng hai Kafka exception import shadow common trong AccountService ngay T03 như sửa prerequisite endpoint 404; T04 xác minh lại handler. Không giả lỗi 400 upstream là 404.
- Trình tự: lookup duplicate PENDING/PROCESSING hiện có trước, duplicate trả giao dịch cũ không tạo/phát thêm; với giao dịch mới gọi kiểm tra source rồi destination, cả hai thành công mới save PENDING → publish Kafka → save PROCESSING. Không thay debit/credit Kafka. REST không bảo đảm số dư hay tài khoản bất biến về sau.
- POST /api/transfers giữ 200 và JSON Transaction; GET /{transactionId}/status giữ contract hiện có. Payload frontend fromAccountNumber,toAccountNumber,amount,description giữ nguyên. T04 bổ sung validation trước service.
- Sở hữu thêm POM Transfer để thêm spring-boot-starter-test; test web/controller + client HTTP thật bằng server JDK HttpServer và timeout ngắn cấu hình trong test, mock repository/producer để assert không save/send ở mọi nhánh preflight lỗi. Test duplicate không gọi Account, valid hai lần GET, source/destination missing, timeout, refused connection, 5xx/malformed payload. Module test dưới Transfer, không đổi common hoặc phần Account khác ngoài hai import prerequisite trong T03.
- E2E giả: POST http://localhost:8082/api/transfers với {"fromAccountNumber":"TH4-A","toAccountNumber":"TH4-B","amount":1000,"description":"TH4 fixture"} sau seed T08; đối chiếu GET http://localhost:8081/api/accounts/number/TH4-A và SELECT bank_transfer.transactions. Account missing phải được nghiệm thu ở T03 sau sửa hai import prerequisite.

## Nghiệm thu và kiểm tra

- Test tài khoản hợp lệ, thiếu nguồn, thiếu đích, timeout và service tắt; assert HTTP response và không save/send event ở nhánh lỗi.
- Lệnh unit/web test: cài bank-common, rồi `mvn -B -ntp -f transfer-service/pom.xml verify`; verify common/Account nếu có đổi contract chung.
- Chạy luồng thật Transfer → Account → Account DB và lưu transaction vào Transfer DB; dùng endpoint/curl cụ thể T01 chốt ở đây.
- Kiểm tra request frontend cũ vẫn được nhận; không đổi response cũ ngoài contract đã chốt.
- Ghi bằng chứng và bước đầu T04 vào progress.

## Bằng chứng implementation — 2026-10-19

- AccountClient dùng RestClient, timeout2s/3s, encode path, không follow redirect; chỉ200 với đúng accountNumber được chấp nhận. 404 upstream trả404; lỗi khác trả503 cố định. Advice503 ưu tiên common RuntimeException handler.
- Duplicate lookup giữ nguyên; giao dịch mới kiểm tra đủ hai tài khoản trước mọi save/event. Chỉ bỏ hai Kafka exception imports Account prerequisite; không đổi debit/credit.
- Coordinator đọc toàn bộ diff, tích hợp commit efb160f và chạy Maven verify lại sau T02 config: 26 test,0 failures/errors/skips, exit0. Tests dùng HTTP server JDK thật + MockMvc controller/service, repository/producer mocked; kiểm tra valid/order, duplicate, 404, timeout/refused, HTTP khác, payload lỗi và không save/event khi lỗi.

## Nghiệm thu MySQL/Kafka thật (2026-10-19)

- Trong WSL2 `bank-th4`, Account dùng riêng `bank_account/account_app`, Transfer dùng riêng `bank_transfer/transfer_app`; Kafka 4.0.1 và Redis 7.4.2 cô lập, chỉ bind localhost.
- `GET /api/accounts/number/TH4-A` → 200 từ DB Account. `POST /api/transfers` với `TH4-A → TH4-B`, 125.25 → 200; SQL dưới `transfer_app` xác nhận transaction `SUCCESS` trong `bank_transfer`. Kafka consumer chạy debit/credit; SQL dưới `account_app`: TH4-A 4875.00, TH4-B 1125.25.
- Tài khoản nguồn không tồn tại →404; dừng Account rồi gửi transfer với hai số hợp lệ →503 `Account service unavailable`. Hai nhánh lỗi không tăng số transaction trong DB.
- Linux Maven verify: Transfer 26 tests, 0 failures/errors/skips; Account/History verify BUILD SUCCESS. HTTP response và runtime/Maven logs ở `D:\Codex\outputs\bank-th4`.
- E2E T03 đạt cho REST preflight → Account/MySQL, Transfer/MySQL và Kafka balance flow; điều này không tuyên bố DB/Kafka là distributed-atomic. **Nghiệm thu T03 đạt; coordinator cập nhật trạng thái duy nhất trong progress.**
- Common install và verify Account/Transfer/History sau tích hợp đều exit0; Account/History chưa có test. Log waveA-*.log dưới D:\Codex\outputs\bank-th4.
- Handoff trước khi có WSL fixture ghi Transfer→Account→MySQL và persistence Transfer/Kafka **NOT RUN**; nghiệm thu thật mới ở phần bên trên, không dùng mock thay bằng chứng DB.
