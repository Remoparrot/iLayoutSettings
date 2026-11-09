# 参数界面 + 实心玻璃 + 原生开关归位（2026-11-10）

状态：已完成（顶栏滑杆的滚动截图待补）
关联版本：15.0 → 15.2 (87)
关联提交：待补
参与者：用户 / DSH（deepseek-flash）

## 1. TL;DR

把 15.0 的自绘开关换回 MIUI 原生 `SlidingSwitch`，只平移 `widget_frame` 修正位置（真机 945 vs 945 对齐）；
搜索栏玻璃删掉 rim 高光、折射降到 12dp/8dp、原生 fill 拉到 `0xFF`，并在 shader 里混 93% 主题表面色做成实心磨砂；
新增应用内参数界面（模糊/折射/厚度/顶栏模糊），桌面图标恢复，跨进程参数改用 `PrefsProvider`。

## 2. 对话重点

- **用户要求（原话要点）**：
  1. 「开关不是重新画，而是在原来的情况下改变位置」；
  2. 「搜索栏的玻璃光边删了」；
  3. 「搜索栏图片的不透明度拉满」——追问后用户指名 `miuix_appcompat_search_mode_edit_text_bg_light.9.png`；
  4. 「折射调小」；「用 kyant 最新的 iOS27 样式的玻璃库」；
  5. 追加：「给应用加一个 gui 界面，桌面图标不再隐藏，可以调搜索栏模糊度、折射力度、顶栏模糊度」。
- **决策**：
  - 开关不再用 `GlassToggle`，而是移动父容器 `widget_frame` 的 translation —— 因为 HyperiOS 的画在 frame 坐标系里；
  - 玻璃「不透明」理解为**整条 bar 实心**，做法是 shader 内混主题表面色（93%），而不是把原生 9-patch 改成不透明
    （后者会把玻璃完全盖住，折射/内阴影就没意义了）；
  - 参数传递不走 XSharedPreferences（本机读不到），走导出的 ContentProvider。
