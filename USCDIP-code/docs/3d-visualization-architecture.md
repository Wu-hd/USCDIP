# 3D 可视化组件优化架构说明

## 目标与边界

本次改造以 PDF 功能清单为最终验收依据，以 `knight-L/sc-datav` 的组件组织方式为参考，但保留现有 Vue 3、Three.js、Spring Boot 技术栈。参考仓库仅用于分析，交付代码全部位于 `USCDIP-code`。

当前 `/mgmt/gis/3d` 已具备 WebGL 检测、GIS bbox 查询、对象详情查询、POINT/LINESTRING 渲染、ResizeObserver 和 2D 降级。后续应将其从单文件“占位视图”升级为正式的 3D GIS 工作台。

## 参考仓库映射

| sc-datav 模式 | 当前项目对应能力 | 建议目标模块 |
|---|---|---|
| `pages/Demo*/map/index.tsx` 场景入口 | `Gis3DPlaceholderView.vue` 内部初始化 | `components/gis3d/Gis3DScene.vue` |
| `map/base.tsx` 地理投影与基础几何 | WKT 解析和本地坐标缩放 | `gis3d/geometry.ts` 与 `GisObjectLayer.ts` |
| `map/label.tsx`、`tooltip.tsx` | 当前无 3D 标签和悬浮信息 | `Gis3DOverlay.vue` |
| `map/flyLine.tsx`、`boundary.tsx` | 当前只有静态线和网格 | `GisFlowLayer.ts`、`GisBoundaryLayer.ts` |
| `OrbitControls` | 当前场景自动旋转、不可直接操作 | Three.js `OrbitControls` 封装 |
| `panel/` 独立数据面板 | 当前侧栏主要说明占位状态 | `Gis3DInspector.vue`、`Gis3DLayerPanel.vue` |
| `stores/` 场景状态 | 当前状态集中在页面局部变量 | `stores/gis3d.ts` 或页面级 composable |
| 场景完成后触发面板动画 | 当前只有 loading/fallback | 明确的 `booting/loading/ready/error/fallback` 生命周期 |

## 目标前端结构

```text
frontend/src/
├── components/gis3d/
│   ├── Gis3DScene.vue          # renderer、camera、controls、生命周期
│   ├── Gis3DToolbar.vue        # 复位、视角、缩放、全屏等命令
│   ├── Gis3DLayerPanel.vue     # 图层显隐、透明度、动画开关
│   ├── Gis3DInspector.vue      # 选中对象与业务关联信息
│   └── Gis3DOverlay.vue        # tooltip、状态、图例和屏幕坐标标签
├── composables/
│   └── useGis3DScene.ts        # 页面与 Three.js 场景之间的命令/事件契约
├── gis3d/
│   ├── geometry.ts             # WKT/坐标归一化与包围盒
│   ├── materials.ts            # 可复用材质与颜色语义
│   ├── layers.ts               # 对象、网格、流向和告警图层
│   └── types.ts                # 纯前端场景类型
└── views/
    └── Gis3DView.vue           # 数据获取、路由上下文和工作台布局
```

实际文件数量以 PDF 需求为准；只有在模块确实拥有独立生命周期或复用价值时才拆分。

## 后端能力映射

当前 GIS API 已覆盖首版场景数据：

- `GET /api/gis/objects/bbox`：按范围和对象类型返回场景对象。
- `POST /api/gis/objects/pick`：按坐标和容差点选对象。
- `GET /api/gis/objects/{objectType}/{objectId}`：返回对象详情。
- `POST /api/gis/convert`：坐标系转换。

如果 PDF 需要流向、实时指标、告警聚合、场景配置或 LOD 数据，应新增清晰的 DTO 和聚合端点，不把业务拼装逻辑塞进 Vue 组件。

## 交互与渲染约束

- 鼠标：左键旋转或点选、滚轮缩放、右键平移；每种行为必须有可见反馈。
- 键盘：工具栏可聚焦，图标按钮具备 `aria-label` 和 tooltip。
- 性能：限制 DPR，上限 2；销毁 geometry/material/texture/control；隐藏标签页时降低或暂停动画。
- 可达性：遵循 `prefers-reduced-motion`，动画不能是理解状态的唯一方式。
- 响应式：画布尺寸稳定，控制条不遮挡对象，375/768/1024/1440 px 无横向溢出。
- 降级：WebGL 或数据不可用时保留 bbox、对象与坐标系上下文返回 2D。

## 待 PDF 确认

以下内容不能只依据标题决定，读取 PDF 后必须转成逐项验收清单：

- 需要新增的具体图层、动画、数据指标和业务联动。
- 是否需要中国地图、省市钻取、行政区边界或全国级 GeoJSON。
- 是否需要热力图、飞线、流向、告警扩散、设备模型或 GLTF 资产。
- 是否需要后端新增聚合接口、实时推送或场景配置持久化。
- 视觉稿、颜色语义、面板字段、交互顺序和性能指标。
