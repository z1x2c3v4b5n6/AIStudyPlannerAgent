[CmdletBinding()]
param([ValidateSet('Doctor','Start','Smoke','E2E')][string]$Mode='Doctor',[string]$MySql='mysql')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Push-Location $root
function Run([string]$Command,[string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command exited with code $LASTEXITCODE." }
}
function Sql([string]$Statement,[string]$Schema='') {
    $mysqlArgs=@('--protocol=TCP','--connect-timeout=4','--batch','--skip-column-names',"-h$dbHost","-P$dbPort","-u$dbUser")
    if ($Schema) { $mysqlArgs += $Schema }
    $saved=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=$env:DB_PASSWORD
        $rows=$Statement | & $MySql @mysqlArgs
        if ($LASTEXITCODE -ne 0) { throw 'MySQL check failed. Verify DB_URL, DB_USERNAME and DB_PASSWORD.' }
        return $rows
    } finally { $env:MYSQL_PWD=$saved }
}
function Assert-Vacant([int[]]$Ports) {
    foreach ($port in $Ports) {
        $listener=New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::IPv6Any),$port
        try {
            $listener.Server.DualMode=$true
            $listener.Server.ExclusiveAddressUse=$true
            $listener.Start()
        } catch { throw "Port $port already in use or cannot be bound. Stop the existing service first." }
        finally { $listener.Stop() }
    }
}
function Stop-OwnedProcess($Process) {
    if ($Process -and !$Process.HasExited) {
        Stop-Process -Id $Process.Id -ErrorAction SilentlyContinue
        if (!$Process.WaitForExit(10000)) { throw "An owned child process did not stop." }
    }
}
function Wait-Http([string]$Url,[int]$Expected,$Process) {
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        if ($Process -and $Process.HasExited) { throw "The service exited before $Url was ready. See logs." }
        try {
            $response=Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq $Expected) { return }
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Expected) { return }
        }
        Start-Sleep -Seconds 1
    }
    throw "HTTP check failed for $Url. Check local logs and port conflicts."
}
$createdSchema=''
$backend=$null
$frontend=$null
$savedUrl=$env:DB_URL
$savedFault=$env:C12_VERIFY_DB_FAULT
$savedLiveC8=$env:C8_LIVE_DEEPSEEK
$savedLiveC11=$env:C11_LIVE_DEEPSEEK
try {
    $javaVersion=(& java --version | Out-String)
    if ($javaVersion -notmatch '(?im)^(?:openjdk|java)\s+(\d+)' -or [int]$Matches[1] -lt 17) { throw 'Set JAVA_HOME and PATH to JDK 17 or later.' }
    $mavenVersion=(& mvn.cmd --version | Out-String)
    if ($mavenVersion -notmatch 'Apache Maven (\d+)\.(\d+)' -or [int]$Matches[1] -lt 3 -or
        ([int]$Matches[1] -eq 3 -and [int]$Matches[2] -lt 8)) { throw 'Install Maven 3.8 or later.' }
    $nodeVersion=& node.exe --version
    if ($nodeVersion -notmatch '^v(\d+)\.(\d+)' -or [int]$Matches[1] -lt 20 -or [int]$Matches[1] -eq 21 -or
        ([int]$Matches[1] -eq 20 -and [int]$Matches[2] -lt 19) -or
        ([int]$Matches[1] -eq 22 -and [int]$Matches[2] -lt 12)) { throw 'Install Node 20.19+ or 22.12+ LTS.' }
    $url=$env:DB_URL
    if (!$url) { $url='jdbc:mysql://localhost:3306/ai_study_planner' }
    if ($url -notmatch '^jdbc:mysql://([a-zA-Z0-9.-]+)(?::([0-9]+))?/([a-zA-Z0-9_]+)(?:\?|$)') {
        throw 'Use a single-host MySQL JDBC URL with a schema name.'
    }
    $dbHost=$Matches[1]
    $dbPort=if ($Matches[2]) {$Matches[2]} else {'3306'}
    $dbSchema=$Matches[3]
    $dbUser=if ($env:DB_USERNAME) {$env:DB_USERNAME} else {'root'}
    Sql 'SELECT 1;' | Out-Null
    Run 'node.exe' @('scripts/check-redis.mjs')
    $ai=($env:AI_ENABLED -eq 'true' -and -not [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY))
    Write-Host "Java/Maven/Node and MySQL PASS. DeepSeek configured=$ai, official-online configured=$($env:APP_KNOWLEDGE_ONLINE_ENABLED -eq 'true')"
    if ($Mode -eq 'Doctor') {
        $known=Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$dbSchema' AND table_name='flyway_schema_history';"
        if ($known -eq '1') { Sql 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC;' $dbSchema }
        else { Write-Host 'Flyway history not found. Start the backend to apply migrations.' }
        return
    }
    if ($Mode -eq 'Start') { Assert-Vacant @(8080,5173) }
    if ($Mode -eq 'E2E') { Assert-Vacant @(8080,5173,18081) }
    if ($Mode -in @('Start','E2E')) {
        Run 'mvn.cmd' @('-B','-f','backend/pom.xml','package','-DskipTests')
        Run 'npm.cmd' @('--prefix','frontend','ci')
        Run 'npm.cmd' @('--prefix','frontend','run','build')
    }
    if ($Mode -eq 'E2E') {
        if ($dbHost -notin @('localhost','127.0.0.1') -or (Test-Path 'application-local.yml')) {
            throw 'E2E requires local MySQL and no root application-local.yml override.'
        }
        $name='c12_' + [Guid]::NewGuid().ToString('N') + '_test'
        Sql "CREATE DATABASE $name;" | Out-Null
        $createdSchema=$name
        $env:DB_URL="jdbc:mysql://${dbHost}:${dbPort}/$name" + '?serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
        Run 'npm.cmd' @('--prefix','frontend','install','--no-save','--package-lock=false','playwright@1.58.2')
        Run 'node.exe' @('frontend/node_modules/playwright/cli.js','install','chromium')
        $env:C12_VERIFY_DB_FAULT='true'
        if ($env:C12_LIVE_DEEPSEEK -eq 'true') {
            Run 'node.exe' @('scripts/run-live-c12.mjs')
        } else {
            $env:C8_LIVE_DEEPSEEK=$null
            $env:C11_LIVE_DEEPSEEK=$null
            Run 'node.exe' @('tests/c8-e2e.mjs')
        }
        return
    }
    if ($Mode -eq 'Start') {
        if (Test-Path 'application-local.yml') { throw 'Export environment variables before using this launcher instead of root application-local.yml overrides.' }
        $logs=Join-Path $root 'backend/target/local-c12'
        New-Item -ItemType Directory -Path $logs -Force | Out-Null
        $options=@{NoNewWindow=$true;PassThru=$true;RedirectStandardOutput="$logs/backend.log";RedirectStandardError="$logs/backend-error.log"}
        $backend=Start-Process java -ArgumentList @('-jar','backend/target/ai-study-planner-agent-0.0.1-SNAPSHOT.jar','--server.port=8080') @options
        $options.RedirectStandardOutput="$logs/frontend.log"
        $options.RedirectStandardError="$logs/frontend-error.log"
        $frontend=Start-Process node.exe -WorkingDirectory (Join-Path $root 'frontend') -ArgumentList @('node_modules/vite/bin/vite.js','preview','--port','5173','--strictPort') @options
        Write-Host "Local logs are in $logs"
    }
    Wait-Http 'http://localhost:8080/api/v1/auth/me' 401 $backend
    Wait-Http 'http://localhost:5173' 200 $frontend
    $migrations=Sql 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC;' $dbSchema
    if (!$migrations -or ($migrations -match '\s0$')) { throw 'Flyway migration history is missing or reports failure.' }
    $migrations
    Write-Host 'Backend auth gate and frontend HTTP PASS. Open http://localhost:5173 to sign in.'
    if ($Mode -eq 'Start') {
        while (!$backend.HasExited -and !$frontend.HasExited) { Start-Sleep -Seconds 2 }
        throw 'A local service stopped. Inspect its log.'
    }
} finally {
    try {
        Stop-OwnedProcess $frontend
    } finally {
        try {
            Stop-OwnedProcess $backend
        } finally {
            try {
                if ($createdSchema -match '^c12_[0-9a-f]{32}_test$') {
                    try { Sql "DROP DATABASE $createdSchema;" | Out-Null }
                    catch { Write-Warning "Could not remove this run's isolated schema: $createdSchema. Verify the local service and clean up that schema."; throw }
                }
            } finally {
                $env:DB_URL=$savedUrl
                $env:C12_VERIFY_DB_FAULT=$savedFault
                $env:C8_LIVE_DEEPSEEK=$savedLiveC8
                $env:C11_LIVE_DEEPSEEK=$savedLiveC11
                Pop-Location
            }
        }
    }
}
