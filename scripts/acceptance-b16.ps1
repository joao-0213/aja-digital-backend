$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'aja-toolchain\jdk-21.0.13+9'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$env:PATH = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
$expectedOperations = 98

if (-not (Test-Path $docker) -or -not (Test-Path $java)) { throw 'Configured Docker Desktop or JDK 21 executable is missing.' }
& $docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker daemon is unavailable.' }

$openApiPath = Join-Path $repo 'docs/http/openapi.json'
$openApi = Get-Content -LiteralPath $openApiPath -Raw | ConvertFrom-Json
if ($openApi.openapi -ne '3.1.0' -or $openApi.info.version -ne '1.0.8') { throw 'The public OpenAPI version changed.' }

$httpMethods = @('get', 'post', 'patch', 'put', 'delete', 'options', 'head', 'trace')
$operations = @()
foreach ($path in $openApi.paths.PSObject.Properties) {
    foreach ($entry in $path.Value.PSObject.Properties) {
        if ($entry.Name -notin $httpMethods) { continue }
        if ([string]::IsNullOrWhiteSpace($entry.Value.operationId)) { throw "Missing operationId: $($entry.Name.ToUpperInvariant()) $($path.Name)" }
        if ($null -eq $entry.Value.responses -or $entry.Value.responses.PSObject.Properties.Count -eq 0) { throw "Missing responses: $($entry.Value.operationId)" }
        $operations += [pscustomobject]@{ Method = $entry.Name.ToUpperInvariant(); Path = $path.Name; OperationId = $entry.Value.operationId }
    }
}
if ($operations.Count -ne $expectedOperations) { throw "Expected $expectedOperations OpenAPI operations, found $($operations.Count)." }
$duplicateIds = $operations | Group-Object OperationId | Where-Object Count -gt 1
if ($duplicateIds) { throw "Duplicate operationId values: $(($duplicateIds.Name) -join ', ')" }

function Get-OpenApiReferences($Value) {
    if ($null -eq $Value -or $Value -is [string]) { return }
    if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [pscustomobject]) {
        foreach ($item in $Value) { Get-OpenApiReferences $item }
        return
    }
    foreach ($property in $Value.PSObject.Properties) {
        if ($property.Name -eq '$ref') { $property.Value }
        Get-OpenApiReferences $property.Value
    }
}

function Resolve-OpenApiReference([string] $Reference, $Document) {
    if (-not $Reference.StartsWith('#/')) { return $false }
    $current = $Document
    foreach ($part in $Reference.Substring(2).Split('/')) {
        $name = $part.Replace('~1', '/').Replace('~0', '~')
        if ($current -is [System.Array]) {
            $index = 0
            if (-not [int]::TryParse($name, [ref]$index) -or $index -ge $current.Length) { return $false }
            $current = $current[$index]
        } else {
            $property = $current.PSObject.Properties[$name]
            if ($null -eq $property) { return $false }
            $current = $property.Value
        }
    }
    return $true
}

$references = @(Get-OpenApiReferences $openApi)
$unresolved = @($references | Where-Object { -not (Resolve-OpenApiReference $_ $openApi) })
if ($unresolved.Count -gt 0) { throw "Unresolved OpenAPI references: $($unresolved -join ', ')" }
Write-Output "PASS: OpenAPI 3.1.0 v$($openApi.info.version), $($operations.Count)/$expectedOperations path+method operations, unique operationIds, $($references.Count) resolved references."

$priorCoverageSetting = [Environment]::GetEnvironmentVariable('B16_OPENAPI_COVERAGE', 'Process')
$env:B16_OPENAPI_COVERAGE = 'true'
try {
    foreach ($service in @('core', 'questions', 'email')) {
        $servicePath = Join-Path $repo "services/$service"
        Push-Location $servicePath
        try {
            Write-Output "RUN: services/$service/gradlew.bat test build --rerun-tasks --no-daemon (B16_OPENAPI_COVERAGE=true)"
            & .\gradlew.bat test build --rerun-tasks --no-daemon
            if ($LASTEXITCODE -ne 0) { throw "services/$service test/build failed." }
        } finally { Pop-Location }
    }
} finally {
    if ($null -eq $priorCoverageSetting) { Remove-Item Env:B16_OPENAPI_COVERAGE -ErrorAction SilentlyContinue }
    else { $env:B16_OPENAPI_COVERAGE = $priorCoverageSetting }
}

foreach ($acceptance in @('acceptance-b03-email-process-restart.ps1', 'acceptance-b08-assessment-snapshots.ps1')) {
    $acceptancePath = Join-Path $PSScriptRoot $acceptance
    Write-Output "RUN: scripts/$acceptance"
    & (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') -NoProfile -ExecutionPolicy Bypass -File $acceptancePath
    if ($LASTEXITCODE -ne 0) { throw "scripts/$acceptance failed." }
}

Write-Output "PASS: B-16 aggregate gates completed. Core's 95 and Questions' 3 OpenAPI operations each have a real 2xx request plus a declared route-specific denial probe (Core: 401 for session-protected routes, 422 for malformed public auth requests, 403 for denied-Origin health; cookie-optional logout: 204; Questions: 401). Email worker and Core-Questions process behavior were exercised by the service suites and B03/B08 process acceptances."
