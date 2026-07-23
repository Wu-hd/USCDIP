# 全国地下管网三维健康态势首页验收清单

来源：`docs/ChatGPT - WHD.pdf`，共 24 页。本文档将 PDF 要求转成可逐项验证的工程清单。

## 1. 页面定位

- [x] `/portal` 仅承担未登录用户的登录、OIDC、紧急登录、状态和错误提示。
- [x] `/home` 是登录后的默认首页，名称为“全国地下管网三维健康态势”。
- [x] 首页是全国态势、平台入口和业务摘要，不是“三维占位视图”。
- [x] 页面中不出现“3D 占位视图”“二期数字孪生插槽”“一期 3D 插槽”“占位验证”“真实三维孪生留到二期”。

## 2. 路由与认证

- [x] `/` 默认进入 `/home`，未登录时由守卫引导到 `/portal`。
- [x] `/home` 使用 `requiresAuth: true` 与 `authOnly: true`，不绑定 MGMT/EMGC/DIAG 权限。
- [x] 守卫先校验 token，再调用 `ensureCurrentUser()`，然后放行 `authOnly`，其余路由继续原平台权限校验。
- [x] RouteMeta 类型声明包含 `authOnly?: boolean`，不使用 `as any` 绕过。
- [x] 已登录用户访问 `/portal`，确认用户会话有效后自动进入 `/home`。
- [x] OIDC 成功默认进入 `/home`，提示语同步更新。
- [x] 紧急登录成功在确认用户加载后进入 `/home`。
- [x] 安全的站内 `redirect` 优先于 `/home`，拒绝 `//`、`/portal`、`/auth/callback` 等开放重定向风险。
- [x] OIDC 跳转前可用 `sessionStorage` 保存安全目标，callback 成功后读取并删除。
- [x] 退出调用现有注销逻辑、清理会话和 3D 资源，并进入 `/portal`。
- [x] 不出现无限重定向、失效 token 错误进入首页或 callback 重复导航。

## 3. 旧地址与二维 GIS

- [x] `/mgmt/gis/3d` 重定向到 `/home` 并完整保留查询参数。
- [x] `/home` 读取 `objectType/objectId/bbox/displaySrid` 等已有上下文。
- [x] `/mgmt/gis` 保持为具体资产查询二维 GIS 页面。
- [x] 二维页面原“进入 3D”按钮进入 `/home` 并携带上下文。
- [x] 首页提供“进入二维一张图”入口，仍受 MGMT 权限约束。

## 4. 首页结构与内容

- [x] 采用全屏数据驾驶舱布局，延续现有深色科技风和玻璃面板。
- [x] 核心视觉区域是中国省级三维地图。
- [x] 标题为“地下管网数字化健康监测系统 / 全国地下管网三维健康态势”。
- [x] 副标题为“多源感知 · 风险预警 · 事件研判 · 工单处置 · 审计复盘”。
- [x] 顶部包含当前时间、当前用户、角色、全屏、刷新、退出和平台入口。
- [x] 当前时间每秒更新且卸载时清理，不在渲染帧中更新。
- [x] 至少展示健康指数、覆盖省级数、管线里程、在线设备、活动告警、待处置事件、进行中工单。
- [x] 展示告警与工单/事件摘要，并有明确加载、空和错误状态。
- [x] 支持 375/768/1024/1440 px，不出现横向溢出或控件遮挡。

## 5. 平台与业务入口

- [x] 展示综合管理、应急指挥、智能诊断三个主入口。
- [x] 继续复用现有平台权限方法，不绕过权限体系。
- [x] 无权限入口显示锁定状态与所需权限，并禁止点击。
- [x] 提供二维一张图、设备台账、实时告警、时序趋势、应急工单、模型治理快捷入口。
- [x] 快捷入口跳转前继续执行对应平台权限判断。

## 6. 数据层

- [x] 优先复用当前用户、平台、菜单、通知、工单、告警、事件、GIS 对象和设备台账接口。
- [x] 不创建重复 API，不虚构后端 URL，不修改无关后端代码。
- [x] 暂无接口的全国聚合数据使用集中、稳定 Mock，渲染过程不调用 `Math.random()`。
- [x] 定义 `NationalOverviewDataAdapter`，当前使用 `MockNationalOverviewDataAdapter`，为后续 API 适配器保留边界。
- [x] 数据类型至少包含 PDF 指定的 `NationalSummary` 与 `ProvinceHealthData` 字段。

