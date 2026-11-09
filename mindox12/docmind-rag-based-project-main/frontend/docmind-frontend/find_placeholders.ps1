$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$idx = 0
while(($idx = $c.IndexOf('placeholder', $idx)) -ge 0) {
    Write-Output ("Found placeholder at " + $idx + ":")
    Write-Output $c.Substring([Math]::Max(0, $idx - 30), [Math]::Min(150, $c.Length - $idx + 30))
    $idx += 15
}
