# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **16.2 / 93**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | **`com.Remo.ilayoutsettings`**（16.0 由 `com.abradant.iossettings` 改名，属于破坏性变更） |
| 应用名 / 仓库名 | **iLayoutSettings**（GitHub 仓库同步改名，旧地址会 301 跳转） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 当前包 | `dist/iLayoutSettings-16.2.apk`（236,593 B，SHA-256 `2D2F068C…EC3B`；**已在 25053RT47C 上覆盖安装并实测**） |
| 旧包 | `dist/` 只保留当前包；16.1 / 16.0 / 15.x / 14.8 在各自 GitHub Release |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `iLayoutSettings-16.2.apk`（**当前**） | 236,593 B | `2D2F068C8DEC1A5D5B982F6347F71580F6C4800B8BE89F4286C2836E945AEC3B` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `iLayoutSettings-16.1.apk`（历史，Release） | 224,305 B | `83BE73357A1AD29DCB57EC8BCF217127BEA7B7461A18DF70E0189EC83571F77F` | 同上 |
| `iLayoutSettings-16.0.apk`（历史，Release） | 220,209 B | `8DA082CD8FC578676EB5138F7150BDCFB40C4DBE45CD3625E8B0521923921A73` | 同上 |

> 16.0 起包名是 `com.Remo.ilayoutsettings`：旧包 `com.abradant.iossettings` 仍需先卸载；
> 16.0 起同签名可直接覆盖安装。见 [安装说明 · 16.0 迁移](04-install.md#160-迁移必须做)。
> 16.2 起 `build.ps1` 成功后自动删除 `dist/` 里除当前包外的 APK；历史包只从 Releases 取。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/Remoparrot/iLayoutSettings （旧 `iLyayot` 自动跳转） |
| 默认分支 | `main` |
| 最新 Release | [v16.2](https://github.com/Remoparrot/iLayoutSettings/releases/tag/v16.2)（附件 `iLayoutSettings-16.2.apk`，236,593 B，SHA-256 `2D2F068C…EC3B`） |
| 自动发版 | `tools/release.ps1`：构建 → 校验 → **自动 add/commit/push 当前分支** → 打 tag → 建 Release → 上传附件 |
| 旧 Release | v16.1、v16.0、v15.5、v14.8 保留（本地 `dist/` 不再保留旧包） |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`），卡片仍由 miuix 绘制
- ✅ 首页「专注模式」「相机」「搜索」合成入口（15.4 / 15.5 起）
- ✅ **iCloud 入口（16.0，16.2 图标更新）**：固定在「Game Center」下方、同一张卡片；
  点击打开小米云服务 `com.miui.cloudservice/.ui.MiCloudMainActivity`；
  图标用模块内置的 `ic_cloud.png`（来源：用户提供的 `com.android.settings` 资源包），
  设置包同名 drawable 只作兜底
- ✅ **软件更新卡片（16.0，条件显示）**：读 `Settings.Global.miui_new_version`，
  非空才出现；标题「有软件更新可用」，右侧红色「1」角标（`HeaderOverlay` 画在 `widget_frame` 里），
  点击打开 `com.android.updater/.MainActivity`
- ✅ **耳机卡片（16.0 起，16.1 增强）**：
  - `AudioManager.getDevices()` 检测 A2DP / SCO / BLE / 有线 / USB / 助听器，A2DP 优先；
  - 标题 = 设备别名（`BluetoothDevice.getAlias()`，取不到用 `getProductName()`）；
  - 图标用模块内置的 `ic_sub_airpods.png`（来源：用户提供的资源包，灰色 AirPods 圆角图标），
    设置包同名 drawable 只作兜底；
  - **点击进 MIUI 耳机详情页**（`miui.bluetooth.action.HEADSET_SETTINGS` →
    `com.android.settings.bluetooth.MiuiHeadsetActivity`，电量 / 改名 / 检查更新 / 功能开关），
    有线耳机等没有蓝牙设备时退回蓝牙设置；
  - **16.1：`HeadsetWatcher` 注册 `AudioDeviceCallback`，在设置首页打开期间连上/断开耳机，
    卡片即时出现/消失，不用强制停止设置**（350ms 防抖）
- ✅ 底部搜索栏 + 液态玻璃（`BottomSearch` / `SearchGlass`）
- ✅ **搜索栏进出场过渡（16.0 / 16.1 修时序）**：进出搜索 220ms 滑动 + 缩放 + 淡入淡出；
  退出时底部栏保持隐藏，等 MIUI 收起顶部搜索框（`ActionModeAnimationListener#onStop(open=false)`）
  后才出现，不再短暂同时显示两个搜索栏
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）
- ✅ **顶栏透明度（16.2）**：参数页「顶栏 · 透明度」0–100%，只作用于滚动后的半透明底色
- ✅ **顶栏发丝线（16.2）**：折叠顶栏出现时，在 `ActionBarContainer` 底部画 0.5dp、黑色 12%
  半透明线（深色模式白色 15%）；大标题展开/回到顶部时隐藏
- ✅ **顶栏模糊可调（16.2 修）**：`topbar_blur` 改为 0–100%，默认 **30%**、透明度 **45%**
  （低模糊淡磨砂，参考图效果）；直接改 `NestedHeaderLayout` 原生 `mBlurEffect`，
  不再只改被盖住的 action bar 背景。旧 110dp 读取时折算为 73%
- ✅ 二级页图标、卡片末行修正、应用内参数界面（搜索栏模糊支持 0.1dp）
- ✅ 无 Gradle 构建脚本（`module/build.ps1`，成功后自动清 `dist/` 旧包）
  + 一键发版脚本（`tools/release.ps1`，默认自动提交/推送/发 Release）

## 已知问题

1. **条件卡片依赖小米自己的状态**：
   - 更新卡的 `miui_new_version` 会被系统更新 App 自己清空/写入，手动 `settings put` 只用于测试；
   - 耳机卡只认「音频输出设备」：蓝牙耳机在 MTP/HFP 之外的连接阶段可能短暂识别不到，
     刷新会慢半拍（350ms 防抖 + 一次重建）。
2. **耳机详情页依赖 MIUI 私有 Activity**：`MiuiHeadsetActivity` 需要 `COME_FROM=MIUI_BLUETOOTH_SETTINGS`
   和 `android.bluetooth.device.extra.DEVICE`（或 `bluetoothaddress`）；ROM 升级改语义时要重新抓。
3. **图标优先级（16.2 起）**：耳机 / iCloud 以模块内置 PNG（用户提供的主题资源）为准，
   `com.android.settings` 同名 drawable 只在模块内置资源缺失时兜底；主题不能再覆盖这两张图。
4. **顶栏发丝线只在滚动后显示**：`NestedHeaderLayout.isHeaderOpen()` 在部分主题/ROM 上语义变化时，
   可能出现过早/过晚显示；深色模式的白色 15% 线还未实拍确认。
5. **顶栏模糊依赖 MIUI 私有 API**：`MiuiBlurUiHelper.setBlurParams` / `NestedHeaderLayout.mBlurEffect`
   在 ROM 升级改名时 hook 会软失败，表现回到「模糊固定、不可调」。
6. **仅在默认主题 / 默认图标包上验证**；第三方主题替换 `ic_*` / 分割线颜色时需要再确认。
7. `docs/07-changelog.md` 的旧「7. 校验」节数据过期（历史记录，未改）。
8. 无 CI：`tools/release.ps1` 已能自动提交/推送/发 Release，但 GitHub Actions 仍未接。

## 下一步（优先级从高到低）

1. 深色模式实拍顶栏发丝线 / 透明度，必要时把黑色 12% 调到 8%；
2. 观察耳机卡在真实「开盖自动连」场景下的出现时机，必要时把防抖从 350ms 调到 200ms；
3. 接 GitHub Actions：tag 触发 `tools/release.ps1` 的只读版本（构建 + 上传）；
4. 清理 `Hook` 未引用的遗留类（`GlassToggle`、`IntroCard`、`AboutIos` 等）；
5. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| 调试产物目录 | 手机上统一放 `/sdcard/Download/iLayoutSettings/`（不要散落在存储根目录；测试截图拉回后即删） |
