# A/B 系列任务与项目需求符合性分析报告

> 对照《项目需求精简版》与《工程级设计方案（审查修订版 V3）》逐项核查

---

## 一、总体结论

| 维度 | 符合度 | 说明 |
|------|--------|------|
| **工程骨架完整性** | ✅ 高度符合 | 一期后端代码已形成"不可被二期推翻"的工程骨架 |
| **安全基线** | ✅ 高度符合 | Token 生命周期/吊销/轮换/旁路/Gateway/RBAC 全部落地 |
| **数据治理** | ✅ 高度符合 | dq_score 公式/标定/坐标转换/主数据版本控制都已实现 |
| **告警-事件-工单闭环** | ✅ 高度符合 | 去重/抑制/升级/事件化/Outbox/幂等/工单/通知/回写/WebSocket 全链路已通 |
| **模型治理框架** | ✅ 高度符合 | 注册/版本/灰度/回退/规则兜底/特征脱敏/审计已到位 |
| **运维工程化** | ⚠️ 部分待补充 | 代码具备基础能力，但 Runbook/CI-CD 门禁/容器化部署/IaC 等运维文档和流程尚未在仓库中体现 |
| **前端任务** | ❌ 未实现 | 项目中无前端代码目录，F 系列任务全部待开发 |

---

## 二、《工程级设计方案 V3》逐章符合性

### 第 3 章：统一口径与总体架构

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| 统一口径：SSO → Portal → 三平台 | `a01-menu-boundary.json` 定义 PORTAL/MGMT/EMGC/DIAG/SUPPORT 五平台，`allowWrite=false` 明确 Portal 不承载写操作 | ✅ |
| 平台支撑层不作为第四入口 | SUPPORT 的 `routePrefix=/api`，`description="工程底座，不作为第四入口"` | ✅ |
| 2D 为主入口，3D 仅增强 | 后端 GIS 服务以 2D 空间查询为核心，无 3D 强依赖设计 | ✅ |
| 所有业务结果落到 segment_id 或 node_id | `IncidentEntity`/`WorkOrderEntity`/`ModelResultEntity` 均有 `segmentId`/`nodeId` 字段，且非空约束 | ✅ |

### 第 4 章：安全与权限基线

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| **4.1 Token 生命周期** — Access 15-30分钟，Refresh 7-14天，Rotation | `LocalTokenService` 默认 AT=1800s、RT=1209600s，refresh 后旧 token 立即 `ROTATED`，重放检测写 `security_audit` | ✅ |
| **4.1 吊销机制** — 退出/禁用/权限变更即时吊销 | `TokenRevocationService` 实现 3 种吊销路径，`user_account.token_valid_after` 实现全平台收敛 | ✅ |
| **4.2 SSO 高可用** — 应急旁路、最小权限、审计 | `EmergencyAccessService` 实现旁路登录、激活/撤销、`BREAK_GLASS_COMMAND` 角色最小权限、独立 `BREAK_GLASS_*` 审计 | ✅ |
| **4.2.1 应急令牌短期有效** | 旁路 AT=600s、RT=1800s，过期自动失效 | ✅ |
| **4.2.1 权限最小化** | `BREAK_GLASS_COMMAND` 仅开放 `ENTRY:EMGC` + 5 条菜单权限，不开放 MGMT/DIAG/SUPPORT/高危配置 | ✅ |
| **4.2.1 滥用检测** | 旁路全部操作写 `security_audit`，`AuthzGuardAspect` 旁路额外写 `BREAK_GLASS_OPERATION` | ✅ |
| **4.3 WebSocket** — 握手鉴权+消息层鉴权+Origin 校验+连接数+帧大小+心跳 | `WebSocketAuthHandshakeInterceptor` 握手鉴权、`WebSocketAuthChannelInterceptor` 消息层鉴权、`WebSocketPushProperties` 配置连接数/帧大小/心跳/空闲超时 | ✅ |
| **4.3 WebSocket 补偿** — last_ack_seq 补发 | `WebSocketAckEntity` 持久化 `last_ack_seq`，重连后按 `seq_no > last_ack_seq` 补发 | ✅ |
| **4.3 API Gateway** — 限流/鉴权/校验/审计 | `GatewayControlFilter` 实现路由策略匹配→黑白名单→Content-Type→Body 大小→分页校验→限流→高风险审计 | ✅ |
| **4.4 算法工程师数据边界** — 默认脱敏，限时授权 | `FeatureViewService` 默认返回 `MASKED` 视图（哈希 ID、粗粒度坐标），`DETAIL` 需限时 grant + 全量审计 | ✅ |
| **4.4 五角色权限矩阵** | `a04-authz-matrix.json` 覆盖平台管理员/区域调度员/巡检人员/算法工程师/领导只读，运行时 `AuthzGuardAspect` 联合校验 | ✅ |

