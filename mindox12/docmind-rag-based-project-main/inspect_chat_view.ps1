$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = 707969
$start = [Math]::Max(0, $pos - 400)
$len = 1500
Write-Output $c.Substring($start, $len)
