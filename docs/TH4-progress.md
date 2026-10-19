# Tiến độ TH4

## Trạng thái hiện tại

- T01 DONE; Wave A đã có code tích hợp và check local, T02/T03 BLOCKED ở kiểm chứng môi trường thật.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task cần gỡ chặn: **T02 + T03**. Không mở T04–T08 khi dependency chưa DONE.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | BLOCKED | Code/config/scripts đã tích hợp; 24 self-check +3 compose đạt; thiếu MySQL fixture để kiểm quyền/API/copy/restore |
| T03 | BLOCKED | REST preflight đã tích hợp;26 test đạt; thiếu Account+MySQL/Kafka thật để kiểm persistence và luồng REST |
| T04 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T05 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T06 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T07 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |
| T08 | TODO | Contract sẵn sàng; chờ phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày: 2026-10-19 (Asia/Saigon). T01 DONE; T02/T03 BLOCKED ở gate DB thật. T04–T08 chưa chạy vì dependency chưa DONE. Đây là lỗi/thiếu môi trường, không kết luận nghiệp vụ đã đạt hoặc code sai.
- Kết quả tại branch codex/th4-integration / D:\Codex\work\bank-th4-codex-th4-integration. Commits: cd067a5 frontend, 26d689c contracts/setup, 2531736 T01, efb160f T03, 04cdc57 T02. Source D:\bank giữ nguyên thay đổi SMTP của người dùng; chỉ mirror docs tiến độ/brief để tiếp tục đúng worktree.
- T01: sửa6 file frontend, đủ7 contract. Java17.0.20.1+1/Maven3.9.9 bank-common install + verify6 service exit0, chưa có test ở baseline. Node22.23.3 npm ci/lint/build exit0; lint0 lỗi/6 cảnh báo hooks, build Sass/chunk warnings. Logs T01-*.log và summary.json tại D:\Codex\outputs\bank-th4. Portable tools D:\Codex\work\bank-th4-tools, checksum JDK/Node/Maven đã khớp; Maven cache hiện hữu vẫn trên C:\Users\ASUS\.m2.
- T02:3 config DB/user riêng,3 compose/env/build contexts/REST URL/cổng fixture,6 script provision/copy/verify/restore/self-check và docs/database-migration.md. Không overwrite đích/backup, không migration thật, không reset volume. Coordinator review toàn bộ diff; self-check24 PASS exit0;3 compose config và assertions datasource/REST/port13306 PASS exit0 với env giả D:\Codex\work\bank-th4-integrated-config.env. Legacy compose còn warning version obsolete.
- T03: REST Account preflight trước save/event, timeout2s/3s, typed404/503, không follow redirect, giữ duplicate/status contract; bỏ đúng2 Kafka exception import Account. Coordinator review toàn bộ diff và chạy lại Transfer sau tích hợp26 test,0 failure/error/skipped, exit0. Tests dùng HTTP thật nhưng repository/producer mocked.
- Check sau tích hợp cuối: common install, Account/Transfer/History verify đều exit0; chỉ Transfer có26 test, Account/History chưa có test. Logs waveA-*.log tại D:\Codex\outputs\bank-th4; git diff check cr-at-eol exit0. Frontend không đổi từ baseline đã kiểm. Không chạy GitHub CI/push/deploy/merge main.
- NOT RUN: MySQL quyền chéo, API→DB, copy/restore fixture và SQL count/PK/số dư; REST Account DB→Transfer DB/Kafka; rollback và end-to-end. Docker Desktop lỗi socket dockerInference và không có Linux engine pipe; rename thất bại, xóa socket bị automatic policy chặn. Không reset/delete dữ liệu. Người dùng hỏi bỏ Docker: code/unit/web đã tiếp tục, gate DB không bị hạ.
- Gỡ chặn: có MySQL8 fixture riêng + mysql/mysqldump để chạy T02; có Account/Transfer và Kafka/Redis cô lập để chạy T03. Docker là một cách dựng môi trường; runtime cài riêng cũng được nếu contract fixture không đổi. Không dùng DB/SMTP thật của người dùng. Khi có môi trường, xác minh diff/code mới nhất, chạy lại check và nghiệm thu T02/T03 trước wave B.
- Rủi ro còn: SQL/migration chưa chạy thực tế; REST check không bảo đảm số dư bất biến hoặc DB+event atomic; API authorization chưa được bổ sung. Baseline frontend audit agent báo25 vulnerabilities chưa upgrade, UI/browser chưa kiểm tra. SMTP của tác giả gốc đã được người dùng xóa; không dùng lại credential.
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
