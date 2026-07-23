<script setup lang="ts">
import {
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleSlash2,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCog,
  UsersRound,
  X
} from 'lucide-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';

import { ApiClientError } from '@/services/api';
import {
  createAccount,
  getAccountOptions,
  getAccounts,
  resetAccountPassword,
  updateAccount
} from '@/services/accounts';
import type {
  AccountCreateRequest,
  AccountDataScopes,
  AccountOptionsResponse,
  AccountPageResponse,
  AccountResponse,
  AccountStatus,
  AccountUpdateRequest
} from '@/types/api';

type EditorMode = 'empty' | 'create' | 'edit';

interface AccountFormState {
  username: string;
  displayName: string;
  primaryRegionId: string;
  password: string;
  status: AccountStatus;
  roleCodes: string[];
  regionScopes: string;
  assigneeScopes: string;
  dataViewScopes: string;
  aggregateScopes: string;
}

const EMPTY_OPTIONS: AccountOptionsResponse = { roles: [], statuses: ['ACTIVE', 'DISABLED'], scopeTypes: [] };
const EMPTY_PAGE: AccountPageResponse = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 20,
  totalPages: 0,
  hasNext: false,
  activeCount: 0,
  disabledCount: 0,
  platformAdminCount: 0
};

const accounts = ref<AccountPageResponse>({ ...EMPTY_PAGE });
const options = ref<AccountOptionsResponse>({ ...EMPTY_OPTIONS });
const keyword = ref('');
const appliedKeyword = ref('');
const statusFilter = ref<'ALL' | AccountStatus>('ALL');
const loading = ref(true);
const saving = ref(false);
const editorMode = ref<EditorMode>('empty');
const selectedUserId = ref('');
const feedback = ref('');
const errorMessage = ref('');
const resetPasswordValue = ref('');
const resetPasswordConfirm = ref('');

const form = reactive<AccountFormState>(createEmptyForm());

const selectedAccount = computed(() =>
  accounts.value.items.find((account) => account.userId === selectedUserId.value) ?? null
);

const metricCards = computed(() => [
  { label: '账号总数', value: accounts.value.activeCount + accounts.value.disabledCount, icon: UsersRound, tone: 'text-cyan-200' },
  { label: '启用账号', value: accounts.value.activeCount, icon: CheckCircle2, tone: 'text-emerald-200' },
  { label: '停用账号', value: accounts.value.disabledCount, icon: CircleSlash2, tone: 'text-rose-200' },
  { label: '平台管理员', value: accounts.value.platformAdminCount, icon: ShieldCheck, tone: 'text-amber-200' }
]);

function createEmptyForm(): AccountFormState {
  return {
    username: '',
    displayName: '',
    primaryRegionId: 'REGION-HZ',
    password: '',
    status: 'ACTIVE',
    roleCodes: [],
    regionScopes: 'REGION-HZ',
    assigneeScopes: '',
    dataViewScopes: '',
    aggregateScopes: ''
  };
}

function applyForm(next: AccountFormState): void {
  Object.assign(form, next);
}

function scopeText(account: AccountResponse, type: string): string {
  return (account.dataScopes[type] ?? []).join(', ');
}

function openCreate(): void {
  editorMode.value = 'create';
  selectedUserId.value = '';
  feedback.value = '';
  errorMessage.value = '';
  resetPasswordValue.value = '';
  resetPasswordConfirm.value = '';
  applyForm(createEmptyForm());
}

function openEdit(account: AccountResponse): void {
  editorMode.value = 'edit';
  selectedUserId.value = account.userId;
  feedback.value = '';
  errorMessage.value = '';
  resetPasswordValue.value = '';
  resetPasswordConfirm.value = '';
  applyForm({
    username: account.username,
    displayName: account.displayName,
    primaryRegionId: account.primaryRegionId,
    password: '',
    status: account.status,
    roleCodes: [...account.roleCodes],
    regionScopes: scopeText(account, 'REGION'),
    assigneeScopes: scopeText(account, 'ASSIGNEE'),
    dataViewScopes: scopeText(account, 'DATA_VIEW'),
    aggregateScopes: scopeText(account, 'REGION_AGGREGATE')
  });
}

