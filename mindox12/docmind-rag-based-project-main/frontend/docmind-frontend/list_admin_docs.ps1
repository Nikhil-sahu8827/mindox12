$loginBody = '{"username":"admin","password":"password123"}'
try {
    $loginRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    $headers = @{ "Authorization" = "Bearer " + $loginRes.accessToken }
    $docsRes = Invoke-RestMethod -Uri "http://localhost:3000/api/v1/documents" -Method Get -Headers $headers
    Write-Output ("Admin docs count: " + $docsRes.data.Count)
    foreach ($d in $docsRes.data) {
        Write-Output ("  File: " + $d.filename)
    }
} catch {
    Write-Output ("Admin login failed: " + $_.Exception.Message)
}
