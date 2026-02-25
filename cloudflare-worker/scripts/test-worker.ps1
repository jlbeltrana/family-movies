# Genera un JWT de prueba y llama al Worker para comprobar que devuelve el manifest.
# Uso: .\scripts\test-worker.ps1
#       o: $env:JWT_SECRET="tu-secret"; .\scripts\test-worker.ps1
# Ejecutar desde la carpeta cloudflare-worker\

$ErrorActionPreference = "Stop"
# Ir a cloudflare-worker/ (carpeta que contiene package.json y scripts/)
$workerRoot = Split-Path $PSScriptRoot -Parent
Set-Location $workerRoot

if (-not $env:JWT_SECRET) {
    $env:JWT_SECRET = Read-Host "Introduce JWT_SECRET (el mismo que en wrangler secret put JWT_SECRET)"
}
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    Write-Error "JWT_SECRET no puede estar vacío."
    exit 1
}

$workerUrl = "https://family-movies-worker.lucyb.workers.dev"
$prefix = "movies/test/"

Write-Host "Generando token (prefix: $prefix)..." -ForegroundColor Cyan
$token = (node scripts/generate-test-token.js $prefix 2>$null).Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Error "No se generó el token. Revisa JWT_SECRET."
    exit 1
}
if ($token.Length -lt 50) {
    Write-Error "Token demasiado corto ($($token.Length) caracteres). Revisa el script o JWT_SECRET."
    exit 1
}
Write-Host "Token generado ($($token.Length) caracteres)." -ForegroundColor Green

Write-Host "Llamando al Worker..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri "$workerUrl/movies/test/master.m3u8" -Headers @{ Authorization = "Bearer $token" }
    Write-Host "OK. El Worker devolvió el manifest (primeros 200 caracteres):" -ForegroundColor Green
    $preview = if ($response -is [string]) { $response } else { $response | ConvertTo-Json -Compress }
    Write-Host ($preview.Substring(0, [Math]::Min(200, $preview.Length)))
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        Write-Host $reader.ReadToEnd()
    }
    exit 1
}
