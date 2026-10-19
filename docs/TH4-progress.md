# Tiến độ TH4

## Trạng thái hiện tại

- T01–T04 và T06 DONE. WSL bank-th4 đã khởi động lại được; Docker/MySQL/Kafka/Redis kiểm tra trực tiếp đạt. T05 đã nghiệm thu; T07 đang triển khai, T08 chờ dependency.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task hiện tại: **T07** transaction/rollback; T08 chờ dependency.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | DONE | MySQL 8 thật: ba schema/user, ma trận quyền, copy/fingerprint/restore fixture; nguồn bank giữ nguyên; API → bank_transfer SELECT được |
| T03 | DONE | Account REST GET thật; transfer POST → MySQL + Kafka SUCCESS, số dư đúng; missing account 404 và Account dừng 503, không tạo row |
| T04 | DONE | Branch tích hợp đã cài common + verify Account 7/7, Transfer 27/27; không failure/error/skip |
| T05 | DONE | CRUD profile; unit/web11 + MySQL IT2, 0 failure/error/skip trên branch tích hợp; SQL filter/LIMIT/OFFSET và FK/no-cascade đạt |
| T06 | DONE | History verify 8/8; MySQL/API/Kafka fixture smoke, backfill và caller frontend đã đối chiếu |
| T07 | IN_PROGRESS | T05 nghiệm thu; kiểm thử approveLoan rollback trên MySQL Testcontainers |
| T08 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày 2026-10-19 (Asia/Saigon), branch codex/th4-integration. T01–T06 DONE, T07 IN_PROGRESS, T08 TODO. T05 commit nguồn4095c4e, tích hợp77bc9dd; coordinator đã đọc toàn bộ thay đổi production/tests và review SQL parameter/sort whitelist, response không password, FK/no-cascade.
- Runtime WSL bank-th4/Docker29.9.1 đã phục hồi; MySQL SELECT1/schema, Redis PONG, Kafka topics exit0. Giữ phiên WSL mở để runtime ổn định, không cần admin/cài lại/xóa dữ liệu.
- T05 branch task: common install exit0; default verify11 và mysql-it verify11+2, tất cả0failure/error/skip. Sau tích hợp: common install BUILD SUCCESS; Account -Pmysql-it verify exit0, Surefire11/Failsafe2,0failure/error/skip. Log T05-integrated-common.log/T05-integrated-verify.log và T05-integrated-summary.json tại D:\Codex\outputs\bank-th4. Script pipe PowerShell có CRLF làm lệnh Account chưa chạy (exit1); đã chạy lại bằng bash-lc, exit0; không tính lần lỗi là PASS. Whitespace check với cr-at-eol exit0.
- Testcontainers mặc định không tương thích Docker29 (IT fail rõ); nâng test-only1.21.4, chạy thật đạt, quyết định trong T05 doc. T07 tái sử dụng. Không đổi frontend ở T05 nên không chạy frontend tại task này.
- Bước tiếp theo: T07 worktree riêng, test proxy + flush SQL trước lỗi và snapshot độc lập sau rollback; sau gate tích hợp mới T08. Fixture cũ giữ nguyên; T08 cần cổng thay thế cho DB/Kafka/Redis để không dừng stack ngoài project test. Không push/deploy/merge main, không dùng SMTP/DB thật.

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
