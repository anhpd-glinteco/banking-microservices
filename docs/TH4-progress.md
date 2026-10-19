# Tiến độ TH4

## Trạng thái hiện tại

- T01–T04 và T06 DONE. WSL bank-th4 đã khởi động lại được; Docker/MySQL/Kafka/Redis kiểm tra trực tiếp đạt. T05 đang triển khai; T07/T08 chờ dependency.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task hiện tại: **T05** CRUD/pagination và MySQL Testcontainers; T07/T08 phụ thuộc và chờ.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | DONE | MySQL 8 thật: ba schema/user, ma trận quyền, copy/fingerprint/restore fixture; nguồn bank giữ nguyên; API → bank_transfer SELECT được |
| T03 | DONE | Account REST GET thật; transfer POST → MySQL + Kafka SUCCESS, số dư đúng; missing account 404 và Account dừng 503, không tạo row |
| T04 | DONE | Branch tích hợp đã cài common + verify Account 7/7, Transfer 27/27; không failure/error/skip |
| T05 | IN_PROGRESS | WSL/Docker đã phục hồi; triển khai CRUD và kiểm thử MySQL Testcontainers trong worktree riêng |
| T06 | DONE | History verify 8/8; MySQL/API/Kafka fixture smoke, backfill và caller frontend đã đối chiếu |
| T07 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T08 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày 2026-10-19 (Asia/Saigon). Branch codex/th4-integration tại D:\Codex\work\bank-th4-codex-th4-integration; HEAD eba5b5d, checkout sạch trước thực hiện. T01–T04/T06 DONE theo bằng chứng đã ghi trong file task; T05 IN_PROGRESS, T07/T08 TODO.
- Runtime kiểm tra lại: wsl bank-th4 /bin/true exit0; Docker Engine29.9.1, Compose5.6.0; MySQL SELECT1 và các schema fixture exit0; Redis PONG exit0; Kafka topics qua localhost:39092 exit0. Không cần restart dịch vụ admin; không xóa/reset volume. Lần probe MySQL không password và Kafka nhầm port9092 thất bại, đã sửa probe đúng config.
- T05 worktree riêng D:\Codex\work\bank-th4-codex-th4-t05, branch codex/th4-t05 từ eba5b5d. Đang triển khai Account CRUD/profile và mysql-it; chưa nghiệm thu Testcontainers hay chuyển DONE.
- Không cài thêm runtime vì các công cụ hiện có hoạt động. Dữ liệu/SMTP người dùng trong D:\bank không đổi; chưa push/deploy/merge main.
- Bước tiếp theo: review diff T05, chạy unit và MySQL integration trên branch tích hợp; chỉ sau khi đạt mới mở T07 rồi T08. Bằng chứng mới lưu D:\Codex\outputs\bank-th4.

## Mẫu bàn giao thay thế mục trên

Giữ bảng trạng thái; thay thế bàn giao gần nhất, không nối log dài. Nếu cần giữ quyết định lâu dài, đưa vào file task hoặc quyết định chung của plan.

```text
Ngày / task / trạng thái:
Thay đổi (file + hành vi):
Kiểm tra (lệnh, exit code, số test/kết quả; NOT RUN kèm lý do):
Quyết định đã chốt và vị trí lưu:
Chưa xong / rủi ro / điều kiện gỡ chặn:
Task tiếp theo và bước đầu tiên cụ thể:
```
