# 搜索栏 iOS 27 液态玻璃 + 小爱麦克风（2026-11-10）

状态：已完成
关联版本：16.5 → 16.6（versionCode 96 → 97）
关联提交：<本次提交 sha，发版后回填>
参与者：AbradantHawk / DeepSeek Harness

## 1. TL;DR

底部搜索栏不再叠 MIUI 的 `miuix_appcompat_search_mode_edit_text_bg_light/dark` 9-patch，改成模块自绘的 Kyant 液态玻璃：G2 连续曲率胶囊、上下等亮光边、无内阴影；搜索/麦克风换成用户给的 iOS 27 图标（随深浅色自动变色、缩小并向内收），点麦克风走电源键长按同一条 `VoiceService` 入口唤醒小爱。GUI 的「高级设置」总开关删除，顶栏选项常显。16.6 / 97 已上机验证并发布。

## 2. 对话重点

- **用户要求（原话要点）**：
  - 「高级设置的开关删除」
  - 「搜索栏不叠加 `miuix_appcompat_search_mode_edit_text_bg_light/dark.9`，用 GitHub @kyant0 的 iOS27 液态玻璃，要光边」
  - 「搜索图标和麦克风图标用 `D:\下载\下载.zip` 里面两个图标，自动变化深浅色，位置要在和图中一样位置」
  - 「点击麦克风还要可以像电源键唤醒小爱同学一样唤醒小爱同学」
  - 「搜索栏玻璃的圆角也要用 kyant0 的类 iOS G2 连续曲线 GitHub 库」
  - 迭代反馈：「搜索栏不要阴影，光边的角度要为 0，就像图中的上下才有」→「上下的光边要一样，深色时高光太亮了，要像图中这样」→「搜索和麦克风的图标都小一点，都靠内一点」→「高光亮一点」
  - 「搞完后自动同步 github」（原话里「自动图标 GitHub 和 Releases」是口误）
- **决策**：
  - 保留原生 `miuix_appcompat_search_stub` 作为布局/点击外壳，只把它的 9-patch 背景去掉；不整栏自绘，避免丢掉 MIUI 的搜索 ActionMode 时序。
  - 光边画进 AGSL（用 SDF 法线的垂直分量），不再用 Java 描边：这样只亮上下、两端自动淡出，且与折射带同一个 SDF 对齐。
  - 删除内阴影（用户明确不要），底色改成上下一致，避免「同一个加法高光在更暗的下半边显得更亮」。
  - 小爱入口用 `com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST` 启动 `com.miui.voiceassist/.VoiceService`；实测与电源键长按出现同一个 `voice_assist_root` 悬浮窗。
