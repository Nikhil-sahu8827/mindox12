$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = 707969
$start = [Math]::Max(0, $pos - 1500)
$len = 1200
Write-Output $c.Substring($start, $len)
