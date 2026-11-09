# 安装与调试

> 状态：**现行** · 对应版本：15.0 (85)

## 1. 前置

- 已 root / 已装 **LSPosed**（1.x 或 2.x，Zygisk 或 Riru 均可）；
- 系统为澎湃 OS 3（HyperOS 3）；
- 已安装 `dist/IosSettingsLayout-15.0.apk`（应用名 **iLyayot**；从 14.8 升级要先卸载，签名不同；14.9 可直接覆盖）。

## 2. 三步装好

1. **安装 APK**（直接点开安装，或用 `adb install -r`）；
2. **LSPosed → 模块 → 启用「iLyayot」**，作用域只勾 **设置**（`com.android.settings`）；
3. **强制停止「设置」**，再重新打开：

```powershell
adb shell am force-stop com.android.settings
```

> 不强制停止的话，设置进程里还是旧代码，改了等于没改——这是最常见的一次「没生效」。

## 3. 验证是否生效

```powershell
adb logcat -s IosSettings:V
```

期望看到（顺序大致如下）：

```
IosSettings: hooks installed
IosSettings: reordered N headers
IosSettings: bottom search installed
IosSettings: bottom bar: vis=0 w=... h=... y=... rootH=...
IosSettings: search glass mask=[17,1][1137,139] view=1152x167 target=LinearLayout 1152x140 visible=[17,1][1137,139]
IosSettings: search glass native fill alpha=0x80 (android.graphics.drawable.NinePatchDrawable)
IosSettings: liquid glass shader compiled
IosSettings: liquid glass active (lens + rim + inner shadow)
IosSettings: hooked PreferenceFragmentCompat (sub-page icons)
IosSettings: airplane glass toggle added to row (HyperiOS-safe)
```

滚动时还会看到（每 2 秒一条，静止后停止）：

```
IosSettings: search glass capture fps=108
IosSettings: search glass draw fps=102 captureCostMs=5
```

对照表：

| 日志 | 含义 | 没有这行说明 |
| --- | --- | --- |
| `hooks installed` | 模块真的进了设置进程 | 作用域没勾 / 没重启设置 / 模块没启用 |
| `reordered N headers` | 首页重排执行了 | `buildAdapter` 没 hook 上（ROM 类名变了） |
| `bottom search installed` | 底部栏装上了 | stub 找不到，退化了 |
| `airplane glass toggle added to row` | HyperiOS 路径下用自有 GlassToggle 塞进控制中心行 | `widget_frame` 没找到或行不是飞行模式行 |
| `airplane switch attached to 飞行模式 row` | 未装 HyperiOS，走原生开关路径 | 同上 |
| `no header found for slot ids: ...` | 部分入口在这台机器上不存在 | 正常现象，看缺的是哪个 |

## 4. 首次使用检查清单

- [ ] 首页顺序：账号卡 → 关于设备 → 网络卡（飞行模式在最上）→ 通用卡 → 通知/声音/屏幕时间 → 隐私卡 → 钱包/Game Center → App → 其他
- [ ] 控制中心行右侧有开关，点开关只切飞行模式，点行进入原「更多连接」页；装 HyperiOS 时开关也应与标题垂直居中对齐
- [ ] 底部搜索条悬浮在导航栏上方，不被手势条遮挡，最后一张卡片不被挡
- [ ] **滚动时玻璃里的模糊内容跟着列表一起动**（不是固定在某一帧的旧图）
- [ ] 玻璃胶囊不溢出原生胶囊，左右不露出原生 fill 的窄边
- [ ] 点底部搜索能进入系统搜索，搜索界面出现时底部条自动隐藏
- [ ] 二级页（通用 / 更多设置）图标出现且大小一致
- [ ] 深色模式、换主题后卡片圆角正常，没有双层圆角
- [ ] 滚动时顶栏出现模糊，回到顶部后消失

## 5. 卸载与回滚

- 在 LSPosed 里停用模块 + 强制停止设置即可恢复原样（不用卸载 APK）；
- 想彻底移除：卸载 APK，再重启设置进程。

## 6. 升级注意

**签名必须一致**才能覆盖安装。本仓库的 `dist` 包与「自己用源码重编」的包证书不同（见 [状态](00-status.md#发布物指纹)），互相覆盖会失败：

```powershell
# 覆盖安装失败时
adb uninstall com.abradant.iossettings
adb install dist\IosSettingsLayout-15.0.apk
```

卸载模块不会影响系统设置数据。

## 7. 给反馈时带上这些

1. 机型 + 系统版本 + 设置版本号；
2. `adb logcat -s IosSettings:V` 的完整输出（从打开设置开始）；
3. 出问题的页面截图；
4. 模块版本（设置 → LSPosed → 模块详情里看）。
