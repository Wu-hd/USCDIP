<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        @click.self="emit('close')"
      >
        <section
          class="w-full max-w-2xl overflow-hidden rounded-lg border border-slate-200 bg-white shadow-2xl shadow-slate-950/20 dark:border-slate-800 dark:bg-[#16161d]"
        >
          <header class="border-b border-slate-200 bg-slate-50 px-5 py-4 dark:border-slate-800 dark:bg-slate-900/70">
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300">
                    <component :is="headerIcon" class="h-4 w-4" />
                  </span>
                  <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                      F-13 Work Order Action
                    </p>
                    <h2 :id="titleId" class="text-lg font-semibold text-slate-950 dark:text-white">
                      {{ title }}
                    </h2>
                  </div>
                </div>
                <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">
                  {{ subtitle }}
                </p>
              </div>
              <button
                type="button"
                class="inline-flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-slate-200 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
                aria-label="关闭弹窗"
                @click="emit('close')"
              >
                <XIcon class="h-4 w-4" />
              </button>
            </div>
          </header>

          <div class="grid gap-0 md:grid-cols-[1fr_16rem]">
            <form class="space-y-4 px-5 py-5" @submit.prevent="handleSubmit">
              <template v-if="isDispatchMode">
                <div>
                  <label class="text-sm font-semibold text-slate-700 dark:text-slate-200">巡检人员</label>
                  <div class="mt-2 grid gap-2">
                    <label
                      v-for="assignee in assigneeOptions"
                      :key="assignee.key"
                      class="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
                      :class="selectedAssignee === assignee.key ? 'border-blue-500 bg-blue-50 dark:border-blue-400 dark:bg-blue-500/10' : 'border-slate-200 bg-white hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900/40 dark:hover:bg-slate-900'"
                    >
                      <input
                        v-model="selectedAssignee"
                        class="mt-1 h-4 w-4 border-slate-300 text-blue-600 focus:ring-blue-500"
                        type="radio"
                        :value="assignee.key"
                      />
                      <span class="min-w-0">
                        <span class="block text-sm font-semibold text-slate-900 dark:text-white">
                          {{ assignee.displayName }}
                        </span>
                        <span class="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">
                          {{ assignee.username }} · {{ assignee.userId || '手动输入用户' }}
                        </span>
                      </span>
                    </label>
                  </div>
                </div>

                <div v-if="selectedAssignee === 'custom'" class="grid gap-3 sm:grid-cols-2">
                  <label class="block">
                    <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">处理人账号</span>
                    <input
                      v-model.trim="customAssignee"
                      class="mt-2 h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                      placeholder="例如 zhangsan"
                    />
                  </label>
                  <label class="block">
                    <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">用户 ID（可选）</span>
                    <input
                      v-model.trim="customAssigneeUserId"
                      class="mt-2 h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                      placeholder="例如 U-INSPECT-001"
                    />
                  </label>
                </div>

                <label class="block">
                  <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">SLA 到期时间</span>
                  <input
                    v-model="slaDueAt"
                    class="mt-2 h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                    type="datetime-local"
                  />
                </label>

                <label class="block">
                  <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">派单原因</span>
                  <textarea
                    v-model.trim="dispatchReason"
                    class="mt-2 min-h-24 w-full resize-y rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                    placeholder="说明现场复核目标、处置要求或转派原因"
                  />
                </label>
              </template>

              <template v-else>
                <div>
                  <label class="text-sm font-semibold text-slate-700 dark:text-slate-200">回写类型</label>
                  <div class="mt-2 grid gap-2">
                    <label
                      v-for="option in writebackOptions"
                      :key="option.value"
                      class="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors"
                      :class="writebackType === option.value ? 'border-amber-500 bg-amber-50 dark:border-amber-400 dark:bg-amber-500/10' : 'border-slate-200 bg-white hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900/40 dark:hover:bg-slate-900'"
                    >
                      <input
                        v-model="writebackType"
                        class="mt-1 h-4 w-4 border-slate-300 text-amber-600 focus:ring-amber-500"
                        type="radio"
                        :value="option.value"
                      />
                      <span>
                        <span class="block text-sm font-semibold text-slate-900 dark:text-white">{{ option.label }}</span>
                        <span class="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">{{ option.description }}</span>
                      </span>
                    </label>
                  </div>
                </div>

                <label class="block">
                  <span class="text-sm font-semibold text-slate-700 dark:text-slate-200">回写原因</span>
                  <textarea
                    v-model.trim="writebackReason"
                    class="mt-2 min-h-32 w-full resize-y rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 dark:border-slate-700 dark:bg-slate-950 dark:text-white"
                    placeholder="误报或漏报需要独立记录原因，不能作为关闭工单的替代动作"
                  />
                </label>
              </template>

              <div v-if="localError || errorMessage" class="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-300">
                {{ localError || errorMessage }}
                <span v-if="traceId" class="mt-1 block font-mono text-xs text-rose-500 dark:text-rose-400">traceId: {{ traceId }}</span>
              </div>

              <div class="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-end dark:border-slate-800">
                <button
                  type="button"
                  class="inline-flex h-10 cursor-pointer items-center justify-center rounded-lg border border-slate-200 px-4 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                  @click="emit('close')"
                >
                  取消
                </button>
                <button
                  type="submit"
                  class="inline-flex h-10 cursor-pointer items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
                  :class="isDispatchMode ? 'bg-blue-600 hover:bg-blue-700 focus-visible:ring-blue-500 dark:focus-visible:ring-offset-slate-950' : 'bg-amber-500 hover:bg-amber-600 focus-visible:ring-amber-500 dark:focus-visible:ring-offset-slate-950'"
                  :disabled="submitting"
                >
                  <Loader2Icon v-if="submitting" class="h-4 w-4 animate-spin" />
                  <component v-else :is="submitIcon" class="h-4 w-4" />
                  {{ submitLabel }}
                </button>
              </div>
            </form>

            <aside class="border-t border-slate-200 bg-slate-50 px-5 py-5 md:border-l md:border-t-0 dark:border-slate-800 dark:bg-slate-950/45">
              <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-500">
                Current Work Order
              </p>
              <dl class="mt-4 space-y-3 text-sm">
                <div>
                  <dt class="text-xs text-slate-500 dark:text-slate-400">工单 ID</dt>
                  <dd class="mt-1 break-all font-mono text-xs font-semibold text-slate-900 dark:text-white">
                    {{ workOrder?.workOrderId || '-' }}
                  </dd>
                </div>
                <div>
                  <dt class="text-xs text-slate-500 dark:text-slate-400">当前状态</dt>
                  <dd class="mt-1 font-semibold text-slate-900 dark:text-white">{{ workOrder?.status || '-' }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-slate-500 dark:text-slate-400">当前处理人</dt>
                  <dd class="mt-1 font-semibold text-slate-900 dark:text-white">{{ workOrder?.assignee || '-' }}</dd>
                </div>
                <div>
                  <dt class="text-xs text-slate-500 dark:text-slate-400">对象链</dt>
                  <dd class="mt-1 text-slate-700 dark:text-slate-200">
                    {{ workOrder?.segmentId || '-' }} / {{ workOrder?.nodeId || '-' }}
                  </dd>
                </div>
              </dl>

              <div class="mt-5 rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs leading-5 text-blue-800 dark:border-blue-900/70 dark:bg-blue-950/30 dark:text-blue-200">
                <ShieldCheckIcon class="mb-2 h-4 w-4" />
                派单、转派和回写均由后端状态机与权限矩阵校验，并进入统一审计。
              </div>
            </aside>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  FilePenLineIcon,
  Loader2Icon,
  Repeat2Icon,
  SendIcon,
  ShieldCheckIcon,
  XIcon
} from 'lucide-vue-next';
import type { WorkOrderDispatchRequest, WorkOrderResponse, WorkOrderWritebackRequest } from '@/types/api';

