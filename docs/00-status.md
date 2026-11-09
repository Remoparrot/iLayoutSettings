# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-09
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **15.0 / 85**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | `com.abradant.iossettings` |
| 应用名 | **iLyayot**（与 GitHub 仓库名一致） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 已发布包 | `dist/IosSettingsLayout-14.8.apk`（上一版 Release） |
| 15.0 预览包 | `dist/IosSettingsLayout-15.0.apk`（**未发布，已上机**：2026-11-09 23:29，25053RT47C / HyperOS 3） |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（用户提供的 14.8 发布包） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da57965f604ae13a2f4fc967ffc6c09f14bbdef5b9329effca65dad0f4a` |
| 14.8 同源码重编（默认 `.keystore` 密钥） | 195,465 B | 每次构建不同（zip 时间戳） | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/IosSettingsLayout-14.9.apk`（液态玻璃预览） | 199,561 B | `C60DA20577DEAC40884630A6EE9091D20F79989779D342F6BDF47A88AA71B05B` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/IosSettingsLayout-15.0.apk`（本次，应用名 iLyayot） | **203,657 B** | **`3A80A921ACD695D2852346FB3F5AB9B7A5A4FDDFBBF17AE86ED7C0E8892E2A20`** | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |

> 重编同一个 commit 的 sha256 会变（zip 条目时间戳），上表记录的是仓库里这份 APK 的哈希。

⚠️ 15.0 与 14.8/14.9 使用同一测试密钥签名，可直接覆盖安装。
⚠️ 15.0 的最终两个参数（玻璃遮罩阈值 72、GlassToggle 右间距 2dp）只做了日志验证：装机后设备进入防误触模式、屏幕亮度接近 0，未能补到最终截图。

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
  - 未装 HyperiOS：原生 `SlidingSwitch` + 居中补偿；
  - 装了 HyperiOS：改用自有 `GlassToggle` 加到行根，绕开 HyperiOS 的 `SlidingSwitch.onDraw` 接管，开关不再下沉
- ✅ 二级页 24 个图标（`SubPageIcons`：23 条 key 映射 + 标题兜底）
- ✅ 底部原生搜索栏 + **Kyant 液态玻璃**（`BottomSearch` / `SearchGlass` / `SearchHost`）：
  抓窗口 → **1dp 模糊** → 圆角折射（**24dp 厚度 / 30dp 强度**）→ 45° 边缘高光 → 底部内阴影；
  遮罩取「把原生胶囊 drawable 画成位图后的实际像素边界」，原生 fill 保留为 0x80 的染色层；
  抓图在 `OnPreDraw` 里做，**只有位图变化才重绘**（静止时不重画，`uiautomator` 能 idle）；
  shader 不可用时逐级退到「只有模糊 / 半透明胶囊」
- ✅ 卡片末行双层圆角修正（`CardFix` / `SubPageCards`）
- ✅ 顶栏按滚动出现的模糊（`ForceBlur` / `UiTweaks`）
- ✅ 无 Gradle 构建脚本可在本机复现（见 `docs/03-build.md`）

## 已知问题

1. **源码与 14.8 发布包不完全一致**
   用户提供的发布包 dex 为 123,864 B，14.8 源码重编为 130,044 B，14.9 为 134,592 B，15.0 为 147,204 B；发布包签名证书也不同。
   源码中仍留有未被 `Hook` 调用的实验类（`IntroCard`、`AboutIos`、`TitleFix`、`TitleOverlay`、`PrefsBridge`）以及 `BottomSearch` 里指向 lattice 的反射分支。
   （`GlassToggle` 从 15.0 起已在 HyperiOS 路径中启用。）
   → 影响：按源码重编的包与发布包行为可能有细微差异；14.8 升级会因签名不同失败，15.0 与 14.8/14.9 同签名可直接覆盖。
   详见 `docs/06-pitfalls.md`、`docs/09-roadmap.md`。

2. **15.0 的液态玻璃与开关已上机（2026-11-09 23:29）**
   真机 25053RT47C / HyperOS 3 / Android 16 实测：`liquid glass shader compiled` + `liquid glass active`；
   `airplane glass toggle added to row (HyperiOS-safe)`；`search glass mask=[17,1][1137,139]`（屏幕 x=81..1201，
   覆盖原生可见 fill），`native fill alpha=0x80`；开关旋钮中心 Y=944.5，标题中心 945。
   **未完成**：最终遮罩阈值 72 与右间距 2dp 的黑/浅色截图（装机后设备进入防误触模式、屏幕黑屏）；
   120Hz 慢动作、分屏/平板、深浅色切换动画的中间帧未测；`gfxinfo` 未取。

3. **`docs/07-changelog.md` 的「7. 校验」节数据过期**
   原文写 size 216,015 B，是后续版本的产物；14.8 实际为 195,465 B。已在该文件顶部标注。

4. **无 CI**：构建依赖本机 JDK/build-tools，尚未接 GitHub Actions。

5. **平板/分屏未适配**，仅手机验证。

## 下一步（优先级从高到低）

1. 设备屏幕可用后补拍 15.0 深/浅色截图，重点看搜索栏左侧 4dp 光晕与 GlassToggle 右边缘是否对齐箭头（1176px）；
2. 观感确认后再把 15.0 放进 Release、更新 README 下载链接；
3. 观察 HyperiOS + 15.0 同时开启时的耗电/滚动，确认 `GlassToggle` 的抓图不会拖慢首页；
4. 用固定 keystore 重签并统一发布包与源码，解决「同版本不同二进制」；
5. 清理 `Hook` 未引用的遗留类，或明确标注为「实验代码」；
6. 接 GitHub Actions：tag 触发构建 + 自动生成 Release；
7. 参数 GUI（`Prefs` 里 `glass_blur` / `rim_alpha` / `rim_width` / `refraction_*` 已就位）；
8. 抓图若仍嫌慢：`CAPTURE_SCALE` 0.6 → 0.45 先试，再考虑硬件 `RecordingCanvas`（但会失去变化检测）。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| Shell | Windows PowerShell 5.1，需 `-ExecutionPolicy Bypass` |
| Shader 校验（可选） | `python -m pip install skia-python`，用 `RuntimeEffect.MakeForShader` 编译 AGSL |
