$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
$failed = $false
foreach ($entry in @(
    @{ Name = 'Frontend'; Url = 'http://127.0.0.1:5173/' },
    @{ Name = 'Backend'; Url = "http://127.0.0.1:$env:SERVER_PORT/health" },
    @{ Name = 'Frontend API proxy'; Url = 'http://127.0.0.1:5173/health' },
    @{ Name = 'MinIO'; Url = "$env:MINIO_ENDPOINT/minio/health/live" }
)) {
    try {
        $response = Invoke-WebRequest $entry.Url -TimeoutSec 10
        if ($entry.Name -in @('Backend', 'Frontend API proxy')) {
            $body = $response.Content | ConvertFrom-Json
            if ($body.code -ne 0 -or $body.data -ne 'UP') { throw 'Unexpected health response' }
        }
        Write-Host "$($entry.Name): OK (HTTP $($response.StatusCode))"
    } catch { $failed = $true; Write-Warning "$($entry.Name): FAILED ($($_.Exception.Message))" }
}
foreach ($port in @(3307, [int]$env:REDIS_PORT, 9876, 10911)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        Write-Host "TCP ${port}: listening"
    } else { $failed = $true; Write-Warning "TCP ${port}: not listening" }
}
if ($failed) { exit 1 }
