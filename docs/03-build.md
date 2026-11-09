# 构建指南

> 状态：**现行** · 对应版本：15.3 (88)

## 1. 为什么不走 Gradle

模块只有一个 dex、没有第三方运行时依赖、资源极少。用 `aapt2 → javac → d8 → zipalign → apksigner` 五步就够，构建一次几秒，产物体积可控（195 KB），也避免了 AGP 与 JDK/AGP 版本互相绑架。

代价是**工具链要自己准备**，见下。

## 2. 工具链

| 工具 | 本项目验证版本 | 用途 | 获取 |
| --- | --- | --- | --- |
| JDK | **17.0.21**（17 即可） | javac / jar / keytool / apksigner 运行时 | https://adoptium.net |
| Android build-tools | **34.0.0** | aapt2 / d8 / zipalign / apksigner | sdkmanager `build-tools;34.0.0` |
| Android platform | **android-36.1**（API 36 的 `android.jar`） | 编译期 bootclasspath | sdkmanager `platforms;android-36` |
| Xposed API | **82** | 编译期 `de.robv.android.xposed.*` | 仓库已带 `lib/xposed-api-82.jar` |

> `aapt2` 用 34 的版本去 link `targetSdk 36` 是可以的（本项目实测通过）；如果换更高版本 build-tools，注意 `--target-sdk-version` 行为可能变化。

## 3. 构建

```powershell
# 方式 A：参数
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 `
  -Jdk          "C:\jdk-17" `
  -BuildTools   "C:\android-sdk\build-tools\34.0.0" `
  -AndroidJar   "C:\android-sdk\platforms\android-36\android.jar"

# 方式 B：环境变量
$env:JAVA_HOME           = "C:\jdk-17"
$env:ANDROID_BUILD_TOOLS = "C:\android-sdk\build-tools\34.0.0"
$env:ANDROID_JAR         = "C:\android-sdk\platforms\android-36\android.jar"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1
```

输出：`dist/IosSettingsLayout-<versionName>.apk`（版本号从 `AndroidManifest.xml` 读，不用手改文件名）。

> **必须加 `-ExecutionPolicy Bypass`**：本机默认执行策略禁止运行 `.ps1`，直接 `& .\build.ps1` 会报 `UnauthorizedAccess`。
> 脚本用 Windows PowerShell 5.1 编写（`#Requires -Version 5.1`），**不需要** PowerShell 7。

### 改了 AGSL 的话：先跑 shader 自检

`javac` 不校验 AGSL（shader 是字符串，真机运行时才编译）。改了 `SearchGlass.java` 里的 shader，先跑：

```powershell
python -m pip install skia-python   # 只需一次
python tools\check_agsl.py
```

它会抽出两个 shader 常量用 Skia 的 SkSL 编译器编译一遍，并核对 uniform 名称与 Java 侧一致；
退出码 0 才继续构建。见 [踩坑 F1/F2](06-pitfalls.md)。

### 参数说明

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `-Jdk` | `$env:JAVA_HOME` | JDK 根目录 |
| `-BuildTools` | `$env:ANDROID_BUILD_TOOLS` | 含 aapt2/d8/zipalign/apksigner 的目录 |
| `-AndroidJar` | `$env:ANDROID_JAR` | platform 的 `android.jar` |
| `-ApiJar` | `lib/xposed-api-82.jar` | Xposed API |
| `-OutDir` | `dist` | 产物目录 |
| `-KeyStore` | `.keystore/testkey.jks` | 签名库，不存在则自动生成（首次构建后复用） |
| `-KeyAlias` / `-KeyPass` | `iossettings` / `android` | 调试密钥参数 |

## 4. 每一步在干什么

1. **aapt2 compile**：把 `module/res` 编译成 `res.zip`（含 `resources.arsc` 与图标）；
2. **aapt2 link**：生成 `base.apk`，写入 manifest、资源、`assets/`（`xposed_init` 在这里），并生成 `R.java`；
3. **javac**：`-source 8 -target 8`，`-bootclasspath android.jar`，`-classpath` 指向 Xposed API；
4. **d8**：`--min-api 29`，把 `classes.jar` 编成 `classes.dex`；
5. **打包**：把 `classes.dex` 塞进 `base.apk`（幂等更新 zip）；
6. **zipalign**：4 字节对齐，`-p` 保持 page 对齐；
7. **apksigner**：v1 + v2 + v3 全开，`--min-sdk-version 29 --max-sdk-version 36`；
8. **verify**：打印证书、badging、dex 版本、`xposed_init` / `Module` / `HeaderData` 是否在 dex 里，最后输出 size + sha256。

## 5. 成功判据

```
package: name='com.abradant.iossettings' versionCode='88' versionName='15.3'
sdkVersion:'29'
targetSdkVersion:'36'
dex version : 039
xposed_init : True
module class: True
reorder     : True
```

`dex version 039` 表示 dex 格式 3.9（Android 9+），与 `--min-api 29` 一致。

## 6. 签名与升级

- 默认 `.keystore/testkey.jks` 是**首次构建自动生成的调试密钥**，之后每次构建复用（它不在 `build/` 里，不会被清理）；
- 同一个 APK 只有**同一把 key** 签出来的才能覆盖安装，否则 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；
- 正式发布请固定一把 keystore，通过 `-KeyStore` 传入，并且**不要提交到仓库**（`.gitignore` 已忽略 `*.jks`）；
- 本仓库 `dist/IosSettingsLayout-14.8.apk` 是用户提供的发布包，其证书与源码重编产物**不同**，见 [状态](00-status.md#发布物指纹)。

## 7. 可复现性说明

同源码、同工具链重编，**字节不一定完全一致**：

- 签名块包含随机/时间相关信息；
- zip 条目时间戳、压缩实现可能不同；
- 不同 JDK 版本生成的 class 文件顺序/常量池可能有差异。

本项目实测：重编后 APK 大小仍为 **195,465 bytes**，但 SHA-256 与发布包不同（且 dex 大小不同，说明构建输入本身也有差异，见 [状态 · 已知问题 1](00-status.md#已知问题)）。

因此**不要用「哈希是否相同」判断构建是否成功**，用第 5 节的四项判据。

## 8. 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `无法加载文件 build.ps1，因为在此系统上禁止运行脚本` | 执行策略 | 用 `powershell.exe -ExecutionPolicy Bypass -File ...` |
| `JDK (JAVA_HOME) not found` | 参数与环境变量都没给 | 传 `-Jdk` 或设 `JAVA_HOME` |
| `javac failed`，log 里是编码/符号错误 | 用了非 UTF-8 保存源码 | 源码统一 UTF-8；`javac` 已带 `-encoding UTF-8` |
| `aapt2 link failed` | `android.jar` 版本低于 targetSdk | 换 API 36 的 platform |
| `apksigner` 报 `FileNotFoundException ... 拒绝访问` | 输出目录在受限位置（如某些临时目录） | 换 `-OutDir` 到仓库内或普通目录 |
| 构建成功但装不上 | 签名与旧包不一致 | 卸载旧包，或改用固定 keystore |
| 装上了但没效果 | 没强制停止设置 / 作用域没勾 | 见 [安装](04-install.md) |

## 9. 重新生成二级页图标

图标源图是 `docs/assets/subpage-icons.png`（原工程根目录的 `subpage-icons.png`）：

```powershell
python tools\make_subpage_icons.py
```

脚本会把切好的 PNG 写回 `module/res/drawable-xxhdpi/ic_sub_*.png`。改图标尺寸/配色后需要重新构建。
