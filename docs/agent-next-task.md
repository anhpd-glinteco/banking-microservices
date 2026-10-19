# Prompt tự điều phối full TH4

Dán một lần để agent hoàn thành toàn bộ DAG trong kế hoạch; không cần gọi lại sau mỗi task hoặc đính kèm DOCX. Agent được tự chạy tới hết các task nghiệm thu được. Dừng chỉ khi có rủi ro dữ liệu/bảo mật cần quyết định, hoặc blocker không thể giải quyết bằng code/repo/CI.

```text
Chạy FULL kế hoạch TH4 trong cùng lượt chat. Tự điều phối tuần tự và chia song song khi an toàn; không dừng giữa các task đã nghiệm thu.

Đọc AGENTS.md và docs/TH4-progress.md trước. Plan là DAG và file task là brief;
chỉ đọc brief task sẵn sàng, không đọc lại toàn bộ DOCX, chat hay tất cả task.
Làm hết T01–T08 theo phụ thuộc và trạng thái đã ghi; nếu task ready theo task file
nhưng trạng thái progress còn TODO, tự chạy. Suy ra contract an toàn từ repo,
chốt thành ruling trong task file và tiếp tục. Chỉ hỏi/dừng nếu phải sửa/xóa dữ liệu
không thể khôi phục hoặc có quyết định bảo mật/nghiệp vụ không thể suy luận an toàn.

Kiểm tra git status, branch và diff; giữ nguyên mọi thay đổi của người dùng.
Không làm trực tiếp trên branch chính hay checkout chung khi chạy agent song song.
Chọn git-wt/Worktrunk nếu cài; trên Windows `wt.exe` có thể là Windows Terminal,
hãy kiểm tra binary trước. Fallback Codex-managed worktree, rồi `git worktree`.
Tạo worktree tích hợp từ branch hiện tại; mọi worktree task khác nhau theo branch.
Giới hạn 2 agent implement cùng lúc; không chạy cùng lúc task phụ thuộc hoặc
đụng cùng file. Đặt ownership file vào brief. Agent con không sửa progress, không
merge, không tự mở agent khác; yêu cầu handoff ngắn: commit/diff, file, check +
exit code, blocker. Coordinator review diff độc lập, hợp nhất mỗi wave, chạy check
rồi mới mở wave phụ thuộc tiếp theo. Nếu không có runtime/agent/worktree, chạy
tuần tự ở worktree tích hợp hoặc ghi BLOCKED/NOT RUN; không bịa PASS.

Coordinator là writer duy nhất của progress; cập nhật sau mỗi task đã review.
Thực hiện đủ code/caller/config/test, chạy mọi check nghiệm thu. Chỉ DONE khi
từng tiêu chí có bằng chứng. Lệnh không chạy ghi NOT RUN với lý do, không suy
đoán. Lỗi tìm được: sửa ở task sở hữu nó, chạy lại check, rồi tiếp tục DAG.
Không skip tests, không sửa/bỏ gate để CI xanh. Không push/deploy, reset dữ liệu
hoặc gộp vào main. Khi kết thúc, chạy suite liên quan đầy đủ và review diff tổng.

Trước khi dừng, cập nhật từng trạng thái và thay mục bàn giao trong progress:
task DONE/BLOCKED, files, lệnh/exit code, CI link nếu có, quyết định/rủi ro và
bước tiếp theo. Tóm tắt toàn đợt: task hoàn tất, test thật đã chạy, blockers,
phần chưa kiểm chứng. Không tuyên bố TH4 hoàn tất nếu task/evidence còn thiếu.
```
