# 仓库初始化与文档体系（2026-11-09）

状态：已完成
关联版本：14.8（无版本号变化）
关联提交：`173a302`（初始提交）+ 本记录所在提交
参与者：用户 / DeepSeek Harness（deepseek-flash）

## 1. TL;DR

把 `D:\下载\IosSettingsLayout-14.8.apk` 及其源码整理成公开仓库 `abradanthawk-design/iLyayot`，建立「文档地图 + 状态快照 + 强制会话记录」三层文档体系，并把构建脚本改成可参数化、在本机复现通过。发布 v14.8 Release。**同时发现：仓库源码重编出的 APK 与用户提供的发布包并非同一份二进制**（dex 大小、签名证书均不同），已在文档中公示。

## 2. 对话重点

- **用户要求（原话要点）**：
  - 建一个 GitHub 仓库，叫 **iLyayot**；
  - 把 `D:\下载\IosSettingsLayout-14.8.apk` 开源；
  - 「随着后续的更新，使得有了文档和 agent.md 之后，直接开新对话就能无缝衔接」；
  - 做**文档地图**；
  - `agent.md` 要写**强制**记录每次对话的重点、修复内容、设计思路、踩过的坑，总结成文档。
- **做出的决策**：
  - 仓库只收「模块源码 + 成品 APK + 文档 + 图标生成脚本」，**不收**反向工程产物（smali 转储、`target-res`、`.tools`、构建中间件）；
  - 许可证选 **GPL-3.0**（Xposed 模块生态惯例，防止闭源分发；第三方内容另见 `NOTICE.md`）；
  - `dist/` 放用户提供的 14.8 原包，历史包走 Release；
  - 文档拆成 10 份专题 + `sessions/` 一层，用 `docs/README.md` 当唯一入口；
  - `agent.md` 用**固定 7 节**模板 + 强制更新清单 + DoD，而不是泛泛的「请写文档」。
- **范围外但被提到的事**：用户希望「后续更新」能延续——本次只搭好机制，真正的持续更新要靠后续会话遵守 `agent.md`。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/build.ps1` | 重写：参数化（`-Jdk` / `-BuildTools` / `-AndroidJar` / `-ApiJar` / `-OutDir` / `-KeyStore`）、三级路径回退、产物名从 manifest 读取、打印 sha256 | 原脚本硬编码开发机路径、`dist` 输出名写死、每构建清空整个 `dist` |
| `module/build.ps1` | `$ErrorActionPreference` 由 `Stop` 改为 `Continue`，逐条检查 `$LASTEXITCODE` | PowerShell 5.1 把 javac 的 warning 当错误，构建会在第 2 步直接退出 |
| `module/build.ps1` | `#Requires -Version 7.0` → `5.1` | 本机只有 Windows PowerShell 5.1，没有 `pwsh` |
| `README.md` / `docs/*` / `agent.md` / `NOTICE.md` / `.gitignore` | 新建 | 对外说明 + 文档地图 + 强制协作规范 |
| `docs/07-changelog.md` | 原文顶部加「历史全文」说明 | 原文「7. 校验」写的是 216,015 B，与 14.8 实际 195,465 B 不符 |
| `lib/xposed-api-82.jar` | 从原工程 `.tools/api-82.jar` 复制 | 让构建自包含（android.jar 体积大，仍外部提供） |

## 4. 设计思路

**问题本质**：不是「写几份文档」，而是「让下一次会话零上下文接手」。所以文档必须满足三个条件：入口唯一、状态可查、过程可追。

- **入口唯一**：`docs/README.md` 做地图，按角色给三条阅读路径（用户 / 改代码 / 新 AI 会话），并规定新增文档必须登记。
- **状态可查**：`docs/00-status.md` 只写「现在是什么样」（版本、指纹、已知问题、下一步），历史过程不进来，避免越写越长。
- **过程可追**：`docs/sessions/` 一次会话一份，固定 7 节。特别要求写「方案对比」和「踩坑四段式（现象→根因→解法→避免）」，因为这两类信息最容易在对话结束后丢失。

**为什么 agent.md 要「强制」**：
AI 会话天然倾向「改完就说完成」。所以 `agent.md` 不写建议，写**规则 + DoD 勾选清单 + 不合格示例**；并把「不写记录 = 任务未完成」明确写出来。

**备选方案与否决**：
- 用 GitHub Wiki：否决——搜索/版本化弱，且离线不可用；
- 所有内容塞一个 README：否决——77 KB 的历史日志已经证明会失控；
- 自动生成文档：否决——当前没有结构化数据源，生成为时过早。

**取舍**：`docs/07-changelog.md` 保留原始 77 KB 全文（不重写），因为它是唯一的历史证据；代价是与主线文档存在少量数据冲突，已用顶部声明隔离。

## 5. 踩过的坑

### 坑：GitHub Desktop 存的 token 全部 401

- **现象**：从 Windows 凭据管理器读到 `github-desktop-*` 凭据，解析出 `gho_` accessToken 和 `ghr_` refreshToken，`expiresAt` 显示未过期，但 `/user` 返回 401。
- **根因**：该 token 已被撤销（GitHub Desktop 重新登录/登出会 revoke），本地 `expiresAt` 只是缓存值。
- **解法**：改用 portable `gh` CLI + 设备码登录（`gh auth login --web`），拿到 `repo` scope 后创建仓库。
- **如何避免**：不要复用第三方客户端缓存的凭据；用 `gh auth status` 验证，或直接 `curl /user` 验证。

