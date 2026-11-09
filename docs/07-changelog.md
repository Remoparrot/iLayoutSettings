# 版本记录（历史全文）

> 状态：**历史** · 面向：想知道「某个版本改了什么」的人
>
> 本文是开发过程中的原始版本日志，时间跨度 v3.1 → v17.0，**包含已放弃的实验分支**（液态玻璃、GUI 参数页、smali 搬运等）。
> 当前主线是 **14.8**，功能与取舍以 [架构](02-architecture.md)、[设计思路](05-design-notes.md) 为准；
> 当前发布包的指纹以 [状态](00-status.md) 为准——本文末尾「7. 校验」写的是后续版本的数据（216,015 B），**不适用于 14.8**。
>
> 新增版本时在**本文顶部**追加一节，格式与下面一致。

---

# iOS 设置布局 · LSPosed 模块（v14.8 回退版）

## v14.8 回退说明

按你的要求回到 14.8 的行为（版本号也改回 **14.8 / versionCode 83**）：

- 搜索栏：抓背景 + 10dp 模糊 +（浅色 30% / 深色 6%）玻璃叠层，**没有** kyant 折射 / 高光 / 内阴影
- 移除 lattice 的 `LiquidGlassView` / `SearchBottom` 及全部 smali 汇编步骤、`assets/liquid_glass.agsl`、`dev.settingslab` 桩类
- GUI 参数界面撤掉，`MainActivity` 回到「关于 / 使用方式」页；桌面图标重新隐藏
- 顶栏模糊固定 110；不读取 SharedPreferences

APK 体积回到 195,465 bytes（与 14.8 一致），但源码没有版本库快照，是按功能逐项回退重建的，sha256 与当时的 14.8 不同。

## v17.0 变更

- **搜索栏回退到「只模糊」的版本**：
  - 关闭折射/rim highlight/内阴影/诊断数字（`if (false)` 跳过 shader 分支）
  - `drawBlurred` 直接把带 **10dp RenderEffect 模糊** 的 RenderNode 画到搜索栏
  - 保留：抓背景、原生 stub 上的胶囊裁剪、无颜色叠加

## v16.8 变更

- **补上 kyant0 液态玻璃的完整合成**（之前只有折射，所以看起来不像）：
  - 折射前先把抓到的背景 **blur 10dp**（kyant 的 glass 一直是 blur + lens 两段）
  - 折射后叠加 **rim highlight**：1.2dp 描边的 45° 白色渐变（左上亮、右下透明），来自 kyant 的 DefaultHighlightShader
  - 再叠一层 **内阴影**：右下 2.5dp 深色渐变描边
  - 仍然没有整块色层，只有边缘高光/内阴影
- shader 继续用内置 kyant AGSL（`refractionHeight/Amount` 由 GUI 控制）。

## v16.7 变更

- **玻璃回退到 kyant 实现**：lattice 的 `LiquidGlassView` / `SearchBottom` 在设备上出现文字重影、图标缺失，已停用；`BottomSearch` 重新直接 `new SearchGlass`，`SearchGlass` 也不再读取 `assets/liquid_glass.agsl`，改用内置的 kyant AGSL（只有折射，无内阴影/色散）。
- Hook 恢复为始终安装我们自己的底部搜索栏（不再走 `LatticeSearchBridge`）。
- lattice 的类仍保留在 dex 里但不再被调用。

## v16.6 变更

- **整段搬 lattice 的搜索栏实现**：把 `dev.settingslab.SearchBottom` 及其全部内部类（`SearchGlyph` / `ThemedHint` / `ThemedHintImage` / `ThemedIcon` / lambda）从 lattice-3.72 反编译出来，和 `LiquidGlassView` 一起用 smali 汇编进模块 dex。
  - 新增 `LatticeSearchBridge`：反射设置 `SearchBottom.CONFIG`（折射厚度/强度来自 GUI 偏好），再调用 `SearchBottom.apply(Context, listView, true)`。
  - Hook 里先跑 lattice 的实现，失败才回退到旧的 `BottomSearch`。
- 依赖桩类：`GlassBackdrop`（接口）、`Diagnostics`（日志）、`SearchBoxConfig`（配置对象）。

## v16.5 变更

- **玻璃尺寸对齐搜索栏**：`LiquidGlassView` 的 LayoutParams 从「宽 MATCH_PARENT + 高 50dp」改成**宽高都 MATCH_PARENT**，直接铺满原生 stub（`search_mode_stub`），不再比搜索栏矮/窄。
- **玻璃不再把搜索栏自己也玻璃化**：抓背景用的 `content` 从整个 decor 改成 **`android.R.id.content` 的第一个子 View**（页面容器，不含搜索栏这个兄弟节点），玻璃里不会再出现搜索栏自身的重影。
- **刷新率 120Hz**：新增 `startGlassFrameLoop()`，用 `Choreographer.postFrameCallback` 每帧调用 lattice 的 `invalidateBackdrop()`，让玻璃跟随屏幕刷新率重抓（原实现只在滚动/失效时更新，看起来像定住）。

## v16.4 变更

- **直接搬 lattice 的 `LiquidGlassView`**（用户已获原作者同意）：
  - 从 `lattice-3.72.apk` 的 classes2.dex 反编译出 `dev/settingslab/LiquidGlassView.smali` 及其两个 lambda，用 `smali.jar` 汇编成 dex，构建时与模块 dex 一起交给 d8 合并
  - 补齐它依赖的桩类：`dev.settingslab.GlassBackdrop`（接口）、`Diagnostics`（日志）、`SearchBoxConfig`、`SearchBottom.config()`（改成读模块偏好）
  - 搜索栏改为反射实例化 `LiquidGlassView(Context, View content, boolean barMode=true)`，调用 `setBarStyle(0f, 0)`；失败才回退到旧的 `SearchGlass`
  - 玻璃的模糊、边框、sheen、内阴影、fallback 全部由 lattice 原代码绘制；折射厚度/强度实时读 GUI 偏好
- 校验：APK 内 dex 已包含 `LiquidGlassView` 与 AGSL 字符串。

## v16.3 修复

- **桌面图标恢复**：`MainActivity` 重新加上 `MAIN/LAUNCHER` intent-filter，可以直接在桌面点开调玻璃参数。
- **玻璃卡住 / 叠加图片消失**：
  - shader 编译失败时不再每帧抛异常：`RuntimeShader` 构造包 try/catch，失败自动回退到内置的 kyant shader
  - 新 shader 才有的 `chromaticAberration` / `innerShadow` / `shadowW` 用 `setUniform()` 逐个设、单独吞异常，回退 shader 也不会整帧失败
  - `drawBlurred` 最外层异常时直接画抓到的位图兜底，至少能看到画面而不是空白
- **飞行模式开关偏下**：`recenter()` 里新增 `centerInParent()`，按父容器类型（FrameLayout / RelativeLayout / LinearLayout）直接设置 `CENTER_VERTICAL`，不再只靠 `translationY` 硬拉；之后仍用屏幕坐标做一次微调。

## v16.2 变更

- **搜索栏玻璃换成 lattice 的 shader**（从 `lattice-3.72.apk` dex 提取，内置为 `assets/liquid_glass.agsl`）：
  - 与 kyant 同源，额外支持 `innerShadow` / `shadowW`（内阴影）与 `chromaticAberration`（色散）
  - shader 不再硬编码，运行时从模块 assets 读取
- **新增 GUI 调节界面**（从 LSPosed 模块详情打开本模块 App；桌面图标仍隐藏）：
  - 折射厚度 0–80dp、折射强度 0–60dp
  - 色散 0–100%、内阴影 0–100%、内阴影宽度 4–60dp
  - 顶栏模糊半径 0–300px（0 = 关闭）
- **参数持久化**：写入模块 SharedPreferences，目标进程用 `XSharedPreferences` 每秒读一次，改完重进设置页生效
- **顶栏模糊可调**：`UiTweaks` 改读 `topbar_blur`，0 时 `clearBackgroundBlur` 关闭

## v15.11 修复（玻璃真正叠在原生胶囊**上面**）

15.10 把原生 fill 调到 45% 透明，但主题里的 capsule 是自定义 drawable，`setAlpha()`
对它不生效——fill 仍然不透明，玻璃还是被盖住。

前几版一直在"把玻璃画在整条搜索栏下面"这个结构里打转，只要原生 fill 在玻璃上面，
无论清空、调透明还是渐变，结果都不可控。15.11 直接把层级改对：

```
wrap (SearchHost)
└── bar（原生 stub，fill 原样保留）
    └── search_mode_stub (FrameLayout)
        ├── glass（SearchGlass，MATCH_PARENT，index 0）   ← 盖住原生 fill
        └── inputArea（放大镜 + "搜索" + 麦克风）          ← 压在玻璃上面
```

- 玻璃不再是"整条栏的容器"，而是 stub 里的第一个子 View：**在原生胶囊之上、文字图标之下**；
- 原生 fill 一个都不清、不改透明度，所以搜索栏不可能再消失；
- 抓图时由 `SearchHost` 一次性跳过整棵搜索栏子树（`dispatchDraw` 直接 return），
  不用 `setVisibility`，不会触发 layout；
- 如果 shader 编译失败，玻璃会把自己 `GONE`，露出下面的原生搜索栏，而不是留一条空白。

版本：**15.11 / versionCode 102**。

## v15.10 修复（搜索栏消失 + 自动清理旧版本）

> 15.11 已改用「玻璃嵌入 stub」的结构，本节 45% 透明的做法已废弃。

15.9 把 stub 的背景全部 `setBackground(null)`，在你这套主题上 `install()` 直接抛异常，
整个底部搜索栏都没加上（顶部原生条又是隐藏的），看起来就是"搜索栏没了"。

现在不再清空背景，改成**把每个 fill 调到 45% 透明**（`softenBackgrounds`）：

- 原生胶囊仍然在，所以搜索栏不可能再消失；
- 玻璃在它下面，45% 的胶囊只是给玻璃染一层原生质感；
- 每个 View 单独 try/catch，且 `findBackgroundView` / `softenBackgrounds` / 文字着色
  全部包在独立的 try 里——任何一步失败都不会中断 `install()`；
- 文字 / 图标 / 位置 / 大小不动。

**自动清理旧版本**：`build.ps1` 每次构建前会删掉 `dist/IosSettingsLayout-*.apk*`，
只保留当前版本的 APK 和 idsig。

版本：**15.10 / versionCode 101**。

## v15.9 修复（玻璃真正露出来 + 每帧抓图）

> 15.10 已把「清空背景」改回「45% 透明」，因为清空会让搜索栏直接消失。

**玻璃为什么一直不在**：原生搜索栏那张胶囊图是画在 `SearchGlass` **上面**的
（`bar` 是 `wrap` 的子 View）。15.8.1 / 15.8.2 为了"保留原生图片"把背景留着了，结果就是
一层不透明胶囊盖在玻璃上，折射再正确也看不见。16.2 清掉过它，所以那版能看到玻璃。

