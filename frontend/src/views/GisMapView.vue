<script setup lang="ts">
import 'leaflet/dist/leaflet.css';

import L, {
  type CircleMarker,
  type LatLngBoundsExpression,
  type LatLngExpression,
  type LeafletMouseEvent,
  type Map as LeafletMap,
  type Polyline
} from 'leaflet';
import {
  AlertTriangle,
  ArrowLeft,
  Boxes,
  CircuitBoard,
  Crosshair,
  DatabaseZap,
  Layers3,
  Loader2,
  MapPinned,
  MousePointer2,
  PanelRightClose,
  RadioTower,
  RefreshCcw,
  Route,
  Satellite,
  Search,
  X
} from 'lucide-vue-next';
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue';
import { RouterLink } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getGisObjectDetail, pickGisObject, queryGisBbox } from '@/services/gis';
import type {
  GisBboxQuery,
  GisObjectPickResponse,
  GisObjectRecordResponse,
  GisObjectType
} from '@/types/api';

type GisLayerKey = GisObjectType;
type ParsedGeometry =
  | { kind: 'POINT'; point: LatLngExpression }
  | { kind: 'LINESTRING'; points: LatLngExpression[] };
type PanelState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

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

const layerConfigs: Array<{
  key: GisLayerKey;
  label: string;
  shortLabel: string;
  color: string;
  icon: typeof CircuitBoard;
}> = [
  { key: 'NODE', label: '节点', shortLabel: 'NODE', color: '#38BDF8', icon: CircuitBoard },
  { key: 'SEGMENT', label: '管段', shortLabel: 'SEGMENT', color: '#F59E0B', icon: Route },
  { key: 'FACILITY', label: '设施', shortLabel: 'FACILITY', color: '#22C55E', icon: Boxes },
  { key: 'DEVICE', label: '设备', shortLabel: 'DEVICE', color: '#A78BFA', icon: RadioTower }
];

const mapElement = ref<HTMLDivElement | null>(null);
const map = shallowRef<LeafletMap | null>(null);
const vectorLayers = new Map<string, CircleMarker | Polyline>();

const objects = ref<GisObjectRecordResponse[]>([]);
const selectedObject = ref<GisObjectRecordResponse | null>(null);
const detailTraceId = ref('');
const bboxTraceId = ref('');
const pickTraceId = ref('');
const state = ref<PanelState>('idle');
const message = ref('等待加载 GIS 对象');
const pickMessage = ref('点击地图可执行对象点查');
const total = ref(0);
const lastPick = ref<GisObjectPickResponse | null>(null);
const isDrawerOpen = ref(false);

const layerVisibility = reactive<Record<GisLayerKey, boolean>>({
  NODE: true,
  SEGMENT: true,
  FACILITY: true,
  DEVICE: true
});

const visibleObjects = computed(() =>
  objects.value.filter((object) => isLayerVisible(object.objectType))
);
const drawableCount = computed(() => objects.value.filter((object) => parseWkt(object.geometry2d)).length);
const selectedRelatedEntries = computed(() =>
  selectedObject.value ? Object.entries(selectedObject.value.relatedObjectIds ?? {}) : []
);
const selectedAttributeEntries = computed(() =>
  selectedObject.value ? Object.entries(selectedObject.value.attributes ?? {}) : []
);

function objectKey(object: GisObjectRecordResponse) {
  return `${object.objectType}:${object.objectId}`;
}

function isLayerVisible(objectType: string) {
  return layerVisibility[objectType as GisLayerKey] ?? false;
}

function getLayerConfig(objectType: string) {
  return layerConfigs.find((item) => item.key === objectType) ?? layerConfigs[0];
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
  map.value.on('click', handleMapClick);
}

function clearVectorLayers() {
  vectorLayers.forEach((layer) => {
    layer.remove();
  });
  vectorLayers.clear();
}

