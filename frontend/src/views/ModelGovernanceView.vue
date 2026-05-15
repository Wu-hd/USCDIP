<script setup lang="ts">
import {
  ArrowLeft,
  BrainCircuit,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  FlaskConical,
  GitBranch,
  History,
  LoaderCircle,
  LockKeyhole,
  RefreshCw,
  RotateCcw,
  ShieldAlert,
  SlidersHorizontal,
  TimerReset,
  X
} from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';

import { ApiClientError } from '@/services/api';
import { getModelDetail, getModels, rollbackModel } from '@/services/models';
import { useAuthStore } from '@/stores/auth';
import type { ModelResponse } from '@/types/api';

type LoadState = 'loading' | 'ready' | 'empty' | 'error';

const MODEL_STATUS_OPTIONS = [
  { label: '全部模型', value: '' },
  { label: 'REGISTERED', value: 'REGISTERED' }
];

const VERSION_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  GRAY: '灰度',
  ACTIVE: '生产',
  ROLLED_BACK: '已回退',
  DISABLED: '停用'
};

const authStore = useAuthStore();

const models = ref<ModelResponse[]>([]);
const selectedModel = ref<ModelResponse | null>(null);
const selectedModelCode = ref('');
const state = ref<LoadState>('loading');
const detailState = ref<LoadState>('loading');
const message = ref('正在读取模型注册表');
const detailMessage = ref('正在读取模型版本');
const traceId = ref('');
const detailTraceId = ref('');
const statusFilter = ref('');
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const totalPages = ref(1);
const isReviewPanelOpen = ref(false);
const rollbackModalOpen = ref(false);
const rollbackTargetVersion = ref('');
const rollbackReason = ref('');
const rollbackState = ref<'idle' | 'submitting' | 'success' | 'error'>('idle');
const rollbackMessage = ref('');
const rollbackTraceId = ref('');
const noticeMessage = ref('');
const noticeTraceId = ref('');

const canWriteModel = computed(() => authStore.hasPermission('MENU:MODEL:WRITE'));
const activeCoverageCount = computed(() => models.value.filter((model) => model.activeVersionNo).length);
const grayReleaseCount = computed(() => models.value.filter((model) => model.grayVersionNo).length);
const fallbackEnabledCount = computed(() => models.value.filter((model) => model.ruleFallbackEnabled).length);
const detailVersions = computed(() => selectedModel.value?.versions ?? []);
const rollbackTargets = computed(() =>
  detailVersions.value.filter((version) => version.versionNo !== selectedModel.value?.activeVersionNo)
);
const selectedActiveVersion = computed(() =>
  detailVersions.value.find((version) => version.versionNo === selectedModel.value?.activeVersionNo)
);
const selectedGrayVersion = computed(() =>
  detailVersions.value.find((version) => version.versionNo === selectedModel.value?.grayVersionNo)
);

async function loadModels(keepSelection = false) {
  state.value = 'loading';
  message.value = '正在读取模型注册表';
  try {
    const response = await getModels(page.value, pageSize.value, { status: statusFilter.value });
    const payload = response.data;
    models.value = payload?.items ?? [];
    total.value = payload?.total ?? 0;
    totalPages.value = payload?.totalPages ?? 1;
    traceId.value = response.traceId;
    state.value = models.value.length ? 'ready' : 'empty';
    message.value = models.value.length ? '模型注册表已同步' : '当前过滤条件下没有模型';

    const stillVisible = models.value.some((model) => model.modelCode === selectedModelCode.value);
    if (keepSelection && stillVisible) {
      await selectModel(selectedModelCode.value);
      return;
    }
    if (models.value[0]) {
      await selectModel(models.value[0].modelCode);
      return;
    }
    selectedModel.value = null;
    selectedModelCode.value = '';
    detailState.value = 'empty';
    detailMessage.value = '请调整过滤条件后查看模型版本';
  } catch (error) {
    state.value = 'error';
    models.value = [];
    selectedModel.value = null;
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '模型注册表读取失败';
  }
}

