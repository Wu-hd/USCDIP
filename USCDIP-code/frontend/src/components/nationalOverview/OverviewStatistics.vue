<script setup lang="ts">
import {
  Activity,
  BellRing,
  ClipboardList,
  Gauge,
  MapPinned,
  RadioTower,
  Route
} from 'lucide-vue-next';
import { computed } from 'vue';

import type { NationalSummary } from '@/features/nationalMap/chinaMapTypes';

const props = defineProps<{ summary: NationalSummary | null; loading: boolean }>();

const items = computed(() => [
  { key: 'health', label: '全国综合健康指数', value: props.summary?.healthScore.toFixed(1) ?? '-', unit: '/ 100', icon: Gauge, tone: 'text-emerald-200' },
  { key: 'regions', label: '接入省级区域', value: props.summary?.coveredProvinceCount.toLocaleString() ?? '-', unit: '个', icon: MapPinned, tone: 'text-cyan-200' },
  { key: 'pipeline', label: '地下管线总里程', value: props.summary?.pipelineLengthKm.toLocaleString() ?? '-', unit: 'km', icon: Route, tone: 'text-blue-200' },
  { key: 'devices', label: '在线监测设备', value: props.summary?.onlineDeviceCount.toLocaleString() ?? '-', unit: '台', icon: RadioTower, tone: 'text-violet-200' },
  { key: 'alerts', label: '当前活动告警', value: props.summary?.activeAlertCount.toLocaleString() ?? '-', unit: '条', icon: BellRing, tone: 'text-amber-200' },
  { key: 'incidents', label: '待处置事件', value: props.summary?.openIncidentCount.toLocaleString() ?? '-', unit: '起', icon: Activity, tone: 'text-rose-200' },
  { key: 'orders', label: '进行中工单', value: props.summary?.activeWorkOrderCount.toLocaleString() ?? '-', unit: '单', icon: ClipboardList, tone: 'text-orange-200' }
]);
</script>

<template>
  <section class="border-b border-white/10 bg-[#080d16]/85 px-4 py-3 sm:px-6" aria-label="全国态势指标">
    <div class="app-frame grid grid-cols-2 gap-px overflow-hidden border border-white/10 bg-white/10 md:grid-cols-4 xl:grid-cols-7">
      <div v-for="item in items" :key="item.key" class="min-w-0 bg-[#0b111c] px-3 py-3">
        <div class="flex items-center gap-2 text-[11px] text-slate-400">
          <component :is="item.icon" class="h-3.5 w-3.5 shrink-0" :class="item.tone" aria-hidden="true" />
          <span class="truncate">{{ item.label }}</span>
        </div>
        <div class="mt-1 flex min-h-8 items-end gap-1">
          <span v-if="loading" class="h-5 w-20 animate-pulse bg-white/10"></span>
          <span v-else class="font-display text-xl font-semibold text-white">{{ item.value }}</span>
          <span class="pb-0.5 text-[10px] text-slate-500">{{ item.unit }}</span>
        </div>
      </div>
    </div>
  </section>
</template>
