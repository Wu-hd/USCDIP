<script setup lang="ts">
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  CalendarClock,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  DatabaseZap,
  Filter,
  Gauge,
  Loader2,
  RadioTower,
  RefreshCcw,
  Search,
  ServerCog,
  ShieldAlert,
  SlidersHorizontal,
  Wifi,
  WifiOff,
  X
} from 'lucide-vue-next';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import { ApiClientError } from '@/services/api';
import {
  getDeviceLedgerDevice,
  getDeviceLedgerDevices,
  type DeviceLedgerQuery
} from '@/services/deviceLedger';
import type { DeviceLedgerResponse } from '@/types/api';

type LoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ONLINE', label: '在线' },
  { value: 'WARNING', label: '预警' },
  { value: 'OFFLINE', label: '离线' }
];

const protocolOptions = [
  { value: '', label: '全部协议' },
  { value: 'MQTT', label: 'MQTT' },
  { value: 'MODBUS', label: 'MODBUS' },
  { value: 'NB_IOT', label: 'NB-IoT' }
];

const calibrationOptions = [
  { value: '', label: '全部标定' },
  { value: 'false', label: '有效' },
  { value: 'true', label: '已过期' }
];

const pageSizeOptions = [10, 20, 50];

const devices = ref<DeviceLedgerResponse[]>([]);
const selectedDevice = ref<DeviceLedgerResponse | null>(null);
const state = ref<LoadState>('idle');
const detailState = ref<LoadState>('idle');
const message = ref('等待加载设备台账');
const detailMessage = ref('选择设备查看台账快照');
const traceId = ref('');
const detailTraceId = ref('');
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const totalPages = ref(0);
const hasNext = ref(false);
const keyword = ref('');
const isDetailOpen = ref(false);

const filters = reactive({
  status: '',
  protocolType: '',
  calibrationExpired: '',
  regionId: '',
  segmentId: '',
  nodeId: '',
  facilityId: ''
});

const query = computed<DeviceLedgerQuery>(() => ({
  status: filters.status,
  protocolType: filters.protocolType,
  regionId: filters.regionId.trim(),
  segmentId: filters.segmentId.trim(),
  nodeId: filters.nodeId.trim(),
  facilityId: filters.facilityId.trim(),
  calibrationExpired:
    filters.calibrationExpired === ''
      ? undefined
      : filters.calibrationExpired === 'true'
}));

const filteredDevices = computed(() => {
  const normalizedKeyword = keyword.value.trim().toUpperCase();
  if (!normalizedKeyword) {
    return devices.value;
  }
  return devices.value.filter((device) =>
    [device.deviceId, device.deviceName, device.regionId, device.protocolType]
      .filter(Boolean)
      .some((value) => String(value).toUpperCase().includes(normalizedKeyword))
  );
});

const pageStats = computed(() => {
  const source = devices.value;
  const buffers = source
    .map((device) => device.bufferLevel)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  const statusCount = source.reduce(
    (result, device) => {
      const status = normalizeStatus(device.onlineStatus);
      if (status === 'ONLINE') {
        result.online += 1;
      } else if (status === 'WARNING') {
        result.warning += 1;
      } else if (status === 'OFFLINE') {
        result.offline += 1;
      }
      if (device.calibrationExpired) {
        result.expired += 1;
      }
      return result;
    },
    { online: 0, warning: 0, offline: 0, expired: 0 }
  );
  const avgBuffer = buffers.length
    ? Math.round(buffers.reduce((sum, value) => sum + value, 0) / buffers.length)
    : null;
  const maxBuffer = buffers.length ? Math.max(...buffers) : null;
  return {
    current: source.length,
    ...statusCount,
    avgBuffer,
    maxBuffer
  };
});