## 7. 中国省级三维地图

- [x] GeoJSON 位于 `frontend/src/assets/geo/china.json`，是有效 FeatureCollection。
- [x] 正确处理 Polygon、MultiPolygon、省级行政区、岛屿和边界。
- [x] 使用统一经纬度投影，以中国中心 104.1954E、35.8617N 为参考。
- [x] 飞线、城市节点、省份中心和 GIS 对象共用同一投影函数。
- [x] 省级面有三维挤出，顶部和侧面材质可区分。
- [x] 有省界、全国轮廓光、动态扫光、城市节点、脉冲光圈和全国飞线。
- [x] 支持 Hover 高亮、Tooltip、点击选中与保持高亮。
- [x] 点击省份后右侧显示详情，相机平滑聚焦。
- [x] 省份详情包含健康评分、里程、在线设备、告警、事件和风险等级。
- [x] “进入省级一张图”在无真实页面时禁用并说明待接入，不创建虚假路由。
- [x] 支持 OrbitControls、相机自适应、自动旋转开关和恢复全国视角。
- [x] Resize 后画布与相机正确更新，不创建重复 Canvas。

## 8. 加载、错误与降级

- [x] 分别处理认证、用户、全国统计、GeoJSON、Three.js、业务 API 的加载状态。
- [x] 处理 WebGL 不可用和 context lost，不产生未处理 Promise rejection。
- [x] GeoJSON 失败时保留头部和业务入口，地图区显示错误、重试和二维入口。
- [x] WebGL 不可用时统计和导航仍可使用，不把用户踢回 Portal。
- [x] 删除旧页面“3 秒自动回到二维地图”的倒计时。

## 9. 生命周期与资源释放

- [x] 页面卸载、退出和路由切换时取消 RAF、定时器和 ResizeObserver。
- [x] 清理 OrbitControls、DOM 事件和 WebGL context lost 监听。
- [x] 递归释放 Geometry、Material、Texture、Renderer 并移除 Canvas。
- [x] 提供统一 `disposeThreeObject(root)` 工具，避免重复创建场景和明显内存泄漏。

## 10. 工程验证

- [x] `npm run typecheck` 通过且 TypeScript 无新增错误。
- [x] `npm run build` 通过。
- [x] 浏览器 Console 无明显错误，画布非空且有有效像素。
- [x] 实测 `/portal`、`/home`、`/mgmt/gis/3d`、`/mgmt/gis`。
- [x] 实测未登录首页、紧急登录、已登录 Portal、旧地址、退出、刷新保持、权限用户差异和窗口缩放。
- [x] 代码路径验证 OIDC callback；若外部 OIDC 不可用，明确记录未实测部分。
- [x] 页面切换后动画停止、Canvas 移除、WebGL 资源释放。

## 验证证据

- 前端：`npm run typecheck` 与 `npm run build` 均通过。
- 后端：`mvn test` 共 130 项，通过 130 项，失败/错误/跳过均为 0。
- 浏览器：Playwright 实测未登录、管理员与 BREAK_GLASS_COMMAND 登录、权限锁定、站内/恶意 redirect、旧 3D 地址、2D GIS、退出和 WebGL context lost 重试。
- 响应式：375/768/1024/1440 px 均无横向溢出且始终只有一个 Three.js Canvas。
- 像素：桌面画布 835x784、抽样 757 色；移动画布 360x471、抽样 520 色。
- 资源：离开 3D 首页后 Three.js Canvas 为 0；2D GIS 中仅保留 Leaflet Canvas。
- 截图：`output/playwright/home-desktop.png`、`home-mobile.png`、`home-canvas.png`、`home-mobile-canvas.png`、`home-gis-context-canvas.png`。
- 外部 OIDC Provider 未在本机运行，因此未执行真实供应商往返；callback 的 state/redirect/重复回调/清理路径已完成代码、类型和构建验证。
