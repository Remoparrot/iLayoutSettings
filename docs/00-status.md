# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **16.1 / 92**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | **`com.Remo.ilayoutsettings`**（16.0 由 `com.abradant.iossettings` 改名，属于破坏性变更） |
| 应用名 / 仓库名 | **iLayoutSettings**（GitHub 仓库同步改名，旧地址会 301 跳转） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 当前包 | `dist/iLayoutSettings-16.1.apk`（224,305 B，SHA-256 `83BE7335…77F7`；**已在 25053RT47C 上覆盖安装并实测**） |
| 旧包 | `dist/iLayoutSettings-16.0.apk`（Release v16.0）与 15.x / 14.8 保留作历史 |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（旧） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da5…0d4a`（与 15.x 不同） |
| `dist/IosSettingsLayout-15.5.apk`（旧） | 207,753 B | `C8146210C872BB737C100FD57E598DCA73DD8F5C1134CE2AEF4C43CB6A05E535` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/iLayoutSettings-16.0.apk`（旧） | 220,209 B | `8DA082CD8FC578676EB5138F7150BDCFB40C4DBE45CD3625E8B0521923921A73` | 同上 |
| `dist/iLayoutSettings-16.1.apk`（**当前**） | 224,305 B | `83BE73357A1AD29DCB57EC8BCF217127BEA7B7461A18DF70E0189EC83571F77F` | 同上 |

> 16.0 起包名是 `com.Remo.ilayoutsettings`：旧包 `com.abradant.iossettings` 仍需先卸载；
> 16.0 → 16.1 同签名，可直接覆盖安装。见 [安装说明 · 16.0 迁移](04-install.md#160-迁移必须做)。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/abradanthawk-design/iLayoutSettings （旧 `iLyayot` 自动跳转） |
| 默认分支 | `main` |
| 最新 Release | [v16.1](https://github.com/abradanthawk-design/iLayoutSettings/releases/tag/v16.1)（tag 指向 16.1 提交，附件 `iLayoutSettings-16.1.apk`，224,305 B） |
| 自动发版 | `tools/release.ps1`：构建 → 校验 → 打 tag → 建 Release → 上传附件，一条命令完成 |
| 旧 Release | v16.0、v15.5、v14.8 保留 |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`），卡片仍由 miuix 绘制
- ✅ 首页「专注模式」「相机」「搜索」合成入口（15.4 / 15.5 起）
- ✅ **iCloud 入口（16.0）**：固定在「Game Center」下方、同一张卡片；
  点击打开小米云服务 `com.miui.cloudservice/.ui.MiCloudMainActivity`；
  图标优先取设置包里的同名 `ic_cloud`，取不到才用模块内置图标
- ✅ **软件更新卡片（16.0，条件显示）**：读 `Settings.Global.miui_new_version`，
  非空才出现；标题「有软件更新可用」，右侧红色「1」角标（`HeaderOverlay` 画在 `widget_frame` 里），
  点击打开 `com.android.updater/.MainActivity`
- ✅ **耳机卡片（16.0 起，16.1 增强）**：
  - `AudioManager.getDevices()` 检测 A2DP / SCO / BLE / 有线 / USB / 助听器，A2DP 优先；
  - 标题 = 设备别名（`BluetoothDevice.getAlias()`，取不到用 `getProductName()`）；
  - 图标优先取设置包里的同名 `ic_sub_airpods`，取不到才用模块内置图标；
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
- ✅ 二级页图标、卡片末行修正、顶栏滚动模糊、应用内参数界面
- ✅ 无 Gradle 构建脚本（`module/build.ps1`）+ 一键发版脚本（`tools/release.ps1`）

## 已知问题

1. **条件卡片依赖小米自己的状态**：
   - 更新卡的 `miui_new_version` 会被系统更新 App 自己清空/写入，手动 `settings put` 只用于测试；
   - 耳机卡只认「音频输出设备」：蓝牙耳机在 MTP/HFP 之外的连接阶段可能短暂识别不到，
     刷新会慢半拍（350ms 防抖 + 一次重建）。
2. **耳机详情页依赖 MIUI 私有 Activity**：`MiuiHeadsetActivity` 需要 `COME_FROM=MIUI_BLUETOOTH_SETTINGS`
   和 `android.bluetooth.device.extra.DEVICE`（或 `bluetoothaddress`）；ROM 升级改语义时要重新抓。
3. **图标回退**：本机 `com.android.settings` 没有 `ic_cloud` / `ic_sub_airpods` 资源（aapt2 已确认），
   所以当前仍显示模块内置图标；主题/新 ROM 提供同名 drawable 时会自动优先使用。
4. **仅在默认主题 / 默认图标包上验证**；第三方主题替换 `ic_*` 资源时需要再确认。
5. `docs/07-changelog.md` 的旧「7. 校验」节数据过期（历史记录，未改）。
6. 无 CI：`tools/release.ps1` 是本机脚本，GitHub Actions 仍未接。

## 下一步（优先级从高到低）

1. 观察耳机卡在真实「开盖自动连」场景下的出现时机，必要时把防抖从 350ms 调到 200ms；
2. 接 GitHub Actions：tag 触发 `tools/release.ps1` 的只读版本（构建 + 上传）；
3. 清理 `Hook` 未引用的遗留类（`GlassToggle`、`IntroCard`、`AboutIos` 等）；
4. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| 调试产物目录 | 手机上统一放 `/sdcard/Download/iLayoutSettings/`（不要散落在存储根目录） |