现在：

- `findBackgroundView()` 先记住原生胶囊的边界（玻璃的遮罩仍按它裁切）；
- 然后 `clearBackgrounds()` 递归清掉 stub 里的所有 fill；
- 文字、放大镜、麦克风、位置、大小全部保留，胶囊由玻璃自己画。

**刷新率**：去掉 60fps 的 `16ms` 上限和"滑动后 600ms / 静止 800ms"的启发式。那套逻辑在
「外观」「待机显示」「墙纸」「控制中心」这类**列表不动、画面在动**的页面上只能约 1.25fps
刷新一次，看起来就是玻璃卡住。现在 `onPreDraw` 每帧都抓：

- 上限 **8ms（120Hz）**，并按实际抓图耗时自适应——抓一次 12ms 就自动降到约 83fps，
  不会把 UI 线程拖死；
- 日志每 2 秒一行 `search glass capture fps=NN`，可以直接确认有没有到 120。

版本：**15.9 / versionCode 100**（可直接覆盖 15.8.2 / 16.2）。

## v15.8.2 修复（玻璃回来了 + 120Hz）

**为什么玻璃没了**：v16.2 加的 `unpremul()` 里混用了 `half3` 和 `float`：

```glsl
float3 rgb = clamp(c.rgb / a, 0.0, 1.0);   // half3 / float
return half4(half3(rgb), 1.0);             // half3 + float 字面量
```

SkSL 对精度类型比 GLSL 严，这段有编译失败的风险；15.8.2 把它改成显式构造。
（15.9 进一步确认：玻璃不显示的**真正原因**是原生胶囊图盖在玻璃上面，见上一节。）

现在：

- `unpremul()` 全部改成显式构造：`float3(c.rgb)`、`half(rgb.r)`、`half(1.0)`，不再混精度；
- 色散本来就是 0，顺手把 3 次采样合成 **1 次**（每像素 3 次纹理采样 → 1 次），120Hz 更稳；
- `gradSdRoundedRect()` 内部的 `normalize(0, 0)` 也换成 `v / max(length(v), 0.0001)`，
  把 NaN 的来源彻底断掉；
- shader 真正生效时会打一行 `liquid glass shader active`；失败仍是
  `liquid glass shader failed, plain backdrop`（带异常原因，方便定位）。

**抓图间隔**：`16ms` → **`8ms`**（`CAPTURE_INTERVAL_MS`）。15.9 起改成"每帧都抓 + 按实际耗时自适应"，
不再有滑动后 600ms / 静止 800ms 的节流。

版本：**15.8.2 / versionCode 99**（可直接覆盖 15.8.1 / 16.2）。

## v15.8.1 修复（以 15.8 为底，修掉搜索栏正中那条线）

**结论：那条线不是图片，也不是布局里的分隔 View，是液态玻璃 shader 自己画出来的。**

AGSL 里法线是这样算的：

```glsl
normalize(gradSdRoundedRect(...) + depthEffect * normalize(centeredCoord))
```

胶囊正中的那一行正好是 `centeredCoord.y == 0`：`sign(0)` 返回 0，SDF 法线退化成
零向量，`normalize((0,0))` 得到 0/NaN，`d * grad` 在这一行采样到无效坐标，于是整个
宽度上出现 **1px** 的接缝。这也解释了为什么 v15.9 把 stub 的所有背景都清掉了、线还在
——它根本不是任何一张 drawable 画的。行号正好在条高的 1/2 处，所以看起来是"正中一条线"。

修法（`SearchGlass` 里的 `LIQUID_GLASS_AGSL`）：

- 先求长度再兜底：`float2 grad = gradRaw / max(gradLen, 0.0001)`，零向量时位移自然为 0，不再产生 NaN；
- 中线附近用 `smoothstep(0.0, 6.0, abs(centeredCoord.y))` 把折射位移平滑收到 0，
  两侧法线翻转处不会再拉出硬接缝；
- 原式里的 `depthEffect * normalize(centeredCoord)` 在 `depthEffect == 0` 时是 `0 * NaN`，
  改成 `depthEffect * float2(0.0, 1.0)`，uniform 仍被引用（`setFloatUniform` 不会报找不到）。

**回到 15.8 的观感（按你的要求）**：

- 原生 stub 的背景**原样保留**：不再 `clearBackgrounds()`、不再 `hideThinDividers()`，
  位置 / 大小 / 透明度 / 文字 / 图标全部是主题自己的；
- 折射参数回到 `refractionHeight = 40dp`、`refractionAmount = -20dp`；
- 抓图缩放回到 **0.6**；
- 去掉 v16.2 加的 1dp 描边 + 内侧高光；
- 依旧**不叠加任何颜色**（浅色/深色都不画玻璃 tint）。

**保留的两个纯修复**（不改观感，只防翻车）：

- `unpremul()`：抓到的窗口位图是预乘 alpha，不除回去整条玻璃会发灰（v16.2 的灰条就是它）；
- shader 编译/设置失败时退回"直接画抓到的背景"，抓图失败时用半透明兜底，不会留空条。

版本：**15.8.1 / versionCode 98**（比 16.2 的 97 大，可以直接覆盖安装，不用先卸载）。

## v16.2 修复（搜索栏整条变灰、中间一条白线）

截图里的样子 = 一条不透明白灰胶囊 + 正中一道白线。两个原因叠在一起：

1. **抓背景的位图是半透明的（真凶）**：设置窗口这一层不是全不透明，`root.draw()` 抓出来的
   位图带 alpha（实测约 **0.667**），而 AGSL 拿到的像素是**预乘**的。shader 末尾 `color.a = 1.0`
   等于把预乘 RGB 直接当直通色用，整条玻璃被乘了 0.667：页面底色 `#F2F2F7` → **`#A2A1A4`**，
   就是截图里那块灰（数值完全对得上）。
   - 新增 `unpremul()`：`rgb /= max(a, 0.05)` 之后才置 `a = 1`，玻璃恢复页面原色，真正通透。
   - shader 编译/设置失败时退回「直接画抓到的背景」，不会再留一条空胶囊。
2. **主题的搜索栏 9-patch 盖在玻璃上面**：v16.0 恢复的
   `miuix_appcompat_search_mode_edit_text_bg_*.9.png` 是不透明灰胶囊（主题版中间还有一条亮线），
   它画在 `SearchGlass` 之上，把折射玻璃整个挡住了。
   - 现在 `BottomSearch` 只保留 stub 的**布局**（位置/大小/文字/图标），
     `clearBackgrounds()` 递归清掉 stub 里**所有**背景，胶囊完全由玻璃自己画。

顺带：

- **玻璃加了一圈边**：1dp 描边 + 内侧高光（浅色 `#1F000000` + `#40FFFFFF`，深色
  `#33FFFFFF` + `#1FFFFFFF`）。纯折射在纯白页面上几乎看不见，有边缘才像玻璃。
- **兜底不再实心**：抓图不可用时从不透明白/深灰改成半透明 `#66FFFFFF` / `#66000000`。
- **诊断日志**（排查时发这几行就够）：
  - `search glass backdrop 1131x136 center=#fff2f2f7`：每 2 秒一条，抓图中心像素；
  - `native search bar fills cleared: N`：清掉了几个原生背景；
  - `search glass mask: none (full bar)` / 具体类名：玻璃按谁的范围裁切。

> 想回到 v16.1 的「保留原生搜索栏图片」：把 `BottomSearch.install()` 里
> `clearBackgrounds(bar)` 那一行删掉即可（灰色不会回来，那是 shader 的锅）。

## v16.1 变更

按「整体通透感」方向调：

- 折射厚度 `refractionHeight` 40 → **22dp**（更薄，背景更清晰）
- 折射强度 `refractionAmount` −20 → **−10dp**（弯折更轻）
- 抓图缩放 0.6 → **1.0**（原分辨率，边缘更锐利、不发糊）
- 依旧：无模糊、无色散、无颜色叠加

## v16.0 变更

- **恢复原生搜索栏图片**：上一版把 `miuix_appcompat_search_mode_edit_text_bg_*.9.png` 也删了，理解错你的意思。v16.0 恢复它，位置/大小/透明度保持原样。
- **细线改由隐藏分隔 View 处理**：搜索栏布局里有一条高度 ≤2dp 的纯 View（分隔线），不是背景图的一部分。新增 `hideThinDividers()` 递归把这类细 View 设为 `GONE`，不动图片和文字。
- 颜色叠加仍是关闭状态（v15.8 起不画任何白色/黑色玻璃层）。

## v15.9 变更

- **去掉原生搜索栏 9-patch**：`miuix_appcompat_search_mode_edit_text_bg_*.9.png` 横向拉伸时中间有一道接缝线（截图正中那条横线就是它）。现在 `bar.setBackground(null)` + 递归清掉 stub 子 View 背景，胶囊形状完全由折射玻璃自己裁剪。
- 不叠加任何颜色（继承 v15.8）。

## v15.8 变更

- **完全不叠颜色**：浅色/深色都不再画白色/黑色玻璃叠层，shader 里的白色混入与边缘高光也去掉，只保留折射本身。
- 抓图失败时的兜底填充保留（否则会露出页面底色）。

## v15.7 变更

- **浅色叠层降到 30%**：`#59E8E8ED`（35%）→ **`#4DE8E8ED`**（30%），浅色下不再发灰发深。

## v15.6 变更

- **去掉色散**：shader 里 `dispersion` 置 0，R/G/B 三通道采样同一点，只剩纯净的透镜折射（不再有边缘彩边）。

## v15.5 变更

- **折射拉高**：`refractionHeight` 26 → **40dp**，`refractionAmount` −12 → **−20dp**，色散 0.6 → **0.9**
- **防止玻璃卡住**：`onPreDraw` 里加安全网——距离上次抓图超过 **800ms** 就强制重抓一次。之前只靠滚动偏移/scroll 事件，遇到 外观 / 控制中心 / 墙纸 这些页面切换后的布局变化时可能一直不触发，玻璃就停在旧帧。

## v15.4 变更

- **玻璃更透明**：
  - 浅色叠层 `#99E8E8ED` → **`#59E8E8ED`**（约 35% 不透明）
  - 深色叠层 `#0F000000` → **`#07000000`**
  - shader 白色混入 `0.04`（原 0.08）
  - 边缘高光 `0.10`（原 0.18）

## v15.3 变更

- **去掉模糊，纯玻璃**：不再给 RenderNode 设 `createBlurEffect`，kyant 的折射 shader 直接采样**清晰**背景，只有透镜折射 + 色散 + 边缘高光。
- **抓图分辨率提高**：0.22 → **0.6**，没有模糊后需要更清晰的底图，否则折射会看出像素块。

## v15.2 变更

