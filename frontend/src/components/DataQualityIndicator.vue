<template>
  <article :class="rootClass" :aria-label="ariaLabel">
    <div class="flex min-w-0 items-center gap-2">
      <span :class="levelIconClass">
        <component :is="levelMeta.icon" class="h-3.5 w-3.5" aria-hidden="true" />
      </span>
      <div class="min-w-0">
        <div class="flex flex-wrap items-center gap-2">
          <span :class="levelBadgeClass" :title="levelMeta.description">
            {{ normalizedLevel }} / {{ scoreLabel }}
          </span>
          <span
            v-if="hasBackfill"
            class="inline-flex items-center gap-1 rounded-md border border-amber-400/25 bg-amber-400/10 px-2 py-1 text-[11px] font-semibold text-amber-100"
            title="边缘断网或延迟回传后的补偿数据，已进入数据质量评分。"
          >
            <RefreshCcw class="h-3 w-3" aria-hidden="true" />
            补偿
          </span>
        </div>
        <p v-if="mode !== 'table'" class="mt-1 truncate text-xs text-slate-400">
          {{ levelMeta.description }}
        </p>
      </div>
    </div>

    <div v-if="mode === 'detail'" class="mt-4 space-y-3">
      <div>
        <div class="mb-1 flex items-center justify-between text-xs">
          <span class="text-slate-400">DQ Score</span>
          <span class="font-mono text-slate-100">{{ scoreLabel }}</span>
        </div>
        <div class="h-2 overflow-hidden rounded-full bg-white/10">
          <div class="h-full rounded-full transition-all duration-300" :class="levelMeta.barClass" :style="{ width: scoreWidth }"></div>
        </div>
      </div>

      <div v-if="typeof alarmFactor === 'number'" class="rounded-lg border border-cyan-400/20 bg-cyan-400/10 px-3 py-2 text-xs text-cyan-100">
        告警置信度系数
        <span class="ml-2 font-mono font-semibold">x{{ alarmFactor.toFixed(2) }}</span>
      </div>

      <dl v-if="dimensionItems.length" class="grid gap-2">
        <div v-for="item in dimensionItems" :key="item.key">
          <div class="mb-1 flex items-center justify-between gap-3 text-xs">
            <dt class="text-slate-400">{{ item.label }}</dt>
            <dd class="font-mono text-slate-100">{{ item.labelValue }}</dd>
          </div>
          <div class="h-1.5 overflow-hidden rounded-full bg-white/10">
            <div class="h-full rounded-full bg-blue-300" :style="{ width: item.width }"></div>
          </div>
        </div>
      </dl>
    </div>

    <div v-if="visibleFlags.length" :class="flagWrapClass">
      <span
        v-for="flag in visibleFlags"
        :key="flag.raw"
        :class="flag.className"
        :title="flag.description"
      >
        <component :is="flag.icon" class="h-3 w-3 shrink-0" aria-hidden="true" />
        <span class="truncate">{{ mode === 'table' ? flag.shortLabel : flag.label }}</span>
      </span>
      <span v-if="hiddenFlagCount > 0" class="rounded-md border border-white/10 bg-white/[0.045] px-2 py-1 text-[11px] text-slate-300">
        +{{ hiddenFlagCount }}
      </span>
    </div>
    <p v-else-if="mode === 'detail'" class="mt-3 text-xs text-slate-400">暂无异常标志</p>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Gauge,
  HelpCircle,
  RefreshCcw,
  ShieldAlert
} from 'lucide-vue-next';

type IndicatorMode = 'compact' | 'table' | 'detail';

interface DimensionInput {
  key: string;
  label: string;
  value: number | null | undefined;
}

const props = withDefaults(
  defineProps<{
    mode?: IndicatorMode;
    score?: number | null;
    level?: string | null;
    flags?: string | string[] | null;
    isBackfill?: boolean;
    alarmFactor?: number | null;
    dimensions?: DimensionInput[];
  }>(),
  {
    mode: 'compact',
    score: null,
    level: null,
    flags: null,
    isBackfill: false,
    alarmFactor: null,
    dimensions: () => []
  }
);

const FLAG_META: Record<string, {
  label: string;
  shortLabel: string;
  description: string;
  className: string;
  icon: unknown;
}> = {
  VALIDITY_RANGE_VIOLATION: {
    label: '量程越界',
    shortLabel: '量程',
    description: '采样值超出当前指标画像的可信量程，需要人工复核或设备校验。',
    className: 'border-rose-400/25 bg-rose-400/10 text-rose-100',
    icon: AlertTriangle
  },
  VALIDITY_PROFILE_MISSING: {
    label: '画像缺失',
    shortLabel: '画像',
    description: '指标未匹配到一期质量画像，后端已按保守策略降权。',
    className: 'border-violet-400/25 bg-violet-400/10 text-violet-100',
    icon: HelpCircle
  },
  TIMELINESS_DELAYED: {
    label: '时效延迟',
    shortLabel: '延迟',
    description: 'eventTime、deviceTime 或 recvTime 存在明显延迟，影响实时告警可信度。',
    className: 'border-amber-400/25 bg-amber-400/10 text-amber-100',
    icon: Clock3
  },
  BACKFILL_DATA: {
    label: '补偿回传',
    shortLabel: '补偿',
    description: '边缘断网或延迟回传后的补偿数据，已进入数据质量评分。',
    className: 'border-amber-400/25 bg-amber-400/10 text-amber-100',
    icon: RefreshCcw
  }
};

