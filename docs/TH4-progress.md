# Tiến độ TH4

## Trạng thái hiện tại

- T01 DONE: baseline Java17/Node22 và contract đã kiểm tra; Wave A T02 + T03 đang làm.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task đang làm: **T02 + T03** ở worktree riêng. Docker chưa hoạt động; kiểm chứng DB thật chưa chạy.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | IN_PROGRESS | Wave A: cấu hình DB riêng và quy trình bảo toàn dữ liệu; Docker integration NOT RUN |
| T03 | IN_PROGRESS | Wave A: REST preflight và kiểm thử lỗi; DB thật NOT RUN |
| T04 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T05 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T06 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T07 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T08 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày: 2026-10-19 (Asia/Saigon). T01 DONE; Wave A T02/T03 IN_PROGRESS trong worktree riêng. T04–T08 chưa chạy vì phụ thuộc chưa DONE.
- Branch tích hợp codex/th4-integration tại D:\Codex\work\bank-th4-codex-th4-integration. Commit frontend cd067a5, docs/contracts 26d689c, nghiệm thu T01 2531736. Checkout D:\bank giữ nguyên source người dùng; mirror docs cho lượt sau tìm đúng trạng thái.
- T01 đổi sáu file frontend tối thiểu để lint/build được; giữ nguyên rule/lockfile. Cấu hình SMTP người dùng đã xóa credential được sao chép vào tích hợp; không dùng credential cũ/gửi mail thật. Người dùng xác nhận thuộc tác giả gốc và yêu cầu tiếp tục; blocker bảo mật gỡ cho phát triển cô lập.
- Baseline thực tế: Temurin17.0.20.1+1/Maven3.9.9, bank-common install và verify sáu service đều exit0; chưa có test/0 XML reports. Node22.23.3 npm ci/lint/build trên worktree tích hợp exit0, lint0 lỗi/6 cảnh báo hooks; build cảnh báo Sass/chunk. Contract readiness assertion bảy file exit0. Git diff whitespace check với cr-at-eol exit0. Logs D:\Codex\outputs\bank-th4\T01-*.log.
- Tool portable dưới D:\Codex\work\bank-th4-tools, archive D:\Codex\downloads\bank-th4; checksum JDK/Node/Maven khớp nguồn phát hành. Maven cache mặc định vẫn trên C:\Users\ASUS\.m2 (không di chuyển cache có sẵn).
- Docker Desktop không khởi động: socket Docker/run/dockerInference không truy cập được, không có Linux engine pipe. Thử rename socket thất bại; lệnh xóa socket bị automatic policy chặn. Không reset/xóa volume. DB/API/SQL/rollback/Kafka integration NOT RUN. Người dùng hỏi có thể bỏ Docker: tiếp tục code/unit/web trong Wave A, giữ gate DB thật; không giả DONE.
- Wave A: T02 cấu hình DB riêng + scripts/migration (worktree codex-th4-t02); T03 REST preflight/test + bỏ đúng hai Kafka exception import Account prerequisite (codex-th4-t03). Coordinator review diff/check sau bàn giao rồi xác định DONE/BLOCKED theo bằng chứng.
- Rủi ro nền: frontend dependency audit agent báo25 vulnerabilities; không upgrade dependency trong baseline. Chưa kiểm tra UI/browser. Chưa chạy GitHub CI, MySQL permissions/migration/rollback hoặc end-to-end.
- Tiếp theo: nhận commit T02/T03, review toàn bộ diff, tích hợp và chạy check có runtime; thiếu Docker/DB thật giữ BLOCKED ở task đó và không mở wave phụ thuộc.
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
