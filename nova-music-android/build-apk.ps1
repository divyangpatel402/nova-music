$ErrorActionPreference = "Stop"

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Error "Gradle is not installed or not on PATH. Open nova-music-android in Android Studio first, or install Gradle/Android SDK."
}

gradle :app:assembleDebug

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host "APK ready: $apk"
} else {
    Write-Error "Build finished but APK was not found at $apk"
}
