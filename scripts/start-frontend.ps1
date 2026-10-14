$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
Push-Location "$projectRoot/client"
try {
    if (!(Test-Path node_modules)) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    }
    & npm.cmd run dev -- --host 127.0.0.1
} finally { Pop-Location }
