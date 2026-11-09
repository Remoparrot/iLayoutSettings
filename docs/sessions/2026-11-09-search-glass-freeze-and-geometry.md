# 搜索栏玻璃「定住」修复与几何对齐（2026-11-09）

状态：已完成
关联版本：14.9 (84)，版本号不变
关联提交：`fix(search): 玻璃不再定住、胶囊对齐 44dp 药丸、模糊默认 5dp`（与本文同一次提交）
参与者：用户 / DeepSeek Harness（AI）

## 1. TL;DR

14.9 的液态玻璃在真机上**只画一次就定住**：`SearchGlass` 挂在 `android.R.id.content` 上，不在滚动列表里，
列表滚动时没有任何东西弄脏它。改成「`OnPreDraw` 抓图 → 与前一张位图比对 → 变了才 `invalidate()`」后，
滚动时实测 `capture fps 108 / draw fps 102`，静止后完全停止重绘（`uiautomator dump` 能拿到 idle）。
同一轮把玻璃胶囊改成对齐原生的 `widget_frame` 药丸（44dp），模糊默认值从 10dp 降到 5dp。

## 2. 对话重点

- **用户要求（原话要点）**：「玻璃定住了，我连接手机了，你帮我看日志和调试直到修复」「顺便把玻璃的模糊调到 5，
  修复搜索栏玻璃和搜索栏宽度和长度不匹配的问题」。
- **做出的决策**：
  - 用**位图变化检测**驱动重绘，而不是无脑 `invalidate()`（后者会让窗口永远不出 idle）；
  - 遮罩（胶囊轮廓）取 stub 里的 `android.R.id.widget_frame`，不再用整个 `search_mode_stub`，也不再叠
    `6dp` 手调内缩；
  - 抓图仍然用软件 `Canvas` + `Bitmap`（因为要能 `sameAs` 比对），没有换成硬件 `RecordingCanvas`。
