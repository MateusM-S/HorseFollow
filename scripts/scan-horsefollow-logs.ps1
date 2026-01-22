param(
    [string]$LogDir = "C:\Users\mateu\AppData\Roaming\Hytale\UserData\Saves\TesteMod-01\logs",
    [int]$Tail = 300,
    [switch]$AllLogs
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -Path $LogDir)) {
    Write-Host "Log directory not found: $LogDir"
    exit 1
}

$logFiles = Get-ChildItem -Path $LogDir -Filter "*_server.log" | Sort-Object LastWriteTime -Descending
if (-not $logFiles) {
    Write-Host "No server logs found in: $LogDir"
    exit 1
}

$targets = if ($AllLogs) { $logFiles } else { $logFiles | Select-Object -First 1 }

$patterns = @(
    "HorseFollow",
    "Exception",
    "ERROR",
    "WARN",
    "Warning",
    "Stack trace",
    "Mount",
    "Dismount",
    "NPCMount",
    "MountNPC",
    "Mounted",
    "MountedBy",
    "Attitude",
    "OverrideAttitude",
    "RoleChange",
    "Role",
    "bind",
    "unbind"
)

foreach ($file in $targets) {
    Write-Host ""
    Write-Host "=== Scanning: $($file.FullName) ==="
    Write-Host "LastWriteTime: $($file.LastWriteTime)"

    $matches = Select-String -Path $file.FullName -Pattern $patterns -SimpleMatch -Context 2,2
    if (-not $matches) {
        Write-Host "No matches for target patterns."
    } else {
        $matches | ForEach-Object {
            $context = $_.Context
            Write-Host ("[{0}:{1}] {2}" -f $_.Filename, $_.LineNumber, $_.Line)
            if ($context.PreContext) {
                $context.PreContext | ForEach-Object { Write-Host ("  " + $_) }
            }
            if ($context.PostContext) {
                $context.PostContext | ForEach-Object { Write-Host ("  " + $_) }
            }
            Write-Host ""
        }
    }

    if ($Tail -gt 0) {
        Write-Host "--- Tail ($Tail lines) ---"
        Get-Content -Path $file.FullName -Tail $Tail
    }
}
