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
- POST /api/auth/logout：OIDC 开启后执行统一登出并返回 providerLogoutUrl；OIDC 关闭时返回 OIDC_DISABLED

### 5) 默认本地模式
- 默认 `BACKEND_OIDC_ENABLED=false`，不会触发 OIDC issuer discovery。
- 此模式下，A-01/A-02/A-03/A-04 的规范与演示接口保持匿名可访问，便于本地开发与联调。
- 此模式下：
   - GET /api/auth/login 与 GET /api/auth/login-url 返回 `enabled=false`
   - GET /api/auth/me 与 POST /api/auth/logout 返回 `503 + OIDC_DISABLED`

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
- B-03 新增认证表：
   - auth_oidc_state
   - auth_refresh_token
   - security_audit

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
   - user_account：6 条（含静态权限样例用户）
   - rbac_role：5 条
   - rbac_permission：12 条
   - rbac_user_role：6 条
   - rbac_role_permission：24 条
   - user_data_scope：7 条
   - topic_scope_rule：6 条
- 已保留 B-02 静态权限样例：
   - user_account: U-OIDC-001 / oidc_static_sample
   - rbac_user_role: U-OIDC-001 -> REGIONAL_DISPATCHER
   - user_data_scope: U-OIDC-001 -> REGION-HZ
- 说明：该样例仅用于权限演示，不代表真实 OIDC 同步结果。
- 已新增 B-03 token / audit 联调样例：
   - 明文 refresh token: sample-refresh-active-001 -> 状态 ACTIVE
   - 明文 refresh token: sample-refresh-rotated-old-001 -> 状态 ROTATED
   - 明文 refresh token: sample-refresh-rotated-new-001 -> 状态 ACTIVE（同 session 新 token）
   - 明文 refresh token: sample-refresh-revoked-001 -> 状态 REVOKED
   - 明文 refresh token: sample-refresh-replay-blocked-001 -> 状态 REPLAY_BLOCKED
   - security_audit: 3 条（刷新成功 / revoked reuse / replay reuse）
- 可直接用于 B-01 分页联调：
   - /api/object-dictionary/page?page=1&pageSize=3
   - /api/object-dictionary/page?page=2&pageSize=3

## 快速验证命令
- 查询对象字典：
   - curl -s http://localhost:8080/api/object-dictionary
- 按 segment_id 查询对象链：
   - curl -s http://localhost:8080/api/object-chain/segment/SEG-001
- 按 node_id 查询对象链：
   - curl -s http://localhost:8080/api/object-chain/node/NODE-002
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
   - curl -s http://localhost:8080/api/authz/users/U-DISPATCH-001/snapshot
- 验证“区域调度员跨区拒绝”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Content-Type: application/json" -d "{\"userId\":\"U-DISPATCH-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"regionId\":\"REGION-SH\",\"topic\":\"region.REGION-SH.alerts.critical\",\"dataView\":\"AGGREGATED\"}"
- 验证“巡检仅本人任务”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Content-Type: application/json" -d "{\"userId\":\"U-INSPECT-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"assignee\":\"zhangsan\",\"topic\":\"user.U-INSPECT-001.workorder.created\",\"dataView\":\"AGGREGATED\"}"
- 验证“算法工程师默认仅脱敏视图”：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Content-Type: application/json" -d "{\"userId\":\"U-ALGO-001\",\"entryPermission\":\"ENTRY:DIAG\",\"menuPermission\":\"MENU:MODEL:READ\",\"dataView\":\"MASKED_FEATURE\",\"topic\":\"diag.model.inference\"}"
- 分页接口联调：
   - curl -s "http://localhost:8080/api/object-dictionary/page?page=1&pageSize=3"
- OpenAPI 文档检查：
   - curl -s http://localhost:8080/v3/api-docs
- 获取正式 OIDC 登录描述：
   - curl -s "http://localhost:8080/api/auth/login?redirectUri=http://localhost:5173/auth/callback"
- 获取 OIDC 登录入口：
   - curl -s "http://localhost:8080/api/auth/login-url?redirectUri=http://localhost:5173/auth/callback"
- 使用 code/state 换取本地 token：
   - curl -s -X POST http://localhost:8080/api/auth/callback -H "Content-Type: application/json" -d "{\"code\":\"<oidc_code>\",\"state\":\"<oidc_state>\",\"redirectUri\":\"http://localhost:5173/auth/callback\"}"
- 使用 refresh token 轮换：
   - curl -s -X POST http://localhost:8080/api/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"sample-refresh-active-001\"}"
- 验证旧 refresh token 重放被拒绝：
   - curl -s -X POST http://localhost:8080/api/auth/refresh -H "Content-Type: application/json" -d "{\"refreshToken\":\"sample-refresh-rotated-old-001\"}"
- OIDC 关闭时查看登录状态：
   - curl -s http://localhost:8080/api/auth/me
- OIDC 开启且已登录后获取当前用户：
   - curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer <access_token>"
- OIDC 开启且已登录后执行统一登出：
   - curl -s -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer <access_token>"

## 下一步建议
- 基于 B-02 继续落地 B-03（Refresh Token 刷新轮换）与 B-04（Token 吊销收敛）。
- 接入 Flyway，落地版本化迁移脚本。
- 在网关层增加限流、审计、风险接口单独策略。
