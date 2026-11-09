$path = "C:\Users\hp\.gemini\antigravity-ide\brain\9163084a-17fc-47ec-9bf6-c08d3f61c9b1\.system_generated\logs\transcript.jsonl"
$lines = Get-Content $path
$line2 = $lines | Where-Object { $_.Contains('"step_index":116') }
Write-Output "=== REQUEST 2 ==="
Write-Output $line2

$line3 = $lines | Where-Object { $_.Contains('"step_index":410') }
Write-Output "`n=== REQUEST 3 ==="
Write-Output $line3
