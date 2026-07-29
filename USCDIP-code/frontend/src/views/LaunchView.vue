<script setup lang="ts">
import { ArrowRight, Globe2, ShieldCheck } from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import chinaGeoJsonUrl from '@/assets/geo/china.json?url';
import type { ChinaFeatureCollection } from '@/features/nationalMap/chinaMapTypes';
import {
  STARTUP_TIMELINE_MS,
  StartupGlobeScene,
  type StartupStage
} from '@/features/startup/startupGlobeScene';
import {
  consumePostLoginLaunchElapsedMs,
  resolvePostLoginRoute
} from '@/services/auth';

type LaunchRenderMode = 'loading' | 'webgl' | 'fallback';

const route = useRoute();
const router = useRouter();
const elapsedBeforeSetup = consumePostLoginLaunchElapsedMs();
const launchStartedAt = performance.now();
const host = ref<HTMLDivElement | null>(null);
const renderMode = ref<LaunchRenderMode>('loading');
const stage = ref<StartupStage>('assembling');
const progress = ref(0);
const isLeaving = ref(false);
const fallbackReason = ref('');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
let scene: StartupGlobeScene | null = null;
let fallbackTimer: ReturnType<typeof window.setTimeout> | null = null;
let navigationTimer: ReturnType<typeof window.setTimeout> | null = null;
const completionRequested = ref(false);
let skipRequested = false;

const targetRoute = computed(() => resolvePostLoginRoute(route.query.redirect));
const titleVisible = computed(
  () => renderMode.value === 'fallback'
    || stage.value === 'revealing'
    || stage.value === 'ready'
    || stage.value === 'leaving'
);
const progressPercent = computed(() => Math.round(progress.value * 100));
const remainingSeconds = computed(() =>
  Math.max(0, Math.ceil((STARTUP_TIMELINE_MS * (1 - progress.value)) / 1000))
);
const statusMessage = computed(() => {
  if (renderMode.value === 'fallback') {
    return reducedMotion ? '已启用减少动态效果，正在进入系统' : '正在使用兼容模式进入系统';
  }
  const messages: Record<StartupStage, string> = {
    assembling: '正在构建全球感知粒子场',
    rotating: '正在校准全球数据坐标',
    focusing: '正在聚焦中国地下管网',
    revealing: '正在启动地下管网数字化健康监测系统',
    ready: '系统初始化完成',
    leaving: '正在进入业务系统'
  };
  return messages[stage.value];
});

function supportsWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(
      window.WebGLRenderingContext
      && (canvas.getContext('webgl2') || canvas.getContext('webgl'))
    );
  } catch {
    return false;
  }
}

function isChinaFeatureCollection(value: unknown): value is ChinaFeatureCollection {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as { type?: unknown; features?: unknown };
  return candidate.type === 'FeatureCollection' && Array.isArray(candidate.features);
}

async function loadChinaGeoJson(): Promise<ChinaFeatureCollection> {
  const response = await fetch(chinaGeoJsonUrl, { cache: 'force-cache' });
  if (!response.ok) {
    throw new Error(`中国地图数据加载失败（HTTP ${response.status}）`);
  }
  const value: unknown = await response.json();
  if (!isChinaFeatureCollection(value)) {
    throw new Error('中国地图数据格式无效');
  }
  return value;
}

function beginLeaving(delayOverride?: number): void {
  if (completionRequested.value) return;
  completionRequested.value = true;
  isLeaving.value = true;
  stage.value = 'leaving';
  progress.value = 1;
  const delay = delayOverride ?? (reducedMotion ? 80 : 460);
  navigationTimer = window.setTimeout(() => {
    navigationTimer = null;
    void router.replace(targetRoute.value);
  }, delay);
}

function skipLaunch(): void {
  if (completionRequested.value) return;
  skipRequested = true;
  if (scene) {
    scene.skip();
    return;
  }
  beginLeaving();
}

