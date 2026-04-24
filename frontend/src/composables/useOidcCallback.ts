import { computed, ref } from 'vue';
import type { LocationQuery, Router } from 'vue-router';

import { ApiClientError, saveTokenPair } from '@/services/api';
import {
  clearPendingOidcState,
  exchangeOidcCallback,
  getPendingOidcState,
  isOidcStateProcessed,
  markOidcStateProcessed
} from '@/services/auth';

type CallbackStatus = 'loading' | 'success' | 'error' | 'cancelled' | 'completed';
type StepStatus = 'waiting' | 'active' | 'done' | 'failed';

interface CallbackStep {
  key: string;
  label: string;
  detail: string;
  status: StepStatus;
}

const REDIRECT_DELAY_MS = 900;

function firstQueryValue(value: LocationQuery[string]): string {
  if (Array.isArray(value)) {
    return value[0] ?? '';
  }
  return value ?? '';
}

function createSteps(): CallbackStep[] {
  return [
    {
      key: 'parse',
      label: '解析回调参数',
      detail: '读取 code、state 与 Provider 返回状态',
      status: 'waiting'
    },
    {
      key: 'state',
      label: '校验 state',
      detail: '对比浏览器会话中的登录请求',
      status: 'waiting'
    },
    {
      key: 'exchange',
      label: '交换本地令牌',
      detail: '调用后端 OIDC callback 接口',
      status: 'waiting'
    },
    {
      key: 'session',
      label: '建立 Portal 会话',
      detail: '保存 token pair 并准备跳转 Portal',
      status: 'waiting'
    }
  ];
}

export function useOidcCallback(router: Router) {
  const status = ref<CallbackStatus>('loading');
  const title = ref('正在处理登录回调');
  const message = ref('正在解析认证中心返回的信息');
  const traceId = ref('');
  const providerError = ref('');
  const providerErrorDescription = ref('');
  const canRetry = ref(false);
  const isProcessing = ref(false);
  const steps = ref<CallbackStep[]>(createSteps());

  const isTerminalError = computed(() => status.value === 'error' || status.value === 'cancelled');

  function setStep(key: string, nextStatus: StepStatus) {
    const step = steps.value.find((item) => item.key === key);
    if (step) {
      step.status = nextStatus;
    }
  }

  function failAt(stepKey: string, nextTitle: string, nextMessage: string) {
    setStep(stepKey, 'failed');
    status.value = 'error';
    title.value = nextTitle;
    message.value = nextMessage;
    canRetry.value = stepKey === 'exchange';
    isProcessing.value = false;
  }

  function cancel(nextTitle: string, nextMessage: string) {
    setStep('parse', 'done');
    status.value = 'cancelled';
    title.value = nextTitle;
    message.value = nextMessage;
    canRetry.value = false;
    isProcessing.value = false;
  }

  async function processCallback(query: LocationQuery) {
    if (isProcessing.value) {
      return;
    }

    status.value = 'loading';
    title.value = '正在处理登录回调';
    message.value = '正在解析认证中心返回的信息';
    traceId.value = '';
    canRetry.value = false;
    isProcessing.value = true;
    steps.value = createSteps();

    setStep('parse', 'active');
    const code = firstQueryValue(query.code);
    const state = firstQueryValue(query.state);
    const error = firstQueryValue(query.error);
    const errorDescription = firstQueryValue(query.error_description);
    providerError.value = error;
    providerErrorDescription.value = errorDescription;

    if (error && !code) {
      cancel('登录未完成', errorDescription || `认证中心返回 ${error}`);
      return;
    }

    if (!code || !state) {
      failAt('parse', '回调参数不完整', '缺少 code 或 state，无法完成本地登录交换。');
      canRetry.value = false;
      return;
    }
    setStep('parse', 'done');

    if (isOidcStateProcessed(state)) {
      setStep('state', 'done');
      setStep('exchange', 'done');
      setStep('session', 'done');
      status.value = 'completed';
      title.value = '登录已完成';
      message.value = '该回调已处理过，避免重复交换令牌。';
      canRetry.value = false;
      isProcessing.value = false;
      return;
    }

    setStep('state', 'active');
    const pendingState = getPendingOidcState();
    if (pendingState && pendingState.state !== state) {
      failAt('state', 'state 校验失败', '回调 state 与本次浏览器会话中的登录请求不一致。');
      canRetry.value = false;
      return;
    }
    if (pendingState?.stateExpiresAt && new Date(pendingState.stateExpiresAt).getTime() < Date.now()) {
      failAt('state', 'state 已过期', '登录请求已超过有效期，请重新发起统一登录。');
      canRetry.value = false;
      return;
    }
    setStep('state', 'done');

    setStep('exchange', 'active');
    const redirectUri = pendingState?.redirectUri ?? `${window.location.origin}/auth/callback`;

    try {
      const response = await exchangeOidcCallback({
        code,
        state,
        redirectUri
      });

      if (!response.data) {
        failAt('exchange', '登录交换失败', '后端未返回 token pair。');
        return;
      }

      traceId.value = response.traceId;
      setStep('exchange', 'done');
      setStep('session', 'active');
      saveTokenPair(response.data);
      markOidcStateProcessed(state);
      if (pendingState?.state === state) {
        clearPendingOidcState();
      }
      setStep('session', 'done');

      status.value = 'success';
      title.value = '登录成功';
      message.value = '本地会话已建立，正在返回 Portal。';
      canRetry.value = false;
      isProcessing.value = false;

      window.setTimeout(() => {
        router.replace('/portal');
      }, REDIRECT_DELAY_MS);
    } catch (error) {
      if (error instanceof ApiClientError) {
        traceId.value = error.traceId ?? '';
        if (error.code === 'OIDC_STATE_INVALID') {
          failAt('state', 'state 校验失败', '后端拒绝了该 state，请重新发起统一登录。');
          canRetry.value = false;
          return;
        }
        if (error.code === 'OIDC_DISABLED') {
          failAt('exchange', 'OIDC 当前未启用', '后端未开启 OIDC 登录链路，请返回 Portal 或联系管理员。');
          canRetry.value = false;
          return;
        }
        failAt('exchange', '登录交换失败', error.message);
        return;
      }

      failAt('exchange', '登录交换失败', '认证交换过程中发生未知错误。');
    }
  }

  function retry(query: LocationQuery) {
    processCallback(query);
  }

  return {
    status,
    title,
    message,
    traceId,
    providerError,
    providerErrorDescription,
    canRetry,
    isProcessing,
    steps,
    isTerminalError,
    processCallback,
    retry
  };
}
