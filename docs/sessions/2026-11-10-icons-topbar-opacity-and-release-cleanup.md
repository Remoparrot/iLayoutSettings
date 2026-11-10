# 指定图标 + 顶栏透明度/发丝线 + 自动清理（2026-11-10）

状态：已完成
关联版本：16.1 → 16.2
关联提交：a6648c0（Release v16.2）

## 1. TL;DR

耳机 / iCloud 改用用户提供的主题资源包里的 `ic_sub_airpods.png`、`ic_cloud.png`；
参数页新增「顶栏 · 透明度」，顶栏底部只在滚动、折叠栏出现后显示发丝线；
同时修好顶栏模糊滑杆（改的是 `NestedHeaderLayout` 自己的原生模糊，不是被盖住的那层）。
构建成功即清理 `dist/` 旧 APK，`release.ps1` 默认自动提交/推送并发 Release。16.2 (93) 已在 25053RT47C 实测。

## 2. 对话重点

- 用户提供 `D:\下载\com.android.settings`（主题资源包，含 `res/`、`nightmode/`、`theme_values.xml`），要求：
  - 连耳机时，耳机图标用包里的 `ic_sub_airpods.png`；
  - iCloud 用包里的 `ic_cloud.png`；
  - 顶栏加透明度调节；
  - 顶栏底部加一条「像图中的黑色半透明线」；
  - 自动删除以前的版本，自动更新 GitHub 和 Releases；
  - 手机测试完把截图自动删掉。
- 第二轮补充：
  - 顶栏模糊「还是调不了」；
  - 半透明线要在滑动、折叠顶栏出现时才出现（大标题展开时不显示）；
  - 折射默认 19、折射厚度 12，模糊的自定义 dp 要支持小数；
  - 第三轮：顶栏模糊的自定义值改成百分比显示。
- 明确决策：
  - 图标以**模块内置**为准，设置包同名 drawable 只做兜底。原实现是 settings 优先，
    主题里若有同名资源会覆盖用户点名要的文件。
  - 顶栏发丝线用 `ViewOverlay + ColorDrawable`，不加子 View，避免 padding / 布局 / 点击影响。
  - 模糊滑杆不能再只改 `ActionBarContainer` 的背景模糊；必须改 `NestedHeaderLayout` 的
    `mBlurEffect` 并 `refreshBlur()`，否则用户看到的是主题固定半径。