function showFallback(reason: string): void {
  scene?.dispose();
  scene = null;
  renderMode.value = 'fallback';
  fallbackReason.value = reason;
  stage.value = 'ready';
  progress.value = 1;
  fallbackTimer = window.setTimeout(() => {
    fallbackTimer = null;
    beginLeaving();
  }, reducedMotion ? 1_500 : 1_800);
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    skipLaunch();
  }
}

async function initializeLaunch(): Promise<void> {
  if (reducedMotion) {
    showFallback('已根据系统设置减少动态效果');
    return;
  }
  if (!supportsWebGL()) {
    showFallback('当前浏览器无法使用 WebGL');
    return;
  }

  try {
    const geoJson = await loadChinaGeoJson();
    await nextTick();
    if (!host.value) return;

    const nextScene = new StartupGlobeScene(host.value, {
      geoJson,
      callbacks: {
        onReady: () => {
          renderMode.value = 'webgl';
        },
        onProgress: (nextProgress, nextStage) => {
          progress.value = nextProgress;
          stage.value = nextStage;
          if (nextStage === 'leaving') {
            isLeaving.value = true;
          }
        },
        onComplete: () => beginLeaving(skipRequested ? 460 : 0),
        onError: (message) => showFallback(message)
      }
    });
    scene = nextScene;
    nextScene.initialize();
    if (scene === nextScene) {
      nextScene.start(elapsedBeforeSetup + performance.now() - launchStartedAt);
    }
  } catch (error) {
    showFallback(error instanceof Error ? error.message : '启动动画初始化失败');
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  void initializeLaunch();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
  if (fallbackTimer) window.clearTimeout(fallbackTimer);
  if (navigationTimer) window.clearTimeout(navigationTimer);
  scene?.dispose();
  scene = null;
});
</script>

<template>
  <main
    class="launch-screen"
    :class="{
      'launch-screen--leaving': isLeaving,
      'launch-screen--fallback': renderMode === 'fallback'
    }"
  >
    <div class="launch-screen__backdrop" aria-hidden="true">
      <div class="launch-screen__grid"></div>
      <div class="launch-screen__horizon"></div>
      <div class="launch-screen__vignette"></div>
    </div>

    <div ref="host" class="launch-screen__scene" aria-hidden="true"></div>

    <div v-if="renderMode === 'fallback'" class="fallback-globe" aria-hidden="true">
      <div class="fallback-globe__halo"></div>
      <div class="fallback-globe__sphere">
        <span class="fallback-globe__china">中国</span>
      </div>
      <div class="fallback-globe__orbit fallback-globe__orbit--one"></div>
      <div class="fallback-globe__orbit fallback-globe__orbit--two"></div>
    </div>

    <header class="launch-screen__header">
      <div class="launch-screen__brand" aria-label="USCDIP 城市生命线智能中枢">
        <span class="launch-screen__brand-mark">
          <Globe2 class="h-5 w-5" aria-hidden="true" />
        </span>
        <span>
          <strong>USCDIP</strong>
          <small>CITY LIFELINE INTELLIGENCE</small>
        </span>
      </div>

      <button
        class="launch-screen__skip focus-ring"
        type="button"
        :disabled="completionRequested"
        aria-label="跳过启动动画并进入业务系统"
        @click="skipLaunch"
      >
        <span>跳过</span>
        <span v-if="renderMode === 'webgl'" class="launch-screen__skip-time">
          {{ remainingSeconds }}s
        </span>
        <ArrowRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </header>

    <section
      class="launch-title"
      :class="{ 'launch-title--visible': titleVisible }"
      aria-labelledby="launch-system-title"
    >
      <div class="launch-title__eyebrow">
        <span></span>
        UNDERGROUND INFRASTRUCTURE DIGITAL TWIN
        <span></span>
      </div>
      <div class="launch-title__reveal">
        <h1 id="launch-system-title">地下管网数字化健康监测系统</h1>
        <i aria-hidden="true"></i>
      </div>
      <p>感知城市脉络 · 守护地下生命线</p>
    </section>

    <footer class="launch-status">
      <div class="launch-status__copy" role="status" aria-live="polite">
        <ShieldCheck class="h-4 w-4 text-cyan-200" aria-hidden="true" />
        <span>{{ statusMessage }}</span>
        <span v-if="fallbackReason" class="sr-only">{{ fallbackReason }}</span>
      </div>
      <div
        class="launch-status__track"
        role="progressbar"
        aria-label="系统启动进度"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="progressPercent"
      >
        <span :style="{ transform: `scaleX(${progress})` }"></span>
      </div>
      <div class="launch-status__meta">
        <span>SECURE SESSION ESTABLISHED</span>
        <span>{{ progressPercent.toString().padStart(3, '0') }}%</span>
      </div>
    </footer>
  </main>
