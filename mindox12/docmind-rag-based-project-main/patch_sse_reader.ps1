$path = "frontend/docmind-frontend/dist/assets/index-WsI9Spll.js"
$c = [System.IO.File]::ReadAllText($path)
$target = 'v=(o.headers.get(`content-type`)||``).includes(`text/event-stream`);for(;!m;){'
$replacement = 'v=(o.headers.get(`content-type`)||``).includes(`text/event-stream`)||!0;for(;!m;){'

if ($c.Contains($target)) {
    $c = $c.Replace($target, $replacement)
    [System.IO.File]::WriteAllText($path, $c)
    Write-Host "Successfully patched index-WsI9Spll.js to ensure SSE data: lines are always cleanly stripped!"
} else {
    Write-Host "Target not found in bundle"
}
