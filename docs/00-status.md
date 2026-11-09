# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-09
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **14.8 / 83**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | `com.abradant.iossettings` |
| 应用名 | iOS 设置布局 |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 发布包 | `dist/IosSettingsLayout-14.8.apk` |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（用户提供的 14.8 发布包） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da57965f604ae13a2f4fc967ffc6c09f14bbdef5b9329effca65dad0f4a` |
| 同源码重编（默认测试密钥，本次验证） | 195,465 B | `DF4A0AAD5F506BBBC63E7A1BD2BB8B74B81F0CEA9A88C84F9DE9915ED894BC69` | `42fba9afb1658fb695a319d022660f970765feec1813e8bc0608839affd04ac8` |

⚠️ 两者**不等价于同一份二进制**，见「已知问题 1」。

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`）
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）
- ✅ 二级页 26 个图标（`SubPageIcons` + `tools/make_subpage_icons.py`）
- ✅ 底部原生搜索栏 + 玻璃/模糊层（`BottomSearch` / `SearchGlass` / `SearchHost`）
- ✅ 卡片末行双层圆角修正（`CardFix` / `SubPageCards`）
- ✅ 顶栏按滚动出现的模糊（`ForceBlur` / `UiTweaks`）
- ✅ 无 Gradle 构建脚本可在本机复现（见 `docs/03-build.md`）

## 已知问题

1. **源码与 14.8 发布包不完全一致**
   用户提供的发布包 dex 为 123,864 B，本仓库源码重编为 130,044 B；签名证书也不同。
   源码中仍留有后续版本才引入、但未被 `Hook` 调用的类（`GlassToggle`、`IntroCard`、`AboutIos`、`TitleFix`、`TitleOverlay`、`PrefsBridge`）以及 `BottomSearch` 里指向 lattice 的反射分支。
   → 影响：按源码重编的包与发布包行为可能有细微差异；升级会因签名不同失败。详见 `docs/06-pitfalls.md`、`docs/09-roadmap.md`。

2. **`docs/07-changelog.md` 的「7. 校验」节数据过期**
   原文写 size 216,015 B，是后续版本的产物；14.8 实际为 195,465 B。已在该文件顶部标注。

3. **无 CI**：构建依赖本机 JDK/build-tools，尚未接 GitHub Actions。

4. **平板/分屏未适配**，仅手机验证。

5. **未做真机回归**：本次会话只验证了构建与 APK 结构，没有安装到设备跑一遍。

## 下一步（优先级从高到低）

1. 用固定 keystore 重签并发布一次，确认与用户手上的 14.8 能否覆盖安装；
2. 清理 `Hook` 未引用的遗留类，或明确标注为「实验代码」；
3. 把 15.x–17.x 实验分支的功能（GUI 参数、液态玻璃）作为独立分支/PR 管理，不混进主线；
4. 接 GitHub Actions：tag 触发构建 + 自动生成 Release；
5. 补一次真机验证清单（首页顺序、开关、图标、搜索栏、深色模式、主题切换）。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`） |
| Shell | Windows PowerShell 5.1，需 `-ExecutionPolicy Bypass` |
