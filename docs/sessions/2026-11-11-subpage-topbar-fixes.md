# 二级页顶栏收尾：进入偏移 / 我的设备留白 / 「通用」共用模糊（2026-11-11）

状态：已完成
关联版本：16.7 (98) → 16.8 (99)
关联提交：<发版提交见 docs/00-status.md>
参与者：AbradantHawk / DeepSeek Harness

## 1. TL;DR

16.8 修掉 16.7 二级页顶栏在真机上的三个问题：进入二级页时列表自带约 500px 滚动偏移（顶栏和发丝线
提前出现）；「我的设备 / 通用」下方卡片被内层 `description_grid` 的 padding 撑出 331px 留白；
「通用」内容进不了栏下、顶栏看不到模糊。25053RT47C / HyperOS 3 上浅色与深色均实测通过。

## 2. 对话重点

- **用户要求（原话）**：
  - 「点击“通用”进入我的设备后，设备详情页的卡片留白很大（图2），请修复，在主界面滑动到顶栏出现时，
    点进二级界面时就有顶栏了，请修复」
  - 随后补充：「“通用”里面的顶栏也要共用模糊」
- **做出的决策**：
  - 继续用 MIUI 原生 `ActionBarContainer`，不另画栏；只修「谁被 padding / 谁被裁 / 谁被抬」；
  - `findScrollable` 改为「最外层、可竖向滚动」的容器，而不是 DFS 遇到的第一个 RecyclerView；
  - 设备详情页只在识别到 `miui_version_card_view` 且 wrapper.top == 栏底时做抬升，其他页面完全不碰；
  - 发丝线增加「列表确实在栏下」条件，避免内容到不了栏底的页面画出悬空的线。
