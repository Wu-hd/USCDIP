# F-03 Token 刷新与登出前端逻辑实施计划

## Summary

- 在现有 `frontend/` 上实现静默刷新、401 自动刷新重试、并发刷新锁、刷新失败强制登出、统一登出反馈。
- 沿用 F-01/F-02 的深色 SaaS dashboard 风格：暗底、玻璃面板、蓝色主状态、琥珀预警、红色失效态、Lucide 图标。
- 与后端现有接口联调，不新增后端 API：`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /api/auth/me`。
- 完成后提交并推送到 `origin/feature/F-01-F-03-frontend-portal-auth`。

## Key Changes

- Token 管理：在 `sessionStorage` token pair 基础上补齐 metadata 读写，包含 access/refresh token、过期时间和 tokenType。
- 静默刷新：access token 距离过期小于 2 分钟时调用 `POST /api/auth/refresh`，成功后原子替换整组 token pair。
- 并发刷新锁：多个请求同时触发刷新时复用同一个 refresh promise，refresh 成功后原请求最多重放一次。
- 强制登出：refresh token 缺失、过期、撤销、重放或无效时清空 token，并在 Portal 登录态区域展示原因和 traceId。
- 统一登出：登出按钮调用 `POST /api/auth/logout`；即使后端返回 401/503，前端也清理本地 token pair 并回到未登录态。
- UI 状态：Portal 登录态面板展示 access token 剩余时间、刷新状态、最近一次 auth notice 或强退原因，不展示 token 明文。

## Public APIs / Interfaces

- 新增/扩展前端类型：
  - `TokenRefreshRequest { refreshToken: string }`
  - `ApiRequestOptions extends RequestInit { skipAuthRefresh?: boolean; retryOnUnauthorized?: boolean }`
  - `AuthRefreshState = 'idle' | 'refreshing' | 'refreshed' | 'failed'`
- 后端接口沿用：
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
- 认证类接口 `login-url / callback / refresh / logout` 跳过递归刷新，避免刷新流程自触发。

## Test Plan

- 静态检查：`cd frontend && npm run typecheck`、`cd frontend && npm run build`。
- access token 有效：`/portal` 正常读取 `/api/auth/me` 和待办。
- access token 即将过期：请求前静默刷新，Portal 显示刷新成功，后续请求使用新 access token。
- 多个并发请求同时触发刷新：只发出一个 `/api/auth/refresh`，所有请求等待同一 refresh 结果后重试。
- access token 失效但 refresh token 有效：首次业务请求 401 后刷新并重试一次。
- refresh token 过期、撤销、重放或缺失：清空 token，展示强制登出原因，待办区回到“需登录并具备权限后查看”。
- 点击单点退出：调用 `/api/auth/logout`，清空 token pair，按钮 loading 不造成布局跳动。

## Assumptions

- F-03 继续使用 F-02 确认的 `sessionStorage` token 存储。
- 静默刷新提前量采用 2 分钟；后端 access token 默认 30 分钟，refresh token 默认 14 天。
- F-03 不实现按钮级权限、路由权限守卫和 WebSocket token 刷新；这些留给 F-04/F-11。
- `backend/src/main/resources/data.sql` 是既有无关脏改动，不纳入本次提交。
