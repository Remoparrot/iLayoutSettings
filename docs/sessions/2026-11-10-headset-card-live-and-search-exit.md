# 耳机卡即时刷新 / 详情页 / 搜索退出时序（2026-11-10）

状态：已完成
关联版本：16.0 → 16.1
关联提交：<待补，见本条会话最后的 commit>

## 1. TL;DR

按用户三点要求改了耳机卡与搜索栏：① iCloud / 耳机图标优先取设置包里的同名资源，没有才用模块内置；
② 耳机卡点击改为打开 MIUI 耳机详情页（电量 / 改名 / 功能开关），不再只跳蓝牙列表；
③ 退出搜索时底部栏等 MIUI 收起顶部搜索框后再进场，消除「短暂两个搜索栏」。
同时修了用户追加的「连耳机必须重启设置」——新增 `HeadsetWatcher` 监听音频设备，卡片即时刷新。
16.1（92）已构建、覆盖安装，并在 25053RT47C 上实测通过。

## 2. 对话重点

- 用户要求（原话要点）：
  1. 「图标是设置中的 ic_cloud.png / ic_sub_airpods.png，没有时才用模块内置的图标」；
  2. 「耳机应该是点击后进入耳机的详情界面」；
  3. 「搜索栏退出时会短暂有两个搜索栏」；
  4. （追加）「在设置中连接耳机时无法显示耳机卡片，必须重启设置，请修复」。
- 决策：
  - 图标按「设置包同名 drawable → 模块内置」解析；**实测本机 `com.android.settings` 没有这两个资源**
    （aapt2 全量 dump + 运行时日志 `<- module`），所以当前视觉不变，属于给主题/新 ROM 留的优先通道；
  - 耳机详情页用 MIUI 自己的 `miui.bluetooth.action.HEADSET_SETTINGS` →
    `com.android.settings.bluetooth.MiuiHeadsetActivity`（反编译确认它读
    `android.bluetooth.device.extra.DEVICE` 或 `bluetoothaddress`，且 `COME_FROM` 为空会直接 finish）；
  - 搜索栏不再用固定延时猜时机，改成给 `miuix.view.SearchActionMode` 挂动态代理
    `ActionModeAnimationListener`，在 `onStop(open=false)` 后进场，700ms 兜底；
  - 耳机卡刷新用 `AudioManager.registerAudioDeviceCallback`（比广播干净、覆盖有线/USB/BLE），
    350ms 防抖，`onPause`/`onDestroy` 注销。
- 范围外但被提到：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../HeaderData.java` | 新增 `cardDrawable()`：Settings 同名 drawable 优先、模块兜底；`addICloudRow` / `addAirpodsRow` 改用它 | 用户要求的图标来源；`HeaderOverlay` 同步 |
| 同上 | 新增 `connectedHeadsetDevice()` / `bestHeadsetOutput()` / `headsetPriority()` / `remoteDevice(AudioDeviceInfo)` / `isUsableBluetoothAddress()` / `hiddenAddress()` | A2DP 优先拿真实 MAC；MIUI 给 `00:00:00:00:00:00` 时读 `mAddress`/`toString()` 兜底 |
| 同上 | 新增 `headsetIntent()`：`HEADSET_SETTINGS` + DEVICE + `COME_FROM` + CATEGORY_DEFAULT；解析不到按类名，再退回蓝牙设置 | 点击进耳机详情页 |
| 同上 | 新增 `refresh(fragment)`：先 `LAST_ORDER.remove(fragment)` 再 `apply()` | `apply()` 的「顺序没变就早退」会让新卡加不进来 |
| 同上 | `activity()` 改为包内可见；`isHeadsetType()` 改为包内可见 | 供 `HeadsetWatcher` 使用 |
| `module/src/.../HeaderOverlay.java` | `moduleDrawable` → `cardDrawable` | 与卡片图标同源 |
| `module/src/.../HeadsetWatcher.java`（新） | 注册音频设备回调、350ms 防抖、`HeaderData.refresh`；`detach` 注销 | 连/断耳机不用重启设置 |
| `module/src/.../Hook.java` | `onViewCreated` / `onResume` 里 `HeadsetWatcher.attach`，新增 `onPause` / `onDestroy` 注销 | 生命周期成对 |
| `module/src/.../BottomSearch.java` | `onDestroyActionMode` 只隐藏不显示；`addExitListener()` 动态代理 `ActionModeAnimationListener#onStop`；`scheduleFallbackShow(700ms)`；`isSearching()` 优先读 `mIsInActionMode` | 修「两个搜索栏」和「二级页回来底部栏消失」 |
| `module/AndroidManifest.xml` | 16.1 / versionCode 92 | 版本号 |
| `tools/release.ps1` | Release 正文改成 16.1 的内容 | 发版说明不能停留在 16.0 |
| 文档 | README / 00-status / 02-architecture / 06-pitfalls / 07-changelog / 本记录 / sessions 索引 | 强制同步 |

