<script setup lang="ts">
import {
  AlertTriangle,
  ArrowRight,
  Bell,
  BrainCircuit,
  Building2,
  CheckCircle2,
  ClipboardList,
  Command,
  DatabaseZap,
  ExternalLink,
  KeyRound,
  Loader2,
  LockKeyhole,
  LogIn,
  LogOut,
  RefreshCcw,
  Route,
  ShieldAlert,
  ShieldCheck,
  TimerReset,
  Waves
} from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiClientError } from '@/services/api';
import {
  checkPlatformEntry,
  checkPlatformRoute,
  filterManagedPlatforms,
  PLATFORM_PERMISSION_REQUIREMENTS
} from '@/services/permissions';
import {
  getMenuBoundaries,
  getNotifications,
  getPlatforms,
  getWorkOrders
} from '@/services/portal';
import { useAuthStore } from '@/stores/auth';
import type {
  BoundarySpec,
  NotificationResponse,
  PageResponse,
  PlatformBoundary,
  WorkOrderResponse
} from '@/types/api';

type PanelState = 'idle' | 'loading' | 'ready' | 'empty' | 'auth-required' | 'error';

interface TodoPanel<T> {
  state: PanelState;
  total: number;
  items: T[];
  message: string;
  traceId: string;
}

const showEmergencyLogin = ref(false);
const emergencyUsername = ref('');
const emergencyPassword = ref('');
const emergencyLoading = ref(false);
const emergencyError = ref('');

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const {
  status,
  user,
  message,
  traceId,
  isLoggingOut,
  displayName,
  isAuthenticated,
  refreshState,
  authNotice,
  forceLogoutReason,
  accessTokenRemainingLabel
} =
  storeToRefs(authStore);

const platforms = ref<PlatformBoundary[]>([]);
const boundarySpec = ref<BoundarySpec | null>(null);
const platformState = ref<PanelState>('loading');
const platformMessage = ref('正在读取平台边界');
const platformTraceId = ref('');

const workOrders = ref<TodoPanel<WorkOrderResponse>>({
  state: 'idle',
  total: 0,
  items: [],
  message: '登录后查看待办工单',
  traceId: ''
});

const notifications = ref<TodoPanel<NotificationResponse>>({
  state: 'idle',
  total: 0,
  items: [],
  message: '登录后查看通知',
  traceId: ''
});

const platformIconMap = {
  MGMT: Building2,
  EMGC: Command,
  DIAG: BrainCircuit
};

const platformAccentMap = {
  MGMT: 'from-blue-500/20 to-cyan-400/10 text-blue-200',
  EMGC: 'from-orange-500/20 to-amber-400/10 text-orange-200',
  DIAG: 'from-violet-500/20 to-blue-400/10 text-violet-200'
};

const portalBoundary = computed(() =>
  boundarySpec.value?.platforms.find((item) => item.platformCode === 'PORTAL')
);

const visiblePlatforms = computed(() =>
  filterManagedPlatforms(platforms.value)
);

const todoTotal = computed(() => workOrders.value.total + notifications.value.total);
const portalNotice = computed(() =>
  route.query.auth === 'required' ? '请先登录并具备对应平台权限后再进入业务平台。' : ''
);

const authStatusLabel = computed(() => {
  if (status.value === 'authenticated') {
    return '已登录';
  }
  if (status.value === 'oidc-disabled') {
    return 'OIDC disabled';
  }
  if (status.value === 'checking') {
    return '校验中';
  }
  return '未登录';
});

const authStatusClass = computed(() => {
  if (status.value === 'authenticated') {
    return 'border-emerald-400/30 bg-emerald-400/10 text-emerald-200';
  }
  if (status.value === 'oidc-disabled') {
    return 'border-amber-400/30 bg-amber-400/10 text-amber-100';
  }
  return 'border-white/10 bg-white/[0.055] text-slate-300';
});

