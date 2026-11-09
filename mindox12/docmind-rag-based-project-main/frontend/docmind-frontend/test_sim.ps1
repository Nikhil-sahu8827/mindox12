$loginBody = '{"username":"tutor_tester","password":"password123"}'
$loginRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$headers = @{ "Authorization" = "Bearer " + $loginRes.accessToken }

# Test similarity search on Generics
$simBody = '{"query":"Generics in Java","topK":10}'
$simRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/chat/search/similarity" -Method Post -ContentType "application/json" -Headers $headers -Body $simBody

Write-Output ("Similarity results for 'Generics in Java': " + $simRes.data.matches.Count)
foreach ($m in $simRes.data.matches) {
    Write-Output ("Score: " + $m.score + " | File: " + $m.fileName + " | Text snippet: " + $m.text.Substring(0, [Math]::Min(120, $m.text.Length)))
}