</template>

<style scoped>
.launch-screen {
  position: relative;
  isolation: isolate;
  width: 100%;
  min-height: 100vh;
  min-height: 100svh;
  overflow: hidden;
  color: #f8fafc;
  background: #020713;
  opacity: 1;
  transition: opacity 460ms ease-in;
}

.launch-screen--leaving {
  opacity: 0;
}

.launch-screen__backdrop,
.launch-screen__scene,
.launch-screen__vignette {
  position: absolute;
  inset: 0;
}

.launch-screen__backdrop {
  z-index: -3;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 50%, rgba(3, 105, 161, 0.18), transparent 38%),
    linear-gradient(180deg, #020713 0%, #04101f 52%, #020713 100%);
}

.launch-screen__grid {
  position: absolute;
  inset: 46% -20% -36%;
  opacity: 0.19;
  background-image:
    linear-gradient(rgba(56, 189, 248, 0.18) 1px, transparent 1px),
    linear-gradient(90deg, rgba(56, 189, 248, 0.18) 1px, transparent 1px);
  background-size: 54px 54px;
  transform: perspective(620px) rotateX(66deg);
  transform-origin: center top;
  mask-image: linear-gradient(to bottom, transparent, black 22%, transparent 82%);
}

.launch-screen__horizon {
  position: absolute;
  right: 8%;
  bottom: 9%;
  left: 8%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.64), transparent);
  box-shadow: 0 0 36px 8px rgba(14, 165, 233, 0.2);
}

.launch-screen__vignette {
  background:
    linear-gradient(90deg, rgba(2, 7, 19, 0.7), transparent 18%, transparent 82%, rgba(2, 7, 19, 0.7)),
    linear-gradient(180deg, rgba(2, 7, 19, 0.82), transparent 22%, transparent 76%, rgba(2, 7, 19, 0.9));
  pointer-events: none;
}

.launch-screen__scene {
  z-index: -1;
}

.launch-screen__scene :deep(.startup-globe-canvas) {
  display: block;
  width: 100%;
  height: 100%;
}

.launch-screen__header {
  position: absolute;
  z-index: 10;
  top: max(22px, env(safe-area-inset-top));
  right: max(24px, env(safe-area-inset-right));
  left: max(24px, env(safe-area-inset-left));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.launch-screen__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: #e0f2fe;
}

.launch-screen__brand-mark {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(125, 211, 252, 0.28);
  background: rgba(14, 116, 144, 0.12);
  box-shadow: 0 0 28px rgba(14, 165, 233, 0.13);
}

.launch-screen__brand span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.launch-screen__brand strong {
  font-family: 'Fira Code', ui-monospace, monospace;
  font-size: 14px;
  letter-spacing: 0.22em;
}

