<script setup lang="ts">
import {
  ArrowLeft,
  ChevronRight,
  CircleAlert,
  ClipboardList,
  Clock3,
  Loader2,
  RefreshCw,
  Send,
  ShieldCheck,
  SlidersHorizontal,
  TimerReset
} from 'lucide-vue-next';
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getWorkOrders } from '@/services/workorders';
import type { PageResponse, WorkOrderResponse } from '@/types/api';

const route = useRoute();
const router = useRouter();

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待派发', value: 'CREATED' },
  { label: '已派单', value: 'DISPATCHED' },
  { label: '处理中', value: 'ACCEPTED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已关闭', value: 'CLOSED' }
];

const activeStatus = ref(typeof route.query.status === 'string' ? route.query.status : '');
const page = ref<PageResponse<WorkOrderResponse> | null>(null);
const loading = ref(false);
const pageError = ref('');
const traceId = ref('');

const workOrders = computed(() => page.value?.items ?? []);
const createdCount = computed(() => workOrders.value.filter((item) => item.status === 'CREATED').length);
const dispatchedCount = computed(() => workOrders.value.filter((item) => item.status === 'DISPATCHED').length);
const activeCount = computed(() =>
  workOrders.value.filter((item) => ['CREATED', 'DISPATCHED', 'ACCEPTED'].includes(item.status)).length
);
const highPriorityCount = computed(() => workOrders.value.filter((item) => item.priority === 'HIGH').length);

const statusMap: Record<string, string> = {
  CREATED: '待派发',
  DISPATCHED: '已派单',
  ACCEPTED: '处理中',
  COMPLETED: '已完成',
  CLOSED: '已关闭'
};

const metricCards = computed(() => [
  {
    label: '当前筛选',
    value: page.value?.total ?? 0,
    helper: statusLabel(activeStatus.value),
    icon: ClipboardList,
    tone: 'blue'
  },
  {
    label: '待派发',
    value: createdCount.value,
    helper: '等待调度确认',
    icon: Send,
    tone: 'amber'
  },
  {
    label: '流转中',
    value: activeCount.value,
    helper: `${dispatchedCount.value} 张已派单`,
    icon: TimerReset,
    tone: 'emerald'
  },
  {
    label: '高优先级',
    value: highPriorityCount.value,
    helper: '需重点关注',
    icon: ShieldCheck,
    tone: 'rose'
  }
]);

function statusLabel(status?: string) {
  if (!status) return '全部状态';
  return statusMap[status] || status;
}

function statusBadgeClass(status: string) {
  return {
    'border-slate-300/20 bg-slate-400/10 text-slate-200': status === 'CREATED',
    'border-amber-300/25 bg-amber-400/10 text-amber-100': status === 'DISPATCHED',
    'border-blue-300/25 bg-blue-400/10 text-blue-100': status === 'ACCEPTED',
    'border-emerald-300/25 bg-emerald-400/10 text-emerald-100': status === 'COMPLETED',
    'border-white/10 bg-white/[0.045] text-slate-300': status === 'CLOSED'
  };
}

function priorityBadgeClass(priority?: string) {
  return {
    'border-rose-300/25 bg-rose-400/10 text-rose-100': priority === 'HIGH',
    'border-amber-300/25 bg-amber-400/10 text-amber-100': priority === 'MEDIUM',
    'border-white/10 bg-white/[0.045] text-slate-300': !priority || priority === 'LOW'
  };
}

function metricToneClass(tone: string) {
  return {
    'border-blue-400/20 bg-blue-400/10 text-blue-100': tone === 'blue',
    'border-amber-400/20 bg-amber-400/10 text-amber-100': tone === 'amber',
    'border-emerald-400/20 bg-emerald-400/10 text-emerald-100': tone === 'emerald',
    'border-rose-400/20 bg-rose-400/10 text-rose-100': tone === 'rose'
  };
}

function formatDate(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function setStatus(status: string) {
  activeStatus.value = status;
  router.replace({ path: '/emgc/workorders', query: status ? { status } : {} });
}

async function loadData() {
  loading.value = true;
  pageError.value = '';
  try {
    const response = await getWorkOrders(1, 50, { status: activeStatus.value || undefined });
    traceId.value = response.traceId;
    page.value = response.data;
  } catch (error) {
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      pageError.value = error.message;
    } else {
      pageError.value = '工单列表读取失败';
    }
  } finally {
    loading.value = false;
  }
}

watch(
  () => route.query.status,
  (status) => {
    activeStatus.value = typeof status === 'string' ? status : '';
    loadData();
  }
);