- **范围外但被提到的事**：
  - 用户只要求同步 GitHub，没有要求改 README 之外的内容；本次仍按仓库规范补了会话记录与专题文档。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../SearchGlass.java` | 去掉 9-patch 依赖：新增 `setPillVisibleRect()` + `paintedBounds()`，在首次 layout 后量出旧 9-patch 的实际可见矩形；轮廓改为 Kyant Shapes 1.2.1 的 G2 连续曲率胶囊（`G2_CAPSULE` 控制点 + `buildG2Capsule`）；AGSL 加 `tintColor/tintTop/tintBottom` 与 `rimColor/rimWidth/rimAlpha/rimFalloff`，光边只取 `abs(grad.y)`；删除内阴影、删除 lens-only 备用 shader、删除 Java 描边 | 用户要求不叠加 9-patch、G2 圆角、上下光边、无阴影；用 shader 保证光边贴合 SDF |
| `module/src/.../BottomSearch.java` | 保存并清掉 `inputArea` 的 9-patch（`pickNativePill` 优先 `android:id/inputArea`），`OnPreDraw` 一次性量可见矩形；替换搜索图标（14dp、21/9dp padding、CENTER_INSIDE）、清掉 TextView 的 compound drawable、加 12×16dp 麦克风（右距 36dp）并绑 `wakeXiaoAi()`；新增 `wakeXiaoAi`/`styleGlassContent`/`firstImageView`，以及 `XIAOAI_*` 常量 | 图标来自 zip；麦克风要独立可点；小爱走 VoiceService |
| `module/src/.../Prefs.java` | 删除 `ADVANCED`/`DEFAULT_ADVANCED`/`advanced()`；`DEFAULT_RIM_ALPHA=0.20`、`DEFAULT_RIM_WIDTH=1.0` | GUI 不再有高级开关；光边默认值按真机反馈调 |
| `module/src/.../MainActivity.java` | 删除「高级设置」开关与 `if (advanced)` 包裹，顶栏卡片常显；说明文案去掉「滚动后」 | 需求：删除开关、选项直接可见 |
| `module/AndroidManifest.xml` | 16.5/96 → **16.6/97**；xposeddescription 加搜索栏描述 | 发版 |
| `module/res/drawable-xxhdpi/ic_search_glass.png`、`ic_mic_glass.png` | 用户提供 `下载.zip` 的两个 PNG 原样入库 | 指定图标 |
| `tools/release.ps1` | Release 正文换 16.6 内容；保持 UTF-8 **带 BOM** | 自动发版；PS5.1 编码坑 |
| `docs/00-status.md`、`01-overview.md`、`02-architecture.md`、`05-design-notes.md`、`06-pitfalls.md`、`07-changelog.md`、`08-troubleshooting.md`、`README.md`、`sessions/README.md`、本文件 | 同步现状/架构/设计/坑/版本 | 项目规定 |

## 4. 设计思路

- **问题本质**：15.3 以来的玻璃只是「在 MIUI 胶囊上叠模糊/折射」，可见表面其实是主题 9-patch；用户要的是「胶囊本身也是玻璃」。一旦拿掉 9-patch，玻璃就必须自己负责底色、边缘和光边，否则栏会变成透明洞或纯黑条。
- **方案对比**：
  - A：整栏自绘（不用 stub）。优点：布局完全可控；缺点：丢掉 MIUI `SearchActionMode` 的入口/动画时序，之前 15.9 就因此「没有搜索栏」。
  - B（采用）：保留 stub，只清背景；玻璃仍是 stub 的子 view，图标/文字沿用原生布局，点击照旧转发 `mAnchorView.performClick()`。
  - C：光边用 Java 沿路径描边。优点：实现简单；缺点：只靠线性渐变无法做到「两端不亮、上下等亮」，而且描边和折射各用一套几何，边角会错位。最终把光边折进 AGSL，用 SDF 法线选边。
- **G2 圆角**：Kyant Shapes 的 `ContinuousCurvatureRoundedRectangleCornerBuilder` 对胶囊（宽 ≥ 2×高）永远走 `tH=1, tV=0` 分支，控制点是常量，所以离线算出 20 个控制点后硬编码（`G2_CAPSULE`），Java 里只做三次贝塞尔，不在手机上解三次/四次方程。折射 SDF 仍是圆角矩形：胶囊下两者只差 <1px，但轮廓/光边必须是 G2。
- **光边参数**：第一版 Java 描边是 45° 渐变 + 内阴影，被否；第二版 shader 光边顶部 1.0、底部 0.55，用户说上下要一样且深色太亮；第三版改成上下等权、底色改成统一 alpha（去掉上下渐变），这样两端的加法高光在同一个底色上看起来一致；`rimAlpha` 从 0.55 → 0.26 → 0.14 → 0.20，最终 0.20（顶部/底部实测峰值 `#63`/`#62`）。
- **小爱**：试过 `KEYCODE_VOICE_ASSIST`（会打开「默认助手」设置页）、`ACTION_ASSIST`（弹 Resolver）、`FloatActivity`（未导出）。最后读 manifest 找到导出的 `VoiceService` 与 `ACTION_VOICE_TRIGGER_START_VOICEASSIST`，`startForegroundService` 后 4–5 秒出现 `voice_assist_root`，与电源键长按同一个悬浮窗。冷启动慢是预期（语音服务要拉模型/进程），点击时给了触感 + 缩放反馈。
- **取舍**：不引入 Kyant 的 Compose/Shapes 依赖（模块是无 Gradle 的纯 Java + AGSL），只移植数学；玻璃仍抓窗口位图，成本与 16.5 相同。

## 5. 踩过的坑

### 坑：9-patch 的可见矩形在 install 时量不到

- **现象**：`paintedBackgroundBounds(pill)` 返回 null，玻璃按整个 44dp `inputArea` 画，比原来的胶囊高出一截，图标位置也跟着变。
- **根因**：`BottomSearch.install()` 发生在首帧 layout 之前，`pill.getWidth()==0`，渲染背景的探针直接 bail。
- **解法**：保存 drawable 引用、先 `setBackground(null)`，在 `OnPreDraw` 第一次回调（layout 完成、尚未绘制）里用保存的 drawable 按真实尺寸渲染并量像素，`setPillVisibleRect()` 后 `invalidate()`。
- **如何避免**：任何「按 view 尺寸算像素」的逻辑，都不要在 install/onCreate 里做。

### 坑：给 ImageView 换 drawable 会把布局撑大

- **现象**：换成模块里的 176px PNG 后，搜索图标变成 44dp 大图，把「搜索」文字推到 103dp。
- **根因**：stub 的 ImageView 是 `wrap_content`，新 drawable 的固有尺寸是 58dp（`drawable-xxhdpi` + 520dpi），view 跟着变成 58+padding=84dp；`CENTER_INSIDE` 的内容框也跟着变大，图标不再缩小。
- **解法**：把 ImageView 的 LayoutParams 固定回 44×24dp，再用 padding 做出 14dp 的内容框；不要依赖 `wrap_content`。
- **如何避免**：替换系统布局里的 drawable 时，先确认目标 view 的 `layout_width/height` 是不是 `wrap_content`。

### 坑：底色渐变会让「上下一样亮」的光边看起来不一样

