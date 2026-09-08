# Cloudflare R2 — production privée + URLs présignées (DeviceManager)

Le bucket R2 reste **privé** (pas d’URL de développement publique, pas besoin de custom domain).  
L’API Spring génère des **URLs présignées GET** à chaque réponse REST pour que Angular affiche photos/PDF.

---

## Chez Cloudflare

1. **R2** → Create bucket (ex. `devicemanager`)
2. **URL de développement publique** → **OFF**
3. **Domaines personnalisés** → inutile si tu restes 100 % privé
4. **Token API R2** → Object Read & Write sur ce bucket  
   → Access Key ID + Secret Access Key

---

## Variables Render / `.env` API

```env
APP_S3_ENABLED=true
APP_S3_BUCKET=devicemanager
APP_S3_REGION=auto
AWS_ACCESS_KEY_ID=…
AWS_SECRET_ACCESS_KEY=…
AWS_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
APP_S3_PUBLIC_BASE_URL=
APP_S3_PRESIGNED_URL_EXPIRATION_MINUTES=15
APP_S3_PRESIGNED_DOCUMENT_EXPIRATION_MINUTES=60
```

Redémarrer l’API.

| Durée | Usage |
|-------|--------|
| 15 min (défaut) | Photos / vignettes |
| 60 min (défaut) | PDF devis, documents, règles de jeux |

---

## Comportement applicatif

- En base : **uniquement la clé objet** (`photo_key`, `file_key`, …) — jamais l’URL présignée
- À chaque GET API : l’URL d’accès est **régénérée** (TTL court)
- Frontend : `resolvePhotoUrl` gère déjà les `https://…` absolues (présignées)

---

## Vérification

1. Upload une photo dans DeviceManager
2. Bucket R2 → objet `spare-parts/…` présent
3. Réponse API `photoUrl` = URL longue avec `X-Amz-Algorithm` / signature
4. La vignette s’affiche ; après expiration, recharger la page (nouvelle URL)

---

*Ancien mode custom domain (`APP_S3_PUBLIC_BASE_URL`) reste optionnel mais n’est plus requis.*
