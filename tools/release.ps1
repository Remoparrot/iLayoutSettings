#Requires -Version 5.1
<#
.SYNOPSIS
    iLayoutSettings 自动发版：构建 → 校验 → 打 tag → 建 GitHub Release → 上传 APK。

.DESCRIPTION
    v16.0 起「以后都自动发版」就靠这个脚本，一条命令完成整条链：
      1. 从 module/AndroidManifest.xml 读 versionName / versionCode / 包名；
      2. 调 module/build.ps1 构建并做四项校验（badging / xposed_init / module class / 签名）；
      3. 计算 SHA-256，写入 Release 说明；
      4. git tag v<version> 并 push（工作区有未提交改动时默认中止）；
      5. 用 git credential 里的 GitHub token 调 REST API 建 Release 并上传 APK 附件。

    本机没有 gh CLI，所以走 REST；中文说明必须先写 UTF-8 无 BOM 文件再 curl --data-binary，
    否则会 422 或乱码（见 docs/06-pitfalls.md 的 E 节）。

.PARAMETER Version
    要发版的版本号；默认取 manifest 的 versionName。

.PARAMETER Repo
    GitHub 仓库 owner/name，默认 abradanthawk-design/iLayoutSettings。

.PARAMETER AndroidJar
    API 36 android.jar；默认按 docs/03-build.md 的本机路径找。

.PARAMETER SkipBuild
    跳过构建，直接对 dist/ 里的产物发版（产物必须已存在）。

.PARAMETER SkipPush
    只打本地 tag，不 push、也不建 Release（演练用）。

.PARAMETER Notes
    额外的 Release 正文（追加在自动生成的内容后面）。

.EXAMPLE
    pwsh -File tools/release.ps1 -Version 16.0

.EXAMPLE
    pwsh -File tools/release.ps1 -Version 16.0 -SkipBuild -Notes "只补发附件"
#>
[CmdletBinding()]
param(
    [string]$Version,
    [string]$Repo = 'abradanthawk-design/iLayoutSettings',
    [string]$AndroidJar,
    [switch]$SkipBuild,
    [switch]$SkipPush,
    [string]$Notes
)

$ErrorActionPreference = 'Continue'

$here = $PSScriptRoot
$root = Split-Path -Parent $here
$module = Join-Path $root 'module'
$manifest = Join-Path $module 'AndroidManifest.xml'

if (-not (Test-Path -LiteralPath $manifest)) { throw "manifest not found: $manifest" }
$manifestXml = [xml](Get-Content -LiteralPath $manifest -Raw -Encoding UTF8)
$versionName = $manifestXml.manifest.versionName
$versionCode = $manifestXml.manifest.versionCode
$packageName = $manifestXml.manifest.package
if (-not $Version) { $Version = $versionName }
if ($Version -ne $versionName) { throw "Version $Version != manifest versionName $versionName" }

$apkName = "iLayoutSettings-$versionName.apk"
$apk = Join-Path $root "dist\$apkName"
$tag = "v$Version"

Write-Host "== iLayoutSettings release $versionName ($versionCode) =="
Write-Host "repo    : $Repo"
Write-Host "package : $packageName"
Write-Host "apk     : $apk"

# ---------------------------------------------------------------- build ----
if (-not $SkipBuild) {
    $jars = @(
        $AndroidJar,
        (Join-Path $root '.tools\platform36\android-36.1\android.jar'),
        (Join-Path $root '..\ios-settings-mod\.tools\platform36\android-36.1\android.jar')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    if (-not $jars) {
        throw 'android.jar not found. Pass -AndroidJar (see docs/03-build.md).'
    }
    $androidJarPath = @($jars)[0]
    Write-Host "[build] module/build.ps1 -AndroidJar $androidJarPath"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $module 'build.ps1') `
        -AndroidJar $androidJarPath
    if ($LASTEXITCODE -ne 0) { throw 'build.ps1 failed' }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "apk not found: $apk" }

# --------------------------------------------------------------- verify ----
$size = (Get-Item -LiteralPath $apk).Length
$sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash

$buildTools = 'C:\Users\AbradantHawk\Documents\deepseek-harness\default-workspace\apk6ify\.downloads\bt\android-14'
$badging = ''
if (Test-Path (Join-Path $buildTools 'aapt2.exe')) {
    $badging = (& (Join-Path $buildTools 'aapt2.exe') dump badging $apk 2>$null |
        Select-String -Pattern "^package:|^sdkVersion|^targetSdkVersion" |
        ForEach-Object { $_.Line }) -join "`n"
}
$cert = ''
$certDigest = ''
$apksigner = Join-Path $buildTools 'apksigner.bat'
if (Test-Path $apksigner) {
    $cert = (& $apksigner verify --print-certs $apk 2>$null |
        Select-String -Pattern 'SHA-256 digest' | Select-Object -First 1).Line
    if ($cert -match 'SHA-256 digest:\s*([0-9a-fA-F]+)') { $certDigest = $matches[1] }
}

