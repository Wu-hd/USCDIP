<script setup lang="ts">
import {
  ArrowRight,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  LockKeyhole,
  RefreshCcw,
  ShieldCheck,
  UserRound,
  Waves
} from 'lucide-vue-next';
import { storeToRefs } from 'pinia';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import loginBackgroundUrl from '@/assets/login-underground-city.png';
import { resolvePostLoginRoute } from '@/services/auth';
import { useAuthStore } from '@/stores/auth';

const CAPTCHA_CHARACTERS = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const REMEMBERED_ACCOUNT_KEY = 'uscdip.login.rememberedAccount';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const { status } = storeToRefs(authStore);

const username = ref('');
const password = ref('');
const captchaInput = ref('');
const captchaCode = ref(createCaptchaCode());
const rememberAccount = ref(true);
const passwordVisible = ref(false);
const loginLoading = ref(false);
const oidcLoading = ref(false);
const loginError = ref('');

const isBusy = computed(() => loginLoading.value || oidcLoading.value || status.value === 'checking');
const routeNotice = computed(() =>
  route.query.auth === 'required' ? '登录后即可继续访问所选业务模块。' : ''
);

function createCaptchaCode(): string {
  return Array.from({ length: 4 }, () =>
    CAPTCHA_CHARACTERS[Math.floor(Math.random() * CAPTCHA_CHARACTERS.length)]
  ).join('');
}

function refreshCaptcha(): void {
  captchaCode.value = createCaptchaCode();
  captchaInput.value = '';
}

function validateForm(): boolean {
  if (!username.value.trim()) {
    loginError.value = '请输入管理员账号';
    return false;
  }
  if (!password.value) {
    loginError.value = '请输入管理员密码';
    return false;
  }
  if (!captchaInput.value.trim()) {
    loginError.value = '请输入验证码';
    return false;
  }
  if (captchaInput.value.trim().toUpperCase() !== captchaCode.value) {
    loginError.value = '验证码不正确，请重新输入';
    refreshCaptcha();
    return false;
  }
  return true;
}

async function handleLogin(): Promise<void> {
  loginError.value = '';
  if (!validateForm()) return;

  loginLoading.value = true;
  try {
    const account = username.value.trim();
    const success = await authStore.emergencyLogin(account, password.value);
    if (!success) {
      loginError.value = authStore.message || '账号或密码错误';
      refreshCaptcha();
      return;
    }
    if (rememberAccount.value) {
      localStorage.setItem(REMEMBERED_ACCOUNT_KEY, account);
    } else {
      localStorage.removeItem(REMEMBERED_ACCOUNT_KEY);
    }
    password.value = '';
    await router.replace(resolvePostLoginRoute(route.query.redirect));
  } catch {
    loginError.value = '登录请求异常，请稍后重试';
    refreshCaptcha();
  } finally {
    loginLoading.value = false;
  }
}

async function startOidcLogin(): Promise<void> {
  loginError.value = '';
  oidcLoading.value = true;
  try {
    await authStore.startLogin(resolvePostLoginRoute(route.query.redirect));
    if (authStore.status === 'oidc-disabled' || authStore.status === 'error') {
      loginError.value = authStore.message;
    }
  } catch {
    loginError.value = '统一身份认证服务暂不可用，请使用管理员账号登录';
  } finally {
    oidcLoading.value = false;
  }
}

onMounted(() => {
  authStore.bindSessionEvents();
  username.value = localStorage.getItem(REMEMBERED_ACCOUNT_KEY) ?? '';
  void authStore.loadCurrentUser();
});
</script>

