# Lista e extrai Furniture_Crude_Torch e Torch do Assets.zip do Hytale
$zipPath = "$env:APPDATA\Hytale\install\release\package\game\latest\Assets.zip"
$outDir = "$PSScriptRoot\vanilla_Torch"
if (-not (Test-Path $zipPath)) { Write-Host "ZIP_NOT_FOUND: $zipPath"; exit 1 }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$torchEntries = $zip.Entries | Where-Object { $_.FullName -match 'Torch|Crude_Torch' }
foreach ($e in $torchEntries) { Write-Host $e.FullName }
$zip.Dispose()

# Extrair para vanilla_Torch mantendo estrutura
$zip2 = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
foreach ($e in $zip2.Entries) {
    if ($e.FullName -match 'Torch|Crude_Torch') {
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
