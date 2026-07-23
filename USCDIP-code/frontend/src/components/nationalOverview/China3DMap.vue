<script setup lang="ts">
import { AlertTriangle, Loader2, MapPinned, RefreshCcw } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import chinaGeoJsonUrl from '@/assets/geo/china.json?url';
import { NationalMapScene } from '@/features/nationalMap/nationalMapScene';
import type {
  ChinaFeatureCollection,
  CityInteractionPayload,
  CityNodeData,
  FlyLineConfig,
  GisContextMarkerData,
  NationalMapLayerState,
  ProvinceHealthData,
  ProvinceInteractionPayload
} from '@/features/nationalMap/chinaMapTypes';

const props = defineProps<{
  provinces: ProvinceHealthData[];
  flyLines: FlyLineConfig[];
  cities: CityNodeData[];
  contextObject?: GisContextMarkerData | null;
  layers: NationalMapLayerState;
  autoRotate: boolean;
  selectedAdcode?: string | null;
}>();

const emit = defineEmits<{
  ready: [];
  error: [message: string];
  'province-select': [province: ProvinceHealthData];
  'city-select': [city: CityNodeData];
}>();

const host = ref<HTMLDivElement | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const provinceTooltip = ref<ProvinceInteractionPayload | null>(null);
const cityTooltip = ref<CityInteractionPayload | null>(null);
const sceneVersion = ref(0);
let scene: NationalMapScene | null = null;

const tooltipStyle = computed(() => {
  const payload = provinceTooltip.value ?? cityTooltip.value;
  if (!payload) return {};
  const width = host.value?.clientWidth ?? 0;
  const height = host.value?.clientHeight ?? 0;
  return {
    left: `${Math.max(12, Math.min(payload.screenX + 16, width - 250))}px`,
    top: `${Math.max(12, Math.min(payload.screenY + 16, height - 190))}px`
  };
});

function supportsWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(window.WebGLRenderingContext && (canvas.getContext('webgl2') || canvas.getContext('webgl')));
  } catch {
    return false;
  }
}

async function initializeScene() {
  state.value = 'loading';
  errorMessage.value = '';
  provinceTooltip.value = null;
  cityTooltip.value = null;
  scene?.dispose();
  scene = null;
  await nextTick();

  if (!host.value) return;
  if (!supportsWebGL()) {
    fail('当前浏览器无法使用 WebGL，统计与业务入口仍可继续使用。');
    return;
  }

  try {
    const geoJson = await loadGeoJson();
    if (!host.value) return;
    scene = new NationalMapScene(host.value, {
      geoJson,
      provinces: props.provinces,
      flyLines: props.flyLines,
      cities: props.cities,
      contextObject: props.contextObject,
      layers: props.layers,
      autoRotate: props.autoRotate,
      selectedAdcode: props.selectedAdcode,
      callbacks: {
        onProvinceHover: (payload) => {
          provinceTooltip.value = payload;
          if (payload) cityTooltip.value = null;
        },
        onProvinceSelect: (province) => emit('province-select', province),
        onCityHover: (payload) => {
          cityTooltip.value = payload;
          if (payload) provinceTooltip.value = null;
        },
        onCitySelect: (city) => emit('city-select', city),
        onContextLost: () => fail('WebGL 上下文已丢失，请重新加载三维地图。'),
        onReady: () => {
          state.value = 'ready';
          emit('ready');
        }
      }
    });
    scene.initialize();
  } catch (error) {
    scene?.dispose();
    scene = null;
    fail(error instanceof Error ? error.message : '中国三维地图初始化失败。');
  }
}

async function loadGeoJson(): Promise<ChinaFeatureCollection> {
  const response = await fetch(chinaGeoJsonUrl, { cache: 'force-cache' });
  if (!response.ok) {
    throw new Error(`中国省级 GeoJSON 加载失败（HTTP ${response.status}）。`);
  }
  const data: unknown = await response.json();
  if (!isChinaFeatureCollection(data)) {
    throw new Error('中国省级 GeoJSON 格式无效。');
  }
  return data;
}

function isChinaFeatureCollection(value: unknown): value is ChinaFeatureCollection {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as { type?: unknown; features?: unknown };
  return candidate.type === 'FeatureCollection' && Array.isArray(candidate.features);
}

function fail(message: string) {
  state.value = 'error';
  errorMessage.value = message;
  emit('error', message);
}

function retry() {
  sceneVersion.value += 1;
  void initializeScene();
}

