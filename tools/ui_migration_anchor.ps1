<#
.SYNOPSIS
  Script de âncora/rollback para migração UI: cria snapshot (git + zip) e restaura se build/teste falhar.
.DESCRIPTION
  - ANCHOR: cria branch ui-migration-configpage (se git), commit ANCHOR_N_PRE_MIGRATION, zip em backup/
  - ROLLBACK: restaura último anchor (git reset --hard + unzip ou só git)
  - BUILD_CHECK: roda build; se falhar, faz rollback automático
.PARAMETER Action
  Anchor | Rollback | BuildCheck
.PARAMETER AnchorName
  Nome do anchor (ex: ANCHOR_1, ANCHOR_2). Usado em Anchor.
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("Anchor", "Rollback", "BuildCheck")]
    [string]$Action,

    [Parameter(Mandatory=$false)]
    [string]$AnchorName = "ANCHOR_1"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot + "\.."
$BackupDir = "$ProjectRoot\backup"
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }

function Do-Anchor {
    $name = $AnchorName
    if (Test-Path "$ProjectRoot\.git") {
        Push-Location $ProjectRoot
        try {
            $branch = "ui-migration-configpage"
            $exists = git branch --list $branch 2>$null
            if (-not $exists) { git checkout -b $branch 2>$null }
            else { git checkout $branch 2>$null }
            git add -A 2>$null
            git commit -m "${name}_PRE_MIGRATION" 2>$null
            if ($LASTEXITCODE -ne 0) { Write-Host "Commit skipped (nothing to commit or already committed)." -ForegroundColor Yellow }
        } finally { Pop-Location }
    }
    $zipPath = "$BackupDir\${name}.zip"
    $include = @("src", "build.gradle", "gradle.properties", "settings.gradle", "gradlew", "gradlew.bat", "gradle", "libs", "tools", "Scripts", "manifest.json")
    $items = $include | ForEach-Object { Join-Path $ProjectRoot $_ } | Where-Object { Test-Path $_ }
    if ($items.Count -gt 0) { Compress-Archive -Path $items -DestinationPath $zipPath -Force }
    if (Test-Path $zipPath) { Write-Host "Backup zip: $zipPath" -ForegroundColor Green }
    Write-Host "Anchor $name done." -ForegroundColor Green
}

function Do-Rollback {
    if (Test-Path "$ProjectRoot\.git") {
        Push-Location $ProjectRoot
        try {
            git checkout ui-migration-configpage 2>$null
            git reset --hard HEAD~1 2>$null
            Write-Host "Rollback: git reset --hard (last commit reverted)." -ForegroundColor Yellow
        } finally { Pop-Location }
    }
    $latest = Get-ChildItem -Path $BackupDir -Filter "ANCHOR_*.zip" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest) {
        Write-Host "To restore from zip manually: Expand-Archive -Path $($latest.FullName) -DestinationPath $ProjectRoot -Force" -ForegroundColor Cyan
    }
    Write-Host "Rollback done." -ForegroundColor Green
}

function Do-BuildCheck {
    Push-Location $ProjectRoot
    try {
        .\gradlew.bat build 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build FAILED. Running rollback." -ForegroundColor Red
            Do-Rollback
            exit 1
        }
        Write-Host "Build OK." -ForegroundColor Green
    } finally { Pop-Location }
}

switch ($Action) {
    "Anchor"   { Do-Anchor }
    "Rollback" { Do-Rollback }
    "BuildCheck" { Do-BuildCheck }
}
