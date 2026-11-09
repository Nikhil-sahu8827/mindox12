$loginBody = '{"username":"tutor_tester","password":"password123"}'
$login = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$token = $login.accessToken
$headers = @{ "Authorization" = "Bearer " + $token }

$questions = @(
    "Hi",
    "Kaise ho?",
    "What can you do?",
    "Explain Java",
    "What topics and documents are available in my study materials?"
)

foreach ($q in $questions) {
    Write-Host "`n========================================================"
    Write-Host "QUESTION: $q"
    $chatBody = @{
        question = $q
    } | ConvertTo-Json

    try {
        $res = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/chat/query' -Method Post -ContentType 'application/json' -Headers $headers -Body $chatBody
        Write-Host "ANSWER:"
        Write-Host $res.data.answer
    } catch {
        Write-Host "ERROR: $_"
    }
}