- **现象**：同一套 `rimAlpha`，顶部峰值 `#45`、底部 `#69`，用户仍觉得上下不一致。
- **根因**：底色是 0.16→0.07 的白色渐变，底边基色更暗；加法高光的绝对增量相同，但对比度更高，肉眼觉得底部更亮。
- **解法**：底色改成统一 alpha（0.10），光边上下同权，实测峰值 `#63`/`#62`（差 1/255）。
- **如何避免**：做「等亮」效果时，先统一基色，再谈高光权重。

### 坑：`VoiceService` 冷启动 4–5 秒，像没点中

- **现象**：第一次点麦克风后 3 秒内没有任何窗口变化，容易以为失败。
- **根因**：`com.miui.voiceassist` 进程/模型冷启动，`voice_assist_root` 要等服务起来才 addView。
- **解法**：点击处加 `KEYBOARD_TAP` 触感和 0.86→1.0 的回弹动画，给即时反馈；不改等待时长（服务端行为）。
- **如何避免**：涉及第三方语音助手时，先用 `dumpsys window | grep voice_assist_root` 确认是「慢」而不是「没触发」。

### 坑：用户给的图标是紧裁的，边缘会被切

- **现象**：`search.png` 的放大镜手柄、`mic.png` 的底座横条都贴着画布边缘，缩小后仍能看到切边。
- **根因**：素材本身是从大图里按图形 bbox 裁出来的，没有留白。
- **解法**：按原样使用（用户指定），只在 ImageView 里用 `FIT_CENTER/CENTER_INSIDE` 控制大小；不补画/不镜像。
- **如何避免**：以后要图标时先确认是否需要留白；本次在记录里写明素材限制。

## 6. 验证与证据

```powershell
# 构建
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk D:\Android\jdk21\jdk-21.0.2 -BuildTools D:\Android\Sdk\build-tools\37.0.0 `
  -AndroidJar D:\Android\Sdk\platforms\android-37.0\android.jar

# 安装 + 冷启动
adb install -r dist\iLayoutSettings-16.6.apk
adb shell am force-stop com.android.settings
adb shell am start -n com.android.settings/.MiuiSettings
```

- 构建：`bin` 六步全过，`size 294434 bytes`，
  `sha256 48BF99273F9A9CB2A1C9C75718249FEEDBE7933FBE68AEA58B7FD231FEE81FEA`。
- badging：`versionCode='97' versionName='16.6'`、`xposed_init : True`、`module class: True`、
  `reorder : True`、apksigner `Verified`（证书 SHA-256 `941e18c5…95248c` 不变）。
- 深色玻璃（截图 `docs/assets/search-glass-16.6-dark.png`）：x=640 竖扫，栏内 `#333334`，
  上边缘峰值 `#63`、下边缘峰值 `#62`（上下一致），两端不亮；栏外 `#1C1C1E`。
- 浅色玻璃（`docs/assets/search-glass-16.6-light.png`）：胶囊 `#F3F3F5` 左右，白底上可辨；
  图标/文字为深色。
- 图标位置（uiautomator）：搜索 ImageView `(64,2559)-(204,2636)`，麦克风 `(1101,2570)-(1139,2621)`；
  搜索字形中心 ≈46.9dp、麦克风中心 ≈344.6dp（栏可见矩形 19.7–374dp，左右基本对称）。
- 麦克风：`input tap 1120 2595` 后 `mCurrentFocus` 从设置变为 `voice_assist_root`（冷启动约 4.8s），
  日志 `xiaoai: VoiceService start requested`。
- 搜索栏：`input tap 600 2596` 后 uiautomator 中出现 `search_panel`，MIUI 搜索框 + 键盘正常。
- GUI（截图 `docs/assets/gui-16.6.png`）：无「高级设置」卡片，搜索栏玻璃/顶栏两组滑杆直接可见。
- **未验证**：第三方主题、平板/横屏、浅色下第三方彩色壁纸的光边观感；小爱服务在后续 MIUI 版本是否改名。

## 7. 未完成 / 下一步 / 风险

- 未完成：浅色模式高光在纯白页面上的可见度仍低（白上白），后续可考虑浅色专用 1px 灰描边，但要先确认不违背「不要阴影」。
- 下一步顺序：
  1. 观察第三方主题/彩色壁纸下的折射与光边；
  2. 如果小爱服务升级改名，`wakeXiaoAi` 的 `ComponentName` 需要回退到 `ACTION_ASSIST` 或新入口；
  3. 考虑把 `rimAlpha` 暴露成 GUI 滑杆（本次为了少改 GUI 没做）。
- 风险：`VoiceService` 是 MIUI 私有导出服务，ROM 升级可能加权限/改 action；G2 控制点只对「宽 ≥ 2×高」的胶囊成立，其他形状会退化为普通圆角；折射 SDF 仍是圆角矩形，极端圆角下与 G2 轮廓有 <1px 偏差。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（`02-architecture.md` / `05-design-notes.md` / `06-pitfalls.md` / `08-troubleshooting.md`）
- [x] `docs/07-changelog.md` 顶部已加一节
