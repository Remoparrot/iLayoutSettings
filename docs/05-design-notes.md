# 设计思路与取舍

> 状态：**现行** · 对应版本：15.2 (87)
> 这份文档回答「为什么这样写」，不回答「怎么改」。改代码前先看这里，能避免把已经踩过的坑重新踩一遍。

## 1. 四条原则

| 原则 | 含义 | 反例（不要做） |
| --- | --- | --- |
| **不重绘** | 只重排、注入、显隐；视觉由 miuix 原生控件产出 | 自己画卡片/列表/开关 |
| **软失败** | 每个 hook 包 `try/catch`，找不到类就只打日志 | 让异常冒到设置进程 |
| **可观察** | 关键路径都有 `IosSettings:` 日志，出问题看日志就能定位 | 静默 return |
| **最小作用域** | 只 hook `com.android.settings`，不碰系统其它进程 | 全局 hook `TextView#setText` 之外的通用类 |

## 2. 关键决策

### 2.1 首页：改 `groupId` 而不是自绘卡片

- **方案 A（选中）**：重排 `mHeaders` + 给每条 header 写 `groupId = 101 + group`，让 `CardGroupAdapter` 自己画卡片；
- **方案 B（否决）**：自定义 RecyclerView + 自绘卡片。

选 A 的原因：卡片圆角、组间距、按压态、深色模式、无障碍全部白嫖；而且 MIUI 改版时只要 header id 还在，布局就不会崩。B 需要复刻整套 miuix 视觉，收益为零、风险极高。

**代价**：必须绕开 MIUI 自己在每行叠的 9-patch（见 2.2）。

### 2.2 末行双层圆角：绑定时把主题 drawable 换掉

MIUI 在 miuix 卡片装饰之上又给每行叠了一层「卡片形状」的 9-patch 背景/前景。最后一行就会露出两层圆角，图标也会被这块背景撑大。

- **方案 A（选中）**：在该行 `onBindViewHolder` 期间，把四个 drawable 资源临时替换为透明图，bind 完恢复；
- **方案 B（否决）**：改行布局/裁剪父容器——会破坏按压涟漪与 ripple 边界；
- **方案 C（否决）**：给最后一行 `setBackground(null)`——只管背景，管不到 `miuix_recyclerview_card_item_press_fg_*` 前景。

实现上首页走 `HeaderAdapter` + `ResourcesImpl#loadDrawable`（`CardFix`），二级页走 `Preference#onBindViewHolder`（`SubPageCards`）。

### 2.3 底部搜索：搬原生 stub，不接管搜索

- **方案 A（选中）**：隐藏原生 `header_view` 但保留为点击目标，inflate 一个 `miuix_appcompat_search_stub` 放到底部，点击转发 `performClick()`；
- **方案 B（否决）**：自建搜索框 + 自己启动搜索 Activity——会丢掉 MIUI 的搜索历史、联想、`ActionMode` 动画。

这样做的副作用是「底部栏和真搜索框必须同时存在」，于是有了 2.4。

### 2.4 玻璃：抓图 + 模糊 + Kyant 折射，并且要「抓不到自己」

`SearchGlass` 每帧把窗口内容画进一张小 bitmap → `RenderEffect` **1dp** 模糊 → `RuntimeShader` 圆角折射
（**24dp 厚度 / 30dp 强度**）→ shader 内再叠 45° 边缘高光与底部内阴影 → 裁到搜索栏胶囊。
胶囊遮罩不是视图边界，而是把原生背景 drawable 画成位图后取的实际像素矩形（主题 9-patch 的透明边距
`getPadding()` 拿不到）；量完再把原生 fill 降到 `0x80` 作为染色层。

三个必须处理的点：

