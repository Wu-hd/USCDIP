<script setup lang="ts">
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  LogIn,
  RotateCcw,
  ShieldCheck,
  XCircle
} from 'lucide-vue-next';
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useOidcCallback } from '@/composables/useOidcCallback';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const {
  status,
  title,
  message,
  traceId,
  providerError,
  providerErrorDescription,
  canRetry,
  isProcessing,
  steps,
  isTerminalError,
  processCallback,
  retry
} = useOidcCallback(router);

const statusIcon = computed(() => {
  if (status.value === 'success' || status.value === 'completed') {
    return CheckCircle2;
  }
  if (status.value === 'cancelled') {
    return XCircle;
  }
  if (status.value === 'error') {
    return AlertTriangle;
  }
  return Loader2;
});

const statusClass = computed(() => {
  if (status.value === 'success' || status.value === 'completed') {
    return 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100';
  }
  if (status.value === 'cancelled') {
    return 'border-amber-400/30 bg-amber-400/10 text-amber-100';
  }
  if (status.value === 'error') {
    return 'border-rose-400/30 bg-rose-400/10 text-rose-100';
  }
  return 'border-blue-400/30 bg-blue-400/10 text-blue-100';
});

function stepClass(stepStatus: string) {
  if (stepStatus === 'done') {
    return 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100';
  }
  if (stepStatus === 'failed') {
    return 'border-rose-400/30 bg-rose-400/10 text-rose-100';
  }
  if (stepStatus === 'active') {
    return 'border-blue-400/30 bg-blue-400/10 text-blue-100';
  }
  return 'border-white/10 bg-white/[0.045] text-slate-300';
}

function stepIcon(stepStatus: string) {
  if (stepStatus === 'done') {
    return CheckCircle2;
  }
  if (stepStatus === 'failed') {
    return AlertTriangle;
  }
  if (stepStatus === 'active') {
    return Loader2;
  }
  return ShieldCheck;
}

function retryExchange() {
  retry(route.query);
}

function backToPortal() {
  router.replace('/portal');
}

onMounted(() => {
  processCallback(route.query);
});
</script>

<template>
  <main class="portal-shell">
    <div class="app-frame app-viewport flex flex-col justify-center px-4 py-10 sm:px-6 lg:px-8">
      <section class="glass-panel p-5 sm:p-7">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div class="min-w-0">
            <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">
              OIDC Callback
            </p>
            <h1 class="mt-2 font-display text-3xl font-semibold text-white sm:text-4xl">
              OIDC 登录回调页
            </h1>
            <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
              正在完成认证中心回跳后的本地令牌交换。此页面只处理登录回调，不承载业务功能。
            </p>
          </div>

          <div
            class="inline-flex min-h-11 items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold"
            :class="statusClass"
            aria-live="polite"
          >
            <component
              :is="statusIcon"
              class="h-5 w-5"
              :class="{ 'animate-spin': status === 'loading' }"
              aria-hidden="true"
            />
            {{ title }}
          </div>
        </div>

        <div class="mt-6 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(280px,0.45fr)]">
          <article class="glass-panel-muted p-5">
            <div :role="isTerminalError ? 'alert' : 'status'" aria-live="polite">
              <p class="font-display text-xl font-semibold text-white">{{ title }}</p>
              <p class="mt-2 text-sm leading-6 text-slate-300">{{ message }}</p>
            </div>

            <div
              v-if="providerError"
              class="mt-4 rounded-lg border border-amber-400/20 bg-amber-400/10 p-4 text-sm text-amber-50"
            >
              <p class="font-semibold">Provider 返回</p>
              <p class="mt-1 font-mono text-xs">{{ providerError }}</p>
              <p v-if="providerErrorDescription" class="mt-2 leading-6">
                {{ providerErrorDescription }}
              </p>
            </div>

            <div class="mt-5 grid gap-3 sm:grid-cols-3">
              <button
                class="primary-button focus-ring"
                type="button"
                :disabled="isProcessing"
                @click="authStore.startLogin()"
              >
                <LogIn class="h-4 w-4" />
                重新登录
              </button>
              <button class="secondary-button focus-ring" type="button" @click="backToPortal">
                <ArrowLeft class="h-4 w-4" />
                返回 Portal
              </button>
              <button
                class="secondary-button focus-ring"
                type="button"
                :disabled="!canRetry || isProcessing"
                @click="retryExchange"
              >
                <RotateCcw class="h-4 w-4" />
                重试交换
              </button>
            </div>

            <dl class="mt-5 grid gap-3 text-sm">
              <div class="flex justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2">
                <dt class="text-slate-400">回调状态</dt>
                <dd class="text-right font-mono text-xs text-slate-100">{{ status }}</dd>
              </div>
              <div class="flex justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.045] px-3 py-2">
                <dt class="text-slate-400">TraceId</dt>
                <dd class="min-w-0 truncate text-right font-mono text-xs text-blue-100">
                  {{ traceId || '-' }}
                </dd>
              </div>
            </dl>
          </article>

          <aside class="glass-panel-muted p-5">
            <p class="font-display text-lg font-semibold text-white">处理步骤</p>
            <div class="mt-4 space-y-3">
              <div
                v-for="step in steps"
                :key="step.key"
                class="rounded-lg border p-3"
                :class="stepClass(step.status)"
              >
                <div class="flex items-start gap-3">
                  <component
                    :is="stepIcon(step.status)"
                    class="mt-0.5 h-4 w-4 shrink-0"
                    :class="{ 'animate-spin': step.status === 'active' }"
                    aria-hidden="true"
                  />
                  <div class="min-w-0">
                    <p class="text-sm font-semibold">{{ step.label }}</p>
                    <p class="mt-1 text-xs leading-5 opacity-80">{{ step.detail }}</p>
                  </div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </section>
    </div>
  </main>
</template>
