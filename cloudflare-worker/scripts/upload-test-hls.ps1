# Sube todos los archivos de test-hls/ a R2 bajo movies/test/
# Uso: desde cloudflare-worker/ ejecutar: .\scripts\upload-test-hls.ps1
# Requiere: wrangler (npx wrangler) y que exista la carpeta test-hls con .m3u8 y .ts

$bucket = "family-movies-media"
$prefix = "movies/test"
$testHlsPath = Join-Path $PSScriptRoot ".." "test-hls"

if (-not (Test-Path $testHlsPath)) {
    Write-Error "No existe la carpeta test-hls. Crea primero el HLS con ffmpeg (ver docs/phase1-worker-jwt.md Paso 1)."
    exit 1
}

$files = Get-ChildItem -Path $testHlsPath -File
if ($files.Count -eq 0) {
    Write-Error "La carpeta test-hls está vacía."
    exit 1
}

foreach ($f in $files) {
    $key = "$prefix/$($f.Name)"
    Write-Host "Subiendo $key ..."
    npx wrangler r2 object put "$bucket/$key" --file $f.FullName
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error subiendo $key"
        exit 1
    }
}

Write-Host "Listo. Archivos en R2 bajo $prefix/"
