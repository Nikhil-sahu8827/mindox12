$baseUrl = "http://localhost:8081"

$loginBody = @{
    username = "tutor_tester"
    password = "password123"
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -UseBasicParsing
$token = $login.accessToken
Write-Host ">>> Logged in successfully. Token acquired: $([bool]$token)"

$headers = @{
    "Authorization" = "Bearer $token"
}

# 1. Test Growth Dashboard
Write-Host "`n>>> Testing GET /api/v1/growth/dashboard ..."
try {
    $growth = Invoke-RestMethod -Uri "$baseUrl/api/v1/growth/dashboard" -Method Get -Headers $headers -UseBasicParsing
    Write-Host ">>> Growth Dashboard SUCCESS!"
    Write-Host "Total Quizzes Taken: $($growth.totalQuizzesTaken)"
    Write-Host "Average Score:       $($growth.averageScore)"
    Write-Host "Total Mistakes:      $($growth.totalMistakesCount)"
    Write-Host "Topic Analytics:     $($growth.topicAnalytics.Count) topics"
} catch {
    Write-Host ">>> Growth Dashboard FAILED: $_"
}

# 2. Find YouTube Document or Quiz Generation
Write-Host "`n>>> Finding Documents for Quiz generation..."
try {
    $docsRes = Invoke-RestMethod -Uri "$baseUrl/api/v1/documents" -Method Get -Headers $headers -UseBasicParsing
    $docs = $docsRes.data
    Write-Host "Total documents available: $($docs.Count)"
    
    $ytDoc = $docs | Where-Object { $_.sourceUrl -like "*youtube.com*" -or $_.filename -like "*YouTube*" } | Select-Object -First 1
    if (-not $ytDoc) {
        $ytDoc = $docs | Select-Object -First 1
    }

    if ($ytDoc) {
        Write-Host ">>> Selected Document: ID=$($ytDoc.id) | File=$($ytDoc.filename) | Source=$($ytDoc.sourceUrl)"
        $quizBody = @{
            documentId = $ytDoc.id
            numQuestions = 3
            difficulty = "MEDIUM"
        } | ConvertTo-Json

        Write-Host ">>> Calling POST /api/v1/quiz/generate ..."
        $quiz = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/generate" -Method Post -Headers $headers -Body $quizBody -ContentType "application/json" -UseBasicParsing
        Write-Host ">>> Quiz Generation SUCCESS!"
        Write-Host "Quiz ID:        $($quiz.id)"
        Write-Host "Quiz Title:     $($quiz.title)"
        Write-Host "Question Count: $($quiz.questions.Count)"
        if ($quiz.questions.Count -gt 0) {
            Write-Host "`nSample Question 1:"
            Write-Host "Question: $($quiz.questions[0].questionText)"
            Write-Host "Options:  $($quiz.questions[0].options -join ' | ')"
            Write-Host "Answer:   $($quiz.questions[0].correctAnswer)"
        }
    } else {
        Write-Host ">>> No documents found."
    }
} catch {
    Write-Host ">>> Quiz Generation FAILED: $_"
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "Response body: $($reader.ReadToEnd())"
    }
}
