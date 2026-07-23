<template>
  <main class="portal-shell">
  <div class="app-frame app-viewport flex flex-col px-4 py-8 sm:px-6 lg:px-8">
      <div class="space-y-6">
        <header class="glass-panel p-5 sm:p-7">
          <div class="flex flex-col justify-between gap-6 lg:flex-row lg:items-start">
            <div class="min-w-0">
              <button type="button" class="secondary-button focus-ring w-fit" @click="router.back()">
                <ArrowLeftIcon class="h-4 w-4" />
                返回
              </button>

              <div class="mt-7 flex items-start gap-5">
                <div
                  class="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg border border-blue-300/20 bg-blue-400/10 text-blue-100 shadow-[0_0_28px_rgba(59,130,246,0.15)]"
                  aria-hidden="true"
                >
                  <FilePenLineIcon class="h-8 w-8" />
                </div>
                <div class="min-w-0">
                  <p class="text-sm font-semibold uppercase tracking-[0.18em] text-blue-200">EMGC Work Order</p>
                  <div class="mt-2 flex flex-wrap items-center gap-3">
                    <h1 class="font-display text-3xl font-semibold text-white sm:text-4xl">工单详情</h1>
                    <span
                      v-if="workOrder?.workOrderType"
                      class="rounded-full border border-blue-300/25 bg-blue-400/10 px-3 py-1 text-xs font-semibold text-blue-100"
                    >
                      {{ workOrder.workOrderType }}
                    </span>
                    <span
                      v-if="workOrder?.status"
                      class="rounded-full border px-3 py-1 text-xs font-semibold"
                      :class="statusBadgeClass(workOrder.status)"
                    >
                      {{ statusLabel(workOrder.status) }}
                    </span>
                    <span
                      v-if="workOrder?.priority"
                      class="rounded-full border px-3 py-1 text-xs font-semibold"
                      :class="priorityBadgeClass(workOrder.priority)"
                    >
                      {{ workOrder.priority }}
                    </span>
                  </div>
                  <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
                    {{ workOrder?.description || '读取工单的处置对象、责任人、SLA 与流转记录。' }}
                  </p>
                  <p class="mt-3 break-all font-mono text-xs text-slate-400">
                    {{ workOrder?.workOrderId || workOrderId }}
                  </p>
                </div>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <button
                v-if="canDispatch"
                type="button"
                class="primary-button focus-ring"
                :disabled="loading.action"
                @click="openAction('dispatch')"
              >
                <SendIcon class="h-4 w-4" />
                派单
              </button>
              <button
                v-if="canTransfer"
                type="button"
                class="secondary-button focus-ring"
                :disabled="loading.action"
                @click="openAction('transfer')"
              >
                <Repeat2Icon class="h-4 w-4" />
                转派
              </button>
              <button
                v-if="canWriteback"
                type="button"
                class="focus-ring inline-flex min-h-10 cursor-pointer items-center justify-center gap-2 rounded-lg border border-amber-300/25 bg-amber-400/15 px-4 py-2 text-sm font-semibold text-amber-50 transition-colors duration-200 hover:border-amber-300/40 hover:bg-amber-400/25 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loading.action"
                @click="openAction('writeback')"
              >
                <FilePenLineIcon class="h-4 w-4" />
                回写
              </button>
              <button
                v-if="workOrder?.status === 'DISPATCHED'"
                type="button"
                class="focus-ring inline-flex min-h-10 cursor-pointer items-center justify-center gap-2 rounded-lg border border-emerald-300/25 bg-emerald-400/15 px-4 py-2 text-sm font-semibold text-emerald-50 transition-colors duration-200 hover:border-emerald-300/40 hover:bg-emerald-400/25 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="loading.action"
                @click="handleAccept"
              >
                <Loader2Icon v-if="loading.action" class="h-4 w-4 animate-spin" />
                <UserCheckIcon v-else class="h-4 w-4" />
                接单
              </button>
            </div>
          </div>

          <div
            v-if="!hasDispatchPermission"
            class="mt-6 flex items-start gap-3 rounded-lg border border-amber-300/25 bg-amber-400/10 px-4 py-3 text-sm text-amber-100"
          >
            <LockKeyholeIcon class="mt-0.5 h-4 w-4 shrink-0" />
            当前账号缺少 MENU:WORKORDER:DISPATCH，派单、转派和回写入口已隐藏。
          </div>
          <div
            v-if="successMessage"
            class="mt-4 flex items-start gap-3 rounded-lg border border-emerald-300/25 bg-emerald-400/10 px-4 py-3 text-sm text-emerald-100"
          >
            <CheckCircleIcon class="mt-0.5 h-4 w-4 shrink-0" />
            {{ successMessage }}
          </div>
          <div
            v-if="pageError"
            class="mt-4 flex items-start gap-3 rounded-lg border border-rose-300/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-100"
          >
            <CircleAlertIcon class="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              {{ pageError }}
              <span v-if="traceId" class="mt-1 block font-mono text-xs text-rose-200">TraceId {{ traceId }}</span>
            </span>
          </div>
        </header>

        <section v-if="!loading.fetch" class="grid gap-3 md:grid-cols-4">
          <div class="rounded-lg border border-blue-400/20 bg-blue-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-blue-100">
              <p class="text-xs text-slate-300">当前状态</p>
              <CheckCircleIcon class="h-4 w-4" />
            </div>
            <p class="mt-3 text-lg font-semibold text-white">{{ statusLabel(workOrder?.status || '-') }}</p>
            <p class="mt-1 font-mono text-xs text-slate-400">version {{ workOrder?.versionNo ?? '-' }}</p>
          </div>
          <div class="rounded-lg border border-amber-400/20 bg-amber-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-amber-100">
              <p class="text-xs text-slate-300">SLA 到期</p>
              <ClockIcon class="h-4 w-4" />
            </div>
            <p class="mt-3 text-lg font-semibold text-white">{{ formatDate(workOrder?.slaDueAt) }}</p>
            <p class="mt-1 text-xs text-slate-400">优先级 {{ workOrder?.priority || '-' }}</p>
          </div>
          <div class="rounded-lg border border-emerald-400/20 bg-emerald-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-emerald-100">
              <p class="text-xs text-slate-300">当前处理人</p>
              <UserIcon class="h-4 w-4" />
            </div>
            <p class="mt-3 text-lg font-semibold text-white">{{ workOrder?.assignee || '-' }}</p>
            <p class="mt-1 font-mono text-xs text-slate-400">{{ workOrder?.assigneeUserId || '-' }}</p>
          </div>
          <div class="rounded-lg border border-rose-400/20 bg-rose-400/10 p-4">
            <div class="flex items-center justify-between gap-3 text-rose-100">
              <p class="text-xs text-slate-300">关联事件</p>
              <CircleAlertIcon class="h-4 w-4" />
            </div>
            <p class="mt-3 break-all font-mono text-sm font-semibold text-white">{{ workOrder?.incidentId || '-' }}</p>
            <p class="mt-1 text-xs text-slate-400">节点 {{ workOrder?.nodeId || '-' }}</p>
          </div>
        </section>

        <div v-if="loading.fetch" class="glass-panel-muted p-10 text-center text-sm text-slate-400">
          <Loader2Icon class="mx-auto mb-3 h-6 w-6 animate-spin text-blue-500" />
          正在读取工单详情
        </div>

        <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div class="space-y-6 lg:col-span-2">
            <section class="glass-panel-muted p-5 sm:p-6">
              <div class="mb-5 flex items-center justify-between gap-4">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-200">Work Order Data</p>
                  <h2 class="mt-1 text-xl font-semibold text-white">工单信息</h2>
                </div>
                <span class="rounded-md border border-white/10 bg-white/[0.055] px-2 py-1 font-mono text-xs text-slate-300">
                  v{{ workOrder?.versionNo ?? '-' }}
                </span>
              </div>
              <div class="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
                <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4 md:col-span-2">
                  <div class="mb-2 text-xs text-slate-400">工单描述</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap text-white">{{ workOrder?.description || '-' }}</div>
                </div>
                <InfoItem label="优先级" :value="workOrder?.priority || '-'" />
                <InfoItem label="关联事件 ID" :value="workOrder?.incidentId || '-'" monospace />
                <InfoItem label="处理人" :value="workOrder?.assignee || '-'" />
                <InfoItem label="处理人用户 ID" :value="workOrder?.assigneeUserId || '-'" monospace />
                <InfoItem label="SLA 到期时间" :value="formatDate(workOrder?.slaDueAt)" />
                <InfoItem label="更新时间" :value="formatDate(workOrder?.updatedAt)" />
                <InfoItem label="关联管段" :value="workOrder?.segmentId || '-'" monospace />
                <InfoItem label="关联节点" :value="workOrder?.nodeId || '-'" monospace />
              </div>
            </section>

            <section
              v-if="workOrder?.completionSummary || workOrder?.closeReason || workOrder?.writebackReason"
              class="glass-panel-muted p-5 sm:p-6"
            >
              <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="text-xs font-semibold uppercase tracking-[0.16em] text-amber-200">Writeback</p>
                  <h2 class="mt-1 text-xl font-semibold text-white">处理结果与回写</h2>
                </div>
                <span
                  v-if="workOrder?.writebackType"
                  class="rounded-full border border-amber-300/25 bg-amber-400/10 px-3 py-1 text-xs font-semibold text-amber-100"
                >
                  {{ writebackTypeLabel(workOrder.writebackType) }}
                </span>
              </div>
              <div class="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
                <div v-if="workOrder?.completionSummary" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-400">处理总结</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap text-white">{{ workOrder.completionSummary }}</div>
                </div>
                <div v-if="workOrder?.closeReason" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-400">关闭原因</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap text-white">{{ workOrder.closeReason }}</div>
                </div>
                <div v-if="workOrder?.writebackReason" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-400">回写原因</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap text-white">{{ workOrder.writebackReason }}</div>
                </div>
                <InfoItem v-if="workOrder?.writebackAt" label="回写时间" :value="formatDate(workOrder.writebackAt)" />
              </div>
            </section>
          </div>

          <aside class="glass-panel-muted p-5 sm:p-6 lg:sticky lg:top-8 lg:self-start">
            <div class="mb-6 flex items-center justify-between gap-3">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.16em] text-blue-200">Timeline</p>
                <h2 class="mt-1 flex items-center text-xl font-semibold text-white">
                  <ClockIcon class="mr-2 h-5 w-5 text-slate-400" />
                  工单流转
                </h2>
              </div>
              <span class="rounded-full border border-white/10 bg-white/[0.055] px-3 py-1 text-xs text-slate-300">
                {{ statusLabel(workOrder?.status || '-') }}
              </span>
            </div>
            <div class="relative ml-3 space-y-6 border-l border-white/10">
              <TimelineItem
                v-if="workOrder?.writebackAt"
                tone="amber"
                title="回写已记录"
                :time="formatDate(workOrder.writebackAt)"
                :description="writebackTypeLabel(workOrder.writebackType)"
                :icon="FilePenLineIcon"
              />
              <TimelineItem
                v-if="workOrder?.closedAt"
                tone="slate"
                title="工单关闭"
                :time="formatDate(workOrder.closedAt)"
                :description="`关闭人: ${workOrder.closedBy || '-'}`"
                :icon="CheckCircleIcon"
              />
              <TimelineItem
                v-if="workOrder?.completedAt"
                tone="emerald"
                title="处理完成"
                :time="formatDate(workOrder.completedAt)"
                :description="`处理人: ${workOrder.completedBy || '-'}`"
                :icon="CheckCircleIcon"
              />
              <TimelineItem
                v-if="workOrder?.acceptedAt"
                tone="blue"
                title="已接单"
                :time="formatDate(workOrder.acceptedAt)"
                :description="`接单人: ${workOrder.acceptedBy || '-'}`"
                :icon="UserIcon"
              />
              <TimelineItem
                v-if="workOrder?.dispatchedAt"
                tone="amber"
                title="已派单"
                :time="formatDate(workOrder.dispatchedAt)"
                :description="`派单给: ${workOrder.assignee || '-'}`"
                :icon="SendIcon"
              />
              <TimelineItem
                tone="indigo"
                title="工单创建"
                :time="formatDate(workOrder?.createdAt)"
                :description="`创建人: ${workOrder?.createdBy || '系统自动'}`"
                :icon="PlusCircleIcon"
              />
            </div>
          </aside>
        </div>
      </div>
    </div>

    <WorkOrderActionModal
      :open="modalOpen"
      :mode="actionMode"
      :work-order="workOrder"
      :submitting="loading.action"
      :error-message="actionError"
      :trace-id="traceId"
      @close="modalOpen = false"
      @dispatch="handleDispatchSubmit"
      @writeback="handleWritebackSubmit"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  CircleAlertIcon,
  ClockIcon,
  FilePenLineIcon,
  Loader2Icon,
  LockKeyholeIcon,
  PlusCircleIcon,
  Repeat2Icon,
  SendIcon,
  UserCheckIcon,
  UserIcon
} from 'lucide-vue-next';
import WorkOrderActionModal from '@/components/WorkOrderActionModal.vue';
import { ApiClientError } from '@/services/api';
import {
  acceptWorkOrder,
  dispatchWorkOrder,
  getWorkOrderDetail,
  transferWorkOrder,
  writebackWorkOrder
} from '@/services/workorders';
import { useAuthStore } from '@/stores/auth';
import type { WorkOrderDispatchRequest, WorkOrderResponse, WorkOrderWritebackRequest } from '@/types/api';

