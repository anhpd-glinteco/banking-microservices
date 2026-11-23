# Banking Microservices

Banking Microservices là dự án thực hành của Nhóm 14, mô phỏng các nghiệp vụ ngân hàng trên kiến trúc microservices. Người dùng thao tác qua giao diện web. Các dịch vụ xử lý yêu cầu đồng bộ qua REST và trao đổi sự kiện giao dịch qua Kafka.

## Chức năng chính

- **Người dùng và tài khoản** — đăng ký bằng OTP, đăng nhập bằng JWT và xem thông tin tài khoản.
- **Chuyển tiền** — tra cứu người nhận, gửi lệnh chuyển tiền và theo dõi trạng thái xử lý.
- **Lịch sử giao dịch** — tra cứu giao dịch theo tài khoản và quyền truy cập của người dùng.
- **Khoản vay** — gửi yêu cầu vay, theo dõi khoản vay và xử lý phê duyệt hoặc trả nợ theo vai trò.

## Tổng quan hệ thống

Giao diện web gửi yêu cầu qua API Gateway. Gateway tìm dịch vụ đã đăng ký trong Eureka. Khi người dùng chuyển tiền, Transfer hỏi Account để kiểm tra thông tin tài khoản rồi gửi sự kiện qua Kafka. Account xử lý số dư, Transfer cập nhật trạng thái và History ghi lại kết quả để tra cứu. Giao diện hỏi lại trạng thái trong lúc giao dịch được xử lý.

### Kiến trúc và công nghệ

| Thành phần | Vai trò |
| --- | --- |
| Frontend | React/Vite chạy sau Nginx, gọi API cùng origin qua Gateway |
| API Gateway và Eureka | Định tuyến API và khám phá các instance dịch vụ |
| Account | Quản lý người dùng, tài khoản, khoản vay và OTP |
| Transfer và Transaction History | Xử lý chuyển tiền và tra cứu lịch sử theo quyền truy cập |
| Notification | Tiêu thụ sự kiện Kafka và gửi thư thử nghiệm đến Mailpit |

Transfer và History tìm Account qua Eureka và Spring Cloud LoadBalancer. Hai instance Account dùng chung schema sở hữu bởi Account. Transfer và History có schema riêng. MySQL, Kafka và Redis sử dụng named volume theo từng lượt triển khai. [Sơ đồ và thiết kế triển khai](docs/th8/deployment-design.md)

### Cấu trúc mã nguồn

| Thư mục hoặc tệp | Nội dung |
| --- | --- |
| `frontend/` | Giao diện React/Vite và cấu hình Nginx |
| `api-gateway/`, `discovery-service/` | Điểm vào API và máy chủ Eureka |
| `account-service/`, `transfer-service/`, `transaction-history-service/`, `notification-service/` | Các dịch vụ nghiệp vụ Spring Boot trên Java 17 |
| `bank-common/` | Kiểu dữ liệu và cấu hình dùng chung |
| `compose.th8.yml`, `database/th8/`, `scripts/th8.sh` | Đóng gói, tạo schema và vận hành môi trường demo |
| `docs/`, `integration/` | Thiết kế, runbook và tài liệu kiểm thử |

## Cài đặt và chạy trên máy

| Cách chạy | Dùng khi |
| --- | --- |
| Docker Compose qua script | Chạy toàn bộ hệ thống để dùng thử hoặc trình bày |
| Docker Compose trực tiếp | Điều khiển stack bằng lệnh Docker và xem log từng dịch vụ |
| Vite chạy trên máy, backend trong Docker | Sửa giao diện và xem thay đổi ngay |

### 1. Chuẩn bị môi trường

