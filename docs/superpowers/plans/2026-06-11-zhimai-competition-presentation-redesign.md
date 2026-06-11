# “智脉守城”创新大赛汇报 PPT 重绘实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重建一套24页、适配8分钟现场答辩、达到参考PPT视觉冲击力的“智脉守城”可编辑 PowerPoint。

**Architecture:** 采用非模板重建模式，以原项目文档和已有真实素材为内容源，以参考PPT作为质量标杆。使用 `@oai/artifact-tool/presentation-jsx` 分模块绘制可编辑页面，经过逐页渲染、联系表审阅、布局检测和 PowerPoint 人工复检后导出最终 `.pptx`。

**Tech Stack:** Node.js、`@oai/artifact-tool`、`@oai/artifact-tool/presentation-jsx`、Microsoft PowerPoint、项目现有 PNG/SVG 素材。

---

## 文件结构

- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/profile-plan.txt`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/source-notes.txt`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/reference-audit.txt`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/claim-spine.md`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/design-system.md`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/contact-sheet-plan.md`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/theme.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/components.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/content.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/act-1.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/act-2.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/act-3.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/slides/act-4.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/build.mjs`
- 创建：`outputs/manual-20260611-zhimai-redesign/presentations/zhimai-competition-redesign/qa/qa-report.md`
- 创建：`cxcy-docx/output/智脉守城-创新大赛答辩汇报-重绘版.pptx`

### Task 1: 建立独立工作区并锁定来源

- [ ] **Step 1: 创建 Presentations 标准目录**

```powershell
$w='E:\Whd\USCDIP\outputs\manual-20260611-zhimai-redesign\presentations\zhimai-competition-redesign'
New-Item -ItemType Directory -Force -Path "$w\slides","$w\assets","$w\preview","$w\layout","$w\qa","$w\output"
```

预期：六个目录均存在，旧版工作区保持不变。

- [ ] **Step 2: 编写 profile-plan**

内容必须明确：

```text
task mode: create
primary deck-profile: product-platform
secondary gates: engineering-platform, strategy-leadership
required proof objects: city-risk scene, solution architecture, technical loop, diagnosis model, emergency workflow, product UI, comparison, implementation path, team evidence
missing inputs: unverified metrics or evidence must be labeled, never invented
```

- [ ] **Step 3: 建立来源和参考审计**

读取原始 DOCX、现有项目图、旧版PPT素材和参考PPT；在 `source-notes.txt` 中记录素材路径与真实性用途，在 `reference-audit.txt` 中记录“保留、提升、不模仿”的视觉规则。

- [ ] **Step 4: 验证来源文件**

```powershell
Test-Path 'E:\Whd\USCDIP\cxcy-docx\智脉守城—地下管网数字化健康监测与应急协同平台.docx'
Test-Path 'C:\Users\w1328\xwechat_files\wxid_6l5ciqjxs2e722_17ea\msg\file\2026-06\最新版——智补偿举升设备.pptx'
```

预期：均输出 `True`。

### Task 2: 锁定24页论点骨架与视觉节奏

- [ ] **Step 1: 编写 claim-spine**

每页必须包含：

```text
slide number
kicker
claim title
proof object
support note
source
speaker-time
```

24页总计讲述时长不得超过460秒，为现场停顿保留至少20秒。

- [ ] **Step 2: 编写 design-system**

明确：

```text
canvas: 16:9
base: near-black navy
primary accent: electric blue / cyan
risk accent: orange-red
minimum body font: 18pt
minimum title font: 28pt
one slide = one claim + one dominant proof object
no generic card grids
```

- [ ] **Step 3: 编写 contact-sheet-plan**

联系表必须能看到四种页面节奏：

```text
hero / chapter stage
full-width evidence
technical mechanism
comparison / route / team proof
```

- [ ] **Step 4: 自检论点覆盖**

```powershell
rg -n "^## Slide " "$w\claim-spine.md"
```

预期：恰好24条；包含“感知、诊断、预警、协同、商业、团队”。

### Task 3: 整理并验证视觉资产

- [ ] **Step 1: 复制真实项目资产到新工作区**

复制项目现有 PNG/SVG、平台截图、模型图和团队/成果证据；参考PPT资产只用于视觉研究，不直接冒充本项目素材。

- [ ] **Step 2: 建立资产清单**

`source-notes.txt` 中每个资产记录：

```text
asset path | source | slide usage | real/generated/reference-only
```

- [ ] **Step 3: 生成项目资产联系表**

用 Presentations 自带渲染/联系表脚本生成 `assets/contact-sheet.png`。

- [ ] **Step 4: 检查分辨率和真实性**

低清技术图重新绘制；生成图只用于背景和概念场景；真实成果页不得使用生成图。

### Task 4: 建立绘制基础组件

- [ ] **Step 1: 编写 `theme.mjs`**

定义颜色、字体、页面尺寸、间距、标题层级、页码和发光线条样式。

- [ ] **Step 2: 编写 `components.mjs`**

实现以下可复用组件：

```js
heroStage()
claimTitle()
sectionMarker()
imageWithOverlay()
glowPipeline()
metricCallout()
evidenceStrip()
sourceFooter()
```

- [ ] **Step 3: 编写 `content.mjs`**

将24页标题、结论、要点、素材路径和讲述时间集中管理，避免正文散落在绘图逻辑中。

- [ ] **Step 4: 运行基础组件冒烟测试**

执行 `build.mjs --slides 1,6,15,24`，预期导出4页测试稿且无渲染错误。

### Task 5: 绘制第一幕“为何必须守城”

- [ ] **Step 1: 绘制1–5页**

重点：

```text
1 封面：城市夜景 + 发光地下管网
2 城市生命线：地上/地下剖面
3 城市级事件：事故场景与风险词
4 三大盲区：断裂治理链路
5 项目使命：由暗到亮的转场
```

- [ ] **Step 2: 渲染并检查**

预期：五页在缩略图尺寸下均能区分；无连续两页使用相同构图。

### Task 6: 绘制第二幕“我们如何守城”

- [ ] **Step 1: 绘制6–10页**

包括总体方案、技术闭环、数字孪生、多源感知和健康指标。

- [ ] **Step 2: 绘制11–15页**

包括风险诊断、趋势预警、模型创新、协同处置和平台驾驶舱。

- [ ] **Step 3: 技术准确性检查**

逐页对照源文档，确保技术名词和链路关系准确；平台截图必须来自真实项目素材。

- [ ] **Step 4: 渲染并检查**

预期：技术页以图为主，任何单页正文不超过60个汉字。

### Task 7: 绘制第三幕“为什么我们更强”

- [ ] **Step 1: 绘制16–20页**

包括三项创新、传统方案对比、典型风险事件、实施效果和成果壁垒。

- [ ] **Step 2: 证据真实性检查**

所有指标、证书、合作和成果必须能回溯；材料不足处明确标记“待补充真实材料”，不得虚构。

- [ ] **Step 3: 渲染并检查**

预期：第17–20页的证明对象在联系表上清晰可辨，不是文字清单。

### Task 8: 绘制第四幕“如何落地与成长”

- [ ] **Step 1: 绘制21–24页**

包括应用场景、商业推广路径、团队保障和结尾回扣。

- [ ] **Step 2: 闭环检查**

确认第24页视觉和语言回扣第1页，“守城”使命贯穿全篇。

- [ ] **Step 3: 渲染并检查**

预期：收尾不出现信息堆积，最后一页适合停留并接受评委提问。

### Task 9: 全稿渲染、联系表审阅与迭代

- [ ] **Step 1: 完整构建24页**

```powershell
node build.mjs
```

预期：生成24页 PPTX、24张预览图和24份布局 JSON。

- [ ] **Step 2: 运行布局检测**

检查文字溢出、元素越界、图片变形和遮挡；任何阻断问题必须修正。

- [ ] **Step 3: 生成联系表并进行 comeback 评分**

评分重点：叙事清晰度、视觉一致性、构图多样性、证据强度、参考PPT质量差距。

- [ ] **Step 4: 迭代最弱页面**

至少修订评分最低的三页，再重新渲染和评分。

### Task 10: PowerPoint 人工复检与最终交付

- [ ] **Step 1: 在 Microsoft PowerPoint 打开成品**

逐页检查字体替换、换行、图片清晰度、边缘裁切和播放比例。

- [ ] **Step 2: 修正发现的问题**

通过源文件修改后重新构建；不得只在临时 PowerPoint 文件中做不可复现修改。

- [ ] **Step 3: 最终验收**

```powershell
Test-Path 'E:\Whd\USCDIP\cxcy-docx\output\智脉守城-创新大赛答辩汇报-重绘版.pptx'
```

预期：输出 `True`；成品为24页，PowerPoint 可正常打开。

- [ ] **Step 4: 交付**

最终只将成品 PPTX 放入 `cxcy-docx/output`；预览、布局和QA资料保留在独立工作区。

