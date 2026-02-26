# Sube una carpeta HLS (master.m3u8 + segmentos .ts) a R2 bajo movies/<slug>/
# Uso interactivo: .\upload-hls-to-r2.ps1
# Uso por parametros: .\upload-hls-to-r2.ps1 -Slug "mi-pelicula"

param(
    [string]$Slug = "",
    [string]$HlsDir = "",
    [string]$Bucket = "family-movies-media"
)

$ErrorActionPreference = "Stop"

$scriptDir  = Split-Path $MyInvocation.MyCommand.Path -Parent
$workerDir  = Join-Path (Split-Path $scriptDir -Parent) "cloudflare-worker"
$wrangler   = Join-Path $workerDir "node_modules\.bin\wrangler.cmd"
if (-not (Test-Path $wrangler)) {
    $wrangler = Join-Path $workerDir "node_modules\.bin\wrangler"
}
if (-not (Test-Path $wrangler)) {
    Write-Error "No se encuentra wrangler en $workerDir. Ejecuta 'npm install' en cloudflare-worker."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Slug)) {
    Write-Host ""
    Write-Host "=== Subir pelicula a R2 ===" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Slug de la pelicula (ej: zootopia-2, frozen):"
    $Slug = (Read-Host).Trim()
}

if ([string]::IsNullOrWhiteSpace($Slug)) {
    Write-Error "No se indico ningun slug."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($HlsDir)) {
    $HlsDir = Join-Path (Join-Path $scriptDir "output") $Slug
}
$HlsDir = [System.IO.Path]::GetFullPath($HlsDir)

if (-not (Test-Path $HlsDir)) {
    Write-Error "No existe la carpeta: $HlsDir. Ejecuta primero convert-to-hls.ps1."
    exit 1
}
if (-not (Test-Path (Join-Path $HlsDir "master.m3u8"))) {
    Write-Error "No se encuentra master.m3u8 en $HlsDir"
    exit 1
}

$files  = Get-ChildItem -Path $HlsDir -File
$total  = $files.Count
$i      = 0

Write-Host ""
Write-Host "Subiendo $total archivos a R2 bajo movies/$Slug/ ..." -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Continue"
foreach ($f in $files) {
    $i++
    $key = "movies/$Slug/$($f.Name)"
    Write-Host "[$i/$total] $key"
    Push-Location $workerDir
    $output = & $wrangler r2 object put "$Bucket/$key" --file $f.FullName 2>&1
    Pop-Location
    if ($LASTEXITCODE -ne 0) {
        Write-Host $output
        Write-Error "Error subiendo $key"
        exit 1
    }
}
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "Listo. $total archivos de video subidos." -ForegroundColor Green

# Subir carátula opcional
Write-Host ""
Write-Host "Caratula (poster.jpg/png): pega la ruta de la imagen o pulsa Enter para omitir:"
$posterPath = (Read-Host).Trim().Trim('"')

if (-not [string]::IsNullOrWhiteSpace($posterPath) -and (Test-Path $posterPath)) {
    $posterExt = [System.IO.Path]::GetExtension($posterPath).ToLowerInvariant()
    $posterKey = "movies/$Slug/poster$posterExt"
    Write-Host "Subiendo caratula: $posterKey ..."
    Push-Location $workerDir
    $output = & $wrangler r2 object put "$Bucket/$posterKey" --file $posterPath 2>&1
    Pop-Location
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Caratula subida." -ForegroundColor Green
    } else {
        Write-Host $output
        Write-Host "No se pudo subir la caratula, puedes subirla despues." -ForegroundColor Yellow
    }
} else {
    Write-Host "Sin caratula. Podras subirla despues." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Ahora crea el documento en Firestore:" -ForegroundColor Yellow
Write-Host "  Coleccion    : movies"
Write-Host "  Documento    : $Slug"
Write-Host "  title        : Nombre de la pelicula"
Write-Host "  manifestPath : movies/$Slug/master.m3u8"
Write-Host "  posterPath   : movies/$Slug/poster.jpg"
Write-Host "  category     : princesas / aventuras / familia"
Write-Host "  year         : 2025"
Write-Host "  duration     : duracion en minutos"
