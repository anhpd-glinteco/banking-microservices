# Tiến độ TH4

## Trạng thái hiện tại

- T01–T06 DONE, gồm T02/T03/T06 nghiệm thu trên MySQL/Kafka/Redis fixture riêng. Wave C chưa mở: WSL bị lỗi khởi động và cần quyền quản trị Windows để khôi phục runtime MySQL/Testcontainers.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task bị chặn kế tiếp: **T05** cần Testcontainers/MySQL thật; T07/T08 phụ thuộc và chờ.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | DONE | MySQL 8 thật: ba schema/user, ma trận quyền, copy/fingerprint/restore fixture; nguồn bank giữ nguyên; API → bank_transfer SELECT được |
| T03 | DONE | Account REST GET thật; transfer POST → MySQL + Kafka SUCCESS, số dư đúng; missing account 404 và Account dừng 503, không tạo row |
| T04 | DONE | Branch tích hợp đã cài common + verify Account 7/7, Transfer 27/27; không failure/error/skip |
| T05 | BLOCKED | Cần Docker/Testcontainers/MySQL để chứng minh filter/pagination/deletion ở DB; riêng distro WSL không tạo được instance; `Restart-Service WslService` cần quyền quản trị |
| T06 | DONE | History verify 8/8; MySQL/API/Kafka fixture smoke, backfill và caller frontend đã đối chiếu |
| T07 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T08 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày: 2026-10-19 (Asia/Saigon). Branch tích hợp `codex/th4-integration`, worktree `D:\Codex\work\bank-th4-codex-th4-integration`. T01–T06 DONE; T05 BLOCKED, T07/T08 chờ dependency. Không push/deploy/merge main.
- Môi trường riêng D: Ubuntu 24.04 WSL2 `bank-th4` tại `D:\Codex\work\bank-th4-wsl`; rootfs SHA256 đã khớp. Docker Engine 29.9.1/Compose 5.6.0, Java17, Maven3.9.9, Node22.23.3, PowerShell7.6.7, MySQL client; Docker hello-world PASS. Maven cache `D:\Codex\work\bank-th4-m2`.
- T02: MySQL8.0.46; fixture `bank` có 2 users/2 accounts/1 loan/1 transfer/1 history. Provision exit0; copy exit0, fingerprint mọi cột khớp và nguồn không đổi; restore `bank_restore` exit0. Ba owner đọc toàn bộ bảng của mình; 16 cross-DB SELECT/UPDATE trả access denied. Account GET từ `bank_account` và Transfer POST tạo row trong `bank_transfer`; lệnh/đối chiếu migration evidence ở `D:\Codex\outputs\bank-th4\T02-fixture-20261019`.
- T03: Linux Maven common install + Account/Transfer/History verify exit0; Transfer 26 tests, 0 failure/error/skip. Account/Transfer chạy với user DB riêng; REST GET 200, transfer POST 200, MySQL status SUCCESS amount 125.25, Kafka xử lý debit/credit, số dư TH4-A 4875.00 và TH4-B 1125.25. Account thiếu →404; Account service dừng →503; cả hai không thêm transfer row. Response tại `D:\Codex\outputs\bank-th4\T03-transfer-response.json`; logs `wsl-*-verify.log` và runtime tại `D:\Codex\outputs\bank-th4`.
- Ba compose config/self-check T02 đã PASS từ wave A; self-check Linux 24 PASS. T02/T03 không dùng DB/Kafka giả. SMTP secret không dùng. Rủi ro còn lại cho DAG: rollback MySQL, CRUD/filter/pagination, history theo tài khoản, full test compose/evidence; baseline frontend warnings/vulnerabilities chưa upgrade.
- T04: commit `342f335`; common install + Account verify 7/7 + Transfer verify 27/27 trên branch tích hợp, chạy bằng JDK17/Maven portable với cache D:; test summaries ở `D:\Codex\outputs\bank-th4\T04-*-integration-surefire.txt`.
- T06: commit `6576c3a`; History verify 8/8 trên branch tích hợp, summary `D:\Codex\outputs\bank-th4\T06-history-integration-surefire.txt`; live MySQL/API/Kafka fixture checks, backfill counts, and frontend lint/build documented in `docs/tasks/T06-history.md` and `D:\Codex\outputs\bank-th4\T06-*`.
- Blocker / next step: `wsl -d bank-th4` fails `Wsl/Service/CreateInstance/0x80072746`; `wsl --terminate bank-th4` did not recover it. `sc stop WslService` failed with Access Denied (5). An administrator must restart WSL service, then start only `bank-th4` and Docker Engine and confirm MySQL/Kafka containers are healthy. Data remains in the named fixture volumes on D:. Resume with T05 Testcontainers/pagination/deletion; then T07 real MySQL rollback and T08 full stack/documentation. No tests were skipped or treated as passed for these blocked tasks.
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