### 坑：`git-credential-manager github login --device` 被取消

- **现象**：GCM 设备码流程 30 秒内报 `User canceled device code authentication`，看不到验证码。
- **根因**：GCM 在非交互终端下走 GUI 提示，命令行的输出拿不到验证码。
- **解法**：换 `gh`（验证码直接打印到 stdout），把码转述给用户。
- **如何避免**：需要用户交互的登录，优先选「码能打印出来」的工具。

### 坑：`$ErrorActionPreference='Stop'` 让 javac 的 warning 中断构建

- **现象**：构建停在 `[2/6] javac`，报 `NativeCommandError`，但 javac 实际成功。
- **根因**：PS 5.1 把原生命令写 stderr 当错误，`Stop` 直接终止。
- **解法**：`Continue` + 逐条查 `$LASTEXITCODE`。
- **如何避免**：调用 `javac`/`aapt2`/`d8` 这类「会往 stderr 写 note」的工具时，永远不要用 `Stop`。

### 坑：`apksigner` 在临时目录报「拒绝访问」

- **现象**：`-OutDir $env:TEMP\ilyayot-build` 时 `apksigner` 抛 `FileNotFoundException ... 拒绝访问`；同目录 PowerShell 写文件正常。
- **根因**：未定位到根因（疑似受保护/重定向目录导致 Java 以 `rw` 打开失败）。
- **解法**：输出到仓库内目录，构建即通过。
- **如何避免**：签名输出优先放普通用户目录；遇到该错误先换路径验证，不要怀疑密钥。

### 坑：源码重编的 APK 与发布包不一致

- **现象**：`dist` 包 195,465 B / dex 123,864 B / 证书 `b6f98d…`；源码重编 195,465 B / dex 130,044 B / 证书 `42fba9…`。
- **根因**：14.8 是按功能逐项回退重建的，没有版本库快照；源码里残留后续版本的未挂载类（`GlassToggle`、`IntroCard`、`AboutIos` 等）和 `BottomSearch` 里的 lattice 反射分支。
- **解法**：本次只在文档中公示差异（`00-status.md`、`06-pitfalls.md` C1/C2）；清理留到路线图 P0。
- **如何避免**：回退必须用「可编译 + dex 体积对比 + 类引用清单」验收；发布包必须能追溯到具体 commit。

### 坑：README 的校验数据过期

- **现象**：原文写 `size 216,015` / sha `1A5B…`，与实际 14.8 包不符。
- **根因**：那段是后续版本写的，回退时未同步。
- **解法**：顶部加「历史全文」声明，当前指纹统一放 `00-status.md`。
- **如何避免**：易变数据只存一处，其它地方引用链接。

## 6. 验证与证据

```powershell
$env:JAVA_HOME          = "...\apk6ify\.downloads\jdk\jdk17.0.21_0"
$env:ANDROID_BUILD_TOOLS = "...\apk6ify\.downloads\bt\android-14"
$env:ANDROID_JAR         = "...\ios-settings-mod\.tools\platform36\android-36.1\android.jar"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1 -OutDir iLyayot\build\out
```

结果：

```
version    : 14.8 (83)
dex version : 039
xposed_init : True
module class: True
reorder     : True
size   : 195465 bytes
sha256 : DF4A0AAD5F506BBBC63E7A1BD2BB8B74B81F0CEA9A88C84F9DE9915ED894BC69
```

APK 对比：

| 包 | 大小 | dex 大小 | 证书 SHA-256 |
| --- | --- | --- | --- |
| 用户提供的 14.8 | 195,465 | 123,864 | `b6f98da5…` |
| 源码重编（测试密钥） | 195,465 | 130,044 | `42fba9af…` |

仓库与发布：

- 仓库：https://github.com/abradanthawk-design/iLyayot
- 初始提交：`173a302`
- Release：https://github.com/abradanthawk-design/iLyayot/releases/tag/v14.8

**未验证**：没有真机安装（无设备连接），所以「首页顺序 / 开关 / 图标 / 搜索栏」的实际显示效果本次未回归；文档中的视觉结论来自源码与历史记录，不是本次实测。

## 7. 未完成 / 下一步 / 风险

- **未完成**：
  - 固定 keystore 重签 + 真机回归；
  - 清理未挂载类与 lattice 反射分支；
  - CI（tag 触发构建/发版）。
- **下一步（按顺序）**：
  1. 用户用手机装一次 14.8 原包，按 `docs/04-install.md` 检查清单过一遍；
  2. 决定正式签名 key（是否有历史 key），再发 14.8 或 14.9；
  3. 清理死代码 → 对比 dex 体积 → 更新架构文档；
  4. 接 GitHub Actions。
- **风险**：
  - 源码与发布包行为差异未知（最大风险）；
  - 默认测试密钥每次重建都会变，容易造成「覆盖安装失败」的用户投诉；
  - 文档中的截图来自原工程，尚未确认与 14.8 当前行为完全一致。

## 更新清单

- [x] `docs/sessions/README.md` 索引已登记
- [x] `docs/00-status.md` 已更新
- [x] 受影响的专题文档已更新（架构 / 坑 / 构建 / 地图）
- [x] 未发新版本，`docs/07-changelog.md` 未改动（仅加历史声明）
