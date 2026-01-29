$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$Gradle = Join-Path $RepoRoot 'gradlew.bat'
$BuildLibs = Join-Path $RepoRoot 'build\\libs'

$ModsDir = 'C:\\Users\\mateu\\AppData\\Roaming\\Hytale\\UserData\\Mods'
$TargetName = 'horsefollow.jar'
$TargetPath = Join-Path $ModsDir $TargetName

Write-Host "== Build ==" -ForegroundColor Cyan
if (!(Test-Path $Gradle)) { throw "Nao achei o gradlew.bat em: $Gradle" }
& $Gradle clean build
if ($LASTEXITCODE -ne 0) { throw "Build falhou (exit code $LASTEXITCODE)." }

Write-Host "== Selecionando JAR ==" -ForegroundColor Cyan
if (!(Test-Path $BuildLibs)) { throw "Nao achei a pasta build\\libs em: $BuildLibs" }

$jar = Get-ChildItem -Path $BuildLibs -Filter 'HorseFollow-*.jar' -File |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) { throw "Nenhum JAR encontrado em $BuildLibs com prefixo HorseFollow-*.jar" }
Write-Host ("JAR escolhido: " + $jar.FullName)

Write-Host "== Instalando no Hytale ==" -ForegroundColor Cyan
New-Item -ItemType Directory -Force $ModsDir | Out-Null

# Remove instalacoes antigas (qualquer versao) + nome fixo
Get-ChildItem -Path $ModsDir -Filter 'HorseFollow-*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Remove-Item -Force $TargetPath -ErrorAction SilentlyContinue

Copy-Item -Path $jar.FullName -Destination $TargetPath -Force
Write-Host ("Instalado em: " + $TargetPath) -ForegroundColor Green

