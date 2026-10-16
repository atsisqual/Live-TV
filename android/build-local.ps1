param(
    [string]$ToolsRoot = "$PSScriptRoot\.tools"
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$GradleVersion = '8.7'
$AndroidPlatform = '35'
$BuildToolsVersion = '35.0.0'
$CommandLineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

function Ensure-Directory([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
}

function Download-IfMissing([string]$Url, [string]$Destination) {
    if (-not (Test-Path $Destination)) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    }
}

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    throw 'Java is not installed. Install JDK 17 or newer and run this script again.'
}

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
Write-Host "Using $javaVersion"

Ensure-Directory $ToolsRoot

# Gradle
$gradleZip = Join-Path $ToolsRoot "gradle-$GradleVersion-bin.zip"
$gradleHome = Join-Path $ToolsRoot "gradle-$GradleVersion"
Download-IfMissing $GradleUrl $gradleZip
if (-not (Test-Path (Join-Path $gradleHome 'bin\gradle.bat'))) {
    Write-Host "Extracting Gradle $GradleVersion"
    Expand-Archive -Path $gradleZip -DestinationPath $ToolsRoot -Force
}
$gradle = Join-Path $gradleHome 'bin\gradle.bat'

# Android command-line tools
$sdkRoot = Join-Path $ToolsRoot 'android-sdk'
$cmdlineZip = Join-Path $ToolsRoot 'commandlinetools-win.zip'
$latestRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
$sdkManager = Join-Path $latestRoot 'bin\sdkmanager.bat'

if (-not (Test-Path $sdkManager)) {
    Download-IfMissing $CommandLineToolsUrl $cmdlineZip
    $extractRoot = Join-Path $ToolsRoot 'cmdline-extract'
    if (Test-Path $extractRoot) { Remove-Item $extractRoot -Recurse -Force }
    Ensure-Directory $extractRoot
    Expand-Archive -Path $cmdlineZip -DestinationPath $extractRoot -Force
    Ensure-Directory (Split-Path $latestRoot -Parent)
    if (Test-Path $latestRoot) { Remove-Item $latestRoot -Recurse -Force }
    Move-Item (Join-Path $extractRoot 'cmdline-tools') $latestRoot
    Remove-Item $extractRoot -Recurse -Force
}

$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

Write-Host 'Accepting Android SDK licences...'
1..20 | ForEach-Object { 'y' } | & $sdkManager --licenses | Out-Host

Write-Host "Installing Android platform $AndroidPlatform and build-tools $BuildToolsVersion..."
& $sdkManager "platforms;android-$AndroidPlatform" "build-tools;$BuildToolsVersion" 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }

Write-Host 'Building Live TV debug APK...'
Push-Location $PSScriptRoot
try {
    & $gradle ':app:assembleDebug' '--stacktrace'
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "Build completed but APK was not found at $apk" }

$hash = (Get-FileHash -Algorithm SHA256 -Path $apk).Hash
Write-Host ''
Write-Host 'BUILD OK'
Write-Host "APK: $apk"
Write-Host "SHA-256: $hash"