- 范围外但被提到：GitHub 旧 Release 是否要一并删；本次只清本地 `dist/`，历史仍留在 Releases。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/res/drawable-xxhdpi/ic_sub_airpods.png` | 替换为资源包里的 128×128 灰色 AirPods 圆角图标 | 用户指定文件；原内置图标是扁平白色耳机 |
| `module/res/drawable-xxhdpi/ic_cloud.png` | 替换为资源包里的 85×85 白底蓝云图标 | 同上 |
| `HeaderData.cardDrawable()` | 改为 `moduleDrawable` 优先，settings 同名资源兜底；日志区分 `<- module (supplied theme)` / `<- settings` | 保证主题覆盖时仍是用户指定的图 |
| `Prefs` | 新增 `topbar_alpha`（0–100，默认 45）、`DEFAULT_TOPBAR_ALPHA`；`DEFAULT_REFRACTION_AMOUNT` 8 → 19；`topbar_blur` 0–100%（默认 30） | 透明度可调；低模糊+低透明度参考图效果；折射默认值按用户要求 |
| `MainActivity` | 新增「顶栏 · 透明度」滑杆（%）；搜索栏模糊改成 0.1dp 步进；顶栏模糊改成 0–100%（默认 30%）；标题/版本改读 manifest，去掉硬编码 `iLyayot` | 用户要求；旧滑杆整数步进选不到 1.5dp；150dp 以上会撞 MIUI 400px 上限，且百分比比 dp 直观 |
| `UiTweaks` | 顶栏底色按 `topbar_alpha` 计算 alpha；发丝线只在 `scrolled && !isHeaderOpen()` 时显示；把 `topbar_blur` 写进 `mBlurEffect` 并 `refreshBlur()` | 透明度、线随折叠栏出现、模糊真正可调 |
| `ForceBlur` | hook `MiuiBlurUiHelper.setBlurParams`，只对 parent 是 `NestedHeaderLayout` 的 `mViewApplyBlur` 替换半径 | 原生顶栏模糊半径来自主题 `Blur.ExtraHeavy`，不替换就一直固定 |
| `module/build.ps1` | 构建成功后删除 `dist/` 里除本次 APK / idsig 外的 `*.apk`、`*.idsig`；`-KeepOldVersions` 可关闭 | 自动删除以前的版本，dist 只留当前包 |
| `tools/release.ps1` | 默认 `git add -A` → commit → push 当前分支 → tag → push tag → Release；`-SkipCommit` 可关；Release 正文更新到 16.2 | 一条命令自动更新 GitHub 和 Releases，tag 不再指向缺改动的旧 commit |

## 4. 设计思路

- **图标优先级为什么反过来**：16.1 的「settings 优先」是为了跟随 ROM/主题；但这次用户点名要
  资源包里的两张图。主题资源包本身就会替换 `com.android.settings` 的同名 drawable，
  继续 settings 优先等于把选择权交给运行环境。模块内置优先才能保证跨主题一致。
- **线为什么用 overlay**：`ActionBarContainer` 高度会随大标题折叠变化（513px → ~210px），
  在它底部画线最自然。加子 View 会受 padding / `clipChildren` 影响，且可能被列表回收；
  `getOverlay()` 的 `ColorDrawable` 按 `(0, h-thickness, w, h)` 画，永远贴着实际底边，
  不参与布局和触摸。
- **线的显示条件**：只用 `scroll offset > 4dp` 会在 513px 高的大标题还在时就画线；
  `NestedHeaderLayout.isHeaderOpen()` 为 true 表示大标题仍展开，取反后才画，和 iOS 的
  「滚动后导航栏出现才出现分隔线」一致。
- **模糊为什么之前调不了**：`MiuiBlurUtils.setBackgroundBlur(actionBar, radius)` 确实返回 true，
  但设置页实际可见的模糊来自 `NestedHeaderLayout.mOverBgView`，由 `MiuiBlurUiHelper` 用
  `MaterialDayNightConfig` 的固定主题半径绘制。只改 action bar 等于在原生模糊下面加了一层，
  被盖住。现在 hook 原生 helper 的 `setBlurParams`，并按 `mBlurEffect(dpi) * density` 应用，
  滑杆才真正改变用户看到的模糊。
- **模糊改成百分比**：`setBackgroundBlur` 在 `MiuiBlurUtils` 里把半径 cap 到 400px，2.75x 屏
  约 145dp。16.1 的 0–300dp 后半段全是饱和区，拖了没反应；现在滑杆 0–100%，
  内部按 `percent * 150 / 100` 映射成 dp，全程有效。默认 **30%（45dp）+ 透明度 45%**，
  对应参考图那种低模糊、低透明度的淡磨砂；旧值（>100）在读取时自动按 150dp 折算
  （110dp → 73%），升级用户不会突然归零。
- **自动发版的边界**：只清本地 `dist/`，不删 GitHub 旧 Release。旧 APK 是不可再生的发布物，
  误删无法恢复；用户没有明确要求删远端 Release，先在会话里标注。

## 5. 踩过的坑

1. **`R.string.app_name` 编译不过**
   - 现象：`javac` 报「找不到符号 R」。
   - 根因：`build.ps1` 只把 `module/src` 传给 javac，aapt2 生成的 `R.java` 在 `build/gen`，从未参与编译；旧代码也从未引用 `R`。
   - 解法：标题改用 `getApplicationInfo().loadLabel(getPackageManager())`，版本用 `getPackageInfo().versionName`。
   - 如何避免：要么把 `build/gen/**/R.java` 加进 sources，要么在 Java 里完全不碰 `R`。

2. **`pm clear` 没有清掉 SharedPreferences**
   - 现象：执行 `pm clear com.Remo.ilayoutsettings` 返回 Success，但 `PrefsProvider` 仍返回旧的
     `refraction_height=13.0`。
   - 根因：MIUI 上 `pm clear` 后进程/文件状态与预期不一致（本次未继续深挖，不把它当可靠重置手段）。
   - 解法：打开模块参数页点「恢复默认值」，`recreate()` 后由代码写入新默认值；`content call` 验证。
   - 如何避免：测试默认值时不要依赖 `pm clear`，直接走应用内重置并读 provider。

3. **第一次构建后 `dist/` 旧包全部被删**
   - 现象：构建日志出现 `[prune] removing 18 old artifact(s)`，其中包括 git 跟踪的 14.8–16.1。
   - 根因：`build.ps1` 新增的自动清理按设计删除除当前包外的所有 `*.apk`。
   - 解法：这是需求「自动删除以前的版本」；旧包仍在 GitHub Releases，提交时记录删除。需要保留时加 `-KeepOldVersions`。
   - 如何避免：改构建脚本前先看清 `git status`，确认 dist 是「下载目录」而不是归档目录。

4. **`input swipe` 经常不滚动**
   - 现象：`adb shell input swipe` 有时完全不触发 RecyclerView 滚动，日志无 `top bar blur=true`。
   - 根因：起点/速度落在不可滚动区域或首帧未就绪；不是模块逻辑。
   - 解法：统一用 `input swipe 640 2200 640 700 400`，并等 1–2s 再截图；必要时先重启设置。
   - 如何避免：自动化截图前先看日志的 offset 是否变化，不要只看截图。

## 6. 验证与证据

命令（原样）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -AndroidJar C:\...\ios-settings-mod\.tools\platform36\android-36.1\android.jar
D:\platform-tools\adb.exe install -r dist\iLayoutSettings-16.2.apk
D:\platform-tools\adb.exe shell content call --uri content://com.Remo.ilayoutsettings.prefs --method get
D:\platform-tools\adb.exe shell am force-stop com.android.settings
D:\platform-tools\adb.exe shell am start -n com.android.settings/.MiuiSettings
D:\platform-tools\adb.exe logcat -d | Select-String 'iLayoutSettings'
```

