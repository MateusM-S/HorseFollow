$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

$LogsDir = 'C:\\Users\\mateu\\AppData\\Roaming\\Hytale\\UserData\\Saves\\MOD_Testing\\logs'
$DestDir = Join-Path $RepoRoot 'libs\\Log'

Write-Host "== Coletando log mais recente ==" -ForegroundColor Cyan
if (!(Test-Path $LogsDir)) { throw "Nao achei a pasta de logs: $LogsDir" }
New-Item -ItemType Directory -Force $DestDir | Out-Null

# Limpa logs anteriores na pasta destino
Get-ChildItem -Path $DestDir -File -Filter '*_server.log' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Remove-Item -Force (Join-Path $DestDir 'latest_server.log') -ErrorAction SilentlyContinue

$log = Get-ChildItem -Path $LogsDir -Filter '*_server.log' -File |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $log) { throw "Nao encontrei nenhum *_server.log em: $LogsDir" }

# Salva sempre como um unico arquivo fixo
$latestPath = Join-Path $DestDir 'latest_server.log'
Copy-Item -Path $log.FullName -Destination $latestPath -Force

Write-Host ("Copiado: " + $log.FullName) -ForegroundColor Green
Write-Host ("Para: " + $latestPath) -ForegroundColor Green

