<script setup lang="ts">
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  DatabaseZap,
  Filter,
  Gauge,
  Loader2,
  RefreshCcw,
  Search,
  ShieldAlert,
  SlidersHorizontal,
  X
} from 'lucide-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import DataQualityIndicator from '@/components/DataQualityIndicator.vue';
import { ApiClientError } from '@/services/api';
import { getDataQualityScores, type DataQualityScoreQuery } from '@/services/dataQuality';
import type { DataQualityScoreResponse, TrendMetricPoint } from '@/types/api';

type LoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

interface ChartPoint extends TrendMetricPoint {
  x: number;
  metricY: number;
  dqY: number | null;
}

const route = useRoute();
const router = useRouter();

const pageSize = 100;
const chartWidth = 960;
const chartHeight = 360;
const chartPadding = { top: 32, right: 64, bottom: 54, left: 64 };

const metricOptions = ['PRESSURE', 'TEMPERATURE', 'VIBRATION', '40001'];
const dqLevelOptions = [
  { value: '', label: '全部质量' },
  { value: 'A', label: 'A 优' },
  { value: 'B', label: 'B 良' },
  { value: 'C', label: 'C 需关注' },
  { value: 'D', label: 'D 风险' }
];
const backfillOptions = [
  { value: '', label: '在线 + 补偿' },
  { value: 'false', label: '仅在线数据' },
  { value: 'true', label: '仅补偿数据' }
];

const state = ref<LoadState>('idle');
const records = ref<DataQualityScoreResponse[]>([]);
const message = ref('等待查询时序趋势');
const traceId = ref('');
const page = ref(1);
const total = ref(0);
const totalPages = ref(0);
const hasNext = ref(false);
const activePointId = ref('');

const filters = reactive({
  deviceId: String(route.query.deviceId ?? 'DEV-001'),
  metricCode: String(route.query.metricCode ?? 'PRESSURE'),
  dqLevel: String(route.query.dqLevel ?? ''),
  isBackfill: String(route.query.isBackfill ?? ''),
  sourceBatchId: String(route.query.sourceBatchId ?? ''),
  startTime: String(route.query.startTime ?? defaultStartTime()),
  endTime: String(route.query.endTime ?? defaultEndTime())
});

const query = computed<DataQualityScoreQuery>(() => ({
  deviceId: filters.deviceId.trim(),
  metricCode: filters.metricCode.trim().toUpperCase(),
  dqLevel: filters.dqLevel,
  sourceBatchId: filters.sourceBatchId.trim(),
  isBackfill:
    filters.isBackfill === ''
      ? undefined
      : filters.isBackfill === 'true',
  startTime: filters.startTime,
  endTime: filters.endTime
}));

const points = computed<TrendMetricPoint[]>(() =>
  records.value
    .map((record) => {
      const metricValue = Number(record.metricValue);
      const timestamp = record.eventTime ? new Date(record.eventTime).getTime() : Number.NaN;
      if (!Number.isFinite(metricValue) || Number.isNaN(timestamp) || !record.eventTime) {
        return null;
      }
      return {
        sourceRecordId: record.sourceRecordId,
        eventTime: record.eventTime,
        timestamp,
        metricValue,
        dqScore: record.dqScore,
        dqLevel: record.dqLevel,
        dqFlags: splitFlags(record.dqFlags),
        isBackfill: record.isBackfill,
        raw: record
      };
    })
    .filter((point): point is TrendMetricPoint => Boolean(point))
    .sort((left, right) => left.timestamp - right.timestamp)
);