- **否决的方案**：
  - A. 给「通用」自绘模糊 / 截屏再模糊：要自己截内容、做 RenderEffect，滚动时每帧开销大；
  - B. 给内层 `description_grid` 继续加 padding：它根本不是滚动容器；
  - C. 用 `computeVerticalScrollOffset()` 判断滚动：部分 MIUI 列表恒 0（16.7 已踩过）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/com/Remo/ilayoutsettings/SubPageTopBar.java` | `findScrollable` 从「第一个 RecyclerView」改为「最外层竖向滚动容器」（RecyclerView / ListView / NestedScrollView / ScrollView；横向 RecyclerView 与 `ios_about_page` 跳过） | 「我的设备」真正的滚动容器是外层 `NestedScrollView`，内层 `description_grid` 是非滚动 grid；后者被加 331px padding 后，下方卡上方出现巨大留白 |
| 同上 | 新增 `resetScrollToTop`：padding 之后立刻 `scrollToPosition(0)` / `scrollTo(0,0)` | overlay 打开 + padding 变化后 `LinearLayoutManager` 会按第 0 行的旧局部坐标重排，读成「已滚动 500px」，进入页面顶栏/发丝线就出现 |
| 同上 | 新增 `liftWrapperUnderBar`：识别 `miui_version_card_view`，wrapper `topMargin -= 栏高`，列表加同高 padding；wrapper→host 一路 `clipChildren/clipToPadding=false` | 「我的设备 / 通用」把内容放在 top == 栏底的 wrapper 里，overlay 模式无效；不抬升内容永远进不了栏下，模糊没有可采样的内容 |
| 同上 | 新增 `OnPreDraw`：清列表/父级 `clipBounds`，抬升过的页面再清 `clipChildren/clipToPadding`；顺带 `ensureBarBackground` | MIUI 每次 layout 都会把这些重新设回去（实测 `wrapper.top` 被重设为 331、`clipChildren` 回到 true） |
| 同上 | `watch` 里排 700/1600/3200ms 三次延迟 refresh；`SCROLL_WATCHED` 改为按列表实例记录 | 设备详情页异步数据落地后二次 inflate，旧树的 padding/抬升随旧 View 丢弃；按实例记录可给新列表重新挂监听 |
| 同上 | padding 记在「被 padded 的 View」上；恢复时用记下的值，不再按当前栏高重算 | 滚动中栏会折叠（500→331），按当前栏高重算会把 inset 改小；ScrollView 的自身 padding 又会被 MIUI inset pass 清 0，所以加在内容子 View 上 |
| 同上 | 发丝线显示条件加 `overlapUnderBar > 0` | 内容到不了栏底的页面不再出现「悬空」的线 |
| `module/AndroidManifest.xml` | 16.7 / 98 → **16.8 / 99** | 发版 |
| `docs/*`、`README.md` | 版本、指纹、架构 §4.3、踩坑表、changelog、截图 | 仓库规范 |

## 4. 设计思路

- **问题本质**：二级页顶栏的模糊不是「给栏加一层效果」就算，而是**栏后面必须有真实绘制的内容**。
  16.7 用 overlay + padding 让普通页做到这一点，但：
  1. 选错了滚动容器 → 内层 grid 被 padding；
  2. padding 与 overlay 同帧生效 → LayoutManager 保留了旧局部坐标，读成滚动偏移；
  3. 「我的设备」这类页的列表被一个 top == 栏底的 wrapper 包住，overlay 对它无效，且 wrapper
     默认 `clipChildren` 把伸出去的内容裁在栏底。
- **方案对比**：
  - A. 自绘栏（背景 + 模糊 + 线）：可控，但要自己处理折叠标题动画、返回键、inset，风险高；
  - B. 只改栏背景/模糊（16.7）：内容进不了栏下，模糊没有内容可采样（把半径调到 100px 也看不出来）；
  - C.（选中）原生栏 + overlay/padding + 对 version-card 页额外抬 wrapper + 预绘制清 clip：
    改动都在原生 View 上，标题/返回键/主题仍是 MIUI 的。
- **关键取舍**：
  - 抬 wrapper 只在「子树含 `miui_version_card_view`」且「wrapper.top == 栏底」时做，其他页面零改动；
  - 每帧清 `clipChildren` 是必要的（MIUI 每次 layout 重设），但只在抬升过的页面做，普通页只清
    `clipBounds`（16.7 已有）；
  - 二次 inflate 用 3 次延迟 refresh 兜底，而不是 hook fragment `onCreateView`：少一个类钩子，
    代价是 3 次很轻的 refresh；慢设备仍偶发丢 padding 时再换 hook（已记入 00-status 下一步）。

## 5. 踩过的坑

### 坑：进入二级页时顶栏和发丝线已经出现

- **现象**：从首页进「显示 / 无线局域网」等页，页面一打开就是滚动状态：内容被栏裁掉、发丝线亮着。
  手动往下滑到顶才恢复正常。
- **根因**：overlay 打开后列表从 top=500 移到 0，我们又把 padding 从 14 改成 500；同一帧里
  `LinearLayoutManager` 用已 attach 的第 0 行的**旧局部坐标**（14）重排，等价于滚动
  `offset = padding - childTop = 486`，`canScrollVertically(-1)` 直接为 true。
- **解法**：`padListUnderBar` 里 `setPadding` 之后立即 `scrollToPosition(0)`（RecyclerView）/
  `scrollTo(0,0)`（ScrollView），把第 0 行锚回 padding 顶部。
- **如何避免**：任何「改 padding 让内容位移」的改动，都要在同一次调用里重置滚动锚点，
  并用 `canScrollVertically(-1)` 打日志验证。

### 坑：「我的设备」卡片留白 331px

- **现象**：进入「通用 → 我的设备」，下方「REDMI Turbo 4 Pro」卡上方多出一大块空白（用户图 2）。
- **根因**：`findScrollable` 返回 DFS 里第一个 RecyclerView，而这页真正的滚动容器是外层
  `miuix.core.widget.NestedScrollView`；被选中的 `description_grid` 是 `nestedScrollingEnabled=false`
  的内层 grid，加 padding 后整张卡被推下去。
- **解法**：只认最外层竖向滚动容器；横向 RecyclerView（tab/carousel）与 AboutIos 自绘页跳过。
- **如何避免**：判断「哪个是页面列表」时先看能不能滚（`nestedScrollingEnabled` / 布局方向），
  不要按类名找第一个。

### 坑：内容进不了栏下（抬升 wrapper 后仍被裁在 331）

- **现象**：`getLocationOnScreen()` 显示列表已在 [0,0]，`child0.top` 也到了负值，但屏幕上内容
  仍从 331 开始，栏区域只有页面底色。
- **根因**：`main_content` 给 wrapper 留了 331 的 top；`topMargin -= 331` 在 miuix 的 FrameLayout 里
  只把高度加了 331、top 仍是 331，列表因此伸到父级 bounds 之外，被 wrapper 的 `clipChildren`
  裁掉。`getLocationOnScreen` 不反映裁剪，所以日志看起来「已经到 0」。
- **解法**：把 wrapper→host 一路的 `clipChildren/clipToPadding` 关掉，并在 `OnPreDraw` 里每帧重关
  （MIUI 每次 layout 会恢复）。
- **如何避免**：看到「bounds 是 0、画面却被裁在 N」时，先怀疑父级 `clipChildren`，再怀疑
  `clipBounds`；`getLocationOnScreen` 只说明布局位置，不说明可见范围。

### 坑：padding 加在 ScrollView 上会被清 0

- **现象**：`sub-page list padded to 331px` 打完，几百毫秒后 `geom` 里的 `padTop` 又变回 0。
- **根因**：`NestedScrollView` 在 MIUI 的 inset pass 里重设自身 padding。
- **解法**：把 padding 加在它唯一的内容子 View（`LinearLayout`）上，padding 随内容一起滚动，
  视觉等价、也不会被 inset 覆盖。

### 坑：设备详情页二次 inflate，padding/抬升丢失

- **现象**：刚进页面 `list=[0,0]`、padding 生效；几秒后 uiautomator 里 wrapper 又回到 331，
  调试日志里 `child0` 的 bounds 也整体换了。
- **根因**：异步设备数据落地后 MIUI 重建了页面视图树，旧树连同我们的修改一起被丢弃。
- **解法**：`watch` 里排 700/1600/3200ms 三次延迟 refresh，每次重新 `findScrollable` 并套用；
  `LIST_PADDING` 按 View 记录，重复调用天然幂等。

## 6. 验证与证据

```bash
cd iLyayot
JAVA_HOME='D:\Android\jdk21\jdk-21.0.2' \
ANDROID_BUILD_TOOLS='D:\Android\Sdk\build-tools\37.0.0' \
ANDROID_JAR='D:\Android\Sdk\platforms\android-37.0\android.jar' \
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File module/build.ps1
adb install -r dist/iLayoutSettings-16.8.apk
adb shell am force-stop com.android.settings
adb shell am start -a android.settings.DISPLAY_SETTINGS   # 应停在页面顶部
adb shell am start -a android.settings.DEVICE_INFO_SETTINGS
adb shell input swipe 640 1900 640 1200 400
```

- 构建：`OK -> dist/iLayoutSettings-16.8.apk`，**302,626 B**，
  SHA-256 `6D543EABA02906A665787A4207FE72464A18E4EA895501C33D05028520B80F3A`；
  badging `versionCode='99' versionName='16.8'`，`xposed_init / module class / reorder` 均 True。
- 安装：`Success` + `Install command complete`，`dumpsys package` 显示 `versionName=16.8`。
- 日志：
  - `sub-page wrapper lifted 331px (margin -331, height -1)`
  - `sub-page list padded to 331px (bar 331, LinearLayout)`
  - `sub-page top bar watching miuix.core.widget.NestedScrollView clip=null top=331`
  - 无 `sub-page overlay mode failed` / `divider failed` / `padding restored` 刷屏。
- 像素证据（1280×2772，x=600 / x=1100 竖扫）：
  - 「显示」页刚进入：**无发丝线**（y=498 = `(243,243,248)`，内容从 y=500 开始）；
  - 「显示」页滚动后：内容滑到栏下，y=520 = `(228,228,228)` → 浅色发丝线；
  - 「通用」页滚动后：y=120–320 显示卡片内容（被栏模糊），y=330 = `(224,224,224)` 发丝线；
  - 深色（「关于手机」滚动后）：y=330 = `(75,75,75)`，栏上内容 `(35,35,35)` → 中性灰 30% 线。
- 截图：`docs/assets/general-16.8-light.png`、`general-16.8-dark.png`、
  `devinfo-16.8-light.png`（另有两页的进入态在 `tmp/`，未提交）。
- 已复核页面（浅色）：显示（外观）、无线局域网、通知、通用、我的设备 / 关于手机、首页；
  深色复验了「关于手机」页的栏下内容与发丝线（`(75,75,75)`）。
- **未验证**：第三方主题、平板/横屏、非 `SettingsActivity` 子类的二级页、ROM 升级后的
  `miui_version_card_view` / `ActionBarOverlayLayout` 改名。

## 7. 未完成 / 下一步 / 风险

- 未完成：
  - 延迟 refresh 仍是固定三次，未改成 hook fragment `onCreateView`；
  - 平板/横屏未测。
- 建议下一步（按顺序）：
  1. 在慢设备 / 第三方主题上复看「我的设备」是否仍偶发丢 padding，必要时改成
     `MyDeviceInfoFragment#onViewCreated` 钩子；
  2. 平板/横屏下复核二级页栏高与 overlay 模式（16.5 已知问题 3）；
  3. 若 ROM 把 version-card 容器换掉，`liftWrapperUnderBar` 会软失败，届时先抓
     `dumpsys` + `OnPreDraw` 日志再改识别条件。
- 风险：`miui_version_card_view`、`ActionBarOverlayLayout`、`NestedScrollView` 的 inset 行为都是
  MIUI 私有约定；每帧 `OnPreDraw` 清 clip 在低端机上增加一点点绘制开销，但只发生在抬升过的页面。
