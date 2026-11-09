# 主题飞行图标 + 搜索栏去色 + 顶栏模糊实时生效（2026-11-10）

状态：已完成
关联版本：15.2 → 15.3 (88)
关联提交：本次提交（见 `git log -1`）
参与者：用户 / DSH（deepseek-flash）

## 1. TL;DR

飞行模式图标改用设置自己的 `ic_airplane_mode_settings`，主题/深浅色自动解析，缺失才回退模块图标；
搜索栏只去掉 shader 表面色，**保留**主题的 `miuix_appcompat_search_mode_edit_text_bg_light/dark` 胶囊，浅色关闭内阴影与外阴影；
顶栏模糊修掉 LSPosed classloader 导致的 `ClassNotFoundException`，并改为按半径实时刷新，滑杆 0→300 已在滚动状态下验证。

## 2. 对话重点

- **用户要求（原话要点）**：
  1. 「飞行模式的图标变成设置中的 ic_airplane.png，主题里有，没有时用现在的，自动变化」；
  2. 「搜索栏不要叠加颜色，浅色时的搜索栏不要阴影」；
  3. 「顶栏的模糊没有变化」；
  4. 追加：「miuix_appcompat_search_mode_edit_text_bg_light/dark 也要保留」；
  5. 收尾：「弄完以后同步到 github 上」。
- **做出的决策**：
  - 用户纠正后，图标资源定为 `ic_airplane_mode_settings`，不是裸 `ic_airplane`；
  - 「不要叠加颜色」只删 shader 的 `surfaceColor/surfaceAlpha` 取色与 93% 混色，**不动**主题胶囊；
  - 「浅色不要阴影」同时处理两处：shader 内阴影置 0，外层 `SearchHost` 的 40dp elevation 置 0；
  - 顶栏模糊不换实现，只修 classloader 和刷新条件，保留 `MiuiBlurUtils`。
