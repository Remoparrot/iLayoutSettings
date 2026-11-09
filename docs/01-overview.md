# 项目概览

> 状态：**现行** · 对应版本：14.9 (84)

## 1. 一句话定位

**一个在「设置」进程内运行的 LSPosed 模块**：把澎湃 OS 3 设置首页重排成 iOS 的分组与顺序，并给二级页面补图标、把搜索栏移到底部——所有视觉元素仍由 MIUI 自己绘制。

它不是主题、不是启动器、不是自绘 App。卸载模块后设置立刻恢复原样。

## 2. 能改什么 / 不能改什么

| 能 | 不能（也是设计边界） |
| --- | --- |
| 调整 `SettingsFragment` 的 header 顺序与分组 | 替换整个 Settings 界面 |
| 往原生行里塞原生控件（`SlidingSwitch`） | 自绘卡片/列表（会导致 miuix 交互、无障碍、主题全失效） |
| 给 `Preference` 行补图标 | 改 MIUI 的点击逻辑与跳转目标 |
| 隐藏/移动原生搜索条，复用其点击 | 接管搜索、自建搜索页 |
| 强制/调整原生模糊开关 | 自绘模糊与阴影 |
| 在原生页面上加一层自有 View（玻璃、卡片背景） | 替换页面的 measure/layout 流程 |

一句话：**只做「重排、注入、显隐」，不做「重绘」。**

## 3. 兼容性

| 项 | 值 |
| --- | --- |
| 系统 | 澎湃 OS 3（HyperOS 3），Android 15 / 16 |
| 设置版本 | 随系统；代码里 hook 的是 `com.android.settings` 的类名 |
| 模块框架 | Xposed API 82；LSPosed 1.x / 2.x |
| minSdk / targetSdk | 29 / 36 |
| 作用域 | 仅 `com.android.settings` |

**换 ROM / 大版本升级会怎样**：所有 `findClassIfExists` 找不到类时只记日志、不崩溃；首页重排依赖 `SLOT_IDS` 里的资源 id 名，名字变了会走标题兜底，再不行就归到「其他」卡片。所以最坏情况是「部分生效」，而不是「设置打不开」。

## 4. 版本线（重要，别被 changelog 绕晕）

- **主线 = 14.9**：本仓库源码对应 14.9；`dist/IosSettingsLayout-14.8.apk` 是上一版发布包。功能是「重排 + 图标 + 底部搜索（液态玻璃）+ 卡片修正」。
- **15.x ~ 17.x 是历史实验分支**：液态玻璃（kyant / lattice shader）、GUI 参数页、动态折射等，记录保留在 [版本记录](07-changelog.md) 里，**没有并入本仓库主线**。
- 但源码里**残留了少量后续版本的类**（`GlassToggle`、`IntroCard`、`AboutIos` 等），它们没有被 `Hook` 调用，属于死代码。这件事写在 [状态](00-status.md#已知问题) 与 [踩坑](06-pitfalls.md) 里，清理前不要误以为它们是现行功能。

## 5. 目录结构

```
iLyayot/
├── module/                  模块工程（唯一源码）
│   ├── AndroidManifest.xml   版本号、xposed 元数据、作用域
│   ├── build.ps1             无 Gradle 构建脚本
│   ├── assets/xposed_init    入口类名
│   ├── res/                  图标、字符串、作用域数组
│   └── src/com/abradant/iossettings/*.java
├── dist/                    当前发布 APK
├── lib/xposed-api-82.jar    编译期依赖（Xposed API）
├── tools/make_subpage_icons.py  二级页图标生成
└── docs/                    文档，入口 docs/README.md
```

## 6. 相关工程（不在本仓库）

开发时还用过一些一次性工具与反向工程目录（`apk6ify`、`target-res`、`smali` 转储等）。它们体积大、可重新生成，**不入库**；需要时按 [构建指南](03-build.md) 重新准备工具链即可。