构建 / 签名：

```
package: name='com.Remo.ilayoutsettings' versionCode='93' versionName='16.2'
sdkVersion:'29'
targetSdkVersion:'36'
dex version : 039
xposed_init : True
module class: True
reorder     : True
size   : 236593 bytes
sha256 : 2D2F068C8DEC1A5D5B982F6347F71580F6C4800B8BE89F4286C2836E945AEC3B
```

真机 25053RT47C / HyperOS 3 / Android 16（2026-11-10 19:14–19:25）：

- 图标：`card icon ic_sub_airpods <- module (supplied theme)`、
  `card icon ic_cloud <- module (supplied theme)`；截图中耳机行是灰色圆角 AirPods，
  iCloud 行是白底蓝云。
- 顶栏线：
  - 首页顶部截图 y≈300–339 全为 `(242,242,247)`，**没有线**；
  - 滚动后截图 y=329–330 为 `(213,213,218)`，**有线**；大标题折叠后线随
    `ActionBarContainer` 高度一起移动。
- 透明度：`topbar_alpha=17` 时日志 `alpha=17%`，顶栏明显透出下方内容；
  默认 `45` 时日志 `alpha=45%`，呈参考图的淡磨砂。
- 模糊：
  - 旧行为只改 action bar，滑块从 110 拖到 300 肉眼无变化；
  - 现在日志 `top bar blur=true offset=65 blur=30% radius=45dp alpha=45%`，
    `native header blur applied: 45 dp`；默认状态就是参考图的低模糊淡磨砂；
  - blur 0 时能看见「通知」文字透出，blur 100% 时顶栏均匀，肉眼可分辨；
  - 拖到 50% 后 provider 为 `topbar_blur=50`，点恢复默认回到 `30`；
  - `setBlurParams` hook 日志：`hooked MiuiBlurUiHelper.setBlurParams (header radius)`。
- 默认值：重置后 provider 返回
  `{refraction_amount=19.0, refraction_height=12.0, glass_blur=1.0, topbar_blur=30, topbar_alpha=45}`。
- 清理：手机 `/sdcard/Download/iLayoutSettings/` 为空；`settings get global ilayout_fake_headset` 为 `null`；本地测试截图目录已删除。
- 发布：`https://github.com/Remoparrot/iLayoutSettings/releases/tag/v16.2`
  （附件 `iLayoutSettings-16.2.apk`，236,593 B，sha256 与上面一致；tag 指向 `a6648c0`）。
- **未验证**：深色模式下发展开态的分隔线对比度；其他 HyperOS 3 机型上 `NestedHeaderLayout$2` 的类名是否一致（软失败，只影响模糊半径）。

## 7. 未完成 / 下一步 / 风险

- 未做：删除 GitHub 旧 Release（本次只清本地 `dist/`）；若确认要删远端，再加 `release.ps1 -PruneReleases`。
- 未做：把 `build/gen/**/R.java` 加进 javac，未来若要在 Java 里引用资源仍需补。
- 风险：`MiuiBlurUiHelper.setBlurParams` / `NestedHeaderLayout.isHeaderOpen()` 是 MIUI 内部 API，
  ROM 升级改名时 hook 会软失败；失败时模糊回落到「不可调但可见」，线回落到只按滚动显示（代码有 null 兜底）。
- 下一步：真机看默认主题/深色主题下的线对比度；需要更淡就把 `UiTweaks` 里 `0x1F000000` 降到 `0x14000000`。