async function selectModel(modelCode: string) {
  selectedModelCode.value = modelCode;
  detailState.value = 'loading';
  detailMessage.value = '正在读取模型版本';
  rollbackModalOpen.value = false;
  rollbackState.value = 'idle';
  try {
    const response = await getModelDetail(modelCode);
    selectedModel.value = response.data;
    detailTraceId.value = response.traceId;
    detailState.value = response.data ? 'ready' : 'empty';
    detailMessage.value = response.data ? '模型版本已同步' : '模型详情为空';
  } catch (error) {
    selectedModel.value = null;
    detailState.value = 'error';
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      detailMessage.value = error.message;
      return;
    }
    detailMessage.value = '模型详情读取失败';
  }
}

function applyFilter() {
  page.value = 1;
  loadModels(false);
}

function changePage(direction: -1 | 1) {
  const nextPage = Math.min(Math.max(page.value + direction, 1), totalPages.value);
  if (nextPage === page.value) {
    return;
  }
  page.value = nextPage;
  loadModels(true);
}

function openRollbackModal() {
  if (!selectedModel.value || !canWriteModel.value) {
    return;
  }
  const preferredTarget =
    rollbackTargets.value.find((version) => version.status === 'ROLLED_BACK') ??
    rollbackTargets.value.find((version) => version.status !== 'DISABLED') ??
    rollbackTargets.value[0];
  rollbackTargetVersion.value = preferredTarget?.versionNo ?? '';
  rollbackReason.value = '';
  rollbackMessage.value = '';
  rollbackTraceId.value = '';
  rollbackState.value = 'idle';
  rollbackModalOpen.value = true;
}

async function submitRollback() {
  if (!selectedModel.value || !rollbackTargetVersion.value || !rollbackReason.value.trim()) {
    rollbackState.value = 'error';
    rollbackMessage.value = '请选择目标版本并填写回退原因';
    return;
  }
  rollbackState.value = 'submitting';
  rollbackMessage.value = '正在提交模型回退';
  try {
    const response = await rollbackModel(selectedModel.value.modelCode, {
      targetVersionNo: rollbackTargetVersion.value,
      reason: rollbackReason.value.trim()
    });
    rollbackTraceId.value = response.traceId;
    rollbackState.value = 'success';
    rollbackMessage.value = `${response.data?.versionNo ?? rollbackTargetVersion.value} 已切换为 ACTIVE`;
    noticeMessage.value = rollbackMessage.value;
    noticeTraceId.value = response.traceId;
    await loadModels(true);
    rollbackModalOpen.value = false;
  } catch (error) {
    rollbackState.value = 'error';
    if (error instanceof ApiClientError) {
      rollbackTraceId.value = error.traceId ?? '';
      rollbackMessage.value = error.message;
      return;
    }
    rollbackMessage.value = '模型回退提交失败';
  }
}

function statusClass(status: string) {
  if (status === 'ACTIVE') return 'border-emerald-300/30 bg-emerald-400/15 text-emerald-100';
  if (status === 'GRAY') return 'border-blue-300/30 bg-blue-400/15 text-blue-100';
  if (status === 'ROLLED_BACK') return 'border-amber-300/30 bg-amber-400/15 text-amber-100';
  if (status === 'DISABLED') return 'border-slate-500/30 bg-slate-500/15 text-slate-200';
  return 'border-white/10 bg-white/[0.06] text-slate-200';
}

