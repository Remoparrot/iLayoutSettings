# 首页「专注模式」入口（2026-11-10）

状态：已完成
关联版本：15.3 (88) → 15.4 (89)
关联提交：<待补>
参与者：用户 / DeepSeek Harness

## 1. TL;DR

在首页「屏幕时间」正上方加了一行合成入口「专注模式」，点击打开系统「勿扰模式」设置页，图标复用设置自己的
`ic_do_not_disturb_mode_settings`。已构建 15.4 (89)、安装上机，浅色/深色与冷启动/返回均只有一行，点击跳转正确。

## 2. 对话重点

- **用户要求**：「给『屏幕时间』上面加一个『专注模式』选项」；随后补充「专注模式是打开勿扰模式的设置，用设置中的
  `ic_do_not_disturb_mode_settings` 图片」。
- **做出的决策**：
  - 用合成 `Header`（与飞行模式行同一套写法），不改 MIUI 数据源；
  - 点击走 `android.settings.ZEN_MODE_SETTINGS`（实测打开 `Settings$ModesSettingsActivity`，标题「勿扰模式」），
    不写死组件名，避免不同 HyperOS 版本 Activity 名变化；
  - 图标不内置 PNG，直接 `res.getIdentifier("ic_do_not_disturb_mode_settings", ...)`，让主题覆盖与深浅色走系统资源。
- **范围外但被提到的事**：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/com/abradant/iossettings/HeaderData.java` | `SLOT_IDS` / `SLOT_GROUP` / `SLOT_TITLES` 在「屏幕时间」前各插一条（`focus_mode_settings`、group 5、标题「专注模式」）；`parse()` 调 `addFocusModeRow()`；`apply()` 的合成行清理加 `isSyntheticFocus()`；新增 `addFocusModeRow()` / `slotIndex()` | 位置要固定在「屏幕时间」上方，并且每次 resume 不能重复累积 |
| `module/AndroidManifest.xml` | 15.3 / 88 → 15.4 / 89，注释同步 | 版本号唯一来源 |
| `docs/*`、`README.md`、`agent.md` | 版本、功能、截图、变更记录同步 | 仓库规则：版本变化一次做齐 |

## 4. 设计思路

- **问题本质**：iOS 首页在「声音」和「屏幕时间」之间有一个独立的「专注模式」，HyperOS 设置没有这个 header，
  但系统里确实有勿扰模式页面；需要的是「一个视觉入口 + 正确的跳转」，而不是新功能。
- **方案对比**：
  - A：合成 header + 隐式 action `ZEN_MODE_SETTINGS` —— 写法与既有飞行模式行一致，跳转由系统解析，主题图标自动跟随；缺点是依赖 action 存在。
  - B：直接启动 `com.android.settings/.dndmode.DoNotDisturbModeActivity` —— 组件名写死，HyperOS 版本一变就可能失效。
- **最终选择与取舍**：A。`resolveActivity` 为空时（非 HyperOS / 页面被裁）就不加这一行，宁可没有也不留死入口。

## 5. 踩过的坑

### 坑：`miui.intent.action.FOCUS_MODE` 不是勿扰页，而是全屏专注计时器

- **现象**：`dumpsys package com.xiaomi.misettings` 里 `FOCUS_MODE` 有两个 Activity（port/land），`resolve-activity` 落到 `ResolverActivity`。
- **根因**：`FOCUS_MODE` 是小米「专注模式」计时器，不是设置里的勿扰配置页。
- **解法**：改用 `android.settings.ZEN_MODE_SETTINGS`，实测进入 `Settings$ModesSettingsActivity`（标题「勿扰模式」）。
- **如何避免**：先在设备上 `resolve-activity` + 真机截图确认目标页，再写 intent。

### 坑：系统原图是红色线稿，主题里却是紫色月亮

- **现象**：从 `settings-base.apk` 抽出的 `ic_do_not_disturb_mode_settings.png` 是红圈斜杠线稿；上机后显示为紫色月亮方块。
- **根因**：当前 HyperOS 主题覆盖了该 drawable；直接内置 PNG 会丢掉主题跟随。
- **解法**：保留 `iconRes`（资源 id），让 MIUI 在设置进程里按当前主题/深浅色解析。
- **如何避免**：凡是「设置自带图标」都走资源 id，不抽成模块 PNG。

## 6. 验证与证据

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk ...\jdk17.0.21_0 -BuildTools ...\bt\android-14 `
  -AndroidJar ...\platform36\android-36.1\android.jar
adb install -r dist\IosSettingsLayout-15.4.apk
adb shell am force-stop com.android.settings
adb shell am start -a android.settings.SETTINGS
```

- 结果：`versionCode='89' versionName='15.4'`，`size : 207753 bytes`，
  `sha256 : 6382711418C80596D362CE727CDBE567FC486AB3FAA88EF0F44136AA5C0A02F9`。
- 日志：`I IosSettings: focus mode row added`。
- 点击：`topResumedActivity=com.android.settings/.Settings$ModesSettingsActivity`，截图标题「勿扰模式」。
- 稳定性：冷启动、从勿扰页返回后都只有一行「专注模式」；深色模式下图标随主题解析。
- 截图：`docs/assets/focus-mode-15.4.png`、`focus-mode-dark-15.4.png`、`focus-mode-page-15.4.png`。
- **未验证的部分**：第三方主题替换 `ic_do_not_disturb_mode_settings` 的情况；平板/分屏。

## 7. 未完成 / 下一步 / 风险

- 未完成：未打 Release、未更新 Release 下载链接。
- 建议下一步（按顺序）：第三方主题验证图标 → 发 Release → 清理死代码。
- 风险：非 HyperOS 设备上 `ZEN_MODE_SETTINGS` 解析不到时不会出现该行（设计如此，但用户可能以为是 bug）。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（概览 / 构建 / agent）
- [x] 若发版：`docs/07-changelog.md` 顶部已加一节
