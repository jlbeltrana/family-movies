# Convierte un archivo de video (MP4 o MKV) a HLS para subir a R2.
# Uso interactivo: .\convert-to-hls.ps1
# Uso por parametros: .\convert-to-hls.ps1 -InputFile "ruta\pelicula.mp4" -Slug "mi-pelicula"
# Requiere: FFmpeg en el PATH (winget install Gyan.FFmpeg)

param(
    [string]$InputFile = "",
    [string]$Slug = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Error "FFmpeg no esta en el PATH. Instalalo con: winget install Gyan.FFmpeg"
    exit 1
}

if ([string]::IsNullOrWhiteSpace($InputFile)) {
    Write-Host ""
    Write-Host "=== Convertir pelicula a HLS ===" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Pega la ruta del archivo de video y pulsa Enter:"
    $InputFile = (Read-Host).Trim().Trim('"')
}

if ([string]::IsNullOrWhiteSpace($InputFile)) {
    Write-Error "No se indico ningun archivo."
    exit 1
}

if (-not (Test-Path $InputFile)) {
    Write-Error "No se encuentra el archivo: $InputFile"
    exit 1
}

$ext = [System.IO.Path]::GetExtension($InputFile).ToLowerInvariant()
if ($ext -notin ".mp4", ".mkv") {
    Write-Error "Solo se admiten .mp4 y .mkv. Recibido: $ext"
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Slug)) {
    $autoSlug = [System.IO.Path]::GetFileNameWithoutExtension($InputFile)
    $autoSlug = $autoSlug -replace "[^a-zA-Z0-9\-]", "-" -replace "-+", "-" -replace "^-|-$", ""
    Write-Host ""
    Write-Host "Slug para la ruta en R2 (ej: zootopia-2, frozen, toy-story-3)"
    Write-Host "Pulsa Enter para usar '$autoSlug' o escribe uno nuevo:"
    $slugResp = (Read-Host).Trim()
    $Slug = if ([string]::IsNullOrWhiteSpace($slugResp)) { $autoSlug } else { $slugResp }
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $scriptDir = Split-Path $MyInvocation.MyCommand.Path -Parent
    $OutputDir = Join-Path (Join-Path $scriptDir "output") $Slug
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$manifestPath = Join-Path $OutputDir "master.m3u8"
$segmentPattern = Join-Path $OutputDir "segment%03d.ts"

Write-Host ""
Write-Host "Entrada : $InputFile"
Write-Host "Salida  : $OutputDir"
Write-Host "Ruta R2 : movies/$Slug/master.m3u8"
Write-Host ""
Write-Host "Convirtiendo..." -ForegroundColor Cyan

$ErrorActionPreference = "Continue"
& ffmpeg -i $InputFile -map 0:v:0 -map 0:a:0 -c:v copy -c:a aac -b:a 192k -ac 2 -f hls -hls_time 6 -hls_list_size 0 `
    -hls_segment_filename $segmentPattern $manifestPath
$ErrorActionPreference = "Stop"

if ($LASTEXITCODE -ne 0) {
    Write-Error "FFmpeg fallo con codigo $LASTEXITCODE"
    exit 1
}

Write-Host ""
Write-Host "Listo." -ForegroundColor Green
Write-Host "Siguiente paso: subir a R2 con:"
Write-Host "  .\upload-hls-to-r2.ps1 -Slug `"$Slug`"" -ForegroundColor Yellow
