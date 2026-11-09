$path = "frontend/docmind-frontend/dist/assets/index-WsI9Spll.js"
$content = [System.IO.File]::ReadAllText($path)
$idx = $content.IndexOf("GrowthDashboard")
Write-Output ("GrowthDashboard index: " + $idx)
$idx2 = $content.IndexOf("growth")
Write-Output ("growth index: " + $idx2)
if ($idx2 -ge 0) {
    Write-Output $content.Substring($idx2 - 50, 150)
}
