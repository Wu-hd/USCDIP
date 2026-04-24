# F-11 实时告警列表 + WebSocket 客户端

## 1. 任务目标
实现基于 STOMP 协议的 WebSocket 客户端，与后端的 `/ws/push` 建立连接，并设计高级 SaaS 数据看板风格的实时告警列表页面。

## 2. 详细设计与实现

### 2.1 WebSocket 客户端封装 (`frontend/src/services/websocket.ts`)
- **核心组件**: 使用 `@stomp/stompjs` 封装 STOMP 客户端。
- **重连与心跳**: 配置自动重连机制，设置 `heartbeatIncoming` 和 `heartbeatOutgoing`。
- **认证握手**: 连接时在 HTTP Header 或 URL 参数中传递 Token，保证握手鉴权。
- **消息可靠性**: 
  - 根据 `event_id` 引入本地暂存与消息去重机制。
  - 支持向服务器回传 `last_ack_seq`，确认消息已被消费，避免断线补发的重复数据。

### 2.2 实时告警状态管理 (`frontend/src/stores/alerts.ts`)
- **全局存储**: 提供 Pinia Store 管理告警列表状态。
- **初始化拉取**: 启动时通过 `GET /api/alerts` 获取历史未恢复的基础告警。
- **实时更新**: WebSocket 接收新消息后动态置顶，并在已恢复时状态联动，配合 Vue 的 `<TransitionGroup>` 提供平滑动画效果。

### 2.3 SaaS 风格 UI (`frontend/src/views/RealtimeAlertsView.vue`)
- **视觉风格**: 参考顶级 SaaS Dashboard，深灰渐变卡片、微阴影、圆角。
- **Live Sync 强化**: 提供跳动的 "Live Sync" 状态呼吸灯，传达实时连接状态。
- **告警卡片**: 每个告警条目按等级以不同徽章色展现，并显示事件来源、`dq_score` 降级补偿等详情。

## 3. 测试与验证
1. 本地启动服务并开启测试界面，观察控制台 STOMP 会话握手反馈。
2. 触发后端发往 WebSocket 的新告警，界面自动动画置顶插入不闪烁。
3. 模拟断网操作（关闭网络），验证断网后的错误恢复机制与补发动作是否被精准去重。