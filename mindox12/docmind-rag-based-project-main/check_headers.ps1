$loginBody = '{"username":"tutor_tester","password":"password123"}'
$login = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$token = $login.accessToken
$headers = @{
    "Authorization" = "Bearer " + $token
    "Content-Type" = "application/json"
}

$res = Invoke-WebRequest -Uri 'http://localhost:8081/api/v1/chat/stream' -Method Post -Headers $headers -Body '{"question":"hi"}' -UseBasicParsing
Write-Host "8081 Content-Type: " $res.Headers['Content-Type']

try {
    $res3000 = Invoke-WebRequest -Uri 'http://localhost:3000/api/v1/chat/stream' -Method Post -Headers $headers -Body '{"question":"hi"}' -UseBasicParsing
    Write-Host "3000 Content-Type: " $res3000.Headers['Content-Type']
} catch {
    Write-Host "3000 error: $_"
}