- **范围外但被提到的事**：无。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/.../AirplaneSwitch.java` | `applyPlaneIcon()` 先取 `com.android.settings:drawable/ic_airplane_mode_settings`，再回退 `ic_plane_*`；新增 `ICON_MODE` 与 `refreshPlaneIcon()` | 主题图标随主题/深浅色变化，不能继续硬编码模块橙色方块 |
| `module/src/.../SearchGlass.java` | `SURFACE_ALPHA` 0.93 → 0；删除 `surfaceColor` 取色、`fillSoftened`、`restoreNativeFill()`；浅色时 `shadowColor.a = 0` | 不再在原生胶囊上叠 shader 颜色；保留 `miuix_appcompat_search_mode_edit_text_bg_light/dark` |
| `module/src/.../BottomSearch.java` | `wrap.setElevation(40dp)` → 0 | 浅色下外阴影来自 wrapper elevation；去掉后胶囊仍保留 |
| `module/src/.../UiTweaks.java` | `callStatic` 增加 host `ClassLoader` 参数；`attachScrollBlur` 跟踪半径、每 500ms 轮询、优先 `scroll_headers`、child top 兜底 | `UiTweaks.class.getClassLoader()` 读不到设置类；旧逻辑滑杆变化被 `state` 短路；`mListView` 在部分版本 offset 恒 0 |
| `module/AndroidManifest.xml` / `MainActivity.java` | 15.2 (87) → **15.3 (88)** | 新构建版本 |
| `docs/*` / `README.md` | 版本、能力、截图、已知问题、changelog、本会话记录 | 仓库文档规范 |

## 4. 设计思路

- **问题本质**：
  - 图标问题是「显示哪一层资源」：模块自绘图标永远无法跟主题走，必须把解析权交回 `com.android.settings` 的资源系统；
  - 搜索栏问题是「谁在可见层」：主题胶囊在上层，清掉它会让搜索栏消失；正确做法是只去掉模块额外叠的那层色；
  - 顶栏问题是「反射调用的 classloader 与状态更新粒度」：不是 MIUI 模糊坏了，而是调用根本没进到 MIUI。
- **方案对比**：
  - 图标 A：模块内做深浅两套（旧方案）——简单，但不跟主题；B：读 Settings 资源 + 模块回退——选中；
  - 搜索栏 A：清掉主题 fill 做纯玻璃——视觉最干净，但用户明确要求保留；B：保留主题 fill、只去 shader 表面色——选中；
  - 顶栏 A：自绘模糊层——重且和 NestedHeaderLayout 打架；B：修 `MiuiBlurUtils` 调用 + 实时轮询——选中。
- **最终选择与取舍**：保留原生胶囊意味着 shader 去色只对半透明/胶囊下方区域可见，不是「完全透明玻璃」；这是用户明确要求的边界，记录在 `00-status.md` 已知问题。

## 5. 踩过的坑

### 坑：`MiuiBlurUtils` 明明在 APK 里，运行时却 `ClassNotFoundException`

- **现象**：日志 `static miuix.core.util.MiuiBlurUtils.clearBackgroundBlur failed: java.lang.ClassNotFoundException`；
  滑杆写读都正常，顶栏完全不变。
- **根因**：LSPosed 把模块类放在独立 classloader；`Class.forName(name, UiTweaks.class.getClassLoader())` 看不到设置进程的类。
- **解法**：`Class.forName(name, false, appBar.getContext().getClassLoader())`，失败再用 `XposedHelpers.findClassIfExists(name, loader)`。
- **如何避免**：模块里反射宿主类时，**永远传宿主 Context/Activity 的 classloader**，不要用模块类自己的。

### 坑：`mListView.computeVerticalScrollOffset()` 一直是 0

- **现象**：页面已经滚到「蓝牙/蜂窝」，日志还是 `top bar blur=false offset=0`。
- **根因**：HyperOS 3 首页真正滚动的是 `scroll_headers` RecyclerView；`SettingsFragment.mListView` 不一定是它。
- **解法**：优先 `activity.findViewById(com.android.settings:id/scroll_headers)`，回退 `mListView`；再用第一个 child 的 `getTop() < -4dp` 兜底。
- **如何避免**：不要假设字段名等于实际滚动容器；先 `uiautomator dump` 看 `scrollable="true"` 的节点。

### 坑：只比较「是否滚动」导致滑杆被吞

- **现象**：滚动后拖顶栏滑杆，回到设置毫无变化；日志停在第一次 `radius=...`。
- **根因**：`if (state[0] == s) return;`，半径变化时 `state` 没变，直接返回。
- **解法**：同时记录 `appliedRadius`，并每 500ms 轮询一次 prefs（原来只在 `OnScrollChanged` 读）。
- **如何避免**：凡是「可调参数 + 状态机」的组合，状态键必须包含参数值，不能只含布尔状态。

## 6. 验证与证据

```powershell
python tools\check_agsl.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk ...\jdk17.0.21_0 -BuildTools ...\bt\android-14 `
  -AndroidJar ...\ios-settings-mod\.tools\platform36\android-36.1\android.jar
adb install -r dist\IosSettingsLayout-15.3.apk
```

- 结果：两个 shader 离线编译 OK；`versionCode='88' versionName='15.3'`；`xposed_init/module class/reorder` 均为 True。
- 产物：`dist/IosSettingsLayout-15.3.apk`，207,753 B，sha256 `563AA37E88EC09A4D2052CB9B069DDC6312301C49967F57BD71DED2D04FB2840`。
- 日志：
  - `airplane icon set: settings:ic_airplane_mode_settings size=89`（浅色、深色各一次）；
  - `top bar blur=true offset=50 radius=105 set=true radiusResult=true`；
  - 同一滚动位置改滑杆后 `radius=0 (off)` → `radius=300 set=true`，截图顶部由清晰变重模糊。
- 截图：`docs/assets/search-glass-15.3-light.png`、`search-glass-15.3-dark.png`、`topbar-blur-15.3.png`、`gui-15.3.png`。
- **未验证的部分**：第三方主题替换 `ic_airplane_mode_settings` 的情况；120Hz 中间帧、分屏/平板。

## 7. 未完成 / 下一步 / 风险

- 未完成：第三方主题图标包实测；15.3 Release 尚未创建。
- 建议下一步（按顺序）：发 15.3 Release；在第三方主题上回归图标；固定 keystore 后统一发布包。
- 风险：主题胶囊在上层，shader 去色的观感在深色/半透明主题下可能不明显；若用户要「纯玻璃」，需要改 `SearchGlass` 与 `widget_frame` 的层级，而不是删主题背景。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（架构 / 设计 / 坑 / 构建 / 安装 / 路线图）
- [x] 若发版：`docs/07-changelog.md` 顶部已加一节
