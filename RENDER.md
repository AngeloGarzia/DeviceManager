# Déploiement Render — 100 % gratuit (sans carte bancaire)

## Architecture

| Service | Plan | Rôle |
|---------|------|------|
| `device-manager-api` | **Free** | API Spring Boot (Docker) |
| `device-manager-web` | **Free** | Front Angular (static) |
| MySQL | **Aiven Free** (externe) | Base de données — sans CB |

Blueprint : [`render.yaml`](./render.yaml)

> L’API free **s’endort** après ~15 min d’inactivité (1er appel un peu lent).  
> Un **keep-alive** interne pingue `/actuator/health` toutes les **14 min** via l’URL publique
> (`RENDER_EXTERNAL_URL`) pour retarder la veille tant que le service tourne déjà.  
> Les photos sont stockées sur disque éphémère (peuvent disparaître au redéploiement).

---

## 1) Créer MySQL gratuit (Aiven — sans CB)

1. Compte : https://console.aiven.io/signup (GitHub OK, **pas de CB**)
2. **Create service** → **MySQL** → plan **Free**
3. Attendre que le service soit `Running`
4. Onglet **Overview** / **Connection information** → noter :
   - Host
   - Port (souvent `18306` ou similaire, pas 3306)
   - User
   - Password
   - Database name

URL JDBC à construire :

```text
jdbc:mysql://HOST:PORT/DATABASE?sslMode=REQUIRED&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

Exemple :

```text
jdbc:mysql://mysql-xxxxx.a.aivencloud.com:18306/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

---

## 2) Déployer le Blueprint Render

1. https://dashboard.render.com → **New** → **Blueprint**
2. Dépôt `AngeloGarzia/DeviceManager`, branche `main`
3. Render demande de renseigner (Generate / coller) :

| Variable | Valeur |
|----------|--------|
| `APP_JWT_SECRET` | bouton **Generate** |
| `SPRING_DATASOURCE_URL` | URL JDBC Aiven ci-dessus |
| `SPRING_DATASOURCE_USERNAME` | user Aiven |
| `SPRING_DATASOURCE_PASSWORD` | password Aiven |
| `APP_CORS_ALLOWED_ORIGINS` | laisser pour l’instant `https://device-manager-web.onrender.com` (ajuster après) |

4. Lancer le déploiement

---

## 3) CORS après le 1er deploy

1. Copier l’URL réelle du front (ex. `https://device-manager-web.onrender.com`)
2. API → **Environment** → `APP_CORS_ALLOWED_ORIGINS` = cette URL (sans `/` final)
3. **Manual Deploy** de l’API

---

## URLs

- Front : `https://device-manager-web.onrender.com`
- API : `https://device-manager-api.onrender.com`
- Health : `https://device-manager-api.onrender.com/actuator/health`

Comptes seed : `admin` / `admin123` · `tech` / `tech123`

---

## CI/CD GitHub Actions

Workflow : [`.github/workflows/ci-cd.yml`](./.github/workflows/ci-cd.yml)

| Événement | Action |
|-----------|--------|
| PR / push `main` | Build + tests backend (Maven) et frontend (Angular) |
| Push `main` (après CI OK) | Déclenche les **Deploy Hooks** Render |

### Secrets GitHub (Settings → Secrets and variables → Actions)

| Secret | Où le trouver |
|--------|----------------|
| `RENDER_DEPLOY_HOOK_API` | Render → service API → Settings → Deploy Hook → Copy |
| `RENDER_DEPLOY_HOOK_WEB` | Render → Static Site front → Settings → Deploy Hook → Copy |

Optionnel (Variables) : `API_URL` = URL de l’API prod (sinon défaut `https://devicemanager-x5g4.onrender.com`).

### Éviter le double déploiement

Sur Render, désactive **Auto-Deploy** (ou mets-le sur *Commit*) pour API et front si tu utilises les hooks GitHub Actions — sinon un push peut déployer deux fois.

---

## Messagerie (demandes de commande)

Par défaut `APP_MAIL_ENABLED=false` → e-mail **simulé** dans les logs.

### Config recommandée (Brevo gratuit)

1. Compte : https://app.brevo.com  
2. **SMTP & API** → créer une clé SMTP  
3. Dans l’app → **Setup** (admin) :

| Clé | Exemple |
|-----|---------|
| `MAIL_ENABLED` | `true` |
| `MAIL_FROM` | e-mail vérifié Brevo |
| `MAIL_ADMIN_EMAIL` | destinataire admin |
| `MAIL_HOST` | `smtp-relay.brevo.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | login SMTP Brevo |
| `MAIL_PASSWORD` | clé SMTP |

4. Bouton **Tester l'e-mail** puis envoyer une demande de commande.

Sur Render, tu peux aussi mettre ces variables dans l’**Environment** de l’API (elles alimentent le Setup au 1er démarrage).

---

## Spring AI (optionnel)

Assistant chat intégré (`/ai` dans le front, `POST /api/ai/chat`).

**Configuration** :
- Page **Setup** : activer l’IA, choisir fournisseur + modèle
- Clés API : **uniquement** dans `.env` / Environment Render (batterie IA) — pas dans Setup

Les paramètres Setup (activation, fournisseur, modèle) sont stockés en base et pris en compte immédiatement.
La clé utilisée = variable `.env` du fournisseur courant (`GEMINI_API_KEY`, `OPENAI_API_KEY`, `GROQ_API_KEY`, …).

Variables Environment Render / `.env` :

```text
APP_AI_ENABLED=false

# -- Batterie IA --
GEMINI_API_KEY=
OPENAI_API_KEY=
GROQ_API_KEY=
MISTRAL_API_KEY=
OPENROUTER_API_KEY=
DEEPSEEK_API_KEY=
TOGETHER_API_KEY=
FIREWORKS_API_KEY=
AI_DEFAULT_PROVIDER=openai
AI_DEFAULT_MODEL=gpt-4o-mini
```

Exemple Gemini :

```text
APP_AI_ENABLED=true
GEMINI_API_KEY=...
AI_DEFAULT_PROVIDER=gemini
AI_DEFAULT_MODEL=gemini-3.1-flash-lite
```

---

## Photos en production

Sur le plan **free** Render, le disque du conteneur est **éphémère** (perdu à chaque deploy).  
DeviceManager enregistre donc aussi les images dans MySQL (`upload_blob`, via JDBC) et les sert via `/uploads/...` (rehydratation disque au besoin).

- **Nouvelles photos** : durables (disque + MySQL).
- **Anciennes photos** absentes de `upload_blob` : à **re-uploader** une fois (éditer la pièce → remplacer l’image).

---

## Limites du gratuit

| Sujet | Comportement |
|-------|----------------|
| Cold start API | ~30–60 s au réveil |
| Photos | Durables en MySQL (`upload_blob`) ; disque Render = cache |
| MySQL Aiven Free | 1 Go, peut s’éteindre si inactif longtemps |
| Pas de CB Render | OK avec plan `free` uniquement |

---

## Dépannage

| Symptôme | Action |
|----------|--------|
| Render demande une CB | Ancien Blueprint Starter — utiliser la branche `main` à jour (plan `free`) |
| API down / timeout DB | Vérifier Aiven Running + URL JDBC + SSL |
| CORS | `APP_CORS_ALLOWED_ORIGINS` = URL exacte du front |
| Front → mauvaise API | Rebuild du static (variable `API_URL`) |
