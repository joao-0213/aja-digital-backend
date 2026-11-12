$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'aja-toolchain\jdk-21.0.13+9'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$env:PATH = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
$project = "aja-b08-snapshots-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$tempRoot = Join-Path $env:TEMP $project
$null = New-Item -ItemType Directory -Path $tempRoot
$composeFile = Join-Path $tempRoot 'compose.yaml'
$envFile = Join-Path $tempRoot 'compose.env'
$coreOut = Join-Path $tempRoot 'core.out.log'
$coreErr = Join-Path $tempRoot 'core.err.log'
$questionsOut = Join-Path $tempRoot 'questions.out.log'
$questionsErr = Join-Path $tempRoot 'questions.err.log'
$coreProcess = $null
$questionsProcess = $null
$composeStarted = $false

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Wait-Healthy([string] $url, [int] $timeoutSeconds = 120) {
    $until = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $until) {
        try {
            if ((Invoke-RestMethod -Uri $url -TimeoutSec 2).status -eq 'UP') { return }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Service did not become healthy: $url"
}

function Invoke-Psql([string] $sql) {
    $sql | & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo exec -T postgres psql -U aja_owner -d aja -v ON_ERROR_STOP=1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance fixture SQL failed.' }
}

function New-Session([string] $role, [string] $userId) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $cookie = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $hashBytes = [System.Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($cookie))
    $hash = ([BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
    $email = "$userId@example.test"
    $sql = "INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES ('$userId',1,now(),now(),'$userId','B08 $role','$email','$email','fixture-only','${role}',true,now()); INSERT INTO core.sessions(id,user_id,token_hash,created_at,expires_at) VALUES ('$([guid]::NewGuid().ToString())','$userId','$hash',now(),now()+interval '1 hour');"
    Invoke-Psql $sql
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $session.Cookies.Add([uri]"http://localhost:$script:corePort", [System.Net.Cookie]::new('aja_session', $cookie, '/', 'localhost'))
    return $session
}

function Post-Json([string] $url, $session, [string] $key, $body) {
    Invoke-RestMethod -Uri $url -Method Post -WebSession $session -Headers @{ Origin = 'https://aja.example.test'; 'Idempotency-Key' = $key } -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 12 -Compress)
}

function Get-Json([string] $url, $session) {
    Invoke-RestMethod -Uri $url -Method Get -WebSession $session -Headers @{ Origin = 'https://aja.example.test' }
}

function Patch-Json([string] $url, $session, [string] $key, $body) {
    Invoke-RestMethod -Uri $url -Method Patch -WebSession $session -Headers @{ Origin = 'https://aja.example.test'; 'Idempotency-Key' = $key } -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 12 -Compress)
}

