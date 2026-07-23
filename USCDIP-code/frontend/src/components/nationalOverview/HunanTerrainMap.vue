<script setup lang="ts">
import { AlertTriangle, Loader2, RefreshCcw } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import demUrl from '@/assets/geo/hunan-dem.bin?url';
import metadata from '@/assets/geo/hunan-dem.json';
import hunanGeoJsonUrl from '@/assets/geo/hunan.json?url';
import { HunanTerrainScene } from '@/features/hunanMap/hunanTerrainScene';
import type {
  HunanMapLayerState,
  HunanStationData,
  HunanTerrainMetadata,
  StationHoverPayload
} from '@/features/hunanMap/hunanMapTypes';
import type { ChinaFeatureCollection } from '@/features/nationalMap/chinaMapTypes';

const props = defineProps<{
  station: HunanStationData | null;
  layers: HunanMapLayerState;
  exaggeration: number;
}>();

const emit = defineEmits<{
  ready: [];
  error: [message: string];
  'station-select': [station: HunanStationData];
}>();

const host = ref<HTMLDivElement | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const hover = ref<StationHoverPayload | null>(null);
let scene: HunanTerrainScene | null = null;

const tooltipStyle = computed(() => {
  if (!hover.value) return {};
  const width = host.value?.clientWidth ?? 0;
  const height = host.value?.clientHeight ?? 0;
  return {
    left: `${Math.max(12, Math.min(hover.value.screenX + 16, width - 280))}px`,
    top: `${Math.max(12, Math.min(hover.value.screenY + 16, height - 110))}px`
  };
});

async function initialize() {
  state.value = 'loading';
  errorMessage.value = '';
  hover.value = null;
  scene?.dispose();
  scene = null;
  await nextTick();
  if (!host.value) return;

  try {
    const [geoResponse, demResponse] = await Promise.all([
      fetch(hunanGeoJsonUrl, { cache: 'force-cache' }),
      fetch(demUrl, { cache: 'force-cache' })
    ]);
    if (!geoResponse.ok || !demResponse.ok) throw new Error('湖南边界或高程资产加载失败。');
    const geoJson = await geoResponse.json() as ChinaFeatureCollection;
    const buffer = await demResponse.arrayBuffer();
    const elevations = new Float32Array(buffer);
    const terrainMetadata = metadata as HunanTerrainMetadata;
    if (elevations.length !== terrainMetadata.width * terrainMetadata.height) {
      throw new Error('湖南高程资产尺寸不匹配。');
    }
    scene = new HunanTerrainScene(host.value, {
      geoJson,
      elevations,
      metadata: terrainMetadata,
      station: props.station,
      layers: props.layers,
      exaggeration: props.exaggeration,
      callbacks: {
        onContextLost: () => fail('WebGL 上下文已丢失，请重新加载湖南地形。'),
        onReady: () => {
          state.value = 'ready';
          emit('ready');
        },
        onStationHover: (payload) => { hover.value = payload; },
        onStationSelect: (station) => emit('station-select', station)
      }
    });
    scene.initialize();
  } catch (error) {
    fail(error instanceof Error ? error.message : '湖南三维地形初始化失败。');
  }
}

function fail(message: string) {
  scene?.dispose();
  scene = null;
  state.value = 'error';
  errorMessage.value = message;
  emit('error', message);
}

function resetView() {
  scene?.resetView();
}

defineExpose({ resetView, retry: initialize });
watch(() => props.layers, (value) => scene?.applyLayerState(value), { deep: true });
watch(() => props.exaggeration, (value) => scene?.setExaggeration(value));
watch(() => props.station, () => void initialize());
onMounted(() => void initialize());
onBeforeUnmount(() => scene?.dispose());
</script>

<template>
  <div class="relative h-full min-h-[420px] w-full overflow-hidden bg-[#050b14]">
    <div ref="host" class="absolute inset-0"></div>

    <div v-if="state === 'loading'" class="absolute inset-0 flex flex-col items-center justify-center bg-[#050b14]/88" role="status">
      <Loader2 class="h-8 w-8 animate-spin text-cyan-200" aria-hidden="true" />
      <p class="mt-3 text-sm font-semibold text-white">正在构建湖南真实高程地形</p>
      <p class="mt-1 text-xs text-slate-400">加载 DEM、市州边界与授权站点</p>
    </div>

    <div v-else-if="state === 'error'" class="absolute inset-0 flex items-center justify-center bg-[#050b14]/92 p-5" role="alert">
      <div class="w-full max-w-md border border-rose-400/25 bg-slate-950/90 p-5 text-center">
        <AlertTriangle class="mx-auto h-8 w-8 text-rose-200" aria-hidden="true" />
        <p class="mt-3 text-base font-semibold text-white">湖南三维地形暂不可用</p>
        <p class="mt-2 text-sm text-slate-300">{{ errorMessage }}</p>
        <button class="primary-button focus-ring mt-5" type="button" @click="initialize">
          <RefreshCcw class="h-4 w-4" />
          重新加载
        </button>
      </div>
    </div>

    <div v-if="hover" class="pointer-events-none absolute z-20 w-[260px] border border-amber-300/35 bg-slate-950/94 p-3 shadow-2xl" :style="tooltipStyle">
      <p class="text-sm font-semibold text-white">{{ hover.station.stationName }}</p>
      <p class="mt-1 font-mono text-[10px] text-amber-200">{{ hover.station.stationId }} / {{ hover.station.status }}</p>
    </div>

    <a
      class="absolute bottom-2 right-3 z-10 text-[9px] text-slate-500 hover:text-slate-300"
      href="https://registry.opendata.aws/terrain-tiles/"
      target="_blank"
      rel="noreferrer"
    >
      地形数据 AWS Open Data / Mapzen
    </a>
  </div>
</template>
