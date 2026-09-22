# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 19.2.27.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

```bash
npm run build
```

(`package.json` → `ng build --configuration=production`. Artifacts : `dist/frontend/browser`.)

### Clever Cloud / CI — esbuild deadlock

On Clever (and some containers), `ng build` can fail with:

```text
fatal error: all goroutines are asleep - deadlock!
… esbuild … ThreadSafeWaitGroup.Wait …
```

Cause: esbuild Go service-mode deadlock, often worse when **several esbuild versions** coexist in `node_modules` (concurrent native binaries). Forced single-thread env vars (`GOMAXPROCS=1`, etc.) did not help and were removed.

Mitigation in this repo:

1. **`overrides.esbuild`: `^0.28.2`** — one resolved version for the whole tree (latest stable compatible with Angular 19.2.27 as of this write-up).
2. After changing overrides: delete `node_modules` + `package-lock.json`, then `npm ci --include=dev`, and verify with `npm ls esbuild` (single version, no extras).
3. Prefer enough RAM on Clever (`NODE_OPTIONS=--max-old-space-size=1536`) if OOM-related deadlocks persist.

Angular CLI / build-angular stay on **19.2.27** (latest 19.x). Clever `CC_BUILD_COMMAND` can keep calling `npm run build` (or `npm run build -- --configuration=production`).

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
