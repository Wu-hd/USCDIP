<script setup lang="ts">
import { RotateCcw, View } from 'lucide-vue-next';

import type { NationalMapLayerState } from '@/features/nationalMap/chinaMapTypes';

const props = defineProps<{
  layers: NationalMapLayerState;
  autoRotate: boolean;
}>();

const emit = defineEmits<{
  'toggle-layer': [key: keyof NationalMapLayerState];
  'update:autoRotate': [value: boolean];
  reset: [];
}>();

const layerOptions: Array<{ key: keyof NationalMapLayerState; label: string }> = [
  { key: 'provinces', label: '省级面' },
  { key: 'boundaries', label: '全国轮廓' },
  { key: 'flyLines', label: '协同飞线' },
  { key: 'cityNodes', label: '城市节点' },
  { key: 'scanLight', label: '动态扫光' }
];
</script>

<template>
  <div class="border border-white/10 bg-slate-950/82 p-3 shadow-xl backdrop-blur-md">
    <div class="flex items-center justify-between gap-3">
      <div class="flex items-center gap-2 text-xs font-semibold text-white">
        <View class="h-4 w-4 text-cyan-200" aria-hidden="true" />
        图层控制
      </div>
      <button class="icon-button focus-ring h-8 w-8" type="button" title="恢复全国视角" aria-label="恢复全国视角" @click="emit('reset')">
        <RotateCcw class="h-3.5 w-3.5" />
      </button>
    </div>
    <label class="mt-3 flex cursor-pointer items-center justify-between gap-3 text-xs text-slate-300">
      <span>自动旋转</span>
      <input
        class="h-4 w-4 accent-cyan-500"
        type="checkbox"
        :checked="props.autoRotate"
        @change="emit('update:autoRotate', ($event.target as HTMLInputElement).checked)"
      />
    </label>
    <div class="mt-2 grid grid-cols-2 gap-x-3 gap-y-2">
      <label v-for="item in layerOptions" :key="item.key" class="flex cursor-pointer items-center gap-2 text-[11px] text-slate-300">
        <input class="h-3.5 w-3.5 accent-cyan-500" type="checkbox" :checked="props.layers[item.key]" @change="emit('toggle-layer', item.key)" />
        {{ item.label }}
      </label>
    </div>
  </div>
</template>
