$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf('$s.stream({')
Write-Output $c.Substring($pos + 1000, 1000)
