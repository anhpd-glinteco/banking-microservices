# Ca nghiệm thu triển khai TH8

| Mã | Bằng chứng bắt buộc |
| --- | --- |
| DEP01 | Lệnh up từ volume trống, compose ps và ảnh Eureka Dashboard có hai Account UP |
| DEP02 | Mã HTTP và JSON health của từng JVM, log OTP đã lọc, xác nhận mail phù hợp |
| DEP03 | History và Transfer trả 503 khi cả hai provider dừng, count và Kafka offset giữ nguyên |
| DEP04 | Hai instance đăng ký lại và giao dịch SUCCESS xuất hiện trong lịch sử |
| DEP05 | Account báo DOWN khi DB dừng, dấu hiệu lỗi kết nối, số dư và lịch sử khớp trước và sau |
| DEP06 | Dựng lại cùng volume, dữ liệu được giữ và giao dịch sau khởi động đi qua frontend proxy |
| BANK01 | Hai instance phục vụ theo trace, năm lượt đọc liên tiếp và chuyển tiền thành công với một instance |
| UI01 | Trình duyệt đăng nhập, mở tài khoản, chuyển tiền và xác nhận ở lịch sử |
| PORT01 | Dựng từ bản ZIP giải nén sạch theo README |

Chạy acceptance trên một project TH8 riêng. `e2e-summary.json` ghi kết quả từng ca, `request-results.csv` ghi trạng thái HTTP và thời gian. Ghi nhận lần lỗi và lần chạy lại trong thư mục riêng. Kiểm tra giao diện và ảnh Eureka riêng với bộ API.

Trạng thái của từng lần thực thi nằm trong bộ bằng chứng bàn giao.