- **直接搬 kyant 的液态玻璃 shader**：从 Maven 下载 `io.github.kyant0:backdrop-android:2.0.1` 源码（Apache-2.0），把 `RoundedRectRefractionWithDispersionShaderString` 移植成模块内的 AGSL：
  - 圆角矩形 SDF + 法线梯度，`circleMap` 计算边缘折射深度
  - `refractionHeight / refractionAmount / depthEffect / cornerRadii` 全部按 kyant 的参数语义设置（height 26dp、amount −12dp、depth 0、corner = 高/2）
  - R/G/B 三通道分别沿法线偏移采样，保留色散
- **渲染链**：`抓图 → 模糊 RenderNode（5dp）→ 折射 RenderNode（RuntimeShader）→ 画到搜索栏`，和 kyant 的「blur + lens」组合一致。
- 来源：https://github.com/Kyant0/AndroidLiquidGlass （Apache-2.0）。

## v15.1 变更

- **刷新率提到 60fps**：抓图间隔 24ms → **16ms**
- **性能优化**：
  - 抓图位图缩放 0.3 → **0.22**（像素量再降约 46%）
  - AGSL 采样从 7 次降到 **5 次**（去掉两个对角采样），每像素开销更低
  - 滚动结束后的持续抓图窗口 1200ms → **600ms**（静止后更快停止抓图）

## v15.0 变更

- **模糊减弱**：AGSL 里采样偏移 `2px / 4px` → **`1px / 2px`**（模糊半径约减半）
- **玻璃更淡**：
  - 白色混入 `0.08`（原 0.16）
  - 边缘高光 `0.18`（原 0.28）
  - 浅色叠层 `#99E8E8ED`（原 `#B3E0E0E6`），深色叠层 `#0F000000`（原 `#14000000`）

## v14.9 变更

- **搜索栏模糊换成 kyant 风格液态玻璃**：`SearchGlass` 在 Android 13+ 改用 **AGSL RuntimeShader**（`RenderEffect.createRuntimeShaderEffect`）处理抓到的背景，实现：
  - **边缘折射**：按圆角矩形 SDF 计算，越靠边缘采样点越往内偏移（`strength = 9dp`），形成厚玻璃透镜感
  - **色散**：R/B 通道沿法线方向以不同偏移采样，边缘出现轻微彩边
  - **多点模糊**：7 次采样平均，替代原来的高斯模糊
  - **玻璃质感**：整体混入 16% 白色、边缘叠加 28% 高光（rim light）
  - Android 12 回退到原来的 `createBlurEffect`，更低版本画原始位图
- 遮罩仍是原生搜索栏 9-patch 的边界 + 圆角裁切。

## v14.8 变更

- **不再处理二级页标题**：按你的要求撤掉所有标题相关逻辑：
  - 不安 `TitleFix`（不再强制 CollapseTitle 常显）
  - 不安 `TitleOverlay`（不再叠加居中标题）
  - 不再 hook `AppCompatActivity.onCreate/onResume` 去重设标题
  - 二级页标题回到 MIUI 原生行为（该显示什么就显示什么）
- 其它功能（首页排序、搜索栏模糊、外观卡片等）不变。

## v14.7 变更

- **顶栏标题兜底方案**：有些页面（如关于设备）根本没有 CollapseTitle，怎么设都不会显示。新增 `TitleOverlay`：
  - hook `android.app.Activity.onResume`（所有 Activity）
  - 如果页面里没有可见的 CollapseTitle，就直接往 `action_bar_container` 里加一个居中 TextView 显示页面名（18sp、sans-serif-medium、主题主文字色、距顶 12dp）
  - 页面名解析：`activity.getTitle()` → ActionBar 标题 → Activity 类名（保证不为空）
  - 同一页面只加一次，标题变了就更新文本
- 日志：`title overlay added: <名字>`。

## v14.6 修复

- **日志里的 `Software rendering doesn't support drawRenderNode`**：抓背景时会把窗口根视图重绘到软件 Canvas，这个过程中搜索栏自己也被绘制，`RenderNode` 在软件画布上直接抛异常，导致**每一帧抓图都失败**（模糊看起来不动/没有）。
  - `drawBlurred` 增加 `canvas.isHardwareAccelerated()` 判断：软件画布时直接画位图（无模糊），不再抛异常
  - 抓图期间把自身改成 `INVISIBLE`（不只是 alpha=0），避免被再次绘制

## v14.5 变更

- **顶栏标题强制显示**：
  - 标题解析链再加一层：`ExpandTitle` 的文本；都没有才用「设置」兜底，**绝不留空**
  - 同时在 `AppCompatActivity.onResume` 再跑一次标题设置，保证 `onCreate` 时拿不到名字的页面（如关于设备）在进入后也能补上
  - 日志：`sub-page title resolved: <名字>`

## v14.4 修复

- **关于设备等页面标题变空**：v14.3 的实时刷新会用 `activity.getTitle()` 覆盖标题，但这些页面的标题挂在 ActionBar 上、`activity.getTitle()` 是空串，于是标题被写成空、顶栏看起来没标题。
  - 标题解析顺序改为：`activity.getTitle()` → `ActionBar.getTitle()` → 已有的 CollapseTitle 文本
  - 三者都为空时**不动** MIUI 原有的标题（也不会隐藏大标题）
  - 300ms 的补充刷新同样只在非空时才写

## v14.3 变更

- **二级页顶栏标题跟随真实页面名**：截图里「通用」这个位置就是顶栏标题。之前是在 `onCreate` 时把 `activity.getTitle()` 写进去，但有些页面（如关于设备）标题是随后才设置的，结果停在上一页的名字。
  - 现在给标题挂 `OnGlobalLayoutListener`：每次布局都用当前 `activity.getTitle()` 刷新一次；另外 300ms 后再补一次。
  - 标题常显逻辑（`TitleFix`）不变。

## v14.2 变更

- **回滚 v14.1 的两处改动**：
  - 搜索栏不再用 `bg.9.png` 做 `DST_IN` 遮罩，恢复按原生 9-patch 边界 + 圆角的 `clipPath` 裁切
  - 外观页不再插入「自动」开关
- **二级页顶栏标题常显**：新增 `TitleFix`，hook `miuix.appcompat.app.CollapseTitle.setVisibility/setVisible`；被 `UiTweaks` 标记过的标题，任何隐藏请求都会被拦下并强制 `VISIBLE + alpha=1`。标题不再由滚动位置决定显隐，进入二级页后始终显示。

## v14.1 变更

- **搜索栏模糊遮罩换成你给的 bg.9.png**：从 `bg.9.png.7z` 解出 9-patch，去掉 1px 标记边后作为 `search_bar_mask.png` 内置；`SearchGlass` 用 `BitmapShader + DST_IN` 把模糊层按这张图的形状裁切（不再用代码画的圆角矩形），图片左右留白、圆角完全按图。
- **外观页「自动」开关**：在预览卡下面插入一条 miuix `SwitchPreference`：
  - 标题「自动」，说明「基于日出或日落，自动在浅色和深色之间切换。」
  - 状态读写 `miui.provider.MiuiSettings$System` 的 `dark_mode_auto_time_enable`
  - 位置在第一个 preference（预览卡）之后

## v14.0 变更

- **二级界面也有下划线**：重新启用 `SubPageCards`（按 `Preference.getParent()` 的末位判断，不依赖 adapter position，所以不会误伤）。每组最后一项清 background/foreground，下划线消失。
- **外观预览图再下移**：`topMargin` 8dp → **20dp**。

## v13.9 变更

- **最后一个选项的下划线又回来了**：v12.7 为了避免误清普通行背景，把 `CardFix` 整个停用了，于是末行 9-patch 的下划线/叠加也回来了。
  - v13.9 重新启用 `CardFix`，但**绑定位置改用标题反查**：读 itemView 的 title 文本，在 `mHeaders` 里找到对应 header 下标，再判断它是不是该组最后一行。这样列表头视图造成的 position 偏移不会再误伤普通行。
  - 只有真正的末行才清 background/foreground（下划线消失），普通行保持原样。

## v13.8 变更

- **浅色搜索栏灰一点**：玻璃叠层改为 `#B3E0E0E6`（真正的灰色 + 70% 不透明，不是黑色半透明）。
- **「搜索」文字颜色**：浅色 `#B3000000`（黑 70%），深色 `#B3FFFFFF`（白 70%）。
- **外观预览图下移**：浅色/深色两个分组各加 `topMargin=8dp`。
- **外观卡片白背景延迟出现**：重试间隔 250ms → **60ms**，最多 25 次，进入页面后基本立刻上背景。

## v13.7 修复（真正的元凶）

- **卡片一轮比一轮短**：底部搜索栏每次 install 都会给列表重新加底部 padding，而 `originalBottom` 是**当场读取**的——上一轮已经加过，下一轮又在此基础上再加一次。每返回一次，列表底部内边距就多出「搜索栏高度 + 导航栏 + 24dp」，内容与卡片装饰被不断往上挤，卡片看起来就一轮短一截。
  - v13.7 用 `BASE_BOTTOM`（WeakHashMap）只记录一次列表原始底部 padding，之后所有计算都以它为基准，不再累加。
- 其它逻辑保持 v13.6（`LAST_ORDER` 只在列表变化时重排）。

## v13.6 修复

- **卡片一轮比一轮短**：根因不是刷新时序，而是**每次 resume 都重新跑一遍 reorder**。多次写 `groupId`、MIUI 又在中间用自己的判定逻辑重算，两边的分组互相打架，卡片就一轮少一截。
  - 现在用 `LAST_ORDER`（WeakHashMap）记住每个 fragment 上次排好的顺序；如果当前 `mHeaders` 与上次完全一致，`apply` **直接返回**，不再改任何 groupId、不再 invalidate。
  - 只有列表真的变了（首次进入、MIUI 重置顺序）才重新排序。

## v13.5 变更

- **每次返回二级页卡片都变短**：`apply` 在「顺序已就绪」时会提前 return，导致没有重算 group info，而 MIUI 在 resume 时可能已经用旧分组重绘过一次。现在提前返回前也会执行 `updateGroupInfo + invalidateItemDecorations + invalidate`，卡片不会再一轮比一轮短。
- **外观预览图放大**：两个 `*_outer_view` 的 padding 置 0，图片占满卡片宽度。
- **外观卡片边距再收**：12dp − 10dp = **2dp**，与其它卡片左边缘对齐。

## v13.4 变更

- **外观预览卡边距**：`TopImageGuideView` 自身已带一部分卡片边距，之前再叠加 12dp 就比其它卡片窄。现在左右各再减 6dp（=6dp），与「选项」「屏幕亮度」等卡片左边缘对齐。
- **二级页返回后搜索栏下方卡片消失**：返回时列表先用旧分组画了一次装饰；现在 `apply` 后在 `post` 里补 `updateGroupInfo + invalidateItemDecorations + invalidate`（不再 `notifyDataSetChanged`），装饰器会按新分组重画，卡片不会缺。

