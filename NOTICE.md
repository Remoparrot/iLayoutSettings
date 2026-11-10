# 第三方声明

本仓库包含或参考了以下第三方内容。分发时请保留本文件。

## 1. Xposed API

- 文件：`lib/xposed-api-82.jar`
- 来源：Xposed Framework API（rovo89 / LSPosed 维护的 API 82）
- 许可：Apache License 2.0
- 用途：编译期依赖，不随 APK 分发其实现（目标设备由框架提供）

## 2. 视觉与实现思路参考

- **kyant0 液态玻璃**（[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)，Apache-2.0）：`SearchGlass` 的折射/高光算法按其 `backdrop` 2.0.1 的 `Shaders.kt` / `HighlightStyle` 移植为 Java + AGSL；
- **kyant0 Shapes**（[Kyant0/Shapes](https://github.com/Kyant0/Shapes)，Apache-2.0）：16.6 的 G2 连续曲率胶囊轮廓来自其 `ContinuousCurvatureRoundedRectangleCornerBuilder`，胶囊分支的 20 个贝塞尔控制点按同一公式离线计算后硬编码；
- **lattice**：15.x–17.x 实验分支曾反编译并复用其 `LiquidGlassView` / `SearchBottom`（据开发记录已获原作者同意），**该部分未包含在本仓库主线**；主线 `SearchGlass` 为独立实现。

上述移植只使用其公开源码中的数学与常量，未引入其运行时库；许可文本见各自仓库。若权利人有异议，请开 Issue，我们会调整或移除。

## 3. 图标

- `module/res/drawable-xxhdpi/ic_sub_*.png`、`ic_plane_*.png`、`ic_launcher.png` 为项目自制，由 `tools/make_subpage_icons.py` 生成；
- 视觉风格参考 iOS，但未使用 Apple 的图标资源文件；
- **Apple、iOS、iPhone 是 Apple Inc. 的商标**。本项目与 Apple Inc. 无任何关联，也未获得其背书。

## 4. 系统组件

模块运行时通过反射调用 Xiaomi/HyperOS 的 `com.android.settings`、`miuix.*` 类。这些代码不属于本仓库，也不随本仓库分发；本仓库仅在运行时 hook 它们，属于互操作性用途。

## 5. 许可

本仓库自有代码以 [GPL-3.0](LICENSE) 发布。第三方内容仍遵循其各自许可；如与 GPL-3.0 冲突，以第三方许可为准。
