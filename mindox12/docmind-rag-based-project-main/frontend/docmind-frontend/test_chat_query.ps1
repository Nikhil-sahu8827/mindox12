$loginBody = '{"username":"tutor_tester","password":"password123"}'
$loginRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $loginRes.accessToken

$headers = @{
    "Authorization" = "Bearer " + $token
}

$chatBody = '{"question":"What are Generics in Java?"}'
try {
    $chatRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/chat/query" -Method Post -ContentType "application/json" -Headers $headers -Body $chatBody
    Write-Output "--- CHAT RESPONSE ---"
    Write-Output ("Answer: " + $chatRes.data.answer)
    Write-Output ("Citations count: " + $chatRes.data.citations.Count)
    foreach ($c in $chatRes.data.citations) {
        Write-Output ("  Source: " + $c.fileName + ", Page: " + $c.pageNumber)
    }
} catch {
    Write-Output ("Error: " + $_.Exception.Message)
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Output ("Response body: " + $reader.ReadToEnd())
    }
}
