# T04 — Validation và lỗi 400

Phụ thuộc: T03
Sẵn sàng: Có

Nhóm song song sau Wave A: Wave B, cùng T06. T04 sở hữu validation của Account/
Transfer DTO/controller và common error handler. Không sửa entity/repository của
History. Chỉ mở Wave B khi T03 đã được coordinator review/hợp nhất.

## Mục tiêu và phạm vi

Validation tại HTTP boundary của các luồng TH4 (account, transfer, loan), dùng Bean Validation nếu phù hợp stack. Đọc DTO/controller/service, handler common và caller frontend. Giữ validation nghiệp vụ ở service; không chỉ dựa vào frontend.

## Quyết định

Request thiếu/null/blank/sai kiểu, số tiền không dương hoặc kỳ hạn không hợp lệ trả 400 nhất quán và không tạo/cập nhật dữ liệu. Giữ lỗi resource 404 và phụ thuộc 503; không đưa mọi lỗi hệ thống thành 400. Không thay toàn bộ Map request ngoài những endpoint task yêu cầu.

### Contract chốt tại T01

- Thêm starter-validation vào common (version Boot 3.3.4) và Account/Transfer; starter-test ở module có test. DTO input riêng cho loan apply/repay và account create nếu tránh ràng buộc DTO response. @Valid ở HTTP boundary; service vẫn giữ kiểm tra nghiệp vụ. Không đổi mọi auth endpoint ngoài phạm vi TH4 account-create, transfer, loan.
- Account create: userId required >0; accountNumber @NotBlank max255 (không ép chỉ digit vì accountNumber hiện là String không contract định dạng); accountName optional max255; balance optional mặc định 0, >=0, @Digits(integer=13,fraction=2). id input không được dùng để ghi đè entity. Giữ khả năng seed balance hiện tại, không thêm generic update balance.
- Transfer: fromAccountNumber/toAccountNumber required nonblank max255, phải khác nhau; amount required >0, tối đa 9999999999999.99 và <=2 chữ số thập phân; description optional max255. Giới hạn lấy theo DECIMAL(15,2) nhỏ nhất hiện có ở Loan; cập nhật precision/scale Account.balance và Transaction.amount để số tiền hợp lệ không bị DB cắt ngầm. Việc đổi schema dữ liệu cũ cần preflight phát hiện ngoài biên, không chạy ALTER dữ liệu thật tự động; T02 hướng dẫn backup và kiểm tra.
- Loan apply: accountId required >0; amount required từ 10000000 tới 10000000000, @Digits(13,2); termMonths required integer 6..360; purpose required nonblank max255. Repay amount required >0, @Digits(13,2), không vượt remainingAmount/số dư theo service. ID đường dẫn phải >0. Giữ status thành công 200 và Loan response.
- Missing/null/blank/sai kiểu/malformed JSON/constraint violation/missing parameter → 400 ErrorResponse {timestamp,status,error,message,path}; message chỉ tên field và mô tả constraint, không rejectedValue. Không echo password/OTP/body, không đưa Jackson raw parser message ra client.
- Dùng common ResourceNotFoundException/DuplicateResourceException, bỏ hai import Kafka shadow trong AccountService. Resource missing 404, duplicate/conflict 409, Account dependency 503 giữ nguyên. IllegalArgumentException riêng 400; RuntimeException chung 500 với message cố định. LoanController bỏ catch tổng quát đang nuốt 404 hoặc biến lỗi hệ thống thành 404; để advice phân loại. Giữ error handling hệ thống, không bắt mọi exception thành 400.
- Test MockMvc invalid inputs và assert service/repository/producer không ghi; service invalid money không mutation; missing account/loan 404, dependency 503, unexpected runtime 500. Kiểm tra frontend loanApi/Transfer vẫn nhận cùng success shape; không đổi auth/security model trong task.

## Nghiệm thu và kiểm tra

- Test request hợp lệ; thiếu field; null/blank; sai kiểu; tiền bằng 0/âm; kỳ hạn ngoài biên; confirm không ghi DB/phát event khi invalid.
- Test phân biệt 400/404/503 sau khi đổi handler.
- `mvn -B -ntp -f bank-common/pom.xml install`; verify Account và Transfer. Nếu caller frontend đổi, chạy lint/build frontend.
- Không log/echo password, OTP hoặc dữ liệu cá nhân trong response lỗi.
- Ghi bằng chứng và bước đầu T05 vào progress.

## Bằng chứng triển khai (2026-10-19)

- Coordinator đã review và tích hợp commit `c11ac0d` vào branch `codex/th4-integration` (commit tích hợp `342f335`); diff check với CRLF được giữ nguyên đã đạt.
- Chạy lại trên branch tích hợp bằng JDK 17/Maven portable trên Windows, Maven offline cache D:; `bank-common install` exit 0, `account-service verify` 7/7, `transfer-service verify` 27/27, không failure/error/skip. Surefire summaries: `D:\Codex\outputs\bank-th4\T04-account-integration-surefire.txt`, `T04-loan-service-integration-surefire.txt`, `T04-transfer-integration-surefire.txt`.
- Docker/MySQL không thuộc gate T04; preflight/schema cũ và migration money precision tiếp tục thuộc T02/T08.