## v13.3 变更

- **外观页改成“一张卡包住两张预览”**：
  - 背景加在 `light_mode_enable` 的父容器（两预览共同的横向 LinearLayout）上，`clipToOutline`
  - 左右边距取 `miuix_recyclerview_card_group_margin_start/end`（**12dp**），和首页其它卡片一致
  - 圆角取 `card_background_radius`（**16dp**），和主题卡片一致
  - 两张预览之间的间隔：`light_mode_enable` 的 `marginRight` 置 **0**，中间不再分开
  - 原来分半的两张卡背景清空，避免双层
- **搜索栏兜底色改成卡片白/深灰**（`#FFFFFF` / `#1C1C1E`），抓图不可用时不会露出页面底色。

## v13.2 变更

- **主界面不再反复刷新**：去掉 `HeaderData.apply` 里 `postDelayed(180ms)` 的第二次 `updateGroupInfo + notifyDataSetChanged + invalidateItemDecorations`，布局只做一次，之后只滚动，不重建。
- **外观白卡增加重试**：外观页的预览控件可能比 `onResume` 更晚 inflate，`AppearanceTweak` 现在每 250ms 重试（最多 8 次）直到找到 `light_mode_outer_view` 再上卡片背景。
- **搜索栏不再留洞**：如果某帧抓图不可用（`backdrop == null`），不再透明，改为直接画卡片底色（浅色 `#F2F2F7` / 深色 `#1C1C1E`），保证搜索栏下面永远不会出现“卡片缺失”的空白。

## v13.1 变更

- **外观页两预览各加一张卡片背景**：不再依赖外层容器（之前挂上去看不见），改成直接给 `light_mode_outer_view` / `dark_mode_outer_view` 各设置一张圆角卡片背景——颜色取 `miuix_recyclerview_card_group_background_light/dark`、圆角 16dp、`clipToOutline`。
- 如果两个 outer 都找不到，才退回挂 `light_mode_enable` 的父容器。
- 日志：`preview card background applied: true/false`。

## v13.0 回滚

- **恢复连续分组**：v12.9 不改 `groupId` 后，MIUI 用各行原始分组值，导致「都分开了」——几乎每行一张卡。v13.0 恢复按**连续区间**写 `groupId`（`GROUP_ID_BASE + group`），布局顺序完全不变，卡片重新合成整块。
- v12.8 的额外 `postDelayed(180ms)` 分组刷新保留。

## v12.9 变更

- **主界面卡片改回原机分组**：`HeaderData.apply` 不再改写 `Header.groupId`，卡片完全由 MIUI 自己按原始分组绘制（不再有我们合并/拆分的判断，也就不会再出现中间断卡）。排序仍然生效，但分组标记保持原值。
- **外观预览卡片再试一次**：白卡背景改为加在 `light_mode_enable` 的**父容器**上（之前挂在 `outer_image_view` 的第 0 个子 View 上，可能不是可视容器），颜色/圆角仍取主界面卡片同款。

## v12.8 变更

- **外观页间距恢复**：撤掉 v12.6 的 `marginRight=0` / `padding=0`，浅色/深色两张预览图之间恢复原来的间隔。
- **预览图加卡片背景**：在两张预览图（含「浅色 / 深色」文字）外面那一层容器上设置圆角卡片背景——颜色取 `miuix_recyclerview_card_group_background_light/dark`（和首页卡片同色），圆角 16dp，`clipToOutline`；效果和参考图里那张白卡一致。
- **卡片背景消失**：`updateGroupInfo + notifyDataSetChanged` 在 `post` 之后再补一次 `postDelayed(180ms)`（并 `invalidateItemDecorations`），避免装饰器用旧分组画卡片导致中间断卡。
- `CardFix` / `SubPageCards` 仍保持停用（v12.7 起），不再清任何行的背景。

## v12.7 修复

- **卡片背景消失**：`CardFix` / `SubPageCards` 会按「每组最后一行」清掉行的 background/foreground，但滚动后 adapter position 和 `mHeaders` 的下标会被列表头视图错位，导致**普通行被误判成最后一行**、背景被清掉，于是卡片出现“洞”。
  - v12.7 把这两个 hook 停用（`Hook.java` 里注释掉），不再动任何行的背景。
  - 代价：之前一直没修干净的那圈“末行 9-patch 叠加”会回来，但至少卡片背景稳定。
- 其余功能（排序、搜索栏模糊、外观间距等）不变。

## v12.6 变更

- **外观页浅色/深色预览图之间的间距删除**：`widget_light_dark_mode_top.xml` 里 `light_mode_enable` 的 `marginRight=4dp` 与两个 `*_outer_view` 的 `padding=dark_mode_picture_padding` 都置 0。新增 `AppearanceTweak`，hook `MiuiDisplaySettings.onViewCreated/onResume`（日志：`light/dark preview gap removed`）。
- **搜索模糊再降开销**：抓图缩放 0.4 → **0.3**，抓图间隔 16ms → **24ms（约 40fps）**，配合滚动偏移轮询，既跟手又少掉帧。

## v12.5 修复

- **搜索栏下面的卡片背景偶尔“消失”**：其实是抓图卡在旧帧（旧帧那里是页面底色），并不是卡片真的被删。v12.4 挂的 `RecyclerView.OnScrollListener` 在部分页面拿不到（`mListView` 不是 RecyclerView 或字段不同），所以 v12.5 再加一层**每帧轮询滚动偏移**：反射调用 `computeVerticalScrollOffset()`，偏移一变就立刻重抓，不依赖任何监听。
- 滚动后 1200ms 内依旧持续 16ms 抓图。

## v12.4 修复

- **模糊卡在「墙纸」不动**：根因是 `ViewTreeObserver.OnScrollChanged` 对 RecyclerView 不生效——RecyclerView 是靠移动子 View 滚动的，不会调用 `View.scrollTo`，所以那个监听几乎不触发，抓图只在首帧发生。
  - v12.4 通过反射给列表挂 `RecyclerView.OnScrollListener`（动态 Proxy），任何 `onScrolled`/`onScrollStateChanged` 都标记重抓并记录时间
  - 滚动结束后 1200ms 内仍持续按 16ms 抓图
  - 日志：`search glass scroll listener attached`

## v12.3 变更

- **浅色搜索栏加一点灰**：浅色玻璃叠层 `#B3FFFFFF` → **`#B3ECECF1`**（带一点冷灰的白），不再死白。

## v12.2 变更

- **模糊再降**：`RenderEffect` 半径 10dp → **6dp**，叠层维持浅色 `#B3FFFFFF` / 深色 `#14000000`。
- **搜索栏每次 install 都重建**：不再复用旧 View（主题切换、Activity 重建、配置变化都会留下失效的遮罩/监听），直接 `removeView` 后重新创建，保证新配置下可用。
- **点击提升**：容器 `elevation` 12dp → **40dp**，并 `setFocusable(true)`，避免被列表/搜索覆盖层挡住。

## v12.1 变更

- **更透明**：浅色叠层 `#E6FFFFFF` → **`#B3FFFFFF`**（70% 白）；深色 `#1F000000` → **`#14000000`**（8% 黑），两边都更透。
- **模糊跟随修复**：滚动后 **800ms 内强制持续抓图**（16ms 一帧），慢速拖动也不会因为滚动事件稀疏而停在某一栏。
- **切换深浅色后搜索栏失效**：除了夜间模式比对，还检查 `existing.getContext() != activity`（Activity 重建后旧 View 的 context 已失效），命中就移除重建。

## v12.0 变更

- **浅色搜索栏改为白色**：浅色模式下玻璃叠层 `#4D000000` → **`#E6FFFFFF`**（近白玻璃）。
- **深色降低不透明度**：深色模式叠层改为 **`#1F000000`**，模糊更透。
- **深浅色切换后搜索栏失效**：`BottomSearch` 记录 `lastNightMode`，主题/夜间模式变化时把旧玻璃 View 移除并**重建**（旧的捕获遮罩、颜色、监听都属于上一套配置，复用会失效）。这也顺带修掉切换后点击无效的问题。
- **模糊停在某一栏**：移除了帧预算自适应里的 `nextAllowed` 推迟逻辑（它会让抓图连续跳帧），现在固定按 16ms 间隔抓取，滚动时逐帧更新。

## v11.9 变更

- **模糊左右留白再收 6dp**：主题那张 9-patch 的左右是烘焙进图的透明边距（`getPadding()` 拿不到），所以除了按 padding 内缩，模糊裁切矩形再额外左右各内缩 **6dp**，让模糊和看得见的搜索栏胶囊重合。
- 高度不变（只收宽度）；60fps 抓图与 0.4 缩放优化保留。

## v11.8 变更

- **模糊左右留白收掉**：裁切矩形在原生 9-patch 宿主范围内，再按该背景 drawable 的 `getPadding()` 内缩，模糊只覆盖可见胶囊，不再有左右那圈白留白处的模糊。
- **滑动更顺**：
  - 抓图位图按 **0.4 缩放**（像素量约原来的 1/6），光栅开销大幅下降
  - 帧预算补偿上限收紧：只有单次抓图 >10ms 才推迟 16ms，>20ms 才 32ms（原来会累积到 48ms，导致模糊停在某一栏不动）
  - 抓图刷新目标仍是 16ms（约 60fps）
- 模糊颜色保持 v11.7 的 `#4D000000` 加深。

## v11.7 变更

- **多出来的那圈模糊**：v11.6 找背景 View 时可能抓到 `bar` 根容器（它的 bounds 就是整个容器）而不是那张 9-patch。v11.7 改成：
  1. 递归收集所有带背景的 View
  2. 优先选背景是 `NinePatchDrawable` 的（含 `RippleDrawable` 里包着的 9-patch）
  3. 没有就选面积最小的那个
  模糊层按选中 View 的实际宽高 + `height/2` 圆角裁切。
- **模糊背景调深**：裁剪区域内模糊后再叠一层 `#4D000000`，玻璃更暗。

## v11.6 变更

- **模糊层裁剪到原生搜索栏 9-patch 的精确范围**：`SearchGlass` 现在找到 stub 里真正带背景的那个 View（也就是 `miuix_appcompat_search_mode_edit_text_bg_light/dark.9.png` 的宿主），把模糊层按它的 `getLocationInWindow` 换算成本地坐标，用 `mask.width × mask.height`、半径 `height/2` 的胶囊路径裁切；不再铺满整个容器，所以模糊的高度/宽度和原生背景图完全一致。
- 原生 9-patch 本身仍然一点没动（位置、大小、透明度都不变），模糊只在它背后。
- 60fps 抓图 + 帧预算自适应保留。

## v11.5 变更