onMounted(loadData);
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-4 py-8 sm:px-6 lg:px-8">
      <section class="glass-panel p-5 sm:p-7">
        <div class="flex flex-col justify-between gap-5 lg:flex-row lg:items-start">
          <div class="min-w-0">
            <RouterLink class="secondary-button focus-ring w-fit" to="/emgc">
              <ArrowLeft class="h-4 w-4" />
              返回应急平台
            </RouterLink>

            <div class="mt-7 flex items-start gap-5">
              <div
                class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-amber-300/20 bg-amber-400/10 text-amber-100 shadow-[0_0_28px_rgba(245,158,11,0.12)]"
                aria-hidden="true"
              >
                <ClipboardList class="h-8 w-8" />
              </div>
              <div class="min-w-0">
                <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">EMGC Work Orders</p>
                <h1 class="mt-2 font-display text-3xl font-semibold text-white sm:text-4xl">应急工单</h1>
                <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300 sm:text-base">
                  汇总告警处置、派单流转与闭环回写。筛选工单后可进入详情页执行派单、接单、转派、回写等操作。
                </p>
              </div>
            </div>
          </div>

          <button
            type="button"
            class="secondary-button focus-ring w-fit"
            :disabled="loading"
            @click="loadData"
          >
            <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
            刷新
          </button>
        </div>

        <div class="mt-7 grid gap-3 md:grid-cols-4">
          <div
            v-for="metric in metricCards"
            :key="metric.label"
            class="rounded-lg border p-4"
            :class="metricToneClass(metric.tone)"
          >
            <div class="flex items-center justify-between gap-3">
              <p class="text-xs text-slate-300">{{ metric.label }}</p>
              <component :is="metric.icon" class="h-4 w-4 text-current" aria-hidden="true" />
            </div>
            <p class="mt-3 font-display text-3xl font-semibold text-white">{{ metric.value }}</p>
            <p class="mt-1 text-xs text-slate-400">{{ metric.helper }}</p>
          </div>
        </div>

        <section class="mt-6 rounded-lg border border-white/10 bg-white/[0.045] p-4">
          <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 items-center justify-center rounded-lg border border-blue-300/20 bg-blue-400/10 text-blue-100"
                aria-hidden="true"
              >
                <SlidersHorizontal class="h-5 w-5" />
              </div>
              <div>
                <p class="text-sm font-semibold text-white">状态筛选</p>
                <p class="mt-1 text-xs text-slate-400">当前 {{ statusLabel(activeStatus) }}，共 {{ page?.total ?? 0 }} 张</p>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <button
                v-for="option in statusOptions"
                :key="option.value || 'all'"
                type="button"
                class="focus-ring h-9 cursor-pointer rounded-lg border px-3 text-sm font-semibold transition-colors duration-200"
                :class="activeStatus === option.value ? 'border-blue-300/40 bg-blue-500/25 text-blue-50' : 'border-white/10 bg-black/15 text-slate-300 hover:border-white/20 hover:bg-white/[0.08] hover:text-white'"
                :aria-pressed="activeStatus === option.value"
                @click="setStatus(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
        </section>

        <div
          v-if="pageError"
          class="mt-5 flex items-start gap-3 rounded-lg border border-rose-300/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-100"
        >
          <CircleAlert class="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            {{ pageError }}
            <span v-if="traceId" class="mt-1 block font-mono text-xs text-rose-200">TraceId {{ traceId }}</span>
          </span>
        </div>

        <section class="mt-5 overflow-hidden rounded-lg border border-white/10 bg-black/10">
          <div class="flex flex-col gap-2 border-b border-white/10 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p class="font-display text-lg font-semibold text-white">工单队列</p>
              <p class="mt-1 text-sm text-slate-400">点击任一工单进入处置详情</p>
            </div>
            <span v-if="traceId" class="font-mono text-xs text-blue-100">TraceId {{ traceId }}</span>
          </div>

          <div v-if="loading" class="px-4 py-12 text-center text-sm text-slate-300">
            <Loader2 class="mx-auto mb-3 h-6 w-6 animate-spin text-blue-300" />
            正在同步工单
          </div>

          <div v-else-if="workOrders.length" class="divide-y divide-white/10">
            <RouterLink
              v-for="item in workOrders"
              :key="item.workOrderId"
              class="group block cursor-pointer px-4 py-4 transition-colors duration-200 hover:bg-white/[0.06]"
              :to="`/emgc/workorders/${item.workOrderId}`"
            >
              <div class="grid gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,0.8fr)_auto] lg:items-center">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <h3 class="truncate font-display text-base font-semibold text-white">
                      {{ item.description || item.workOrderId }}
                    </h3>
                    <span class="rounded-md border px-2 py-1 text-xs" :class="statusBadgeClass(item.status)">
                      {{ statusLabel(item.status) }}
                    </span>
                    <span class="rounded-md border px-2 py-1 text-xs" :class="priorityBadgeClass(item.priority)">
                      {{ item.priority || '-' }}
                    </span>
                  </div>
                  <p class="mt-2 break-all font-mono text-xs text-slate-400">
                    {{ item.workOrderId }} / {{ item.incidentId }}
                  </p>
                </div>

                <div class="grid gap-2 text-xs text-slate-400 sm:grid-cols-2">
                  <div>
                    <span class="text-slate-500">处理人</span>
                    <p class="mt-1 text-sm text-slate-200">{{ item.assignee || '未分派' }}</p>
                  </div>
                  <div>
                    <span class="text-slate-500">SLA</span>
                    <p class="mt-1 text-sm text-slate-200">{{ formatDate(item.slaDueAt) }}</p>
                  </div>
                  <div>
                    <span class="text-slate-500">类型</span>
                    <p class="mt-1 font-mono text-xs text-slate-200">{{ item.workOrderType || '-' }}</p>
                  </div>
                  <div>
                    <span class="text-slate-500">更新时间</span>
                    <p class="mt-1 text-sm text-slate-200">{{ formatDate(item.updatedAt) }}</p>
                  </div>
                </div>

                <div class="inline-flex items-center gap-2 text-sm font-semibold text-blue-200 transition-colors group-hover:text-white">
                  <Clock3 class="h-4 w-4" />
                  进入处理
                  <ChevronRight class="h-4 w-4" />
                </div>
              </div>
            </RouterLink>
          </div>

          <div v-else class="px-4 py-12 text-center text-sm text-slate-300">
            当前筛选下暂无工单。
          </div>
        </section>
      </section>
    </div>
  </main>
</template>
