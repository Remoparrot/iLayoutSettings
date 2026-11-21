# 澎湃 4 搜索栏玻璃截帧 / 关于本机卡片位置（2026-11-21）

状态：已完成（真机已验证）
关联版本：16.9 → 16.10
关联提交：待补

## 1. TL;DR

澎湃 4 + `Settings2_Glass_v37` 上，搜索栏玻璃滑到「控制中心」以下就停住、重开设置变半透明，
根因是 `root.draw()` 碰到高级材质的 AGSL `RenderEffect` 每帧抛异常；改成只画 `scroll_headers`
列表子树后玻璃跟手，失败也保留上一帧。关于本机「本机详情」卡比原设置低 331px，根因是
MIUI 已给 NestedScrollView 加了栏高 inset、模块又给 `scroll_layout` 加了一遍；减去后卡片回到 1571px。

## 2. 对话重点

- 用户要求（原话要点）：「帮我把设置的玻璃修好，澎湃 4 现在是到『控制中心』选项的底下时，
  搜索栏的玻璃就卡住了，刷新就变成半透明了，关于本机的本机详情的卡片位置太靠下了，
  保留澎湃 4 原设置的卡片位置，搞完同步 GitHub 和 releases。」
- 设备：25053RT47C / HyperOS 4.0.4.0.XOLCNXM / Android 17；设置是 `Settings2_Glass_v37`
  （versionCode 37 / versionName 17，基于澎湃 3 设置改版），模块 16.9（16.8 基线）。
- 决策：从 `release/16.9` 拉修复分支，不采用已撤回的 17.0 OS4 专用适配；只移植「列表子树抓帧」
  和「二级页 inset 去重」两处，版本 16.10 / 101。
- 范围外：不改搜索栏默认模糊 / 折射 / 厚度，不改首页布局，不动 17.0 的 ForceBlur / translationY /
  CardMargins / PixelCopy 路径。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/com/Remo/ilayoutsettings/SearchGlass.java` | SDK≥37 的 `capture()` 改走 `captureListOnly()`：找 `scroll_headers`，填页面底色，`clipRect` 到玻璃带，`canvas.translate` 按窗口坐标，`list.draw()` 画进 `captureScratch`，成功后与 `backdrop` 交换 | 澎湃 4 的顶栏 / `bgEffectView` 带 AGSL RenderEffect，软件画布抛 `Software rendering doesn't support RuntimeShader`；旧路径每帧失败，玻璃冻结/变半透明。列表子树绕开材质视图、含卡片内容、不含玻璃自己 |
| 同上 | 抓帧失败不再 `backdrop = null`，保留上一帧；错误日志 1s 节流 | 「冻结」好过「变半透明」；避免 120Hz 刷 logcat |
| 同上 | `activityOf` / `captureList` / `logBackdrop` / `noteCaptureError` 辅助；`onDetached` 清列表缓存 | 列表可随 fragment 重建，弱引用避免泄漏 |
| `module/src/com/Remo/ilayoutsettings/SubPageTopBar.java` | `padListUnderBar` 首轮 `add = max(0, desired - list.getPaddingTop())`；新增 `BASE_PADDING` 与 `trimDoubleInset` 预绘制兜底 | 关于本机页 NestedScrollView 已有 331px inset，模块再叠 331px，卡片从 1571 被推到 1902；去掉后与「模块不参与」时的原生位置一致 |
| `module/AndroidManifest.xml` | versionName 16.10 / versionCode 101 | 发版 |
| `README.md`、`docs/00-status.md`、`docs/02-architecture.md`、`docs/06-pitfalls.md`、`docs/07-changelog.md` | 同步版本、指纹、根因、坑与截图 | 项目规范 |

## 4. 设计思路

- **为什么只画列表子树，而不是修 `root.draw()`**：异常来自树里某个 View 的 `RenderEffect`，
  模块无法安全地把系统 View 的 effect 临时摘掉（改属性会触发重绘/布局，且可能被 MIUI 覆盖）。
  列表子树是搜索栏唯一需要的背景来源，且不含材质顶栏 / 全屏 effect / 玻璃自己，三方都满足。
- **为什么用 `list.draw()` 而不是逐行 `child.draw()`**：17.0 为了省 3ms 改成只画与玻璃相交的 2~3 行，
  但会跳过 `RecyclerView.ItemDecoration` 画的卡片底。当前设备实测整棵列表画进软件画布只要 ~1ms，
  保留卡片装饰更重要，所以先用 `list.draw()`；如果低端机出现掉帧再退回行级裁剪。
- **为什么要 scratch + 交换**：`list.draw()` 中途抛异常会留下画了一半的 bitmap；
  先画 scratch、成功再换，失败时玻璃仍显示上一帧完整内容。
- **为什么保留 `root.draw()` 回退**：OS3（SDK<37）完全走旧路径；SDK≥37 但找不到列表的 ROM
  仍需要一个兜底。只有列表存在时新路径才独占。
