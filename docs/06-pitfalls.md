# 踩坑记录

> 状态：**现行** · 对应版本：16.8 (99)
> 规则：每踩一个新坑就追加一条，写清 **现象 → 根因 → 解法 → 如何避免**。旧结论被推翻时保留原文并标「已过时」。

## 最贵的五个坑（先看这五条）

### 坑 1：改完没生效——设置进程还活着

- **现象**：重新装模块后打开设置，界面完全没变，日志里连 `hooks installed` 都没有。
- **根因**：设置进程是常驻的，LSPosed 只在进程启动时注入；不杀进程就还是旧代码。
- **解法**：`adb shell am force-stop com.android.settings`，再打开设置。
- **如何避免**：把它写进自测第一步（[安装](04-install.md)）；反馈问题时先问「强制停止过吗」。

### 坑 2：卡片分组失效——`groupId` 没写进去

- **现象**：顺序对了，但每一行都变成独立卡片，圆角/间距全乱。
- **根因**：miuix 的 `CardGroupAdapter` 只认 `Header.groupId`；MIUI 重建列表时会重置它，或者字段不可写。
- **解法**：重排后逐条 `setIntField(header, "groupId", 101 + group)`，并校验写回值；列表不可变时替换 `mHeaders` + `mHeaderAdapter.mHeaders` 两个字段。
- **如何避免**：`HeaderData.apply` 里已有「顺序没变也要 `updateGroupInfo` + `invalidateItemDecorations`」的分支——别删。

### 坑 3：每组最后一行双层圆角 / 图标偏大

- **现象**：卡片底部出现两层圆角，最后一行图标比其它行大一圈。
- **根因**：主题给每行叠了 `miuix_preference_item_background_*` 与 `miuix_recyclerview_card_item_press_fg_*` 两组 9-patch，画在 miuix 卡片装饰之上。
- **解法**：绑定时把四个 drawable 换成透明图，bind 后恢复（`CardFix` / `SubPageCards`）。
- **如何避免**：不要试图改布局或裁剪父容器，会破坏 ripple。

### 坑 4：底部搜索栏把「自己」拍进玻璃里

- **现象**：搜索栏的玻璃层里出现搜索栏自身的重影/双层文字。
- **根因**：抓图是 `draw(canvas)` 整个 decor，搜索栏也在里面。
- **解法**：抓图那一帧给容器设 `capturing`，让 `SearchHost` 跳过整棵子树；**不要**用 `setVisibility(GONE)`——会触发 layout，直接把抓图帧率打下来。
- **如何避免**：任何「抓自己所在窗口」的实现都要先问：怎么把自己排除掉？

### 坑 5：玻璃卡住 / 整页空白——shader 每帧抛异常

- **现象**：进入设置后搜索栏空白，日志每帧刷同一条异常。
- **根因**：`RuntimeShader` 编译失败后没有兜底，每帧重新构造、重新抛。
- **解法**：构造包 try/catch，失败回退到内置 kyant shader；可选 uniform（`chromaticAberration` / `innerShadow` / `shadowW`）逐个 `setUniform` 并单独吞异常；最外层异常时直接画未模糊的底图。
- **如何避免**：所有「每帧执行」的图形代码，必须有一次性失败标记 + 静态兜底路径。

---

## 其它坑（按主题）

### A. Hook 与进程

| 现象 | 根因 | 解法 |
| --- | --- | --- |
| 部分功能生效、部分没有 | 某个 `findClassIfExists` 返回 null，只影响那一块 | 看 `IosSettings` 日志定位缺哪个类；用标题兜底或加别名 |
| 设置进程崩溃 | hook 里抛异常冒到主线程 | 所有回调 `try/catch(Throwable)`；`XC_MethodReplacement` 也要包 |
| 二级页图标时有时无 | 只在 `onCreate` 注入，页面复用/异步刷新后丢失 | 同时挂 `onViewCreated` / `onResume`（`SubPageIcons`） |

### B. 布局与视觉

