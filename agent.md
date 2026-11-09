# agent.md — 协作与记录规范（强制）

> 状态：**现行**
> 适用对象：所有进入本仓库的 AI 会话（Claude / GPT / DeepSeek…）与人类协作者。
> 性质：**强制**。不按本文件写记录 = 本次任务未完成（见第 10 节 DoD）。

---

## 0. 为什么有这个文件

这个项目的状态不在任何人的记忆里，而在仓库里。

- `docs/sessions/` 记录**发生了什么、为什么**；
- `docs/00-status.md` 记录**现在在哪**；
- `docs/README.md` 记录**东西在哪**。

下次开新对话时，任何人（或 AI）只读三份文件就能接手，不用重新摸索、不用问「上次改到哪了」。
**你这次会话的价值，一半在代码，一半在你留下的记录。**

---

## 1. 会话开始（5 分钟，不许跳）

按顺序读，读完再动手：

1. `agent.md`（本文件）
2. `docs/00-status.md` —— 当前版本、发布物哈希、已知问题
3. `docs/README.md` —— 文档地图，按任务挑专题
4. `docs/sessions/README.md` 的索引表 → 最近 1~2 份会话记录
5. 与任务相关的专题：改代码看 `02-architecture.md` + `05-design-notes.md` + `06-pitfalls.md`；构建看 `03-build.md`；排错看 `08-troubleshooting.md`

然后先回复一段**接手确认**，再开始改：

```
接手确认
- 当前版本：14.9 (84)
- 本次目标：<一句话>
- 受影响文件：<预估>
- 主要风险：<例如：改 Header 顺序可能影响卡片分组>
```

### 给用户：新对话开场白（直接复制）

> 读 `agent.md`、`docs/00-status.md`、`docs/README.md` 和 `docs/sessions/` 里最新的一份记录，
> 先用 5 行给我接手确认（当前版本、已知问题、本次建议、风险、你打算先看哪些文件），
> 在我确认之前不要改代码。

这句话的作用是强制 AI 先对齐状态再动手，避免它凭训练记忆「重写」一遍项目。

> 禁止凭记忆改代码。历史上有多次「以为已经回退了，其实源码里还留着后面版本的东西」——见 `docs/sessions/2026-11-09-repo-bootstrap.md`。

---

## 2. 会话结束（强制，缺一不可）

新开一个文件：`docs/sessions/YYYY-MM-DD-<slug>.md`（日期用本机时区，slug 小写连字符），
**必须包含且只按顺序包含以下 7 节**：

```markdown
# <标题>（YYYY-MM-DD）

状态：已完成 / 部分完成 / 阻塞
关联版本：14.8 → x.y
关联提交：<commit sha，可后补>

## 1. TL;DR
三句话以内：做了什么、结果如何、还差什么。

## 2. 对话重点
- 用户要求（尽量保留原话要点）
- 明确做出的决策 / 否决的方案
- 范围外但被提到的事（记下来，别做）

## 3. 修复内容
| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
没有代码改动的会话写「无代码改动」，并说明原因。

## 4. 设计思路
- 问题本质是什么
- 考虑过哪些方案（A/B），为什么不选另一个
- 关键取舍（兼容性 / 性能 / 可维护性）

## 5. 踩过的坑
每条固定四段：**现象 → 根因 → 解法 → 如何避免**。
没有坑就写「本次无新坑」，不要编。

## 6. 验证与证据
- 执行过的命令（原样）
- 结果（关键输出、APK 大小、sha256、日志行）
- 没验证的部分要明确写「未验证」

## 7. 未完成 / 下一步 / 风险
- 明确未做的事
- 建议的下一步顺序
- 可能翻车的点
```

写完必须做三件同步（见第 4 节）：登记索引、更新 `00-status.md`、更新受影响专题文档。

---

## 3. 记录质量线（写之前自查）

| 小节 | 必须回答 | 不合格示例 |
| --- | --- | --- |
| TL;DR | 一句话能看懂结论 | 「做了一些优化」 |
| 对话重点 | 用户到底要什么、否掉了什么 | 只抄需求标题 |
| 修复内容 | 文件 + 行为变化（before/after） | 「修复了 bug」 |
| 设计思路 | 为什么不是另一种做法 | 只写「按需求实现」 |
| 踩坑 | 现象→根因→解法→预防 | 「有个坑，解决了」 |
| 验证 | 命令 + 输出 + 哈希 | 「测试通过」 |
| 下一步 | 可执行的具体动作 | 「继续优化」 |

**判断标准**：三个月后一个陌生人（或新 AI）只看这份记录，能不能复现你的结论？

---

## 4. 必须同步更新的文件

| 触发 | 必须更新 |
| --- | --- |
| **每次会话结束** | 新建 `docs/sessions/<日期>-<slug>.md` + `docs/sessions/README.md` 索引 + `docs/00-status.md` |
| 版本号变化 / 出新 APK | `docs/07-changelog.md` **顶部**新增一节 + `00-status.md` + `README.md` 版本徽章 |
| 新增/修改 Hook、类、资源映射 | `docs/02-architecture.md` |
| 新坑、或推翻旧结论 | `docs/06-pitfalls.md`（旧结论标「已过时」，说明为什么） |
| 新增/改名/删除文档 | `docs/README.md`（文档地图） |
| 构建脚本 / 依赖升级 | `docs/03-build.md` |