const metricCards = computed(() => [
  {
    key: 'total',
    label: '当前页设备',
    value: String(pageStats.value.current),
    hint: `总计 ${total.value}`,
    icon: DatabaseZap,
    tone: 'blue'
  },
  {
    key: 'online',
    label: '在线',
    value: String(pageStats.value.online),
    hint: '心跳与缓冲正常',
    icon: Wifi,
    tone: 'emerald'
  },
  {
    key: 'warning',
    label: '预警',
    value: String(pageStats.value.warning),
    hint: '缓冲或异常标记触发',
    icon: AlertTriangle,
    tone: 'amber'
  },
  {
    key: 'offline',
    label: '离线',
    value: String(pageStats.value.offline),
    hint: '心跳超时或无心跳',
    icon: WifiOff,
    tone: 'rose'
  },
  {
    key: 'calibration',
    label: '标定过期',
    value: String(pageStats.value.expired),
    hint: 'calibrationExpired=true',
    icon: CalendarClock,
    tone: 'orange'
  },
  {
    key: 'buffer',
    label: '缓冲水位',
    value: pageStats.value.avgBuffer === null ? '-' : `${pageStats.value.avgBuffer}%`,
    hint: pageStats.value.maxBuffer === null ? '暂无缓冲数据' : `峰值 ${pageStats.value.maxBuffer}%`,
    icon: Gauge,
    tone: 'cyan'
  }
]);

const canGoPrev = computed(() => page.value > 1 && state.value !== 'loading');
const canGoNext = computed(() => hasNext.value && state.value !== 'loading');

watch(pageSize, () => {
  void loadDevices(true);
});

onMounted(() => {
  void loadDevices(true);
});

async function loadDevices(resetPage = false) {
  if (resetPage) {
    page.value = 1;
  }
  state.value = 'loading';
  message.value = '正在读取设备台账';
  traceId.value = '';
  try {
    const response = await getDeviceLedgerDevices(page.value, pageSize.value, query.value);
    const data = response.data;
    devices.value = data?.items ?? [];
    total.value = data?.total ?? 0;
    totalPages.value = data?.totalPages ?? 0;
    hasNext.value = Boolean(data?.hasNext);
    traceId.value = response.traceId;
    state.value = devices.value.length ? 'ready' : 'empty';
    message.value = devices.value.length ? '设备台账已同步' : '当前筛选无设备';
  } catch (error) {
    devices.value = [];
    total.value = 0;
    totalPages.value = 0;
    hasNext.value = false;
    state.value = 'error';
    if (error instanceof ApiClientError) {
      traceId.value = error.traceId ?? '';
      message.value = error.message;
      return;
    }
    message.value = '设备台账读取失败';
  }
}

async function loadDeviceDetail(deviceId: string) {
  detailState.value = 'loading';
  detailMessage.value = '正在读取设备详情';
  detailTraceId.value = '';
  try {
    const response = await getDeviceLedgerDevice(deviceId);
    if (response.data) {
      selectedDevice.value = response.data;
      detailState.value = 'ready';
      detailMessage.value = '设备详情已同步';
      detailTraceId.value = response.traceId;
      return;
    }
    detailState.value = 'empty';
    detailMessage.value = '后端未返回设备详情';
  } catch (error) {
    detailState.value = 'error';
    if (error instanceof ApiClientError) {
      detailTraceId.value = error.traceId ?? '';
      detailMessage.value = error.message;
      return;
    }
    detailMessage.value = '设备详情读取失败';
  }
}

function applyFilters() {
  void loadDevices(true);
}

function resetFilters() {
  filters.status = '';
  filters.protocolType = '';
  filters.calibrationExpired = '';
  filters.regionId = '';
  filters.segmentId = '';
  filters.nodeId = '';
  filters.facilityId = '';
  keyword.value = '';
  void loadDevices(true);
}

function goPrev() {
  if (!canGoPrev.value) {
    return;
  }
  page.value -= 1;
  void loadDevices();
}

function goNext() {
  if (!canGoNext.value) {
    return;
  }
  page.value += 1;
  void loadDevices();
}

function openDevice(device: DeviceLedgerResponse) {
  selectedDevice.value = device;
  isDetailOpen.value = true;
  void loadDeviceDetail(device.deviceId);
}

function closeDetail() {
  isDetailOpen.value = false;
}

function refreshSelectedDevice() {
  if (!selectedDevice.value) {
    return;
  }
  void loadDeviceDetail(selectedDevice.value.deviceId);
}

function normalizeStatus(status: string | null | undefined) {
  return status?.toUpperCase() ?? '';
}

function statusLabel(status: string | null | undefined) {
  const normalized = normalizeStatus(status);
  if (normalized === 'ONLINE') {
    return '在线';
  }
  if (normalized === 'WARNING') {
    return '预警';
  }
  if (normalized === 'OFFLINE') {
    return '离线';
  }
  return status || '-';
}

