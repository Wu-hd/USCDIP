<template>
  <div class="h-full flex flex-col bg-slate-50 dark:bg-ink text-slate-900 dark:text-slate-100">
    <div class="flex-1 p-6 overflow-y-auto">
      <div class="max-w-7xl mx-auto space-y-6">
        
        <!-- Header -->
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div class="flex items-center gap-3">
              <h1 class="text-2xl font-bold tracking-tight">工单详情</h1>
              <span class="px-2.5 py-1 text-xs font-semibold rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
                {{ workOrder?.workOrderType || '未知' }}
              </span>
              <span 
                v-if="workOrder?.status"
                class="px-2.5 py-1 text-xs font-semibold rounded-full"
                :class="{
                  'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300': workOrder.status === 'CREATED',
                  'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300': workOrder.status === 'DISPATCHED',
                  'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300': workOrder.status === 'ACCEPTED',
                  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300': workOrder.status === 'COMPLETED',
                  'bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400': workOrder.status === 'CLOSED'
                }"
              >
                {{ statusLabel(workOrder.status) }}
              </span>
            </div>
            <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">ID: {{ workOrder?.workOrderId }}</p>
          </div>
          <div class="flex items-center gap-3">
            <button 
              v-if="workOrder?.status === 'DISPATCHED'"
              @click="handleAccept"
              :disabled="loading.action"
              class="px-4 py-2 text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              接单
            </button>
            <button class="px-4 py-2 text-sm font-medium rounded-lg text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700 dark:hover:bg-slate-750 transition-colors">
              返回列表
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          
          <!-- Key Info (Left Col) -->
          <div class="col-span-1 lg:col-span-2 space-y-6">
            <div class="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6">
              <h2 class="text-lg font-semibold mb-4">工单信息</h2>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8">
                <div class="md:col-span-2">
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">工单描述</div>
                  <div class="font-medium whitespace-pre-wrap">{{ workOrder?.description || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">优先级</div>
                  <div class="font-medium inline-flex items-center">
                    {{ workOrder?.priority || '-' }}
                  </div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关联事件 ID</div>
                  <div class="font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer">
                    {{ workOrder?.incidentId || '-' }}
                  </div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">处理人</div>
                  <div class="font-medium">{{ workOrder?.assignee || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">SLA 到期时间</div>
                  <div class="font-medium">{{ formatDate(workOrder?.slaDueAt) }}</div>
                </div>
              </div>
            </div>
            
            <div class="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6" v-if="workOrder?.completionSummary || workOrder?.closeReason">
              <h2 class="text-lg font-semibold mb-4">处理结果</h2>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8">
                <div class="md:col-span-2" v-if="workOrder?.completionSummary">
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">处理总结</div>
                  <div class="font-medium whitespace-pre-wrap">{{ workOrder.completionSummary }}</div>
                </div>
                <div class="md:col-span-2" v-if="workOrder?.closeReason">
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关闭原因</div>
                  <div class="font-medium whitespace-pre-wrap">{{ workOrder.closeReason }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Timeline (Right Col) -->
          <div class="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6">
            <h2 class="text-lg font-semibold mb-6 flex items-center">
              <ClockIcon class="w-5 h-5 mr-2 text-slate-400" />
              工单流转
            </h2>
            <div class="relative border-l border-slate-200 dark:border-slate-800 ml-3 space-y-6">
              
              <div class="mb-6 ml-6" v-if="workOrder?.closedAt">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-slate-100 dark:bg-slate-800 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <CheckCircleIcon class="w-3.5 h-3.5 text-slate-600 dark:text-slate-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">工单关闭</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(workOrder.closedAt) }}</time>
                <div class="text-xs text-slate-500 mt-1">关闭人: {{ workOrder.closedBy || '-' }}</div>
              </div>

              <div class="mb-6 ml-6" v-if="workOrder?.completedAt">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-emerald-100 dark:bg-emerald-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <CheckCircleIcon class="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">处理完成</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(workOrder.completedAt) }}</time>
                <div class="text-xs text-slate-500 mt-1">处理人: {{ workOrder.completedBy || '-' }}</div>
              </div>

              <div class="mb-6 ml-6" v-if="workOrder?.acceptedAt">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-blue-100 dark:bg-blue-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <UserIcon class="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">已接单</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(workOrder.acceptedAt) }}</time>
                <div class="text-xs text-slate-500 mt-1">接单人: {{ workOrder.acceptedBy || '-' }}</div>
              </div>

              <div class="mb-6 ml-6" v-if="workOrder?.dispatchedAt">
                 <span class="absolute flex items-center justify-center w-6 h-6 bg-amber-100 dark:bg-amber-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <SendIcon class="w-3.5 h-3.5 text-amber-600 dark:text-amber-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">已派单</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(workOrder.dispatchedAt) }}</time>
                <div class="text-xs text-slate-500 mt-1">派单给: {{ workOrder.assignee || '-' }}</div>
              </div>

              <div class="mb-6 ml-6">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-indigo-100 dark:bg-indigo-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <PlusCircleIcon class="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">工单创建</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(workOrder?.createdAt) }}</time>
                <div class="text-xs text-slate-500 mt-1">创建人: {{ workOrder?.createdBy || '系统自动' }}</div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { 
  AlertTriangleIcon, ClockIcon, CheckCircleIcon, UserIcon, SendIcon, PlusCircleIcon 
} from 'lucide-vue-next';
import { getWorkOrderDetail, acceptWorkOrder } from '@/services/workorders';
import type { WorkOrderResponse } from '@/types/api';

const route = useRoute();
const workOrderId = route.params.id as string;
const workOrder = ref<WorkOrderResponse | null>(null);

const loading = ref({
  fetch: false,
  action: false
});

const statusMap: Record<string, string> = {
  'CREATED': '已创建',
  'DISPATCHED': '已派单',
  'ACCEPTED': '处理中',
  'COMPLETED': '已完成',
  'CLOSED': '已关闭'
};

const statusLabel = (s: string) => statusMap[s] || s;

const formatDate = (ds?: string | null) => {
  if (!ds) return '-';
  return new Date(ds).toLocaleString();
};

const loadData = async () => {
  if (!workOrderId) return;
  loading.value.fetch = true;
  try {
    const res = await getWorkOrderDetail(workOrderId);
    if (res.success && res.data) {
      workOrder.value = res.data;
    }
  } catch (err) {
    console.error('Failed to load work order detail', err);
  } finally {
    loading.value.fetch = false;
  }
};

const handleAccept = async () => {
  if (!workOrderId) return;
  loading.value.action = true;
  try {
    const res = await acceptWorkOrder(workOrderId);
    if (res.success && res.data) {
      workOrder.value = res.data;
    }
  } catch (err) {
    console.error('Failed to accept work order', err);
  } finally {
    loading.value.action = false;
  }
};

onMounted(() => {
  loadData();
});
</script>
