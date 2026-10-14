# Start the native Windows services configured in .env.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
$localDir = Join-Path $projectRoot '.local'
$logs = Join-Path $localDir 'logs'
New-Item -ItemType Directory -Force $logs | Out-Null
function Start-LocalService {
    param([string]$Name, [int]$Port, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory)
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        Write-Host "$Name port $Port already listening; skipping."
        return
    }
    if (!(Test-Path $FilePath)) { throw "$Name executable missing: $FilePath" }
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -PassThru -WindowStyle Hidden -RedirectStandardOutput "$logs/$Name.log" -RedirectStandardError "$logs/$Name.err.log"
    $process.Id | Set-Content "$localDir/$Name.pid"
    Write-Host "$Name started (PID $($process.Id))."
}
if (!$env:LOCAL_MYSQL_HOME -or !$env:LOCAL_ROCKETMQ_HOME -or !$env:LOCAL_MINIO_EXE) {
    throw 'Set LOCAL_MYSQL_HOME, LOCAL_ROCKETMQ_HOME, LOCAL_MINIO_EXE in .env before starting native services.'
}
Start-LocalService 'mysql' 3307 "$env:LOCAL_MYSQL_HOME/bin/mysqld.exe" @("--defaults-file=`"$localDir/mysql.ini`"") $localDir
if (!(Get-NetTCPConnection -State Listen -LocalPort ([int]$env:REDIS_PORT) -ErrorAction SilentlyContinue)) {
    Start-Service Memurai
}
Write-Host "Redis: using existing Memurai service on port $env:REDIS_PORT, database $env:REDIS_DATABASE."
$env:MINIO_ROOT_USER = $env:MINIO_ACCESS_KEY
$env:MINIO_ROOT_PASSWORD = $env:MINIO_SECRET_KEY
Start-LocalService 'minio' 9000 $env:LOCAL_MINIO_EXE @('server', "`"$localDir/minio-data`"", '--address', '127.0.0.1:9000', '--console-address', '127.0.0.1:9001') $localDir
$env:ROCKETMQ_HOME = $env:LOCAL_ROCKETMQ_HOME
$mqLib = "$env:LOCAL_ROCKETMQ_HOME/lib/*"
$java = "$env:JAVA_HOME/bin/java.exe"
$common = @('-Xms128m', '-Xmx256m', '-XX:MaxDirectMemorySize=128m', '--add-opens=java.base/java.lang=ALL-UNNAMED', '--add-opens=java.base/java.nio=ALL-UNNAMED', '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED', '-cp', "`"$mqLib`"")
Start-LocalService 'namesrv' 9876 $java ($common + @('org.apache.rocketmq.namesrv.NamesrvStartup')) $localDir
$deadline = (Get-Date).AddSeconds(30)
while (!(Get-NetTCPConnection -State Listen -LocalPort 9876 -ErrorAction SilentlyContinue)) {
    if ((Get-Date) -gt $deadline) { throw "NameServer did not start; see $logs/namesrv.err.log" }
    Start-Sleep -Milliseconds 500
}
Start-LocalService 'broker' 10911 $java ($common + @('org.apache.rocketmq.broker.BrokerStartup', '-n', '127.0.0.1:9876', '-c', "`"$localDir/broker.conf`"")) $localDir
Write-Host "Service logs: $logs"
