#Requires -Version 5.1
<#
.SYNOPSIS
    Build the iLayoutSettings LSPosed module without Gradle.

.DESCRIPTION
    Pipeline: aapt2 compile/link -> javac -> d8 -> inject classes.dex
              -> zipalign -> apksigner.

    Required inputs (parameter or environment variable, parameter wins):
        -Jdk          / JAVA_HOME            JDK 17 (javac, jar, keytool)
        -BuildTools   / ANDROID_BUILD_TOOLS  Android build-tools (aapt2, d8, zipalign, apksigner)
        -AndroidJar   / ANDROID_JAR          platform android.jar (API 36)
        -ApiJar       / XPOSED_API_JAR       Xposed API 82 jar (defaults to lib/xposed-api-82.jar)

    Nothing is downloaded automatically: point the parameters at an existing SDK.
    See docs/03-build.md for the exact versions this project was built with.

.PARAMETER OutDir
    Output directory. Defaults to <repo>/dist.

.PARAMETER KeyStore
    Signing keystore. Defaults to <repo>/.keystore/testkey.jks and is generated on demand.
    Pass your own keystore for a release build: the shipped APK must be signed with
    the same key as the previous release, otherwise Android refuses to upgrade.

.PARAMETER KeepOldVersions
    Keep previous APKs in the output directory. By default a successful build prunes
    every other *.apk / *.idsig there, so dist/ only contains the artifact just built
    (old versions stay on GitHub Releases).

.EXAMPLE
    pwsh -File module/build.ps1 -Jdk C:\jdk17 -BuildTools C:\android-sdk\build-tools\34.0.0 `
         -AndroidJar C:\android-sdk\platforms\android-36\android.jar
#>
[CmdletBinding()]
param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$BuildTools = $env:ANDROID_BUILD_TOOLS,
    [string]$AndroidJar = $env:ANDROID_JAR,
    [string]$ApiJar = $env:XPOSED_API_JAR,
    [string]$OutDir,
    [string]$KeyStore,
    [string]$KeyAlias = 'iossettings',
    [string]$KeyPass = 'android',
    [switch]$KeepOldVersions
)

# Native tools (javac/aapt2/d8) write notes to stderr, so keep the preference
# non-terminating and check $LASTEXITCODE explicitly after every call.
$ErrorActionPreference = 'Continue'

$here = $PSScriptRoot
$root = Split-Path -Parent $here
$build = Join-Path $root 'build'
if (-not $OutDir) { $OutDir = Join-Path $root 'dist' }
# The keystore must NOT live under build/: build/ is wiped on every run, which
# used to regenerate the key and change the APK signature each build.
if (-not $KeyStore) { $KeyStore = Join-Path $root '.keystore\testkey.jks' }

function Find-First {
    param([string[]]$Candidates, [string]$Label)
    foreach ($c in $Candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) { return $c }
    }
    throw "$Label not found. Tried:`n  " + (($Candidates | Where-Object { $_ }) -join "`n  ") +
          "`nSet the matching parameter or environment variable (see docs/03-build.md)."
}

function Get-Tool {
    param([string]$Dir, [string]$Name)
    $exe = Join-Path $Dir $Name
    if (-not (Test-Path -LiteralPath $exe)) { throw "$Name not found in $Dir" }
    return $exe
}

$jdk = Find-First @(
    $Jdk,
    (Join-Path $root 'tools\jdk'),
    (Join-Path $root 'tools\jdk-17'),
    'C:\Users\AbradantHawk\Documents\deepseek-harness\default-workspace\apk6ify\.downloads\jdk\jdk17.0.21_0',
    "$env:USERPROFILE\apktool_work\jdk-17.0.20.1+1"
) 'JDK (JAVA_HOME)'

$bt = Find-First @(
    $BuildTools,
    (Join-Path $root 'tools\build-tools'),
    'C:\Users\AbradantHawk\Documents\deepseek-harness\default-workspace\apk6ify\.downloads\bt\android-14'
) 'Android build-tools (ANDROID_BUILD_TOOLS)'

$androidJar = Find-First @(
    $AndroidJar,
    (Join-Path $root '.tools\platform36\android-36.1\android.jar'),
    (Join-Path $root '.tools\android.jar')
) 'android.jar (ANDROID_JAR)'

$apiJar = Find-First @(
    $ApiJar,
    (Join-Path $root 'lib\xposed-api-82.jar')
) 'Xposed API jar (XPOSED_API_JAR)'

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

$aapt2 = Get-Tool $bt 'aapt2.exe'
$d8 = Get-Tool $bt 'd8.bat'
$zipalign = Get-Tool $bt 'zipalign.exe'
$apksigner = Get-Tool $bt 'apksigner.bat'
$javac = Get-Tool (Join-Path $jdk 'bin') 'javac.exe'
$jarTool = Get-Tool (Join-Path $jdk 'bin') 'jar.exe'
$keytool = Get-Tool (Join-Path $jdk 'bin') 'keytool.exe'

# Version comes from the manifest so the artifact name can never drift from the APK.
$manifest = Join-Path $here 'AndroidManifest.xml'
$manifestXml = [xml](Get-Content -LiteralPath $manifest -Raw -Encoding UTF8)
$versionName = $manifestXml.manifest.versionName
$versionCode = $manifestXml.manifest.versionCode
if (-not $versionName) { throw 'android:versionName missing in AndroidManifest.xml' }

