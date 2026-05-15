# USCDIP Backend Bootstrap

该目录提供一期后端 Java 启动骨架，当前已实现：
- A-01 平台边界配置读取与查询 API
- A-02 统一对象主键与对象链字典（数据库版）
- A-03 坐标与深度字段冻结（GIS 字段规范、坐标转换、深度校验）
- A-04 权限模型与数据范围矩阵（RBAC + 数据范围 + topic 订阅范围）
- A-05 实时链路指标口径表（traceId / eventTime / recvTime / isBackfill / lastAckSeq 统一规范）
- A-06 事件状态机与工单状态机（告警/事件/工单三套状态枚举与流转规则）
- A-07 API 错误码与异常响应规范（统一错误码、全局异常处理、traceId 透传）
- B-11 设备台账与心跳接口（设备注册、心跳上报、在线状态计算）
- B-12 统一入站 DTO 与协议适配骨架（统一 DTO、协议适配、接入批次落库）
- B-13 TSDB 写入与补偿写入服务（在线写入、补偿写入、写入日志与重试）
- B-14 数据质量评分服务（dq_score / dq_flags / 查询接口）
- B-15 标定与漂移管理接口（标定版本、漂移复核、到期提醒与校正预览）
- B-16 边缘断网补偿接口（batchNo / seqNo / originalSampleTime、乱序回传、重复幂等与冲突拦截）
- B-17 告警规则引擎一期骨架（阈值规则、组合规则、自动/手动评估与告警记录查询）
- B-18 告警去重 / 抑制 / 升级服务（alert_policy / alert_case、去重窗口、抑制窗口、升级与恢复扫描）
- B-19 事件化服务（incident 事件化、最小 incident API、人工确认与自动恢复）
- B-20 Outbox 与 Relay（outbox_event、事务事件写入、Relay 认领发送、失败重试与死信）
- B-21 幂等键与死信处理（idempotent_record、dead_letter、工单创建幂等消费）
- B-22 工单服务（创建、派单、接单、转派、完成、关闭、回写与数据范围过滤）
- B-23 通知服务（稳定工单事件消费、多通道模拟发送、失败重试与死信）
- B-24 WebSocket 推送网关（STOMP 推送、握手鉴权、订阅授权、ack 与断线补发）
- B-25 模型网关一期框架（模型注册、版本、灰度、回退、规则兜底与审计）
- B-26 特征视图与算法脱敏接口（默认脱敏特征视图、限时明细授权与访问审计）
- B-27 统一审计日志服务（跨域审计主表、查询接口与应急旁路专项报表）
- B-28 Trace 与链路埋点中间件（HTTP、Outbox、通知、WebSocket traceId 透传）
- F-11 实时告警列表 + WebSocket 客户端（STOMP 客户端，断线重连，心跳包及基于 traceId/eventId 去重，并在前端实现 SaaS 化 Dashboard 风格交互 UI）
- F-13 派单与回写弹窗（工单详情页接入派单、转派、SLA 设置和误报/漏报独立回写，并对齐 B-22 后端契约）
- F-14 数据质量标识 UI（趋势页统一展示 dq_score、dq_flags、补偿回传、五维评分与告警置信度降权提示）
- F-15 3D 占位与降级页（复用 B-10 GIS 数据生成 Three.js 线框占位，WebGL 或 GIS 不可用时保留上下文回退 2D）
- F-16 模型治理页一期占位（智能诊断中枢接入模型注册表、版本态势、灰度状态、复核预留和真实回退操作）

当前项目已添加数据库能力。

## 快速启动
1. 进入 backend 目录。
2. 首次或切换 JDK 后执行 mvn clean spring-boot:run。
3. 访问接口：
   - GET /api/auth/login
   - GET /api/auth/login-url
   - POST /api/auth/callback
   - POST /api/auth/refresh
   - POST /api/auth/admin/users/{userId}/disable
   - POST /api/auth/admin/users/{userId}/permissions/revoke
   - GET /api/menu-boundaries
   - GET /api/platforms
   - GET /api/platforms/{platformCode}
   - GET /api/realtime-link/spec
   - GET /api/object-dictionary
   - GET /api/object-dictionary/page?page=1&pageSize=3
   - GET /api/object-chain/segment/{segmentId}
   - GET /api/object-chain/node/{nodeId}
   - GET /api/master/nodes
   - GET /api/master/segments
   - GET /api/master/facilities
   - GET /api/master/devices
   - GET /api/device-ledger/devices
   - GET /api/device-ledger/devices/{deviceId}
   - POST /api/device-ledger/devices/register
   - POST /api/device-ledger/devices/{deviceId}/heartbeat
   - POST /api/ingest/metrics
   - POST /api/ingest/adapt/{protocolType}
   - POST /api/ingest/backfill
   - GET /api/ingest/batches
   - GET /api/ingest/batches/{batchId}
   - GET /api/ingest/write-logs
   - GET /api/ingest/write-logs/{writeLogId}
   - POST /api/ingest/write-logs/{writeLogId}/retry
   - GET /api/dq/scores
   - GET /api/dq/scores/{sourceRecordId}
   - POST /api/alerts/evaluate
   - GET /api/alerts
   - GET /api/alerts/{alertId}
   - GET /api/alerts/rules
   - GET /api/workorders
   - GET /api/workorders/{workOrderId}
   - POST /api/workorders
   - POST /api/workorders/{workOrderId}/dispatch
   - POST /api/workorders/{workOrderId}/accept
   - POST /api/workorders/{workOrderId}/transfer
   - POST /api/workorders/{workOrderId}/complete
   - POST /api/workorders/{workOrderId}/close
   - POST /api/workorders/{workOrderId}/writeback
   - GET /api/notifications
   - GET /api/notifications/{notificationId}
   - POST /api/notifications/consume-outbox
   - POST /api/notifications/{notificationId}/retry
   - GET /api/models
   - GET /api/models/{modelCode}
   - POST /api/models/register
   - POST /api/models/{modelCode}/versions
   - POST /api/models/{modelCode}/versions/{versionNo}/gray
   - POST /api/models/{modelCode}/versions/{versionNo}/activate
   - POST /api/models/{modelCode}/rollback
   - POST /api/models/{modelCode}/infer
   - GET /api/feature-views/metrics
   - GET /api/feature-views/model-results
   - POST /api/feature-views/grants
   - POST /api/feature-views/grants/{grantId}/revoke
   - GET /api/feature-views/audits
   - GET /api/audit-logs
   - GET /api/audit-logs/{auditId}
   - GET /api/audit-logs/break-glass-report
   - GET /api/traces
   - GET /api/traces/{traceId}
   - POST /api/calibration/devices/{deviceId}/profiles
   - GET /api/calibration/devices/{deviceId}/profiles
   - GET /api/calibration/devices/{deviceId}/profiles/active?metricCode=PRESSURE
   - POST /api/calibration/devices/{deviceId}/drift-checks
   - GET /api/calibration/devices/{deviceId}/drift-checks
   - GET /api/calibration/metrics/{sourceRecordId}/corrected
   - POST /api/master/changes
   - GET /api/master/changes
   - GET /api/master/changes/{requestId}
   - POST /api/master/changes/{requestId}/approve
   - POST /api/master/changes/{requestId}/reject
   - GET /api/gis/field-spec
   - POST /api/gis/convert
   - POST /api/gis/depth/validate
   - GET /api/authz/matrix-spec
   - GET /api/authz/matrix
   - GET /api/authz/users/{userId}/snapshot
   - GET /api/authz/users/{userId}/topics
   - POST /api/authz/check
   - GET /api/error-codes
   - GET /api/state-machines
   - GET /api/state-machines/{machineType}/allowed/{status}
   - POST /api/authz/topics/check
   - POST /api/authz/topics/subscribe
   - GET /api/auth/me
   - POST /api/auth/logout
   - GET /v3/api-docs
   - GET /swagger-ui/index.html

## 常见问题
- 报错 `UnsupportedClassVersionError`（如 class file version 65.0）：
   1. 确认 `java -version` 为 17。
   2. 执行 `mvn clean compile` 清理旧产物并重编译。
   3. 再执行 `mvn spring-boot:run`。

## 当前实现范围
- A-01 边界配置文件：src/main/resources/a01-menu-boundary.json
- 统一响应结构：success/data/error/traceId/timestamp
- B-01 错误码枚举：INVALID_PARAMETER、UNAUTHORIZED、FORBIDDEN、RESOURCE_NOT_FOUND、DATA_SCOPE_EMPTY、IDEMPOTENT_CONFLICT 等
- B-01 OpenAPI 草案：Controller 注解 + /v3/api-docs + Swagger UI
- B-01 分页对象：PageResponse(items/total/page/pageSize/totalPages/hasNext)
- B-02 OIDC 登录集成：默认本地可关闭 + 按需启用 OAuth2 Login + PKCE + 用户信息同步 + 统一登出
- B-03 Access / Refresh Token 刷新轮换：本地 access token、refresh token rotation、重放检测、最小安全审计
- B-04 Token 吊销与权限收敛：当前 session 登出吊销、账号禁用失效、权限重大变更全量收敛
- B-05 应急旁路账号与审计：本地受控旁路账号、独立短期 token、激活/撤销和审计查询
- B-06 Gateway 校验与限流：数据库驱动的应用内 Gateway 过滤、黑白名单、输入校验、单实例限流与高风险审计
- B-07 RBAC + 数据范围 + 订阅范围：运行时声明式鉴权、对象范围绑定、业务查询过滤与 topic 模拟订阅
- B-08 主数据表与对象链服务：主数据只读 API、对象链关系真值表、基于 relation 的链路遍历
- B-09 主数据版本与并发控制：变更申请、单级审批生效、主表 optimistic lock、版本快照与主数据审计
- B-10 GIS 空间查询与坐标转换服务：bbox 检索、对象点查、统一 WKT 坐标转换与空间索引真值
- B-11 设备台账与心跳接口：设备注册 Upsert、心跳历史、在线/预警/离线三态计算
- B-12 统一入站 DTO 与协议适配骨架：统一 eventTime/recvTime/deviceTime、协议适配示例、接入批次与记录真值落库
- B-13 TSDB 写入与补偿写入服务：在线/补偿写入、写入日志、失败重试与 Timescale 目标口径
- B-14 数据质量评分服务：自动评分、dq_flags、查询过滤与告警置信度降权因子
- B-15 标定与漂移管理接口：版本化标定档案、漂移复核、到期提醒工单、校正预览
- B-16 边缘断网补偿接口：`batchNo / seqNo / originalSampleTime` 必填、乱序可接受、重复幂等与冲突重复直接拒绝
- B-17 告警规则引擎一期骨架：阈值/组合规则、DQ 降权置信度、自动触发与手动回放评估
- B-20 Outbox 与 Relay：事务内写 `outbox_event`，定时 Relay 认领发送，支持失败重试和 DEAD 死信状态
- B-21 幂等键与死信处理：消费侧以 `incident_id + action_type + version` 生成幂等键，记录 `idempotent_record`，失败超过阈值写入 `dead_letter`
- B-22 工单服务：支持一事件多工单、工单状态流转、回写记录、对象范围继承与工单 Outbox 事件
- B-23 通知服务：只消费稳定工单事件，生成 `notification_message / notification_delivery`，支持多通道模拟发送、重试与死信
- B-24 WebSocket 推送网关：`/ws/push` STOMP 推送、握手鉴权、订阅授权、心跳、ack 与断线补发
- B-25 模型网关一期框架：注册模型、管理版本、灰度发布、激活/回退、模拟推理、失败超时规则兜底与模型域审计
- B-26 特征视图与算法脱敏接口：算法工程师默认只能查询 `MASKED` 特征，`DETAIL` 明细需限时授权并全量审计
- B-27 统一审计日志服务：登录、旁路、权限变更、派单、模型回退和高风险访问统一进入 `audit_log`
- B-28 Trace 与链路埋点中间件：HTTP、Outbox、通知、WebSocket 推送统一透传 `traceId`
- F-13 前端联调：`/emgc/workorders/:id` 已接入派单、转派、回写弹窗；派单请求体必须包含非空 `assignee`，回写独立调用 `/writeback`，不复用关闭工单语义
- F-14 前端联调：`/mgmt/trends` 已接入统一数据质量标识组件，展示 `dqScore / dqLevel / dqFlags / isBackfill / dqAlarmConfFactor` 和五维评分
- F-15 前端联调：`/mgmt/gis/3d` 已接入 3D 占位与降级页；复用 `GET /api/gis/objects/bbox` 与 `GET /api/gis/objects/{objectType}/{objectId}`，失败时带 query 回退 `/mgmt/gis`
- F-16 前端联调：`/diag/models` 已接入模型治理一期页；复用 `GET /api/models`、`GET /api/models/{modelCode}` 与 `POST /api/models/{modelCode}/rollback`，注册、创建版本、灰度调整、激活、推理和 B-26 复核明细仍作为后续入口预留
- 平台查询接口：按平台编码读取边界定义
- A-02 对象链实体：node、segment、facility、device、incident、work_order、model_result
- A-02 对象链接口：按 segment_id 和 node_id 查询完整对象链
- A-03 字段冻结：authority_srid、display_srid、geometry_2d、z_top、z_bottom、bury_depth、elevation_ref
- A-03 坐标转换：统一后端服务化处理（禁止前端/导入工具私算）
- A-04 约束表达：access = entryPermission && menuPermission && dataScope && topicScope
- A-04 角色覆盖：平台管理员、区域调度员、巡检人员、算法工程师、领导只读
- A-04 测试约束：跨区订阅拒绝、巡检仅本人任务、算法默认脱敏视图
- A-05 实时链路口径：`trace_id / event_time / recv_time / is_backfill / last_ack_seq`

## A-03 接口说明

### 1) GIS 字段规范
- GET /api/gis/field-spec
- 用途：返回 A-03 字段定义、必填约束、字段含义和样例。

### 2) 坐标转换服务
- POST /api/gis/convert
- 请求体示例：
   - {"authoritySrid":"EPSG:4490","displaySrid":"EPSG:3857","geometry2d":"POINT(120.1533 30.2741)"}
- 说明：当前版本对 JTS 可解析的 WKT 提供服务化转换，一期已覆盖 `POINT / LINESTRING` 的 4490<->3857 转换；其他 SRID 组合返回 passthrough 结果。

### 3) 深度字段一致性校验
- POST /api/gis/depth/validate
- 请求体示例：
   - {"zTop":2.50,"zBottom":-1.20,"buryDepth":3.70,"elevationRef":"MSL","tolerance":0.05}
- 规则：bury_depth 应满足 |z_top - z_bottom|，支持容差配置。

## A-05 接口说明

### 1) 实时链路指标口径表
- GET /api/realtime-link/spec
- 用途：返回 A-05 统一字段口径、链路阶段要求与衍生指标定义。
- 当前冻结字段：
   - `trace_id / traceId`
   - `event_time / eventTime`
   - `recv_time / recvTime`
   - `is_backfill / isBackfill`
   - `last_ack_seq / lastAckSeq`
- 说明：前端接收与地图渲染阶段当前仅冻结口径，不在本仓库内验收。

## B-01 契约说明

### 1) 统一响应体
- 成功响应示例字段：
   - success: true
   - data: 业务数据
   - error: null
   - traceId: 请求链路标识
   - timestamp: 服务端时间戳（ISO-8601）

### 2) 统一错误码
- 参数错误：INVALID_PARAMETER
- 未登录/认证失败：UNAUTHORIZED
- 权限不足：FORBIDDEN
- 资源不存在：RESOURCE_NOT_FOUND / PLATFORM_NOT_FOUND / SEGMENT_NOT_FOUND / NODE_NOT_FOUND / USER_NOT_FOUND
- 设备台账错误：DEVICE_NOT_FOUND / DEVICE_RELATION_INVALID / DEVICE_HEARTBEAT_INVALID
- 接入错误：INGEST_PROTOCOL_UNSUPPORTED / INGEST_DEVICE_NOT_FOUND / INGEST_PAYLOAD_INVALID / INGEST_BATCH_NOT_FOUND
- 标定与漂移错误：CALIBRATION_PROFILE_NOT_FOUND / CALIBRATION_PROFILE_CONFLICT / CALIBRATION_DRIFT_EVALUATION_INVALID / CALIBRATION_CORRECTION_PREVIEW_INVALID
- 幂等冲突：IDEMPOTENT_CONFLICT
- 内部异常：INTERNAL_ERROR

### 3) 分页结构示例
- 端点：GET /api/object-dictionary/page?page=1&pageSize=3
- 返回 data 为 PageResponse：
   - items: 当前页列表
   - total: 总记录数
   - page: 当前页（从 1 开始）
   - pageSize: 每页条数
   - totalPages: 总页数
   - hasNext: 是否有下一页

### 4) TraceId 透传规则
- 请求头可携带 X-Trace-Id。
- 若未携带，后端自动生成 UUID。
- 响应头固定回传 X-Trace-Id，响应体 traceId 与其一致。

### 5) OpenAPI 访问
- 原始文档：GET /v3/api-docs
- 可视化页面：GET /swagger-ui/index.html

## B-02 OIDC 登录集成说明

### 1) 目标能力
- 接入 OIDC（一期默认 Keycloak）
- 浏览器端走标准授权码流程，后端开启 PKCE 参数
- 默认开发模式可关闭 OIDC，保证本地无 Keycloak 时也能正常启动
- 登录成功后自动同步用户到 user_account、rbac_user_role、user_data_scope
- 提供统一登出接口，返回 OIDC 提供方退出地址

### 2) 必需依赖
- spring-boot-starter-security
- spring-boot-starter-oauth2-client
- spring-boot-starter-oauth2-resource-server
- spring-security-oauth2-jose

### 3) 配置项
- BACKEND_OIDC_ENABLED（默认 false，true 时启用 OIDC 登录链路）
- OIDC_ISSUER_URI（默认 http://localhost:8081/realms/uscdip）
- OIDC_CLIENT_ID（默认 uscdip-backend）
- OIDC_CLIENT_SECRET（默认 change-me）
- OIDC_REGISTRATION_ID（默认 keycloak）
- OIDC_POST_LOGOUT_REDIRECT_URI（默认 http://localhost:8080/swagger-ui/index.html）
- OIDC_DEFAULT_ROLE（默认 LEADER_READONLY）
- OIDC_DEFAULT_REGION（默认 REGION-HZ）

### 4) 认证接口
- GET /api/auth/login：正式登录入口描述接口，返回 enabled、registrationId、authorizationUrl
- GET /api/auth/login-url：兼容别名，响应与 /api/auth/login 一致
- POST /api/auth/callback：前端回调页携带 code/state/redirectUri 调后端，交换本地 access/refresh token
- POST /api/auth/refresh：携带 refresh token 执行 rotation，返回新的 access/refresh token
- GET /api/auth/me：OIDC 开启后返回当前登录用户权限快照；OIDC 关闭时返回 OIDC_DISABLED
- POST /api/auth/logout：标准会话与应急旁路会话都支持吊销当前 session；OIDC 会话额外返回 providerLogoutUrl
- POST /api/auth/emergency/login：应急旁路账号登录，返回短期 access/refresh token
- POST /api/auth/emergency/accounts/{accountId}/activate：管理员激活应急旁路账号
- POST /api/auth/emergency/accounts/{accountId}/revoke：管理员撤销应急旁路账号
- GET /api/auth/emergency/audit：管理员查询独立应急审计记录

