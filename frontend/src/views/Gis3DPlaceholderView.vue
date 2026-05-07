<script setup lang="ts">
import {
  ArrowLeft,
  Box,
  DatabaseZap,
  Loader2,
  MapPinned,
  RotateCcw,
  ShieldAlert,
  Sparkles,
  TriangleAlert
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import * as THREE from 'three';

import { ApiClientError } from '@/services/api';
import { getGisObjectDetail, queryGisBbox } from '@/services/gis';
import type { GisBboxQuery, GisObjectRecordResponse } from '@/types/api';

type LoadState = 'booting' | 'loading' | 'ready' | 'fallback';
type ParsedGeometry =
  | { kind: 'POINT'; point: [number, number] }
  | { kind: 'LINESTRING'; points: Array<[number, number]> };

const DEFAULT_QUERY: GisBboxQuery = {
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
const router = useRouter();
const canvasHost = ref<HTMLDivElement | null>(null);
const state = ref<LoadState>('booting');
const message = ref('正在检测 WebGL 与 3D 占位能力');
const traceId = ref('');
const fallbackReason = ref('');
const fallbackCountdown = ref(0);
const fallbackCancelled = ref(false);
const objects = ref<GisObjectRecordResponse[]>([]);
const selectedObject = ref<GisObjectRecordResponse | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let frameId = 0;
let fallbackTimer: ReturnType<typeof window.setInterval> | null = null;
let resizeObserver: ResizeObserver | null = null;

const bboxQuery = computed<GisBboxQuery>(() => ({
  minX: readNumberQuery('minX', DEFAULT_QUERY.minX),
  minY: readNumberQuery('minY', DEFAULT_QUERY.minY),
  maxX: readNumberQuery('maxX', DEFAULT_QUERY.maxX),
  maxY: readNumberQuery('maxY', DEFAULT_QUERY.maxY),
  authoritySrid: readStringQuery('authoritySrid', DEFAULT_QUERY.authoritySrid),
  displaySrid: readStringQuery('displaySrid', DEFAULT_QUERY.displaySrid),
  page: 1,
  pageSize: 50
}));

const objectType = computed(() => readStringQuery('objectType', ''));
const objectId = computed(() => readStringQuery('objectId', ''));
const fallbackQuery = computed(() => {
  const query: Record<string, string> = {
    minX: String(bboxQuery.value.minX),
    minY: String(bboxQuery.value.minY),
    maxX: String(bboxQuery.value.maxX),
    maxY: String(bboxQuery.value.maxY),
    authoritySrid: bboxQuery.value.authoritySrid,
    displaySrid: bboxQuery.value.displaySrid,
    source: '3d-fallback'
  };
  if (objectType.value && objectId.value) {
    query.objectType = objectType.value;
    query.objectId = objectId.value;
  }
  return query;
});
const objectSummary = computed(() => selectedObject.value
  ? `${selectedObject.value.objectType} / ${selectedObject.value.objectName || selectedObject.value.objectId}`
  : objectType.value && objectId.value
    ? `${objectType.value} / ${objectId.value}`
    : '默认 bbox 场景'
);
const renderedSummary = computed(() => {
  const counts = objects.value.reduce<Record<string, number>>((acc, item) => {
    const key = item.objectType || 'UNKNOWN';
    acc[key] = (acc[key] ?? 0) + 1;
    return acc;
  }, {});
  return Object.entries(counts).map(([key, value]) => `${key} ${value}`).join(' / ') || '-';
});

onMounted(async () => {
  await nextTick();
  await bootScene();
});

onBeforeUnmount(() => {
  stopFallbackTimer();
  teardownScene();
});

async function bootScene() {
  if (!supportsWebGL()) {
    scheduleFallback('浏览器不支持 WebGL，已切换回 2D 主入口。');
    return;
  }

  if (!canvasHost.value) {
    scheduleFallback('3D 画布容器不可用，已切换回 2D 主入口。');
    return;
  }

  try {
    setupRenderer();
    await loadGisContext();
    drawScene();
    state.value = 'ready';
    message.value = '3D 占位场景已加载';
    startRenderLoop();
  } catch (error) {
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      scheduleFallback(error.status === 404 ? '当前上下文没有可用 GIS 空间对象。' : error.message);
      return;
    }
    scheduleFallback(error instanceof Error ? error.message : '3D 占位页初始化失败。');
  }
}

function setupRenderer() {
  const host = canvasHost.value;
  if (!host) {
    throw new Error('3D canvas host is missing');
  }

  scene = new THREE.Scene();
  scene.background = new THREE.Color('#07111f');
  camera = new THREE.PerspectiveCamera(52, 1, 0.1, 2000);
  camera.position.set(0, -120, 88);
  camera.lookAt(0, 0, 0);

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, preserveDrawingBuffer: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.innerHTML = '';
  host.appendChild(renderer.domElement);

  scene.add(new THREE.AmbientLight('#7dd3fc', 1.4));
  const pointLight = new THREE.PointLight('#fbbf24', 1.6, 300);
  pointLight.position.set(40, -60, 90);
  scene.add(pointLight);

  resizeObserver = new ResizeObserver(resizeScene);
  resizeObserver.observe(host);
  resizeScene();
}

async function loadGisContext() {
  state.value = 'loading';
  message.value = '正在读取 GIS 上下文并生成 3D 线框';

  if (objectType.value && objectId.value) {
    const detail = await getGisObjectDetail(objectType.value, objectId.value, bboxQuery.value.displaySrid);
    traceId.value = detail.traceId;
    if (detail.data) {
      selectedObject.value = detail.data;
    }
  }

  const response = await queryGisBbox(bboxQuery.value);
  traceId.value = response.traceId || traceId.value;
  objects.value = response.data?.items ?? [];
  const currentObject = selectedObject.value;
  if (currentObject && !objects.value.some((item) => objectKey(item) === objectKey(currentObject))) {
    objects.value = [currentObject, ...objects.value];
  }
  if (!objects.value.length) {
    throw new ApiClientError('当前 bbox 未返回 GIS 对象，3D 占位自动回到 2D。', 404, {
      success: false,
      data: null,
      error: {
        code: 'GIS_3D_EMPTY',
        message: '当前 bbox 未返回 GIS 对象，3D 占位自动回到 2D。'
      },
      traceId: response.traceId,
      timestamp: new Date().toISOString()
    });
  }
}

function drawScene() {
  if (!scene) return;
  const group = new THREE.Group();
  group.name = 'gis-placeholder-wireframe';
  scene.add(group);

  const center = {
    x: (bboxQuery.value.minX + bboxQuery.value.maxX) / 2,
    y: (bboxQuery.value.minY + bboxQuery.value.maxY) / 2
  };
  const scale = 9000;

  drawGrid(group);

  objects.value.forEach((object, index) => {
    const parsed = parseWkt(object.geometry2d);
    const selected = selectedObject.value ? objectKey(object) === objectKey(selectedObject.value) : false;
    const z = selected ? 8 : zForObject(object.objectType, index);
    if (parsed?.kind === 'LINESTRING') {
      const points = parsed.points.map(([x, y]) => toVector(x, y, z, center, scale));
      if (points.length > 1) {
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        const material = new THREE.LineBasicMaterial({ color: selected ? '#FBBF24' : colorForObject(object.objectType), linewidth: 2 });
        group.add(new THREE.Line(geometry, material));
      }
      return;
    }

    const point = parsed?.kind === 'POINT'
      ? toVector(parsed.point[0], parsed.point[1], z, center, scale)
      : toVector(object.anchorPoint.x, object.anchorPoint.y, z, center, scale);
    const geometry = object.objectType === 'FACILITY'
      ? new THREE.BoxGeometry(selected ? 5.5 : 3.6, selected ? 5.5 : 3.6, selected ? 7 : 4)
      : new THREE.SphereGeometry(selected ? 3.8 : 2.5, 18, 18);
    const material = new THREE.MeshStandardMaterial({
      color: selected ? '#FBBF24' : colorForObject(object.objectType),
      emissive: selected ? '#7c2d12' : '#020617',
      roughness: 0.4,
      metalness: 0.18
    });
    const mesh = new THREE.Mesh(geometry, material);
    mesh.position.copy(point);
    group.add(mesh);
  });
}

function drawGrid(group: THREE.Group) {
  const grid = new THREE.GridHelper(150, 12, '#2563eb', '#1e293b');
  grid.rotation.x = Math.PI / 2;
  grid.position.z = -2;
  group.add(grid);

  const axes = new THREE.AxesHelper(40);
  axes.position.set(-70, -64, 0);
  group.add(axes);
}

function startRenderLoop() {
  const tick = () => {
    if (scene && camera && renderer) {
      const target = scene.getObjectByName('gis-placeholder-wireframe');
      if (target) {
        target.rotation.z += 0.0018;
      }
      renderer.render(scene, camera);
    }
    frameId = window.requestAnimationFrame(tick);
  };
  tick();
}

function resizeScene() {
  const host = canvasHost.value;
  if (!host || !renderer || !camera) return;
  const width = Math.max(host.clientWidth, 320);
  const height = Math.max(host.clientHeight, 320);
  renderer.setSize(width, height, false);
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
}

function scheduleFallback(reason: string) {
  state.value = 'fallback';
  fallbackReason.value = reason;
  message.value = reason;
  fallbackCountdown.value = 3;
  stopFallbackTimer();
  fallbackTimer = window.setInterval(() => {
    if (fallbackCancelled.value) return;
    fallbackCountdown.value -= 1;
    if (fallbackCountdown.value <= 0) {
      void goTo2D();
    }
  }, 1000);
}

function cancelFallback() {
  fallbackCancelled.value = true;
  stopFallbackTimer();
  fallbackCountdown.value = 0;
}

async function goTo2D() {
  stopFallbackTimer();
  await router.replace({ path: '/mgmt/gis', query: fallbackQuery.value });
}

function stopFallbackTimer() {
  if (fallbackTimer) {
    window.clearInterval(fallbackTimer);
    fallbackTimer = null;
  }
}

function teardownScene() {
  if (frameId) {
    window.cancelAnimationFrame(frameId);
    frameId = 0;
  }
  resizeObserver?.disconnect();
  resizeObserver = null;
  renderer?.dispose();
  renderer?.domElement.remove();
  renderer = null;
  scene = null;
  camera = null;
}

function supportsWebGL() {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(window.WebGLRenderingContext && (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
  } catch {
    return false;
  }
}

function parseWkt(value: string | null | undefined): ParsedGeometry | null {
  if (!value) return null;
  const pointMatch = value.match(/^POINT\s*\(\s*([\d.-]+)\s+([\d.-]+)\s*\)$/i);
  if (pointMatch) {
    return { kind: 'POINT', point: [Number(pointMatch[1]), Number(pointMatch[2])] };
  }
  const lineMatch = value.match(/^LINESTRING\s*\((.+)\)$/i);
  if (lineMatch) {
    const points = lineMatch[1].split(',').map((pair) => {
      const [x, y] = pair.trim().split(/\s+/).map(Number);
      return [x, y] as [number, number];
    }).filter(([x, y]) => Number.isFinite(x) && Number.isFinite(y));
    return points.length ? { kind: 'LINESTRING', points } : null;
  }
  return null;
}

function toVector(x: number, y: number, z: number, center: { x: number; y: number }, scale: number) {
  return new THREE.Vector3((x - center.x) * scale, (y - center.y) * scale, z);
}

function colorForObject(type: string) {
  const colors: Record<string, string> = {
    SEGMENT: '#F59E0B',
    NODE: '#38BDF8',
    DEVICE: '#A78BFA',
    FACILITY: '#22C55E'
  };
  return colors[type] ?? '#60A5FA';
}

function zForObject(type: string, index: number) {
  if (type === 'SEGMENT') return 0;
  if (type === 'FACILITY') return 5;
  if (type === 'DEVICE') return 10;
  return 3 + (index % 4);
}

function objectKey(object: GisObjectRecordResponse) {
  return `${object.objectType}:${object.objectId}`;
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
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-[1600px] flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8">
      <header class="glass-panel flex flex-col gap-4 p-4 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex min-w-0 items-center gap-3">
          <RouterLink class="icon-button focus-ring" :to="{ path: '/mgmt/gis', query: fallbackQuery }" aria-label="返回 2D 一张图">
            <ArrowLeft class="h-4 w-4" />
          </RouterLink>
          <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-blue-400/25 bg-blue-400/10 text-blue-100">
            <Box class="h-6 w-6" aria-hidden="true" />
          </div>
          <div class="min-w-0">
            <p class="font-display text-lg font-semibold text-white sm:text-xl">3D 占位视图</p>
            <p class="truncate text-sm text-slate-400">二期数字孪生插槽 / 自动回退 2D / {{ bboxQuery.displaySrid }}</p>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3 md:grid-cols-4 lg:min-w-[44rem]">
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">状态</p>
            <p class="mt-1 truncate font-display text-lg font-semibold text-white">{{ state }}</p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">对象上下文</p>
            <p class="mt-1 truncate font-mono text-xs text-blue-100">{{ objectSummary }}</p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">已载入</p>
            <p class="mt-1 truncate font-mono text-xs text-emerald-100">{{ renderedSummary }}</p>
          </div>
          <div class="glass-panel-muted p-3">
            <p class="text-xs text-slate-400">TraceId</p>
            <p class="mt-1 truncate font-mono text-xs text-amber-100">{{ traceId || '-' }}</p>
          </div>
        </div>
      </header>

      <section class="grid min-h-[calc(100vh-9rem)] gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div class="glass-panel relative min-h-[520px] overflow-hidden">
          <div ref="canvasHost" class="absolute inset-0" aria-label="3D GIS 占位画布"></div>
          <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(59,130,246,0.16),transparent_28%),radial-gradient(circle_at_82%_78%,rgba(245,158,11,0.12),transparent_30%)]"></div>

          <div v-if="state === 'booting' || state === 'loading'" class="absolute inset-0 flex flex-col items-center justify-center bg-slate-950/70 text-center">
            <Loader2 class="mb-3 h-8 w-8 animate-spin text-blue-200" aria-hidden="true" />
            <p class="text-sm font-semibold text-white">{{ message }}</p>
            <p class="mt-2 max-w-sm text-xs leading-5 text-slate-400">正在复用 B-10 GIS 数据生成一期轻量线框，不阻塞 2D 主入口。</p>
          </div>

          <div v-if="state === 'fallback'" class="absolute inset-0 flex items-center justify-center bg-slate-950/78 p-4">
            <section class="w-full max-w-xl rounded-lg border border-amber-400/25 bg-slate-950/95 p-5 shadow-2xl shadow-amber-950/30">
              <div class="flex items-start gap-3">
                <TriangleAlert class="mt-1 h-5 w-5 shrink-0 text-amber-200" aria-hidden="true" />
                <div>
                  <p class="font-display text-lg font-semibold text-white">3D 占位不可用，正在降级到 2D</p>
                  <p class="mt-2 text-sm leading-6 text-amber-50">{{ fallbackReason }}</p>
                  <p v-if="traceId" class="mt-2 truncate font-mono text-xs text-amber-100">TraceId {{ traceId }}</p>
                </div>
              </div>
              <div class="mt-5 flex flex-col gap-2 sm:flex-row">
                <button class="primary-button focus-ring" type="button" @click="goTo2D">
                  <MapPinned class="h-4 w-4" />
                  立即回到 2D
                </button>
                <button class="secondary-button focus-ring" type="button" :disabled="fallbackCancelled" @click="cancelFallback">
                  <RotateCcw class="h-4 w-4" />
                  {{ fallbackCancelled ? '已暂停自动回退' : `暂停倒计时 ${fallbackCountdown}s` }}
                </button>
              </div>
            </section>
          </div>
        </div>

        <aside class="glass-panel flex flex-col gap-4 p-4">
          <section class="rounded-lg border border-blue-400/20 bg-blue-400/10 p-4">
            <div class="flex items-center gap-2 text-sm font-semibold text-white">
              <Sparkles class="h-4 w-4 text-blue-200" aria-hidden="true" />
              一期 3D 插槽
            </div>
            <p class="mt-2 text-sm leading-6 text-slate-300">
              当前页面只渲染轻量线框，占位验证 2D/3D 上下文和降级策略；真实三维孪生、泄漏扩散和动态推演留到二期。
            </p>
          </section>

          <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
            <div class="flex items-center gap-2 text-sm font-semibold text-white">
              <DatabaseZap class="h-4 w-4 text-emerald-200" aria-hidden="true" />
              GIS 联调
            </div>
            <dl class="mt-3 space-y-2 text-xs">
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">bbox</dt>
                <dd class="text-right font-mono text-slate-100">{{ bboxQuery.minX }}, {{ bboxQuery.minY }} / {{ bboxQuery.maxX }}, {{ bboxQuery.maxY }}</dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">detail</dt>
                <dd class="text-right font-mono text-slate-100">{{ objectType || '-' }} / {{ objectId || '-' }}</dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">displaySrid</dt>
                <dd class="text-right font-mono text-slate-100">{{ bboxQuery.displaySrid }}</dd>
              </div>
            </dl>
          </section>

          <section class="rounded-lg border border-amber-400/20 bg-amber-400/10 p-4">
            <div class="flex items-center gap-2 text-sm font-semibold text-amber-50">
              <ShieldAlert class="h-4 w-4" aria-hidden="true" />
              降级规则
            </div>
            <ul class="mt-3 space-y-2 text-sm leading-6 text-amber-50">
              <li>WebGL 不可用：立即回到 2D。</li>
              <li>GIS API 失败：展示 traceId 后 3 秒回退。</li>
              <li>回退保留对象与 bbox query，不丢失上下文。</li>
            </ul>
          </section>

          <div class="mt-auto grid gap-2">
            <RouterLink class="primary-button focus-ring" :to="{ path: '/mgmt/gis', query: fallbackQuery }">
              <MapPinned class="h-4 w-4" />
              回到 2D 主入口
            </RouterLink>
            <RouterLink class="secondary-button focus-ring" to="/mgmt">
              <ArrowLeft class="h-4 w-4" />
              返回综合管理平台
            </RouterLink>
          </div>
        </aside>
      </section>
    </div>
  </main>
</template>
