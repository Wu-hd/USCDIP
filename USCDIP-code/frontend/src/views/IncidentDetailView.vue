<template>
  <div class="app-frame app-viewport flex flex-col bg-slate-50 text-slate-900 dark:bg-ink dark:text-slate-100">
    <div class="flex-1 p-6 overflow-y-auto">
      <div class="w-full space-y-6">
        
        <!-- Header -->
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div class="flex items-center gap-3">
              <h1 class="text-2xl font-bold tracking-tight">事件详情</h1>
              <span class="px-2.5 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
                {{ incident?.incidentType || '未知' }}
              </span>
              <span 
                v-if="incident?.status"
                class="px-2.5 py-1 text-xs font-semibold rounded-full"
                :class="{
                  'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300': incident.status === 'UNCONFIRMED',
                  'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300': incident.status === 'CONFIRMED',
                  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300': incident.status === 'RESOLVED',
                  'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300': incident.status === 'CLOSED'
                }"
              >
                {{ statusLabel(incident.status) }}
              </span>
            </div>
            <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">ID: {{ incident?.incidentId }}</p>
          </div>
          <div class="flex items-center gap-3">
            <button 
              v-if="incident?.status === 'UNCONFIRMED'"
              @click="handleConfirm"
              :disabled="loading.confirm"
              class="px-4 py-2 text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Loader2Icon v-if="loading.confirm" class="w-4 h-4 animate-spin inline mr-2" />
              人工确认
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
              <h2 class="text-lg font-semibold mb-4">基本信息</h2>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8">
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">事件标题</div>
                  <div class="font-medium">{{ incident?.title || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">严重程度</div>
                  <div class="font-medium inline-flex items-center">
                    <AlertTriangleIcon class="w-4 h-4 mr-1.5" :class="severityColor(incident?.severity)" />
                    {{ incident?.severity || '-' }}
                  </div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关联管段 ID</div>
                  <div class="font-medium">{{ incident?.segmentId || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关联节点 ID</div>
                  <div class="font-medium">{{ incident?.nodeId || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关联设备 ID</div>
                  <div class="font-medium">{{ incident?.deviceId || '-' }}</div>
                </div>
                <div>
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">数据质量分数</div>
                  <div class="font-medium">{{ incident?.dqScoreSnapshot ?? '-' }}</div>
                </div>
                <div class="md:col-span-2">
                  <div class="text-sm text-slate-500 dark:text-slate-400 mb-1">关闭原因</div>
                  <div class="font-medium whitespace-pre-wrap">{{ incident?.closeReason || '-' }}</div>
                </div>
              </div>
            </div>

            <!-- Associated Alerts -->
            <div class="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6">
              <h2 class="text-lg font-semibold mb-4">关联信息</h2>
              <div class="text-sm text-slate-600 dark:text-slate-400">
                来源告警规则: <span class="font-medium text-slate-900 dark:text-slate-200">{{ incident?.sourceRuleCode || '-' }}</span><br/>
                原始告警ID: <span class="font-medium text-slate-900 dark:text-slate-200">{{ incident?.sourceAlertId || '-' }}</span>
              </div>
            </div>
          </div>

          <!-- Timeline (Right Col) -->
          <div class="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6">
            <h2 class="text-lg font-semibold mb-6 flex items-center">
              <ClockIcon class="w-5 h-5 mr-2 text-slate-400" />
              处置时间线
            </h2>
            <div class="relative border-l border-slate-200 dark:border-slate-800 ml-3 space-y-6">
              <div class="mb-6 ml-6" v-if="incident?.resolvedAt">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-emerald-100 dark:bg-emerald-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <CheckCircleIcon class="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">解决/恢复</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(incident.resolvedAt) }}</time>
              </div>

              <div class="mb-6 ml-6" v-if="incident?.confirmedAt">
                <span class="absolute flex items-center justify-center w-6 h-6 bg-purple-100 dark:bg-purple-900 rounded-full -left-3 ring-4 ring-white dark:ring-slate-900">
                  <UserCheckIcon class="w-3.5 h-3.5 text-purple-600 dark:text-purple-400" />
                </span>
                <h3 class="flex items-center mb-1 text-sm font-semibold text-slate-900 dark:text-white">人工确认</h3>
                <time class="block mb-2 text-xs font-normal text-slate-400 dark:text-slate-500">{{ formatDate(incident.confirmedAt) }}</time>
                <p class="text-xs text-slate-500 dark:text-slate-400 border border-slate-100 dark:border-slate-800 rounded p-2 mt-2">
                  操作人: {{ incident.confirmedBy || '系统/未知' }}
                </p>
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
  AlertTriangleIcon, ClockIcon, CheckCircleIcon, UserCheckIcon, Loader2Icon 
} from 'lucide-vue-next';
import { getIncidentDetail, confirmIncident } from '@/services/incidents';
import type { IncidentResponse } from '@/types/api';

const route = useRoute();
const incidentId = route.params.id as string;
const incident = ref<IncidentResponse | null>(null);

const loading = ref({
  fetch: false,
  confirm: false
});

const statusMap: Record<string, string> = {
  'UNCONFIRMED': '待确认',
  'CONFIRMED': '已确认',
  'RESOLVED': '已解决',
  'CLOSED': '已关闭'
};

const statusLabel = (s: string) => statusMap[s] || s;

const severityColor = (sev?: string) => {
  if (!sev) return 'text-slate-400';
  if (sev.includes('CRITICAL')) return 'text-red-500';
  if (sev.includes('HIGH')) return 'text-orange-500';
  if (sev.includes('MEDIUM')) return 'text-amber-500';
  return 'text-blue-500';
};

const formatDate = (ds?: string | null) => {
  if (!ds) return '-';
  return new Date(ds).toLocaleString();
};

const loadData = async () => {
  if (!incidentId) return;
  loading.value.fetch = true;
  try {
    const res = await getIncidentDetail(incidentId);
    if (res.success && res.data) {
      incident.value = res.data;
    }
  } catch (err) {
    console.error('Failed to load incident detail', err);
  } finally {
    loading.value.fetch = false;
  }
};

const handleConfirm = async () => {
  if (!incidentId) return;
  loading.value.confirm = true;
  try {
    const res = await confirmIncident(incidentId);
    if (res.success && res.data) {
      incident.value = res.data;
    }
  } catch (err) {
    console.error('Failed to confirm incident', err);
  } finally {
    loading.value.confirm = false;
  }
};

onMounted(() => {
  loadData();
});
</script>