type ActionMode = 'dispatch' | 'transfer' | 'writeback';

const InfoItem = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
    monospace: { type: Boolean, default: false }
  },
  setup(props) {
    return () =>
      h('div', { class: 'rounded-lg border border-white/10 bg-white/[0.045] p-4' }, [
        h('div', { class: 'mb-2 text-xs text-slate-400' }, props.label),
        h(
          'div',
          {
            class: [
              'font-medium text-white',
              props.monospace ? 'break-all font-mono text-xs leading-5 text-slate-100' : 'text-sm'
            ]
          },
          props.value
        )
      ]);
  }
});

const TimelineItem = defineComponent({
  props: {
    title: { type: String, required: true },
    time: { type: String, required: true },
    description: { type: String, required: true },
    tone: { type: String, required: true },
    icon: { type: Object, required: true }
  },
  setup(props) {
    const toneClass: Record<string, string> = {
      amber: 'border-amber-300/30 bg-amber-400/15 text-amber-100',
      blue: 'border-blue-300/30 bg-blue-400/15 text-blue-100',
      emerald: 'border-emerald-300/30 bg-emerald-400/15 text-emerald-100',
      indigo: 'border-indigo-300/30 bg-indigo-400/15 text-indigo-100',
      slate: 'border-white/10 bg-white/[0.055] text-slate-300'
    };
    return () =>
      h('div', { class: 'relative ml-6' }, [
        h(
          'span',
          {
            class: [
              'absolute -left-9 flex h-6 w-6 items-center justify-center rounded-full border ring-4 ring-[#111117]',
              toneClass[props.tone] ?? toneClass.slate
            ]
          },
          [h(props.icon, { class: 'h-3.5 w-3.5' })]
        ),
        h('h3', { class: 'mb-1 text-sm font-semibold text-white' }, props.title),
        h('time', { class: 'mb-2 block text-xs font-normal text-slate-500' }, props.time),
        h('div', { class: 'text-xs text-slate-400' }, props.description)
      ]);
  }
});

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const workOrderId = route.params.id as string;
const workOrder = ref<WorkOrderResponse | null>(null);
const pageError = ref('');
const actionError = ref('');
const successMessage = ref('');
const traceId = ref('');
const modalOpen = ref(false);
const actionMode = ref<ActionMode>('dispatch');