.launch-screen__brand small {
  margin-top: 2px;
  overflow: hidden;
  color: rgba(186, 230, 253, 0.55);
  font-family: ui-monospace, monospace;
  font-size: 8px;
  letter-spacing: 0.16em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.launch-screen__skip {
  display: inline-flex;
  min-width: 112px;
  min-height: 44px;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px solid rgba(125, 211, 252, 0.24);
  background: rgba(4, 16, 31, 0.66);
  padding: 0 15px;
  color: #e0f2fe;
  font-size: 13px;
  font-weight: 600;
  backdrop-filter: blur(18px);
  transition:
    border-color 200ms ease,
    background-color 200ms ease,
    color 200ms ease;
}

.launch-screen__skip:hover {
  border-color: rgba(125, 211, 252, 0.5);
  background: rgba(14, 116, 144, 0.22);
  color: #ffffff;
}

.launch-screen__skip:disabled {
  cursor: default;
  opacity: 0.55;
}

.launch-screen__skip-time {
  color: rgba(186, 230, 253, 0.55);
  font-family: ui-monospace, monospace;
  font-size: 10px;
}

.launch-title {
  position: absolute;
  z-index: 5;
  right: max(24px, env(safe-area-inset-right));
  bottom: clamp(128px, 13vh, 170px);
  left: max(24px, env(safe-area-inset-left));
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
  opacity: 0;
  transform: translateY(24px);
  transition:
    opacity 760ms ease-out,
    transform 760ms cubic-bezier(0.16, 1, 0.3, 1);
  pointer-events: none;
}

.launch-title::before {
  position: absolute;
  z-index: -1;
  inset: -34px max(-8vw, -120px);
  background: radial-gradient(
    ellipse at center,
    rgba(2, 7, 19, 0.88) 0%,
    rgba(2, 7, 19, 0.58) 42%,
    transparent 76%
  );
  content: '';
}

.launch-title--visible {
  opacity: 1;
  transform: translateY(0);
}

.launch-title__eyebrow {
  display: flex;
  width: min(680px, 86vw);
  align-items: center;
  gap: 14px;
  justify-content: center;
  color: rgba(186, 230, 253, 0.6);
  font-family: ui-monospace, monospace;
  font-size: clamp(7px, 0.7vw, 10px);
  letter-spacing: 0.18em;
}

.launch-title__eyebrow span {
  height: 1px;
  flex: 1 1 80px;
  background: linear-gradient(90deg, transparent, rgba(125, 211, 252, 0.58));
}

.launch-title__eyebrow span:last-child {
  background: linear-gradient(90deg, rgba(125, 211, 252, 0.58), transparent);
}

.launch-title__reveal {
  position: relative;
  margin-top: 15px;
  overflow: hidden;
  padding: 2px 30px 8px;
}

.launch-title h1 {
  margin: 0;
  color: #ffffff;
  font-family: 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
  font-size: clamp(28px, 4vw, 60px);
  font-weight: 600;
  letter-spacing: clamp(0.08em, 0.34vw, 0.2em);
  line-height: 1.2;
  text-shadow:
    0 0 18px rgba(125, 211, 252, 0.34),
    0 0 48px rgba(14, 165, 233, 0.16);
}

.launch-title__reveal i {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -44%;
  width: 38%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.06) 35%,
    rgba(255, 255, 255, 0.74) 50%,
    rgba(246, 200, 95, 0.18) 64%,
    transparent 100%
  );
  mix-blend-mode: screen;
  opacity: 0;
  transform: translateX(0);
}

.launch-title--visible .launch-title__reveal i {
  animation: launch-title-scan 1.35s ease-out 120ms 1 both;
}

.launch-title p {
  margin: 8px 0 0;
  color: rgba(224, 242, 254, 0.68);
  font-size: clamp(11px, 1.15vw, 15px);
  letter-spacing: 0.28em;
}

.launch-status {
  position: absolute;
  z-index: 10;
  right: max(24px, env(safe-area-inset-right));
  bottom: max(24px, env(safe-area-inset-bottom));
  left: max(24px, env(safe-area-inset-left));
  margin: 0 auto;
  max-width: 980px;
}

