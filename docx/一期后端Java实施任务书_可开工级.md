# 一期后端 Java 实施任务书（可开工级）

## 1. 输入依据
- [地下管网数字化健康监测系统_一期前后端任务拆分汇总.md](地下管网数字化健康监测系统_一期前后端任务拆分汇总.md)
- [地下管网一体化监测运维与应急指挥平台工程级设计方案_审查修订版_v3.md](地下管网一体化监测运维与应急指挥平台工程级设计方案_审查修订版_v3.md)
- [项目需求精简版.md](项目需求精简版.md)

说明：本任务书严格按一期范围组织，后端实现语言固定为 Java。

## 2. A-01 统一菜单与平台边界表（后端落地版）

| 平台编码 | 平台名称 | 路由前缀 | 菜单权限码前缀 | 是否允许写操作 | 平台边界说明 | 后端归属服务 |
|---|---|---|---|---|---|---|
| PORTAL | 平台选择门户 | /portal | portal:* | 否（仅导航类） | 仅负责登录态、入口导航、跨平台跳转、单点退出、待办汇总；不承载业务编辑页面 | auth-center, portal-gateway |
| MGMT | 综合管理平台 | /mgmt | mgmt:* | 是 | 负责主数据建档、台账配置、规则配置、治理配置、日常运维配置 | master-data, gis-service, rule-service, config-service |
| EMGC | 应急指挥平台 | /emgc | emgc:* | 是 | 负责事件处置、联动调度、态势展示、工单闭环 | incident-service, workorder-service, notify-service |
| DIAG | 智能诊断中枢 | /diag | diag:* | 是（受限） | 负责模型注册、版本、灰度、回退、漂移监控、人工复核；默认脱敏访问 | model-gateway, feature-view-service |
| SUPPORT | 平台支撑层（不可见入口） | /api | support:* | 是（平台内部） | 工程底座，不作为第四入口；提供统一认证、主数据、GIS、接入、时序、告警/事件/工单、可观测能力 | 所有基础域服务 |

### 2.1 菜单归属冻结规则
1. 任一菜单仅允许归属一个业务平台，禁止多平台重复挂载。
2. 菜单权限码采用 平台前缀:域:动作 规范，例如 mgmt:asset:write。
3. 写操作只允许在 MGMT、EMGC、DIAG 出现，PORTAL 仅允许 read/nav/logout。
4. 平台支撑层 API 只能经网关暴露，不直接暴露独立前端入口。
5. 权限模型必须同时校验：入口权限 + 菜单权限 + 数据范围 + topic 订阅范围。

## 3. 一期后端 Java 服务分层与模块边界

## 3.1 架构分层
- 接入层：API Gateway、WebSocket Gateway、统一鉴权与限流。
- 领域层：认证与权限、主数据、GIS、设备接入、时序、告警、事件、工单、通知、模型治理。
- 平台能力层：审计、追踪、配置中心、任务调度、消息中间件、对象存储。
- 数据层：关系库、时序库、缓存、消息总线、日志与指标存储。

## 3.2 一期必建微服务清单
- auth-center：OIDC 集成、Token 刷新轮换、吊销、旁路账号。
- api-gateway：鉴权、参数校验、限流、黑白名单、高风险接口审计。
- permission-service：RBAC + 数据范围 + 订阅范围联合校验。
- master-data-service：node/segment/facility/device 对象链与版本控制。
- gis-service：bbox 查询、对象点查、坐标转换、2D 主入口支撑。
- ingest-service：统一入站 DTO、协议适配、边缘补偿接入。
- tsdb-writer-service：时序写入、补偿写入、失败重试、写入耗时埋点。
- dq-service：数据质量评分与标签输出。
- alert-engine-service：规则引擎、去重、抑制、升级。
- incident-service：事件化、事件状态机、人工确认流。
- workorder-service：工单状态机与闭环回写。
- notify-service：稳定业务事件通知、多通道重试。
- ws-push-service：WSS 鉴权、心跳、ack、补发。
- model-gateway-service：模型注册、版本、灰度、回退、规则兜底。
- feature-view-service：脱敏特征视图与限时授权。
- audit-trace-service：统一审计与 trace_id 透传。

## 4. 技术栈分析与建议（Java）

## 4.1 语言与框架
- Java 21 LTS：长期支持，虚拟线程能力可用于高并发 I/O 场景。
- Spring Boot 3.x：统一应用框架。
- Spring Cloud Gateway：统一网关能力。
- Spring Security + OAuth2 Resource Server/Client：OIDC、PKCE、Token 校验。
- Spring Authorization Server（或对接企业现有 IdP）：统一认证中心。

## 4.2 数据与存储
- PostgreSQL 16 + PostGIS：主数据、空间数据、事务数据统一管理。
- TimescaleDB：时序采样、聚合视图、保留策略。
- Redis：权限缓存、令牌撤销缓存、幂等键、限流计数。
- Kafka：告警、事件、工单、通知的异步解耦与回放。
- 对象存储（MinIO 或 S3 兼容）：附件、审计归档、导出文件。