const refreshStateLabel = computed(() => {
  if (refreshState.value === 'refreshing') {
    return '正在刷新';
  }
  if (refreshState.value === 'refreshed') {
    return '会话已刷新';
  }
  if (refreshState.value === 'failed') {
    return '刷新失败';
  }
  return '空闲';
});

const refreshStateClass = computed(() => {
  if (refreshState.value === 'refreshing') {
    return 'border-blue-400/30 bg-blue-400/10 text-blue-100';
  }
  if (refreshState.value === 'refreshed') {
    return 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100';
  }
  if (refreshState.value === 'failed') {
    return 'border-rose-400/30 bg-rose-400/10 text-rose-100';
  }
  return 'border-white/10 bg-white/[0.045] text-slate-300';
});

function getPlatformIcon(code: string) {
  return platformIconMap[code as keyof typeof platformIconMap] ?? Route;
}

function getPlatformAccent(code: string) {
  return (
    platformAccentMap[code as keyof typeof platformAccentMap] ??
    'from-blue-500/20 to-slate-400/10 text-blue-200'
  );
}

function getPlatformEntryCheck(platform: PlatformBoundary) {
  return checkPlatformEntry(user.value, platform.platformCode);
}

function getPlatformRouteCheck(platform: PlatformBoundary) {
  return checkPlatformRoute(user.value, platform.platformCode as 'MGMT' | 'EMGC' | 'DIAG');
}

function getPlatformRequirement(code: string) {
  return PLATFORM_PERMISSION_REQUIREMENTS[code as keyof typeof PLATFORM_PERMISSION_REQUIREMENTS];
}

function getPlatformCardClass(platform: PlatformBoundary) {
  if (!getPlatformEntryCheck(platform).allowed) {
    return 'cursor-not-allowed border-amber-400/20 bg-amber-400/[0.055] opacity-80';
  }
  return 'cursor-pointer border-white/10 bg-white/[0.055] hover:border-white/25 hover:bg-white/[0.085]';
}

