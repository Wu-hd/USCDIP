<script setup lang="ts">
import { ArrowLeft, Mountain, RotateCcw } from 'lucide-vue-next';

import type { HunanMapLayerState } from '@/features/hunanMap/hunanMapTypes';

const props = defineProps<{
  layers: HunanMapLayerState;
  exaggeration: number;
}>();

const emit = defineEmits<{
  back: [];
  reset: [];
  'toggle-layer': [key: keyof HunanMapLayerState];
  'update:exaggeration': [value: number];
}>();

const layerOptions: Array<{ key: keyof HunanMapLayerState; label: string }> = [
  { key: 'boundaries', label: '市州边界' },
  { key: 'labels', label: '市州标签' },
  { key: 'station', label: '监测站点' }
];
</script>

<template>
  <div class="border border-white/10 bg-slate-950/86 p-3 shadow-xl backdrop-blur-md">
    <div class="flex items-center justify-between gap-2">
      <button class="icon-button focus-ring h-8 w-8" type="button" title="返回全国地图" aria-label="返回全国地图" @click="emit('back')">
        <ArrowLeft class="h-4 w-4" />
      </button>
      <div class="flex min-w-0 items-center gap-2 text-xs font-semibold text-white">
        <Mountain class="h-4 w-4 shrink-0 text-emerald-200" aria-hidden="true" />
        <span class="truncate">湖南真实地形</span>
      </div>
      <button class="icon-button focus-ring h-8 w-8" type="button" title="恢复湖南视角" aria-label="恢复湖南视角" @click="emit('reset')">
        <RotateCcw class="h-4 w-4" />
      </button>
    </div>

    <label class="mt-3 block text-[11px] text-slate-300">
      <span class="flex items-center justify-between gap-3">
        <span>高程夸张</span>
        <span class="font-mono text-emerald-200">{{ props.exaggeration.toFixed(1) }}x</span>
      </span>
      <input
        class="mt-2 h-1.5 w-full cursor-pointer accent-emerald-500"
        type="range"
        min="1"
        max="3"
        step="0.1"
        :value="props.exaggeration"
        @input="emit('update:exaggeration', Number(($event.target as HTMLInputElement).value))"
      />
    </label>

    <div class="mt-3 grid grid-cols-2 gap-2">
      <label v-for="item in layerOptions" :key="item.key" class="flex cursor-pointer items-center gap-2 text-[11px] text-slate-300">
        <input class="h-3.5 w-3.5 accent-emerald-500" type="checkbox" :checked="props.layers[item.key]" @change="emit('toggle-layer', item.key)" />
        {{ item.label }}
      </label>
    </div>
  </div>
</template>
