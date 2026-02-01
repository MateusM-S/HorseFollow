# Verifica se o JAR do HorseFollow contém HyUI e o HTML da UI.
# Executar APÓS: .\gradlew clean jar (na raiz do projeto).

$ErrorActionPreference = 'Stop'
# PSScriptRoot = .../libs/Analise/HyUI -> repo root = 3 níveis acima
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\")).Path
$JarPath = Join-Path $RepoRoot "build\libs"
$Jar = Get-ChildItem -Path $JarPath -Filter "HorseFollow-*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $Jar) {
    Write-Host "JAR nao encontrado em: $JarPath" -ForegroundColor Red
    Write-Host "Execute antes: .\gradlew clean jar" -ForegroundColor Yellow
    exit 1
}

Write-Host "JAR: $($Jar.FullName)" -ForegroundColor Cyan
Write-Host ""

$content = jar tf $Jar.FullName 2>&1
$hasHyUI = $content | Select-String -Pattern "au/ellie/hyui" -Quiet
$hasHtml = $content | Select-String -Pattern "ConfigPage_HyUI\.html" -Quiet
$hasCommonUI = $content | Select-String -Pattern "Common/UI/Custom" -Quiet

if ($hasHyUI) {
    Write-Host "[OK] HyUI (au/ellie/hyui) esta no JAR" -ForegroundColor Green
} else {
    Write-Host "[FALTA] HyUI nao encontrado no JAR" -ForegroundColor Red
}

if ($hasHtml) {
    Write-Host "[OK] ConfigPage_HyUI.html esta no JAR" -ForegroundColor Green
} else {
    Write-Host "[FALTA] ConfigPage_HyUI.html nao encontrado no JAR" -ForegroundColor Red
}

if ($hasCommonUI) {
    Write-Host "[OK] Recursos Common/UI/Custom estao no JAR" -ForegroundColor Green
} else {
    Write-Host "[FALTA] Common/UI/Custom nao encontrado no JAR" -ForegroundColor Red
}

if (-not $hasHyUI -or -not $hasHtml) {
    Write-Host ""
    Write-Host "Se HyUI ou HTML faltam, o mod nao carrega a UI HyUI. Verifique build.gradle:" -ForegroundColor Yellow
    Write-Host "  - implementation files('libs/HyUI-0.5.10-all.jar')" -ForegroundColor Gray
    Write-Host "  - jar { from sourceSets.main.output; from { runtimeClasspath jars zipTree } }" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "JAR parece correto. Se a UI ainda nao abrir, use /horsefollow config e veja a mensagem no chat:" -ForegroundColor Cyan
Write-Host "  - '[HorseFollow] Abrindo configurações (HyUI)...' = codigo novo rodando" -ForegroundColor Gray
Write-Host "  - '[HorseFollow] Erro HyUI: ClassNotFoundException' = HyUI nao no classpath em runtime" -ForegroundColor Gray
Write-Host "  - '[HorseFollow] Erro ao carregar a UI' = recurso HTML nao encontrado" -ForegroundColor Gray
exit 0
