$res = Invoke-WebRequest -Uri 'http://localhost:3000/' -UseBasicParsing
Write-Output ("Length: " + $res.Content.Length)
Write-Output $res.Content
