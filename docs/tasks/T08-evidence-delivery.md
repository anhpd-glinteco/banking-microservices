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

## Quyết định triển khai (2026-10-19)

- Project test riêng `bank-th4-test`, không fixed container_name, volume theo TH4_FIXTURE_ID ngẫu nhiên; giữ mọi volume sau down/retry. Fixture T02 cũ vẫn chạy nên lượt này override MySQL/Kafka/Redis thành23306/49092/26379, giữ app18081/18082/18084; tất cả bind loopback. Không stop/ghi/reset project cũ.
- Dockerfile test chỉ copy jar sau Maven verify; dùng Temurin17 JRE Alpine, đã kiểm tra wget có thật. Kafka4 KRaft dùng listener nội bộ kafka:9092 và EXTERNAL localhost:49092. Volume Kafka phải mount `/var/lib/kafka/data` tồn tại và appuser sở hữu trong image; lần đầu mount đường dẫn /tmp chưa tồn tại lỗi permission, giữ nguyên volume thất bại và retry bằng identity mới.
- `th4-init.ps1` tạo credentials giả ngoài repo; loader chỉ nhận đủ key allowlist, từ chối duplicate/key lạ, port trùng/ngoài range, identity sai và password ngoài dạng generated. Validate map trước khi đổi processenv. `th4-self-check.ps1` kiểm parser, sáu case env không an toàn và composeconfig; không in credentials.
- `th4-e2e.ps1` dựng infra, tái sử dụng scripts/db/provision.ps1, rồi dựng app; chặn fixture đã có dữ liệu trước seed, không overwrite. Account stop/restart chỉ trong project test và restart trong finally. Script fail-fast, ghi exit/check/count/API error shape và SQL an toàn; không dump payload/password/OTP.
- `th4-verify.ps1` chạy đủ7 lệnh Maven, thêm Account mysql-it và frontend ci/lint/build; giữ log/exit/XML counts. Modules không test ghi build-only. CI thêm Ubuntu Docker mysql-it gate >0test,0failure/error/skipped và upload Failsafe; remoteCI chưa chạy vì không push. Không tự ghi progress.
- Tài liệu đúng10 nội dung ở docs/data-and-service-design.md, phân biệt DBconstraint với validationHTTP và FK thật/logical, REST đồng bộ/Kafka bất đồng bộ, ghi rõ auth/plaintext và DB-event non-atomic baseline.

- Readiness MySQL phải là authenticated TCP SELECT1: mysqladmin localhost/socket có thể healthy ở temporary initserver trước khi TCP/grant sẵn sàng. PowerShell HTTP đặt Accept application/json để Actuator không trả vendor MIME dưới dạng Byte[]; giữ timeout60s thật, không bỏ health gate. Các lượt setup lỗi giữ evidence exit1 và volume riêng; không coi là backend lỗi hay PASS.
- E2E ghi riêng Compose arguments/exit/log an toàn (config chỉ --quiet), API ErrorResponse shape và count; History poll đợi TRANSFER_COMPLETED cho cả owner trước khi so alias để tránh race event còn đến.

## Bằng chứng nghiệm thu (2026-10-20)

- Runner final source: 11 lệnh exit0 (common +6 service, Account mysql-it, frontend ci/lint/build). Unit/web Account11/Transfer27/History8; MySQL IT8, tất cả0failure/error/skipped. Common/Notification/Gateway/Discovery chỉ build-only. Coordinator đã kiểm XML và SHA256 ba jar; build-summary.json, coordinator-build-check.json, verified-jars.json trong D:/Codex/outputs/bank-th4/T08-20261019.
- E2E-r5 exit0,41 checks: CRUD/pagination, approve loan, REST PROCESSING→Kafka SUCCESS; SQL cuối Alice59999000.00/Bob1001000.00; History3 event mỗi owner;400/404/503 không tăng transaction count1; Account restart healthy. Năm bảng owner đọc được,12 SELECT/UPDATE chéo bị từ chối,0FK xuyên schema. SQL/metadata không chứa password/OTP/raw payload.
- Các lần trước exit1 do cấu hình Kafka volume, MySQL readiness và HTTP helper content-type; đã sửa root cause trong test setup và giữ nguyên evidence/volume. Không tính chúng là PASS; không sửa production backend ở T08.
- Self-check5 parser/6 unsafe-env cases/config và actionlint exit0. Metadata thật xác nhận History LONGTEXT và Loan enum; tài liệu10 mục có schema/API constraints và diagrams. T02 retained source/restore khớp manifest5 bảng bằng kiểm tra chỉ đọc.
- Chưa push/deploy/merge main; remote CI NOT RUN. Coordinator đã review toàn diff, chạy actionlint/self-check exit0 và E2E sau tích hợp exit0,41 checks/5 Compose actions exit0; SQL cuối sau restart Account đúng. Bằng chứng: D:/Codex/outputs/bank-th4/T08-20261020-integrated/coordinator-summary.json. T08 đủ tiêu chí nghiệm thu; trạng thái duy nhất ở progress.
