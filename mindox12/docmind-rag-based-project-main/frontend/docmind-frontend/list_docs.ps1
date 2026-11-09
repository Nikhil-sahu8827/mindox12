$loginBody = '{"username":"tutor_tester","password":"password123"}'
$loginRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$headers = @{ "Authorization" = "Bearer " + $loginRes.accessToken }

$docsRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/documents" -Method Get -Headers $headers
Write-Output ("Total documents: " + $docsRes.data.Count)
foreach ($d in $docsRes.data) {
    Write-Output ("Doc ID: " + $d.id + " | File: " + $d.filename + " | Chunks: " + $d.totalChunks + " | Status: " + $d.status)
}
