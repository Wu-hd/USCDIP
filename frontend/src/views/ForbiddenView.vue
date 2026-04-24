<script setup lang="ts">
import {
  ArrowLeft,
  BadgeAlert,
  LockKeyhole,
  LogIn,
  RefreshCcw,
  ShieldAlert,
  ShieldCheck
} from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { extractPermissionSnapshot } from '@/services/permissions';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const authStore = useAuthStore();
const { user, roleSummary, permissionSummary, traceId, message, isAuthenticated } =
  storeToRefs(authStore);
const isRefreshing = ref(false);

const missingPermissions = computed(() => splitQueryList(route.query.missing));
const requiredPermissions = computed(() => splitQueryList(route.query.required));
const platformCode = computed(() => String(route.query.platform ?? '-'));
const fromPath = computed(() => String(route.query.from ?? '/portal'));
const reason = computed(() =>
  route.query.reason === 'ROUTE_PERMISSION_META_INVALID' ? '路由权限配置异常' : '权限不足'
);
const snapshot = computed(() => extractPermissionSnapshot(user.value));

async function refreshSnapshot() {
  isRefreshing.value = true;
  try {
    await authStore.refreshPermissionSnapshot();
  } finally {
    isRefreshing.value = false;
  }
}

function splitQueryList(value: unknown): string[] {
  if (typeof value !== 'string' || !value) {
    return [];
  }
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-5xl flex-col justify-center px-4 py-10 sm:px-6">
      <section class="glass-panel overflow-hidden p-6 sm:p-8">
        <div class="flex flex-col gap-6 lg:flex-row lg:items-start">
          <div
            class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-amber-400/30 bg-amber-400/10 text-amber-100 shadow-amber"
            aria-hidden="true"
          >
            <LockKeyhole class="h-8 w-8" />
          </div>

          <div class="min-w-0 flex-1">
            <p class="text-sm font-semibold uppercase tracking-[0.18em] text-amber-200">
              Access Control
            </p>
            <h1 class="mt-2 font-display text-3xl font-semibold text-white sm:text-4xl">
              {{ reason }}
            </h1>
            <p class="mt-4 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base" role="alert">
              当前会话不能访问
              <span class="font-mono text-blue-100">{{ platformCode }}</span>
              平台路由。请确认登录用户角色、入口权限与菜单权限是否已同步。
            </p>

            <div class="mt-6 grid gap-3 md:grid-cols-3">
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">来源路由</p>
                <p class="mt-2 break-all font-mono text-sm text-white">{{ fromPath }}</p>
              </div>
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">当前角色</p>
                <p class="mt-2 line-clamp-2 text-sm text-white">{{ roleSummary }}</p>
              </div>
              <div class="glass-panel-muted p-4">
                <p class="text-xs text-slate-400">权限快照</p>
                <p class="mt-2 text-sm text-white">{{ permissionSummary }}</p>
              </div>
            </div>

            <div class="mt-5 grid gap-4 lg:grid-cols-2">
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <div class="flex items-center gap-2 text-sm font-semibold text-slate-100">
                  <ShieldCheck class="h-4 w-4 text-blue-200" aria-hidden="true" />
                  路由要求
                </div>
                <div class="mt-3 flex flex-wrap gap-2">
                  <span
                    v-for="permission in requiredPermissions"
                    :key="permission"
                    class="rounded-md border border-blue-400/20 bg-blue-400/10 px-2 py-1 font-mono text-xs text-blue-100"
                  >
                    {{ permission }}
                  </span>
                  <span v-if="!requiredPermissions.length" class="text-sm text-slate-400">未读取到要求</span>
                </div>
              </div>

              <div class="rounded-lg border border-rose-400/20 bg-rose-400/10 p-4">
                <div class="flex items-center gap-2 text-sm font-semibold text-rose-100">
                  <ShieldAlert class="h-4 w-4" aria-hidden="true" />
                  缺失权限
                </div>
                <div class="mt-3 flex flex-wrap gap-2">
                  <span
                    v-for="permission in missingPermissions"
                    :key="permission"
                    class="rounded-md border border-rose-400/20 bg-rose-400/10 px-2 py-1 font-mono text-xs text-rose-100"
                  >
                    {{ permission }}
                  </span>
                  <span v-if="!missingPermissions.length" class="text-sm text-rose-100">
                    请检查路由 meta 或重新刷新权限快照
                  </span>
                </div>
              </div>
            </div>

            <div class="mt-5 rounded-lg border border-white/10 bg-white/[0.045] p-4 text-sm text-slate-300">
              <div class="flex gap-3">
                <BadgeAlert class="mt-0.5 h-5 w-5 shrink-0 text-amber-200" aria-hidden="true" />
                <div class="min-w-0">
                  <p>{{ message }}</p>
                  <p class="mt-2">
                    当前用户
                    <span class="font-mono text-slate-100">{{ snapshot.userId || '未登录' }}</span>
                    ，最近 traceId
                    <span class="font-mono text-blue-100">{{ traceId || '-' }}</span>
                  </p>
                </div>
              </div>
            </div>

            <div class="mt-6 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
              <RouterLink class="secondary-button focus-ring" to="/portal">
                <ArrowLeft class="h-4 w-4" />
                返回 Portal
              </RouterLink>
              <button
                class="secondary-button focus-ring"
                type="button"
                :disabled="isRefreshing"
                @click="refreshSnapshot"
              >
                <RefreshCcw class="h-4 w-4" :class="{ 'animate-spin': isRefreshing }" />
                刷新权限快照
              </button>
              <button
                v-if="!isAuthenticated"
                class="primary-button focus-ring"
                type="button"
                @click="authStore.startLogin()"
              >
                <LogIn class="h-4 w-4" />
                重新登录
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
