# Hướng dẫn triển khai và vận hành TH8

## Triển khai từ bản sạch

Dùng Linux hoặc WSL có Docker Engine, Compose hỗ trợ `up --wait`, Bash, OpenSSL và Python 3. Đảm bảo Docker Engine đọc được đường dẫn state do Bash tạo. Vào root của mã nguồn.

```bash
docker info
docker compose version
bash scripts/th8.sh init demo01
bash scripts/th8.sh up demo01
bash scripts/th8.sh status demo01
```

Lệnh `up` gọi một lệnh Compose để build JAR, image, triển khai, khởi tạo database và chờ health. Cấu hình được sinh theo mẫu `.env.example`, mặc định đặt tại `${XDG_STATE_HOME:-$HOME/.local/state}/bank-th8/demo01`. Có thể đổi bốn cổng host trong file cấu hình của run trước khi up. Ký tự và độ dài của mật khẩu DB trong mẫu do script sinh.

- Frontend: `http://127.0.0.1:25173`
- Gateway: `http://127.0.0.1:28080`
- Eureka Dashboard: `http://127.0.0.1:28761`
- Mailpit: `http://127.0.0.1:28025`

Đăng ký bằng địa chỉ thử nghiệm thuộc `example.invalid`, lấy OTP trong Mailpit rồi đăng nhập. Để có dữ liệu chuyển tiền, bộ acceptance tạo hai người dùng riêng trong run và nạp số dư fixture trực tiếp vào schema Account.

## Vận hành và lưu bằng chứng

```bash
export TH8_OUTPUT_ROOT=/mnt/d/Codex/outputs/bank/th8  # Tùy chọn trong WSL
bash scripts/th8.sh test demo01
bash scripts/th8.sh down demo01
bash scripts/th8.sh up demo01
```

Mỗi lần test sinh một thư mục bằng chứng riêng. Nếu ca lỗi giữa chừng, giữ lại log và dùng lệnh `up` với đúng ID để đưa các service đã dừng về trạng thái healthy trước khi thử lại. Lệnh test chủ động dừng ứng dụng và dữ liệu trong project tương ứng. Dùng `down` thường để giữ volume và khóa của run. Dùng cùng ID để kiểm tra dữ liệu sau dựng lại. Dùng ID mới để diễn tập với volume mới.

Để gọi thao tác Compose thủ công, thay đường dẫn `<run-dir>` bằng thư mục script `init` in ra.

```bash
docker compose -p bank-th8-demo01 --env-file <run-dir>/.env -f compose.th8.yml logs --tail 100 account-service-a
docker compose -p bank-th8-demo01 --env-file <run-dir>/.env -f compose.th8.yml exec -T account-service-a curl -fsS http://127.0.0.1:8081/actuator/health
```

Các lệnh `stop`, `start` và `restart` dùng cùng project, file env và tên service. Xem lần lượt `ps`, Actuator, Eureka, log rồi gọi lại API nghiệp vụ. Trong bằng chứng chỉ lưu log đã rà và che thông tin đăng nhập hay nội dung mail.

## Xử lý sự cố

| Dấu hiệu | Kiểm tra | Khôi phục |
| --- | --- | --- |
| Healthcheck DB lỗi | Log init MySQL, quyền truy cập ba schema, file env đúng run | Sửa cấu hình rồi khởi tạo bằng ID mới nếu hỏng ngay ở lần đầu. Giữ volume cũ để đối chiếu. |
| Container chạy nhưng Account/History DOWN | Actuator, kết nối MySQL và trạng thái health của DB | Khởi động DB và chờ kết nối phục hồi. Đọc lại số dư và lịch sử. |
| Consumer trả 503 | Xem số Account UP trong Eureka, kiểm tra log LoadBalancer | Khởi động provider, chờ registry hội tụ và thử lại một lượt đọc. Đối chiếu trạng thái trước khi tạo lại lệnh chuyển tiền. |
| Không thấy OTP | Kiểm tra Kafka, Notification, health Mailpit và địa chỉ thử | Khôi phục dependency, phát sinh OTP mới rồi kiểm tra hộp thư. |