### 第 5 章：分期交付边界

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| 一期 = 后续不能推翻的工程骨架 | 35 项 A+B 任务已形成完整后端骨架 | ✅ |
| 模型治理框架一期先做 | `ModelGatewayService` 已实现注册/版本/灰度/回退/规则兜底/审计 | ✅ |
| 模型接口预留，二期接入算法 | 一期用 Java 模拟适配器，支持 `simulateFailure/simulateLatencyMs`，二期替换真实模型 | ✅ |

### 第 6 章：实时链路与 SLO

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| **6.1 trace_id / event_time / recv_time 全链路** | `TraceIdFilter` HTTP 透传 MDC，`TsMetricEntity` 区分 `event_time/recv_time/device_time/trace_id`，`IngestBatch/AlertRecord/Incident/Outbox/AuditLog` 均携带 `trace_id` | ✅ |
| **6.2 边缘断网补偿** — batchNo/seqNo/originalSampleTime/is_backfill | `POST /api/ingest/backfill` 必填 `batchNo/seqNo/originalSampleTime/isBackfill=true`，乱序可接受，重复幂等，冲突 409 | ✅ |
| **6.2 在线与补偿双口径** | `TsMetricEntity.is_backfill` 标记，`ts_metric` 可按 `is_backfill` 过滤导出 | ✅ |

### 第 7 章：数据治理

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| **7.1 主数据对象链** — segment_id / node_id 唯一链 | `object_relation` 表定义 `SEGMENT_START_NODE/SEGMENT_END_NODE/SEGMENT_FACILITY/NODE_FACILITY/FACILITY_DEVICE`，`ObjectChainTraversalService` 遍历 | ✅ |
| **7.1 并发变更控制** — 乐观锁/审批/版本归档 | `@Version` JPA 乐观锁，`MasterDataChangeService` 实现提交→PENDING→approve→生效→`object_version` 归档 | ✅ |
| **7.2 dq_score 公式** — 100分制，5 维加权 `0.30C+0.25V+0.20T+0.15K+0.10S` | `DataQualityScoringService.scoreMetric()` 第 131 行：`100.0 * ((0.30*C) + (0.25*V) + (0.20*T) + (0.15*K) + (0.10*S))` **与方案公式完全一致** | ✅ |
| **7.2 dq_score 分级** — A≥85, B≥70, C≥60, D<60 | `resolveLevel()` 方法阈值与方案完全一致 | ✅ |
| **7.2 告警置信度联动** — `alarm_conf_final = raw × (0.5 + 0.5 × dq/100)` | `DataQualityScoringService` 计算 `dqAlarmConfFactor`，`AlertRuleEngineService` 使用该因子降权 | ✅ |
| **7.2 DQ 与告警决策联动** — dq≥85 原始/70-85 升级上调/60-70 人工确认/＜60 不自动触发 | `AlertRuleEngineService` 决策分档 `TRIGGERED/REVIEW_REQUIRED/DQ_BLOCKED` 与方案对齐；`AlertCaseLifecycleService` 升级阈值上调 | ✅ |
| **7.3 标定与漂移** — 版本/有效期/修正/漂移检修工单 | `CalibrationManagementService` 实现版本化档案 `DRAFT/ACTIVE/EXPIRED/SUPERSEDED`、漂移复核、到期自动生成 `incident+work_order` | ✅ |
| **7.3 历史数据不覆盖** | `ts_metric.metric_value` 保持原始值，校正结果仅通过 `/api/calibration/metrics/{id}/corrected` 预览接口动态返回 | ✅ |
| **7.4 坐标与深度** — authority_srid/display_srid/geometry_2d/z_top/z_bottom/bury_depth | `NodeEntity` 完整包含 7 个坐标字段；`GisCoordinateService` 统一后端坐标转换（EPSG:4490↔3857）+ 深度校验 | ✅ |
| **7.4 坐标转换服务化** — 禁止前端私算 | `GisCoordinateService` 后端统一封装，前端调 `POST /api/gis/convert` | ✅ |
| **7.5 Feature View / 特征视图** | `FeatureViewService` 实现关键特征表、脱敏/明细切换、限时授权 | ✅ |
| **7.5 模型版本绑定** | `ModelVersionEntity` 记录 `version_no`，推理审计绑定版本，回退可追溯 | ✅ |

