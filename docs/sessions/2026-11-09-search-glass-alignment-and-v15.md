# 搜索栏玻璃对齐与 v15.0（2026-11-09）

状态：部分完成（核心修复已上机，收尾参数仅日志验证）
关联版本：14.9 (84) → 15.0 (85)
关联提交：本次会话提交（hash 见 `git log`）

## 1. TL;DR

飞行模式开关下沉不是布局写错，而是 **HyperiOS 的液态开关 hook 接管了 `SlidingSwitch.onDraw`**：它用 Compose host 按开关的 layout box 绘制，MIUI 的 `widget_frame` 又把开关放在底部，所以 `setTranslationY` 对可见画面无效。HyperiOS 存在时改用模块自有的 `GlassToggle`（普通 View，不在 HyperiOS 的 hook 名单里）加到行根，开关恢复居中。

搜索玻璃的遮罩改为 **把主题胶囊 drawable 渲染成位图、取实际像素边界**（主题 9-patch 的透明边距烘在 PNG 里，`getPadding()` 返回 false），先取边界再把原生 fill 降到 50% alpha；模糊默认 1dp、折射 24dp/30dp。产出 **15.0 (85)**，应用名改为 **iLyayot**。

## 2. 对话重点

- **用户要求（原话要点）**：
  - 「图1的飞行模式开关，是因为开了 hyperios 的 hook 液态玻璃的原因导致开关下沉」；
  - 「图2是搜索栏的液态玻璃层错位和宽度宽了的问题」；
  - 「玻璃的模糊调成 1，折射强度拉大」；
  - 「请帮我测试，并产出 15 版本，应用标题改成和我 GitHub 仓库一样的名字」。
- **明确决策**：
  - 检测到 `dev.hyperios.app` 时，飞行模式行不再注入 `SlidingSwitch`，改用 `GlassToggle`；
  - 未安装 HyperiOS 的机器保留原生 `SlidingSwitch` 路径，不改变原有行为；
  - 搜索玻璃遮罩不再用视图边界 + `getPadding()`，改为测量「背景实际画出来的像素」；
  - 原生 fill 不删除，只降到 `0x80`，既能让透镜透出来，又保留主题胶囊的颜色/对比度；
  - 版本号 15.0 / 85，`app_name` 与 `MainActivity` 标题统一为 `iLyayot`（GitHub 仓库名）。
- **范围外但被提到的事**：没有改 HyperiOS 自身；没有清空原生搜索栏背景；没有做参数 GUI。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../AirplaneSwitch.java` | 新增 `hasHyperios()`；HyperiOS 存在时走 `injectGlassToggle()`，把 `GlassToggle` + 一个 weight spacer 加到 `itemView` 行根，frame 隐藏；回收成普通行时 `removeGlassToggle()` 并恢复 frame | HyperiOS 的 `LiquidTogglesHook` 只 hook `SlidingSwitch` 等类；普通 View 不在名单里，位置由行布局决定，不再被 host 拉到底部 |
| `module/src/.../AirplaneSwitch.java` | 原生路径补 `setBaselineAligned(false)`、`setPadding(0)`、显式 `CENTER_VERTICAL` LayoutParams、`normalizeFrame()` + 多次 delayed `recenter()` | 无 HyperiOS 时的兜底；也记录了 MIUI `widget_frame` 会把子 View 放到底部的现象 |
| `module/src/.../GlassToggle.java` | 打开行时旋钮霜化从 25% 白提高到 70% 白（深色） | 原生白色旋钮被替换后，玻璃旋钮在深色卡片上太灰；提高对比度更像 iOS 开关 |
| `module/src/.../SearchGlass.java` | 新增 `paintedBackgroundBounds()`：把背景 drawable 画到 ARGB 位图，按 alpha ≥ 72 取边界；`computeMask()` 用该边界做胶囊；删掉 `(h-mask.height())/2` 手调居中；测完边界后再把背景 alpha 设为 `0x80` | 主题 9-patch 的左右透明边距是烘进 PNG 的，`getPadding()` 拿不到；按视图边界裁会宽一圈并上移 |
| `module/src/.../BottomSearch.java` | 只保留 `glass.setMaskView(pill)`；移除 `softenPillFill()` 与背景 dump 诊断 | 测量与降 alpha 必须在玻璃第一次绘制时按正确顺序做：先量不透明边界，再降 alpha |
| `module/src/.../Prefs.java` | `DEFAULT_GLASS_BLUR` 5 → **1**；`DEFAULT_REFRACTION_HEIGHT` 18 → **24**；`DEFAULT_REFRACTION_AMOUNT` 10 → **30** | 用户要求「模糊调成 1，折射强度拉大」 |
| `module/res/values/strings.xml` | `app_name` = `iLyayot`，`module_desc` 带 iLyayot | 应用标题与 GitHub 仓库一致 |
| `module/AndroidManifest.xml` | `versionCode 85` / `versionName 15.0` | 产出 15 版本 |
| `module/src/.../MainActivity.java` | 标题 `iLyayot`，副标题 `v15.0` | 模块内关于页同步 |

## 4. 设计思路

- **开关下沉的本质**：不是「开关 view 没居中」，而是「可见的开关不是这个 view 画的」。反编译 HyperiOS 的 `LiquidTogglesHook` 可以看到：
  它 hook 了 `miuix.slidingwidget.widget.SlidingSwitch#onDraw`，`USE_LIQUID_GLASS_TOGGLE=true` 时直接 `LiquidToggleHost.ensure(view)` 并 `return`，原生 onDraw 被跳过；
  host 是挂在 `widget_frame` 上的 ComposeView，位置取 `view.getTop()`。MIUI 的 frame 把开关放到底部（实测 `top=61 / height=156`，开关高 95），
  所以 `setTranslationY(-30)` 只改了 `getLocationOnScreen`，Compose host 仍按 61 布局 → 看起来永远下沉 30px。
