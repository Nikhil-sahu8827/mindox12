$c = [System.IO.File]::ReadAllText("frontend/docmind-frontend/dist/assets/index-WsI9Spll.js")
$pos = $c.IndexOf('kb=({onOpenUpload:e})=>{')
$kbText = $c.Substring($pos, 16000)

@("placeholder", "input", "chat", "Search", "Ask", "query") | ForEach-Object {
    $idx = 0
    $count = 0
    while(($idx = $kbText.IndexOf($_, $idx)) -ge 0) {
        $count++
        $idx += $_.Length
    }
    Write-Output ("In kb: " + $_ + " count = " + $count)
}
