# 架构与 Hook 链路

> 状态：**现行** · 对应版本：16.7 (98)
> 源码：`module/src/com/Remo/ilayoutsettings/`

## 1. 从加载到生效

```
LSPosed
  └─ Module.handleLoadPackage()            // xposed_init 指向 com.abradant.iossettings.Module
       └─ Hook.install(classLoader)        // 只在 com.android.settings 进程里跑
            ├─ SettingsFragment            // 首页
            │    ├─ buildAdapter           → HeaderData.apply() + BottomSearch.install()
            │    ├─ onViewCreated          → BottomSearch.install() + ForceBlur.forceHeaderBlur() + UiTweaks.refreshTopBar()（顶栏底色/模糊/发丝线）+ HeadsetWatcher.attach()
            │    ├─ onResume               → HeaderData.apply() + BottomSearch.install() + HeadsetWatcher.attach()
            │    └─ onPause / onDestroy    → HeadsetWatcher.detach()
            ├─ SubPageIcons.install()      → PreferenceFragmentCompat#onCreate/onViewCreated/onResume
            ├─ AirplaneSwitch.install()    → MiuiSettings$HeaderAdapter#onBindViewHolder
            ├─ HeaderOverlay.install()     → HeaderAdapter#onBindViewHolder（更新卡角标 / 耳机/iCloud 图标）
            ├─ CardFix.install()           → HeaderAdapter#onBindViewHolder + ResourcesImpl#loadDrawable
            ├─ SubPageCards.install()      → androidx Preference#onBindViewHolder
            ├─ WirelessPage.install()      → MiuiWirelessSettings#onViewCreated/onResume
            ├─ AccountTextFix.install()    → TextView#setText
            ├─ AppearanceTweak.install()   → MiuiDisplaySettings#onViewCreated/onResume
            ├─ SubPageTopBar.install()     → SettingsActivity / SettingsBaseActivity#onResume/onDestroy（二级页顶栏 + 发丝线）
            └─ ForceBlur.install()         → HyperMaterialUtils / MiuiBlurUtils
```

设计上所有入口都包在 `try/catch(Throwable)` 里：**单个 hook 失败只打日志，不能让设置进程崩**。

## 2. Hook 点总表

| 目标类 | 方法 | 目的 | 实现类 |
| --- | --- | --- | --- |
| `com.android.settings.SettingsFragment` | `buildAdapter` | 首页 header 列表建好后立刻重排/重分组 | `HeaderData` |
| 同上 | `onViewCreated` | 原生搜索条 `mAnchorView` 此时才赋值，之后再装底部栏 | `BottomSearch` |
| 同上 | `onResume` | MIUI 可能刷新 header，回来后再排一次 | `HeaderData` |
| 同上 | `onPause` / `onDestroy` | 注销 `AudioDeviceCallback` | `HeadsetWatcher` |
| `androidx.preference.PreferenceFragmentCompat` | `onCreate` / `onViewCreated` / `onResume` | 二级页每次进入都补图标 | `SubPageIcons` |
| `MiuiSettings$HeaderAdapter` | `onBindViewHolder(HeaderViewHolder,int)` | 控制中心行塞开关（HyperiOS 时用自有 `GlassToggle`）；末行清理主题 drawable | `AirplaneSwitch` / `CardFix` |
| 同上 | `onBindViewHolder` | 更新卡右侧红色「1」角标；耳机 / iCloud 行强制设置模块图标 | `HeaderOverlay` |
| `ResourcesImpl` | `loadDrawable` | 末行绑定时把四个主题 drawable 换成透明图 | `CardFix` |
| `androidx.preference.Preference` | `onBindViewHolder` | 二级页每组最后一行去双层圆角 | `SubPageCards` |
| `MiuiWirelessSettings` | `onViewCreated` / `onResume` | 删飞行模式行、补「隔空播放与连续互通」 | `WirelessPage` |
| `android.widget.TextView` | `setText` | 账号副标题兜底改成 iOS 文案 | `AccountTextFix` |
| `MiuiDisplaySettings` | `onViewCreated` / `onResume` | 外观页预览加卡片底 | `AppearanceTweak` |
| `com.android.settings.SettingsActivity` / `core.SettingsBaseActivity` | `onResume` / `onDestroy` | 二级页顶栏：同款半透明底色 + 原生模糊、`ActionBarOverlayLayout` overlay 模式、列表 padding、发丝线 | `SubPageTopBar` |
| `HyperMaterialUtils` / `MiuiBlurUtils` | 多个开关方法 | 强制开启原生模糊 | `ForceBlur` |
| `MiuiBlurUiHelper` | `setBlurParams(int[],int[],int)` | 把顶栏原生模糊半径换成 `topbar_blur`（只匹配 `NestedHeaderLayout` 的 over-bg view） | `ForceBlur` |