function statusToneClass(status: string | null | undefined) {
  const normalized = normalizeStatus(status);
  if (normalized === 'ONLINE') {
    return 'border-emerald-400/25 bg-emerald-400/10 text-emerald-100';
  }
  if (normalized === 'WARNING') {
    return 'border-amber-400/25 bg-amber-400/10 text-amber-100';
  }
  if (normalized === 'OFFLINE') {
    return 'border-rose-400/25 bg-rose-400/10 text-rose-100';
  }
  return 'border-white/10 bg-white/[0.055] text-slate-200';
}

function statusIcon(status: string | null | undefined) {
  const normalized = normalizeStatus(status);
  if (normalized === 'ONLINE') {
    return Wifi;
  }
  if (normalized === 'WARNING') {
    return AlertTriangle;
  }
  if (normalized === 'OFFLINE') {
    return WifiOff;
  }
  return RadioTower;
}

function metricToneClass(tone: string) {
  const tones: Record<string, string> = {
    blue: 'border-blue-400/20 bg-blue-400/10 text-blue-100',
    emerald: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-100',
    amber: 'border-amber-400/20 bg-amber-400/10 text-amber-100',
    rose: 'border-rose-400/20 bg-rose-400/10 text-rose-100',
    orange: 'border-orange-400/20 bg-orange-400/10 text-orange-100',
    cyan: 'border-cyan-400/20 bg-cyan-400/10 text-cyan-100'
  };
  return tones[tone] ?? tones.blue;
}

function bufferToneClass(value: number | null | undefined) {
  if (typeof value !== 'number') {
    return 'text-slate-400';
  }
  if (value >= 80) {
    return 'text-rose-100';
  }
  if (value >= 60) {
    return 'text-amber-100';
  }
  return 'text-emerald-100';
}

