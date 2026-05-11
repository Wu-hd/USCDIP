<script setup lang="ts">
import 'leaflet/dist/leaflet.css';

import L, {
  type CircleMarker,
  type LatLngBoundsExpression,
  type LatLngExpression,
  type LayerGroup,
  type LeafletMouseEvent,
  type Map as LeafletMap,
  type Polyline
} from 'leaflet';
import {
  AlertTriangle,
  ArrowLeft,
  Boxes,
  ChevronDown,
  ChevronUp,
  CircuitBoard,
  Crosshair,
  DatabaseZap,
  Eye,
  EyeOff,
  GitFork,
  Gauge,
  Layers3,
  Loader2,
  MapPinned,
  MousePointer2,
  PanelRightClose,
  RadioTower,
  RefreshCcw,
  RotateCcw,
  Route,
  Search,
  SlidersHorizontal,
  X
} from 'lucide-vue-next';
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getAlerts } from '@/services/alerts';
import { getDeviceLedgerDevice, getDeviceLedgerDevices } from '@/services/deviceLedger';
import { getGisObjectDetail, pickGisObject, queryGisBbox } from '@/services/gis';
import { getIncidents } from '@/services/incidents';
import { getMasterDataDetail, getMasterDataPage } from '@/services/masterData';
import { getWorkOrders } from '@/services/workorders';
import type {
  AlertRecordResponse,
  AssetDetailContext,
  AssetDetailTab,
  AssetDetailTabState,
  AssetSearchIndexState,
  AssetSearchResult,
  AssetSearchType,
  DeviceLedgerResponse,
  GisBboxQuery,
  GisObjectPickResponse,
  GisObjectRecordResponse,
  GisObjectType,
  IncidentResponse,
  MapLayerConfig,
  MapLayerKey,
  MapLayerState,
  MasterDataRecordResponse,
  WorkOrderResponse
} from '@/types/api';

type GisLayerKey = MapLayerKey;
type FlowSegmentKey = 'INLET' | 'BRANCH_A' | 'BRANCH_B';
type FlowSensorMode = 'SIMULATED' | 'SENSOR_READY';
type ParsedGeometry =
  | { kind: 'POINT'; point: LatLngExpression }
  | { kind: 'LINESTRING'; points: LatLngExpression[] };
type PanelState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';
type RenderedObjectLayer = {
  layerKey: GisObjectType;
  layer: CircleMarker | Polyline;
  object: GisObjectRecordResponse;
};
type RenderedAlertLayer = {
  layer: CircleMarker;
  alert: AlertRecordResponse;
  object: GisObjectRecordResponse;
};
type FlowSegmentConfig = {
  key: FlowSegmentKey;
  label: string;
  path: LatLngExpression[];
  color: string;
};
type FlowReading = {
  segmentKey: FlowSegmentKey;
  flow: number;
  unit: string;
  ratio: number;
};
type FlowSnapshot = {
  mode: FlowSensorMode;
  totalFlow: number;
  updatedAt: string;
  readings: Record<FlowSegmentKey, FlowReading>;
};

const LAYER_STATE_STORAGE_KEY = 'uscdip.gis.layerState.v1';
const ASSET_SEARCH_PAGE_SIZE = 100;
const ASSET_SEARCH_MAX_PAGES = 10;
const ASSET_SEARCH_MIN_CHARS = 2;
const ASSET_SEARCH_DEBOUNCE_MS = 250;
const FLOW_TOTAL_M3H = 120;
const FLOW_RANDOM_MIN_RATIO = 0.28;
const FLOW_RANDOM_MAX_RATIO = 0.72;
const FLOW_UPDATE_MS = 2500;
const FLOW_ANIMATION_MS = 3600;

const INITIAL_QUERY: GisBboxQuery = {
  minX: 120.15,
  minY: 30.27,
  maxX: 120.18,
  maxY: 30.3,
  authoritySrid: 'EPSG:4490',
  displaySrid: 'EPSG:4490',
  page: 1,
  pageSize: 50
};

const route = useRoute();

const layerConfigs: Array<MapLayerConfig & { icon: typeof CircuitBoard }> = [
  {
    key: 'SEGMENT',
    label: '管线',
    shortLabel: 'SEGMENT',
    description: '地下管段与拓扑连线',
    color: '#F59E0B',
    icon: Route,
    legendItems: [
      { label: '管线', color: '#F59E0B', shape: 'line', description: 'SEGMENT LINESTRING' },
      { label: '选中', color: '#FBBF24', shape: 'line', description: '当前对象高亮' }
    ]
  },
  {
    key: 'DEVICE',
    label: '设备',
    shortLabel: 'DEVICE',
    description: '监测终端、网关与现场设备',
    color: '#A78BFA',
    icon: RadioTower,
    legendItems: [
      { label: '设备点', color: '#A78BFA', shape: 'circle', description: 'DEVICE POINT' }
    ]
  },
  {
    key: 'NODE',
    label: '节点',
    shortLabel: 'NODE',
    description: '检查井、阀门井与空间节点',
    color: '#38BDF8',
    icon: CircuitBoard,
    legendItems: [
      { label: '节点点位', color: '#38BDF8', shape: 'circle', description: 'NODE POINT' }
    ]
  },
  {
    key: 'FACILITY',
    label: '设施',
    shortLabel: 'FACILITY',
    description: '泵站、闸门等设施对象',
    color: '#22C55E',
    icon: Boxes,
    legendItems: [
      { label: '设施点位', color: '#22C55E', shape: 'circle', description: 'FACILITY POINT' }
    ]
  },
  {
    key: 'ALERT',
    label: '告警',
    shortLabel: 'ALERT',
    description: '当前 bbox 可定位告警叠加层',
    color: '#FB7185',
    icon: AlertTriangle,
    legendItems: [
      { label: 'LOW', color: '#38BDF8', shape: 'ring', description: '低级告警' },
      { label: 'MEDIUM', color: '#F59E0B', shape: 'ring', description: '中级告警' },
      { label: 'HIGH', color: '#F97316', shape: 'ring', description: '高级告警' },
      { label: 'CRITICAL', color: '#F43F5E', shape: 'ring', description: '严重告警' }
    ]
  }
];

const assetSearchTypes: Array<{ key: AssetSearchType; label: string; shortLabel: string }> = [
  { key: 'ALL', label: '全部', shortLabel: 'ALL' },
  { key: 'NODE', label: '节点', shortLabel: 'NODE' },
  { key: 'SEGMENT', label: '管线', shortLabel: 'SEGMENT' },
  { key: 'FACILITY', label: '设施', shortLabel: 'FACILITY' },
  { key: 'DEVICE', label: '设备', shortLabel: 'DEVICE' }
];

const detailTabConfigs: Array<{ key: AssetDetailTab; label: string; icon: typeof Search }> = [
  { key: 'profile', label: '基础档案', icon: DatabaseZap },
  { key: 'devices', label: '关联设备', icon: RadioTower },
  { key: 'alerts', label: '历史告警', icon: AlertTriangle },
  { key: 'incidents', label: '事件', icon: CircuitBoard },
  { key: 'workorders', label: '工单', icon: Route }
];

const defaultLayerState: Record<GisLayerKey, MapLayerState> = {
  SEGMENT: { visible: true, opacity: 80, legendCollapsed: false },
  DEVICE: { visible: true, opacity: 80, legendCollapsed: false },
  NODE: { visible: true, opacity: 80, legendCollapsed: false },
  FACILITY: { visible: true, opacity: 80, legendCollapsed: false },
  ALERT: { visible: true, opacity: 80, legendCollapsed: false }
};

const mapElement = ref<HTMLDivElement | null>(null);
const map = shallowRef<LeafletMap | null>(null);
const layerGroups = new Map<GisLayerKey, LayerGroup>();
const objectLayers = new Map<string, RenderedObjectLayer>();
const alertLayers = new Map<string, RenderedAlertLayer>();
const assetIndexPromises = new Map<GisObjectType, Promise<void>>();
const searchHighlightLayer = shallowRef<CircleMarker | Polyline | null>(null);
const flowLayerGroup = shallowRef<LayerGroup | null>(null);
const flowLineLayers = new Map<FlowSegmentKey, Polyline>();
const flowPulseLayers = new Map<FlowSegmentKey, CircleMarker>();

const objects = ref<GisObjectRecordResponse[]>([]);
const alerts = ref<AlertRecordResponse[]>([]);
const assetIndex = reactive<Record<GisObjectType, MasterDataRecordResponse[]>>({
  NODE: [],
  SEGMENT: [],
  FACILITY: [],
  DEVICE: []
});
const assetIndexState = reactive<Record<GisObjectType, AssetSearchIndexState>>({
  NODE: createAssetIndexState(),
  SEGMENT: createAssetIndexState(),
  FACILITY: createAssetIndexState(),
  DEVICE: createAssetIndexState()
});
const selectedObject = ref<GisObjectRecordResponse | null>(null);
const detailTraceId = ref('');
const bboxTraceId = ref('');
const pickTraceId = ref('');
const alertTraceId = ref('');
const searchTraceId = ref('');
const state = ref<PanelState>('idle');
const alertState = ref<PanelState>('idle');
const searchState = ref<PanelState>('idle');
const message = ref('等待加载 GIS 对象');
const pickMessage = ref('点击地图可执行对象点查');
const alertMessage = ref('等待同步告警图层');
const searchMessage = ref('输入至少 2 个字符，按编码、名称或类型检索资产');
const total = ref(0);
const alertTotal = ref(0);
const unlocatedAlertCount = ref(0);
const lastPick = ref<GisObjectPickResponse | null>(null);
const isDrawerOpen = ref(false);
const searchKeyword = ref('');
const selectedSearchType = ref<AssetSearchType>('ALL');
const searchResults = ref<AssetSearchResult[]>([]);
const locatingAssetKey = ref('');
const activeDetailTab = ref<AssetDetailTab>('profile');
const flowLayerVisible = ref(true);
const flowSnapshot = ref<FlowSnapshot>(createInitialFlowSnapshot());
const detailContext = reactive<AssetDetailContext>({
  objectKey: '',
  profile: createDetailTabState<MasterDataRecordResponse | null>(null),
  devices: createDetailTabState<DeviceLedgerResponse[]>([]),
  alerts: createDetailTabState<AlertRecordResponse[]>([]),
  incidents: createDetailTabState<IncidentResponse[]>([]),
  workorders: createDetailTabState<WorkOrderResponse[]>([])
});
let searchDebounceTimer: ReturnType<typeof window.setTimeout> | null = null;
let flowUpdateTimer: ReturnType<typeof window.setInterval> | null = null;
let flowAnimationFrame = 0;
const layerState = reactive(loadLayerState());
const activeGisQuery = ref<GisBboxQuery>({ ...INITIAL_QUERY });

const flowSegments: FlowSegmentConfig[] = [
  {
    key: 'INLET',
    label: '总管入口',
    path: [[30.2789, 120.1568], [30.2851, 120.1604]],
    color: '#38BDF8'
  },
  {
    key: 'BRANCH_A',
    label: '左支管',
    path: [[30.2851, 120.1604], [30.2913, 120.1555]],
    color: '#22C55E'
  },
  {
    key: 'BRANCH_B',
    label: '右支管',
    path: [[30.2851, 120.1604], [30.2914, 120.1649]],
    color: '#F59E0B'
  }
];

