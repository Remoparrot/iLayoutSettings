# 踩坑记录

> 状态：**现行** · 对应版本：14.9 (84)
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
| 外观页预览和卡片之间有空隙 | MIUI 自己加了 margin | 保留原间距，只在预览后补一层同色同圆角的卡片背景（`AppearanceTweak`） |

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

### E. 发布与签名

| 现象 | 根因 | 解法 |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 新旧包证书不同 | 卸载旧包再装；发布时固定 keystore |
| 每次构建签名都变（已修复） | 旧版默认 key 放在 `build/` 里，而 `build/` 每次构建都被清空 | 现版本默认 keystore 在 `.keystore/`，构建不再清理；正式发版仍应用 `-KeyStore` 固定仓库外的密钥 |
| `versionName` 与产物名不一致 | 手改产物名 | 产物名由 manifest 版本号生成，不要手改 |

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
- **解法**：本机 `python -m pip install skia-python`，用 `skia.RuntimeEffect.MakeForShader(源码)`
  编译，并核对 `effect.uniforms()` 的名字/顺序和 Java 侧 `setFloatUniform` 一致。
- **如何避免**：把这一步当成 shader 改动的固定回归；它能在打包前抓到语法和类型错误。

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

---

## 已过时的结论（保留备查）

- ~~「搜索栏玻璃必须用 kyant AGSL，否则不像」~~ —— 15.x 的结论，曾被 14.8 的「抓图 + 10dp 模糊 + 浅/深色叠层」取代。
  **2026-11-09 用户明确要求恢复**，14.9 用 Kyant 的折射 + 高光 + 内阴影重写；这里保留旧结论只为记录取舍变化。
- ~~「回退到 14.8 只要删掉 lattice 的类」~~ —— 见坑 C1，还要清反射分支与依赖桩类。