- **为什么关于本机只减 `getPaddingTop()`**：`overlapUnderBar` 已经是「栏底 - 列表顶」，
  而 NestedScrollView 的 `paddingTop` 是 MIUI 用同一个栏高写的；两者是同一份 inset。
  用 `max(0, overlap - miuiInset)` 不会在 MIUI 没加 inset 的页面上少加。
- **为什么还要 `trimDoubleInset`**：首轮 padding 可能发生在 MIUI inset pass 之前（页面重建/二次 inflate），
  这时减法看到的是 0；等 MIUI 写入后需要一次兜底把多余的层清掉。只在列表回顶时执行，避免滚动中跳动。

## 5. 踩过的坑

1. **现象**：修好玻璃后，关于本机卡片仍在 1902，日志只显示 `padded to 331px`。
   **根因**：先入为主以为 MIUI 的 ScrollView padding 是模块加的；实际是 MIUI 自己在 inset pass 写入，
   而模块在它之前读到了 0。
   **解法**：加日志打印 `desired / miui / target / add`，确认 `miui=331` 后再做减法；
   另加预绘制兜底。
   **如何避免**：给系统容器加 inset 前先打印它自己的 padding，不要假设「这个空白是模块加的」。

2. **现象**：第一次只改 `SearchGlass` 时，抓帧日志从 120 条/秒异常变成正常，但 `draw fps=0` 一度让人以为玻璃没重绘。
   **根因**：`draw fps` 是 2 秒窗口统计，静止时确实不重绘；`capture fps` 在滚动时到 100+ 才是判断依据。
   **解法**：用滚动中截图 + `capture fps`/`draw fps` 两条日志一起看。
   **如何避免**：不要用单条 fps 日志判断「冻结」；看截图里的玻璃内容是否随列表移动。

3. **现象**：`release/16.9` 分支不在本地，`git log` 只看到 main 的 17.0。
   **根因**：16.9 发布在远程 `release/16.9` 分支，未合回 main。
   **解法**：`git fetch origin release/16.9:release/16.9` 后从它拉分支。
   **如何避免**：接手前先 `git ls-remote origin`，不要只看本地分支和 main 的文件。

## 6. 验证与证据

设备：25053RT47C / HyperOS 4.0.4.0.XOLCNXM / Android 17，设置 `Settings2_Glass_v37`（37/17）。

构建：

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module/build.ps1 \
  -Jdk '...\jdk17.0.21_0' \
  -BuildTools 'D:\Android\Sdk\build-tools\37.0.0' \
  -AndroidJar 'D:\Android\Sdk\platforms\android-37.0\android.jar'
# → dist/iLayoutSettings-16.10.apk
#   size: 302,626 B  sha256: 7A4E2048644B85047757604C7093AA1E7E352642D920B8AFBFC330BCFB5E32DA
```

验证：

- 玻璃：滑到「控制中心」以下截图，玻璃里能看到 `面容ID与密码` 的绿色图标和行文字（16.9 是冻结的旧帧）；
  回桌面再打开设置，玻璃仍有内容，不再是半透明底板；滚动中 `capture fps=100~109`、`draw fps=108`，
  无 `Software rendering doesn't support RuntimeShader` 日志。
- 深色：`cmd uimode night yes` 后玻璃内可见「屏幕时间」模糊图标，backdrop 中心 `#1C1C1E` 页面底色。
- 关于本机：`uiautomator dump` 显示 `device_basic_layout` 从 `[38,1902]` 变为 `[38,1571]`
  （`miui_version_card_view` 底部正是 1571）；日志
  `sub-page padding calc desired=331 miui=331 target=0 add=0`。
  把 `SubPageTopBar.refresh()` 临时置空做对照，布局与修复后完全一致（1571），确认是原设置位置。
- 回归：通知 / 显示 / Wi-Fi 页列表仍在 513，未被新逻辑加 padding；二级页发丝线/模糊正常。
- 未验证：OS3 真机（SDK<37 走旧路径，未上机）；第三方主题。

## 7. 未完成 / 下一步 / 风险

- 16.10 需要真机安装后由用户复验手感；本次已在连接设备上安装验证。
- `SearchGlass` 的列表抓帧依赖 `scroll_headers` 这个 id；换 ROM 若改名，会回退到 `root.draw()`
  并可能在高级材质下继续失败（保底是保留上一帧，不再半透明）。
- `list.draw()` 在低端机/长列表上的单帧成本未量化；若用户报掉帧，可改回 17.0 的
  `drawBandRows`（只画与玻璃带相交的行），代价是丢掉 ItemDecoration 的卡片底。
- 17.0 的 Choreographer 驱动未移植；当前自定义设置下滚动由 UI 线程绘制，`OnPreDraw` 实测
  100+ fps，够用。若以后列表改硬件层滚动，再移植。
- `trimDoubleInset` 只处理 ScrollView；RecyclerView 页面的双重 inset 若出现，需要另做。
- 文档里的截图取自本机浅色/深色；release 正文引用 `docs/assets/*-16.10-*.png`。
