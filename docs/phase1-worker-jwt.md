# Fase 1: Worker R2 + JWT

Documentación del Worker que sirve vídeo HLS desde R2 usando un token JWT en el header.

## URL del Worker

Tras desplegar con `wrangler deploy` desde `cloudflare-worker/`, la URL base será:

```
https://family-movies-worker.<TU_SUBDOMINIO>.workers.dev
```

Sustituye `<TU_SUBDOMINIO>` por el subdominio de tu cuenta Cloudflare (ej. `cloudflare-account.workers.dev`). Puedes ver la URL exacta en el output de `wrangler deploy` o en el dashboard de Workers.

## Formato del JWT

El Worker exige el header:

```
Authorization: Bearer <JWT>
```

El JWT debe estar firmado con **HS256** y el mismo secret configurado en el Worker (`JWT_SECRET`).

### Claims

| Claim   | Tipo     | Obligatorio | Descripción |
|--------|----------|-------------|-------------|
| `exp`  | number   | Sí          | Expiración (timestamp en segundos, ej. `Math.floor(Date.now()/1000) + 7200` para 2 h). |
| `prefix` | string | No          | Path permitido en R2. La petición solo podrá acceder a objetos cuya clave empiece por este prefix. Ej: `"movies/mi-pelicula/"` permite `movies/mi-pelicula/master.m3u8` y `movies/mi-pelicula/segment0.ts`. |

Si no se incluye `prefix`, el token da acceso a cualquier path (útil para pruebas; en producción el backend debe emitir siempre un `prefix` por película).

## Configuración

### 1. Crear el bucket R2

En Cloudflare Dashboard → R2 → Create bucket. Nombre sugerido: `family-movies-media`. No habilites acceso público.

En `cloudflare-worker/wrangler.toml` el binding está como:

```toml
[[r2_buckets]]
binding = "MEDIA"
bucket_name = "family-movies-media"
```

Si usas otro nombre de bucket, cambia `bucket_name`.

### 2. Configurar el secret JWT

El Worker necesita un secret compartido con el backend (Firebase Cloud Functions) para firmar y verificar el JWT:

```bash
cd cloudflare-worker
npx wrangler secret put JWT_SECRET
```

Introduce un string aleatorio largo (ej. generado con `openssl rand -base64 32`). **El mismo valor** se configurará después en Firebase (Cloud Functions) como variable de entorno o secret.

### 3. Desplegar el Worker

```bash
cd cloudflare-worker
npm install
npm run deploy
```

## Prueba paso a paso (primera vez)

Sigue estos pasos en orden. Necesitas: **FFmpeg** instalado (para generar el HLS de prueba), el **mismo JWT_SECRET** que pusiste en el Worker, y la **URL de tu Worker** (la que salió al hacer `npm run deploy`).

---

### Paso 1 — Crear un HLS de prueba en tu PC

Abre una terminal en la carpeta del repo.

**Opción A — Tienes FFmpeg instalado**

Genera un vídeo HLS de unos segundos (sin necesidad de tener un MP4; FFmpeg crea uno de prueba):

```bash
cd cloudflare-worker
mkdir -p test-hls
cd test-hls
ffmpeg -f lavfi -i color=c=blue:s=640x360:d=5 -f lavfi -i anullsrc=r=44100:cl=stereo -c:v libx264 -t 5 -pix_fmt yuv420p -c:a aac -f hls -hls_time 2 -hls_list_size 0 master.m3u8
```

En Windows (PowerShell), el equivalente es crear la carpeta y ejecutar ffmpeg desde ahí:

```powershell
cd cloudflare-worker
New-Item -ItemType Directory -Force -Path test-hls | Out-Null
cd test-hls
ffmpeg -f lavfi -i color=c=blue:s=640x360:d=5 -f lavfi -i anullsrc=r=44100:cl=stereo -c:v libx264 -t 5 -pix_fmt yuv420p -c:a aac -f hls -hls_time 2 -hls_list_size 0 master.m3u8
```