.launch-status__copy,
.launch-status__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: rgba(224, 242, 254, 0.7);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.launch-status__copy {
  justify-content: center;
  margin-bottom: 12px;
}

.launch-status__track {
  height: 2px;
  overflow: hidden;
  background: rgba(125, 211, 252, 0.12);
}

.launch-status__track span {
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, #0284c7, #67e8f9 66%, #f6c85f);
  box-shadow: 0 0 14px rgba(103, 232, 249, 0.75);
  transform: scaleX(0);
  transform-origin: left center;
  will-change: transform;
}

.launch-status__meta {
  margin-top: 9px;
  color: rgba(148, 163, 184, 0.48);
  font-family: ui-monospace, monospace;
  font-size: 8px;
  letter-spacing: 0.12em;
}

.fallback-globe {
  position: absolute;
  z-index: 0;
  top: 50%;
  left: 50%;
  width: min(68vmin, 650px);
  aspect-ratio: 1;
  transform: translate(-50%, -54%);
}

.fallback-globe__halo,
.fallback-globe__sphere,
.fallback-globe__orbit {
  position: absolute;
  border-radius: 50%;
}

.fallback-globe__halo {
  inset: -12%;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.2), transparent 66%);
}

.fallback-globe__sphere {
  inset: 7%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(103, 232, 249, 0.46);
  background:
    radial-gradient(circle at 62% 42%, rgba(246, 200, 95, 0.16), transparent 18%),
    repeating-radial-gradient(circle at center, transparent 0 30px, rgba(56, 189, 248, 0.07) 31px 32px),
    linear-gradient(140deg, rgba(14, 116, 144, 0.16), rgba(2, 7, 19, 0.3));
  box-shadow:
    inset 0 0 60px rgba(14, 165, 233, 0.12),
    0 0 50px rgba(14, 165, 233, 0.15);
}

.fallback-globe__china {
  border: 1px solid rgba(246, 200, 95, 0.68);
  padding: 10px 18px;
  color: #fde68a;
  font-size: clamp(18px, 3vw, 34px);
  letter-spacing: 0.28em;
  text-shadow: 0 0 18px rgba(246, 200, 95, 0.55);
}

.fallback-globe__orbit {
  inset: 3%;
  border: 1px solid rgba(56, 189, 248, 0.18);
  transform: rotateX(64deg) rotateZ(12deg);
}

.fallback-globe__orbit--two {
  inset: 0;
  border-color: rgba(246, 200, 95, 0.14);
  transform: rotateY(65deg) rotateZ(-18deg);
}

@keyframes launch-title-scan {
  0% {
    opacity: 0;
    transform: translateX(0);
  }
  18% {
    opacity: 1;
  }
  72% {
    opacity: 0.7;
  }
  100% {
    opacity: 0;
    transform: translateX(495%);
  }
}

@media (max-width: 767px) {
  .launch-screen__header {
    top: max(16px, env(safe-area-inset-top));
    right: max(16px, env(safe-area-inset-right));
    left: max(16px, env(safe-area-inset-left));
  }

  .launch-screen__brand-mark {
    width: 38px;
    height: 38px;
  }

  .launch-screen__brand small {
    display: none;
  }

  .launch-screen__skip {
    min-width: 96px;
    min-height: 44px;
    padding: 0 12px;
  }

  .launch-title {
    right: 16px;
    bottom: 130px;
    left: 16px;
  }

  .launch-title__reveal {
    padding-right: 4px;
    padding-left: 4px;
  }

  .launch-title h1 {
    max-width: 330px;
    letter-spacing: 0.08em;
  }

  .launch-title p {
    letter-spacing: 0.16em;
  }

  .launch-status {
    right: 16px;
    bottom: max(18px, env(safe-area-inset-bottom));
    left: 16px;
  }

  .launch-status__copy {
    font-size: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .launch-screen,
  .launch-title {
    transition: none;
  }

  .launch-title--visible .launch-title__reveal i {
    animation: none;
  }
}
</style>
