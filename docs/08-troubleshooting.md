# 排错手册

> 状态：**现行** · 对应版本：16.6 (97)
> 用法：先按「现象」定位，再看「下一步」。每个问题都给了判断依据，不要凭感觉重装。

## 0. 通用排查顺序

```
1. 模块启用了吗？作用域只有 com.android.settings 吗？
2. 强制停止设置了吗？          adb shell am force-stop com.android.settings
3. 日志里有没有 hooks installed？  adb logcat -s iLayoutSettings:V
4. 哪一行日志缺失？→ 对照 docs/04-install.md 的日志表
5. 还不行 → 抓日志 + 截图 + 机型版本，按 04-install 第 7 节反馈
```

## 1. 完全没效果

| 判断依据 | 结论 | 处理 |
| --- | --- | --- |
| 日志无任何 `iLayoutSettings` | 模块没进进程 | 检查 LSPosed 启用状态、作用域、是否重启过设置 |
| 有 `hooks installed` 但界面没变 | hook 跑在别的进程/页面 | 确认打开的是系统「设置」而不是第三方设置页 |
| 有 `hooks installed` 但无 `reordered` | `SettingsFragment#buildAdapter` 没匹配上 | ROM 版本差异，抓日志反馈 |
| 装了两个同名模块 | 旧模块抢先生效 | 卸载旧包，只留一个 |

## 2. 首页顺序不对 / 某些入口不见了

