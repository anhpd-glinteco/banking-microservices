# Kết quả kiểm thử TH6

Lượt chạy `th6-fixed-url-demo` trên WSL và Docker Compose, mốc code `ba92b99`. Newman thực thi 6 yêu cầu và 8 assertion.

| Mã | Tình huống | Kết quả | Quan sát |
| --- | --- | --- | --- |
| TH6-01 | Chuyển 1.000 | PASS | 200 PROCESSING → SUCCESS. Lịch sử có cùng mã giao dịch. Nguồn 50000000.00 → 49999000.00, đích 0.00 → 1000.00. |
| TH6-02 | Tài khoản đích không tồn tại | PASS | HTTP 404. Vẫn có 4 dòng giao dịch và Kafka offset giữ nguyên. |
| TH6-03 | Dừng Account Service | PASS | HTTP 503. Vẫn có 4 dòng giao dịch và Kafka offset giữ nguyên. |
| TH6-04 | Account timeout hoặc trả dữ liệu sai | PASS | HTTP stub trả kết quả kiểm tra 503. Không gọi lưu giao dịch hoặc phát sự kiện. |

Maven: account-service 18 test, bank-common build thành công, transaction-history-service 14 test, transfer-service 37 test. Các JAR đã được kiểm thử tại lượt build `th6-fixed-url-acceptance` và dùng lại cho demo sau khi điều chỉnh bộ nhớ fixture.

Kafka: `banking.transaction:0:12` sau ca thành công và sau mỗi ca lỗi. Ca timeout và dữ liệu sai dùng HTTP stub trong bộ test Transfer.

Lượt Newman đầu tiên ghi FAIL khi lệnh chuyển tiền không nhận được phản hồi trong giới hạn 10 giây của client. Lượt chạy lại dùng tài khoản giả mới và giới hạn 30 giây cho mỗi HTTP request. Giới hạn chờ trạng thái SUCCESS và timeout trong RestClient của Transfer giữ nguyên. [Kết quả lượt FAIL](evidence/e2e-attempt-newman-fail.json).

[Kết quả API và đối chiếu dữ liệu](evidence/e2e-summary.json) · [Tóm tắt Maven](evidence/build-summary.json) · [Ca HTTP stub](evidence/http-stub-results.json)