- **方案对比**：
  - A：继续 `setTranslationY` + `OnPreDraw` 每帧纠正。实测 host 不跟 translation 走，无效；
  - B：反射把 HyperiOS 的 `USE_LIQUID_GLASS_TOGGLE` 置 false。会影响 HyperiOS 全局所有开关，越权且脆弱，否掉；
  - C：自己画一个普通 View 开关。`GlassToggle` 已存在，HyperiOS 的 hook 名单只含 `SlidingSwitch` / `SlidingButton` 等，不会碰它；
    开关位置由行 LinearLayout 决定，竖向居中稳定。选 C。
- **搜索栏方案对比**：
  - A：按 `inputArea` 视图边界裁。视图 1152×140，但主题 9-patch 的实际可见胶囊约 1104×123，左右上都有烘死的透明边距；玻璃会宽一圈；
  - B：按 `getPadding()` 内缩。主题 9-patch 的 `getPadding()` 返回 false，拿不到；
  - C：把背景 drawable 渲染成位图，扫描 alpha 边界。能拿到真实可见范围，且兼容换主题/换 ROM；选 C。
- **关键取舍**：保留原生 fill（alpha 0x80）而不是清空。清空在 15.9 有过「搜索栏直接消失」的历史；保留 fill 还能给玻璃一层原生颜色和对比度。
  代价是 fill 的软边与玻璃遮罩之间可能有几像素色差，需要在真机上按阈值微调。

## 5. 踩过的坑

### 坑：`setTranslationY` 改了坐标，画面不动

- **现象**：日志 `switch at=898..993 transY=-30`，截图里旋钮仍在 938..1012（原位置）；`getLocationOnScreen` 已经变了。
- **根因**：HyperiOS 的 `LiquidToggleHost` 是按 view 的 **layout box** 绘制 Compose 覆盖层，不跟随 translation；原生 onDraw 又被它跳过。
- **解法**：HyperiOS 存在时换 `GlassToggle`（普通 View），由行布局居中；不再依赖 translation。
- **如何避免**：看到「坐标对了但画面不对」，先确认画面是不是这个 view 画的；hook 别的模块的 onDraw 时尤其如此。

### 坑：主题 9-patch 的透明边距，`getPadding()` 拿不到

- **现象**：玻璃胶囊左右各宽出约 13px、上方高出一截，和原生药丸错位。
- **根因**：主题把 `miuix_appcompat_search_mode_edit_text_bg_*` 换成了带透明边距的 9-patch；透明区域是图片像素，不是 drawable padding，
  `getPadding()` 返回 false，按视图边界裁必然偏大。
- **解法**：`paintedBackgroundBounds()` 把背景画进位图，按 alpha 阈值取最小外接矩形；先量不透明状态，再把 fill alpha 降到 0x80。
- **如何避免**：主题/9-patch 场景不要信 `getPadding()`，直接测像素；阈值要在真机截图上核对（本次 72 能覆盖可见 fill，96 会在左侧露一条原生 fill）。

### 坑：首帧 `getLocationOnScreen` 返回 `Integer.MIN_VALUE`

- **现象**：`recenter` 计算出 `dy=2147483293`，开关被 `translationY` 甩出屏幕，且 layout listener 反复触发。
- **根因**：`post` 在 view 完成 layout 之前跑，`getLocationOnScreen` 填的是 `Integer.MIN_VALUE` 哨兵；没有做合法性检查就 setTranslation。
- **解法**：加 `isAttachedToWindow` / `isLaidOut` / 坐标 `< 0` 检查；`dy` 超过 40dp 视为无效，直接返回并复位异常 translation；
  同时把 `centerInParent()` 改成「gravity 已经正确就不 setLayoutParams」，避免布局自激。