function renderObjects(fit = false) {
  if (!map.value) {
    return;
  }

  clearVectorLayers();
  const bounds: LatLngExpression[] = [];

  visibleObjects.value.forEach((object) => {
    const geometry = parseWkt(object.geometry2d);
    if (!geometry) {
      return;
    }

    const config = getLayerConfig(object.objectType);
    const isSelected = selectedObject.value
      ? objectKey(object) === objectKey(selectedObject.value)
      : false;
    const color = isSelected ? '#F59E0B' : config.color;
    let layer: CircleMarker | Polyline;

    if (geometry.kind === 'POINT') {
      layer = L.circleMarker(geometry.point, {
        radius: isSelected ? 8 : 6,
        color,
        fillColor: color,
        fillOpacity: isSelected ? 0.95 : 0.72,
        weight: isSelected ? 3 : 2
      });
      bounds.push(geometry.point);
    } else {
      layer = L.polyline(geometry.points, {
        color,
        weight: isSelected ? 5 : 3,
        opacity: isSelected ? 0.95 : 0.78
      });
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
    layer.addTo(map.value as LeafletMap);
    vectorLayers.set(objectKey(object), layer);
  });

  if (fit && bounds.length > 0) {
    map.value.fitBounds(bounds as LatLngBoundsExpression, { padding: [36, 36], maxZoom: 17 });
  }
}

async function loadBboxObjects() {
  state.value = 'loading';
  message.value = '正在读取 bbox 范围内 GIS 对象';
  try {
    const response = await queryGisBbox(INITIAL_QUERY);
    const page = response.data;
    objects.value = page?.items ?? [];
    total.value = page?.total ?? 0;
    bboxTraceId.value = response.traceId;
    state.value = objects.value.length ? 'ready' : 'empty';
    message.value = objects.value.length ? 'GIS 对象已加载' : '当前 bbox 未返回 GIS 对象';
    await nextTick();
    renderObjects(true);
  } catch (error) {
    objects.value = [];
    total.value = 0;
    state.value = 'error';
    if (error instanceof ApiClientError) {
      bboxTraceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = 'GIS 对象读取失败';
  }
}

async function selectObject(object: GisObjectRecordResponse) {
  selectedObject.value = object;
  isDrawerOpen.value = true;
  renderObjects(false);

  try {
    const response = await getGisObjectDetail(object.objectType, object.objectId, INITIAL_QUERY.displaySrid);
    selectedObject.value = response.data ?? object;
    detailTraceId.value = response.traceId;
    renderObjects(false);
  } catch (error) {
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '对象详情读取失败';
  }
}

async function handleMapClick(event: LeafletMouseEvent) {
  pickMessage.value = '正在执行对象点查';
  lastPick.value = null;
  try {
    const response = await pickGisObject({
      x: event.latlng.lng,
      y: event.latlng.lat,
      authoritySrid: INITIAL_QUERY.authoritySrid,
      displaySrid: INITIAL_QUERY.displaySrid,
      objectTypes: layerConfigs.filter((item) => layerVisibility[item.key]).map((item) => item.key),
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

function toggleLayer(layerKey: GisLayerKey) {
  layerVisibility[layerKey] = !layerVisibility[layerKey];
  renderObjects(false);
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

onMounted(async () => {
  await nextTick();
  initializeMap();
  await loadBboxObjects();
});

onBeforeUnmount(() => {
  if (map.value) {
    map.value.off('click', handleMapClick);
    map.value.remove();
    map.value = null;
  }
  clearVectorLayers();
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
            <p class="text-xs text-slate-400">可见对象</p>
            <p class="mt-1 font-display text-xl font-semibold text-emerald-200">{{ visibleObjects.length }}</p>
          </div>
          <button class="secondary-button focus-ring" type="button" @click="loadBboxObjects">
            <RefreshCcw class="h-4 w-4" />
            刷新
          </button>
        </div>
      </header>

      <section class="grid min-h-[calc(100vh-9rem)] gap-4 xl:grid-cols-[280px_minmax(0,1fr)_360px]">
        <aside class="glass-panel order-2 flex flex-col gap-4 p-4 xl:order-1">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-white">图层插槽</p>
              <p class="mt-1 text-xs text-slate-400">F-06 将接入透明度、图例和持久化</p>
            </div>
            <Layers3 class="h-5 w-5 text-blue-200" aria-hidden="true" />
          </div>

          <div class="space-y-3">
            <button
              v-for="layer in layerConfigs"
              :key="layer.key"
              class="flex min-h-[56px] w-full cursor-pointer items-center justify-between gap-3 rounded-lg border px-3 py-2 text-left transition-colors duration-200 focus-ring"
              :class="layerVisibility[layer.key] ? 'border-blue-400/25 bg-blue-400/10 text-blue-100' : 'border-white/10 bg-white/[0.045] text-slate-400'"
              type="button"
              :aria-pressed="layerVisibility[layer.key]"
              @click="toggleLayer(layer.key)"
            >
              <span class="flex min-w-0 items-center gap-3">
                <span
                  class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-white/10"
                  :style="{ color: layer.color }"
                  aria-hidden="true"
                >
                  <component :is="layer.icon" class="h-4 w-4" />
                </span>
                <span class="min-w-0">
                  <span class="block text-sm font-semibold text-white">{{ layer.label }}</span>
                  <span class="block font-mono text-xs">{{ layer.shortLabel }}</span>
                </span>
              </span>
              <span
                class="h-2.5 w-2.5 shrink-0 rounded-full"
                :style="{ backgroundColor: layerVisibility[layer.key] ? layer.color : '#475569' }"
              />
            </button>
          </div>

          <div class="rounded-lg border border-white/10 bg-white/[0.045] p-3 text-xs leading-5 text-slate-300">
            <div class="flex gap-2">
              <Satellite class="mt-0.5 h-4 w-4 shrink-0 text-amber-200" aria-hidden="true" />
              <p>当前不接公网瓦片，地图使用本地深色网格底图和后端 GIS 矢量对象。</p>
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

            <dl class="mt-5 grid gap-3 text-sm">
              <div class="flex justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2">
                <dt class="text-slate-400">regionId</dt>
                <dd class="truncate text-right text-slate-100">{{ selectedObject.regionId }}</dd>
              </div>
              <div class="flex justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2">
                <dt class="text-slate-400">authority</dt>
                <dd class="font-mono text-slate-100">{{ selectedObject.authoritySrid }}</dd>
              </div>
              <div class="flex justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2">
                <dt class="text-slate-400">display</dt>
                <dd class="font-mono text-slate-100">{{ selectedObject.displaySrid }}</dd>
              </div>
            </dl>

            <section class="mt-5 rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <p class="text-sm font-semibold text-white">anchorPoint</p>
              <p class="mt-2 font-mono text-xs text-blue-100">
                x {{ formatNumber(selectedObject.anchorPoint?.x) }} / y {{ formatNumber(selectedObject.anchorPoint?.y) }}
              </p>
            </section>

            <section class="mt-4 rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <p class="text-sm font-semibold text-white">bbox</p>
              <div class="mt-2 grid grid-cols-2 gap-2 font-mono text-xs text-slate-300">
                <span>minX {{ formatNumber(selectedObject.bbox?.minX) }}</span>
                <span>minY {{ formatNumber(selectedObject.bbox?.minY) }}</span>
                <span>maxX {{ formatNumber(selectedObject.bbox?.maxX) }}</span>
                <span>maxY {{ formatNumber(selectedObject.bbox?.maxY) }}</span>
              </div>
            </section>

            <section class="mt-4 rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <p class="text-sm font-semibold text-white">relatedObjectIds</p>
              <div v-if="selectedRelatedEntries.length" class="mt-3 space-y-2">
                <div
                  v-for="[key, values] in selectedRelatedEntries"
                  :key="key"
                  class="rounded-md border border-white/10 bg-black/10 px-3 py-2"
                >
                  <p class="font-mono text-xs text-blue-100">{{ key }}</p>
                  <p class="mt-1 break-all text-xs text-slate-300">{{ values.join(' / ') }}</p>
                </div>
              </div>
              <p v-else class="mt-2 text-sm text-slate-400">暂无关联对象</p>
            </section>

            <section class="mt-4 rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <p class="text-sm font-semibold text-white">attributes</p>
              <div v-if="selectedAttributeEntries.length" class="mt-3 space-y-2">
                <div
                  v-for="[key, value] in selectedAttributeEntries"
                  :key="key"
                  class="flex justify-between gap-3 rounded-md border border-white/10 bg-black/10 px-3 py-2 text-xs"
                >
                  <span class="font-mono text-slate-400">{{ key }}</span>
                  <span class="min-w-0 truncate text-right text-slate-200">{{ formatAttribute(value) }}</span>
                </div>
              </div>
              <p v-else class="mt-2 text-sm text-slate-400">暂无扩展属性</p>
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
            <X class="h-4 w-4 text-slate-300" aria-hidden="true" />
            3D 降级口径
          </div>
          <p class="mt-2 text-sm text-slate-400">一期 2D 为主入口，3D 不影响本页主业务。</p>
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
