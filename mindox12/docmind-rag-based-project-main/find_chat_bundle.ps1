$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$idx = 0
while(($idx = $c.IndexOf('/api/v1/chat', $idx)) -ge 0) {
    $start = [Math]::Max(0, $idx - 100)
    $len = [Math]::Min(250, $c.Length - $start)
    Write-Output ("Match at $idx : " + $c.Substring($start, $len))
    $idx += 12
}
