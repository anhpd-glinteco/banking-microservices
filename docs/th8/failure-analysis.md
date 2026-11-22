# Phân tích sự cố TH8

## Các lần chạy triển khai

- Lần chạy baseline TH7 dừng giữa Maven verify sau khi WSL khởi động lại. Log chỉ xác nhận module common đã chạy xong. Kết quả lịch sử TH7 không thay thế lần chạy này.
- Các lần build đầu tiên có phiên Docker bị ngắt giữa quá trình tải và đóng gói. Giữ lại log gốc cùng các lần chạy lại. Khi gặp POM rỗng trong cache sau gián đoạn, thử lại với cache Maven riêng.
- Kết quả runtime thực tế và các lần thử nằm trong thư mục bằng chứng phát hành đi kèm.

## Phân tích ca chủ động

| Sự cố | Tác động quan sát cần xác nhận | Xử lý |
| --- | --- | --- |
| Một Account dừng | Eureka hội tụ về một instance; B tiếp tục xử lý kiểm tra quyền và preflight | Khởi động A, kiểm tra register và xác nhận cả hai instance nhận truy vấn |
| Cả hai Account dừng | Consumer trả 503 và không tạo dữ liệu chuyển tiền mới | Khởi động hai provider, đợi health và registry, sau đó chạy lại một luồng nghiệp vụ |
| MySQL dừng | JVM vẫn chạy nhưng health báo DOWN và log chỉ ra lỗi kết nối | Khởi động MySQL rồi xác nhận số dư và lịch sử cũ |
| Dựng lại stack | Container thay đổi nhưng các volume được giữ | Dùng cùng project ID và cấu hình để đọc lại dữ liệu và gửi giao dịch mới |

Xem [hướng dẫn vận hành](operation-guide.md) để tái hiện trên project thử riêng.
