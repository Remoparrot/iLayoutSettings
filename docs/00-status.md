# 项目状态快照

> 状态：**现行** · 最后更新：2026-11-10
> 每次会话结束必须更新本文件。只写「现在是什么样」，历史过程放 `sessions/`。

## 当前版本

| 项 | 值 |
| --- | --- |
| versionName / versionCode | **16.0 / 91**（唯一来源：`module/AndroidManifest.xml`） |
| 包名 | **`com.Remo.ilayoutsettings`**（16.0 由 `com.abradant.iossettings` 改名，属于破坏性变更） |
| 应用名 / 仓库名 | **iLayoutSettings**（GitHub 仓库同步改名，旧地址会 301 跳转） |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | `com.android.settings` |
| 当前包 | `dist/iLayoutSettings-16.0.apk`（220,209 B，SHA-256 `8DA082CD…21A73`；**只在本机测试包上机验证，正式包待用户安装**） |
| 旧包 | `dist/IosSettingsLayout-15.5.apk`（Release v15.5）与 14.8（证书不同）保留作历史 |

## 发布物指纹

| 包 | 大小 | SHA-256 | 签名证书 SHA-256 |
| --- | --- | --- | --- |
| `dist/IosSettingsLayout-14.8.apk`（旧） | 195,465 B | `8D6DC362E5FDA29B3C2B56C5A14FB71325072A4626B141CE3428B2460B9F4951` | `b6f98da5…0d4a`（与 15.x 不同） |
| `dist/IosSettingsLayout-15.5.apk`（旧） | 207,753 B | `C8146210C872BB737C100FD57E598DCA73DD8F5C1134CE2AEF4C43CB6A05E535` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |
| `dist/iLayoutSettings-16.0.apk`（**当前**） | 220,209 B | `8DA082CD8FC578676EB5138F7150BDCFB40C4DBE45CD3625E8B0521923921A73` | `941e18c51012be17f592ae25847a341d165bf77a27f9bc5a1b2e3ad14895248c` |

> 16.0 仍用同一把测试密钥，但**包名变了**：不能覆盖安装旧模块，必须先卸载旧包；
> 见 [安装说明 · 16.0 迁移](04-install.md#160-迁移必须做)。

## 仓库与发布

| 项 | 值 |
| --- | --- |
| 仓库 | https://github.com/abradanthawk-design/iLayoutSettings （旧 `iLyayot` 自动跳转） |
| 默认分支 | `main` |
| 最新 Release | `v16.0`（tag 指向 16.0 提交，附件 `iLayoutSettings-16.0.apk`） |
| 自动发版 | `tools/release.ps1`：构建 → 校验 → 打 tag → 建 Release → 上传附件，一条命令完成 |
| 旧 Release | v15.5、v14.8 保留 |

## 现在能做什么

- ✅ 首页入口按 iOS 分组重排（`HeaderData`），卡片仍由 miuix 绘制
- ✅ 首页「专注模式」「相机」「搜索」合成入口（15.4 / 15.5 起）
- ✅ **iCloud 入口（16.0）**：固定在「Game Center」下方、同一张卡片；
  点击打开小米云服务 `com.miui.cloudservice/.ui.MiCloudMainActivity`；
  图标为模块内置 `ic_cloud`（`tools/make_subpage_icons.py` 生成）
- ✅ **软件更新卡片（16.0，条件显示）**：读 `Settings.Global.miui_new_version`，
  非空才出现；标题「有软件更新可用」，右侧红色「1」角标（`HeaderOverlay` 画在 `widget_frame` 里），
  点击打开 `com.android.updater/.MainActivity`；更新状态变化后重进设置即刷新
- ✅ **耳机卡片（16.0，条件显示）**：`AudioManager.getDevices()` 检测 A2DP / SCO / BLE / 有线 / USB / 助听器，
  标题 = 设备别名（`BluetoothDevice.getAlias()`，取不到用 `getProductName()`），图标模块内置 `ic_sub_airpods`，
  点击打开蓝牙设置；没有连接耳机时整张卡不出现
- ✅ 底部搜索栏 + 液态玻璃（`BottomSearch` / `SearchGlass`）
- ✅ **搜索栏进出场过渡（16.0）**：进出搜索时底部胶囊 220ms 滑动 + 缩放 + 淡入淡出，
  不再瞬间消失/出现；动画期间隐藏玻璃层避免抓图拖影，动画结束后恢复
- ✅ 控制中心行右侧飞行模式开关（`AirplaneSwitch`）
- ✅ 二级页图标、卡片末行修正、顶栏滚动模糊、应用内参数界面
- ✅ 无 Gradle 构建脚本（`module/build.ps1`）+ 一键发版脚本（`tools/release.ps1`）

## 已知问题

1. **16.0 正式包尚未在真机以新包名验证**（本机 MIUI 禁止 adb 安装新包：
   `INSTALL_FAILED_USER_RESTRICTED`）。功能代码已用**同源测试包**（旧包名，`com.abradant.iossettings`）
   在 25053RT47C 上验证：iCloud 行、更新卡 + 角标、耳机卡（真实 REDMI Buds 8 Pro）、
   点击跳转、搜索栏过渡全部正常；正式包只差包名/资源包名不同。
2. **包名变更 = 必须重装**：旧包 `com.abradant.iossettings` 要卸载；新包安装后
   要在 LSPosed 里**重新启用并勾选作用域**（否则设置不生效）。
3. **条件卡片依赖小米自己的状态**：
   - 更新卡的 `miui_new_version` 会被系统更新 App 自己清空/写入，手动 `settings put` 只用于测试；
   - 耳机卡只认音频输出设备；耳机连上/断开后需要重进设置（或 resume）才刷新。
4. **16.0 只在默认主题 / 默认图标包上验证**；第三方主题替换 `ic_*` 资源时需要再确认。
5. `docs/07-changelog.md` 的旧「7. 校验」节数据过期（历史记录，未改）。
6. 无 CI：`tools/release.ps1` 是本机脚本，GitHub Actions 仍未接。

## 下一步（优先级从高到低）

1. 用户安装 `dist/iLayoutSettings-16.0.apk` → LSPosed 启用 → 真机确认 16.0 正式包；
2. 观察「有软件更新可用」在真实 OTA 场景下的出现/消失，必要时补 `miui_update_ready` 作为辅助条件；
3. 接 GitHub Actions：tag 触发 `tools/release.ps1` 的只读版本（构建 + 上传）；
4. 清理 `Hook` 未引用的遗留类（`GlassToggle`、`IntroCard`、`AboutIos` 等）；
5. 平板/分屏适配。

## 环境事实（本机）

| 项 | 路径 / 版本 |
| --- | --- |
| JDK | 17.0.21（`apk6ify\.downloads\jdk\jdk17.0.21_0`） |
| build-tools | 34.0.0（`apk6ify\.downloads\bt\android-14`） |
| android.jar | API 36（`ios-settings-mod\.tools\platform36\android-36.1\android.jar`，构建必须显式传 `-AndroidJar`） |
| 设备 | 25053RT47C / HyperOS 3 / Android 16，adb `D:\platform-tools\adb.exe` |
| 调试产物目录 | 手机上统一放 `/sdcard/Download/iLayoutSettings/`（不要散落在存储根目录） |
