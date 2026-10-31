# T01 — Baseline và chốt contract

Phụ thuộc: Không
Sẵn sàng: Có

## Mục tiêu và phạm vi

Xác minh repo có build/lint được và biến các điểm chưa chốt thành contract cụ thể cho T02–T08. Chỉ sửa lỗi baseline tái hiện được bằng các lệnh bên dưới, không thực hiện tính năng TH4 ở task này. Đọc POM, config, controller/service/entity liên quan và caller frontend trước khi quyết định.

## Quyết định phải hoàn tất

- T02: tên DB/user theo service, tất cả compose/config cần đổi; cách backup/copy/verify dữ liệu bank cũ và phục hồi. Không tự chạy migration dữ liệu thật.
- T03: REST endpoint/response Account hiện có, cách resolve địa chỉ, timeout, mapping 404/503, thứ tự kiểm tra trước save/event, giữ contract frontend.
- T04: field bắt buộc, số tiền dương, giới hạn hợp lý, null/blank và error response; xác minh exception Kafka import có đang làm lệch handler common.
- T05: resource CRUD dùng để minh chứng, method/path/status/request/response, filter/sort và giới hạn page/size; chính sách xóa không làm mất sổ giao dịch hoặc phá quan hệ.
- T06: accountId là ID hay số tài khoản theo caller thực tế; cách map event vào lịch sử, chống trả lẫn tài khoản và xử lý dữ liệu history cũ. Chốt API mới/cũ khi cần phân trang.
- T07: chọn nghiệp vụ LoanService có hai lần ghi DB để test rollback thật; chốt test dependency và môi trường test tách biệt, không mock DB thay bằng chứng rollback.
- T08: lệnh khởi chạy, dữ liệu seed giả, kịch bản HTTP/SQL và nơi lưu bằng chứng; chốt điều kiện triển khai test tích hợp vào CI hoặc chạy riêng có ghi rõ.

Ghi contract vào đúng file task sau, thay mục “Cần chốt tại T01”; chỉ đổi `Sẵn sàng: Có` khi đủ quyết định. T01 DONE yêu cầu tất cả task sau đủ contract (phụ thuộc vẫn áp dụng). Nếu chưa giải quyết an toàn được một quyết định, ghi BLOCKED và câu hỏi cụ thể, không coi task xong.

## Nghiệm thu và kiểm tra

- Chạy toàn bộ lệnh ở mục CI/lệnh kiểm tra trong [plan](../TH4-plan.md); lưu exit code và lỗi gốc ngắn. Dùng Java 17/Node 22 để xác minh chuẩn CI; nếu chỉ chạy phiên bản khác phải ghi rõ và chưa coi là tương đương hoàn toàn.
- Cài bank-common trước service; không dùng skipTests. Module chưa có test phải ghi “chưa có test”, không ghi test PASS.
- Nếu có lỗi build/lint nền, sửa tối thiểu và chạy lại check liên quan; lỗi ngoài phạm vi không thể sửa an toàn thì BLOCKED. Không hạ rule, bỏ module hay che lỗi CI.
- Đủ contract và readiness của T02–T08; không sửa nghiệp vụ hoặc chạy migration như thể đã thực hiện các task sau.
- Cập nhật progress với kết quả baseline, phần chưa chạy, quyết định và bước đầu T02.

## Xử lý cấu hình SMTP — 2026-10-19

- Đã phát hiện credential literal trong cấu hình cũ; người dùng xóa khỏi hai file và xác nhận thuộc tác giả gốc. Không chép giá trị vào docs/log, không dùng credential/gửi mail hay rewrite lịch sử.
- Quyết định tiếp tục bên dưới thay thế điều kiện dừng ban đầu; nghiệm thu T01 nằm ở cuối file.

## Quyết định tiếp tục — 2026-10-19

Người dùng xác nhận credential thuộc người khởi tạo project, đã xóa khỏi cấu hình và yêu cầu tiếp tục. Gỡ blocker cho phát triển/kiểm thử cô lập; không dùng credential cũ, không gửi email thật, không rewrite lịch sử. Hai file cấu hình người dùng sửa được giữ nguyên trong worktree tích hợp. Việc thu hồi credential thuộc chủ tài khoản gốc.

## Ruling contract và ownership

- T02–T08 đã có contract cụ thể trong đúng file task; readiness chỉ nghĩa đủ brief, không chứng minh nghiệm thu.
- T03 bỏ hai import Kafka exception trong AccountService để common handler trả 404/409 trước integration test; giải quyết phụ thuộc ngược T03→T04. T04 kiểm tra lại, không cần đổi nghiệp vụ Account tại T03.
- CRUD minh chứng chọn User profile chỉ xóa khi không có account; giữ toàn bộ dữ liệu tài chính. History dùng accountNumber, legacy numeric ID phải resolve REST.
- MySQL thật cô lập qua Testcontainers/profile mysql-it; môi trường Docker chưa chạy thì ghi NOT RUN, không thay bằng mock/H2 để tuyên bố đạt.

## Nghiệm thu T01 — 2026-10-19

- Java Temurin17.0.20.1+1, Maven3.9.9, Node22.23.3 portable; SHA256 JDK/Node và SHA512 Maven khớp nguồn phát hành.
- bank-common install và verify sáu service đều exit0, mỗi module chưa có test (0 report XML). Không đồng nghĩa nghiệp vụ đã được kiểm chứng.
- Sau sửa sáu file frontend, coordinator review toàn bộ diff và chạy lại npm ci/lint/build: exit0; lint0 lỗi/6 cảnh báo hooks. Build cảnh báo Sass/chunk size. Không đổi dependency/rule.
- Assertion bảy task T02–T08 có contract/readiness và không còn placeholder exit0. Hai import Kafka prerequisite thuộc T03 đã chốt rõ.
- Logs: D:\Codex\outputs\bank-th4\T01-*.log. Docker engine chưa chạy; integration thuộc T02 trở đi NOT RUN. Người dùng hỏi khả năng làm không Docker; giữ gate thực tế, không đổi mock thành bằng chứng DB.
