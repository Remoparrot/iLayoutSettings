# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **15.5 / 90**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | `com.abradant.iossettings` |
| 应用名 | **iLyayot**（与 GitHub 仓库名一致，桌面图标已恢复） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 已发布包 | `dist/IosSettingsLayout-15.5.apk`（**Release v15.5**，2026-11-10 发布；已上机：12:43，25053RT47C / HyperOS 3） |
| 旧发布包 | `dist/IosSettingsLayout-14.8.apk`（Release v14.8；证书不同，从它升级要先卸载） |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（旧发布包 v14.8） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da57965f604ae13a2f4fc967ffc6c09f14bbdef5b9329effca65dad0f4a` |
| `dist/IosSettingsLayout-15.0.apk` | 203,657 B | `3A80A921ACD695D2852346FB3F5AB9B7A5A4FDDFBBF17AE86ED7C0E8892E2A20` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/IosSettingsLayout-15.3.apk` | 207,753 B | `563AA37E88EC09A4D2052CB9B069DDC6312301C49967F57BD71DED2D04FB2840` | 同上 |
| `dist/IosSettingsLayout-15.4.apk` | 207,753 B | `6382711418C80596D362CE727CDBE567FC486AB3FAA88EF0F44136AA5C0A02F9` | 同上 |
| `dist/IosSettingsLayout-15.5.apk`（**Release v15.5 附件，与本地同一份**） | 207,753 B | `C8146210C872BB737C100FD57E598DCA73DD8F5C1134CE2AEF4C43CB6A05E535` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |

> 重编同一个 commit 的 sha256 会变（zip 条目时间戳），上表只记录仓库里这份 APK 的哈希。

⚠️ 15.x 全部使用同一测试密钥签名，可直接覆盖安装。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/abradanthawk-design/iLyayot |
| 默认分支 | `main` |
| Release（最新） | https://github.com/abradanthawk-design/iLyayot/releases/tag/v15.5 —— tag `v15.5` → `fc25b08`，附件 `IosSettingsLayout-15.5.apk` |
| 旧 Release | https://github.com/abradanthawk-design/iLyayot/releases/tag/v14.8（证书不同，仅留档） |
| 初始提交 | `173a302` |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`）
- ✅ 首页「专注模式」入口（`HeaderData`）：排在「屏幕时间」正上方，点击打开系统「勿扰模式」页
  （`android.settings.ZEN_MODE_SETTINGS`），图标复用设置自己的 `ic_do_not_disturb_mode_settings`
- ✅ 首页「相机」入口（`HeaderData`）：排在「外观」正下方，显式组件打开
  `com.android.camera/.CameraPreferenceActivity`，图标复用设置自己的 `ic_settings_camera`
- ✅ 首页「搜索」入口（`HeaderData`）：排在「墙纸」正下方，显式组件打开
  `com.android.quicksearchbox/.preferences.SearchSettingsPreferenceActivity`，图标复用设置自己的 `ic_search_24dp`
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）：
  - 一律使用 MIUI 原生 `SlidingSwitch`，**不再自绘**；
  - HyperiOS 的 hook 把开关画在 `widget_frame` 的坐标系里，且首帧缓存 box → 只平移 `widget_frame` 的 translation；
  - 图标优先取设置自己的 `ic_airplane_mode_settings`（主题可覆盖、深浅色自动重新解析），资源缺失时回退模块 `ic_plane_*`；
- ✅ 二级页 24 个图标（`SubPageIcons`：23 条 key 映射 + 标题兜底）
- ✅ 底部原生搜索栏 + **Kyant backdrop 2.0.1 玻璃**（`BottomSearch` / `SearchGlass` / `SearchHost`）：
  抓窗口 → 模糊 → 圆角折射（默认 **12dp 厚度 / 8dp 力度**）→ 裁到胶囊；
  **不叠加 shader 表面色**；原主题 `miuix_appcompat_search_mode_edit_text_bg_light/dark` 胶囊保留；
  浅色模式关闭内阴影和 wrapper elevation，深色保留内阴影；
  抓图在 `OnPreDraw` 里做，**只有位图变化才重绘**；shader 不可用时逐级退到「只有模糊 / 半透明胶囊」
- ✅ 应用内参数界面（`MainActivity`）：搜索栏模糊 / 折射力度 / 折射厚度、顶栏模糊，滑杆即改即生效（400ms 轮询）
- ✅ 跨进程参数：`PrefsProvider`（导出 ContentProvider）→ `ContentResolver.call()`，读不到时回退 XSharedPreferences
- ✅ 卡片末行双层圆角修正（`CardFix` / `SubPageCards`）
- ✅ 顶栏按滚动出现的模糊（`ForceBlur` / `UiTweaks`）：
  半径从 `topbar_blur` 实时读取；滚动后拖滑杆会重新套用，不再只在「滚/未滚」切换时更新；
  通过设置进程的 classloader 解析 `MiuiBlurUtils`，不再 `ClassNotFoundException`
- ✅ 无 Gradle 构建脚本可在本机复现（见 `docs/03-build.md`）

## 已知问题

1. **源码与 14.8 发布包不完全一致**
   用户提供的发布包 dex 为 123,864 B，14.8 源码重编 130,044 B，14.9 为 134,592 B，15.0 为 147,204 B。
   源码中仍留有未被 `Hook` 调用的实验类（`IntroCard`、`AboutIos`、`TitleFix`、`TitleOverlay`、`PrefsBridge`、`GlassToggle`）。
   详见 `docs/06-pitfalls.md`、`docs/09-roadmap.md`。

2. **15.4 / 15.5 只在默认主题 / 默认图标包上验证**
   `ic_airplane_mode_settings`、`ic_do_not_disturb_mode_settings`、`ic_settings_camera`、`ic_search_24dp`
   都已能随深浅色重新解析；第三方主题若把这几个资源换掉，需要在对应主题上再确认一次。
   顶栏模糊已用滑杆 0 → 300 实测（滚动后立即重新套用），但 120Hz 中间帧、分屏/平板仍未测。

3. **搜索栏的原生胶囊仍是上层可见面**
   15.3 按要求保留 `miuix_appcompat_search_mode_edit_text_bg_light/dark`，因此 shader 的去色只对胶囊下方/半透明部分生效；
   若后续想要纯玻璃观感，需要另行调整 `SearchGlass` 与 `widget_frame` 的层级，不能简单删掉主题背景。

4. **XSharedPreferences 在本机不可用**
   LSPosed 的镜像文件是 `-rw-rw----`、属主为模块 UID，设置进程 `readable=false`，`keys=[]`。
   所以参数必须走 `PrefsProvider`；`xposedsharedprefs` 元数据保留但不依赖。

5. **`docs/07-changelog.md` 的「7. 校验」节数据过期**（原文写 216,015 B，是后续版本产物，14.8 实际 195,465 B）。

6. **无 CI**：构建依赖本机 JDK/build-tools，尚未接 GitHub Actions。

## 下一步（优先级从高到低）

1. 接 GitHub Actions：tag 触发构建 + 自动生成 Release（现在 release 是本地构建后手动 curl 上传）；
2. 在第三方主题 / 自定义图标包上验证 `ic_airplane_mode_settings` 的自动切换；
3. 用固定 keystore 重签并统一发布包与源码；
4. 清理 `Hook` 未引用的遗留类（`GlassToggle` 已停用，可删）；
5. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| Shader 校验（可选） | `python tools/check_agsl.py`（需要 skia-python） |
