$path = "frontend/docmind-frontend/dist/assets/index-WsI9Spll.js"
$content = [System.IO.File]::ReadAllText($path)
$target = '{id:`growth`,label:`My Growth`,icon:yc}'
$replace = '{id:`growth`,label:`Study Dashboard`,icon:yc}'
if ($content.Contains($target)) {
    $content = $content.Replace($target, $replace)
    [System.IO.File]::WriteAllText($path, $content)
    Write-Output "Successfully updated sidebar label to 'Study Dashboard'"
} else {
    Write-Output "Already updated or target not found"
}