const chart = computed(() => {
  const innerWidth = chartWidth - chartPadding.left - chartPadding.right;
  const innerHeight = chartHeight - chartPadding.top - chartPadding.bottom;
  const source = points.value;
  const timestamps = source.map((point) => point.timestamp);
  const values = source.map((point) => point.metricValue);
  const minTime = timestamps.length ? Math.min(...timestamps) : 0;
  const maxTime = timestamps.length ? Math.max(...timestamps) : 1;
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;
  const span = rawMax - rawMin || Math.max(Math.abs(rawMax), 1);
  const minValue = rawMin - span * 0.12;
  const maxValue = rawMax + span * 0.12;

  const xFor = (timestamp: number) => {
    if (minTime === maxTime) {
      return chartPadding.left + innerWidth / 2;
    }
    return chartPadding.left + ((timestamp - minTime) / (maxTime - minTime)) * innerWidth;
  };
  const metricYFor = (value: number) =>
    chartPadding.top + (1 - (value - minValue) / (maxValue - minValue || 1)) * innerHeight;
  const dqYFor = (value: number) =>
    chartPadding.top + (1 - Math.max(0, Math.min(100, value)) / 100) * innerHeight;

  const chartPoints: ChartPoint[] = source.map((point) => ({
    ...point,
    x: xFor(point.timestamp),
    metricY: metricYFor(point.metricValue),
    dqY: typeof point.dqScore === 'number' ? dqYFor(point.dqScore) : null
  }));

  return {
    chartPoints,
    metricPolyline: chartPoints.map((point) => `${point.x},${point.metricY}`).join(' '),
    dqPolyline: chartPoints
      .filter((point) => point.dqY !== null)
      .map((point) => `${point.x},${point.dqY}`)
      .join(' '),
    minValue,
    maxValue,
    innerWidth,
    innerHeight,
    plotX: chartPadding.left,
    plotY: chartPadding.top,
    plotBottom: chartPadding.top + innerHeight,
    plotRight: chartPadding.left + innerWidth
  };
});

const activePoint = computed(() =>
  points.value.find((point) => point.sourceRecordId === activePointId.value)
    ?? points.value[points.value.length - 1]
    ?? null
);

const stats = computed(() => {
  const source = points.value;
  const dqScores = source
    .map((point) => point.dqScore)
    .filter((score): score is number => typeof score === 'number' && Number.isFinite(score));
  const values = source.map((point) => point.metricValue);
  const backfillCount = source.filter((point) => point.isBackfill).length;
  const riskCount = source.filter((point) => point.dqLevel === 'D' || (point.dqScore ?? 100) < 70).length;
  return {
    samples: source.length,
    backfillCount,
    onlineCount: source.length - backfillCount,
    riskCount,
    avgDq: dqScores.length ? Math.round(dqScores.reduce((sum, score) => sum + score, 0) / dqScores.length) : null,
    minValue: values.length ? Math.min(...values) : null,
    maxValue: values.length ? Math.max(...values) : null
  };
});

const metricCards = computed(() => [
  {
    key: 'samples',
    label: '当前页样本',
    value: String(stats.value.samples),
    hint: `后端 total ${total.value}`,
    icon: DatabaseZap,
    tone: 'blue'
  },
  {
    key: 'dq',
    label: '平均 DQ',
    value: stats.value.avgDq === null ? '-' : `${stats.value.avgDq}`,
    hint: '0-100 质量评分',
    icon: Gauge,
    tone: dqTone(stats.value.avgDq)
  },
  {
    key: 'backfill',
    label: '补偿样本',
    value: String(stats.value.backfillCount),
    hint: 'isBackfill=true',
    icon: RefreshCcw,
    tone: 'amber'
  },
  {
    key: 'risk',
    label: '低质量点',
    value: String(stats.value.riskCount),
    hint: 'DQ D 或 < 70',
    icon: AlertTriangle,
    tone: stats.value.riskCount ? 'rose' : 'emerald'
  },
  {
    key: 'range',
    label: '指标区间',
    value: stats.value.minValue === null ? '-' : `${formatNumber(stats.value.minValue)} ~ ${formatNumber(stats.value.maxValue)}`,
    hint: filters.metricCode || 'metricCode',
    icon: Activity,
    tone: 'cyan'
  }
]);

const canGoPrev = computed(() => page.value > 1 && state.value !== 'loading');
const canGoNext = computed(() => hasNext.value && state.value !== 'loading');
const isPaged = computed(() => total.value > pageSize);

onMounted(() => {
  void loadTrend(true);
});

async function loadTrend(resetPage = false) {
  if (resetPage) {
    page.value = 1;
  }
  state.value = 'loading';
  message.value = '正在查询时序趋势';
  traceId.value = '';
  activePointId.value = '';
  try {
    const response = await getDataQualityScores(page.value, pageSize, query.value);
    const pageData = response.data;
    records.value = pageData?.items ?? [];
    total.value = pageData?.total ?? 0;
    totalPages.value = pageData?.totalPages ?? 0;
    hasNext.value = Boolean(pageData?.hasNext);
    traceId.value = response.traceId;
    state.value = records.value.length ? 'ready' : 'empty';
    message.value = records.value.length ? '趋势数据已同步' : '当前筛选无时序样本';
    await syncQuery();
  } catch (error) {
    records.value = [];
    total.value = 0;
    totalPages.value = 0;
    hasNext.value = false;
    state.value = 'error';
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '时序趋势读取失败';
  }
}