Verás que se generan `master.m3u8` y varios `.ts`. Quédate en la carpeta `test-hls`.

**Opción B — No tienes FFmpeg (descargar un HLS de prueba)**

Hay un script que descarga un HLS desde una URL y deja en `test-hls` el `master.m3u8` y todos los segmentos (`segment0.ts`, `segment1.ts`, …). Solo tienes que pasar la URL del .m3u8:

```powershell
cd cloudflare-worker
node scripts/download-test-hls.js "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
```

El script descarga el manifest, detecta todos los archivos `.ts` que enlaza, los descarga y los guarda con nombres `segment0.ts`, `segment1.ts`, etc., y reescribe el manifest como `master.m3u8`. Si la URL es un “master playlist” (que enlaza a otro .m3u8 por calidad), el script sigue el enlace y usa esa variante. Al terminar tendrás la carpeta `test-hls` lista para subir a R2 (Paso 2).

---

### Paso 2 — Subir los archivos a R2

Desde la carpeta `cloudflare-worker/test-hls/` (donde están `master.m3u8` y los `.ts`).

**Con Wrangler — script automático (recomendado):**

Desde la carpeta `cloudflare-worker/` (después de haber generado los archivos en `test-hls/`):

```powershell
cd cloudflare-worker
.\scripts\upload-test-hls.ps1
```

El script sube todo lo que haya en `test-hls/` a R2 bajo `movies/test/` (master.m3u8, master0.ts, etc.).

**Con Wrangler a mano (una orden por archivo):**

Sustituye `family-movies-media` si tu bucket tiene otro nombre. Usa los nombres reales que haya en `test-hls/` (pueden ser `master0.ts`, `segment0.ts`, etc.):

```powershell
cd cloudflare-worker\test-hls
$bucket = "family-movies-media"
npx wrangler r2 object put "$bucket/movies/test/master.m3u8" --file=master.m3u8
npx wrangler r2 object put "$bucket/movies/test/master0.ts" --file=master0.ts
# ... uno por cada .ts
```

**Con el dashboard de Cloudflare:** R2 → tu bucket → Upload → sube todos los archivos de `test-hls`. Al subir, asegúrate de que la “ruta” o “key” sea `movies/test/master.m3u8`, `movies/test/master0.ts`, etc. (algunos dashboards permiten elegir un prefijo de carpeta al subir varios archivos).

---

### Paso 3 — Generar el token

En la carpeta `cloudflare-worker` (no dentro de `test-hls`), usando **exactamente el mismo secret** que configuraste con `wrangler secret put JWT_SECRET`:

**Windows (PowerShell):**

```powershell
cd cloudflare-worker
$env:JWT_SECRET = "PEGA_AQUI_EL_MISMO_SECRET_QUE_PUSISTE_EN_WRANGLER"
node scripts/generate-test-token.js "movies/test/"
```

**Windows (CMD):**

```cmd
set JWT_SECRET=PEGA_AQUI_EL_MISMO_SECRET_QUE_PUSISTE_EN_WRANGLER
node scripts/generate-test-token.js "movies/test/"
```

**Mac/Linux:**

```bash
cd cloudflare-worker
JWT_SECRET="tu-secret-aqui" node scripts/generate-test-token.js "movies/test/"
```

El token es la primera línea que imprime el script (sin el texto de “Prefix” ni “Expira”). El token es la **primera línea** que imprime el script: un JWT completo (cadena larga con **dos puntos**, tipo `eyJ...xxx.eyJ...yyy.zzz`). Si copias a mano, no cortes nada (debe tener más de 50 caracteres).

**En PowerShell, guardar el token en variable** evita errores al copiar (el script escribe el token en stdout y "Prefix..." en stderr):

