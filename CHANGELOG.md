# Changelog

All notable changes to DeviceManager are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Versioning notes

- Root `package.json` holds the product version for tracking (`1.1.0`).
- Backend Maven `pom.xml` `<version>` should stay aligned on release.
- Frontend `frontend/package.json` may remain `0.0.0` for the Angular workspace; product version is the root one.
- **Bump process:** update Unreleased → new `## [x.y.z] - YYYY-MM-DD`, set root `package.json` + `backend/pom.xml` version, commit, tag `vX.Y.Z`, push tag (`.github/workflows/release.yml` creates the GitHub Release from this file).

## [Unreleased]

### Security

- Remove plaintext password remember on the frontend; purge legacy `dm_remember_*` keys.
- Demo `admin`/`tech` seeding only outside `production`; force password change on first login.
- Introduce Flyway (baseline + must_change_password + refresh_token); `ddl-auto=validate` in prod; remove `SchemaMigrationRunner`.
- Drop CORS wildcard `*.onrender.com`; replace `xlsx` with `exceljs`.
- Login rate limit; access + refresh JWT (HttpOnly cookie); HTTP security headers; upload magic-byte checks.
- CI: ESLint, OWASP Dependency-Check, Trivy image scan, prod smoke + GitHub Environment; OpenAPI/springdoc; Playwright scaffold; Testcontainers IT; semver changelog.

## [1.0.0] - 2026-01-01

### Added

- Initial DeviceManager application (Angular frontend + Spring Boot backend).