## 4. 设计思路

- **为什么图标「设置优先」而不是直接换图**：用户的原话是「设置中的 …，没有时才用模块内置」。
  设置包的图标会随主题/ROM 更新，优先用系统的能少一份维护；但本机 `com.android.settings`
  的 drawable 全量 dump 里确实没有 `ic_cloud` / `ic_sub_airpods`，所以运行时日志是
  `card icon ic_cloud <- module`。保留这条通道，主题或新 ROM 提供同名资源时自动切换。
  备选的 `cloud_icon.png`（204×204 蓝色圆角云）和 `ic_bt_untethered_earbuds.xml`（白色线稿）
  都不适合塞进 header 的「白 glyph + 灰底」图标位，会变成双层底色，所以没有采用。
- **为什么耳机详情用 MiuiHeadsetActivity**：反编译 `MiuiBluetoothSettings` 看到原生蓝牙页点的就是它，
  传 `DEVICE` + `COME_FROM=MIUI_BLUETOOTH_SETTINGS`；实测（先 `am start` 传 `bluetoothaddress`）
  直接落到标题为耳机别名、带左/右/充电盒电量与「重命名 / 检查更新 / 通话 / 媒体音频」的页面。
  备选的 `BLUETOOTH_DEVICE_DETAIL_SETTINGS` 走 `FRAGMENT_CLASS` + `show_fragment_args`，
  参数要包一层 Bundle，且 MIUI 实际不使用；只作为解析失败后的类名兜底。
- **为什么用 AudioDeviceCallback 而不是广播**：`BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`
  只覆盖 A2DP，SCO/BLE/有线/USB 都要另写；`AudioManager` 回调直接对应我们判断卡片的
  `getDevices(GET_DEVICES_OUTPUTS)`，一套代码覆盖全部类型，也不需要 BLUETOOTH_CONNECT。
- **为什么搜索退出要等 onStop**：`onDestroyActionMode` 是收起动画的**开始**，此时 MIUI 的顶部
  搜索框还在屏幕上；如果立刻播底部栏进场，两个栏会重叠约 200ms。给 `SearchActionMode` 挂监听是
  唯一能精确知道「收完了」的信号；`onStop` 的 boolean 经实测 `false=收起`（`true=展开`），
  配合 `mIsInActionMode=false` 双保险。
- **为什么顺手修 `isSearching()`**：原来探测 `search_result_ll` 的可见性 + 高度，但它是全屏容器，
  首次布局后有高度，导致每次 resume 都被判成「还在搜索」，底部栏从二级页回来就被 `GONE`。
  改读 `mIsInActionMode` 后行为稳定；视图探测只作字段缺失时的兜底。

## 5. 踩过的坑

1. **耳机卡地址是 `00:00:00:00:00:00`**
   - 现象：`headset card -> headset detail 00:00:00:00:00:00`，点开是空白/错误设备。
   - 根因：`AudioDeviceInfo.getAddress()` 对 A2DP 输出有时返回占位地址；输出列表里 SCO 条目也可能没有真实 MAC。
   - 解法：`headsetPriority` 让 A2DP 优先；`isUsableBluetoothAddress` 过滤全 0 / 全 F；无效时读私有
     `mAddress`，再不行从 `toString()` 的 `addr:` 段取。
   - 如何避免：任何「从系统对象拿地址」的地方都先做有效性校验，别直接 `getRemoteDevice`。
2. **`apply()` 早退导致新卡永远加不进来**
   - 现象：想用 `HeaderData.apply()` 刷新时，列表没变化就直接 return，耳机卡不出现。
   - 根因：`LAST_ORDER` 缓存了上次的顺序，`sameOrder` 命中就早退，早退发生在「清理/重新加合成行」之前。
   - 解法：`refresh()` 先 `LAST_ORDER.remove(fragment)`；耳机设备变化走 `refresh()` 而不是 `apply()`。
   - 如何避免：给「幂等重排」加一个显式的 force 入口，别依赖改调用参数。
