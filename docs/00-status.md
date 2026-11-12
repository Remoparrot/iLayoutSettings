# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-12
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **17.0 / 100**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | **`com.Remo.ilayoutsettings`**（16.0 由 `com.abradant.iossettings` 改名，属于破坏性变更） |
| 应用名 / 仓库名 | **iLayoutSettings**（GitHub 仓库同步改名，旧地址会 301 跳转） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 当前包 | `dist/iLayoutSettings-17.0.apk`（302,626 B，SHA-256 `55A35F55…3E6F`；**已在 25053RT47C（OS4.0.2.0.XOLCNXM / Android 17）上覆盖安装并实测**） |
| 旧包 | `dist/` 只保留当前包；16.8 / 16.7 / 16.6 / 16.5 / 16.4 / 16.3 / 16.2 / 16.1 / 16.0 / 15.x / 14.8 在各自 GitHub Release |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `iLayoutSettings-17.0.apk`（**当前**） | 302,626 B | `55A35F55105CD489D7B972003D11FB1310A8241355DC9BB860297A13EB743E6F` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `iLayoutSettings-16.8.apk`（历史，Release） | 302,626 B | `6D543EABA02906A665787A4207FE72464A18E4EA895501C33D05028520B80F3A` | 同上 |
| `iLayoutSettings-16.7.apk`（历史，Release） | 298,530 B | `25E0FCA8A39256DFCEC726DCEEC50CB8B268427CDE27A0C18F61589E11CDD973` | 同上 |
| `iLayoutSettings-16.6.apk`（历史，Release） | 294,434 B | `F167D3820FEEAF83FED1B8C7FFDF8CD006BF9ADB38EFE4C24E4E9116076BE6FE` | 同上 |
| `iLayoutSettings-16.5.apk`（历史，Release） | 286,069 B | `8449352927AD053BD49FDDF3505AADE4532F90933E680CDF94AE72257854B566` | 同上 |
| `iLayoutSettings-16.4.apk`（历史，Release） | 286,069 B | `EE68C55D05E9567831C3DE3327544A3D9AB2EBAD426B907EEC9C17329368B3C0` | 同上 |
| `iLayoutSettings-16.3.apk`（历史，Release） | 236,593 B | `ED0C383E5A91DC4F054ED3C362AAF4AAE3F44506CE80F4078CD3C36439402873` | 同上 |
| `iLayoutSettings-16.2.apk`（历史，Release） | 236,593 B | `2D2F068C8DEC1A5D5B982F6347F71580F6C4800B8BE89F4286C2836E945AEC3B` | 同上 |
| `iLayoutSettings-16.1.apk`（历史，Release） | 224,305 B | `83BE73357A1AD29DCB57EC8BCF217127BEA7B7461A18DF70E0189EC83571F77F` | 同上 |
| `iLayoutSettings-16.0.apk`（历史，Release） | 220,209 B | `8DA082CD8FC578676EB5138F7150BDCFB40C4DBE45CD3625E8B0521923921A73` | 同上 |

