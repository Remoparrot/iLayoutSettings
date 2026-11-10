# 排错手册

> 状态：**现行** · 对应版本：15.5 (90)
> 用法：先按「现象」定位，再看「下一步」。每个问题都给了判断依据，不要凭感觉重装。

## 0. 通用排查顺序

```
1. 模块启用了吗？作用域只有 com.android.settings 吗？
2. 强制停止设置了吗？          adb shell am force-stop com.android.settings
3. 日志里有没有 hooks installed？  adb logcat -s IosSettings:V
4. 哪一行日志缺失？→ 对照 docs/04-install.md 的日志表
5. 还不行 → 抓日志 + 截图 + 机型版本，按 04-install 第 7 节反馈
```

## 1. 完全没效果

| 判断依据 | 结论 | 处理 |
| --- | --- | --- |
| 日志无任何 `IosSettings` | 模块没进进程 | 检查 LSPosed 启用状态、作用域、是否重启过设置 |
| 有 `hooks installed` 但界面没变 | hook 跑在别的进程/页面 | 确认打开的是系统「设置」而不是第三方设置页 |
| 有 `hooks installed` 但无 `reordered` | `SettingsFragment#buildAdapter` 没匹配上 | ROM 版本差异，抓日志反馈 |
| 装了两个同名模块 | 旧模块抢先生效 | 卸载旧包，只留一个 |

## 2. 首页顺序不对 / 某些入口不见了

- 看日志 `no header found for slot ids: xxx`：说明这台机器上没有这个 entry，**属正常**，不是 bug；
- 看日志 `reordered N headers`：N 是否合理（一般 20+）；
- 某些入口被归到「其他」卡片：说明 id 与标题都没匹配上，需要补 `HeaderData.SLOT_IDS` 或 `SLOT_TITLES`；
- 顺序对但卡片不对：`groupId` 没写进去，见 [坑 2](06-pitfalls.md#坑-2卡片分组失效groupid-没写进去)。

## 3. 底部搜索栏

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 完全没有 | stub inflate 失败，且 fallback 也没显示 | 日志找 `bottom search installed` / `fallback pill` |
| 位置太低/被手势条挡 | 导航栏 inset 没拿到 | 日志看 `bottom bar: ... rootH=...`；确认 `bottomMargin = 14dp + nav` |
| 挡住最后一张卡片 | 列表 padding 没加上 | 同上，检查 `listView.setPadding` 分支 |
| 点它没反应 | anchor 为空（未进搜索态） | 日志看 `bottom bar: vis=...`；确认 `mAnchorView` 已赋值 |
| 搜索时还显示 | ActionMode hook 没生效 | 日志看 `onCreateActionMode` 相关行 |
| 玻璃里有重影 | `capturing` 没跳过自身 | 见 [坑 4](06-pitfalls.md#坑-4底部搜索栏把自己拍进玻璃里) |

## 4. 控制中心行没有开关

1. 日志是否出现 `airplane switch attached to 控制中心 row`；
2. 没有 → `widget_frame` 没找到，或 `SlidingSwitch` 创建失败（日志会有异常堆栈）；
3. 有但位置偏 → 见 [坑 B 表](06-pitfalls.md#b-布局与视觉) 的 `centerInParent`；
4. 开关状态不同步 → 只在列表重新绑定时刷新；在控制中心里改了飞行模式，回到首页应会更新，若仍不同步说明 `onResume` 没触发。

## 5. 二级页图标

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 完全没图标 | `PreferenceFragmentCompat` 没 hook 上 | 日志 `hooked PreferenceFragmentCompat` |
| 部分行没图标 | key/标题不在映射表 | 在 `SubPageIcons.KEY_ICONS` 补，或补标题关键词 |
| 图标大小不一 | 源图尺寸不一致 | 重跑 `tools/make_subpage_icons.py`（统一 28dp） |
| 图标是方的 | 生成时没做圆角遮罩 | 同上，改脚本重新生成 |

## 6. 卡片视觉

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 双层圆角 | `CardFix`/`SubPageCards` 没生效 | 检查是否 hook 到 `HeaderAdapter` / `Preference#onBindViewHolder` |
| 最后一格图标偏大 | 同上 | 同上 |
| 深色模式下卡片发白 | 主题 drawable 替换用了固定颜色 | 用透明 drawable，不要用白色 |
| 切换主题后错乱 | 主题切换会重建列表 | 重新进入设置，或在 `onResume` 里再跑一次 |

## 7. 崩溃 / 卡顿 / 发热

- **设置闪退**：`adb logcat -b crash` 抓堆栈，看是否 `com.abradant.iossettings` 抛异常；大概率是某个 hook 没包 try/catch；
- **卡顿**：玻璃抓图是主要开销；日志里若每帧都在抓图，考虑降低抓图尺寸或触发频率；
- **发热**：同上；先把模块停用做对照，确认是不是本模块导致。

## 8. 安装/升级失败

| 报错 | 含义 | 处理 |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名不一致 | `adb uninstall com.abradant.iossettings` 后重装 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | versionCode 比已装的小 | 卸载旧包，或提高 versionCode |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | 包没签名/签名损坏 | 重新构建；不要手工改 zip |

## 9. 构建失败

见 [构建指南 · 常见失败](03-build.md#8-常见失败)。

## 10. 反馈模板

```text
机型：
系统：HyperOS 3 / Android 15（设置版本：）
模块版本：15.5 (90)
现象：
复现步骤：
日志（adb logcat -s IosSettings:V，从打开设置开始）：
截图：
```
