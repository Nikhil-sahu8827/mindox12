$res = Invoke-WebRequest -Uri 'http://localhost:3000/assets/growth-dashboard.js?v=growth_v4' -UseBasicParsing
Write-Output ("Served length: " + $res.Content.Length + " bytes")
if ($res.Content.Contains("My Growth & Smart Study Tracker")) {
    Write-Output "VERIFIED: Served script contains 'My Growth & Smart Study Tracker'!"
}
