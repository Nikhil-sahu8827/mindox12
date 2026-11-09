$path = "frontend/docmind-frontend/dist/assets/index-WsI9Spll.js"
$content = [System.IO.File]::ReadAllText($path)
$idx = $content.IndexOf("label:`Study Dashboard`")
Write-Output ("Index: " + $idx)
if ($idx -ge 0) {
    Write-Output $content.Substring([Math]::Max(0, $idx - 300), 600)
}
