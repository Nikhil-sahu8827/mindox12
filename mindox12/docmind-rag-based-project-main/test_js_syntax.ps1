param(
    [string]$path = 'frontend/docmind-frontend/dist/assets/growth-dashboard.js.bak'
)

$text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Stack for opening delimiters
$stack = New-Object System.Collections.Generic.Stack[PSObject]
$len = $text.Length
$i = 0
$line = 1
$col = 1

while ($i -lt $len) {
    $c = $text[$i]
    
    if ($c -eq "`n") {
        $line++
        $col = 1
        $i++
        continue
    }
    
    # Check single line comment
    if ($c -eq '/' -and $i + 1 -lt $len -and $text[$i+1] -eq '/') {
        $i += 2
        $col += 2
        while ($i -lt $len -and $text[$i] -ne "`n") {
            $i++
            $col++
        }
        continue
    }
    
    # Check multi line comment
    if ($c -eq '/' -and $i + 1 -lt $len -and $text[$i+1] -eq '*') {
        $i += 2
        $col += 2
        while ($i + 1 -lt $len -and -not ($text[$i] -eq '*' -and $text[$i+1] -eq '/')) {
            if ($text[$i] -eq "`n") { $line++; $col = 1 } else { $col++ }
            $i++
        }
        $i += 2
        $col += 2
        continue
    }
    
    # Check string literals '...' or "..."
    if ($c -eq '"' -or $c -eq "'") {
        $quote = $c
        $i++
        $col++
        while ($i -lt $len) {
            $ch = $text[$i]
            if ($ch -eq '\') {
                $i += 2
                $col += 2
                continue
            }
            if ($ch -eq $quote) {
                $i++
                $col++
                break
            }
            if ($ch -eq "`n") { $line++; $col = 1 } else { $col++ }
            $i++
        }
        continue
    }
    
    # Check template literal `...`
    if ($c -eq '`') {
        $i++
        $col++
        while ($i -lt $len) {
            $ch = $text[$i]
            if ($ch -eq '\') {
                $i += 2
                $col += 2
                continue
            }
            if ($ch -eq '`') {
                $i++
                $col++
                break
            }
            if ($ch -eq '$' -and $i + 1 -lt $len -and $text[$i+1] -eq '{') {
                # template expr inside `
                $i += 2
                $col += 2
                $stack.Push([PSCustomObject]@{ Char = '`{'; Line = $line; Col = $col })
                break
            }
            if ($ch -eq "`n") { $line++; $col = 1 } else { $col++ }
            $i++
        }
        continue
    }
    
    # Check Regex literal /.../
    # In JS, '/' is regex if preceding token is an operator or keyword or opening paren
    if ($c -eq '/') {
        # Check if it could be regex: look back past whitespace
        $k = $i - 1
        while ($k -ge 0 -and [char]::IsWhiteSpace($text[$k])) { $k-- }
        $prevChar = if ($k -ge 0) { $text[$k] } else { '' }
        if ($prevChar -in @('(', ',', '=', ':', '[', '!', '&', '|', '?', ';', '{', '}', '~', '+', '-', '*', '^', '%')) {
            # Likely regex literal
            $i++
            $col++
            $inClass = $false
            while ($i -lt $len) {
                $ch = $text[$i]
                if ($ch -eq '\') {
                    $i += 2
                    $col += 2
                    continue
                }
                if ($ch -eq '[') { $inClass = $true }
                if ($ch -eq ']') { $inClass = $false }
                if ($ch -eq '/' -and -not $inClass) {
                    $i++
                    $col++
                    # skip flags
                    while ($i -lt $len -and $text[$i] -match '[gimsuy]') {
                        $i++
                        $col++
                    }
                    break
                }
                if ($ch -eq "`n") { break } # regex cannot span newline without \
                $i++
                $col++
            }
            continue
        }
    }
    
    # Check open brackets
    if ($c -in @('(', '{', '[')) {
        $stack.Push([PSCustomObject]@{ Char = $c; Line = $line; Col = $col })
        $i++
        $col++
        continue
    }
    
    # Check close brackets
    if ($c -in @(')', '}', ']')) {
        if ($stack.Count -eq 0) {
            Write-Output "ERROR: Unexpected closing '$c' at line $line, col $col"
            exit 1
        }
        $top = $stack.Pop()
        $expected = switch ($top.Char) {
            '(' { ')' }
            '{' { '}' }
            '[' { ']' }
            '`{' { '}' }
        }
        if ($c -ne $expected) {
            Write-Output "ERROR: Mismatched bracket at line $line, col $col. Expected '$expected' for '$($top.Char)' from line $($top.Line), but found '$c'"
            exit 1
        }
        
        # If closing a template expression ``, resume scanning template string
        if ($top.Char -eq '`{') {
            # continue scanning template string until closing backtick
            $i++
            $col++
            while ($i -lt $len) {
                $ch = $text[$i]
                if ($ch -eq '\') { $i += 2; $col += 2; continue }
                if ($ch -eq '`') { $i++; $col++; break }
                if ($ch -eq '$' -and $i + 1 -lt $len -and $text[$i+1] -eq '{') {
                    $i += 2; $col += 2
                    $stack.Push([PSCustomObject]@{ Char = '`{'; Line = $line; Col = $col })
                    break
                }
                if ($ch -eq "`n") { $line++; $col = 1 } else { $col++ }
                $i++
            }
            continue
        }
        
        $i++
        $col++
        continue
    }
    
    $i++
    $col++
}

if ($stack.Count -gt 0) {
    Write-Output "ERROR: Unclosed delimiters at end of file:"
    foreach ($item in $stack) {
        Write-Output "  Unclosed '$($item.Char)' opened at line $($item.Line), col $($item.Col)"
    }
    exit 1
}

Write-Output "SUCCESS: All delimiters in $path are perfectly balanced! (Lines: $line)"
