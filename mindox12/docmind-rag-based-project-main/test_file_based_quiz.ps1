# test_file_based_quiz.ps1
$baseUrl = "http://localhost:8081"

# 1. Login to get JWT
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

# 2. Get user documents
$docsResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/documents" -Method Get -Headers $headers -UseBasicParsing
$docs = $docsResp.data
Write-Host ">>> Found $($docs.Count) documents for user."
foreach ($d in $docs) {
    Write-Host "   - Doc ID: $($d.id), File: $($d.filename), Chunks: $($d.totalChunks)"
}

# 3. Test with a newly uploaded file about "Cloud Computing Fundamentals"
$cloudPath = "c:\Users\hp\OneDrive\Desktop\mindox12\mindox12\docmind-rag-based-project-main\Cloud_Computing.txt"
@"
Cloud computing is the on-demand delivery of IT resources over the internet with pay-as-you-go pricing.
Infrastructure as a Service (IaaS) provides users with fundamental compute, storage, and networking resources on demand.
Platform as a Service (PaaS) provides a managed cloud environment for developing, running, and managing applications without hardware management overhead.
Software as a Service (SaaS) delivers complete software applications over the internet accessible via web browsers.
Elasticity in cloud systems refers to the ability to automatically scale computing resources up or down according to workload demand.
Object storage systems such as Amazon S3 store unstructured data as discrete objects containing data, metadata, and unique identifiers.
A Virtual Private Cloud (VPC) provides an isolated private network within a public cloud infrastructure.
"@ | Set-Content -Path $cloudPath -Encoding utf8

Write-Host "`n>>> Uploading new file: Cloud_Computing.txt"
$uploadOut = & curl.exe -s -X POST "$baseUrl/api/v1/documents/upload" -H "Authorization: Bearer $token" -F "file=@$cloudPath"
$uploadObj = $uploadOut | ConvertFrom-Json
$cloudDocId = $uploadObj.data.id
Write-Host ">>> Cloud Doc Uploaded ID: $cloudDocId"

# 4. TEST 1: Generate Quiz strictly based on content of Cloud_Computing.txt
Write-Host "`n========================================================"
Write-Host ">>> TEST 1: Generating Quiz strictly on Cloud_Computing.txt"
Write-Host "========================================================"
$genBody = @{
    documentId = $cloudDocId
    questionCount = 4
    difficulty = "MEDIUM"
    questionTypes = @("MCQ", "TRUE_FALSE", "FILL_IN_THE_BLANK")
} | ConvertTo-Json

$quizResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/generate" -Method Post -Headers $headers -ContentType "application/json" -Body $genBody -UseBasicParsing
$quiz = $quizResp.data

Write-Host "Quiz Generated Successfully!"
Write-Host "Quiz Title: $($quiz.title)"
Write-Host "Total Questions: $($quiz.questions.Count)"
foreach ($q in $quiz.questions) {
    Write-Host "`n[Question ID: $($q.id) | Type: $($q.type) | Diff: $($q.difficulty)]"
    Write-Host "  Q: $($q.question)"
    if ($q.codeSnippet) {
        Write-Host "  Code Snippet:`n$($q.codeSnippet)"
    }
    Write-Host "  Options:"
    for ($i = 0; $i -lt $q.options.Count; $i++) {
        Write-Host "    ($([char](65 + $i))) $($q.options[$i])"
    }
    Write-Host "  Explanation: $($q.explanation)"
}

# 5. TEST 2: File Switching - Generate from another document (e.g. Generics or Java Quiz doc)
Write-Host "`n========================================================"
Write-Host ">>> TEST 2: File Switching (Verify no leak from previous Cloud file)"
Write-Host "========================================================"
$javaDocPath = "C:\Users\hp\.gemini\antigravity-ide\brain\14259ae3-4885-48f5-99a7-29dcb444fd3e\scratch\Sample_Java_Quiz_Doc.txt"
$javaUpload = & curl.exe -s -X POST "$baseUrl/api/v1/documents/upload" -H "Authorization: Bearer $token" -F "file=@$javaDocPath" | ConvertFrom-Json
$javaDocId = $javaUpload.data.id

$javaGenBody = @{
    documentId = $javaDocId
    questionCount = 3
    difficulty = "HARD"
    questionTypes = @("CODE", "OUTPUT_PREDICTION", "DEBUGGING")
} | ConvertTo-Json

$javaQuizResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/generate" -Method Post -Headers $headers -ContentType "application/json" -Body $javaGenBody -UseBasicParsing
$javaQuiz = $javaQuizResp.data
Write-Host "Java Quiz Generated: Title='$($javaQuiz.title)', Count=$($javaQuiz.questions.Count)"
foreach ($jq in $javaQuiz.questions) {
    Write-Host "`n[Java Q: $($jq.id) | Type: $($jq.type)]"
    Write-Host "  Q: $($jq.question)"
    if ($jq.codeSnippet) {
        Write-Host "  Code Block:`n$($jq.codeSnippet)"
    }
}

# 6. TEST 3: Multi-file selection combining both files
Write-Host "`n========================================================"
Write-Host ">>> TEST 3: Multi-File Selection (Cloud + Java documents combined)"
Write-Host "========================================================"
$multiBody = @{
    documentIds = @($cloudDocId, $javaDocId)
    questionCount = 4
    difficulty = "MIXED"
    questionTypes = @("MCQ", "TRUE_FALSE")
} | ConvertTo-Json

$multiResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/generate" -Method Post -Headers $headers -ContentType "application/json" -Body $multiBody -UseBasicParsing
$mQuiz = $multiResp.data
Write-Host "Multi-Document Quiz Created: Title='$($mQuiz.title)'"
Write-Host "Questions Count: $($mQuiz.questions.Count)"
foreach ($mq in $mQuiz.questions) {
    Write-Host "  Q: $($mq.question)"
}

# 7. TEST 4: Validation on empty/insufficient content
Write-Host "`n========================================================"
Write-Host ">>> TEST 4: Validation on Insufficient Readable Content"
Write-Host "========================================================"
$emptyPath = "c:\Users\hp\OneDrive\Desktop\mindox12\mindox12\docmind-rag-based-project-main\Empty_Test.txt"
"Short" | Set-Content -Path $emptyPath -Encoding utf8
$emptyUpload = & curl.exe -s -X POST "$baseUrl/api/v1/documents/upload" -H "Authorization: Bearer $token" -F "file=@$emptyPath" | ConvertFrom-Json
$emptyDocId = $emptyUpload.data.id

try {
    $badReq = @{
        documentId = $emptyDocId
        questionCount = 3
    } | ConvertTo-Json
    $badResp = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/generate" -Method Post -Headers $headers -ContentType "application/json" -Body $badReq -UseBasicParsing
    Write-Host "FAIL: Expected 400 Bad Request but succeeded"
} catch {
    Write-Host "PASS: Successfully caught expected validation error: $($_.Exception.Message)"
}

# 8. TEST 5: PDF Verification
Write-Host "`n========================================================"
Write-Host ">>> TEST 5: Automatic PDF Generation & Preview"
Write-Host "========================================================"
$pdfStatus = Invoke-RestMethod -Uri "$baseUrl/api/v1/quiz/$($quiz.id)/pdf/status" -Method Get -Headers $headers -UseBasicParsing
Write-Host "PDF Status: Available=$($pdfStatus.data.available), Size=$($pdfStatus.data.fileSizeBytes) bytes"
Write-Host "PDF Download URL: $($pdfStatus.data.downloadUrl)"
Write-Host "PDF Preview URL: $($pdfStatus.data.previewUrl)"