const loading = ref({
  fetch: false,
  action: false
});

const hasDispatchPermission = computed(() =>
  authStore.hasAllPermissions(['ENTRY:EMGC', 'MENU:WORKORDER:DISPATCH'])
);
const canDispatch = computed(() => workOrder.value?.status === 'CREATED' && hasDispatchPermission.value);
const canTransfer = computed(() =>
  ['DISPATCHED', 'ACCEPTED'].includes(workOrder.value?.status ?? '') && hasDispatchPermission.value
);
const canWriteback = computed(() => Boolean(workOrder.value) && hasDispatchPermission.value);

const statusMap: Record<string, string> = {
  CREATED: '已创建',
  DISPATCHED: '已派单',
  ACCEPTED: '处理中',
  COMPLETED: '已完成',
  CLOSED: '已关闭'
};

const writebackMap: Record<string, string> = {
  FALSE_POSITIVE: '误报回写',
  MISSED_REPORT: '漏报回写',
  OTHER: '其他回写'
};

function statusLabel(status: string) {
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

function priorityBadgeClass(priority: string) {
  return {
    'border-rose-300/25 bg-rose-400/10 text-rose-100': priority === 'HIGH',
    'border-amber-300/25 bg-amber-400/10 text-amber-100': priority === 'MEDIUM',
    'border-white/10 bg-white/[0.045] text-slate-300': !priority || priority === 'LOW'
  };
}

function writebackTypeLabel(type?: string | null) {
  if (!type) return '未回写';
  return writebackMap[type] || type;
}

function formatDate(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

function openAction(mode: ActionMode) {
  actionMode.value = mode;
  actionError.value = '';
  successMessage.value = '';
  modalOpen.value = true;
}

async function loadData() {
  if (!workOrderId) return;
  loading.value.fetch = true;
  pageError.value = '';
  try {
    const response = await getWorkOrderDetail(workOrderId);
    traceId.value = response.traceId;
    if (response.success && response.data) {
      workOrder.value = response.data;
    }
  } catch (error) {
    applyError(error, pageError, '工单详情读取失败');
  } finally {
    loading.value.fetch = false;
  }
}

async function handleAccept() {
  loading.value.action = true;
  pageError.value = '';
  successMessage.value = '';
  try {
    const response = await acceptWorkOrder(workOrderId);
    traceId.value = response.traceId;
    if (response.data) {
      workOrder.value = response.data;
      successMessage.value = '工单已接单，状态已同步。';
    }
  } catch (error) {
    applyError(error, pageError, '接单失败');
  } finally {
    loading.value.action = false;
  }
}

async function handleDispatchSubmit(request: WorkOrderDispatchRequest) {
  loading.value.action = true;
  actionError.value = '';
  successMessage.value = '';
  try {
    const response =
      actionMode.value === 'transfer'
        ? await transferWorkOrder(workOrderId, request)
        : await dispatchWorkOrder(workOrderId, request);
    traceId.value = response.traceId;
    if (response.data) {
      workOrder.value = response.data;
      modalOpen.value = false;
      successMessage.value = actionMode.value === 'transfer' ? '工单已转派，处理人和 SLA 已更新。' : '工单已派发，处理人和 SLA 已更新。';
    }
  } catch (error) {
    applyError(error, actionError, actionMode.value === 'transfer' ? '转派失败' : '派单失败');
  } finally {
    loading.value.action = false;
  }
}

async function handleWritebackSubmit(request: WorkOrderWritebackRequest) {
  loading.value.action = true;
  actionError.value = '';
  successMessage.value = '';
  try {
    const response = await writebackWorkOrder(workOrderId, request.writebackType, request.writebackReason);
    traceId.value = response.traceId;
    if (response.data) {
      workOrder.value = response.data;
      modalOpen.value = false;
      successMessage.value = '回写已独立记录，工单状态未被关闭动作覆盖。';
    }
  } catch (error) {
    applyError(error, actionError, '回写失败');
  } finally {
    loading.value.action = false;
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

onMounted(() => {
  loadData();
});
</script>
