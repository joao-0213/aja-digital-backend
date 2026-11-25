param(
    [string]$ApiBase = 'http://127.0.0.1:3002/api/v1',
    [string]$MailpitBase = 'http://127.0.0.1:8025',
    [string]$FrontendBase = 'http://127.0.0.1:3000'
)

$ErrorActionPreference = 'Stop'
$ApiBase = $ApiBase.TrimEnd('/')
$MailpitBase = $MailpitBase.TrimEnd('/')
$FrontendBase = $FrontendBase.TrimEnd('/')
$headers = @{ Origin = ([uri]$FrontendBase).GetLeftPart([System.UriPartial]::Authority) }

function Post-Api($path, $body, $session, $key) {
    $request = @{ Method = 'Post'; Uri = "$ApiBase$path"; Headers = $headers.Clone(); ContentType = 'application/json'; Body = ($body | ConvertTo-Json -Compress); TimeoutSec = 10 }
    if ($session) { $request.WebSession = $session }
    if ($key) { $request.Headers['Idempotency-Key'] = $key }
    try { Invoke-RestMethod @request }
    catch {
        if ($_.Exception.Response) { throw "POST $path returned HTTP $([int]$_.Exception.Response.StatusCode)" }
        throw
    }
}

function Get-Messages($recipient, $subject) {
    $query = [uri]::EscapeDataString("to:$recipient subject:`"$subject`"")
    $result = Invoke-RestMethod "$MailpitBase/api/v1/search?query=$query" -TimeoutSec 5
    $result.messages | Where-Object {
        $_.Subject -ceq $subject -and @($_.To | ForEach-Object { $_.Address }) -contains $recipient
    }
}

function Wait-Message($recipient, $subject) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $messages = @(Get-Messages $recipient $subject)
        if ($messages.Count -gt 1) { throw "Duplicate mail for $recipient and subject $subject" }
        if ($messages.Count -eq 1) {
            return Invoke-RestMethod "$MailpitBase/api/v1/message/$($messages[0].ID)" -TimeoutSec 5
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Mail did not arrive for $recipient and subject $subject"
}

function Read-ActionToken($message, $path) {
    $prefix = [regex]::Escape("$FrontendBase${path}?token=")
    $link = [regex]::Match($message.Text, "$prefix([^\s&#]+)")
    if (!$link.Success) { throw "Mail has no valid $path action link" }
    [uri]::UnescapeDataString($link.Groups[1].Value)
}

Invoke-RestMethod "$MailpitBase/readyz" -TimeoutSec 5 | Out-Null
Invoke-RestMethod "$ApiBase/health" -TimeoutSec 5 | Out-Null

$run = [guid]::NewGuid().ToString('N')
$email = "int04-$run@aja.local.test"
$oldPassword = 'Int04-demo-first!'
$newPassword = 'Int04-demo-second!'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Post-Api '/auth/register' @{ name = 'INT-04 Demo Student'; email = $email; password = $oldPassword } | Out-Null
$confirmation = Wait-Message $email 'Confirme seu e-mail'
$confirmationToken = Read-ActionToken $confirmation '/cadastro'
$user = Post-Api '/auth/email-confirmations' @{ token = $confirmationToken }
if ($user.email -ne $email -or !$user.emailConfirmedAt) { throw 'Registration confirmation did not confirm the account' }
Post-Api '/auth/login' @{ email = $email; password = $oldPassword } $session | Out-Null

Post-Api '/auth/password-reset-requests' @{ email = $email } | Out-Null
$reset = Wait-Message $email 'Redefina sua senha'
$resetToken = Read-ActionToken $reset '/recuperar-senha'
Post-Api '/auth/password-resets' @{ token = $resetToken; password = $newPassword } | Out-Null
try {
    Invoke-RestMethod "$ApiBase/me" -WebSession $session -Headers $headers | Out-Null
    throw 'The prior session still works after password reset'
} catch {
    if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
Post-Api '/auth/login' @{ email = $email; password = $newPassword } $session | Out-Null

$teacher = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Post-Api '/auth/login' @{ email = 'professora@aja.local.test'; password = 'AjaDemo-2026!' } $teacher | Out-Null
$title = "INT04-$run"
$draft = @{ classId = 'demo-class'; subjectId = 'demo-subject'; title = "$title silencioso"; body = 'Aviso de teste.'; notify = $false; occurrence = $null }
Post-Api '/announcements' $draft $teacher "silent-$run" | Out-Null
$draft.title = $title
$draft.notify = $true
$announcement = Post-Api '/announcements' $draft $teacher "notify-$run"
$link = "$FrontendBase/avisos#aviso-$($announcement.id)"
foreach ($recipient in 'aluno@aja.local.test', 'bia@aja.local.test') {
    $message = Wait-Message $recipient $title
    if (!$message.Text.Contains($draft.body) -or !$message.Text.Contains($link)) {
        throw 'Announcement mail is missing the body or frontend link'
    }
    if (@(Get-Messages $recipient "$title silencioso").Count) { throw 'A silent announcement was sent' }
}
if (@(Get-Messages 'caio@aja.local.test' $title).Count) { throw 'Announcement reached a student from another class' }

Write-Output 'Verified registration confirmation, password reset and class announcement delivery in Mailpit.'
