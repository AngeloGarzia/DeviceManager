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

On Clever, `ng build` hangs or crashes (`all goroutines are asleep - deadlock!`).  
**Mitigation :** Angular is built on **GitHub Actions**; Clever only serves the committed `frontend/www` tree (no `ng build` on Clever). See [CLEVERCLOUD.md](../CLEVERCLOUD.md).

`overrides.esbuild: ^0.28.2` still keeps a single esbuild version for local / CI builds.

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