- **原生搜索栏 9-patch 原样保留**：不再清 stub 背景，`miuix_appcompat_search_mode_edit_text_bg_light/dark.9.png` 的位置、大小、透明度完全不动；`SearchGlass` 只在它**下面**画实时模糊，自身不再叠加任何白色/图片层。
- **滑动流畅度优化（保持 60fps 目标）**：
  - 抓图区域 padding 8dp → **4dp**（位图更小，光栅更省）
  - 增加**帧预算自适应**：每次抓图计时，超过 8ms 就把下一次抓图推迟 24ms（超过 16ms 推迟 48ms），避免连续掉帧；正常情况下仍按 16ms（60fps）刷新
  - 仍只在滚动/布局需要时抓图，静止不抓

## v11.4 变更

- **搜索栏图片保留，直接叠在模糊层上**：不再用白色圆角矩形替代，改为把 `search_bar_bg.9.png`（用你给的图做的那张）按 `alpha=0xA8` 直接画在模糊结果上面，胶囊形状和图片一致。
- 模糊抓图仍保持 16ms（约 60fps）刷新。

## v11.3 变更

- **重新启用玻璃搜索栏**，并把白色做得更接近原生那条：
  - 白色玻璃层 `#40FFFFFF` → **`#5AFFFFFF`**
  - 描边 `#33FFFFFF` → **`#4DFFFFFF`**
  - 容器高度 50dp，原生 stub 背景清掉，只留放大镜/文字/麦克风
- **模糊刷新频率调高**：抓图节流 50ms（20fps）→ **16ms（约 60fps）**，滚动时模糊几乎逐帧更新。
- 代价说明：真正实时模糊必须盖住原生不透明白胶囊才能看见，所以只能做成“白色半透明玻璃”而不是原样不透明条；如果卡顿回来，把节流调回 33ms 即可。

## v11.2 回滚

- **恢复原生搜索栏**：底部搜索栏容器换回普通 `FrameLayout`，不再用 `SearchGlass`；原生 stub 的背景/文字/图标原样保留，即原来那条白色胶囊搜索栏。
- **卡顿修复**：干掉了「每帧把窗口根视图重绘到 Bitmap + RenderNode 模糊」的管线（那是 v11.0/11.1 卡顿的来源），现在没有任何逐帧抓图。
- 尺寸回到 54dp 高、左右 8dp；搜索时隐藏 / 退出恢复的 ActionMode 回调逻辑保留。

## v11.1 修复

- **尺寸与搜索栏一致**：玻璃容器高度 56dp → **50dp**；同时递归清掉原生 stub 里所有子 View 的背景（之前那层白色胶囊就是 stub 自己画的），只保留放大镜/文字/麦克风。
- **改成白色半透明**：不再把那张黑色 9-patch 实心盖上去，而是用它的形状做遮罩——胶囊内先画模糊背景，再叠 `#40FFFFFF` 白色玻璃层 + 1dp `#33FFFFFF` 描边。
- **模糊调小**：`RenderEffect` 模糊半径 16dp → **10dp**。

## v11.0 变更

- **搜索栏换成实时模糊玻璃**：新增 `SearchGlass`（继承 FrameLayout）作为底部搜索栏容器：
  1. 用你给的图做了一个标准 9-patch（圆角胶囊、带拉伸标记）→ `module/res/drawable-xxhdpi/search_bar_bg.9.png`
  2. 每帧前把窗口根视图绘制到小块 Bitmap（绘制时自身 alpha=0，同帧恢复），滚动时标记重抓（50ms 节流）
  3. 用 `RenderNode` + `RenderEffect.createBlurEffect(16dp)` 模糊这张背景，裁剪成胶囊
  4. 9-patch 以 alpha 200 盖在上面，形成深色磨砂玻璃，背景实时透出
  5. 原生搜索 stub 的自身背景已清掉，只保留文字/图标，不再像以前那样直接叠一块实心条
- 底部栏尺寸保持 56dp、左右 8dp；主界面显示、搜索时隐藏、退出搜索恢复（v10.5 的 ActionMode 回调逻辑不变）。

## v10.5 修复

- **主界面搜索栏消失**：v10.4 的全局布局监听把 `search_result_ll` 当成「搜索中」，可它默认就是 VISIBLE（有时还带高度），结果主界面一布局就把底部栏隐藏了。v10.5 彻底去掉这个启发式判断：
  - 主界面/进入页面时底部栏一律 **VISIBLE**
  - 点击底部栏 → 立即隐藏
  - hook `mSearchCallback` 的 `onCreateActionMode` → 隐藏；`onDestroyActionMode` → 恢复
  不再依赖任何控件可见性猜测。
- 桌面图标仍然是隐藏的（v10.4 已删 LAUNCHER intent-filter）。

## v10.4 变更

- **关闭搜索后底部搜索栏消失**：加了 `OnGlobalLayoutListener`（挂在 `android.R.id.content` 上，每页只装一次），每次布局重算时按搜索态切换底部栏：搜索中 → GONE，退出搜索 → VISIBLE。点击后 600ms 内不因布局事件提前弹回，避免闪一下。
- **隐藏桌面图标**：`MainActivity` 的 `MAIN/LAUNCHER` intent-filter 已删除，安装后桌面/抽屉不再出现图标；LSPosed 里仍能看到模块（`xposedmodule` meta-data 不变）。

## v10.3 变更

- **搜索栏加长**：左右边距 16dp → **8dp**，高度 54dp → **56dp**，横向更接近 iOS 那条。
- **点击修复**：点击时先 `anchor.performClick()`；如果返回 false（MIUI 搜索没起来），再走兜底——反射取 `SettingsFragment.mSearchCallback`，直接调 `startActionMode(callback)`。点完立即把底部搜索栏 `GONE`。
- **搜索时不再双搜索栏**：`BottomSearch` 每次 install（onViewCreated / onResume）会检测 `search_result_ll` / `search_action_mode` / `search_mode_stub` 是否可见且有高度，处于搜索态就把底部栏隐藏；退出搜索（onResume）再显示。主界面仍然保留底部搜索栏。

## v10.2 修复

- 上一版删错了「高级设置」：现在改为**保留原生的高级设置 header**（`removeSyntheticAdvanced` 删掉合成那条），并把原生那条按标题归到钱包卡 slot 3——位置仍然是 通用 的正下方。
- 钱包卡顺序：钱包 → 通用 → 高级设置（原生）→ 未成年人。

## v10.1 修复

- **两个高级设置**：原来 `other_advanced_settings` 被移到钱包卡后，通用卡里还留着另一个标题为「高级设置」的原生 header。现在 `removeDuplicateAdvanced()` 按标题去重，只保留合成的那条（`advanced_settings_synth`）。
- **隔空播放回到首页**：不再加进控制中心页（`WirelessPage` 只保留删除飞行模式那一栏），首页通用卡顺序改为 **关于设备 → 无障碍 → 隔空播放与连续互通 → 待机显示 → 墙纸 → 控制中心 → 外观 → 主屏幕与App资源库 → 超级小爱 → Siri**；缺 header 时会用 MiLink intent 合成一条。
- 钱包卡维持：钱包 → 通用 → 高级设置 → 未成年人（Game Center）。

## v10.0 变更

- **通用 / 高级设置回到首页**（不再往关于本机页塞）：
  - 钱包卡顺序变成：钱包 → **通用** → **高级设置** → 未成年人
  - 「高级设置」是合成行（图标 `ic_other_advanced_settings`，fragment `OtherPersonalSettings`），位置就在通用下面
- **无障碍移到关于设备下面**：通用卡顺序变成 关于设备 → **无障碍** → 待机显示 → 墙纸 → 控制中心 → 外观 → 主屏幕与App资源库 → 超级小爱 → Siri；安全卡只剩 面容 → SOS → 隐私。
- `AboutExtras` 不再安装（关于本机页恢复原生，不再追加「通用」、不再强制材质）。

## v9.4 修复

- **新增行「细条」的真正原因**：v9.3 拿「页面第一个 Preference」当模板，而它往往是一个 `PreferenceCategory`（分组标题），于是我们新建的行也成了分类标题 → 只有一条细条。现在模板筛选条件：
  - class 名不含 `Category`
  - `getWidgetLayoutResource()==0`（避免复制到开关的 widget 布局）
  - 必须有 title
  这样复制到的是普通行的 `layoutResource`，隔空播放 / 通用 都能正常成行。
- **高级设置还在首页**：只按 `idName` 删除没命中（主题改了标题）。v9.4 额外按 **标题包含「高级设置」/「通用」** 删除，双保险；同时关于本机页底部仍会追加这条入口。

## v9.3 变更

- **去掉高级材质强制**：不再对关于本机页的 `miui_version_card_view` 调用 `setBackgroundBlur` / 强制硬件层，恢复原生背景。
- **账户副标题彻底替换**：新增 `AccountTextFix`，hook `TextView.setText`——任何文本只要包含「云服务 / 会员权益」就替换成「Apple账户、iCloud+等」。不管 MIUI 是在绑定、异步刷新还是回收时写旧文案，最终显示的都是新的，不会再闪回。
- **新增条目显示不全修复**：隔空播放 / 通用 这类动态添加的 Preference 之前是细条，因为新建 preference 没带 MIUI 的布局资源。现在会先拿页面上已有的 Preference 作模板，**同 class 构造**，并复制 `layoutResource` + `widgetLayoutResource`；没有模板时退回 `miuix_preference_layout`。
- 「通用（高级设置）」继续在关于本机页底部追加，`setFragment` 指向 `OtherPersonalSettings`。

## v9.2 修复

- **账户副标题偶发回退**：根因是 MIUI 每次重建 header 列表（resume / 刷新）后，绑定发生在我们的 after-hook 之前，所以会先显示旧的「账号、云服务、会员权益等」。v9.2 在 `HeaderAdapter.onBindViewHolder` 的 **before** 阶段就把 `Header.summary` 写成「Apple账户、iCloud+等」，并把 `summaryRes` 清 0（强制走字符串），这样 MIUI 绑定时读到的就是新文案，after-hook 和 `post` 再兜底一次。

## v9.1 变更

- **隔空播放行显示不全**：v9.0 直接 `addPreference` 到页尾，落进了「在查找其他设置吗?」那个特殊分类里，被压成一条细条。现在先把这个分类摘出来，把「隔空播放与连续互通」插到它**前面**再放回分类；并且优先用 miuix 的 `Preference`（布局正常），找不到才退回 androidx。
- **关于本机强制高级材质**：`AboutExtras` hook `MiuiMyDeviceSettings.onViewCreated/onResume`，对 `miui_version_card_view` 调 `MiuiBlurUtils.setBackgroundBlur(view, 120)`，子 View 强制硬件层；配合已有的 HyperMaterial 强制开关。
- **通用/高级设置移到关于本机页底**：首页不再显示这一行；`AboutExtras` 在关于本机页底部追加一条「通用（高级设置）」，`setFragment` 指向 `com.android.settings.personal.OtherPersonalSettings`。

