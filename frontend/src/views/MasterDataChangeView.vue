<script setup lang="ts">
import {
  ArrowLeft,
  CircleAlert,
  Clock3,
  DatabaseZap,
  FilePenLine,
  Loader2,
  RefreshCw,
  Send,
  ShieldCheck
} from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';

import { ApiClientError } from '@/services/api';
import {
  listMasterDataChanges,
  submitMasterDataChange
} from '@/services/masterDataChanges';
import type {
  GisObjectType,
  MasterChangeResponse,
  MasterChangeSubmitRequest,
  PageResponse
} from '@/types/api';

const objectTypes: Array<{ label: string; value: GisObjectType; helper: string }> = [
  { label: '节点', value: 'NODE', helper: '管网拓扑节点' },
  { label: '管段', value: 'SEGMENT', helper: '地下管段与连线' },
  { label: '设施', value: 'FACILITY', helper: '阀门井、泵站等' },
  { label: '设备', value: 'DEVICE', helper: '传感器与采集设备' }
];

const statusMap: Record<string, string> = {
  PENDING: '待审批',
  QUEUED: '排队中',
  APPROVED: '已通过',
  REJECTED: '已驳回'
};

const form = ref({
  objectType: 'DEVICE' as GisObjectType,
  objectId: 'DEV-002',
  baseVersionNo: 1,
  reason: '',
  payloadText: '{\n  "deviceName": "压力传感器-02-变更版",\n  "protocolType": "NB-IOT"\n}'
});

const changes = ref<PageResponse<MasterChangeResponse> | null>(null);
const selectedStatus = ref('');
const loading = ref({
  list: false,
  submit: false
});
const pageError = ref('');
const formError = ref('');
const successMessage = ref('');
const traceId = ref('');

const latestChanges = computed(() => changes.value?.items ?? []);
const pendingCount = computed(() =>
  latestChanges.value.filter((item) => ['PENDING', 'QUEUED'].includes(item.requestStatus)).length
);

function statusLabel(status: string) {
  return statusMap[status] || status;
}

function statusBadgeClass(status: string) {
  return {
    'border-amber-300/25 bg-amber-400/10 text-amber-100': status === 'PENDING',
    'border-blue-300/25 bg-blue-400/10 text-blue-100': status === 'QUEUED',
    'border-emerald-300/25 bg-emerald-400/10 text-emerald-100': status === 'APPROVED',
    'border-rose-300/25 bg-rose-400/10 text-rose-100': status === 'REJECTED'
  };
}

function formatDate(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function parsePayload() {
  let payload: unknown;
  try {
    payload = JSON.parse(form.value.payloadText);
  } catch {
    throw new Error('变更内容必须是合法 JSON。');
  }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('变更内容必须是 JSON 对象。');
  }
  return payload as Record<string, unknown>;
}

async function loadChanges() {
  loading.value.list = true;
  pageError.value = '';
  try {
    const response = await listMasterDataChanges(1, 20, {
      requestStatus: selectedStatus.value || undefined
    });
    traceId.value = response.traceId;
    changes.value = response.data;
  } catch (error) {
    applyError(error, pageError, '主数据变更列表读取失败');
  } finally {
    loading.value.list = false;
  }
}

async function handleSubmit() {
  formError.value = '';
  pageError.value = '';
  successMessage.value = '';

  let payload: Record<string, unknown>;
  try {
    payload = parsePayload();
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '变更内容解析失败';
    return;
  }

  const request: MasterChangeSubmitRequest = {
    objectType: form.value.objectType,
    objectId: form.value.objectId.trim(),
    baseVersionNo: Number(form.value.baseVersionNo),
    reason: form.value.reason.trim(),
    payload
  };

  if (!request.objectId || !request.reason || !Number.isFinite(request.baseVersionNo)) {
    formError.value = '请填写对象 ID、基线版本和申请原因。';
    return;
  }

  loading.value.submit = true;
  try {
    const response = await submitMasterDataChange(request);
    traceId.value = response.traceId;
    successMessage.value = response.data
      ? `变更申请已提交：${response.data.requestId}`
      : '变更申请已提交。';
    await loadChanges();
  } catch (error) {
    applyError(error, formError, '主数据变更申请提交失败');
  } finally {
    loading.value.submit = false;
  }
}

