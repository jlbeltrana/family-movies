# Plan: App Family Movies para tablet Android

## Estado del proyecto (última actualización)

- **Fases 1, 2 y 3:** completadas (R2 + Worker, scripts HLS, Firebase + getPlayToken desplegada).
- **Próximo paso:** Fase 4 — App Android (login + reproducción con token).
- **Para retomar:** ver **`docs/RETOMAR-FASE4.md`** (resumen de lo hecho, datos clave y pasos concretos para la Fase 4).

---

## 1. Visión del producto

- **App Android nativa** (Kotlin) para tablets: reproducción de películas en streaming.
- **Usuaria principal:** niña de 10 años — UI grande, clara, atractiva y segura.
- **Funciones:** elegir película, reanudar donde se quedó, aviso de película nueva.
- **Decisiones:** R2 + Cloudflare (máximo ahorro); tú administras catálogo y subes películas a R2; solo las cuentas Google que tú autorizas pueden hacer sesión.

---

## 2. Arquitectura: R2 + Cloudflare + Firebase

- **Cloudflare R2** almacena el vídeo; **Worker** exige JWT en `Authorization: Bearer` y sirve desde R2.
- **Firebase:** Auth (Google), Firestore (catálogo, progreso, allowlist), Cloud Functions (**getPlayToken** que emite el JWT).
- La app envía el JWT en todas las peticiones al Worker (manifest y segmentos HLS).

---

## 3. Plan de implementación por fases y tareas

### Fase 1 — Infraestructura R2 + Worker

- [x] **1.1** Crear cuenta Cloudflare (si no tienes) y abrir R2 en el dashboard.
- [x] **1.2** Crear bucket R2 (ej. `family-movies-media`); dejarlo privado.
- [x] **1.3** Crear proyecto Worker en el mismo account; binding al bucket R2 (ver `cloudflare-worker/`).
- [x] **1.4** Implementar en el Worker: leer `Authorization: Bearer <token>`, verificar JWT con `JWT_SECRET`, extraer claim `prefix`; si es válido, servir objeto desde R2 por path de la URL; si no, 401.
- [x] **1.5** Desplegar Worker (`cd cloudflare-worker && npm run deploy`); anotar URL base.
- [x] **1.6** Generar JWT de prueba (`JWT_SECRET=... node cloudflare-worker/scripts/generate-test-token.js "movies/test/"`); subir vídeo HLS de prueba a R2; probar con `curl` que el manifest y segmentos se sirven con el token.
- [x] **1.7** Documentar en `docs/` la URL del Worker, formato JWT y secret compartido con el backend (ver `docs/phase1-worker-jwt.md`).

### Fase 2 — Script de conversión MP4/MKV → HLS

- [x] **2.1** Crear carpeta `scripts/` en el repo.
- [x] **2.2** Script de conversión con FFmpeg (MP4/MKV → HLS): `scripts/convert-to-hls.ps1` y `convert-to-hls.sh`.
- [x] **2.3** Convención de rutas en R2 (ej. `movies/<slug>/master.m3u8`); documentada en `scripts/README.md`.
- [x] **2.4** README con pasos para ejecutar script y subir a R2; script `upload-hls-to-r2.ps1` para subida.
- [x] **2.5** Probar con un MP4 y un MKV (probado: vídeo de 5 s → HLS en `output/prueba-fase2/` y `output/prueba-fase2-mkv/`).

### Fase 3 — Backend Firebase (Auth, Firestore, getPlayToken)

- [x] **3.1** Proyecto Firebase en consola; activar Authentication (Google). Ver `docs/phase3-firebase.md`.
- [x] **3.2** Firestore: estructura `movies`, `users/{uid}/progress`, `config/allowedEmails` (reglas y doc en `backend/`).
- [x] **3.3** Añadir emails a la allowlist (documento `config/allowedEmails` en Firestore, campo `emails` map).
- [x] **3.4** Reglas de Firestore en `backend/firestore.rules` (solo allowlist lee movies y progress).
- [x] **3.5** Cloud Function **getPlayToken** en `backend/functions/index.js` (callable; verifica Auth + allowlist; emite JWT con prefix).
- [x] **3.6** Desplegar (`firebase deploy --only firestore:rules,functions`), configurar JWT_SECRET y WORKER_BASE_URL (hecho al crear la función).

### Fase 4 — App Android base (login + reproducción con token)

- [ ] **4.1** Proyecto Android (Kotlin, Compose); Firebase.
- [ ] **4.2** Login con Google; comprobar allowlist; mensaje “No tienes permiso” si no está.
- [ ] **4.3** Llamar getPlayToken(movieId); recibir JWT y baseUrl.
- [ ] **4.4** ExoPlayer con `DataSource.Factory` que añade `Authorization: Bearer <token>`.
- [ ] **4.5** Probar en emulador/tablet.

### Fase 5 — Catálogo en la app

- [ ] **5.1** Leer `movies` desde Firestore.
- [ ] **5.2** Home: grid de posters; badge “Nueva”.
- [ ] **5.3** Detalle: “Reproducir” → getPlayToken + ExoPlayer.
- [ ] **5.4** Película de prueba en Firestore y R2.

### Fase 6 — Progreso y “Continuar viendo”

- [ ] **6.1** Guardar `positionMs` en Firestore al pausar/salir.
- [ ] **6.2** Leer progreso; botón “Continuar” y `seekTo(positionMs)`.
- [ ] **6.3** Sección “Continuar viendo” en Home.

### Fase 7 — Películas nuevas y notificaciones

- [ ] **7.1** Campo `isNew` en `movies`; badge en app.
- [ ] **7.2** FCM: registrar token en Firestore.
- [ ] **7.3** Cloud Function: onCreate en `movies` con `isNew: true` → enviar FCM.
- [ ] **7.4** Probar notificación.

### Fase 8 — UI/UX para niña de 10 años

- [ ] **8.1** Botones ≥48dp; posters y textos grandes.
- [ ] **8.2** Navegación simple (Home, Detalle, Reproductor).
- [ ] **8.3** Iconos + texto; tema claro; animaciones suaves.
- [ ] **8.4** Reproductor: controles grandes.

---

## 4. Estructura del repositorio

```
family-movies/
├── android-app/
├── backend/
├── cloudflare-worker/   # Worker R2 + JWT (Fase 1)
├── scripts/
└── docs/
```

---

## 5. Resumen de decisiones cerradas

- **Almacenamiento y CDN:** R2 + Cloudflare Worker con JWT.
- **Administración del catálogo:** Tú; subes a R2 y mantienes Firestore.
- **Formatos:** MP4/MKV → HLS con FFmpeg local.
- **Cuentas permitidas:** Lista en Firestore; solo esas cuentas Google pueden usar la app.
