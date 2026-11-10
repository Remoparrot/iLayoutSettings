# 二级页顶栏（模糊/半透明/发丝线）+ 控制中心上移 + 浅色搜索栏高光回退（2026-11-11）

状态：已完成
关联版本：16.6 (97) → 16.7 (98)
关联提交：<发版提交见 docs/00-status.md>
参与者：AbradantHawk / DeepSeek Harness

## 1. TL;DR

16.7 把「控制中心」移到「墙纸」上方；二级页顶栏从「原生不透明栏」改成和首页同一套：半透明底色 +
原生模糊 + 内容滑进栏下 + 滚动时出现 0.5dp 半透明发丝线。浅色搜索栏的高光按用户要求**回退到 16.6**
（16.7 早前加的灰色描边 + 内移白高光偏重，已整段撤掉）。无线局域网、所有应用两页在浅色/深色下都真机核对过。

## 2. 对话重点

- **用户要求（原话）**：
  - 「把『控制中心』改到『墙纸』的上面」
  - 「浅色模式时，搜索栏的高光不明显」
  - 「二级界面的顶栏也要图标主界面的顶栏模糊、透明度」（原话，指二级页顶栏也用首页那套模糊/透明度）
  - 「GitHub 用新的截图」「测试文件不要放手机存储根目录，放 Download/iLayoutSettings 里」
  - 本轮：「浅色模式的搜索栏高光调回去吧，二级界面的顶栏也要有半透明的线」
- **做出的决策**：
  - 二级页不做「自绘栏」，直接沿用 MIUI 的原生 `ActionBarContainer`：换半透明底色 + 下发原生模糊；
  - 为了「内容滑进栏下」，用 `ActionBarOverlayLayout.setOverlayMode(true)`（MIUI 自带的 overlay 模式），
    再给列表补栏高 padding，而不是自己改 layout 层级；
  - 发丝线**复用首页那套** `applyDivider`，显示条件用 `canScrollVertically(-1)`，
    不再写第二套条件（首页那套依赖 `OPEN_BAR_HEIGHT` 判断折叠，二级页没有这个字段）；
  - 搜索栏灰边试了一版后被否：不再自行加强浅色高光，先回退、以后要动先给对比图。
- **范围外但被提到的事**：GitHub 截图（已用 16.7 新截图更新 README，Release 正文内嵌同一批），
  测试产物统一放 `/sdcard/Download/iLayoutSettings/`。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/.../HeaderData.java` | 组 4 槽位调整：`控制中心` 提到 `墙纸` 之前（并同步 `SLOT_TITLES` 兜底） | 用户要求顺序 |
| `module/.../SubPageTopBar.java`（新） | `SettingsActivity` / `SettingsBaseActivity#onResume` 后给 `action_bar_container` 换半透明底色、下发 `MiuiBlurUtils.setBackgroundBlur`、切 overlay 模式、列表补栏高 padding；新增 `applyDivider` 在滚动回调里更新发丝线 | 二级页原本完全不透明、不模糊、没有线 |
| `module/.../UiTweaks.java` | `applyDivider` 从 `private` 改为包内可见 | 让 `SubPageTopBar` 复用首页的发丝线实现，避免两套色值/厚度 |
| `module/.../Hook.java` | 安装 `SubPageTopBar.install(cl)`（在 `ForceBlur` 之前） | 接入 |
| `module/.../SearchGlass.java` | **整文件回退到 16.6**（灰色描边 / `edgeColor` / `rimStart` / 浅色 tint 0.62 / 1.2dp 白高光全部撤掉） | 用户要求「高光调回去」：灰环 + 白环两条线偏重 |
| `docs/assets/*16.7*`、`README.md`、`00-status`、`02-architecture`、`06-pitfalls`、`07-changelog` | 新截图 + 文档 | 仓库规范 |

## 4. 设计思路

- **问题本质**：二级页的顶栏是 `ActionBarContainer`（不透明、无模糊），而首页的顶栏效果来自
  `NestedHeaderLayout` + `mOverBgView` 的 `MiuiBlurUiHelper`。二级页完全不经过这些类，
  所以要「在共同的那一个 view 上做」——即 `action_bar_container` 自己下发背景色与 blur。
- **方案对比**：
  - A. 给二级页自绘一条栏（背景 + 模糊 + 线）——可控，但会盖住 MIUI 的折叠标题动画、
    返回按钮与搜索框，还得自己处理 inset，风险高；
  - B. 继续用原生栏，只改它的背景/模糊，并打开 `ActionBarOverlayLayout` 的 overlay 模式让内容
    铺到窗口顶——改动最小，折叠标题、返回键、主题全部还是 MIUI 自己的；
  - C. 只清 `clipBounds`（首页那种做法）——二级页列表本来就在栏下方，没有东西可模糊，等于白改。
- **最终选择**：B。代价是依赖 `ActionBarOverlayLayout.setOverlayMode` 这个内部方法与
  「列表 padding = 栏高」的约定，ROM 改了会软失败（栏仍半透明，但内容不滑进栏下），已写进已知问题。
- **发丝线**：不新写条件，直接复用首页的 `applyDivider`（0.5dp `ColorDrawable`、浅色黑 12%、
  深色中性灰 30%、宿主是栏父级的 `ViewOverlay`），只把「是否显示」换成 `canScrollVertically(-1)`。

## 5. 踩过的坑

### 坑：二级页栏半透明了，但内容一直不会滑到栏下