- 看日志 `no header found for slot ids: xxx`：说明这台机器上没有这个 entry，**属正常**，不是 bug；
- 看日志 `reordered N headers`：N 是否合理（一般 20+）；
- 某些入口被归到「其他」卡片：说明 id 与标题都没匹配上，需要补 `HeaderData.SLOT_IDS` 或 `SLOT_TITLES`；
- 顺序对但卡片不对：`groupId` 没写进去，见 [坑 2](06-pitfalls.md#坑-2卡片分组失效groupid-没写进去)。

## 3. 底部搜索栏

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 完全没有 | stub inflate 失败，且 fallback 也没显示 | 日志找 `bottom search installed` / `fallback pill` |
| 位置太低/被手势条挡 | 导航栏 inset 没拿到 | 日志看 `bottom bar: ... rootH=...`；确认 `bottomMargin = 14dp + nav` |
| 挡住最后一张卡片 | 列表 padding 没加上 | 同上，检查 `listView.setPadding` 分支 |
| 点它没反应 | anchor 为空（未进搜索态） | 日志看 `bottom bar: vis=...`；确认 `mAnchorView` 已赋值 |
| 搜索时还显示 | ActionMode hook 没生效 | 日志看 `onCreateActionMode` 相关行 |
| 进出搜索时出现两个搜索栏 | 旧胶囊没被复用（16.0 之前） | 16.0 的 `install()` 在同一个 fragment / 同一夜间模式下会复用胶囊；仍有则看日志是否反复 `bottom search installed` |
| 退出搜索时顶部搜索框和底部栏短暂同屏 | 底部栏在 MIUI 收起动画开始时就进场（16.1 之前） | 16.1 等 `action mode onStop opening=false` 后才进场；日志里没有这行则说明监听没挂上（`search exit listener failed`） |
| 从二级页返回后底部栏消失 | `isSearching()` 被 `search_result_ll` 误判（16.1 之前） | 16.1 读 `mIsInActionMode`；仍消失则看 `bottom bar: vis=` 与 `mIsInActionMode` 字段是否存在 |
| 顶栏模糊滑杆拖了没变化 | 16.1 只改了 `ActionBarContainer` 背景，真正可见的是 `NestedHeaderLayout` 的原生模糊 | 16.2+ 应出现 `hooked MiuiBlurUiHelper (header radius + refreshBlur)`；没有则看 `top bar hooks installed` 与 `forced MIUI material blur on` |
| 顶栏发丝线在顶部就出现 | `isHeaderOpen()` 判断失败（返回 null / ROM 改语义） | 16.2 代码在取不到时回退到「滚动即显示」；看日志是否有 `top bar divider failed` |
| 设置内切深浅色后发丝线消失 | 16.3 把滚动监听和线色都绑在 fragment 视图上，切主题 recreate 后监听失联 | 16.4 改挂 `NestedHeaderLayout` 类钩子、每帧按当前 `uiMode` 取色；切主题后滚动仍应有线，没有则看 `top bar hooks installed` 是否出现在新进程 |
| 深色模式顶栏看不到线 | 16.2 的白 15% 线在近乎全透明的黑栏上只剩 `#262626`，接近黑色 | 16.3 深色改中性灰 30%（`0x4DAAAAAA`）；截图取栏底一行像素，应为 `(58,58,59)` 左右 |
| **折叠时账户卡被栏切掉、没有模糊**（16.5 之后） | 栏的模糊/底色没常开，或 MIUI 给滚动容器设了 `clipBounds` 把内容裁在栏底 | 16.5 不再按 `scrolled` 开关模糊；确认 `unclip()` 在 `updateOverBgState` 之后执行（ROM 若换地方设 clip 会复发）。滑杆为 0% 时本来就没有模糊 |
| 顶栏模糊比预期弱很多 | `setBackgroundBlur` 的半径单位是 px，传 dp 会弱 2.75 倍 | 16.5 起按 `density` 换算；仍弱则看 `MiuiBlurUtils` 是否返回 false（日志 `static miuix.core.util.MiuiBlurUtils.setBackgroundBlur: class not found` 之类） |
| 耳机 / iCloud 图标不是指定图 | 旧包缓存或主题覆盖 | 16.2 日志应为 `card icon … <- module (supplied theme)`；若是 `<- settings`，说明模块资源没打进 APK |
| 过渡动画期间玻璃有拖影 | 玻璃抓图层跟着移动 | 16.0 动画期间会隐藏 `SearchGlass`；如果还看到拖影说明动画没走 `animateBar` |
| 玻璃里有重影 | `capturing` 没跳过自身 | 见 [坑 4](06-pitfalls.md#坑-4底部搜索栏把自己拍进玻璃里) |
| **玻璃比原胶囊高、图标偏**（16.6） | 首帧没量到 9-patch，遮罩退回整个 `inputArea` | 日志应有 `search glass pill: [x,y][x,y]`；若为 `none`，检查 `OnPreDraw` 是否执行、`pill` 是否拿到 `android:id/inputArea` |
| **搜索图标过大/把文字挤走**（16.6） | ImageView 是 `wrap_content`，新 drawable 撑大布局 | 见 [坑 F12](06-pitfalls.md#坑-f12给-wrap_content-的-imageview-换大-drawable-会把布局撑开) |
| **上下光边看起来不等亮**（16.6） | 底色渐变导致下半边对比更强 | 16.6 已把底色改成统一 alpha、光边上下同权；若仍不等，看是否用了旧包（`rimAlpha` 旧值） |
| **点麦克风没反应**（16.6） | 小爱服务冷启动 4–5s；或 ROM 改了 `VoiceService` | 等 5 秒看 `mCurrentFocus=voice_assist_root`；日志应有 `xiaoai: VoiceService start requested`；失败会走 `ACTION_ASSIST` 兜底（可能弹 Resolver），见 [坑 F14](06-pitfalls.md#坑-f14voiceservice-冷启动-45-秒像没点中) |
| **麦克风/搜索图标出现切边** | 用户素材本身按 bbox 紧裁，没有留白 | 见 [坑 F15](06-pitfalls.md#坑-f15用户提供的图标是紧裁的边缘被切)；换图时直接替换 `ic_*_glass.png` 资源名 |

## 3.1 条件卡片（16.0 起，16.1 增强）

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 一直没有「有软件更新可用」 | 系统更新没发现新版本（`miui_new_version` 为空） | 正常；日志 `update card: no new version, skip` |
| 有更新但卡片不出现 | 更新卡在 resume 时解析失败 / 更新 App 刚清空键值 | 重进设置；日志看 `update card added:` |
| 更新卡有但没红色角标 | `HeaderOverlay` 没 hook 上 | 日志看 `hooked HeaderAdapter.onBindViewHolder (badge / airpods icon)` |
| 连着耳机但没耳机卡 | 输出设备是 `TYPE_BLE_SPEAKER`（不是耳机）/ 耳机只做输入 | 日志看 `airpods card:` 那行；必要时补 `isHeadsetType` |
| 耳机卡名称不对 | 蓝牙别名为空，退回了产品名 | 正常兜底；可在蓝牙设置里重新命名设备 |
| 连上/断开耳机后卡片不刷新 | `HeadsetWatcher` 没 attach（页面没 resume）/ 回调被注销 | 日志看 `headset watcher attached` 与 `headset devices changed, refresh cards`；没有则看 `onPause` 后是否回到首页 |
| 点耳机卡进的是蓝牙列表 | 详情页 intent 没解析到（`MiuiHeadsetActivity` 缺失 / 没有蓝牙设备） | 日志看 `headset card -> headset detail <MAC>` 还是 `-> bluetooth settings`；有线耳机属正常兜底 |
| 详情页打不开或是空白 | MAC 是 `00:00:00:00:00:00` / 设备未配对 | 日志看 intent 那行的地址；A2DP 优先 + `mAddress` 兜底，仍不行就是设备本身没有可用的 `BluetoothDevice` |
| iCloud / 耳机行没有图标 | 设置和模块都没有该图标 | 日志看 `card icon ic_cloud <- module|settings|missing`；`missing` 说明两个来源都失败 |

## 4. 控制中心行没有开关

1. 日志是否出现 `airplane switch attached to 控制中心 row`；
2. 没有 → `widget_frame` 没找到，或 `SlidingSwitch` 创建失败（日志会有异常堆栈）；
3. 有但位置偏 → 见 [坑 B 表](06-pitfalls.md#b-布局与视觉) 的 `centerInParent`；
4. 开关状态不同步 → 只在列表重新绑定时刷新；在控制中心里改了飞行模式，回到首页应会更新，若仍不同步说明 `onResume` 没触发。

## 5. 二级页图标

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 完全没图标 | `PreferenceFragmentCompat` 没 hook 上 | 日志 `hooked PreferenceFragmentCompat` |
| 部分行没图标 | key/标题不在映射表 | 在 `SubPageIcons.KEY_ICONS` 补，或补标题关键词 |
| 图标大小不一 | 源图尺寸不一致 | 重跑 `tools/make_subpage_icons.py`（统一 28dp） |
| 图标是方的 | 生成时没做圆角遮罩 | 同上，改脚本重新生成 |

## 6. 卡片视觉

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 双层圆角 | `CardFix`/`SubPageCards` 没生效 | 检查是否 hook 到 `HeaderAdapter` / `Preference#onBindViewHolder` |
| 最后一格图标偏大 | 同上 | 同上 |
| 深色模式下卡片发白 | 主题 drawable 替换用了固定颜色 | 用透明 drawable，不要用白色 |
| 切换主题后错乱 | 主题切换会重建列表 | 重新进入设置，或在 `onResume` 里再跑一次 |

## 7. 崩溃 / 卡顿 / 发热

- **设置闪退**：`adb logcat -b crash` 抓堆栈，看是否 `com.Remo.ilayoutsettings` 抛异常；大概率是某个 hook 没包 try/catch；
- **卡顿**：玻璃抓图是主要开销；日志里若每帧都在抓图，考虑降低抓图尺寸或触发频率；
- **发热**：同上；先把模块停用做对照，确认是不是本模块导致。

## 8. 安装/升级失败

| 报错 | 含义 | 处理 |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名不一致 | `adb uninstall com.Remo.ilayoutsettings` 后重装 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | versionCode 比已装的小 | 卸载旧包，或提高 versionCode |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | 包没签名/签名损坏 | 重新构建；不要手工改 zip |

## 9. 构建失败

见 [构建指南 · 常见失败](03-build.md#8-常见失败)。

## 10. 反馈模板

```text
机型：
系统：HyperOS 3 / Android 15（设置版本：）
模块版本：16.5 (96)
现象：
复现步骤：
日志（adb logcat -s iLayoutSettings:V，从打开设置开始）：
截图：
```