function formatDateTime(value: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function versionStatusLabel(status: string) {
  return VERSION_STATUS_LABELS[status] ?? status;
}

onMounted(() => {
  loadModels();
});
</script>

<template>
  <main class="portal-shell min-h-screen">
    <div class="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <header class="flex flex-col gap-4 border-b border-white/10 pb-5 lg:flex-row lg:items-center lg:justify-between">
        <div class="min-w-0">
          <RouterLink class="secondary-button focus-ring mb-4 w-fit" to="/diag">
            <ArrowLeft class="h-4 w-4" />
            返回智能诊断
          </RouterLink>
          <div class="flex items-center gap-3">
            <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-blue-300/20 bg-blue-400/15 text-blue-100">
              <BrainCircuit class="h-6 w-6" aria-hidden="true" />
            </div>
            <div class="min-w-0">
              <p class="text-xs font-semibold uppercase tracking-[0.18em] text-blue-200">F-16 Model Governance</p>
              <h1 class="mt-1 font-display text-2xl font-semibold text-white sm:text-3xl">模型治理一期占位</h1>
            </div>
          </div>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
            读取 B-25 模型网关的注册表与版本状态，保留灰度、回退和复核治理框架；一期仅开放真实回退闭环。
          </p>
        </div>
        <div class="flex flex-col gap-2 sm:flex-row">
          <button class="secondary-button focus-ring" type="button" @click="loadModels(true)">
            <RefreshCw class="h-4 w-4" />
            刷新
          </button>
          <button class="primary-button focus-ring bg-amber-500 hover:bg-amber-400" type="button" @click="isReviewPanelOpen = true">
            <FlaskConical class="h-4 w-4" />
            复核预留
          </button>
        </div>
      </header>

      <section class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div class="glass-panel-muted p-4">
          <p class="text-xs text-slate-400">模型总数</p>
          <p class="mt-2 font-mono text-2xl font-semibold text-white">{{ total }}</p>
        </div>
        <div class="glass-panel-muted p-4">
          <p class="text-xs text-slate-400">ACTIVE 覆盖</p>
          <p class="mt-2 font-mono text-2xl font-semibold text-emerald-100">{{ activeCoverageCount }}</p>
        </div>
        <div class="glass-panel-muted p-4">
          <p class="text-xs text-slate-400">GRAY 发布</p>
          <p class="mt-2 font-mono text-2xl font-semibold text-blue-100">{{ grayReleaseCount }}</p>
        </div>
        <div class="glass-panel-muted p-4">
          <p class="text-xs text-slate-400">规则兜底开启</p>
          <p class="mt-2 font-mono text-2xl font-semibold text-amber-100">{{ fallbackEnabledCount }}</p>
        </div>
      </section>

      <div v-if="noticeMessage" class="mt-5 rounded-lg border border-emerald-300/20 bg-emerald-400/10 p-3 text-sm text-emerald-50">
        <div class="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <span>{{ noticeMessage }}</span>
          <span v-if="noticeTraceId" class="font-mono text-xs text-emerald-100">TraceId {{ noticeTraceId }}</span>
        </div>
      </div>

      <div class="mt-5 grid gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(520px,1.25fr)]">
        <section class="glass-panel-muted overflow-hidden">
          <div class="flex flex-col gap-3 border-b border-white/10 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-white">模型注册表</h2>
              <p class="mt-1 text-xs text-slate-400">{{ message }}</p>
            </div>
            <label class="flex min-w-[180px] items-center gap-2 rounded-lg border border-white/10 bg-black/20 px-3 py-2 text-sm text-slate-200 focus-within:border-blue-300/40">
              <SlidersHorizontal class="h-4 w-4 text-blue-200" aria-hidden="true" />
              <select v-model="statusFilter" class="w-full bg-transparent text-sm text-white outline-none" @change="applyFilter">
                <option v-for="option in MODEL_STATUS_OPTIONS" :key="option.value" class="bg-slate-950" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
          </div>

          <div v-if="state === 'loading'" class="flex min-h-[360px] items-center justify-center p-6 text-sm text-blue-100">
            <LoaderCircle class="mr-2 h-4 w-4 animate-spin" />
            正在加载模型注册表
          </div>
          <div v-else-if="state === 'error'" class="m-4 rounded-lg border border-amber-300/20 bg-amber-400/10 p-4 text-sm text-amber-50">
            <p>{{ message }}</p>
            <p v-if="traceId" class="mt-2 font-mono text-xs text-amber-100">TraceId {{ traceId }}</p>
          </div>
          <div v-else-if="state === 'empty'" class="m-4 rounded-lg border border-white/10 bg-white/[0.045] p-6 text-sm text-slate-300">
            当前没有可展示模型，请调整过滤条件。
          </div>
          <div v-else class="overflow-x-auto">
            <table class="min-w-[760px] w-full text-left text-sm">
              <thead class="border-b border-white/10 bg-white/[0.035] text-xs uppercase tracking-wide text-slate-400">
                <tr>
                  <th class="px-4 py-3 font-medium">模型</th>
                  <th class="px-4 py-3 font-medium">状态</th>
                  <th class="px-4 py-3 font-medium">ACTIVE</th>
                  <th class="px-4 py-3 font-medium">GRAY</th>
                  <th class="px-4 py-3 font-medium">更新</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/10">
                <tr
                  v-for="model in models"
                  :key="model.modelCode"
                  class="cursor-pointer transition-colors duration-200 hover:bg-white/[0.055]"
                  :class="selectedModelCode === model.modelCode ? 'bg-blue-400/10' : ''"
                  @click="selectModel(model.modelCode)"
                >
                  <td class="px-4 py-4">
                    <p class="font-mono text-sm font-semibold text-white">{{ model.modelCode }}</p>
                    <p class="mt-1 truncate text-xs text-slate-400">{{ model.modelName }} · {{ model.modelType }}</p>
                  </td>
                  <td class="px-4 py-4">
                    <span class="inline-flex rounded-md border px-2 py-1 font-mono text-xs" :class="statusClass(model.status)">
                      {{ model.status }}
                    </span>
                  </td>
                  <td class="px-4 py-4 font-mono text-xs text-emerald-100">{{ model.activeVersionNo ?? '-' }}</td>
                  <td class="px-4 py-4 font-mono text-xs text-blue-100">{{ model.grayVersionNo ?? '-' }}</td>
                  <td class="px-4 py-4 font-mono text-xs text-slate-300">{{ formatDateTime(model.updatedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="flex items-center justify-between border-t border-white/10 px-4 py-3 text-xs text-slate-400">
            <span>第 {{ page }} / {{ totalPages }} 页</span>
            <div class="flex gap-2">
              <button class="icon-button focus-ring h-9 w-9" type="button" :disabled="page <= 1" @click="changePage(-1)">
                <ChevronLeft class="h-4 w-4" />
              </button>
              <button class="icon-button focus-ring h-9 w-9" type="button" :disabled="page >= totalPages" @click="changePage(1)">
                <ChevronRight class="h-4 w-4" />
              </button>
            </div>
          </div>
        </section>

        <section class="glass-panel-muted overflow-hidden">
          <div class="border-b border-white/10 p-4">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div class="min-w-0">
                <p class="text-xs font-semibold uppercase tracking-[0.18em] text-blue-200">Version Governance</p>
                <h2 class="mt-2 text-xl font-semibold text-white">
                  {{ selectedModel?.modelName ?? '请选择模型' }}
                </h2>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
                  {{ selectedModel?.description ?? detailMessage }}
                </p>
              </div>
              <div class="flex flex-col gap-2 sm:flex-row">
                <button
                  class="secondary-button focus-ring"
                  type="button"
                  :disabled="!selectedModel || !canWriteModel || rollbackTargets.length === 0"
                  @click="openRollbackModal"
                >
                  <RotateCcw class="h-4 w-4" />
                  回退版本
                </button>
              </div>
            </div>

            <div class="mt-4 grid gap-3 md:grid-cols-3">
              <div class="rounded-lg border border-white/10 bg-black/20 p-3">
                <p class="text-xs text-slate-400">当前生产</p>
                <p class="mt-2 font-mono text-sm text-emerald-100">{{ selectedActiveVersion?.versionNo ?? '-' }}</p>
              </div>
              <div class="rounded-lg border border-white/10 bg-black/20 p-3">
                <p class="text-xs text-slate-400">灰度版本</p>
                <p class="mt-2 font-mono text-sm text-blue-100">{{ selectedGrayVersion?.versionNo ?? '-' }}</p>
              </div>
              <div class="rounded-lg border border-white/10 bg-black/20 p-3">
                <p class="text-xs text-slate-400">默认超时</p>
                <p class="mt-2 font-mono text-sm text-white">{{ selectedModel?.defaultTimeoutMs ?? '-' }} ms</p>
              </div>
            </div>

            <div v-if="!canWriteModel" class="mt-4 rounded-lg border border-amber-300/20 bg-amber-400/10 p-3 text-sm text-amber-50">
              <div class="flex gap-2">
                <LockKeyhole class="mt-0.5 h-4 w-4 shrink-0" />
                <p>当前账号缺少 MENU:MODEL:WRITE，仅可查看模型与版本状态。</p>
              </div>
            </div>
          </div>

          <div v-if="detailState === 'loading'" class="flex min-h-[420px] items-center justify-center p-6 text-sm text-blue-100">
            <LoaderCircle class="mr-2 h-4 w-4 animate-spin" />
            正在加载模型版本
          </div>
          <div v-else-if="detailState === 'error'" class="m-4 rounded-lg border border-amber-300/20 bg-amber-400/10 p-4 text-sm text-amber-50">
            <p>{{ detailMessage }}</p>
            <p v-if="detailTraceId" class="mt-2 font-mono text-xs text-amber-100">TraceId {{ detailTraceId }}</p>
          </div>
          <div v-else-if="!selectedModel" class="m-4 rounded-lg border border-white/10 bg-white/[0.045] p-6 text-sm text-slate-300">
            请先从左侧注册表选择模型。
          </div>
          <div v-else class="overflow-x-auto">
            <table class="min-w-[960px] w-full text-left text-sm">
              <thead class="border-b border-white/10 bg-white/[0.035] text-xs uppercase tracking-wide text-slate-400">
                <tr>
                  <th class="px-4 py-3 font-medium">版本</th>
                  <th class="px-4 py-3 font-medium">状态</th>
                  <th class="px-4 py-3 font-medium">灰度</th>
                  <th class="px-4 py-3 font-medium">超时/兜底</th>
                  <th class="px-4 py-3 font-medium">Schema</th>
                  <th class="px-4 py-3 font-medium">Artifact</th>
                  <th class="px-4 py-3 font-medium">时间</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/10">
                <tr v-for="version in detailVersions" :key="version.versionId" class="transition-colors duration-200 hover:bg-white/[0.055]">
                  <td class="px-4 py-4 font-mono text-sm font-semibold text-white">{{ version.versionNo }}</td>
                  <td class="px-4 py-4">
                    <span class="inline-flex rounded-md border px-2 py-1 text-xs" :class="statusClass(version.status)">
                      {{ versionStatusLabel(version.status) }}
                    </span>
                  </td>
                  <td class="px-4 py-4">
                    <div class="flex items-center gap-2">
                      <div class="h-2 w-20 overflow-hidden rounded-full bg-white/10">
                        <div class="h-full rounded-full bg-blue-300" :style="{ width: `${version.grayPercent ?? 0}%` }" />
                      </div>
                      <span class="font-mono text-xs text-blue-100">{{ version.grayPercent ?? 0 }}%</span>
                    </div>
                  </td>
                  <td class="px-4 py-4 text-xs text-slate-300">
                    <p class="font-mono">{{ version.timeoutMs ?? '-' }} ms</p>
                    <p class="mt-1" :class="version.ruleFallbackEnabled ? 'text-amber-100' : 'text-slate-500'">
                      {{ version.ruleFallbackEnabled ? '规则兜底' : '无兜底' }}
                    </p>
                  </td>
                  <td class="px-4 py-4 font-mono text-xs text-slate-300">{{ version.featureSchemaVersion ?? '-' }}</td>
                  <td class="max-w-[220px] px-4 py-4">
                    <p class="truncate font-mono text-xs text-slate-300" :title="version.artifactUri ?? '-'">
                      {{ version.artifactUri ?? '-' }}
                    </p>
                  </td>
                  <td class="px-4 py-4 text-xs text-slate-300">
                    <p><GitBranch class="mr-1 inline h-3.5 w-3.5 text-blue-200" />{{ formatDateTime(version.publishedAt) }}</p>
                    <p class="mt-1"><History class="mr-1 inline h-3.5 w-3.5 text-amber-200" />{{ formatDateTime(version.rolledBackAt) }}</p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>

    <div v-if="rollbackModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-6 backdrop-blur-sm">
      <section class="w-full max-w-xl rounded-lg border border-white/10 bg-slate-950 p-5 shadow-glow">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-amber-200">Rollback Confirmation</p>
            <h2 class="mt-2 text-xl font-semibold text-white">回退 {{ selectedModel?.modelCode }}</h2>
          </div>
          <button class="icon-button focus-ring" type="button" @click="rollbackModalOpen = false">
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="mt-5 grid gap-4">
          <label class="block">
            <span class="text-sm font-medium text-slate-200">目标版本</span>
            <select v-model="rollbackTargetVersion" class="focus-ring mt-2 h-11 w-full rounded-lg border border-white/10 bg-black/30 px-3 font-mono text-sm text-white">
              <option v-for="version in rollbackTargets" :key="version.versionId" class="bg-slate-950" :value="version.versionNo">
                {{ version.versionNo }} · {{ version.status }}
              </option>
            </select>
          </label>
          <label class="block">
            <span class="text-sm font-medium text-slate-200">回退原因</span>
            <textarea
              v-model.trim="rollbackReason"
              class="focus-ring mt-2 min-h-28 w-full resize-none rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm leading-6 text-white placeholder:text-slate-500"
              placeholder="例如：灰度异常，回退至上一稳定版本。"
            />
          </label>
          <div class="rounded-lg border border-amber-300/20 bg-amber-400/10 p-3 text-sm leading-6 text-amber-50">
            <ShieldAlert class="mr-2 inline h-4 w-4" />
            回退会将当前 ACTIVE / GRAY 版本标记为 ROLLED_BACK，并把目标版本置为 ACTIVE。
          </div>
          <div v-if="rollbackMessage" class="rounded-lg border p-3 text-sm" :class="rollbackState === 'error' ? 'border-amber-300/20 bg-amber-400/10 text-amber-50' : 'border-emerald-300/20 bg-emerald-400/10 text-emerald-50'">
            <p>{{ rollbackMessage }}</p>
            <p v-if="rollbackTraceId" class="mt-1 font-mono text-xs">TraceId {{ rollbackTraceId }}</p>
          </div>
        </div>

        <div class="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button class="secondary-button focus-ring" type="button" @click="rollbackModalOpen = false">取消</button>
          <button class="primary-button focus-ring bg-amber-500 hover:bg-amber-400" type="button" :disabled="rollbackState === 'submitting'" @click="submitRollback">
            <LoaderCircle v-if="rollbackState === 'submitting'" class="h-4 w-4 animate-spin" />
            <RotateCcw v-else class="h-4 w-4" />
            确认回退
          </button>
        </div>
      </section>
    </div>

    <div v-if="isReviewPanelOpen" class="fixed inset-0 z-40 flex justify-end bg-black/60 backdrop-blur-sm">
      <aside class="h-full w-full max-w-md overflow-y-auto border-l border-white/10 bg-slate-950 p-5 shadow-glow">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-blue-200">Review Placeholder</p>
            <h2 class="mt-2 text-xl font-semibold text-white">特征 / 结果复核预留</h2>
          </div>
          <button class="icon-button focus-ring" type="button" @click="isReviewPanelOpen = false">
            <X class="h-4 w-4" />
          </button>
        </div>
        <div class="mt-5 space-y-3 text-sm leading-6 text-slate-300">
          <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
            <CheckCircle2 class="mb-3 h-5 w-5 text-blue-200" />
            一期已接入模型注册表、版本状态、灰度态势和真实回退链路。
          </div>
          <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
            <TimerReset class="mb-3 h-5 w-5 text-amber-200" />
            二期复核将承接 B-26 特征视图、模型结果明细授权和访问审计。
          </div>
          <p class="rounded-lg border border-amber-300/20 bg-amber-400/10 p-4 text-amber-50">
            当前按钮只说明治理框架，不提交复核任务，也不绕过脱敏视图权限。
          </p>
        </div>
      </aside>
    </div>
  </main>
</template>
