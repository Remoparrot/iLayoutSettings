# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **16.5 / 96**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | **`com.Remo.ilayoutsettings`**（16.0 由 `com.abradant.iossettings` 改名，属于破坏性变更） |
| 应用名 / 仓库名 | **iLayoutSettings**（GitHub 仓库同步改名，旧地址会 301 跳转） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 当前包 | `dist/iLayoutSettings-16.5.apk`（286,069 B，SHA-256 `84493529…B566`；**已在 25053RT47C 上覆盖安装并实测**） |
| 旧包 | `dist/` 只保留当前包；16.4 / 16.3 / 16.2 / 16.1 / 16.0 / 15.x / 14.8 在各自 GitHub Release |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `iLayoutSettings-16.5.apk`（**当前**） | 286,069 B | `8449352927AD053BD49FDDF3505AADE4532F90933E680CDF94AE72257854B566` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `iLayoutSettings-16.4.apk`（历史，Release） | 286,069 B | `EE68C55D05E9567831C3DE3327544A3D9AB2EBAD426B907EEC9C17329368B3C0` | 同上 |
| `iLayoutSettings-16.3.apk`（历史，Release） | 236,593 B | `ED0C383E5A91DC4F054ED3C362AAF4AAE3F44506CE80F4078CD3C36439402873` | 同上 |
| `iLayoutSettings-16.2.apk`（历史，Release） | 236,593 B | `2D2F068C8DEC1A5D5B982F6347F71580F6C4800B8BE89F4286C2836E945AEC3B` | 同上 |
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
| 最新 Release | [v16.5](https://github.com/Remoparrot/iLayoutSettings/releases/tag/v16.5)（附件 `iLayoutSettings-16.5.apk`，286,069 B，SHA-256 `84493529…B566`） |
| 自动发版 | `tools/release.ps1`：构建 → 校验 → **自动 add/commit/push 当前分支** → 打 tag → 建 Release → 上传附件；脚本必须是 UTF-8 **带 BOM**，且在发 POST 前自检正文（中文丢失 / 占位符残留即中止） |
| 旧 Release | v16.4、16.3、16.2、16.1、16.0、15.5、14.8 保留（本地 `dist/` 不再保留旧包） |

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
- ✅ **顶栏透明度（16.2）**：参数页「顶栏 · 透明度」0–100%；16.3 起默认 **2%**，
  16.5 起**任何滚动位置都用这个半透明底色**（不再在顶部恢复实色）
- ✅ **顶栏发丝线（16.2，16.3 改深色色值，16.4 修主题切换，16.5 跟随折叠）**：
  在 `ActionBarOverlayLayout` 的 overlay 上画 0.5dp 半透明线——浅色模式黑色 12%，深色模式
  **中性灰 30%（`0x4DAAAAAA`）**。16.4 起颜色每帧按当前 `uiMode` 取，设置界面内切深浅色后
  线不再消失；16.5 起显示条件改为「列表已滚动 **或** 栏已从大标题高度收缩」，
  折叠动画中栏开始压住内容时线就出现，回到顶部（栏恢复大标题高度）后隐藏。
- ✅ **顶栏始终模糊（16.2 修，16.3 改默认，16.4 修首发，16.5 改常开）**：`topbar_blur` 0–100%，
  默认 **5%**（约 7.5dp ≈ 22px），透明度默认 **2%**。直接改 `NestedHeaderLayout` 原生
  `mBlurEffect`，并给 `action_bar_container` 自己下发 `MiuiBlurUtils.setBackgroundBlur`
  （参数是像素，按 density 换算）。16.4 在 `refreshBlur()` 前预写半径，折叠栏第一次出现
  就带模糊；**16.5 起不再按「是否滚动」开关模糊和底色，并且清掉 MIUI 给滚动容器的
  `clipBounds`**——账户卡等内容滑进栏下就是模糊的，不再被不透明栏硬切。
  旧 110dp 读取时折算为 73%
- ✅ **高级设置开关（16.4，GUI）**：模块应用内「高级设置」总开关（默认关）。关闭时只显示
  搜索栏玻璃滑杆；开启后才出现「顶栏」卡片（模糊 / 透明度 / 显示顶栏分割线）。
  发丝线默认仍绘制，开关只控制是否显示/隐藏
- ✅ **应用图标（16.4 换风格）**：蓝紫渐变 + 半透明玻璃盘 + 白齿轮，`tools/make_launcher_icon.py`
  生成 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 五档 PNG；另有 B（纸感线框）、C（深色滑杆）两套候选
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
4. **顶栏发丝线/模糊依赖栏高与 clip**：16.5 用 `OPEN_BAR_HEIGHT` 记录见过的最大栏高来判断
   「折叠中」；若某些主题下 action bar 高度本身就会随模式变化（如搜索模式），线可能在那些
   模式下也显示。清 `clipBounds` 依赖 `updateOverBgState()` 仍被调用；换 ROM 若改成别处设 clip，
   内容会重新被裁在栏底。线宿主仍是 `action_bar_container` 的父级（当前 `ActionBarOverlayLayout`），
   若父级也不画 overlay，线会不可见（实测 `ActionBarContainer` 自己的 overlay 不绘制）。
   深浅色线色 16.3 已实拍确认（浅色 `(222,222,223)`、深色 `(58,58,59)`），16.5 复测一致。
5. **顶栏模糊依赖 MIUI 私有 API**：`MiuiBlurUiHelper.setBlurParams` / `mBlurEffect` /
   `refreshBlur` 在 ROM 升级改名时 hook 会软失败，表现回到「模糊固定、不可调」。
6. **仅在默认主题 / 默认图标包上验证**；第三方主题替换 `ic_*` / 分割线颜色时需要再确认
   （16.3 起耳机/iCloud 用模块内置图标，不受主题覆盖）。
7. **图标是 PNG（非 adaptive icon）**：HyperOS 会自己套圆角遮罩；第三方图标包/主题不会替换它。
   要跟系统主题走，后续可加 `mipmap-anydpi-v26/ic_launcher.xml`。
8. `docs/07-changelog.md` 的旧「7. 校验」节数据过期（历史记录，未改）。
9. 无 CI：`tools/release.ps1` 已能自动提交/推送/发 Release，但 GitHub Actions 仍未接。

## 下一步（优先级从高到低）

1. 观察 16.5 的「常开模糊」在**搜索模式**、第三方主题、平板/横屏下的表现（栏高变化会不会
   让线提前出现）；必要时把发丝线的显示条件收紧到「折叠进度 > 阈值」；
2. 图标如需跟随系统主题，补 `mipmap-anydpi-v26` adaptive icon（前景 = 齿轮+玻璃盘，背景 = 渐变）；
3. 观察耳机卡在真实「开盖自动连」场景下的出现时机，必要时把防抖从 350ms 调到 200ms；
4. 接 GitHub Actions：tag 触发 `tools/release.ps1` 的只读版本（构建 + 上传）；
5. 清理 `Hook` 未引用的遗留类（`GlassToggle`、`IntroCard`、`AboutIos` 等）；
6. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 21.0.2（`D:\Android\jdk21\jdk-21.0.2`，16.4 构建实测；17 亦可） |
| build-tools | 37.0.0（`D:\Android\Sdk\build-tools\37.0.0`，aapt2 / d8 / zipalign / apksigner 实测可用） |
| android.jar | API 37（`D:\Android\Sdk\platforms\android-37.0\android.jar`，构建显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| 调试产物目录 | 手机上统一放 `/sdcard/Download/iLayoutSettings/`（不要散落在存储根目录；测试截图拉回后即删） |
