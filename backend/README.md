# USCDIP Backend Bootstrap

该目录提供一期后端 Java 启动骨架，当前已实现：
- A-01 平台边界配置读取与查询 API
- A-02 统一对象主键与对象链字典（数据库版）
- A-03 坐标与深度字段冻结（GIS 字段规范、坐标转换、深度校验）
- A-04 权限模型与数据范围矩阵（RBAC + 数据范围 + topic 订阅范围）
- B-11 设备台账与心跳接口（设备注册、心跳上报、在线状态计算）
- B-12 统一入站 DTO 与协议适配骨架（统一 DTO、协议适配、接入批次落库）
- B-13 TSDB 写入与补偿写入服务（在线写入、补偿写入、写入日志与重试）
- B-14 数据质量评分服务（dq_score / dq_flags / 查询接口）
- B-15 标定与漂移管理接口（标定版本、漂移复核、到期提醒与校正预览）
- B-16 边缘断网补偿接口（batchNo / seqNo / originalSampleTime、乱序回传、重复幂等与冲突拦截）
- B-17 告警规则引擎一期骨架（阈值规则、组合规则、自动/手动评估与告警记录查询）
- B-18 告警去重 / 抑制 / 升级服务（alert_policy / alert_case、去重窗口、抑制窗口、升级与恢复扫描）
- B-19 事件化服务（incident 事件化、最小 incident API、人工确认与自动恢复）

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
- 平台查询接口：按平台编码读取边界定义
- A-02 对象链实体：node、segment、facility、device、incident、work_order、model_result
- A-02 对象链接口：按 segment_id 和 node_id 查询完整对象链
- A-03 字段冻结：authority_srid、display_srid、geometry_2d、z_top、z_bottom、bury_depth、elevation_ref
- A-03 坐标转换：统一后端服务化处理（禁止前端/导入工具私算）
- A-04 约束表达：access = entryPermission && menuPermission && dataScope && topicScope
- A-04 角色覆盖：平台管理员、区域调度员、巡检人员、算法工程师、领导只读
- A-04 测试约束：跨区订阅拒绝、巡检仅本人任务、算法默认脱敏视图

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
   - 支持 `deviceId / metricCode / dqLevel / minScore / maxScore / sourceBatchId / isBackfill`
- `GET /api/dq/scores/{sourceRecordId}`

### 3) 数据库与联调说明
- B-14 不新增独立评分表，直接扩展 `ts_metric`：
   - `dq_score / dq_level / dq_flags`
   - `dq_completeness / dq_validity / dq_timeliness / dq_consistency / dq_stability`
   - `dq_alarm_conf_factor / dq_scored_at`
- 查询接口继续复用 B-07 数据范围，只允许看到有权限设备对应的评分样本。
- 一期范围画像内置在代码中，覆盖 `PRESSURE / TEMPERATURE / VIBRATION / 40001`；未知指标按降权处理并标记 `VALIDITY_PROFILE_MISSING`。

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
- `work_order` 本轮不自动写入，仅继续保留后续 B-22 的联动位。
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
- B-16 / B-17 / B-18 / B-19 同样复用以上三套数据库配置；H2 默认自动装载补偿、告警规则、告警策略、case 与 incident 事件化种子，PostgreSQL / Timescale 首次联调仍需手动导入 `src/main/resources/data.sql`

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
   - work_order：4 条
   - model_result：2 条
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
   - gateway_route_policy：52 条（公共规范接口、B-08 主数据接口、B-10 GIS 查询接口、B-11 设备台账接口、B-12/B-13 接入层接口、B-14 评分查询接口、B-15 标定与漂移接口、受保护业务查询、当前高风险、未来导出/批量派单/模型发布预置策略）
   - gateway_client_rule：3 条（1 条 IP allowlist、1 条 IP blocklist、1 条用户 blocklist）
   - gateway_risk_audit：3 条（ALLOW / BLOCKLIST_MATCHED / RATE_LIMITED）
   - user_account: `U-B06-BLOCKED-001` 作为用户级 blocklist 样例
- 已新增 B-07 数据范围与 topic 联调样例：
   - object_scope_binding：21 条（覆盖 NODE / SEGMENT / FACILITY / DEVICE / INCIDENT / WORK_ORDER / MODEL_RESULT）
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
- 接入 Flyway，落地版本化迁移脚本。
- 评估将单实例内存限流升级为 Redis 共享限流。
