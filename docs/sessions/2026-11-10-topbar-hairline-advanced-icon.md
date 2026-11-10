# 顶栏发丝线跟随深浅色 + 模糊首发 + 高级设置开关 + 新图标（2026-11-10）

状态：已完成
关联版本：16.3 → 16.4
关联提交：<待补>

## 1. TL;DR

16.4 修了两个顶栏问题：设置界面内切换深浅色后发丝线消失（监听器绑在会被重建的 fragment 视图上），
以及顶栏第一次出现时模糊可能从 0 开始（原生 `refreshBlur()` 前没有把半径写好）。
另外 GUI 新增「高级设置」总开关（关闭时隐藏顶栏/发丝线选项），启动图标换成蓝紫渐变 + 玻璃盘 + 白齿轮。
真机 25053RT47C 深浅色各验一次，发丝线像素与 16.3 文档一致。

## 2. 对话重点

- 用户要求（原话要点）：
  1. 顶栏刚出来的时候没有模糊；
  2. 设置界面内切换深浅色模式，顶栏的半透明分割线会消失；
  3. GUI 内加一个高级设置开关，只在设置里加了高级设置的情况下才可以显示、隐藏（发丝线）；
  4. 应用图标换一种风格。
- 决策：
  - 发丝线**默认仍绘制**；「高级设置」只控制 GUI 里是否出现顶栏/发丝线选项，关掉开关不强制隐藏已有的线
    （避免升级后所有人的线突然消失，也符合「只在该条件下才可以显示/隐藏」的字面意思）。
  - 高级设置默认**关闭**：新装用户只看到搜索栏玻璃滑杆，顶栏选项收起来。
  - 图标选「A 液态玻璃」方案（蓝紫渐变 + 半透明玻璃盘 + 白齿轮），脚本保留 B（纸感线框）、C（深色滑杆）备选。