| 现象 | 根因 | 解法 |
| --- | --- | --- |
| 底部搜索栏挡住最后一张卡片 | 没算导航栏 inset | 列表 padding 加 `栏高 + nav + 24dp`；栏本身 `bottomMargin = 14dp + nav` |
| 手势条压住搜索栏 | 同上，只加了固定 dp | 用 `WindowInsets.Type.navigationBars()`，兼容旧 API 回退 `getSystemWindowInsetBottom()` |
| 飞行模式开关偏下/不居中 | 只改 `translationY`，父容器类型不同 | `centerInParent()` 按 FrameLayout/RelativeLayout/LinearLayout 设 `CENTER_VERTICAL`，再用屏幕坐标微调 |
| 顶栏模糊不出现 | 主题把 `background_blur_enable` 关了，`HyperMaterialUtils.isEnable()` 为 false | `ForceBlur` 同时改 `isEnable` / `isForceEnable` / `isFeatureEnable` 三个入口 |
| 顶栏模糊滑杆完全没反应 | `Class.forName("miuix.core.util.MiuiBlurUtils", UiTweaks.class.getClassLoader())` 在 LSPosed 下抛 `ClassNotFoundException`（模块类与设置类不在同一 loader） | 改用 `appBar.getContext().getClassLoader()`，并以 `XposedHelpers.findClassIfExists(..., loader)` 兜底 |
| 滚动后 `top bar blur` 只在 true/false 之间切、半径不更新 | 旧逻辑只比较「是否滚动」，且只在 `OnScrollChanged` 时读 prefs；滑杆变化被吞掉 | 同时跟踪半径并每 500ms 轮询 prefs；滚动源优先 `scroll_headers`，`mListView` 取不到时回退 |
| **顶栏模糊滑杆拖了没反应**（16.1） | `setBackgroundBlur(actionBar, r)` 返回 true，但可见模糊来自 `NestedHeaderLayout.mOverBgView` 的 `MiuiBlurUiHelper`，半径被主题 `Blur.ExtraHeavy` 固定；改 action bar 等于在它下面加了一层看不见的 | 16.2 hook `MiuiBlurUiHelper.setBlurParams`，只替换 `NestedHeaderLayout` over-bg 的半径；同时写 `mBlurEffect` 并 `refreshBlur()` |
| 顶栏模糊拖到后半段不变 | `MiuiBlurUtils` 把半径 cap 到 400px，2.75x 屏约 145dp；旧 0–300dp 后半段全饱和 | 滑杆改 0–100%，内部映射 0–150dp；16.3 默认 5%，旧 110dp 折算 73% |
| 默认太实、不像参考图的淡磨砂 | 16.1 默认 110dp / 80% 透明度，白底上看起来就是一条实色栏 | 16.2 默认 30% / 45%；16.3 再降到 **5% / 2%**（近乎全透明，只留发丝线），淡磨砂仍可用滑杆选回 |
| 深色模式顶栏「没有线」 | 16.2 用白 15%（`0x26FFFFFF`）画线，压在 2% 透明度的黑栏上只剩 `#262626`，和黑底几乎同色 | 16.3 深色改中性灰 30%（`0x4DAAAAAA`），实拍 `(58,58,59)`，黑栏/亮内容上都读得出；浅色仍黑 12% |
| 顶栏发丝线在大标题还没折叠时就出现 | 只看 `scroll offset > 4dp`，而大标题要滚过一段才收起 | 读 `NestedHeaderLayout.isHeaderOpen()`，为 true 时隐藏线；回到顶部也隐藏 |
| **画了发丝线但完全看不见**（16.4 排查） | MIUI 的 `ActionBarContainer` 不画自己的 `ViewOverlay`，`getOverlay().add()` 的 drawable 不参与绘制；日志里 bounds 正常 | 加到父级 `ActionBarOverlayLayout` 的 overlay，坐标用 `appBar.getLeft()/getTop()` 换算；调试时先用洋红 8px 探针确认宿主会不会画 |
| **设置内切换深浅色后发丝线消失**（16.4 修） | 控制器把 `appBar`/`list` 抓在 fragment 视图上；切主题会 recreate 视图，旧的 `OnScrollChangedListener` 不再触发，永远认为「没滚动」 | 控制器改挂 `NestedHeaderLayout` 类钩子（`onScrollingProgressUpdated` / `onAttachedToWindow` / `onConfigurationChanged`），每次 fresh 解析视图；颜色每帧从当前 `uiMode` 取 |
| 控制器一直认为列表在顶部 | `NestedHeaderLayout.mScrollableView` 在 HyperOS 3 上是 `SpringBackLayout` 包装层，`computeVerticalScrollOffset()` 恒 0 | 优先按 `com.android.settings:id/scroll_headers` 找 RecyclerView；必要时解包 ViewGroup 的 RecyclerView 子级 |
| 冷启动后第一次折叠顶栏没有模糊 | 启动时把 `mBlurEffect` 写成 0；首次 `applyBlur(true)` → `refreshBlur()` 复用了它 | 在 `MiuiBlurUiHelper.refreshBlur()` 的 before hook 里按 prefs 预写半径（只匹配父级为 `NestedHeaderLayout` 的 helper） |
| **头部折叠时账户卡被栏「切一刀」，没有模糊**（16.5） | 模糊只在 `scrolled` 时下发、静止时栏底色写成不透明实色；折叠过程中列表还没滚动（offset=0，`scrolled=false`），所以栏是不透明 + 0 模糊 | 底色始终用 `topbar_alpha` 的透明色，模糊半径始终用 `topbar_blur`（不按滚动开关）；另外 MIUI 在 over-bg 未出现时会 `setClipBounds` 把内容裁在栏底，要在 `refreshTopBar()` / `updateOverBgState()` 之后清 clip，内容才滑得进栏下 |
| **`setBackgroundBlur` 传 dp，模糊比配置值弱 2.75 倍**（16.5 修） | `MiuiBlurUtils.setBackgroundBlur(View, int)` 的 int 是**像素**（内部 400px 截断），旧代码把 `topbar_blur` 的 dp 值直接传进去 | 按 `density` 换算成 px 再传，`setBackgroundBlurRadius` 用同一个值；改完实测 5%（≈22px）可见明显模糊 |
| **改了栏底色后模糊消失** | `setBackgroundColor()` 会换掉 view 的背景层，把之前挂上的 blur 一起盖掉 | 底色变化时重新下发一次 `setBackgroundBlur`；用 `APPLIED_BG` 判断「底色变了」，别只看半径缓存 |
| **二级页的栏也被套上透明底色 + 模糊**（16.5 防范） | `action_bar_container` 是 activity 共享的；二级页在前台时首页 `NestedHeaderLayout` 仍 attached，轮询还会改它 | `refreshTopBar()` 先判断 `header.isAttachedToWindow() && header.isShown()`，不可见就跳过 |
| 发丝线要等头部完全收起才出现 | 显示条件写死 `scrolled && !isHeaderOpen()`，折叠动画期间 `scrolled` 还是 false | 16.5：条件改为「`scrolled` **或** 栏已从大标题高度收缩」——用 `OPEN_BAR_HEIGHT` 记住见过的最大栏高，当前高度小 1dp 就算折叠中 |
| 耳机 / iCloud 图标被第三方主题换掉 | 16.1 的 `cardDrawable` 是 settings 优先，主题同名 `ic_*` 会赢 | 16.2 改为模块内置优先，settings 只作兜底 |
| `R.string.*` 编译报「找不到符号 R」 | `build.ps1` 只把 `module/src` 传给 javac，aapt2 生成的 `build/gen/**/R.java` 从未参与编译 | 避免在 Java 引用 `R`；标题用 `loadLabel()`，版本用 `getPackageInfo().versionName` |
| ~~搜索栏去色后完全消失~~（**已过时，见 F11–F15**） | 把 `miuix_appcompat_search_mode_edit_text_bg_light/dark` 的 fill 清掉了；它才是可见胶囊 | 15.x 的解法是保留主题胶囊；**16.6 改为不再使用 9-patch**，玻璃自己画底色与光边，见 F11–F15 |
| **16.6：去掉 9-patch 后玻璃变高、图标漂移** | install 时 view 还没布局，`paintedBackgroundBounds()` 直接返回 null，遮罩退回整个 44dp `inputArea` | 保存 drawable → 清背景 → 首帧 `OnPreDraw` 里按真实尺寸量（`paintedBounds`）再 `setPillVisibleRect` |
| **16.6：换 drawable 后搜索图标撑满 44dp** | stub 的 ImageView 是 `wrap_content`，模块 PNG 固有 58dp，view 被撑到 84dp | 把 LayoutParams 固定回 44×24dp，用 padding 做 14dp 内容框 |
| **16.6：上下光边看起来不一样亮** | 底色是上亮下暗的渐变；同一加法高光在更暗的下半边对比更强 | 底色改统一 alpha（0.10），光边上下同权；实测峰值 `#63`/`#62` |
| **16.6：点麦克风后几秒没反应** | `VoiceService` 冷启动要 4–5s 才 add `voice_assist_root` | 点击给触感 + 回弹；用 `dumpsys window` 确认是慢而不是没触发 |
| 外观页预览和卡片之间有空隙 | MIUI 自己加了 margin | 保留原间距，只在预览后补一层同色同圆角的卡片背景（`AppearanceTweak`） |
| 合成行的图标是空的（耳机/iCloud） | MIUI 的 `HeaderAdapter` 在有些版本优先读 `iconRes`，只给 `Header.icon` 不生效 | `HeaderOverlay` 在 `onBindViewHolder` 之后直接 `icon.setImageDrawable(...)` 兜底 |
| 更新卡右侧没有红色角标 | 原生 header 行没有「角标」这个位置 | 自己在 `android.R.id.widget_frame` 里加一个 24dp 圆形 `TextView`（`HeaderOverlay`），按 tag 清理 |
| 角标比箭头低半行（16.6 修复前） | `widget_frame` 是**竖直** LinearLayout，上面还有一个空占位子 View，角标被顶到底部 | 预绘制里把同帧其它子 View 全设 `GONE`，只留角标（`HeaderOverlay.hideOthers`） |
| 换上红角标后偶尔又冒出原生蓝「1」 | MIUI 的角标是 bind **之后**才淡入的，`onBindViewHolder` 里读到的文本还是空的 | 用 `OnPreDraw` guard 每帧检查，数字出现再 `post` 替换一次（`HeaderOverlay.ensureGuard`） |
| 搜索栏进出场时出现两个胶囊 | `install()` 每次 resume 都删旧建新，旧胶囊还在播动画 | 16.0 改为同 fragment / 同夜间模式复用现有胶囊；动画状态用 `animUntil` 互斥 |
| 退出搜索时短暂同时出现顶部搜索框和底部搜索栏 | `onDestroyActionMode` 在 MIUI 收起动画**开始**时就回调，底部栏立刻进场；等顶部框收完（约 200ms）才不重叠 | 16.1：`onDestroyActionMode` 只隐藏；给 `SearchActionMode` 挂 `ActionModeAnimationListener` 代理，`onStop(open=false)` 后进场，700ms 兜底 |
| 从二级页返回后底部搜索栏消失 | `isSearching()` 探测 `search_result_ll`，它首次布局后一直有高度/可见，被误判成「还在搜索」 | 16.1：优先读 `SettingsFragment.mIsInActionMode`（`onPrepareActionMode` 置 true、`onDestroyActionMode` 置 false），视图探测只作兜底 |
| 耳机连上后卡片不出现，必须强制停止设置 | `HeaderData.apply()` 只在 `buildAdapter` / `onResume` 跑，音频设备变化不会触发页面重建 | 16.1：`HeadsetWatcher` 注册 `AudioManager.registerAudioDeviceCallback`，350ms 防抖后 `HeaderData.refresh()`；refresh 必须先 `LAST_ORDER.remove(fragment)` 绕过早退分支 |
| 耳机卡点了只进蓝牙列表，不进电量/功能页 | 16.0 的 intent 是 `ACTION_BLUETOOTH_SETTINGS` | 16.1：`miui.bluetooth.action.HEADSET_SETTINGS` + `DEVICE` + `COME_FROM=MIUI_BLUETOOTH_SETTINGS` → `MiuiHeadsetActivity` |
| 详情页 intent 里的 MAC 是 `00:00:00:00:00:00` | MIUI 音频策略有时给 A2DP 输出占位地址；SCO 条目也没有真实 MAC | A2DP 优先（`headsetPriority`），地址无效时读 `AudioDeviceInfo.mAddress` / `toString()` 的 `addr:` 兜底，仍无效则退回蓝牙设置 |

