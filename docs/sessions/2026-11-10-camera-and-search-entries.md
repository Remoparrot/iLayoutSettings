# 首页「相机」「搜索」入口（2026-11-10）

状态：已完成
关联版本：15.4 (89) → 15.5 (90)
关联提交：279d4fb
参与者：用户 / DeepSeek Harness

## 1. TL;DR

在首页「外观」下方加了一行「相机」（打开小米相机设置），在「墙纸」下方加了一行「搜索」（打开小米搜索设置），
图标分别复用设置自己的 `ic_settings_camera` / `ic_search_24dp`。已构建 15.5 (90)、安装上机；
两行位置、图标、点击跳转、返回后不重复均验证通过。

## 2. 对话重点

- **用户要求**：「再加一个相机，可以进入小米相机的设置，在『外观』的下面，`ic_settings_camera`；
  在『墙纸』下面加一个搜索，`ic_search_24dp`，可以打开小米搜索的设置」。
- **做出的决策**：
  - 沿用「合成 Header」写法，不碰 MIUI 数据源；
  - 两条行都用**显式组件**（`Intent.setClassName`）而不是隐式 action：目标 Activity 在同一 ROM 上唯一，
    显式组件不受 `miui.intent.action.APP_SETTINGS` 这种多应用共用 action 的解析顺序影响；
  - 位置用「共用锚点 slot + 更大 `originalIndex`」实现，不重编号 `SLOT_IDS`、不改其它行的 slot；
  - 图标只查 `com.android.settings` 的资源（`res.getIdentifier(..., "drawable", "com.android.settings")`），
    资源缺失时保留纯文字行并记日志，不内置 PNG。
