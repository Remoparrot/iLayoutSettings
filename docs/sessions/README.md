# 会话记录

> 状态：**现行**
> 这里是项目真正的「记忆」。每次对话/AI 会话结束前必须新增一份记录，并在下表登记。

## 为什么要有这一层

`07-changelog.md` 只告诉你「哪个版本改了什么」，不告诉你**为什么**、**试过什么失败的方案**、**当时卡在哪**。
这一层记录的就是这些：让下一次会话不用重新推理、不用重复踩坑。

规则见 [agent.md](../../agent.md)。

## 索引

| 日期 | 标题 | 一句话结论 | 状态 |
| --- | --- | --- | --- |
| 2026-11-12 | [HyperOS 4 适配：顶栏模糊 / 双搜索栏 / 卡片边距 / 玻璃冻结](2026-11-12-hyperos4-17.0.md) | 17.0：OS4 删字段（`SUPPORT_HYPER_MATERIAL`/`mBlurEffect`/`mScrollableView`）导致顶栏 blur 整套 hook 没装、列表进不了栏下；改 `isSupport()/mBlurRadius/getScrollableView()` + `hasMaterialPermission` 强制 + 列表 translationY 补栏高差；退出搜索后重复隐藏原生顶部栏；`updateCardGroupMarginForSmallScreen` 覆盖主题 16.5dp 边距 → 重新写回；OS4 软件画布捕获冻结 → SDK37+ 改 PixelCopy。电池/VPN 重分组按用户否决已回退 | 已完成（OS3 未上机） |
| 2026-11-11 | [二级页顶栏收尾：进入偏移 / 我的设备留白 / 「通用」共用模糊](2026-11-11-subpage-topbar-fixes.md) | 16.8：二级页加完 padding 立刻把第 0 行锚回顶部（进入不再自带滚动偏移）；`findScrollable` 只认最外层竖向滚动容器（修「我的设备」331px 卡片留白）；「我的设备 / 通用」抬 wrapper + 预绘制清 clip，内容滑进栏下共用同一套模糊 | 已完成 |
| 2026-11-11 | [二级页顶栏（模糊/半透明/发丝线）+ 控制中心上移 + 浅色搜索栏高光回退](2026-11-11-subpage-topbar-and-search-rollback.md) | 16.7：「控制中心」移到「墙纸」上方；二级页顶栏改成首页同款半透明 + 原生模糊，靠 `ActionBarOverlayLayout.setOverlayMode` + 列表栏高 padding 让内容滑进栏下；发丝线复用 `applyDivider`，条件 `canScrollVertically(-1)`；浅色搜索栏的灰边试验按用户要求整段回退 16.6 | 已完成 |
| 2026-11-10 | [红 1 角标统一 + 补箭头 + 更新卡标题左移](2026-11-10-update-badge-unify.md) | 16.6：「通用」行的蓝 1 换成与更新卡同一颗 24dp 红角标；带角标的行补上系统自带箭头（克隆 `arrow_right` drawable）；角标在 `widget_frame`（竖直 LinearLayout + 空占位）里本来贴底，改由 `OnPreDraw` 隐藏同帧其它子 View 才与箭头齐平；更新卡标题左移到 16dp 内边距 | 已完成 |
| 2026-11-10 | [搜索栏 iOS 27 液态玻璃 + 小爱麦克风](2026-11-10-search-glass-ios27-rim-mic.md) | 16.6：搜索栏不再叠 MIUI 9-patch，改为自绘 Kyant 液态玻璃 + G2 连续圆角；光边只在上下且等亮、无内阴影；换成指定 iOS 27 图标（随深浅色、缩小内收）；点麦克风走 `VoiceService` 唤醒小爱；删除高级设置总开关 | 已完成 |
| 2026-11-10 | [顶栏始终模糊 + 发丝线跟随栏出现](2026-11-10-topbar-always-blur.md) | 16.5：模糊半径与透明底色不再按「是否滚动」开关，折叠中账户卡滑进栏下就是模糊的；`setBackgroundBlur` 改传像素；清掉 MIUI 给滚动容器的 `clipBounds`；发丝线在「列表滚动或栏已收缩」时出现；只在首页 header `isShown()` 时动共享 action bar | 已完成 |
| 2026-11-10 | [顶栏发丝线跟随深浅色 + 高级设置开关 + 新图标](2026-11-10-topbar-hairline-advanced-icon.md) | 16.4：顶栏控制器改挂 `NestedHeaderLayout` 类钩子，设置内切深浅色后发丝线不再消失；首次折叠前预写模糊半径；GUI 高级设置总开关（只在开启后出现顶栏/发丝线选项）；蓝紫玻璃盘新图标 | 已完成 |
| 2026-11-10 | [顶栏近乎全透明 + 深色灰色发丝线](2026-11-10-topbar-clear-and-dark-divider.md) | 16.3：顶栏默认改 5% 模糊 / 2% 透明度（近乎全透明）；深色发丝线由白 15% 改为中性灰 30%（`0x4DAAAAAA`，实拍 `(58,58,59)`）；`pm clear` 复验默认值 | 已完成 |
| 2026-11-10 | [指定图标 + 顶栏透明度/发丝线 + 自动清理](2026-11-10-icons-topbar-opacity-and-release-cleanup.md) | 16.2：耳机/iCloud 改用指定主题图标；顶栏透明度与「滚动后才出现」的发丝线；顶栏模糊改为 0–100%（原生 `NestedHeaderLayout` 半径）并真正可调；搜索栏折射默认 19/12、模糊 0.1dp；构建自动清 `dist/` 旧包，release 自动提交/推送发版 | 已完成 |
| 2026-11-10 | [耳机卡即时刷新 / 详情页 / 搜索退出时序](2026-11-10-headset-card-live-and-search-exit.md) | 16.1：耳机连/断即时刷新卡片（AudioDeviceCallback）、点击进 MIUI 耳机详情页（MiuiHeadsetActivity）、退出搜索不再短暂两个搜索栏（等 ActionMode 收起动画结束）、修二级页返回底部栏消失 | 已完成 |
| 2026-11-10 | [改名 iLayoutSettings + 条件卡片 + 搜索栏过渡](2026-11-10-rename-and-conditional-cards.md) | 16.0：应用/仓库改名 iLayoutSettings、包名 `com.Remo.ilayoutsettings`；新增 iCloud 行、条件式「有软件更新可用」卡（红色 1 角标）与耳机卡；搜索栏进出场加 220ms 过渡；新增 `tools/release.ps1` 自动发版 | 已完成（正式包待手动安装验证） |
| 2026-11-10 | [Releases 同步：发布 v15.5](2026-11-10-release-v15.5.md) | 补 tag `v15.5`（→ `fc25b08`）并在 Releases 挂上与 `dist/` 同一份的 APK；14.9~15.4 未补发，14.8 留档；文档里指向 v14.8 的说法改到 v15.5 | 已完成 |
| 2026-11-10 | [首页「相机」「搜索」入口](2026-11-10-camera-and-search-entries.md) | 「相机」锚在「外观」下方、「搜索」锚在「墙纸」下方；显式组件分别打开相机设置/搜索设置，图标复用设置 `ic_settings_camera` / `ic_search_24dp` | 已完成 |
| 2026-11-10 | [首页「专注模式」入口](2026-11-10-focus-mode-entry.md) | 「屏幕时间」上方合成一行专注模式，点击走 `ZEN_MODE_SETTINGS` 打开勿扰页；图标复用设置 `ic_do_not_disturb_mode_settings`，主题/深浅色自动跟随 | 已完成 |
| 2026-11-10 | [主题飞行图标 + 搜索栏去色 + 顶栏模糊实时生效](2026-11-10-theme-icon-clear-glass-topbar.md) | 飞行图标改用 `ic_airplane_mode_settings`；搜索栏只去 shader 表面色、保留主题胶囊；顶栏模糊修 classloader 并实时刷新 | 已完成 |
| 2026-11-10 | [参数界面 + 实心玻璃 + 原生开关归位](2026-11-10-gui-and-solid-glass.md) | 开关改为平移 `widget_frame`（HyperiOS 缓存的画随父移动）；玻璃去光边、折射 12/8、混 93% 表面色做实心；新增 GUI 与 `PrefsProvider` | 已完成（顶栏滑杆截图待补） |
| 2026-11-09 | [搜索栏玻璃对齐与 v15.0](2026-11-09-search-glass-alignment-and-v15.md) | HyperiOS 接管 `SlidingSwitch.onDraw` 导致开关下沉，改用自有 `GlassToggle`；玻璃遮罩改为渲染背景取像素；15.0 应用名 iLyayot | 部分完成（收尾参数仅日志验证） |
| 2026-11-09 | [搜索栏玻璃「定住」修复与几何对齐](2026-11-09-search-glass-freeze-and-geometry.md) | 玻璃只画一次就定住：改成「抓图比对、变了才重绘」；胶囊对齐 44dp `widget_frame` 药丸；模糊默认 5dp | 已完成 |
| 2026-11-09 | [搜索栏接入 Kyant 液态玻璃](2026-11-09-search-bar-liquid-glass.md) | 搜索栏改为「模糊 + 圆角折射 + 边缘高光 + 内阴影」；shader 用 Skia 离线编译校验，14.9 预览包未上机 | 已完成 |
| 2026-11-09 | [仓库初始化与文档体系](2026-11-09-repo-bootstrap.md) | 建立 iLyayot 仓库、文档地图、agent.md 强制记录规范；发现源码与发布包不一致 | 已完成 |

> 新记录追加到表格**最上面一行**（最新在上），并保持日期 `YYYY-MM-DD` 格式。

## 怎么写

1. 复制 [`_template.md`](_template.md) 为 `YYYY-MM-DD-<slug>.md`；
2. 按模板 7 节填写，不删小节（没有内容就写「无」）；
3. 在本文索引表登记；
4. 同步更新 [`../00-status.md`](../00-status.md) 与受影响的专题文档；
5. 提交信息：`docs(sessions): <本次主题>`。

## 记录里必须出现的三类信息

| 类型 | 例子 |
| --- | --- |
| **决策** | 「选择改 groupId 而不是自绘卡片，因为…」 |
| **证据** | 命令、日志行、APK 大小/sha256、截图路径 |
| **反面** | 「试过 X，失败，因为 Y」——比成功路径更值钱 |

## 不要做的事

- 不要写「优化了一下」「修了个 bug」这种无法复现的记录；
- 不要覆盖旧文件，勘误用追加；
- 不要把会话记录写成 changelog 的复制品——changelog 面向用户，会话记录面向开发者。
