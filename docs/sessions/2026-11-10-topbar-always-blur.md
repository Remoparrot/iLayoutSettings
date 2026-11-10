# 顶栏始终模糊 + 发丝线跟随栏出现（2026-11-10）

状态：已完成
关联版本：16.4 → 16.5（versionCode 95 → 96）
关联提交：发版提交（`tools/release.ps1 -Version 16.5` 自动生成）
参与者：AbradantHawk / DeepSeek Harness

## 1. TL;DR

用户发来一张截图：顶栏（收起状态）是纯黑一块，小米账户卡片滑到栏底下时被硬切，看不到模糊，
要求「把顶栏改成始终模糊」，随后补充「分割线也要随着顶栏、模糊一起出现」。
根因是模糊半径只在 `scrolled` 时为非 0、静止时栏底色被写成不透明实色；现在底色和模糊都常开，
并清掉 MIUI 给滚动容器的 `clipBounds`，折叠过程中卡片被模糊而不是被切；发丝线改成
「列表滚动或栏已收缩」就出现。16.5 / 96 已上机验证并发布。

## 2. 对话重点

- **用户要求（原话）**：
  - 附图 + 「经过小米账户是没有模糊，把顶栏改成始终模糊吧」
  - 「顶栏的分割线也要随着顶栏、模糊一起出现」
- **做出的决策**：
  - 不再依赖 MIUI 自己的 over-bg（`mOverBgView`）来出模糊——它只在头部完全收起后才出现，
    而且盖住大标题时会误伤；改为常开 action bar 自己的 `MiuiBlurUtils.setBackgroundBlur`。
  - 底色不再区分「静止实色 / 滚动半透明」，统一用 `topbar_alpha`。
  - 发丝线不直接「常显」（静止时线会横在大标题和账户卡之间，很怪），而是跟着栏的折叠状态出现。
- **范围外但被提到的事**：
  - 用户截图的时刻是「头部折叠动画中」，这个状态在静止时不可达，验证时必须慢拖抓帧；
  - 用户没有要求改默认值（模糊 5% / 透明度 2% 保持不变）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../UiTweaks.java` | `refreshTopBar()`：`radius` 去掉 `scrolled ? … : 0`；底色去掉 `solid` 分支，恒为 `(alpha<<24)\|base`；`setBackgroundBlur` 改传 `radius * density` 的像素；底色变化时重发一次模糊；新增 `unclip()`（清 `mScrollableView` / `scroll_headers` 的 clipBounds）与 `isShown()` 守卫；发丝线条件改为 `scrolled \|\| condensed`（`OPEN_BAR_HEIGHT` 记最大栏高） | 见「设计思路」 |
| `module/src/.../UiTweaks.java` | 新增 `NestedHeaderLayout.updateOverBgState` 的 after 钩子，只做 `unclip()` | MIUI 在这个方法里给滚动容器设 clip，必须在它之后立刻清掉 |
| `module/AndroidManifest.xml` | 16.4 / 95 → **16.5 / 96** | 发版 |
| `tools/release.ps1` | Release 正文模板换成 16.5 内容 | 自动发版要带本版说明 |
| `docs/00-status.md`、`02-architecture.md`、`06-pitfalls.md`、`07-changelog.md`、本文件、`sessions/README.md` | 同步现状 / 架构 / 坑 / 版本记录 | 项目规定：每次会话结束更新 `00-status.md` |

## 4. 设计思路

- **问题本质**：不是「模糊强度不够」，而是**糊的那一层在折叠阶段根本不存在**——
  旧的 `radius = scrolled ? pref : 0` 把「滚动」当成「栏可见」的代理，但折叠动画里列表 offset 还是 0；
  同时静止时栏底色是 `0xFF000000`，内容滑到它下面只会被盖住。
- **方案对比**：
  - A：强制 MIUI 的 over-bg 常显（`mIsShowOverBg = true` / `overBg.setVisibility(VISIBLE)`）。
    —— 优点：完全走原生 blur 面，颜色/混合由 MIUI 管；缺点：over-bg 在展开态会盖住大标题
    （它从 `header.getTop()` 到栏底，正是大标题的区域），而且它的显示/隐藏有内部状态机，
    每帧硬写会和动画打架。
  - B（采用）：让 `action_bar_container` 自己始终带透明底色 + `setBackgroundBlur`，
    折叠时内容直接糊在栏底下。—— 优点：栏是 overlay 的最上层，天然盖住滑动内容，
    半径随便调；缺点：`setBackgroundBlur` 收像素、且 `setBackgroundColor` 会盖掉模糊，要自己维护。
  - C：模块自绘一个模糊浮层（像 `SearchGlass` 那样抓图 + RenderEffect）。
    —— 优点：完全可控；缺点：对一个「本来就有原生 API」的栏来说太重，抓图开销和时序都难保证。
- **最终选择与取舍**：选 B，并把 `unclip()` 一起做掉——否则透明栏底下什么都没有，还是硬切。
  牺牲的是：栏的底色/模糊不再借用主题的 `Blur.ExtraHeavy` token，全靠模块下发的两个 pref；
  另外常开模糊比「只在滚动时」多耗一点 GPU（静态帧有缓存，实测无明显掉帧）。

## 5. 踩过的坑

### 坑：`setBackgroundBlur` 的半径单位是像素，不是 dp

- **现象**：调好滑杆后模糊肉眼几乎不变，和 `mBlurEffect` 的 8dp 对不上。
- **根因**：`MiuiBlurUtils.setBackgroundBlur(View, int)` 内部 `if (radius > 400) radius = 400`，
  是 px；`MiuiBlurUiHelper.refreshBlur()` 里也是 `mBlurEffect * density + 0.5` 后才下发。
  旧代码直接传 dp（5% ≈ 7.5），等于按 7.5px 生效。
- **解法**：按 `density` 换算后再传，`setBackgroundBlurRadius` 用同一个 px 值。
- **如何避免**：看到 miuix 模糊相关 API 先反编译看一眼单位（`dexdump -d` + `findstr`）。

### 坑：改了栏底色，模糊就没了

- **现象**：主题切换（底色变化）后，栏变得「干净透明」但没有模糊。
- **根因**：`setBackgroundColor()` 换掉 view 的背景层，此前挂上的 blur 一起被盖掉；
  而半径缓存的 key 只比较半径，底色变了不会重发。
- **解法**：用 `tintChanged` 触发一次 `setBackgroundBlur` 重发。
- **如何避免**：任何「一次性 set 上去的视觉效果」都要想清楚哪些操作会把它覆盖掉。

### 坑：`clipBounds` 让透明栏底下什么也没有

- **现象**：栏已经全透明了，但账户卡还是被切在栏底边，一点模糊都看不到。
- **根因**：MIUI 的 `updateOverBgState()` 在 over-bg 隐藏时给滚动容器
  `setClipBounds(0, overBgH - listTop, w, h)`，内容根本画不到栏底下；透明栏后面只有页面背景。
- **解法**：`refreshTopBar()` 与 `updateOverBgState()` 的 after 钩子里都清 `clipBounds`
  （`mScrollableView` 和 `scroll_headers` 两处都清）。
- **如何避免**：「透明了但看不见内容」优先怀疑裁剪，而不是模糊没生效。

### 坑：慢拖 / 抓帧——验证折叠动画比改代码难

- **现象**：`input swipe` 有时完全不动；`screencap` 抓不到折叠中间帧（前几次全是黑图）。
- **根因**：`input swipe`（不带 `touchscreen`）注入的事件偶尔被当成「唤醒」；另外
  `input swipe` 的慢速拖动在这台 ROM 上不会驱动 nested header（SpringBackLayout 吃掉了）；
  黑图是抓帧时设置正在冷启动 / 屏幕刚点亮。
- **解法**：用 `input touchscreen swipe ... 4000`（4 秒慢拖）把折叠过程拉长到几秒，
  中途 `screencap` 就能拍到「卡片正滑进栏底」的那一帧；也可以用
  `settings put global animator_duration_scale 8` 拉长动画（记得改回 1）。
- **如何避免**：验证动画态优先「放慢动作」，不要跟 200ms 的弹簧抢帧。

## 6. 验证与证据

```powershell
# 构建
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk D:\Android\jdk21\jdk-21.0.2 -BuildTools D:\Android\Sdk\build-tools\37.0.0 `
  -AndroidJar D:\Android\Sdk\platforms\android-37.0\android.jar

# 安装 + 冷启动
adb install -r dist\iLayoutSettings-16.5.apk
adb shell am force-stop com.android.settings
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.android.settings/.MiuiSettings

# 抓「折叠中」的帧
adb shell input touchscreen swipe 640 1500 640 1200 4000   # 4s 慢拖
adb shell screencap -p /sdcard/mid.png
```