## 4.3 工程与可靠性
- MyBatis-Plus 或 JOOQ：复杂查询与可控 SQL。
- Flyway：数据库版本管理。
- Outbox + Relay（可结合 Debezium CDC）：保证事务消息一致性。
- Resilience4j：重试、限流、熔断、隔离。
- XXL-Job 或 Quartz：补偿任务、清理任务、周期校验。

## 4.4 可观测与安全
- OpenTelemetry：trace_id 全链路透传。
- Prometheus + Grafana：指标与 SLO 看板。
- Loki/ELK：结构化日志检索。
- Vault 或 KMS：密钥与证书管理。
- TLS 1.2+ 全链路加密，关键接口启用签名与重放防护。

## 4.5 测试与交付
- JUnit 5 + Mockito：单元测试。
- Testcontainers：数据库、Kafka、Redis 集成测试。
- WireMock：外部依赖模拟。
- OpenAPI 驱动联调：前后端契约优先。
- 容器化 + CI/CD：镜像构建、扫描、灰度发布、回滚。

## 5. 对应 B-01 到 B-28 的可开工任务包

## 5.1 P0 口径冻结与骨架（第 1 周）
- T1：A-01 菜单边界配置中心化（数据库 + 配置缓存 + 查询 API）。
- T2：统一 API 响应、错误码、分页结构与 OpenAPI 基础规范。
- T3：对象链主键与字段字典冻结（node_id、segment_id、device_id 等）。
- T4：权限矩阵结构（角色、区域、topic）与拦截器骨架。
- T5：trace_id、event_time、recv_time、is_backfill 字段规范落库。

验收口径：冻结文档评审通过，契约示例接口可跑通，字段不可再随意变更。

## 5.2 P1 主链路打通（第 2-4 周）
- T6：OIDC + PKCE 登录、Token 刷新轮换、吊销收敛。
- T7：主数据服务 + GIS 查询服务 + 坐标转换服务。
- T8：设备接入、统一 DTO、时序写入、补偿写入、质量评分。
- T9：规则引擎、告警去重/抑制/升级、事件化服务。
- T10：工单闭环、通知服务、WebSocket 推送与 ack 补发。
- T11：Outbox、幂等键、死信处理，保障最终一致性。

验收口径：资产/GIS 到 告警/事件/工单/回写 全链路打通，关键链路可追踪可回放。

## 5.3 P2 治理与运维增强（第 5-6 周）
- T12：主数据版本并发控制、漂移管理、边缘断网补偿细化。
- T13：算法脱敏特征视图、模型网关一期治理框架。
- T14：统一审计报表，支持应急旁路单独出报。
- T15：SLO 看板与告警策略（入库时延、推送时延、工单触达）。

验收口径：权限边界、审计闭环、降级策略和运维可观测全部具备。

## 6. 数据模型最小集合（一期）
- 平台与权限：user_account, role, permission, role_permission, user_scope, topic_scope。
- 主数据对象链：node, segment, facility, device, object_relation, object_version。
- 设备与时序：device_heartbeat, ingest_batch, ts_metric, ts_write_log, calibration_profile。
- 告警闭环：alert_rule, alert_record, incident, work_order, notify_record。
- 可靠性与治理：outbox_event, idempotent_record, dead_letter, model_registry, model_version。
- 审计与链路：audit_log, security_audit, trace_snapshot。

## 7. API 契约最小集合（一期）
- 认证：/api/auth/login, /api/auth/refresh, /api/auth/revoke, /api/auth/emergency-login。
- 菜单与边界：/api/platforms, /api/menus, /api/menu-boundaries。
- 主数据：/api/master/nodes, /api/master/segments, /api/master/devices。
- GIS：/api/gis/bbox-query, /api/gis/object/{id}, /api/gis/convert-srid。
- 接入：/api/ingest/metrics, /api/ingest/backfill。
- 告警与事件：/api/alerts, /api/incidents, /api/incidents/{id}/confirm。
- 工单与通知：/api/workorders, /api/workorders/{id}/dispatch, /api/notifications/send。
- 模型治理：/api/models/register, /api/models/{id}/gray, /api/models/{id}/rollback。

## 8. 一期验收清单
1. 平台边界：Portal 不承载业务编辑页，支撑层不作为第四入口。
2. 主链路：资产/GIS -> 接入 -> 时序 -> 告警 -> 事件 -> 工单 -> 回写 全链路可验证。
3. 权限：入口、菜单、数据范围、订阅范围联合生效。
4. 一致性：Outbox、幂等、死信策略可演练。
5. 安全：OIDC、Token 轮换、吊销、旁路审计全部通过。
6. 可观测：trace_id 全链路贯通，SLO 指标可视化。
7. 回退：模型调用失败可自动回退到规则链，不影响告警闭环。

## 9. 实施建议
1. 先开一次口径冻结会，只确认边界和字段，不讨论页面细节。
2. 先做契约和状态机，再做页面联调，减少返工。
3. 对高风险接口（批量导出、批量派单、模型发布）单独做风控与审计。
4. 每周固定一次链路回放演练，避免只看单服务单点成功。