- **范围外但被提到的事**：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../SearchGlass.java` | `OnPreDraw` 里抓图后先 `backdropChanged()`，变过才 `invalidate()`；新增 `prevBackdrop` 与比对 | 玻璃不在滚动列表里，没人弄脏它；无条件 invalidate 又会让窗口永不 idle |
| `module/src/.../SearchGlass.java` | `computeMask()` 去掉 `sideInset = dp(6)`，遮罩直接用遮罩视图的 padding 内缩 | 6dp 是按「整条 50dp 包装层」调出来的补偿值，换成 44dp 药丸后会把玻璃缩窄 |
| `module/src/.../SearchGlass.java` | 新增 `mask=` / `backdrop` / `draw fps` 诊断日志；`DEBUG_BLUR_ONLY`、`DEBUG_PASS_THROUGH` 两个开关 | 真机定位「是没抓图、没画、还是画错」必须靠日志分段 |
| `module/src/.../BottomSearch.java` | `glass.setMaskView(null)` → `pickNativePill()`（`widget_frame`，兜底 `findBackgroundView`） | 原生药丸是 stub 里 44dp 的 `inputArea`，不是 56dp 的 stub 本身 |
| `module/src/.../BottomSearch.java` | 新增 `DEBUG_TREE` + `dumpTree()` | 打印 stub 树（类名/资源名/尺寸/背景）才能知道药丸到底是谁 |
| `module/src/.../Prefs.java` | `DEFAULT_GLASS_BLUR` 10f → **5f** | 用户要求 |

## 4. 设计思路

- **问题本质**：不是 shader 画错了，而是**没人触发重画**。`SearchGlass` 是 `content` 的子视图，列表滚动只弄脏
  `RecyclerView` 自己；`OnPreDraw` 里抓图更新了 `backdrop` 位图，但 `onDraw` 不再被调用，屏幕上是第一帧的旧图。
  另外带 `RenderEffect` 的 `RenderNode` 有图层缓存，不标脏就不会重跑 shader。
- **方案对比**：
  - A：每帧 `invalidate()` —— 一行代码就活；缺点：窗口永远 120Hz 重画，`uiautomator dump` 直接
    `could not get idle state`，耗电且影响无障碍/自动化。
  - B：抓图后与上一张 `Bitmap.sameAs()` 比对，变了才 `invalidate()` —— 静止时 0 重绘，滚动时跟着内容走；
    代价是抓图必须是「可读像素」的位图（软件 Canvas），且每帧多一次 memcmp（752×161×4 ≈ 484KB，<0.1ms）。
  - C：监听列表 `onScrolled` 再重绘 —— 要按 ROM 反射 `RecyclerView.OnScrollListener`，跨 ROM 脆弱，且盖不住
    「非滚动导致的变化」。否掉。
- **最终选择与取舍**：选 B。牺牲的是「用硬件录制抓图（更快）」的可能——硬件录制没有像素可比对，
  换不回 idle 能力；目前软件抓图实测 4~5ms/帧、连续甩动 554 帧只有 1 帧 jank（0.18%），够用。

## 5. 踩过的坑

### 坑：玻璃「定住了」，滚动时内容完全不动

- **现象**：搜索栏里的模糊内容固定在某一帧，截图对比滚动前后胶囊内部 **逐像素完全相同**（`mean abs diff 0.0`）；
  日志却有 `search glass capture fps=103` —— 抓图在跑，画面不更新。
- **根因**：①`SearchGlass` 不在滚动列表里，列表滚动不会 invalidate 它；②`onPreDraw` 只更新了 `backdrop` 位图，
  `onDraw` 没被调用；③`lensNode` 带 `RenderEffect`，图层缓存不标脏不重画。
- **解法**：`OnPreDraw` 里抓图 → `backdropChanged()` → 变了才 `invalidate()`。
- **如何避免**：任何「抓自己窗口」的浮层，先问「谁会让它重画？」，再问「静止时怎么停下来？」——两个问题都要有答案。

### 坑：无脑 `invalidate()` 让窗口永不 idle

- **现象**：`adb shell uiautomator dump` 报 `ERROR: could not get idle state`；`draw fps` 常年 120。
- **根因**：在 `OnPreDraw` 里 `invalidate()` 会给下一次 vsync 再排一次 traversal，下一帧又 invalidate，自激。
- **解法**：只在「抓到的位图变了」时 invalidate；静止时最后一帧比对相等，链条自然断掉。
- **如何避免**：把「idle 能不能被外部观察到」当成验收项（`uiautomator dump` 成功 = 通过）。

### 坑：玻璃胶囊比原生药丸大一圈（宽高都不匹配）

- **现象**：玻璃上下边缘溢出白色药丸，左右各宽出一截，看起来像「玻璃和搜索栏不是同一个东西」。
- **根因**：`setMaskView(null)` 让遮罩 = 整个 `SearchGlass`（50dp 包装层 / 56dp stub），而原生药丸是 stub 里
  `android.R.id.widget_frame` 的 `LinearLayout`，**44dp**（`miuix_appcompat_searchbar_bg_height`），
  且 stub 自身有 38px 左右 padding。
- **解法**：遮罩取 `widget_frame`；`computeMask()` 只按该视图背景的 padding 内缩，不再叠 6dp 补偿。
  实测 `mask=[0,13][1152,153]`，正好是 44dp 药丸。
- **如何避免**：布局尺寸别猜，`aapt2 dump xmltree` 看 `miuix_appcompat_search_stub.xml` + `dumpTree()` 打真机尺寸。

### 坑（环境）：PowerShell 5.1 读写把 UTF-8 注释变成乱码

- **现象**：用 `(Get-Content ...) -replace ... | Set-Content -Encoding UTF8` 改 Java 源码后，
  文件里 `→`、`×`、`°` 和中文注释全变成 `鈫?`、`脳`、`掳`、`澶栬`。
- **根因**：Windows PowerShell 5.1 的 `Get-Content` 默认按 ANSI(GBK) 解码 UTF-8 文件，写回时已是坏字符。
- **解法**：`git checkout -- <file>` 还原，改用带编码的编辑方式重做；或 `Get-Content -Encoding UTF8`。
- **如何避免**：不要用 shell 的读改写去动 UTF-8 源码；改完 `git diff` 必须看一眼非 ASCII 行。

## 6. 验证与证据

```powershell
# 构建（必须显式传 android.jar）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -AndroidJar "C:\Users\AbradantHawk\Documents\deepseek-harness\default-workspace\ios-settings-mod\.tools\platform36\android-36.1\android.jar"

