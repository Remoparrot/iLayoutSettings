# 架构与 Hook 链路

> 状态：**现行** · 对应版本：15.0 (85)
> 源码：`module/src/com/abradant/iossettings/`

## 1. 从加载到生效

```
LSPosed
  └─ Module.handleLoadPackage()            // xposed_init 指向 com.abradant.iossettings.Module
       └─ Hook.install(classLoader)        // 只在 com.android.settings 进程里跑
            ├─ SettingsFragment            // 首页
            │    ├─ buildAdapter           → HeaderData.apply() + BottomSearch.install()
            │    ├─ onViewCreated          → BottomSearch.install() + ForceBlur.forceHeaderBlur() + UiTweaks.attachScrollBlur()
            │    └─ onResume               → HeaderData.apply() + BottomSearch.install()
            ├─ SubPageIcons.install()      → PreferenceFragmentCompat#onCreate/onViewCreated/onResume
            ├─ AirplaneSwitch.install()    → MiuiSettings$HeaderAdapter#onBindViewHolder
            ├─ CardFix.install()           → HeaderAdapter#onBindViewHolder + ResourcesImpl#loadDrawable
            ├─ SubPageCards.install()      → androidx Preference#onBindViewHolder
            ├─ WirelessPage.install()      → MiuiWirelessSettings#onViewCreated/onResume
            ├─ AccountTextFix.install()    → TextView#setText
            ├─ AppearanceTweak.install()   → MiuiDisplaySettings#onViewCreated/onResume
            └─ ForceBlur.install()         → HyperMaterialUtils / MiuiBlurUtils
```

设计上所有入口都包在 `try/catch(Throwable)` 里：**单个 hook 失败只打日志，不能让设置进程崩**。

## 2. Hook 点总表

| 目标类 | 方法 | 目的 | 实现类 |
| --- | --- | --- | --- |
| `com.android.settings.SettingsFragment` | `buildAdapter` | 首页 header 列表建好后立刻重排/重分组 | `HeaderData` |
| 同上 | `onViewCreated` | 原生搜索条 `mAnchorView` 此时才赋值，之后再装底部栏 | `BottomSearch` |
| 同上 | `onResume` | MIUI 可能刷新 header，回来后再排一次 | `HeaderData` |
| `androidx.preference.PreferenceFragmentCompat` | `onCreate` / `onViewCreated` / `onResume` | 二级页每次进入都补图标 | `SubPageIcons` |
| `MiuiSettings$HeaderAdapter` | `onBindViewHolder(HeaderViewHolder,int)` | 控制中心行塞开关（HyperiOS 时用自有 `GlassToggle`）；末行清理主题 drawable | `AirplaneSwitch` / `CardFix` |
| `ResourcesImpl` | `loadDrawable` | 末行绑定时把四个主题 drawable 换成透明图 | `CardFix` |
| `androidx.preference.Preference` | `onBindViewHolder` | 二级页每组最后一行去双层圆角 | `SubPageCards` |
| `MiuiWirelessSettings` | `onViewCreated` / `onResume` | 删飞行模式行、补「隔空播放与连续互通」 | `WirelessPage` |
| `android.widget.TextView` | `setText` | 账号副标题兜底改成 iOS 文案 | `AccountTextFix` |
| `MiuiDisplaySettings` | `onViewCreated` / `onResume` | 外观页预览加卡片底 | `AppearanceTweak` |
| `HyperMaterialUtils` / `MiuiBlurUtils` | 多个开关方法 | 强制开启原生模糊 | `ForceBlur` |

> 类名或方法名随 MIUI 版本变化时，`findClassIfExists` 返回 null，只打一条日志；这是有意的「软失败」。

## 3. 首页重排（`HeaderData`）

这是整个模块最核心、也最绕的一段。MIUI 的首页 header 列表是 `SettingsFragment.mHeaders`（`List<PreferenceActivity$Header>`），miuix 的 `CardGroupAdapter` 按每个 `Header.groupId` 决定哪些行属于同一张卡片。

**流程**（`HeaderData.apply`）：

