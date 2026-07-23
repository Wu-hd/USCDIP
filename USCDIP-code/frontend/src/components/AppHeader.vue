<script setup lang="ts">
import {
  Clock3,
  Expand,
  LogOut,
  Minimize,
  RefreshCw,
  ShieldCheck,
  Waves
} from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

const PLATFORM_LABELS = {
  MGMT: '综合管理平台',
  EMGC: '应急指挥平台',
  DIAG: '智能诊断中枢'
} as const;

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const { displayName, roleSummary, isLoggingOut } = storeToRefs(authStore);

const currentTime = ref('');
const refreshing = ref(false);
const isFullscreen = ref(false);
let clockTimer: ReturnType<typeof window.setInterval> | null = null;

const moduleName = computed(() => String(route.meta.permissionLabel ?? '全国综合态势'));
const platformName = computed(() => {
  const code = route.meta.platformCode;
  if (code && code in PLATFORM_LABELS) {
    return PLATFORM_LABELS[code];
  }
  return route.meta.authOnly ? '全国态势中心' : '业务工作台';
});

function updateClock(): void {
  currentTime.value = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date());
}

function refreshPage(): void {
  if (refreshing.value) return;
  refreshing.value = true;
  window.requestAnimationFrame(() => window.location.reload());
}

async function toggleFullscreen(): Promise<void> {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      await document.documentElement.requestFullscreen();
    }
  } catch {
    isFullscreen.value = Boolean(document.fullscreenElement);
  }
}

function syncFullscreenState(): void {
  isFullscreen.value = Boolean(document.fullscreenElement);
}

async function logout(): Promise<void> {
  if (isLoggingOut.value) return;
  await authStore.logoutCurrentSession();
  await router.replace('/portal');
}

onMounted(() => {
  updateClock();
  clockTimer = window.setInterval(updateClock, 1000);
  document.addEventListener('fullscreenchange', syncFullscreenState);
});

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer);
  clockTimer = null;
  document.removeEventListener('fullscreenchange', syncFullscreenState);
});
</script>

<template>
  <header class="app-global-header" aria-label="全局应用顶栏">
    <div class="app-frame app-global-header__grid">
      <div class="app-global-header__context">
        <RouterLink class="app-global-header__home focus-ring" to="/home" title="返回全国态势" aria-label="返回全国态势">
          <Waves class="h-5 w-5" aria-hidden="true" />
        </RouterLink>
        <div class="min-w-0">
          <p class="app-global-header__platform">{{ platformName }}</p>
          <p class="app-global-header__module">{{ moduleName }}</p>
        </div>
      </div>

      <RouterLink class="app-global-header__identity focus-ring" to="/home" aria-label="地下管网数字化健康监测系统首页">
        <span class="app-global-header__title">地下管网数字化健康监测系统</span>
        <span class="app-global-header__subtitle">UNDERGROUND PIPELINE DIGITAL HEALTH COMMAND</span>
      </RouterLink>

      <div class="app-global-header__actions">
        <div class="app-global-header__clock" aria-label="当前时间">
          <Clock3 class="h-4 w-4 text-cyan-200" aria-hidden="true" />
          <span class="font-mono">{{ currentTime }}</span>
        </div>

        <div class="app-global-header__user">
          <ShieldCheck class="h-4 w-4 shrink-0 text-emerald-300" aria-hidden="true" />
          <div class="min-w-0">
            <p class="truncate font-semibold text-white">{{ displayName }}</p>
            <p class="app-global-header__role">{{ roleSummary }}</p>
          </div>
        </div>

        <button class="icon-button focus-ring" type="button" title="刷新当前页面" aria-label="刷新当前页面" :disabled="refreshing" @click="refreshPage">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': refreshing }" aria-hidden="true" />
        </button>
        <button class="icon-button focus-ring" type="button" :title="isFullscreen ? '退出全屏' : '进入全屏'" :aria-label="isFullscreen ? '退出全屏' : '进入全屏'" @click="toggleFullscreen">
          <Minimize v-if="isFullscreen" class="h-4 w-4" aria-hidden="true" />
          <Expand v-else class="h-4 w-4" aria-hidden="true" />
        </button>
        <button class="app-global-header__logout focus-ring" type="button" title="退出登录" aria-label="退出登录" :disabled="isLoggingOut" @click="logout">
          <LogOut class="h-4 w-4" aria-hidden="true" />
          <span>退出登录</span>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-global-header {
  position: relative;
  z-index: 50;
  height: var(--app-header-height);
  border-bottom: 1px solid rgba(125, 211, 252, 0.16);
  background: rgba(3, 9, 20, 0.96);
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.22);
  color: #e2e8f0;
  backdrop-filter: blur(18px);
}