## v9.0 变更

- **文字跟着图标右移**：图标 `translationX=3dp` 的同时，标题与副标题也加 3dp，图标和文字间距保持不变。
- **账户副标题不再回退**：除了改 TextView，还把 `Header.summary` 直接写成「Apple账户、iCloud+等」，并在 `post` 里再补一次，resume / 滚动复用都保持新文案。
- **箭头不再消失**：清掉飞行模式行的箭头后，回收给普通行时按 `fragment/intent` 重新 `setVisibility(VISIBLE)`，之前「有的箭头没了」就是复用残留。
- **关于设备移入通用卡**：「关于设备」从独立卡移到「通用」卡的**第一行**，「通用」紧跟其后：关于设备 → 通用 → 待机显示 → 墙纸 → 控制中心 → 外观 → 主屏幕与App资源库 → 超级小爱 → Siri（原来那张独立「关于设备」卡没了）。
- **隔空播放移入控制中心二级页**：首页不再显示「隔空播放与连续互通」；新增 `WirelessPage`，hook `com.android.settings.MiuiWirelessSettings`，在该页加一条「隔空播放与连续互通」（intent `miui.intent.action.MIRROR_SETTING` → `com.milink.service`，可解析才加），并**删除该页里的飞行模式一栏**。

## v8.7 变更

- **图标整体右移 3dp**：所有 header 行图标（含账户头像）统一 `translationX = 3dp`，配合之前的 28dp 统一尺寸，视觉上和卡片内边距更协调。
- **账户副标题改成 iOS 文案**：账户行 summary 由「账号、云服务、会员权益等」改为 **「Apple账户、iCloud+等」**，每次绑定都会覆盖，滚动复用也不会回退。

## v8.6 变更

- **二级界面也去掉末行叠加图**：新增 `SubPageCards`，hook `androidx.preference.Preference.onBindViewHolder`（目标 classloader），对「每个 PreferenceGroup / PreferenceCategory 的最后一项」清掉 background + foreground（缓存/还原），删除那四张主题 9-patch 在卡片最后一行的叠加，中间行不变。
- **图标统一大小、统一边距**：首页所有 header 行的 `@android:id/icon` 统一设成 **28dp × 28dp + FIT_CENTER**（账户头像仍单独 56dp 圆形）；飞行模式图标也固定 28dp，不再按邻居动态测量，所以不会再出现「有的偏大、有的偏左」。
- 保留 v8.5 的 ClassLoader 修复（CardFix 现在真正生效）。

## v8.5：找到「没有区别」的根因

`CardFix`（负责清最后一行 background/foreground、拦那四个主题 9-patch）一直用 **模块自己的 ClassLoader** 去 `XposedHelpers.findClass("com.android.settings.MiuiSettings$HeaderAdapter", CardFix.class.getClassLoader())`——模块 classloader 里根本没有 Settings 的类，所以 findClass 直接抛异常，`install()` 里两个 hook 全部没装上，日志里是 `card background hook failed`。

也就是说 v7.1 之后所有「最后一行去叠加图」的代码从未执行过，表现当然和以前一模一样。

v8.5：

- `CardFix.install(ClassLoader cl)` 改为接收 **目标进程 classloader**（`Hook.install` 传进来的那个），并用它 findClass `HeaderAdapter` / `HeaderViewHolder` / `ResourcesImpl`
- 最后一行清除成功时打日志：`last row cleared: <标题>`；hook 装成功打 `hooked HeaderAdapter.onBindViewHolder (card bottom fix)`
- 复用已有开关时也补一次 `recenter`，避免 HyperiOS 后置改 padding 后开关再次偏下

## v8.4 变更

- **卡片最后一项不显示那四个主题 9-patch**：在绑定「每组最后一行」时用 ThreadLocal 标记，hook `ResourcesImpl.loadDrawable`，命中下面四个资源名时直接返回透明 drawable，绑定结束后再清掉该行的 background/foreground：
  - `miuix_preference_item_background_light/dark.9.png`
  - `miuix_recyclerview_card_item_press_fg_light/dark.9.png`
- **图标大小忽大忽小**：原来量「第一个邻居图标」作尺寸，邻居不同结果就不同；而且最后一行本来就被那几张 9-patch 撑大。现在改为取所有邻居图标宽度的**最小值**（稳定值）再套用，配合上面的抑制，飞行图标和其余图标一致。
- **HyperiOS 下开关偏下**：不再只在绑定时校正一次。给行加 `OnLayoutChangeListener`，每次布局都重新计算开关中心与行中心的 dy 并补偿，另在 `post` 和 `postDelayed(120ms)` 各补一次，HyperiOS 后置改 padding 也能拉回居中。
- **构建脚本修复**：class 文件太多导致 d8 命令行超长，改为先 `jar` 打包再喂给 d8。

## v8.3 变更

- **开关改回原生**：飞行模式行恢复 MIUI 原生开关（`miuix_preference_widget_switch`，失败回退 `SlidingSwitch`/`Switch`），玻璃滑块不再使用。
- **飞行模式图标对齐**：不再写死 28dp。绑定后 `post` 里量同一列表里**相邻行图标的实际宽度**，把飞机图标设成同样的尺寸（量不到才回退 28dp），这样左边距、大小都和无线局域网等图标一致，不会偏左。
- **二级界面图标删掉**：`SubPageIcons` 的图标注入全部停用（hook 保留但 no-op），二级页的行恢复原生无图标；顶部介绍卡仍保留。
- **HyperiOS 下开关偏下**：原生开关的 `translationY` 自动校正逻辑还在（绑定后 `post` 里比较开关中心与行中心，偏差 >1dp 就补偿），HyperiOS 改行内边距时会自动拉回垂直居中。

## v8.2 变更

- **开关位置/尺寸修正**：`GlassToggle` 之前被父容器用 EXACTLY 测量撑到整行高，且贴死卡片右边（截图里右侧被裁）。现在 `onMeasure` 固定 56×32dp（父约束更小时才缩小），并给开关加 14dp 右外边距，和箭头同一内缩。
- **真·逐帧抓背景 + 折射**：`GlassToggle` 重写成抓背景管线——
  1. `OnPreDrawListener` 里把窗口根视图 `root.draw()` 到一张小 Bitmap（绘制前把自身 alpha 设为 0，绘制完立刻恢复，同帧不闪）
  2. 轨道用 `RenderNode` + `RenderEffect.createBlurEffect(9dp)` 对这张背景做磨砂，裁剪在胶囊内，再叠绿色/中性色调与高光
  3. 玻璃球用**未模糊**的背景按 1.18× 放大采样（src→dst），形成真实透镜折射，再叠磨砂、内阴影、高光点、描边
  4. 滚动/拖动/动画时标记 `needsCapture`，16ms 节流重抓；抓取失败自动回退到纯渐变版
- 日志：抓取失败会打 `glass backdrop capture failed`。

## v8.1 修复

- **飞行模式图标尺寸对齐**：模块内置的飞机图是 128px，直接塞进 header 的 `@android:id/icon` 会被当成 42dp 左右（截图里明显比旁边大一圈）。现在给飞行模式行的图标 ImageView 显式设成 **28dp + FIT_CENTER**，和其它行图标同大小。
- **HyperiOS 入口会被清掉**：`apply()` 里已有的识别逻辑会把 intent 指向 `dev.hyperios.app` 的 header 全部移除（包括 HyperiOS 自己注入的那条），所以 8.0/8.1 装上后列表里不会再出现 HyperiOS 那行。
- 玻璃滑块（`GlassToggle`）是 v8.0 才有的，8.1 保持。

## v8.0 变更

- **移除 HyperiOS 应用入口**：v7.4 加的那个「HyperiOS」header 去掉了（你要的是设置里的滑块本体，不是跳转入口）。
- **设置里的玻璃滑块（自绘）**：新增 `GlassToggle`，替换飞行模式那一行的原生开关，直接画在设置列表里：
  - 轨道：磨砂胶囊，上下 LinearGradient 高光 + 底部加深 + 1dp 白色 rim light，开= iOS 绿 `#34C759`，关=浅色 `#E9E9EB` / 深色 `#39393D`，按进度插值
  - 滑块：玻璃球，RadialGradient（左上高光 → 冷边）+ 底部内阴影 + 高光点 + 柔和投影（软件层 3dp）
  - 交互：按下/拖动阻尼跟随（`requestDisallowInterceptTouchEvent`），松手过 0.5 才切换，220ms DecelerateInterpolator 回弹；点整行也能切
  - 状态与系统飞行模式双向同步（`Airplane.isOn/set`）
- 尺寸 56×32dp，深色模式自动换色，日志 `glass toggle -> airplane true/false`。

## 关于“照搬 HyperiOS 渲染”

HyperiOS 那套是 Compose + `LiquidSliderHost` / `LiquidSliderViewBackdrop` + `glass/DampedDragAnimation`，靠 RenderEffect 抓窗口硬件缓冲做实时折射，属于它自己的渲染管线，没法以 View 形式直接搬进另一个进程的模块里。另外设置行背后就是纯色卡片，真做背景折射出来也是白的——所以这里按它的视觉语言（磨砂渐变 + 玻璃球 + 高光 + 阻尼）用 Java 重画，观感一致、开销更小。如果你确实要逐帧抓背景+折射的版本，需要单独的窗口捕获管线，工作量和风险都大，可以再单独做。

## v7.4 变更

- **飞行模式图标终于对了**：v7.1 用的 `ic_device_connection` 在主题里其实是「控制中心」那个双旋钮图标，所以没变。v7.4 把你给的两张图内置成模块资源 `ic_plane_light` / `ic_plane_dark`，在绑定飞行模式行时按深浅色直接 `setImageDrawable`——浅色橙底白飞机、深色黑底橙飞机。
- **底部重复的飞行模式行**：`apply()` 每次 resume 都会新建一个合成行，旧行残留在列表尾部的 extras 分组里，越积越多。现在 apply 前先删掉所有合成行（按 intent action / 标题识别）再添加，永不多余。
- **卡片末行叠加图**：v7.1 的 CardFix 里 `holder.adapter` 取不到（holder 没这个字段），所以整段没执行。现在从 hook 的 `thisObject` 拿 adapter，末行清 background + foreground，中间行恢复原值。
- **HyperiOS 设置入口**：逆向了 `D:\下载\HyperiOS_1.0.0(1).apk`（包名 `dev.hyperios.app`），它的 `SettingsEntryHook` 会 hook `MiuiSettings.updateHeaderList`，在 `my_device` 后面插一个标题为 **HyperiOS** 的 header，intent 指向 `dev.hyperios.app/.ui.MainActivity`，图标用它的 `ic_settings_entry_flat`/`monet`。v7.4 在我们的模块里复刻了这个入口：单独一张卡（关于设备下面），图标直接从 HyperiOS 的 Resources 取，点击打开 HyperiOS（玻璃滑块的设置就在它里面）。如果列表里已经有 HyperiOS 标题（HyperiOS 自己也注入了），我们的会跳过，避免重复。

