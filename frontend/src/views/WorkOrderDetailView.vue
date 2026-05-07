<template>
  <div class="flex h-full flex-col bg-slate-50 text-slate-900 dark:bg-ink dark:text-slate-100">
    <div class="flex-1 overflow-y-auto p-4 sm:p-6">
      <div class="mx-auto max-w-7xl space-y-6">
        <header class="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div class="flex flex-col justify-between gap-4 lg:flex-row lg:items-start">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-3">
                <h1 class="text-2xl font-bold tracking-tight">工单详情</h1>
                <span class="rounded-full bg-indigo-100 px-2.5 py-1 text-xs font-semibold text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
                  {{ workOrder?.workOrderType || '未知' }}
                </span>
                <span
                  v-if="workOrder?.status"
                  class="rounded-full px-2.5 py-1 text-xs font-semibold"
                  :class="statusBadgeClass(workOrder.status)"
                >
                  {{ statusLabel(workOrder.status) }}
                </span>
              </div>
              <p class="mt-2 break-all font-mono text-xs text-slate-500 dark:text-slate-400">
                {{ workOrder?.workOrderId || workOrderId }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <button
                v-if="canDispatch"
                type="button"
                class="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 dark:focus-visible:ring-offset-slate-950"
                :disabled="loading.action"
                @click="openAction('dispatch')"
              >
                <SendIcon class="h-4 w-4" />
                派单
              </button>
              <button
                v-if="canTransfer"
                type="button"
                class="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-4 text-sm font-semibold text-blue-700 transition-colors hover:bg-blue-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-blue-900/70 dark:bg-blue-950/30 dark:text-blue-200 dark:hover:bg-blue-950/50"
                :disabled="loading.action"
                @click="openAction('transfer')"
              >
                <Repeat2Icon class="h-4 w-4" />
                转派
              </button>
              <button
                v-if="canWriteback"
                type="button"
                class="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg bg-amber-500 px-4 text-sm font-semibold text-white transition-colors hover:bg-amber-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 dark:focus-visible:ring-offset-slate-950"
                :disabled="loading.action"
                @click="openAction('writeback')"
              >
                <FilePenLineIcon class="h-4 w-4" />
                回写
              </button>
              <button
                v-if="workOrder?.status === 'DISPATCHED'"
                type="button"
                class="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg bg-emerald-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 dark:focus-visible:ring-offset-slate-950"
                :disabled="loading.action"
                @click="handleAccept"
              >
                <Loader2Icon v-if="loading.action" class="h-4 w-4 animate-spin" />
                <UserCheckIcon v-else class="h-4 w-4" />
                接单
              </button>
              <button
                type="button"
                class="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
                @click="router.back()"
              >
                <ArrowLeftIcon class="h-4 w-4" />
                返回
              </button>
            </div>
          </div>

          <div
            v-if="!hasDispatchPermission"
            class="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-900/70 dark:bg-amber-950/30 dark:text-amber-200"
          >
            <LockKeyholeIcon class="mt-0.5 h-4 w-4 shrink-0" />
            当前账号缺少 MENU:WORKORDER:DISPATCH，派单、转派和回写入口已隐藏。
          </div>
          <div
            v-if="successMessage"
            class="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-900/70 dark:bg-emerald-950/30 dark:text-emerald-200"
          >
            <CheckCircleIcon class="mt-0.5 h-4 w-4 shrink-0" />
            {{ successMessage }}
          </div>
          <div
            v-if="pageError"
            class="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300"
          >
            <CircleAlertIcon class="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              {{ pageError }}
              <span v-if="traceId" class="mt-1 block font-mono text-xs">traceId: {{ traceId }}</span>
            </span>
          </div>
        </header>

        <div v-if="loading.fetch" class="rounded-lg border border-slate-200 bg-white p-10 text-center text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
          <Loader2Icon class="mx-auto mb-3 h-6 w-6 animate-spin text-blue-500" />
          正在读取工单详情
        </div>

        <div v-else class="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div class="space-y-6 lg:col-span-2">
            <section class="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <div class="mb-5 flex items-center justify-between gap-4">
                <h2 class="text-lg font-semibold">工单信息</h2>
                <span class="rounded-md bg-slate-100 px-2 py-1 font-mono text-xs text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  v{{ workOrder?.versionNo ?? '-' }}
                </span>
              </div>
              <div class="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
                <div class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-500 dark:text-slate-400">工单描述</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap">{{ workOrder?.description || '-' }}</div>
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
              class="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900"
            >
              <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
                <h2 class="text-lg font-semibold">处理结果与回写</h2>
                <span
                  v-if="workOrder?.writebackType"
                  class="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800 dark:bg-amber-500/15 dark:text-amber-200"
                >
                  {{ writebackTypeLabel(workOrder.writebackType) }}
                </span>
              </div>
              <div class="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
                <div v-if="workOrder?.completionSummary" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-500 dark:text-slate-400">处理总结</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap">{{ workOrder.completionSummary }}</div>
                </div>
                <div v-if="workOrder?.closeReason" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-500 dark:text-slate-400">关闭原因</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap">{{ workOrder.closeReason }}</div>
                </div>
                <div v-if="workOrder?.writebackReason" class="md:col-span-2">
                  <div class="mb-1 text-sm text-slate-500 dark:text-slate-400">回写原因</div>
                  <div class="font-medium leading-7 whitespace-pre-wrap">{{ workOrder.writebackReason }}</div>
                </div>
                <InfoItem v-if="workOrder?.writebackAt" label="回写时间" :value="formatDate(workOrder.writebackAt)" />
              </div>
            </section>
          </div>

          <aside class="rounded-lg border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 class="mb-6 flex items-center text-lg font-semibold">
              <ClockIcon class="mr-2 h-5 w-5 text-slate-400" />
              工单流转
            </h2>
            <div class="relative ml-3 space-y-6 border-l border-slate-200 dark:border-slate-800">
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
  </div>
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
      h('div', [
        h('div', { class: 'mb-1 text-sm text-slate-500 dark:text-slate-400' }, props.label),
        h(
          'div',
          {
            class: [
              'font-medium text-slate-900 dark:text-slate-100',
              props.monospace ? 'break-all font-mono text-xs' : ''
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
      amber: 'bg-amber-100 text-amber-600 dark:bg-amber-900 dark:text-amber-300',
      blue: 'bg-blue-100 text-blue-600 dark:bg-blue-900 dark:text-blue-300',
      emerald: 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900 dark:text-emerald-300',
      indigo: 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900 dark:text-indigo-300',
      slate: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
    };
    return () =>
      h('div', { class: 'relative ml-6' }, [
        h(
          'span',
          {
            class: [
              'absolute -left-9 flex h-6 w-6 items-center justify-center rounded-full ring-4 ring-white dark:ring-slate-900',
              toneClass[props.tone] ?? toneClass.slate
            ]
          },
          [h(props.icon, { class: 'h-3.5 w-3.5' })]
        ),
        h('h3', { class: 'mb-1 text-sm font-semibold text-slate-900 dark:text-white' }, props.title),
        h('time', { class: 'mb-2 block text-xs font-normal text-slate-400 dark:text-slate-500' }, props.time),
        h('div', { class: 'text-xs text-slate-500 dark:text-slate-400' }, props.description)
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
    'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300': status === 'CREATED',
    'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300': status === 'DISPATCHED',
    'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300': status === 'ACCEPTED',
    'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300': status === 'COMPLETED',
    'bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400': status === 'CLOSED'
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
