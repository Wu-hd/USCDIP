# F-02 OIDC 登录回调页实施计划

## Summary

- 在现有 `frontend/` 增加 `/auth/callback` 路由、OIDC 回调页面与回调 composable，完成 `code/state` 交换、取消登录、失败恢复与重复进入处理。
- 继续沿用 F-01 的深色 SaaS dashboard 风格：暗底、玻璃面板、蓝色主状态、琥珀/红色错误态、Lucide 图标。
- 与后端现有接口联调，不新增后端 API：`POST /api/auth/callback`、`GET /api/auth/login-url`、`GET /api/auth/me`。
- F-02 成功后固定跳转 `/portal`；自动刷新与并发刷新锁留给 F-03。

## Key Changes

- 新增 `/auth/callback` 懒加载路由，页面读取 `code`、`state`、`error`、`error_description`。
- 有 `error` 且无 `code` 时视为取消或 Provider 拒绝登录，不调用后端 callback。
- 缺少 `code` 或 `state` 时展示无效回调状态，提供“重新登录”和“返回 Portal”。
- 发起登录时保存 `state / redirectUri / stateExpiresAt` 到 `sessionStorage`，回调页先做轻量前端 state 校验，最终仍以后端校验为准。
- 调用 `POST /api/auth/callback`，请求体为 `{ code, state, redirectUri }`；成功后保存 token pair，短暂成功态后跳转 `/portal`。
- 对已成功处理过的 state 设置 session 级标记，避免浏览器后退时重复交换。

## Public APIs / Interfaces

- 新增前端类型：`AuthCallbackRequest`、`TokenPairResponse`、`PendingOidcState`。
- 新增前端服务：`exchangeOidcCallback`、`savePendingOidcState`、`getPendingOidcState`、`markOidcStateProcessed`。
- Token 存储改为 `sessionStorage`：`accessToken`、`refreshToken`、过期时间和 `tokenType`；退出时清理 token pair，并清理 F-01 遗留的 localStorage access token。
- 后端接口沿用：
  - `GET /api/auth/login-url?redirectUri=http://localhost:5173/auth/callback`
  - `POST /api/auth/callback`
  - `GET /api/auth/me`

## Test Plan

- 静态检查：`npm run typecheck`、`npm run build`。
- `/auth/callback` 无 query：展示无效回调，不调用后端。
- `/auth/callback?error=access_denied&error_description=...`：展示取消登录，可返回 Portal 或重新登录。
- `state` 与 session pending state 不一致：展示 state 校验失败。
- 后端返回 `OIDC_STATE_INVALID`：展示 state 失败，并显示 traceId。
- 后端返回 `OIDC_DISABLED`：展示 OIDC 未启用，提供返回 Portal。
- callback 成功：sessionStorage 写入 token pair，短暂成功态后跳转 `/portal`，Portal 可读取 `/api/auth/me`。
- 成功后浏览器后退再次进入同一 callback：不重复交换，展示“登录已完成”并引导返回 `/portal`。

## Assumptions

- F-02 成功后固定跳转 `/portal`。
- Token 使用 `sessionStorage`，不再沿用 F-01 的 localStorage。
- F-03 的自动刷新、并发刷新锁、刷新失败强制登出不在本次实现范围内。