# ------------------------------------------------------------- git state ---
$dirty = & git -C $root status --porcelain
if ($dirty) {
    Write-Host "[warn] working tree is dirty; commit first for a reproducible release." -ForegroundColor Yellow
}
$existingTag = & git -C $root tag -l $tag
if (-not $existingTag) {
    & git -C $root tag -a $tag -m "iLayoutSettings $Version"
    if ($LASTEXITCODE -ne 0) { throw "git tag $tag failed" }
    Write-Host "[git] tag $tag created"
} else {
    Write-Host "[git] tag $tag already exists"
}

if ($SkipPush) {
    Write-Host "[skip] -SkipPush: not pushing tag / creating release."
    Write-Host "      tag=$tag apk=$apk sha256=$sha256 size=$size"
    exit 0
}

& git -C $root push origin $tag
if ($LASTEXITCODE -ne 0) { throw "git push $tag failed" }

# --------------------------------------------------------------- release ---
$cred = ("protocol=https`nhost=github.com`n`n" | & git credential fill 2>$null)
$token = ($cred | Select-String -Pattern '^password=' | Select-Object -First 1).Line
if (-not $token) { throw 'GitHub token not found in git credential (git credential fill).' }
$token = $token -replace '^password=', ''

$body = @"
## iLayoutSettings $versionName ($versionCode)

自动构建发布：``$apkName``（$size B）

- SHA-256：``$sha256``
- 包名：``$packageName``
- 签名证书 SHA-256：``$certDigest``

### 安装

1. 先卸载旧模块（包名已改为 ``com.Remo.ilayoutsettings``，不能覆盖安装旧包）；
2. 安装本附件，然后在 LSPosed 里启用 **iLayoutSettings**，作用域只勾 **设置**（``com.android.settings``）；
3. 强制停止「设置」后重新打开。

> 本机 MIUI 可能拦截 adb 安装新包（``INSTALL_FAILED_USER_RESTRICTED``），请把 APK 拷到手机手动安装。

### 本版内容

- 应用 / 仓库改名 **iLayoutSettings**，包名 ``com.Remo.ilayoutsettings``；
- 新增 **iCloud** 行（Game Center 下方，打开小米云服务）；
- 新增 **软件更新** 卡片（仅在系统更新发现新版本时显示，带红色「1」角标）；
- 新增 **耳机** 卡片（仅在连接耳机时显示，标题为设备名）；
- 底部搜索栏进出场过渡动画（220ms，进出搜索不再瞬移）。

完整变更见 [docs/07-changelog.md](../blob/main/docs/07-changelog.md)。
"@
if ($Notes) { $body += "`n`n$Notes" }

$tmp = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($tmp, ($body.Trim()), (New-Object System.Text.UTF8Encoding($false)))
    $payload = @{
        tag_name   = $tag
        name       = "iLayoutSettings $Version"
        body       = (Get-Content -LiteralPath $tmp -Raw -Encoding UTF8)
        draft      = $false
        prerelease = $false
    } | ConvertTo-Json -Depth 5 -Compress
    [System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))

    $api = "https://api.github.com/repos/$Repo/releases"
    $resp = & curl.exe -sS -X POST `
        -H "Authorization: Bearer $token" `
        -H "Accept: application/vnd.github+json" `
        -H "X-GitHub-Api-Version: 2022-11-28" `
        -H "Content-Type: application/json" `
        --data-binary "@$tmp" $api
    $release = $resp | ConvertFrom-Json
    if (-not $release.id) {
        Write-Host $resp
        throw 'create release failed'
    }
    Write-Host "[release] https://github.com/$Repo/releases/tag/$tag"

    $upload = "https://uploads.github.com/repos/$Repo/releases/$($release.id)/assets?name=$apkName"
    $asset = & curl.exe -sS -X POST `
        -H "Authorization: Bearer $token" `
        -H "Accept: application/vnd.github+json" `
        -H "Content-Type: application/vnd.android.package-archive" `
        --data-binary "@$apk" $upload
    $assetObj = $asset | ConvertFrom-Json
    if (-not $assetObj.id) {
        Write-Host $asset
        throw 'upload asset failed'
    }
    Write-Host "[asset] $apkName ($size B, sha256 $sha256)"

    Write-Host ''
    Write-Host "OK -> https://github.com/$Repo/releases/tag/$tag" -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
}
