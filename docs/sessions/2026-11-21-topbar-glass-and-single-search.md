# 顶栏玻璃跟手 + 退出搜索单搜索栏（2026-11-21，第二轮）

状态：已完成（真机已验证）
关联版本：16.10 → 16.11
关联提交：040cf62 / 4afb6ad（16.10），本轮待补

## 1. TL;DR

16.10 修好底部搜索栏玻璃后，用户发现「上面的玻璃停了」：顶栏内容被裁在栏底，
玻璃没有可采样内容；同时退出搜索后偶尔出现顶部原生搜索栏 + 底部胶囊两条。
16.11 在 SDK≥37 给列表清父链 clip、按栏高差上移 `translationY`、隐藏 MIUI 的 `mOverBgView`
白色遮罩，顶栏重新跟手；`BottomSearch.hideAnchor()` 在非搜索态的每次稳定布局重隐藏
`mAnchorView`，退出搜索只剩底部一条。

## 2. 对话重点

- 用户反馈（原话）：「现在是上面的玻璃停了，下面的玻璃好了，反过来了，有时会有两个搜索栏」
- 上一轮 16.10：底部搜索栏玻璃已改成只抓 `scroll_headers` 列表子树，关于本机卡片回到 1571px；
  本轮只补顶栏和搜索栏数量，不改首页布局、不改玻璃参数。
- 决策：继续用 16.9/16.8 基线（不是已撤回的 17.0），只移植 17.0 里顶栏/搜索栏相关的
  最小片段；OS3 走旧行为（SDK 门控）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `UiTweaks.java` | `unclip()`：`mScrollableView` 为空时回退 `getScrollableView()`；SDK≥37 沿列表父链 `clearClipUp` 清 `clipBounds`、关 `clipChildren` | OS4 删了 `mScrollableView`；列表被 `SpringBackLayout` 在栏底裁切，内容到不了栏下 |
| 同上 | `refreshTopBar()`：SDK≥37 把 `scroll_headers` 按 `OPEN_BAR_HEIGHT - barHeight` 设置 `translationY`（当前 182px），回顶部回 0；`lastShiftedList` 记住列表实例，fragment 重建后重新应用 | OS4 折叠时栏高从 513 缩到 331，但列表仍锚在 513，栏与内容之间有 182px 空白、模糊无内容可采样 |
| 同上 | `hideOverBackground()`：把 `NestedHeaderLayout.mOverBgView` alpha 置 0 并清背景 | MIUI 的白色 over-bg 盖在栏上，会把下面的模糊内容遮住，看起来就是一条平带 |
| `BottomSearch.java` | 新增 `hideAnchor()`，在 `setBarVisible` 和 `onGlobalLayout`（`!searching && !isInActionMode`）里把 `mAnchorView` 设 GONE | 澎湃 4 在 ActionMode 结束后重新显示原生顶部搜索栏，退出搜索会出现两条 |
| `module/AndroidManifest.xml` | versionName 16.11 / versionCode 102 | 发版 |
| `README.md`、`docs/*` | 版本、指纹、架构、踩坑、截图、会话记录 | 项目规范 |

## 4. 设计思路

- **为什么上移列表而不是改 padding**：padding 会改变 MIUI 自己的 nested-scroll 偏移和
  RecyclerView 布局，容易和折叠动画打架；`translationY` 只影响绘制/命中，不动布局，
  回顶部回 0，和 17.0 已验证的做法一致。
- **为什么只清父链 clip**：只清 `scroll_headers` 的 `clipBounds` 不够，`SpringBackLayout`
  和它的父级仍会在栏底裁切；沿父链清到 `NestedHeaderLayout` 才能让行画进栏下。
  OS3 不需要，所以用 SDK≥37 门控，避免 OS3 二次位移。
- **为什么要隐藏 `mOverBgView`**：OS4 的 over-bg 是白色渐变遮罩，叠在模糊层之上；
  不清掉的话即使内容进入栏下也看不到。alpha 设 0 而不是 GONE，保留 MIUI 的 blur 状态机。
