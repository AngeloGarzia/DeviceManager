# Playwright E2E (smoke)

Minimal end-to-end smoke tests for the Angular frontend.

## Setup

```bash
cd frontend
npm install
npx playwright install chromium
```

## Run

Set `E2E_BASE_URL` to the app under test (local or deployed). Without it, the login smoke test **skips**.

```bash
# against local ng serve
E2E_BASE_URL=http://localhost:4200 npm run e2e

# CI
E2E_BASE_URL=https://your-frontend.example npm run e2e:ci
```

On Windows PowerShell:

```powershell
$env:E2E_BASE_URL = "http://localhost:4200"
npm run e2e
```
