# Scripts — Conversión MP4/MKV a HLS y subida a R2

Estos scripts sirven para preparar películas y subirlas al bucket R2 para la app Family Movies.

## Requisitos

- **FFmpeg** en el PATH ([descarga](https://ffmpeg.org/download.html); en Windows puedes añadirlo al PATH o usar [ffmpeg en winget](https://winget.run/pkg/Gyan.FFmpeg)).
- **Node.js** (para `wrangler` al subir a R2)
- **Wrangler** (`npm install -g wrangler` o `npx wrangler` desde un proyecto que lo tenga, ej. `cloudflare-worker/`)

## Convención de rutas en R2

Todas las películas se guardan bajo:

```
movies/<slug>/master.m3u8    ← manifest HLS
movies/<slug>/segment000.ts
movies/<slug>/segment001.ts
...
```

- **`<slug>`**: nombre corto sin espacios ni caracteres raros (ej. `mi-pelicula`, `coco-2024`).
- En Firestore, cada documento de la colección `movies` tendrá un campo **`manifestPath`** con el valor `movies/<slug>/master.m3u8`.

## 1. Convertir un vídeo a HLS

Desde la carpeta `scripts/`:

**Windows (PowerShell):**

```powershell
.\convert-to-hls.ps1 -InputFile "C:\ruta\a\pelicula.mp4"
# Con slug y carpeta de salida opcionales:
.\convert-to-hls.ps1 -InputFile "pelicula.mkv" -Slug "coco" -OutputDir ".\mis-salidas\coco"
```

**Mac/Linux:**

```bash
chmod +x convert-to-hls.sh
./convert-to-hls.sh /ruta/a/pelicula.mp4
# O: ./convert-to-hls.sh pelicula.mp4 ./salida mi-slug
```

- Admite **MP4** y **MKV**.
- Por defecto no re-codifica el vídeo (`-c copy`), solo lo empaqueta en HLS (rápido).
- La salida se escribe en `scripts/output/<slug>/` (o la carpeta que indiques) con `master.m3u8` y `segment000.ts`, `segment001.ts`, etc.

## 2. Subir el HLS a R2

Después de convertir, sube la carpeta generada a tu bucket R2.

**Opción A — Script (desde `scripts/`, con Wrangler disponible):**

Necesitas tener `wrangler` en el PATH (por ejemplo desde `cloudflare-worker` con `npx`). Si wrangler está en `../cloudflare-worker`:

```powershell
cd scripts
$env:PATH = "..\cloudflare-worker\node_modules\.bin;$env:PATH"
.\upload-hls-to-r2.ps1 -Slug "mi-pelicula"
```

O desde `cloudflare-worker/` puedes subir una carpeta externa así (ajustando rutas):

```powershell
cd cloudflare-worker
# Subir cada archivo de scripts/output/mi-pelicula a movies/mi-pelicula/
$slug = "mi-pelicula"
$dir = "..\scripts\output\$slug"
Get-ChildItem $dir -File | ForEach-Object {
  npx wrangler r2 object put "family-movies-media/movies/$slug/$($_.Name)" --file $_.FullName
}
```

**Opción B — Dashboard de Cloudflare**

1. Entra en R2 → tu bucket (`family-movies-media`).
2. Upload → selecciona todos los archivos de `scripts/output/<slug>/` (master.m3u8 y todos los .ts).
3. Si el dashboard permite “prefix” o “carpeta”, usa `movies/<slug>/` para que las claves queden como `movies/<slug>/master.m3u8`, etc.

## 3. Registrar la película en Firestore

Cuando el HLS esté en R2, añade un documento en la colección **`movies`** (Fase 3) con al menos:

- `title`: título para la app
- `manifestPath`: `movies/<slug>/master.m3u8`
- `posterUrl` o `posterPath`: URL o ruta del póster (opcional)
- `isNew`: `true` si quieres que salga como “Nueva”

## Resumen rápido

1. `.\convert-to-hls.ps1 -InputFile "pelicula.mp4"` → genera `output/<slug>/`
2. Subir el contenido de `output/<slug>/` a R2 bajo `movies/<slug>/`
3. En Firestore, crear documento en `movies` con `manifestPath: "movies/<slug>/master.m3u8"`