- 结果：`bin` 六步全过，`size 286069 bytes`，`sha256 8449352927AD053BD49FDDF3505AADE4532F90933E680CDF94AE72257854B566`。
- 产物：`dist/iLayoutSettings-16.5.apk`（286,069 B），已覆盖安装到 25053RT47C。
- 像素证据（深色）：滚动时 `y=330` 发丝线 `(58,58,59)`、栏底 `(10,10,11)`、卡片 `(28,28,30)`；
  静止 `y=495–510` 全 `(0,0,0)`（无线）。浅色：滚动 `y=330` `(222,222,223)`、栏底 `(253,253,254)`、
  下方 `(255,255,255)`；静止 `(243,242,247)`。
- 折叠动画帧：栏内能看到账户卡被模糊的头像/文字（截图 `docs/topbar-always-blur-16.5.png`，
  对照图 `docs/topbar-always-blur-16.5-before-after.png`）。
- 日志：`XLog` 的 TRACE 探针已在发版前移除；本版没有新增日志。
- **未验证的部分**：
  - 搜索模式下的栏高变化（`OPEN_BAR_HEIGHT` 记忆的最大值可能被搜索模式的矮栏污染）；
  - 第三方主题 / 平板横屏；
  - 「常开模糊」的耗电影响（只做了主观流畅度观察）。

## 7. 未完成 / 下一步 / 风险

- 未完成：搜索模式、第三方主题的复测。
- 建议下一步（按顺序）：
  1. 进搜索模式看发丝线会不会因为栏变矮而误显示；必要时把条件收紧成「折叠进度 > 阈值」；
  2. 观察第三方主题（尤其自带 action bar 背景的）在常开底色下会不会出现双层底色；
  3. 平板 / 分屏下的栏高基准值。
- 风险：清 `clipBounds` 依赖 `updateOverBgState()` 仍被调用；ROM 若换地方设 clip，
  内容会重新被裁在栏底（表现回到「没有模糊」）。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（`02-architecture.md` / `06-pitfalls.md`）
- [x] 若发版：`docs/07-changelog.md` 顶部已加一节
