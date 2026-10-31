# TH4 evidence

Collected on 2026-10-19/20; copied for portable submission, not rerun during packaging.

- [Build summary](T08-20261019/build/build-summary.json): 11 successful commands. Log fields name original local logs, not bundled files.
- [Final test XML](T08-20261019/build/reports): 46 unit/web tests and 8 MySQL IT, zero failures/errors/skips. Machine properties and stdout/stderr removed; testcase names and counts unchanged.
- [Integrated E2E](T08-20261020-integrated/e2e/e2e-summary.json): 41 checks; adjacent safe SQL, metadata and Compose command results.
- [Coordinator summary](T08-20261020-integrated/coordinator-summary.json).
- [Pagination SQL excerpt](t05-verify-r3.log.sql-excerpt.txt), [rollback SQL excerpt](T07-mysql-verify-r2.log.sql-excerpt.txt), and T05/T07 XML folders. Excerpts keep SQL statements and test result lines, exclude bind values.
- [Legacy copy/restore fingerprints](T08-20261019/T02-retained-restore-check.json). No DB dumps included.

Remote CI and browser UI: NOT RUN. Credentials, env files, DB dumps and runtime volumes excluded. Source commit and SHA256 inventory are in SUBMISSION.json at package root.
