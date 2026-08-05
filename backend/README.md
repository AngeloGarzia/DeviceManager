# Backend DeviceManager

Projet Maven Spring Boot 3.5 (Java 21).

## Variables d'environnement (backend uniquement)

Les secrets ne sont **jamais** exposés au frontend.

| Fichier | Usage |
|---------|--------|
| `.env.development` | Développement (défaut) |
| `.env.production` | Production |
| `.env.*.example` | Modèles versionnés (sans secrets) |

Chargement auto via `DotEnvEnvironmentPostProcessor` :
- défaut → `.env.development`
- `APP_ENV=production` ou `--spring.profiles.active=prod` → `.env.production`

```powershell
# Dev
mvn spring-boot:run

# Prod
$env:APP_ENV="production"
mvn spring-boot:run
# ou
java -jar target/device-manager-1.0.0.jar --spring.profiles.active=prod

# Qualité : SpotBugs 4.10.3 + Checkstyle 13.8.0 + JaCoCo 0.8.15
mvn -DskipTests verify
# Individuellement :
mvn -DskipTests compile spotbugs:check
mvn -DskipTests checkstyle:check
mvn test jacoco:report
# Rapports : target/site/spotbugs.html, target/site/checkstyle.html, target/site/jacoco/index.html
# CodeQL : workflow GitHub Actions `.github/workflows/codeql.yml` (Action v4)
```

API : http://localhost:8080
