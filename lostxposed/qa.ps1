<#
    Lost Xposed: one-command QA.

    Nothing here needs a device reboot. The only thing that would is a system_server hook,
    and there is deliberately none until the boot safety guard exists. Everything else is a
    process restart measured in seconds:

        SystemUI        am crash com.android.systemui   (force-stop is a NO-OP, see below)
        a target app    am force-stop <pkg>
        the keyboard    am force-stop <ime-pkg>

    Usage:
        .\qa.ps1                       # build, install, run every check
        .\qa.ps1 -SkipBuild            # use the APK already built
        .\qa.ps1 -TargetApp com.foo    # app to prove per-app display on
        .\qa.ps1 -ImePackage com.bar   # keyboard to prove Text Engine on
#>

param(
    [switch]$SkipBuild,
    # system_server features only load at boot, so they cannot be checked in the same pass.
    # Run once normally, reboot once, then run with -PostReboot.
    [switch]$PostReboot,
    [string]$TargetApp = "com.android.settings",
    [string]$ImePackage = "",
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
)

$ErrorActionPreference = "Continue"
$script:results = @()

function Record($name, $ok, $detail) {
    $script:results += [pscustomobject]@{ Check = $name; Result = $(if ($ok) { "PASS" } else { "FAIL" }); Detail = $detail }
    $colour = if ($ok) { "Green" } else { "Red" }
    Write-Host ("  [{0}] {1}: {2}" -f $(if ($ok) { "PASS" } else { "FAIL" }), $name, $detail) -ForegroundColor $colour
}

function Sh($cmd) { & $Adb shell $cmd 2>&1 | Out-String }
function LogSince() { (& $Adb logcat -d -s LostXposed 2>&1) -join "`n" }

Write-Host "`n=== Lost Xposed QA ===`n" -ForegroundColor Cyan

# ---------------------------------------------------------------- device
$devices = (& $Adb devices) -join "`n"
if ($devices -notmatch "`tdevice") {
    Write-Host "No device. Plug it in, allow USB debugging, and re-run." -ForegroundColor Red
    exit 1
}
$model = (Sh "getprop ro.product.model").Trim()
$sdk = (Sh "getprop ro.build.version.sdk").Trim()
Write-Host "Device: $model (SDK $sdk)`n"

# ---------------------------------------------------------------- build + install
if (-not $SkipBuild) {
    Write-Host "Building..."
    $env:JAVA_HOME = $JavaHome
    Push-Location $PSScriptRoot
    # Tests first. They need no device and cover the logic that is painful to reach from
    # one -- fuzzy time phrasing, notification keyword matching, the boot guard's
    # fail-closed path, config key resolution and the snapshot format.
    $unit = & .\gradlew.bat test --console=plain 2>&1 | Out-String
    Pop-Location
    Record "unit tests" ($unit -match "BUILD SUCCESSFUL") $(
        if ($unit -match "BUILD SUCCESSFUL") { "all JVM tests pass" } else { "see build/reports/tests" }
    )
    if ($unit -notmatch "BUILD SUCCESSFUL") { exit 1 }
    Push-Location $PSScriptRoot
    $build = & .\gradlew.bat :app:assembleDebug --console=plain 2>&1 | Out-String
    Pop-Location
    Record "build" ($build -match "BUILD SUCCESSFUL") $(if ($build -match "BUILD SUCCESSFUL") { "debug APK" } else { "see gradle output" })
    if ($build -notmatch "BUILD SUCCESSFUL") { exit 1 }

    # Built but not installed. R8 renames aggressively and the framework finds the entry
    # class by name from a resource file, so a broken keep rule shows up as "module does
    # nothing" long after the fact. Installing it here would need an uninstall first, which
    # clears settings and drops the module from scope, so the build is the check.
    Push-Location $PSScriptRoot
    $rel = & .\gradlew.bat :app:assembleRelease --console=plain 2>&1 | Out-String
    Pop-Location
    $relApk = Join-Path $PSScriptRoot "app/build/outputs/apk/release/app-release.apk"
    Record "release build" (($rel -match "BUILD SUCCESSFUL") -and (Test-Path $relApk)) $(
        if (Test-Path $relApk) { "signed, minified" }
        elseif ($rel -match "BUILD SUCCESSFUL") { "UNSIGNED - no keystore.properties, see README" }
        else { "R8 or signing failed" }
    )
}

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$install = (& $Adb install -r $apk 2>&1) -join "`n"
Record "install" ($install -match "Success") $apk

# Broadcasts are dropped to stopped packages, so launch once to clear the flag.
& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Sh "am start -n dev.lostxposed/.MainActivity" | Out-Null
Start-Sleep -Seconds 3

# ---------------------------------------------------------------- self scope / config channel
& $Adb logcat -c
Sh "am broadcast -p dev.lostxposed -a dev.lostxposed.config.DUMP" | Out-Null
Start-Sleep -Seconds 2
$log = LogSince
# The preference mode says nothing about delivery: MODE_WORLD_READABLE is accepted on this
# device without being applied. What matters is that a snapshot was written, because that is
# the file the hooked processes fall back to reading.
$wrote = $log -match "snapshot (\d+) bytes"
Record "config written" $wrote $(
    if ($wrote) { "snapshot $($Matches[1]) bytes" } else { "no snapshot written, see logcat -s LostXposed" }
)