> 类名或方法名随 MIUI 版本变化时，`findClassIfExists` 返回 null，只打一条日志；这是有意的「软失败」。

## 3. 首页重排（`HeaderData`）

这是整个模块最核心、也最绕的一段。MIUI 的首页 header 列表是 `SettingsFragment.mHeaders`（`List<PreferenceActivity$Header>`），miuix 的 `CardGroupAdapter` 按每个 `Header.groupId` 决定哪些行属于同一张卡片。

**流程**（`HeaderData.apply`）：

1. 反射读 `mHeaders`；
2. `parse()` 把每个 header 包成内部 `Item`，拿到 `id` 资源名、标题、原下标；
3. `assign()`：按 `SLOT_IDS[slot]` 里的资源 id 别名逐槽匹配，第一个存在的赢；没匹配上的再按标题（`SLOT_TITLES`）兜底；
4. `overrideSlots()`：处理「同一个 id 在不同机型上归属不同卡片」的特例（如把某些行塞进指定卡片）；
5. `addAirplaneRow()`：在「网络」卡片最前面**合成**一条飞行模式行（系统原生没有这个 entry）；
6. `addFocusModeRow()`：在「屏幕时间」正上方**合成**「专注模式」行（`android.settings.ZEN_MODE_SETTINGS`，
   图标取设置自己的 `ic_do_not_disturb_mode_settings`）；`apply()` 每次重排前用 `isSyntheticFocus()` 把上次合成的行删掉，
   避免 resume 越积越多；
7. `addCameraRow()` / `addSearchRow()`：`addIntentRow()` 生成的两条合成行 —— 「相机」锚在「外观」（slot 14）之后、
   「搜索」锚在「墙纸」（slot 11）之后。锚点写法是**共用锚点的 slot + `originalIndex = MAX_VALUE-4`**，
   排序后自然落在锚点行下面，不需要重编号 `SLOT_IDS`；`apply()` 用显式组件（`isIntentRow()`）识别并清理；
7.1 `addUpdateRow()`（16.0，条件）：`Settings.Global.miui_new_version` 非空才合成「有软件更新可用」行，
  单独占 group 1（账号卡之后、网络卡之前），点击打开系统更新 `com.android.updater/.MainActivity`；
7.2 `addAirpodsRow()`（16.0 / 16.1 增强，条件）：`bestHeadsetOutput()` 找耳机类输出设备
  （A2DP 50 > BLE 40 > SCO 30 > 助听器 20 > 有线/USB 10，A2DP 才有真实 MAC），
  标题 = 蓝牙别名 / 产品名，占 group 2；点击用 `headsetIntent()` 打开 MIUI 耳机详情页
  （`miui.bluetooth.action.HEADSET_SETTINGS` + `android.bluetooth.device.extra.DEVICE` +
  `COME_FROM=MIUI_BLUETOOTH_SETTINGS` → `MiuiHeadsetActivity`），没有蓝牙设备时退回蓝牙设置；
7.3 `addICloudRow()`（16.0）：直接排在「Game Center」之后（同 group 7、slot 28），
  跳 `com.xiaomi.action.MICLOUD_MAIN`（失败退回显式 `MiCloudMainActivity`）；
  这三条都用 `mark(header, tag)` 往 `Header.extras` 写标记，`apply()` 靠 `isMarked()` 识别并清理；