const normalizedSearchKeyword = computed(() => searchKeyword.value.trim().toUpperCase());
const drawableCount = computed(() => objects.value.filter((object) => parseWkt(object.geometry2d)).length);
const visibleLayerCount = computed(() =>
  layerConfigs.filter((layer) => layerState[layer.key].visible).length
);
const locatedAlertCount = computed(() => alertLayers.size);
const indexedAssetCount = computed(() =>
  (Object.keys(assetIndex) as GisObjectType[]).reduce((sum, type) => sum + assetIndex[type].length, 0)
);
const activeSearchTypes = computed(() =>
  selectedSearchType.value === 'ALL'
    ? (['NODE', 'SEGMENT', 'FACILITY', 'DEVICE'] as GisObjectType[])
    : [selectedSearchType.value]
);
const searchReachedLimit = computed(() =>
  activeSearchTypes.value.some((type) => assetIndexState[type].reachedLimit)
);
const activeDetailTabState = computed(() => detailContext[activeDetailTab.value]);
const selectedRelatedEntries = computed(() =>
  selectedObject.value ? Object.entries(selectedObject.value.relatedObjectIds ?? {}) : []
);
const selectedAttributeEntries = computed(() =>
  selectedObject.value ? Object.entries(selectedObject.value.attributes ?? {}) : []
);
const selectedProfileAttributeEntries = computed(() =>
  detailContext.profile.data ? Object.entries(detailContext.profile.data.attributes ?? {}) : []
);
const selectedProfileRelatedEntries = computed(() =>
  detailContext.profile.data ? Object.entries(detailContext.profile.data.relatedObjectIds ?? {}) : []
);
const gis3dQuery = computed(() => {
  const query: Record<string, string> = {
    minX: String(activeGisQuery.value.minX),
    minY: String(activeGisQuery.value.minY),
    maxX: String(activeGisQuery.value.maxX),
    maxY: String(activeGisQuery.value.maxY),
    authoritySrid: activeGisQuery.value.authoritySrid,
    displaySrid: activeGisQuery.value.displaySrid
  };
  if (selectedObject.value) {
    query.objectType = String(selectedObject.value.objectType);
    query.objectId = selectedObject.value.objectId;
  }
  return query;
});
const branchAFlow = computed(() => flowSnapshot.value.readings.BRANCH_A.flow);
const branchBFlow = computed(() => flowSnapshot.value.readings.BRANCH_B.flow);
const branchBalanceLabel = computed(() =>
  `${Math.round(flowSnapshot.value.readings.BRANCH_A.ratio * 100)}% / ${Math.round(flowSnapshot.value.readings.BRANCH_B.ratio * 100)}%`
);

function createAssetIndexState(): AssetSearchIndexState {
  return {
    status: 'idle',
    loadedPages: 0,
    total: 0,
    reachedLimit: false,
    traceId: '',
    message: ''
  };
}

function createDetailTabState<T>(data: T): AssetDetailTabState<T> {
  return {
    status: 'idle',
    data,
    traceId: '',
    message: ''
  };
}

function createInitialFlowSnapshot(): FlowSnapshot {
  return buildFlowSnapshot(FLOW_TOTAL_M3H, 0.52, 'SIMULATED');
}

function buildFlowSnapshot(totalFlow: number, branchARatio: number, mode: FlowSensorMode): FlowSnapshot {
  const normalizedRatio = Math.min(FLOW_RANDOM_MAX_RATIO, Math.max(FLOW_RANDOM_MIN_RATIO, branchARatio));
  const branchA = roundFlow(totalFlow * normalizedRatio);
  const branchB = roundFlow(totalFlow - branchA);
  return {
    mode,
    totalFlow,
    updatedAt: new Date().toISOString(),
    readings: {
      INLET: {
        segmentKey: 'INLET',
        flow: roundFlow(totalFlow),
        unit: 'm³/h',
        ratio: 1
      },
      BRANCH_A: {
        segmentKey: 'BRANCH_A',
        flow: branchA,
        unit: 'm³/h',
        ratio: branchA / totalFlow
      },
      BRANCH_B: {
        segmentKey: 'BRANCH_B',
        flow: branchB,
        unit: 'm³/h',
        ratio: branchB / totalFlow
      }
    }
  };
}

function roundFlow(value: number) {
  return Math.round(value * 10) / 10;
}

function getSimulatedFlowSnapshot(): FlowSnapshot {
  const ratio =
    FLOW_RANDOM_MIN_RATIO + Math.random() * (FLOW_RANDOM_MAX_RATIO - FLOW_RANDOM_MIN_RATIO);
  return buildFlowSnapshot(FLOW_TOTAL_M3H, ratio, 'SIMULATED');
}

// Later this can call a real sensor API or WebSocket cache and return the same FlowSnapshot shape.
async function getCurrentFlowSnapshot(): Promise<FlowSnapshot> {
  return getSimulatedFlowSnapshot();
}

function objectKey(object: GisObjectRecordResponse) {
  return `${object.objectType}:${object.objectId}`;
}

function assetResultKey(result: AssetSearchResult) {
  return `${result.objectType}:${result.objectId}`;
}

function getAssetTypeLabel(type: string) {
  return assetSearchTypes.find((item) => item.key === type)?.label ?? type;
}

function isKnownGisObjectType(value: string): value is GisObjectType {
  return ['NODE', 'SEGMENT', 'FACILITY', 'DEVICE'].includes(value);
}

function setSearchType(type: AssetSearchType) {
  selectedSearchType.value = type;
}

function clearSearch() {
  searchKeyword.value = '';
  searchResults.value = [];
  searchTraceId.value = '';
  searchState.value = 'idle';
  searchMessage.value = '输入至少 2 个字符，按编码、名称或类型检索资产';
}

function refreshSearchIndex() {
  activeSearchTypes.value.forEach((type) => {
    assetIndex[type] = [];
    Object.assign(assetIndexState[type], createAssetIndexState());
  });
  void runAssetSearch();
}

function matchAsset(record: MasterDataRecordResponse, keyword: string): AssetSearchResult['matchedBy'] | null {
  if (record.objectId?.toUpperCase().includes(keyword)) {
    return 'objectId';
  }
  if (record.objectName?.toUpperCase().includes(keyword)) {
    return 'objectName';
  }
  if (record.objectType?.toUpperCase().includes(keyword)) {
    return 'objectType';
  }
  return null;
}

function toAssetSearchResult(
  record: MasterDataRecordResponse,
  matchedBy: AssetSearchResult['matchedBy']
): AssetSearchResult | null {
  if (!isKnownGisObjectType(record.objectType)) {
    return null;
  }
  return {
    objectType: record.objectType,
    objectId: record.objectId,
    objectName: record.objectName,
    status: record.status,
    regionId: record.regionId,
    relatedObjectIds: record.relatedObjectIds ?? {},
    attributes: record.attributes ?? {},
    matchedBy
  };
}

async function ensureAssetIndex(types: GisObjectType[]) {
  await Promise.all(types.map((type) => loadAssetIndex(type)));
}

async function loadAssetIndex(type: GisObjectType) {
  if (assetIndexState[type].status === 'ready') {
    return;
  }
  const inflight = assetIndexPromises.get(type);
  if (inflight) {
    return inflight;
  }

  const promise = doLoadAssetIndex(type).finally(() => {
    assetIndexPromises.delete(type);
  });
  assetIndexPromises.set(type, promise);
  return promise;
}

async function doLoadAssetIndex(type: GisObjectType) {
  assetIndexState[type].status = 'loading';
  assetIndexState[type].message = `正在加载 ${getAssetTypeLabel(type)} 主数据索引`;
  const records: MasterDataRecordResponse[] = [];

  try {
    for (let pageNo = 1; pageNo <= ASSET_SEARCH_MAX_PAGES; pageNo += 1) {
      const response = await getMasterDataPage(type, pageNo, ASSET_SEARCH_PAGE_SIZE);
      const page = response.data;
      records.push(...(page?.items ?? []));
      assetIndexState[type].loadedPages = pageNo;
      assetIndexState[type].total = page?.total ?? records.length;
      assetIndexState[type].traceId = response.traceId;
      searchTraceId.value = response.traceId;
      if (!page?.hasNext) {
        break;
      }
      if (pageNo === ASSET_SEARCH_MAX_PAGES && page.hasNext) {
        assetIndexState[type].reachedLimit = true;
      }
    }

    assetIndex[type] = records;
    assetIndexState[type].status = 'ready';
    assetIndexState[type].message = `${getAssetTypeLabel(type)}索引已加载`;
  } catch (error) {
    assetIndexState[type].status = 'error';
    if (error instanceof ApiClientError) {
      assetIndexState[type].traceId = error.traceId ?? '';
      assetIndexState[type].message = error.message;
      searchTraceId.value = error.traceId ?? '';
      throw error;
    }
    assetIndexState[type].message = `${getAssetTypeLabel(type)}索引加载失败`;
    throw error;
  }
}

async function runAssetSearch() {
  const keyword = normalizedSearchKeyword.value;
  const typeSnapshot = selectedSearchType.value;

  if (keyword.length < ASSET_SEARCH_MIN_CHARS) {
    searchResults.value = [];
    searchState.value = 'idle';
    searchMessage.value = '输入至少 2 个字符，按编码、名称或类型检索资产';
    return;
  }

  searchState.value = 'loading';
  searchMessage.value = '正在同步主数据索引并匹配资产对象';

  try {
    const types = typeSnapshot === 'ALL'
      ? (['NODE', 'SEGMENT', 'FACILITY', 'DEVICE'] as GisObjectType[])
      : [typeSnapshot];
    await ensureAssetIndex(types);

    if (keyword !== normalizedSearchKeyword.value || typeSnapshot !== selectedSearchType.value) {
      return;
    }

    const results = types
      .flatMap((type) =>
        assetIndex[type]
          .map((record) => {
            const matchedBy = matchAsset(record, keyword);
            return matchedBy ? toAssetSearchResult(record, matchedBy) : null;
          })
          .filter((item): item is AssetSearchResult => Boolean(item))
      )
      .sort((left, right) => {
        const leftExact = left.objectId.toUpperCase() === keyword ? 0 : 1;
        const rightExact = right.objectId.toUpperCase() === keyword ? 0 : 1;
        return leftExact - rightExact || left.objectType.localeCompare(right.objectType) || left.objectId.localeCompare(right.objectId);
      })
      .slice(0, 40);

    searchResults.value = results;
    searchState.value = results.length ? 'ready' : 'empty';
    searchMessage.value = results.length
      ? `匹配到 ${results.length} 个资产对象`
      : '未匹配到资产，可尝试 NODE-001、SEG-001 或压力传感器';
  } catch (error) {
    searchResults.value = [];
    searchState.value = 'error';
    searchMessage.value = error instanceof ApiClientError ? error.message : '资产索引读取失败';
  }
}

function scheduleAssetSearch() {
  if (searchDebounceTimer) {
    window.clearTimeout(searchDebounceTimer);
  }
  searchDebounceTimer = window.setTimeout(() => {
    void runAssetSearch();
  }, ASSET_SEARCH_DEBOUNCE_MS);
}

function resetDetailContext(nextObjectKey: string) {
  detailContext.objectKey = nextObjectKey;
  Object.assign(detailContext.profile, createDetailTabState<MasterDataRecordResponse | null>(null));
  Object.assign(detailContext.devices, createDetailTabState<DeviceLedgerResponse[]>([]));
  Object.assign(detailContext.alerts, createDetailTabState<AlertRecordResponse[]>([]));
  Object.assign(detailContext.incidents, createDetailTabState<IncidentResponse[]>([]));
  Object.assign(detailContext.workorders, createDetailTabState<WorkOrderResponse[]>([]));
}

function prepareDetailContext(object: GisObjectRecordResponse) {
  const key = objectKey(object);
  if (detailContext.objectKey !== key) {
    resetDetailContext(key);
  }
}

function setDetailTabState<T>(
  state: AssetDetailTabState<T>,
  status: AssetDetailTabState<T>['status'],
  data: T,
  message: string,
  traceId = ''
) {
  state.status = status;
  state.data = data;
  state.message = message;
  state.traceId = traceId;
}

function setActiveDetailTab(tab: AssetDetailTab) {
  activeDetailTab.value = tab;
  void loadDetailTab(tab);
}

function refreshActiveDetailTab() {
  void loadDetailTab(activeDetailTab.value, true);
}

async function loadDetailTab(tab: AssetDetailTab, force = false) {
  if (!selectedObject.value) {
    return;
  }

  const state = detailContext[tab];
  if (!force && (state.status === 'ready' || state.status === 'empty' || state.status === 'loading')) {
    return;
  }

  if (tab === 'profile') {
    await loadProfileTab();
  } else if (tab === 'devices') {
    await loadDevicesTab(force);
  } else if (tab === 'alerts') {
    await loadAlertsTab(force);
  } else if (tab === 'incidents') {
    await loadIncidentsTab(force);
  } else {
    await loadWorkordersTab(force);
  }
}

