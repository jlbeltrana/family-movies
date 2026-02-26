# Sube una carpeta HLS (master.m3u8 + segmentos .ts) a R2 bajo movies/<slug>/
# Retoma automaticamente si se interrumpe: guarda progreso en upload-progress-<slug>.log
# Uso interactivo: .\upload-hls-to-r2.ps1

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

# --- Progreso: cargar archivos ya subidos ---
$progressFile = Join-Path $scriptDir "upload-progress-$Slug.log"
$alreadyUploaded = @{}

if (Test-Path $progressFile) {
    Write-Host ""
    Write-Host "Se encontro un progreso anterior para '$Slug'." -ForegroundColor Yellow
    $count = (Get-Content $progressFile | Measure-Object -Line).Lines
    Write-Host "$count archivos ya subidos. Retomando desde donde quedo..." -ForegroundColor Green
    Get-Content $progressFile | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) { $alreadyUploaded[$_.Trim()] = $true }
    }
}

# --- Archivos a subir (excluye .vtt) ---
$files = Get-ChildItem -Path $HlsDir -File | Where-Object { $_.Extension -ne ".vtt" }
$total = $files.Count
$pending = $files | Where-Object { -not $alreadyUploaded.ContainsKey($_.Name) }
$pendingCount = ($pending | Measure-Object).Count

Write-Host ""
Write-Host "Total: $total archivos | Ya subidos: $($alreadyUploaded.Count) | Pendientes: $pendingCount" -ForegroundColor Cyan
Write-Host ""

if ($pendingCount -eq 0) {
    Write-Host "Todo ya estaba subido." -ForegroundColor Green
} else {
    $i = $alreadyUploaded.Count
    $ErrorActionPreference = "Continue"
    foreach ($f in $pending) {
        $i++
        $key = "movies/$Slug/$($f.Name)"
        Write-Host "[$i/$total] $key"
        $uploaded = $false
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            Push-Location $workerDir
            $output = & $wrangler r2 object put "$Bucket/$key" --file $f.FullName 2>&1
            Pop-Location
            if ($LASTEXITCODE -eq 0) { $uploaded = $true; break }
            Write-Host "  Intento $attempt fallido, reintentando en 3s..." -ForegroundColor Yellow
            Start-Sleep -Seconds 3
        }
        if (-not $uploaded) {
            Write-Host $output
            Write-Host ""
            Write-Host "Error tras 3 intentos. Progreso guardado en: $progressFile" -ForegroundColor Red
            Write-Host "Vuelve a correr el script para retomar desde aqui." -ForegroundColor Yellow
            exit 1
        }
        # Anotar como subido
        Add-Content -Path $progressFile -Value $f.Name
    }
    $ErrorActionPreference = "Stop"

    Write-Host ""
    Write-Host "Listo. $total archivos de video subidos." -ForegroundColor Green

    # Borrar archivo de progreso al completar
    if (Test-Path $progressFile) { Remove-Item $progressFile -Force }
}

# Subir caratula opcional
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
