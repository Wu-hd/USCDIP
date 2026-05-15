# F-01 Portal 基础壳与三平台入口页实施计划

## Summary

- 新建 `frontend/`：Vue 3 + Vite + TypeScript + Vue Router + Pinia + Tailwind CSS + `lucide-vue-next`。
- 实现 `/portal` 门户首页：顶部登录态、三平台入口、统一待办汇总、退出按钮；Portal 只做导航与聚合，不做任何业务编辑。
- 视觉参考 `https://ui-ux-pro-max-skill.nextlevelbuilder.io/demo/saas-analytics-dashboard`：深色 SaaS dashboard、玻璃质感面板、蓝色数据主色、琥珀色行动强调、紧凑运维风布局。
- 本计划写入 `docx/F-01_Portal基础壳与三平台入口页_实施计划.md`，并同步更新 `backend/README.md` 的前端联调说明。

## Key Changes

- 前端工程：建立 Vite 项目，路由包含 `/portal`，默认 `/` 重定向到 `/portal`；配置 `VITE_API_BASE_URL=http://localhost:8080`；统一封装 `ApiResponse<T> = { success, data, error, traceId, timestamp }`。
- Auth store：读取本地 access token、调用 `/api/auth/me`、调用 `/api/auth/logout`；401/503 分别展示未登录、OIDC disabled 等明确状态。
- Portal 页面：顶部栏显示系统名、当前用户或未登录状态、traceId、刷新和单点退出；三平台入口只展示 `MGMT / EMGC / DIAG`，从 `/api/platforms` 获取并过滤掉 `PORTAL / SUPPORT`。
- 待办汇总：优先读取 `/api/workorders?page=1&pageSize=5` 与 `/api/notifications?page=1&pageSize=5`；未登录或无权限时降级显示“需登录并具备权限后查看”，不伪造业务数据。
- 平台占位页：`/mgmt`、`/emgc`、`/diag` 只显示平台边界、职责和后续建设提示，避免 Portal 或占位页承载业务编辑。

## Public APIs / Interfaces

- `GET /api/platforms`：读取平台边界并生成三入口。
- `GET /api/menu-boundaries`：校验 Portal `allowWrite=false` 与支撑层不作为第四入口。
- `GET /api/auth/login-url?redirectUri=<frontend callback>`：登录按钮获取 OIDC 入口；`enabled=false` 时显示本地/OIDC 关闭状态。
- `GET /api/auth/me`：展示登录用户与权限快照。
- `POST /api/auth/logout`：统一退出并清理本地 token。
- `GET /api/workorders`、`GET /api/notifications`：用于统一待办摘要。
- 前端类型包含 `ApiResponse<T>`、`PlatformBoundary`、`AuthMePayload`、`PageResponse<T>`、`WorkOrderResponse`、`NotificationResponse`。

## Test Plan

- 静态检查：`npm run typecheck`、`npm run build`。
- 后端联调：后端启动后验证 `/api/platforms` 能渲染三平台，且不展示 `SUPPORT` 第四入口。
- 无 token 访问 `/portal`：平台入口正常，登录态显示未登录或 OIDC disabled，待办显示需登录。
- 有效 token 访问：`/api/auth/me` 展示用户，待办汇总读取工单/通知分页 totals 与前 5 条。
- 点击退出：调用 `/api/auth/logout`，清理 token，回到未登录态。
- UI 验收：375px、768px、1024px、1440px 检查无文本溢出、无横向滚动、focus 可见；平台卡片 hover 不改变布局尺寸。

## Assumptions

- 前端栈按 Vue 3 + Vite 执行。
- README 更新目标为 `backend/README.md`。
- F-02 的 OIDC 回调页与完整 token refresh 并发锁不在本次 F-01 内实现；本次只预留登录入口、读取已有 token、退出与登录态展示。
