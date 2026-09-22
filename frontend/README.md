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
npm run build -- --configuration=production
```

Artifacts go to `dist/frontend/browser`.

### Clever Cloud / CI — esbuild deadlock

On Clever (and some containers), `ng build` can fail with:

```text
fatal error: all goroutines are asleep - deadlock!
… esbuild … ThreadSafeWaitGroup.Wait …
```

Cause: esbuild (Go) + Angular workers mis-detect CPU/threads under cgroups; also seen when RAM is tight. Local builds are usually fine.

Mitigations in this repo:

1. **`overrides.esbuild`: `0.28.2`** — patch release that fixes a known deadlock (was `0.28.0` via Angular 19.2.27).
2. **`npm run build`** sets (via `cross-env`, Windows + Linux):
   - `GOMAXPROCS=1`
   - `NG_BUILD_MAX_WORKERS=1`
   - `UV_THREADPOOL_SIZE=1`
   - `ESBUILD_WORKER_THREADS=0`
3. Production config already sets `optimization.styles.inlineCritical: false` (less CSS pipeline pressure). Prefer `NODE_OPTIONS=--max-old-space-size=1536` on Clever if OOM persists.

Angular CLI / build-angular stay on **19.2.27** (latest 19.x). Clever `CC_BUILD_COMMAND` can keep calling `npm run build -- --configuration=production` — the env vars are baked into the script.

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
