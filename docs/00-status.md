# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **15.2 / 87**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | `com.abradant.iossettings` |
| 应用名 | **iLyayot**（与 GitHub 仓库名一致，桌面图标已恢复） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 已发布包 | `dist/IosSettingsLayout-14.8.apk`（上一版 Release） |
| 15.2 预览包 | `dist/IosSettingsLayout-15.2.apk`（**未发布，已上机**：2026-11-10 00:0x，25053RT47C / HyperOS 3） |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（用户提供的 14.8 发布包） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da57965f604ae13a2f4fc967ffc6c09f14bbdef5b9329effca65dad0f4a` |
| `dist/IosSettingsLayout-15.0.apk` | 203,657 B | `3A80A921ACD695D2852346FB3F5AB9B7A5A4FDDFBBF17AE86ED7C0E8892E2A20` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/IosSettingsLayout-15.2.apk`（本次） | 见构建输出 | 每次构建不同（zip 时间戳） | 同上 |

> 重编同一个 commit 的 sha256 会变（zip 条目时间戳），上表只记录仓库里这份 APK 的哈希。

⚠️ 15.x 全部使用同一测试密钥签名，可直接覆盖安装。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/abradanthawk-design/iLyayot |
| 默认分支 | `main` |
| Release | https://github.com/abradanthawk-design/iLyayot/releases/tag/v14.8 |
| 初始提交 | `173a302` |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`）
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）：
  - 一律使用 MIUI 原生 `SlidingSwitch`，**不再自绘**；
  - HyperiOS 的 hook 把开关画在 `widget_frame` 的坐标系里，且首帧缓存 box → 只平移 `widget_frame` 的 translation，
    实测旋钮中心与标题中心重合（945 vs 945）；
  - 无 HyperiOS 时同样走原生开关，`recenter()` 兼容两种父布局
- ✅ 二级页 24 个图标（`SubPageIcons`：23 条 key 映射 + 标题兜底）
- ✅ 底部原生搜索栏 + **Kyant backdrop 2.0.1 玻璃**（`BottomSearch` / `SearchGlass` / `SearchHost`）：
  抓窗口 → **1dp 模糊** → 圆角折射（**12dp 厚度 / 8dp 力度**）→ 底部内阴影；
  **无 rim 高光**；原生胶囊 fill 恢复 `0xFF`；shader 内按 93% 混入主题表面色 → **实心磨砂**；
  抓图在 `OnPreDraw` 里做，**只有位图变化才重绘**；shader 不可用时逐级退到「只有模糊 / 半透明胶囊」
- ✅ 应用内参数界面（`MainActivity`）：搜索栏模糊 / 折射力度 / 折射厚度、顶栏模糊，滑杆即改即生效（400ms 轮询）
- ✅ 跨进程参数：`PrefsProvider`（导出 ContentProvider）→ `ContentResolver.call()`，读不到时回退 XSharedPreferences
- ✅ 卡片末行双层圆角修正（`CardFix` / `SubPageCards`）
- ✅ 顶栏按滚动出现的模糊（`ForceBlur` / `UiTweaks`），半径读 `topbar_blur`
- ✅ 无 Gradle 构建脚本可在本机复现（见 `docs/03-build.md`）

## 已知问题

1. **源码与 14.8 发布包不完全一致**
   用户提供的发布包 dex 为 123,864 B，14.8 源码重编 130,044 B，14.9 为 134,592 B，15.0 为 147,204 B。
   源码中仍留有未被 `Hook` 调用的实验类（`IntroCard`、`AboutIos`、`TitleFix`、`TitleOverlay`、`PrefsBridge`、`GlassToggle`）。
   详见 `docs/06-pitfalls.md`、`docs/09-roadmap.md`。

2. **15.2 的参数界面只验证了「搜索栏」链路**
   顶栏滑杆写读正常，但「滚动后 `MiuiBlurUtils` 实际套用的半径」还差一次滚动截图；
   设备随后进入锁屏 / 防误触模式，浅色主题、120Hz 中间帧、分屏/平板未测。

3. **XSharedPreferences 在本机不可用**
   LSPosed 的镜像文件是 `-rw-rw----`、属主为模块 UID，设置进程 `readable=false`，`keys=[]`。
   所以参数必须走 `PrefsProvider`；`xposedsharedprefs` 元数据保留但不依赖。

4. **`docs/07-changelog.md` 的「7. 校验」节数据过期**（原文写 216,015 B，是后续版本产物，14.8 实际 195,465 B）。

5. **无 CI**：构建依赖本机 JDK/build-tools，尚未接 GitHub Actions。

## 下一步（优先级从高到低）

1. 解锁设备后补：顶栏模糊滑杆的滚动截图（`top bar blur=... radius=...`）、浅色主题下的实心玻璃颜色；
2. 观感确认后把 15.2 放进 Release、更新 README 下载链接；
3. 用固定 keystore 重签并统一发布包与源码；
4. 清理 `Hook` 未引用的遗留类（`GlassToggle` 已停用，可删）；
5. 接 GitHub Actions：tag 触发构建 + 自动生成 Release；
6. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| Shader 校验（可选） | `python tools/check_agsl.py`（需要 skia-python） |
