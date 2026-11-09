$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf("`growth`")
Write-Output ("Pos: " + $pos)
if ($pos -ge 0) {
    Write-Output $c.Substring([Math]::Max(0, $pos - 200), 500)
}