- 范围外但被提到的事：搜索栏不修（延续 16.3 末的回退结论）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../UiTweaks.java` | 删除 `attachScrollBlur()` 的 fragment 滚动监听 + 500ms 轮询；新增 `installTopBar()`，在 `NestedHeaderLayout` 的 `onScrollingProgressUpdated` / `onAttachedToWindow` / `onConfigurationChanged` 上挂钩；每次 fresh 解析 action bar 与 `scroll_headers`；发丝线画在 `ActionBarOverlayLayout` 的 overlay 上；颜色每帧按当前 `uiMode` 取 | fragment 视图在 uiMode 变化时重建，旧 appBar/list 失联 → 线消失；`ActionBarContainer` 不画自己的 ViewOverlay，线不可见；`mScrollableView` 是 SpringBackLayout（offset 恒 0） |
| `module/src/.../ForceBlur.java` | `MiuiBlurUiHelper.refreshBlur()` 前把 `mBlurEffect` 写成 `topbar_blur` 对应半径（只匹配父级是 NestedHeaderLayout 的 helper）；`setBlurParams` 钩子保留 | 启动时 `mBlurEffect` 被写成 0，首次出现靠 `applyBlur(true)` → `refreshBlur()`，此时字段还是 0，所以第一帧没有模糊 |
| `module/src/.../Prefs.java` | 新增 `advanced_mode`（默认 false）、`topbar_divider`（默认 true），`getBoolean()` / `topBarDivider()` / `advanced()` | GUI 开关与发丝线显示开关 |
| `module/src/.../MainActivity.java` | 新增「高级设置」卡片 + 开关；开启后显示「顶栏」卡片（模糊 / 透明度 / 显示顶栏分割线）；重置默认值包含 divider | 需求 3 |
| `module/src/.../Hook.java` | onViewCreated/onResume 改调 `UiTweaks.refreshTopBar()`；install 时调 `UiTweaks.installTopBar(cl)` | 新控制器入口 |
| `module/AndroidManifest.xml` | 16.4 / versionCode 95 | 版本 |
| `module/res/mipmap-*` | 新增 mdpi/hdpi/xhdpi/xxxhdpi，重画全部 `ic_launcher.png` | 需求 4；新图在 `tools/make_launcher_icon.py` |
| `tools/make_launcher_icon.py` | 新脚本：三套候选 + 导出 mipmap 五档 | 图标可复现 |

## 4. 设计思路

- **问题本质**：16.2/16.3 的顶栏控制器是「在 SettingsFragment.onViewCreated 里抓一次 appBar/list，
  挂 OnScrollChangedListener + 500ms 轮询」。设置内切换深浅色会 recreate activity，
  旧 appBar 被丢弃；旧代码的 `BLUR_ATTACHED` 以 appBar 为 key，本应重新挂，但实测新视图上的
  listener 不再收到滚动（旧 ViewTreeObserver 随视图死亡），于是 `update` 永远认为「没滚动」，
  线不画、底色保持实色。用户看到的就是「线消失」。
- **方案 A（未选）**：在 onConfigurationChanged 里手动 re-attach。仍依赖 fragment 生命周期，
  搜索模式/分屏再来一次同样的坑。
- **方案 B（选用）**：把控制器挂到 `NestedHeaderLayout` 这个**类**上，视图重建后新实例自动触发；
  每次回调 fresh 解析 appBar（`action_bar_container`）和真正的 RecyclerView（`scroll_headers`），
  不保存任何跨配置的 View 引用。颜色/半径每帧从 Context 的当前 `Configuration` 读，天然跟随深浅色。
- **发丝线的宿主**：`ActionBarContainer` 是 MIUI 自定义 View，`View.getOverlay()` 返回
  `ViewGroupOverlay` 但它不画（实测洋红 8px 线完全不可见）。改画在父级
  `ActionBarOverlayLayout` 的 overlay 上，坐标用 `appBar.getTop()/getLeft()`，仍然贴在栏的最底边。
- **模糊首发**：`MiuiBlurUiHelper` 的 `mBlurEffect` 初始被写成 0；`refreshBlur()` 只在
  `mApplyBlur` 为 true 时才真正下发。首次折叠时 `applyBlur(true)` 立即 `refreshBlur()`，
  半径仍是 0 → 第一帧无模糊。在 `refreshBlur` 前写好字段，等于把「正确半径」提前到原生下发之前。
- **取舍**：类级 hook 每帧一次 `Prefs` 读取（已有 400ms 缓存）+ 700ms 空闲轮询，换来配置变更零特判；
  发丝线画在父级 overlay，牺牲「只属于 action bar」的洁癖，换取可见性。

## 5. 踩过的坑

1. **发丝线画了但看不见**
   - 现象：日志显示 `divider bounds Rect(0,329-1280,331)`，截图对应像素完全没变。
   - 根因：`ActionBarContainer`（MIUI 自定义）没有画自己的 `ViewOverlay`；加在它上面的 drawable 不参与绘制。
   - 解法：把 drawable 加到父级 `ActionBarOverlayLayout` 的 overlay，用 appBar 的 left/top 换算坐标。
   - 如何避免：以后往 MIUI 原生容器加 overlay，先用亮色 8px 线做可见性探针，不要只看 bounds 日志。

2. **切换深浅色后线不再更新**
   - 现象：`cmd uimode night yes` 后滚动，顶栏保持实色、无发丝线；日志里 `scroll blur attached` 仍只出现一次。
   - 根因：控制器捕获的 appBar/list 属于旧 activity 视图；recreate 后新视图上的监听器没有收到滚动。
   - 解法：类级 hook + 每帧 fresh 解析视图，不再保存跨配置引用。
   - 如何避免：任何「挂在页面视图上的滚动/布局监听」都要问一句：配置变更后这个 View 还活着吗？

3. **`mScrollableView` 不是列表**
   - 现象：`computeVerticalScrollOffset()` 恒 0，控制器一直认为在顶部。
   - 根因：HyperOS 3 的 `NestedHeaderLayout.mScrollableView` 是 `SpringBackLayout` 包装层。
   - 解法：优先按 `com.android.settings:id/scroll_headers` 找 RecyclerView，并对 ViewGroup 子级做一次解包。
   - 如何避免：字段名带 Scrollable 不代表它就是 RecyclerView；以 id / 类名做兜底。

4. **启动时把模糊半径写成 0**
   - 现象：冷启动后第一次折叠，第一帧顶栏没有模糊（旧日志 `native header blur applied: 0 dp`）。
   - 根因：`mBlurEffect` 在未滚动时被写成 0，首次 `applyBlur(true)` 复用它。
   - 解法：在 `refreshBlur()` 的 before hook 里按当前 prefs 重写半径。
   - 如何避免：不要用「写 0 表示关闭」去打断原生「稍后自动应用」的路径。

## 6. 验证与证据

构建（JDK 21.0.2 / build-tools 37.0.0 / android-37.0）：

```
package: name='com.Remo.ilayoutsettings' versionCode='95' versionName='16.4'
dex version : 039
xposed_init : True
module class: True
reorder     : True
size   : 286069 bytes
sha256 : EE68C55D05E9567831C3DE3327544A3D9AB2EBAD426B907EEC9C17329368B3C0
```

真机 25053RT47C / HyperOS 3 / Android 16：

- 深色：`cmd uimode night yes` 后滚动，`y=329–330` 采样 `(58,58,59)`，其上 `(10,10,11)`、其下 `(28,28,30)`；
- 浅色：`y=329–330` 采样 `(222,222,223)`，其上 `(253,253,254)`、其下 `(255,255,255)`——与 16.3 文档一致；
- GUI：高级设置开 → 顶栏卡片出现（模糊 5% / 透明度 2% / 显示顶栏分割线 开）；
  关闭「显示顶栏分割线」后回设置滚动，`y=329–330` 变回 `(253,253,254)`，线消失；再打开恢复 `(222,222,223)`；
- 高级设置关 → 顶栏卡片消失，只剩搜索栏玻璃 + 高级设置卡；
- 图标：应用信息页显示蓝紫渐变玻璃盘 + 白齿轮，五档 mipmap 均打入 APK（286,069 B，比 16.3 的 236,593 B 大约 +49 KB）；
- 未验证：模糊在「折叠动画第一帧」的肉眼效果（screencap 约 200ms，抓不到单帧；只确认半径下发顺序已修正）。

## 7. 未完成 / 下一步 / 风险

- 未发 Release（如需要：`tools/release.ps1 -Version 16.4`）。
- 图标只上了 A 方案；`tools/make_launcher_icon.py` 里 B/C 可一行切换，用户不满意可直接换。
- 发丝线宿主依赖 `action_bar_container` 的父级是一个会画 overlay 的 ViewGroup（当前是 ActionBarOverlayLayout）；
  换 ROM 若父级也自定义绘制，需要再换宿主（可退回根 content overlay）。
- 高级设置开关只影响 GUI，不做权限/作用域校验；「只在设置里加了高级设置」如果用户原意是别的语义，
  需要再对齐一次。
