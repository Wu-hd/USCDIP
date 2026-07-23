<script setup lang="ts">
import { RISK_COLORS, RISK_LABELS } from '@/features/nationalMap/chinaMapConfig';
import type { RiskLevel } from '@/features/nationalMap/chinaMapTypes';

const levels: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
</script>

<template>
  <div class="border border-white/10 bg-slate-950/82 px-3 py-2 backdrop-blur-md">
    <p class="text-[10px] font-semibold uppercase text-slate-400">风险图例</p>
    <div class="mt-2 flex flex-wrap gap-x-3 gap-y-1.5">
      <span v-for="level in levels" :key="level" class="flex items-center gap-1.5 text-[10px] text-slate-300">
        <span class="h-2 w-2" :style="{ backgroundColor: RISK_COLORS[level] }"></span>
        {{ RISK_LABELS[level] }}
      </span>
    </div>
  </div>
</template>
