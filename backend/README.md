# USCDIP Backend Bootstrap

该目录提供一期后端 Java 启动骨架，当前已实现：
- A-01 平台边界配置读取与查询 API
- A-02 统一对象主键与对象链字典（数据库版）
- A-03 坐标与深度字段冻结（GIS 字段规范、坐标转换、深度校验）
- A-04 权限模型与数据范围矩阵（RBAC + 数据范围 + topic 订阅范围）
- A-05 实时链路指标口径表（trace_id/event_time/recv_time/is_backfill/last_ack_seq）
- A-06 事件状态机与工单状态机（告警/事件/工单三套状态枚举与流转规则）
- A-07 API 错误码与异常响应规范（统一错误码、全局异常处理、traceId 透传）

当前项目已添加数据库能力。

## 快速启动
1. 进入 backend 目录。
2. 首次或切换 JDK 后执行 mvn clean spring-boot:run。
3. 访问接口：
   - GET /api/menu-boundaries
   - GET /api/platforms
   - GET /api/platforms/{platformCode}
   - GET /api/object-dictionary
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
   - GET /api/realtime/field-spec
   - GET /api/realtime/metrics?limit=20
   - GET /api/realtime/traces/{traceId}
   - GET /api/state-machines
   - GET /api/state-machines/{machineType}/allowed/{status}
   - GET /api/alarms/{alarmId}/allowed-actions
   - PUT /api/alarms/{alarmId}/actions
   - GET /api/incidents/{incidentId}/allowed-actions
   - PUT /api/incidents/{incidentId}/actions
   - GET /api/work-orders/{workOrderId}/allowed-actions
   - PUT /api/work-orders/{workOrderId}/actions
   - GET /api/error-codes

## 常见问题
- 报错 `UnsupportedClassVersionError`（如 class file version 65.0）：
   1. 确认 `java -version` 为 17。
   2. 执行 `mvn clean compile` 清理旧产物并重编译。
   3. 再执行 `mvn spring-boot:run`。

## 当前实现范围
- A-01 边界配置文件：src/main/resources/a01-menu-boundary.json
- 统一响应结构：success/data/error/traceId
- 平台查询接口：按平台编码读取边界定义
- A-02 对象链实体：node、segment、facility、device、incident、work_order、model_result
- A-02 对象链接口：按 segment_id 和 node_id 查询完整对象链
- A-03 字段冻结：authority_srid、display_srid、geometry_2d、z_top、z_bottom、bury_depth、elevation_ref
- A-03 坐标转换：统一后端服务化处理（禁止前端/导入工具私算）
- A-04 约束表达：access = entryPermission && menuPermission && dataScope && topicScope
- A-04 角色覆盖：平台管理员、区域调度员、巡检人员、算法工程师、领导只读
- A-04 测试约束：跨区订阅拒绝、巡检仅本人任务、算法默认脱敏视图
- A-05 字段口径：trace_id、event_time、recv_time、is_backfill、last_ack_seq
- A-05 链路阶段：device_sampling -> edge_recv -> cloud_ingest -> alarm_decide -> frontend_recv -> map_render
- A-05 查询接口：支持最近链路样例与按 trace_id 时间线回放
- A-06 三套状态机：alarm、incident、work_order
- A-06 关键动作：去重（dedupe）、抑制（suppress）、升级（escalate）、派单（dispatch）、误报回写（false_report_writeback）、关闭（close）
- A-06 流转校验：所有状态切换统一经后端状态机服务校验，非法流转返回 INVALID_STATE_TRANSITION
- A-07 统一错误结构：success/data/error/traceId
- A-07 统一错误码：AUTHENTICATION_FAILED、FORBIDDEN、DATA_SCOPE_EMPTY、IDEMPOTENT_CONFLICT、INVALID_PARAMETER、INVALID_REQUEST、RESOURCE_NOT_FOUND、INVALID_STATE_TRANSITION、INTERNAL_ERROR
- A-07 异常处理：全局异常处理器统一处理参数校验、业务异常、未捕获异常
- A-07 traceId：请求头 X-Trace-Id 支持透传，未提供时自动生成

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

## A-06 接口说明

### 1) 查看三套状态机定义
- GET /api/state-machines
- 用途：返回 alarm/incident/work_order 的状态枚举与流转规则。

### 2) 查询某状态的允许动作
- GET /api/state-machines/{machineType}/allowed/{status}
- 说明：machineType 支持 alarm、incident、work_order。

### 3) 告警状态流转
- PUT /api/alarms/{alarmId}/actions
- 请求体示例：
   - {"action":"escalate"}

### 4) 事件状态流转
- PUT /api/incidents/{incidentId}/actions
- 请求体示例：
   - {"action":"dispatch"}

### 5) 工单状态流转
- PUT /api/work-orders/{workOrderId}/actions
- 请求体示例：
   - {"action":"false_report_writeback","feedbackType":"FALSE_POSITIVE","feedbackReason":"现场复核无异常"}
- 说明：误报回写独立建模，不与 close 动作合并。

## A-07 接口说明

