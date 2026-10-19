# Tiến độ TH4

## Trạng thái hiện tại

- **T01–T08 DONE** trên branch `codex/th4-integration`, worktree `D:\Codex\work\bank-th4-codex-th4-integration`.
- Hoàn tất gate local Java17/Node22, MySQL integration và REST/SQL/Kafka E2E sau tích hợp. Remote CI chưa chạy; không push/deploy/merge main.
- Không còn task tiếp theo trong DAG TH4. Giữ nguyên fixture/volume và thay đổi người dùng trong D:\bank.

| ID | Trạng thái | Kết quả hoặc điều kiện tiếp tục |
| --- | --- | --- |
| T01 | DONE | bank-common install + 6 service verify exit 0 (chưa có test); Node22 ci/lint/build exit 0; đủ 7 contract |
| T02 | DONE | MySQL 8 thật: ba schema/user, ma trận quyền, copy/fingerprint/restore fixture; nguồn bank giữ nguyên; API → bank_transfer SELECT được |
| T03 | DONE | Account REST GET thật; transfer POST → MySQL + Kafka SUCCESS, số dư đúng; missing account 404 và Account dừng 503, không tạo row |
| T04 | DONE | Branch tích hợp đã cài common + verify Account 7/7, Transfer 27/27; không failure/error/skip |
| T05 | DONE | CRUD profile; unit/web11 + MySQL IT2, 0 failure/error/skip trên branch tích hợp; SQL filter/LIMIT/OFFSET và FK/no-cascade đạt |
| T06 | DONE | History verify 8/8; MySQL/API/Kafka fixture smoke, backfill và caller frontend đã đối chiếu |
| T07 | DONE | MySQL IT8 + unit11,0failure/error/skip sau tích hợp; rollback sau SQL flush giữ nguyên cả5 trường snapshot |
| T08 | DONE | Full verify11 lệnh exit0; unit/web46 + MySQL IT8,0failure/error/skip; E2E sau tích hợp41 checks PASS, docs10 mục và CI MySQL đã kiểm local |

## Bàn giao gần nhất

- Bản nộp 2026-10-20: thêm evidence/ được chọn lọc, liên kết tương đối và README tái lập; sửa mô tả CI MySQL. Chỉ đổi docs/evidence, không chạy lại runtime. XML đóng gói bỏ machine properties/stdout/stderr, giữ testcase/count. Kiểm tra liên kết, 54 test zero failure/error/skip và 41 E2E checks từ bằng chứng cũ đạt. Được người dùng cho phép fast-forward main cục bộ và tạo submission-20261020.zip; không push/deploy.

- Ngày 2026-10-20 (Asia/Saigon). T01–T08 DONE; branch codex/th4-integration, worktree D:\Codex\work\bank-th4-codex-th4-integration. T05 tích hợp77bc9dd, T07 fea66dd, T08 nguồn7d61b47/tích hợp956c4bd. Không push/deploy/merge main; D:\bank giữ nguyên thay đổi source/SMTP của người dùng.
- Runtime WSL bank-th4/Docker29.9.1 hoạt động; không cần admin/cài lại. Bổ sung Testcontainers test-only1.21.4 cho Docker29. Môi trường/tool/cache trên D; dữ liệu thật/SMTP không dùng, không reset/delete volume. Stack cũ13306/39092/16379 giữ nguyên; test riêng23306/49092/26379 + API18081/18082/18084, loopback, credentials giả ngoài git.
- Full verify final backend/frontend source: 11 lệnh exit0 (common install +6 service verify +Account mysql-it +frontend ci/lint/build). Unit/web Account11/Transfer27/History8 và MySQL IT8,0failure/error/skipped; module không test chỉ build-only. Coordinator đối chiếu XML, source không đổi và SHA256 cả3 jar. Báo cáo D:\Codex\outputs\bank-th4\T08-20261019\build\build-summary.json và coordinator-build-check.json cùng task folder.
- Sau tích hợp ngày20: actionlint exit0; self-check5 parser/6 case env/config exit0; E2E exit0,41 checks,5 Compose actions exit0. REST PROCESSING→Kafka SUCCESS; SQL cuối sau restart Account: Alice59999000.00/Bob1001000.00, History3 event/owner;400/404/503 giữ transaction count1. Owner đọc5 bảng,12 SELECT/UPDATE chéo bị từ chối,0FK xuyên DB. Bằng chứng an toàn D:\Codex\outputs\bank-th4\T08-20261020-integrated\coordinator-summary.json và e2e\{e2e-summary.json,safe-sql.txt,schema-metadata.txt,compose-commands.json}.
- T02 retained bank/bank_restore khớp manifest5 bảng bằng kiểm tra chỉ đọc; T07 chứng minh flush SQL rồi rollback5 trường bằng transaction độc lập. T08 docs/data-and-service-design.md đủ10 mục,47 entity fields, diagram và lệnh tái lập; CI MySQL job có gate executed tests/zero failure/error/skip, remote CI NOT RUN. Các lần setup lỗi được giữ với exit1, không tính PASS.
- Rủi ro/NOT RUN: chưa UI browser/remote CI; lint6 hook warnings, Sass/bundle warnings; npm ci báo25 advisory (1 critical), chưa upgrade dependency. Baseline auth/plaintext và DB-event không atomic; unsupported media có thể500, JSON đúng content-type đã kiểm400. Không coi đây là chứng nhận production.
- Bước tiếp theo: không còn task TH4; giữ artifacts/volumes để review. Muốn chạy lại dùng scripts/th4-init.ps1 tạo env/volume identity mới rồi th4-e2e.ps1; không dùng down -v. Chỉ xử lý main/push/deploy khi người dùng chỉ định riêng.

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
