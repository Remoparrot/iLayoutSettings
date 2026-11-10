# 顶栏近乎全透明 + 深色灰色发丝线（2026-11-10）

状态：已完成
关联版本：16.2 (93) → **16.3 (94)**
关联提交：<待发版提交后补>
参与者：用户 / DSH bot（deepseek）

## 1. TL;DR

按用户要求把顶栏默认改成 **5% 模糊 + 2% 透明度**（近乎全透明），并把深色模式发丝线从
16.2 的白 15% 换成**中性灰 30%**——它在近乎全透明的黑栏上终于读得出来。真机深浅色各拍一张、
`pm clear` 复验默认值后出 16.3，提交 + tag + GitHub Release。

## 2. 对话重点

- **用户要求**（原话要点）：「顶栏模糊默认改到 5%，透明度改到 2%，深色模式时的顶栏也有线，
  是灰色半透明的，产出 16.3，传 GitHub 和 Release」。
- **做出的决策**：
  - 只改 `Prefs.DEFAULT_TOPBAR_BLUR_PCT` / `DEFAULT_TOPBAR_ALPHA` 两个常量，**不迁移已有安装**：
    存过的 `topbar_blur` / `topbar_alpha` 继续生效，用户点「恢复默认值」才回到 5 / 2；
  - 深色线不用「白色 xx%」继续叠，而是中性灰 `0x4DAAAAAA`（30% 灰），因为线要压在
    **2% 透明度的黑栏**上，白 15% 实际只到 `#262626`，跟黑底没区别；
  - 浅色线维持 16.2 的黑色 12%，没动。
- **范围外但被提到的事**：无（用户没提删除旧 Release、也没要求改搜索栏玻璃）。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/src/com/Remo/ilayoutsettings/Prefs.java` | `DEFAULT_TOPBAR_BLUR_PCT` 30 → **5**；`DEFAULT_TOPBAR_ALPHA` 45 → **2**（注释改成 16.3 语义） | 用户要「近乎全透明」的顶栏，16.2 的 30/45 仍是淡磨砂 |
| `module/src/com/Remo/ilayoutsettings/UiTweaks.java` | 深色 `dividerColor` `0x26FFFFFF` → **`0x4DAAAAAA`** | 白 15% 在 2% 黑栏上等于没有线；中性灰 30% 在黑底与亮模糊内容上都可读 |
| `module/AndroidManifest.xml` | versionCode 93 → **94**、versionName 16.2 → **16.3**（注释头同步） | 发版版本号唯一来源 |
| `tools/release.ps1` | Release 正文「本版内容」整段换成 16.3 | 发版说明必须与包一致 |
| `README.md` / `docs/00-status.md` / `01`~`09` / `docs/README.md` / `agent.md` | 版本号、APK 名、SHA、默认值、深色线色值 | 文档跟随发布 |

## 4. 设计思路

- **问题本质**：16.2 把「可见的模糊」修对了，但默认值仍是 30% / 45% 的淡磨砂；
  用户要的是 iOS 那种「滚动后基本就是内容 + 一条线」。透明度降到 2% 后，
  顶栏底色几乎消失，**线成了唯一的视觉边界**，所以线必须够读。
- **方案对比**：
  - A：深色线继续用白 15%（16.2 原样）——和 iOS 的 separator 数值一致，
    但 1px 白 15% 叠在 `#000` 栏上只有 `#262626`，16.3 的低透明度下「等于没线」。
  - B：深色线用更亮白（30–40%）——够亮，但在亮模糊内容（图标、缩略图）上会过曝成白条。
  - C（选中）：中性灰 `0x4DAAAAAA`——在纯黑上得到 `(58,58,59)`，在亮内容上仍是一条灰线，
    两边都不突兀；浅色继续保持黑 12%。
- **最终选择与取舍**：选 C，牺牲「跟 iOS 数值完全一致」，换来「深色模式下确实看得见」。
  线仍然只在折叠顶栏出现后显示（`isHeaderOpen()` 门控），没有改成常显。

