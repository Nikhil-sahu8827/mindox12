$oldLines = [System.IO.File]::ReadAllLines("frontend/docmind-frontend/dist/assets/growth-dashboard.js.bak")
$newLines = [System.IO.File]::ReadAllLines("frontend/docmind-frontend/dist/assets/growth-dashboard.js")

Write-Output ("Old lines count: " + $oldLines.Length + " | New lines count: " + $newLines.Length)

# Check for differences
$diffs = Compare-Object $oldLines $newLines -IncludeEqual:$false
Write-Output ("Total diff records: " + $diffs.Length)
foreach ($d in $diffs | Select-Object -First 25) {
    Write-Output ($d.SideIndicator + " " + $d.InputObject.Trim())
}
