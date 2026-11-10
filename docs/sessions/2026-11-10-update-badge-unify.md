# 红 1 角标统一：通用行改红 + 补箭头 + 更新卡标题左移（2026-11-10）

状态：已完成
关联版本：16.6（versionCode 97，不升号）
关联提交：<发版提交见 docs/00-status.md>
参与者：AbradantHawk / DeepSeek Harness

## 1. TL;DR

把「有软件更新可用」卡的红色「1」和「通用」行 MIUI 自带的蓝色「1」统一成同一颗红角标（24dp），
两行都补上系统自带的箭头（复用原生 `arrow_right` 的 drawable），更新卡标题从图标槽左移到 16dp 内边距。
深色 / 浅色、有更新 / 无更新四种状态都在真机核对过。

## 2. 对话重点

- **用户要求（原话）**：
  - 「红1要箭头」
  - 「『通用』的蓝1系统更新提醒也改成红1，大小一样」
  - 「有更新提醒也要箭头，『有软件更新可用』向左移，视觉居中」
  - 「箭头用设置自带的」
- **做出的决策**：
  - 角标不直接改原生 `text_right` 的样式（回收后会把「已开启」这类摘要行也染红），
    而是把原生角标隐藏、塞模块自己的 `TextView`；
  - 箭头先自绘过一版（1.8dp 圆角折线），用户指出「用设置自带的」后改为克隆该行
    `arrow_right` 的 drawable + `imageTintList`；
  - 更新卡标题按「与箭头留白对称」处理（左内边距 16dp），而不是整行居中——居中的话会和角标抢位置。
- **范围外但被提到**：麦克风识别范围、搜索/麦克风图标大小，属于 16.6 早前已完成的搜索栏部分。

## 3. 修复内容

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `module/.../HeaderOverlay.java` | 角标 20dp → 24dp、11.5sp → 13sp；把 `widget_frame` 里其它子 View 在预绘制时置 `GONE`；新增 `ensureChevron` / `shiftTitle` / `ensureGuard` | 角标原本贴底、原生蓝 1 会回来、带角标行没有箭头、合成行标题被图标槽缩进 |
| `module/.../HeaderData.java` | 新增 `isGeneralHeader()`（按标题「通用 / 更多设置」判断） | 主题会改 id，标题比 id 稳 |
| `docs/*`、`docs/assets/*` | 架构 / 踩坑 / 状态 / 版本记录、截图 | 仓库规范 |

## 4. 设计思路

- **问题本质**：header 行右侧只有一个 `widget_frame` 槽位，MIUI 放角标时会把箭头一起藏掉；
  这块 frame 又是**竖直** LinearLayout、还可能带一个空占位子 View，于是角标被顶到行底部。
- **方案对比**：
  - A. 直接改原生 `text_right` 的 background / 颜色 / 内边距 —— 代码最少，但该 View 会被回收成
    「已开启」「CMCC-…」这类摘要行，样式会污染；
  - B. 隐藏原生角标 + 塞模块自己的角标，用 `OnPreDraw` 每帧校正 —— 多一层 guard，但完全不碰原生样式。
- **最终选择**：B。箭头同样走「克隆原生 drawable」而不是「把原生箭头设 VISIBLE」——后者 MIUI 在
  bind 后会再藏回去，抢不过；克隆 drawable 像素级一致且不会被回收逻辑改掉。

## 5. 踩过的坑

### 坑 1：角标比箭头低 11dp

- **现象**：`1` 的中心比箭头低 30px（约 11dp），dump 里角标底边正好贴着 `widget_frame` 底边。
- **根因**：`widget_frame` 是竖直 LinearLayout，上面还有一个 61px 的空占位子 View，角标被排到底部；
  对竖直 LinearLayout 设 `gravity=CENTER_VERTICAL` 没用（子 View 是依次堆叠的）。
- **解法**：`hideOthers()` 在每次预绘制时把非角标子 View 全部 `GONE`，frame 收缩到角标大小，
  行本身会把 frame 居中，角标就与箭头齐平（实测两者中心均为 y=945）。
- **如何避免**：遇到 MIUI 的 `widget_frame` 不要假设是横向容器；先用 uiautomator 量父子 bounds。

### 坑 2：换上红角标后偶尔又冒出原生蓝 1

- **现象**：首次进入设置时正常，切深浅色或重进后「通用」行又变回蓝色「1」。
- **根因**：MIUI 的角标是 bind **之后**才淡入的，`onBindViewHolder` 里读到 `text_right` 文本还是空串，
  `nativeCount()` 判定「这行没有角标」→ 清理逻辑把我们的角标摘掉，随后原生角标淡入。
- **解法**：把替换逻辑挂到 `OnPreDraw`（`ensureGuard`），发现数字角标就 `post` 一次替换；
  预绘制里只在「角标已存在」时才校正，避免每帧 addView。
- **如何避免**：凡是「等某个 View 出现再改」的需求，都别只在 bind 里判断一次。

### 坑 3：自绘箭头和系统箭头对不上

- **现象**：自绘箭头比系统箭头细、偏浅，放大看形状也不同。
- **根因**：系统箭头是 MIUI 自己的 drawable（带 tint），手画折线很难同款。
- **解法**：从该行原生 `arrow_right` 的 `ImageView` 取 `getDrawable()`，用 `ConstantState` 克隆一份
  （`mutate()` 防止两边共享 bounds），再套上原 view 的 `imageTintList`，放进 10.2dp 宽的槽位。
- **如何避免**：能克隆系统资源就别手画。

## 6. 验证（25053RT47C / HyperOS 3，密度 520dpi）

| 状态 | 更新卡 | 通用行 |
| --- | --- | --- |
| 深色 | 标题 x=114、角标 997–1074、箭头 1148–1176 | 角标 999–1076、箭头 1149–1176，与「通用」文字中心同高 |
| 浅色 | 同上 | 同上（箭头为浅色 tint） |
| 无更新（`settings delete global miui_new_version`） | 整张卡消失 | 蓝色/红色角标都不出现，箭头回到原生位置 |

截图：[更新卡](../assets/update-badge-16.6.png)、[通用行](../assets/general-badge-16.6.png)、
[改前 / 改后对照](../assets/badge-before-after-16.6.png)（上：旧的蓝 1 与贴底红 1；下：统一后的红 1 + 箭头）。

## 7. 遗留 / 注意

- 「通用」行的角标如果在设置打开期间消失（更新被装掉），红角标会留到下次 bind；概率很低，先不处理。
- 标题左移用的是 `translationX`（实测 -120px，迭代到 16dp 内边距），换 MIUI 字号/主题可能差几像素；
  `shiftTitle()` 每次预绘制最多修 6 次，收敛到 ±1px 停。
- 复现「有更新」状态：`adb shell settings put global miui_new_version 3.0.999.0.WOLCNXM.TEST`。