8. `ensureInterconnection()`：设备有 MiLink 服务但列表里没有对应 entry 时补一条；
9. 按 `(group, slot, 原下标)` 排序；
10. 给每个 header 写 `groupId = 101 + group`（`GROUP_ID_BASE`），让 miuix 画出卡片；
11. 回写 `mHeaders`（不可变列表时改为替换字段 + `mHeaderAdapter.mHeaders`），再 `notifyDataSetChanged` / `updateGroupInfo` / `invalidateItemDecorations`。

**为什么不用自绘**：卡片圆角、间距、按压反馈、深色模式全在 miuix 的 `CardItemDecoration` 里。改 `groupId` 就能白嫖这一整套，自绘反而要重写一遍且容易和系统动画打架。

卡片的 `group` 号（0~9）与内容对应关系见 `HeaderData.SLOT_GROUP`，首页视觉顺序见 [版本记录 · 首页顺序](07-changelog.md#1-首页顺序)。

### 3.1 条件卡片与角标（`HeaderOverlay`）

- 更新卡（group 1）与耳机卡（group 2）用的是 `SLOT_GROUP` 里没占用的两个组号，排序天然落在
  账号卡（0）之后、网络卡（3）之前；iCloud 复用 Game Center 所在的 group 7。
- 原生 header 行没有「角标」位，所以 `HeaderOverlay` 把红色圆点 `TextView`（24dp / 13sp 白粗体）
  塞进 `android.R.id.widget_frame`（同样处理 LinearLayout / FrameLayout / RelativeLayout 三种父容器，
  `marginEnd` 8dp 给箭头留位）。
- 判断「这行是不是我们的」不靠标题（耳机名会变），而是 `Header.extras` 里的 `ios_synthetic` 标记；
  行被回收成普通行时按 tag 把角标摘掉。
- **16.6 起「通用」行共用同一颗角标**：MIUI 自己在「通用」上画一个蓝色「1」(`com.android.settings:id/text_right`)，
  模块把它隐藏、换成同样的红角标，并复用该行原生 `arrow_right` 的 drawable 在角标右侧补一个箭头
  （原生箭头在这类行里是 GONE 的）。`widget_frame` 在 header 行里是**竖直** LinearLayout、还可能带一个空占位，
  所以角标会贴到底部；模块在 `OnPreDraw` 里把同帧其它子 View 全部 GONE 掉，角标才和箭头齐平。
- 原生角标是 **bind 之后才淡入** 的，`onBindViewHolder` 里读到的往往是空文本；因此替换逻辑挂在
  `OnPreDraw`（`HeaderOverlay.ensureGuard`）里，等数字出现再 `post` 一次替换。
- **更新卡标题左移（16.6）**：合成行没有 icon，MIUI 仍按图标槽缩进标题；模块在预绘制里把标题容器
  `translationX` 平移到 16dp 内边距，与右侧箭头留白对称（`HeaderOverlay.shiftTitle`）。
- 耳机 / iCloud 的图标除了写 `Header.icon` 之外，还会在 bind 之后直接 `setImageDrawable`：
  MIUI 的 adapter 有时优先读 `iconRes`，只写 `icon` 会渲染成空行。
- **图标来源（16.2）**：`HeaderData.cardDrawable()` **先取模块内置**的
  `ic_cloud` / `ic_sub_airpods`（16.2 已替换为用户提供的主题资源包 PNG），
  模块资源取不到才回退 `com.android.settings` 的同名 drawable；
  日志 `card icon <name> <- module (supplied theme)` 或 `<- settings (module copy unavailable)`。
  16.1 的顺序相反，第三方主题的同名资源会覆盖用户指定的图标，16.2 已反转。
- **即时刷新（16.1）**：`HeadsetWatcher` 在 `onResume`/`onViewCreated` 注册
  `AudioManager.registerAudioDeviceCallback`，耳机类设备增减时 350ms 防抖，然后
  `HeaderData.refresh(fragment)`（先 `LAST_ORDER.remove(fragment)` 再 `apply()`，
  否则「列表没变」的早退分支会让新卡永远加不进来）。`onPause`/`onDestroy` 注销。
- 调试用：`settings put global ilayout_fake_headset "Test Buds"` 可以在没有耳机时预览耳机卡
  （只影响标题，不产生蓝牙设备）；`settings put global ilayout_fake_headset_address <MAC>`
  可以指定详情页要打开的耳机；`settings delete global …` 恢复真实检测。

## 4. 底部搜索栏（`BottomSearch` / `SearchGlass` / `SearchHost`）

1. 找到首页的原生搜索条（`header_view` / `mAnchorView`），**隐藏但保留**；
2. inflate 系统的 `miuix_appcompat_search_stub` 作为底部浮层（失败则退化成自绘胶囊）；
   **16.6 起 stub 只提供布局和点击外壳**——`inputArea` 的 9-patch 背景会被清掉；
3. 浮层尺寸：高 50dp，左右 8dp，底部 12dp；拿到导航栏 inset 后改为 `14dp + nav`；
4. 列表底部 padding 同步加上 `搜索栏高 + nav + 24dp`，避免最后一张卡片被挡；
5. 点击浮层 → 先播 120ms 出场动画，再调原生 anchor 的 `performClick()` → 走 MIUI 自己的搜索 ActionMode；
   搜索界面出现时浮层隐藏；退出搜索时同一路径反向播放（`animateBar`，220ms 滑动 + 缩放 + 淡入淡出）；
   **16.1 时序**：`onDestroyActionMode` 只隐藏底部栏，不给它出场——那一刻 MIUI 的顶部搜索框还在收起；
   给 `miuix.view.SearchActionMode` 挂一个 `ActionModeAnimationListener` 动态代理，
   收到 `onStop(open=false)`（收起动画结束、`mIsInActionMode` 已为 false）后才播底部栏进场，
   另有 700ms 兜底。`isSearching()` 也改成优先读 `mIsInActionMode`：原来的
   `search_result_ll` 探测在首次布局后永远为真，害得从二级页返回时底部栏被误隐藏；
6. `SearchGlass` 负责视觉层（16.6 重做）：
   - 抓窗口（跳过自身子树）→ **1dp 模糊**（`RenderEffect`）→ `RuntimeShader` 折射（默认 **12dp 厚度 / 19dp 力度**）；
   - **不再画 MIUI 的 `miuix_appcompat_search_mode_edit_text_bg_light/dark`**：玻璃自己画半透明底色
     （深色白 10%、浅色浅灰 `#E6E6EB` 50%），并做上下等亮的光边（AGSL 里用 `abs(grad.y)` 选边，
     `rimAlpha=0.20`、`rimWidth=1dp`）；内阴影已删除；
   - 轮廓是 Kyant Shapes 的 **G2 连续曲率胶囊**（`G2_CAPSULE` 控制点 + `buildG2Capsule`），
     折射 SDF 仍是圆角矩形（胶囊下差 <1px）；
   - 胶囊的可见矩形不再靠 9-patch 的 padding：install 时保存 drawable、先清背景，首帧 `OnPreDraw`
     里用保存的 drawable 按真实尺寸渲染后量像素（`SearchGlass.paintedBounds`），再 `setPillVisibleRect()`；
   - **抓图在 `OnPreDraw` 里做，只有抓到的位图与前一张不同才 `invalidate()`**；
7. 图标/麦克风（16.6）：搜索 ImageView 固定回 44×24dp，内容框 14dp、左 21dp/右 9dp；
   麦克风是本模块加的 12×16dp ImageView（右距 36dp），`PorterDuff.SRC_IN` 按深浅色染色；
   点麦克风 → `startForegroundService(com.miui.voiceassist/.VoiceService)`，
   action `com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST`，与电源键长按同一个
   `voice_assist_root` 悬浮窗（冷启动约 4–5s）；
8. 逐级兜底：完整 AGSL → 只有模糊 + 底色 + 平直上下光边（`drawFlatRim`）→ 半透明 G2 胶囊
   （`shaderUnavailable` 只置一次，不会每帧重试抛异常）。`SearchHost` 在抓图那一帧跳过自身子树，
   避免把搜索栏自己拍进背景里。

> 抓图 + 两个 RenderEffect 是整份代码里性能最敏感的部分，改之前先读 [设计思路 · 2.4 玻璃](05-design-notes.md)。
> 搜索栏挂在 `android.R.id.content` 上，**不在滚动列表里**：列表滚动不会弄脏它，所以必须自己判断
> 「背景变了」再重绘（见 [坑 F6](06-pitfalls.md)）。

### 4.1 飞行模式开关（`AirplaneSwitch`）

- 行是 `MiuiSettings$HeaderAdapter` 合成出来的，`widget_frame` 里原本只有 `text_right`；
- **未装 HyperiOS**：inflate `miuix_preference_widget_switch`（`SlidingSwitch`）加到 `widget_frame`，
  用 `CENTER_VERTICAL` + 少量 `translationY` 校正；
- **装了 HyperiOS**：HyperiOS 的 `LiquidTogglesHook` 会 hook `SlidingSwitch#onDraw`，
  `USE_LIQUID_GLASS_TOGGLE=true` 时直接 `LiquidToggleHost.ensure(view)` 并跳过原生绘制；
  host 画在 `widget_frame` 的坐标系里，并在首帧缓存开关的 box —— `setTranslationY` 和 `offsetTopAndBottom`
  都推不动它，而 MIUI 的 `widget_frame` 把 95px 高的开关放在 156px frame 的底部，于是开关下沉约 9dp。
- **15.2 的解法**：不再自绘（15.0 的 `GlassToggle` 已停用），改为平移**父容器 `widget_frame`** 的 `translationY`；
  canvas 随父容器移动，hook 画在里面的开关跟着走。目标位置取标题视觉中心，实测 -30px 后旋钮中心 945 = 标题中心 945；
- **15.3 图标**：优先 `context.getDrawable(com.android.settings:drawable/ic_airplane_mode_settings)`，
  主题覆盖和深浅色由资源系统处理；资源缺失才回退模块 `ic_plane_dark/light`。行布局变化时按 `uiMode` 重新解析一次；
- 行被回收成普通 header 时移除多出来的开关视图，并恢复 `widget_frame` 可见。

### 4.2 顶栏滚动模糊 / 透明度 / 发丝线（`UiTweaks` / `ForceBlur`）

- **16.4 起控制器挂在类上**：`UiTweaks.installTopBar(cl)` 在 `NestedHeaderLayout` 的
  `onScrollingProgressUpdated` / `onAttachedToWindow` / `onConfigurationChanged` 上挂钩，
  每次回调调用 `refreshTopBar()`；设置内切深浅色会 recreate fragment 视图，旧实现
  （拿一次 appBar/list 挂 `OnScrollChangedListener` + 500ms 轮询）会失联，线就不画了；
- 每次刷新都 fresh 解析视图：`action_bar_container` 用根视图 `findViewById`，
  滚动源优先 `com.android.settings:id/scroll_headers` 的 RecyclerView；
  **不要用 `NestedHeaderLayout.mScrollableView`**——HyperOS 3 上它是 `SpringBackLayout` 包装层，
  `computeVerticalScrollOffset()` 恒 0（必要时对它的子级做一次解包兜底）；
- **16.5 起底色与模糊常开**：底色始终是按 `topbar_alpha`（0–100%，16.3 默认 2）算出的透明色，
  模糊半径始终是 `topbar_blur`（默认 5%），不再按「是否滚动」切换。旧逻辑回到顶部会把底色写成
  实色（不透明黑/白）、半径写 0，而头部折叠过程中列表还没开始滚动（offset=0），栏就是一层不透明
  实色——账户卡滑到栏下时被硬切一刀，看起来「经过小米账户没有模糊」；
- `MiuiBlurUtils.setBackgroundBlur(view, radiusPx)` 的第二个参数是**像素**（内部按 400px 截断），
  按 `density` 从 dp 换算，`setBackgroundBlurRadius` 用同一个值；**底色变化时要重新下发一次模糊**
  （`setBackgroundColor` 会盖掉 blur 层），`APPLIED_BG` / `APPLIED_APPBAR_BLUR` 两个缓存负责去重；
- **允许内容画到栏下**：MIUI 自己的 over-bg 还没出现时会给滚动容器 `setClipBounds`，内容被裁在
  栏底边。`refreshTopBar()` 与 `updateOverBgState()` 之后都把 `mScrollableView` / `scroll_headers`
  的 clip 清掉，内容才会真的滑进栏底下被模糊（否则「透明栏」下面什么也没有）；
- **只作用首页**：`refreshTopBar()` 先判断 `header.isAttachedToWindow() && header.isShown()`。
  二级页在前台时首页 header 仍 attached 但不可见，而 `action_bar_container` 是 activity 共享的，
  不拦住的话二级页的栏会被套上首页的透明底色 + 模糊；
- **模糊真正可调**：可见的模糊来自 `NestedHeaderLayout.mOverBgView` 的 `MiuiBlurUiHelper`，
  半径由主题 token 固定。`ForceBlur` hook `MiuiBlurUiHelper.setBlurParams`，只对父级是
  `NestedHeaderLayout` 的 `mViewApplyBlur` 把半径换成 `topbar_blur`；16.4 还在
  `refreshBlur()` 前把 `mBlurEffect` 预写成同一半径——启动时该字段是 0，首次折叠的
  `refreshBlur()` 会复用它，不预写就会第一帧没模糊。`topbar_blur` 是 **0–100%**，
  内部映射到 0–150dp（MIUI 在 400px 处截断，2.75x 屏约 145dp）；默认 **5% / 2% 透明度**；
  16.1 的旧值（>100）读取时按 150dp 折算，110dp → 73%，不会归零。
- **发丝线跟着栏一起出现**：显示条件 = 列表滚动超过 4dp **或** 栏已从大标题高度收缩
  （`OPEN_BAR_HEIGHT` 记下见过的最大栏高，当前高度比它小 1dp 即视为折叠中），
  回到顶部、栏恢复原高后隐藏。这样折叠动画里「栏开始压住内容」的瞬间线就在，
  不再等头部完全收起。线画在 `action_bar_container` 的**父级** `ActionBarOverlayLayout` 的
  overlay 里（0.5dp `ColorDrawable`；MIUI 的 `ActionBarContainer` 不画自己的 `ViewOverlay`，
  直接加在它上面不可见，实测用洋红 8px 探针确认）。颜色每帧按当前 `uiMode` 取：
  浅色黑色 12%（`0x1F000000`），深色中性灰 30%（`0x4DAAAAAA`，16.3 由白 15% 改来）；
  坐标用 `appBar.getLeft()/getTop()` 换算，仍贴在栏的最底边。
- **高级设置（16.4 引入，16.6 删除）**：16.4 的 `Prefs.ADVANCED`（`advanced_mode`，默认 false）
  曾控制 `MainActivity` 是否构建「顶栏」卡片；16.6 删除这个总开关，顶栏卡片常显。
  `Prefs.TOPBAR_DIVIDER`（`topbar_divider`，默认 true）仍控制发丝线是否绘制。
- 15.3 用**设置进程的 classloader** 解析 `miuix.core.util.MiuiBlurUtils`。LSPosed 把模块类放在独立 loader 里，
  之前的 `UiTweaks.class.getClassLoader()` 会 `ClassNotFoundException`，所以滑杆写进去了但从未生效；
- 16.4 仍保留 700ms 的空闲轮询（`ensurePoll`，`attach` 时启动、detach 时停），
  让 GUI 滑杆改完回到设置后即使不滚动也能重新套用新值；滚动过程中则完全由类钩子驱动。

### 4.3 二级页顶栏（`SubPageTopBar`，16.7）

二级页（`Settings$WifiSettingsActivity` 这类）是普通 `ActionBar` Activity，不走 `NestedHeaderLayout`，
所以首页那套顶栏逻辑完全够不着它。`SubPageTopBar.install(cl)` 在 `SettingsActivity` /
`SettingsBaseActivity` 的 `onResume` 上挂钩，对 `com.android.settings:id/action_bar_container` 做四件事：

1. **半透明底色**：与首页同一公式 `(alpha << 24) | (dark ? 0x000000 : 0xF2F2F7)`，
   alpha 来自 `topbar_alpha`；`APPLIED_BG` 去重，滚动帧里只做比较；
2. **原生模糊**：`MiuiBlurUtils.setBackgroundBlur(bar, px)` + `setBackgroundBlurRadius`，
   半径 = `topbar_blur` 按 density 换算的像素（该 API 收的是 px，不是 dp）；底色变了必须重发一次
   （`setBackgroundColor` 会盖掉 blur 层），`APPLIED_BLUR` 记上次值；
3. **让内容滑进栏下**：二级页本来 `list.top == bar.bottom`，只清 clip 没用。反射调
   `ActionBarOverlayLayout.setOverlayMode(true)` 让内容铺到窗口顶部，再给列表 `paddingTop = 栏高`
   并 `setClipToPadding(false)`；`padListUnderBar` 只做一次（`LIST_PADDING` 记录），
   否则滚动中栏高变化会把 padding 越加越大；
4. **发丝线**：直接复用 `UiTweaks.applyDivider(appBar, color, visible)`（16.7 起包内可见）。
   显示条件 = 列表 `canScrollVertically(-1)`（第一行已经滑到栏下）；**不要用
   `computeVerticalScrollOffset()`**——部分 MIUI 列表恒 0。线宿主是栏的父级
   `ActionBarOverlayLayout` 的 `ViewOverlay`（`ActionBarContainer` 自己的 overlay 不绘制），
   颜色每帧按 `uiMode` 取：浅色黑 12%、深色中性灰 30%，并受 `topbar_divider` 开关控制。

视图在 `onResume` 后 post 两次（activity 自己的 resume 工作、MIUI 重建折叠标题都在其后），
滚动回调只重算发丝线（`clearListClip` + `applyDivider`），不做整套 refresh。

## 5. 二级页图标（`SubPageIcons`）

- 入口是 `PreferenceFragmentCompat`（所有二级页的公共基类）；
- 遍历 preference 树，对**还没有图标**的行 `setIcon(...)`；
- 图标从 `res/drawable-xxhdpi/ic_sub_*.png` 按 `KEY_ICONS` 映射取，key 不认识时用标题关键词兜底；
- 图标由 `tools/make_subpage_icons.py` 从 `subpage-icons.png` 切图生成，改图标要重跑脚本。

## 6. 卡片修正（`CardFix` / `SubPageCards`）

MIUI 会给每一行再叠一层「卡片形状」的 9-patch 背景/前景，画在 miuix 卡片装饰之上。每组最后一行就会露出**双层圆角**，并让最后一个图标看起来偏大。

处理方式：在该行 bind 的那一瞬间，把四个主题 drawable（`miuix_preference_item_background_*`、`miuix_recyclerview_card_item_press_fg_*`）替换为透明图，bind 完再恢复。首页在 `HeaderAdapter` 上做，二级页在 `Preference#onBindViewHolder` 上做。

## 7. 已废弃 / 未挂载的类

以下类仍在源码里，但 `Hook.install` **没有调用**，属于后续实验版本残留：

| 类 | 原用途 | 状态 |
| --- | --- | --- |
| `GlassToggle` | 飞行模式行的液态玻璃开关 | **在用**（HyperiOS 安装时的开关实现） |
| `IntroCard` | 二级页顶部 iOS 大标题卡片 | 未挂载 |
| `AboutIos` / `AboutExtras` | 替换「关于本机」为 iOS 页 | 未挂载（互相引用，整条链死） |
| `TitleFix` / `TitleOverlay` | 强制二级页标题常显 | 未挂载 |
| `PrefsBridge` | 给搬进来的 lattice 类读偏好 | 未挂载 |
| `SearchGlass` / `SearchHost` | 底部搜索的玻璃层 | **在用**（`BottomSearch` 引用） |

清理计划见 [路线图](09-roadmap.md)。