try {
    if (-not (Test-Path $docker) -or -not (Test-Path $java)) { throw 'Configured Docker Desktop or JDK 21 executable is missing.' }
    foreach ($service in @('core', 'questions')) {
        Push-Location (Join-Path $repo "services/$service")
        try { & .\gradlew.bat bootJar; if ($LASTEXITCODE -ne 0) { throw "$service bootJar failed." } } finally { Pop-Location }
    }
    $coreJar = Join-Path $repo 'services/core/build/libs/core-0.0.1-SNAPSHOT.jar'
    $questionsJar = Join-Path $repo 'services/questions/build/libs/questions-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $coreJar) -or -not (Test-Path $questionsJar)) { throw 'A service boot jar was not produced.' }

    $pgPort = Get-FreePort
    $rabbitPort = Get-FreePort
    $managementPort = Get-FreePort
    $script:corePort = Get-FreePort
    $questionsPort = Get-FreePort
    $compose = Get-Content (Join-Path $repo 'compose.yaml') -Raw
    $compose = $compose.Replace('ports: ["5432:5432"]', "ports: [`"$pgPort`:5432`"]").Replace('ports: ["5672:5672", "15672:15672"]', "ports: [`"$rabbitPort`:5672`", `"$managementPort`:15672`"]")
    Set-Content -LiteralPath $composeFile -Value $compose -Encoding utf8
    @(
        'POSTGRES_USER=aja_owner', 'POSTGRES_PASSWORD=b08-postgres-isolated',
        'CORE_DATABASE_PASSWORD=b08-core-isolated', 'QUESTIONS_DATABASE_PASSWORD=b08-questions-isolated',
        'EMAIL_DATABASE_PASSWORD=b08-email-isolated', 'RABBITMQ_USER=b08', 'RABBITMQ_PASSWORD=b08-rabbit-isolated'
    ) | Set-Content -LiteralPath $envFile -Encoding ascii
    $composeStarted = $true
    & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo up -d --wait postgres rabbitmq
    if ($LASTEXITCODE -ne 0) { throw 'Isolated B-08 PostgreSQL/RabbitMQ stack did not start.' }

    $env:CORE_DATABASE_URL = "jdbc:postgresql://localhost:$pgPort/aja?currentSchema=core"
    $env:CORE_DATABASE_USER = 'core'
    $env:CORE_DATABASE_PASSWORD = 'b08-core-isolated'
    $env:QUESTIONS_DATABASE_URL = "jdbc:postgresql://localhost:$pgPort/aja?currentSchema=questions"
    $env:QUESTIONS_DATABASE_USER = 'questions'
    $env:QUESTIONS_DATABASE_PASSWORD = 'b08-questions-isolated'
    $env:RABBITMQ_HOST = 'localhost'
    $env:RABBITMQ_PORT = "$rabbitPort"
    $env:RABBITMQ_USER = 'b08'
    $env:RABBITMQ_PASSWORD = 'b08-rabbit-isolated'
    $env:CORE_OUTBOX_ENCRYPTION_KEY = [Convert]::ToBase64String([byte[]](0..31))
    $env:CORE_OUTBOX_OPERATOR_TOKEN = 'b08-isolated-outbox-operator-token'
    $env:CORE_QUESTIONS_SERVICE_TOKEN = 'b08-isolated-questions-service-token-012345'
    $env:CORE_QUESTIONS_SNAPSHOTS_URL = "http://localhost:$questionsPort/internal/v1/snapshots"
    $env:API_ALLOWED_ORIGINS = 'https://aja.example.test'
    $env:CORE_INTERNAL_SESSION_URL = "http://localhost:$script:corePort/internal/v1/session"
    $env:SERVER_PORT = "$questionsPort"
    $questionsProcess = Start-Process -FilePath $java -ArgumentList @('-jar', $questionsJar) -WorkingDirectory $repo -RedirectStandardOutput $questionsOut -RedirectStandardError $questionsErr -WindowStyle Hidden -PassThru
    Wait-Healthy "http://localhost:$questionsPort/actuator/health"
    $env:SERVER_PORT = "$script:corePort"
    $env:CORE_OUTBOX_POLL_INTERVAL_MS = '1000'
    $coreProcess = Start-Process -FilePath $java -ArgumentList @('-jar', $coreJar) -WorkingDirectory $repo -RedirectStandardOutput $coreOut -RedirectStandardError $coreErr -WindowStyle Hidden -PassThru
    Wait-Healthy "http://localhost:$script:corePort/actuator/health"

    $prefix = [guid]::NewGuid().ToString('N')
    $adminId = [guid]::NewGuid().ToString()
    $teacherId = [guid]::NewGuid().ToString()
    $admin = New-Session 'admin' $adminId
    $teacher = New-Session 'teacher' $teacherId
    $baseUrl = "http://localhost:$script:corePort/api/v1"
    $class = Post-Json "$baseUrl/classes" $admin "$prefix-class" @{ name = 'B08 isolated'; code = "B08-$($prefix.Substring(0, 8))"; year = 2026; shift = 'Noite' }
    $subject = Post-Json "$baseUrl/subjects" $admin "$prefix-subject" @{ classId = $class.id; name = 'B08 isolated'; teacherIds = @($teacherId); color = 'blue'; icon = 'book'; description = '' }
    $module = Post-Json "$baseUrl/modules" $teacher "$prefix-module" @{ subjectId = $subject.id; title = 'B08 snapshot module'; description = '' }

    $questionId = "bank-$prefix"
    $fileId = [guid]::NewGuid().ToString()
    $fileHash = ([BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash([byte[]](1, 2, 3, 4)))).Replace('-', '').ToLowerInvariant()
    Invoke-Psql "INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at) VALUES ('$fileId','question','$questionId','figure.png','image/png',4,'$fileHash','attached',decode('01020304','hex'),now()); INSERT INTO questions.question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation,image_file_id,image_alt,withdrawn_at) VALUES ('$questionId',1,now(),'Math','Algebra','easy','AJA',2026,'Original statement','{four,five}',0,'Original rationale','$fileId','Original image',NULL);"
    $created = Post-Json "$baseUrl/assessments" $teacher "$prefix-create" @{ moduleId = $module.id; title = 'Snapshot source'; description = ''; kind = 'quiz'; maxGrade = 5; deadline = $null; required = $true; allowLate = $false; questions = @(@{ source = 'bank'; questionId = $questionId }) }
    if ($created.questions[0].statement -ne 'Original statement') { throw 'Initial Core↔Questions snapshot was not captured.' }
    $assessmentId = $created.id
    $copiedId = [regex]::Match($created.questions[0].image.src, '/files/([^/]+)/content$').Groups[1].Value
    if (-not $copiedId) { throw 'Assessment image was not copied into Core.' }
    $beforePath = Join-Path $tempRoot 'image-before.bin'
    $afterPath = Join-Path $tempRoot 'image-after.bin'
    $null = Invoke-WebRequest -Uri "$baseUrl/files/$copiedId/content" -WebSession $teacher -Headers @{ Origin = 'https://aja.example.test' } -OutFile $beforePath

    Invoke-Psql "UPDATE questions.question_bank SET revision=revision+1,statement='Changed source',explanation='Changed rationale',withdrawn_at=now() WHERE id='$questionId';"
    $afterSourceChange = Get-Json "$baseUrl/assessments/$assessmentId" $teacher
    if ($afterSourceChange.questions[0].statement -ne 'Original statement') { throw 'Captured question changed after source edit/withdrawal.' }
    $null = Invoke-WebRequest -Uri "$baseUrl/files/$copiedId/content" -WebSession $teacher -Headers @{ Origin = 'https://aja.example.test' } -OutFile $afterPath
    if (([Convert]::ToBase64String([IO.File]::ReadAllBytes($beforePath))) -ne ([Convert]::ToBase64String([IO.File]::ReadAllBytes($afterPath)))) { throw 'Captured image bytes changed after source withdrawal.' }

    Stop-Process -Id $questionsProcess.Id -Force
    $questionsProcess = $null
    $metadata = Patch-Json "$baseUrl/assessments/$assessmentId" $teacher "$prefix-metadata" @{ expectedRevision = 1; title = 'Metadata while Questions is down' }
    if ($metadata.title -ne 'Metadata while Questions is down' -or $metadata.questions[0].statement -ne 'Original statement') { throw 'Metadata-only edit failed to preserve the draft snapshot while Questions was down.' }

    $newQuestionId = "new-$prefix"
    $failedPatchBody = @{ expectedRevision = 2; confirmResetDrafts = $true; questions = @(@{ source = 'bank'; questionId = $questionId }, @{ source = 'bank'; questionId = $newQuestionId }) }
    try {
        $null = Patch-Json "$baseUrl/assessments/$assessmentId" $teacher "$prefix-provider-failure" $failedPatchBody
        throw 'New question unexpectedly succeeded while Questions was unavailable.'
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 503) { throw "Provider failure returned unexpected HTTP $status." }
    }
    $preserved = Get-Json "$baseUrl/assessments/$assessmentId" $teacher
    if ($preserved.revision -ne 2 -or $preserved.title -ne 'Metadata while Questions is down' -or $preserved.questionIds.Count -ne 1 -or $preserved.questionIds[0] -ne $questionId) { throw 'Failed snapshot update did not preserve the existing draft and snapshot.' }
    Write-Output 'B-08 isolated live acceptance passed: source edit/withdrawal kept the snapshot and image bytes; metadata edit succeeded with Questions down; adding a new ID returned 503 and preserved the draft.'
} catch {
    foreach ($log in @($coreErr, $questionsErr)) {
        if (Test-Path $log) { Get-Content $log -Tail 35 | Write-Output }
    }
    throw
} finally {
    if ($coreProcess -and -not $coreProcess.HasExited) { Stop-Process -Id $coreProcess.Id -Force }
    if ($questionsProcess -and -not $questionsProcess.HasExited) { Stop-Process -Id $questionsProcess.Id -Force }
    if ($composeStarted) {
        & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo down -v --remove-orphans | Out-Null
    }
    $resolvedTemp = [IO.Path]::GetFullPath($tempRoot)
    $tempParent = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    if ($resolvedTemp.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolvedTemp)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