1. 反射读 `mHeaders`；
2. `parse()` 把每个 header 包成内部 `Item`，拿到 `id` 资源名、标题、原下标；
3. `assign()`：按 `SLOT_IDS[slot]` 里的资源 id 别名逐槽匹配，第一个存在的赢；没匹配上的再按标题（`SLOT_TITLES`）兜底；
4. `overrideSlots()`：处理「同一个 id 在不同机型上归属不同卡片」的特例（如把某些行塞进指定卡片）；
5. `addAirplaneRow()`：在「网络」卡片最前面**合成**一条飞行模式行（系统原生没有这个 entry）；
6. `ensureInterconnection()`：设备有 MiLink 服务但列表里没有对应 entry 时补一条；
7. 按 `(group, slot, 原下标)` 排序；
8. 给每个 header 写 `groupId = 101 + group`（`GROUP_ID_BASE`），让 miuix 画出卡片；
9. 回写 `mHeaders`（不可变列表时改为替换字段 + `mHeaderAdapter.mHeaders`），再 `notifyDataSetChanged` / `updateGroupInfo` / `invalidateItemDecorations`。

**为什么不用自绘**：卡片圆角、间距、按压反馈、深色模式全在 miuix 的 `CardItemDecoration` 里。改 `groupId` 就能白嫖这一整套，自绘反而要重写一遍且容易和系统动画打架。

卡片的 `group` 号（0~9）与内容对应关系见 `HeaderData.SLOT_GROUP`，首页视觉顺序见 [版本记录 · 首页顺序](07-changelog.md#1-首页顺序)。

## 4. 底部搜索栏（`BottomSearch` / `SearchGlass` / `SearchHost`）

1. 找到首页的原生搜索条（`header_view` / `mAnchorView`），**隐藏但保留**；
2. inflate 系统的 `miuix_appcompat_search_stub` 作为底部浮层（失败则退化成自绘胶囊）；
3. 浮层尺寸：高 50dp，左右 8dp，底部 12dp；拿到导航栏 inset 后改为 `14dp + nav`；
4. 列表底部 padding 同步加上 `搜索栏高 + nav + 24dp`，避免最后一张卡片被挡；
5. 点击浮层 → 调原生 anchor 的 `performClick()` → 走 MIUI 自己的搜索 ActionMode；搜索界面出现时浮层自动隐藏；
6. `SearchGlass` 负责视觉层：抓窗口（跳过自身子树）→ **1dp 模糊**（`RenderEffect`）→
   圆角矩形折射（`RuntimeShader` + AGSL，**24dp 厚度 / 30dp 强度**）→ 45° 边缘高光 → 底部内阴影；
   遮罩不是视图边界，而是 `paintedBackgroundBounds()` 把原生胶囊 drawable 画进位图后取的实际像素矩形
   （主题 9-patch 的透明边距烘在 PNG 里，`getPadding()` 不可靠）；测量完成后再把原生 fill `setAlpha(0x80)`；
   **抓图在 `OnPreDraw` 里做，只有抓到的位图与前一张不同才 `invalidate()`**；
7. 逐级兜底：全量 shader → 只有折射的 shader → 只有模糊 → 半透明胶囊（`shaderUnavailable` 只置一次，
   不会每帧重试抛异常）。`SearchHost` 在抓图那一帧跳过自身子树，避免把搜索栏自己拍进背景里。

> 抓图 + 两个 RenderEffect 是整份代码里性能最敏感的部分，改之前先读 [设计思路 · 2.4 玻璃](05-design-notes.md)。
> 搜索栏挂在 `android.R.id.content` 上，**不在滚动列表里**：列表滚动不会弄脏它，所以必须自己判断
> 「背景变了」再重绘（见 [坑 F6](06-pitfalls.md)）。

### 4.1 飞行模式开关（`AirplaneSwitch`）

- 行是 `MiuiSettings$HeaderAdapter` 合成出来的，`widget_frame` 里原本只有 `text_right`；
- **未装 HyperiOS**：inflate `miuix_preference_widget_switch`（`SlidingSwitch`）加到 `widget_frame`，
  用 `CENTER_VERTICAL` + 少量 `translationY` 校正；
- **装了 HyperiOS**：HyperiOS 的 `LiquidTogglesHook` 会 hook `SlidingSwitch#onDraw`，
  `USE_LIQUID_GLASS_TOGGLE=true` 时直接 `LiquidToggleHost.ensure(view)` 并跳过原生绘制；
  host 是按 view 的 layout box 定位的 ComposeView，`setTranslationY` 不会移动它，
  而 MIUI 的 `widget_frame` 把 95px 高的开关放在 156px frame 的底部，于是开关下沉约 10dp。
  15.0 改用模块自己的 `GlassToggle`（普通 View，不在 HyperiOS 的 hook 名单里），
  加一个 `weight=1` 的 spacer 把开关推到行根右侧，竖向由行 LinearLayout 居中；
- 行被回收成普通 header 时 `removeGlassToggle()` 移除开关和 spacer，并恢复 `widget_frame` 可见。

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