- **如何避免**：所有 `post` 里的几何计算都要先验证 view 已 layout；log 里出现 `-214748...` 立刻当脏数据。

## 6. 验证与证据

```powershell
# 构建
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk ... -BuildTools ... `
  -AndroidJar "...\ios-settings-mod\.tools\platform36\android-36.1\android.jar"

# 装机 + 重启设置
adb install -r dist\IosSettingsLayout-15.0.apk
adb shell am force-stop com.android.settings
adb shell am start -a android.settings.SETTINGS
adb logcat -d -v time -s IosSettings:*

# 校验
aapt2 dump badging dist\IosSettingsLayout-15.0.apk
```

- 产物：`dist/IosSettingsLayout-15.0.apk`，**203,657 B**，
  sha256 `3A80A921ACD695D2852346FB3F5AB9B7A5A4FDDFBBF17AE86ED7C0E8892E2A20`。
- badging：`versionCode='85' versionName='15.0'`，`application-label:'iLyayot'`，
  `xposed_init : True`、`module class : True`、`reorder : True`，签名证书 SHA-256 仍为
  `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c`（与 14.9 相同，可直接覆盖安装）。
- 关键日志（15.0 最终构建，2026-11-09 23:29）：
  ```
  I/IosSettings: glass tuning: blur=1.0 refraction=30.0/24.0 rim=0.5/1.0
  I/IosSettings: airplane glass toggle added to row (HyperiOS-safe)
  I/IosSettings: search glass mask=[17,1][1137,139] view=1152x167 target=LinearLayout 1152x140 visible=[17,1][1137,139]
  I/IosSettings: search glass native fill alpha=0x80 (android.graphics.drawable.NinePatchDrawable)
  I/IosSettings: liquid glass active (lens + rim + inner shadow)
  ```
  `mask` 换算到屏幕是 x=81..1201（1120px），完整覆盖原生可见 fill（约 94..1198），不再出现「玻璃比药丸窄、左边露一条原生 fill」。
- 开关几何（15.0 测试构建）：
  - 修复前：旋钮 bbox 中心 Y=**975**，标题中心 Y=**945**（低 30px）；
  - 修复后（GlassToggle）：旋钮 bbox 中心 Y=**944.5**，标题中心 Y=**945**（对齐）。
- 截图：`docs/assets/row-switch-15.0.png`（GlassToggle 居中）、`docs/assets/search-glass-15.0-dark.png`（深色搜索栏）。
  两张都来自 15.0 测试构建；最终构建只微调了遮罩阈值和右间距，画面结构相同。
- **未验证 / 受限**：
  - 收尾参数（遮罩阈值 72、GlassToggle 右间距 2dp）装进最终包后，设备进入「防误触模式」且屏幕亮度被压到近 0，
    截图全黑，只有日志验证，没有补到最终截图；
  - 浅色模式、120Hz 慢动作、分屏未测；
  - `dumpsys gfxinfo` 性能数据未取到（屏幕黑屏期间没有有效帧）。

## 7. 未完成 / 下一步 / 风险

- 未完成：
  - 最终包的深/浅色截图与像素级对齐复核；
  - `GlassToggle` 的抓图开销（它每帧 `root.draw`）在滚动时的 gfxinfo 数据；
  - HyperiOS 版本差异：当前用「包是否存在」判断，HyperiOS 以后改 hook 名单时可能失效。
- 建议下一步（按顺序）：
  1. 设备屏幕可用后，重拍深/浅色截图；若左侧仍有 4dp 左右光晕，把 `paintedBackgroundBounds` 的阈值从 72 调到 80~88，
     或在 `computeMask` 里按 `mask.width()` 与原生 fill 的差值做一次对称收紧；
  2. 若「原生 fill 0x80 + 玻璃」仍出现双层边，改成 fill alpha=0 + shader tint（`color = mix(color, tint, alpha)`），
     并保留 rim/inner shadow 在最上层；
  3. 用 `dumpsys gfxinfo com.android.settings` 在滚动 3 次后记录 jank；`GlassToggle` 的 `OnScrollChangedListener` 可能每帧抓图，
     必要时去掉滚动重抓，只在动画/状态变化时抓；
  4. 把 HyperiOS 检测改成读其版本/开关状态，避免误伤未启用液态玻璃的 HyperiOS 用户。
- 风险：
  - `GlassToggle` 是普通 View，HyperiOS 若未来把 hook 名单扩到所有 CompoundButton/View 就会再次被接管；
  - 主题 9-patch 换图后 alpha 分布会变，72 的阈值需要重新标定；
  - 原生 fill alpha 0x80 在浅色主题上可能偏淡，需看浅色截图再定。
