# 安装与调试

> 状态：**现行** · 对应版本：16.2 (93)

## 1. 前置

- 已 root / 已装 **LSPosed**（1.x 或 2.x，Zygisk 或 Riru 均可）；
- 系统为澎湃 OS 3（HyperOS 3）；
- 已安装 `dist/iLayoutSettings-16.2.apk`（与 [Release v16.2](https://github.com/abradanthawk-design/iLayoutSettings/releases/tag/v16.2) 的附件同一份；应用名 **iLayoutSettings**，包名 `com.Remo.ilayoutsettings`）。

## 2. 16.0 迁移（从 15.x 升级才需要）

16.0 改了包名，**不能覆盖安装**（16.0 起同包名同签名，直接覆盖即可）：

1. 先卸载旧模块：设置 → 应用管理 → `com.abradant.iossettings`（旧名 iLyayot）→ 卸载；
   （或 `adb uninstall com.abradant.iossettings`）
2. 安装 16.x 的 APK；
3. LSPosed → 模块 → 启用 **iLayoutSettings**（旧模块的勾选不会继承），作用域勾 **设置**；
4. 强制停止设置再打开。

> 本机 MIUI 会拦截 adb 安装**新包**（`INSTALL_FAILED_USER_RESTRICTED`）：
> 把 APK 拷到手机（`/sdcard/Download/iLayoutSettings/`）在文件管理器里点开安装，
> 或到开发者选项里允许「USB 安装」。
> 已经安装过的同包名覆盖安装不受这个限制。

## 3. 三步装好

1. **安装 APK**（直接点开安装，或用 `adb install -r`）；
2. **LSPosed → 模块 → 启用「iLayoutSettings」**，作用域只勾 **设置**（`com.android.settings`）；
3. **强制停止「设置」**，再重新打开：

```powershell
adb shell am force-stop com.android.settings
```

> 不强制停止的话，设置进程里还是旧代码，改了等于没改——这是最常见的一次「没生效」。

## 4. 验证是否生效

```powershell
adb logcat -s iLayoutSettings:V
```

期望看到（顺序大致如下）：

```
iLayoutSettings: loaded in com.android.settings
iLayoutSettings: hooks installed
iLayoutSettings: reordered N headers
iLayoutSettings: bottom search installed
iLayoutSettings: bottom bar: vis=0 w=... h=... y=... rootH=...
iLayoutSettings: search glass mask=[17,1][1137,139] view=1152x167 target=LinearLayout 1152x140 visible=[17,1][1137,139]
iLayoutSettings: liquid glass shader compiled
iLayoutSettings: liquid glass active (lens + inner shadow)
iLayoutSettings: hooked HeaderAdapter.onBindViewHolder (badge / airpods icon)
```

新的条件卡片只在条件满足时打日志：

```
iLayoutSettings: update card: no new version, skip        ← 没有 OTA
iLayoutSettings: update card added: 3.0.306.0.WOLCNXM.C11 ← 有新版
iLayoutSettings: airpods card: no headset connected, skip
iLayoutSettings: airpods card added: REDMI Buds 8 Pro
iLayoutSettings: icloud row added below Game Center
```

对照表：

| 日志 | 含义 | 没有这行说明 |
| --- | --- | --- |
| `hooks installed` | 模块真的进了设置进程 | 作用域没勾 / 没重启设置 / 模块没启用 |
| `reordered N headers` | 首页重排执行了 | `buildAdapter` 没 hook 上（ROM 类名变了） |
| `bottom search installed` | 底部栏装上了 | stub 找不到，退化了 |
| `update card: no new version` | 系统更新没有新版本 | 正常（iOS 同样不显示这张卡） |
| `airpods card: no headset connected` | 没连耳机 | 正常 |
| `no header found for slot ids: ...` | 部分入口在这台机器上不存在 | 正常现象，看缺的是哪个 |

## 5. 首次使用检查清单

- [ ] 首页顺序：账号卡 →（有 OTA 时）软件更新卡 →（连耳机时）耳机卡 → 网络卡（飞行模式在最上）→ 通用卡 → 通知/声音/专注模式/屏幕时间 → 隐私卡 → 钱包/Game Center/**iCloud** → App → 其他
- [ ] 更新卡右侧有红色「1」角标，点击进入系统更新页
- [ ] 耳机卡标题是耳机名、图标是灰底耳机，点击进入蓝牙设置；断开耳机后卡片消失
- [ ] iCloud 在「Game Center」下一行，点击进入小米云服务
- [ ] 控制中心行右侧有开关，点开关只切飞行模式，点行进入原「更多连接」页
- [ ] 底部搜索条悬浮在导航栏上方，不被手势条遮挡，最后一张卡片不被挡
- [ ] 点击/取消搜索时胶囊有 220ms 滑动淡入淡出，**没有瞬间出现/消失，也没有残留第二个搜索栏**
- [ ] 点底部搜索能进入系统搜索，搜索界面出现时底部条自动隐藏
- [ ] 深色模式、换主题后卡片圆角正常，没有双层圆角
- [ ] 滚动时顶栏出现模糊，回到顶部后消失

## 6. 调试技巧

- **预览耳机卡**（没连耳机也能看排版）：

  ```powershell
  adb shell "settings put global ilayout_fake_headset 'Test Buds'"
  adb shell settings delete global ilayout_fake_headset
  ```

- **预览更新卡**（模拟系统更新发现了新版本，注意系统更新 App 可能会自己清掉这个值）：

  ```powershell
  adb shell settings put global miui_new_version 3.0.999.0.TEST
  adb shell settings delete global miui_new_version
  ```

- **手机上不要往存储根目录丢文件**：UI dump、截图、日志统一放
  `/sdcard/Download/iLayoutSettings/`，用完可以在电脑侧拉走再删。

## 7. 卸载与回滚

- 在 LSPosed 里停用模块 + 强制停止设置即可恢复原样（不用卸载 APK）；
- 想彻底移除：卸载 APK，再重启设置进程。

## 8. 升级注意

**签名必须一致**才能覆盖安装。本仓库的 `dist` 包与「自己用源码重编」的包证书不同（见 [状态](00-status.md#发布物指纹)），互相覆盖会失败：

```powershell
# 覆盖安装失败时
adb uninstall com.Remo.ilayoutsettings
adb install dist\iLayoutSettings-16.2.apk
```

卸载模块不会影响系统设置数据。

## 9. 给反馈时带上这些

1. 机型 + 系统版本 + 设置版本号；
2. `adb logcat -s iLayoutSettings:V` 的完整输出（从打开设置开始）；
3. 出问题的页面截图；
4. 模块版本（设置 → LSPosed → 模块详情里看）。