# ---------------------------------------------------------------- SystemUI injection
Write-Host "`nRestarting SystemUI (am crash, not force-stop)..."
& $Adb logcat -c
Sh "am crash com.android.systemui" | Out-Null
Start-Sleep -Seconds 14
$log = LogSince
Record "systemui injection" ($log -match "LostXposed in com.android.systemui") "engine ran in SystemUI"
Record "framework detected" ($log -match "api 102") $(
    if ($log -match "config: (.+?)\s*$") { "config: $($Matches[1])" } else { "see log" }
)
Record "noop installed" ($log -match "No-op reference\s+installed") "reference feature hooked"
# The open question this whole investigation turns on: does anything carry settings INTO a
# hooked app process? Reported rather than assumed, and a failure here is expected until a
# channel works -- it must not be quietly folded into "injection works".
$delivered = $log -match "channel=(?!none)(\S+)"
Record "config delivered to SystemUI" $delivered $(
    if ($delivered) { "channel=$($Matches[1])" }
    elseif ($log -match "(remote-prefs=\S+ remote-file=\S+ direct-file=\S+)") { "no channel: $($Matches[1])" }
    else { "no channel report in log" }
)

# ---------------------------------------------------------------- detach
& $Adb logcat -c
Sh "am broadcast -a dev.lostxposed.STATUS" | Out-Null
Start-Sleep -Seconds 2
$before = LogSince
Sh "am broadcast -a dev.lostxposed.DISABLE --es feature core.noop" | Out-Null
Start-Sleep -Seconds 2
$after = LogSince
Record "detach" ($after -match "removed 1 hook") "unhook() removed the live hook"

# ---------------------------------------------------------------- per-app display
Write-Host "`nPer-app display on $TargetApp..."
& $Adb logcat -c
Sh "am broadcast -p dev.lostxposed -a dev.lostxposed.config.SET --es package $TargetApp --ei dpi 400" | Out-Null
Start-Sleep -Seconds 2
Sh "am force-stop $TargetApp" | Out-Null
Sh "monkey -p $TargetApp -c android.intent.category.LAUNCHER 1" | Out-Null
Start-Sleep -Seconds 8
$log = LogSince
Record "display profile applied" ($log -match "applied .*400dpi") $(
    if ($log -match "applied (.+?)\s*$") { $Matches[1] } else { "no 'applied' line. Is $TargetApp in the module scope?" }
)
Sh "am broadcast -p dev.lostxposed -a dev.lostxposed.config.CLEAR --es package $TargetApp" | Out-Null
Sh "am force-stop $TargetApp" | Out-Null

# ---------------------------------------------------------------- text engine
if (-not $ImePackage) {
    $ImePackage = ((Sh "settings get secure default_input_method").Trim() -split "/")[0]
}
Write-Host "`nText Engine on $ImePackage..."
& $Adb logcat -c
Sh "am force-stop $ImePackage" | Out-Null
Start-Sleep -Seconds 6
Sh "am start -n dev.lostxposed/.MainActivity" | Out-Null
Start-Sleep -Seconds 6
$log = LogSince
Record "text engine hooked" ($log -match "gestures active") $(
    if ($log -match "gestures active on (.+?)\s*$") { $Matches[1] }
    else { "no 'gestures active'. Is $ImePackage in the module scope and a known IME?" }
)

# ---------------------------------------------------------------- smart status bar
Write-Host "`nSmart status bar (fuzzy clock)..."
Sh "am broadcast -p dev.lostxposed -a dev.lostxposed.config.SET --es feature core.smartstatusbar --es key style --es value fuzzy" | Out-Null
Start-Sleep -Seconds 2
& $Adb logcat -c
Sh "am crash com.android.systemui" | Out-Null
Start-Sleep -Seconds 14
$log = LogSince
Record "smart status bar" ($log -match "clock style=FUZZY") $(
    if ($log -match "clock style=(\S+)") { "style $($Matches[1]); check the status bar reads e.g. 'quarter past three'" }
    else { "no 'clock style' line" }
)

# ---------------------------------------------------------------- system_server features
Write-Host "`nsystem_server features..."
if ($PostReboot) {
    $log = (& $Adb logcat -d -s LostXposed 2>&1) -join "`n"
    Record "boot guard armed" ($log -match "boot guard armed|BOOT GUARD TRIPPED") $(
        if ($log -match "BOOT GUARD TRIPPED: (.+)") { "TRIPPED: $($Matches[1])" }
        elseif ($log -match "boot guard armed for (\d+)") { "armed for $($Matches[1]) feature(s)" }
        else { "no guard line" }
    )
    Record "power inspector" ($log -match "power inspector watching") "hooked wakelock/alarm entry points"
    Record "notification rules" ($log -match "notification rules watching") "hooked enqueue entry points"

    Sh "am broadcast -a dev.lostxposed.POWER" | Out-Null
    Start-Sleep -Seconds 2
    $power = (& $Adb logcat -d -s LostXposed 2>&1) -join "`n"
    Record "power ledger" ($power -match "wakelocks") "ledger reported"
} else {
    Write-Host "  skipped: these inject at boot only." -ForegroundColor Yellow
    Write-Host "  Reboot once, then: .\qa.ps1 -SkipBuild -PostReboot" -ForegroundColor Yellow
    Write-Host "  That single reboot also exercises the boot guard, which cannot be" -ForegroundColor Yellow
    Write-Host "  tested any other way and exists to stop you needing many more." -ForegroundColor Yellow
}

# ---------------------------------------------------------------- summary
Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
$script:results | Format-Table -AutoSize
$failed = @($script:results | Where-Object { $_.Result -eq "FAIL" }).Count
Write-Host ("{0} of {1} checks passed.`n" -f ($script:results.Count - $failed), $script:results.Count) `
    -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Yellow" })
exit $(if ($failed -eq 0) { 0 } else { 1 })