```powershell
$token = (node scripts/generate-test-token.js "movies/test/" 2>$null).Trim()
$token.Length   # debe ser > 50
Invoke-RestMethod -Uri "https://TU_WORKER_URL/movies/test/master.m3u8" -Headers @{ Authorization = "Bearer $token" }
```

Si recibes "Invalid token": (1) que `JWT_SECRET` al generar sea **exactamente** el mismo que en `wrangler secret put JWT_SECRET`; (2) que uses el JWT entero, no un fragmento.

**Atajo:** desde `cloudflare-worker/` puedes ejecutar `.\scripts\test-worker.ps1`. El script te pedirá el JWT_SECRET (o usará la variable de entorno si está definida), generará el token y probará el Worker. Si la URL de tu Worker no es `family-movies-worker.lucyb.workers.dev`, edita la variable `$workerUrl` dentro del script.

---

### Paso 4 — Probar con curl

Sustituye en los comandos:

- `TU_WORKER_URL` → la URL de tu Worker (ej. `https://family-movies-worker.mi-cuenta.workers.dev`).
- `TU_TOKEN` → la línea que copiaste en el paso 3.

**Probar el manifest (.m3u8):**

```bash
curl -H "Authorization: Bearer TU_TOKEN" "https://TU_WORKER_URL/movies/test/master.m3u8"
```

Deberías ver el contenido del archivo (texto con líneas como `#EXTM3U` y nombres de segmentos). Si ves `401 Unauthorized`, el token no se está enviando bien o el secret no coincide. Si ves `404`, la ruta en R2 no es correcta (revisa que subiste `movies/test/master.m3u8`).

**Si en Windows curl da error de certificado (CRYPT_E_NO_REVOCATION_CHECK):** usa una de estas alternativas:

- Con curl desactivando la comprobación de revocación:
  ```powershell
  curl.exe --ssl-no-revoke -H "Authorization: Bearer TU_TOKEN" "https://TU_WORKER_URL/movies/test/master.m3u8"
  ```
- Con PowerShell (sin curl):
  ```powershell
  Invoke-RestMethod -Uri "https://TU_WORKER_URL/movies/test/master.m3u8" -Headers @{ Authorization = "Bearer TU_TOKEN" }
  ```

**Probar un segmento (.ts):**

```bash
curl -H "Authorization: Bearer TU_TOKEN" "https://TU_WORKER_URL/movies/test/master0.ts" -o segmento.ts
```

(En Windows con PowerShell, `curl` puede ser un alias de Invoke-WebRequest. Prueba con `curl.exe` si hace falta.) Si todo va bien, el archivo `segmento.ts` se descarga y tiene tamaño > 0.

**Probar sin token (debe fallar con 401):**

```bash
curl "https://TU_WORKER_URL/movies/test/master.m3u8"
```

La respuesta debe ser `401 Unauthorized`.

---

## Resumen rápido (cuando ya tengas HLS en R2)

- **Generar JWT:** `JWT_SECRET=tu-secret node cloudflare-worker/scripts/generate-test-token.js "movies/test/"`
- **Probar:** `curl -H "Authorization: Bearer <token>" "https://TU_WORKER_URL/movies/test/master.m3u8"`

Sin header o con token inválido recibirás 401. Si el path no coincide con el `prefix` del JWT, 403.

## Resumen para el backend (Fase 3)

Cuando implementes la Cloud Function **getPlayToken**:

1. Usa el **mismo** `JWT_SECRET` que configuraste en el Worker.
2. Genera un JWT con `exp` (ej. ahora + 2 h) y `prefix` = el path de la película en R2 (ej. `movies/slug-pelicula/`).
3. Devuelve a la app: `{ token: "<JWT>", baseUrl: "https://family-movies-worker....workers.dev" }`.
4. La app construye la URL del manifest como `baseUrl + "/" + manifestPath` y envía el token en `Authorization: Bearer` en todas las peticiones (manifest y segmentos).
