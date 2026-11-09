$path = "frontend/docmind-frontend/dist/assets/index-WsI9Spll.js"
$content = [System.IO.File]::ReadAllText($path)
if ($content.Contains("label:`My Growth`")) {
    $content = $content.Replace("label:`My Growth`", "label:`Study Dashboard`")
    [System.IO.File]::WriteAllText($path, $content)
    Write-Output "Successfully updated sidebar label to 'Study Dashboard'"
} else {
    Write-Output "Target already updated or not found"
}