function closeEditor(): void {
  editorMode.value = 'empty';
  selectedUserId.value = '';
  feedback.value = '';
  errorMessage.value = '';
}

function toggleRole(roleCode: string): void {
  form.roleCodes = form.roleCodes.includes(roleCode)
    ? form.roleCodes.filter((code) => code !== roleCode)
    : [...form.roleCodes, roleCode];
}

function parseValues(value: string): string[] {
  return [...new Set(value.split(/[\n,，]+/).map((item) => item.trim()).filter(Boolean))];
}

function buildScopes(): AccountDataScopes {
  const scopes: AccountDataScopes = {};
  const entries = [
    ['REGION', form.regionScopes],
    ['ASSIGNEE', form.assigneeScopes],
    ['DATA_VIEW', form.dataViewScopes],
    ['REGION_AGGREGATE', form.aggregateScopes]
  ] as const;
  for (const [type, value] of entries) {
    const values = parseValues(value);
    if (values.length) scopes[type] = values;
  }
  return scopes;
}

function validateForm(): string {
  if (!form.username.trim() || !form.displayName.trim() || !form.primaryRegionId.trim()) return '请完整填写账号基础信息';
  if (!form.roleCodes.length) return '请至少选择一个角色';
  if (editorMode.value === 'create' && !isStrongPassword(form.password)) return '初始密码需为 10–72 位，并包含大小写字母、数字和特殊字符';
  return '';
}

function isStrongPassword(value: string): boolean {
  return value.length >= 10
    && value.length <= 72
    && /[a-z]/.test(value)
    && /[A-Z]/.test(value)
    && /\d/.test(value)
    && /[^A-Za-z0-9]/.test(value);
}

async function loadOptions(): Promise<void> {
  const response = await getAccountOptions();
  options.value = response.data ?? { ...EMPTY_OPTIONS };
}

