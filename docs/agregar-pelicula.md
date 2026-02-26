# Cómo añadir una película nueva

## Qué necesitas
- El archivo de la película en formato `.mp4` o `.mkv`
- FFmpeg instalado ([ffmpeg.org](https://ffmpeg.org/download.html))
- PowerShell (ya lo tienes en Windows)
- Wrangler configurado (`npm install` dentro de `cloudflare-worker/`)

---

## Paso 1 — Elegir un slug

El **slug** es el identificador interno de la película. Se usa como ruta en R2 y como ID en Firestore.

Reglas:
- Solo letras minúsculas, números y guiones
- Sin tildes ni espacios
- Corto y descriptivo

| Título | Slug sugerido |
|--------|---------------|
| Frozen | `frozen` |
| El Rey León | `el-rey-leon` |
| Toy Story 3 | `toy-story-3` |

---

## Paso 2 — Convertir a HLS

Abre PowerShell en la raíz del repo y ejecuta:

```powershell
cd scripts
.\convert-to-hls.ps1 -InputFile "C:\ruta\pelicula.mp4" -Slug "el-slug-elegido"
```

La conversión **no re-codifica el vídeo** (es rápida). Al terminar verás los archivos en:
```
scripts/output/el-slug-elegido/
  ├── master.m3u8
  ├── segment000.ts
  ├── segment001.ts
  └── ...
```

---

## Paso 3 — Subir a R2

```powershell
# (sigue en la carpeta scripts/)
.\upload-hls-to-r2.ps1 -Slug "el-slug-elegido"
```

El script sube `master.m3u8` y todos los segmentos a Cloudflare R2 bajo `movies/el-slug-elegido/`.

> Si es la primera vez, asegúrate de estar autenticado en Wrangler:
> ```powershell
> cd ..\cloudflare-worker
> npx wrangler login
> ```

---

## Paso 4 — Crear el documento en Firestore

Firebase Console → **Firestore** → colección `movies` → **Añadir documento**

| Campo | Tipo | Valor |
|-------|------|-------|
| ID del documento | — | `el-slug-elegido` |
| `title` | string | `El nombre visible en la app` |
| `manifestPath` | string | `movies/el-slug-elegido/master.m3u8` |
| `category` | string | `princesas` / `aventuras` / `familia` |
| `year` | number | `2013` |
| `duration` | number | `102` *(minutos)* |

> El campo `manifestPath` debe coincidir exactamente con la ruta en R2.

---

## Paso 5 — Verificar

1. Abre la app
2. La película nueva debe aparecer en el catálogo
3. Pulsa para reproducir y comprueba que carga

---

## Limpieza opcional

Una vez verificado que la película funciona en la app, puedes borrar los archivos locales generados:

```powershell
Remove-Item -Recurse scripts/output/el-slug-elegido
```

---

## Resumen rápido

```
1. Elegir slug
2. .\convert-to-hls.ps1 -InputFile "pelicula.mp4" -Slug "slug"
3. .\upload-hls-to-r2.ps1 -Slug "slug"
4. Crear doc en Firestore → movies/slug
5. Probar en la app
```
