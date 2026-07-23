<script setup lang="ts">
import { Activity, BellRing, ClipboardList, DatabaseZap, Loader2, ShieldCheck } from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import MapControlPanel from '@/components/nationalOverview/MapControlPanel.vue';
import HunanMapControlPanel from '@/components/nationalOverview/HunanMapControlPanel.vue';
import OverviewStatistics from '@/components/nationalOverview/OverviewStatistics.vue';
import PlatformQuickEntry from '@/components/nationalOverview/PlatformQuickEntry.vue';
import ProvinceDetailPanel from '@/components/nationalOverview/ProvinceDetailPanel.vue';
import RiskLegend from '@/components/nationalOverview/RiskLegend.vue';
import { DEFAULT_LAYER_STATE } from '@/features/nationalMap/chinaMapConfig';
import { nationalOverviewDataAdapter } from '@/features/nationalMap/nationalOverviewDataAdapter';
import type {
  CityNodeData,
  FlyLineConfig,
  GisContextMarkerData,
  NationalMapLayerState,
  NationalSummary,
  ProvinceHealthData
} from '@/features/nationalMap/chinaMapTypes';
import type { HunanMapLayerState, HunanStationData } from '@/features/hunanMap/hunanMapTypes';
import { checkPermissions } from '@/services/permissions';
import { getGisObjectDetail } from '@/services/gis';
import { getMonitoringStations } from '@/services/masterData';
import { getPlatforms } from '@/services/portal';
import {
  loadAuthorizedBusinessOverview,
  type AuthorizedBusinessOverview,
  type AuthorizedModuleSnapshot
} from '@/services/nationalOverviewBusiness';
import { useAuthStore } from '@/stores/auth';
import type { PlatformBoundary } from '@/types/api';

type China3DMapApi = {
  resetView(): void;
  focusProvince(adcode: string): void;
  retry(): void;
};

type HunanTerrainMapApi = {
  resetView(): void;
  retry(): void;
};

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const China3DMap = defineAsyncComponent(() => import('@/components/nationalOverview/China3DMap.vue'));
const HunanTerrainMap = defineAsyncComponent(() => import('@/components/nationalOverview/HunanTerrainMap.vue'));
const { user, status } = storeToRefs(authStore);

const mapRef = ref<China3DMapApi | null>(null);
const hunanMapRef = ref<HunanTerrainMapApi | null>(null);
const summary = ref<NationalSummary | null>(null);
const provinces = ref<ProvinceHealthData[]>([]);
const flyLines = ref<FlyLineConfig[]>([]);
const cities = ref<CityNodeData[]>([]);
const contextObject = ref<GisContextMarkerData | null>(null);
const hunanStation = ref<HunanStationData | null>(null);
const platforms = ref<PlatformBoundary[]>([]);
const business = ref<AuthorizedBusinessOverview | null>(null);
const selectedProvince = ref<ProvinceHealthData | null>(null);
const selectedCity = ref<CityNodeData | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const loadMessage = ref('正在加载全国态势数据');
const mapMessage = ref('');
const autoRotate = ref(true);
const layers = reactive<NationalMapLayerState>({ ...DEFAULT_LAYER_STATE });
const hunanLayers = reactive<HunanMapLayerState>({ boundaries: true, labels: true, station: true });
const terrainExaggeration = ref(2);

const routeContext = computed(() => {
  const entries = ['objectType', 'objectId', 'bbox', 'minX', 'minY', 'maxX', 'maxY', 'displaySrid']
    .map((key) => [key, route.query[key]] as const)
    .filter((entry): entry is readonly [string, string] => typeof entry[1] === 'string' && Boolean(entry[1]));
  return entries;
});

const mapMode = computed<'national' | 'hunan'>(() =>
  route.query.view === 'hunan' && route.query.province === '430000' ? 'hunan' : 'national'
);

const canEnterGis = computed(() => checkPermissions(
  user.value,
  ['ENTRY:MGMT', 'MENU:ASSET:READ'],
  '二维一张图'
));

const latestAlert = computed(() => business.value?.alerts.items[0] ?? null);
const latestIncident = computed(() => business.value?.incidents.items[0] ?? null);
const latestWorkOrder = computed(() => business.value?.workOrders.items[0] ?? null);