$out = Join-Path $OutDir "iLayoutSettings-$versionName.apk"

Write-Host "jdk        : $jdk"
Write-Host "buildtools : $bt"
Write-Host "android    : $androidJar"
Write-Host "xposed api : $apiJar"
Write-Host "version    : $versionName ($versionCode)"

if (Test-Path -LiteralPath $build) { Remove-Item -Recurse -Force -LiteralPath $build }
New-Item -ItemType Directory -Force -Path $build, (Join-Path $build 'classes'), (Join-Path $build 'dex'), (Join-Path $build 'gen'), $OutDir | Out-Null

Write-Host '[1/6] aapt2 compile + link'
& $aapt2 compile --dir (Join-Path $here 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

& $aapt2 link -o (Join-Path $build 'base.apk') -I $androidJar `
    --manifest $manifest `
    --min-sdk-version 29 --target-sdk-version 36 `
    --java (Join-Path $build 'gen') -A (Join-Path $here 'assets') (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[2/6] javac'
$sources = Get-ChildItem -Recurse -Filter '*.java' (Join-Path $here 'src') | ForEach-Object { $_.FullName }
if (-not $sources) { throw 'no Java sources found under module/src' }
$sourceList = Join-Path $build 'sources.txt'
[System.IO.File]::WriteAllLines($sourceList, $sources, (New-Object System.Text.UTF8Encoding($false)))
& $javac -source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:none `
    -bootclasspath $androidJar -classpath $apiJar -d (Join-Path $build 'classes') "@$sourceList" `
    2> (Join-Path $build 'javac.log')
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath (Join-Path $build 'javac.log') | Write-Host
    throw 'javac failed'
}

Write-Host '[3/6] d8'
Push-Location (Join-Path $build 'classes')
try { & $jarTool cf (Join-Path $build 'classes.jar') . } finally { Pop-Location }
& $d8 --min-api 29 --lib $androidJar --output (Join-Path $build 'dex') (Join-Path $build 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[4/6] package'
Copy-Item (Join-Path $build 'base.apk') (Join-Path $build 'stage.apk') -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path $build 'stage.apk'), [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, (Join-Path $build 'dex\classes.dex'), 'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally {
    $zip.Dispose()
}

& $zipalign -p -f 4 (Join-Path $build 'stage.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[5/6] sign'
if (-not (Test-Path -LiteralPath $KeyStore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $KeyStore) | Out-Null
    Write-Host "  generating test keystore: $KeyStore"
    & $keytool -genkeypair -keystore $KeyStore -alias $KeyAlias -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass $KeyPass -keypass $KeyPass `
        -dname 'CN=iOS Settings Layout, O=abradant, C=CN' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& $apksigner sign --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass "pass:$KeyPass" --key-pass "pass:$KeyPass" `
    --min-sdk-version 29 --max-sdk-version 36 `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $out (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '[6/6] verify'
& $apksigner verify --min-sdk-version 29 --print-certs $out | Select-Object -First 4
& $aapt2 dump badging $out | Select-String -Pattern '^package:|^sdkVersion|^targetSdkVersion|^launchable-activity' | Select-Object -First 5

$zipr = [System.IO.Compression.ZipFile]::OpenRead($out)
try {
    $names = @($zipr.Entries | ForEach-Object { $_.FullName })
    $entry = $zipr.GetEntry('classes.dex')
    if (-not $entry) { throw 'classes.dex missing from APK' }
    $ms = New-Object System.IO.MemoryStream
    $stream = $entry.Open()
    $stream.CopyTo($ms)
    $stream.Dispose()
    $bytes = $ms.ToArray()
    $latin = [System.Text.Encoding]::GetEncoding('ISO-8859-1').GetString($bytes)
    Write-Host ("dex version : {0}{1}{2}" -f [char]$bytes[4], [char]$bytes[5], [char]$bytes[6])
    Write-Host ("xposed_init : " + ($names -contains 'assets/xposed_init'))
    Write-Host ("module class: " + $latin.Contains('Lcom/Remo/ilayoutsettings/Module;'))
    Write-Host ("reorder     : " + $latin.Contains('Lcom/Remo/ilayoutsettings/HeaderData;'))
} finally {
    $zipr.Dispose()
}

$hash = (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash

# dist/ is the download folder, not an archive: only the APK just built stays.
# History lives in git tags / GitHub Releases, so a stale local copy only makes
# "which file do I install?" ambiguous. Opt out with -KeepOldVersions.
if (-not $KeepOldVersions) {
    $current = @($out, "$out.idsig") | ForEach-Object { [System.IO.Path]::GetFullPath($_) }
    $stale = Get-ChildItem -LiteralPath $OutDir -File | Where-Object {
        ($_.Name -like '*.apk' -or $_.Name -like '*.idsig') -and
        ($current -notcontains [System.IO.Path]::GetFullPath($_.FullName))
    }
    if ($stale) {
        Write-Host ''
        Write-Host ("[prune] removing " + $stale.Count + " old artifact(s) from " + $OutDir)
        $stale | ForEach-Object {
            Write-Host ("        - " + $_.Name)
            Remove-Item -LiteralPath $_.FullName -Force
        }
    }
}

Write-Host ''
Write-Host "OK -> $out"
Write-Host ("size   : " + (Get-Item -LiteralPath $out).Length + " bytes")
Write-Host ("sha256 : " + $hash)
exit 0
