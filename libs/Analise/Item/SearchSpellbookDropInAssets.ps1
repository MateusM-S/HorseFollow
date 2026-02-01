# Procura no Assets.zip do Hytale onde o item Weapon_Spellbook_Rekindle_Embers
# aparece como drop (NPC / lista de loot). Mostra o arquivo e a linha para você ver o NPC e a chance.
$zipPath = "$env:APPDATA\Hytale\install\release\package\game\latest\Assets.zip"
$searchTerm = "Weapon_Spellbook_Rekindle_Embers"
if (-not (Test-Path $zipPath)) {
    Write-Host "ZIP_NOT_FOUND: $zipPath"
    Write-Host "Ajuste zipPath se o Assets.zip estiver em outro lugar."
    exit 1
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$jsonEntries = $zip.Entries | Where-Object { $_.Name -match '\.json$' -and $_.Length -gt 0 }
$found = @()
foreach ($e in $jsonEntries) {
    $stream = $e.Open()
    $reader = New-Object System.IO.StreamReader($stream)
    $content = $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
    if ($content -match [regex]::Escape($searchTerm)) {
        $found += $e.FullName
        Write-Host $e.FullName
    }
}
$zip.Dispose()
if ($found.Count -eq 0) {
    Write-Host "Nenhum arquivo JSON no Assets.zip contem: $searchTerm"
} else {
    Write-Host "--- Total: $($found.Count) arquivo(s). Abra esses JSONs para ver o NPC e a chance de drop."
}
