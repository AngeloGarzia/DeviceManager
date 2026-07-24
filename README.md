# DeviceManager

Application **mobile-first** pour inventorier les pièces détachées des machines à sous (casino).

## Structure

```
DeviceManager/
├── backend/
│   ├── .env.development      ← secrets DEV (gitignored)
│   ├── .env.production       ← secrets PROD (gitignored)
│   ├── .env.*.example        ← modèles versionnés
│   ├── pom.xml
│   └── src/
├── frontend/                 ← aucune variable secrète
├── sql/init.sql
└── docker-compose.yml
```

## Variables d'environnement (backend only)

Toutes les configs sensibles sont dans les fichiers `.env` du **backend**.
Le frontend n'a accès à aucun secret (JWT, BDD, mail, S3, etc.).

| Environnement | Fichier | Activation |
|---------------|---------|------------|
| Développement | `backend/.env.development` | défaut |
| Production | `backend/.env.production` | `APP_ENV=production` |

## Modèle de données

### `device` — pièces détachées
| Colonne | Type |
|---------|------|
| nom | string |
| reference | string |
| usage | string |
| date_acquisition | date |
| obsolete | boolean |
| sfm_id | FK → `sfm` |
| mas_id | FK → `mas` |

### `sfm`
| Colonne | Type |
|---------|------|
| nom | string |
| responsable | string |
| telephone | string |
| email | string |

### `mas` — machines à sous
| Colonne | Type |
|---------|------|
| numero | string |
| marque | string |
| utilise | boolean |

## Architecture backend

```
Controller → Service → Repository → MySQL
                ↓
              DTO
```

Auth : login / mot de passe + JWT expirable.  
Rôles : **ADMIN** / **TECHNICIEN**.

## Lancer

```powershell
# MySQL
docker compose up -d

# Backend (charge .env.development automatiquement)
cd backend
$env:Path = "c:\Users\dell\device-manager\.tools\apache-maven-3.9.9\bin;" + $env:Path
mvn spring-boot:run

# Frontend
cd frontend
npm start
```

Production :
```powershell
cd backend
$env:APP_ENV="production"
mvn spring-boot:run
```

- App : http://localhost:4200  
- API : http://localhost:8080  
- Comptes : `admin` / `admin123` · `tech` / `tech123`

## API

| Méthode | Endpoint |
|---------|----------|
| POST | `/api/auth/login` |
| CRUD | `/api/devices` |
| CRUD | `/api/sfm` |
| CRUD | `/api/mas` |
| POST | `/api/order-requests` |
| GET | `/api/order-requests` (admin) |