---

## 5. 命名、格式与勘误

- 会话文件名：`2026-11-09-repo-bootstrap.md`（日期-英文短标题）；
- 同一天多次会话：`2026-11-09-<slug>-2.md`，不要覆盖已有文件；
- **只能追加，不能改写历史**。发现旧记录有错，在文件末尾追加：

```markdown
## 勘误 YYYY-MM-DD
- 原文「……」有误，实际为「……」，依据：<命令/日志>
```

- 索引表列固定：`日期 | 标题 | 一句话结论 | 状态`。

---

## 6. 版本与发布规则

1. 版本号只在 `module/AndroidManifest.xml` 改：`android:versionName` / `android:versionCode` 同时 +1；
2. 改版本必须一次做齐：改 manifest → 构建 → 记 sha256 → 更新 changelog 顶部 → 更新 `00-status.md` → 更新 README 徽章；
3. `dist/` 只放**当前**发布包；历史包放 GitHub Release，不要堆在仓库；
4. 正式包必须用**固定 keystore** 签名，换了 key 必须在 changelog 里写升级说明（用户需要先卸载）；
5. 发布包文件名固定 `IosSettingsLayout-<versionName>.apk`。

---

## 7. 构建与验证（复制即用）

本机是 Windows PowerShell 5.1 且默认禁止运行脚本，必须带 `-ExecutionPolicy Bypass`：

```powershell
# 1) 准备工具链路径（按本机实际情况改）
$env:JAVA_HOME          = "C:\path\to\jdk17"
$env:ANDROID_BUILD_TOOLS = "C:\path\to\build-tools\34.0.0"
$env:ANDROID_JAR         = "C:\path\to\platforms\android-36\android.jar"

# 2) 构建
powershell.exe -NoProfile -ExecutionPolicy Bypass -File module\build.ps1

# 3) 校验产物
Get-FileHash dist\IosSettingsLayout-14.9.apk -Algorithm SHA256
& "$env:ANDROID_BUILD_TOOLS\aapt2.exe" dump badging dist\IosSettingsLayout-14.9.apk |
  Select-String '^package:|^sdkVersion|^targetSdkVersion'
& "$env:ANDROID_BUILD_TOOLS\apksigner.bat" verify --print-certs dist\IosSettingsLayout-14.9.apk
```

构建成功的判定（缺一不可）：
`package: name='com.abradant.iossettings' versionCode='84' versionName='14.9'`、
`xposed_init : True`、`module class: True`、`reorder : True`、apksigner `Verified`。

**没有设备实测的改动，必须在记录第 6 节写「未上机验证」。**

---

## 8. 仓库地图

```
README.md                 对外说明：是什么 / 怎么装 / 去哪下载
agent.md                  本文件：强制工作流
LICENSE                   GPL-3.0
NOTICE.md                 第三方声明
module/                   模块源码
  AndroidManifest.xml     唯一版本号来源
  build.ps1               无 Gradle 构建脚本
  src/com/abradant/iossettings/*.java
  res/ assets/
dist/                     当前发布 APK
lib/xposed-api-82.jar     Xposed API（编译期依赖）
tools/make_subpage_icons.py  二级页图标生成脚本
tools/check_agsl.py          AGSL 离线编译自检（需要 skia-python，改 shader 必跑）
docs/                     文档（入口 docs/README.md）
```

---

## 9. 禁止事项

- ❌ 提交 `build/`、`*.jks`、反编译目录（`smali/`、`target-res/`）、临时图像/日志；
- ❌ 把 token、密钥、账号凭据写进仓库或文档；
- ❌ 删除或重写历史会话记录、changelog 旧条目；
- ❌ 未验证就声称「已修复」；
- ❌ 改了行为却不写会话记录（本文件存在的原因）；
- ❌ 在没搞清「发布包和源码是否一致」前，直接改版本号发版。

---

## 10. Definition of Done（每次任务收尾逐项勾）

```markdown
- [ ] 改动可构建（module/build.ps1 退出码 0）
- [ ] APK 四项校验通过（badging / xposed_init / module class / 签名）
- [ ] docs/sessions/<本次>.md 已写，7 节齐全
- [ ] sessions/README.md 索引已登记
- [ ] docs/00-status.md 已更新
- [ ] 受影响的专题文档已更新（架构/坑/构建/地图）
- [ ] commit message 符合规范
```

---

## 11. 提交信息规范

```
<type>(<scope>): <中文摘要，不超过 50 字>

<可选正文：为什么改、影响范围>

<可选 footer：Refs #issue / BREAKING CHANGE: ...>
```

| type | 用途 |
| --- | --- |
| feat | 新能力 |
| fix | 修缺陷 |
| docs | 只改文档 |
| build | 构建脚本 / 依赖 |
| refactor | 不改行为的重构 |
| chore | 杂项（版本号、格式） |

示例：

```
fix(bottom-search): 搜索栏底部间距改用导航栏 inset，修复手势条遮挡
docs(sessions): 记录 14.8 源码与发布包不一致的排查过程
```

---

**记住了：你写的不只是记录，是下一个人的起点。**
