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
   - incident：2 条
   - work_order：2 条
   - model_result：2 条
- 已生成 A-04 权限联调数据：
   - user_account：5 条（五类角色示例用户）
   - rbac_role：5 条
   - rbac_permission：12 条
   - rbac_user_role：5 条
   - rbac_role_permission：24 条
   - user_data_scope：6 条
   - topic_scope_rule：6 条

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

## 下一步建议
- 接入 Spring Security OIDC，落地 B-02 到 B-05。
- 接入 Flyway，落地版本化迁移脚本。
- 在网关层增加限流、审计、风险接口单独策略。
