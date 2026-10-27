param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = 'Stop'
$GradleVersion = '8.8'
$CacheRoot = Join-Path $env:LOCALAPPDATA 'CivitasBuildTools'
$GradleHome = Join-Path $CacheRoot "gradle-$GradleVersion"
$GradleExecutable = Join-Path $GradleHome 'bin\gradle.bat'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host 'Java was not found. Install a 64-bit JDK 21 and add java to PATH.' -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $GradleExecutable)) {
    New-Item -ItemType Directory -Path $CacheRoot -Force | Out-Null
    $Archive = Join-Path $CacheRoot "gradle-$GradleVersion-bin.zip"
    $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Downloading Gradle $GradleVersion (first run only)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $Url -OutFile $Archive
    Expand-Archive -Path $Archive -DestinationPath $CacheRoot -Force
    Remove-Item $Archive -Force
}

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @('build')
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    Write-Host "Running Gradle: $($GradleArgs -join ' ')" -ForegroundColor Cyan
    & $GradleExecutable @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