- **Windows** — dùng WSL 2 và chạy các lệnh bên dưới trong terminal Linux. Nếu dùng Docker Desktop, bật tích hợp cho distro WSL đang sử dụng. [Hướng dẫn Docker Desktop và WSL 2](https://docs.docker.com/desktop/features/wsl/)
- **Linux** — cài Docker Engine và Docker Compose plugin theo bản phân phối. [Hướng dẫn cài Docker Engine](https://docs.docker.com/engine/install/)

Terminal cần có Git, Bash và OpenSSL. Kiểm tra Docker có thể kết nối đến Engine trước khi chạy.

```bash
git --version
openssl version
docker info
docker compose version
```

### 2. Tải mã nguồn và khởi động

Các lệnh sau tải nhánh chứa cấu hình triển khai hiện tại. Docker build backend bằng Java 17 và build frontend trong image. Java, Maven, Node.js và MySQL trên máy chỉ cần khi bạn chọn quy trình phát triển hoặc kiểm thử riêng tương ứng.

```bash
git clone --branch feat/bank-compose-deployment https://github.com/anhpd-glinteco/banking-microservices.git
cd banking-microservices
bash scripts/th8.sh init demo01
bash scripts/th8.sh up demo01
bash scripts/th8.sh status demo01
```

`init` tạo cấu hình, mật khẩu và khóa JWT ở ngoài repo. `up` build image, khởi tạo database trên volume mới và chờ healthcheck. Lần đầu cần kết nối Internet để tải image và dependencies. Khi chạy lại, dùng cùng ID `demo01` để tái sử dụng cấu hình và dữ liệu.

### 3. Mở ứng dụng

| Địa chỉ | Công dụng |
| --- | --- |
| [Frontend](http://127.0.0.1:25173) | Đăng ký, đăng nhập và thao tác trên giao diện |
| [Gateway health](http://127.0.0.1:28080/actuator/health) | Kiểm tra sức khỏe Gateway |
| [Eureka](http://127.0.0.1:28761) | Xem trạng thái đăng ký, gồm hai instance Account |
| [Mailpit](http://127.0.0.1:28025) | Đọc OTP gửi đến địa chỉ thử nghiệm |

Mở frontend, chọn đăng ký và dùng email thử nghiệm như `demo@example.invalid`. Lấy OTP từ Mailpit, hoàn tất đăng ký rồi đăng nhập. Để kiểm thử chuyển tiền với số dư mẫu, chạy bộ kiểm thử bên dưới. Bộ kiểm thử tự tạo dữ liệu giả cho lượt chạy.

### Mở lại một lượt demo đã có

Khởi động Docker Engine, vào thư mục repo rồi chạy `bash scripts/th8.sh up demo01`. Dùng đúng ID đã tạo trước đó để giữ dữ liệu và cấu hình. Sau khi stack healthy, mở lại [frontend](http://127.0.0.1:25173).

### Dùng lệnh Docker Compose trực tiếp

Chạy `bash scripts/th8.sh init demo01` một lần. Thay đường dẫn tệp cấu hình trong ví dụ bằng đường dẫn `init` in ra rồi chạy từ thư mục gốc repo.

```bash
ENV_FILE="$HOME/.local/state/bank-th8/demo01/.env"
docker compose -p bank-th8-demo01 --env-file "$ENV_FILE" -f compose.th8.yml up -d --build --wait --wait-timeout 300
docker compose -p bank-th8-demo01 --env-file "$ENV_FILE" -f compose.th8.yml ps
docker compose -p bank-th8-demo01 --env-file "$ENV_FILE" -f compose.th8.yml logs --tail 50 transfer-service
```

### Phát triển giao diện với backend Docker

Khởi động stack theo cách trên. Cài Node.js 22.12 trở lên trong nhánh 22.x, rồi chạy Vite từ thư mục `frontend/`. Gateway trên cổng 28080 chuyển tiếp các API tài khoản, khoản vay, chuyển tiền và lịch sử.

```bash
cd frontend
npm ci
VITE_API_URL=http://127.0.0.1:28080 VITE_LOAN_API_URL=http://127.0.0.1:28080 \
  npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Mở [Vite](http://127.0.0.1:5173) và dùng Mailpit trên cổng 28025 để lấy OTP. Nếu cổng 5173 đang bận, có thể đổi lệnh sang 5174. Cả hai cổng đã được cấu hình cho các lần gọi từ trình duyệt.

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
