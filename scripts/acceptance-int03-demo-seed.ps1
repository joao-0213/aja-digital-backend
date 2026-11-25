param(
    [string]$Project = 'aja-int03-acceptance',
    [int]$Port = 33123
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $env:TEMP "$Project.env"
$sampleEnv = Get-Content (Join-Path $root 'env.integrated.example') -Raw
$sampleEnv = $sampleEnv -replace '(?m)^GATEWAY_PORT=.*$', "GATEWAY_PORT=$Port"
$sampleEnv = $sampleEnv -replace 'CORE_PUBLIC_API_URL=http://127.0.0.1:3002/api/v1', "CORE_PUBLIC_API_URL=http://127.0.0.1:$Port/api/v1"
Set-Content -Path $envFile -Value $sampleEnv -Encoding ascii
Push-Location $root
try {
    docker compose --env-file $envFile -f compose.integrated.yaml -p $Project rm --force demo-seed | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not remove the prior one-shot seed container' }
    docker compose --env-file $envFile -f compose.integrated.yaml -p $Project up --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Integrated Compose did not become ready' }

    $headers = @{ Origin = 'http://127.0.0.1:3000' }
    $health = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health"
    foreach ($account in @(
        @{ Email = 'admin@aja.local.test'; Role = 'admin' },
        @{ Email = 'professora@aja.local.test'; Role = 'teacher' },
        @{ Email = 'aluno@aja.local.test'; Role = 'student' }
    )) {
        $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
        $login = @{ email = $account.Email; password = 'AjaDemo-2026!' } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/v1/auth/login" -ContentType 'application/json' -Body $login -WebSession $session -Headers $headers | Out-Null
        $dashboard = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/me/dashboard" -WebSession $session -Headers $headers
        if ($account.Role -eq 'admin' -and $dashboard.studentCount -lt 1) { throw 'Admin dashboard has no enrolled students' }
        if ($account.Role -eq 'teacher' -and $dashboard.pendingFeedbackCount -lt 1) { throw 'Teacher dashboard has no pending feedback' }
        $cookies = @($session.Cookies.GetCookies([uri]"http://127.0.0.1:$Port/api/v1"))
        if ($cookies.Count -ne 1 -or $cookies[0].Name -ne 'aja_session') { throw "Login did not issue aja_session for $($account.Role)" }
    }

    $questionSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $login = @{ email = 'aluno@aja.local.test'; password = 'AjaDemo-2026!' } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/v1/auth/login" -ContentType 'application/json' -Body $login -WebSession $questionSession -Headers $headers | Out-Null
    $questions = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/questions" -WebSession $questionSession -Headers $headers
    if (-not $questions) { throw 'Question catalogue returned no demo records' }
    $illustrated = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/questions/demo-question-2" -WebSession $questionSession -Headers $headers
    if ($illustrated.image.src -notlike "*:$Port/api/v1/files/demo-file-question/content") { throw 'Question illustration does not point to this gateway' }
    Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/attempts/demo-attempt/result" -WebSession $questionSession -Headers $headers | Out-Null
    foreach ($path in @('/attendance', '/calendar', '/topics', '/materials')) {
        Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1$path" -WebSession $questionSession -Headers $headers | Out-Null
    }
    $feedbackSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $feedbackLogin = @{ email = 'bia@aja.local.test'; password = 'AjaDemo-2026!' } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/v1/auth/login" -ContentType 'application/json' -Body $feedbackLogin -WebSession $feedbackSession -Headers $headers | Out-Null
    $teacherSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $teacherLogin = @{ email = 'professora@aja.local.test'; password = 'AjaDemo-2026!' } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/v1/auth/login" -ContentType 'application/json' -Body $teacherLogin -WebSession $teacherSession -Headers $headers | Out-Null
    $published = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/submissions/demo-submission" -WebSession $teacherSession -Headers $headers
    if ($published.feedbackPublished.author.id -ne 'demo-teacher' -or $published.feedbackHistory.Count -lt 1) { throw 'Published feedback author or history is missing' }
    $assessment = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/assessments/demo-draft" -WebSession $teacherSession -Headers $headers
    $patchHeaders = @{ Origin = 'http://127.0.0.1:3000'; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $question = $assessment.questions[0]
    $selection = @{
        source = 'authored'
        clientKey = 'demo-authored-1'
        questionId = $question.id
        question = @{
            subject = $question.subject
            topic = $question.topic
            difficulty = $question.difficulty
            institution = $question.institution
            year = $question.year
            statement = $question.statement
            options = $question.options
            image = $question.image
            correct = $question.correct
            explanation = $question.explanation
        }
    }
    $patchBody = @{ expectedRevision = $assessment.revision; questions = @($selection) } | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Method Patch -Uri "http://127.0.0.1:$Port/api/v1/assessments/demo-draft" -ContentType 'application/json' -Body $patchBody -WebSession $teacherSession -Headers $patchHeaders | Out-Null
    foreach ($asset in @(@{ Id = 'demo-file-pdf' }, @{ Id = 'demo-file-question' })) {
        $assetPath = Join-Path $env:TEMP "$Project-$($asset.Id).bin"
        Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/v1/files/$($asset.Id)/content" -WebSession $questionSession -Headers $headers -OutFile $assetPath
        if ((Get-Item $assetPath).Length -eq 0) { throw "Demo asset $($asset.Id) is empty" }
        Remove-Item $assetPath -ErrorAction SilentlyContinue
    }

    $before = docker compose --env-file $envFile -f compose.integrated.yaml -p $Project exec -T postgres sh -c 'PGPASSWORD="$CORE_DATABASE_PASSWORD" psql -U core -d aja -Atc "select (select count(*) from core.users where id like ''demo-%'') || '','' || (select count(*) from core.assessments where id like ''demo-%'') || '','' || (select count(*) from core.topics where id like ''demo-%'')"'
    if ($LASTEXITCODE -ne 0) { throw 'Could not query seeded Core records' }
    docker compose --env-file $envFile -f compose.integrated.yaml -p $Project run --rm --no-deps demo-seed
    if ($LASTEXITCODE -ne 0) { throw 'Second seed run failed' }
    $after = docker compose --env-file $envFile -f compose.integrated.yaml -p $Project exec -T postgres sh -c 'PGPASSWORD="$CORE_DATABASE_PASSWORD" psql -U core -d aja -Atc "select (select count(*) from core.users where id like ''demo-%'') || '','' || (select count(*) from core.assessments where id like ''demo-%'') || '','' || (select count(*) from core.topics where id like ''demo-%'')"'
    if ($before.Trim() -ne $after.Trim()) { throw "Repeat seed changed counts ($before -> $after)" }

    Write-Output "INT-03 accepted. Gateway: http://127.0.0.1:$Port/api/v1. Demo accounts share password AjaDemo-2026!. Compose project $Project remains running."
}
finally {
    Pop-Location
    Write-Output "Compose environment file: $envFile"
}