async function refreshAll() {
  if (refreshing.value) return;
  refreshing.value = true;
  loading.value = summary.value === null;
  loadMessage.value = '正在同步全国态势与授权范围数据';

  await authStore.ensureCurrentUser();
  const nationalTask = Promise.all([
    nationalOverviewDataAdapter.loadNationalSummary(),
    nationalOverviewDataAdapter.loadProvinceStatistics(),
    nationalOverviewDataAdapter.loadNetworkConnections(),
    nationalOverviewDataAdapter.loadCityNodes()
  ]);
  const [nationalResult, platformResult, businessResult, contextObjectResult, stationResult] = await Promise.allSettled([
    nationalTask,
    getPlatforms(),
    loadAuthorizedBusinessOverview(authStore.user),
    loadRouteContextObject(),
    loadAuthorizedHunanStation()
  ]);

  if (nationalResult.status === 'fulfilled') {
    [summary.value, provinces.value, flyLines.value, cities.value] = nationalResult.value;
    if (mapMode.value === 'hunan') {
      selectedProvince.value = provinces.value.find((province) => province.adcode === '430000') ?? null;
    }
  } else {
    loadMessage.value = nationalResult.reason instanceof Error
      ? nationalResult.reason.message
      : '全国态势数据加载失败';
  }

  if (platformResult.status === 'fulfilled') {
    platforms.value = platformResult.value.data ?? [];
  }
  if (businessResult.status === 'fulfilled') {
    business.value = businessResult.value;
  }
  contextObject.value = contextObjectResult.status === 'fulfilled'
    ? contextObjectResult.value
    : null;
  hunanStation.value = stationResult.status === 'fulfilled' ? stationResult.value : null;

  loading.value = false;
  refreshing.value = false;
}

async function loadAuthorizedHunanStation(): Promise<HunanStationData | null> {
  if (!canEnterGis.value.allowed) return null;
  const response = await getMonitoringStations({ provinceAdcode: '430000', cityAdcode: '430200', status: 'ACTIVE', pageSize: 10 });
  const record = response.data?.items.find((item) => item.objectId === 'ST-HUT-ZZ-001') ?? null;
  if (!record) return null;
  const longitude = Number(record.attributes.longitude);
  const latitude = Number(record.attributes.latitude);
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null;
  return {
    stationId: record.objectId,
    stationName: record.objectName,
    coordinate: [longitude, latitude],
    status: record.status
  };
}

async function loadRouteContextObject(): Promise<GisContextMarkerData | null> {
  const objectType = typeof route.query.objectType === 'string' ? route.query.objectType : '';
  const objectId = typeof route.query.objectId === 'string' ? route.query.objectId : '';
  if (!objectType || !objectId || !canEnterGis.value.allowed) return null;

  const response = await getGisObjectDetail(objectType, objectId, 'EPSG:4490');
  const object = response.data;
  if (!object) return null;
  const longitude = Number(object.anchorPoint?.x);
  const latitude = Number(object.anchorPoint?.y);
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null;

  return {
    objectType: object.objectType,
    objectId: object.objectId,
    objectName: object.objectName,
    coordinate: [longitude, latitude],
    sourceSrid: 'EPSG:4490'
  };
}

function selectProvince(province: ProvinceHealthData) {
  selectedProvince.value = province;
  selectedCity.value = null;
}

function selectCity(city: CityNodeData) {
  selectedCity.value = city;
}

function resetMap() {
  if (mapMode.value === 'hunan') {
    hunanMapRef.value?.resetView();
    return;
  }
  selectedProvince.value = null;
  selectedCity.value = null;
  mapRef.value?.resetView();
}

function toggleHunanLayer(key: keyof HunanMapLayerState) {
  hunanLayers[key] = !hunanLayers[key];
}

async function enterHunanMap() {
  selectedProvince.value = provinces.value.find((province) => province.adcode === '430000') ?? selectedProvince.value;
  await router.push({
    path: '/home',
    query: { ...route.query, view: 'hunan', province: '430000' }
  });
}

async function returnToNationalMap() {
  const query = { ...route.query };
  delete query.view;
  delete query.province;
  await router.push({ path: '/home', query });
}

async function openHunanStation(station: HunanStationData) {
  if (!canEnterGis.value.allowed) return;
  await router.push({
    path: '/mgmt/gis',
    query: {
      objectType: 'STATION',
      objectId: station.stationId,
      displaySrid: 'EPSG:4490'
    }
  });
}

function toggleLayer(key: keyof NationalMapLayerState) {
  layers[key] = !layers[key];
}

function formatDate(value: string | null | undefined) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function moduleStatusLabel(snapshot: AuthorizedModuleSnapshot<unknown> | undefined) {
  if (!snapshot) return '加载中';
  if (snapshot.status === 'locked') return '无权限';
  if (snapshot.status === 'error') return '加载失败';
  if (snapshot.status === 'empty') return '暂无数据';
  return snapshot.total?.toLocaleString() ?? '-';
}

