<script setup lang="ts">
import {
  ArrowLeft,
  BrainCircuit,
  Building2,
  Command,
  Construction,
  Database,
  DoorOpen,
  LockKeyhole,
  Pencil,
  Route
} from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getPlatform } from '@/services/platform';
import {
  checkPermissions,
  isManagedPlatformCode,
  PLATFORM_ACTIONS,
  PLATFORM_PERMISSION_REQUIREMENTS
} from '@/services/permissions';
import { useAuthStore } from '@/stores/auth';
import type { ButtonPermissionConfig, ManagedPlatformCode, PlatformBoundary } from '@/types/api';

const route = useRoute();
const authStore = useAuthStore();
const { user, roleSummary, permissionSummary } = storeToRefs(authStore);
const platform = ref<PlatformBoundary | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const message = ref('正在读取平台边界');
const traceId = ref('');

const platformCode = computed(() => String(route.meta.platformCode ?? ''));

const iconMap = {
  MGMT: Building2,
  EMGC: Command,
  DIAG: BrainCircuit
};

const pageIcon = computed(() => iconMap[platformCode.value as keyof typeof iconMap] ?? Route);
const managedPlatformCode = computed<ManagedPlatformCode | null>(() =>
  isManagedPlatformCode(platformCode.value) ? platformCode.value : null
);
const routeRequirement = computed(() =>
  managedPlatformCode.value ? PLATFORM_PERMISSION_REQUIREMENTS[managedPlatformCode.value] : null
);
const actionConfigs = computed(() =>
  managedPlatformCode.value ? PLATFORM_ACTIONS[managedPlatformCode.value] : []
);

async function loadPlatform() {
  state.value = 'loading';
  try {
    const response = await getPlatform(platformCode.value);
    platform.value = response.data;
    traceId.value = response.traceId;
    state.value = 'ready';
    message.value = '平台边界已同步';
  } catch (error) {
    state.value = 'error';
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '平台边界读取失败';
  }
}

function checkAction(action: ButtonPermissionConfig) {
  return checkPermissions(user.value, action.requiredPermissions, action.label);
}

function actionToneClass(action: ButtonPermissionConfig) {
  if (!checkAction(action).allowed) {
    return 'border-amber-400/20 bg-amber-400/10 text-amber-100';
  }
  if (action.tone === 'write' || action.tone === 'dispatch' || action.tone === 'model') {
    return 'border-orange-400/25 bg-orange-400/10 text-orange-100 hover:border-orange-300/40 hover:bg-orange-400/15';
  }
  return 'border-blue-400/25 bg-blue-400/10 text-blue-100 hover:border-blue-300/40 hover:bg-blue-400/15';
}

onMounted(() => {
  loadPlatform();
});
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-5xl flex-col justify-center px-4 py-10 sm:px-6">
      <section class="glass-panel p-6 sm:p-8">
        <RouterLink class="secondary-button focus-ring" to="/portal">
          <ArrowLeft class="h-4 w-4" />
          返回 Portal
        </RouterLink>

        <div class="mt-8 flex flex-col gap-6 md:flex-row md:items-start">
          <div
            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-primary/20 text-blue-100"
            aria-hidden="true"
          >
            <component :is="pageIcon" class="h-8 w-8" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">
              {{ platformCode }} Placeholder
            </p>
            <h1 class="mt-2 font-display text-3xl font-semibold text-white sm:text-4xl">
              {{ platform?.platformName ?? '平台入口占位' }}
            </h1>
            <p class="mt-4 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
              {{ platform?.description ?? message }}
            </p>

            <div class="mt-6 grid gap-3 sm:grid-cols-3">
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">路由前缀</p>
                <p class="mt-2 font-mono text-sm text-white">
                  {{ platform?.routePrefix ?? '-' }}
                </p>
              </div>
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">权限前缀</p>
                <p class="mt-2 font-mono text-sm text-white">
                  {{ platform?.permissionPrefix ?? '-' }}
                </p>
              </div>
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">写操作</p>
                <p class="mt-2 font-mono text-sm text-white">
                  {{ platform?.allowWrite ? 'allowWrite=true' : 'allowWrite=false' }}
                </p>
              </div>
            </div>

            <div class="mt-6 grid gap-3 md:grid-cols-3">
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-xs text-slate-400">入口权限</p>
                <p class="mt-2 font-mono text-sm text-blue-100">
                  {{ routeRequirement?.entryPermission ?? '-' }}
                </p>
              </div>
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-xs text-slate-400">菜单权限</p>
                <p class="mt-2 font-mono text-sm text-blue-100">
                  {{ routeRequirement?.menuPermission ?? '-' }}
                </p>
              </div>
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <p class="text-xs text-slate-400">当前角色</p>
                <p class="mt-2 line-clamp-2 text-sm text-white">{{ roleSummary }}</p>
              </div>
            </div>

            <section class="mt-6 rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p class="text-sm font-semibold text-white">按钮级权限</p>
                  <p class="mt-1 text-sm text-slate-400">{{ permissionSummary }}</p>
                </div>
                <Database class="h-5 w-5 text-blue-200" aria-hidden="true" />
              </div>

              <div class="mt-4 grid gap-3 sm:grid-cols-2">
                <button
                  v-for="action in actionConfigs"
                  :key="action.key"
                  class="min-h-[132px] rounded-lg border p-4 text-left transition-colors duration-200 focus-ring disabled:cursor-not-allowed disabled:opacity-80"
                  :class="actionToneClass(action)"
                  type="button"
                  :disabled="!checkAction(action).allowed"
                  :aria-disabled="!checkAction(action).allowed"
                >
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="font-semibold text-white">{{ action.label }}</p>
                      <p class="mt-1 text-sm leading-5 text-slate-300">{{ action.description }}</p>
                    </div>
                    <Pencil
                      v-if="checkAction(action).allowed"
                      class="h-5 w-5 shrink-0 text-current"
                      aria-hidden="true"
                    />
                    <LockKeyhole
                      v-else
                      class="h-5 w-5 shrink-0 text-current"
                      aria-hidden="true"
                    />
                  </div>
                  <div class="mt-3 flex flex-wrap gap-2">
                    <span
                      v-for="permission in action.requiredPermissions"
                      :key="permission"
                      class="rounded-md border border-white/10 bg-black/15 px-2 py-1 font-mono text-xs"
                    >
                      {{ permission }}
                    </span>
                  </div>
                  <p
                    v-if="!checkAction(action).allowed"
                    class="mt-3 text-xs leading-5 text-amber-100"
                  >
                    缺少 {{ checkAction(action).missingPermissions.join(' / ') }}
                  </p>
                </button>
              </div>
            </section>

            <div class="mt-6 rounded-lg border border-amber-400/20 bg-amber-400/10 p-4 text-sm leading-6 text-amber-50">
              <div class="flex gap-3">
                <Construction class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
                <p>
                  这是 F-01 的平台占位页，仅用于验证 Portal 路由和平台边界。
                  后续业务页面应在对应平台内建设，Portal 不承载资产编辑、应急处置或模型运营功能。
                </p>
              </div>
            </div>

            <div class="mt-5 flex flex-wrap items-center gap-3 text-xs text-slate-400">
              <span>{{ message }}</span>
              <span v-if="traceId" class="font-mono text-blue-100">TraceId {{ traceId }}</span>
            </div>
          </div>
        </div>
      </section>

      <RouterLink class="mx-auto mt-6 inline-flex items-center gap-2 text-sm text-slate-400 hover:text-white" to="/portal">
        <DoorOpen class="h-4 w-4" />
        回到统一入口
      </RouterLink>
    </div>
  </main>
</template>
