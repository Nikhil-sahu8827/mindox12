$loginBody = '{"username":"tutor_tester","password":"password123"}'
$login = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$token = $login.accessToken

$chatBody = '{"question":"Provide a concise executive summary of the key points in this document."}'
$webRequest = [System.Net.HttpWebRequest]::Create("http://localhost:8081/api/v1/chat/stream")
$webRequest.Method = "POST"
$webRequest.ContentType = "application/json"
$webRequest.Headers.Add("Authorization", "Bearer " + $token)

$byteData = [System.Text.Encoding]::UTF8.GetBytes($chatBody)
$webRequest.ContentLength = $byteData.Length
$postStream = $webRequest.GetRequestStream()
$postStream.Write($byteData, 0, $byteData.Length)
$postStream.Close()

Write-Host ">>> Reading stream response..."
$response = $webRequest.GetResponse()
$reader = New-Object System.IO.StreamReader($response.GetResponseStream())
$count = 0
while (!$reader.EndOfStream -and $count -lt 30) {
    $line = $reader.ReadLine()
    if ($line) {
        Write-Host "STREAM: $line"
        $count++
    }
}
$reader.Close()
$response.Close()
