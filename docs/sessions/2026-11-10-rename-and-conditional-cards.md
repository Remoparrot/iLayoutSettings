# 改名 iLayoutSettings + 条件卡片 + 搜索栏过渡（2026-11-10）

状态：已完成（16.0 正式包待用户安装后补一次真机确认）
关联版本：15.5 → 16.0
关联提交：<待补，见本条会话最后的 commit>

## 1. TL;DR

首页新增三条能力：iCloud 行（Game Center 下）、按条件出现的「有软件更新可用」卡（带红色 1 角标）、
按条件出现的耳机卡；底部搜索栏进出场加了 220ms 过渡；同时把模块/应用/仓库改名为 **iLayoutSettings**、
包名改为 `com.Remo.ilayoutsettings`，并加了自动发版脚本。功能在**同源测试包**（旧包名）上机验证，
正式包因 MIUI 禁止 adb 安装新包尚未上机。

## 2. 对话重点

- 用户要求（原话要点）：
  1. 「加一个 iCloud 在 Game Center(未成年人模式) 的下面，图标 ic_cloud.png，可以打开小米云服务」；
  2. 「再加一个耳机显示，ic_sub_airpods.png，只有连接耳机时才会像图中那样新开一个卡片，名称是耳机的名称，
     再加一个软件更新提示，如图，只有在有软件更新时才会显示」；
  3. 「此 lsp 模块的名称改成 iLayoutSettings，github 仓库名一起改，Releases 以后都自动」；
  4. 「点击搜索栏时搜索栏会瞬移到上面，加一个过渡动画，取消搜索时也是」；
  5. 「不要在我手机的存储根目录里放文件，创一个文件夹来放」。
- 决策：
  - 包名按用户指定的 `com.Remo.ilayoutsettings`（保留大写 `Remo` 段；实测 MIUI 安装拦截与新包名无关，
    是「ADB 安装新应用」策略）；
  - 「自动图标」经确认是 **「自动更新」打错**，落地为自动发版脚本，不是自动生成图标；
  - 更新卡条件用 `Settings.Global.miui_new_version`（反编译 `com.android.updater` 找到的写入点）；
  - 耳机卡用 `AudioManager` 输出设备 + 蓝牙别名，而不是读 MIUI 的私有耳机页；
  - 搜索过渡放弃「整页飞行」方案（玻璃抓图拖影 + 两个胶囊），改成短距离滑动+缩放+淡入淡出。