- **为什么 `hideAnchor` 要在每次布局里做**：搜索 ActionMode 销毁、onResume、fragment 重建
  都会让 MIUI 重新显示 `mAnchorView`；只在 install 时隐藏一次必然失效。
- **兼容取舍**：所有 OS4 专用路径都加 `Build.VERSION.SDK_INT >= 37` 门控；OS3（SDK<37）
  保持 16.10 的旧行为，没有 OS3 真机回归负担。

## 5. 踩过的坑

1. **现象**：底栏已经好了，顶栏还是平带，调模糊半径没变化。
   **根因**：不是模糊半径问题，是列表没进入栏下——栏底硬切，模糊层没有内容可采样。
   **解法**：截图确认内容是否被裁在栏底；再清父链 clip + `translationY` + 隐藏 over-bg。
   **如何避免**：把「顶栏玻璃不跟手」拆成两个问题：采样源头（内容有没有进栏下）和
   采样效果（模糊半径/遮罩）；先看源头。

2. **现象**：加完 `translationY` 后截图顶栏出现模糊内容，但深色下截图一度是黑色梯形，
   以为又坏了。
   **根因**：截图落在通知/折叠过渡帧；等 2–3 秒稳定后重拍，深色顶栏是正常的黑底 + 模糊。
   **解法**：验证顶栏用「稳定 2s 后截图」；不要在通知横幅/折叠动画中判定。
   **如何避免**：系统通知「Xposed 模块已更新」会盖住顶栏，安装后先等它消失或下拉清掉。

3. **现象**：退出搜索仍偶尔有顶部搜索栏。
   **根因**：`onGlobalLayout` 在搜索结束时可能先于 MIUI 重新显示 anchor，或者当时还在
   ActionMode 内；只在 `setBarVisible` 里隐藏不够。
   **解法**：`hideAnchor` 同时在 `setBarVisible` 和每次稳定布局（非搜索态）调用，幂等。
   **如何避免**：系统「隐藏」类 hook 要在多个恢复时机重做，不能假设一次设置永久有效。

## 6. 验证与证据

设备：25053RT47C / HyperOS 4.0.4.0.XOLCNXM / Android 17 + `Settings2_Glass_v37`（37/17）。

验证：

- 顶栏玻璃：滚动后顶栏内可见 VPN 蓝色图标/文字的模糊像；继续滚动时模糊内容随列表变化。
  浅色：`topbar-16.11-light.png`；深色：`topbar-16.11-dark.png`。
- 回顶部：`top bar diag` 显示 `bar=513 open=513 shift=0 listY=0`（临时诊断，已删）；
  列表恢复原位置，顶栏恢复大标题。
- 重开设置（热启动）后滚动：顶栏仍跟手（before/after 截图一致）。
- 双搜索栏：点底部搜索 → 返回两次；`uiautomator` 只剩 `search_mode_stub` / `inputArea`
  底部一套，无顶部原生搜索栏。
- 16.10 回归：底部玻璃仍显示列表内容，关于本机 `device_basic_layout` 仍是 `[38,1571]`。
- 日志无 RuntimeShader 报错（新进程）。
- 未验证：OS3 真机（SDK<37 走旧路径，未上机）；第三方主题。

## 7. 未完成 / 下一步 / 风险

- 请用户复验顶栏玻璃：上下滚动、回顶部、重开设置、深色模式。
- 16.11 顶栏依赖 `OPEN_BAR_HEIGHT` 和 `scroll_headers`；若进程在栏折叠态冷启动且从未
  记录展开栏高，shift 可能为 0，需要滚动一次才恢复。
- `hideOverBackground`/`clearClipUp` 依赖 MIUI 的 `mOverBgView` 和父链层级；换 ROM 会软失败，
  表现退回平带（不会崩）。
- `hideAnchor` 依赖 `mAnchorView`/`sAnchor`；ROM 换 id 时可能仍出现两条，需要补新查找。
- OS3 未回归；如果之后在 OS3 上发现顶栏差异，优先把新逻辑再收紧到 OS4 的类名/字段判断。