function applyError(error: unknown, target: { value: string }, fallback: string) {
  if (error instanceof ApiClientError) {
    traceId.value = error.traceId ?? '';
    target.value = error.message || fallback;
    return;
  }
  target.value = fallback;
}

onMounted(loadChanges);
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-4 py-8 sm:px-6 lg:px-8">
      <section class="glass-panel p-5 sm:p-7">
        <div class="flex flex-col justify-between gap-6 lg:flex-row lg:items-start">
          <div class="min-w-0">
            <RouterLink class="secondary-button focus-ring w-fit" to="/mgmt">
              <ArrowLeft class="h-4 w-4" />
              返回综合管理平台
            </RouterLink>

            <div class="mt-7 flex items-start gap-5">
              <div
                class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-orange-300/20 bg-orange-400/10 text-orange-100 shadow-[0_0_28px_rgba(249,115,22,0.12)]"
                aria-hidden="true"
              >
                <DatabaseZap class="h-8 w-8" />
              </div>
              <div class="min-w-0">
                <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">MGMT Asset Change</p>
                <h1 class="mt-2 font-display text-3xl font-semibold text-white sm:text-4xl">资产变更申请</h1>
                <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300 sm:text-base">
                  提交节点、管段、设施和设备的主数据变更，申请进入审批队列后再生效，避免直接覆盖线上资产真值。
                </p>
              </div>
            </div>
          </div>

          <button type="button" class="secondary-button focus-ring w-fit" :disabled="loading.list" @click="loadChanges">
            <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading.list }" />
            刷新
          </button>
        </div>

        <div class="mt-7 grid gap-3 md:grid-cols-3">
          <div class="rounded-lg border border-blue-400/20 bg-blue-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-blue-100">
              <p class="text-xs text-slate-300">变更总数</p>
              <FilePenLine class="h-4 w-4" />
            </div>
            <p class="mt-3 font-display text-3xl font-semibold text-white">{{ changes?.total ?? 0 }}</p>
            <p class="mt-1 text-xs text-slate-400">当前列表范围</p>
          </div>
          <div class="rounded-lg border border-amber-400/20 bg-amber-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-amber-100">
              <p class="text-xs text-slate-300">待处理</p>
              <Clock3 class="h-4 w-4" />
            </div>
            <p class="mt-3 font-display text-3xl font-semibold text-white">{{ pendingCount }}</p>
            <p class="mt-1 text-xs text-slate-400">PENDING / QUEUED</p>
          </div>
          <div class="rounded-lg border border-emerald-400/20 bg-emerald-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-emerald-100">
              <p class="text-xs text-slate-300">权限边界</p>
              <ShieldCheck class="h-4 w-4" />
            </div>
            <p class="mt-3 font-mono text-sm font-semibold text-white">MENU:ASSET:WRITE</p>
            <p class="mt-1 text-xs text-slate-400">提交和查询变更申请</p>
          </div>
        </div>
      </section>

      <div class="mt-6 grid gap-6 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <section class="glass-panel-muted p-5 sm:p-6">
          <div class="mb-5">
            <p class="text-xs font-semibold uppercase tracking-[0.16em] text-orange-200">Submit</p>
            <h2 class="mt-1 text-xl font-semibold text-white">提交变更</h2>
          </div>

          <form class="space-y-4" @submit.prevent="handleSubmit">
            <div>
              <label class="mb-2 block text-xs font-semibold text-slate-300" for="object-type">对象类型</label>
              <select
                id="object-type"
                v-model="form.objectType"
                class="focus-ring h-11 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white"
              >
                <option v-for="type in objectTypes" :key="type.value" :value="type.value">
                  {{ type.label }} / {{ type.value }}
                </option>
              </select>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div>
                <label class="mb-2 block text-xs font-semibold text-slate-300" for="object-id">对象 ID</label>
                <input
                  id="object-id"
                  v-model="form.objectId"
                  class="focus-ring h-11 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white"
                  placeholder="DEV-002"
                />
              </div>
              <div>
                <label class="mb-2 block text-xs font-semibold text-slate-300" for="base-version">基线版本</label>
                <input
                  id="base-version"
                  v-model.number="form.baseVersionNo"
                  class="focus-ring h-11 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white"
                  min="1"
                  type="number"
                />
              </div>
            </div>

            <div>
              <label class="mb-2 block text-xs font-semibold text-slate-300" for="reason">申请原因</label>
              <input
                id="reason"
                v-model="form.reason"
                class="focus-ring h-11 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white"
                placeholder="说明本次资产变更原因"
              />
            </div>

            <div>
              <label class="mb-2 block text-xs font-semibold text-slate-300" for="payload">变更内容 JSON</label>
              <textarea
                id="payload"
                v-model="form.payloadText"
                class="focus-ring min-h-48 w-full resize-y rounded-lg border border-white/10 bg-black/25 px-3 py-3 font-mono text-xs leading-5 text-blue-50"
                spellcheck="false"
              />
            </div>

            <div
              v-if="formError"
              class="flex items-start gap-3 rounded-lg border border-rose-300/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-100"
            >
              <CircleAlert class="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                {{ formError }}
                <span v-if="traceId" class="mt-1 block font-mono text-xs text-rose-200">TraceId {{ traceId }}</span>
              </span>
            </div>

            <div
              v-if="successMessage"
              class="rounded-lg border border-emerald-300/25 bg-emerald-400/10 px-4 py-3 text-sm text-emerald-100"
            >
              {{ successMessage }}
            </div>

            <button type="submit" class="primary-button focus-ring w-full" :disabled="loading.submit">
              <Loader2 v-if="loading.submit" class="h-4 w-4 animate-spin" />
              <Send v-else class="h-4 w-4" />
              提交资产变更
            </button>
          </form>
        </section>

        <section class="glass-panel-muted p-5 sm:p-6">
          <div class="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-200">Requests</p>
              <h2 class="mt-1 text-xl font-semibold text-white">变更申请列表</h2>
            </div>
            <select
              v-model="selectedStatus"
              class="focus-ring h-10 rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white"
              @change="loadChanges"
            >
              <option value="">全部状态</option>
              <option value="PENDING">待审批</option>
              <option value="QUEUED">排队中</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已驳回</option>
            </select>
          </div>

          <div
            v-if="pageError"
            class="mb-4 flex items-start gap-3 rounded-lg border border-rose-300/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-100"
          >
            <CircleAlert class="mt-0.5 h-4 w-4 shrink-0" />
            <span>{{ pageError }}</span>
          </div>

          <div v-if="loading.list" class="rounded-lg border border-white/10 bg-white/[0.045] p-8 text-center text-sm text-slate-400">
            <Loader2 class="mx-auto mb-3 h-6 w-6 animate-spin text-blue-300" />
            正在读取变更申请
          </div>

          <div v-else class="space-y-3">
            <article
              v-for="item in latestChanges"
              :key="item.requestId"
              class="rounded-lg border border-white/10 bg-white/[0.045] p-4"
            >
              <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <h3 class="break-all font-mono text-sm font-semibold text-white">{{ item.requestId }}</h3>
                    <span class="rounded-full border px-2.5 py-1 text-xs font-semibold" :class="statusBadgeClass(item.requestStatus)">
                      {{ statusLabel(item.requestStatus) }}
                    </span>
                  </div>
                  <p class="mt-2 text-sm text-slate-300">{{ item.reason }}</p>
                </div>
                <p class="shrink-0 font-mono text-xs text-slate-400">{{ item.objectType }} / {{ item.objectId }}</p>
              </div>
              <div class="mt-4 grid gap-3 sm:grid-cols-3">
                <div>
                  <p class="text-xs text-slate-500">申请人</p>
                  <p class="mt-1 text-sm text-slate-200">{{ item.requestedBy }}</p>
                </div>
                <div>
                  <p class="text-xs text-slate-500">版本</p>
                  <p class="mt-1 font-mono text-sm text-slate-200">
                    {{ item.baseVersionNo }} -> {{ item.effectiveVersionNo ?? '-' }}
                  </p>
                </div>
                <div>
                  <p class="text-xs text-slate-500">更新时间</p>
                  <p class="mt-1 text-sm text-slate-200">{{ formatDate(item.updatedAt) }}</p>
                </div>
              </div>
            </article>

            <div v-if="!latestChanges.length" class="rounded-lg border border-white/10 bg-white/[0.045] p-8 text-center text-sm text-slate-400">
              暂无资产变更申请
            </div>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>
