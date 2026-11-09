$node = "C:\Users\hp\AppData\Local\Programs\node-v22.16.0-win-x64\node.exe"
& $node -c "frontend/docmind-frontend/dist/assets/growth-dashboard.js"
Write-Output "Syntax check finished with exit code: $LASTEXITCODE"
