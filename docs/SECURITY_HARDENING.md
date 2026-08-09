# Security hardening (phases 1–4)

Résumé du durcissement DeviceManager. Aucun secret dans le dépôt : utiliser `.env*`, secrets GitHub et Environment `production`.

## Phase 1 — Sécurité bloquante

- Suppression du stockage du mot de passe en clair (`rememberCredentials` / `dm_remember_pass`) + purge legacy.
- Seed comptes démo `admin` / `tech` uniquement hors profil `production` ; `mustChangePassword=true` au premier login.
- Flyway (`V1` baseline, `V2` must_change_password, `V3` refresh_token) ; `ddl-auto=validate` en prod ; suppression de `SchemaMigrationRunner`.
- CORS : plus de wildcard `*.onrender.com` ; origines exactes via `APP_CORS_ALLOWED_ORIGINS` (+ localhost en dev).
- Remplacement de `xlsx` par `exceljs`.

## Phase 2 — Renforcement

- Rate limiting sur `POST /api/auth/login`.
- JWT access court + refresh en cookie HttpOnly (`dm_refresh`) ; endpoints `/refresh`, `/logout`, `/change-password`.
- En-têtes HTTP (CSP, HSTS en prod, Referrer-Policy, Permissions-Policy, etc.) côté API + meta CSP front.
- Validation magic bytes des images uploadées.

## Phase 3 — Outillage & CI/CD

- ESLint frontend (`npm run lint`) intégré à la CI.
- OWASP Dependency-Check Maven (`failBuildOnCVSS=7`) + `npm audit` (bloquant **critical** en prod deps ; high en rapport — patchs Angular 19 incomplets).
- Scan Trivy de l’image Docker backend (CRITICAL/HIGH).
- Smoke post-déploiement `/actuator/health` + GitHub Environment `production`.
- Swagger/OpenAPI (springdoc) sur `/swagger-ui.html` / `/v3/api-docs`.

## Phase 4 — Qualité continue

- Tests guards frontend + Playwright smoke (`frontend/e2e`, skip si pas d’`E2E_BASE_URL`).
- Testcontainers MySQL (`AuthLoginIT`, `skipITs=true` par défaut).
- Versionnement sémantique : `CHANGELOG.md`, version `1.1.0`, workflow release sur tags `v*`.

## Déploiement prod — checklist

1. `SPRING_PROFILES_ACTIVE=production` et `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.
2. `APP_CORS_ALLOWED_ORIGINS` = URL exacte du front (https, sans wildcard).
3. Première montée Flyway : `baseline-on-migrate=true` (déjà configuré) sur DB existante.
4. Créer l’Environment GitHub `production` + variable `API_URL`.
5. Compte admin prod : créer manuellement (plus de seed démo) avec un mot de passe fort.

## Commandes locales

```bash
cd frontend && npm run lint && npm run test:ci
E2E_BASE_URL=http://localhost:4200 npm run e2e

cd backend && mvn -B test
cd backend && mvn -B -DskipITs=false verify   # Docker requis
cd backend && mvn -B -DskipTests org.owasp:dependency-check-maven:check
```