1. **不能把搜索栏自己拍进背景**：抓图那一帧让 `SearchHost` 整棵子树跳过绘制（`capturing` 标志），否则玻璃里会出现搜索栏的重影；
2. **不能每帧改可见性**：`setVisibility` 会触发 layout，直接限制抓图帧率。所以用「跳过绘制」而不是「隐藏」；
3. **shader 失败必须降级**：全量 shader → 只有折射 → 只有模糊 → 半透明胶囊，且失败标记只置一次，不能每帧重试抛异常。

**为什么把高光/内阴影写进同一个 shader**：16.8 用三个叠加层（折射 / 描边 / 内阴影），每层各裁一次胶囊，
边缘抗锯齿各算各的，容易出现 1px 亮边；而且同一套 SDF 要写三遍。折进 shader 后只有一个输出，
折射位移、rim 宽度、内阴影带都由同一个 SDF 决定，天然对齐，也少两次 RenderNode 合成。

**代价**：抓图 + 模糊 + 折射是整份代码里最贵的一步，低端机/高刷屏要控制频率（见 2.5）。

### 2.5 刷新率：跟随 Choreographer，而不是只在滚动时刷新

早期版本只在滚动/失效时重抓，玻璃看起来是「定住」的。后来加了 `Choreographer.postFrameCallback` 每帧失效重抓，代价是功耗。

折中策略：**默认跟随屏幕刷新，抓图尺寸尽量小，抓图期间跳过自身子树**；真机上如果发热明显，优先降抓图分辨率而不是降刷新率（后者肉眼可见地卡）。

### 2.6 不搬 smali，但可以按源码重写

15.x 的实验分支曾把 lattice 的 `LiquidGlassView` 反编译成 smali 再汇编进 dex。结论是：**不要在主线上这么干**。

- 体积、可读性、可维护性全面变差；
- 依赖桩类（`GlassBackdrop`、`Diagnostics`…）需要一起搬，缺一个就 `NoClassDefFoundError`；
- 原实现假设的宿主环境与本模块不同，出现过文字重影、图标缺失。

14.9 的液态玻璃改走另一条路：**读 Kyant 的 Kotlin 源码，用 Java + AGSL 按同一套数学重写**（`SearchGlass`），
逻辑可控、失败可回退；AGSL 用 Skia 的编译器离线验一遍（`javac` 不校验 shader）。相关历史见 [版本记录](07-changelog.md) 的 v16.x、v14.9 段。

## 3. 性能

| 热点 | 做法 |
| --- | --- |
| 抓图 | 小尺寸 bitmap；抓图帧跳过自身子树；失败直接画不模糊的底图兜底 |
| RenderEffect | shader 编译失败只回退一次，不要每帧抛异常 |
| 列表重排 | 只在 `buildAdapter` / `onResume` 触发；顺序没变时只刷新装饰，不重建 adapter |
| 图标注入 | 只在 `PreferenceFragmentCompat` 的三个生命周期点跑，遍历已有 preference 树，不新增 View 层级 |

## 4. 可维护性

- **版本线分离**：主线能力以 `00-status.md` 为准；15.x–17.x 的 GUI 参数页、smali 搬运等实验仍不并入，
  14.9 只把「液态玻璃」这一项按源码重写后回迁，避免「发布包和源码对不上」；
- **单一版本号来源**：`AndroidManifest.xml`，构建脚本从里面读版本名，产物名自动跟随；
- **日志即契约**：`IosSettings:` 前缀的日志行是排错入口，改代码时不要顺手删；
- **文档强制**：每次会话写记录（[agent.md](../agent.md)），决策写进本文档，比写在 commit message 里更容易被找到。

## 5. 已知的设计债

1. 源码里残留后续版本的类（`GlassToggle`、`IntroCard`、`AboutIos`…），未挂载但会进 dex，增大体积、干扰阅读；
2. `BottomSearch` 里还留着指向 lattice 的反射分支，属于「回退不彻底」；
3. 平板/分屏没有专门适配，宽度断点未处理；
4. 没有自动化测试，回归靠真机清单（[安装 · 检查清单](04-install.md#4-首次使用检查清单)）。
