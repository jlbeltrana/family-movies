# Cómo agregar una película nueva a Lucy Movies

## Qué necesitas antes de empezar
- El archivo de la película en formato `.mp4` o `.mkv`
- Una imagen para la carátula (poster) en `.jpg` o `.png`
- Conexión a internet estable (la subida puede tardar si la película es grande)

---

## Paso 1 — Elegir un slug

El **slug** es el identificador interno de la película. Se usa como carpeta en R2 y como ID en Firestore. Una vez elegido **no se puede cambiar** sin re-subir todo.

**Reglas:**
- Solo letras minúsculas, números y guiones
- Sin tildes, espacios ni caracteres especiales
- Corto y descriptivo

| Título | Slug |
|--------|------|
| Zootopia 2 | `zootopia-2` |
| Frozen | `frozen` |
| El Rey León | `el-rey-leon` |
| Toy Story 3 | `toy-story-3` |
| Moana 2 | `moana-2` |

---

## Paso 2 — Convertir a HLS

Abre PowerShell y ejecuta el script de conversión:

```powershell
cd "C:\Repositorios Personales\family-movies\scripts"
.\convert-to-hls.ps1
```

El script te pregunta todo de forma interactiva:
1. **Pega la ruta** del archivo de video (puedes arrastrarlo a la ventana de PowerShell)
2. **Confirma o escribe el slug** (sugiere uno automáticamente basado en el nombre del archivo)

El script convierte el video a HLS automáticamente:
- El video se copia sin re-codificar (rápido)
- El audio se convierte a AAC estéreo (compatible con todos los móviles)
- Los segmentos quedan en `scripts/output/el-slug/`

> ⏱ **Tiempo estimado:** 2-10 minutos dependiendo del tamaño del archivo

Al terminar verás los archivos en:
```
scripts/output/el-slug/
  ├── master.m3u8       ← manifiesto principal
  ├── segment000.ts
  ├── segment001.ts
  └── ...               ← cientos de segmentos según la duración
```

### Problemas frecuentes en la conversión

| Error | Solución |
|-------|----------|
| `ffmpeg no está en el PATH` | Instala FFmpeg: `winget install Gyan.FFmpeg`, luego abre una terminal nueva |
| `No se encuentra el archivo` | Verifica que la ruta no tenga comillas raras, usa arrastar-soltar |
| FFmpeg falla sin mensaje claro | El archivo puede estar corrupto, prueba con otro |

---

## Paso 3 — Preparar la carátula (poster)

Antes de subir, prepara la imagen de la carátula:

- Formato recomendado: **JPG**, proporción **2:3** (vertical, tipo portada de película)
- Tamaño recomendado: **600×900 px** mínimo
- Nómbrala como quieras, el script te pide la ruta

Puedes descargar carátulas de buena calidad en [The Movie DB (themoviedb.org)](https://www.themoviedb.org) → busca la película → Images → Posters.

---

## Paso 4 — Subir a R2

```powershell
cd "C:\Repositorios Personales\family-movies\scripts"
.\upload-hls-to-r2.ps1
```

El script te pide:
1. El **slug** de la película (el mismo que usaste en la conversión)
2. Al terminar la subida, la **ruta de la carátula** (poster.jpg)

### Función de retomar subida

Si se corta la conexión durante la subida, **no empieces de cero**. El script guarda automáticamente el progreso. Cuando lo vuelvas a ejecutar con el mismo slug, detecta dónde quedó y continúa desde ahí.

```
[23/1078] movies/el-slug/segment022.ts  ← si se corta aquí
...
→ Vuelve a correr el script → retoma desde [24/1078]
```

> ⏱ **Tiempo estimado:** 30-90 minutos para una película de 100 min (depende de la velocidad de internet)

### Problemas frecuentes en la subida

| Error | Solución |
|-------|----------|
| `fetch failed` | Error de red transitorio, el script reintenta automáticamente 3 veces |
| `wrangler not found` | Ejecuta `npm install` dentro de `cloudflare-worker/` |
| `Error: not authenticated` | Ejecuta `cd ..\cloudflare-worker && npx wrangler login` |

---

## Paso 5 — Crear el documento en Firestore

Ve a **Firebase Console → Firestore Database → colección `movies` → Agregar documento**

**ID del documento:** el slug exacto (ej. `zootopia-2`)

| Campo | Tipo | Ejemplo | Notas |
|-------|------|---------|-------|
| `title` | string | `Zootopia 2` | Nombre que aparece en la app |
| `manifestPath` | string | `movies/zootopia-2/master.m3u8` | Siempre `movies/{slug}/master.m3u8` |
| `posterPath` | string | `movies/zootopia-2/poster.jpg` | Siempre `movies/{slug}/poster.jpg` |
| `category` | string | `aventuras` | Ver categorías abajo |
| `year` | **number** | `2025` | Debe ser tipo número, no texto |
| `duration` | **number** | `107` | Minutos, tipo número, no texto |

> ⚠️ **Importante:** `year` y `duration` deben ser tipo **number** en Firestore, no string. Al crear el campo en la consola, selecciona "number" en el desplegable.

### Categorías disponibles

| Categoría | Usar para |
|-----------|-----------|
| `aventuras` | Películas de acción y aventura |
| `animacion` | Películas animadas en general |
| `princesas` | Películas de princesas Disney |
| `familia` | Películas para toda la familia |
| `musical` | Películas con canciones |

Puedes usar cualquier categoría — los tabs del catálogo se generan automáticamente con las que existan en Firestore.

---

## Paso 6 — Verificar en la app

1. Abre la app Lucy Movies
2. Arrastra hacia abajo en el catálogo para **recargar** (pull to refresh)
3. La película nueva debe aparecer con su carátula
4. Toca la carátula → verifica que muestra la pantalla de detalle (título, año, duración)
5. Pulsa **▶ Reproducir** → verifica que carga y se ve bien

---

## Paso 7 — Limpieza (opcional)

Una vez que verificaste que la película funciona, puedes borrar los archivos locales para liberar espacio en disco (los originales en R2 siguen intactos):

```powershell
Remove-Item -Recurse "C:\Repositorios Personales\family-movies\scripts\output\el-slug"
```

---

## Agregar un usuario nuevo a la app

Si quieres que alguien más pueda usar la app:

1. Ve a **Firebase Console → Firestore → config → allowedEmails**
2. En el campo `emails` (tipo Map), agrega una nueva entrada:
   - **Campo:** el email exacto de la persona (ej. `lucia@gmail.com`)
   - **Tipo:** boolean
   - **Valor:** `true`
3. Comparte el APK con esa persona (archivo `app-debug.apk`)
4. En el teléfono de destino: Settings → Apps → activar "Instalar apps de fuentes desconocidas"

---

## Resumen rápido

```
1. Elegir slug (ej. "moana-2")
2. cd scripts && .\convert-to-hls.ps1
3. .\upload-hls-to-r2.ps1  (incluye subir el poster)
4. Firebase Console → Firestore → movies → nuevo doc con el slug
5. Abrir app → pull to refresh → verificar
6. Borrar scripts/output/moana-2  (opcional)
```

---

## Notas técnicas

- **Audio Dolby Atmos / EAC3:** el script de conversión lo convierte automáticamente a AAC, no hay que hacer nada especial
- **Resolución:** se recomienda 1080p máximo. 4K no es necesario y genera archivos muy grandes
- **Subtítulos:** el script los ignora automáticamente (no son compatibles con el formato HLS usado)
- **Formato del video:** MP4 y MKV son compatibles. Otros formatos pueden requerir conversión previa
