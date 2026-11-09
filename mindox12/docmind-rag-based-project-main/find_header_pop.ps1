$path = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js'
$text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$stack = New-Object System.Collections.Generic.Stack[PSObject]
$len = $text.Length
$i = 0
$line = 1
$col = 1

while ($i -lt $len -and $line -lt 1640) {
    $c = $text[$i]
    if ($c -eq "`n") { $line++; $col = 1; $i++; continue }
    
    # string/comment handling
    if ($c -eq '/' -and $i + 1 -lt $len -and $text[$i+1] -eq '/') {
        while ($i -lt $len -and $text[$i] -ne "`n") { $i++; $col++ }
        continue
    }
    if ($c -eq '"' -or $c -eq "'") {
        $q = $c; $i++; $col++
        while ($i -lt $len) {
            if ($text[$i] -eq '\') { $i += 2; $col += 2; continue }
            if ($text[$i] -eq $q) { $i++; $col++; break }
            if ($text[$i] -eq "`n") { $line++; $col = 1 } else { $col++ }
            $i++
        }
        continue
    }
    if ($c -eq '`') {
        $i++; $col++
        while ($i -lt $len) {
            if ($text[$i] -eq '\') { $i += 2; $col += 2; continue }
            if ($text[$i] -eq '`') { $i++; $col++; break }
            if ($text[$i] -eq '$' -and $i + 1 -lt $len -and $text[$i+1] -eq '{') {
                $i += 2; $col += 2
                $stack.Push([PSCustomObject]@{ Char = '`{'; Line = $line; Col = $col })
                break
            }
            if ($text[$i] -eq "`n") { $line++; $col = 1 } else { $col++ }
            $i++
        }
        continue
    }
    if ($c -in @('(', '{', '[')) {
        $stack.Push([PSCustomObject]@{ Char = $c; Line = $line; Col = $col })
    }
    if ($c -in @(')', '}', ']')) {
        $top = $stack.Pop()
        if ($top.Line -eq 1226 -or $top.Line -eq 1229) {
            Write-Output "Item '$($top.Char)' from line $($top.Line) POPPED by '$c' at line $line"
        }
        if ($top.Char -eq '`{') {
            $i++; $col++
            while ($i -lt $len) {
                if ($text[$i] -eq '\') { $i += 2; $col += 2; continue }
                if ($text[$i] -eq '`') { $i++; $col++; break }
                if ($text[$i] -eq '$' -and $i + 1 -lt $len -and $text[$i+1] -eq '{') {
                    $i += 2; $col += 2
                    $stack.Push([PSCustomObject]@{ Char = '`{'; Line = $line; Col = $col })
                    break
                }
                if ($text[$i] -eq "`n") { $line++; $col = 1 } else { $col++ }
                $i++
            }
            continue
        }
    }
    $i++
    $col++
}
