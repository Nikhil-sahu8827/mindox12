$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf('v=(o.headers.get')
Write-Output ("Pos: " + $pos)
if ($pos -ge 0) {
    Write-Output $c.Substring($pos, 100)
}
