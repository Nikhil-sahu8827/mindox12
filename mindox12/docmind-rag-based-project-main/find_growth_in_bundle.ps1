$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf('GrowthDashboard')
Write-Output ("GrowthDashboard pos: " + $pos)
if ($pos -ge 0) {
    Write-Output $c.Substring([Math]::Max(0, $pos - 150), 400)
}
