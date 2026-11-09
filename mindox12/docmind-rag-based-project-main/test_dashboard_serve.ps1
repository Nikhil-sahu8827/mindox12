$res = Invoke-WebRequest -Uri 'http://localhost:3000/assets/growth-dashboard.js' -UseBasicParsing
Write-Output ("assets/growth-dashboard.js: Status " + $res.StatusCode + ", Length " + $res.Content.Length)
Write-Output $res.Content.Substring(0, [Math]::Min(150, $res.Content.Length))