async function syncQuery() {
  await router.replace({
    query: {
      ...route.query,
      deviceId: filters.deviceId.trim() || undefined,
      metricCode: filters.metricCode.trim().toUpperCase() || undefined,
      dqLevel: filters.dqLevel || undefined,
      isBackfill: filters.isBackfill || undefined,
      sourceBatchId: filters.sourceBatchId.trim() || undefined,
      startTime: filters.startTime || undefined,
      endTime: filters.endTime || undefined
    }
  });
}

function applyFilters() {
  filters.metricCode = filters.metricCode.trim().toUpperCase();
  void loadTrend(true);
}

function resetFilters() {
  filters.deviceId = 'DEV-001';
  filters.metricCode = 'PRESSURE';
  filters.dqLevel = '';
  filters.isBackfill = '';
  filters.sourceBatchId = '';
  filters.startTime = defaultStartTime();
  filters.endTime = defaultEndTime();
  void loadTrend(true);
}

function setRange(days: number) {
  const end = new Date();
  const start = new Date(end);
  start.setDate(end.getDate() - days);
  filters.startTime = toDatetimeLocal(start);
  filters.endTime = toDatetimeLocal(end);
  void loadTrend(true);
}

function goPrev() {
  if (!canGoPrev.value) {
    return;
  }
  page.value -= 1;
  void loadTrend();
}

function goNext() {
  if (!canGoNext.value) {
    return;
  }
  page.value += 1;
  void loadTrend();
}

function setActivePoint(point: TrendMetricPoint) {
  activePointId.value = point.sourceRecordId;
}

function clearActivePoint() {
  activePointId.value = '';
}

function defaultEndTime() {
  return toDatetimeLocal(new Date());
}

function defaultStartTime() {
  const date = new Date();
  date.setDate(date.getDate() - 7);
  return toDatetimeLocal(date);
}

function toDatetimeLocal(date: Date) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return offsetDate.toISOString().slice(0, 19);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatNumber(value: number | null | undefined) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-';
  }
  return Math.abs(value) >= 100 ? value.toFixed(0) : value.toFixed(2);
}

