# Fase 3: Backend Firebase (Auth, Firestore, getPlayToken)

Pasos para tener el backend listo: proyecto Firebase, Firestore con allowlist y catálogo, y la Cloud Function que emite el JWT de reproducción para el Worker.

## 1. Crear proyecto Firebase

1. Entra en [Firebase Console](https://console.firebase.google.com/) y crea un proyecto (o usa uno existente).
2. Anota el **ID del proyecto** (ej. `family-movies-xxx`).
3. En el repo, edita `backend/.firebaserc` y sustituye `TU_PROYECTO_FIREBASE` por ese ID.

## 2. Activar Authentication (Google)

1. En Firebase Console → **Authentication** → **Sign-in method**.
2. Activa **Google** y configura la pantalla de consentimiento si te lo pide (nombre del proyecto, email de soporte).
3. Guarda.

## 3. Crear Firestore

1. En [Firebase Console](https://console.firebase.google.com/) abre tu proyecto.
2. En el menú lateral (Build), entra en **Firestore Database**.
3. Pulsa **Create database** (o “Crear base de datos”).
4. **Modo de inicio:** elige **Start in production mode** (empezar en modo producción). Así las reglas bloquean todo hasta que despliegues las nuestras. Pulsa **Next**.
5. **Ubicación:** elige una región (ej. `europe-west1` o `us-central1`). No se puede cambiar después. Pulsa **Enable**.
6. Espera a que la base de datos esté lista (unos segundos).
7. **Reglas:** en la pestaña **Rules** de Firestore, borra lo que haya y pega el contenido de `backend/firestore.rules` del repo. Pulsa **Publish** (Publicar).

Listo: ya tienes Firestore creado. Las colecciones (`config`, `movies`, `users`) se crean solas al añadir el primer documento en cada una.

## 4. Documento de allowlist (emails permitidos)

En Firestore, crea un documento:

- **ID del documento:** `allowedEmails` (dentro de la colección **`config`**).
- **Campos:** un mapa llamado **`emails`** donde cada clave es un email y el valor `true`:
  - Ejemplo: `emails` → mapa → `"tu@gmail.com"` = `true`, `"otro@gmail.com"` = `true`.

Desde la consola: Colección `config` → Añadir documento → ID `allowedEmails` → Añadir campo `emails` (tipo map) → en el map, añade entradas como `tu@gmail.com` = `true`.

## 5. Película de prueba (opcional)

Para probar getPlayToken, crea un documento en la colección **`movies`**:

- ID: por ejemplo `test` (o el que uses en la app como `movieId`).
- Campos:
  - `title`: "Prueba"
  - `manifestPath`: `movies/test/master.m3u8` (o la ruta real en R2 que ya subiste en Fase 1, ej. `movies/prueba-fase2/master.m3u8`)
  - `isNew`: false
  - `createdAt`: timestamp (opcional)

## 6. Cloud Functions: configuración

Desde la carpeta **`backend`**:

```bash
cd backend
npm install -g firebase-tools
firebase login
cd functions
npm install
```

**Secret JWT (mismo valor que en el Worker):**

```bash
firebase functions:secrets:set JWT_SECRET
```

Cuando pida el valor, pega **exactamente** el mismo string que usaste en `wrangler secret put JWT_SECRET` (Cloudflare Worker).

**URL del Worker:**

La función usa la variable `WORKER_BASE_URL`. Opciones:

- **Opción A — Variable de entorno en deploy:** crea `functions/.env` (no la subas al repo) con:
  ```
  WORKER_BASE_URL=https://family-movies-worker.TU_SUBDOMINIO.workers.dev
  ```
  En Firebase Gen 2, las variables definidas con `defineString` pueden leerse desde el entorno. Si no se configuran, al desplegar puedes definirlas en Google Cloud Console (Cloud Functions → tu función → Variables de entorno).

- **Opción B — Google Cloud Console:** después del primer deploy, en Google Cloud Console → Cloud Functions → función `getplaytoken` → Editar → Variables de entorno → Añadir `WORKER_BASE_URL` = tu URL del Worker.

## 7. Desplegar

```bash
cd backend
firebase deploy --only firestore:rules
firebase deploy --only functions
```

Si la función falla por `WORKER_BASE_URL` no definido, configúrala como en el paso 6 y vuelve a desplegar.

## 8. Probar getPlayToken

Desde una app (o Postman) que tenga un **Firebase ID token** (usuario logueado con Google y en la allowlist):

- **Callable:** nombre de la función `getPlayToken`, datos `{ "movieId": "test" }` (o el ID del documento de la película que creaste).
- Respuesta esperada: `{ "token": "...", "baseUrl": "https://..." }`.

Puedes usar la extensión **Firebase REST** o un pequeño script con el SDK de Firebase (Admin o client) que llame a la callable. En la Fase 4 la app Android llamará a esta función con el ID token y usará `token` y `baseUrl` para reproducir el HLS en ExoPlayer.

## Resumen de colecciones/documentos

| Ruta | Uso |
|------|-----|
| `config/allowedEmails` | Mapa `emails`: { "email@ejemplo.com": true }. Solo tú escribes (consola). |
| `movies/{movieId}` | Catálogo: title, manifestPath, posterUrl, isNew, createdAt. |
| `users/{uid}/progress/{movieId}` | positionMs (y opcional updatedAt) para reanudar. |

## Reglas (resumen)

- Solo pueden leer `movies` y su progreso los usuarios autenticados cuyo email esté en `config/allowedEmails.emails`.
- Nadie escribe en `config` ni en `movies` desde la app (solo desde la consola o un admin).
- Cada usuario solo lee/escribe `users/{uid}/progress` con su propio `uid`.
