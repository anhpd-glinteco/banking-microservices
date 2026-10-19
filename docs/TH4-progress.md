# Tiến độ TH4

## Trạng thái hiện tại

- T01–T04 và T06 DONE. WSL bank-th4 đã khởi động lại được; Docker/MySQL/Kafka/Redis kiểm tra trực tiếp đạt. T05/T07 đã nghiệm thu; T08 đang triển khai kiểm chứng E2E và tài liệu.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task hiện tại: **T08** full integration và tài liệu nộp.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | DONE | MySQL 8 thật: ba schema/user, ma trận quyền, copy/fingerprint/restore fixture; nguồn bank giữ nguyên; API → bank_transfer SELECT được |
| T03 | DONE | Account REST GET thật; transfer POST → MySQL + Kafka SUCCESS, số dư đúng; missing account 404 và Account dừng 503, không tạo row |
| T04 | DONE | Branch tích hợp đã cài common + verify Account 7/7, Transfer 27/27; không failure/error/skip |
| T05 | DONE | CRUD profile; unit/web11 + MySQL IT2, 0 failure/error/skip trên branch tích hợp; SQL filter/LIMIT/OFFSET và FK/no-cascade đạt |
| T06 | DONE | History verify 8/8; MySQL/API/Kafka fixture smoke, backfill và caller frontend đã đối chiếu |
| T07 | DONE | MySQL IT8 + unit11,0failure/error/skip sau tích hợp; rollback sau SQL flush giữ nguyên cả5 trường snapshot |
| T08 | IN_PROGRESS | T05/T06/T07 DONE; dựng stack test độc lập, E2E, CI MySQL và tài liệu10 mục |

## Bàn giao gần nhất

- Ngày 2026-10-19 (Asia/Saigon), branch codex/th4-integration. T01–T07 DONE, T08 IN_PROGRESS; không push/deploy/merge main. Runtime WSL bank-th4/Docker hoạt động; giữ phiên WSL mở trong kiểm thử. Fixture cũ và dữ liệu/SMTP D:\bank không đổi.
- T05 nguồn4095c4e/tích hợp77bc9dd: profile CRUD, SQL filter/pagination, FK/no-cascade; coordinator review toàn diff và chạy common install + Account mysql-it, exit0, unit11+IT2,0failure/error/skip. Logs/summary T05-integrated-* tại D:\Codex\outputs\bank-th4. Testcontainers test-only1.21.4 sửa tương thích Docker29, quyết định ghi T05/T07.
- T07 nguồna07dc53/tích hợpfea66dd: chỉ LoanTransactionIT + task doc, không sửa production. Coordinator đọc toàn diff; injected proxy, REQUIRES_NEW snapshots, flush + JDBC chứng minh balance đổi trước exception, rồi rollback5field về snapshot. Unit/default task11 PASS; profile task11+8 PASS. Sau tích hợp Account -Pmysql-it verify exit0, unit11+IT8,0failure/error/skip; T07-integrated-verify.log/T07-integrated-summary.json. Lần đầu fail vì Spring dịch IllegalStateException của test; dùng FixtureFailure riêng, không nới assertion.
- T08 tiếp theo: worktree riêng, compose/project bank-th4-test; không dừng stack cũ chiếm13306/39092/16379. Cho phép override host ports, lần chạy này dùng23306/49092/26379, app18081/18082/18084; defaults vẫn theo contract. Dùng fixture/credential giả, giữ volume, không SMTP.
- Cần hoàn tất T08 E2E API/SQL/Kafka +400/404/503, full7lệnh Maven và frontend, CI MySQL syntax/local check, docs10mục + artifact đã lọc secret. CI remote chưa chạy; không tuyên bố PASS. Giới hạn còn: chưa authn/authz, DB-event không atomic, baseline frontend advisory chưa nâng dependency.

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