function splitFlags(value: string | null | undefined) {
  if (!value) {
    return [];
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function metricToneClass(tone: string) {
  const tones: Record<string, string> = {
    blue: 'border-blue-400/20 bg-blue-400/10 text-blue-100',
    emerald: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-100',
    amber: 'border-amber-400/20 bg-amber-400/10 text-amber-100',
    rose: 'border-rose-400/20 bg-rose-400/10 text-rose-100',
    cyan: 'border-cyan-400/20 bg-cyan-400/10 text-cyan-100'
  };
  return tones[tone] ?? tones.blue;
}

function dqTone(score: number | null) {
  if (score === null) {
    return 'blue';
  }
  if (score >= 85) {
    return 'emerald';
  }
  if (score >= 70) {
    return 'amber';
  }
  return 'rose';
}

function qualityDimensions(record: DataQualityScoreResponse | null | undefined) {
  return [
    { key: 'completeness', label: '完整性', value: record?.dqCompleteness },
    { key: 'validity', label: '有效性', value: record?.dqValidity },
    { key: 'timeliness', label: '时效性', value: record?.dqTimeliness },
    { key: 'consistency', label: '一致性', value: record?.dqConsistency },
    { key: 'stability', label: '稳定性', value: record?.dqStability }
  ];
}

function diamondPoints(x: number, y: number, size: number) {
  return `${x},${y - size} ${x + size},${y} ${x},${y + size} ${x - size},${y}`;
}
</script>

<template>
  <main class="portal-shell">
    <div class="app-frame app-viewport flex flex-col gap-5 px-4 py-6 sm:px-6 lg:px-8">
      <header class="flex flex-col gap-4 rounded-lg border border-white/10 bg-white/[0.045] p-4 backdrop-blur-xl lg:flex-row lg:items-center lg:justify-between">
        <div class="min-w-0">
          <RouterLink class="secondary-button focus-ring mb-4" to="/mgmt">
            <ArrowLeft class="h-4 w-4" />
            返回管理平台
          </RouterLink>
          <div class="flex items-center gap-3">
            <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg border border-blue-400/20 bg-blue-400/10 text-blue-100">
              <Activity class="h-6 w-6" aria-hidden="true" />
            </div>
            <div class="min-w-0">
              <p class="text-xs font-semibold uppercase tracking-[0.18em] text-blue-200">
                MGMT / TIME SERIES
              </p>
              <h1 class="mt-1 font-display text-2xl font-semibold text-white sm:text-3xl">
                时序趋势图
              </h1>
            </div>
          </div>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
            按设备和指标查看采样趋势，补偿数据以琥珀标记区分，DQ 分数和质量标识随点位联动展示。
          </p>
        </div>

        <div class="grid min-w-full grid-cols-2 gap-3 sm:min-w-[380px]">
          <div class="rounded-lg border border-white/10 bg-black/20 p-3">
            <p class="text-xs text-slate-400">Device / Metric</p>
            <p class="mt-1 truncate font-mono text-sm font-semibold text-white">{{ filters.deviceId || '-' }} / {{ filters.metricCode || '-' }}</p>
          </div>
          <div class="rounded-lg border border-white/10 bg-black/20 p-3">
            <p class="text-xs text-slate-400">TraceId</p>
            <p class="mt-1 truncate font-mono text-xs text-blue-100">{{ traceId || '-' }}</p>
          </div>
        </div>
      </header>

      <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-5" aria-label="趋势指标">
        <article
          v-for="card in metricCards"
          :key="card.key"
          class="rounded-lg border bg-white/[0.045] p-4 backdrop-blur-xl"
          :class="metricToneClass(card.tone)"
        >
          <div class="flex items-center justify-between gap-3">
            <p class="text-xs font-medium text-slate-300">{{ card.label }}</p>
            <component :is="card.icon" class="h-4 w-4" aria-hidden="true" />
          </div>
          <p class="mt-3 truncate font-mono text-2xl font-semibold text-white">{{ card.value }}</p>
          <p class="mt-1 truncate text-xs text-slate-400">{{ card.hint }}</p>
        </article>
      </section>

      <section class="glass-panel p-4">
        <form class="grid gap-3 lg:grid-cols-[1fr_0.9fr_0.8fr_0.9fr] xl:grid-cols-[1fr_0.9fr_0.75fr_0.85fr_1fr_1fr_auto]" @submit.prevent="applyFilters">
          <label>
            <span class="mb-1 flex items-center gap-2 text-xs font-medium text-slate-300">
              <Search class="h-3.5 w-3.5 text-blue-200" aria-hidden="true" />
              设备
            </span>
            <input
              v-model="filters.deviceId"
              class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white placeholder:text-slate-500"
              placeholder="DEV-001"
            />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">指标</span>
            <input
              v-model="filters.metricCode"
              class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm uppercase text-white placeholder:text-slate-500"
              list="metric-options"
              placeholder="PRESSURE"
            />
            <datalist id="metric-options">
              <option v-for="metric in metricOptions" :key="metric" :value="metric" />
            </datalist>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">DQ</span>
            <select v-model="filters.dqLevel" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white">
              <option v-for="option in dqLevelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">数据来源</span>
            <select v-model="filters.isBackfill" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white">
              <option v-for="option in backfillOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">开始时间</span>
            <input v-model="filters.startTime" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white" type="datetime-local" step="1" />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">结束时间</span>
            <input v-model="filters.endTime" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white" type="datetime-local" step="1" />
          </label>

          <div class="flex items-end gap-2">
            <button class="primary-button focus-ring h-10" type="submit" :disabled="state === 'loading'">
              <Filter class="h-4 w-4" />
              查询
            </button>
            <button class="secondary-button focus-ring h-10 px-3" type="button" @click="resetFilters">
              <X class="h-4 w-4" />
            </button>
          </div>
        </form>

        <div class="mt-3 flex flex-wrap gap-2">
          <button class="secondary-button focus-ring h-9 px-3" type="button" @click="setRange(1)">
            最近 24 小时
          </button>
          <button class="secondary-button focus-ring h-9 px-3" type="button" @click="setRange(7)">
            最近 7 天
          </button>
          <button class="secondary-button focus-ring h-9 px-3" type="button" @click="setRange(30)">
            最近 30 天
          </button>
        </div>
      </section>

      <section class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div class="glass-panel min-w-0 overflow-hidden">
          <div class="flex flex-col gap-3 border-b border-white/10 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-white">
                <SlidersHorizontal class="h-4 w-4 text-blue-200" aria-hidden="true" />
                趋势曲线
              </div>
              <p class="mt-1 text-xs text-slate-400">
                {{ message }}，当前页 {{ records.length }} / {{ total }} 条
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-2 text-xs text-slate-400">
              <span class="inline-flex items-center gap-1 rounded-md border border-blue-400/20 bg-blue-400/10 px-2 py-1 text-blue-100">
                <span class="h-0.5 w-5 rounded-full bg-blue-300"></span>
                指标值
              </span>
              <span class="inline-flex items-center gap-1 rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2 py-1 text-cyan-100">
                <span class="h-0.5 w-5 border-t border-dashed border-cyan-200"></span>
                DQ 分数
              </span>
              <span class="inline-flex items-center gap-1 rounded-md border border-amber-400/20 bg-amber-400/10 px-2 py-1 text-amber-100">
                <span class="h-2.5 w-2.5 rotate-45 bg-amber-300"></span>
                补偿
              </span>
            </div>
          </div>

          <div v-if="state === 'loading'" class="flex min-h-[460px] flex-col items-center justify-center text-sm text-slate-300">
            <Loader2 class="mb-3 h-7 w-7 animate-spin text-blue-200" aria-hidden="true" />
            正在加载趋势数据
          </div>

          <div v-else-if="state === 'error'" class="m-4 rounded-lg border border-rose-400/20 bg-rose-400/10 p-5 text-sm text-rose-50">
            <div class="flex gap-3">
              <ShieldAlert class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div class="min-w-0">
                <p class="font-semibold">趋势查询失败</p>
                <p class="mt-1 text-rose-100">{{ message }}</p>
                <p v-if="traceId" class="mt-2 truncate font-mono text-xs text-rose-100">TraceId {{ traceId }}</p>
              </div>
            </div>
          </div>

          <div v-else-if="state === 'empty' || !points.length" class="flex min-h-[460px] flex-col items-center justify-center p-6 text-center text-sm text-slate-400">
            <Search class="mb-3 h-8 w-8 text-blue-200" aria-hidden="true" />
            <p class="font-semibold text-white">没有可绘制的时序样本</p>
            <p class="mt-2 max-w-sm leading-6">检查设备、指标、时间范围、DQ 等级或补偿筛选后重新查询。</p>
          </div>

          <div v-else class="p-4">
            <div v-if="isPaged" class="mb-3 rounded-lg border border-amber-400/20 bg-amber-400/10 p-3 text-xs leading-5 text-amber-50">
              当前图表只绘制第 {{ page }} 页 {{ records.length }} 条样本；后端 total 为 {{ total }}，结果可能分页截断。
            </div>

            <div class="overflow-x-auto">
              <svg
                class="min-w-[820px] rounded-lg border border-white/10 bg-black/20"
                :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
                role="img"
                aria-label="设备指标时序趋势图"
              >
                <defs>
                  <linearGradient id="metric-fill" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stop-color="#3B82F6" stop-opacity="0.22" />
                    <stop offset="100%" stop-color="#3B82F6" stop-opacity="0" />
                  </linearGradient>
                </defs>

                <rect :x="chart.plotX" :y="chart.plotY" :width="chart.innerWidth" :height="chart.innerHeight" fill="rgba(15, 23, 42, 0.58)" />
                <g stroke="rgba(148, 163, 184, 0.16)" stroke-width="1">
                  <line v-for="index in 6" :key="`h-${index}`" :x1="chart.plotX" :x2="chart.plotRight" :y1="chart.plotY + (index - 1) * chart.innerHeight / 5" :y2="chart.plotY + (index - 1) * chart.innerHeight / 5" />
                  <line v-for="index in 7" :key="`v-${index}`" :x1="chart.plotX + (index - 1) * chart.innerWidth / 6" :x2="chart.plotX + (index - 1) * chart.innerWidth / 6" :y1="chart.plotY" :y2="chart.plotBottom" />
                </g>

                <text x="18" y="32" fill="#BFDBFE" font-size="12" font-family="monospace">metric</text>
                <text :x="chartWidth - 48" y="32" fill="#A5F3FC" font-size="12" font-family="monospace">dq</text>
                <text x="16" :y="chart.plotY + 6" fill="#CBD5E1" font-size="11" font-family="monospace">{{ formatNumber(chart.maxValue) }}</text>
                <text x="16" :y="chart.plotBottom" fill="#CBD5E1" font-size="11" font-family="monospace">{{ formatNumber(chart.minValue) }}</text>
                <text :x="chartWidth - 36" :y="chart.plotY + 6" fill="#A5F3FC" font-size="11" font-family="monospace">100</text>
                <text :x="chartWidth - 28" :y="chart.plotBottom" fill="#A5F3FC" font-size="11" font-family="monospace">0</text>

                <polyline
                  v-if="chart.dqPolyline"
                  :points="chart.dqPolyline"
                  fill="none"
                  stroke="#67E8F9"
                  stroke-dasharray="6 8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  opacity="0.75"
                />
                <polyline
                  :points="chart.metricPolyline"
                  fill="none"
                  stroke="#3B82F6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="3"
                />

                <g v-for="point in chart.chartPoints" :key="point.sourceRecordId">
                  <line
                    v-if="activePoint?.sourceRecordId === point.sourceRecordId"
                    :x1="point.x"
                    :x2="point.x"
                    :y1="chart.plotY"
                    :y2="chart.plotBottom"
                    stroke="#FBBF24"
                    stroke-dasharray="4 5"
                    stroke-width="1"
                  />
                  <polygon
                    v-if="point.isBackfill"
                    :points="diamondPoints(point.x, point.metricY, 7)"
                    class="cursor-pointer"
                    :fill="point.dqLevel === 'D' ? '#FB7185' : '#FBBF24'"
                    stroke="#111827"
                    stroke-width="2"
                    tabindex="0"
                    @focus="setActivePoint(point)"
                    @mouseenter="setActivePoint(point)"
                  />
                  <circle
                    v-else
                    class="cursor-pointer"
                    :cx="point.x"
                    :cy="point.metricY"
                    :r="point.dqLevel === 'D' ? 6 : 5"
                    :fill="point.dqLevel === 'D' ? '#FB7185' : '#60A5FA'"
                    stroke="#111827"
                    stroke-width="2"
                    tabindex="0"
                    @focus="setActivePoint(point)"
                    @mouseenter="setActivePoint(point)"
                  />
                </g>
              </svg>
            </div>

            <div class="mt-4 flex flex-col gap-3 border-t border-white/10 pt-4 sm:flex-row sm:items-center sm:justify-between">
              <p class="text-xs text-slate-400">第 {{ page }} 页，pageSize {{ pageSize }}</p>
              <div class="flex items-center gap-2">
                <button class="secondary-button focus-ring h-9 px-3" type="button" :disabled="!canGoPrev" @click="goPrev">
                  <ChevronLeft class="h-4 w-4" />
                  上一页
                </button>
                <button class="secondary-button focus-ring h-9 px-3" type="button" :disabled="!canGoNext" @click="goNext">
                  下一页
                  <ChevronRight class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <aside class="glass-panel min-h-[320px] overflow-hidden xl:sticky xl:top-6 xl:h-[calc(100svh-3rem)]" aria-label="趋势点详情">
          <div class="border-b border-white/10 p-4">
            <div class="flex items-center gap-2 text-sm font-semibold text-white">
              <CalendarClock class="h-4 w-4 text-amber-200" aria-hidden="true" />
              点位上下文
            </div>
            <p class="mt-1 text-xs text-slate-400">悬停或聚焦图中点位查看质量细节。</p>
          </div>

          <div v-if="activePoint" class="space-y-4 p-4">
            <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate font-mono text-sm font-semibold text-white">{{ activePoint.sourceRecordId }}</p>
                  <p class="mt-1 text-xs text-slate-400">{{ formatDateTime(activePoint.eventTime) }}</p>
                </div>
                <DataQualityIndicator
                  class="shrink-0"
                  mode="compact"
                  :score="activePoint.dqScore"
                  :level="activePoint.dqLevel"
                  :flags="activePoint.dqFlags"
                  :is-backfill="activePoint.isBackfill"
                />
              </div>
              <dl class="mt-4 grid grid-cols-2 gap-3 text-xs">
                <div>
                  <dt class="text-slate-400">metricValue</dt>
                  <dd class="mt-1 font-mono text-lg font-semibold text-white">{{ formatNumber(activePoint.metricValue) }}</dd>
                </div>
                <div>
                  <dt class="text-slate-400">source</dt>
                  <dd class="mt-1 font-mono text-lg font-semibold text-white">{{ activePoint.isBackfill ? 'BACKFILL' : 'ONLINE' }}</dd>
                </div>
              </dl>
            </section>

            <DataQualityIndicator
              mode="detail"
              :score="activePoint.dqScore"
              :level="activePoint.dqLevel"
              :flags="activePoint.dqFlags"
              :is-backfill="activePoint.isBackfill"
              :alarm-factor="activePoint.raw.dqAlarmConfFactor"
              :dimensions="qualityDimensions(activePoint.raw)"
            />

            <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <p class="text-sm font-semibold text-white">时间口径</p>
              <dl class="mt-3 space-y-2 text-xs">
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">eventTime</dt>
                  <dd class="text-right font-mono text-slate-100">{{ formatDateTime(activePoint.raw.eventTime) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">recvTime</dt>
                  <dd class="text-right font-mono text-slate-100">{{ formatDateTime(activePoint.raw.recvTime) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">deviceTime</dt>
                  <dd class="text-right font-mono text-slate-100">{{ formatDateTime(activePoint.raw.deviceTime) }}</dd>
                </div>
              </dl>
            </section>

            <button class="secondary-button focus-ring w-full" type="button" @click="clearActivePoint">
              清除点位聚焦
            </button>
          </div>

          <div v-else class="flex min-h-[320px] flex-col items-center justify-center p-6 text-center text-sm text-slate-400">
            <Activity class="mb-3 h-8 w-8 text-blue-200" aria-hidden="true" />
            查询后可查看最近一个趋势点。
          </div>
        </aside>
      </section>

      <section class="glass-panel overflow-hidden">
        <div class="border-b border-white/10 p-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-white">
            <DatabaseZap class="h-4 w-4 text-blue-200" aria-hidden="true" />
            时序明细
          </div>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[960px] table-fixed border-collapse text-left text-sm">
            <thead class="border-b border-white/10 bg-white/[0.035] text-xs uppercase tracking-[0.08em] text-slate-400">
              <tr>
                <th class="w-[18%] px-4 py-3 font-medium">sourceRecordId</th>
                <th class="w-[17%] px-4 py-3 font-medium">eventTime</th>
                <th class="w-[11%] px-4 py-3 font-medium">value</th>
                <th class="w-[11%] px-4 py-3 font-medium">source</th>
                <th class="w-[12%] px-4 py-3 font-medium">DQ</th>
                <th class="w-[19%] px-4 py-3 font-medium">flags</th>
                <th class="w-[12%] px-4 py-3 font-medium">recv/device</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/10">
              <tr
                v-for="record in records"
                :key="record.sourceRecordId"
                class="transition-colors duration-200 hover:bg-blue-400/10"
              >
                <td class="px-4 py-3 align-top">
                  <p class="truncate font-mono text-xs text-blue-100">{{ record.sourceRecordId }}</p>
                  <p class="mt-1 truncate font-mono text-[11px] text-slate-500">{{ record.sourceBatchId }}</p>
                </td>
                <td class="px-4 py-3 align-top font-mono text-xs text-slate-100">{{ formatDateTime(record.eventTime) }}</td>
                <td class="px-4 py-3 align-top font-mono text-sm font-semibold text-white">{{ record.metricValue }}</td>
                <td class="px-4 py-3 align-top">
                  <span
                    class="rounded-md border px-2 py-1 text-xs"
                    :class="record.isBackfill ? 'border-amber-400/25 bg-amber-400/10 text-amber-100' : 'border-blue-400/20 bg-blue-400/10 text-blue-100'"
                  >
                    {{ record.isBackfill ? 'BACKFILL' : 'ONLINE' }}
                  </span>
                </td>
                <td class="px-4 py-3 align-top" colspan="2">
                  <DataQualityIndicator
                    mode="table"
                    :score="record.dqScore"
                    :level="record.dqLevel"
                    :flags="record.dqFlags"
                    :is-backfill="record.isBackfill"
                    :alarm-factor="record.dqAlarmConfFactor"
                  />
                </td>
                <td class="px-4 py-3 align-top text-xs text-slate-400">
                  <p class="truncate">recv {{ formatDateTime(record.recvTime) }}</p>
                  <p class="mt-1 truncate">dev {{ formatDateTime(record.deviceTime) }}</p>
                </td>
              </tr>
              <tr v-if="!records.length && state !== 'loading'">
                <td class="px-4 py-8 text-center text-sm text-slate-400" colspan="7">
                  暂无时序明细
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </main>
</template>
