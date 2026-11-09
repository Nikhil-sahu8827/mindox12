$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf('wA=')
Write-Output ("wA pos: " + $pos)
if ($pos -ge 0) {
    Write-Output $c.Substring($pos, 1500)
}
