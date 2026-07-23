<script setup lang="ts">
import { Building2, MapPinned, RotateCcw } from 'lucide-vue-next';
import { computed } from 'vue';

import { RISK_COLORS, RISK_LABELS } from '@/features/nationalMap/chinaMapConfig';
import type { CityNodeData, ProvinceHealthData } from '@/features/nationalMap/chinaMapTypes';

const props = defineProps<{
  province: ProvinceHealthData | null;
  city: CityNodeData | null;
}>();

defineEmits<{ reset: []; 'enter-hunan': [] }>();

const riskLabel = computed(() => props.province ? RISK_LABELS[props.province.riskLevel] : '-');
const riskColor = computed(() => props.province ? RISK_COLORS[props.province.riskLevel] : '#64748b');
</script>

<template>
  <section aria-labelledby="province-detail-title">
    <div class="flex items-center justify-between gap-3">
      <h2 id="province-detail-title" class="text-sm font-semibold text-white">区域详情</h2>
      <button class="icon-button focus-ring h-8 w-8" type="button" title="恢复全国视角" aria-label="恢复全国视角" @click="$emit('reset')">
        <RotateCcw class="h-3.5 w-3.5" />
      </button>
    </div>

    <div v-if="province" class="mt-3 border border-white/10 bg-white/[0.04] p-3">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <p class="truncate text-base font-semibold text-white">{{ province.provinceName }}</p>
          <p class="mt-1 font-mono text-[10px] text-slate-500">ADCODE {{ province.adcode }}</p>
        </div>
        <span class="shrink-0 border px-2 py-1 text-[10px]" :style="{ color: riskColor, borderColor: `${riskColor}55`, backgroundColor: `${riskColor}14` }">
          {{ riskLabel }}
        </span>
      </div>
      <dl class="mt-4 grid grid-cols-2 gap-px overflow-hidden border border-white/10 bg-white/10 text-xs">
        <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">健康评分</dt><dd class="mt-1 font-mono text-lg text-white">{{ province.healthScore }}</dd></div>
        <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">管线里程</dt><dd class="mt-1 font-mono text-sm text-white">{{ province.pipelineLengthKm.toLocaleString() }} km</dd></div>
        <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">在线设备</dt><dd class="mt-1 font-mono text-sm text-white">{{ province.onlineDeviceCount.toLocaleString() }}</dd></div>
        <div class="bg-[#0b111c] p-2.5"><dt class="text-slate-500">活动告警</dt><dd class="mt-1 font-mono text-sm text-white">{{ province.activeAlertCount }}</dd></div>
        <div class="col-span-2 bg-[#0b111c] p-2.5"><dt class="text-slate-500">待处置事件</dt><dd class="mt-1 font-mono text-sm text-white">{{ province.openIncidentCount }}</dd></div>
      </dl>
      <button
        v-if="province.adcode === '430000'"
        class="primary-button focus-ring mt-3 w-full"
        type="button"
        @click="$emit('enter-hunan')"
      >
        <MapPinned class="h-4 w-4" />
        进入湖南三维图
      </button>
      <button v-else class="secondary-button mt-3 w-full cursor-not-allowed" type="button" disabled title="该省三维地图暂未接入">
        <MapPinned class="h-4 w-4" />
        进入省级三维图
      </button>
      <p class="mt-2 text-center text-[10px] text-slate-500">{{ province.adcode === '430000' ? '真实高程与市州边界已接入' : '该省三维地图暂未接入' }}</p>
    </div>

    <div v-else class="mt-3 border border-dashed border-white/10 p-4 text-center">
      <MapPinned class="mx-auto h-6 w-6 text-cyan-200" aria-hidden="true" />
      <p class="mt-2 text-xs font-semibold text-white">选择省份查看健康详情</p>
      <p class="mt-1 text-[10px] leading-4 text-slate-500">悬浮查看摘要，点击保持高亮并聚焦相机。</p>
    </div>

    <div v-if="city" class="mt-3 border border-cyan-300/20 bg-cyan-400/[0.055] p-3">
      <div class="flex items-center gap-2 text-xs font-semibold text-white">
        <Building2 class="h-4 w-4 text-cyan-200" aria-hidden="true" />
        {{ city.cityName }}监测节点
      </div>
      <p class="mt-2 text-[11px] text-slate-300">
        设备 {{ city.deviceCount.toLocaleString() }} / 告警 {{ city.alertCount }} / 健康评分 {{ city.healthScore }}
      </p>
    </div>
  </section>
</template>
