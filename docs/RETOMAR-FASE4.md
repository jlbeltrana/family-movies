# Retomar el proyecto — Fase 4 (App Android)

Documento para retomar el desarrollo en otra iteración. Estado del proyecto y pasos para continuar con la Fase 4.

---

## Estado actual (hasta aquí)

### Completado

| Fase | Qué hay |
|------|---------|
| **Fase 1** | Worker en Cloudflare que sirve HLS desde R2 con JWT. URL: `https://family-movies-worker.lucyb.workers.dev`. Bucket R2 `family-movies-media`. Secret `JWT_SECRET` configurado en Worker. |
| **Fase 2** | Scripts en `scripts/` para convertir MP4/MKV → HLS (`convert-to-hls.ps1` / `.sh`) y subir a R2 (`upload-hls-to-r2.ps1`). FFmpeg instalado. Convención: `movies/<slug>/master.m3u8`. |
| **Fase 3** | Firebase proyecto **lucy-movies**. Auth con Google. Firestore con reglas (allowlist en `config/allowedEmails`). Cloud Function **getPlayToken** desplegada; JWT_SECRET y WORKER_BASE_URL configurados. |

### Pendiente

- **Fase 4:** App Android (login + reproducción con token).
- **Fases 5–8:** Catálogo en app, progreso, notificaciones, pulir UI.

---

## Datos útiles para retomar

- **Firebase proyecto:** `lucy-movies` (ID en `backend/.firebaserc`).
- **Worker URL:** `https://family-movies-worker.lucyb.workers.dev` (usar esta en la app como baseUrl que devuelve getPlayToken; la función ya la tiene configurada).
- **R2 bucket:** `family-movies-media`. Rutas de vídeo: `movies/<slug>/master.m3u8`.
- **Allowlist:** Firestore → `config` → documento `allowedEmails` → campo `emails` (map: `"email@gmail.com": true`).

---

## Cómo retomar la Fase 4

### Objetivo de la Fase 4

App Android (Kotlin, Jetpack Compose) que:

1. Inicia sesión con **Google** (Firebase Auth).
2. Comprueba si el usuario está en la **allowlist** (documento `config/allowedEmails`); si no, muestra “No tienes permiso”.
3. Tiene una pantalla mínima con un botón “Reproducir prueba” (o similar).
4. Al pulsar: llama a la Cloud Function **getPlayToken(movieId)** con el ID token de Firebase.
5. Recibe `{ token, baseUrl }` y reproduce el HLS en **ExoPlayer** enviando en todas las peticiones el header `Authorization: Bearer <token>`.

### Requisitos previos (ya cubiertos)

- Proyecto Firebase con Auth (Google) y Firestore.
- Función **getPlayToken** desplegada y con WORKER_BASE_URL y JWT_SECRET configurados.
- Al menos un documento en Firestore en `movies` con `manifestPath` (ej. `movies/test/master.m3u8`) para poder pedir un token de esa película.

### Pasos concretos para Fase 4

1. **Crear proyecto Android** en `android-app/`: Kotlin, Jetpack Compose, minSdk 24+.
2. **Añadir Firebase** al proyecto: archivo `google-services.json` (descargar de Firebase Console → Configuración del proyecto → Tus apps → Añadir app Android). Dependencias: `firebase-bom`, `firebase-auth`, `firebase-firestore`, `firebase-functions`.
3. **Pantalla de login:** botón “Entrar con Google” (Firebase Auth). Tras login, leer `config/allowedEmails` (o llamar a una función/endpoint que compruebe allowlist) y si el email no está, mostrar “No tienes permiso” y no permitir navegar.
4. **Pantalla principal mínima:** por ejemplo un botón “Reproducir prueba”. Al pulsar: obtener ID token con `FirebaseAuth.getInstance().currentUser?.getIdToken(false)`, llamar a la callable `getPlayToken` con datos `{ "movieId": "test" }` (o el ID de una película que exista en Firestore), recibir `token` y `baseUrl`.
5. **ExoPlayer:** construir URL del manifest = `baseUrl + "/" + manifestPath` (el manifestPath lo tienes en el documento de la película; para la prueba puedes hardcodear `movies/test/master.m3u8` si esa película existe). Usar un **DataSource.Factory** personalizado que añada el header `Authorization: Bearer <token>` a todas las peticiones (manifest y segmentos .ts). Reproducir el HLS.
6. **Probar** en emulador o tablet: login con cuenta en allowlist → reproducir; con cuenta no permitida → mensaje de no permitido.

### Referencias en el repo

- **Plan completo y tareas:** `docs/IMPLEMENTATION_PLAN.md` (Fase 4, 5, etc.).
- **Worker y JWT:** `docs/phase1-worker-jwt.md` (formato del token, URL del Worker).
- **Firebase backend:** `docs/phase3-firebase.md` (estructura Firestore, getPlayToken).
- **Backend (código):** `backend/functions/index.js` (lógica de getPlayToken).

### Estructura sugerida para android-app

```
android-app/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/.../MainActivity.kt
│       └── res/
├── build.gradle.kts (raíz y app)
└── settings.gradle.kts
```

Módulos/carpetas típicos: `ui.login`, `ui.home`, `ui.player`, `data.firebase`, `data.player` (ExoPlayer + DataSource con token).

---

Al retomar, abrir este archivo y `docs/IMPLEMENTATION_PLAN.md` (Fase 4) y seguir los pasos anteriores. Si algo falla (Auth, callable, ExoPlayer), revisar que el proyecto Firebase sea `lucy-movies`, que WORKER_BASE_URL esté configurada y que la película de prueba exista en Firestore con `manifestPath` correcto.