function resetView() {
  scene?.resetView();
}

function focusProvince(adcode: string) {
  scene?.focusProvince(adcode);
}

defineExpose({ resetView, focusProvince, retry });

watch(() => props.autoRotate, (value) => scene?.setAutoRotate(value));
watch(() => props.layers, (value) => scene?.applyLayerState(value), { deep: true });
watch(() => props.selectedAdcode, (value) => scene?.setSelectedProvince(value));
watch(
  () => [props.provinces, props.flyLines, props.cities, props.contextObject],
  () => {
    if (state.value !== 'loading') retry();
  }
);

onMounted(() => void initializeScene());
onBeforeUnmount(() => {
  scene?.dispose();
  scene = null;
});
</script>

<template>
  <div class="relative h-full min-h-[420px] w-full overflow-hidden bg-[#050b14]">
    <div :key="sceneVersion" ref="host" class="absolute inset-0"></div>

    <div
      v-if="state === 'loading'"
      class="absolute inset-0 flex flex-col items-center justify-center bg-[#050b14]/85 text-center"
      role="status"
      aria-live="polite"
    >
      <Loader2 class="h-8 w-8 animate-spin text-cyan-200" aria-hidden="true" />
      <p class="mt-3 text-sm font-semibold text-white">正在构建中国省级三维地图</p>
      <p class="mt-1 text-xs text-slate-400">加载省界、风险材质、城市节点与跨区域协同链路</p>
    </div>

    <div
      v-else-if="state === 'error'"
      class="absolute inset-0 flex items-center justify-center bg-[#050b14]/92 p-5"
      role="alert"
    >
      <div class="w-full max-w-md border border-rose-400/25 bg-slate-950/90 p-5 text-center">
        <AlertTriangle class="mx-auto h-8 w-8 text-rose-200" aria-hidden="true" />
        <p class="mt-3 text-base font-semibold text-white">三维地图暂不可用</p>
        <p class="mt-2 text-sm leading-6 text-slate-300">{{ errorMessage }}</p>
        <div class="mt-5 flex flex-col justify-center gap-2 sm:flex-row">
          <button class="primary-button focus-ring" type="button" @click="retry">
            <RefreshCcw class="h-4 w-4" />
            重新加载
          </button>
          <RouterLink class="secondary-button focus-ring" to="/mgmt/gis">
            <MapPinned class="h-4 w-4" />
            进入二维一张图
          </RouterLink>
        </div>
      </div>
    </div>

    <div
      v-if="provinceTooltip"
      class="pointer-events-none absolute z-20 w-[230px] border border-cyan-300/25 bg-slate-950/92 p-3 shadow-2xl shadow-cyan-950/40 backdrop-blur-md"
      :style="tooltipStyle"
    >
      <div class="flex items-center justify-between gap-3">
        <p class="font-semibold text-white">{{ provinceTooltip.province.provinceName }}</p>
        <span class="font-mono text-xs text-cyan-100">{{ provinceTooltip.province.healthScore }}</span>
      </div>
      <dl class="mt-2 grid grid-cols-2 gap-2 text-xs text-slate-400">
        <div>管线 <span class="text-slate-100">{{ provinceTooltip.province.pipelineLengthKm.toLocaleString() }} km</span></div>
        <div>设备 <span class="text-slate-100">{{ provinceTooltip.province.onlineDeviceCount.toLocaleString() }}</span></div>
        <div>告警 <span class="text-slate-100">{{ provinceTooltip.province.activeAlertCount }}</span></div>
        <div>事件 <span class="text-slate-100">{{ provinceTooltip.province.openIncidentCount }}</span></div>
      </dl>
    </div>

    <div
      v-if="cityTooltip"
      class="pointer-events-none absolute z-20 w-[220px] border border-cyan-300/25 bg-slate-950/92 p-3 shadow-2xl shadow-cyan-950/40 backdrop-blur-md"
      :style="tooltipStyle"
    >
      <div class="flex items-center justify-between gap-3">
        <p class="font-semibold text-white">{{ cityTooltip.city.cityName }}</p>
        <span class="font-mono text-xs text-cyan-100">{{ cityTooltip.city.healthScore }}</span>
      </div>
      <p class="mt-2 text-xs text-slate-300">
        在线设备 {{ cityTooltip.city.deviceCount.toLocaleString() }} / 活动告警 {{ cityTooltip.city.alertCount }}
      </p>
    </div>
  </div>
</template>
