# Hợp đồng API của luồng chuyển tiền

Các đường dẫn dưới đây gọi thẳng từng service trong fixture. Trừ đăng nhập, gửi header `Authorization: Bearer <accessToken>`. API bảo vệ trả 401 khi thiếu hoặc sai JWT và 403 khi người gọi không có quyền.

| Service | Phương thức và đường dẫn | Dữ liệu và kết quả |
| --- | --- | --- |
| Account | `POST /api/accounts/login` | JSON `username`, `password`. 200 trả `accessToken`, `tokenType`, `expiresIn`, `role` và `account`. |
| Account | `GET /api/accounts/number/{accountNumber}` | Kiểm tra quyền đọc tài khoản nguồn. 200 trả tài khoản có `accountNumber` và `userId` để Consumer xác minh quyền sở hữu. 404 nếu thiếu. |
| Account | `GET /api/accounts/lookup/{accountNumber}` | Kiểm tra tài khoản đích. 200 trả `accountNumber` và `accountName`. 404 nếu thiếu. |
| Transfer | `POST /api/transfers` | JSON `fromAccountNumber`, `toAccountNumber`, `amount`, `description`. Yêu cầu hợp lệ và mới trả 200 với `id` và `status: PROCESSING`. Dữ liệu sai trả 400, tài khoản thiếu trả 404, lỗi phụ thuộc trả 503. |
| Transfer | `GET /api/transfers/{transactionId}/status` | 200 trả giao dịch và trạng thái hiện tại. Giao dịch thiếu trả 404. Chỉ thành viên giao dịch hoặc admin được đọc. |
| History | `GET /api/history/number/{number}` | 200 trả danh sách sự kiện của tài khoản, có trường `transactionId`. 404 nếu thiếu tài khoản và 503 nếu Account không khả dụng hoặc trả phản hồi lỗi. |

Ví dụ lệnh chuyển tiền giữa hai tài khoản giả:

```json
{"fromAccountNumber":"<sender>","toAccountNumber":"<recipient>","amount":1000,"description":"TH6 demo"}
```

Consumer đối chiếu `accountNumber` trong JSON với tài khoản được yêu cầu và yêu cầu có `userId` ở phản hồi tra cứu nguồn. Phản hồi sai hợp đồng được xử lý như lỗi dịch vụ. Chỉ xác nhận giao dịch hoàn tất khi tra cứu trạng thái trả `SUCCESS`.
