$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
Push-Location "$projectRoot/server"
try {
    & ./mvnw.cmd -s .mvn/central-settings.xml spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
