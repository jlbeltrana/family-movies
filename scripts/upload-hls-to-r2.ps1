# Sube una carpeta HLS (master.m3u8 + segmentos .ts) a R2 bajo movies/<slug>/
# Uso: .\upload-hls-to-r2.ps1 -Slug "mi-pelicula" [-HlsDir ".\output\mi-pelicula"] [-Bucket "family-movies-media"]
# Ejecutar desde scripts/ o indicar HlsDir con ruta completa.

param(
    [Parameter(Mandatory = $true, HelpMessage = "Slug de la película (ruta en R2: movies/<slug>/).")]
    [string]$Slug,
    [Parameter(Mandatory = $false)]
    [string]$HlsDir = "",
    [Parameter(Mandatory = $false)]
    [string]$Bucket = "family-movies-media"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path $MyInvocation.MyCommand.Path -Parent
$workerDir = Join-Path (Split-Path $scriptDir -Parent) "cloudflare-worker"
$wrangler = Join-Path $workerDir "node_modules\.bin\wrangler.cmd"
if (-not (Test-Path $wrangler)) { $wrangler = Join-Path $workerDir "node_modules\.bin\wrangler" }
if (-not (Test-Path $wrangler)) {
    Write-Error "No se encuentra wrangler en $workerDir. Ejecuta 'npm install' en cloudflare-worker."
    exit 1
}
if ([string]::IsNullOrWhiteSpace($HlsDir)) {
    $HlsDir = Join-Path $scriptDir "output" $Slug
}
$HlsDir = [System.IO.Path]::GetFullPath($HlsDir)
if (-not (Test-Path $HlsDir)) {
    Write-Error "No existe la carpeta: $HlsDir. Ejecuta primero convert-to-hls.ps1."
    exit 1
}
$manifestPath = Join-Path $HlsDir "master.m3u8"
if (-not (Test-Path $manifestPath)) {
    Write-Error "No se encuentra master.m3u8 en $HlsDir"
    exit 1
}

$prefix = "movies/$Slug"
$files = Get-ChildItem -Path $HlsDir -File
foreach ($f in $files) {
    $key = "$prefix/$($f.Name)"
    Write-Host "Subiendo $key ..."
    & $wrangler r2 object put "$Bucket/$key" --file $f.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error subiendo $key"
        exit 1
    }
}
Write-Host "Listo. Contenido en R2 bajo $prefix/" -ForegroundColor Green
Write-Host "En Firestore, usa manifestPath: $prefix/master.m3u8"