## v7.1 变更

- **飞行模式图标改用主题里那张飞机**：图标资源从 `ic_airplane_mode_settings` 换成 `ic_device_connection`——主题包里正好有它的浅色/深色两版（`res/…/ic_device_connection.png` 橙色底白飞机、`nightmode/…/ic_device_connection.png` 深色底橙飞机），跟随系统深浅色自动切换，不用模块自带图。
- **去掉每张卡最后一个选项的叠加图片**：分析主题包后确认，主题把 `miuix_preference_item_background_*` 和 `miuix_recyclerview_card_item_press_fg_*` 都换成了透明的 9-patch，卡片本身由 miuix 的 CardItemDecoration 画；但每一行仍带自己的 background/foreground drawable，画在 item 的矩形范围内，到卡片最后一行时会比卡片圆角多出一点，形成“双圆角/叠加图”。v7.1 在 `HeaderAdapter.onBindViewHolder` 里对**每组最后一行**清掉 background + foreground（缓存原值，回收复用成普通行时再还原），中间行不受影响，分割线和按压反馈都保留。

## v7.0 变更

- **关于本机换回原生**：移除自绘的 iOS About 页，点「关于设备」就是 MIUI 原来的页面（介绍卡也在这个页面跳过，保持纯原生）。
- **「控制中心」移到「墙纸」下面**：通用卡顺序变成 通用 → 待机显示 → 墙纸 → **控制中心** → 隔空播放 → 外观 → 主屏幕 → 超级小爱 → Siri。
- **原位置改成「飞行模式」行**：网络卡第一行现在是 飞行模式（图标 `ic_airplane_mode_settings`），原来的开关跟着搬过来——开关就是飞行模式开关；点这一行（左侧）也会切换飞行模式；箭头隐藏，开关贴右。

## v6.7 修复

- **卡片底色真正共用**：关于本机的卡片颜色不再写死，改为解析 MIUI 自己的 `miuix_recyclerview_card_group_background_light / _dark`（主界面卡片用的就是这两个颜色，主题覆盖也会跟着变），和主界面卡片完全同色。
- **最后一个选项的下划线**：
  - 关于本机页：卡片内部只在行与行之间画分隔线，最后一行不加（本来就是这样，这次又确认了一遍）。
  - 首页：卡片圆角/分割线由 miuix `CardGroupAdapter` 的 type map 决定，之前可能在我们 `updateGroupInfo()` 之后又被一次布局用到旧 map。v6.7 在 `post` 里再做一次 `updateGroupInfo() + notifyDataSetChanged() + invalidateItemDecorations()`，确保最后一次绘制用的是新分组，最后一行不再多一条线。

## v6.6 修复

- **搜索栏点不动**：底部搜索条是加在 `android.R.id.content` 上的，但 fragment 的列表视图是在 `onViewCreated` 之后才挂进容器的，结果列表盖在搜索条上面——看得见、点不到。现在给搜索条 `setElevation(12dp)` + `bringToFront()`（添加后、resume 时、post 后各一次），触摸命中按 Z 排序，列表再也压不住它；同时把 stub 里的 TextView/Frame 全部 `clickable=false`，点哪都算整条搜索栏。
- 日志：`bottom search clicked` / `bottom search performClick -> true|false`。

## v6.5 修复

- **关于本机背景终于和主界面共用**：直接取 Settings 的 `background_grey` 颜色资源（MIUI 自己的 windowBackground，浅色 `#F3F2F8` / 深色同源），不再用 `windowBackground` 兜底猜色。
- **名称可改**：关于本机的「名称」行可点击，弹原生输入框，保存时写 `Settings.Global.device_name` + `Settings.Secure.bluetooth_name` + `BluetoothAdapter.setName`，行内数值即时更新；「型号名称」保持只读、不响应点击。
- 顶部裁剪仍用实测重叠（`about page top padding = N (overlap=M)`）。

## v6.4 修复

- **背景彻底共用**：自绘页不再设置任何背景色（透明），直接露出窗口/主题自己的背景，和主界面完全同一层底色，不会再出现我们自己调的灰/白。
- **顶部裁剪用实测值**：页面加进去之后，在 `post` 里用 `getLocationOnScreen` 实测 appbar 底边与页面顶边的重叠，再给内容加 padding（取不到 appbar 时用状态栏 insets 兜底），日志：`about page top padding = N (overlap=M)`。不再靠猜 root padding。

## v6.3 修复

- **关于本机顶部被压掉**：自绘页面之前没算 MIUI 内容区的顶部 padding，前两行（名称/系统版本）被 appbar 盖住。现在先取原生 fragment 根的 `paddingTop`，取不到再用「appbar 高度 + 状态栏 insets」兜底，整页下移。
- **箭头共用**：不再用字符 `›`，改成 MIUI 自己的 `miuix_appcompat_intent_arrow_right` drawable（和主界面列表同一个箭头），取不到才回退字符。
- **背景/分割线**：页面底色读主题 `android:windowBackground`（实测和主界面一致 `#F3F2F8`），卡片白/深色、分割线 `#E5E5EA` 与主界面统一；底部原生入口同样用这套卡片、分割线、箭头。

## v6.2 修复

- v6.1 想“在原生列表上加页头”，但 `MiuiMyDeviceSettings` 的列表不是独立的 RecyclerView，`getListView()` 拿不到，所以整个页头没加上，看起来就“变回去了”。
- v6.2 改回“整页自绘”，但**把原生的入口全部按原顺序重建到最底**：
  - 顶部：名称 / 系统版本 / 型号名称 / 型号 / 序列号
  - 第二张：歌曲 / 视频 / 照片 / 应用程序 / 总容量 / 可用容量
  - 第三张：证书信任设置
  - **最底**：MIUI 原来的那些入口（全部参数与信息、小米换机、备份与恢复…），点按仍走原 preference 的 `performClick()`
- 页面背景不再写死，改成读主题的 `android:windowBackground`（取不到才回退浅/深色默认），所以跟原生页面一致。

## v6.1 修复

- **「关于本机」改成“顶部加卡片”而不是整页替换**：MIUI 原来的列表和条目全部保留，滚动到最底就是原来那些选项；iOS 的信息卡（名称/系统版本/型号名称/型号/序列号、歌曲/视频/照片/应用程序/总容量/可用容量、证书信任设置）作为页头插在列表上方，背景直接用原生的，不再自己设页面底色。
- 页头随列表滚动一起划走，列表顶部按页头高度留白。
- v6.0 里“隐藏原生页面、自己画整页”的那套已删除。

## v6.0 变更

- **账户名称加大**：账户行的名字从默认字号改成 **22sp + sans-serif-medium**，更接近 iOS 参考图。
- **「关于本机」整页改成 iOS 样子**：进「关于设备」时用自绘页面替换 MIUI 原生内容，三张卡：
  - 名称 / 系统版本 / 型号名称 / 型号 / 序列号
  - 歌曲 / 视频 / 照片 / 应用程序 / 总容量 / 可用容量
  - 证书信任设置（带箭头，点了去系统证书页面，找不到会提示）
  - 名称取 `Settings.Global.device_name`，系统版本取 `ro.mi.os.version.name` / `ro.miui.ui.version.name` + Android 版本，型号名称取 `ro.product.marketname`，序列号取 `Build.getSerial()`（取不到显示 —），媒体数量走 MediaStore，容量走 `StatFs(/data)`，应用数量走 PackageManager。
  - 深色模式自动换色；介绍卡在这一页会自动跳过（避免和自绘页面重复）。

## v5.3 变更

- **头像上下留白**：账户行头像 56dp，并加 12dp 上下外边距，不再贴着卡片边。
- **「关于设备」单独一张卡**：账户卡只留 账号 + 我的 Airpods；关于设备独立成一张卡，排在它下面。
- **二级页介绍卡（v5.2 加的能力）**：进入二级页时在列表顶部加一张 iOS 风格介绍卡——大图标 + 页面名 + 一段说明；列表顶部预留同等高度，滚动时卡片跟随内容一起划走。图标按页面名从 Settings 主题 drawable 里取，描述用内置文案表（通用/显示/声音/WLAN/蓝牙/电池/隐私/安全/无障碍/桌面/墙纸/锁屏/热点/蜂窝/应用/关于设备/控制中心/隔空播放…），没有对应文案的页面用“管理与『页面名』相关的设置和偏好”。

## v5.1 修复

- **账户头像没放大**：账户 header 的 id 在你机器上可能取不到名字，v5.1 增加了「summary 里含云服务/会员权益」「界面上绑定的 summary 文本」两种兜底识别；识别到后把 `@android:id/icon` 直接设成 56dp、`CENTER_CROP`，并打印 `account avatar resized to … id=…`。
- **二级页标题可能整条消失**：现在强制 `CollapseTitle.setVisibility(VISIBLE)` + `setTitleVisibility(VISIBLE)` + 布局可见，并复查 `getVisibility()`；如果还是不可见，会把 MIUI 原本的大标题恢复回来，保证页面一定有名字（日志：`compact title still hidden, restored expand title`）。

## v5.0 变更

- **滚动时顶栏模糊**：给 `action_bar_container` 挂了滚动监听；列表 `computeVerticalScrollOffset() > 4dp` 时把顶栏底色换成半透明 + `MiuiBlurUtils.setBackgroundBlur(view, 110)`，回到顶部恢复实色并 `clearBackgroundBlur`。只在滚动时出现，不滚动时保持原生。
- **二级界面标题仿图一**：进入二级页时隐藏 MIUI 的展开大标题（`ExpandTitle`），把折叠标题 `CollapseTitle` 设为居中（`updateTitleCenter(true)`）并显示条目名；找不到 `CollapseTitle` 时不动，避免把标题弄没。
- **小米账户头像放大**：首页账户行图标从原生 34dp 放大到 56dp，和 iOS 参考图比例接近。

## v4.3 变更

- **开关偏大/偏下**：原来直接 `new SlidingSwitch(context)`，没有套用 `preferenceTheme`，拿到的是默认尺寸（偏宽、内部留白让视觉偏下）。现在改为 inflate 系统自己的 `miuix_preference_widget_switch`（用 `preferenceTheme` 包装的 Context），尺寸和设置里其它开关一致；另外加了“按行中心自动校正 translationY”，并打印 `dy` 诊断。
- **强制模糊**：hook `HyperMaterialUtils.isEnable() / isFeatureEnable(Context)`、`MiuiBlurUtils.isEffectEnable(Context)` 一律返回 true，并置 `isForceEnable=true`（仅当 `SUPPORT_HYPER_MATERIAL` 为 true 时），这样不管主题/系统开关怎么关，澎湃自己的材质模糊都会开着；首页的 `NestedHeaderLayout` 也强制 `setEnableBlur(true) + setOverlayMode(true)`。