- 范围外但被提到：暂不做 GitHub Actions（脚本已具备条件，P1 再做）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/AndroidManifest.xml` | package / label / versionName 16.0 / versionCode 91 / provider authority | 改名 + 破坏性升级 |
| `module/assets/xposed_init` | `com.Remo.ilayoutsettings.Module` | 包名变更 |
| `module/src/com/Remo/ilayoutsettings/*` | 整包迁移（git mv），包声明与常量全部替换 | 同上 |
| `HeaderData.java` | 新增 `addICloudRow` / `addUpdateRow` / `addAirpodsRow`、`mark()/isMarked()`、`connectedHeadsetName()`、`moduleDrawable()` | 三条新卡片；条件加载 |
| `HeaderOverlay.java`（新） | 在 `HeaderAdapter.onBindViewHolder` 后画更新角标、给耳机/iCloud 行强制设图标 | 原生 header 没有角标位；adapter 有时只读 `iconRes` |
| `BottomSearch.java` | `animateBar()` 进出场动画、`ANIM_OPEN_DELAY`、`setGlassVisible()`、`install()` 复用胶囊 | 消除瞬移、避免重复胶囊和玻璃拖影 |
| `Hook.java` | 安装 `HeaderOverlay` | 新 hook 入口 |
| `XLog.java` | TAG → `iLayoutSettings` | 改名后日志过滤 |
| `tools/make_subpage_icons.py` | 修输出目录、新增 `ic_cloud` / `ic_sub_airpods` | 生成 26 个图标 |
| `module/res/drawable-xxhdpi/ic_cloud.png`、`ic_sub_airpods.png` | 新图标（84px，28dp） | 卡片图标 |
| `module/build.ps1` | 产物名 `iLayoutSettings-*.apk`、dex 校验类名 | 改名 |
| `tools/release.ps1`（新） | 构建→校验→tag→Release→上传 | 「以后都自动发版」 |
| 文档 | 00/01/02/03/04/06/07/08/09/README/agent + 本记录 | 强制同步 |

## 4. 设计思路

- **条件卡片为什么用合成 header 而不是自绘**：卡片圆角/间距/按压/深色模式都是 miuix 的
  `CardItemDecoration` 画的，只要给 header 一个独立的 `groupId`（更新 1、耳机 2）就能白嫖整套卡片；
  自绘要和系统动画、主题、无障碍全对着干。
- **为什么用 `Header.extras` 作标记**：标题会随耳机名变化，`id` 资源又必须来自 Settings 包；
  extras 是 Header 自带的 Bundle，重排时不需要额外全局状态，被回收成普通行时也容易判断。
- **更新状态为什么读 `miui_new_version`**：反编译 `com.android.updater` 后发现它发现新版本时写
  `<version>.<codebase>`、检查完写 null（`SettingsUtil.h` / `Updater2`），比读通知或轮询 OTA 稳定；
  代价是这个键会被系统更新自己清掉（已记入坑 C5）。
- **耳机为什么用音频设备而不是蓝牙配对列表**：配对不等于连接；`AudioManager.getDevices(OUTPUTS)` 直接给
  A2DP/SCO/BLE/有线/USB 的连接态，且 `AudioDeviceInfo.getAddress()` 能拿到 MAC 再查别名。
- **搜索过渡为什么放弃「飞向顶部」**：第一版让胶囊飞满全程（~900px），结果玻璃抓的底图被拉出拖影，
  且 MIUI 收起自己的搜索框时画面上同时存在两个胶囊；改成 28dp + 缩放 + 淡入淡出，并在动画期间隐藏
  `SearchGlass`，观感干净且不会和 MIUI 的折叠动画打架。

## 5. 踩过的坑

1. **MIUI 拦截 adb 安装新包**
   - 现象：`adb install` 新包报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，
     `pm install-create/-write/-commit` 同样失败；换任意新包（无关 APK）也一样。
   - 根因：MIUI 安全中心 `com.miui.permcenter.install.AdbInstallActivity` 立刻 finish（「USB 安装」未放行），
     同包名覆盖安装不受限。
   - 解法：功能验证改用**同包名测试包**（源码相同，只把包名换回旧 `com.abradant.iossettings`），
     正式包装好后让用户手动安装。
   - 如何避免：把「新包名必须手动装」写进 04-install 与 Release 说明；以后尽量少改包名。
2. **`settings put global miui_new_version` 一会儿就没了**
   - 现象：更新卡出现后重进设置又消失。
   - 根因：`com.android.updater` 检查完会把这个键写成 null。
   - 解法：接受；调试时短时间预览即可。已记入坑 C5。
3. **搜索过渡出现两个胶囊 / 玻璃拖影**
   - 现象：取消搜索时画面上同时出现移动中的胶囊和停在底部的胶囊；玻璃层像被拉花。
   - 根因：`install()` 每次 resume 都删旧建新，旧胶囊的动画还在播；`SearchGlass` 抓的底图跟着视图移动。
   - 解法：同 fragment / 同夜间模式复用胶囊（`sWrap`/`sAnchor`）；动画期间 `setGlassVisible(false)`，
     结束后恢复。
4. **合成行图标不显示**
   - 现象：iCloud 行只有文字（耳机行因为实时设图标才正常）。
   - 根因：MIUI adapter 绑定图标时优先 `iconRes`，只写 `Header.icon` 不生效。
   - 解法：`HeaderOverlay` 在 bind 后对耳机/iCloud 行直接 `ImageView.setImageDrawable`。
5. **`[IO.File]::ReadAllText` 相对路径踩到 PowerShell 的 CWD**
   - 现象：`cd iLyayot` 之后脚本仍报找不到 `module\build.ps1`。
   - 根因：`.NET` 的当前目录不等于 PowerShell 的 location。
   - 解法：批处理里一律用 `Resolve-Path` / 绝对路径。

## 6. 验证与证据

- 构建（正式包）：
  ```
  package: name='com.Remo.ilayoutsettings' versionCode='91' versionName='16.0'
  launchable-activity: name='com.Remo.ilayoutsettings.MainActivity' label='iLayoutSettings'
  xposed_init : True / module class: True / reorder : True
  size   : 220209 bytes
  sha256 : 8DA082CD8FC578676EB5138F7150BDCFB40C4DBE45CD3625E8B0521923921A73
  证书   : 941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c
  ```
- 同源测试包（旧包名，代码相同）真机 25053RT47C / HyperOS 3 / Android 16：
  - `icloud row added below Game Center`；点击 → `com.miui.cloudservice/.ui.MiCloudMainActivity`；
  - 临时写 `miui_new_version=3.0.999.0.WOLCNXM.TEST` → 首页出现「有软件更新可用」+红色 1；
    点击 → `com.android.updater/.ui?UpdateActivity`（`mCurrentFocus=com.android.updater/com.android.updater.UpdateActivity`）；
    删除后卡片消失；
  - 连接 REDMI Buds 8 Pro → `airpods card added: REDMI Buds 8 Pro`，卡片为耳机名+图标；
    点击 → `com.android.settings/.Settings$BluetoothSettingsActivity`；断开后消失；
  - 搜索栏：点击后先播 120ms 出场再进搜索；back 后同一路径返回；截图（过渡中）无重复胶囊；
  - 截图：`docs/assets/home-cards-16.0.png`（更新卡+耳机卡）、`docs/assets/home-icloud-16.0.png`（iCloud 行）。
- **未验证**：16.0 正式包（新包名）未能在真机安装（MIUI 拦截）；LSPosed 里新模块也未启用。
- 附带清理：手机存储根目录里遗留的 `ui*.xml / gui*.xml / fui*.xml / window.xml` 已移到
  `/sdcard/Download/iLayoutSettings/`，之后所有调试产物统一放这个目录。

## 7. 未完成 / 下一步 / 风险

- 未完成：正式包上机；GitHub Actions 自动构建（脚本已就绪）；第三方主题下的新图标验证。
- 下一步顺序：用户装 16.0 → LSPosed 启用 → 抓 `iLayoutSettings` 日志确认三类卡片 → 再看是否需要
  给更新卡补 `miui_update_ready` 辅助判断。
- 风险：`local` 的 `miui_new_version` / 耳机检测都依赖 MIUI/系统行为，ROM 升级后可能改名/改语义；
  搜索栏过渡的复用逻辑保留了旧胶囊，若以后要做主题热切换重建，需要同时清掉 `sWrap` 引用。