async function loadProfileTab() {
  if (!selectedObject.value || !isKnownGisObjectType(selectedObject.value.objectType)) {
    setDetailTabState(detailContext.profile, 'empty', null, '当前对象缺少可查询的主数据类型');
    return;
  }

  setDetailTabState(detailContext.profile, 'loading', detailContext.profile.data, '正在读取主数据档案');
  try {
    const response = await getMasterDataDetail(selectedObject.value.objectType, selectedObject.value.objectId);
    setDetailTabState(
      detailContext.profile,
      response.data ? 'ready' : 'empty',
      response.data,
      response.data ? '基础档案已同步' : '后端未返回主数据档案',
      response.traceId
    );
  } catch (error) {
    setDetailError(detailContext.profile, error, null, '基础档案读取失败');
  }
}

async function loadDevicesTab(force = false) {
  if (!selectedObject.value) {
    return;
  }

  setDetailTabState(detailContext.devices, 'loading', detailContext.devices.data, '正在读取关联设备');
  try {
    const devices = await fetchRelatedDevices(selectedObject.value, force);
    setDetailTabState(
      detailContext.devices,
      devices.length ? 'ready' : 'empty',
      devices,
      devices.length ? `已关联 ${devices.length} 台设备` : '当前对象暂无关联设备'
    );
  } catch (error) {
    setDetailError(detailContext.devices, error, [], '关联设备读取失败');
  }
}

async function loadAlertsTab(force = false) {
  if (!selectedObject.value) {
    return;
  }

  setDetailTabState(detailContext.alerts, 'loading', detailContext.alerts.data, '正在聚合历史告警');
  try {
    const devices = await fetchRelatedDevices(selectedObject.value, force);
    const deviceIds = devices.map((device) => device.deviceId);
    if (!deviceIds.length) {
      setDetailTabState(detailContext.alerts, 'empty', [], '无关联设备，暂无法聚合历史告警');
      return;
    }
    const responses = await Promise.all(deviceIds.map((deviceId) => getAlerts(1, 20, { deviceId })));
    const alertsForDevices = uniqueBy(
      responses.flatMap((response) => response.data?.items ?? []),
      (alert) => alert.alertId
    );
    setDetailTabState(
      detailContext.alerts,
      alertsForDevices.length ? 'ready' : 'empty',
      alertsForDevices,
      alertsForDevices.length ? `已聚合 ${alertsForDevices.length} 条历史告警` : '关联设备暂无历史告警',
      responses.find((response) => response.traceId)?.traceId ?? ''
    );
  } catch (error) {
    setDetailError(detailContext.alerts, error, [], '历史告警读取失败');
  }
}

async function loadIncidentsTab(force = false) {
  if (!selectedObject.value) {
    return;
  }

  setDetailTabState(detailContext.incidents, 'loading', detailContext.incidents.data, '正在聚合事件');
  try {
    const incidents = await fetchRelatedIncidents(selectedObject.value, force);
    setDetailTabState(
      detailContext.incidents,
      incidents.length ? 'ready' : 'empty',
      incidents,
      incidents.length ? `已聚合 ${incidents.length} 个事件` : '关联设备暂无事件'
    );
  } catch (error) {
    setDetailError(detailContext.incidents, error, [], '事件读取失败');
  }
}

async function loadWorkordersTab(force = false) {
  if (!selectedObject.value) {
    return;
  }

  setDetailTabState(detailContext.workorders, 'loading', detailContext.workorders.data, '正在聚合工单');
  try {
    const incidents = await fetchRelatedIncidents(selectedObject.value, force);
    if (!incidents.length) {
      setDetailTabState(detailContext.workorders, 'empty', [], '暂无事件，未关联工单');
      return;
    }
    const responses = await Promise.all(
      incidents.map((incident) => getWorkOrders(1, 20, { incidentId: incident.incidentId }))
    );
    const workorders = uniqueBy(
      responses.flatMap((response) => response.data?.items ?? []),
      (workorder) => workorder.workOrderId
    );
    setDetailTabState(
      detailContext.workorders,
      workorders.length ? 'ready' : 'empty',
      workorders,
      workorders.length ? `已聚合 ${workorders.length} 张工单` : '事件暂无关联工单',
      responses.find((response) => response.traceId)?.traceId ?? ''
    );
  } catch (error) {
    setDetailError(detailContext.workorders, error, [], '工单读取失败，可能缺少应急工单权限');
  }
}

async function fetchRelatedDevices(object: GisObjectRecordResponse, force = false) {
  if (!force && detailContext.devices.status === 'ready') {
    return detailContext.devices.data;
  }

  const directDeviceIds = relatedIds(object, 'deviceIds');
  const results: DeviceLedgerResponse[] = [];
  let listResponseTraceId = '';

  if (object.objectType === 'DEVICE') {
    const response = await getDeviceLedgerDevice(object.objectId);
    listResponseTraceId = response.traceId;
    if (response.data) {
      results.push(response.data);
    }
  } else if (object.objectType === 'SEGMENT') {
    const response = await getDeviceLedgerDevices(1, 50, { segmentId: object.objectId });
    listResponseTraceId = response.traceId;
    results.push(...(response.data?.items ?? []));
  } else if (object.objectType === 'NODE') {
    const response = await getDeviceLedgerDevices(1, 50, { nodeId: object.objectId });
    listResponseTraceId = response.traceId;
    results.push(...(response.data?.items ?? []));
  } else if (object.objectType === 'FACILITY') {
    const response = await getDeviceLedgerDevices(1, 50, { facilityId: object.objectId });
    listResponseTraceId = response.traceId;
    results.push(...(response.data?.items ?? []));
  }

  const loadedIds = new Set(results.map((device) => device.deviceId));
  const fallbackIds = directDeviceIds.filter((deviceId) => !loadedIds.has(deviceId));
  if (fallbackIds.length) {
    const fallbackResponses = await Promise.all(fallbackIds.map((deviceId) => getDeviceLedgerDevice(deviceId)));
    fallbackResponses.forEach((response) => {
      if (response.data) {
        results.push(response.data);
      }
    });
    listResponseTraceId = fallbackResponses.find((response) => response.traceId)?.traceId ?? listResponseTraceId;
  }

  const uniqueDevices = uniqueBy(results, (device) => device.deviceId);
  setDetailTabState(
    detailContext.devices,
    uniqueDevices.length ? 'ready' : 'empty',
    uniqueDevices,
    uniqueDevices.length ? `已关联 ${uniqueDevices.length} 台设备` : '当前对象暂无关联设备',
    listResponseTraceId
  );
  return uniqueDevices;
}

async function fetchRelatedIncidents(object: GisObjectRecordResponse, force = false) {
  if (!force && detailContext.incidents.status === 'ready') {
    return detailContext.incidents.data;
  }

  const devices = await fetchRelatedDevices(object, force);
  const deviceIds = devices.map((device) => device.deviceId);
  if (!deviceIds.length) {
    setDetailTabState(detailContext.incidents, 'empty', [], '无关联设备，暂无法聚合事件');
    return [];
  }

  const responses = await Promise.all(deviceIds.map((deviceId) => getIncidents(1, 20, { deviceId })));
  const incidents = uniqueBy(
    responses.flatMap((response) => response.data?.items ?? []),
    (incident) => incident.incidentId
  );
  setDetailTabState(
    detailContext.incidents,
    incidents.length ? 'ready' : 'empty',
    incidents,
    incidents.length ? `已聚合 ${incidents.length} 个事件` : '关联设备暂无事件',
    responses.find((response) => response.traceId)?.traceId ?? ''
  );
  return incidents;
}

function setDetailError<T>(state: AssetDetailTabState<T>, error: unknown, fallback: T, fallbackMessage: string) {
  state.status = 'error';
  state.data = fallback;
  if (error instanceof ApiClientError) {
    state.traceId = error.traceId ?? '';
    state.message = error.message;
    return;
  }
  state.traceId = '';
  state.message = fallbackMessage;
}

function relatedIds(object: GisObjectRecordResponse, key: string) {
  const entries = Object.entries(object.relatedObjectIds ?? {});
  const matched = entries.find(([entryKey]) => entryKey.toLowerCase() === key.toLowerCase());
  return matched?.[1] ?? [];
}

function relatedKeyToObjectType(key: string): GisObjectType | null {
  const normalized = key.toLowerCase();
  if (normalized.includes('device')) {
    return 'DEVICE';
  }
  if (normalized.includes('segment')) {
    return 'SEGMENT';
  }
  if (normalized.includes('node')) {
    return 'NODE';
  }
  if (normalized.includes('facility')) {
    return 'FACILITY';
  }
  return null;
}

function uniqueBy<T>(items: T[], keyGetter: (item: T) => string | null | undefined) {
  const seen = new Set<string>();
  const result: T[] = [];
  items.forEach((item) => {
    const key = keyGetter(item);
    if (!key || seen.has(key)) {
      return;
    }
    seen.add(key);
    result.push(item);
  });
  return result;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function statusToneClass(status: string | null | undefined) {
  const normalized = status?.toUpperCase() ?? '';
  if (['ONLINE', 'ACTIVE', 'OPEN', 'TRIGGERED', 'CREATED', 'DISPATCHED', 'ACCEPTED'].includes(normalized)) {
    return 'border-emerald-400/20 bg-emerald-400/10 text-emerald-100';
  }
  if (['WARNING', 'PENDING_CONFIRMATION', 'ESCALATED', 'SUPPRESSED'].includes(normalized)) {
    return 'border-amber-400/20 bg-amber-400/10 text-amber-100';
  }
  if (['OFFLINE', 'HIGH', 'CRITICAL', 'CLOSED', 'FALSE_POSITIVE'].includes(normalized)) {
    return 'border-rose-400/20 bg-rose-400/10 text-rose-100';
  }
  return 'border-white/10 bg-white/[0.045] text-slate-200';
}

async function openRelatedAsset(objectType: string, objectId: string) {
  if (!isKnownGisObjectType(objectType)) {
    return;
  }
  detailTraceId.value = '';
  try {
    const response = await getGisObjectDetail(objectType, objectId, INITIAL_QUERY.displaySrid);
    if (response.data) {
      detailTraceId.value = response.traceId;
      await selectObject(response.data, false);
      renderSearchHighlight(response.data);
      focusSelectedOnMap();
    }
  } catch (error) {
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '关联对象读取失败';
  }
}

function cloneDefaultLayerState(): Record<GisLayerKey, MapLayerState> {
  return {
    SEGMENT: { ...defaultLayerState.SEGMENT },
    DEVICE: { ...defaultLayerState.DEVICE },
    NODE: { ...defaultLayerState.NODE },
    FACILITY: { ...defaultLayerState.FACILITY },
    ALERT: { ...defaultLayerState.ALERT }
  };
}

function loadLayerState(): Record<GisLayerKey, MapLayerState> {
  const defaults = cloneDefaultLayerState();
  if (typeof window === 'undefined') {
    return defaults;
  }

  try {
    const raw = window.localStorage.getItem(LAYER_STATE_STORAGE_KEY);
    if (!raw) {
      return defaults;
    }
    const payload = JSON.parse(raw) as {
      version?: number;
      layers?: Partial<Record<GisLayerKey, Partial<MapLayerState>>>;
    };
    if (payload.version !== 1 || !payload.layers) {
      return defaults;
    }
    layerConfigs.forEach((config) => {
      const stored = payload.layers?.[config.key];
      if (!stored) {
        return;
      }
      defaults[config.key] = {
        visible: typeof stored.visible === 'boolean' ? stored.visible : defaults[config.key].visible,
        opacity: normalizeOpacity(stored.opacity),
        legendCollapsed:
          typeof stored.legendCollapsed === 'boolean'
            ? stored.legendCollapsed
            : defaults[config.key].legendCollapsed
      };
    });
  } catch {
    return defaults;
  }

  return defaults;
}

function persistLayerState() {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(
    LAYER_STATE_STORAGE_KEY,
    JSON.stringify({
      version: 1,
      layers: layerState
    })
  );
}

function normalizeOpacity(value: unknown): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return 80;
  }
  return Math.min(100, Math.max(20, Math.round(numeric)));
}

function getLayerConfig(objectType: string) {
  return layerConfigs.find((item) => item.key === objectType) ?? layerConfigs[0];
}

function isGisObjectLayer(layerKey: GisLayerKey): layerKey is GisObjectType {
  return layerKey !== 'ALERT';
}

function layerObjectCount(layerKey: GisLayerKey) {
  if (layerKey === 'ALERT') {
    return locatedAlertCount.value;
  }
  return objects.value.filter((object) => object.objectType === layerKey).length;
}

