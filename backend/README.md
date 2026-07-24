# Backend DeviceManager

Projet Maven Spring Boot 3 (Java 21).

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
```

API : http://localhost:8080