function formatTime(value: string | null | undefined) {
  if (!value) {
    return '未设置';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function statusTone(value: string | null | undefined) {
  const normalized = value?.toUpperCase() ?? '';
  if (['COMPLETED', 'CLOSED', 'SENT', 'DELIVERED'].includes(normalized)) {
    return 'text-emerald-200 bg-emerald-400/10 border-emerald-400/20';
  }
  if (['FAILED', 'TIMEOUT', 'OVERDUE'].includes(normalized)) {
    return 'text-rose-200 bg-rose-400/10 border-rose-400/20';
  }
  return 'text-amber-100 bg-amber-400/10 border-amber-400/20';
}

function applyTodoError<T>(target: TodoPanel<T>, error: unknown) {
  target.total = 0;
  target.items = [];

  if (error instanceof ApiClientError) {
    target.traceId = error.traceId ?? '';
    if (error.status === 401 || error.status === 403 || error.status === 503) {
      target.state = 'auth-required';
      target.message = '需登录并具备权限后查看';
      return;
    }
    target.state = 'error';
    target.message = error.message;
    return;
  }

  target.state = 'error';
  target.message = '待办读取失败';
}

function applyTodoPage<T>(target: TodoPanel<T>, page: PageResponse<T>, trace: string) {
  target.total = page.total;
  target.items = page.items;
  target.traceId = trace;
  target.state = page.total > 0 ? 'ready' : 'empty';
  target.message = page.total > 0 ? '已同步后端待办' : '暂无待办';
}

async function loadPlatforms() {
  platformState.value = 'loading';
  platformMessage.value = '正在读取平台边界';

  try {
    const [platformResponse, boundaryResponse] = await Promise.all([
      getPlatforms(),
      getMenuBoundaries()
    ]);
    platforms.value = platformResponse.data ?? [];
    boundarySpec.value = boundaryResponse.data;
    platformTraceId.value = platformResponse.traceId;
    platformState.value = visiblePlatforms.value.length > 0 ? 'ready' : 'empty';
    platformMessage.value =
      visiblePlatforms.value.length > 0 ? '平台边界已同步' : '未读取到三平台入口';
  } catch (error) {
    platformState.value = 'error';
    if (error instanceof ApiClientError) {
      platformTraceId.value = error.traceId ?? '';
      platformMessage.value = error.message;
      return;
    }
    platformMessage.value = '平台边界读取失败';
  }
}

async function loadTodos() {
  workOrders.value.state = 'loading';
  notifications.value.state = 'loading';

  const [workOrderResult, notificationResult] = await Promise.allSettled([
    getWorkOrders(),
    getNotifications()
  ]);

  if (workOrderResult.status === 'fulfilled' && workOrderResult.value.data) {
    applyTodoPage(workOrders.value, workOrderResult.value.data, workOrderResult.value.traceId);
  } else {
    applyTodoError(
      workOrders.value,
      workOrderResult.status === 'rejected' ? workOrderResult.reason : undefined
    );
  }

  if (notificationResult.status === 'fulfilled' && notificationResult.value.data) {
    applyTodoPage(
      notifications.value,
      notificationResult.value.data,
      notificationResult.value.traceId
    );
  } else {
    applyTodoError(
      notifications.value,
      notificationResult.status === 'rejected' ? notificationResult.reason : undefined
    );
  }
}

async function refreshAll() {
  await Promise.all([authStore.loadCurrentUser(), loadPlatforms()]);
  await loadTodos();
}

function openPlatform(platform: PlatformBoundary) {
  if (!getPlatformEntryCheck(platform).allowed) {
    return;
  }
  router.push(platform.routePrefix);
}

async function handleEmergencyLogin() {
  if (!emergencyUsername.value.trim() || !emergencyPassword.value) {
    emergencyError.value = '请输入用户名和密码';
    return;
  }
  emergencyLoading.value = true;
  emergencyError.value = '';
  try {
    const success = await authStore.emergencyLogin(
      emergencyUsername.value.trim(),
      emergencyPassword.value
    );
    if (success) {
      showEmergencyLogin.value = false;
      emergencyUsername.value = '';
      emergencyPassword.value = '';
      await refreshAll();
    } else {
      emergencyError.value = authStore.message || '登录失败';
    }
  } catch {
    emergencyError.value = '登录请求异常';
  } finally {
    emergencyLoading.value = false;
  }
}

onMounted(() => {
  authStore.bindSessionEvents();
  refreshAll();
});
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex w-full max-w-7xl flex-col gap-5 px-4 py-4 sm:px-6 lg:px-8">
      <header class="glass-panel flex flex-col gap-4 p-4 sm:p-5 lg:flex-row lg:items-center">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <div
            class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-primarySoft/30 bg-primary/20 text-blue-100"
            aria-hidden="true"
          >
            <Waves class="h-6 w-6" />
          </div>
          <div class="min-w-0">
            <p class="font-display text-lg font-semibold text-white sm:text-xl">
              地下管网数字化健康监测系统
            </p>
            <p class="truncate text-sm text-slate-400">
              SSO -> Portal -> 综合管理 / 应急指挥 / 智能诊断
            </p>
          </div>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div
            class="inline-flex min-h-10 items-center gap-2 rounded-lg border px-3 py-2 text-sm"
            :class="authStatusClass"
          >
            <Loader2 v-if="status === 'checking'" class="h-4 w-4 animate-spin" />
            <ShieldCheck v-else class="h-4 w-4" />
            <span class="font-medium">{{ authStatusLabel }}</span>
            <span class="max-w-[11rem] truncate text-slate-300">{{ displayName }}</span>
          </div>
          <template v-if="!isAuthenticated">
            <button
              class="primary-button focus-ring"
              type="button"
              @click="authStore.startLogin()"
            >
              <LogIn class="h-4 w-4" />
              统一登录
            </button>
            <button
              class="secondary-button focus-ring"
              type="button"
              @click="showEmergencyLogin = !showEmergencyLogin"
            >
              <KeyRound class="h-4 w-4" />
              紧急登录
            </button>
          </template>
          <button
            v-else
            class="secondary-button focus-ring"
            type="button"
            :disabled="isLoggingOut"
            @click="authStore.logoutCurrentSession()"
          >
            <LogOut class="h-4 w-4" />
            单点退出
          </button>
          <button class="icon-button focus-ring" type="button" aria-label="刷新门户数据" @click="refreshAll">
            <RefreshCcw class="h-4 w-4" />
          </button>
        </div>
      </header>

      <div
        v-if="portalNotice"
        class="rounded-lg border border-amber-400/25 bg-amber-400/10 px-4 py-3 text-sm text-amber-50"
        role="alert"
      >
        {{ portalNotice }}
      </div>

      <!-- Emergency Login Panel -->
      <Transition name="slide-fade">
        <section
          v-if="showEmergencyLogin && !isAuthenticated"
          class="glass-panel overflow-hidden"
        >
          <div class="border-b border-white/10 bg-gradient-to-r from-amber-500/10 to-orange-400/5 px-5 py-4">
            <div class="flex items-center gap-3">
              <div class="flex h-10 w-10 items-center justify-center rounded-lg border border-amber-400/30 bg-amber-400/15">
                <KeyRound class="h-5 w-5 text-amber-200" />
              </div>
              <div>
                <p class="font-display text-base font-semibold text-white">紧急旁路登录</p>
                <p class="text-sm text-slate-400">OIDC 关闭时使用应急账号登录系统</p>
              </div>
            </div>
          </div>
          <form class="px-5 py-5" @submit.prevent="handleEmergencyLogin">
            <div class="grid gap-4 sm:grid-cols-[1fr_1fr_auto]">
              <div class="space-y-1.5">
                <label for="emergency-username" class="block text-sm font-medium text-slate-300">用户名</label>
                <input
                  id="emergency-username"
                  v-model="emergencyUsername"
                  type="text"
                  autocomplete="username"
                  placeholder="输入应急账号用户名"
                  class="w-full rounded-lg border border-white/15 bg-white/[0.065] px-3 py-2.5 text-sm text-white placeholder-slate-500 outline-none transition-colors focus:border-blue-400/50 focus:bg-white/[0.08] focus:ring-1 focus:ring-blue-400/30"
                  :disabled="emergencyLoading"
                />
              </div>
              <div class="space-y-1.5">
                <label for="emergency-password" class="block text-sm font-medium text-slate-300">密码</label>
                <input
                  id="emergency-password"
                  v-model="emergencyPassword"
                  type="password"
                  autocomplete="current-password"
                  placeholder="输入密码"
                  class="w-full rounded-lg border border-white/15 bg-white/[0.065] px-3 py-2.5 text-sm text-white placeholder-slate-500 outline-none transition-colors focus:border-blue-400/50 focus:bg-white/[0.08] focus:ring-1 focus:ring-blue-400/30"
                  :disabled="emergencyLoading"
                />
              </div>
              <div class="flex items-end">
                <button
                  type="submit"
                  class="primary-button focus-ring w-full sm:w-auto"
                  :disabled="emergencyLoading"
                >
                  <Loader2 v-if="emergencyLoading" class="h-4 w-4 animate-spin" />
                  <KeyRound v-else class="h-4 w-4" />
                  {{ emergencyLoading ? '登录中' : '登录' }}
                </button>
              </div>
            </div>
            <div
              v-if="emergencyError"
              class="mt-4 flex items-start gap-2 rounded-lg border border-rose-400/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-100"
              role="alert"
            >
              <AlertTriangle class="mt-0.5 h-4 w-4 shrink-0" />
              <span>{{ emergencyError }}</span>
            </div>
            <p class="mt-3 text-xs text-slate-500">
              管理员：admin / admin123! &nbsp;·&nbsp; 应急指挥：bg_active_hz / BreakGlass123!
            </p>
          </form>
        </section>
      </Transition>

      <section class="grid gap-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.7fr)]">
        <div class="glass-panel overflow-hidden p-5 sm:p-6">
          <div class="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">
                Portal Gateway
              </p>
              <h1 class="mt-2 font-display text-3xl font-semibold leading-tight text-white sm:text-4xl">
                平台选择门户
              </h1>
              <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
                这里只负责登录态、入口导航、跨平台跳转、单点退出与待办汇总。
                业务建档、指挥处置和模型运营均进入对应平台。
              </p>
            </div>
            <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 md:min-w-[22rem]">
              <div class="glass-panel-muted p-3">
                <p class="text-xs text-slate-400">三平台入口</p>
                <p class="mt-1 font-display text-2xl font-semibold text-white">
                  {{ authStore.accessiblePlatformCount }} / {{ visiblePlatforms.length }}
                </p>
              </div>
              <div class="glass-panel-muted p-3">
                <p class="text-xs text-slate-400">统一待办</p>
                <p class="mt-1 font-display text-2xl font-semibold text-amber-100">
                  {{ todoTotal }}
                </p>
              </div>
              <div class="glass-panel-muted col-span-2 p-3 sm:col-span-1">
                <p class="text-xs text-slate-400">Portal 写操作</p>
                <p class="mt-1 font-display text-2xl font-semibold text-emerald-200">
                  {{ portalBoundary?.allowWrite ? '开放' : '关闭' }}
                </p>
              </div>
            </div>
          </div>

          <div class="mt-6 grid gap-4 md:grid-cols-3">
            <button
              v-for="platform in visiblePlatforms"
              :key="platform.platformCode"
              class="group min-h-[244px] rounded-lg border p-5 text-left shadow-glow transition-colors duration-200 focus-ring disabled:cursor-not-allowed"
              :class="getPlatformCardClass(platform)"
              type="button"
              :disabled="!getPlatformEntryCheck(platform).allowed"
              :aria-describedby="`${platform.platformCode}-permission-state`"
              @click="openPlatform(platform)"
            >
              <div
                class="flex h-12 w-12 items-center justify-center rounded-lg border border-white/10 bg-gradient-to-br"
                :class="getPlatformAccent(platform.platformCode)"
                aria-hidden="true"
              >
                <component :is="getPlatformIcon(platform.platformCode)" class="h-6 w-6" />
              </div>
              <div class="mt-5 flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="font-display text-xl font-semibold text-white">
                    {{ platform.platformName }}
                  </p>
                  <p class="mt-1 font-mono text-xs text-blue-200">
                    {{ platform.platformCode }} / {{ platform.routePrefix }}
                  </p>
                </div>
                <ArrowRight
                  v-if="getPlatformEntryCheck(platform).allowed"
                  class="mt-1 h-5 w-5 shrink-0 text-slate-400 transition-colors duration-200 group-hover:text-white"
                />
                <LockKeyhole
                  v-else
                  class="mt-1 h-5 w-5 shrink-0 text-amber-200"
                  aria-hidden="true"
                />
              </div>
              <p class="mt-4 line-clamp-3 text-sm leading-6 text-slate-300">
                {{ platform.description }}
              </p>
              <div
                :id="`${platform.platformCode}-permission-state`"
                class="mt-5 rounded-lg border px-3 py-2 text-sm"
                :class="getPlatformEntryCheck(platform).allowed ? 'border-blue-400/20 bg-blue-400/10 text-blue-100' : 'border-amber-400/20 bg-amber-400/10 text-amber-100'"
              >
                <div class="flex items-center gap-2 font-semibold">
                  <ExternalLink
                    v-if="getPlatformEntryCheck(platform).allowed"
                    class="h-4 w-4"
                    aria-hidden="true"
                  />
                  <LockKeyhole v-else class="h-4 w-4" aria-hidden="true" />
                  <span>
                    {{ getPlatformEntryCheck(platform).allowed ? '入口权限已通过' : '入口已锁定' }}
                  </span>
                </div>
                <p class="mt-1 font-mono text-xs">
                  {{ getPlatformRequirement(platform.platformCode)?.entryPermission ?? '-' }}
                </p>
                <p
                  v-if="getPlatformEntryCheck(platform).allowed && !getPlatformRouteCheck(platform).allowed"
                  class="mt-1 text-xs text-amber-100"
                >
                  仍缺少 {{ getPlatformRouteCheck(platform).missingPermissions.join(' / ') }}
                </p>
              </div>
            </button>
          </div>

          <div
            v-if="platformState !== 'ready'"
            class="mt-5 rounded-lg border border-white/10 bg-white/[0.045] p-4 text-sm text-slate-300"
            role="status"
          >
            {{ platformMessage }}
          </div>
        </div>

        <aside class="flex flex-col gap-5">
          <section class="glass-panel p-5">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-white">登录态</p>
                <p class="mt-1 text-sm text-slate-400">{{ message }}</p>
              </div>
              <CheckCircle2
                v-if="isAuthenticated"
                class="h-5 w-5 shrink-0 text-emerald-300"
                aria-hidden="true"
              />
              <AlertTriangle v-else class="h-5 w-5 shrink-0 text-amber-300" aria-hidden="true" />
            </div>
            <dl class="mt-4 grid gap-3 text-sm">
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">用户</dt>
                <dd class="min-w-0 truncate text-right text-slate-100">
                  {{ user?.userId ?? '未登录' }}
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">认证类型</dt>
                <dd class="min-w-0 truncate text-right text-slate-100">
                  {{ user?.authMode ?? user?.authenticationType ?? '-' }}
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">Access 剩余</dt>
                <dd class="min-w-0 truncate text-right text-slate-100">
                  {{ accessTokenRemainingLabel }}
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">角色</dt>
                <dd class="min-w-0 truncate text-right text-slate-100">
                  {{ authStore.roleSummary }}
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">权限</dt>
                <dd class="min-w-0 truncate text-right text-slate-100">
                  {{ authStore.permissionSummary }}
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">刷新状态</dt>
                <dd>
                  <span
                    class="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-semibold"
                    :class="refreshStateClass"
                  >
                    <TimerReset
                      class="h-3.5 w-3.5"
                      :class="{ 'animate-spin': refreshState === 'refreshing' }"
                      aria-hidden="true"
                    />
                    {{ refreshStateLabel }}
                  </span>
                </dd>
              </div>
              <div class="flex justify-between gap-3">
                <dt class="text-slate-400">TraceId</dt>
                <dd class="min-w-0 truncate text-right font-mono text-xs text-blue-100">
                  {{ traceId || platformTraceId || '-' }}
                </dd>
              </div>
            </dl>
            <div
              v-if="authNotice || forceLogoutReason"
              class="mt-4 rounded-lg border p-3 text-sm"
              :class="forceLogoutReason ? 'border-rose-400/20 bg-rose-400/10 text-rose-100' : 'border-blue-400/20 bg-blue-400/10 text-blue-100'"
              :role="forceLogoutReason ? 'alert' : 'status'"
              aria-live="polite"
            >
              <div class="flex gap-2">
                <ShieldAlert
                  v-if="forceLogoutReason"
                  class="mt-0.5 h-4 w-4 shrink-0"
                  aria-hidden="true"
                />
                <TimerReset v-else class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <p>{{ forceLogoutReason || authNotice }}</p>
              </div>
            </div>
          </section>

          <section class="glass-panel p-5">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-white">边界校验</p>
                <p class="mt-1 text-sm text-slate-400">支撑层不作为第四入口</p>
              </div>
              <DatabaseZap class="h-5 w-5 text-blue-200" aria-hidden="true" />
            </div>
            <div class="mt-4 space-y-3">
              <div
                class="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2 text-sm"
              >
                <span class="text-slate-300">Portal allowWrite</span>
                <span class="font-semibold text-emerald-200">
                  {{ portalBoundary?.allowWrite ? 'true' : 'false' }}
                </span>
              </div>
              <div
                class="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2 text-sm"
              >
                <span class="text-slate-300">展示入口</span>
                <span class="font-semibold text-blue-100">MGMT / EMGC / DIAG</span>
              </div>
            </div>
          </section>
        </aside>
      </section>

      <section class="grid gap-5 lg:grid-cols-2">
        <article class="glass-panel p-5">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="font-display text-lg font-semibold text-white">待办工单</p>
              <p class="mt-1 text-sm text-slate-400">{{ workOrders.message }}</p>
            </div>
            <ClipboardList class="h-5 w-5 text-amber-200" aria-hidden="true" />
          </div>

          <div v-if="workOrders.state === 'loading'" class="mt-5 flex items-center gap-2 text-sm text-slate-300">
            <Loader2 class="h-4 w-4 animate-spin" />
            正在同步工单
          </div>
          <div v-else-if="workOrders.items.length" class="mt-5 space-y-3">
            <div
              v-for="item in workOrders.items"
              :key="item.workOrderId"
              class="rounded-lg border border-white/10 bg-white/[0.045] p-4"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-white">
                    {{ item.description || item.workOrderId }}
                  </p>
                  <p class="mt-1 font-mono text-xs text-slate-400">
                    {{ item.workOrderId }} / {{ item.priority }}
                  </p>
                </div>
                <span class="rounded-md border px-2 py-1 text-xs" :class="statusTone(item.status)">
                  {{ item.status }}
                </span>
              </div>
              <p class="mt-3 text-xs text-slate-400">
                SLA {{ formatTime(item.slaDueAt) }} · {{ item.assignee ?? '未分派' }}
              </p>
            </div>
          </div>
          <div v-else class="mt-5 rounded-lg border border-white/10 bg-white/[0.045] p-4 text-sm text-slate-300">
            {{ workOrders.message }}
          </div>
        </article>

        <article class="glass-panel p-5">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="font-display text-lg font-semibold text-white">通知汇总</p>
              <p class="mt-1 text-sm text-slate-400">{{ notifications.message }}</p>
            </div>
            <Bell class="h-5 w-5 text-blue-200" aria-hidden="true" />
          </div>

          <div v-if="notifications.state === 'loading'" class="mt-5 flex items-center gap-2 text-sm text-slate-300">
            <Loader2 class="h-4 w-4 animate-spin" />
            正在同步通知
          </div>
          <div v-else-if="notifications.items.length" class="mt-5 space-y-3">
            <div
              v-for="item in notifications.items"
              :key="item.notificationId"
              class="rounded-lg border border-white/10 bg-white/[0.045] p-4"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-white">{{ item.title }}</p>
                  <p class="mt-1 line-clamp-2 text-xs leading-5 text-slate-400">
                    {{ item.content }}
                  </p>
                </div>
                <span class="rounded-md border px-2 py-1 text-xs" :class="statusTone(item.status)">
                  {{ item.status }}
                </span>
              </div>
              <p class="mt-3 text-xs text-slate-400">
                {{ item.recipientUsername }} · {{ formatTime(item.createdAt) }}
              </p>
            </div>
          </div>
          <div v-else class="mt-5 rounded-lg border border-white/10 bg-white/[0.045] p-4 text-sm text-slate-300">
            {{ notifications.message }}
          </div>
        </article>
      </section>

      <footer class="pb-3 text-center text-xs text-slate-500">
        F-01 Portal 不承载业务编辑页。平台支撑层仅作为工程底座，不作为用户可见第四入口。
      </footer>
    </div>
  </main>
</template>

<style scoped>
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.25s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
</style>