### 5) 默认本地模式
- 默认 `BACKEND_OIDC_ENABLED=false`，不会触发 OIDC issuer discovery。
- 此模式下，规范与矩阵说明接口保持匿名可访问，真实业务查询与管理接口仍需本地 access token。
- 此模式下：
   - GET /api/auth/login 与 GET /api/auth/login-url 返回 `enabled=false`
   - 未携带本地 access token 时，GET /api/auth/me 与 POST /api/auth/logout 返回 `503 + OIDC_DISABLED`
   - 携带失效的本地 access token 时，GET /api/auth/me 与 POST /api/auth/logout 返回 `401`
   - `POST /api/auth/emergency/login` 仍可独立使用，不依赖 OIDC provider

### 6) 启用 OIDC 联调
- 启动示例：
   - `mvn spring-boot:run -Dspring-boot.run.arguments="--backend.oidc.enabled=true"`
- 启用后若 `OIDC_ISSUER_URI` 不可达，应用会在启动阶段失败，这是预期行为。
- Keycloak Client 建议使用标准授权码流程并开启 PKCE
- Redirect URI 建议配置：
   - http://localhost:8080/login/oauth2/code/keycloak

### 7) 用户同步规则
- 首次成功 OIDC 登录后，后端按 `issuer + sub` 生成稳定的 `OIDC-*` user_id。
- 用户名优先取 `preferred_username`，其次 `email`、`name`。
- 角色优先取 OIDC claims 中的角色信息；若为空，则回退到 `OIDC_DEFAULT_ROLE`。
- 区域优先取 `region_scopes`/`region`；若为空，则回退到 `OIDC_DEFAULT_REGION`。

## B-03 Access / Refresh Token 刷新轮换说明

### 1) 目标能力
- OIDC 只负责身份建立，后端自签发本地 access token / refresh token。
- Access Token 默认有效期 30 分钟，仅用于调用业务接口。
- Refresh Token 默认有效期 14 天，启用 rotation；刷新成功后旧 token 立即失效。
- 旧 refresh token 重放会被拒绝，并写入 `security_audit`。

### 2) 新增配置项
- LOCAL_ACCESS_TOKEN_SECRET：本地 access token HMAC 密钥
- LOCAL_ACCESS_TOKEN_TTL_SECONDS：本地 access token 有效期，默认 1800
- LOCAL_REFRESH_TOKEN_TTL_SECONDS：refresh token 有效期，默认 1209600
- OIDC_STATE_TTL_SECONDS：OIDC state 有效期，默认 300
- REFRESH_TOKEN_HASH_ALGORITHM：refresh token 哈希算法，默认 SHA-256
- LOCAL_TOKEN_ISSUER：本地 access token issuer，默认 uscdip-backend

### 3) B-03 接口
- GET /api/auth/login?redirectUri=<front-end-callback>：生成带 PKCE challenge 和 state 的授权地址
- GET /api/auth/login-url?redirectUri=<front-end-callback>：兼容别名
- POST /api/auth/callback：请求体
   - {"code":"<oidc_code>","state":"<oidc_state>","redirectUri":"http://localhost:5173/auth/callback"}
- POST /api/auth/refresh：请求体
   - {"refreshToken":"<refresh_token>"}

### 4) B-03 响应要点
- `POST /api/auth/callback` 与 `POST /api/auth/refresh` 成功时都返回：
   - accessToken
   - accessTokenExpiresAt
   - refreshToken
   - refreshTokenExpiresAt
   - tokenType
   - userSnapshot
- 旧 refresh token 重放返回 `TOKEN_REFRESH_REPLAY_DETECTED`
- 被吊销 refresh token 返回 `TOKEN_REFRESH_REVOKED`
- 过期 refresh token 返回 `TOKEN_REFRESH_EXPIRED`

### 5) Rotation 规则
- 每次 refresh 成功后，旧 refresh token 状态改为 `ROTATED`
- 新 refresh token 会记录 `rotated_from_token_id`
- 如果再次使用旧 token，会阻断该 `session_id` 下仍然可用的 refresh token，并写审计

### 6) 数据加载验证（数据库已接入）
- H2 模式：启动后访问 H2 Console，执行
   - SELECT COUNT(*) FROM user_account;
- 预期包含静态权限样例用户（U-OIDC-001）；真实 OIDC 登录后会新增稳定的 `OIDC-*` 用户记录。

### 7) PostgreSQL 模式提示
- profile=postgres 下 sql.init.mode=never，不自动执行 data.sql
- 首次联调需手动导入：
   - psql -U postgres -d uscdip -f src/main/resources/data.sql

## B-04 Token 吊销与权限收敛说明

### 1) 目标能力
- `POST /api/auth/logout` 只吊销当前 session，对应 access token 立即失效。
- 账号禁用会更新 `user_account.status=DISABLED`，并让该用户全部旧 access/refresh token 失效。
- 权限重大变更通过 `user_account.token_valid_after` 做用户级收敛，不依赖额外缓存中间件。

### 2) 新增数据模型
- `auth_session`：本地 session 真值表，记录 `session_id / user_id / status / revoked_at / revoke_reason`
- `user_account.token_valid_after`：权限重大变更后的 access token 失效阈值
- `user_account.disabled_at`：账号禁用时间

### 3) 收敛规则
- access token 验签后会追加校验：
   - `user_account.status == ACTIVE`
   - `token.iat >= user_account.token_valid_after`
   - `auth_session.status == ACTIVE`
- refresh token 刷新前会校验：
   - 用户未禁用
   - refresh token 签发时间未早于 `token_valid_after`
   - 对应 `auth_session` 仍为 `ACTIVE`

### 4) B-04 管理接口
- POST /api/auth/admin/users/{userId}/disable
- POST /api/auth/admin/users/{userId}/permissions/revoke
- 两个接口都要求调用者具备 `PLATFORM_ADMIN` 角色。
- 响应字段固定包含：
   - `userId`
   - `action`
   - `tokenValidAfter`
   - `revokedSessionCount`
   - `revokedRefreshTokenCount`

### 5) 缓存说明
- 当前版本无 Redis / Caffeine / 独立权限缓存依赖。
- B-04 以数据库中的 `auth_session` 与 `user_account.token_valid_after` 作为收敛真值。

## B-05 应急旁路账号与审计说明

### 1) 目标能力
- 提供独立于 OIDC 的本地应急旁路账号登录能力，适用于 OIDC 不可用或演练场景。
- 旁路 token 使用更短 TTL：
   - access token 默认 600 秒
   - refresh token 默认 1800 秒
- 旁路账号支持激活、自动到期、人工撤销，并单独写入 `BREAK_GLASS_*` 审计事件。

### 2) 最小权限边界
- 新增角色 `BREAK_GLASS_COMMAND`，仅开放：
   - ENTRY:EMGC
   - MENU:DASHBOARD:READ
   - MENU:WORKORDER:READ
   - MENU:WORKORDER:DISPATCH
   - MENU:ASSET:READ
- 默认不开放：
   - ENTRY:MGMT / ENTRY:DIAG / ENTRY:SUPPORT
   - MENU:ASSET:WRITE / MENU:MODEL:WRITE
- 数据范围继续沿用 `user_data_scope`，测试数据里旁路用户仅绑定 `REGION-EMGC-HZ`。

### 3) 数据模型
- `emergency_account`：应急旁路账号表，记录账号状态、到期时间、激活人、撤销原因和 BCrypt 口令哈希。
- `auth_session.auth_mode`：区分 `STANDARD` 与 `BREAK_GLASS` 会话。
- `auth_session.emergency_account_id`：旁路 session 对应的应急账号。
- `auth_refresh_token.auth_mode / emergency_account_id`：旁路 refresh token 标记。
- `security_audit.auth_mode / emergency_account_id`：独立审计查询所需的旁路事件标识。

### 4) B-05 接口
- POST /api/auth/emergency/login：请求体
   - {"username":"<emergency_username>","password":"<plaintext_password>"}
- POST /api/auth/emergency/accounts/{accountId}/activate：请求体
   - {"password":"<new_password>","expiresAt":"2026-12-31T23:59:59","reason":"drill"}
- POST /api/auth/emergency/accounts/{accountId}/revoke：请求体
   - {"reason":"incident closed"}
- GET /api/auth/emergency/audit：支持 `from / to / username / outcome / page / pageSize`

### 5) 审计与收敛规则
- 旁路登录成功、失败、激活、撤销、refresh、拒绝访问都会写入 `security_audit`。
- 旁路账号一旦 `EXPIRED / REVOKED / INACTIVE`，对应活跃 session 与 refresh token 会被收敛。
- 旁路会话调用 `POST /api/auth/logout` 时不会返回 OIDC provider 跳转地址，但会返回 `authMode=BREAK_GLASS`。

## B-06 Gateway 校验与限流说明

### 1) 目标能力
- 在现有 Spring Boot 单体内实现应用级 Gateway 过滤器，不额外引入 Spring Cloud Gateway。
- 路由策略、黑白名单和高风险审计由数据库表驱动。
- 限流默认按：
   - 已登录：`userId + routeCode`
   - 匿名：`clientIp + routeCode`
- 当前版本限流计数器为单实例内存实现，不依赖 Redis / MQ / 分布式共享状态。

### 2) 过滤器职责
- `GatewayControlFilter` 放在本地 access token 认证之后、控制器之前。
- 固定执行顺序：
   - 路由策略匹配
   - IP / 用户黑白名单检查
   - `authRequired` 前置要求
   - `POST/PUT/PATCH` JSON Content-Type 校验
   - 请求体大小限制
   - `page/pageSize` 分页参数范围校验
   - 单实例内存限流
   - 高风险访问审计

### 3) 新增配置项
- GATEWAY_BODY_SIZE_LIMIT_BYTES：请求体上限，默认 4096
- GATEWAY_PAGE_SIZE_MAX：分页接口 `pageSize` 上限，默认 200
- GATEWAY_DEFAULT_WINDOW_SECONDS：默认限流窗口秒数，默认 60
- GATEWAY_DEFAULT_CAPACITY：默认窗口容量，默认 10

### 4) 路由策略口径
- 当前已接入 Gateway 风控的真实高风险接口：
   - POST /api/auth/admin/users/{userId}/disable
   - POST /api/auth/admin/users/{userId}/permissions/revoke
   - POST /api/auth/emergency/accounts/{accountId}/activate
   - POST /api/auth/emergency/accounts/{accountId}/revoke
   - POST /api/auth/emergency/login
   - POST /api/auth/refresh
- 继续保持匿名可访问的公共接口样例：
   - GET /api/menu-boundaries
   - GET /api/platforms
   - GET /api/platforms/{platformCode}
   - GET /api/authz/matrix-spec
   - GET /api/authz/matrix