async function loadAccounts(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const response = await getAccounts({
      keyword: appliedKeyword.value || undefined,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      page: accounts.value.page,
      pageSize: accounts.value.pageSize
    });
    accounts.value = response.data ?? { ...EMPTY_PAGE };
    if (selectedUserId.value) {
      const current = accounts.value.items.find((account) => account.userId === selectedUserId.value);
      if (current) openEdit(current);
      else closeEditor();
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : '账号列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function searchAccounts(): Promise<void> {
  appliedKeyword.value = keyword.value.trim();
  accounts.value.page = 1;
  await loadAccounts();
}

async function changePage(nextPage: number): Promise<void> {
  if (nextPage < 1 || nextPage > Math.max(accounts.value.totalPages, 1)) return;
  accounts.value.page = nextPage;
  await loadAccounts();
}

async function saveAccount(): Promise<void> {
  feedback.value = '';
  errorMessage.value = validateForm();
  if (errorMessage.value) return;

  saving.value = true;
  let successMessage = '';
  try {
    if (editorMode.value === 'create') {
      const request: AccountCreateRequest = {
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        primaryRegionId: form.primaryRegionId.trim(),
        password: form.password,
        roleCodes: form.roleCodes,
        dataScopes: buildScopes()
      };
      const response = await createAccount(request);
      successMessage = '账号已创建，可使用初始密码登录';
      selectedUserId.value = response.data?.userId ?? '';
    } else if (selectedAccount.value) {
      const request: AccountUpdateRequest = {
        displayName: form.displayName.trim(),
        primaryRegionId: form.primaryRegionId.trim(),
        status: form.status,
        roleCodes: form.roleCodes,
        dataScopes: buildScopes()
      };
      await updateAccount(selectedAccount.value.userId, request);
      successMessage = form.status === 'DISABLED' ? '账号已停用并吊销现有会话' : '账号配置已保存';
    }
    await loadAccounts();
    feedback.value = successMessage;
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : '账号保存失败';
  } finally {
    saving.value = false;
  }
}

async function resetPassword(): Promise<void> {
  feedback.value = '';
  if (!selectedAccount.value) return;
  if (!isStrongPassword(resetPasswordValue.value)) {
    errorMessage.value = '新密码需为 10–72 位，并包含大小写字母、数字和特殊字符';
    return;
  }
  if (resetPasswordValue.value !== resetPasswordConfirm.value) {
    errorMessage.value = '两次输入的新密码不一致';
    return;
  }

  saving.value = true;
  errorMessage.value = '';
  try {
    await resetAccountPassword(selectedAccount.value.userId, resetPasswordValue.value);
    resetPasswordValue.value = '';
    resetPasswordConfirm.value = '';
    await loadAccounts();
    feedback.value = '密码已重置，旧登录会话已失效';
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : '密码重置失败';
  } finally {
    saving.value = false;
  }
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '从未登录';
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(new Date(value));
}

onMounted(async () => {
  try {
    await Promise.all([loadOptions(), loadAccounts()]);
  } catch (error) {
    errorMessage.value = error instanceof ApiClientError ? error.message : '账号管理初始化失败';
    loading.value = false;
  }
});
</script>

<template>
  <main class="portal-shell">
    <div class="app-frame app-viewport flex flex-col gap-5 px-4 py-5 sm:px-6 lg:px-8">
      <header class="flex flex-col gap-4 border-b border-white/10 pb-5 lg:flex-row lg:items-end lg:justify-between">
        <div class="min-w-0">
          <RouterLink class="secondary-button focus-ring mb-4" to="/mgmt">
            <ArrowLeft class="h-4 w-4" />
            返回综合管理平台
          </RouterLink>
          <div class="flex items-center gap-3">
            <span class="flex h-11 w-11 shrink-0 items-center justify-center border border-cyan-300/25 bg-cyan-400/10 text-cyan-100">
              <UserCog class="h-6 w-6" aria-hidden="true" />
            </span>
            <div>
              <p class="text-xs font-semibold text-cyan-200">MGMT / IDENTITY ADMINISTRATION</p>
              <h1 class="mt-1 font-display text-2xl font-semibold text-white sm:text-3xl">账号管理</h1>
            </div>
          </div>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-300">
            统一维护本地登录账号、角色权限和数据域，安全变更会立即收敛已有会话。
          </p>
        </div>
        <button class="primary-button focus-ring" type="button" @click="openCreate">
          <Plus class="h-4 w-4" />
          新建账号
        </button>
      </header>

      <section class="grid grid-cols-2 gap-3 xl:grid-cols-4" aria-label="账号指标">
        <article v-for="card in metricCards" :key="card.label" class="border border-white/10 bg-white/[0.045] p-4">
          <div class="flex items-center justify-between gap-3">
            <p class="text-xs text-slate-400">{{ card.label }}</p>
            <component :is="card.icon" class="h-4 w-4" :class="card.tone" aria-hidden="true" />
          </div>
          <p class="mt-3 font-mono text-2xl font-semibold text-white">{{ card.value }}</p>
        </article>
      </section>

      <section class="flex flex-col gap-3 border border-white/10 bg-white/[0.035] p-3 md:flex-row md:items-center" aria-label="账号筛选">
        <form class="flex min-w-0 flex-1 gap-2" @submit.prevent="searchAccounts">
          <label class="relative min-w-0 flex-1">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" aria-hidden="true" />
            <input v-model="keyword" class="focus-ring h-10 w-full border border-white/10 bg-black/20 pl-10 pr-3 text-sm text-white placeholder:text-slate-600" placeholder="搜索账号、姓名或用户 ID" />
          </label>
          <button class="secondary-button focus-ring" type="submit">查询</button>
        </form>
        <select v-model="statusFilter" class="focus-ring h-10 border border-white/10 bg-[#101620] px-3 text-sm text-slate-200" @change="searchAccounts">
          <option value="ALL">全部状态</option>
          <option value="ACTIVE">启用</option>
          <option value="DISABLED">停用</option>
        </select>
        <button class="icon-button focus-ring" type="button" title="刷新账号列表" aria-label="刷新账号列表" :disabled="loading" @click="loadAccounts">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        </button>
      </section>

      <p v-if="errorMessage && editorMode === 'empty'" class="border border-rose-300/20 bg-rose-400/10 px-4 py-3 text-sm text-rose-100" role="alert">{{ errorMessage }}</p>

      <section class="grid min-h-[560px] gap-4 xl:grid-cols-[minmax(0,1.55fr)_minmax(360px,0.75fr)]">
        <div class="min-w-0 border border-white/10 bg-white/[0.03]">
          <div class="flex items-center justify-between border-b border-white/10 px-4 py-3">
            <div>
              <h2 class="text-sm font-semibold text-white">账号列表</h2>
              <p class="mt-1 text-xs text-slate-500">当前结果 {{ accounts.total }} 条</p>
            </div>
            <Loader2 v-if="loading" class="h-4 w-4 animate-spin text-cyan-200" aria-label="正在加载" />
          </div>

          <div class="overflow-x-auto">
            <table class="w-full min-w-[780px] border-collapse text-left text-sm">
              <thead class="bg-black/15 text-xs text-slate-500">
                <tr>
                  <th class="px-4 py-3 font-medium">账号</th>
                  <th class="px-4 py-3 font-medium">姓名 / 数据域</th>
                  <th class="px-4 py-3 font-medium">角色</th>
                  <th class="px-4 py-3 font-medium">登录状态</th>
                  <th class="px-4 py-3 font-medium">最近登录</th>
                  <th class="px-4 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="account in accounts.items" :key="account.userId" class="transition-colors hover:bg-white/[0.035]" :class="{ 'bg-cyan-400/[0.045]': selectedUserId === account.userId }">
                  <td class="px-4 py-3">
                    <p class="font-semibold text-white">{{ account.username }}</p>
                    <p class="mt-1 font-mono text-[10px] text-slate-600">{{ account.userId }}</p>
                  </td>
                  <td class="px-4 py-3">
                    <p class="text-slate-200">{{ account.displayName }}</p>
                    <p class="mt-1 text-xs text-slate-500">{{ account.primaryRegionId }}</p>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex max-w-56 flex-wrap gap-1">
                      <span v-for="role in account.roleCodes" :key="role" class="border border-blue-300/15 bg-blue-400/10 px-1.5 py-1 font-mono text-[10px] text-blue-100">{{ role }}</span>
                    </div>
                  </td>
                  <td class="px-4 py-3">
                    <span class="inline-flex items-center gap-1.5 text-xs" :class="account.status === 'ACTIVE' ? 'text-emerald-200' : 'text-rose-200'">
                      <i class="h-1.5 w-1.5" :class="account.status === 'ACTIVE' ? 'bg-emerald-400' : 'bg-rose-400'" />
                      {{ account.status === 'ACTIVE' ? '启用' : '停用' }}
                    </span>
                    <p class="mt-1 text-[10px] text-slate-600">{{ account.localLoginEnabled ? '本地登录可用' : '本地登录不可用' }}</p>
                  </td>
                  <td class="px-4 py-3 text-xs text-slate-400">{{ formatDate(account.lastLoginAt) }}</td>
                  <td class="px-4 py-3 text-right">
                    <button class="secondary-button focus-ring min-h-8 px-3 py-1 text-xs" type="button" @click="openEdit(account)">管理</button>
                  </td>
                </tr>
                <tr v-if="!loading && !accounts.items.length">
                  <td class="px-4 py-16 text-center text-sm text-slate-500" colspan="6">没有符合条件的账号</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="flex items-center justify-between border-t border-white/10 px-4 py-3 text-xs text-slate-500">
            <span>第 {{ accounts.page }} / {{ Math.max(accounts.totalPages, 1) }} 页</span>
            <div class="flex gap-2">
              <button class="icon-button focus-ring h-8 w-8" type="button" title="上一页" aria-label="上一页" :disabled="accounts.page <= 1" @click="changePage(accounts.page - 1)"><ChevronLeft class="h-4 w-4" /></button>
              <button class="icon-button focus-ring h-8 w-8" type="button" title="下一页" aria-label="下一页" :disabled="!accounts.hasNext" @click="changePage(accounts.page + 1)"><ChevronRight class="h-4 w-4" /></button>
            </div>
          </div>
        </div>

        <aside class="min-w-0 border border-white/10 bg-[#0c111b]">
          <div v-if="editorMode === 'empty'" class="flex h-full min-h-[420px] flex-col items-center justify-center px-8 text-center">
            <UserCog class="h-10 w-10 text-slate-600" aria-hidden="true" />
            <h2 class="mt-4 text-base font-semibold text-white">选择一个账号进行管理</h2>
            <p class="mt-2 max-w-xs text-sm leading-6 text-slate-500">可修改角色与数据域、启停账号或重置本地登录密码。</p>
          </div>

          <div v-else>
            <div class="flex items-center justify-between border-b border-white/10 px-4 py-3">
              <div>
                <h2 class="text-sm font-semibold text-white">{{ editorMode === 'create' ? '新建账号' : '账号配置' }}</h2>
                <p class="mt-1 text-xs text-slate-500">{{ editorMode === 'create' ? '创建身份、权限和登录凭据' : selectedAccount?.userId }}</p>
              </div>
              <button class="icon-button focus-ring h-8 w-8" type="button" title="关闭编辑面板" aria-label="关闭编辑面板" @click="closeEditor"><X class="h-4 w-4" /></button>
            </div>

            <form class="space-y-5 p-4" @submit.prevent="saveAccount">
              <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                <label class="text-xs text-slate-400">登录账号
                  <input v-model="form.username" class="focus-ring mt-2 h-10 w-full border border-white/10 bg-black/20 px-3 text-sm text-white disabled:text-slate-500" name="username" autocomplete="username" :disabled="editorMode === 'edit'" placeholder="例如 operator01" />
                </label>
                <label class="text-xs text-slate-400">显示名称
                  <input v-model="form.displayName" class="focus-ring mt-2 h-10 w-full border border-white/10 bg-black/20 px-3 text-sm text-white" placeholder="用户姓名或岗位" />
                </label>
                <label class="text-xs text-slate-400">主数据域
                  <input v-model="form.primaryRegionId" class="focus-ring mt-2 h-10 w-full border border-white/10 bg-black/20 px-3 font-mono text-sm text-white" placeholder="REGION-HZ" />
                </label>
                <label v-if="editorMode === 'create'" class="text-xs text-slate-400">初始密码
                  <input v-model="form.password" class="focus-ring mt-2 h-10 w-full border border-white/10 bg-black/20 px-3 text-sm text-white" type="password" autocomplete="new-password" placeholder="至少 10 位强密码" />
                </label>
                <label v-else class="text-xs text-slate-400">账号状态
                  <select v-model="form.status" class="focus-ring mt-2 h-10 w-full border border-white/10 bg-[#101620] px-3 text-sm text-white">
                    <option value="ACTIVE">启用</option>
                    <option value="DISABLED">停用并吊销会话</option>
                  </select>
                </label>
              </div>

              <fieldset class="border-t border-white/10 pt-4">
                <legend class="px-1 text-xs font-semibold text-slate-300">角色分配</legend>
                <div class="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                  <label v-for="role in options.roles" :key="role.roleCode" class="flex cursor-pointer gap-3 border border-white/10 bg-white/[0.025] p-3 transition-colors hover:bg-white/[0.05]">
                    <input class="mt-0.5 h-4 w-4 accent-cyan-400" type="checkbox" :checked="form.roleCodes.includes(role.roleCode)" @change="toggleRole(role.roleCode)" />
                    <span class="min-w-0">
                      <span class="block text-xs font-semibold text-white">{{ role.roleName }}</span>
                      <span class="mt-1 block font-mono text-[9px] text-slate-500">{{ role.roleCode }}</span>
                    </span>
                  </label>
                </div>
              </fieldset>

              <fieldset class="border-t border-white/10 pt-4">
                <legend class="px-1 text-xs font-semibold text-slate-300">数据域</legend>
                <p class="mt-2 text-[11px] leading-5 text-slate-500">多个值使用逗号或换行分隔，平台管理员可留空。</p>
                <div class="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                  <label class="text-xs text-slate-400">区域范围
                    <textarea v-model="form.regionScopes" class="focus-ring mt-2 min-h-20 w-full resize-y border border-white/10 bg-black/20 p-3 font-mono text-xs text-white" placeholder="REGION-HZ" />
                  </label>
                  <label class="text-xs text-slate-400">责任人范围
                    <textarea v-model="form.assigneeScopes" class="focus-ring mt-2 min-h-20 w-full resize-y border border-white/10 bg-black/20 p-3 font-mono text-xs text-white" placeholder="zhangsan" />
                  </label>
                  <label class="text-xs text-slate-400">数据视图
                    <textarea v-model="form.dataViewScopes" class="focus-ring mt-2 min-h-20 w-full resize-y border border-white/10 bg-black/20 p-3 font-mono text-xs text-white" placeholder="MASKED_FEATURE" />
                  </label>
                  <label class="text-xs text-slate-400">聚合区域
                    <textarea v-model="form.aggregateScopes" class="focus-ring mt-2 min-h-20 w-full resize-y border border-white/10 bg-black/20 p-3 font-mono text-xs text-white" placeholder="CITY-HZ" />
                  </label>
                </div>
              </fieldset>

              <p v-if="feedback" class="border border-emerald-300/20 bg-emerald-400/10 px-3 py-2.5 text-xs text-emerald-100" role="status">{{ feedback }}</p>
              <p v-if="errorMessage" class="border border-rose-300/20 bg-rose-400/10 px-3 py-2.5 text-xs text-rose-100" role="alert">{{ errorMessage }}</p>

              <button class="primary-button focus-ring w-full" type="submit" :disabled="saving">
                <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
                {{ editorMode === 'create' ? '创建账号' : '保存账号配置' }}
              </button>
            </form>

            <form v-if="editorMode === 'edit'" class="border-t border-white/10 p-4" @submit.prevent="resetPassword">
              <div class="flex items-center gap-2 text-xs font-semibold text-white"><KeyRound class="h-4 w-4 text-cyan-200" />重置登录密码</div>
              <input class="sr-only" name="username" autocomplete="username" :value="form.username" tabindex="-1" aria-hidden="true" readonly />
              <div class="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
                <input v-model="resetPasswordValue" class="focus-ring h-10 border border-white/10 bg-black/20 px-3 text-sm text-white" type="password" autocomplete="new-password" placeholder="输入新密码" />
                <input v-model="resetPasswordConfirm" class="focus-ring h-10 border border-white/10 bg-black/20 px-3 text-sm text-white" type="password" autocomplete="new-password" placeholder="再次确认" />
              </div>
              <button class="secondary-button focus-ring mt-3 w-full" type="submit" :disabled="saving || !resetPasswordValue">重置密码并注销旧会话</button>
            </form>
          </div>
        </aside>
      </section>
    </div>
  </main>
</template>
