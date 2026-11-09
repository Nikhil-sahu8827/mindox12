$res = Invoke-RestMethod -Uri 'http://localhost:8081/v3/api-docs' -Method Get
$paths = $res.paths.PSObject.Properties.Name | Where-Object { $_ -like '*study*' }
Write-Output "Found Study Endpoints:"
$paths
