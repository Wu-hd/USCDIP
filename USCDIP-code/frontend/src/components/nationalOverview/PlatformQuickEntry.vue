<script setup lang="ts">
import {
  BellRing,
  BrainCircuit,
  Building2,
  ChartNoAxesCombined,
  ClipboardList,
  Command,
  Cpu,
  LockKeyhole,
  MapPinned,
  RadioTower
} from 'lucide-vue-next';
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import {
  checkPermissions,
  checkPlatformRoute,
  PLATFORM_PERMISSION_REQUIREMENTS
} from '@/services/permissions';
import type { AuthMePayload, ManagedPlatformCode, PlatformBoundary } from '@/types/api';

const props = defineProps<{
  user: AuthMePayload | null;
  platforms: PlatformBoundary[];
}>();

const router = useRouter();

const platformDefinitions: Array<{
  code: ManagedPlatformCode;
  fallbackName: string;
  route: string;
  icon: typeof Building2;
}> = [
  { code: 'MGMT', fallbackName: '综合管理平台', route: '/mgmt', icon: Building2 },
  { code: 'EMGC', fallbackName: '应急指挥平台', route: '/emgc', icon: Command },
  { code: 'DIAG', fallbackName: '智能诊断中枢', route: '/diag', icon: BrainCircuit }
];

const moduleDefinitions = [
  { key: 'gis', label: '二维一张图', route: '/mgmt/gis', permissions: ['ENTRY:MGMT', 'MENU:ASSET:READ'], icon: MapPinned },
  { key: 'devices', label: '设备台账', route: '/mgmt/devices', permissions: ['ENTRY:MGMT', 'MENU:ASSET:READ'], icon: RadioTower },
  { key: 'alerts', label: '实时告警', route: '/mgmt/alerts', permissions: ['ENTRY:MGMT', 'MENU:ASSET:READ'], icon: BellRing },
  { key: 'trends', label: '时序趋势', route: '/mgmt/trends', permissions: ['ENTRY:MGMT', 'MENU:ASSET:READ'], icon: ChartNoAxesCombined },
  { key: 'orders', label: '应急工单', route: '/emgc/workorders', permissions: ['ENTRY:EMGC', 'MENU:WORKORDER:READ'], icon: ClipboardList },
  { key: 'models', label: '模型治理', route: '/diag/models', permissions: ['ENTRY:DIAG', 'MENU:MODEL:READ'], icon: Cpu }
];

const platformEntries = computed(() => platformDefinitions.map((definition) => {
  const boundary = props.platforms.find((item) => item.platformCode === definition.code);
  const check = checkPlatformRoute(props.user, definition.code);
  return {
    ...definition,
    label: boundary?.platformName || definition.fallbackName,
    description: boundary?.description || PLATFORM_PERMISSION_REQUIREMENTS[definition.code].actionLabel,
    route: boundary?.routePrefix || definition.route,
    check
  };
}));

const moduleEntries = computed(() => moduleDefinitions.map((definition) => ({
  ...definition,
  check: checkPermissions(props.user, definition.permissions, definition.label)
})));

function navigate(route: string, allowed: boolean) {
  if (allowed) void router.push(route);
}
</script>

<template>
  <section aria-labelledby="platform-entry-title">
    <div class="flex items-center justify-between gap-3">
      <h2 id="platform-entry-title" class="text-sm font-semibold text-white">平台快捷入口</h2>
      <span class="text-[10px] text-slate-500">按当前用户权限</span>
    </div>

    <div class="mt-3 space-y-2">
      <button
        v-for="entry in platformEntries"
        :key="entry.code"
        class="focus-ring flex w-full items-start gap-3 border px-3 py-3 text-left transition-colors duration-200"
        :class="entry.check.allowed ? 'cursor-pointer border-white/10 bg-white/[0.045] hover:border-cyan-300/30 hover:bg-cyan-400/10' : 'cursor-not-allowed border-amber-400/15 bg-amber-400/[0.045] opacity-80'"
        type="button"
        :disabled="!entry.check.allowed"
        :title="entry.check.reason"
        @click="navigate(entry.route, entry.check.allowed)"
      >
        <component :is="entry.icon" class="mt-0.5 h-4 w-4 shrink-0" :class="entry.check.allowed ? 'text-cyan-200' : 'text-amber-200'" aria-hidden="true" />
        <span class="min-w-0 flex-1">
          <span class="block truncate text-xs font-semibold text-white">{{ entry.label }}</span>
          <span class="mt-1 block text-[10px] leading-4 text-slate-400">{{ entry.check.allowed ? entry.description : entry.check.reason }}</span>
        </span>
        <LockKeyhole v-if="!entry.check.allowed" class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-200" aria-hidden="true" />
      </button>
    </div>

    <h3 class="mt-5 text-xs font-semibold text-slate-300">业务模块</h3>
    <div class="mt-2 grid grid-cols-2 gap-2">
      <button
        v-for="entry in moduleEntries"
        :key="entry.key"
        class="focus-ring flex min-h-16 flex-col items-start justify-between border p-2.5 text-left text-[11px] transition-colors duration-200"
        :class="entry.check.allowed ? 'cursor-pointer border-white/10 bg-white/[0.035] text-slate-200 hover:border-cyan-300/30 hover:bg-cyan-400/10' : 'cursor-not-allowed border-white/5 bg-white/[0.02] text-slate-500'"
        type="button"
        :disabled="!entry.check.allowed"
        :title="entry.check.reason"
        @click="navigate(entry.route, entry.check.allowed)"
      >
        <component :is="entry.icon" class="h-4 w-4" :class="entry.check.allowed ? 'text-cyan-200' : 'text-slate-600'" aria-hidden="true" />
        <span>{{ entry.label }}</span>
      </button>
    </div>
  </section>
</template>