function calibrationToneClass(device: DeviceLedgerResponse) {
  return device.calibrationExpired
    ? 'border-rose-400/25 bg-rose-400/10 text-rose-100'
    : 'border-emerald-400/20 bg-emerald-400/10 text-emerald-100';
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

function formatValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

function formatBuffer(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}%` : '-';
}
</script>

<template>
  <main class="portal-shell">
    <div class="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-5 px-4 py-6 sm:px-6 lg:px-8">
      <header class="flex flex-col gap-4 rounded-lg border border-white/10 bg-white/[0.045] p-4 backdrop-blur-xl lg:flex-row lg:items-center lg:justify-between">
        <div class="min-w-0">
          <RouterLink class="secondary-button focus-ring mb-4" to="/mgmt">
            <ArrowLeft class="h-4 w-4" />
            返回管理平台
          </RouterLink>
          <div class="flex items-center gap-3">
            <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg border border-blue-400/20 bg-blue-400/10 text-blue-100">
              <ServerCog class="h-6 w-6" aria-hidden="true" />
            </div>
            <div class="min-w-0">
              <p class="text-xs font-semibold uppercase tracking-[0.18em] text-blue-200">
                MGMT / DEVICE LEDGER
              </p>
              <h1 class="mt-1 font-display text-2xl font-semibold text-white sm:text-3xl">
                设备台账列表
              </h1>
            </div>
          </div>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
            按后端台账口径展示设备在线、心跳、缓冲水位与标定有效期，在线状态由心跳和缓冲水位共同决定。
          </p>
        </div>

        <div class="grid min-w-full grid-cols-2 gap-3 sm:min-w-[360px]">
          <div class="rounded-lg border border-white/10 bg-black/20 p-3">
            <p class="text-xs text-slate-400">Page</p>
            <p class="mt-1 font-mono text-lg font-semibold text-white">{{ page }} / {{ totalPages || 1 }}</p>
          </div>
          <div class="rounded-lg border border-white/10 bg-black/20 p-3">
            <p class="text-xs text-slate-400">TraceId</p>
            <p class="mt-1 truncate font-mono text-xs text-blue-100">{{ traceId || '-' }}</p>
          </div>
        </div>
      </header>

      <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-6" aria-label="设备台账指标">
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
          <p class="mt-3 font-mono text-2xl font-semibold text-white">{{ card.value }}</p>
          <p class="mt-1 truncate text-xs text-slate-400">{{ card.hint }}</p>
        </article>
      </section>

      <section class="glass-panel p-4">
        <form class="grid gap-3 lg:grid-cols-[1.3fr_repeat(3,minmax(0,0.8fr))] xl:grid-cols-[1.4fr_repeat(7,minmax(0,0.8fr))_auto]" @submit.prevent="applyFilters">
          <label class="min-w-0">
            <span class="mb-1 flex items-center gap-2 text-xs font-medium text-slate-300">
              <Search class="h-3.5 w-3.5 text-blue-200" aria-hidden="true" />
              当前页关键词
            </span>
            <input
              v-model="keyword"
              class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white placeholder:text-slate-500"
              type="search"
              placeholder="deviceId / 名称 / region"
            />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">状态</span>
            <select v-model="filters.status" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white" @change="applyFilters">
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">协议</span>
            <select v-model="filters.protocolType" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white" @change="applyFilters">
              <option v-for="option in protocolOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">标定</span>
            <select v-model="filters.calibrationExpired" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 text-sm text-white" @change="applyFilters">
              <option v-for="option in calibrationOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">Region</span>
            <input v-model="filters.regionId" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white placeholder:text-slate-500" placeholder="REGION-HZ" />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">Segment</span>
            <input v-model="filters.segmentId" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white placeholder:text-slate-500" placeholder="SEG-001" />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">Node</span>
            <input v-model="filters.nodeId" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white placeholder:text-slate-500" placeholder="NODE-001" />
          </label>

          <label>
            <span class="mb-1 block text-xs font-medium text-slate-300">Facility</span>
            <input v-model="filters.facilityId" class="focus-ring h-10 w-full rounded-lg border border-white/10 bg-black/20 px-3 font-mono text-sm text-white placeholder:text-slate-500" placeholder="FAC-001" />
          </label>

          <div class="flex items-end gap-2">
            <button class="primary-button focus-ring h-10" type="submit" :disabled="state === 'loading'">
              <Filter class="h-4 w-4" />
              筛选
            </button>
            <button class="secondary-button focus-ring h-10 px-3" type="button" @click="resetFilters">
              <X class="h-4 w-4" />
            </button>
          </div>
        </form>
      </section>

      <section class="grid flex-1 gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div class="glass-panel min-w-0 overflow-hidden">
          <div class="flex flex-col gap-3 border-b border-white/10 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div class="flex items-center gap-2 text-sm font-semibold text-white">
                <SlidersHorizontal class="h-4 w-4 text-blue-200" aria-hidden="true" />
                台账结果
              </div>
              <p class="mt-1 text-xs text-slate-400">
                {{ message }}，当前页显示 {{ filteredDevices.length }} / {{ devices.length }} 条
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <label class="flex items-center gap-2 text-xs text-slate-400">
                pageSize
                <select v-model.number="pageSize" class="focus-ring h-9 rounded-lg border border-white/10 bg-black/20 px-2 text-sm text-white">
                  <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
                </select>
              </label>
              <button class="secondary-button focus-ring h-9 px-3" type="button" :disabled="state === 'loading'" @click="loadDevices(false)">
                <RefreshCcw class="h-4 w-4" />
                刷新
              </button>
            </div>
          </div>

          <div v-if="state === 'loading'" class="flex min-h-[420px] flex-col items-center justify-center text-sm text-slate-300">
            <Loader2 class="mb-3 h-7 w-7 animate-spin text-blue-200" aria-hidden="true" />
            正在加载设备台账
          </div>

          <div v-else-if="state === 'error'" class="m-4 rounded-lg border border-rose-400/20 bg-rose-400/10 p-5 text-sm text-rose-50">
            <div class="flex gap-3">
              <ShieldAlert class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div class="min-w-0">
                <p class="font-semibold">设备台账读取失败</p>
                <p class="mt-1 text-rose-100">{{ message }}</p>
                <p v-if="traceId" class="mt-2 truncate font-mono text-xs text-rose-100">TraceId {{ traceId }}</p>
              </div>
            </div>
          </div>

          <div v-else-if="state === 'empty' || !filteredDevices.length" class="flex min-h-[420px] flex-col items-center justify-center p-6 text-center text-sm text-slate-400">
            <Search class="mb-3 h-8 w-8 text-blue-200" aria-hidden="true" />
            <p class="font-semibold text-white">没有匹配设备</p>
            <p class="mt-2 max-w-sm leading-6">调整状态、协议、对象链或当前页关键词后重新筛选。</p>
          </div>

          <div v-else>
            <div class="hidden overflow-x-auto lg:block">
              <table class="w-full table-fixed border-collapse text-left text-sm">
                <thead class="border-b border-white/10 bg-white/[0.035] text-xs uppercase tracking-[0.08em] text-slate-400">
                  <tr>
                    <th class="w-[22%] px-4 py-3 font-medium">设备</th>
                    <th class="w-[12%] px-4 py-3 font-medium">协议</th>
                    <th class="w-[16%] px-4 py-3 font-medium">在线状态</th>
                    <th class="w-[18%] px-4 py-3 font-medium">最近心跳</th>
                    <th class="w-[14%] px-4 py-3 font-medium">缓冲水位</th>
                    <th class="w-[18%] px-4 py-3 font-medium">标定有效期</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-white/10">
                  <tr
                    v-for="device in filteredDevices"
                    :key="device.deviceId"
                    class="cursor-pointer transition-colors duration-200 hover:bg-blue-400/10"
                    :class="selectedDevice?.deviceId === device.deviceId ? 'bg-blue-400/10' : ''"
                    tabindex="0"
                    @click="openDevice(device)"
                    @keydown.enter="openDevice(device)"
                  >
                    <td class="px-4 py-4 align-top">
                      <p class="truncate font-semibold text-white">{{ device.deviceName || device.deviceId }}</p>
                      <p class="mt-1 truncate font-mono text-xs text-blue-100">{{ device.deviceId }}</p>
                      <p class="mt-1 truncate text-xs text-slate-500">{{ device.regionId || '-' }}</p>
                    </td>
                    <td class="px-4 py-4 align-top">
                      <span class="rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2 py-1 font-mono text-xs text-cyan-100">
                        {{ device.protocolType }}
                      </span>
                    </td>
                    <td class="px-4 py-4 align-top">
                      <span class="inline-flex max-w-full items-center gap-1.5 rounded-md border px-2 py-1 text-xs" :class="statusToneClass(device.onlineStatus)">
                        <component :is="statusIcon(device.onlineStatus)" class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        {{ statusLabel(device.onlineStatus) }}
                      </span>
                      <p class="mt-2 line-clamp-2 text-xs text-slate-400">{{ device.onlineStatusReason || 'HEARTBEAT_OK' }}</p>
                    </td>
                    <td class="px-4 py-4 align-top">
                      <p class="font-mono text-xs text-slate-100">{{ formatDateTime(device.lastHeartbeat) }}</p>
                      <p class="mt-1 text-xs text-slate-500">recv {{ formatDateTime(device.lastRecvTime) }}</p>
                    </td>
                    <td class="px-4 py-4 align-top">
                      <p class="font-mono text-sm font-semibold" :class="bufferToneClass(device.bufferLevel)">{{ formatBuffer(device.bufferLevel) }}</p>
                      <p class="mt-1 truncate text-xs text-slate-500">{{ device.abnormalFlags?.length ? device.abnormalFlags.join(' / ') : '无异常标记' }}</p>
                    </td>
                    <td class="px-4 py-4 align-top">
                      <span class="inline-flex rounded-md border px-2 py-1 text-xs" :class="calibrationToneClass(device)">
                        {{ device.calibrationExpired ? '已过期' : '有效' }}
                      </span>
                      <p class="mt-2 font-mono text-xs text-slate-400">{{ formatDateTime(device.calibrationDueAt) }}</p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="grid gap-3 p-4 lg:hidden">
              <button
                v-for="device in filteredDevices"
                :key="device.deviceId"
                class="focus-ring cursor-pointer rounded-lg border border-white/10 bg-white/[0.045] p-4 text-left transition-colors duration-200 hover:border-blue-300/40 hover:bg-blue-400/10"
                type="button"
                @click="openDevice(device)"
              >
                <span class="flex items-start justify-between gap-3">
                  <span class="min-w-0">
                    <span class="block truncate font-semibold text-white">{{ device.deviceName || device.deviceId }}</span>
                    <span class="mt-1 block font-mono text-xs text-blue-100">{{ device.deviceId }} / {{ device.protocolType }}</span>
                  </span>
                  <span class="shrink-0 rounded-md border px-2 py-1 text-xs" :class="statusToneClass(device.onlineStatus)">
                    {{ statusLabel(device.onlineStatus) }}
                  </span>
                </span>
                <span class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-400">
                  <span>heartbeat <span class="block text-slate-100">{{ formatDateTime(device.lastHeartbeat) }}</span></span>
                  <span>buffer <span class="block" :class="bufferToneClass(device.bufferLevel)">{{ formatBuffer(device.bufferLevel) }}</span></span>
                  <span>region <span class="block text-slate-100">{{ device.regionId || '-' }}</span></span>
                  <span>calibration <span class="block text-slate-100">{{ device.calibrationExpired ? '已过期' : formatDateTime(device.calibrationDueAt) }}</span></span>
                </span>
              </button>
            </div>
          </div>

          <div class="flex flex-col gap-3 border-t border-white/10 p-4 sm:flex-row sm:items-center sm:justify-between">
            <p class="text-xs text-slate-400">
              第 {{ page }} 页，后端总计 {{ total }} 条
            </p>
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

        <aside
          class="glass-panel min-h-[420px] overflow-hidden xl:sticky xl:top-6 xl:h-[calc(100vh-3rem)]"
          :class="isDetailOpen ? 'block' : 'hidden xl:block'"
          aria-label="设备详情"
        >
          <div class="flex items-center justify-between gap-3 border-b border-white/10 p-4">
            <div>
              <p class="text-sm font-semibold text-white">设备详情</p>
              <p class="mt-1 text-xs text-slate-400">{{ detailMessage }}</p>
            </div>
            <div class="flex items-center gap-2">
              <button class="icon-button focus-ring" type="button" :disabled="!selectedDevice || detailState === 'loading'" @click="refreshSelectedDevice">
                <RefreshCcw class="h-4 w-4" />
                <span class="sr-only">刷新设备详情</span>
              </button>
              <button class="icon-button focus-ring xl:hidden" type="button" @click="closeDetail">
                <X class="h-4 w-4" />
                <span class="sr-only">关闭设备详情</span>
              </button>
            </div>
          </div>

          <div v-if="detailState === 'loading'" class="flex h-full min-h-[320px] flex-col items-center justify-center p-6 text-sm text-slate-300">
            <Loader2 class="mb-3 h-7 w-7 animate-spin text-blue-200" aria-hidden="true" />
            正在同步设备详情
          </div>

          <div v-else-if="detailState === 'error'" class="m-4 rounded-lg border border-rose-400/20 bg-rose-400/10 p-4 text-sm text-rose-50">
            <div class="flex gap-3">
              <ShieldAlert class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div class="min-w-0">
                <p class="font-semibold">详情读取失败</p>
                <p class="mt-1">{{ detailMessage }}</p>
                <p v-if="detailTraceId" class="mt-2 truncate font-mono text-xs">TraceId {{ detailTraceId }}</p>
              </div>
            </div>
          </div>

          <div v-else-if="selectedDevice" class="space-y-4 overflow-y-auto p-4 xl:h-[calc(100%-73px)]">
            <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-lg font-semibold text-white">{{ selectedDevice.deviceName || selectedDevice.deviceId }}</p>
                  <p class="mt-1 font-mono text-xs text-blue-100">{{ selectedDevice.deviceId }}</p>
                </div>
                <span class="shrink-0 rounded-md border px-2 py-1 text-xs" :class="statusToneClass(selectedDevice.onlineStatus)">
                  {{ statusLabel(selectedDevice.onlineStatus) }}
                </span>
              </div>
              <p class="mt-3 rounded-lg border border-white/10 bg-black/20 p-3 text-xs leading-5 text-slate-300">
                {{ selectedDevice.onlineStatusReason || 'HEARTBEAT_OK' }}
              </p>
              <RouterLink
                class="secondary-button focus-ring mt-3 w-full"
                :to="{ path: '/mgmt/trends', query: { deviceId: selectedDevice.deviceId, metricCode: 'PRESSURE' } }"
              >
                <Activity class="h-4 w-4" />
                查看趋势
              </RouterLink>
            </section>

            <section class="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <div class="flex items-center gap-2 text-sm font-semibold text-white">
                  <Clock3 class="h-4 w-4 text-blue-200" aria-hidden="true" />
                  心跳链路
                </div>
                <dl class="mt-3 space-y-2 text-xs">
                  <div class="flex justify-between gap-3">
                    <dt class="text-slate-400">lastHeartbeat</dt>
                    <dd class="text-right font-mono text-slate-100">{{ formatDateTime(selectedDevice.lastHeartbeat) }}</dd>
                  </div>
                  <div class="flex justify-between gap-3">
                    <dt class="text-slate-400">lastRecvTime</dt>
                    <dd class="text-right font-mono text-slate-100">{{ formatDateTime(selectedDevice.lastRecvTime) }}</dd>
                  </div>
                </dl>
              </div>

              <div class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
                <div class="flex items-center gap-2 text-sm font-semibold text-white">
                  <Gauge class="h-4 w-4 text-amber-200" aria-hidden="true" />
                  缓冲与异常
                </div>
                <dl class="mt-3 space-y-2 text-xs">
                  <div class="flex justify-between gap-3">
                    <dt class="text-slate-400">bufferLevel</dt>
                    <dd class="font-mono font-semibold" :class="bufferToneClass(selectedDevice.bufferLevel)">{{ formatBuffer(selectedDevice.bufferLevel) }}</dd>
                  </div>
                  <div class="flex justify-between gap-3">
                    <dt class="text-slate-400">abnormalFlags</dt>
                    <dd class="min-w-0 truncate text-right text-slate-100">{{ selectedDevice.abnormalFlags?.length ? selectedDevice.abnormalFlags.join(' / ') : '-' }}</dd>
                  </div>
                </dl>
              </div>
            </section>

            <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <div class="flex items-center gap-2 text-sm font-semibold text-white">
                <Activity class="h-4 w-4 text-emerald-200" aria-hidden="true" />
                对象链
              </div>
              <dl class="mt-3 grid gap-2 text-xs">
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">regionId</dt>
                  <dd class="font-mono text-slate-100">{{ formatValue(selectedDevice.regionId) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">segmentId</dt>
                  <dd class="font-mono text-slate-100">{{ formatValue(selectedDevice.segmentId) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">nodeId</dt>
                  <dd class="font-mono text-slate-100">{{ formatValue(selectedDevice.nodeId) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">facilityId</dt>
                  <dd class="font-mono text-slate-100">{{ formatValue(selectedDevice.facilityId) }}</dd>
                </div>
              </dl>
            </section>

            <section class="rounded-lg border border-white/10 bg-white/[0.045] p-4">
              <div class="flex items-center gap-2 text-sm font-semibold text-white">
                <CalendarClock class="h-4 w-4 text-orange-200" aria-hidden="true" />
                标定与版本
              </div>
              <dl class="mt-3 grid gap-2 text-xs">
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">calibrationDueAt</dt>
                  <dd class="text-right font-mono text-slate-100">{{ formatDateTime(selectedDevice.calibrationDueAt) }}</dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">calibrationExpired</dt>
                  <dd class="inline-flex items-center gap-1 text-slate-100">
                    <CheckCircle2 v-if="!selectedDevice.calibrationExpired" class="h-3.5 w-3.5 text-emerald-200" />
                    <AlertTriangle v-else class="h-3.5 w-3.5 text-rose-200" />
                    {{ selectedDevice.calibrationExpired ? 'true' : 'false' }}
                  </dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-slate-400">versionNo</dt>
                  <dd class="font-mono text-slate-100">{{ formatValue(selectedDevice.versionNo) }}</dd>
                </div>
              </dl>
            </section>

            <p v-if="detailTraceId" class="truncate font-mono text-xs text-blue-100">
              Detail TraceId {{ detailTraceId }}
            </p>
          </div>

          <div v-else class="flex min-h-[420px] flex-col items-center justify-center p-6 text-center text-sm text-slate-400">
            <RadioTower class="mb-3 h-8 w-8 text-blue-200" aria-hidden="true" />
            点击设备行查看心跳、缓冲与标定上下文。
          </div>
        </aside>
      </section>
    </div>
  </main>
</template>