- 已预置未来路由策略样例，但当前仓库没有对应控制器：
   - /api/exports/**
   - /api/workorders/batch-dispatch
   - /api/models/publish

### 5) 错误码与行为
- 命中 blocklist：`403 + GATEWAY_BLOCKED`
- 命中限流：`429 + RATE_LIMITED`
- 变更请求 Content-Type 错误：`415 + UNSUPPORTED_CONTENT_TYPE`
- 请求体超限：`413 + REQUEST_BODY_TOO_LARGE`
- 分页参数超限：`400 + INVALID_PARAMETER`

### 6) 边界说明
- `gateway_route_policy` 与 `gateway_client_rule` 是当前版本的 Gateway 真值表。
- `gateway_risk_audit` 记录 allow / deny / rate-limited 等决策。
- 限流计数器不落库；多实例部署时各实例窗口互相独立。
- README 与 `data.sql` 里的阈值只用于联调示例，不代表生产建议值。

## B-07 RBAC + 数据范围 + 订阅范围说明

### 1) 目标能力
- 将 A-04 中的角色权限、数据范围、topic 范围从“矩阵说明”收口为默认生效的运行时约束。
- 真实业务查询接口改为鉴权访问，违规策略为：
   - 列表接口返回已过滤结果
   - 单对象详情、写操作、topic 订阅直接拒绝
- 本轮提供 HTTP 侧的 topic 检查与模拟订阅入口，供后续 WebSocket 网关直接复用。

### 2) 鉴权骨架
- 新增声明式注解 `@AuthzGuard`，用于在控制器层声明：
   - `requiredRole`
   - `entryPermission`
   - `menuPermission`
- 新增 `AuthzGuardAspect`、`CurrentUserResolver` 与 `AuthorizationContext`，统一从当前本地 access token 解析：
   - `roleCodes`
   - `permissionCodes`
   - `dataScopeRule`
   - `authorizedRegions`
   - `authorizedAssignees`
   - `dataViewConstraint`
   - `topicPatterns`
- `GET /api/auth/me` 与 `GET /api/authz/users/{userId}/snapshot` 现在对齐同一套权限快照口径。

### 3) 数据范围模型
- 新增表：`object_scope_binding`
- 作用：为对象链中的真实对象提供统一归属真值，不直接大改现有主数据表。
- 字段：
   - `binding_id`
   - `object_type`
   - `object_id`
   - `region_id`
   - `owner_user_id`
   - `owner_username`
   - `scope_level`
   - `created_at`
   - `updated_at`
- `object_type` 覆盖：
   - `NODE / SEGMENT / FACILITY / DEVICE / INCIDENT / WORK_ORDER / MODEL_RESULT`
- `scope_level` 覆盖：
   - `DETAIL / MASKED / AGGREGATED`
- `ResolvedDataScope` 规则：
   - 平台管理员：`ALL`
   - 区域调度员 / 应急指挥：`REGION_ONLY`
   - 巡检人员：`ASSIGNEE_ONLY`
   - 算法工程师：`MASKED_FEATURE_ONLY`
   - 领导只读：`AGGREGATED_READ_ONLY`

### 4) 受保护接口口径
- 继续匿名开放：
   - GET /api/menu-boundaries
   - GET /api/platforms
   - GET /api/platforms/{platformCode}
   - GET /api/gis/field-spec
   - POST /api/gis/convert
   - POST /api/gis/depth/validate
   - GET /api/gis/objects/bbox
   - POST /api/gis/objects/pick
   - GET /api/gis/objects/{objectType}/{objectId}
   - GET /api/authz/matrix-spec
   - GET /api/authz/matrix
- 改为鉴权访问并接入 B-07 联合校验：
   - GET /api/object-dictionary
   - GET /api/object-dictionary/page
   - GET /api/object-chain/segment/{segmentId}
   - GET /api/object-chain/node/{nodeId}
   - GET /api/authz/users/{userId}/snapshot
   - GET /api/authz/users/{userId}/topics
   - POST /api/authz/check
   - POST /api/authz/topics/check
   - POST /api/authz/topics/subscribe
- Gateway 路由种子也已同步更新，确保这些业务查询不再被视为匿名公开接口。

### 5) Topic 授权入口
- 新增 `TopicAuthorizationService`，统一提供：
   - `expandAuthorizedTopics(userId)`
   - `checkTopics(userId, requestedTopics)`
   - `filterAuthorizedTopics(userId, requestedTopics)`
- 新增接口：
   - POST /api/authz/topics/check
   - POST /api/authz/topics/subscribe
- `topics/check` 返回：
   - `requestedTopics`
   - `allowedTopics`
   - `deniedTopics`
   - `allAllowed`
- `topics/subscribe` 是 B-24 WebSocket 前的 HTTP 模拟订阅入口；不保存长连接状态，只返回授权结果或拒绝错误。

### 6) 错误码
- `ENTRY_PERMISSION_DENIED`
- `MENU_PERMISSION_DENIED`
- `DATA_SCOPE_DENIED`
- `TOPIC_SCOPE_DENIED`
- `SUBSCRIPTION_NOT_ALLOWED`

## B-08 主数据表与对象链服务说明

### 1) 目标能力
- 将现有 A-02 的对象链查询骨架收口为主数据中心只读服务。
- 新增 `/api/master/*` 只读接口，面向 node、segment、facility、device 提供稳定查询入口。
- 对象链遍历优先依赖 `object_relation`，不再只靠业务 service 内零散拼接。

### 2) 主数据与关系真值
- 主数据主表继续使用：
   - `node`
   - `segment`
   - `facility`
   - `device`
- 新增关系表：`object_relation`
- 作用：保存对象链遍历真值，统一表达：
   - `SEGMENT_START_NODE`
   - `SEGMENT_END_NODE`
   - `SEGMENT_FACILITY`
   - `NODE_FACILITY`
   - `FACILITY_DEVICE`
- 业务结果表 `incident / work_order / model_result` 继续保留原有 `segment_id / node_id` 外键落点，但必须可追溯回主数据对象链。

### 3) 接口口径
- 保留兼容：
   - GET /api/object-dictionary
   - GET /api/object-dictionary/page
   - GET /api/object-chain/segment/{segmentId}
   - GET /api/object-chain/node/{nodeId}
- 新增主数据只读接口：
   - GET /api/master/nodes
   - GET /api/master/nodes/{nodeId}
   - GET /api/master/segments
   - GET /api/master/segments/{segmentId}
   - GET /api/master/facilities
   - GET /api/master/facilities/{facilityId}
   - GET /api/master/devices
   - GET /api/master/devices/{deviceId}
- 一期最小查询参数：
   - `page`
   - `pageSize`
   - `status`
   - `regionId`
   - `segmentId / nodeId / facilityId`
- 返回约束：
   - 必须包含当前对象主键
   - 必须包含对象类型
   - 必须包含关联对象主键列表
   - 不能只返回 geometry 或展示字段

### 4) 访问控制
- `/api/master/*` 作为真实业务接口，默认需要鉴权。
- 继续复用现有 B-07 数据范围过滤：
   - 平台管理员：全量
   - 区域调度员：本区
   - 巡检人员：本人归属对象
   - 算法工程师：脱敏范围
   - 领导只读：聚合只读范围
- Gateway 种子策略已同步加入 `/api/master/*`，不会被视为匿名开放接口。

## B-09 主数据版本与并发控制说明

### 1) 目标能力
- 在 B-08 主数据只读 API 之上，补齐最小可用的主数据变更流。
- 覆盖 `node / segment / facility / device` 四类主数据。
- 采用单级审批模型：提交后进入 `PENDING/QUEUED`，审批通过后才写入主表真值。
- 主数据写入启用 optimistic lock，避免“最后一次写入覆盖”。

### 2) 版本与审计模型
- 主表新增 `version_no`，由 JPA `@Version` 驱动乐观锁：
   - `node.version_no`
   - `segment.version_no`
   - `facility.version_no`
   - `device.version_no`
- 新增 B-09 表：
   - `master_change_request`
   - `object_version`
   - `master_data_audit`
- 表用途：
   - `master_change_request` 保存申请状态、审批状态、基线版本号和待生效 payload
   - `object_version` 保存每次审批生效后的对象快照归档
   - `master_data_audit` 保存提交、审批、拒绝、冲突等主数据审计，不与 `security_audit` 混用

### 3) 接口口径
- 新增接口：
   - POST /api/master/changes
   - GET /api/master/changes
   - GET /api/master/changes/{requestId}
   - POST /api/master/changes/{requestId}/approve
   - POST /api/master/changes/{requestId}/reject
- 权限要求：
   - 提交申请、查询列表/详情：`ENTRY:MGMT + MENU:ASSET:WRITE`
   - 审批通过、审批拒绝：仅 `PLATFORM_ADMIN`
- 错误码：
   - `MASTER_DATA_VERSION_CONFLICT`
   - `MASTER_CHANGE_PENDING`
   - `MASTER_CHANGE_NOT_FOUND`
   - `MASTER_CHANGE_INVALID_STATE`
   - `MASTER_CHANGE_APPROVAL_REQUIRED`

### 4) 并发与拓扑一致性
- `node / segment` 若已有 `PENDING` 申请，新申请不会直接覆盖，而是进入 `QUEUED`。
- 审批通过时执行顺序：
   - 校验当前主表 `version_no` 是否仍等于申请的 `baseVersionNo`
   - 更新主数据真表
   - 写入 `object_version`
   - 写入 `master_data_audit`
- 若变更涉及拓扑字段：
   - `segment.startNodeId/endNodeId`
   - `facility.segmentId/nodeId`
   - `device.facilityId/segmentId/nodeId`
- 系统会同步更新 `object_relation`，保证 `/api/object-chain/*` 始终返回最新已生效拓扑。

## B-10 GIS 空间查询与坐标转换服务说明

### 1) 目标能力
- 在现有 A-03 GIS 字段规范之上，补齐真正可用的空间查询能力：
   - bbox 检索
   - 对象点查
   - 单对象 GIS 详情
- 继续由后端统一负责 `authority_srid -> display_srid` 的坐标转换，避免前端私自计算。
- 返回结果不能只有 geometry，必须带对象链主键。

### 2) 空间真值模型
- 新增空间索引表：`object_geo_index`
- 覆盖对象：
   - `NODE`
   - `SEGMENT`
   - `FACILITY`
   - `DEVICE`
- 字段用途：
   - `geometry_2d`：空间真值 WKT
   - `anchor_x / anchor_y`：对象锚点
   - `bbox_min_x / bbox_min_y / bbox_max_x / bbox_max_y`：bbox 范围
   - `authority_srid / display_srid`：存储与默认展示坐标系
   - `region_id`：供数据范围和 GIS 检索联动过滤
- 一期生成规则：
   - `NODE`：直接使用 `node.geometry_2d`
   - `SEGMENT`：由 `start_node + end_node` 生成 `LINESTRING`
   - `FACILITY / DEVICE`：复用所属 `node` 的锚点坐标

### 3) 接口口径
- 保留匿名接口：
   - GET /api/gis/field-spec
   - POST /api/gis/convert
   - POST /api/gis/depth/validate
- 新增鉴权接口：
   - GET /api/gis/objects/bbox
   - POST /api/gis/objects/pick
   - GET /api/gis/objects/{objectType}/{objectId}
- bbox 查询参数：
   - `minX`
   - `minY`
   - `maxX`
   - `maxY`
   - `authoritySrid`
   - `displaySrid`
   - `objectType`
   - `page`
   - `pageSize`
- 点查请求体：
   - `x`
   - `y`
   - `authoritySrid`
   - `displaySrid`
   - `objectTypes`
   - `toleranceMeters`
- GIS 返回统一包含：
   - `objectType`
   - `objectId`
   - `objectName`
   - `geometry2d`
   - `anchorPoint`
   - `bbox`
   - `relatedObjectIds`

### 4) 权限与查询边界
- `GET /api/gis/objects/bbox`、`POST /api/gis/objects/pick`、`GET /api/gis/objects/{objectType}/{objectId}` 都按真实业务接口处理，默认需要鉴权。
- 继续复用 B-07/B-08：
   - `ObjectScopeService` 数据范围过滤
   - 对象详情越权直接拒绝
- B-06 Gateway 已新增 GIS 路由策略：
   - bbox 查询按分页规则校验
   - 点查走 JSON body 校验

## B-11 设备台账与心跳接口说明

### 1) 目标能力
- 提供设备台账分页查询、详情查询、设备注册 Upsert、心跳上报。
- 在线状态固定为三态：`ONLINE / WARNING / OFFLINE`。
- 在线状态由最后心跳、缓冲水位和异常标记联合计算，不是简单布尔值。
- 标定有效期仅用于台账展示；过期时返回 `calibrationExpired=true`，不直接改变在线状态。

### 2) 状态计算口径
- `OFFLINE`：无心跳，或最后心跳距当前超过 90 秒。
- `WARNING`：90 秒内有心跳，且 `bufferLevel >= 80` 或存在 `abnormalFlags`。
- `ONLINE`：90 秒内有心跳，且缓冲水位正常、无异常标记。
- `onlineStatusReason` 当前返回：
   - `NO_HEARTBEAT`
   - `HEARTBEAT_TIMEOUT`
   - `BUFFER_LEVEL_HIGH`
   - `ABNORMAL_FLAGS_PRESENT`
   - `BUFFER_LEVEL_HIGH_AND_ABNORMAL`
   - `HEARTBEAT_OK`

### 3) B-11 接口
- `GET /api/device-ledger/devices`
   - 支持过滤：`status / regionId / segmentId / nodeId / facilityId / protocolType / calibrationExpired / page / pageSize`
- `GET /api/device-ledger/devices/{deviceId}`
- `POST /api/device-ledger/devices/register`
   - 请求体：
   - `{"deviceId":"DEV-004","deviceName":"液位计-04","facilityId":"FAC-002","segmentId":"SEG-001","nodeId":"NODE-002","protocolType":"MQTT","calibrationDueAt":"2099-12-31T23:59:59"}`
- `POST /api/device-ledger/devices/{deviceId}/heartbeat`
   - 请求体：
   - `{"heartbeatTime":"2026-04-23T01:00:00","recvTime":"2026-04-23T01:00:03","bufferLevel":85,"abnormalFlags":["BUFFER_BACKLOG"]}`

### 4) 对象链与权限边界
- 查询接口复用 B-07 数据范围过滤，只返回调用者可访问的 `DEVICE` 对象。
- 详情接口越权访问直接返回 `DATA_SCOPE_DENIED`。
- 注册接口是幂等 Upsert：
   - `deviceId` 已存在时更新可变字段
   - `deviceId` 不存在时创建新设备
- 注册时会同步校验 `facilityId / segmentId / nodeId` 的对象链关系，并补齐：
   - `object_scope_binding`
   - `object_relation(FACILITY -> DEVICE)`
- 心跳接口不会自动补建设备；未知 `deviceId` 返回 `DEVICE_NOT_FOUND`。

## B-12 统一入站 DTO 与协议适配骨架说明

### 1) 目标能力
- 提供统一入站 DTO，固定区分 `eventTime / recvTime / deviceTime / metricCode / value`。
- 提供 `MQTT / MODBUS / NB_IOT` 三类协议适配骨架，演示协议载荷向统一 DTO 的归一化过程。
- 提供 `ingest_batch / ingest_record` 两张接入真值表，供后续 B-13、B-14、B-16 继续复用。

### 2) B-12 接口
- `POST /api/ingest/metrics`
   - 请求体固定包含：`protocolType / sourceType / sourceKey / traceId / isBackfill / metrics[]`
   - 每条 `metrics[]` 固定包含：`deviceId / metricCode / value / eventTime / recvTime / deviceTime`
- `POST /api/ingest/adapt/{protocolType}`
   - 请求体固定包含：`sourceType / sourceKey / traceId / isBackfill / payload`
   - 当前骨架支持：`MQTT / MODBUS / NB_IOT`
- `GET /api/ingest/batches`
- `GET /api/ingest/batches/{batchId}`

### 3) 数据库与联调说明
- 当前项目已经接入数据库。
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL profile 首次联调仍需手动导入 `src/main/resources/data.sql`。
- B-12 新增表：
   - `ingest_batch`：保存批次元信息，如 `protocol_type / source_type / source_key / trace_id / record_count / status / is_backfill / received_at`
   - `ingest_record`：保存单条归一化采样，如 `device_id / metric_code / metric_value / event_time / recv_time / device_time / trace_id / is_backfill / adapter_type / attributes_json / raw_payload_excerpt`

### 4) 错误码与校验口径
- `INGEST_PROTOCOL_UNSUPPORTED`：协议类型不受支持
- `INGEST_DEVICE_NOT_FOUND`：设备不存在，不允许接入层自动补建设备
- `INGEST_PAYLOAD_INVALID`：请求体缺字段、时间格式错误、三种时间口径冲突、空批次等
- `INGEST_BATCH_NOT_FOUND`：批次不存在
- 三种时间不会合并为单一 `collectTime`

### 5) B-12 联调示例
- 统一 DTO 直传：
   - `{"protocolType":"MQTT","sourceType":"EDGE_GATEWAY","sourceKey":"EDGE-HZ-GW-02","traceId":"TRACE-INGEST-001","isBackfill":false,"metrics":[{"deviceId":"DEV-001","metricCode":"PRESSURE","value":0.92,"eventTime":"2026-04-23T09:00:00","recvTime":"2026-04-23T09:00:03","deviceTime":"2026-04-23T08:59:58","attributes":{"topic":"region/hz/dev-001/pressure","qos":1}}]}`
- Modbus 协议适配：
   - `{"sourceType":"PLC_GATEWAY","sourceKey":"PLC-HZ-02","traceId":"TRACE-INGEST-ADAPT-001","isBackfill":false,"payload":{"deviceId":"DEV-002","registerAddress":"40001","registerValue":41.8,"sampledAt":"2026-04-23T10:00:00","gatewayReceivedAt":"2026-04-23T10:00:02","controllerTime":"2026-04-23T09:59:59","slaveId":"8"}}`

## B-13 TSDB 写入与补偿写入服务说明

### 1) 目标能力
- `POST /api/ingest/metrics`、`POST /api/ingest/adapt/{protocolType}` 与 `POST /api/ingest/backfill` 在接入层落库后会自动串联 TSDB 写入。
- 在线与补偿写入统一写入 `ts_metric`，并记录 `ts_write_log` 作为写入尝试真值。
- 支持数据库驱动的失败重试与手动重试入口，不引入 Redis / MQ。

### 2) B-13 接口
- `POST /api/ingest/backfill`
- `GET /api/ingest/write-logs`
- `GET /api/ingest/write-logs/{writeLogId}`
- `POST /api/ingest/write-logs/{writeLogId}/retry`

### 3) 数据库与联调说明
- 当前项目已经接入数据库。
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL profile 首次联调仍需手动导入 `src/main/resources/data.sql`。
- Timescale 目标口径额外提供 `src/main/resources/application-timescale.yml` 与 `src/main/resources/timescale-init.sql`。
- Timescale 初始化命令：
   - `CREATE EXTENSION IF NOT EXISTS timescaledb;`
   - `psql -f src/main/resources/timescale-init.sql`
- B-13 新增表：
   - `ts_metric`：保存最终时序样本，以及 `batch_no / seq_no / original_sample_time / late_arrival / latency_ms`
   - `ts_write_log`：保存每次写入尝试、重试状态和错误
- `ingest_batch` 已扩展：
   - `tsdb_write_status / last_write_log_id / last_write_at`
   - `batch_no / seq_no / original_sample_time`

## B-14 数据质量评分服务说明

### 1) 目标能力
- TSDB 写入成功后自动计算 `dq_score / dq_level / dq_flags`，并回写到 `ts_metric`。
- 评分固定采用完整性、有效性、时效性、一致性、稳定性五项加权公式。
- 写入结果同时落 `dq_alarm_conf_factor`，供后续 B-17 告警置信度降权直接复用。

### 2) B-14 接口
- `GET /api/dq/scores`
   - 支持 `deviceId / metricCode / dqLevel / minScore / maxScore / sourceBatchId / isBackfill / startTime / endTime`
   - `startTime / endTime` 按 `event_time` 过滤，使用 ISO DateTime；`startTime` 晚于 `endTime` 返回 `DQ_QUERY_INVALID`
- `GET /api/dq/scores/{sourceRecordId}`

### 3) 数据库与联调说明
- B-14 不新增独立评分表，直接扩展 `ts_metric`：
   - `dq_score / dq_level / dq_flags`
   - `dq_completeness / dq_validity / dq_timeliness / dq_consistency / dq_stability`
   - `dq_alarm_conf_factor / dq_scored_at`
- 查询接口继续复用 B-07 数据范围，只允许看到有权限设备对应的评分样本。
- 一期范围画像内置在代码中，覆盖 `PRESSURE / TEMPERATURE / VIBRATION / 40001`；未知指标按降权处理并标记 `VALIDITY_PROFILE_MISSING`。
- F-14 前端标识映射：
   - `VALIDITY_RANGE_VIOLATION`：量程越界，提示采样值超出当前指标画像。
   - `VALIDITY_PROFILE_MISSING`：画像缺失，提示后端按保守策略降权。
   - `TIMELINESS_DELAYED`：时效延迟，提示 `eventTime / deviceTime / recvTime` 口径异常。
   - `BACKFILL_DATA` 或 `isBackfill=true`：补偿回传，趋势点和明细表均显示琥珀色补偿标识。
   - `dq_alarm_conf_factor`：在点位上下文中展示为告警置信度降权系数。

### 4) 错误码
- `TSDB_WRITE_FAILED`
- `TSDB_WRITE_LOG_NOT_FOUND`
- `BACKFILL_PAYLOAD_INVALID`
- `BACKFILL_DUPLICATE`
- `DQ_SCORE_NOT_FOUND`
- `DQ_QUERY_INVALID`
- `DQ_PROFILE_INVALID`

## B-15 标定与漂移管理接口说明

### 1) 目标能力
- 提供 `calibration_profile` 版本化标定档案，支持 `DRAFT / ACTIVE / EXPIRED / SUPERSEDED`。
- 提供 `calibration_drift_record` 漂移复核真值，基于 `observedValue + referenceValue` 做阈值判定。
- 标定激活与日级扫描会为临期或过期设备生成数据治理类 `incident + work_order`。
- 历史 `ts_metric.metric_value` 保持原始值不变，校正结果仅通过预览接口动态返回。

### 2) B-15 接口
- `POST /api/calibration/devices/{deviceId}/profiles`
   - 请求体固定包含：`profileVersion / metricCode / calibratedAt / effectiveFrom / effectiveUntil / operatorName / referenceStandard / correctionSlope / correctionOffset / driftThresholdAbs / driftThresholdPct / activate`
- `GET /api/calibration/devices/{deviceId}/profiles`
- `GET /api/calibration/devices/{deviceId}/profiles/active`
   - 查询参数：`metricCode`
- `POST /api/calibration/devices/{deviceId}/drift-checks`
   - 请求体固定包含：`profileVersion / metricCode / observedValue / referenceValue / checkedAt / checkedBy`
- `GET /api/calibration/devices/{deviceId}/drift-checks`
- `GET /api/calibration/metrics/{sourceRecordId}/corrected`
   - 支持可选查询参数：`profileVersion`

### 3) 数据库与联调说明
- 当前项目已经接入数据库。
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale profile 首次联调仍需手动导入 `src/main/resources/data.sql`。
- B-15 新增表：
   - `calibration_profile`：保存 `profile_version / metric_code / calibrated_at / effective_from / effective_until / correction_* / drift_threshold_* / status`
   - `calibration_drift_record`：保存 `observed_value / reference_value / deviation_abs / deviation_pct / drift_status / incident_id / work_order_id`
- B-15 继续复用并补充：
   - `device.calibration_due_at`：仅保存设备台账快照，不保存完整标定历史
   - `incident / work_order`：保存到期提醒与漂移确认生成的数据治理工单
   - `object_scope_binding`：为新生成的 incident / work_order 继承设备所属区域和责任人

### 4) 错误码
- `CALIBRATION_PROFILE_NOT_FOUND`
- `CALIBRATION_PROFILE_CONFLICT`
- `CALIBRATION_DRIFT_EVALUATION_INVALID`
- `CALIBRATION_CORRECTION_PREVIEW_INVALID`

## B-16 边缘断网补偿接口说明

### 1) 目标能力
- 继续复用 `POST /api/ingest/backfill` 作为边缘断网补偿主入口，要求固定携带 `batchNo / seqNo / originalSampleTime / isBackfill=true`。
- 补偿批次允许乱序回传，不要求 `seqNo` 按到达顺序递增；样本时间仍以 `eventTime / originalSampleTime` 为真值。
- 完全相同的重复补偿按幂等成功处理，不重复写入 `ts_metric`。
- 相同 `deviceId + metricCode + batchNo + seqNo` 但载荷不同，直接返回 `409 BACKFILL_DUPLICATE`，不再以 `201 + tsdbWrite=FAILED` 吞掉业务冲突。

### 2) 接口与口径
- `POST /api/ingest/backfill`
   - 请求体固定包含：`protocolType / sourceType / sourceKey / traceId / batchNo / seqNo / originalSampleTime / isBackfill / metrics[]`
   - 每条 `metrics[]` 固定包含：`deviceId / metricCode / value / eventTime / recvTime / deviceTime`
- 幂等重复：
   - 命中同一 `deviceId + metricCode + batchNo + seqNo` 且 `metricValue / eventTime / recvTime / deviceTime / originalSampleTime / traceId` 全部一致时返回 `201`
   - `ts_metric` 不新增重复样本
- 冲突重复：
   - 命中同一 `deviceId + metricCode + batchNo + seqNo` 但载荷不一致时返回 `409`
   - 错误码固定为 `BACKFILL_DUPLICATE`

### 3) 数据库与联调说明
- 当前项目已经接入数据库。
- 默认开发联调使用 H2；可选使用 PostgreSQL / TimescaleDB，配置文件分别为 `application.yml`、`application-postgres.yml`、`application-timescale.yml`。
- B-16 继续复用现有表，不新增数据库类型：
   - `ingest_batch`：保存补偿批次元信息与 `batch_no / seq_no / original_sample_time / is_backfill`
   - `ts_metric`：保存最终补偿样本与 `batch_no / seq_no / original_sample_time / late_arrival / latency_ms`
   - `ts_write_log`：保存补偿写入尝试与状态
- `src/main/resources/data.sql` 已新增 B-16 专属种子：
   - `INGB-SEED-B16-STD-001`：标准补偿样例
   - `INGB-SEED-B16-OOO-SEQ2 / INGB-SEED-B16-OOO-SEQ1`：乱序 `seqNo` 样例

### 4) 联调示例
- 标准补偿写入：
   - `curl -s -X POST http://localhost:8080/api/ingest/backfill -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"protocolType\":\"MQTT\",\"sourceType\":\"EDGE_GATEWAY\",\"sourceKey\":\"EDGE-HZ-GW-B16\",\"traceId\":\"TRACE-B16-001\",\"batchNo\":\"BATCH-B16-001\",\"seqNo\":1,\"originalSampleTime\":\"2026-04-21T08:00:00\",\"isBackfill\":true,\"metrics\":[{\"deviceId\":\"DEV-001\",\"metricCode\":\"PRESSURE\",\"value\":0.91,\"eventTime\":\"2026-04-21T08:00:00\",\"recvTime\":\"2026-04-24T08:00:00\",\"deviceTime\":\"2026-04-21T07:59:58\"}]}"` 
- 冲突重复示例：
   - 首次发送后，再把同一 `batchNo=BATCH-B16-001`、`seqNo=1` 的 `value` 改为其他值重复提交，应返回 `409 BACKFILL_DUPLICATE`
- 查询种子数据：
   - `SELECT batch_id, batch_no, seq_no, original_sample_time FROM ingest_batch WHERE batch_id LIKE 'INGB-SEED-B16-%' ORDER BY batch_no, seq_no DESC;`
   - `SELECT source_record_id, batch_no, seq_no, is_backfill, latency_ms FROM ts_metric WHERE source_batch_id LIKE 'INGB-SEED-B16-%' ORDER BY batch_no, seq_no DESC;`

### 5) 错误码
- `BACKFILL_PAYLOAD_INVALID`
- `BACKFILL_DUPLICATE`
- `TSDB_WRITE_FAILED`

## B-17 告警规则引擎一期骨架说明

### 1) 目标能力
- 统一以 `ts_metric` 中已完成 DQ 评分的样本作为规则评估真值，不再引入额外规则中间件。
- 一期支持两类规则：
   - 阈值规则：`GT / GTE / LT / LTE / BETWEEN`
   - 组合规则：`ANY / ALL`
- 自动触发口径：
   - 新批次写入 `ts_metric` 并完成 DQ 评分后，同步执行规则评估并生成 `alert_record`
   - 若规则配置本身异常，会跳过本次自动评估，不回滚已成功的采集写入
- 手动触发口径：
   - `POST /api/alerts/evaluate`
   - 支持 `sourceRecordIds[]` 与 `sourceBatchId` 二选一
   - `ruleCodes[]` 不传时评估所有启用规则

### 2) 规则决策与置信度
- 最终告警置信度固定采用：
   - `alarm_conf_final = alarm_conf_raw x (0.5 + 0.5 x dq_score / 100)`
- 命中后的决策分档固定为：
   - `TRIGGERED`：`dq_score >= 70`
   - `REVIEW_REQUIRED`：`60 <= dq_score < 70`
   - `DQ_BLOCKED`：`dq_score < 60`
- 组合规则 raw confidence：
   - `ANY`：取命中子规则 `alarm_conf_raw` 最大值
   - `ALL`：取命中子规则 `alarm_conf_raw` 最小值

### 3) 数据库与联调说明
- 当前项目已经接入数据库。
- B-17 继续复用已有 H2 / PostgreSQL / TimescaleDB 三套配置：
   - 默认开发联调：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- B-17 新增表：
   - `alert_rule`：保存 `rule_code / rule_type / metric_code / operator_code / threshold_* / logic_type / base_confidence / severity / enabled / expression_json`
   - `alert_record`：保存 `source_record_id / source_batch_id / device_id / rule_code / decision / alert_conf_* / dq_* / metric_* / trace_id`
- B-17 继续复用：
   - `ts_metric`：规则评估输入样本源，直接读取 `dq_score / dq_level / dq_alarm_conf_factor`
   - `device`：补齐 `segment_id / node_id`
- `src/main/resources/data.sql` 已新增 B-17 专属种子：
   - 规则种子：`ALR-TH-PRESSURE-HIGH`、`ALR-TH-VIBRATION-HIGH`、`ALR-TH-DQ-ANOMALY`、`ALR-CB-PRESSURE-OR-DQ`、`ALR-CB-PRESSURE-AND-DQ`
   - 告警种子：`ALERT-SEED-001`、`ALERT-SEED-002`、`ALERT-SEED-003`、`ALERT-SEED-004`
- H2 默认启动会自动建表并装载以上种子；PostgreSQL / Timescale 首次联调仍需手动导入 `src/main/resources/data.sql`

### 4) 接口与联调示例
- 手动按源记录评估：
   - `curl -s -X POST http://localhost:8080/api/alerts/evaluate -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"sourceRecordIds\":[\"INGR-SEED-011\"],\"ruleCodes\":[\"ALR-TH-PRESSURE-HIGH\",\"ALR-CB-PRESSURE-OR-DQ\",\"ALR-CB-PRESSURE-AND-DQ\"]}"`
- 手动按批次评估：
   - `curl -s -X POST http://localhost:8080/api/alerts/evaluate -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"sourceBatchId\":\"INGB-SEED-MQTT-001\"}"`
- 查询告警：
   - `curl -s "http://localhost:8080/api/alerts?page=1&pageSize=10&deviceId=DEV-001" -H "Authorization: Bearer <hz_scope_access_token>"`
- 查询启用规则：
   - `curl -s http://localhost:8080/api/alerts/rules -H "Authorization: Bearer <admin_access_token>"`
- SQL 联调：
   - `SELECT rule_code, rule_type, metric_code, operator_code, logic_type, enabled FROM alert_rule ORDER BY rule_code;`
   - `SELECT alert_id, source_record_id, device_id, rule_code, decision, alert_conf_raw, alert_conf_final FROM alert_record ORDER BY created_at DESC;`

### 5) 错误码
- `ALERT_RULE_NOT_FOUND`
- `ALERT_RULE_INVALID`
- `ALERT_EVALUATION_INVALID`
- `ALERT_RECORD_NOT_FOUND`

## B-18 告警去重 / 抑制 / 升级服务说明

### 1) 目标能力
- B-18 继续复用 B-17 的规则评估结果，不引入 Redis、Kafka、Drools 等新中间件。
- 自动链路固定为：
   - `ingest -> ts_metric -> dq -> alert_rule_engine -> alert_dedup_service`
- 手动链路固定为：
   - `POST /api/alerts/evaluate`
   - 先生成原始 `alert_record`，再立即进入去重 / 抑制 / 升级处理
- 本轮只处理告警层，不自动联动 `incident / work_order`。

### 2) 去重 / 抑制 / 升级 / 恢复口径
- 去重 key 固定按 `object + rule_code + active_window_start` 生成并持久化到 `dedupe_key`。
- 同一对象判定优先使用 `device_id`；若后续存在无设备告警，再退化到 `segment_id + node_id`。
- 去重窗口：
   - 同一 `rule_code`
   - 且命中时间仍落在活跃 case 的 `dedupe_window_seconds` 内
   - 满足后归并到同一 `alert_case`
- 抑制窗口：
   - 新 case 首条命中为可见告警
   - 同 case 后续命中若仍落在 `suppressed_until` 之前，则 `alert_record.process_status=SUPPRESSED`
   - 被抑制命中仍会刷新 `last_triggered_at / hit_count`，但不会增加 `unsuppressed_hit_count`
- 升级规则：
   - 仅 `decision=TRIGGERED` 参与自动升级
   - 默认阈值：`L1=3`、`L2=5`
   - 严重级别固定按 `LOW -> MEDIUM -> HIGH -> CRITICAL` 上调，封顶 `CRITICAL`
- DQ 联动：
   - `dq_score >= 85`：按原阈值升级
   - `70 <= dq_score < 85`：升级阈值整体上调一档，默认等效 `L1=5 / L2=7`
   - `REVIEW_REQUIRED`：case 状态为 `REVIEW_ONLY`，允许建 case / 去重 / 抑制，但不自动升级
   - `DQ_BLOCKED`：case 状态为 `DQ_BLOCKED`，允许建 case / 去重，但不自动升级
- 恢复规则：
   - 若 `now - last_triggered_at > recovery_window_seconds`，则定时扫描将 case 标记为 `RECOVERED`
   - 已恢复 case 不再复用，新命中会新开 case

### 3) 数据库与测试数据说明
- 当前项目已经接入数据库，B-18 继续复用已有三套配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- B-18 新增表：
   - `alert_policy`：保存 `rule_code / dedupe_window_seconds / suppress_window_seconds / recovery_window_seconds / escalate_threshold_* / enabled`
   - `alert_case`：保存 `dedupe_key / device_id / rule_code / case_status / hit_count / unsuppressed_hit_count / escalation_level / suppressed_until / recovered_at`
- B-18 扩展表：
   - `alert_record`：新增 `case_id / dedupe_key / process_status / suppressed / escalation_level / processed_at`
- `src/main/resources/data.sql` 已补充：
   - 策略种子：`APOL-SEED-001` 到 `APOL-SEED-005`
   - case 种子：覆盖 `ACTIVE / SUPPRESSED / ESCALATED / RECOVERED / REVIEW_ONLY / DQ_BLOCKED`
   - 告警明细种子：覆盖同对象同规则窗口内重复命中、抑制命中、升级命中、恢复前历史命中
- H2 默认启动会自动建表并装载以上种子；PostgreSQL / Timescale 首次联调仍需手动导入 `src/main/resources/data.sql`

### 4) 接口与联调示例
- 手动触发评估并观察 case 摘要：
   - `curl -s -X POST http://localhost:8080/api/alerts/evaluate -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"sourceRecordIds\":[\"INGR-SEED-007\",\"INGR-SEED-008\",\"INGR-SEED-009\"],\"ruleCodes\":[\"ALR-TH-PRESSURE-HIGH\"]}"`
- 查询告警明细：
   - `curl -s "http://localhost:8080/api/alerts?page=1&pageSize=10&deviceId=DEV-001&ruleCode=ALR-TH-PRESSURE-HIGH" -H "Authorization: Bearer <hz_scope_access_token>"`
- 查询告警 case：
   - `curl -s "http://localhost:8080/api/alerts/cases?page=1&pageSize=10&deviceId=DEV-001&caseStatus=ESCALATED" -H "Authorization: Bearer <hz_scope_access_token>"`
- 查询策略：
   - `curl -s http://localhost:8080/api/alerts/policies -H "Authorization: Bearer <admin_access_token>"`
- SQL 联调：
   - `SELECT rule_code, dedupe_window_seconds, suppress_window_seconds, recovery_window_seconds, escalate_threshold_l1, escalate_threshold_l2 FROM alert_policy ORDER BY rule_code;`
   - `SELECT case_id, device_id, rule_code, case_status, hit_count, unsuppressed_hit_count, escalation_level, suppressed_until, recovered_at FROM alert_case ORDER BY updated_at DESC;`
   - `SELECT alert_id, case_id, rule_code, process_status, suppressed, escalation_level, event_time FROM alert_record ORDER BY created_at DESC;`

### 5) 错误码
- `ALERT_POLICY_NOT_FOUND`
- `ALERT_POLICY_INVALID`
- `ALERT_CASE_NOT_FOUND`

## B-19 事件化服务说明

### 1) 目标能力
- B-19 在 B-18 告警 case 基础上补齐事件化，不引入 Outbox、Relay、幂等消费或工单自动创建。
- 自动链路固定为：
   - `ingest -> ts_metric -> dq -> alert_rule_engine -> alert_case_lifecycle -> incident_eventization`
- 手动规则评估同样会在 case 写入后同步触发 incident 创建或更新。
- 本轮提供最小 incident 查询与确认能力，对齐一期文档里的 `/api/incidents` 与 `/api/incidents/{id}/confirm`。

### 2) 事件化规则与状态机
- incident 状态机固定为：
   - `PENDING_CONFIRMATION`
   - `OPEN`
   - `RESOLVED`
   - `CLOSED`
   - `FALSE_POSITIVE`
- 事件化口径固定为：
   - `alert_case.case_status in (ACTIVE, ESCALATED, SUPPRESSED)` 且 `decision_snapshot=TRIGGERED`：创建或更新 `OPEN` incident
   - `alert_case.case_status=REVIEW_ONLY`：创建或更新 `PENDING_CONFIRMATION` incident
   - `alert_case.case_status=DQ_BLOCKED`：默认不创建正式 incident
   - `alert_case.case_status=RECOVERED`：若 incident 尚未终态，则自动转为 `RESOLVED`
- 去重口径固定为“一活跃 `alert_case` 对应一个 `incident`”；重复命中只刷新原 incident，不重复新开。
- `POST /api/incidents/{incidentId}/confirm` 仅允许 `PENDING_CONFIRMATION -> OPEN`，并记录 `confirmed_by / confirmed_at`。

### 3) 数据库与测试数据说明
- 当前项目已经接入数据库，B-19 继续复用已有三套配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- B-19 扩展 `incident` 表：
   - `incident_type`：区分 `ALERT_EVENT` 与 `CALIBRATION_GOVERNANCE`
   - `source_case_id / source_alert_id / source_rule_code / source_batch_id / device_id`
   - `dq_score_snapshot / alert_conf_final / severity_source`
   - `confirmed_by / confirmed_at / resolved_at / close_reason / trace_id / version_no`
- `work_order` 在 B-19 阶段仅保留联动位；B-22 已补齐工单服务的创建、派单、状态流转与回写能力。
- `src/main/resources/data.sql` 已补充：
   - 事件化 incident 种子：覆盖 `OPEN / PENDING_CONFIRMATION / RESOLVED / FALSE_POSITIVE`
   - 告警事件 incident 均关联现有 `alert_case / alert_record`
   - 新增 incident 对应的 `object_scope_binding` 与 gateway route policy
- H2 默认启动会自动建表并装载以上种子；PostgreSQL / Timescale 首次联调仍需手动导入 `src/main/resources/data.sql`

### 4) 接口与联调示例
- 查询事件列表：
   - `curl -s "http://localhost:8080/api/incidents?page=1&pageSize=10&deviceId=DEV-001&incidentType=ALERT_EVENT" -H "Authorization: Bearer <hz_scope_access_token>"`
- 查询单条事件：
   - `curl -s http://localhost:8080/api/incidents/INC-ALERT-SEED-001 -H "Authorization: Bearer <admin_access_token>"`
- 确认待人工复核事件：
   - `curl -s -X POST http://localhost:8080/api/incidents/INC-ALERT-SEED-002/confirm -H "Authorization: Bearer <admin_access_token>"`
- SQL 联调：
   - `SELECT incident_id, incident_type, device_id, source_case_id, source_rule_code, status, severity, dq_score_snapshot, alert_conf_final FROM incident ORDER BY updated_at DESC;`
   - `SELECT object_id, region_id, owner_username FROM object_scope_binding WHERE object_type='INCIDENT' ORDER BY object_id;`

### 5) 错误码
- `INCIDENT_NOT_FOUND`
- `INCIDENT_INVALID_STATE`

## B-20 Outbox 与 Relay 说明

### 1) 目标能力
- 当前项目已经接入数据库，B-20 是在现有 H2 / PostgreSQL / TimescaleDB 配置上扩展 Outbox 能力，不是重新引入数据库。
- 业务服务在同一事务中同时写入业务表和 `outbox_event`，一期先接入 B-19 incident 事件化链路。
- Relay 使用 Spring `@Scheduled` 定时扫描 `NEW / FAILED` 且到期的事件，先认领为 `SENDING`，再通过可替换的 `OutboxDispatcher` 发送。
- 默认 `LoggingOutboxDispatcher` 只记录结构化日志，不引入 Kafka / Redis / Flyway / Quartz；后续可替换为真实消息总线实现。

### 2) outbox_event 字段与状态
- 核心字段：`event_id / aggregate_type / aggregate_id / event_type / payload / trace_id / status / retry_count / next_retry_time / created_at / updated_at / claimed_by / claimed_at / sent_at / last_error`。
- 状态固定为：
   - `NEW`：待发送
   - `SENDING`：已被 Relay 实例认领
   - `SENT`：已发送成功
   - `FAILED`：发送失败，等待下一次重试
   - `DEAD`：超过最大重试次数，进入人工处理
- 目前已接入 incident 事件类型：`INCIDENT_OPENED / INCIDENT_UPDATED / INCIDENT_RESOLVED / INCIDENT_CONFIRMED`。
- `payload` 固定包含：`incidentId / status / severity / versionNo / sourceCaseId / sourceAlertId / traceId / occurredAt`。

### 3) Relay 配置项
- `OUTBOX_RELAY_FIXED_DELAY_MS`：Relay 扫描间隔，默认 `30000`
- `OUTBOX_BATCH_SIZE`：每批认领数量，默认 `20`
- `OUTBOX_MAX_ATTEMPTS`：最大发送尝试次数，默认 `3`
- `OUTBOX_RETRY_BASE_DELAY_SECONDS`：指数退避基础秒数，默认 `30`
- `OUTBOX_CLAIM_TIMEOUT_SECONDS`：`SENDING` 认领超时秒数，默认 `120`
- `OUTBOX_INSTANCE_ID`：Relay 实例标识，默认 `local-relay-1`

### 4) 数据库与测试数据说明
- B-20 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：
   - `psql -U postgres -d uscdip -f src/main/resources/data.sql`
- `src/main/resources/data.sql` 已新增 B-20 种子：
   - `OBE-SEED-NEW-001`：`NEW` 待发送样例
   - `OBE-SEED-FAILED-001`：`FAILED` 可重试样例
   - `OBE-SEED-SENT-001`：`SENT` 已发送样例
   - `OBE-SEED-DEAD-001`：`DEAD` 死信样例

### 5) SQL 联调示例
- 查看 Outbox 全量状态：
   - `SELECT event_id, aggregate_id, event_type, status, retry_count, next_retry_time, sent_at, last_error FROM outbox_event ORDER BY created_at;`
- 按 trace_id 串联 incident 与 outbox：
   - `SELECT i.incident_id, i.status AS incident_status, o.event_type, o.status AS outbox_status, o.trace_id FROM incident i JOIN outbox_event o ON o.aggregate_id = i.incident_id WHERE o.trace_id = 'TRACE-INGEST-SEED-001';`
- 启动后观察 Relay 日志：
   - `mvn spring-boot:run`
   - 日志中出现 `outbox dispatch eventId=... eventType=... aggregateType=INCIDENT ...` 表示默认日志发送器已接管发送。

## B-21 幂等键与死信处理说明

### 1) 目标能力
- 当前项目已经接入数据库，B-21 继续复用现有 H2 / PostgreSQL / TimescaleDB 配置，不重新引入数据库或中间件。
- 消费侧统一以 `incident_id + action_type + version` 生成幂等键，避免工单创建、通知消费、回写消费重复执行。
- 本轮已将校准治理工单创建接入幂等消费；后续 B-22 工单服务、B-23 通知服务可复用同一 `IdempotentConsumerService`。
- B-20 的 `outbox_event.DEAD` 表示发送侧失败；B-21 的 `dead_letter` 表示消费侧失败，两者分表记录。

### 2) idempotent_record 与 dead_letter
- `idempotent_record` 核心字段：`idempotent_key / action_type / aggregate_type / aggregate_id / event_id / version_no / status / result_ref_id / attempt_count / first_seen_at / last_seen_at / completed_at / last_error`。
- 幂等状态：
   - `PROCESSING`：首次消费已认领，业务动作执行中
   - `SUCCESS`：消费成功，`result_ref_id` 保存业务结果引用
   - `FAILED`：消费失败但未达到最大尝试次数，可再次重试
   - `DEAD`：达到最大尝试次数，已写入消费侧死信
- `dead_letter` 核心字段：`dead_letter_id / source_event_id / idempotent_key / action_type / aggregate_type / aggregate_id / payload / trace_id / failure_reason / retry_count / status / created_at / resolved_at`。
- 预留 action type：`CREATE_WORK_ORDER / SEND_NOTIFICATION / WRITEBACK_RESULT`。

### 3) 配置项
- `IDEMPOTENCY_MAX_ATTEMPTS`：消费最大尝试次数，默认 `3`。

### 4) 数据库与测试数据说明
- B-21 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：
   - `psql -U postgres -d uscdip -f src/main/resources/data.sql`
- `src/main/resources/data.sql` 已新增 B-21 种子：
   - `INC-CAL-SEED-001:CREATE_WORK_ORDER:1`：工单创建成功样例，结果引用 `WO-CAL-SEED-001`
   - `INC-ALERT-SEED-001:SEND_NOTIFICATION:1`：通知消费成功样例
   - `INC-ALERT-SEED-004:WRITEBACK_RESULT:2`：回写消费死信样例
   - `DLQ-SEED-WRITEBACK-001`：消费侧死信样例

### 5) SQL 联调示例
- 查看幂等记录：
   - `SELECT idempotent_key, action_type, aggregate_id, version_no, status, result_ref_id, attempt_count, last_error FROM idempotent_record ORDER BY first_seen_at;`
- 查看消费侧死信：
   - `SELECT dead_letter_id, source_event_id, idempotent_key, action_type, status, retry_count, failure_reason FROM dead_letter ORDER BY created_at;`
- 串联 incident、工单与幂等记录：
   - `SELECT i.incident_id, w.work_order_id, r.idempotent_key, r.status FROM incident i JOIN work_order w ON w.incident_id = i.incident_id JOIN idempotent_record r ON r.result_ref_id = w.work_order_id WHERE r.action_type = 'CREATE_WORK_ORDER';`

## B-22 工单服务说明

### 1) 目标能力
- 当前项目已经接入数据库，B-22 继续复用现有 H2 / PostgreSQL / TimescaleDB 配置，不新增 Redis、Kafka、Flyway 或其他中间件。
- `work_order` 保留一事件多工单能力，`incident_id` 不做唯一业务约束；工单创建接口允许同一 incident 生成多个不同工单。
- 新增工单闭环字段：`work_order_type / priority / description / assignee_user_id / sla_due_at / created_by / dispatched_by / accepted_by / completed_by / closed_by / dispatched_at / accepted_at / completed_at / closed_at / completion_summary / close_reason / writeback_type / writeback_reason / writeback_at / version_no`。
- 新工单会继承 incident 的 `object_scope_binding` 区域范围；派单和转派会同步工单责任人范围，继续复用 B-07 `ObjectScopeService` 的区域调度、巡检本人任务和管理员范围逻辑。

### 2) 状态机与回写规则
- 合法状态流：`CREATED -> DISPATCHED -> ACCEPTED -> COMPLETED -> CLOSED`。
- 转派：`DISPATCHED / ACCEPTED -> DISPATCHED`，更新 `assignee_user_id / assignee / sla_due_at / dispatched_by / dispatched_at`，并清空接单与完成字段。
- 人工关闭：`CREATED / DISPATCHED / ACCEPTED / COMPLETED -> CLOSED`，记录 `close_reason / closed_by / closed_at`。
- 回写：`POST /api/workorders/{workOrderId}/writeback` 仅记录 `writeback_type / writeback_reason / writeback_at`，不等同关闭动作。
- 非法状态流返回 `WORK_ORDER_INVALID_STATE`。

### 3) 接口与权限
- `GET /api/workorders`：分页查询，支持 `status / incidentId / assignee / workOrderType` 过滤，并按当前用户数据范围过滤。
- `GET /api/workorders/{workOrderId}`：工单详情。
- `POST /api/workorders`：基于 incident 创建工单。
- `POST /api/workorders/{workOrderId}/dispatch`：派单。
- `POST /api/workorders/{workOrderId}/accept`：接单。
- `POST /api/workorders/{workOrderId}/transfer`：转派。
- `POST /api/workorders/{workOrderId}/complete`：完成。
- `POST /api/workorders/{workOrderId}/close`：关闭。
- `POST /api/workorders/{workOrderId}/writeback`：误报、漏报或其他回写。
- 查询需要 `MENU:WORKORDER:READ`；创建、派单、转派、关闭、回写需要 `MENU:WORKORDER:DISPATCH`；接单和完成允许当前 assignee 或具备派单权限的用户操作。

### 3.1) F-13 前端联调请求体
- 派单与转派请求体一致，`assignee` 必须非空，`slaDueAt` 使用 ISO 本地时间：
   - `{"assigneeUserId":"U-INSPECT-001","assignee":"zhangsan","slaDueAt":"2026-05-07T18:30","reason":"现场复核压力异常"}`
- 回写独立建模，不等同关闭工单：
   - `{"writebackType":"FALSE_POSITIVE","writebackReason":"现场复核为误报"}`
   - `{"writebackType":"MISSED_REPORT","writebackReason":"现场发现异常但规则链路未触发"}`
- 关闭工单只提交关闭原因：
   - `{"closeReason":"处置完成并复核通过"}`

### 4) Outbox 事件
- 工单状态变更会写入 `outbox_event`，`aggregate_type=WORK_ORDER`。
- 已支持事件类型：`WORK_ORDER_CREATED / WORK_ORDER_DISPATCHED / WORK_ORDER_ACCEPTED / WORK_ORDER_TRANSFERRED / WORK_ORDER_COMPLETED / WORK_ORDER_CLOSED / WORK_ORDER_WRITEBACK_RECORDED`。
- B-21 的消费侧幂等能力继续保留；B-22 创建接口不把 `incident_id + CREATE_WORK_ORDER + version` 作为工单业务唯一键。

### 5) 数据库与测试数据说明
- B-22 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：
   - `psql -U postgres -d uscdip -f src/main/resources/data.sql`
- `src/main/resources/data.sql` 已新增 B-22 种子：
   - `WO-B22-CREATED-001 / WO-B22-DISPATCHED-001 / WO-B22-ACCEPTED-001 / WO-B22-COMPLETED-001 / WO-B22-CLOSED-001`：覆盖多状态工单样例
   - `WO-B22-CREATED-001` 与 `WO-B22-DISPATCHED-001`：同一 incident 多工单样例
   - `WO-B22-COMPLETED-001 / WO-B22-CLOSED-001`：`FALSE_POSITIVE / MISSED_REPORT` 回写样例
   - `WORKORDER_LIST / WORKORDER_DETAIL / WORKORDER_CREATE / WORKORDER_DISPATCH / WORKORDER_ACCEPT / WORKORDER_TRANSFER / WORKORDER_COMPLETE / WORKORDER_CLOSE / WORKORDER_WRITEBACK`：Gateway 路由策略样例

### 6) SQL 联调示例
- 查看工单状态分布：
   - `SELECT status, COUNT(*) FROM work_order GROUP BY status ORDER BY status;`
- 验证一事件多工单：
   - `SELECT incident_id, COUNT(*) AS work_order_count FROM work_order GROUP BY incident_id HAVING COUNT(*) > 1;`
- 查看工单数据范围：
   - `SELECT w.work_order_id, w.status, b.region_id, b.owner_username FROM work_order w JOIN object_scope_binding b ON b.object_id = w.work_order_id WHERE b.object_type = 'WORK_ORDER' ORDER BY w.work_order_id;`
- 查看工单 Outbox：
   - `SELECT event_id, aggregate_id, event_type, status, payload FROM outbox_event WHERE aggregate_type = 'WORK_ORDER' ORDER BY created_at DESC;`

## B-23 通知服务说明

### 1) 目标能力
- 当前项目已经接入数据库，B-23 继续复用现有 H2 / PostgreSQL / TimescaleDB 配置，不新增 Redis、Kafka、Flyway 或真实第三方通知 SDK。
- 通知服务只消费稳定工单 Outbox 事件，不消费原始告警流，避免告警风暴直接放大到通知链路。
- 新增 `notification_message` 与 `notification_delivery`，一条业务通知按 `IN_APP / SMS / WECHAT / EMAIL` 等通道拆成多条发送记录。
- 本轮通道为本地模拟发送器：`IN_APP` 写库即成功，其他通道生成本地 target 并按配置模拟成功或失败。

### 2) 事件、幂等与重试
- 消费事件白名单：`WORK_ORDER_CREATED / WORK_ORDER_DISPATCHED / WORK_ORDER_ACCEPTED / WORK_ORDER_TRANSFERRED / WORK_ORDER_COMPLETED / WORK_ORDER_CLOSED / WORK_ORDER_WRITEBACK_RECORDED`，并预留 `WORK_ORDER_ESCALATED`。
- 幂等规则复用 B-21：`work_order_id + SEND_NOTIFICATION + versionNo`；`source_event_id` 也唯一约束，重复扫描不会重复生成通知。
- 通道失败进入 `notification_delivery.FAILED`，到达 `next_retry_at` 后由定时任务或手动接口重试。
- 超过最大尝试次数后进入 `notification_delivery.DEAD`，同步写入消费侧 `dead_letter`，action type 为 `SEND_NOTIFICATION`。

### 3) 接口与权限
- `GET /api/notifications`：分页查询，支持 `status / channel / recipient / sourceEventId / workOrderId` 过滤。
- `GET /api/notifications/{notificationId}`：通知详情，包含各通道 delivery。
- `POST /api/notifications/consume-outbox`：手动消费稳定工单 Outbox，便于本地联调。
- `POST /api/notifications/{notificationId}/retry`：手动重试失败或死信通道。
- 查询使用 `MENU:WORKORDER:READ`，按工单对象范围过滤；通知接收人也可看到自己的通知。手动消费与重试使用 `MENU:WORKORDER:DISPATCH`。

### 4) 配置项
- `NOTIFICATION_CHANNELS`：默认 `IN_APP,SMS,WECHAT`。
- `NOTIFICATION_FAIL_CHANNELS`：默认空；测试可配置如 `SMS` 表示首尝试失败，`SMS_ALWAYS` 表示持续失败。
- `NOTIFICATION_MAX_ATTEMPTS`：通道最大尝试次数，默认 `3`。
- `NOTIFICATION_RETRY_BASE_DELAY_SECONDS`：指数退避基础秒数，默认 `30`。
- `NOTIFICATION_RETRY_FIXED_DELAY_MS`：通知重试扫描间隔，默认 `30000`。

### 5) 数据库与测试数据说明
- B-23 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：
   - `psql -U postgres -d uscdip -f src/main/resources/data.sql`
- `src/main/resources/data.sql` 已新增 B-23 种子：
   - `OBE-SEED-WO-DISPATCHED-001 / OBE-SEED-WO-COMPLETED-001 / OBE-SEED-WO-CLOSED-001`：稳定工单 Outbox 样例
   - `NOTIFY-B23-SENT-001 / NOTIFY-B23-FAILED-001 / NOTIFY-B23-DEAD-001`：成功、待重试、死信通知样例
   - `ND-B23-*`：`IN_APP / SMS / WECHAT` 多通道 delivery 样例
   - `DLQ-SEED-NOTIFY-SMS-001`：通知通道死信样例
   - `NOTIFICATION_LIST / NOTIFICATION_DETAIL / NOTIFICATION_CONSUME_OUTBOX / NOTIFICATION_RETRY`：Gateway 路由策略样例

### 6) SQL 联调示例
- 查看通知主记录：
   - `SELECT notification_id, source_event_id, aggregate_id, event_type, recipient_username, status, last_error FROM notification_message ORDER BY created_at DESC;`
- 查看通道发送状态：
   - `SELECT notification_id, channel, target, status, attempt_count, next_retry_at, sent_at, last_error FROM notification_delivery ORDER BY notification_id, channel;`
- 串联工单、Outbox、通知与死信：
   - `SELECT w.work_order_id, o.event_type, n.notification_id, d.channel, d.status, dl.dead_letter_id FROM work_order w JOIN outbox_event o ON o.aggregate_id = w.work_order_id LEFT JOIN notification_message n ON n.source_event_id = o.event_id LEFT JOIN notification_delivery d ON d.notification_id = n.notification_id LEFT JOIN dead_letter dl ON dl.source_event_id = o.event_id WHERE o.aggregate_type = 'WORK_ORDER';`

## B-24 WebSocket 推送网关说明

### 1) 目标能力
- 当前项目已经接入数据库，B-24 继续复用 H2 / PostgreSQL / TimescaleDB 与 JPA 自动建表。
- 新增 Spring WebSocket/STOMP endpoint：`/ws/push`。本地为 `ws://localhost:8080/ws/push`，生产 WSS 由 TLS/反向代理或 Boot SSL 承载。
- 首版推送范围以 B-23 `notification_message` 为补发源，把工单通知转换成可订阅、可 ack、可重连补发的 WebSocket 消息。

### 2) 握手、订阅与消息目的地
- 握手鉴权支持两种方式：`Authorization: Bearer <accessToken>` 或 `?access_token=<accessToken>`。
- broker 前缀：`/topic`、`/queue`、`/user`；应用消息前缀：`/app`。
- 订阅示例：区域调度订阅 `/topic/region.REGION-HZ.workorder.notifications`；巡检本人订阅 `/user/queue/workorder.notifications`。
- 应用消息：`SEND /app/push/heartbeat` 刷新活跃时间；`SEND /app/push/ack` 请求体为 `{"topic":"user.U-INSPECT-001.workorder.notifications","lastAckSeq":100}`。
- 订阅授权复用 `TopicAuthorizationService`，入口 `ENTRY:EMGC`，菜单 `MENU:WORKORDER:READ`；非法 Origin、无效 token、跨区或他人 topic 会被拒绝。

### 3) 持久化、ack 与补发
- 新增表：`websocket_connection / websocket_subscription / websocket_push_message / websocket_ack`。
- 通知转换 topic：`user.{userId}.workorder.notifications` 与 `region.{regionId}.workorder.notifications`。
- 客户端重连订阅后，服务端按 `websocket_ack.last_ack_seq` 补发 `seq_no > last_ack_seq` 的消息。
- 推送 payload 固定包含 `seqNo / messageId / topic / type / sourceNotificationId / traceId / title / content / createdAt`。

### 4) 配置项
- `WEBSOCKET_ALLOWED_ORIGINS`：允许 Origin，默认 `http://localhost:3000,http://localhost:5173,http://localhost:8080`。
- `WEBSOCKET_MAX_CONNECTIONS` / `WEBSOCKET_MAX_CONNECTIONS_PER_USER`：总连接数和单用户连接数限制。
- `WEBSOCKET_FRAME_SIZE_LIMIT_BYTES` / `WEBSOCKET_SEND_BUFFER_SIZE_LIMIT_BYTES` / `WEBSOCKET_SEND_TIME_LIMIT_MS`：帧大小、发送缓冲和发送超时。
- `WEBSOCKET_IDLE_TIMEOUT_SECONDS / WEBSOCKET_HEARTBEAT_INTERVAL_MS / WEBSOCKET_REPLAY_BATCH_SIZE`：空闲关闭、心跳间隔和补发批量大小。
- `WEBSOCKET_IDLE_SCAN_FIXED_DELAY_MS / WEBSOCKET_NOTIFICATION_BRIDGE_FIXED_DELAY_MS`：空闲扫描与通知桥接扫描间隔。

### 5) 数据库与测试数据说明
- B-24 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：`psql -U postgres -d uscdip -f src/main/resources/data.sql`。
- `src/main/resources/data.sql` 已新增 B-24 种子：`WSC-SEED-*` 连接样例、`WSS-SEED-*` 订阅样例、`WSP-SEED-*` 推送消息样例、`websocket_ack` ack 水位样例，以及 `WEBSOCKET_PUSH_HANDSHAKE` Gateway 路由策略样例。

### 6) SQL 联调示例
- 查看连接与订阅：
   - `SELECT c.connection_id, c.user_id, c.status, s.topic, s.status AS sub_status FROM websocket_connection c LEFT JOIN websocket_subscription s ON s.connection_id = c.connection_id ORDER BY c.connected_at DESC;`
- 查看待补发消息：
   - `SELECT seq_no, topic, source_notification_id, title FROM websocket_push_message WHERE seq_no > 99 ORDER BY seq_no;`
- 查看 ack 水位：
   - `SELECT user_id, topic, last_ack_seq, updated_at FROM websocket_ack ORDER BY user_id, topic;`
- 串联通知与推送：
   - `SELECT n.notification_id, n.aggregate_id, p.seq_no, p.topic, a.last_ack_seq FROM notification_message n JOIN websocket_push_message p ON p.source_notification_id = n.notification_id LEFT JOIN websocket_ack a ON a.topic = p.topic ORDER BY p.seq_no;`

## B-25 模型网关一期框架说明

### 1) 目标能力
- 当前项目已经接入数据库，B-25 继续复用 H2 / PostgreSQL / TimescaleDB 与 JPA 自动建表。
- 新增模型治理框架：模型注册、版本管理、灰度发布、激活、回退、推理审计和规则兜底。
- 一期不接真实 MLflow、Python 服务或外部模型运行时；`POST /api/models/{modelCode}/infer` 使用 Java 本地模拟适配器，支持通过 `simulateFailure / simulateLatencyMs` 复现失败和超时。

### 2) API 与权限
- 查询：`GET /api/models`、`GET /api/models/{modelCode}`，需要 `ENTRY:DIAG + MENU:MODEL:READ`。
- 写操作：`POST /api/models/register`、`POST /api/models/{modelCode}/versions`、`/gray`、`/activate`、`/rollback`、`/infer`，需要 `ENTRY:DIAG + MENU:MODEL:WRITE`。
- 推理请求必须携带 `segmentId` 或 `nodeId`；生成的 `model_result` 会继续写入对象范围绑定，默认使用 `MASKED` 级别，便于算法工程师按脱敏视图查看。

### 3) 版本、灰度与回退
- 模型状态：`REGISTERED`。
- 版本状态：`DRAFT / GRAY / ACTIVE / ROLLED_BACK / DISABLED`。
- 灰度选择按 `requestId` 做确定性哈希；`grayPercent=100` 时稳定命中灰度版本。
- 激活或回退会把当前 `ACTIVE / GRAY` 版本标记为 `ROLLED_BACK`，目标版本置为 `ACTIVE`。
- F-16 前端一期治理页路径为 `/diag/models`，读取权限为 `ENTRY:DIAG + MENU:MODEL:READ`，回退按钮额外要求 `MENU:MODEL:WRITE`。
- 回退请求体示例：`{"targetVersionNo":"V1","reason":"灰度异常，回退至上一稳定版本"}`；前端会在成功后刷新模型注册表与详情。
- F-16 暂不开放注册、创建版本、灰度百分比调整、激活、推理和 B-26 明细复核写操作，页面仅保留后续治理入口说明。

### 4) 规则兜底与审计
- 无可用版本、模拟失败或模拟耗时超过版本 `timeoutMs` 时，立即走 `RuleFallbackService`，返回 `resultSource=RULE_FALLBACK`。
- 成功推理和规则兜底都会写入 `model_result` 与 `model_invocation_audit`。
- 注册、创建版本、灰度、激活、回退、推理写入 `model_operation_audit`；B-26 统一审计服务后可再汇聚。

### 5) 数据库与测试数据说明
- B-25 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：`psql -U postgres -d uscdip -f src/main/resources/data.sql`。
- `src/main/resources/data.sql` 已新增 B-25 种子：`LEAK_DETECTOR / CALIBRATION_DRIFT_GUARD` 模型、`ACTIVE / GRAY / ROLLED_BACK / DRAFT` 版本、成功推理 `model_result`、超时规则兜底审计，以及 `/api/models/**` Gateway 路由策略。

### 6) SQL 联调示例
- 查看模型与版本：
   - `SELECT m.model_code, m.model_name, v.version_no, v.status, v.gray_percent, v.timeout_ms FROM model_registry m JOIN model_version v ON v.model_code = m.model_code ORDER BY m.model_code, v.version_no;`
- 查看推理结果与审计：
   - `SELECT a.request_id, a.model_code, a.version_no, a.result_source, a.failure_reason, r.model_result_id, r.status FROM model_invocation_audit a JOIN model_result r ON r.model_result_id = a.model_result_id ORDER BY a.created_at DESC;`
- 查看模型操作审计：
   - `SELECT model_code, version_no, operation_type, operator_user_id, created_at FROM model_operation_audit ORDER BY created_at DESC;`

## B-26 特征视图与算法脱敏接口说明

### 1) 目标能力
- 当前项目已经接入数据库，B-26 继续复用 H2 / PostgreSQL / TimescaleDB 与 JPA 自动建表。
- 面向算法工程师提供默认 `MASKED` 特征视图，避免直接暴露生产明细主键、设备名称、设施名称、精确坐标或 `geometry_2d`。
- `DETAIL` 明细视图必须由具备 `ENTRY:DIAG + MENU:MODEL:WRITE` 的用户创建限时授权，授权过期或撤销后自动拒绝。

### 2) API 与权限
- `GET /api/feature-views/metrics`：按 `segmentId / nodeId / deviceId / metricCode / startTime / endTime` 查询时序特征，需要 `ENTRY:DIAG + MENU:MODEL:READ`。
- `GET /api/feature-views/model-results`：查询模型结果特征摘要，串联 `model_result / model_invocation_audit`，需要 `ENTRY:DIAG + MENU:MODEL:READ`。
- `POST /api/feature-views/grants`：创建限时明细授权，需要 `ENTRY:DIAG + MENU:MODEL:WRITE`。
- `POST /api/feature-views/grants/{grantId}/revoke`：撤销授权，需要 `ENTRY:DIAG + MENU:MODEL:WRITE`。
- `GET /api/feature-views/audits`：查询特征视图访问审计，需要 `ENTRY:DIAG + MENU:MODEL:WRITE`。

### 3) 脱敏与明细授权
- `MASKED` 返回稳定哈希 ID、区域、对象类型、指标、时间桶、DQ 分数、坐标粗粒度桶和可训练特征值。
- `MASKED` 不返回 `sourceRecordId / deviceId / deviceName / segmentId / nodeId / geometry2d` 等生产明细字段。
- `DETAIL` 在有效授权命中时返回原始对象 ID、设备名称和精确 `geometry_2d`；授权目标支持 `GLOBAL / SEGMENT / NODE / DEVICE`。
- 无授权、授权过期或授权已撤销时，`DETAIL` 查询返回 `FEATURE_VIEW_DENIED`，并写入拒绝审计。

### 4) 审计与 Gateway
- `feature_view_grant` 保存用户、授权视图级别、目标范围、原因、授权人、过期时间和状态。
- `feature_view_access_audit` 记录每次 `MASKED / DETAIL / DENIED` 查询的用户、条件、结果条数、命中授权和拒绝原因。
- `/api/feature-views/**` 已加入 Gateway 路由策略；明细授权、撤销和审计查询按高风险或关键操作记录。

### 5) 数据库与测试数据说明
- B-26 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：`psql -U postgres -d uscdip -f src/main/resources/data.sql`。
- `src/main/resources/data.sql` 已新增 B-26 种子：有效、过期、撤销三类 `feature_view_grant`，`MASKED / DETAIL / DENIED` 三类 `feature_view_access_audit`，以及 `/api/feature-views/**` Gateway 路由策略。

### 6) SQL 联调示例
- 查看明细授权：
   - `SELECT grant_id, user_id, view_level, target_type, target_id, status, expires_at FROM feature_view_grant ORDER BY created_at DESC;`
- 查看访问审计：
   - `SELECT user_id, query_type, requested_view_level, effective_view_level, decision, result_count, reason FROM feature_view_access_audit ORDER BY created_at DESC;`
- 串联模型结果特征与调用审计：
   - `SELECT r.model_result_id, r.model_code, r.status, a.result_source, a.failure_reason FROM model_result r LEFT JOIN model_invocation_audit a ON a.model_result_id = r.model_result_id ORDER BY r.created_at DESC;`

## B-27 统一审计日志服务说明

### 1) 目标能力
- 当前项目已经接入数据库，B-27 继续复用 H2 / PostgreSQL / TimescaleDB 与 JPA 自动建表。
- 新增统一 `audit_log` 主表，用于跨域查询和报表；既有 `security_audit / gateway_risk_audit / master_data_audit / model_operation_audit / feature_view_access_audit` 不删除，继续保存领域细节。
- 统一审计覆盖登录、应急旁路、权限变更、工单派单/闭环、模型操作、特征视图授权/明细访问和高风险 Gateway 访问。

### 2) API 与权限
- `GET /api/audit-logs`：分页查询统一审计，支持 `eventCategory / eventType / sourceModule / actorUserId / authMode / emergencyAccountId / outcome / riskLevel / objectType / objectId / traceId / from / to / emergencyOnly`。
- `GET /api/audit-logs/{auditId}`：查询统一审计详情。
- `GET /api/audit-logs/break-glass-report`：查询应急旁路专项报表，只返回 `authMode=BREAK_GLASS`、有 `emergencyAccountId` 或 `eventCategory=BREAK_GLASS` 的记录。
- 三个接口均要求 `PLATFORM_ADMIN + ENTRY:MGMT`；Gateway 路由策略均启用审计。

### 3) 写入与旁路报表
- `SecurityAuditService` 写入领域安全审计后，同步写入 `audit_log`。
- `GatewayPolicyService` 对启用审计的路由和拒绝/限流行为写入统一审计。
- `WorkOrderService / ModelGatewayService / FeatureViewService` 在关键业务动作成功后写入统一审计。
- `AuthzGuardAspect` 会识别 `BREAK_GLASS` 本地 token，所有受 `@AuthzGuard` 保护的旁路操作额外写 `BREAK_GLASS_OPERATION`，用于单独出报表。

### 4) 数据库与测试数据说明
- B-27 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：`psql -U postgres -d uscdip -f src/main/resources/data.sql`。
- `src/main/resources/data.sql` 已新增 B-27 种子：登录/Token、应急旁路、权限变更、工单派单、模型回退、导出高风险访问样例，以及 `/api/audit-logs/**` Gateway 路由策略。

### 5) SQL 联调示例
- 查看统一审计：
   - `SELECT audit_id, event_category, event_type, actor_user_id, auth_mode, outcome, risk_level, trace_id FROM audit_log ORDER BY event_time DESC;`
- 查看应急旁路专项报表：
   - `SELECT audit_id, event_type, actor_user_id, emergency_account_id, request_path, detail FROM audit_log WHERE auth_mode = 'BREAK_GLASS' OR emergency_account_id IS NOT NULL OR event_category = 'BREAK_GLASS' ORDER BY event_time DESC;`
- 按 trace 串联：
   - `SELECT event_time, source_module, event_type, object_type, object_id, outcome FROM audit_log WHERE trace_id = 'TRACE-B27-BG-001' ORDER BY event_time;`

## B-28 Trace 与链路埋点中间件说明

### 1) 目标能力
- 当前项目已经接入数据库，B-28 继续复用 H2 / PostgreSQL / TimescaleDB 与 JPA 自动建表。
- 新增统一 `trace_link_event` 主表，用于把 HTTP、Outbox、通知消费和 WebSocket 推送/ack 串成可查询链路。
- `TraceIdFilter`、Outbox relay、通知消费和 WebSocket 推送网关会统一透传 `traceId`，并把它注入 MDC 与响应头。

### 2) API 与权限
- `GET /api/traces`：分页查询链路埋点，支持 `traceId / stage / sourceModule / objectType / objectId / status / from / to`。
- `GET /api/traces/{traceId}`：按时间顺序返回单条 trace 的全链路事件。
- 两个接口均要求 `PLATFORM_ADMIN + ENTRY:MGMT`；Gateway 路由策略均启用审计。

### 3) Trace 透传规则
- HTTP 优先读取 `X-Trace-Id`，兼容读取 `traceparent` 并提取其中的 trace 部分；非法或缺失时自动生成 UUID。
- HTTP 响应头固定回传 `X-Trace-Id`，响应体 `traceId` 与响应头保持一致。
- `outbox_event.trace_id` 与 payload 内的 `traceId` 同步写入，通知消费和 WebSocket push payload 继续沿用同一条 trace。
- WebSocket 推送 payload 现包含 `seqNo / messageId / topic / type / sourceNotificationId / traceId / title / content / createdAt`。

### 4) 数据库与测试数据说明
- B-28 继续复用已有三套数据库配置：
   - H2：`src/main/resources/application.yml`
   - PostgreSQL：`src/main/resources/application-postgres.yml`
   - TimescaleDB：`src/main/resources/application-timescale.yml`
- H2 默认启动会自动建表并执行 `src/main/resources/data.sql`。
- PostgreSQL / Timescale 首次联调仍需手动导入测试数据：`psql -U postgres -d uscdip -f src/main/resources/data.sql`。
- `src/main/resources/data.sql` 已新增 B-28 种子：`TRACE-B28-E2E-001` 串联 HTTP 入站、工单 Outbox、通知消费、WebSocket 握手/推送/ack，以及 WebSocket 断开样例和 `/api/traces/**` Gateway 路由策略。

### 5) SQL 联调示例
- 查看全量链路埋点：
   - `SELECT trace_id, stage, event_type, source_module, object_type, object_id, status, route_path, message_id FROM trace_link_event ORDER BY event_time DESC;`
- 查询单条 trace：
   - `SELECT trace_id, stage, event_type, status, route_path, topic, message_id, detail FROM trace_link_event WHERE trace_id = 'TRACE-B28-E2E-001' ORDER BY event_time;`
- 按链路阶段统计：
   - `SELECT stage, COUNT(*) FROM trace_link_event GROUP BY stage ORDER BY stage;`

## 数据库配置说明

### 默认数据库（开发/联调）
- 类型：H2 内存数据库
- 配置文件：src/main/resources/application.yml
- JDBC URL：jdbc:h2:mem:uscdip;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
- 用户名：sa
- 密码：空
- H2 Console：http://localhost:8080/h2-console

### PostgreSQL（可选）
- 配置文件：src/main/resources/application-postgres.yml
- 启动命令：mvn clean spring-boot:run -Dspring-boot.run.profiles=postgres
- 环境变量：
   - DB_HOST（默认 localhost）
   - DB_PORT（默认 5432）
   - DB_NAME（默认 uscdip）
   - DB_USER（默认 postgres）
   - DB_PASSWORD（默认 postgres）
   - BACKEND_OIDC_ENABLED（默认 false）
   - OIDC_ISSUER_URI / OIDC_CLIENT_ID / OIDC_CLIENT_SECRET
   - LOCAL_ACCESS_TOKEN_SECRET / LOCAL_ACCESS_TOKEN_TTL_SECONDS / LOCAL_REFRESH_TOKEN_TTL_SECONDS
   - EMERGENCY_ACCESS_TOKEN_TTL_SECONDS / EMERGENCY_REFRESH_TOKEN_TTL_SECONDS / EMERGENCY_PASSWORD_HASH_STRENGTH
   - GATEWAY_BODY_SIZE_LIMIT_BYTES / GATEWAY_PAGE_SIZE_MAX
   - GATEWAY_DEFAULT_WINDOW_SECONDS / GATEWAY_DEFAULT_CAPACITY
   - OIDC_STATE_TTL_SECONDS / REFRESH_TOKEN_HASH_ALGORITHM / LOCAL_TOKEN_ISSUER

### TimescaleDB（PostgreSQL 目标口径）
- 配置文件：src/main/resources/application-timescale.yml
- 启动命令：mvn clean spring-boot:run -Dspring-boot.run.profiles=timescale
- 初始化步骤：
   - 执行 `CREATE EXTENSION IF NOT EXISTS timescaledb;`
   - 再执行 `psql -f src/main/resources/timescale-init.sql`
- 说明：
   - 默认开发和测试仍使用 H2
   - Timescale profile 基于 PostgreSQL 配置扩展，不会在 H2 启动阶段执行 Timescale 专属 SQL
- B-16 / B-17 / B-18 / B-19 / B-20 / B-21 / B-22 / B-23 / B-24 / B-25 / B-26 / B-27 / B-28 同样复用以上三套数据库配置；H2 默认自动装载补偿、告警规则、告警策略、case、incident 事件化、outbox_event、idempotent_record、dead_letter、work_order 闭环、notification、websocket、model gateway、feature view、unified audit 与 trace link 种子，PostgreSQL / Timescale 首次联调仍需手动导入 `src/main/resources/data.sql`

### A-04 数据库存储（本轮新增）
- 资源配置文件：src/main/resources/a04-authz-matrix.json
- 权限相关表（由 JPA 自动建表）：
   - user_account
   - rbac_role
   - rbac_permission
   - rbac_user_role
   - rbac_role_permission
   - user_data_scope
   - topic_scope_rule
- B-03/B-04 认证表：
   - auth_oidc_state
   - auth_refresh_token
   - auth_session
   - security_audit
- B-05 新增：
   - emergency_account
- B-06 新增：
   - gateway_route_policy
   - gateway_client_rule
   - gateway_risk_audit
- B-07 新增：
   - object_scope_binding
- B-08 新增：
   - object_relation
- B-09 新增：
   - master_change_request
   - object_version
   - master_data_audit
- B-10 新增：
   - object_geo_index
- B-11 新增：
   - device_heartbeat
- B-12 新增：
   - ingest_batch
   - ingest_record
- B-13 新增：
   - ts_metric
   - ts_write_log
- B-15 新增：
   - calibration_profile
   - calibration_drift_record
- B-25 新增：
   - model_registry
   - model_version
   - model_invocation_audit
   - model_operation_audit
- B-26 新增：
   - feature_view_grant
   - feature_view_access_audit
- B-27 新增：
   - audit_log
- B-28 新增：
   - trace_link_event
- B-05 在认证链路上新增字段：
   - auth_session.auth_mode
   - auth_session.emergency_account_id
   - auth_refresh_token.auth_mode
   - auth_refresh_token.emergency_account_id
   - security_audit.auth_mode
   - security_audit.emergency_account_id
- B-04 在 `user_account` 上新增：
   - token_valid_after
   - disabled_at
- B-06 说明：
   - `gateway_route_policy` 保存路由风控、输入校验和限流策略
   - `gateway_client_rule` 保存 IP / 用户黑白名单
   - `gateway_risk_audit` 保存 Gateway allow / deny / rate-limited 审计记录
- B-07 说明：
   - `object_scope_binding` 保存对象所属区域、归属用户和视图级别真值
   - `rbac_* / user_data_scope / topic_scope_rule` 继续保存角色、数据范围和 topic 模式
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调需手动导入 `src/main/resources/data.sql`
- B-08 说明：
   - `object_relation` 保存 node/segment/facility/device 的拓扑关系真值
   - `/api/master/*` 提供主数据只读查询，`/api/object-chain/*` 提供聚合链路查询
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-09 说明：
   - `master_change_request` 保存主数据变更申请与审批状态
   - `object_version` 保存审批生效后的版本快照
   - `master_data_audit` 保存主数据变更审计，不与 `security_audit` 混用
   - `node / segment / facility / device` 新增 `version_no`
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-10 说明：
   - `object_geo_index` 保存 GIS bbox、锚点、统一 geometry 与区域归属真值
   - 当前版本不依赖 PostGIS，H2 / PostgreSQL 都可联调
   - GIS 查询采用应用层空间计算 + 数据库存储索引的组合方式
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-11 说明：
   - `device` 新增 `last_recv_time / last_buffer_level / last_abnormal_flags / online_status_reason / calibration_due_at`
   - `device_heartbeat` 保存每次心跳上报历史
   - `gateway_route_policy` 已新增 B-11 四条设备台账路由策略
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-12 说明：
   - `ingest_batch` 保存接入批次元信息与 `trace_id / is_backfill`
   - `ingest_record` 保存归一化后的 `event_time / recv_time / device_time / metric_code / metric_value`
   - `gateway_route_policy` 已新增 B-12/B-13/B-14 路由策略
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-13 说明：
   - `ts_metric` 保存写入后的在线/补偿样本、写入延迟与补偿顺序字段
   - `ts_write_log` 保存写入状态、错误、重试次数与下次重试时间
   - `application-timescale.yml` 与 `timescale-init.sql` 提供 Timescale 目标部署说明
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL / Timescale profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-14 说明：
   - `ts_metric` 直接追加 `dq_*` 字段，不额外拆分评分表
   - `dq_flags` 以逗号分隔大写标签存储
   - `gateway_route_policy` 已新增 B-14 两条评分查询路由策略
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL / Timescale profile 首次联调同样需要手动导入 `src/main/resources/data.sql`
- B-15 说明：
   - `calibration_profile` 保存标定版本、有效期、校正参数和漂移阈值
   - `calibration_drift_record` 保存复核输入、偏差计算、判定状态与关联工单
   - `device.calibration_due_at` 继续作为设备台账快照字段，由 ACTIVE/EXPIRED profile 同步刷新
   - `gateway_route_policy` 已新增 B-15 六条标定/漂移路由策略
   - H2 默认启动会自动建表并执行 `data.sql`
   - PostgreSQL / Timescale profile 首次联调同样需要手动导入 `src/main/resources/data.sql`

## 测试数据说明
- 文件：src/main/resources/data.sql
- 已生成可联调测试数据，覆盖七类对象：
   - node：3 条
   - segment：2 条
   - facility：3 条
   - device：3 条
   - incident：4 条
   - work_order：9 条
   - notification_message：3 条
   - notification_delivery：9 条
   - model_result：4 条
   - model_registry：2 条
   - model_version：5 条
   - model_invocation_audit：2 条
   - model_operation_audit：3 条
   - feature_view_grant：3 条
   - feature_view_access_audit：3 条
   - audit_log：7 条
   - trace_link_event：8 条
- 已生成 B-11 设备台账联调数据：
   - `device`：3 条，覆盖 `ONLINE / WARNING / OFFLINE`
   - `device_heartbeat`：3 条历史样例
   - `DEV-002` 的 `calibrationDueAt` 已过期，可直接验证 `calibrationExpired=true`
- 已生成 B-12 接入层联调数据：
   - `ingest_batch`：8 条，覆盖 `MQTT / MODBUS / NB_IOT / B16_STANDARD / B16_OUT_OF_ORDER / FLATLINE / INVALID`
   - `ingest_record`：13 条，覆盖 MQTT、Modbus、NB-IoT 回填、B-16 标准补偿、B-16 乱序补偿、平线样本和量程异常样本
   - `INGB-SEED-NBIOT-001` 可直接验证 `isBackfill=true`
   - `INGB-SEED-B16-STD-001` 可直接验证 B-16 标准补偿批次
   - `INGB-SEED-B16-OOO-SEQ2 / INGB-SEED-B16-OOO-SEQ1` 可直接验证乱序 `seqNo` 口径
   - `INGB-SEED-FLAT-001` 可直接验证平线窗口
   - `INGB-SEED-INVALID-001` 可直接验证量程异常与低质量评分
- 已生成 B-13 / B-14 时序与质量联调数据：
   - `ts_write_log`：8 条成功写入样例
   - `ts_metric`：13 条时序样例，均带 `dq_score / dq_level / dq_flags`
   - `INGR-SEED-004`：回填 + 延迟样例，对应 `C` 级质量
   - `INGR-SEED-011 / INGR-SEED-012 / INGR-SEED-013`：B-16 补偿样例，覆盖标准补偿与乱序补偿
   - `INGR-SEED-009`：平线样例，带 `STABILITY_FLATLINE`
   - `INGR-SEED-010`：量程异常 + 延迟样例，对应 `D` 级质量
- 已生成 B-15 标定与漂移联调数据：
   - `calibration_profile`：3 条，覆盖 `SUPERSEDED / ACTIVE / EXPIRED`
   - `calibration_drift_record`：1 条 `CONFIRMED` 漂移样例，已关联 `INC-CAL-SEED-001 / WO-CAL-SEED-001`
   - `INC-CAL-SEED-002 / WO-CAL-SEED-002`：过期标定提醒样例
   - `GET /api/calibration/metrics/INGR-SEED-001/corrected` 可直接验证校正预览
- 已生成 A-04 权限联调数据：
   - user_account：11 条（含静态权限样例用户、B-04 禁用用户样例、B-04 权限收敛样例、B-05 应急用户样例、B-06 网关阻断用户样例、B-07 单区域调度样例）
   - rbac_role：6 条
   - rbac_permission：12 条
   - rbac_user_role：11 条
   - rbac_role_permission：29 条
   - user_data_scope：12 条
   - topic_scope_rule：8 条
- 已保留 B-02 静态权限样例：
   - user_account: U-OIDC-001 / oidc_static_sample
   - rbac_user_role: U-OIDC-001 -> REGIONAL_DISPATCHER
   - user_data_scope: U-OIDC-001 -> REGION-HZ
- 说明：该样例仅用于权限演示，不代表真实 OIDC 同步结果。
- 已新增 B-03 token / audit 联调样例：
   - auth_refresh_token：ACTIVE / ROTATED / REVOKED / REPLAY_BLOCKED 多状态样例
   - security_audit：5 条（刷新成功 / revoked reuse / replay reuse / disabled user / permission convergence）
   - 说明：仓库不保存可直接使用的明文 refresh token；联调 `/api/auth/refresh` 时请先通过回调换 token 或本地服务签发真实 refresh token
- 已新增 B-04 session / convergence 联调样例：
   - auth_session: ACTIVE / REVOKED 两类 session 状态样例，并包含 `ACCOUNT_DISABLED` / `PERMISSION_CHANGED` 吊销原因样例
   - user_account: `token_valid_after` 与 `disabled_at` 字段样例
   - auth_refresh_token: disabled user / permission convergence 对应的 `REVOKED` 样例记录
- 已新增 B-05 应急旁路联调样例：
   - emergency_account: ACTIVE / INACTIVE / EXPIRED 三类旁路账号
   - user_account: `U-B05-COMMAND-001` 绑定 `BREAK_GLASS_COMMAND`
   - auth_session / auth_refresh_token: `BREAK_GLASS` 样例记录
   - security_audit: `BREAK_GLASS_ACCOUNT_ACTIVATED / BREAK_GLASS_LOGIN_SUCCESS / BREAK_GLASS_ACCOUNT_REVOKED`
- 已新增 B-06 Gateway 联调样例：
   - gateway_route_policy：70 条（公共规范接口、B-08 主数据接口、B-10 GIS 查询接口、B-11 设备台账接口、B-12/B-13 接入层接口、B-14 评分查询接口、B-15 标定与漂移接口、受保护业务查询、通知、WebSocket、模型网关、特征视图、统一审计、trace 查询与当前高风险策略）
   - gateway_client_rule：3 条（1 条 IP allowlist、1 条 IP blocklist、1 条用户 blocklist）
   - gateway_risk_audit：3 条（ALLOW / BLOCKLIST_MATCHED / RATE_LIMITED）
   - user_account: `U-B06-BLOCKED-001` 作为用户级 blocklist 样例
- 已新增 B-07 数据范围与 topic 联调样例：
   - object_scope_binding：23 条（覆盖 NODE / SEGMENT / FACILITY / DEVICE / INCIDENT / WORK_ORDER / MODEL_RESULT）
   - user_account: `U-B07-HZ-001` 作为单区域调度用户样例
   - 区域归属：`REGION-HZ` 与 `REGION-BINJIANG`
   - 巡检归属：`U-INSPECT-001 / zhangsan`
   - 算法脱敏样例：`MR-001 -> MASKED`
   - 领导聚合样例：`MR-002 -> AGGREGATED`
- 已新增 B-08 主数据对象链联调样例：
   - object_relation：13 条（覆盖 segment-start/end-node、segment-facility、node-facility、facility-device）
   - `/api/master/nodes|segments|facilities|devices` 可直接使用现有 H2 或 PostgreSQL 种子联调
   - `incident / work_order / model_result` 仍通过 `segment_id / node_id` 反查对象链
- 已新增 B-09 主数据版本联调样例：
   - master_change_request：4 条（PENDING / QUEUED / APPROVED / REJECTED）
   - object_version：11 条（覆盖 node / segment / facility / device 当前已归档版本）
   - master_data_audit：3 条（SUBMIT / APPROVE / REJECT）
   - `node / segment / facility / device` 初始 `version_no = 1`
   - 可直接用于联调变更申请、审批通过、审批拒绝和版本冲突场景
- 已新增 B-10 GIS 联调样例：
   - object_geo_index：11 条（3 个 NODE、2 条 SEGMENT、3 个 FACILITY、3 个 DEVICE）
   - bbox 范围样例可直接命中 `REGION-HZ` 与 `REGION-BINJIANG` 两组对象
   - 点查样例可直接命中 `NODE-001`
   - `SEGMENT` 已提供 `LINESTRING` 空间真值
- 已新增 B-11 设备台账联调样例：
   - `DEV-001`：在线正常设备
   - `DEV-002`：高缓冲预警设备，且标定已过期
   - `DEV-003`：心跳超时离线设备
   - `POST /api/device-ledger/devices/register` 可新增 `DEV-004` 用于联调 Upsert
- 已新增 B-12 接入层联调样例：
   - `INGB-SEED-MQTT-001`：2 条 MQTT 统一 DTO 样例
   - `INGB-SEED-MODBUS-001`：1 条 Modbus 寄存器映射样例
   - `INGB-SEED-NBIOT-001`：1 条 NB-IoT 回填样例，且 `isBackfill=true`
- 已新增 B-13 TSDB 联调样例：
   - `GET /api/ingest/write-logs` 可直接查看 5 条写入日志
   - `POST /api/ingest/backfill` 会自动写入 `ts_metric` 并生成 `ts_write_log`
   - `application-timescale.yml` 与 `timescale-init.sql` 可直接作为 PostgreSQL/Timescale 联调模板
- 已新增 B-14 数据质量联调样例：
   - `GET /api/dq/scores?dqLevel=D` 可直接命中 `INGR-SEED-010`
   - `GET /api/dq/scores/INGR-SEED-009` 可直接查看 `STABILITY_FLATLINE`
   - `GET /api/dq/scores/INGR-SEED-004` 可直接查看 `BACKFILL_DATA`
- 已新增 B-15 标定与漂移联调样例：
   - `GET /api/calibration/devices/DEV-001/profiles` 可直接查看 `CAL-2025-11 / CAL-2026-02`
   - `GET /api/calibration/devices/DEV-001/profiles/active?metricCode=PRESSURE` 可直接命中 `CAL-2026-02`
   - `GET /api/calibration/devices/DEV-001/drift-checks` 可直接查看 `CALD-SEED-001`
   - `GET /api/calibration/metrics/INGR-SEED-001/corrected` 可直接验证校正预览
- 已新增 B-25 模型网关联调样例：
   - `LEAK_DETECTOR`：`V1 ACTIVE / V2 GRAY / V0 ROLLED_BACK`
   - `CALIBRATION_DRIFT_GUARD`：`V1 ACTIVE / V2 DRAFT`
   - `MR-B25-MODEL-001`：成功推理结果样例
   - `MR-B25-FALLBACK-001`：超时后规则兜底结果样例
   - `model_invocation_audit` 可直接查看 `MODEL / RULE_FALLBACK` 两类结果来源
- 已新增 B-26 特征视图联调样例：
   - `feature_view_grant`：有效、过期、撤销三类明细授权样例
   - `feature_view_access_audit`：`MASKED / DETAIL / DENIED` 三类访问审计样例
   - `GET /api/feature-views/metrics?metricCode=PRESSURE` 可验证默认脱敏时序特征
   - `GET /api/feature-views/model-results?modelCode=LEAK_DETECTOR` 可验证模型结果脱敏视图
   - `/api/feature-views/**` Gateway 路由策略已写入测试数据
- 已新增 B-27 统一审计联调样例：
   - `audit_log`：7 条，覆盖登录/Token、应急旁路、权限变更、工单派单、模型回退和未来导出高风险访问
   - `GET /api/audit-logs?eventCategory=MODEL` 可验证模型回退统一审计
   - `GET /api/audit-logs/break-glass-report` 可验证应急旁路专项报表
   - `/api/audit-logs/**` Gateway 路由策略已写入测试数据
- 已新增 B-28 Trace 联调样例：
   - `trace_link_event`：8 条，覆盖 HTTP 入站、Outbox dispatch、通知消费、WebSocket 握手/推送/ack/断开
   - `GET /api/traces?traceId=TRACE-B28-E2E-001` 可验证整条链路
   - `GET /api/traces/TRACE-B28-E2E-001` 可验证按时间顺序回放
   - `/api/traces/**` Gateway 路由策略已写入测试数据
- 说明：
   - 仓库不保存旁路账号明文口令
   - `data.sql` 仅保存 BCrypt 哈希样例；联调时建议通过激活接口重新设置测试口令
   - Gateway 黑白名单和阈值只用于联调，不建议直接照搬到生产
- 可直接用于 B-01 分页联调：
   - /api/object-dictionary/page?page=1&pageSize=3
   - /api/object-dictionary/page?page=2&pageSize=3

## 快速验证命令
- 查询对象字典：
   - curl -s http://localhost:8080/api/object-dictionary -H "Authorization: Bearer <access_token>"
- 按 segment_id 查询对象链：
   - curl -s http://localhost:8080/api/object-chain/segment/SEG-001 -H "Authorization: Bearer <access_token>"
- 按 node_id 查询对象链：
   - curl -s http://localhost:8080/api/object-chain/node/NODE-002 -H "Authorization: Bearer <access_token>"
- 查询主数据节点：
   - curl -s "http://localhost:8080/api/master/nodes?page=1&pageSize=10" -H "Authorization: Bearer <access_token>"
- 查询主数据管段详情：
   - curl -s http://localhost:8080/api/master/segments/SEG-001 -H "Authorization: Bearer <access_token>"
- 查询主数据设备详情：
   - curl -s http://localhost:8080/api/master/devices/DEV-001 -H "Authorization: Bearer <access_token>"
- 查询设备台账列表：
   - curl -s "http://localhost:8080/api/device-ledger/devices?page=1&pageSize=10&status=WARNING" -H "Authorization: Bearer <access_token>"
- 查询设备台账详情：
   - curl -s http://localhost:8080/api/device-ledger/devices/DEV-002 -H "Authorization: Bearer <access_token>"
- 注册设备台账：
   - curl -s -X POST http://localhost:8080/api/device-ledger/devices/register -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"deviceId\":\"DEV-004\",\"deviceName\":\"液位计-04\",\"facilityId\":\"FAC-002\",\"segmentId\":\"SEG-001\",\"nodeId\":\"NODE-002\",\"protocolType\":\"MQTT\",\"calibrationDueAt\":\"2099-12-31T23:59:59\"}"
- 上报设备心跳：
   - curl -s -X POST http://localhost:8080/api/device-ledger/devices/DEV-002/heartbeat -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"heartbeatTime\":\"2026-04-23T01:00:00\",\"recvTime\":\"2026-04-23T01:00:03\",\"bufferLevel\":85,\"abnormalFlags\":[\"BUFFER_BACKLOG\"]}"
- 写入统一入站批次：
   - curl -s -X POST http://localhost:8080/api/ingest/metrics -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"protocolType\":\"MQTT\",\"sourceType\":\"EDGE_GATEWAY\",\"sourceKey\":\"EDGE-HZ-GW-02\",\"traceId\":\"TRACE-INGEST-001\",\"isBackfill\":false,\"metrics\":[{\"deviceId\":\"DEV-001\",\"metricCode\":\"PRESSURE\",\"value\":0.92,\"eventTime\":\"2026-04-23T09:00:00\",\"recvTime\":\"2026-04-23T09:00:03\",\"deviceTime\":\"2026-04-23T08:59:58\",\"attributes\":{\"topic\":\"region/hz/dev-001/pressure\",\"qos\":1}}]}"
- 触发协议适配骨架：
   - curl -s -X POST http://localhost:8080/api/ingest/adapt/MODBUS -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"sourceType\":\"PLC_GATEWAY\",\"sourceKey\":\"PLC-HZ-02\",\"traceId\":\"TRACE-INGEST-ADAPT-001\",\"isBackfill\":false,\"payload\":{\"deviceId\":\"DEV-002\",\"registerAddress\":\"40001\",\"registerValue\":41.8,\"sampledAt\":\"2026-04-23T10:00:00\",\"gatewayReceivedAt\":\"2026-04-23T10:00:02\",\"controllerTime\":\"2026-04-23T09:59:59\",\"slaveId\":\"8\"}}"
- 提交补偿写入：
   - curl -s -X POST http://localhost:8080/api/ingest/backfill -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"protocolType\":\"MQTT\",\"sourceType\":\"EDGE_GATEWAY\",\"sourceKey\":\"EDGE-HZ-GW-05\",\"traceId\":\"TRACE-BACKFILL-001\",\"batchNo\":\"BATCH-001\",\"seqNo\":1,\"originalSampleTime\":\"2026-04-20T09:00:00\",\"isBackfill\":true,\"metrics\":[{\"deviceId\":\"DEV-001\",\"metricCode\":\"PRESSURE\",\"value\":0.91,\"eventTime\":\"2026-04-20T09:00:00\",\"recvTime\":\"2026-04-24T09:00:00\",\"deviceTime\":\"2026-04-20T08:59:59\"}]}"
- 查询接入批次列表：
   - curl -s "http://localhost:8080/api/ingest/batches?page=1&pageSize=10" -H "Authorization: Bearer <access_token>"
- 查询接入批次详情：
   - curl -s http://localhost:8080/api/ingest/batches/INGB-SEED-MQTT-001 -H "Authorization: Bearer <access_token>"
- 查询 TSDB 写入日志：
   - curl -s "http://localhost:8080/api/ingest/write-logs?page=1&pageSize=10" -H "Authorization: Bearer <access_token>"
- 手动重试 TSDB 写入：
   - curl -s -X POST http://localhost:8080/api/ingest/write-logs/<writeLogId>/retry -H "Authorization: Bearer <admin_access_token>"
- 查询数据质量评分列表：
   - curl -s "http://localhost:8080/api/dq/scores?page=1&pageSize=10&dqLevel=D" -H "Authorization: Bearer <access_token>"
- 查询单条数据质量评分：
   - curl -s http://localhost:8080/api/dq/scores/INGR-SEED-009 -H "Authorization: Bearer <admin_access_token>"
- 创建标定版本：
   - curl -s -X POST http://localhost:8080/api/calibration/devices/DEV-001/profiles -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"profileVersion\":\"CAL-2026-07\",\"metricCode\":\"PRESSURE\",\"calibratedAt\":\"2026-04-23T09:00:00\",\"effectiveFrom\":\"2026-04-23T09:00:00\",\"effectiveUntil\":\"2026-10-31T23:59:59\",\"operatorName\":\"赵工\",\"referenceStandard\":\"STD-PRESSURE-C\",\"correctionSlope\":1.020000,\"correctionOffset\":-0.010000,\"driftThresholdAbs\":0.150000,\"driftThresholdPct\":8.000000,\"activate\":true}"
- 查询设备标定档案：
   - curl -s http://localhost:8080/api/calibration/devices/DEV-001/profiles -H "Authorization: Bearer <access_token>"
- 查询当前激活标定版本：
   - curl -s "http://localhost:8080/api/calibration/devices/DEV-001/profiles/active?metricCode=PRESSURE" -H "Authorization: Bearer <access_token>"
- 提交漂移复核：
   - curl -s -X POST http://localhost:8080/api/calibration/devices/DEV-001/drift-checks -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"profileVersion\":\"CAL-2026-02\",\"metricCode\":\"PRESSURE\",\"observedValue\":1.350000,\"referenceValue\":0.850000,\"checkedAt\":\"2026-04-23T10:30:00\",\"checkedBy\":\"zhangsan\"}"
- 查询漂移复核记录：
   - curl -s http://localhost:8080/api/calibration/devices/DEV-001/drift-checks -H "Authorization: Bearer <access_token>"
- 查询校正预览：
   - curl -s "http://localhost:8080/api/calibration/metrics/INGR-SEED-001/corrected?profileVersion=CAL-2026-02" -H "Authorization: Bearer <access_token>"
- 查看提醒/检修工单样例：
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM incident WHERE incident_id LIKE 'INC-CAL-%' ORDER BY created_at DESC;`
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM work_order WHERE work_order_id LIKE 'WO-CAL-%' ORDER BY created_at DESC;`
- 提交主数据变更申请：
   - curl -s -X POST http://localhost:8080/api/master/changes -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"objectType\":\"DEVICE\",\"objectId\":\"DEV-002\",\"baseVersionNo\":1,\"reason\":\"upgrade device metadata\",\"payload\":{\"deviceName\":\"流量计-02-升级版\",\"protocolType\":\"NB-IOT\"}}"
- 查询主数据变更列表：
   - curl -s "http://localhost:8080/api/master/changes?page=1&pageSize=10" -H "Authorization: Bearer <admin_access_token>"
- 审批通过主数据变更：
   - curl -s -X POST http://localhost:8080/api/master/changes/<requestId>/approve -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"reason\":\"approved for production\"}"
- 驳回主数据变更：
   - curl -s -X POST http://localhost:8080/api/master/changes/<requestId>/reject -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"reason\":\"payload needs revision\"}"
- 查看主数据版本与审计样例：
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM master_change_request ORDER BY created_at DESC;`
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM object_version ORDER BY created_at DESC;`
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM master_data_audit ORDER BY created_at DESC;`
- 查询 GIS 字段规范：
   - curl -s http://localhost:8080/api/gis/field-spec
- 坐标转换：
   - curl -s -X POST http://localhost:8080/api/gis/convert -H "Content-Type: application/json" -d "{\"authoritySrid\":\"EPSG:4490\",\"displaySrid\":\"EPSG:3857\",\"geometry2d\":\"POINT(120.1533 30.2741)\"}"
- LINESTRING 坐标转换：
   - curl -s -X POST http://localhost:8080/api/gis/convert -H "Content-Type: application/json" -d "{\"authoritySrid\":\"EPSG:4490\",\"displaySrid\":\"EPSG:3857\",\"geometry2d\":\"LINESTRING(120.1533 30.2741,120.1634 30.2842)\"}"
- 深度校验：
   - curl -s -X POST http://localhost:8080/api/gis/depth/validate -H "Content-Type: application/json" -d "{\"zTop\":2.50,\"zBottom\":-1.20,\"buryDepth\":3.70,\"elevationRef\":\"MSL\"}"
- GIS bbox 查询：
   - curl -s "http://localhost:8080/api/gis/objects/bbox?minX=120.15&minY=30.27&maxX=120.18&maxY=30.30&authoritySrid=EPSG:4490&displaySrid=EPSG:3857&objectType=NODE&page=1&pageSize=10" -H "Authorization: Bearer <hz_scope_access_token>"
- GIS 对象点查：
   - curl -s -X POST http://localhost:8080/api/gis/objects/pick -H "Authorization: Bearer <hz_scope_access_token>" -H "Content-Type: application/json" -d "{\"x\":120.1533,\"y\":30.2741,\"authoritySrid\":\"EPSG:4490\",\"displaySrid\":\"EPSG:3857\",\"objectTypes\":[\"NODE\"],\"toleranceMeters\":50}"
- GIS 对象详情：
   - curl -s "http://localhost:8080/api/gis/objects/SEGMENT/SEG-001?displaySrid=EPSG:3857" -H "Authorization: Bearer <admin_access_token>"
- 查看 A-04 矩阵规范：
   - curl -s http://localhost:8080/api/authz/matrix-spec
- 查看角色矩阵（数据库展开后）：
   - curl -s http://localhost:8080/api/authz/matrix
- 查看用户权限快照：
   - curl -s http://localhost:8080/api/authz/users/U-DISPATCH-001/snapshot -H "Authorization: Bearer <dispatch_access_token>"
- 验证“区域调度员跨区拒绝”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Authorization: Bearer <dispatch_access_token>" -H "Content-Type: application/json" -d "{\"userId\":\"U-DISPATCH-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"regionId\":\"REGION-SH\",\"topic\":\"region.REGION-SH.alerts.critical\",\"dataView\":\"AGGREGATED\"}"
- 验证“巡检仅本人任务”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Authorization: Bearer <inspector_access_token>" -H "Content-Type: application/json" -d "{\"userId\":\"U-INSPECT-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"assignee\":\"zhangsan\",\"topic\":\"user.U-INSPECT-001.workorder.created\",\"dataView\":\"AGGREGATED\"}"
- 验证“算法工程师默认仅脱敏视图”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Authorization: Bearer <algo_access_token>" -H "Content-Type: application/json" -d "{\"userId\":\"U-ALGO-001\",\"entryPermission\":\"ENTRY:DIAG\",\"menuPermission\":\"MENU:MODEL:READ\",\"dataView\":\"MASKED_FEATURE\",\"topic\":\"diag.model.inference\"}"
- 验证“B-07 单区域调度只能访问本区对象”：
   - curl -s http://localhost:8080/api/object-chain/segment/SEG-001 -H "Authorization: Bearer <hz_scope_access_token>"
   - curl -s http://localhost:8080/api/object-chain/segment/SEG-002 -H "Authorization: Bearer <hz_scope_access_token>"
- 验证“B-08 单区域调度主数据过滤”：
   - curl -s "http://localhost:8080/api/master/nodes?page=1&pageSize=10" -H "Authorization: Bearer <hz_scope_access_token>"
   - curl -s http://localhost:8080/api/master/devices/DEV-003 -H "Authorization: Bearer <hz_scope_access_token>"
- 验证 topic 检查：
   - curl -s -X POST http://localhost:8080/api/authz/topics/check -H "Authorization: Bearer <hz_scope_access_token>" -H "Content-Type: application/json" -d "{\"userId\":\"U-B07-HZ-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"regionId\":\"REGION-HZ\",\"requestedTopics\":[\"region.REGION-HZ.alerts.critical\",\"region.REGION-BINJIANG.alerts.critical\"]}"
- 验证 topic 模拟订阅：
   - curl -s -X POST http://localhost:8080/api/authz/topics/subscribe -H "Authorization: Bearer <hz_scope_access_token>" -H "Content-Type: application/json" -d "{\"userId\":\"U-B07-HZ-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"regionId\":\"REGION-HZ\",\"requestedTopics\":[\"region.REGION-HZ.alerts.critical\"]}"
- 分页接口联调：
   - curl -s "http://localhost:8080/api/object-dictionary/page?page=1&pageSize=3" -H "Authorization: Bearer <access_token>"
- OpenAPI 文档检查：
   - curl -s http://localhost:8080/v3/api-docs
- 获取正式 OIDC 登录描述：
   - curl -s "http://localhost:8080/api/auth/login?redirectUri=http://localhost:5173/auth/callback"
- 获取 OIDC 登录入口：
   - curl -s "http://localhost:8080/api/auth/login-url?redirectUri=http://localhost:5173/auth/callback"
- 使用 code/state 换取本地 token：
   - curl -s -X POST http://localhost:8080/api/auth/callback -H "Content-Type: application/json" -d "{\"code\":\"<oidc_code>\",\"state\":\"<oidc_state>\",\"redirectUri\":\"http://localhost:5173/auth/callback\"}"
- 使用 refresh token 轮换：
   - 先通过 `POST /api/auth/callback` 或 `POST /api/auth/emergency/login` 获取真实 refresh token，再调用 `POST /api/auth/refresh`
- OIDC 关闭时查看登录状态：
   - curl -s http://localhost:8080/api/auth/me
- OIDC 开启且已登录后获取当前用户：
   - curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer <access_token>"
- OIDC 开启且已登录后执行统一登出：
   - curl -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <access_token>"
- 以平台管理员身份禁用用户并触发全量失效：
   - curl -s -X POST http://localhost:8080/api/auth/admin/users/U-INSPECT-001/disable -H "Authorization: Bearer <admin_access_token>"
- 以平台管理员身份触发权限重大变更收敛：
   - curl -s -X POST http://localhost:8080/api/auth/admin/users/U-OIDC-001/permissions/revoke -H "Authorization: Bearer <admin_access_token>"
- 激活应急旁路账号并设置临时口令：
   - curl -s -X POST http://localhost:8080/api/auth/emergency/accounts/EA-B05-INACTIVE-001/activate -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"password\":\"Temp#2026\",\"expiresAt\":\"2026-12-31T23:59:59\",\"reason\":\"drill\"}"
- 使用应急旁路账号登录：
   - curl -s -X POST http://localhost:8080/api/auth/emergency/login -H "Content-Type: application/json" -d "{\"username\":\"bg_inactive_hz\",\"password\":\"Temp#2026\"}"
- 撤销应急旁路账号：
   - curl -s -X POST http://localhost:8080/api/auth/emergency/accounts/EA-B05-INACTIVE-001/revoke -H "Authorization: Bearer <admin_access_token>" -H "Content-Type: application/json" -d "{\"reason\":\"drill finished\"}"
- 查询应急旁路独立审计：
   - curl -s "http://localhost:8080/api/auth/emergency/audit?page=0&pageSize=20" -H "Authorization: Bearer <admin_access_token>"
- 验证 Gateway IP blocklist 拒绝：
   - curl -s http://localhost:8080/api/menu-boundaries -H "X-Forwarded-For: 203.0.113.77" -H "X-Trace-Id: TRACE-B06-BLOCK"
- 验证 Gateway 分页参数限制：
   - curl -s "http://localhost:8080/api/object-dictionary/page?page=1&pageSize=10000" -H "Authorization: Bearer <access_token>" -H "X-Trace-Id: TRACE-B06-PAGE"
- 验证 Gateway Content-Type 校验：
   - curl -s -X POST http://localhost:8080/api/auth/refresh -H "Content-Type: text/plain" -d "not-json" -H "X-Trace-Id: TRACE-B06-CT"
- 验证应急登录接口限流：
   - 连续多次执行 `curl -s -X POST http://localhost:8080/api/auth/emergency/login -H "Content-Type: application/json" -H "X-Trace-Id: TRACE-B06-RATE" -d "{\"username\":\"bg_active_hz\",\"password\":\"wrong-password\"}"`
- 查看 Gateway 审计样例：
   - H2 Console / PostgreSQL 中执行 `SELECT * FROM gateway_risk_audit ORDER BY created_at DESC;`

## 下一步建议
- F-01 前端 Portal 联调已新增 `frontend/`：
   - 安装依赖：`cd ../frontend && npm install`
   - 启动前端：`npm run dev -- --host 0.0.0.0`
   - 默认访问：`http://localhost:5173/portal`
   - 默认后端：`VITE_API_BASE_URL=http://localhost:8080`；如需切换后端地址，可在前端启动前设置该环境变量。
   - Portal 依赖接口：`GET /api/platforms`、`GET /api/menu-boundaries`、`GET /api/auth/login-url`、`GET /api/auth/me`、`POST /api/auth/logout`、`GET /api/workorders`、`GET /api/notifications`。
   - 无 token 时，Portal 仍展示三平台入口，待办汇总显示“需登录并具备权限后查看”；有有效 access token 时，会读取用户快照、工单和通知分页摘要。
   - F-01 只实现 Portal 壳、三平台入口与占位页；`/mgmt`、`/emgc`、`/diag` 不承载业务编辑，支撑层不作为第四入口。
- F-02 OIDC 回调页已新增 `frontend/src/views/AuthCallbackView.vue`：
   - 回调地址固定为：`http://localhost:5173/auth/callback`
   - Keycloak / OIDC Client Redirect URI 建议配置：`http://localhost:5173/auth/callback`
   - 前端发起登录时调用 `GET /api/auth/login-url?redirectUri=http://localhost:5173/auth/callback`，并将后端返回的 `state / stateExpiresAt / redirectUri` 保存到 `sessionStorage`。
   - 回调页读取 `code/state/error/error_description`；取消登录或 Provider 返回 `error` 时不调用后端 callback。
   - code 交换调用：`POST /api/auth/callback`，请求体为 `{"code":"<oidc_code>","state":"<oidc_state>","redirectUri":"http://localhost:5173/auth/callback"}`。
   - 成功后前端将 `accessToken / refreshToken / tokenType / expiresAt` 保存到 `sessionStorage`，短暂成功态后跳回 `/portal`；F-03 再实现自动刷新与并发刷新锁。
   - 常见失败：`OIDC_DISABLED` 表示后端未启用 OIDC；`OIDC_STATE_INVALID` 表示 state 不存在、过期、已消费或 redirectUri 不匹配；页面会显示 traceId 并提供重新登录/返回 Portal。
- F-03 Token 刷新与登出前端逻辑已接入统一请求层：
   - 前端 token pair 继续保存在 `sessionStorage`，包括 `accessToken / refreshToken / accessTokenExpiresAt / refreshTokenExpiresAt / tokenType`。
   - 业务请求默认会在 access token 距离过期小于 2 分钟时静默调用 `POST /api/auth/refresh`，请求体为 `{"refreshToken":"<refresh_token>"}`。
   - 多个并发请求同时触发刷新时，前端复用同一个 refresh promise；刷新成功后原请求最多重试一次，避免 refresh token rotation 自相冲突。
   - `GET /api/auth/login-url`、`POST /api/auth/callback`、`POST /api/auth/refresh`、`POST /api/auth/logout` 跳过递归刷新。
   - `TOKEN_REFRESH_EXPIRED / TOKEN_REFRESH_REVOKED / TOKEN_REFRESH_REPLAY_DETECTED / TOKEN_REFRESH_INVALID` 会触发本地强制登出，清理 token pair，并在 Portal 登录态面板显示原因与 traceId。
   - `POST /api/auth/logout` 无论后端返回成功、401 还是 OIDC disabled，前端都会清理本地 token pair 并回到未登录态。
   - 联调建议：先通过 OIDC callback 获取真实 token pair，再访问 `/portal`；可用旧 refresh token 或已撤销 token 验证强制登出路径。
- F-04 路由守卫与按钮级权限控制已接入前端权限快照：
   - 前端路由 `/mgmt`、`/emgc`、`/diag` 分别校验 `ENTRY:MGMT + MENU:ASSET:READ`、`ENTRY:EMGC + MENU:WORKORDER:READ`、`ENTRY:DIAG + MENU:MODEL:READ`。
   - `/portal` 保持三平台卡片可见；无 `ENTRY:*` 的平台卡片会显示锁定态，不隐藏入口，也不会跳转业务平台。
   - 已登录但缺少路由权限时进入 `/forbidden`，页面展示 required/missing 权限、当前角色、权限快照摘要与 traceId，并可刷新权限快照。
   - 平台占位页仅展示按钮级权限状态，不执行真实业务编辑；写类按钮分别校验 `MENU:ASSET:WRITE`、`MENU:WORKORDER:DISPATCH`、`MENU:MODEL:WRITE`。
   - 联调角色预期：平台管理员可进入 MGMT/EMGC/DIAG；区域调度员可进入 MGMT/EMGC；巡检人员只进入 EMGC；算法工程师只进入 DIAG；领导只读进入 EMGC 且写按钮锁定。
   - 权限快照验证：`curl -s http://localhost:8080/api/authz/users/<userId>/snapshot -H "Authorization: Bearer <access_token>"`，确认 `snapshot.permissionCodes` 与前端展示一致。
   - 后端最终校验仍以 `AuthzGuardAspect` 为准；可用 `POST /api/authz/check` 验证 ENTRY、MENU、dataScope、topicScope 联合鉴权。
- F-05 2D 一张图主页面已接入综合管理平台：
   - 前端新增依赖 `leaflet` 与类型包 `@types/leaflet`；安装依赖后启动：`cd ../frontend && npm install && npm run dev -- --host 0.0.0.0`。
   - 页面地址：`http://localhost:5173/mgmt/gis`，从 `/mgmt` 占位页也可点击“进入 2D 一张图”。
   - 权限要求：`ENTRY:MGMT + MENU:ASSET:READ`；未登录会回到 Portal，缺权限会进入 `/forbidden`。
   - 默认 bbox：`minX=120.15&minY=30.27&maxX=120.18&maxY=30.30&authoritySrid=EPSG:4490&displaySrid=EPSG:4490&page=1&pageSize=50`。
   - 页面调用 `GET /api/gis/objects/bbox` 绘制 `NODE / SEGMENT / FACILITY / DEVICE`，点击对象后调用 `GET /api/gis/objects/{objectType}/{objectId}?displaySrid=EPSG:4490` 打开右侧详情抽屉。
   - 地图空白点击会调用 `POST /api/gis/objects/pick`，请求体包含 `x/y/authoritySrid/displaySrid/objectTypes/toleranceMeters`；未命中显示空结果提示，不伪造业务数据。
   - 默认不接公网瓦片，使用本地深色网格底图；F-06 再补图层透明度、图例与状态持久化，F-11 再补 WebSocket 地图刷新事件。
- F-15 3D 占位与降级页已新增 `/mgmt/gis/3d`：
   - 前端新增依赖 `three` 与类型包 `@types/three`，不新增后端 3D API。
   - 3D 页面复用 `GET /api/gis/objects/bbox` 绘制轻量管网线框；若 query 携带 `objectType / objectId`，额外调用 `GET /api/gis/objects/{objectType}/{objectId}` 高亮上下文对象。
   - 入口 query 支持 `objectType / objectId / displaySrid / minX / minY / maxX / maxY`；从 3D 降级回 `/mgmt/gis` 时完整保留 query。
   - WebGL 不可用、Three 初始化异常、canvas 不可用、GIS API 失败或 bbox 空数据都会进入 fallback 状态，展示原因与 traceId，并默认 3 秒后回到 2D 主入口。
- F-06 图层控制面板已升级 `/mgmt/gis`：
   - 图层固定为 `SEGMENT / DEVICE / NODE / FACILITY / ALERT`，支持显隐、透明度、图例折叠、全部显示、全部隐藏、恢复默认。
   - 图层偏好保存在浏览器 `localStorage`，key 为 `uscdip.gis.layerState.v1`；刷新页面后恢复显隐、透明度和图例折叠状态。
   - 切换图层不重建 Leaflet map，不重新请求 bbox；前端为每个业务层维护独立 `LayerGroup`，显隐只 add/remove group，透明度只更新 layer style。
   - 告警图层调用 `GET /api/alerts?page=1&pageSize=50`，按 `deviceId / segmentId / nodeId` 匹配当前 bbox 已加载 GIS 对象；无法匹配空间对象的告警只计数，不绘制假坐标。
   - 联调建议：打开浏览器 DevTools Network，切换图层显隐和透明度时不应出现新的 `/api/gis/objects/bbox` 请求；点击“重新同步 bbox / 告警”时才重新请求 bbox 与 alerts。
   - 告警接口失败只影响告警图层状态，基础 GIS 对象图层、对象点击和详情抽屉继续可用。
- F-07 资产对象检索与地图定位已升级 `/mgmt/gis`：
   - 左侧面板顶部新增资产检索，支持按编码、名称、类型搜索 `NODE / SEGMENT / FACILITY / DEVICE`，类型筛选包含 `全部 / 节点 / 管线 / 设施 / 设备`。
   - 前端不新增后端接口，通过 `GET /api/master/nodes|segments|facilities|devices?page=1&pageSize=100` 建立主数据索引；关键词至少 2 个字符触发，防抖 250ms，每类型最多读取 10 页。
   - 点击搜索结果后调用 `GET /api/gis/objects/{objectType}/{objectId}?displaySrid=EPSG:4490`，定位到地图并打开右侧详情抽屉；会自动恢复对应业务图层可见。
   - 若搜索命中的对象不在当前 bbox 已绘制图层中，前端使用独立琥珀色搜索高亮层展示，不计入 bbox total / 已绘制统计；无 geometry 或 GIS 详情 404 时只提示“可检索但不可定位”，不伪造坐标。
   - 联调建议：搜索 `NODE-001`、`SEG-001`、`压力传感器` 验证结果字段；浏览器 Network 应看到主数据分页请求和选中后的 GIS detail 请求。
- F-08 资产详情抽屉已升级 `/mgmt/gis`：
   - 右侧抽屉改为 `基础档案 / 关联设备 / 历史告警 / 事件 / 工单` 多标签资产上下文面板，保留地图定位与对象链切换能力。
   - 基础档案调用 `GET /api/master/nodes|segments|facilities|devices/{id}`，并与 GIS 详情中的空间字段、bbox、anchorPoint、attributes 合并展示。
   - 关联设备调用 `GET /api/device-ledger/devices` 或 `GET /api/device-ledger/devices/{deviceId}`；NODE/SEGMENT/FACILITY 按 nodeId/segmentId/facilityId 聚合设备，DEVICE 展示自身台账。
   - 历史告警与事件以关联设备为主线调用 `GET /api/alerts?deviceId=...`、`GET /api/incidents?deviceId=...` 并去重；工单 tab 再按事件 `incidentId` 调用 `GET /api/workorders?incidentId=...` 聚合。
   - 工单接口需要 EMGC 工单读权限；缺权限时只在工单 tab 显示错误与 traceId，不影响基础档案、关联设备、告警、事件 tab。
   - 联调建议：分别点击 `NODE-001 / SEG-001 / FAC-001 / DEV-001`，验证基础档案、关联设备、告警、事件、工单 tab 的空态、错误态和数据态。
- F-09 设备台账列表页已新增 `/mgmt/devices`：
   - 页面入口位于 `/mgmt` 综合管理平台，占用权限 `ENTRY:MGMT + MENU:ASSET:READ`，与 `/mgmt/gis` 使用同一资产读权限边界。
   - 列表调用 `GET /api/device-ledger/devices?page=1&pageSize=20`，支持 `status / regionId / segmentId / nodeId / facilityId / protocolType / calibrationExpired / page / pageSize` 筛选。
   - 点击设备行调用 `GET /api/device-ledger/devices/{deviceId}` 打开右侧详情面板，展示心跳链路、缓冲水位、异常标记、对象链、标定到期和 versionNo。
   - 在线状态直接展示后端返回的 `onlineStatus / onlineStatusReason`，不在前端按布尔值重算；页面指标为当前查询页聚合。
   - 联调建议：访问 `http://localhost:5173/mgmt/devices`，验证 `DEV-001` 在线正常、`DEV-002` 高缓冲预警且标定过期、`DEV-003` 心跳超时离线；筛选 `status=WARNING` 和 `calibrationExpired=true` 时 Network 请求参数应同步变化。
- F-10 时序趋势图页已新增 `/mgmt/trends`：
   - 页面入口位于 `/mgmt` 综合管理平台，占用权限 `ENTRY:MGMT + MENU:ASSET:READ`；设备台账详情可携带 `deviceId` 跳转趋势页。
   - `GET /api/dq/scores` 已新增 `startTime / endTime` 查询参数，按 `ts_metric.event_time` 过滤；`startTime > endTime` 返回 `DQ_QUERY_INVALID`。
   - 趋势页调用 `GET /api/dq/scores?page=1&pageSize=100&deviceId=DEV-001&metricCode=PRESSURE&startTime=...&endTime=...`，展示指标折线、DQ 虚线、补偿样本琥珀标记和低质量红色点。
   - 下方明细表展示 `sourceRecordId / eventTime / metricValue / isBackfill / dqScore / dqLevel / dqFlags / recvTime / deviceTime`；后端 total 超过 pageSize 时显示分页截断提示。
   - 联调建议：访问 `http://localhost:5173/mgmt/trends?deviceId=DEV-001&metricCode=PRESSURE`；筛选 `isBackfill=true` 验证补偿样本，筛选 `dqLevel=D` 验证低质量点与 `dqFlags`。
- 继续推进 F-11 实时告警列表 + WebSocket 客户端。
- 接入 Flyway，落地版本化迁移脚本。
- 评估将单实例内存限流升级为 Redis 共享限流。

### F-12 事件详情与工单详情页 (`frontend/src/views/*DetailView.vue`)
- **入口**: `/mgmt/incidents/:id` & `/emgc/workorders/:id`
- **功能**: 展示事件流转和设备检修派单的全周期记录，包含交互动作(确认/接单)。
- **关键技术**: 采用预设的 `SaaS Analytics` UI、包含时间线流和状态角标，调用 `IncidentResponse` 与 `WorkOrderResponse` 数据模型。