## v4.2 修复

- 隐藏箭头改成**双路**：优先用 `HeaderViewHolder.arrowRight` 字段（和 MIUI 自己用的同一个 ImageView），字段取不到再按 `arrow_right` id 找，避免 id 解析不到导致箭头没隐藏。
- `widget_frame` 去掉 `minWidth`、清 padding、gravity 改为 `END`，开关尽量贴右。
- 新增诊断日志：绑定后会打印
  `airplane switch laid out: switchRight=... rowRight=... gap=... rowClass=...`
  ——`gap` 就是开关右边到行右边的像素距离，行类名能看出用的是 LinearLayout 还是 HyperCellLayout。如果还偏，把这行发我就能算准偏移量。

## v4.1 修复

- **开关位置**：控制中心这一行原来右侧还有箭头，开关被挤在箭头左边。v4.1 把这一行的 `arrow_right` 隐藏了，开关对齐到最右侧，和设置里其它开关同一位置。
- **隔空播放位置改回去**：不再跟着控制中心上移，回到「通用」那张卡里「墙纸」下面。

## v4.0 变更

- **「控制中心」移到「无线局域网」上面**（现在是第 2 张卡的第一行）
- 控制中心这一行右侧加了**系统同款的开关**（`miuix.slidingwidget.widget.SlidingSwitch`），开关飞行模式
  - 点开关：写 `Settings.Global.airplane_mode_on` + 发 `ACTION_AIRPLANE_MODE_CHANGED`，和控制中心里的飞行模式一致
  - 点左侧标题：仍然进入原来的「控制中心」页面
- 隔空播放与连续互通跟着控制中心，放在它下面
- 顺序表里其余卡片相应上移

## v3.1 修复

- **底部搜索条不显示**：v3 里用 `search_result_ll` 的可见性判断“是否在搜索”，但这个容器非搜索时也是 VISIBLE，导致底部条一装好就被自己隐藏。v3.1 去掉了这段判断。
- 底部条加了**日志自检**：装好后会打印 `bottom bar: vis=0 w=... h=... y=... rootH=...`，能直接看出是没加上、被隐藏、还是被裁掉。
- 原生 stub inflate 失败时，会退化成一条简单的胶囊搜索条（仍走同一个点击入口），不会再“什么都没有”。
- **隔空播放与连续互通**：补了 `interconnection` / `milink_settings` 等 id 别名和“互传/投屏”标题别名；如果 MIUI 把它裁掉了、但机器上确实有 MiLink 服务，会按 `settings_headers.xml` 里同样的 intent 补一条回来（没有 MiLink 则不补）。
- 日志会打印 `no header found for slot ids: ...`，直接说明哪些卡片条目在你机器上根本不存在。

依旧**不重绘页面、不换背景、不改行样式**，只在原生 UI 上做三件事：

1. 首页入口顺序 / 分组
2. 二级页面入口加图标
3. 搜索栏移到底部（用小米原生搜索）

> 目标底包：`com.android.settings` versionCode 36 / Android 16（澎湃 OS 3）

---

## 1. 首页顺序

| 卡片 | 顺序 |
| --- | --- |
| 0 | 账号 → 我的 Airpods |
| 1 | **关于设备**（单独一张卡） |
| 2 | **飞行模式（右侧开关，点行也能切）** → 无线局域网 → 蓝牙 → 蜂窝网络 → 个人热点 → 电池 → VPN |
| 3 | 通用 → 待机显示 → 墙纸 → **控制中心** → 隔空播放与连续互通 → 外观 → 主屏幕与App资源库 → 超级小爱 → Siri |
| 4 | 通知 → 声音与触感反馈 → 屏幕时间 |
| 5 | 面容ID与密码 → 无障碍 → SOS 紧急联络 → 隐私与安全 |
| 6 | 钱包与Apple Pay → Game Center |
| 7 | App |
| 8 | 其余原生入口（安全状态、系统应用更新、主题、位置信息、问题反馈…） |

「关于设备」页保持 MIUI 原生（v7.0 起不再自绘）。

控制中心行：**右侧开关控制飞行模式，左侧标题点击进入原来的「控制中心」（更多连接）页面**。这一行的箭头已隐藏，开关和系统其它开关一样贴最右边。

---

## 2. 二级页面图标

`SubPageIcons` 在 `PreferenceFragmentCompat` 的 `onCreate / onViewCreated / onResume` 之后遍历 preference 树，给**还没有图标**的条目 `setIcon(...)`。图标是模块内置的 iOS 风格圆角方块（28dp），按 preference key 匹配，key 不认识时按标题关键词兜底。

已覆盖（通用页整页 + 常见项）：

| key / 标题 | 图标 | key / 标题 | 图标 |
| --- | --- | --- | --- |
| `date_time_settings` 日期和时间 | 时钟 | `freeform_guide_settings` 自由窗口 | 窗口 |
| `language_settings` 语言与输入法 | 键盘 | `mi_wallet_settings` 卡包 | 钱包 |
| `locale_settings` 地区 | 地球 | `screen_recorder` 屏幕录制 | 录制 |
| `circle_to_search_switch` 搜索 | 放大镜 | `miui_desktop_mode_settings` 桌面模式 | 显示器 |
| `back_strap` 背部灯效 | 灯泡 | `external_ram` 内存扩展 | 芯片 |
| `game_booster` 游戏加速 | 手柄 | `parental_control` 家长控制 | 双人 |
| `beauty_fc_assistant` 美颜 | 相机 | `kid_space_settings` 儿童空间 | 单人 |
| `conversation_assistant` 通话助手 | 对话 | `edge_settings` 边缘设置 | 边缘 |
| `securitycenter_sidebar_box` 侧边栏 | 侧栏 | `speaker_settings` 扬声器清理 | 扬声器 |
| `taplus_settings` 传送门 | 门/传送门 | `user_settings` 用户 | 人像 |
| `smart_clipboard_settings` 复制直达 | 剪贴板 | `account_sync` 账号与同步 | 同步 |

图标源图：`subpage-icons.png`；生成脚本 `make_subpage_icons.py`（改完重跑会把 PNG 写回 `module/res/drawable-xxhdpi/`）。

想加更多条目：在 `SubPageIcons.KEY_ICONS` 里加 `{ "preference_key", "ic_sub_xxx" }`，或在生成脚本里加图标。

---

## 3. 底部搜索栏

- 原生顶部的 `header_view`（小米搜索条）被隐藏，仍保留为点击入口
- 底部浮层用**系统自己的** `miuix_appcompat_search_stub` 布局 inflate 出来，视觉和原生搜索条一致
- 点它 → `anchor.performClick()` → 走 MIUI 原生搜索 ActionMode
- 搜索界面出现时（`search_result_ll` 可见）底部条自动隐藏，退出搜索再出现
- 列表底部会按导航栏高度自动加 padding，最后一张卡片不会被挡住

不想用底部搜索：把 `Hook` 里两处 `BottomSearch.install(self)` 注释掉重编即可。

---

## 4. 安装

1. 安装 `dist/IosSettingsLayout-14.8-restored.apk`（LSPosed 模块列表里版本应为 **14.8 / versionCode 83**）
2. LSPosed 启用，作用域勾 **设置**（`com.android.settings`）
3. **必须**强制停止「设置」或重启手机，否则还在跑旧模块

底部搜索条的位置是：左右各 16dp、底部离导航栏 14dp、高 54dp。要改成贴底/更矮/悬浮球之类的，发一张目标图我调。

日志里应当能看到：

```
IosSettings: hooks installed
IosSettings: reordered N headers
IosSettings: bottom search installed
IosSettings: hooked PreferenceFragmentCompat (sub-page icons)
IosSettings: airplane switch attached to 控制中心 row
```

---

## 5. 原理

- **首页顺序**：`SettingsFragment.buildAdapter()` 返回后改 `mHeaders` 顺序，并给每条 Header 写 `groupId = 101 + 卡片号`。miuix 的 `CardGroupAdapter.getItemViewGroup()` 读的就是 `Header.groupId`，所以卡片圆角/间距仍由 miuix 原生 `CardItemDecoration` 绘制。
- **控制中心行开关**：hook `MiuiSettings$HeaderAdapter.onBindViewHolder`，在 `wireless_settings`（控制中心）这一行的 `@android:id/widget_frame` 里放一个 `miuix.slidingwidget.widget.SlidingSwitch`；开关只负责写飞行模式，行的点击仍是 MIUI 原来的 `onHeaderClick`。行被复用给别的 header 时会把开关移除。
- **二级图标**：`androidx.preference.PreferenceFragmentCompat` 是所有二级页的基类（链：`OtherPersonalSettings → DashboardFragment → SettingsPreferenceFragment → … → PreferenceFragmentCompat`）。preference 行布局 `miuix_preference_layout.xml` 里本来就有 `@android:id/icon`，只是平时为空。
- **底部搜索**：不接管搜索逻辑，只把原生搜索条的点击搬到屏幕底部。

---

## 6. 已知限制 / 可能翻车点（重点看）

| 现象 | 说明 |
| --- | --- |
| 底部搜索条没出现 | 看日志 `IosSettings: bottom search installed` 和紧随的 `bottom bar: vis=...`；把这两行发我 |
| 底部条挡住内容 | 我按 nav bar 加了 padding；如果仍挡，发截图我调 |
| 隔空播放没有出现 | 日志里看 `no header found for slot ids:` 是否含 interconnection；有 MiLink 服务时会自动补一条，没有就不补 |
| 控制中心行没有开关 | 日志里看 `airplane switch attached to 控制中心 row`；没有这行说明 `widget_frame` 没找到或 SlidingSwitch 创建失败 |
| 开关状态不同步 | 只在列表重新绑定/返回首页时刷新；如果飞行模式是在控制中心里改的，回到设置首页会更新 |
| 二级页面部分条目没图标 | 只加了我认识 key/标题的条目，把页面截图发我，我补映射 |
| 图标偏大/偏小 | 现在是 28dp（`Widget.PreferenceIcon` 是 wrap_content），改 `make_subpage_icons.py` 里的 `SIZE` 重生成即可 |
| 首页某条没出现在预期卡片 | 说明 id 不在映射表，日志里有 `id/title/group`，发我补 |
| 平板/分屏 | 没专门处理，异常的话发机型 |

---

## 7. 校验

```
dist/IosSettingsLayout-14.8-restored.apk
size   : 216,015 bytes
sha256 : 1A5B5BC3BD8F2D82893247183B112D34F1FBF040D71D97CDEFBBF257E0928C2D
签名   : CN=iOS Settings Layout, O=abradant, C=CN（v1+v2+v3，测试签名）
```