<template>
  <main class="login-shell app-viewport relative isolate overflow-hidden bg-[#020713] text-slate-100">
    <img
      class="absolute inset-0 -z-30 h-full w-full object-cover object-center"
      :src="loginBackgroundUrl"
      alt=""
      aria-hidden="true"
    />
    <div class="absolute inset-0 -z-20 bg-[linear-gradient(90deg,rgba(1,6,17,0.08)_0%,rgba(1,7,19,0.18)_43%,rgba(1,6,16,0.72)_68%,rgba(1,5,14,0.92)_100%)]" />
    <div class="absolute inset-0 -z-10 bg-[linear-gradient(180deg,rgba(2,7,19,0.38),transparent_28%,transparent_72%,rgba(1,5,14,0.72))]" />

    <div class="app-frame grid min-h-[100svh] items-center gap-10 px-5 py-8 sm:px-8 lg:grid-cols-[minmax(0,1.25fr)_minmax(390px,0.68fr)] lg:px-12 xl:gap-20 xl:px-20">
      <section class="flex min-h-[260px] flex-col justify-between self-stretch py-2 sm:min-h-[320px] lg:min-h-0 lg:py-8" aria-label="系统介绍">
        <div class="flex items-center gap-3">
          <span class="flex h-11 w-11 items-center justify-center border border-cyan-300/25 bg-cyan-400/10 text-cyan-100 shadow-[0_0_28px_rgba(34,211,238,0.16)] backdrop-blur-xl">
            <Waves class="h-6 w-6" aria-hidden="true" />
          </span>
          <div>
            <p class="font-display text-lg font-semibold text-white sm:text-xl">地下管网数字化健康监测中枢</p>
            <p class="mt-1 font-mono text-[10px] uppercase text-cyan-100/60">Underground Infrastructure Digital Twin</p>
          </div>
        </div>

        <div class="hidden max-w-xl lg:block">
          <div class="mb-5 h-px w-20 bg-cyan-300/70 shadow-[0_0_16px_rgba(103,232,249,0.65)]" />
          <h1 class="font-display text-3xl font-semibold leading-tight text-white xl:text-4xl">感知城市脉络<br />守护地下生命线</h1>
          <p class="mt-4 max-w-md text-sm leading-7 text-slate-300/75">
            汇聚管网资产、监测感知与调度模型，构建面向城市地下空间的数字化健康监测与协同处置能力。
          </p>
        </div>

        <div class="hidden items-center gap-5 text-[11px] text-slate-400 lg:flex">
          <span class="inline-flex items-center gap-2"><i class="h-1.5 w-1.5 bg-emerald-400 shadow-[0_0_10px_#34d399]" />实时感知</span>
          <span class="inline-flex items-center gap-2"><i class="h-1.5 w-1.5 bg-cyan-300 shadow-[0_0_10px_#67e8f9]" />智能研判</span>
          <span class="inline-flex items-center gap-2"><i class="h-1.5 w-1.5 bg-blue-400 shadow-[0_0_10px_#60a5fa]" />协同调度</span>
        </div>
      </section>

      <section class="w-full justify-self-end border border-white/15 bg-[#07111f]/68 p-5 shadow-[0_24px_90px_rgba(0,0,0,0.48),inset_0_1px_0_rgba(255,255,255,0.08)] backdrop-blur-2xl sm:p-8 lg:max-w-[460px] xl:p-10" aria-labelledby="login-title">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold tracking-[0.18em] text-cyan-200/75">SECURE ACCESS</p>
            <h2 id="login-title" class="mt-3 font-display text-2xl font-semibold text-white sm:text-3xl">欢迎登录系统</h2>
            <p class="mt-2 text-sm text-slate-400">请验证管理员身份后进入监测中枢</p>
          </div>
          <span class="flex h-10 w-10 shrink-0 items-center justify-center border border-emerald-300/20 bg-emerald-400/10 text-emerald-200">
            <ShieldCheck class="h-5 w-5" aria-hidden="true" />
          </span>
        </div>

        <div v-if="routeNotice" class="mt-5 border border-cyan-300/20 bg-cyan-400/10 px-3 py-2.5 text-xs text-cyan-100">
          {{ routeNotice }}
        </div>

        <form class="mt-7 space-y-5" @submit.prevent="handleLogin">
          <label class="block">
            <span class="mb-2 block text-sm font-medium text-slate-200">管理员账号</span>
            <span class="flex min-h-12 items-center border border-white/12 bg-white/[0.055] px-3 transition-colors focus-within:border-cyan-300/55 focus-within:bg-white/[0.075]">
              <UserRound class="h-4 w-4 shrink-0 text-cyan-200/75" aria-hidden="true" />
              <input
                v-model="username"
                class="min-w-0 flex-1 bg-transparent px-3 py-3 text-sm text-white outline-none placeholder:text-slate-500"
                type="text"
                autocomplete="username"
                placeholder="请输入管理员账号"
                :disabled="isBusy"
              />
            </span>
          </label>

          <label class="block">
            <span class="mb-2 block text-sm font-medium text-slate-200">管理员密码</span>
            <span class="flex min-h-12 items-center border border-white/12 bg-white/[0.055] px-3 transition-colors focus-within:border-cyan-300/55 focus-within:bg-white/[0.075]">
              <LockKeyhole class="h-4 w-4 shrink-0 text-cyan-200/75" aria-hidden="true" />
              <input
                v-model="password"
                class="min-w-0 flex-1 bg-transparent px-3 py-3 text-sm text-white outline-none placeholder:text-slate-500"
                :type="passwordVisible ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入管理员密码"
                :disabled="isBusy"
              />
              <button class="focus-ring p-1 text-slate-400 hover:text-white" type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" @click="passwordVisible = !passwordVisible">
                <EyeOff v-if="passwordVisible" class="h-4 w-4" aria-hidden="true" />
                <Eye v-else class="h-4 w-4" aria-hidden="true" />
              </button>
            </span>
          </label>

          <label class="block">
            <span class="mb-2 block text-sm font-medium text-slate-200">验证码</span>
            <span class="grid grid-cols-[minmax(0,1fr)_116px] gap-3">
              <span class="flex min-h-12 items-center border border-white/12 bg-white/[0.055] px-3 transition-colors focus-within:border-cyan-300/55">
                <KeyRound class="h-4 w-4 shrink-0 text-cyan-200/75" aria-hidden="true" />
                <input
                  v-model="captchaInput"
                  class="min-w-0 flex-1 bg-transparent px-3 py-3 font-mono text-sm uppercase text-white outline-none placeholder:normal-case placeholder:text-slate-500"
                  type="text"
                  inputmode="text"
                  maxlength="4"
                  autocomplete="off"
                  placeholder="输入验证码"
                  :disabled="isBusy"
                />
              </span>
              <button class="focus-ring group flex min-h-12 items-center justify-between border border-cyan-300/20 bg-cyan-400/10 px-3" type="button" aria-label="刷新验证码" @click="refreshCaptcha">
                <span class="font-mono text-base font-semibold tracking-[0.18em] text-cyan-100">{{ captchaCode }}</span>
                <RefreshCcw class="h-3.5 w-3.5 text-cyan-300/70 transition-transform group-hover:rotate-90" aria-hidden="true" />
              </button>
            </span>
          </label>

          <label class="flex cursor-pointer items-center gap-2 text-xs text-slate-400">
            <input v-model="rememberAccount" class="h-4 w-4 accent-emerald-400" type="checkbox" />
            记住管理员账号
          </label>

          <p v-if="loginError" class="border border-rose-300/25 bg-rose-400/10 px-3 py-2.5 text-sm text-rose-100" role="alert" aria-live="polite">
            {{ loginError }}
          </p>

          <button class="focus-ring flex min-h-12 w-full items-center justify-center gap-2 bg-blue-600 px-4 text-sm font-semibold text-white shadow-[0_0_30px_rgba(37,99,235,0.28)] transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60" type="submit" :disabled="isBusy">
            <Loader2 v-if="loginLoading" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <span>{{ loginLoading ? '正在登录' : '立即登录' }}</span>
            <ArrowRight v-if="!loginLoading" class="h-4 w-4" aria-hidden="true" />
          </button>

          <button class="focus-ring flex min-h-11 w-full items-center justify-center gap-2 border border-white/12 bg-white/[0.04] px-4 text-sm font-medium text-slate-200 transition-colors hover:border-cyan-300/30 hover:bg-white/[0.07] disabled:cursor-not-allowed disabled:opacity-60" type="button" :disabled="isBusy" @click="startOidcLogin">
            <Loader2 v-if="oidcLoading" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <KeyRound v-else class="h-4 w-4 text-cyan-200" aria-hidden="true" />
            统一身份认证
          </button>
        </form>

        <div class="mt-7 flex items-center justify-between border-t border-white/10 pt-4 text-[10px] text-slate-500">
          <span>USCDIP COMMAND CENTER</span>
          <span class="inline-flex items-center gap-1.5"><i class="h-1.5 w-1.5 bg-emerald-400 shadow-[0_0_8px_#34d399]" />安全连接</span>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.login-shell::after {
  position: absolute;
  inset: 0;
  z-index: -5;
  background: linear-gradient(105deg, transparent 20%, rgba(56, 189, 248, 0.035) 44%, transparent 66%);
  content: '';
  pointer-events: none;
  animation: login-sheen 12s ease-in-out infinite alternate;
}

@keyframes login-sheen {
  from { transform: translateX(-8%); }
  to { transform: translateX(8%); }
}

@media (prefers-reduced-motion: reduce) {
  .login-shell::after { animation: none; }
}
</style>
