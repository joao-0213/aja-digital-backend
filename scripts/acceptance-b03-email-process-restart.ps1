$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
$java = Join-Path $env:LOCALAPPDATA 'aja-toolchain\jdk-21.0.13+9\bin\java.exe'
$project = "aja-b03-email-restart-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$tempRoot = Join-Path $env:TEMP "aja-b03-email-restart-$([guid]::NewGuid().ToString('N'))"
$null = New-Item -ItemType Directory -Path $tempRoot
$composeFile = Join-Path $tempRoot 'compose.yaml'
$envFile = Join-Path $tempRoot 'compose.env'
$providerFile = Join-Path $tempRoot 'provider.py'
$capturedIds = Join-Path $tempRoot 'provider-event-ids.txt'
$capturedResponses = Join-Path $tempRoot 'provider-responses.txt'
$providerOut = Join-Path $tempRoot 'provider.out.log'
$providerErr = Join-Path $tempRoot 'provider.err.log'
$coreOut = Join-Path $tempRoot 'core.out.log'
$coreErr = Join-Path $tempRoot 'core.err.log'
$emailOut = Join-Path $tempRoot 'email.out.log'
$emailErr = Join-Path $tempRoot 'email.err.log'
$provider = $null
$core = $null
$email = $null

function Wait-Http([string] $url, [int] $timeoutSeconds = 90) {
    $until = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $until) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Service did not become healthy: $url"
}