type ActionMode = 'dispatch' | 'transfer' | 'writeback';

const props = defineProps<{
  open: boolean;
  mode: ActionMode;
  workOrder: WorkOrderResponse | null;
  submitting?: boolean;
  errorMessage?: string;
  traceId?: string;
}>();

const emit = defineEmits<{
  close: [];
  dispatch: [request: WorkOrderDispatchRequest];
  writeback: [request: WorkOrderWritebackRequest];
}>();

const assigneeOptions = [
  {
    key: 'U-INSPECT-001',
    userId: 'U-INSPECT-001',
    username: 'zhangsan',
    displayName: '巡检人员张三'
  },
  {
    key: 'custom',
    userId: '',
    username: 'custom',
    displayName: '手动输入处理人'
  }
];

const writebackOptions = [
  {
    value: 'FALSE_POSITIVE',
    label: '误报回写',
    description: '现场复核后确认告警或事件判断不成立。'
  },
  {
    value: 'MISSED_REPORT',
    label: '漏报回写',
    description: '现场发现异常，但算法或规则链路未及时识别。'
  },
  {
    value: 'OTHER',
    label: '其他回写',
    description: '记录补充校验、口径偏差或人工修正原因。'
  }
] as const;

const selectedAssignee = ref('U-INSPECT-001');
const customAssignee = ref('');
const customAssigneeUserId = ref('');
const slaDueAt = ref('');
const dispatchReason = ref('');
const writebackType = ref<WorkOrderWritebackRequest['writebackType']>('FALSE_POSITIVE');
const writebackReason = ref('');
const localError = ref('');

