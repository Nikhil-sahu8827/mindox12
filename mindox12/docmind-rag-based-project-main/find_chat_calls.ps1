$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$idx = 0
while(($idx = $c.IndexOf('$s.', $idx)) -ge 0) {
    $start = [Math]::Max(0, $idx - 50)
    $len = [Math]::Min(300, $c.Length - $start)
    Write-Output ("Call at $idx : " + $c.Substring($start, $len))
    $idx += 3
}