# 装机 + 重启设置进程
adb install -r dist\IosSettingsLayout-14.9.apk
adb shell am force-stop com.android.settings
adb shell am start -a android.settings.SETTINGS

# 关键日志
adb logcat -d -v time | Select-String 'IosSettings'
```

- 关键日志：
  ```
  I/IosSettings: search glass mask=[0,13][1152,153] view=1152x167 target=LinearLayout 1152x140 pad=no
  I/IosSettings: liquid glass shader compiled
  I/IosSettings: liquid glass active (lens + rim + inner shadow)
  I/IosSettings: search glass capture fps=108
  I/IosSettings: search glass draw fps=102 captureCostMs=5
  I/IosSettings: search glass backdrop 752x161 center=#ffffffff strip=fff3f2f7 ff7de8a6 ffffffff ...
  ```
- 滚动前后胶囊内部：`mean abs diff 9.51`（修复前是 `0.0`）。
- 静止 10 秒：`IosSettings` 无任何 `fps`/`backdrop` 行；`uiautomator dump` 成功（不再 `could not get idle state`）。
- 性能（连续甩动 3 次，`dumpsys gfxinfo com.android.settings`）：
  `Total frames 554 / Janky frames 1 (0.18%) / 50th 9ms / 90th 10ms / 99th 18ms / Missed Vsync 0`。
- 产物：`dist/IosSettingsLayout-14.9.apk`，199,561 B，dex 136,880 B，
  sha256 `C60DA20577DEAC40884630A6EE9091D20F79989779D342F6BDF47A88AA71B05B`。
- 截图：`docs/assets/search-glass-14.9-light.png`、`docs/assets/search-glass-14.9-dark.png`（均为真机裁切）。
- **未验证的部分**：120Hz 慢动作录屏（没录）；分屏/平板；深色与浅色切换动画过程中的中间帧。

## 7. 未完成 / 下一步 / 风险

- 未完成：
  - 抓图仍是软件 `Canvas`（4~5ms/帧），换成硬件 `RecordingCanvas` 可以更快，但会失去像素比对能力；
  - 玻璃参数（模糊/折射/高光/内阴影）仍只有代码默认值，没有 GUI。
- 建议下一步（按顺序）：
  1. 观察几天耗电，确认「静止不重绘」在真机上成立；
  2. 如果滚动时仍看到 `captureCostMs` 偏高，把 `CAPTURE_SCALE` 从 0.6 降到 0.45 再测；
  3. 参数 GUI（`Prefs` 里 `glass_blur` / `rim_alpha` / `rim_width` / `refraction_*` 已就位）。
- 风险：
  - 变化检测依赖 `Bitmap.sameAs()`，若 ROM 在抓图里混入噪声（逐帧抖动）会退化成每帧重绘——用 `draw fps` 日志可判定；
  - `widget_frame` 是系统布局里的 id，MIUI 换布局时会变；`pickNativePill()` 已有 `findBackgroundView()` 兜底，
    但兜底会退化成「最小的带背景视图」，需要再看截图确认。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（架构 / 坑 / 安装）
- [ ] 若发版：`docs/07-changelog.md` 顶部已加一节（本次版本号不变，未加）
