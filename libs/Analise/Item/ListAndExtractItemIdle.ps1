# Lista e extrai o conjunto "Item" (Idle, Walk, etc.) do Assets.zip do Hytale.
# Item = mão direita (R-Arm, R-Forearm, R-Hand). Caminho esperado: Characters/Animations/Items/Item/
$zipPath = "$env:APPDATA\Hytale\install\release\package\game\latest\Assets.zip"
$outDir = "$PSScriptRoot\vanilla_Item"
if (-not (Test-Path $zipPath)) {
    Write-Host "ZIP_NOT_FOUND: $zipPath"
    Write-Host "Coloque o Assets.zip em libs/Analise/Item/ ou ajuste zipPath."
    exit 1
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
# Match: Characters/Animations/Items/Item/ (conjunto vanilla "Item", mão direita)
$itemEntries = $zip.Entries | Where-Object { $_.FullName -match 'Characters[/\\]Animations[/\\]Items[/\\]Item[/\\]' }
foreach ($e in $itemEntries) { Write-Host $e.FullName }
$zip.Dispose()

# Extrair para vanilla_Item mantendo estrutura
$zip2 = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
foreach ($e in $zip2.Entries) {
    if ($e.FullName -match 'Characters[/\\]Animations[/\\]Items[/\\]Item[/\\]') {
        $target = Join-Path $outDir $e.FullName
        $targetDir = Split-Path $target -Parent
        if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }
        if (-not $e.Name) { continue }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        Write-Host "Extracted: $($e.FullName)"
    }
}
$zip2.Dispose()
Write-Host "Done. Check: $outDir"
