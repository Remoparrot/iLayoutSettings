# iLayoutSettings · iOS 设置布局

> 把澎湃 OS（HyperOS 3）「设置」首页改成 iOS 风格：入口重排、条件卡片（软件更新 / 耳机 / iCloud）、搜索栏沉底、卡片细节修正。
> **全部复用 MIUI 原生控件与原生数据，模块不重绘任何页面。**

[![Version](https://img.shields.io/badge/version-16.1-1f6feb)](https://github.com/abradanthawk-design/iLayoutSettings/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-HyperOS%203%20%7C%20Android%2015%2B-green)](docs/01-overview.md)
[![LSPosed](https://img.shields.io/badge/framework-LSPosed-orange)](docs/04-install.md)

---

## 这是什么

一个运行在「设置」（`com.android.settings`）进程里的 **LSPosed 模块**。它只做三件事：

1. **重排**首页入口，并按 iOS 的分组重写 `Header.groupId`，让 miuix 自己把卡片画出来；
2. **按条件合成卡片**：有 OTA 时才出现的「有软件更新可用」、连着耳机时才出现的耳机卡、固定在 Game Center 下的 iCloud；
3. 把原生搜索栏**移到底部**，进出搜索时带过渡动画，点击行为仍然是 MIUI 自己的搜索。

背景、行样式、圆角、点击全部是系统原生的——所以它看起来像换了一套布局，而不是套了一层主题。

| 能力 | 说明 |
| --- | --- |
| 首页重排 / 分组 | iOS 顺序 + 独立卡片，卡片圆角与间距仍由 miuix `CardItemDecoration` 绘制 |
| 软件更新卡（条件） | 读 `Settings.Global.miui_new_version`，非空才显示「有软件更新可用」+ 红色「1」角标，点击进系统更新 |
| 耳机卡（条件） | 检测已连接的蓝牙/有线/USB 耳机，标题 = 设备别名；**连上/断开后即时刷新，不用重启设置**；没连耳机整张卡不出现，点击进 MIUI 耳机详情页（电量/改名/EQ） |
| iCloud 入口 | 固定在「Game Center」下方，点击打开小米云服务 |
| 条件卡图标 | 优先用设置（`com.android.settings`）里的同名 `ic_cloud` / `ic_sub_airpods`，没有才用模块内置图标 |
| 专注模式 / 相机 / 搜索入口 | 分别在「屏幕时间」「外观」「墙纸」附近，复用系统或模块图标 |
| 飞行模式开关 | 右侧原生 `SlidingSwitch`，图标优先用设置主题里的 `ic_airplane_mode_settings` |
| 底部搜索栏 | `miuix_appcompat_search_stub` 原生布局 + **Kyant backdrop 玻璃**（模糊 + 折射；保留主题胶囊） |
| 搜索栏过渡 | 进出搜索 220ms 滑动+缩放+淡入淡出；**退出时等 MIUI 收起顶部搜索框后再出现底部栏**，不再短暂出现两个搜索栏 |
| 参数界面 | 应用内滑杆：搜索栏模糊 / 折射力度 / 折射厚度、顶栏模糊，滑杆即改即生效 |
| 卡片细节修正 | 去掉每组最后一行的双层圆角/放大图标；外观页预览加卡片底 |
| 顶部模糊 | 列表滚动时出现；半径由 `topbar_blur` 实时读取 |

## 截图

| 条件卡片（16.0：更新 + 耳机） | iCloud 入口（16.0） |
| --- | --- |
| ![条件卡片](docs/assets/home-cards-16.0.png) | ![iCloud](docs/assets/home-icloud-16.0.png) |

首页（账号 + 卡片分组）与搜索栏：

| 首页 | 搜索栏 · 浅色 | 搜索栏 · 深色 |
| --- | --- | --- |
| ![首页](docs/assets/home-15.3-light.png) | ![搜索栏 · 浅色](docs/assets/search-glass-15.3-light.png) | ![搜索栏 · 深色](docs/assets/search-glass-15.3-dark.png) |

## 安装

1. 下载 [`dist/iLayoutSettings-16.1.apk`](dist/iLayoutSettings-16.1.apk)，或到 [Releases · v16.1](https://github.com/abradanthawk-design/iLayoutSettings/releases/tag/v16.1) 下同名附件（224,305 B，SHA-256 `83BE7335…77F7`，与 `dist/` 同一份）；
   **16.0 起包名是** `com.Remo.ilayoutsettings`，从 16.0 升级可直接覆盖（同一签名）；
2. LSPosed 中启用 **iLayoutSettings**，作用域只勾选 **设置**（`com.android.settings`）；
3. **强制停止「设置」**（或重启手机），再打开设置；
4. 打开桌面上的 **iLayoutSettings** 图标即可调参数（模糊 / 折射 / 顶栏模糊）。

> 本机 MIUI 会拦截 adb 安装新包（`INSTALL_FAILED_USER_RESTRICTED`）：把 APK 复制到手机后
> 在文件管理器里点开安装即可；或在开发者选项里允许「USB 安装」。
> 旧版本 15.5 / 14.8 仍在各自 Release 里（14.8 证书不同）。

日志过滤 `iLayoutSettings` 应能看到 `hooks installed`、`icloud row added below Game Center`、
按条件出现的 `update card added: …` / `airpods card added: …`、`headset devices changed, refresh cards`，
以及 `liquid glass shader compiled`、`search glass mask=...`、`airplane icon set: ...`。
详见 [安装与调试](docs/04-install.md)。

## 兼容性

| 项 | 值 |
| --- | --- |
| 系统 | 澎湃 OS 3（HyperOS 3）· Android 15/16 |
| 目标进程 | `com.android.settings`（仅此一个作用域） |
| 模块 | Xposed API 82 / LSPosed 1.x、2.x |
| minSdk / targetSdk | 29 / 36 |
| 设备 | 手机；平板/分屏未专门适配（欢迎反馈） |

## 从源码构建

不需要 Gradle，`aapt2 → javac → d8 → zipalign → apksigner` 一条 PowerShell 脚本：

```powershell
pwsh -File module/build.ps1 `
  -Jdk C:\jdk17 `
  -BuildTools C:\android-sdk\build-tools\34.0.0 `
  -AndroidJar C:\android-sdk\platforms\android-36\android.jar
```

输出 `dist/iLayoutSettings-<versionName>.apk`。
发版用 `tools/release.ps1 -Version 16.1`：构建 → 校验 → 打 tag → 建 Release → 上传 APK，一条命令完成。
完整工具链版本、可复现性说明见 [构建指南](docs/03-build.md)。

## 文档

- **第一次来 / 新开一个 AI 会话** → [agent.md](agent.md) → [文档地图](docs/README.md) → [当前状态](docs/00-status.md)
- 想改代码 → [架构](docs/02-architecture.md) + [设计思路](docs/05-design-notes.md) + [踩坑记录](docs/06-pitfalls.md)
- 出问题了 → [排错手册](docs/08-troubleshooting.md)
- 想看历史版本 → [版本记录](docs/07-changelog.md)

## 协作与记录（重要）

本仓库把「文档」当成项目的一部分，而不是附属品：

- 每次对话/AI 会话结束前，**必须**在 [`docs/sessions/`](docs/sessions/) 写一份会话记录（重点、修复、设计、踩坑、验证），规则见 [agent.md](agent.md)；
- 版本变化同步更新 [版本记录](docs/07-changelog.md)，新坑同步更新 [踩坑记录](docs/06-pitfalls.md)；
- 新增文档必须登记到 [文档地图](docs/README.md)。

**新开一个对话时，把这句话发给 AI：**

> 读 `agent.md`、`docs/00-status.md`、`docs/README.md` 和 `docs/sessions/` 最新一份记录，
> 先用 5 行给我接手确认，在我确认之前不要改代码。

这样下一次开新对话时，读三份文件就能无缝接手。

## 许可与致谢

- 代码：[GPL-3.0](LICENSE)；
- 图标为自制（`tools/make_subpage_icons.py` 生成），仅参考 iOS 视觉风格；Apple / iOS 是 Apple Inc. 的商标，本项目与其无关；
- 玻璃/折射实现的思路参考了 kyant0 的液态玻璃与 lattice 的实现，第三方声明见 [NOTICE.md](NOTICE.md)。

---

<sub>仓库名 `iLayoutSettings`（旧名 `iLyayot`），应用名 **iLayoutSettings**，包名 `com.Remo.ilayoutsettings`。</sub>