> 16.0 起包名是 `com.Remo.ilayoutsettings`：旧包 `com.abradant.iossettings` 仍需先卸载；
> 16.0 起同签名可直接覆盖安装。见 [安装说明 · 16.0 迁移](04-install.md#160-迁移必须做)。
> 16.2 起 `build.ps1` 成功后自动删除 `dist/` 里除当前包外的 APK；历史包只从 Releases 取。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/Remoparrot/iLayoutSettings （旧 `iLyayot` 自动跳转） |
| 默认分支 | `main` |
| 最新 Release | [v17.0](https://github.com/Remoparrot/iLayoutSettings/releases/tag/v17.0)（附件 `iLayoutSettings-17.0.apk`，302,626 B，SHA-256 `55A35F55…3E6F`） |
| 自动发版 | `tools/release.ps1`：构建 → 校验 → **自动 add/commit/push 当前分支** → 打 tag → 建 Release → 上传附件；脚本必须是 UTF-8 **带 BOM**，且在发 POST 前自检正文（中文丢失 / 占位符残留即中止） |
| 旧 Release | v16.8、16.5、16.4、16.3、16.2、16.1、16.0、15.5、14.8 保留（本地 `dist/` 不再保留旧包） |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`），卡片仍由 miuix 绘制
- ✅ **HyperOS 4 适配（17.0）**：
  - 顶栏模糊/发丝线/透明度恢复：`ForceBlur` 的能力探测改三级（`SUPPORT_HYPER_MATERIAL` → `mIsSupport` → `isSupport()`），半径同时写 `mBlurEffect`（OS3）/`mBlurRadius`（OS4），强制 `hasMaterialPermission` 与 `applyBlur(true)`；`UiTweaks` 放开 `getScrollableView()` 父链裁剪，并（仅 OS4）把列表按「展开栏高 − 当前栏高」`translationY` 上移——内容是真正滑进栏下被模糊的；
  - 退出搜索不再出现两条搜索栏：`BottomSearch.hideAnchor()` 在每次布局/恢复时幂等隐藏 MIUI 原生顶部栏；
  - 卡片左右边距重新跟随主题 `theme_values.xml`：OS4 的 `updateCardGroupMarginForSmallScreen()` 用 `card_group_margin_horizontal`（12dp）覆盖 miuix 主题化边距，`CardMargins` hook 后写回 `miuix_recyclerview_card_group_margin_start/end`（实测 38px→53px，即 16.5dp）；
  - 搜索栏玻璃背景不再冻结：OS4 的列表在硬件层里，软件画布 `root.draw()` 每帧返回同一帧；`SearchGlass` 在 SDK≥37 改为**只绘制 `scroll_headers` 列表子树**作为背景（绕开冻结层、且不含玻璃自身；`PixelCopy` 会把玻璃拍进去、逐帧叠加发白），取不到列表才退回 `PixelCopy`（诊断计数从 `cap=101 chg=1` 变 `cap=562 chg=947`）；
  - 搜索栏麦克风图标恢复：OS4 的 stub 根是 `LinearLayout`，16.8 的 `addView(mic, FrameLayout.LayoutParams)` 抛异常被吞；现在挂到内层 `search_mode_stub` 并按宿主类型构造 LayoutParams。
- ✅ **控制中心上移（16.7）**：首页「控制中心」行移到「墙纸」上方（组 4 内调槽位，卡片不变）
- ✅ 首页「专注模式」「相机」「搜索」合成入口（15.4 / 15.5 起）
- ✅ **iCloud 入口（16.0，16.2 图标更新）**：固定在「Game Center」下方、同一张卡片；
  点击打开小米云服务 `com.miui.cloudservice/.ui.MiCloudMainActivity`；
  图标用模块内置的 `ic_cloud.png`（来源：用户提供的 `com.android.settings` 资源包），
  设置包同名 drawable 只作兜底
- ✅ **软件更新卡片（16.0，条件显示）**：读 `Settings.Global.miui_new_version`，
  非空才出现；标题「有软件更新可用」（16.6 起标题左移到 16dp 内边距），右侧红色「1」角标
  （`HeaderOverlay` 画在 `widget_frame` 里，24dp / 13sp 白粗体，与箭头垂直居中）；
  **16.6 起「通用」行的蓝色「1」也换成同一颗红角标，并补上和其它行一样的箭头**，
  点击打开 `com.android.updater/.MainActivity`
- ✅ **耳机卡片（16.0 起，16.1 增强）**：
  - `AudioManager.getDevices()` 检测 A2DP / SCO / BLE / 有线 / USB / 助听器，A2DP 优先；
  - 标题 = 设备别名（`BluetoothDevice.getAlias()`，取不到用 `getProductName()`）；
  - 图标用模块内置的 `ic_sub_airpods.png`（来源：用户提供的资源包，灰色 AirPods 圆角图标），
    设置包同名 drawable 只作兜底；
  - **点击进 MIUI 耳机详情页**（`miui.bluetooth.action.HEADSET_SETTINGS` →
    `com.android.settings.bluetooth.MiuiHeadsetActivity`，电量 / 改名 / 检查更新 / 功能开关），
    有线耳机等没有蓝牙设备时退回蓝牙设置；
  - **16.1：`HeadsetWatcher` 注册 `AudioDeviceCallback`，在设置首页打开期间连上/断开耳机，
    卡片即时出现/消失，不用强制停止设置**（350ms 防抖）
- ✅ **底部搜索栏 + iOS 27 液态玻璃（16.6 重做）**：不再叠 MIUI 的
  `miuix_appcompat_search_mode_edit_text_bg_light/dark` 9-patch，`SearchGlass` 自己画
  模糊 + 折射 + 半透明底色；轮廓是 Kyant Shapes 的 **G2 连续曲率胶囊**；光边只在**上下**
  且两侧等亮，无内阴影。搜索/麦克风图标换成指定 iOS 27 图标，随深浅色自动变色；
  点麦克风通过 `com.miui.voiceassist/.VoiceService`（与电源键长按同一条入口）唤醒小爱。
- ✅ **搜索栏进出场过渡（16.0 / 16.1 修时序）**：进出搜索 220ms 滑动 + 缩放 + 淡入淡出；
  退出时底部栏保持隐藏，等 MIUI 收起顶部搜索框（`ActionModeAnimationListener#onStop(open=false)`）
  后才出现，不再短暂同时显示两个搜索栏
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）
- ✅ **顶栏透明度（16.2）**：参数页「顶栏 · 透明度」0–100%；16.3 起默认 **2%**，
  16.5 起**任何滚动位置都用这个半透明底色**（不再在顶部恢复实色）
- ✅ **顶栏发丝线（16.2，16.3 改深色色值，16.4 修主题切换，16.5 跟随折叠）**：
  在 `ActionBarOverlayLayout` 的 overlay 上画 0.5dp 半透明线——浅色模式黑色 12%，深色模式
  **中性灰 30%（`0x4DAAAAAA`）**。16.4 起颜色每帧按当前 `uiMode` 取，设置界面内切深浅色后
  线不再消失；16.5 起显示条件改为「列表已滚动 **或** 栏已从大标题高度收缩」，
  折叠动画中栏开始压住内容时线就出现，回到顶部（栏恢复大标题高度）后隐藏。
- ✅ **顶栏始终模糊（16.2 修，16.3 改默认，16.4 修首发，16.5 改常开）**：`topbar_blur` 0–100%，
  默认 **5%**（约 7.5dp ≈ 22px），透明度默认 **2%**。直接改 `NestedHeaderLayout` 原生
  `mBlurEffect`，并给 `action_bar_container` 自己下发 `MiuiBlurUtils.setBackgroundBlur`
  （参数是像素，按 density 换算）。16.4 在 `refreshBlur()` 前预写半径，折叠栏第一次出现
  就带模糊；**16.5 起不再按「是否滚动」开关模糊和底色，并且清掉 MIUI 给滚动容器的
  `clipBounds`**——账户卡等内容滑进栏下就是模糊的，不再被不透明栏硬切。
  旧 110dp 读取时折算为 73%
- ✅ **二级页顶栏（16.7 新增，16.8 收尾，`SubPageTopBar`）**：二级页的 `action_bar_container` 与首页同一套参数——
  底色按 `topbar_alpha`、模糊按 `topbar_blur`（像素换算后走 `MiuiBlurUtils.setBackgroundBlur`）。
  二级页是普通列布局，需把 `ActionBarOverlayLayout` 切到 overlay 模式并给列表加**栏高 padding**，
  内容才滑得进栏下；发丝线复用首页的 `applyDivider`（宿主是栏父级的 `ViewOverlay`），
  显示条件 = 列表 `canScrollVertically(-1)` **且列表确实在栏下**，回顶部即消失。
  **16.8**：`findScrollable` 只认页面最外层的滚动容器（不再给「我的设备」内层 `description_grid` 加 padding）；
  加完 padding 后把第 0 行重新锚回 padding 顶部，进入二级页不再自带滚动偏移；
  「我的设备 / 通用」的 version-card 页会抬升列表 wrapper（`topMargin -= 栏高`）并在预绘制里清父级 clip，
  内容因此能滑进栏下；实测显示 / 无线局域网 / 通知 / 通用 / 关于手机 五类页在浅色下均生效，深色复验「关于手机」。
- ✅ **GUI（16.6 简化）**：删除「高级设置」总开关，搜索栏玻璃与顶栏两组滑杆直接显示；
  发丝线开关只控制是否显示/隐藏。搜索栏玻璃滑杆仍支持 0.1dp 步进
- ✅ **应用图标（16.4 换风格）**：蓝紫渐变 + 半透明玻璃盘 + 白齿轮，`tools/make_launcher_icon.py`
  生成 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 五档 PNG；另有 B（纸感线框）、C（深色滑杆）两套候选
- ✅ 二级页图标、卡片末行修正、应用内参数界面（搜索栏模糊支持 0.1dp）
- ✅ 无 Gradle 构建脚本（`module/build.ps1`，成功后自动清 `dist/` 旧包）
  + 一键发版脚本（`tools/release.ps1`，默认自动提交/推送/发 Release）

## 已知问题

1. **条件卡片依赖小米自己的状态**：
   - 更新卡的 `miui_new_version` 会被系统更新 App 自己清空/写入，手动 `settings put` 只用于测试；
   - 耳机卡只认「音频输出设备」：蓝牙耳机在 MTP/HFP 之外的连接阶段可能短暂识别不到，
     刷新会慢半拍（350ms 防抖 + 一次重建）。
2. **耳机详情页依赖 MIUI 私有 Activity**：`MiuiHeadsetActivity` 需要 `COME_FROM=MIUI_BLUETOOTH_SETTINGS`
   和 `android.bluetooth.device.extra.DEVICE`（或 `bluetoothaddress`）；ROM 升级改语义时要重新抓。
3. **图标优先级（16.2 起）**：耳机 / iCloud 以模块内置 PNG（用户提供的主题资源）为准，
   `com.android.settings` 同名 drawable 只在模块内置资源缺失时兜底；主题不能再覆盖这两张图。
4. **顶栏发丝线/模糊依赖栏高与 clip**：16.5 用 `OPEN_BAR_HEIGHT` 记录见过的最大栏高来判断
   「折叠中」；若某些主题下 action bar 高度本身就会随模式变化（如搜索模式），线可能在那些
   模式下也显示。清 `clipBounds` 依赖 `updateOverBgState()` 仍被调用；换 ROM 若改成别处设 clip，
   内容会重新被裁在栏底。线宿主仍是 `action_bar_container` 的父级（当前 `ActionBarOverlayLayout`），
   若父级也不画 overlay，线会不可见（实测 `ActionBarContainer` 自己的 overlay 不绘制）。
   深浅色线色 16.3 已实拍确认（浅色 `(222,222,223)`、深色 `(58,58,59)`），16.5 复测一致。
5. **顶栏模糊依赖 MIUI 私有 API**：`MiuiBlurUiHelper.setBlurParams` / `mBlurEffect` /
   `refreshBlur` 在 ROM 升级改名时 hook 会软失败，表现回到「模糊固定、不可调」。
6. **仅在默认主题 / 默认图标包上验证**；第三方主题替换 `ic_*` / 分割线颜色时需要再确认
   （16.3 起耳机/iCloud 用模块内置图标，不受主题覆盖）。
7. **图标是 PNG（非 adaptive icon）**：HyperOS 会自己套圆角遮罩；第三方图标包/主题不会替换它。
   要跟系统主题走，后续可加 `mipmap-anydpi-v26/ic_launcher.xml`。
8. `docs/07-changelog.md` 的旧「7. 校验」节数据过期（历史记录，未改）。
9. 无 CI：`tools/release.ps1` 已能自动提交/推送/发 Release，但 GitHub Actions 仍未接。
10. **搜索栏玻璃不再依赖 9-patch**：16.6 起 `SearchGlass` 是唯一表面，`visibleBackgroundBounds`
    改为「先存 drawable、首帧 `OnPreDraw` 量像素」；如果换 ROM 后 `android:id/inputArea`
    改名或 9-patch 结构变了，玻璃会退回整个 `inputArea`（高 44dp）而不是可见胶囊。
11. **小爱入口是 MIUI 私有导出服务**：`com.miui.voiceassist/.VoiceService` +
    `com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST`，冷启动约 4–5s 才出现
    `voice_assist_root`；ROM 升级若改 action/加权限，`wakeXiaoAi` 会走 `ACTION_ASSIST` 兜底
    （可能弹 Resolver）。
12. **光边在浅色纯白页上几乎不可见**：白色加法高光叠在浅灰胶囊上，白底环境没有对比；深色
    模式实测上下峰值 `#63`/`#62`（等亮），浅色只在彩色内容上明显。**16.7 试过「灰色描边 + 内移白高光」，
    真机是灰环 + 白环两条线、偏重，按反馈已整段回退 16.6**；这个问题仍是已知问题，不要再自动加灰边。
13. **G2 轮廓只对宽 ≥ 2×高 的胶囊生效**；其他比例退回普通圆角矩形（`buildShape` 分支）。
14. **二级页顶栏依赖 MIUI 的 `ActionBarOverlayLayout` overlay 模式**：`SubPageTopBar` 用
    `setOverlayMode(true)` + 列表 padding 让内容滑进栏下；若 ROM 改名/去掉该模式，或二级页不是
    `SettingsActivity` / `SettingsBaseActivity` 子类，表现退回「栏有半透明底色，但内容不会滑到栏下、
    也不会出现发丝线」。线宿主仍是栏父级 overlay，宿主不绘制时线不可见（同第 4 条）。
15. **「我的设备 / 通用」的栏下内容依赖 `miui_version_card_view` + wrapper 位置**：16.8 靠
    「wrapper.top == 栏底」识别这类页，做 `topMargin -= 栏高` 抬升并在预绘制里清父级
    `clipChildren`/`clipBounds`；ROM 改 id/层级、MIUI 换掉 version-card 容器，或该页不再把内容
    放在栏下方时，抬升会软失败——表现退回 16.7（栏半透明，但内容仍被裁在栏底、没有模糊）。
    另外 MIUI 会在数据落地后二次 inflate，所以加了 700/1600/3200ms 的三次延迟 refresh 兜底。
16. **二级页顶栏的 tint 是同一只 `ColorDrawable`**（16.8）：`MiuiBlurUtils.setBackgroundBlur`
    依赖 view 背景层，若某主题给 `action_bar_container` 换了自带模糊的 drawable，会被模块的
    纯色 drawable 覆盖，模糊只剩 `setBackgroundBlur` 这一路。
17. **OS4 首页「通用」仍排在「电池 / VPN」之后**：这是 16.8 的分组（网络卡 6 行），
    17.0 曾把电池/VPN 移到隐私卡让「通用」紧跟「个人热点」，**用户明确否决「不要改布局」后整段回退**。
    要再动必须先给用户看两种布局的对比图。
18. **OS4 的列表上移依赖记录的「展开栏高」**（`OPEN_BAR_HEIGHT`，实测 506px）：某主题若把展开态
    栏高改得和折叠态接近，`shift` 偏小，栏下会重新出现留白。上移只在 SDK≥37 生效，OS3 不走这段。
19. **搜索栏玻璃模糊默认只有 1.0dp**（`DEFAULT_GLASS_BLUR`，用户要求保持不动）：浅色页面上基本
    看不出「糊」，看起来更像半透明色块；要更明显需在 GUI 里调大「搜索栏玻璃 · 模糊」
    （实测 15.6dp 时黑图标会被糊成色团）。
20. **OS4 的 `PixelCopy` 偶发 `pixelcopy:3`（`ERROR_SOURCE_INVALID`）**：回调里已忽略并保留上一帧，
    表现为那一帧玻璃背景不刷新；连续滚动时暂未观察到可见抖动。

## 下一步（优先级从高到低）

1. **OS3 真机回归 17.0**：本机已升 OS4，17.0 的 OS3 分支（软件捕获、miuix 默认边距、无 translationY）
   **未上机验证**，是当前最大的发布风险；
2. 观察 17.0 的 `PixelCopy` 在长滚动/搜索切换时的功耗与 `pixelcopy:3` 频率；
   必要时降到 30fps 或复用 bitmap；
3. 观察 16.6 搜索栏在**第三方主题/彩色壁纸/浅色纯白页**下的光边与折射。16.7 已试过给浅色加
   1px 灰色轮廓 + 内移白高光，实测偏重（灰环 + 白环），按反馈回退——**要再动先让用户看对比图**；
4. 观察小爱入口在 ROM 升级后的可用性；必要时把 `VoiceService` 的 `ComponentName` 做成
   可配置，或改走 `ACTION_ASSIST`；
5. 观察 16.5 的「常开模糊」在**搜索模式**、第三方主题、平板/横屏下的表现（栏高变化会不会
   让线提前出现）；必要时把发丝线的显示条件收紧到「折叠进度 > 阈值」；
6. 16.8 的延迟 refresh 目前是 700/1600/3200ms 三次；若「我的设备」在慢设备上仍偶发丢
   padding，改成 hook fragment `onCreateView` 重挂更稳（代价是多一个类钩子）；
7. 图标如需跟随系统主题，补 `mipmap-anydpi-v26` adaptive icon（前景 = 齿轮+玻璃盘，背景 = 渐变）；
8. 接 GitHub Actions：tag 触发 `tools/release.ps1` 的只读版本（构建 + 上传）；
9. 清理 `Hook` 未引用的遗留类（`GlassToggle`、`IntroCard`、`AboutIos` 等）；
10. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 21.0.2（`D:\Android\jdk21\jdk-21.0.2`，16.4 构建实测；17 亦可） |
| build-tools | 37.0.0（`D:\Android\Sdk\build-tools\37.0.0`，aapt2 / d8 / zipalign / apksigner 实测可用） |
| android.jar | API 37（`D:\Android\Sdk\platforms\android-37.0\android.jar`，构建显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 4（OS4.0.2.0.XOLCNXM） / Android 17，adb `D:\platform-tools\adb.exe` |
| 调试产物目录 | 手机上统一放 `/sdcard/Download/iLayoutSettings/`（不要散落在存储根目录；测试截图拉回后即删） |