.app-global-header::after {
  position: absolute;
  right: 12%;
  bottom: -1px;
  left: 12%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.56), rgba(52, 211, 153, 0.38), transparent);
  content: '';
  pointer-events: none;
}

.app-global-header__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  height: 100%;
  gap: 18px;
  padding-inline: clamp(16px, 2vw, 32px);
}

.app-global-header__context,
.app-global-header__actions,
.app-global-header__user,
.app-global-header__clock,
.app-global-header__logout {
  display: flex;
  align-items: center;
}

.app-global-header__context {
  min-width: 0;
  gap: 12px;
}

.app-global-header__home {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(103, 232, 249, 0.28);
  background: rgba(34, 211, 238, 0.08);
  color: #a5f3fc;
  transition: border-color 180ms ease, background-color 180ms ease, color 180ms ease;
}

.app-global-header__home:hover {
  border-color: rgba(103, 232, 249, 0.5);
  background: rgba(34, 211, 238, 0.14);
  color: #ecfeff;
}

.app-global-header__platform {
  overflow: hidden;
  color: #67e8f9;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.app-global-header__module {
  overflow: hidden;
  margin-top: 4px;
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-global-header__identity {
  display: flex;
  min-width: 320px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #f8fafc;
  text-align: center;
  text-decoration: none;
}

.app-global-header__title {
  font-family: 'Space Grotesk', 'DM Sans', system-ui, sans-serif;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.15;
  text-shadow: 0 0 18px rgba(103, 232, 249, 0.14);
  white-space: nowrap;
}

.app-global-header__subtitle {
  margin-top: 5px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 9px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.app-global-header__actions {
  min-width: 0;
  justify-self: end;
  justify-content: flex-end;
  gap: 8px;
}

.app-global-header__clock,
.app-global-header__user {
  min-height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
}

.app-global-header__clock {
  gap: 8px;
  padding-inline: 12px;
  color: #cbd5e1;
  font-size: 11px;
  white-space: nowrap;
}

.app-global-header__user {
  max-width: 170px;
  gap: 9px;
  padding: 6px 11px;
  font-size: 11px;
}

.app-global-header__role {
  overflow: hidden;
  max-width: 130px;
  margin-top: 2px;
  color: #64748b;
  font-size: 9px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-global-header__logout {
  min-height: 40px;
  cursor: pointer;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.055);
  padding-inline: 14px;
  color: #f1f5f9;
  font-size: 12px;
  font-weight: 600;
  transition: border-color 180ms ease, background-color 180ms ease;
}

.app-global-header__logout:hover {
  border-color: rgba(248, 113, 113, 0.34);
  background: rgba(248, 113, 113, 0.1);
}

.app-global-header__logout:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1535px) {
  .app-global-header__clock {
    display: none;
  }
}

@media (max-width: 1199px) {
  .app-global-header__identity {
    min-width: 280px;
  }

  .app-global-header__subtitle,
  .app-global-header__role {
    display: none;
  }

  .app-global-header__user {
    max-width: 125px;
  }

  .app-global-header__logout {
    width: 40px;
    padding: 0;
  }

  .app-global-header__logout span {
    display: none;
  }
}

@media (max-width: 767px) {
  .app-global-header__grid {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-rows: 46px 56px;
    gap: 0 10px;
    padding-inline: 14px;
  }

  .app-global-header__identity {
    grid-column: 1 / -1;
    grid-row: 1;
    min-width: 0;
  }

  .app-global-header__title {
    font-size: 15px;
  }

  .app-global-header__context {
    grid-column: 1;
    grid-row: 2;
  }

  .app-global-header__home {
    width: 36px;
    height: 36px;
    flex-basis: 36px;
  }

  .app-global-header__platform {
    font-size: 9px;
  }

  .app-global-header__module {
    max-width: 120px;
    margin-top: 2px;
    font-size: 10px;
  }

  .app-global-header__actions {
    grid-column: 2;
    grid-row: 2;
  }

  .app-global-header__user {
    display: none;
  }

  .app-global-header .icon-button,
  .app-global-header__logout {
    width: 36px;
    height: 36px;
    min-height: 36px;
  }
}
</style>