function Start-JavaService([string] $jar, [string] $stdout, [string] $stderr) {
    return Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $repo `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
}

function Get-DatabaseScalar([string] $query) {
    $values = @(& $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo exec -T postgres psql -U aja -d aja -Atc $query)
    if ($values.Count -eq 0) { return '' }
    return ([string]$values[0]).Trim()
}

try {
    if (-not (Test-Path $docker) -or -not (Test-Path $java)) { throw 'Configured Docker Desktop or JDK 21 path is missing.' }
    $coreJar = Join-Path $repo 'services/core/build/libs/core-0.0.1-SNAPSHOT.jar'
    $emailJar = Join-Path $repo 'services/email/build/libs/email-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path $coreJar) -or -not (Test-Path $emailJar)) { throw 'Build the Core and Email boot jars before running this acceptance harness.' }

    $compose = (Get-Content (Join-Path $repo 'compose.yaml') -Raw).Replace('["5432:5432"]', '["5433:5432"]').Replace('["5672:5672", "15672:15672"]', '["5673:5672", "15673:15672"]')
    Set-Content -LiteralPath $composeFile -Value $compose -Encoding utf8
    @(
        'POSTGRES_USER=aja', 'POSTGRES_PASSWORD=b03-postgres-test',
        'CORE_DATABASE_PASSWORD=b03-core-test', 'QUESTIONS_DATABASE_PASSWORD=b03-questions-test',
        'EMAIL_DATABASE_PASSWORD=b03-email-test', 'RABBITMQ_USER=aja', 'RABBITMQ_PASSWORD=b03-rabbit-test'
    ) | Set-Content -LiteralPath $envFile -Encoding ascii
    & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Fresh B-03 acceptance PostgreSQL/RabbitMQ stack did not start.' }

    @'
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os, time
import json
from urllib.parse import parse_qs, urlparse

mode = {"value": "retry"}
counts = {}
captured_bodies = {}

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def do_GET(self):
        if self.path == "/block":
            mode["value"] = "block"
            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if self.path.startswith("/token?"):
            event_id = parse_qs(urlparse(self.path).query).get("eventId", [""])[0]
            request_body = captured_bodies.get(event_id)
            token = ""
            if request_body is not None:
                token = parse_qs(urlparse(request_body.get("text", "")).query).get("token", [""])[0]
            if not token:
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            token_bytes = token.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(token_bytes)))
            self.end_headers()
            self.wfile.write(token_bytes)
            return
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()
    def do_POST(self):
        event_id = self.headers.get("Idempotency-Key", "")
        request_size = int(self.headers.get("Content-Length", "0"))
        captured_bodies[event_id] = json.loads(self.rfile.read(request_size))
        counts[event_id] = counts.get(event_id, 0) + 1
        with open(os.environ["B03_CAPTURE_PATH"], "a", encoding="utf-8") as output:
            output.write(event_id + "\n")
        if mode["value"] == "block":
            time.sleep(60)
            response_status = 202
        else:
            response_status = 429 if counts[event_id] == 1 else 202
        with open(os.environ["B03_RESPONSE_PATH"], "a", encoding="utf-8") as output:
            output.write(f"{event_id}|{response_status}\n")
        self.send_response(response_status)
        self.send_header("Content-Length", "0")
        self.end_headers()
    def log_message(self, fmt, *args):
        pass

ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
'@ | Set-Content -LiteralPath $providerFile -Encoding ascii

    $env:B03_CAPTURE_PATH = $capturedIds
    $env:B03_RESPONSE_PATH = $capturedResponses
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $provider = Start-Process -FilePath $python -ArgumentList @($providerFile) -WorkingDirectory $tempRoot `
        -RedirectStandardOutput $providerOut -RedirectStandardError $providerErr -WindowStyle Hidden -PassThru

    $env:CORE_DATABASE_URL = 'jdbc:postgresql://localhost:5433/aja?currentSchema=core'
    $env:CORE_DATABASE_USER = 'core'
    $env:CORE_DATABASE_PASSWORD = 'b03-core-test'
    $env:EMAIL_DATABASE_URL = 'jdbc:postgresql://localhost:5433/aja?currentSchema=email'
    $env:EMAIL_DATABASE_USER = 'email'
    $env:EMAIL_DATABASE_PASSWORD = 'b03-email-test'
    $env:RABBITMQ_HOST = 'localhost'
    $env:RABBITMQ_PORT = '5673'
    $env:RABBITMQ_USER = 'aja'
    $env:RABBITMQ_PASSWORD = 'b03-rabbit-test'
    $env:CORE_OUTBOX_ENCRYPTION_KEY = [Convert]::ToBase64String([byte[]](0..31))
    $env:CORE_OUTBOX_OPERATOR_TOKEN = 'b03-core-operator-test'
    $env:CORE_QUESTIONS_SERVICE_TOKEN = 'b03-questions-test-token-0123456789'
    $env:EMAIL_DELIVERY_OPERATOR_TOKEN = 'b03-email-operator-test'
    $env:EMAIL_PROVIDER_URL = 'http://127.0.0.1:8765/email'
    $env:EMAIL_PROVIDER_TOKEN = 'b03-provider-test-token'
    $env:EMAIL_RETRY_DELAY_MS = '50'
    $env:API_ALLOWED_ORIGINS = 'https://aja.example.test'
    $env:SERVER_PORT = '8081'
    $core = Start-JavaService $coreJar $coreOut $coreErr
    Wait-Http 'http://127.0.0.1:8081/api/v1/health'

    $retryEmail = "retry-$([guid]::NewGuid().ToString('N'))@example.test"
    $retryRegistration = @{ name = 'B03 Retry Probe'; email = $retryEmail; password = 'A long test password' } | ConvertTo-Json -Compress
    $registerResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/register' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $retryRegistration -UseBasicParsing
    if ($registerResponse.StatusCode -ne 201) { throw "Core registration returned $($registerResponse.StatusCode)." }

    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $retryEventId = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $retryEventId = Get-DatabaseScalar "select id from core.outbox_events where event_type='identity.email-confirmation-requested' order by created_at desc limit 1"
        if ($retryEventId) { break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $retryEventId) { throw 'Core did not persist an outbox event for the retry flow.' }
    if ($retryEventId -notmatch '^[0-9a-fA-F-]{36}$') { throw 'Provider captured an invalid event ID.' }

    $outboxSql = "select case when published_at is null then 'pending' else 'published' end || ':' || coalesce(last_error,'') from core.outbox_events where id='$retryEventId'"
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    $outboxState = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $outboxState = Get-DatabaseScalar $outboxSql
        if ($outboxState.StartsWith('pending:') -and $outboxState.Length -gt 'pending:'.Length) { break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $outboxState.StartsWith('pending:') -or $outboxState.Length -le 'pending:'.Length) { throw "Core did not retain the unroutable event with an observable retry failure before Email startup; state=$outboxState" }
    $unroutableAttempts = Get-DatabaseScalar "select attempt_count from core.outbox_events where id='$retryEventId'"
    if ([int]$unroutableAttempts -lt 1) { throw 'Unroutable event did not increment the outbox retry count.' }
    & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo exec -T postgres psql -U aja -d aja -c "update core.outbox_events set next_attempt_at=now() + interval '1 hour' where id='$retryEventId'" | Out-Null

    $env:SERVER_PORT = '8083'
    $email = Start-JavaService $emailJar $emailOut $emailErr
    Wait-Http 'http://127.0.0.1:8083/health'
    & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo exec -T postgres psql -U aja -d aja -c "update core.outbox_events set next_attempt_at=now() where id='$retryEventId'" | Out-Null
    $retrySql = "select status || ':' || attempt_count from email.delivery_events where event_id='$retryEventId'"
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $retryState = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $retryState = Get-DatabaseScalar $retrySql
        $retryCalls = if (Test-Path $capturedIds) { @((Get-Content $capturedIds | Where-Object { $_.Trim() -eq $retryEventId })).Count } else { 0 }
        $outboxState = Get-DatabaseScalar $outboxSql
        if ($outboxState -eq 'published:' -and $retryState -match '^sent:[2-9][0-9]*$' -and $retryCalls -ge 2) { break }
        Start-Sleep -Milliseconds 200
    }
    $retryCalls = @(Get-Content $capturedIds | Where-Object { $_.Trim() -eq $retryEventId }).Count
    $retryResponses = @(Get-Content $capturedResponses | Where-Object { $_.StartsWith("$retryEventId|") } | ForEach-Object { $_.Split('|')[1] })
    $outboxAttempts = Get-DatabaseScalar "select attempt_count from core.outbox_events where id='$retryEventId'"
    if ($outboxState -ne 'published:' -or [int]$outboxAttempts -lt 2 -or $retryState -notmatch '^sent:[2-9][0-9]*$' -or $retryCalls -ne 2 -or ($retryResponses -join ',') -ne '429,202') {
        throw "Unroutable event was not eventually delivered and retried after Email binding; outbox=$outboxState; delivery=$retryState; provider requests=$retryCalls; responses=$($retryResponses -join ',')"
    }

    $confirmationToken = (Invoke-WebRequest -Uri "http://127.0.0.1:8765/token?eventId=$retryEventId" -UseBasicParsing).Content
    if ([string]::IsNullOrWhiteSpace($confirmationToken)) { throw 'Email provider did not retain the confirmation token in process memory.' }
    $confirmationRequest = @{ token = $confirmationToken } | ConvertTo-Json -Compress
    $confirmationResult = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/email-confirmations' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $confirmationRequest -UseBasicParsing
    if ($confirmationResult.StatusCode -ne 200) { throw "Core did not accept the token captured from the confirmation email; status=$($confirmationResult.StatusCode)." }
    $confirmed = Get-DatabaseScalar "select email_confirmed_at is not null from core.users where normalized_email='$retryEmail'"
    if ($confirmed -ne 't') { throw 'Core did not persist confirmation using the worker-delivered token.' }
    $confirmationToken = $null
    $confirmationRequest = $null

    $resetRequest = @{ email = $retryEmail } | ConvertTo-Json -Compress
    $resetResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/password-reset-requests' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $resetRequest -UseBasicParsing
    if ($resetResponse.StatusCode -ne 202) { throw "Core password reset request returned $($resetResponse.StatusCode)." }
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $resetEventId = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $resetEventId = Get-DatabaseScalar "select id from core.outbox_events where event_type='identity.password-reset-requested' order by created_at desc limit 1"
        if ($resetEventId) { break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $resetEventId) { throw 'Core did not persist a password reset outbox event.' }
    $resetDeliverySql = "select status || ':' || attempt_count from email.delivery_events where event_id='$resetEventId'"
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $resetDeliveryState = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $resetDeliveryState = Get-DatabaseScalar $resetDeliverySql
        $resetCalls = if (Test-Path $capturedIds) { @((Get-Content $capturedIds | Where-Object { $_.Trim() -eq $resetEventId })).Count } else { 0 }
        if ($resetDeliveryState -match '^sent:[1-9][0-9]*$' -and $resetCalls -ge 1) { break }
        Start-Sleep -Milliseconds 200
    }
    if ($resetDeliveryState -notmatch '^sent:[1-9][0-9]*$') { throw "Email worker did not send the reset event; state=$resetDeliveryState" }
    $resetToken = (Invoke-WebRequest -Uri "http://127.0.0.1:8765/token?eventId=$resetEventId" -UseBasicParsing).Content
    if ([string]::IsNullOrWhiteSpace($resetToken)) { throw 'Email provider did not retain the reset token in process memory.' }
    $oldPasswordHash = Get-DatabaseScalar "select password_hash from core.users where normalized_email='$retryEmail'"
    $newPassword = 'B03 worker delivered reset password'
    $passwordResetPayload = @{ token = $resetToken; password = $newPassword } | ConvertTo-Json -Compress
    $passwordResetResult = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/password-resets' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $passwordResetPayload -UseBasicParsing
    if ($passwordResetResult.StatusCode -ne 200) { throw "Core did not accept the token captured from the reset email; status=$($passwordResetResult.StatusCode)." }
    $newPasswordHash = Get-DatabaseScalar "select password_hash from core.users where normalized_email='$retryEmail'"
    $resetTokenConsumed = Get-DatabaseScalar "select used_at is not null from core.auth_tokens where email_at_issue='$retryEmail' and purpose='password_reset' order by created_at desc limit 1"
    if ($oldPasswordHash -eq $newPasswordHash -or $resetTokenConsumed -ne 't') { throw 'Core did not persist password reset using the worker-delivered token.' }
    $loginRequest = @{ email = $retryEmail; password = $newPassword } | ConvertTo-Json -Compress
    $newLogin = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $loginRequest -UseBasicParsing
    if ($newLogin.StatusCode -ne 200) { throw 'Core login did not accept the password set using the worker-delivered reset token.' }
    $resetToken = $null
    $passwordResetPayload = $null
    $loginRequest = $null

    Invoke-WebRequest -Uri 'http://127.0.0.1:8765/block' -UseBasicParsing | Out-Null

    $registration = @{ name = 'B03 Restart Probe'; email = "restart-$([guid]::NewGuid().ToString('N'))@example.test"; password = 'A long test password' } | ConvertTo-Json -Compress
    $registerResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:8081/api/v1/auth/register' -Method Post `
        -Headers @{ Origin = 'https://aja.example.test' } -ContentType 'application/json' -Body $registration -UseBasicParsing
    if ($registerResponse.StatusCode -ne 201) { throw "Core restart probe registration returned $($registerResponse.StatusCode)." }

    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $eventId = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $eventId = (Get-Content $capturedIds | Where-Object { $_.Trim() -and $_.Trim() -ne $retryEventId -and $_.Trim() -ne $resetEventId } | Select-Object -First 1)
        if ($eventId) { break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $eventId) { throw 'Provider did not capture an event ID for the restart flow.' }
    $eventId = $eventId.Trim()

    $sql = "select status from email.delivery_events where event_id='$eventId'"
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $status = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $status = Get-DatabaseScalar $sql
        if ($status -eq 'sending') { break }
        Start-Sleep -Milliseconds 200
    }
    if ($status -ne 'sending') { throw "Email delivery did not persist sending before provider blocked; status=$status" }
    $emailPid = $email.Id
    Stop-Process -Id $emailPid -Force
    $email = $null
    Start-Sleep -Seconds 2

    $env:SERVER_PORT = '8083'
    $email = Start-JavaService $emailJar $emailOut $emailErr
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $status = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        $status = Get-DatabaseScalar $sql
        if ($status -eq 'unknown') { break }
        Start-Sleep -Milliseconds 250
    }
    if ($status -ne 'unknown') { throw "Restart did not reconcile redelivered sending event to unknown; status=$status" }
    $captured = @(Get-Content $capturedIds | Where-Object { $_.Trim() -eq $eventId }).Count
    if ($captured -ne 1) { throw "Expected exactly one provider request across the restart, observed $captured." }
    Write-Output "PASS: Compose project=$project; Core-first unroutable outbox retained then delivered after Email binding; worker-delivered confirmation token accepted by Core; worker-delivered reset token accepted and new password login succeeded; retry delay=50ms (provider 429->202); Email PID killed=$emailPid; restarted PID=$($email.Id); crash event=$eventId, state=sending->unknown, provider requests=$captured (provider bodies/tokens stayed in process memory; files contain only event IDs and response status codes)."
} finally {
    $confirmationToken = $null
    $resetToken = $null
    $confirmationRequest = $null
    $passwordResetPayload = $null
    $loginRequest = $null
    foreach ($process in @($email, $core, $provider)) {
        if ($null -ne $process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
    if (Test-Path $composeFile) {
        & $docker compose -p $project -f $composeFile --env-file $envFile --project-directory $repo stop
    }
    Write-Output "Preserved fresh Compose volume for project $project and existing aja-b03-acceptance_postgres-data; temporary evidence is at $tempRoot"
}