### 第 8 章：可靠性、运维与部署

| 设计要求 | 代码实现 | 符合 |
|----------|----------|------|
| **8.1 事件闭环** — 本地事务+Outbox+幂等键+死信 | `OutboxService`+`OutboxRelayService`+`IdempotentConsumerService`+`DeadLetterEntity` 已实现完整链路 | ✅ |
| **8.1 幂等键** — incident_id+action_type+version | 代码中 `IdempotentConsumerService` 采用相同键结构 | ✅ |
| **8.1 Outbox Relay** — event_id/aggregate/status/retry/dead | `OutboxEventEntity` 完整包含方案要求的全部字段，状态 `NEW→SENDING→SENT→FAILED→DEAD` 与方案一致 | ✅ |
| **8.1 通知只消费稳定事件** | `NotificationService` 白名单限定为 `WORK_ORDER_CREATED/DISPATCHED/...` 等稳定工单事件 | ✅ |
| **8.2 WebSocket 持久化 ack 补发** | `websocket_push_message` 持久化推送消息 + `websocket_ack.last_ack_seq` 补发 | ✅ |
| **8.2 Gateway 限流** | `GatewayRateLimitService` 单实例内存限流 + `GatewayControlFilter` 路由策略审计 | ✅ |
| **8.3 B-28 trace_id 贯穿** | `TraceIdFilter`(HTTP) + `WebSocketAuthChannelInterceptor` + MDC 注入日志上下文 + 多实体 trace_id 字段 | ✅ |
| **8.3 统一审计** | `UnifiedAuditService` 跨域统一 `audit_log`，覆盖登录/旁路/权限/工单/模型/Gateway 高风险，支持应急旁路专项报表 | ✅ |

---

## 三、《项目需求精简版》符合性

| 需求要点 | 代码实现 | 符合 |
|----------|----------|------|
| 泛在感知与云端深析平台 — 多维密集感知、云-边-端协同 | 后端实现设备接入（MQTT/MODBUS/NB_IOT 协议适配）、边缘补偿（batchNo/seqNo/is_backfill）、时序入库、数据质量评分 | ✅ |
| 数据质量 — 异常剔除、缺失补全 | `DataQualityScoringService` 完整性/有效性/时效性/一致性/稳定性五维评分，异常标记 `dq_flags` | ✅ |
| 泄漏诊断 — 模型框架 | `ModelGatewayService` 预留注册/版本/灰度/回退/规则兜底，二期接入 ST-GNN/贝叶斯 | ✅ |
| 数字孪生 — 3D 可视化 | 一期以 2D 为主，3D 占位；后端 GIS 空间索引已就绪 | ✅（一期范围内） |
| 实时监测与预警 — 感知-传输-决策闭环 | 设备→边缘→入库→DQ→告警→事件→工单→通知→WebSocket推送 **全链路已通** | ✅ |

