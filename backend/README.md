# Backend — Firebase (Auth, Firestore, Cloud Functions)

Backend para Family Movies: autenticación Google, Firestore (catálogo, progreso, allowlist) y la Cloud Function **getPlayToken** que emite el JWT para el Worker R2.

## Requisitos

- Node.js 20+
- Cuenta Firebase
- Mismo **JWT_SECRET** que en el Worker de Cloudflare

## Guía completa

Sigue **[docs/phase3-firebase.md](../docs/phase3-firebase.md)** para:

1. Crear proyecto Firebase y activar Auth (Google)
2. Crear Firestore y desplegar reglas
3. Crear el documento `config/allowedEmails` con los emails permitidos
4. Configurar y desplegar Cloud Functions (secret JWT, WORKER_BASE_URL)
5. Probar getPlayToken

## Estructura

- `firestore.rules` — reglas de seguridad (solo usuarios en allowlist leen movies y progress)
- `firestore.indexes.json` — índices (vacío por ahora)
- `functions/index.js` — Cloud Function callable **getPlayToken(movieId)**
- `functions/.env.example` — ejemplo de variables (WORKER_BASE_URL)

## Comandos rápidos

```bash
cd backend
firebase use TU_PROYECTO_FIREBASE   # o edita .firebaserc
firebase deploy --only firestore:rules
cd functions && npm install
firebase functions:secrets:set JWT_SECRET
firebase deploy --only functions
```
