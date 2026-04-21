# USCDIP Backend Bootstrap

该目录提供一期后端 Java 启动骨架，当前已实现：
- A-01 平台边界配置读取与查询 API
- A-02 统一对象主键与对象链字典（数据库版）
- A-03 坐标与深度字段冻结（GIS 字段规范、坐标转换、深度校验）
- A-04 权限模型与数据范围矩阵（RBAC + 数据范围 + topic 订阅范围）

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
- 说明：当前版本对 POINT WKT 提供服务化转换，覆盖 4490<->3857；其他 SRID 组合返回 passthrough 结果。

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

## 测试数据说明
- 文件：src/main/resources/data.sql
- 已生成可联调测试数据，覆盖七类对象：
   - node：3 条
   - segment：2 条
   - facility：3 条
   - device：3 条
   - incident：2 条
   - work_order：2 条
   - model_result：2 条
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
   - gateway_route_policy：21 条（公共规范接口、受保护业务查询、当前高风险、未来导出/批量派单/模型发布预置策略）
   - gateway_client_rule：3 条（1 条 IP allowlist、1 条 IP blocklist、1 条用户 blocklist）
   - gateway_risk_audit：3 条（ALLOW / BLOCKLIST_MATCHED / RATE_LIMITED）
   - user_account: `U-B06-BLOCKED-001` 作为用户级 blocklist 样例
- 已新增 B-07 数据范围与 topic 联调样例：
   - object_scope_binding：17 条（覆盖 NODE / SEGMENT / FACILITY / DEVICE / INCIDENT / WORK_ORDER / MODEL_RESULT）
   - user_account: `U-B07-HZ-001` 作为单区域调度用户样例
   - 区域归属：`REGION-HZ` 与 `REGION-BINJIANG`
   - 巡检归属：`U-INSPECT-001 / zhangsan`
   - 算法脱敏样例：`MR-001 -> MASKED`
   - 领导聚合样例：`MR-002 -> AGGREGATED`
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
- 查询 GIS 字段规范：
   - curl -s http://localhost:8080/api/gis/field-spec
- 坐标转换：
   - curl -s -X POST http://localhost:8080/api/gis/convert -H "Content-Type: application/json" -d "{\"authoritySrid\":\"EPSG:4490\",\"displaySrid\":\"EPSG:3857\",\"geometry2d\":\"POINT(120.1533 30.2741)\"}"
- 深度校验：
   - curl -s -X POST http://localhost:8080/api/gis/depth/validate -H "Content-Type: application/json" -d "{\"zTop\":2.50,\"zBottom\":-1.20,\"buryDepth\":3.70,\"elevationRef\":\"MSL\"}"
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
- 在 B-07 的 `TopicAuthorizationService` 之上接入 B-24 WebSocket 握手与订阅鉴权。
- 接入 Flyway，落地版本化迁移脚本。
- 评估将单实例内存限流升级为 Redis 共享限流。
