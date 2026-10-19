# Tiến độ TH4

## Trạng thái hiện tại

- T01 đã bắt đầu và dừng ở blocker bảo mật; chưa task TH4 nào được nghiệm thu DONE.
- Chế độ: coordinator tự điều phối full DAG, chạy tiếp qua các task; không cần user gọi từng task.
- Task đang làm: **T01**. Chưa chạy task phụ thuộc.
- Chọn wave theo quy tắc trong [AGENTS.md](../AGENTS.md) và phụ thuộc ở [plan](TH4-plan.md).

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | IN_PROGRESS | Người dùng xác nhận credential của tác giả gốc đã xóa và yêu cầu tiếp tục; đang chạy lại baseline/chốt contract |
| T02 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T03 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T04 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T05 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T06 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T07 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |
| T08 | TODO | Chưa sẵn sàng; chờ contract T01 và phụ thuộc DONE |

## Bàn giao gần nhất

- Ngày: 2026-10-19 (Asia/Saigon). T01 BLOCKED do credential SMTP có trong file được Git theo dõi; không ghi lại giá trị. Chưa task nào DONE; T02–T08 giữ TODO.
- Isolation: Worktrunk exit 0, branch `codex/th4-integration`, worktree `D:\Codex\work\bank-th4-codex-th4-integration`. Checkout `D:\bank` vẫn ở main; bộ docs/CI chưa tracked được sao chép nguyên vẹn sang worktree.
- Thay đổi an toàn tại worktree tích hợp: docker-compose.yml thay SMTP password literal bằng biến MAIL_PASSWORD bắt buộc; docs/tasks/T01-baseline-contracts.md ghi blocker. Không push/deploy, migration hay thay dữ liệu.
- Kiểm tra: `git diff --check` exit 0; assertion SMTP dùng biến bắt buộc và không còn literal exit 0. `docker info --format ...` exit 1: Linux engine pipe không tồn tại. Java/Maven không có trên PATH; Maven portable đã giải nén tại D:\Codex\work\bank-th4-tools; Node 22 tải xong (file node22.zip thực chất là executable), JDK tải dở đã dừng (exit 1); chưa xác minh checksum/version, chưa sử dụng.
- Frontend baseline worktree `D:\Codex\work\bank-th4-codex-th4-baseline-frontend`: không sửa source/không commit; npm ci đã hủy khi dừng bảo mật, exit 1; dependency installation có thể chưa hoàn tất.
- Backend verify, frontend lint/build chuẩn Node 22, API/SQL/rollback: NOT RUN trong lượt này vì dừng bảo mật; không sử dụng kết quả cũ làm PASS. Contract T02–T08 chưa chốt và readiness chưa đổi.
- Gỡ chặn: chủ tài khoản SMTP thu hồi/đổi credential đã lộ và xác nhận đã xử lý; chỉ xóa literal không thu hồi được credential trong lịch sử Git. Chưa rewrite lịch sử vì cần quyết định riêng, không tự xóa bằng chứng.
- Tiếp theo: kiểm tra lại điều kiện bảo mật; khi đạt, chuyển T01 IN_PROGRESS, review worktree/diff và chạy lại baseline trước khi chốt contract/DAG.
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