### 1) 错误码字典
- GET /api/error-codes
- 用途：提供前后端统一错误码与默认文案，便于前端按 code 做分支处理。

### 2) 统一错误响应示例
- 参数校验失败（HTTP 400）：
   - {"success":false,"data":null,"error":{"code":"INVALID_PARAMETER","message":"authority_srid is required"},"traceId":"..."}
- 权限不足（HTTP 403）：
   - {"success":false,"data":null,"error":{"code":"FORBIDDEN","message":"Authorization denied: ENTRY_PERMISSION_DENIED"},"traceId":"..."}
- 数据范围为空（HTTP 403）：
   - {"success":false,"data":null,"error":{"code":"DATA_SCOPE_EMPTY","message":"No accessible data in current scope"},"traceId":"..."}

### 3) traceId 约定
- 请求可选头：X-Trace-Id
- 若请求头缺失，后端会自动生成并回传在响应头与响应体 traceId 字段中。

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

### 初始化行为说明（必须了解）
- H2 默认模式（application.yml）：
   - spring.jpa.hibernate.ddl-auto=create-drop
   - spring.jpa.defer-datasource-initialization=true
   - spring.sql.init.mode=always
   - 含义：先由 JPA 建表，再执行 data.sql 初始化测试数据，应用停止后内存库销毁。
- PostgreSQL 模式（application-postgres.yml）：
   - spring.jpa.hibernate.ddl-auto=update
   - spring.sql.init.mode=never
   - 含义：默认不自动执行 data.sql，需要手动导入测试数据。

### PostgreSQL 手动导入测试数据
- 建议在首次切换 postgres profile 后执行：
   - psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f src/main/resources/data.sql
- 若使用 PowerShell，可改为：
   - psql -h $env:DB_HOST -p $env:DB_PORT -U $env:DB_USER -d $env:DB_NAME -f src/main/resources/data.sql

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

## 测试数据说明
- 文件：src/main/resources/data.sql
- 已生成可联调测试数据，覆盖七类对象：
   - node：3 条
   - segment：2 条
   - facility：3 条
   - device：3 条
   - alarm：3 条
   - incident：2 条
   - work_order：3 条
   - model_result：2 条
- 已生成 A-04 权限联调数据：
   - user_account：6 条（含 1 条无角色错误场景用户）
   - rbac_role：5 条
   - rbac_permission：12 条
   - rbac_user_role：5 条
   - rbac_role_permission：24 条
   - user_data_scope：6 条
   - topic_scope_rule：6 条
- 已生成 A-05 实时链路联调数据：
   - realtime_link_metric：12 条
   - 覆盖 3 条 trace_id（含 1 条 is_backfill=true 的补偿链路）
   - 覆盖字段：trace_id、event_time、recv_time、is_backfill、last_ack_seq
   - 覆盖阶段：device_sampling、edge_recv、cloud_ingest、alarm_decide、frontend_recv、map_render
- 已生成 A-07 错误场景联调数据：
   - user_account：新增 U-NOROLE-001（用于权限不足/角色未分配场景）
   - work_order：新增 WO-CONFLICT-001（用于幂等冲突场景模拟）

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
- 查看 A-05 字段口径：
   - curl -s http://localhost:8080/api/realtime/field-spec
- 查看最近实时链路样例：
   - curl -s "http://localhost:8080/api/realtime/metrics?limit=20"
- 按 trace_id 回放链路时间线：
   - curl -s http://localhost:8080/api/realtime/traces/tr-20260411-0001
- 查看 A-06 状态机定义：
   - curl -s http://localhost:8080/api/state-machines
- 查询事件当前状态可执行动作：
   - curl -s http://localhost:8080/api/incidents/INC-001/allowed-actions
- 执行事件派单动作：
   - curl -s -X PUT http://localhost:8080/api/incidents/INC-001/actions -H "Content-Type: application/json" -d "{\"action\":\"dispatch\"}"
- 执行工单误报回写动作：
   - curl -s -X PUT http://localhost:8080/api/work-orders/WO-001/actions -H "Content-Type: application/json" -d "{\"action\":\"false_report_writeback\",\"feedbackType\":\"FALSE_POSITIVE\",\"feedbackReason\":\"现场复核无异常\"}"
- 查看 A-07 错误码字典：
   - curl -s http://localhost:8080/api/error-codes
- 验证 A-07 参数校验错误：
   - curl -s -X POST http://localhost:8080/api/gis/convert -H "Content-Type: application/json" -d "{}"
- 验证 A-07 数据范围为空错误：
   - curl -s -X POST http://localhost:8080/api/authz/check -H "Content-Type: application/json" -d "{\"userId\":\"U-DISPATCH-001\",\"entryPermission\":\"ENTRY:EMGC\",\"menuPermission\":\"MENU:WORKORDER:READ\",\"regionId\":\"REGION-SH\",\"topic\":\"region.REGION-SH.alerts.critical\",\"dataView\":\"AGGREGATED\"}"

## 下一步建议
- 接入 Spring Security OIDC，落地 B-02 到 B-05。
- 接入 Flyway，落地版本化迁移脚本。
- 在网关层增加限流、审计、风险接口单独策略。