- **现象**：底色和模糊都生效（对着栏看是糊的），但列表滚到栏底就被裁断，栏下面永远是空白。
- **根因**：二级页是普通列布局：`list.top == bar.bottom`，栏下面没有东西可采样；
  首页能「滑进去」是因为 `NestedHeaderLayout` 让内容铺满窗口再自己 overlay。
- **解法**：反射调 `ActionBarOverlayLayout.setOverlayMode(true)`（内容铺到窗口顶部），
  然后给列表 `paddingTop = appBar.getHeight()` + `setClipToPadding(false)`。
  padding 只能加一次（`LIST_PADDING` 记住），因为折叠动画中栏高会变，重复加会把列表越推越下。
- **如何避免**：先看布局关系（`dumpsys activity top` 里的 bounds），再决定是「清 clip」还是「改 overlay 模式」。

### 坑：二级页加了发丝线但不显示

- **现象**：`applyDivider` 调用正常、bounds 也写了，但屏幕上没有线。
- **根因**：没有写显隐条件时线在静止状态 `visible=false`；第一版还试过用
  `computeVerticalScrollOffset()` 判断滚动，MIUI 的列表恒返回 0，永远认为「在顶部」。
- **解法**：显隐条件改 `list.canScrollVertically(-1)`（第一行已滑到栏下才为 true）；
  宿主继续用栏父级 `ActionBarOverlayLayout` 的 overlay（`ActionBarContainer` 自己的 overlay 不绘制）。
- **如何避免**：别在 MIUI 列表上用 `computeVerticalScrollOffset()`；发丝线先打洋红探针确认宿主会画。

### 坑：16.7 给浅色搜索栏加灰边后变成「两条线」

- **现象**：栏边缘出现灰环，往里还有一圈白高光，整体比 16.6 重；用户直接要求「高光调回去」。
- **根因**：为了让白底上的白高光可读，加了灰色描边 + 把白高光内移，两者叠在一起就成双线；
  同时浅色 tint 从 0.50 提到 0.62，整体更灰。
- **解法**：`git checkout -- SearchGlass.java` 整段回退（16.6 的上下白高光）；真机截图与
  `docs/assets/search-glass-16.6-light.png` 逐像素对照，底色回到 `#F2F2F5`、无双环。
- **如何避免**：这类「在已有高光上加一层」的改动一定先出对比图给用户看；白底高光不可见是
  16.6 的已知问题，别自动重做。

## 6. 验证与证据

```bash
cd iLyayot
JAVA_HOME='D:\Android\jdk21\jdk-21.0.2' \
ANDROID_BUILD_TOOLS='D:\Android\Sdk\build-tools\37.0.0' \
ANDROID_JAR='D:\Android\Sdk\platforms\android-37.0\android.jar' \
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File module/build.ps1
adb install -r dist/iLayoutSettings-16.7.apk
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell input swipe 640 2000 640 1000 500
adb shell screencap -p /sdcard/Download/iLayoutSettings/16.7b-sub-scr-light.png
adb shell dumpsys window | grep mCurrentFocus
```

- 结果：构建 OK；`Install command complete`；二级页 `mCurrentFocus=...Settings$WifiSettingsActivity`。
- 产物：`dist/iLayoutSettings-16.7.apk`，298,530 B，
  SHA-256 `25E0FCA8A39256DFCEC726DCEEC50CB8B268427CDE27A0C18F61589E11CDD973`。
- 日志：`hooked sub-page activity bases: 2`、`sub-page action bar overlay mode on`、
  `sub-page list padded to 500px (bar 500)`、`sub-page top bar watching miuix.recyclerview.widget.RecyclerView`；
  无 `sub-page divider failed` / `sub-page top bar refresh failed`。
- 像素证据（1280×2772，x=200 竖直方向）：
  - 浅色滚动后栏底 y=329–330 = `(222,222,222)`，栏上方 `(253,253,253)` → 黑 12% 线；
  - 深色滚动后同位置 = `(75,75,75)`，栏上方 `(36,36,36)` → 中性灰 30% 线；
  - 回到顶部（y=500 栏底）无线，内容在栏下不再被裁。
- 截图：`docs/assets/home-16.7-light.png`、`home-16.7-dark.png`、
  `subpage-topbar-16.7-light.png`、`subpage-topbar-16.7-dark.png`（另附发丝线特写
  `subpage-topbar-16.7-hairline.png`）；回退前双环对照片 `docs/assets/search-glass-16.7-light-overtry.png`。
- **未验证的部分**：第三方主题、平板/横屏、搜索模式下的二级页顶栏；
  非 `SettingsActivity` 子类的二级页（如系统内嵌的设置页）不保证。

## 7. 未完成 / 下一步 / 风险

- 未完成：无（16.7 已构建、安装、验证）。
- 建议下一步（按顺序）：
  1. 观察二级页顶栏在**第三方主题 / 平板**下的栏高与 overlay 模式；
  2. 若 UI 滑杆改动后二级页栏不刷新，考虑给 `SubPageTopBar` 加一个空闲轮询（同首页 `ensurePoll`）；
  3. 浅色搜索栏高光若要再改，先出对比图。
- 风险：`ActionBarOverlayLayout.setOverlayMode` 与列表 padding 是 MIUI 内部约定，ROM 升级可能软失败
  （栏半透明但内容不滑进栏下、也没有线）；发丝线宿主要求「父级会画 overlay」。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（架构 / 坑 / 构建 / 地图）
- [x] 若发版：`docs/07-changelog.md` 顶部已加一节