| **二级页顶栏半透明了，但内容不会滑到栏下**（16.7） | 二级页是普通列布局（`list.top == bar.bottom`），只清 `clipBounds` 也没东西可模糊；`setBackgroundBlur` 不改变布局 | 反射 `ActionBarOverlayLayout.setOverlayMode(true)` 让内容铺到窗口顶部，再给列表加**栏高** `paddingTop` + `clipToPadding=false`；padding 只加一次（`LIST_PADDING`），滚动中栏高变化不能重复加，否则越滚越空 |
| **二级页顶栏没有发丝线**（16.7） | 首页的线挂在 `NestedHeaderLayout` 上；二级页不走这个类，而 `UiTweaks.refreshTopBar()` 在首页不可见时会主动跳过 | `SubPageTopBar` 直接复用 `UiTweaks.applyDivider()`：滚动回调里按 `canScrollVertically(-1)` 更新显隐，宿主仍是栏父级 `ActionBarOverlayLayout` 的 `ViewOverlay`；别用 `computeVerticalScrollOffset()`（部分 MIUI 列表恒 0） |
| **16.7 给浅色搜索栏加灰边后变成「两条线」** | 灰色描边 + 内移的白色高光叠在浅灰胶囊上，白底上读成灰环 + 白环，比 16.6 重 | 按用户反馈整段回退 16.6（`SearchGlass` 与 HEAD 一致）。「浅色纯白页高光不明显」属 16.6 已知问题，未看图确认前不要再自动加灰边 |
| **「我的设备」下方卡片留白很大**（16.8） | `findScrollable` 取的是 DFS 里第一个 RecyclerView；这页真正的滚动容器是外层 `NestedScrollView`，内层 `description_grid` 是 `nestedScrollingEnabled=false` 的非滚动 grid，被加 331px padding 后整张卡被推下去 | `findScrollable` 改成「最外层、可竖向滚动」的容器（含 NestedScrollView / ScrollView；横向 RecyclerView 与 `ios_about_page` 跳过） |
| **进入二级页时顶栏 / 发丝线已经出现**（16.8） | overlay 打开 + padding 改变后，`LinearLayoutManager` 把已 attach 的第 0 行按旧局部坐标重排，`offset = padding - childTop`，`canScrollVertically(-1)` 直接为 true | padding 之后立刻 `scrollToPosition(0)` / `scrollTo(0,0)`，把第 0 行锚回 padding 顶部；别等下一帧 |
| **「通用 / 我的设备」内容滑不进栏下**（16.8） | 页面把列表放在 top == 栏底的 wrapper 里（`miui_version_card_view` 是兄弟），overlay 模式对这份布局无效；`topMargin -= 栏高` 在 miuix FrameLayout 里只加高、不移动 top，子 View 被 `clipChildren` 裁在栏底 | 抬 wrapper（负 margin）+ 给列表/内容加同高 padding；`clipChildren/clipToPadding/clipBounds` 在 `OnPreDraw` 里每帧清一次（MIUI 每次 layout 会重设）；只对含 `miui_version_card_view` 的页面做 |
| **「通用」的顶栏没有模糊**（16.8 修） | 模糊采样的是栏后面**真实绘制**的内容；内容被裁在栏底时栏后面只有页面底色，5% 的模糊看不出来（把半径调到 100px 也一样平） | 先让内容能进栏下（上一条），`setBackgroundBlur` 自然就采到行；不需要另画一层模糊 |
| **设备详情页二次 inflate 丢 padding**（16.8） | 异步设备数据落地后 MIUI 重建了页面视图树，旧树的 padding/抬升随旧 View 一起丢弃（log 里的 View 树 bounds 会突然回到 331） | `watch` 里排 700/1600/3200ms 三次延迟 refresh，每次都重新 `findScrollable` 并对新树套用；`LIST_PADDING` 按 View 记录，天然幂等 |
| **ScrollView 的 padding 被重置**（16.8） | `NestedScrollView` 在 MIUI 的 inset pass 里会重设 padding，加在它自己身上会被清 0 | 加在它唯一的内容子 View（`LinearLayout`）上，padding 随内容一起滚动 |
| **OS4：顶栏模糊整套失效**（17.0） | `ForceBlur` 探测 `HyperMaterialUtils.SUPPORT_HYPER_MATERIAL`，OS4 删了这个字段 → 读到 false 直接 `return`，后面的 `setBlurParams` / `refreshBlur` / `isEffectEnable` hook 一个都没装 | 能力探测改三级（`SUPPORT_HYPER_MATERIAL` 字段 → `mIsSupport` 字段 → `isSupport()`），读不到也继续装 hook；同时强制 OS4 新增的 `hasMaterialPermission(view,pkg,2)` 门槛，否则 `setEnableBlur()` 仍会 `setEnableInner(false)` |
| **OS4：栏折叠后内容进不了栏下，栏下留 175px 空白**（17.0） | OS4 把 `action_bar_container` 从 506px 缩到 331px，但 `NestedHeaderLayout` 没重新布局列表（RecyclerView top 仍是 506），清 `clipBounds` 也没用 | 仅在 SDK≥37 时按「展开栏高 − 当前栏高」对列表 `setTranslationY(-shift)`；不改 LayoutParams，避免和 MIUI 的 nested-scroll offset 冲突。**OS3 列表本来就在栏下，必须门控，否则二次位移** |
| **OS4：`mScrollableView` 字段没了**（17.0） | `UiTweaks.unclip()` 用 `HeaderData.field(header,"mScrollableView")` 清 clip，OS4 该字段不存在 → 永远返回 null | 回退 `call(header,"getScrollableView")`；并沿列表父链清 `clipBounds` + `clipChildren=false` |
| **OS4：退出搜索后原生顶部搜索栏常驻**（17.0） | OS4 在 ActionMode 销毁时把 `mAnchorView` 重新设为 VISIBLE，16.8 只在 install 时设过一次 GONE | `BottomSearch.hideAnchor()` 在 `setBarVisible()` 与每次 global layout（非搜索态）幂等隐藏 |
| **OS4：主题 16.5dp 卡片边距失效**（17.0） | OS4 新增 `updateCardGroupMarginForSmallScreen()`，用设置自己的 `card_group_margin_horizontal`（12dp）`setCardMarginStart/End`，覆盖 miuix 的主题化边距 | `CardMargins` hook 该方法，之后把 `miuix_recyclerview_card_group_margin_start/end` 写回；实测卡片左右 38px → 53px |
| **OS4：搜索栏玻璃背景冻结**（17.0） | OS4 列表在硬件层里，`root.draw(software canvas)` 每帧返回同一帧（滚动 400ms、捕获 101 次只有 1 次「变化」），`backdropChanged()` 永远判「没变」 | SDK≥37 改 `PixelCopy` 抓窗口 surface（诊断 `cap=242 chg=192`）；`pixelCopyInFlight` 防重入，失败保留上一帧 |
| **HyperOS 4 的 logcat main 缓冲区对 shell 不可读**（17.0） | `adb logcat -d` 全空，`logcat -g` 显示 `0 B readable`，模块日志拿不到 | 诊断改走 `uiautomator dump` 读 `content-desc`：把捕获次数/变化次数/硬件加速/backdrop 状态写进 contentDescription，比日志可靠；发布前把 `DEBUG_DIAG` 关掉 |
| **OS4：麦克风图标消失、无法唤起小爱**（17.0） | `styleGlassContent()` 的 `addView(mic, FrameLayout.LayoutParams)` 在 OS4 的 `LinearLayout` stub 上抛 `ClassCastException` 被 catch 吞掉；`VoiceService.startService` 在 OS4 被接受但不弹 `voice_assist_root` | mic 改挂内层 `search_mode_stub` 并按宿主类型构造 LayoutParams（右边距 36dp→23dp 补偿宿主差）；SDK≥37 的唤起改 `ACTION_ASSIST` + `setPackage`（真机焦点切到 `voice_assist_root`），OS3 保留 `VoiceService` |