## 5. 踩过的坑

### 坑：改默认值不影响已经装过的设备

- **现象**：装完 16.3 后打开设置仍是旧观感。
- **根因**：`topbar_blur` / `topbar_alpha` 一旦被滑杆写过就存在 `shared_prefs`，
  读取时永远优先于 `DEFAULT_*`。
- **解法**：本次用户的设备里存的就是 5 / 2（之前手动拖过），所以视觉立刻正确；
  要验证「全新安装的默认值」必须 `adb shell pm clear com.Remo.ilayoutsettings` 再重开设置。
- **如何避免**：改默认值后，验证清单里固定加一条 `pm clear` 复验；
  文档里也写明「默认值只影响没存过值的安装」。

### 坑：以为深色模式「没有线」，其实有线但看不出来

- **现象**：16.2 在深色下截图，肉眼看不出分隔线。
- **根因**：不是没画——取像素后 `y=329–330` 是 `(49,60,53)`，比周围 `(14,27,18)` 亮，
  只是白 15% 的黑栏上对比太小。
- **解法**：先取像素确认存在，再只改颜色，而不是去改显示逻辑。
- **如何避免**：判断「线有没有」用 `PIL` 取栏底像素，别只靠缩略图目测。

### 坑：`Get-Content` 看中文文档乱码、据此编辑会写坏文件

- **现象**：PowerShell 输出 `璁捐鎬濊矾` 之类的乱码。
- **根因**：控制台代码页是 GBK，文件本身是 UTF-8（不是文件坏了）。
- **解法**：改用 read 工具读原文再 `edit`，不要复制终端输出当 `old_string`。
- **如何避免**：凡是中文 old_string，一律来自 read 工具的输出。

## 6. 验证与证据

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -AndroidJar 'C:\Users\AbradantHawk\Documents\deepseek-harness\default-workspace\ios-settings-mod\.tools\platform36\android-36.1\android.jar'
adb install -r dist\iLayoutSettings-16.3.apk
adb shell pm clear com.Remo.ilayoutsettings      # 复验「全新安装」默认值
adb shell am force-stop com.android.settings; adb shell am start -n com.android.settings/.Settings
adb shell input swipe 800 940 800 340 300        # 折叠顶栏
adb logcat -d -s iLayoutSettings:*
```

- 结果：构建 `version : 16.3 (94)`、`xposed_init : True`、`module class : True`、`reorder : True`；
  安装 `Install command complete in 526 ms`。
- 日志：`top bar blur=true offset=69 blur=5% radius=8dp alpha=2% set=true radiusResult=true`、
  `native header blur applied: 8 dp`（清空数据后仍是 5% / 2%）。
- 像素（1280×2772，栏底 y=329–330）：
  - 深色：`(58,58,59)`，上方黑栏 `(11,11,11)`、下方卡片 `(28,28,30)`；
  - 浅色：`(222,222,223)`，上方栏底 `(253,253,254)`、下方卡片 `(255,255,255)`。
- 产物：`dist/iLayoutSettings-16.3.apk`，**236,593 B**，
  SHA-256 `ED0C383E5A91DC4F054ED3C362AAF4AAE3F44506CE80F4078CD3C36439402873`。
- **未验证的部分**：第三方主题 / 非默认图标包下的线色；平板/分屏。

## 7. 未完成 / 下一步 / 风险

- 未完成：无（本版目标全部上机验证）。
- 建议下一步（按顺序）：观察深色线下第三方主题是否偏亮/偏暗；接 GitHub Actions 自动发版；
  清理死代码后对比 dex 体积。
- 风险：`0x4DAAAAAA` 是固定灰，如果将来系统栏底色改成浅灰（非纯黑），对比会变低；
  届时按 `06-pitfalls.md` 的「深色模式顶栏看不到线」一行重新取色。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（架构 / 坑 / 设计 / 构建 / 安装 / 排错 / 路线图 / 地图）
- [x] 若发版：`docs/07-changelog.md` 顶部已加一节