- **范围外但被提到**：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../AirplaneSwitch.java` | 删除 `injectGlassToggle` 调用；`recenter()` 改为移动 `widget_frame` 的 `translationY`；新增 `frameOf()` / `toggleChildren()`；日志补 frame 子视图清单 | HyperiOS 的 `SlidingSwitch.onDraw` 被替换并在首帧缓存 box，`setTranslationY` 与 `offsetTopAndBottom` 都推不动，只有父容器的 transform 能带走它 |
| `module/src/.../SearchGlass.java` | 删 rim 全部 uniform/代码；折射默认 12/8；`surfaceColor`+`surfaceAlpha`；2.0.1 的提前返回分支也混表面色；prefs 改为 `Prefs.getFloat(context,...)` + 500ms 轮询（放在 `OnPreDraw`） | 光边与半透明是用户明确要去掉的；早退分支不混色会导致「只有边缘实心、中间透」 |
| `module/src/.../Prefs.java` | 新增 `snapshot(Context)` / `getFloat(Context,...)` / `getInt(Context,...)`，400ms 缓存；旧 `XSharedPreferences` 路径保留为回退 | 设置进程读不到模块的 prefs 文件 |
| `module/src/.../PrefsProvider.java` | 新增导出 ContentProvider，`call("get")` 返回全部 prefs 的 Bundle | 跨进程读参数的可靠通道 |
| `module/src/.../MainActivity.java` | 重写为参数界面：4 个 SeekBar + 恢复默认 + 深/浅色 + insets；写 `ios_settings` | 用户要求应用内 GUI |
| `module/AndroidManifest.xml` | 15.2 / 87；`MAIN`+`LAUNCHER`；`<provider>`；`xposedsharedprefs` | 桌面图标 + 参数通道 |
| `module/src/.../UiTweaks.java` | 顶栏模糊改用 `Prefs.getInt(context,...)`，日志带 `radius=` | 同上 |

## 4. 设计思路

- **问题本质**：HyperiOS 把 MIUI 开关的「绘制」换成了自己的实现，并缓存了首帧看到的矩形；任何在开关自身上的位移都不影响那份缓存。
- **方案对比**：
  - A：继续自绘开关（15.0 的 `GlassToggle`）—— 完全可控，但用户明确否掉，且和 HyperiOS 的玻璃质感不一致；
  - B：改开关的 LayoutParams / `offsetTopAndBottom` —— 实测无效（旋钮中心一直 975）；
  - C：平移 `widget_frame` —— canvas 随父容器移动，hook 画的开关跟着走；实测有效（945）。
- **最终选择与取舍**：C。代价是 frame 会向上溢出该行 30px，需要 `clipChildren=false`（已有）。

## 5. 踩过的坑

### 坑：`surfaceAlpha=0.93` 只让边缘变实心，中间还是透的

- **现象**：bar 边缘 `(53,53,53)`、中部 `(73,73,75)`，页面内容仍能透出来。
- **根因**：kyant 2.0.1 的 `RoundedRectRefractionShaderString` 在 `-sd >= refractionHeight` 时直接 `return content.eval(coord)`；我照搬了早退，却没在早退分支里混 `surfaceColor`。
- **解法**：早退分支也 `mix(inside, surfaceColor.rgb, surfaceAlpha)`（`LENS_ONLY_AGSL` 同样处理）。
- **如何避免**：改 shader 的每个 `return` 都要问一句「这个分支有没有漏掉新加的合成层」。

### 坑：XSharedPreferences 在 LSPosed 下不可读

- **现象**：`glass prefs: file=/data/misc/apexdata/.../ios_settings.xml readable=false keys=[] blur=1.0`，滑杆怎么调都不生效。
- **根因**：镜像文件 `-rw-rw----`、属主是模块自己的 UID；设置进程是另一个 UID。加了 `xposedsharedprefs` 元数据也没用。
- **解法**：导出 `PrefsProvider`，`ContentResolver.call()` 拉 Bundle，400ms 缓存。
- **如何避免**：跨进程参数不要假设文件可读；先打一条 `file/canRead/keys` 的日志再写逻辑。

### 坑：PowerShell `Set-Content` 把 UTF-8 源码写坏

- **现象**：`SearchGlass.java` 里 `→` 变成 `鈫?`、中文注释乱码，`git diff` 显示整段注释被改。
- **根因**：Windows PowerShell 5.1 的 `Get-Content` 按 ANSI（GBK）解码 UTF-8 文件，再 `Set-Content -Encoding UTF8` 写回。
- **解法**：用 `edit` 工具或 Python 显式 `encoding='utf-8'` 改文件；已用脚本把注释里的 `→`/`×` 统一成 ASCII。
- **如何避免**：不要用 `pwsh` 做文本替换；至少不要 `Get-Content -Raw | Set-Content`。

## 6. 验证与证据

```powershell
python tools\check_agsl.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk ...\jdk17.0.21_0 -BuildTools ...\bt\android-14 `
  -AndroidJar ...\ios-settings-mod\.tools\platform36\android-36.1\android.jar
adb install -r dist\IosSettingsLayout-15.2.apk
adb shell am force-stop com.android.settings; adb shell am start -a android.settings.SETTINGS
```

- 结果：两个 shader 离线编译通过；构建 `versionCode='87' versionName='15.2'`，`launchable-activity` 为 `MainActivity`。
- 日志：
  - `airplane switch recentered by -30 (frame translation)`，随后 `airplane geometry: ... kids: [0] TextView 0x61 [1] SlidingSwitch 191x95 top=61`
  - `search glass surface=#ff000000 veilAlpha=0xff mix=0.93`、`native fill alpha=0xFF`
  - `glass prefs via provider: blur=11.0` → `glass tuning: blur=11.0`；改成 3dp 后 `glass tuning: blur=3.0`（无需重启设置）
- 截图量像素：旋钮中心 Y=945 vs 标题中心 Y=945；bar 整条 `(53,53,53)` 均匀。
- **未验证**：顶栏滑杆的滚动截图、浅色主题、120Hz 中间帧；设备随后锁屏 + 防误触模式。

## 7. 未完成 / 下一步 / 风险

- 未完成：`topbar_blur` 实际套用半径的截图；浅色主题颜色；`GlassToggle` 类尚未删除。
- 建议下一步：解锁后滚动一次补 `top bar blur=... radius=...` 截图 → 发 Release → 清理遗留类。
- 风险：`PrefsProvider` 是导出组件（无权限保护），只能读到三个 UI 数字；若在意可加自定义 signature 权限。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] `docs/07-changelog.md` 顶部已加 v15.2
- [ ] `docs/02-architecture.md` / `docs/04-install.md` 的版本号引用待同步
