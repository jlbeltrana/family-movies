# Convierte un archivo de vídeo (MP4 o MKV) a HLS para subir a R2.
# Uso: .\convert-to-hls.ps1 -InputFile "ruta\pelicula.mp4" [-OutputDir "ruta\salida"] [-Slug "mi-pelicula"]
# Requiere: FFmpeg en el PATH (https://ffmpeg.org/download.html)
#
# La salida queda en una carpeta con master.m3u8 y segmentos .ts, lista para subir a R2
# bajo la ruta: movies/<slug>/ (ej. movies/mi-pelicula/master.m3u8)

param(
    [Parameter(Mandatory = $true, HelpMessage = "Archivo de vídeo de entrada (MP4 o MKV)")]
    [string]$InputFile,
    [Parameter(Mandatory = $false)]
    [string]$OutputDir = "",
    [Parameter(Mandatory = $false, HelpMessage = "Nombre corto para la ruta en R2 (ej. mi-pelicula). Por defecto se deduce del nombre del archivo.")]
    [string]$Slug = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $InputFile)) {
    Write-Error "No se encuentra el archivo: $InputFile"
    exit 1
}
$ext = [System.IO.Path]::GetExtension($InputFile).ToLowerInvariant()
if ($ext -notin ".mp4", ".mkv") {
    Write-Error "Solo se admiten archivos .mp4 y .mkv. Recibido: $ext"
    exit 1
}

# Slug para la ruta en R2: movies/<slug>/master.m3u8
if ([string]::IsNullOrWhiteSpace($Slug)) {
    $Slug = [System.IO.Path]::GetFileNameWithoutExtension($InputFile)
    $Slug = $Slug -replace "[^a-zA-Z0-9\-_]", "-" -replace "-+", "-" -replace "^-|-$", ""
    if ([string]::IsNullOrWhiteSpace($Slug)) { $Slug = "video" }
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $scriptDir = Split-Path $MyInvocation.MyCommand.Path -Parent
    $OutputDir = Join-Path $scriptDir "output" $Slug
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)

# Comprobar FFmpeg
$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) {
    Write-Error "FFmpeg no está en el PATH. Instálalo desde https://ffmpeg.org/download.html"
    exit 1
}

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
$manifestPath = Join-Path $OutputDir "master.m3u8"

Write-Host "Entrada:  $InputFile"
Write-Host "Salida:   $OutputDir"
Write-Host "Ruta R2:  movies/$Slug/master.m3u8 (y segmentos .ts)"
Write-Host "Convirtiendo (sin re-codificar vídeo, solo empaquetado HLS)..." -ForegroundColor Cyan

# -c copy = sin re-codificar (rápido); -hls_time 6 = segmentos ~6 s; -hls_list_size 0 = listar todos
& ffmpeg -i $InputFile -c copy -f hls -hls_time 6 -hls_list_size 0 -hls_segment_filename (Join-Path $OutputDir "segment%03d.ts") $manifestPath 2>&1 | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Error "FFmpeg falló con código $LASTEXITCODE"
    exit 1
}

Write-Host "Listo. Archivos en: $OutputDir" -ForegroundColor Green
Write-Host "Para subir a R2, desde la raíz del repo:"
Write-Host "  cd cloudflare-worker"
Write-Host "  npx wrangler r2 object put family-movies-media/movies/$Slug/master.m3u8 --file=../scripts/output/$Slug/master.m3u8"
Write-Host "  (y un put por cada segment*.ts, o usa el dashboard de Cloudflare R2 para subir la carpeta.)"