const isDispatchMode = computed(() => props.mode === 'dispatch' || props.mode === 'transfer');
const titleId = computed(() => `work-order-action-${props.mode}`);
const title = computed(() => {
  if (props.mode === 'transfer') return '转派工单';
  if (props.mode === 'writeback') return '回写处置结论';
  return '派发工单';
});
const subtitle = computed(() => {
  if (props.mode === 'writeback') {
    return '误报、漏报或其他处置回写会独立记录，不会替代关闭工单。';
  }
  return '选择巡检人员、设置 SLA，并补充本次派单原因。';
});
const headerIcon = computed(() => {
  if (props.mode === 'transfer') return Repeat2Icon;
  if (props.mode === 'writeback') return FilePenLineIcon;
  return SendIcon;
});
const submitIcon = computed(() => (props.mode === 'writeback' ? FilePenLineIcon : SendIcon));
const submitLabel = computed(() => {
  if (props.mode === 'transfer') return '确认转派';
  if (props.mode === 'writeback') return '提交回写';
  return '确认派单';
});

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    localError.value = '';
    selectedAssignee.value = 'U-INSPECT-001';
    customAssignee.value = props.workOrder?.assignee ?? '';
    customAssigneeUserId.value = props.workOrder?.assigneeUserId ?? '';
    slaDueAt.value = toDateTimeLocal(props.workOrder?.slaDueAt) || toDateTimeLocal(defaultSlaDate());
    dispatchReason.value = '';
    writebackType.value = (props.workOrder?.writebackType as WorkOrderWritebackRequest['writebackType']) || 'FALSE_POSITIVE';
    writebackReason.value = props.workOrder?.writebackReason ?? '';
  }
);

function handleSubmit() {
  localError.value = '';
  if (isDispatchMode.value) {
    const request = buildDispatchRequest();
    if (!request.assignee) {
      localError.value = '请填写处理人账号，后端派单接口要求 assignee 非空。';
      return;
    }
    emit('dispatch', request);
    return;
  }

  if (!writebackReason.value.trim()) {
    localError.value = '请填写回写原因，误报/漏报必须独立留痕。';
    return;
  }
  emit('writeback', {
    writebackType: writebackType.value,
    writebackReason: writebackReason.value.trim()
  });
}

function buildDispatchRequest(): WorkOrderDispatchRequest {
  if (selectedAssignee.value === 'custom') {
    return {
      assigneeUserId: customAssigneeUserId.value || null,
      assignee: customAssignee.value.trim(),
      slaDueAt: slaDueAt.value || null,
      reason: dispatchReason.value.trim() || null
    };
  }

  const assignee = assigneeOptions[0];
  return {
    assigneeUserId: assignee.userId,
    assignee: assignee.username,
    slaDueAt: slaDueAt.value || null,
    reason: dispatchReason.value.trim() || null
  };
}

function defaultSlaDate() {
  const date = new Date();
  date.setHours(date.getHours() + 4, 0, 0, 0);
  return date.toISOString();
}

function toDateTimeLocal(value?: string | null) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 16);
  }
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
</script>

<style scoped>
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
  transform: scale(0.98);
}
</style>