const LEVEL_META = {
  A: {
    description: '高可信，可直接进入规则判定、模型推理和自动处置链路。',
    badgeClass: 'border-emerald-400/25 bg-emerald-400/10 text-emerald-100',
    iconClass: 'bg-emerald-400/15 text-emerald-200',
    barClass: 'bg-emerald-300',
    icon: CheckCircle2
  },
  B: {
    description: '可用但需保留降权标记，重要事件仍可自动进入处置链路。',
    badgeClass: 'border-blue-400/25 bg-blue-400/10 text-blue-100',
    iconClass: 'bg-blue-400/15 text-blue-200',
    barClass: 'bg-blue-300',
    icon: Gauge
  },
  C: {
    description: '数据质量降级，优先进入人工复核或多源交叉校验。',
    badgeClass: 'border-amber-400/25 bg-amber-400/10 text-amber-100',
    iconClass: 'bg-amber-400/15 text-amber-200',
    barClass: 'bg-amber-300',
    icon: AlertTriangle
  },
  D: {
    description: '数据不可靠，默认不直接触发正式事件。',
    badgeClass: 'border-rose-400/25 bg-rose-400/10 text-rose-100',
    iconClass: 'bg-rose-400/15 text-rose-200',
    barClass: 'bg-rose-300',
    icon: ShieldAlert
  },
  UNKNOWN: {
    description: '后端未返回数据质量等级或分数。',
    badgeClass: 'border-white/10 bg-white/[0.045] text-slate-200',
    iconClass: 'bg-white/[0.065] text-slate-300',
    barClass: 'bg-slate-300',
    icon: HelpCircle
  }
};

const mode = computed(() => props.mode);
const rawFlags = computed(() => {
  const values = Array.isArray(props.flags)
    ? props.flags
    : typeof props.flags === 'string'
      ? props.flags.split(',')
      : [];
  const normalized = values.map((flag) => flag.trim().toUpperCase()).filter(Boolean);
  if (props.isBackfill && !normalized.includes('BACKFILL_DATA')) {
    normalized.push('BACKFILL_DATA');
  }
  return Array.from(new Set(normalized));
});

const normalizedLevel = computed(() => {
  const explicit = props.level?.trim().toUpperCase();
  if (explicit && ['A', 'B', 'C', 'D'].includes(explicit)) {
    return explicit;
  }
  if (typeof props.score !== 'number' || Number.isNaN(props.score)) {
    return 'UNKNOWN';
  }
  if (props.score >= 85) return 'A';
  if (props.score >= 70) return 'B';
  if (props.score >= 60) return 'C';
  return 'D';
});

const levelMeta = computed(() => LEVEL_META[normalizedLevel.value as keyof typeof LEVEL_META] ?? LEVEL_META.UNKNOWN);
const scoreLabel = computed(() => (typeof props.score === 'number' && Number.isFinite(props.score) ? Math.round(props.score).toString() : '-'));
const scoreWidth = computed(() => {
  const value = typeof props.score === 'number' && Number.isFinite(props.score) ? Math.max(0, Math.min(100, props.score)) : 0;
  return `${value}%`;
});
const hasBackfill = computed(() => rawFlags.value.includes('BACKFILL_DATA'));
const flagItems = computed(() =>
  rawFlags.value.map((raw) => {
    const meta = FLAG_META[raw] ?? {
      label: raw,
      shortLabel: raw,
      description: `后端返回的扩展数据质量标志：${raw}`,
      className: 'border-white/10 bg-white/[0.045] text-slate-200',
      icon: HelpCircle
    };
    return { raw, ...meta };
  })
);
const flagLimit = computed(() => (mode.value === 'compact' ? 2 : Number.POSITIVE_INFINITY));
const visibleFlags = computed(() => flagItems.value.slice(0, flagLimit.value));
const hiddenFlagCount = computed(() => Math.max(flagItems.value.length - visibleFlags.value.length, 0));
const dimensionItems = computed(() =>
  props.dimensions
    .filter((item) => typeof item.value === 'number' && Number.isFinite(item.value))
    .map((item) => {
      const value = Math.max(0, Math.min(1, Number(item.value)));
      return {
        ...item,
        labelValue: value.toFixed(2),
        width: `${Math.round(value * 100)}%`
      };
    })
);
const ariaLabel = computed(() => `数据质量 ${normalizedLevel.value} 级，分数 ${scoreLabel.value}`);
const rootClass = computed(() => {
  const base = 'min-w-0 rounded-lg';
  if (mode.value === 'detail') {
    return `${base} border border-white/10 bg-white/[0.045] p-4`;
  }
  if (mode.value === 'table') {
    return `${base} space-y-2`;
  }
  return `${base} flex flex-col gap-2`;
});
const levelBadgeClass = computed(() => `inline-flex items-center rounded-md border px-2 py-1 font-mono text-xs font-semibold ${levelMeta.value.badgeClass}`);
const levelIconClass = computed(() => `inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${levelMeta.value.iconClass}`);
const flagWrapClass = computed(() => {
  const base = 'flex min-w-0 flex-wrap gap-1.5';
  return mode.value === 'table' ? `${base} max-w-full` : `${base} mt-3`;
});
</script>