### C. 版本与回退（本仓库特有）

#### 坑 C1：回退不彻底——源码里还留着后面版本的东西

- **现象**：按 v14.8 描述「已移除 lattice / 液态玻璃」，但源码里 `BottomSearch` 仍有 `LiquidGlassView` 反射分支，dex 里也还有 `GlassToggle`、`IntroCard` 等类。
- **根因**：14.8 是按功能逐项回退重建的，没有版本库快照，靠人工比对，漏掉了死代码。
- **证据**：发布包 dex 123,864 B，源码重编 dex 130,044 B；未挂载类见 [架构](02-architecture.md#7-已废弃--未挂载的类)。
- **解法（未完成）**：清理未引用类与反射分支，或在类注释里显式标注「实验代码，未挂载」。
- **如何避免**：以后每次回退都以「可编译 + dex 体积对比 + 类引用清单」三件套验收；**不要只删调用点**。

#### 坑 C2：发布包和源码不是同一份二进制

- **现象**：`dist/IosSettingsLayout-14.8.apk` 与源码重编产物同名同大小，但 SHA-256、签名证书、dex 大小都不同。
- **根因**：发布包由早期环境/密钥构建，仓库源码是后来重建的。
- **影响**：覆盖安装失败；行为可能有细微差异。
- **处理**：在 [状态](00-status.md) 里公示两个指纹；发新版时固定 keystore 并重新对齐源码。
- **如何避免**：把「发布包必须来自本仓库某次 commit + 记录 sha256」写进发布流程（[agent.md](../agent.md#6-版本与发布规则)）。

#### 坑 C3：README 里的校验数据会过期

- **现象**：`docs/07-changelog.md` 的「7. 校验」写 size 216,015 B、sha256 `1A5B...`，与 14.8 实际包（195,465 B）对不上。
- **根因**：那段是后续版本写的，回退时没同步。
- **处理**：已在文件顶部加「历史全文」说明；当前指纹以 [状态](00-status.md) 为准。
- **如何避免**：校验数据只写在一处（`00-status.md`），其它地方引用链接。

#### 坑 C4：改了包名之后「模块装了但设置没变」

- **现象**：16.0 把包名从 `com.abradant.iossettings` 改成 `com.Remo.ilayoutsettings`，装上后设置毫无变化。
- **根因**：① LSPosed 的启用状态与作用域按包名存储，新包默认是**未启用**；
  ② 旧包如果没卸载，仍然在 hook 设置（两份模块同时生效还会重复插行）。
- **解法**：先卸载旧包，再安装新包，然后在 LSPosed 里重新启用 + 勾作用域，最后强制停止设置。
- **如何避免**：包名变更按「破坏性升级」处理，写进 [安装 · 16.0 迁移](04-install.md#2-160-迁移从-15x-升级才需要) 与 Release 说明。

#### 坑 C5：`miui_new_version` 会被系统更新自己清掉

- **现象**：手动 `settings put global miui_new_version …` 让更新卡出现，过一会儿重进设置卡片又没了。
- **根因**：`com.android.updater` 每次检查完都会写/清这个键（见 `h0.1.v.smali` 的 `SettingsUtil.h` / `putString(..., null)`）。
- **解释**：这不是 bug——更新卡本来就该跟着真实的更新状态走。
- **如何避免**：调试时接受它可能被清掉；需要稳定预览就临时断网或改成假键（未实现）。

### D. 构建与环境

#### 坑 D1：`$ErrorActionPreference='Stop'` 让 javac 的 warning 中断构建

- **现象**：构建在 `[2/6] javac` 后直接退出，报 `NativeCommandError`，但 javac 其实成功了。
- **根因**：PowerShell 5.1 下原生命令写 stderr 会被当成错误；设为 `Stop` 就直接终止。
- **解法**：`$ErrorActionPreference = 'Continue'`，每个原生命令后检查 `$LASTEXITCODE`。
- **证据**：本仓库 `module/build.ps1` 顶部注释。

#### 坑 D2：默认执行策略禁止运行脚本

- **现象**：`& .\module\build.ps1` 报 `UnauthorizedAccess` / `PSSecurityException`。
- **解法**：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1`。
- **注意**：本机没有 PowerShell 7（`pwsh` 不存在），脚本按 5.1 写。

#### 坑 D3：`apksigner` 在受限目录报「拒绝访问」

- **现象**：`apksigner` 输出到某些临时目录时抛 `FileNotFoundException ... 拒绝访问`，同目录用 PowerShell 写文件却正常。
- **解法**：`-OutDir` 指向仓库内或普通用户目录；先手工 `apksigner` 验证一次再排查脚本。
- **备注**：疑似受保护/重定向目录导致 Java 以 `rw` 打开失败。

#### 坑 D4：构建脚本硬编码了开发机路径

- **现象**：换机器构建直接 `throw`，报找不到 JDK/build-tools。
- **根因**：早期脚本把 `apk6ify\.downloads\...` 写死。
- **处理**：现版本已改为「参数 → 环境变量 → 少量兼容路径」三级回退；仍保留开发机路径作为最后兜底，正式使用请传参数。

#### 坑 D5：PowerShell 5.1 读写把 UTF-8 源码改出乱码

- **现象**：用 `(Get-Content x.java) -replace ... | Set-Content -Encoding UTF8` 改完，文件里 `→` `×` `°` 和中文
  注释变成 `鈫?` `脳` `掳` `澶栬`，编译照样过（在字符串/注释里），但 diff 一片红。
- **根因**：Windows PowerShell 5.1 的 `Get-Content` 默认按 ANSI(GBK) 解码 UTF-8，字符在读取时就坏了，写回已经晚了。
- **解法**：`git checkout -- <file>` 还原后重做；需要脚本处理时显式 `-Encoding UTF8` 读、`[IO.File]::WriteAllText`
  （UTF-8 无 BOM）写，或改用编辑器/补丁工具。
- **如何避免**：改完 Java 源码 `git diff` 必须扫一眼非 ASCII 行；本仓库源码统一 UTF-8 无 BOM。

### E. 发布与签名

| 现象 | 根因 | 解法 |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 新旧包证书不同 | 卸载旧包再装；发布时固定 keystore |
| 每次构建签名都变（已修复） | 旧版默认 key 放在 `build/` 里，而 `build/` 每次构建都被清空 | 现版本默认 keystore 在 `.keystore/`，构建不再清理；正式发版仍应用 `-KeyStore` 固定仓库外的密钥 |
| `versionName` 与产物名不一致 | 手改产物名 | 产物名由 manifest 版本号生成，不要手改 |
| `adb install` 新包报 `INSTALL_FAILED_USER_RESTRICTED`（16.0 实测） | MIUI 安全中心拦「ADB 安装新应用」：`com.miui.permcenter.install.AdbInstallActivity` 直接 finish；同包名覆盖安装不受限 | 把 APK 拷到手机手动点装，或开发者选项里允许「USB 安装」；`pm install-create/-write/-commit` 也会被拦，别浪费时间 |
| 自动发版时 Release 正文中文乱码 / 422 | 直接用 `Invoke-RestMethod -Body` 发多行中文 | 先 `ConvertTo-Json -Compress` 写 UTF-8 无 BOM 文件，再 `curl --data-binary @file`（`tools/release.ps1` 已内置） |
| **发出去的 Release 正文中文乱码 + 出现 `{{SIZE}}` 残留**（16.3、16.5 各中一次） | **`release.ps1` 被编辑器改成 UTF-8 无 BOM**：Windows PowerShell 5.1 按 GBK 解码整个脚本，中文模板变乱码，且多字节序列把 `{{SIZE}}` 的第一个 `{` 吃掉，`Replace()` 再也匹配不上 | 重写回**带 BOM 的 UTF-8**（`[IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($true)))`），重跑脚本或用 `PATCH /releases/{id}` 修正文；`git diff` 里确认首行是 `﻿#Requires -Version 5.1`。**16.5 起脚本在 POST 前自检**：模板中文丢失或残留 `{XXX}` 占位符就直接 `throw`，宁可不发也不发乱码（探针用 `[char]0x…` 拼成，避免自查也被同一编码问题带偏） |
| 改 `release.ps1` 后如何自查 | 只看脚本能否运行不够——脚本能跑，但正文已错 | 用 `[regex]` 抽出 `@'...'@` 模板，替换占位符后打印前几行，确认是正常中文且无 `{{`、无 `\uE000-\uF8FF` 私用区字符；脚本里的 `$probe` 自检也会在发版前拦下这一类错误 |

---

### F. 液态玻璃（14.9）

#### 坑 F1：AGSL 自定义函数和 SkSL 内置函数重名

- **现象**：`javac`/d8/打包全部成功，但 shader 编译报
  `functions 'float3 unpremul(half4 c)' and '$pure half4 unpremul(half4 a)' differ only in return type`，
  后面跟着一串 `unknown identifier`；真机上表现为玻璃整层失效（本项目会降级到「只有模糊」）。
- **根因**：SkSL 内置了 `$pure half4 unpremul(half4)`，同名但返回类型不同，不允许重载。
- **解法**：改名 `unpremulColor`（以及避免 `pack` 这类像内置的名字，本项目用 `toHalf4`）。
- **如何避免**：改完 shader 先用 Skia 编译器离线编译一遍，不要等真机日志。

#### 坑 F2：`javac` 通过 ≠ shader 能编译

- **现象**：构建四项校验全绿，但 AGSL 是运行期才编译的，真机上可能抛异常。
- **根因**：AGSL 以字符串进 dex，构建链里没有任何一步校验它。
- **解法**：`python -m pip install skia-python`，然后跑仓库里的 `python tools/check_agsl.py`：
  它从 `SearchGlass.java` 抽出入内嵌的 AGSL、用 `RuntimeEffect.MakeForShader` 编译，并核对 uniform 名称与
  Java 侧一致。
- **如何避免**：把这一步当成 shader 改动的固定回归；它能在打包前抓到语法、类型和 uniform 不一致。

#### 坑 F3：RenderNode 位置被算两次

- **现象**：玻璃内容和抓图错位整整一个 padding，模糊底图和折射采样对不上。
- **根因**：`RenderNode.setPosition()` 是节点在父坐标里的位置，`Canvas.drawRenderNode()` 在录制进另一个节点时会再叠加一次；
  「节点 position = (-pad,-pad) + 再 translate」等于平移两次。
- **解法**：节点统一 `setPosition(0,0,w,h)`，摆放只在 `drawNodeAt()` 里用 `canvas.translate(left, top)`。
- **如何避免**：RenderNode 只保留一种摆放方式，别混用 position 和 translate。

#### 坑 F4：`sign(0)` 让胶囊中线出现接缝

- **现象**：搜索栏正中一条 1px 的横向接缝（15.8.1 修过一次，14.9 重写时差点再犯）。
- **根因**：SDF 法线用 `sign(coord)`，在中线/中点上返回 0，梯度退化成零向量，采样坐标失效。
- **解法**：`outerSign()` 永远返回 ±1，并对梯度长度做 `max(len, 1e-4)` 兜底；**不要**再用 `smoothstep` 把位移在中线拉回 0
  ——那会从「法线退化」变成「位移不连续」，同样是肉眼可见的横向分界。
- **如何避免**：判断接缝要算「逐行差分 + 与无折射版本做差」，不要凭缩略图感觉（见 F5）。

#### 坑 F5：预览里的横线其实是页面内容

- **现象**：numpy 预览里胶囊中部有横向分层，看起来像 shader 接缝。
- **根因**：测试页在 bar 中间正好有一条色带边界，模糊后就是横向分层；逐行梯度统计没有尖峰，折射前后差异只有 1/255。
- **解法**：用数值指标定位（逐行 |Δ| 排行、差分图），别用缩略图肉眼判断。
- **如何避免**：任何「看起来有条线」的结论，先拿对照图和像素统计证伪。

#### 坑 F6：玻璃「定住了」——只画一次，滚动时内容不动

- **现象**：真机上胶囊里的模糊内容固定在某一帧；滚动前后截图逐像素相同（`mean abs diff 0.0`），
  但日志有 `search glass capture fps=103` —— 抓图在跑，画面不更新。
- **根因**：三层叠加。①`SearchGlass` 挂在 `android.R.id.content` 上，**不在滚动列表里**，列表滚动只弄脏
  `RecyclerView` 自己，没人 invalidate 它；②`OnPreDraw` 只更新了 `backdrop` 位图，`onDraw` 根本没被调用；
  ③`lensNode` 带 `RenderEffect`，图层有缓存，不标脏不会重跑 shader。
- **解法**：`OnPreDraw` 抓图后与上一张位图 `sameAs()` 比对，**变了才** `invalidate()`。
- **如何避免**：做「抓自己窗口」的浮层时先回答两个问题：谁会让它重画？静止时怎么停下来？
  **不要**用无条件 `invalidate()` 糊过去——见 F7。

#### 坑 F7：无脑 `invalidate()` 让窗口永不 idle

- **现象**：`adb shell uiautomator dump` 报 `ERROR: could not get idle state`；日志里 `draw fps` 常年 120，
  掉电明显。
- **根因**：在 `OnPreDraw` 里 `invalidate()` 会为下一次 vsync 再排一次 traversal；下一帧的 `OnPreDraw` 又
  invalidate，自激成永久 120Hz。
- **解法**：只在「抓到的位图变了」时 invalidate；静止时最后一张比对相等，链条自然断掉。
- **如何避免**：把「能不能 idle」当成验收项（`uiautomator dump` 成功 = 通过），别只看帧率。

#### 坑 F8：玻璃胶囊比原生药丸大一圈

- **现象**：玻璃上下边缘溢出白色药丸、左右各宽出一截，看起来「玻璃和搜索栏不是同一个东西」。
- **根因**：`setMaskView(null)` 时遮罩 = 整个 `SearchGlass`（50dp 包装层 / 56dp stub），而原生药丸是 stub 里
  `android.R.id.widget_frame` 的 `LinearLayout`，**44dp**（`miuix_appcompat_searchbar_bg_height`）；
  另外 `computeMask()` 还叠了一个 `6dp` 手调内缩，是按整条包装层调出来的补偿值。
- **解法**：遮罩取 `widget_frame`（`pickNativePill()`，兜底 `findBackgroundView()`），`computeMask()` 只按该视图
  背景的 padding 内缩。实测 `mask=[0,13][1152,153]`，正好 44dp 药丸。
- **如何避免**：布局尺寸别猜——`aapt2 dump xmltree --file res/layout/miuix_appcompat_search_stub.xml Settings.apk`
  看 XML，`DEBUG_TREE` 的 `dumpTree()` 看真机实际尺寸。

#### 坑 F9：HyperiOS 把 `SlidingSwitch` 换成自己的 Compose host，开关「下沉」

- **现象**：飞行模式行标题中心 Y=945，开关旋钮中心 Y=975（低 30px）；日志里 `setTranslationY(-30)` 已执行、
  `getLocationOnScreen` 也变成了 898..993，但截图里旋钮仍在 938..1012，像是 translation 没生效。
- **根因**：HyperiOS 1.x 的 `dev.hyperios.app.toggles.LiquidTogglesHook` hook 了
  `miuix.slidingwidget.widget.SlidingSwitch#onDraw`。`USE_LIQUID_GLASS_TOGGLE=true` 时它调用
  `LiquidToggleHost.ensure(view)` 后直接 `return`，原生 onDraw 被跳过；host 是挂在 `widget_frame` 上的
  ComposeView，位置取 `view.getTop()`。MIUI 的 `widget_frame` 把 95px 高的开关放在 156px frame 的底部
  （`top=61`），所以 host 永远画在底部，translation 不会移动它。
- **解法**：检测到 `dev.hyperios.app` 时改用模块自有的 `GlassToggle`（普通 View，不在 HyperiOS 的
  `TARGET_TOGGLE_CLASSES` 里），用 `weight=1` 的 spacer 推到行根右侧，竖向由行 LinearLayout 居中。
- **如何避免**：别用「改坐标」去修另一个模块画的 View；先确认画面到底是谁画的。hook 别的模块的
  `onDraw` 时，任何基于 layout box 的覆盖层都不会跟 translation/scale 走。

#### 坑 F10：主题 9-patch 的透明边距烘在 PNG 里，`getPadding()` 返回 false

- **现象**：玻璃胶囊比原生搜索栏宽一圈、向上错位；`mask=[63,1][1084,139]`（1021px）或 `[50,1][1137,139]`
  取决于阈值，而原生可见 fill 约 94..1198（1104px）。
- **根因**：主题把 `miuix_appcompat_search_mode_edit_text_bg_*` 换成了带透明边距的 9-patch；
  透明区域是图片像素，不是 drawable padding，`getPadding()` 返回 false。按 `inputArea` 视图边界裁必然偏大；
  再叠 `(h-mask.height())/2` 的手调居中还会上下错位。
- **解法**：`paintedBackgroundBounds()` 把背景 drawable 画进 ARGB 位图，按 alpha ≥ 72 取实际像素外接矩形；
  **先量不透明状态，再**把原生 fill `setAlpha(0x80)`（顺序反了，半透明 9-patch 的侧边会掉到阈值以下，
  量出来会窄 8dp 左右）。遮罩直接用这个矩形，不再手调居中。
- **如何避免**：主题/9-patch 场景不要信 `getPadding()`，直接测像素；阈值要在真机截图上核对
  （本次 72 能覆盖可见 fill，96 会在左侧露一条原生 fill）。
- **16.6 补充**：上面「保留 9-patch 并量像素」的做法已被 16.6 取代——现在量完就清掉背景，
  且必须等首帧 `OnPreDraw`（见 F11）。F10 只保留「透明边距烘在 PNG 里、`getPadding()` 不可信」
  这一条仍然成立。

#### 坑 F11：去掉 9-patch 后，胶囊可见矩形在 install 时量不到

- **现象**：`setPillVisibleRect()` 拿到 null，玻璃按整个 `inputArea`（44dp）画，比原来的可见胶囊高，
  图标也跟着偏；日志 `visible=none`。
- **根因**：`BottomSearch.install()` 在首帧 layout 之前执行，`pill.getWidth()==0`，
  `paintedBackgroundBounds()` 直接返回 null；而背景一旦清掉，之后也没有 drawable 可量。
- **解法**：install 时保存 `pill.getBackground()` 引用再 `setBackground(null)`；在 `OnPreDraw`
  第一次回调（layout 完成、尚未绘制）里用保存的 drawable 按真实尺寸调用 `SearchGlass.paintedBounds()`
  （与 `paintedBackgroundBounds` 共用实现），拿到矩形后 `setPillVisibleRect()` + `invalidate()`，
  这样第一帧就用正确几何，不会闪一下 44dp。
- **如何避免**：任何依赖 view 尺寸的测量都放到 `OnPreDraw`/`onLayout` 之后；不要「先清掉再想办法量」。

#### 坑 F12：给 wrap_content 的 ImageView 换大 drawable 会把布局撑开

- **现象**：搜索图标变成 44dp 大图，把「搜索」文字从 63dp 推到 103dp，之前按原图标算的 padding 全失效。
- **根因**：`miuix_appcompat_search_stub` 里的搜索 ImageView 是 `wrap_content`；模块 PNG 在
  `drawable-xxhdpi` 下固有尺寸约 58dp，view 变成 58+26=84dp；`CENTER_INSIDE` 的内容框也随之变大，
  图标不再缩小。
- **解法**：在 `styleGlassContent()` 里把 ImageView 的 `LayoutParams` 固定回 44×24dp，再用
  `paddingLeft=21dp / paddingRight=9dp / paddingTop/Bottom=5dp` 做 14dp 内容框；`CENTER_INSIDE` 才会
  按 14dp 缩放。麦克风则直接给 12×16dp 的 `FrameLayout.LayoutParams`，不依赖固有尺寸。
- **如何避免**：替换系统布局里的 drawable 前先看目标 view 的 `layout_width/height`；`wrap_content`
  的 view 会跟着 drawable 的固有尺寸变。

#### 坑 F13：底色渐变让「上下等亮」的光边看起来不等

- **现象**：同一套 `rimAlpha`，顶部峰值 `#45`、底部 `#69`，用户仍说上下不一致、深色高光太亮。
- **根因**：底色是 0.16→0.07 的白色渐变，下边缘基色更暗；加法高光的绝对增量相同，但对比度更高，
  肉眼觉得底部更亮。
- **解法**：底色改成统一 alpha（深色 0.10、浅色 `#E6E6EB` 50%），光边上下同权（去掉 top/bottom
  权重差），`rimAlpha` 从 0.55 → 0.26 → 0.14 → 0.20 逐轮按真机截图调；最终上下峰值 `#63`/`#62`。
- **如何避免**：做「等亮」效果时先统一基色，再谈高光权重；验收要看像素峰值，不要只看代码里的权重。

#### 坑 F14：`VoiceService` 冷启动 4–5 秒，像没点中

- **现象**：第一次点麦克风后 3 秒内没有任何窗口变化，容易以为点击没生效。
- **根因**：`com.miui.voiceassist` 进程/模型冷启动，`voice_assist_root` 要等服务起来才 addView；
  实测从点击到 `mCurrentFocus=voice_assist_root` 约 4.8s，热启动会快很多。
- **解法**：点击处加 `HapticFeedbackConstants.KEYBOARD_TAP` 触感和 0.86→1.0 的回弹动画，给即时反馈；
  不去缩短服务端等待。调试时用 `dumpsys window | findstr voice_assist_root` 确认是「慢」而不是「没触发」。
- **如何避免**：接入第三方语音助手时先用 `adb shell am start-foreground-service` 实测入口和延迟，
  再写进代码；不要用「3 秒没反应 = 失败」的假设验收。

#### 坑 F15：用户提供的图标是紧裁的，边缘被切

- **现象**：`search.png` 的放大镜手柄、`mic.png` 的底座横条都贴到画布边缘，缩小后仍能看到切边。
- **根因**：素材按图形 bbox 紧裁，没有留白；`下载.zip` 里的 PNG 就是最终边界。
- **解法**：按原样使用（用户指定），只在 ImageView 里用 `CENTER_INSIDE/FIT_CENTER` 控制大小；
  不补画、不镜像、不擅自扩画布。记录里写明素材限制，后续如用户换图直接替换资源名即可。
- **如何避免**：接图标素材时先看 alpha 包围盒是否贴边；贴边就说明被裁过，提前和用户确认接受。

---

## 已过时的结论（保留备查）

- ~~「搜索栏玻璃必须用 kyant AGSL，否则不像」~~ —— 15.x 的结论，曾被 14.8 的「抓图 + 10dp 模糊 + 浅/深色叠层」取代。
  **2026-11-09 用户明确要求恢复**，14.9 用 Kyant 的折射 + 高光 + 内阴影重写；这里保留旧结论只为记录取舍变化。
- ~~「回退到 14.8 只要删掉 lattice 的类」~~ —— 见坑 C1，还要清反射分支与依赖桩类。
