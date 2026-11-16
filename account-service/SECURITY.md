# KT1 account security

Required external files: `SECURITY_PUBLIC_KEY` points to an X.509 RSA public PEM; `SECURITY_PRIVATE_KEY` points to a matching PKCS#8 unencrypted RSA private PEM (at least 2048 bits). Keys are read at startup; missing, invalid, or mismatched files fail startup. Do not commit private keys. Other resource servers receive only the public key.

Login issues RS256 bearer JWTs with issuer `bank-kt1`, audience `bank-api`, numeric user ID subject, `roles: [USER|ADMIN]`, and a 900 second lifetime. Response fields: `accessToken`, `tokenType`, `expiresIn`, `role`, `account`. No refresh token is provided. Public login/OTP/register/password recovery/check-exists/health routes are defined in `AccountSecurityConfiguration`.

Every password write uses BCrypt. Existing plaintext passwords are rejected at login; reset through the OTP flow or use a newly created isolated test account. No legacy database is migrated by this change. A persisted `users.role` column must exist, and existing users must be assigned `USER` through a reviewed additive migration before using an existing database. Requests cannot assign roles; registration and admin user creation default to `USER`. Initial test administrators must be provisioned outside public APIs.

Administrators create/list/delete users, create accounts, approve loans, and may read/manage user profiles and read accounts/loans. Users access their own profiles, accounts and loans. Loan application/repayment require the account owner even for an administrator. Authenticated recipient lookup `/api/accounts/lookup/{number}` returns only `accountNumber` and `accountName`. Passwords are excluded from entity serialization and all HTTP user responses use `UserProfile`.

Shared JWT configuration is opt-in via `@Import(BankJwtConfiguration.class)` and an explicit resource-server starter dependency. Notification does not opt in. Security failures use structured 401/403 responses; no authentication exception is returned as a generic 500.

Dedicated tests cover real signed JWT issuance/verification/tampering/wrong key, missing or mismatched key files, BCrypt write paths, legacy plaintext denial, HTTP authentication/authorization, safe lookup, owner-only loan mutation, and administrator ownership limits. Persistence integration tests mock the token infrastructure because those tests exercise database behavior, not HTTP authentication.