watch(status, (value) => {
  if (value === 'anonymous' && route.path === '/home') {
    void router.replace('/portal');
  }
});

watch(mapMode, (value) => {
  if (value === 'hunan' && provinces.value.length) {
    selectedProvince.value = provinces.value.find((province) => province.adcode === '430000') ?? null;
    selectedCity.value = null;
  }
});

onMounted(() => {
  authStore.bindSessionEvents();
  void refreshAll();
});
</script>

<template>
  <main class="app-viewport overflow-x-hidden bg-[#050a12] text-slate-100">
    <OverviewStatistics :summary="summary" :loading="loading" />

    <div class="app-frame grid xl:h-[calc(100svh-17.75rem)] xl:min-h-[420px] xl:grid-cols-[280px_minmax(0,1fr)_310px]">
      <aside class="order-2 border-t border-white/10 bg-[#080d16] p-4 xl:order-1 xl:min-h-0 xl:overflow-y-auto xl:border-r xl:border-t-0">
        <PlatformQuickEntry :user="user" :platforms="platforms" />

        <section v-if="routeContext.length" class="mt-5 border-t border-white/10 pt-4" aria-labelledby="gis-context-title">
          <div class="flex items-center gap-2 text-xs font-semibold text-white">
            <DatabaseZap class="h-4 w-4 text-cyan-200" aria-hidden="true" />
            <h2 id="gis-context-title">二维 GIS 上下文</h2>
          </div>
          <dl class="mt-2 space-y-1 text-[10px]">
            <div v-for="[key, value] in routeContext" :key="key" class="flex justify-between gap-2 border-b border-white/5 py-1.5">
              <dt class="font-mono text-slate-500">{{ key }}</dt>
              <dd class="max-w-40 truncate font-mono text-slate-300">{{ value }}</dd>
            </div>
          </dl>
          <RouterLink
            v-if="canEnterGis.allowed"
            class="secondary-button focus-ring mt-3 w-full"
            :to="{ path: '/mgmt/gis', query: route.query }"
          >
            进入二维一张图
          </RouterLink>
          <button v-else class="secondary-button mt-3 w-full cursor-not-allowed" type="button" disabled :title="canEnterGis.reason">
            权限不足
          </button>
        </section>
      </aside>

      <section class="relative order-1 min-h-[58vh] overflow-hidden border-white/10 xl:order-2" :aria-label="mapMode === 'hunan' ? '湖南三维地形地图' : '全国三维地图'">
        <China3DMap
          v-if="mapMode === 'national' && provinces.length"
          ref="mapRef"
          :provinces="provinces"
          :fly-lines="flyLines"
          :cities="cities"
          :context-object="contextObject"
          :layers="layers"
          :auto-rotate="autoRotate"
          :selected-adcode="selectedProvince?.adcode"
          @province-select="selectProvince"
          @city-select="selectCity"
          @ready="mapMessage = ''"
          @error="mapMessage = $event"
        />
        <HunanTerrainMap
          v-else-if="mapMode === 'hunan'"
          ref="hunanMapRef"
          :station="hunanStation"
          :layers="hunanLayers"
          :exaggeration="terrainExaggeration"
          @station-select="openHunanStation"
          @ready="mapMessage = ''"
          @error="mapMessage = $event"
        />
        <div v-else class="flex h-full min-h-[58vh] items-center justify-center bg-[#050b14] text-center">
          <div>
            <Loader2 v-if="loading" class="mx-auto h-8 w-8 animate-spin text-cyan-200" aria-hidden="true" />
            <Activity v-else class="mx-auto h-8 w-8 text-rose-200" aria-hidden="true" />
            <p class="mt-3 text-sm text-slate-300">{{ loadMessage }}</p>
          </div>
        </div>

        <MapControlPanel
          v-if="mapMode === 'national'"
          class="absolute left-3 top-3 z-10 w-[238px] max-w-[calc(100%-1.5rem)]"
          :layers="layers"
          :auto-rotate="autoRotate"
          @toggle-layer="toggleLayer"
          @update:auto-rotate="autoRotate = $event"
          @reset="resetMap"
        />
        <HunanMapControlPanel
          v-else
          class="absolute left-3 top-3 z-10 w-[238px] max-w-[calc(100%-1.5rem)]"
          :layers="hunanLayers"
          :exaggeration="terrainExaggeration"
          @back="returnToNationalMap"
          @reset="resetMap"
          @toggle-layer="toggleHunanLayer"
          @update:exaggeration="terrainExaggeration = $event"
        />
        <RiskLegend v-if="mapMode === 'national'" class="absolute bottom-3 left-3 z-10 max-w-[calc(100%-1.5rem)]" />
        <p v-if="mapMessage" class="absolute bottom-3 right-3 z-10 max-w-xs border border-amber-300/20 bg-slate-950/85 px-3 py-2 text-[10px] text-amber-100">
          {{ mapMessage }}
        </p>
      </section>

      <aside class="order-3 border-t border-white/10 bg-[#080d16] p-4 xl:min-h-0 xl:overflow-y-auto xl:border-l xl:border-t-0">
        <ProvinceDetailPanel :province="selectedProvince" :city="selectedCity" @reset="resetMap" @enter-hunan="enterHunanMap" />

        <section class="mt-5 border-t border-white/10 pt-4" aria-labelledby="authorized-title">
          <div class="flex items-center gap-2">
            <ShieldCheck class="h-4 w-4 text-emerald-200" aria-hidden="true" />
            <h2 id="authorized-title" class="text-sm font-semibold text-white">授权范围业务数据</h2>
          </div>
          <p class="mt-1 text-[10px] leading-4 text-slate-500">以下数据来自现有后端接口，受当前用户权限和数据范围约束，不代表全国总量。</p>
          <dl class="mt-3 grid grid-cols-2 gap-px overflow-hidden border border-white/10 bg-white/10 text-xs">
            <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">设备台账</dt><dd class="mt-1 font-mono text-white">{{ moduleStatusLabel(business?.devices) }}</dd></div>
            <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">告警记录</dt><dd class="mt-1 font-mono text-white">{{ moduleStatusLabel(business?.alerts) }}</dd></div>
            <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">开放事件</dt><dd class="mt-1 font-mono text-white">{{ moduleStatusLabel(business?.incidents) }}</dd></div>
            <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">应急工单</dt><dd class="mt-1 font-mono text-white">{{ moduleStatusLabel(business?.workOrders) }}</dd></div>
          </dl>
        </section>
      </aside>
    </div>

    <section class="border-t border-white/10 bg-[#080d16] px-4 py-3 sm:px-6" aria-label="最新业务摘要">
      <div class="app-frame grid gap-px overflow-hidden border border-white/10 bg-white/10 lg:grid-cols-3">
        <article class="min-w-0 bg-[#0b111c] p-3">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2 text-xs font-semibold text-white"><BellRing class="h-4 w-4 text-amber-200" />最新告警</div>
            <RouterLink v-if="business?.alerts.status === 'ready'" class="text-[10px] text-cyan-200 hover:text-white" to="/mgmt/alerts">查看全部</RouterLink>
          </div>
          <p v-if="latestAlert" class="mt-2 truncate text-xs text-slate-300">{{ latestAlert.ruleCode }} · {{ latestAlert.severity }} · {{ formatDate(latestAlert.eventTime) }}</p>
          <p v-else class="mt-2 text-xs text-slate-500">{{ business?.alerts.message ?? '正在加载' }}</p>
        </article>
        <article class="min-w-0 bg-[#0b111c] p-3">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2 text-xs font-semibold text-white"><Activity class="h-4 w-4 text-rose-200" />开放事件</div>
          </div>
          <p v-if="latestIncident" class="mt-2 truncate text-xs text-slate-300">{{ latestIncident.title || latestIncident.incidentId }} · {{ latestIncident.severity }} · {{ formatDate(latestIncident.createdAt) }}</p>
          <p v-else class="mt-2 text-xs text-slate-500">{{ business?.incidents.message ?? '正在加载' }}</p>
        </article>
        <article class="min-w-0 bg-[#0b111c] p-3">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2 text-xs font-semibold text-white"><ClipboardList class="h-4 w-4 text-orange-200" />应急工单</div>
            <RouterLink v-if="business?.workOrders.status === 'ready'" class="text-[10px] text-cyan-200 hover:text-white" to="/emgc/workorders">查看全部</RouterLink>
          </div>
          <p v-if="latestWorkOrder" class="mt-2 truncate text-xs text-slate-300">{{ latestWorkOrder.description || latestWorkOrder.workOrderId }} · {{ latestWorkOrder.status }} · {{ formatDate(latestWorkOrder.updatedAt) }}</p>
          <p v-else class="mt-2 text-xs text-slate-500">{{ business?.workOrders.message ?? '正在加载' }}</p>
        </article>
      </div>
    </section>
  </main>
</template>
