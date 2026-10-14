# Load the project's dotenv file without evaluating shell expressions.
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (!(Test-Path $envFile)) { throw "Missing $envFile; copy .env.example first." }
foreach ($line in Get-Content $envFile) {
    $text = $line.Trim()
    if (!$text -or $text.StartsWith('#')) { continue }
    if ($text -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"')))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if ($env:FFMPEG_DIR) { $env:PATH = "$env:FFMPEG_DIR;$env:PATH" }