3. **搜索退出两个栏 / 从二级页回来底部栏消失**
   - 现象：退出搜索的一瞬间顶部搜索框和底部胶囊同屏；从二级页返回后底部胶囊消失。
   - 根因：`onDestroyActionMode` 是收起动画开始；`isSearching()` 用 `search_result_ll` 误判。
   - 解法：等 `ActionModeAnimationListener#onStop(open=false)`；`isSearching` 改读 `mIsInActionMode`。
   - 如何避免：动画时序不要用固定 sleep 猜，优先找系统的「动画结束」回调；视图探测要有明确的生命周期。
4. **`am start` 传空字符串 extra 会报错**
   - 现象：`am start ... --es MIUI_HEADSET_SUPPORT ""` → `IllegalArgumentException: Argument expected after ...`。
   - 根因：shell 把空字符串当成没有参数。
   - 解法：不传这个 extra（`MiuiHeadsetActivity` 允许 mSupport 为 null）。
   - 如何避免：手工 `am start` 调试时，可选参数宁可不传。

## 6. 验证与证据

- 构建（16.1 / 92，本机）：
  ```
  package: name='com.Remo.ilayoutsettings' versionCode='92' versionName='16.1'
  sdkVersion:'29' / targetSdkVersion:'36'
  launchable-activity: name='com.Remo.ilayoutsettings.MainActivity'  label='iLayoutSettings'
  dex version : 039 / xposed_init : True / module class : True / reorder : True
  size   : 224305 bytes
  sha256 : 83BE73357A1AD29DCB57EC8BCF217127BEA7B7461A18DF70E0189EC83571F77F
  证书   : 941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c
  ```
- 安装：`adb install -r -d dist\iLayoutSettings-16.1.apk` → `Success`（同包名同签名覆盖，MIUI 不再拦）。
- 真机 25053RT47C / HyperOS 3 / Android 16：
  - 图标：`card icon ic_sub_airpods <- module`、`card icon ic_cloud <- module`
    （`aapt2 dump resources base.apk` 确认 Settings 没有这两个 drawable）；
  - 耳机卡（REDMI Buds 8 Pro 已连）：
    `headset card -> headset detail B8:53:84:F0:C6:C4`、
    `airpods card added: Remo的REDMI Buds 8 Pro`；
    点击卡片 → `topResumedActivity=com.android.settings/.bluetooth.MiuiHeadsetActivity`，
    截图为电量页（左/右 100%、充电盒 30%、重命名、检查更新、通话/媒体音频/输入设备开关）；
  - 即时刷新：设置首页在前台时 `adb shell cmd bluetooth_manager disable` →
    `headset devices changed, refresh cards` → `airpods card: no headset connected, skip` →
    `reordered 31 headers`，截图确认耳机卡消失、网络卡上移（无需重启设置）；
    `enable` + 快速连接回来后卡片自动恢复；
  - 搜索退出：`action mode onStop opening=false inMode=false` 后底部栏才进场；
    截图（收起中）只有顶部搜索框，800ms 后只有底部栏，无重叠；
  - 二级页返回：`uiautomator dump` 中 `search_mode_stub` 仍在，底部栏不再消失。
- **未验证**：开机后首次连接耳机（首页从未 resume 过的极端时序）、第三方主题提供
  `ic_cloud`/`ic_sub_airpods` 的情况（本机没有这样的主题/ROM）。

## 7. 未完成 / 下一步 / 风险

- 未完成：GitHub Actions 自动构建；耳机详情页在非小米耳机（如 AirPods Pro）上的兜底表现未逐一验证。
- 下一步顺序：
  1. 观察真实「开盖自动连」时卡片的出现延迟，必要时把防抖 350ms 降到 200ms；
  2. 若用户主题提供了 `ic_cloud` / `ic_sub_airpods`，确认 `cardDrawable` 能取到并切到 `<- settings`；
  3. 接 CI：tag 触发 release.ps1 的只读版本。
- 风险：`MiuiHeadsetActivity` / `mIsInActionMode` / `mAddress` 都是 MIUI 私有实现，ROM 升级可能改名；
  已按「字段缺失→保守隐藏 / 退回蓝牙设置」处理，但换 ROM 后需要重新验证。
