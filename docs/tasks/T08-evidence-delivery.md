# T08 — Kiểm thử tích hợp và tài liệu nộp

Phụ thuộc: T05, T06, T07
Sẵn sàng: Có

Wave E, chỉ mở sau T05, T06, T07 review/hợp nhất. Đây là final integration/QA,
không chạy song song implementation. Ghi lại mọi tiêu chí còn BLOCKED/NOT RUN.

## Mục tiêu và phạm vi

Chạy hệ thống với dữ liệu giả, hoàn thiện docs/data-and-service-design.md và minh chứng tái lập được cho TH4. Không chỉ chép kế hoạch thành báo cáo đã đạt. Không dùng dữ liệu thật, không thêm UI hay security framework ngoài phạm vi.

## Quyết định

Tài liệu nộp có đúng 10 nội dung: (1) danh sách service, (2) DB từng service, (3) entity với field/kiểu/PK/ràng buộc/bắt buộc, (4) bảng sở hữu dữ liệu, (5) API, (6) giao tiếp liên service, (7) Mermaid sequence diagram, (8) Mermaid database diagram, (9) nghiệp vụ end-to-end, (10) tình huống lỗi. Sơ đồ phân biệt REST đồng bộ và Kafka bất đồng bộ.

Luồng phải thể hiện Client → Transfer → Account → Account DB → Account → Transfer → Client, đồng thời Transfer ghi Transfer DB. Bằng chứng riêng cho Kafka cập nhật tiền/lịch sử, không gộp thành distributed transaction.

### Contract chốt tại T01

- Thêm compose.th4-test.yml độc lập (không override volume/data người dùng), project bank-th4-test, named volumes riêng, cổng host Account18081/Transfer18082/History18084/MySQL13306/Kafka39092/Redis16379. T02 chịu config ba compose hiện hữu; T08 tạo compose test và scripts kiểm chứng. Healthcheck phải dùng công cụ có thật trong image; không dùng curl khi runtime image không có.
- Start: docker compose --env-file <env-gia> -p bank-th4-test -f compose.th4-test.yml up -d --build; stop: cùng prefix down (không -v). Không dùng fixed container_name, không dừng service ngoài project; dọn volume cần ủy quyền riêng. SMTP không cần bật Notification để kiểm chứng; không gửi mail thật.
- Seed script tạo user giả th4_alice/th4_bob/th4_empty, email miền example.invalid, password giả chỉ trong runtime input không in bằng chứng. POST /api/accounts/users, POST /api/accounts {"userId":<id>,"accountNumber":"TH4-A","accountName":"TH4 Alice","balance":50000000}, tương tự TH4-B balance1000000. User th4_empty không có account để CRUD deletion. Loan fixture POST /api/loans/apply với accountId động, amount10000000/termMonths12/purpose "TH4 fixture"; POST /api/loans/{id}/approve cho happy path, không dùng endpoint gây lỗi production. Ghi ID động từ response, không hardcode PK.
- Request suite scripts/th4-e2e.ps1, chạy pwsh -NoProfile -File scripts/th4-e2e.ps1 -EnvFile <env-gia> -OutputDirectory D:\Codex\outputs\bank-th4\<run-id>, fail-fast assertions: valid Transfer POST 200/PROCESSING; missing account404 sau T04; bad amount/null/blank400; tắt duy nhất account-service project test rồi POST unique new transaction→503, restart và chờ healthy; timeout dùng HTTP stub riêng trong test client, không làm chậm service người dùng. Mọi nhánh lỗi SQL transaction count giữ nguyên. Không kiểm tra 503 bằng duplicate request.
- Poll Kafka transfer SUCCESS trong timeout hữu hạn (60s), SQL assert Alice balance giảm1000/Bob tăng1000, History API hai account chỉ đúng transaction; phân biệt REST accepted và Kafka eventual completion. Không giả rằng REST200 nghĩa đã commit debit/credit. Script timeout thất bại phải exit nonzero.
- SQL assertions: đúng schema bảng/bản ghi; account user không SELECT/UPDATE transfer/history (và hoán vị); User pagination/filter có LIMIT/OFFSET; backup-copy-restore fixture T02 và rollback MySQL T07 reports. SQL chọn field cần chứng minh, không dump users.password/OTP hay dữ liệu thật.
- E2E base URLs http://localhost:18081,18082,18084; DB13306 credentials từ env giả. Deliverables D:\Codex\outputs\bank-th4 theo run-id: command/exit/status/count, redacted API/SQL evidence và Maven Surefire/Failsafe summary. Docs nguồn docs/data-and-service-design.md có đủ10 mục; liên kết artifact thay vì chép logs.
- Toàn bộ 7 lệnh Maven và frontend ci/lint/build theo plan chạy lại Java17/Node22; thêm Account -Pmysql-it verify (kèm pagination tests T05) và History/Transfer unit/web tests. CI mặc định giữ unit/web, thêm job MySQL integration trên Ubuntu Docker -Pmysql-it; E2E Kafka chạy riêng có bằng chứng và docs lệnh, không tuyên bố CI E2E khi chưa tích hợp.

## Nghiệm thu và kiểm tra

- Chạy REST hợp lệ, resource không tồn tại (404), Account tắt/timeout (503), request sai (400); lỗi không tạo giao dịch mới. Không tắt service của môi trường người dùng đang dùng.
- Chứng minh bản ghi từ API có trong đúng DB; từng user không truy cập DB service khác; rollback và pagination/query đúng.
- Chạy toàn bộ Maven verify và frontend npm ci/lint/build ở [plan](../TH4-plan.md). Ghi số test và các kiểm tra end-to-end đã chạy riêng.
- Tài liệu đủ 10 mục, sơ đồ khớp code, request tái lập được với dữ liệu giả; bằng chứng không chứa secret/PII. Deliverable sinh thêm đặt dưới D:\Codex\outputs\bank-th4, source docs giữ trong repo.
- Chỉ đánh dấu T08 DONE khi mọi minh chứng đạt; nếu môi trường chưa cho phép test thì BLOCKED, không viết kết luận hoàn thành TH4.
