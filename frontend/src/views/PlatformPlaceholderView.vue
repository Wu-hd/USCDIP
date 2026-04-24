<script setup lang="ts">
import {
  ArrowLeft,
  BrainCircuit,
  Building2,
  Command,
  Construction,
  DoorOpen,
  Route
} from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getPlatform } from '@/services/platform';
import type { PlatformBoundary } from '@/types/api';

const route = useRoute();
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
