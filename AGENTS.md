<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Storage preference

Prefer D:\Codex\downloads for downloaded files and D:\Codex\outputs for generated deliverables, grouped by task. Prefer D:\Codex\work for large temporary files. If the current environment requires a specific output folder, follow that requirement and disclose that the result is still on C. Keep existing project source locations unchanged.

## TH4 — tự chạy trọn kế hoạch

- Khi người dùng yêu cầu chạy/làm full TH4 (mặc định khi chỉ nói “tiếp tục TH4”), tự điều phối hết DAG trong `docs/TH4-plan.md` tới khi tất cả task `DONE` hoặc có rủi ro cần dừng; không yêu cầu người dùng gửi lại prompt sau mỗi task. Chỉ làm một task khi người dùng nêu rõ đúng một task. Trước tiên đọc `docs/TH4-progress.md`; sau đó chỉ đọc file task và quyết định cần thiết. Không đọc lại cả DOCX/lịch sử chat nếu không có mâu thuẫn cần xác minh.
- Bảng trạng thái trong progress là nguồn duy nhất về trạng thái. File task là nguồn về phạm vi, readiness và nghiệm thu; plan là nguồn về thứ tự/phụ thuộc. Không tạo bản sao các bảng này.
- Khi chạy full, chọn task `IN_PROGRESS` trước. Nếu không có, tạo wave từ các task `TODO` có `Sẵn sàng: Có` và tất cả phụ thuộc `DONE`. Không bỏ qua một blocker để làm task sau. Tự giải quyết contract suy ra được từ code và TH4; ghi quyết định/ảnh hưởng vào file task. Chỉ dừng hỏi người dùng trước thao tác phá/hủy dữ liệu, rủi ro bảo mật hoặc quyết định không thể khôi phục bằng chứng.
- Nếu có nhiều task `IN_PROGRESS`, trạng thái mâu thuẫn hoặc dependency chưa đạt, đối chiếu git diff/bằng chứng rồi sửa sổ bàn giao trước khi làm. Không tự coi task đã xong.
- Task `BLOCKED` chỉ tiếp tục khi kiểm tra lại thấy điều kiện gỡ chặn đã đạt; khi đó chuyển `IN_PROGRESS`. Nếu chưa đạt, báo ngắn lý do và dừng.
- Trước khi sửa, chạy `git status --short`, đọc diff liên quan, kiểm tra code và mọi caller bị ảnh hưởng. Không ghi đè thay đổi có sẵn của người dùng.
- Chia wave tối đa 2 task song song, chỉ khi đường dẫn ghi khác nhau và task không phụ thuộc nhau. Chọn Worktrunk (`git-wt`) nếu có; trên Windows `wt.exe` có thể là Windows Terminal, không gọi như Worktrunk. Nếu Worktrunk không có, dùng Codex-managed worktrees khi sẵn có, nếu không dùng `git worktree`. Mỗi agent sở hữu worktree/branch riêng và chỉ file của task; không chạy agent ghi code trong checkout chung. Dựng một worktree tích hợp riêng để hợp nhất. Đọc toàn bộ diff từng task và chạy review/check sau khi tích hợp; báo cáo của agent không phải bằng chứng.
- Được chạy các task kế tiếp trong cùng turn sau khi nghiệm thu task trước; không chờ người dùng giữa task. Agent điều phối là writer duy nhất cho `docs/TH4-progress.md` và trạng thái. Agent con nhận brief rút gọn có objective, file được sở hữu, contract liên quan và acceptance; chúng bàn giao commit hash/diff, file đã đổi, lệnh/kết quả, blocker. Chỉ coordinator đổi trạng thái sau kiểm tra diff/tests.
- Làm trọn task, gồm callers, config, fixture/test bị ảnh hưởng. Không push/deploy, không reset/xóa dữ liệu hoặc gộp vào main nếu chưa được người dùng chỉ định. Có thể tạo nhánh/commit/worktree cục bộ cho điều phối và hợp nhất vào branch của task; giữ branch gốc sạch.
- Chỉ ghi `DONE` khi tất cả tiêu chí nghiệm thu đạt với bằng chứng kiểm tra thực tế. Lệnh chưa chạy không phải PASS; thiếu test không phải chứng minh nghiệp vụ đúng. Không dùng skip-tests/continue-on-error để che lỗi.
- Nếu thiếu contract hoặc cần thay đổi ngoài phạm vi, ghi `BLOCKED`, quyết định còn thiếu và điều kiện gỡ chặn. Lỗi môi trường chỉ ghi là lỗi môi trường; không kết luận code sai hoặc đạt.
- Trước khi kết thúc, cập nhật bảng progress và thay thế mục bàn giao gần nhất: file/hành vi đổi, lệnh + exit code/kết quả, thiếu sót/rủi ro và bước tiếp theo cụ thể. Giữ ngắn, không chép log dài, secret hay dữ liệu cá nhân.
- Nếu bị gián đoạn, lượt sau xác minh code và chạy lại kiểm tra liên quan; không tin kết quả cũ khi code đã đổi. Nếu cả bộ task `DONE`, báo hoàn tất và dừng.
- GitHub CI chỉ xác minh code; không tự cập nhật progress hay chuyển task sang DONE. Nếu local thiếu runtime nhưng CI có, lưu check `NOT RUN`, không giả làm PASS.
- Yêu cầu trực tiếp mới của người dùng được ưu tiên; quy trình này không tự cho phép thực hiện task TH4 khi người dùng chỉ yêu cầu thiết lập docs/CI.