---

## 四、发现的差距与建议

### 4.1 代码层符合但缺文档/流程的领域

| 领域 | 差距 | 方案要求章节 | 建议 |
|------|------|------------|------|
| **Runbook** | 代码能力已具备（如模型回退、旁路切换），但项目中无 Runbook 文档 | §8.3 | 补齐 10 类高频故障 Runbook |
| **CI/CD 门禁** | 无 CI/CD 配置文件（如 `.github/workflows`、Jenkinsfile） | §8.4 | 建立 CI/CD 流水线，含测试覆盖率、安全扫描、DB 迁移门禁 |
| **容器化部署** | 无 `Dockerfile`、`docker-compose.yml` 或 K8s manifest | §8.4-8.5 | 补齐容器化+部署 manifest |
| **IaC** | 无基础设施即代码文件 | §8.4 | 按实际部署环境补充 |
| **值班与演练** | 无值班制度文档 | §8.3.4 | 制定值班升级机制与季度演练计划 |

### 4.2 未实现的设计方案部分

| 领域 | 差距 | 方案要求章节 | 建议 |
|------|------|------------|------|
| **前端 F 系列** | 项目无前端代码目录，16 项前端任务全部待开发 | §5 | 启动前端项目，按文档排期推进 |
| **暴力破解防护** | 登录接口未见失败次数阈值/锁定逻辑 | §4.1 | 补齐登录限速+账号锁定 |
| **TLS/静态加密** | 开发模式下无 TLS 配置，密钥管理为 `application.yml` 明文配置项 | §4.3.3 | 生产部署时接入 Vault/KMS |
| **RPO/RTO 演练** | 代码层面无备份恢复能力验证 | §6.3 | 配合运维建立恢复演练机制 |
| **等保三级** | 代码层已覆盖安全审计/访问控制/加密传输预留，但无等保对照表 | §4.5.1 | 输出等保三级控制项对照文档 |
| **PIPL 个人信息保护** | 代码层脱敏能力已有（Feature View），但无个人信息清单文档 | §4.5.2 | 建立个人信息清单 |

### 4.3 实现超出方案预期的亮点

| 亮点 | 说明 |
|------|------|
| **dq_alarm_conf_factor** | 方案定义公式，代码不仅实现了公式计算还将因子持久化到 `ts_metric`，供告警引擎直接复用 |
| **告警 case 生命周期** | 方案要求去重/抑制/升级，代码额外实现了 `RECOVERED` 自动恢复扫描和 DQ-aware 升级阈值上调 |
| **工单 Outbox 事件** | 方案要求事件化，代码在工单每次状态变更都写 Outbox，通知服务可按需消费 |
| **坐标转换 JTS** | 方案只要求服务化转换，代码引入 JTS 库支持 WKT POINT/LINESTRING 的真实坐标转换 |

---

## 五、总体评价

> [!IMPORTANT]
> **A+B 系列已完成的 35 项后端任务与两份需求文档的技术要求高度一致。**
> 方案中关于安全基线、数据治理、告警闭环、模型治理、实时链路等核心工程要求，在代码中均有对应的完整实现，且关键公式（dq_score、alarm_conf_final）和状态机（告警/事件/工单）与方案保持一致。

> [!WARNING]
> **主要差距集中在两个方面：**
> 1. **前端任务（F 系列）完全未开始** — 16 项前端任务零实现
> 2. **运维工程化文档** — Runbook、CI/CD、容器化、IaC、值班制度等运维侧配套尚未落地
>
> 这两部分是方案要求的一期交付范围，建议尽快排期推进。
