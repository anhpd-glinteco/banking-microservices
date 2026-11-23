# Banking Microservices

Hệ thống ngân hàng thực hành của Nhóm 14. Ứng dụng hỗ trợ đăng ký bằng OTP, đăng nhập bằng JWT, quản lý tài khoản, chuyển tiền và tra cứu lịch sử.

## Kiến trúc

| Thành phần | Vai trò |
| --- | --- |
| Frontend | React/Vite chạy sau Nginx, gọi API cùng origin qua Gateway |
| API Gateway và Eureka | Định tuyến API và khám phá các instance dịch vụ |
| Account | Quản lý người dùng, tài khoản, khoản vay và OTP |
| Transfer và Transaction History | Xử lý chuyển tiền và tra cứu lịch sử theo quyền truy cập |
| Notification | Tiêu thụ sự kiện Kafka và gửi thư thử nghiệm đến Mailpit |

Transfer và History tìm Account qua Eureka và Spring Cloud LoadBalancer. Hai instance Account dùng chung schema sở hữu bởi Account. Transfer và History có schema riêng. MySQL, Kafka và Redis sử dụng named volume theo từng lượt triển khai. [Sơ đồ và thiết kế triển khai](docs/th8/deployment-design.md)

## Khởi động môi trường demo

Cần Linux hoặc WSL với Docker Engine, Docker Compose hỗ trợ `up --wait`, Bash và OpenSSL. Chạy từ thư mục gốc của repo.

```bash
bash scripts/th8.sh init demo01
bash scripts/th8.sh up demo01
bash scripts/th8.sh status demo01
```

`init` sinh mật khẩu và khóa JWT cho lượt chạy ở ngoài repo. `up` build image, khởi tạo database cho volume mới và chờ các container healthy. Các địa chỉ mặc định chỉ mở trên loopback.

| Địa chỉ | Công dụng |
| --- | --- |
| [Frontend](http://127.0.0.1:25173) | Đăng ký, đăng nhập và thao tác trên giao diện |
| [Gateway health](http://127.0.0.1:28080/actuator/health) | Kiểm tra sức khỏe Gateway |
| [Eureka](http://127.0.0.1:28761) | Xem trạng thái đăng ký, gồm hai instance Account |
| [Mailpit](http://127.0.0.1:28025) | Đọc OTP gửi đến địa chỉ thử nghiệm |

Đăng ký bằng địa chỉ thuộc `example.invalid` và lấy OTP trong Mailpit. Bộ kiểm thử tự tạo tài khoản giả và số dư phục vụ việc chuyển tiền.

## Kiểm thử và vận hành

```bash
# Chạy ca triển khai, failover và phục hồi trên môi trường thử riêng. Cần Python 3.
bash scripts/th8.sh test demo01

# Chạy kiểm tra Maven. Cần Java 17 và Maven.
bash scripts/th8.sh verify demo01

# Dừng stack và giữ volume. Dùng lại cùng ID để đọc dữ liệu sau khi dựng lại.
bash scripts/th8.sh down demo01
```

Lệnh `test` chủ động dừng và khởi động lại dịch vụ trong project đã chọn. Xem [hướng dẫn vận hành](docs/th8/operation-guide.md), [ca nghiệm thu](docs/th8/deployment-test-cases.md) và [phân tích sự cố](docs/th8/failure-analysis.md).

## Tài liệu các tuần trước

| Tuần | Tài liệu |
| --- | --- |
| TH4 | [Thiết kế dữ liệu và dịch vụ](docs/data-and-service-design.md) · [Hướng dẫn dữ liệu](docs/database-migration.md) · [Bằng chứng](evidence/README.md) |
| TH6 | [Thiết kế tích hợp REST](integration/integration-design.md) · [Kết quả kiểm thử](integration/test-results.md) |
| TH7 | [Service Discovery và kết quả kiểm thử](docs/th7-service-discovery.md) |
