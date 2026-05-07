# F-14 数据质量标识 UI - 实施计划

## 1. 目标
以 `/mgmt/trends` 时序趋势页为主，沉淀可复用的数据质量标识组件，统一展示 `dq_score`、`dq_level`、`dq_flags`、补偿数据标识和告警置信度降权信息。

## 2. 设计规范
- 使用 `ui-ux-pro-max` 的 SaaS Analytics Dashboard 思路：数据密集、紧凑表格、KPI 卡片、hover tooltip 和清晰状态色。
- A/B/C/D 等级按可信度分层：
  - A：高可信，可自动进入规则/模型/处置链路。
  - B：可用但需保留降权。
  - C：人工复核优先。
  - D：不可靠，默认不直接触发正式事件。
- 补偿数据和关注态使用琥珀色，正常数据使用蓝色，风险态使用玫红/红色，保持和前序页面一致的深色运维看板风格。

## 3. 前后端联调契约
- 复用 B-14 接口：`GET /api/dq/scores` 与 `GET /api/dq/scores/{sourceRecordId}`。
- 复用字段：
  - `dqScore / dqLevel / dqFlags`
  - `dqCompleteness / dqValidity / dqTimeliness / dqConsistency / dqStability`
  - `dqAlarmConfFactor / dqScoredAt / isBackfill`
- 不新增后端接口，不改数据库结构，不改变鉴权；仍要求 `ENTRY:MGMT + MENU:ASSET:READ`。

## 4. UI 映射规则
- `VALIDITY_RANGE_VIOLATION`：量程越界，提示采样值超出可信画像。
- `VALIDITY_PROFILE_MISSING`：画像缺失，提示按保守策略降权。
- `TIMELINESS_DELAYED`：时效延迟，提示 event/device/recv 时间口径异常。
- `BACKFILL_DATA` 或 `isBackfill=true`：补偿回传，独立显示补偿标识。
- 未知 flag 兼容展示原始编码，并提示为后端扩展质量标志。

## 5. 实现内容
- 新增 `DataQualityIndicator.vue`，支持 `compact / table / detail` 三种密度。
- 替换 `TrendAnalyticsView.vue` 中分散的 DQ badge、flags 文本和补偿标识。
- 点位上下文增加五维评分条与告警置信度系数。
- 明细表中将 raw flags 转为可扫描标签组，保留 tooltip 说明。

## 6. 验收与测试
- `npm run typecheck`
- `npm run build`
- 默认查询 `DEV-001 / PRESSURE`，确认 DQ 等级、分数、flags、补偿标识正常展示。
- 使用 `dqLevel=C/D`、`isBackfill=true`、`minScore/maxScore` 等后端过滤时，前端标识与响应一致。
- 检查 null score、空 flags、未知 flag、D 级低分、补偿数据等边界状态。
