# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-09
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **14.9 / 84**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | `com.abradant.iossettings` |
| 应用名 | iOS 设置布局 |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 已发布包 | `dist/IosSettingsLayout-14.8.apk`（上一版，仍是 Release 用的包） |
| 14.9 预览包 | `dist/IosSettingsLayout-14.9.apk`（**未发布、未上机验证**） |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（用户提供的 14.8 发布包） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da57965f604ae13a2f4fc967ffc6c09f14bbdef5b9329effca65dad0f4a` |
| 14.8 同源码重编（默认 `.keystore` 密钥） | 195,465 B | 每次构建不同（zip 时间戳） | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/IosSettingsLayout-14.9.apk`（本次预览，dex 134,592 B） | 199,561 B | `6010AAC2CED9A9375910EDEAAAA10168B4BFD2F022D6FAC84538AB3AEE12E40A` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |

> 重编同一个 commit 的 sha256 会变（zip 条目时间戳），上表记录的是仓库里这份 APK 的哈希。

⚠️ 14.8 发布包与源码重编**不等价于同一份二进制**，见「已知问题 1」。
⚠️ 14.9 预览包用默认测试密钥签名，与 14.8 发布包证书不同：**不能覆盖安装 14.8，要先卸载。**

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/abradanthawk-design/iLyayot |
| 默认分支 | `main` |
| Release | https://github.com/abradanthawk-design/iLyayot/releases/tag/v14.8 |
| 初始提交 | `173a302` |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`）
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）
- ✅ 二级页 24 个图标（`SubPageIcons`：23 条 key 映射 + 标题兜底）
- ✅ 底部原生搜索栏 + **Kyant 液态玻璃**（`BottomSearch` / `SearchGlass` / `SearchHost`）：
  抓窗口 → 10dp 模糊 → 圆角折射（AGSL）→ 45° 边缘高光 → 底部内阴影，裁在 50dp 胶囊内；
  shader 不可用时逐级退到「只有模糊 / 半透明胶囊」
- ✅ 卡片末行双层圆角修正（`CardFix` / `SubPageCards`）
- ✅ 顶栏按滚动出现的模糊（`ForceBlur` / `UiTweaks`）
- ✅ 无 Gradle 构建脚本可在本机复现（见 `docs/03-build.md`）

## 已知问题

1. **源码与 14.8 发布包不完全一致**
   用户提供的发布包 dex 为 123,864 B，14.8 源码重编为 130,044 B，14.9 为 134,592 B；发布包签名证书也不同。
   源码中仍留有后续版本才引入、但未被 `Hook` 调用的类（`GlassToggle`、`IntroCard`、`AboutIos`、`TitleFix`、`TitleOverlay`、`PrefsBridge`）以及 `BottomSearch` 里指向 lattice 的反射分支。
   → 影响：按源码重编的包与发布包行为可能有细微差异；升级会因签名不同失败。详见 `docs/06-pitfalls.md`、`docs/09-roadmap.md`。

2. **14.9 的液态玻璃未上机验证**
   只做了构建、APK 四项校验，以及用 Skia 的 SkSL 编译器离线编译 AGSL（`javac` 不校验 shader）。
   真机观感、120Hz 帧率、深色模式、与原生 stub 9-patch 的边界对齐都还没有证据。

3. **`docs/07-changelog.md` 的「7. 校验」节数据过期**
   原文写 size 216,015 B，是后续版本的产物；14.8 实际为 195,465 B。已在该文件顶部标注。

4. **无 CI**：构建依赖本机 JDK/build-tools，尚未接 GitHub Actions。

5. **平板/分屏未适配**，仅手机验证。

## 下一步（优先级从高到低）

1. 真机装 `dist/IosSettingsLayout-14.9.apk`，过滤 `IosSettings` 日志确认
   `liquid glass shader compiled` + `liquid glass active`，并看 `search glass capture fps=`；
2. 观感确认后再把 14.9 放进 `dist/`、更新 Release 与 README 下载链接；
3. 用固定 keystore 重签并统一发布包与源码，解决「同版本不同二进制」；
4. 清理 `Hook` 未引用的遗留类，或明确标注为「实验代码」；
5. 接 GitHub Actions：tag 触发构建 + 自动生成 Release；
6. 参数 GUI（`Prefs` 里 `glass_blur` / `rim_alpha` / `rim_width` / `refraction_*` 已就位）。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| Shell | Windows PowerShell 5.1，需 `-ExecutionPolicy Bypass` |
| Shader 校验（可选） | `python -m pip install skia-python`，用 `RuntimeEffect.MakeForShader` 编译 AGSL |