function opacityFactor(layerKey: GisLayerKey) {
  return layerState[layerKey].opacity / 100;
}

function severityColor(severity: string | null | undefined) {
  const normalized = severity?.toUpperCase() ?? '';
  if (normalized === 'CRITICAL') {
    return '#F43F5E';
  }
  if (normalized === 'HIGH') {
    return '#F97316';
  }
  if (normalized === 'MEDIUM') {
    return '#F59E0B';
  }
  return '#38BDF8';
}

function legendShapeClass(shape: string) {
  if (shape === 'line') {
    return 'h-0.5 w-8 rounded-full';
  }
  if (shape === 'diamond') {
    return 'h-3 w-3 rotate-45 rounded-[2px]';
  }
  if (shape === 'ring') {
    return 'h-3.5 w-3.5 rounded-full border-2 bg-transparent';
  }
  return 'h-3.5 w-3.5 rounded-full';
}

function formatNumber(value: number | null | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-';
  }
  return value.toFixed(6);
}

function formatAttribute(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function parseWkt(wkt: string | null | undefined): ParsedGeometry | null {
  if (!wkt) {
    return null;
  }

  const normalized = wkt.trim();
  const pointMatch = normalized.match(/^POINT\s*\(\s*([-\d.]+)\s+([-\d.]+)\s*\)$/i);
  if (pointMatch) {
    const lng = Number(pointMatch[1]);
    const lat = Number(pointMatch[2]);
    if (Number.isFinite(lng) && Number.isFinite(lat)) {
      return { kind: 'POINT', point: [lat, lng] };
    }
  }

  const lineMatch = normalized.match(/^LINESTRING\s*\((.+)\)$/i);
  if (lineMatch) {
    const points = lineMatch[1]
      .split(',')
      .map((pair) => pair.trim().split(/\s+/).map(Number))
      .filter(([lng, lat]) => Number.isFinite(lng) && Number.isFinite(lat))
      .map(([lng, lat]) => [lat, lng] as LatLngExpression);
    if (points.length >= 2) {
      return { kind: 'LINESTRING', points };
    }
  }

  return null;
}

function initializeMap() {
  if (!mapElement.value || map.value) {
    return;
  }

  map.value = markRaw(L.map(mapElement.value, {
    zoomControl: false,
    attributionControl: false,
    preferCanvas: true
  }).setView([30.285, 120.165], 14));

  L.control.zoom({ position: 'bottomright' }).addTo(map.value);
  L.control.attribution({ position: 'bottomleft', prefix: false }).addTo(map.value);
  initializeLayerGroups();
  map.value.on('click', handleMapClick);
}

function initializeLayerGroups() {
  if (!map.value) {
    return;
  }
  layerConfigs.forEach((config) => {
    if (!layerGroups.has(config.key)) {
      layerGroups.set(config.key, markRaw(L.layerGroup()));
    }
    syncLayerVisibility(config.key);
  });
  initializeFlowLayer();
}

function initializeFlowLayer() {
  if (!map.value || flowLayerGroup.value) {
    return;
  }
  flowLayerGroup.value = markRaw(L.layerGroup());
  if (flowLayerVisible.value) {
    flowLayerGroup.value.addTo(map.value);
  }
  renderFlowLayer();
  startFlowTimers();
}

function syncLayerVisibility(layerKey: GisLayerKey) {
  if (!map.value) {
    return;
  }
  const group = layerGroups.get(layerKey);
  if (!group) {
    return;
  }
  const isOnMap = map.value.hasLayer(group);
  if (layerState[layerKey].visible && !isOnMap) {
    group.addTo(map.value);
  }
  if (!layerState[layerKey].visible && isOnMap) {
    map.value.removeLayer(group);
  }
}

function syncFlowLayerVisibility() {
  if (!map.value || !flowLayerGroup.value) {
    return;
  }
  const isOnMap = map.value.hasLayer(flowLayerGroup.value);
  if (flowLayerVisible.value && !isOnMap) {
    flowLayerGroup.value.addTo(map.value);
    startFlowTimers();
  }
  if (!flowLayerVisible.value && isOnMap) {
    map.value.removeLayer(flowLayerGroup.value);
  }
}

function clearObjectLayers() {
  objectLayers.forEach(({ layerKey, layer }) => {
    layerGroups.get(layerKey)?.removeLayer(layer);
  });
  objectLayers.clear();
}

function clearAlertLayers() {
  alertLayers.forEach(({ layer }) => {
    layerGroups.get('ALERT')?.removeLayer(layer);
  });
  alertLayers.clear();
  unlocatedAlertCount.value = 0;
}

function clearFlowLayer() {
  flowLineLayers.clear();
  flowPulseLayers.clear();
  flowLayerGroup.value?.clearLayers();
}

function clearSearchHighlight() {
  if (searchHighlightLayer.value && map.value) {
    map.value.removeLayer(searchHighlightLayer.value);
  }
  searchHighlightLayer.value = null;
}

function renderObjectLayers(fit = false) {
  if (!map.value) {
    return;
  }

  clearObjectLayers();
  const bounds: LatLngExpression[] = [];

  objects.value.forEach((object) => {
    const geometry = parseWkt(object.geometry2d);
    if (!geometry) {
      return;
    }

    const config = getLayerConfig(object.objectType);
    const layerKey = config.key;
    if (!isGisObjectLayer(layerKey)) {
      return;
    }
    const isSelected = selectedObject.value
      ? objectKey(object) === objectKey(selectedObject.value)
      : false;
    let layer: CircleMarker | Polyline;

    if (geometry.kind === 'POINT') {
      layer = L.circleMarker(geometry.point, getObjectPointStyle(object, isSelected));
      bounds.push(geometry.point);
    } else {
      layer = L.polyline(geometry.points, getObjectLineStyle(object, isSelected));
      bounds.push(...geometry.points);
    }

    layer.on('click', (event) => {
      event.originalEvent.stopPropagation();
      selectObject(object);
    });
    layer.bindTooltip(`${object.objectType} / ${object.objectName || object.objectId}`, {
      direction: 'top',
      opacity: 0.9
    });
    layerGroups.get(layerKey)?.addLayer(layer);
    objectLayers.set(objectKey(object), { layerKey, layer, object });
  });

  if (fit && bounds.length > 0) {
    map.value.fitBounds(bounds as LatLngBoundsExpression, { padding: [36, 36], maxZoom: 17 });
  }
}

function renderFlowLayer() {
  if (!map.value || !flowLayerGroup.value) {
    return;
  }
  clearFlowLayer();

  flowSegments.forEach((segment) => {
    const reading = flowSnapshot.value.readings[segment.key];
    const line = L.polyline(segment.path, getFlowLineStyle(segment.key));
    line.bindTooltip(`${segment.label} / ${reading.flow} ${reading.unit}`, {
      direction: 'top',
      opacity: 0.92
    });
    flowLayerGroup.value?.addLayer(line);
    flowLineLayers.set(segment.key, markRaw(line));

    const pulse = L.circleMarker(interpolatePath(segment.path, 0), getFlowPulseStyle(segment.key));
    pulse.bindTooltip(`${segment.label} 实时流量 ${reading.flow} ${reading.unit}`, {
      direction: 'top',
      opacity: 0.92
    });
    flowLayerGroup.value?.addLayer(pulse);
    flowPulseLayers.set(segment.key, markRaw(pulse));
  });
}

function updateFlowLayerStyles() {
  flowSegments.forEach((segment) => {
    const reading = flowSnapshot.value.readings[segment.key];
    const line = flowLineLayers.get(segment.key);
    const pulse = flowPulseLayers.get(segment.key);
    line?.setStyle(getFlowLineStyle(segment.key));
    line?.setTooltipContent(`${segment.label} / ${reading.flow} ${reading.unit}`);
    pulse?.setStyle(getFlowPulseStyle(segment.key));
    pulse?.setTooltipContent(`${segment.label} 实时流量 ${reading.flow} ${reading.unit}`);
  });
}

function getFlowLineStyle(segmentKey: FlowSegmentKey) {
  const segment = flowSegments.find((item) => item.key === segmentKey);
  const reading = flowSnapshot.value.readings[segmentKey];
  return {
    color: segment?.color ?? '#38BDF8',
    dashArray: segmentKey === 'INLET' ? undefined : '10 10',
    lineCap: 'round' as const,
    opacity: flowLayerVisible.value ? 0.92 : 0,
    weight: 4 + Math.max(0, reading.ratio) * 5
  };
}

function getFlowPulseStyle(segmentKey: FlowSegmentKey) {
  const segment = flowSegments.find((item) => item.key === segmentKey);
  const reading = flowSnapshot.value.readings[segmentKey];
  return {
    radius: 5 + Math.max(0, reading.ratio) * 5,
    color: '#E0F2FE',
    fillColor: segment?.color ?? '#38BDF8',
    fillOpacity: 0.95,
    opacity: 0.98,
    weight: 2
  };
}

function interpolatePath(path: LatLngExpression[], progress: number): LatLngExpression {
  const [start, end] = path;
  const [startLat, startLng] = toLatLngTuple(start);
  const [endLat, endLng] = toLatLngTuple(end);
  return [
    startLat + (endLat - startLat) * progress,
    startLng + (endLng - startLng) * progress
  ];
}

function toLatLngTuple(value: LatLngExpression): [number, number] {
  if (Array.isArray(value)) {
    return [Number(value[0]), Number(value[1])];
  }
  const point = L.latLng(value);
  return [point.lat, point.lng];
}

function startFlowTimers() {
  if (!flowUpdateTimer) {
    flowUpdateTimer = window.setInterval(() => {
      void refreshFlowSnapshot();
    }, FLOW_UPDATE_MS);
  }
  if (!flowAnimationFrame) {
    flowAnimationFrame = window.requestAnimationFrame(animateFlowPulses);
  }
}

function stopFlowTimers() {
  if (flowUpdateTimer) {
    window.clearInterval(flowUpdateTimer);
    flowUpdateTimer = null;
  }
  if (flowAnimationFrame) {
    window.cancelAnimationFrame(flowAnimationFrame);
    flowAnimationFrame = 0;
  }
}

async function refreshFlowSnapshot() {
  flowSnapshot.value = await getCurrentFlowSnapshot();
  updateFlowLayerStyles();
}

function animateFlowPulses(timestamp: number) {
  if (!flowLayerVisible.value) {
    flowAnimationFrame = window.requestAnimationFrame(animateFlowPulses);
    return;
  }
  flowSegments.forEach((segment, index) => {
    const reading = flowSnapshot.value.readings[segment.key];
    const speedFactor = 0.85 + reading.ratio * 0.55;
    const progress = ((timestamp * speedFactor + index * 620) % FLOW_ANIMATION_MS) / FLOW_ANIMATION_MS;
    flowPulseLayers.get(segment.key)?.setLatLng(interpolatePath(segment.path, progress));
  });
  flowAnimationFrame = window.requestAnimationFrame(animateFlowPulses);
}

function toggleFlowLayer() {
  flowLayerVisible.value = !flowLayerVisible.value;
  syncFlowLayerVisibility();
}

function renderSearchHighlight(object: GisObjectRecordResponse) {
  if (!map.value) {
    return false;
  }

  clearSearchHighlight();
  if (objectLayers.has(objectKey(object))) {
    return true;
  }

  const geometry = parseWkt(object.geometry2d);
  if (!geometry) {
    return false;
  }

  const layer =
    geometry.kind === 'POINT'
      ? L.circleMarker(geometry.point, {
        radius: 11,
        color: '#F59E0B',
        fillColor: '#F59E0B',
        fillOpacity: 0.2,
        opacity: 0.98,
        weight: 3
      })
      : L.polyline(geometry.points, {
        color: '#F59E0B',
        dashArray: '6 6',
        opacity: 0.98,
        weight: 6
      });

  layer.bindTooltip(`搜索定位 / ${object.objectType} / ${object.objectName || object.objectId}`, {
    direction: 'top',
    opacity: 0.92
  });
  layer.addTo(map.value);
  searchHighlightLayer.value = markRaw(layer);
  return true;
}

function getObjectPointStyle(object: GisObjectRecordResponse, isSelected: boolean) {
  const config = getLayerConfig(object.objectType);
  const layerKey = config.key;
  const factor = isGisObjectLayer(layerKey) ? opacityFactor(layerKey) : 0.8;
  const color = isSelected ? '#F59E0B' : config.color;
  return {
    radius: isSelected ? 8 : 6,
    color,
    fillColor: color,
    fillOpacity: isSelected ? 0.95 : Math.max(0.12, 0.72 * factor),
    opacity: isSelected ? 1 : Math.max(0.18, factor),
    weight: isSelected ? 3 : 2
  };
}

function getObjectLineStyle(object: GisObjectRecordResponse, isSelected: boolean) {
  const config = getLayerConfig(object.objectType);
  const layerKey = config.key;
  const factor = isGisObjectLayer(layerKey) ? opacityFactor(layerKey) : 0.8;
  return {
    color: isSelected ? '#F59E0B' : config.color,
    weight: isSelected ? 5 : 3,
    opacity: isSelected ? 0.95 : Math.max(0.18, 0.78 * factor)
  };
}

function updateObjectLayerStyles(layerKey?: GisLayerKey) {
  objectLayers.forEach((rendered) => {
    if (layerKey && rendered.layerKey !== layerKey) {
      return;
    }
    const isSelected = selectedObject.value
      ? objectKey(rendered.object) === objectKey(selectedObject.value)
      : false;
    const geometry = parseWkt(rendered.object.geometry2d);
    if (geometry?.kind === 'LINESTRING') {
      rendered.layer.setStyle(getObjectLineStyle(rendered.object, isSelected));
      return;
    }
    rendered.layer.setStyle(getObjectPointStyle(rendered.object, isSelected));
  });
}

async function loadBboxObjects(query: GisBboxQuery = readRouteBboxQuery()) {
  state.value = 'loading';
  message.value = '正在读取 bbox 范围内 GIS 对象';
  activeGisQuery.value = query;
  try {
    const response = await queryGisBbox(query);
    const page = response.data;
    objects.value = page?.items ?? [];
    total.value = page?.total ?? 0;
    bboxTraceId.value = response.traceId;
    state.value = objects.value.length ? 'ready' : 'empty';
    message.value = objects.value.length ? 'GIS 对象已加载' : '当前 bbox 未返回 GIS 对象';
    await nextTick();
    renderObjectLayers(true);
    await loadAlerts();
  } catch (error) {
    objects.value = [];
    total.value = 0;
    state.value = 'error';
    clearObjectLayers();
    clearAlertLayers();
    if (error instanceof ApiClientError) {
      bboxTraceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = 'GIS 对象读取失败';
  }
}

async function selectObject(object: GisObjectRecordResponse, loadDetail = true) {
  clearSearchHighlight();
  selectedObject.value = object;
  isDrawerOpen.value = true;
  activeDetailTab.value = 'profile';
  prepareDetailContext(object);
  void loadDetailTab('profile', true);
  updateObjectLayerStyles();

  if (!loadDetail) {
    return;
  }

  try {
    const response = await getGisObjectDetail(object.objectType, object.objectId, activeGisQuery.value.displaySrid);
    selectedObject.value = response.data ?? object;
    detailTraceId.value = response.traceId;
    if (response.data) {
      prepareDetailContext(response.data);
      void loadDetailTab('profile', true);
    }
    updateObjectLayerStyles();
  } catch (error) {
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '对象详情读取失败';
  }
}

async function locateSearchResult(result: AssetSearchResult) {
  const key = assetResultKey(result);
  locatingAssetKey.value = key;
  searchMessage.value = `正在定位 ${result.objectType} / ${result.objectId}`;
  searchTraceId.value = '';

  try {
    if (!layerState[result.objectType].visible) {
      setLayerVisible(result.objectType, true);
    }

    const response = await getGisObjectDetail(result.objectType, result.objectId, activeGisQuery.value.displaySrid);
    const object = response.data;
    searchTraceId.value = response.traceId;
    detailTraceId.value = response.traceId;

    if (!object) {
      searchState.value = 'empty';
      searchMessage.value = '资产存在，但 GIS 详情未返回可定位对象';
      return;
    }

    await selectObject(object, false);
    const canRender = renderSearchHighlight(object);
    updateObjectLayerStyles();
    focusSelectedOnMap();
    searchState.value = 'ready';
    searchMessage.value = canRender
      ? `已定位 ${object.objectType} / ${object.objectName || object.objectId}`
      : `${object.objectType} / ${object.objectId} 可检索但不可定位`;
  } catch (error) {
    searchState.value = 'error';
    if (error instanceof ApiClientError) {
      searchTraceId.value = error.traceId ?? '';
      searchMessage.value = error.status === 404
        ? `${result.objectType} / ${result.objectId} 可检索但未找到 GIS 空间对象`
        : error.message;
      return;
    }
    searchMessage.value = '资产定位失败';
  } finally {
    locatingAssetKey.value = '';
  }
}

async function handleMapClick(event: LeafletMouseEvent) {
  pickMessage.value = '正在执行对象点查';
  lastPick.value = null;
  try {
    const response = await pickGisObject({
      x: event.latlng.lng,
      y: event.latlng.lat,
      authoritySrid: activeGisQuery.value.authoritySrid,
      displaySrid: activeGisQuery.value.displaySrid,
      objectTypes: layerConfigs
        .filter((item) => isGisObjectLayer(item.key) && layerState[item.key].visible)
        .map((item) => item.key),
      toleranceMeters: 80
    });
    if (response.data?.object) {
      lastPick.value = response.data;
      pickTraceId.value = response.traceId;
      pickMessage.value = `命中 ${response.data.object.objectType} / ${response.data.object.objectName}`;
      await selectObject(response.data.object);
      return;
    }
    pickMessage.value = '未命中对象';
  } catch (error) {
    lastPick.value = null;
    if (error instanceof ApiClientError) {
      pickTraceId.value = error.traceId ?? '';
      pickMessage.value = error.status === 404 ? '该点位未命中 GIS 对象' : error.message;
      return;
    }
    pickMessage.value = '对象点查失败';
  }
}

async function applyRouteContext() {
  const objectType = readStringQuery('objectType', '');
  const objectId = readStringQuery('objectId', '');
  if (!objectType || !objectId) {
    return;
  }

  try {
    const response = await getGisObjectDetail(objectType, objectId, activeGisQuery.value.displaySrid);
    if (!response.data) {
      return;
    }
    detailTraceId.value = response.traceId;
    if (isGisObjectLayer(response.data.objectType as GisLayerKey)) {
      layerState[response.data.objectType as GisObjectType].visible = true;
    }
    await selectObject(response.data, false);
    const canRender = renderSearchHighlight(response.data);
    focusSelectedOnMap();
    searchState.value = canRender ? 'ready' : searchState.value;
    searchMessage.value = canRender
      ? `已恢复 3D 回退上下文 ${response.data.objectType} / ${response.data.objectId}`
      : `${response.data.objectType} / ${response.data.objectId} 已恢复上下文但不可定位`;
  } catch (error) {
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      message.value = error.status === 404 ? '3D 回退对象未找到 GIS 空间详情' : error.message;
      return;
    }
    message.value = '3D 回退上下文恢复失败';
  }
}

function readRouteBboxQuery(): GisBboxQuery {
  return {
    minX: readNumberQuery('minX', INITIAL_QUERY.minX),
    minY: readNumberQuery('minY', INITIAL_QUERY.minY),
    maxX: readNumberQuery('maxX', INITIAL_QUERY.maxX),
    maxY: readNumberQuery('maxY', INITIAL_QUERY.maxY),
    authoritySrid: readStringQuery('authoritySrid', INITIAL_QUERY.authoritySrid),
    displaySrid: readStringQuery('displaySrid', INITIAL_QUERY.displaySrid),
    page: INITIAL_QUERY.page,
    pageSize: INITIAL_QUERY.pageSize
  };
}

function readStringQuery(key: string, fallback: string) {
  const value = route.query[key];
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function readNumberQuery(key: string, fallback: number) {
  const value = route.query[key];
  const parsed = typeof value === 'string' ? Number(value) : Number.NaN;
  return Number.isFinite(parsed) ? parsed : fallback;
}

function toggleLayer(layerKey: GisLayerKey) {
  setLayerVisible(layerKey, !layerState[layerKey].visible);
}

function setLayerVisible(layerKey: GisLayerKey, visible: boolean) {
  layerState[layerKey].visible = visible;
  syncLayerVisibility(layerKey);
  persistLayerState();
}

function setLayerOpacity(layerKey: GisLayerKey, value: number) {
  layerState[layerKey].opacity = normalizeOpacity(value);
  if (layerKey === 'ALERT') {
    updateAlertLayerStyles();
  } else {
    updateObjectLayerStyles(layerKey);
  }
  persistLayerState();
}

function setAllLayers(visible: boolean) {
  layerConfigs.forEach((layer) => {
    layerState[layer.key].visible = visible;
    syncLayerVisibility(layer.key);
  });
  persistLayerState();
}

function resetLayerState() {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem(LAYER_STATE_STORAGE_KEY);
  }
  const defaults = cloneDefaultLayerState();
  layerConfigs.forEach((layer) => {
    layerState[layer.key] = { ...defaults[layer.key] };
    syncLayerVisibility(layer.key);
  });
  updateObjectLayerStyles();
  updateAlertLayerStyles();
}

function toggleLegend(layerKey: GisLayerKey) {
  layerState[layerKey].legendCollapsed = !layerState[layerKey].legendCollapsed;
  persistLayerState();
}

function onOpacityInput(layerKey: GisLayerKey, event: Event) {
  const input = event.target as HTMLInputElement;
  setLayerOpacity(layerKey, Number(input.value));
}

async function loadAlerts() {
  alertState.value = 'loading';
  alertMessage.value = '正在同步告警图层';
  try {
    const response = await getAlerts(1, 50);
    const page = response.data;
    alerts.value = page?.items ?? [];
    alertTotal.value = page?.total ?? 0;
    alertTraceId.value = response.traceId;
    alertState.value = alerts.value.length ? 'ready' : 'empty';
    alertMessage.value = alerts.value.length ? '告警图层已同步' : '当前无告警记录';
    renderAlertLayers();
  } catch (error) {
    alerts.value = [];
    alertTotal.value = 0;
    clearAlertLayers();
    alertState.value = 'error';
    if (error instanceof ApiClientError) {
      alertTraceId.value = error.traceId ?? '';
      alertMessage.value = error.message;
      return;
    }
    alertMessage.value = '告警图层读取失败';
  }
}

function renderAlertLayers() {
  clearAlertLayers();
  alerts.value.forEach((alert) => {
    const object = findAlertObject(alert);
    if (!object) {
      unlocatedAlertCount.value += 1;
      return;
    }
    const point = getAlertPoint(object);
    if (!point) {
      unlocatedAlertCount.value += 1;
      return;
    }
    const marker = L.circleMarker(point, getAlertStyle(alert));
    marker.on('click', (event) => {
      event.originalEvent.stopPropagation();
      pickMessage.value = `告警定位 ${alert.alertId} / ${alert.severity}`;
      selectObject(object);
    });
    marker.bindTooltip(`${alert.severity || 'ALERT'} / ${alert.ruleCode} / ${alert.alertId}`, {
      direction: 'top',
      opacity: 0.92
    });
    layerGroups.get('ALERT')?.addLayer(marker);
    alertLayers.set(alert.alertId, { layer: marker, alert, object });
  });
}

function updateAlertLayerStyles() {
  alertLayers.forEach(({ layer, alert }) => {
    layer.setStyle(getAlertStyle(alert));
  });
}

function getAlertStyle(alert: AlertRecordResponse) {
  const color = severityColor(alert.severity);
  const factor = opacityFactor('ALERT');
  return {
    radius: alert.severity?.toUpperCase() === 'CRITICAL' ? 10 : 8,
    color,
    fillColor: color,
    fillOpacity: Math.max(0.16, 0.42 * factor),
    opacity: Math.max(0.24, factor),
    weight: alert.severity?.toUpperCase() === 'CRITICAL' ? 3 : 2,
    dashArray: alert.suppressed ? '4 4' : undefined
  };
}

function findAlertObject(alert: AlertRecordResponse) {
  const candidates = [
    alert.deviceId ? { type: 'DEVICE', id: alert.deviceId } : null,
    alert.segmentId ? { type: 'SEGMENT', id: alert.segmentId } : null,
    alert.nodeId ? { type: 'NODE', id: alert.nodeId } : null
  ].filter((item): item is { type: GisObjectType; id: string } => Boolean(item));

  for (const candidate of candidates) {
    const matched = objects.value.find(
      (object) => object.objectType === candidate.type && object.objectId === candidate.id
    );
    if (matched) {
      return matched;
    }
  }
  return null;
}

function getAlertPoint(object: GisObjectRecordResponse): LatLngExpression | null {
  const geometry = parseWkt(object.geometry2d);
  if (geometry?.kind === 'POINT') {
    return geometry.point;
  }
  if (geometry?.kind === 'LINESTRING') {
    return geometry.points[Math.floor(geometry.points.length / 2)] ?? null;
  }
  if (object.anchorPoint) {
    return [object.anchorPoint.y, object.anchorPoint.x];
  }
  return null;
}

function closeDrawer() {
  isDrawerOpen.value = false;
}

function focusSelectedOnMap() {
  if (!map.value || !selectedObject.value) {
    return;
  }
  const geometry = parseWkt(selectedObject.value.geometry2d);
  if (!geometry) {
    return;
  }
  if (geometry.kind === 'POINT') {
    map.value.setView(geometry.point, Math.max(map.value.getZoom(), 16));
  } else {
    map.value.fitBounds(geometry.points as LatLngBoundsExpression, {
      padding: [44, 44],
      maxZoom: 17
    });
  }
}

watch([searchKeyword, selectedSearchType], scheduleAssetSearch);

onMounted(async () => {
  await nextTick();
  initializeMap();
  await loadBboxObjects();
  await applyRouteContext();
});

onBeforeUnmount(() => {
  if (searchDebounceTimer) {
    window.clearTimeout(searchDebounceTimer);
  }
  stopFlowTimers();
  assetIndexPromises.clear();
  clearSearchHighlight();
  if (map.value) {
    map.value.off('click', handleMapClick);
    map.value.remove();
    map.value = null;
  }
  clearObjectLayers();
  clearAlertLayers();
  clearFlowLayer();
  layerGroups.clear();
});
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-[1600px] flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8">
      <header class="glass-panel flex flex-col gap-4 p-4 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex min-w-0 items-center gap-3">
          <RouterLink class="icon-button focus-ring" to="/mgmt" aria-label="返回综合管理平台">
            <ArrowLeft class="h-4 w-4" />
          </RouterLink>
          <div
            class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-primarySoft/30 bg-primary/20 text-blue-100"
            aria-hidden="true"
          >
            <MapPinned class="h-6 w-6" />
          </div>
          <div class="min-w-0">
            <p class="font-display text-lg font-semibold text-white sm:text-xl">2D 一张图</p>
            <p class="truncate text-sm text-slate-400">
              综合管理平台 / GIS 主入口 / EPSG:4490
            </p>
          </div>
          <RouterLink class="secondary-button focus-ring hidden sm:inline-flex" :to="{ path: '/mgmt/gis/3d', query: gis3dQuery }">
            <Layers3 class="h-4 w-4" />
            3D 占位
          </RouterLink>
        </div>

        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:min-w-[32rem]">
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">bbox total</p>
            <p class="mt-1 font-display text-xl font-semibold text-white">{{ total }}</p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">已绘制</p>
            <p class="mt-1 font-display text-xl font-semibold text-blue-100">{{ drawableCount }}</p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">可见图层</p>
            <p class="mt-1 font-display text-xl font-semibold text-emerald-200">
              {{ visibleLayerCount }} / {{ layerConfigs.length }}
            </p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">定位告警</p>
            <p class="mt-1 font-display text-xl font-semibold text-rose-100">
              {{ locatedAlertCount }}
            </p>
          </div>
        </div>
      </header>

      <section class="grid min-h-[calc(100vh-9rem)] gap-4 xl:grid-cols-[280px_minmax(0,1fr)_360px]">
        <aside class="glass-panel order-2 flex flex-col gap-4 p-4 xl:order-1">
          <section class="rounded-lg border border-blue-400/20 bg-blue-400/10 p-3">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-white">资产检索</p>
                <p class="mt-1 text-xs text-slate-400">编码、名称、类型定位</p>
              </div>
              <Search class="h-5 w-5 text-blue-200" aria-hidden="true" />
            </div>

            <label class="mt-3 block text-xs font-semibold text-slate-300" for="asset-search-input">
              检索关键词
            </label>
            <div class="mt-2 flex items-center gap-2 rounded-lg border border-white/10 bg-black/20 px-3 py-2 focus-within:border-blue-300/60">
              <Search class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
              <input
                id="asset-search-input"
                v-model="searchKeyword"
                class="min-w-0 flex-1 bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                type="search"
                autocomplete="off"
                placeholder="NODE-001 / SEG-001 / 压力传感器"
              />
              <button
                v-if="searchKeyword"
                class="icon-button focus-ring h-7 w-7 border-transparent bg-transparent"
                type="button"
                aria-label="清空资产检索"
                @click="clearSearch"
              >
                <X class="h-3.5 w-3.5" />
              </button>
            </div>

            <div class="mt-3 grid grid-cols-5 gap-1 rounded-lg border border-white/10 bg-black/10 p-1" role="group" aria-label="资产类型筛选">
              <button
                v-for="type in assetSearchTypes"
                :key="type.key"
                class="focus-ring min-h-8 cursor-pointer rounded-md px-1 text-xs font-semibold transition-colors duration-200"
                :class="selectedSearchType === type.key ? 'bg-primary text-white' : 'text-slate-400 hover:bg-white/10 hover:text-slate-100'"
                type="button"
                :aria-pressed="selectedSearchType === type.key"
                @click="setSearchType(type.key)"
              >
                {{ type.label }}
              </button>
            </div>

            <div
              class="mt-3 rounded-lg border px-3 py-2 text-xs leading-5"
              :class="searchState === 'error' ? 'border-rose-400/20 bg-rose-400/10 text-rose-100' : 'border-white/10 bg-black/10 text-slate-300'"
              :role="searchState === 'error' ? 'alert' : 'status'"
              aria-live="polite"
            >
              <div class="flex items-start gap-2">
                <Loader2 v-if="searchState === 'loading'" class="mt-0.5 h-4 w-4 shrink-0 animate-spin text-blue-200" />
                <Crosshair v-else class="mt-0.5 h-4 w-4 shrink-0 text-amber-200" aria-hidden="true" />
                <div class="min-w-0">
                  <p>{{ searchMessage }}</p>
                  <p v-if="searchTraceId" class="mt-1 truncate font-mono text-slate-400">TraceId {{ searchTraceId }}</p>
                  <p v-if="searchReachedLimit" class="mt-1 text-amber-100">索引达到前端分页上限，结果可能截断。</p>
                </div>
              </div>
            </div>

            <div class="mt-3 max-h-72 space-y-2 overflow-y-auto pr-1">
              <button
                v-for="result in searchResults"
                :key="assetResultKey(result)"
                class="focus-ring w-full cursor-pointer rounded-lg border border-white/10 bg-white/[0.045] p-3 text-left transition-colors duration-200 hover:border-blue-300/40 hover:bg-blue-400/10 disabled:cursor-wait disabled:opacity-70"
                type="button"
                :disabled="locatingAssetKey === assetResultKey(result)"
                @click="locateSearchResult(result)"
              >
                <span class="flex items-start justify-between gap-3">
                  <span class="min-w-0">
                    <span class="block truncate text-sm font-semibold text-white">
                      {{ result.objectName || result.objectId }}
                    </span>
                    <span class="mt-1 block font-mono text-[11px] text-blue-100">
                      {{ result.objectType }} / {{ result.objectId }}
                    </span>
                  </span>
                  <span class="shrink-0 rounded-md border border-white/10 px-2 py-1 text-[11px] text-slate-300">
                    {{ result.matchedBy }}
                  </span>
                </span>
                <span class="mt-3 grid grid-cols-2 gap-2 text-xs">
                  <span class="rounded-md border border-white/10 bg-black/10 px-2 py-1 text-slate-400">
                    region <span class="text-slate-100">{{ result.regionId || '-' }}</span>
                  </span>
                  <span class="rounded-md border border-white/10 bg-black/10 px-2 py-1 text-slate-400">
                    status <span class="text-slate-100">{{ result.status || '-' }}</span>
                  </span>
                </span>
              </button>
            </div>

            <div class="mt-3 flex items-center justify-between gap-3 text-xs text-slate-400">
              <span>索引 {{ indexedAssetCount }} / 结果 {{ searchResults.length }}</span>
              <button class="secondary-button focus-ring min-h-8 px-2 text-xs" type="button" @click="refreshSearchIndex">
                <RefreshCcw class="h-3.5 w-3.5" />
                刷新
              </button>
            </div>
          </section>

          <section
            class="rounded-lg border p-3"
            :class="flowLayerVisible ? 'border-cyan-400/25 bg-cyan-400/10' : 'border-white/10 bg-white/[0.045]'"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2 text-sm font-semibold text-white">
                  <GitFork class="h-4 w-4 text-cyan-200" aria-hidden="true" />
                  人字形流量模拟
                </div>
                <p class="mt-1 text-xs leading-5 text-slate-400">固定总流量，两个支管随机分配</p>
              </div>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-semibold text-slate-200">
                <input
                  class="h-4 w-4 cursor-pointer accent-primary"
                  type="checkbox"
                  :checked="flowLayerVisible"
                  aria-label="模拟流量图层显隐"
                  @change="toggleFlowLayer"
                />
                显示
              </label>
            </div>

            <dl class="mt-3 grid grid-cols-3 gap-2 text-xs">
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">总管</dt>
                <dd class="mt-1 font-mono text-cyan-100">
                  {{ flowSnapshot.readings.INLET.flow }} {{ flowSnapshot.readings.INLET.unit }}
                </dd>
              </div>
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">左支</dt>
                <dd class="mt-1 font-mono text-emerald-100">{{ branchAFlow }} m³/h</dd>
              </div>
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">右支</dt>
                <dd class="mt-1 font-mono text-amber-100">{{ branchBFlow }} m³/h</dd>
              </div>
            </dl>

            <div class="mt-3 rounded-lg border border-white/10 bg-black/10 p-3">
              <div class="flex items-center justify-between gap-3 text-xs">
                <span class="text-slate-400">分流比例</span>
                <span class="font-mono text-blue-100">{{ branchBalanceLabel }}</span>
              </div>
              <div class="mt-2 flex h-2 overflow-hidden rounded-full bg-white/10">
                <span class="bg-emerald-400" :style="{ width: `${flowSnapshot.readings.BRANCH_A.ratio * 100}%` }" />
                <span class="bg-amber-400" :style="{ width: `${flowSnapshot.readings.BRANCH_B.ratio * 100}%` }" />
              </div>
            </div>

            <div class="mt-3 flex items-center justify-between gap-3 text-xs text-slate-400">
              <span class="truncate">{{ flowSnapshot.mode }} / {{ formatDateTime(flowSnapshot.updatedAt) }}</span>
              <button class="secondary-button focus-ring min-h-8 px-2 text-xs" type="button" @click="refreshFlowSnapshot">
                <RefreshCcw class="h-3.5 w-3.5" />
                随机
              </button>
            </div>
          </section>

          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-white">图层控制</p>
              <p class="mt-1 text-xs text-slate-400">显隐、透明度、图例与本地持久化</p>
            </div>
            <SlidersHorizontal class="h-5 w-5 text-blue-200" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-3 gap-2">
            <button class="secondary-button focus-ring px-2 text-xs" type="button" @click="setAllLayers(true)">
              <Eye class="h-4 w-4" />
              全显
            </button>
            <button class="secondary-button focus-ring px-2 text-xs" type="button" @click="setAllLayers(false)">
              <EyeOff class="h-4 w-4" />
              全隐
            </button>
            <button class="secondary-button focus-ring px-2 text-xs" type="button" @click="resetLayerState">
              <RotateCcw class="h-4 w-4" />
              默认
            </button>
          </div>
          <button class="secondary-button focus-ring w-full text-xs" type="button" @click="() => loadBboxObjects()">
            <RefreshCcw class="h-4 w-4" />
            重新同步 bbox / 告警
          </button>

          <div class="space-y-3">
            <section
              v-for="layer in layerConfigs"
              :key="layer.key"
              class="rounded-lg border p-3 transition-colors duration-200"
              :class="layerState[layer.key].visible ? 'border-blue-400/25 bg-blue-400/10' : 'border-white/10 bg-white/[0.045]'"
            >
              <div class="flex items-start justify-between gap-3">
                <label class="flex min-w-0 cursor-pointer items-start gap-3">
                  <input
                    class="mt-1 h-4 w-4 cursor-pointer accent-primary"
                    type="checkbox"
                    :checked="layerState[layer.key].visible"
                    :aria-label="`${layer.label}图层显隐`"
                    @change="toggleLayer(layer.key)"
                  />
                  <span class="min-w-0">
                    <span class="flex items-center gap-2">
                      <span
                        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-white/10"
                        :style="{ color: layer.color }"
                        aria-hidden="true"
                      >
                        <component :is="layer.icon" class="h-4 w-4" />
                      </span>
                      <span>
                        <span class="block text-sm font-semibold text-white">{{ layer.label }}</span>
                        <span class="block font-mono text-[11px] text-slate-400">
                          {{ layer.shortLabel }} · {{ layerObjectCount(layer.key) }}
                        </span>
                      </span>
                    </span>
                    <span class="mt-2 block text-xs leading-5 text-slate-400">{{ layer.description }}</span>
                  </span>
                </label>
                <button
                  class="icon-button focus-ring h-8 w-8"
                  type="button"
                  :aria-expanded="!layerState[layer.key].legendCollapsed"
                  :aria-label="`${layer.label}图例折叠`"
                  @click="toggleLegend(layer.key)"
                >
                  <ChevronUp v-if="!layerState[layer.key].legendCollapsed" class="h-4 w-4" />
                  <ChevronDown v-else class="h-4 w-4" />
                </button>
              </div>

              <label class="mt-3 block text-xs text-slate-300">
                <span class="flex items-center justify-between gap-3">
                  <span>透明度</span>
                  <span class="font-mono text-blue-100">{{ layerState[layer.key].opacity }}%</span>
                </span>
                <input
                  class="mt-2 h-2 w-full cursor-pointer accent-primary"
                  type="range"
                  min="20"
                  max="100"
                  step="5"
                  :value="layerState[layer.key].opacity"
                  :aria-label="`${layer.label}透明度`"
                  @input="onOpacityInput(layer.key, $event)"
                />
              </label>

              <div
                v-if="!layerState[layer.key].legendCollapsed"
                class="mt-3 space-y-2 rounded-lg border border-white/10 bg-black/10 p-3"
              >
                <div
                  v-for="item in layer.legendItems"
                  :key="`${layer.key}-${item.label}`"
                  class="flex items-center justify-between gap-3 text-xs"
                >
                  <span class="flex min-w-0 items-center gap-2">
                    <span
                      class="shrink-0"
                      :class="legendShapeClass(item.shape)"
                      :style="item.shape === 'ring' ? { borderColor: item.color } : { backgroundColor: item.color }"
                      aria-hidden="true"
                    />
                    <span class="min-w-0">
                      <span class="block font-semibold text-slate-100">{{ item.label }}</span>
                      <span class="block truncate text-slate-400">{{ item.description }}</span>
                    </span>
                  </span>
                </div>
              </div>
            </section>
          </div>

          <div class="rounded-lg border border-white/10 bg-white/[0.045] p-3 text-xs leading-5 text-slate-300">
            <div class="flex gap-2">
              <Layers3 class="mt-0.5 h-4 w-4 shrink-0 text-amber-200" aria-hidden="true" />
              <p>图层状态保存到 localStorage；切换显隐不会重建地图或重新请求 bbox。</p>
            </div>
          </div>

          <section class="rounded-lg border border-white/10 bg-white/[0.045] p-3">
            <div class="flex items-center gap-2 text-sm font-semibold text-white">
              <MousePointer2 class="h-4 w-4 text-amber-200" aria-hidden="true" />
              点查状态
            </div>
            <p class="mt-2 text-sm leading-5 text-slate-300" aria-live="polite">{{ pickMessage }}</p>
            <p v-if="lastPick" class="mt-2 font-mono text-xs text-blue-100">
              distance {{ lastPick.distanceMeters }}m / tolerance {{ lastPick.toleranceMeters }}m
            </p>
            <p v-if="pickTraceId" class="mt-2 truncate font-mono text-xs text-slate-400">
              TraceId {{ pickTraceId }}
            </p>
          </section>

          <section
            class="rounded-lg border p-3"
            :class="alertState === 'error' ? 'border-rose-400/20 bg-rose-400/10' : 'border-white/10 bg-white/[0.045]'"
          >
            <div class="flex items-center gap-2 text-sm font-semibold text-white">
              <AlertTriangle class="h-4 w-4 text-rose-200" aria-hidden="true" />
              告警图层
            </div>
            <p class="mt-2 text-sm leading-5 text-slate-300" :role="alertState === 'error' ? 'alert' : 'status'">
              {{ alertMessage }}
            </p>
            <dl class="mt-3 grid grid-cols-3 gap-2 text-xs">
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">总数</dt>
                <dd class="font-mono text-white">{{ alertTotal }}</dd>
              </div>
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">定位</dt>
                <dd class="font-mono text-rose-100">{{ locatedAlertCount }}</dd>
              </div>
              <div class="rounded-md border border-white/10 bg-black/10 p-2">
                <dt class="text-slate-400">未定位</dt>
                <dd class="font-mono text-amber-100">{{ unlocatedAlertCount }}</dd>
              </div>
            </dl>
            <p v-if="alertTraceId" class="mt-2 truncate font-mono text-xs text-slate-400">
              TraceId {{ alertTraceId }}
            </p>
          </section>
        </aside>

        <section class="glass-panel order-1 min-h-[560px] overflow-hidden p-3 xl:order-2">
          <div class="relative h-[64vh] min-h-[520px] overflow-hidden rounded-lg border border-white/10 bg-panel xl:h-full">
            <div ref="mapElement" class="h-full w-full" aria-label="2D GIS 地图容器" />
            <div
              v-if="state === 'loading'"
              class="absolute inset-x-4 top-4 z-[500] rounded-lg border border-blue-400/20 bg-ink/90 px-4 py-3 text-sm text-blue-100 backdrop-blur"
              role="status"
            >
              <div class="flex items-center gap-2">
                <Loader2 class="h-4 w-4 animate-spin" />
                {{ message }}
              </div>
            </div>
            <div
              v-else-if="state === 'error'"
              class="absolute inset-x-4 top-4 z-[500] rounded-lg border border-rose-400/20 bg-rose-400/10 px-4 py-3 text-sm text-rose-100 backdrop-blur"
              role="alert"
            >
              <div class="flex items-center gap-2">
                <AlertTriangle class="h-4 w-4" />
                {{ message }}
              </div>
            </div>
            <div
              v-if="flowLayerVisible"
              class="absolute right-4 top-4 z-[500] w-[min(20rem,calc(100%-2rem))] rounded-lg border border-cyan-300/20 bg-ink/85 p-3 text-xs text-slate-300 backdrop-blur"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="flex items-center gap-2 font-semibold text-white">
                  <Gauge class="h-4 w-4 text-cyan-200" aria-hidden="true" />
                  管道流量
                </span>
                <span class="font-mono text-cyan-100">{{ flowSnapshot.readings.INLET.flow }} m³/h</span>
              </div>
              <div class="mt-3 grid grid-cols-2 gap-2">
                <div class="rounded-md border border-emerald-300/20 bg-emerald-400/10 p-2">
                  <p class="text-slate-400">左支管</p>
                  <p class="mt-1 font-mono text-sm font-semibold text-emerald-100">{{ branchAFlow }} m³/h</p>
                </div>
                <div class="rounded-md border border-amber-300/20 bg-amber-400/10 p-2">
                  <p class="text-slate-400">右支管</p>
                  <p class="mt-1 font-mono text-sm font-semibold text-amber-100">{{ branchBFlow }} m³/h</p>
                </div>
              </div>
            </div>
            <div
              class="absolute bottom-4 left-4 z-[500] max-w-[calc(100%-2rem)] rounded-lg border border-white/10 bg-ink/85 px-3 py-2 text-xs text-slate-300 backdrop-blur"
            >
              bbox {{ INITIAL_QUERY.minX }},{{ INITIAL_QUERY.minY }} -> {{ INITIAL_QUERY.maxX }},{{ INITIAL_QUERY.maxY }}
              <span v-if="bboxTraceId" class="ml-2 font-mono text-blue-100">TraceId {{ bboxTraceId }}</span>
            </div>
          </div>
        </section>

        <aside
          class="glass-panel order-3 flex max-h-none flex-col overflow-hidden xl:max-h-[calc(100vh-9rem)]"
          :class="{ 'hidden xl:flex': !isDrawerOpen && !selectedObject }"
        >
          <div class="flex items-center justify-between gap-3 border-b border-white/10 p-4">
            <div>
              <p class="text-sm font-semibold text-white">对象详情抽屉</p>
              <p class="mt-1 text-xs text-slate-400">对象链主键与空间边界</p>
            </div>
            <button class="icon-button focus-ring" type="button" aria-label="关闭详情抽屉" @click="closeDrawer">
              <PanelRightClose class="h-4 w-4" />
            </button>
          </div>

          <div v-if="selectedObject" class="min-h-0 flex-1 overflow-y-auto p-4">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="font-display text-xl font-semibold text-white">
                  {{ selectedObject.objectName || selectedObject.objectId }}
                </p>
                <p class="mt-1 font-mono text-xs text-blue-100">
                  {{ selectedObject.objectType }} / {{ selectedObject.objectId }}
                </p>
              </div>
              <button class="icon-button focus-ring" type="button" aria-label="定位到当前对象" @click="focusSelectedOnMap">
                <Crosshair class="h-4 w-4" />
              </button>
            </div>

            <dl class="mt-5 grid grid-cols-3 gap-2 text-xs">
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-3">
                <dt class="text-slate-400">region</dt>
                <dd class="mt-1 truncate font-mono text-blue-100">{{ selectedObject.regionId || '-' }}</dd>
              </div>
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-3">
                <dt class="text-slate-400">related</dt>
                <dd class="mt-1 font-mono text-white">{{ selectedRelatedEntries.length }}</dd>
              </div>
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-3">
                <dt class="text-slate-400">attrs</dt>
                <dd class="mt-1 font-mono text-white">{{ selectedAttributeEntries.length }}</dd>
              </div>
            </dl>

            <nav class="mt-4 grid grid-cols-5 gap-1 rounded-lg border border-white/10 bg-black/10 p-1" aria-label="资产详情标签页">
              <button
                v-for="tab in detailTabConfigs"
                :key="tab.key"
                class="focus-ring flex min-h-9 cursor-pointer items-center justify-center rounded-md px-1 text-xs font-semibold transition-colors duration-200"
                :class="activeDetailTab === tab.key ? 'bg-primary text-white' : 'text-slate-400 hover:bg-white/10 hover:text-slate-100'"
                type="button"
                :aria-selected="activeDetailTab === tab.key"
                @click="setActiveDetailTab(tab.key)"
              >
                <component :is="tab.icon" class="mr-1 hidden h-3.5 w-3.5 sm:inline-block" aria-hidden="true" />
                {{ tab.label }}
              </button>
            </nav>

            <div
              v-if="activeDetailTabState.status === 'loading' || activeDetailTabState.status === 'error' || activeDetailTabState.status === 'empty'"
              class="mt-4 rounded-lg border px-3 py-2 text-sm"
              :class="activeDetailTabState.status === 'error' ? 'border-rose-400/20 bg-rose-400/10 text-rose-100' : 'border-white/10 bg-white/[0.045] text-slate-300'"
              :role="activeDetailTabState.status === 'error' ? 'alert' : 'status'"
            >
              <div class="flex items-start gap-2">
                <Loader2 v-if="activeDetailTabState.status === 'loading'" class="mt-0.5 h-4 w-4 shrink-0 animate-spin text-blue-200" />
                <AlertTriangle v-else-if="activeDetailTabState.status === 'error'" class="mt-0.5 h-4 w-4 shrink-0" />
                <DatabaseZap v-else class="mt-0.5 h-4 w-4 shrink-0 text-slate-300" />
                <div class="min-w-0">
                  <p>{{ activeDetailTabState.message }}</p>
                  <p v-if="activeDetailTabState.traceId" class="mt-1 truncate font-mono text-xs text-slate-400">
                    TraceId {{ activeDetailTabState.traceId }}
                  </p>
                </div>
              </div>
            </div>

            <div class="mt-4 flex justify-end">
              <button class="secondary-button focus-ring min-h-8 px-3 text-xs" type="button" @click="refreshActiveDetailTab">
                <RefreshCcw class="h-3.5 w-3.5" />
                刷新当前标签
              </button>
            </div>

            <section v-if="activeDetailTab === 'profile'" class="mt-4 space-y-4">
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-sm font-semibold text-white">基础档案</p>
                <dl class="mt-3 grid gap-2 text-sm">
                  <div class="flex justify-between gap-3 rounded-md border border-white/10 bg-black/10 px-3 py-2">
                    <dt class="text-slate-400">status</dt>
                    <dd class="truncate text-right text-slate-100">{{ detailContext.profile.data?.status || '-' }}</dd>
                  </div>
                  <div class="flex justify-between gap-3 rounded-md border border-white/10 bg-black/10 px-3 py-2">
                    <dt class="text-slate-400">authority</dt>
                    <dd class="font-mono text-slate-100">{{ selectedObject.authoritySrid }}</dd>
                  </div>
                  <div class="flex justify-between gap-3 rounded-md border border-white/10 bg-black/10 px-3 py-2">
                    <dt class="text-slate-400">display</dt>
                    <dd class="font-mono text-slate-100">{{ selectedObject.displaySrid }}</dd>
                  </div>
                </dl>
              </div>

              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-sm font-semibold text-white">空间边界</p>
                <p class="mt-2 font-mono text-xs text-blue-100">
                  anchor x {{ formatNumber(selectedObject.anchorPoint?.x) }} / y {{ formatNumber(selectedObject.anchorPoint?.y) }}
                </p>
                <div class="mt-3 grid grid-cols-2 gap-2 font-mono text-xs text-slate-300">
                  <span>minX {{ formatNumber(selectedObject.bbox?.minX) }}</span>
                  <span>minY {{ formatNumber(selectedObject.bbox?.minY) }}</span>
                  <span>maxX {{ formatNumber(selectedObject.bbox?.maxX) }}</span>
                  <span>maxY {{ formatNumber(selectedObject.bbox?.maxY) }}</span>
                </div>
              </div>

              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-sm font-semibold text-white">对象链</p>
                <div v-if="selectedProfileRelatedEntries.length || selectedRelatedEntries.length" class="mt-3 space-y-2">
                  <div
                    v-for="[key, values] in selectedProfileRelatedEntries.length ? selectedProfileRelatedEntries : selectedRelatedEntries"
                    :key="key"
                    class="rounded-md border border-white/10 bg-black/10 px-3 py-2"
                  >
                    <p class="font-mono text-xs text-blue-100">{{ key }}</p>
                    <div class="mt-2 flex flex-wrap gap-2">
                      <button
                        v-for="value in values"
                        :key="`${key}-${value}`"
                        class="focus-ring cursor-pointer rounded-md border border-white/10 bg-white/[0.045] px-2 py-1 font-mono text-[11px] text-slate-200 transition-colors duration-200 hover:border-amber-300/40 hover:bg-amber-400/10"
                        type="button"
                        :disabled="!relatedKeyToObjectType(key)"
                        @click="relatedKeyToObjectType(key) && openRelatedAsset(relatedKeyToObjectType(key)!, value)"
                      >
                        {{ value }}
                      </button>
                    </div>
                  </div>
                </div>
                <p v-else class="mt-2 text-sm text-slate-400">暂无关联对象</p>
              </div>

              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-sm font-semibold text-white">扩展属性</p>
                <div v-if="selectedProfileAttributeEntries.length || selectedAttributeEntries.length" class="mt-3 space-y-2">
                  <div
                    v-for="[key, value] in selectedProfileAttributeEntries.length ? selectedProfileAttributeEntries : selectedAttributeEntries"
                    :key="key"
                    class="flex justify-between gap-3 rounded-md border border-white/10 bg-black/10 px-3 py-2 text-xs"
                  >
                    <span class="font-mono text-slate-400">{{ key }}</span>
                    <span class="min-w-0 truncate text-right text-slate-200">{{ formatAttribute(value) }}</span>
                  </div>
                </div>
                <p v-else class="mt-2 text-sm text-slate-400">暂无扩展属性</p>
              </div>
            </section>

            <section v-else-if="activeDetailTab === 'devices'" class="mt-4 space-y-3">
              <button
                v-for="device in detailContext.devices.data"
                :key="device.deviceId"
                class="focus-ring w-full cursor-pointer rounded-lg border border-white/10 bg-white/[0.045] p-3 text-left transition-colors duration-200 hover:border-blue-300/40 hover:bg-blue-400/10"
                type="button"
                @click="openRelatedAsset('DEVICE', device.deviceId)"
              >
                <span class="flex items-start justify-between gap-3">
                  <span class="min-w-0">
                    <span class="block truncate text-sm font-semibold text-white">{{ device.deviceName || device.deviceId }}</span>
                    <span class="mt-1 block font-mono text-[11px] text-blue-100">{{ device.deviceId }} / {{ device.protocolType }}</span>
                  </span>
                  <span class="shrink-0 rounded-md border px-2 py-1 text-[11px]" :class="statusToneClass(device.onlineStatus)">
                    {{ device.onlineStatus }}
                  </span>
                </span>
                <span class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <span>heartbeat <span class="text-slate-100">{{ formatDateTime(device.lastHeartbeat) }}</span></span>
                  <span>buffer <span class="text-slate-100">{{ device.bufferLevel ?? '-' }}</span></span>
                  <span>facility <span class="text-slate-100">{{ device.facilityId || '-' }}</span></span>
                  <span>calibration <span class="text-slate-100">{{ device.calibrationExpired ? 'EXPIRED' : formatDateTime(device.calibrationDueAt) }}</span></span>
                </span>
              </button>
            </section>

            <section v-else-if="activeDetailTab === 'alerts'" class="mt-4 space-y-3">
              <div
                v-for="alert in detailContext.alerts.data"
                :key="alert.alertId"
                class="rounded-lg border border-white/10 bg-white/[0.045] p-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-white">{{ alert.ruleCode }}</p>
                    <p class="mt-1 font-mono text-[11px] text-blue-100">{{ alert.alertId }}</p>
                  </div>
                  <span class="rounded-md border px-2 py-1 text-[11px]" :class="statusToneClass(alert.severity)">
                    {{ alert.severity || '-' }}
                  </span>
                </div>
                <dl class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <div>device <span class="text-slate-100">{{ alert.deviceId || '-' }}</span></div>
                  <div>decision <span class="text-slate-100">{{ alert.decision || '-' }}</span></div>
                  <div>metric <span class="text-slate-100">{{ alert.metricCode || '-' }} {{ alert.metricValue || '' }}</span></div>
                  <div>event <span class="text-slate-100">{{ formatDateTime(alert.eventTime) }}</span></div>
                  <div class="col-span-2">case <span class="font-mono text-slate-100">{{ alert.caseId || '-' }}</span></div>
                </dl>
              </div>
            </section>

            <section v-else-if="activeDetailTab === 'incidents'" class="mt-4 space-y-3">
              <div
                v-for="incident in detailContext.incidents.data"
                :key="incident.incidentId"
                class="rounded-lg border border-white/10 bg-white/[0.045] p-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-white">{{ incident.title || incident.incidentId }}</p>
                    <p class="mt-1 font-mono text-[11px] text-blue-100">{{ incident.incidentId }}</p>
                  </div>
                  <span class="rounded-md border px-2 py-1 text-[11px]" :class="statusToneClass(incident.status)">
                    {{ incident.status || '-' }}
                  </span>
                </div>
                <dl class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <div>severity <span class="text-slate-100">{{ incident.severity || '-' }}</span></div>
                  <div>device <span class="text-slate-100">{{ incident.deviceId || '-' }}</span></div>
                  <div>alert <span class="font-mono text-slate-100">{{ incident.sourceAlertId || '-' }}</span></div>
                  <div>case <span class="font-mono text-slate-100">{{ incident.sourceCaseId || '-' }}</span></div>
                  <div class="col-span-2">created <span class="text-slate-100">{{ formatDateTime(incident.createdAt) }}</span></div>
                </dl>
              </div>
            </section>

            <section v-else class="mt-4 space-y-3">
              <div
                v-for="workorder in detailContext.workorders.data"
                :key="workorder.workOrderId"
                class="rounded-lg border border-white/10 bg-white/[0.045] p-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-white">{{ workorder.description || workorder.workOrderId }}</p>
                    <p class="mt-1 font-mono text-[11px] text-blue-100">{{ workorder.workOrderId }} / {{ workorder.incidentId }}</p>
                  </div>
                  <span class="rounded-md border px-2 py-1 text-[11px]" :class="statusToneClass(workorder.status)">
                    {{ workorder.status || '-' }}
                  </span>
                </div>
                <dl class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <div>priority <span class="text-slate-100">{{ workorder.priority || '-' }}</span></div>
                  <div>type <span class="text-slate-100">{{ workorder.workOrderType || '-' }}</span></div>
                  <div>assignee <span class="text-slate-100">{{ workorder.assignee || '-' }}</span></div>
                  <div>sla <span class="text-slate-100">{{ formatDateTime(workorder.slaDueAt) }}</span></div>
                  <div class="col-span-2">updated <span class="text-slate-100">{{ formatDateTime(workorder.updatedAt) }}</span></div>
                </dl>
              </div>
            </section>

            <p v-if="detailTraceId" class="mt-4 truncate font-mono text-xs text-blue-100">
              Detail TraceId {{ detailTraceId }}
            </p>
          </div>

          <div v-else class="flex flex-1 flex-col items-center justify-center p-6 text-center text-sm text-slate-400">
            <Search class="mb-3 h-8 w-8 text-blue-200" aria-hidden="true" />
            点击地图对象或执行点查后打开详情。
          </div>
        </aside>
      </section>

      <section class="glass-panel grid gap-3 p-4 md:grid-cols-3">
        <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-white">
            <DatabaseZap class="h-4 w-4 text-blue-200" aria-hidden="true" />
            告警联动插槽
          </div>
          <p class="mt-2 text-sm text-slate-400">待 F-11 接入 WebSocket 地图刷新事件。</p>
        </div>
        <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-white">
            <Route class="h-4 w-4 text-amber-200" aria-hidden="true" />
            工单联动插槽
          </div>
          <p class="mt-2 text-sm text-slate-400">对象详情将承接工单与事件链路入口。</p>
        </div>
        <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-white">
            <Layers3 class="h-4 w-4 text-blue-200" aria-hidden="true" />
            3D 占位与降级
          </div>
          <p class="mt-2 text-sm text-slate-400">一期 2D 为主入口，3D 仅做增强插槽；不可用时保留上下文回到本页。</p>
          <RouterLink class="secondary-button focus-ring mt-3 w-full" :to="{ path: '/mgmt/gis/3d', query: gis3dQuery }">
            <Layers3 class="h-4 w-4" />
            进入 3D 占位
          </RouterLink>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
:deep(.leaflet-container) {
  height: 100%;
  width: 100%;
  background:
    radial-gradient(circle at 20% 15%, rgba(37, 99, 235, 0.18), transparent 26%),
    radial-gradient(circle at 80% 80%, rgba(245, 158, 11, 0.12), transparent 28%),
    linear-gradient(rgba(59, 130, 246, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.12) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    #090d18;
  background-size:
    auto,
    auto,
    64px 64px,
    64px 64px,
    16px 16px,
    16px 16px;
  color: #e2e8f0;
  font-family: inherit;
}

:deep(.gis-grid-tile) {
  width: 256px;
  height: 256px;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.12) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size:
    64px 64px,
    64px 64px,
    16px 16px,
    16px 16px;
}

:deep(.leaflet-control-zoom a),
:deep(.leaflet-control-attribution) {
  border-color: rgba(255, 255, 255, 0.14) !important;
  background: rgba(11, 11, 16, 0.85) !important;
  color: #dbeafe !important;
}

:deep(.leaflet-tooltip) {
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 8px;
  background: rgba(11, 11, 16, 0.88);
  color: #f8fafc;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.3);
}
</style>