- **范围外但被提到的事**：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/com/abradant/iossettings/HeaderData.java` | 新增 `CAMERA_*` / `SEARCH_*` 常量；`parse()` 调 `addCameraRow()` / `addSearchRow()`；新增 `isIntentRow()` / `isSyntheticCamera()` / `isSyntheticSearch()` 与通用 `addIntentRow()`；`apply()` 清理合成行时加上这两类 | 两行都需要「锚在指定行之后 + resume 不累积」，用同一个 helper 避免三份重复代码 |
| `module/AndroidManifest.xml` | 15.4 / 89 → 15.5 / 90，注释同步 | 版本号唯一来源 |
| `docs/assets/*-15.5.png` | 新增 4 张截图（浅色/深色首页、相机设置页、搜索设置页） | 发布与记录需要视觉证据 |
| `docs/00-status.md`、`07-changelog.md`、`02-architecture.md`、`04-install.md`、`README.md` 等 | 版本、功能、顺序、日志关键字同步 | 仓库规则：版本变化一次做齐 |

## 4. 设计思路

- **问题本质**：iOS 设置卡片里有「相机」和「搜索」两个入口；HyperOS 没有对应 header，但两个设置页都在各自应用里。
  需要的只是「视觉入口 + 正确跳转」，不需要新功能。
- **方案 A（选中）：锚点 slot + originalIndex**
  - `Item.slot` 决定卡片内顺序。给合成行复用锚点行（墙纸 = slot 11、外观 = slot 14）的 slot，
    再把 `originalIndex` 设为 `Integer.MAX_VALUE - 4`，排序 `(group, slot, originalIndex)` 后它必然排在锚点行之后、
    下一 slot 的行之前；
  - 好处：`SLOT_IDS` / `SLOT_GROUP` 一个字都不用改，其它行完全不受影响；锚点行缺失时仍会落在该 slot 的位置，
    不会掉进「其他」卡片。
- **方案 B（否决）：在 `SLOT_IDS` 里插新 slot 并给后面所有行 +1**
  - 要同步改 `SLOT_GROUP`、标题表与所有硬编码的 slot 数字，牵一发动全身，且后续加行还会再痛一次。
- **跳转目标的选择**：
  - 相机：`com.android.camera/.CameraPreferenceActivity`。`dumpsys package com.android.camera` 里它挂在
    `miui.intent.action.APP_SETTINGS` 与 `QS_TILE_PREFERENCES` 两个 action 下，显式组件更直接；
  - 搜索：`com.android.quicksearchbox/.preferences.SearchSettingsPreferenceActivity`，同样挂
    `miui.intent.action.APP_SETTINGS` / `android.search.action.SEARCH_SETTINGS`。
  - 两条都用 `resolveActivity` 做存在性检查，非 HyperOS / 未装该应用时跳过，不留死入口。
- **图标来源**：`aapt2 dump resources` 确认两个资源都在设置 APK 里
  （`drawable/ic_search_24dp` = `0x7f080cb9`，`drawable/ic_settings_camera` = `0x7f080cd4`），
  所以继续用 `iconRes`，主题覆盖与深浅色由资源系统解析。

## 5. 踩过的坑

本次无新坑（生产代码一次通过）。验证阶段有一次误判：按截图缩略图比例换算点击坐标，结果点在「外观」行上，
打开了外观页。改用 `uiautomator dump` 读 `android:id/title` 节点的真实 `bounds` 后一次点中。
**如何避免**：真机点击验证不要用缩略图估坐标，直接解析 UI dump 的 bounds。

## 6. 验证与证据

构建（PowerShell 5.1，JDK 17 / build-tools 37.0.0 / android-37.0）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk C:\...\apk6ify\.downloads\jdk\jdk17.0.21_0 `
  -BuildTools D:\Android\Sdk\build-tools\37.0.0 `
  -AndroidJar D:\Android\Sdk\platforms\android-37.0\android.jar
```

结果：

```
package: name='com.abradant.iossettings' versionCode='90' versionName='15.5'
dex version : 039
xposed_init : True
module class: True
reorder     : True
size   : 207753 bytes
sha256 : C8146210C872BB737C100FD57E598DCA73DD8F5C1134CE2AEF4C43CB6A05E535
```

安装与日志：

```powershell
adb install -r dist\IosSettingsLayout-15.5.apk
adb shell am force-stop com.android.settings
adb shell am start -n com.android.settings/.Settings
adb logcat -d -s IosSettings
```

```
I IosSettings: camera_entry row added below slot 14
I IosSettings: search_entry row added below slot 11
I IosSettings: reordered 30 headers
I IosSettings:   g4 s11 id=personalize_title title=墙纸
I IosSettings:   g4 s11 id=search_entry title=搜索
I IosSettings:   g4 s12 id=wireless_settings title=控制中心
I IosSettings:   g4 s14 id=display_settings title=外观
I IosSettings:   g4 s14 id=camera_entry title=相机
I IosSettings:   g4 s15 id=launcher_settings title=主屏幕与App资源库
```

点击验证（用 `uiautomator dump` 的 bounds 点击）：

- 搜索行 → `topResumedActivity=com.android.quicksearchbox/.preferences.SearchSettingsPreferenceActivity`，
  页面标题「搜索设置」；
- 相机行 → `topResumedActivity=com.android.camera/.CameraPreferenceActivity`，页面标题「相机设置」；
- 返回设置后 `uiautomator dump` 里 `text="搜索" resource-id="android:id/title"` 与
  `text="相机" resource-id="android:id/title"` 各 1 个节点（冷启动、往返后都不重复）；
- 深色模式（`cmd uimode night yes`）下两行图标仍正常解析；
- 截图：`docs/assets/home-camera-search-15.5.png`、`home-camera-search-dark-15.5.png`、
  `camera-settings-15.5.png`、`search-settings-15.5.png`。

未验证：第三方主题/图标包替换这两个资源时的表现；平板/分屏。

## 7. 未完成 / 下一步 / 风险

- 未做：把 15.5 发到 GitHub Release、更新 Release 说明与下载链接（属于发版流程，见 `00-status.md` 下一步）；
- 建议下一步：在第三方主题上验证 `ic_settings_camera` / `ic_search_24dp` 是否仍可取到；若某主题删了资源，
  行会退化成纯文字（日志 `no Settings drawable ...`），可按需内置 PNG 兜底；
- 风险：两个目标 Activity 都是写死的类名，HyperOS 大版本升级后可能改名；`resolveActivity` 为空时只会少一行，
  不会崩，但需要按日志补新类名。
