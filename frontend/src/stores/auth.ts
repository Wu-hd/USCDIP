import { defineStore } from 'pinia';

import {
  ApiClientError,
  addAuthSessionListener,
  clearTokenPair,
  getAccessToken,
  getAccessTokenRemainingMs
} from '@/services/api';
import { savePendingOidcState } from '@/services/auth';
import { getCurrentUser, getLoginDescriptor, logout } from '@/services/portal';
import type { AuthMePayload, AuthRefreshState } from '@/types/api';

type AuthStatus = 'checking' | 'authenticated' | 'anonymous' | 'oidc-disabled' | 'error';

interface AuthState {
  status: AuthStatus;
  user: AuthMePayload | null;
  message: string;
  traceId: string;
  isLoggingOut: boolean;
  refreshState: AuthRefreshState;
  authNotice: string;
  forceLogoutReason: string;
  sessionEventsBound: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    status: 'checking',
    user: null,
    message: '正在校验登录态',
    traceId: '',
    isLoggingOut: false,
    refreshState: 'idle',
    authNotice: '',
    forceLogoutReason: '',
    sessionEventsBound: false
  }),
  getters: {
    isAuthenticated: (state) => state.status === 'authenticated',
    displayName: (state) => {
      const snapshot = state.user?.snapshot ?? {};
      const username = snapshot.username ?? snapshot.userName ?? snapshot.name;
      return typeof username === 'string' && username ? username : state.user?.userId ?? '未登录';
    },
    accessTokenRemainingLabel: () => {
      const remaining = getAccessTokenRemainingMs();
      if (remaining === null) {
        return '未建立';
      }
      if (remaining <= 0) {
        return '已过期';
      }
      const minutes = Math.floor(remaining / 60000);
      const seconds = Math.floor((remaining % 60000) / 1000);
      if (minutes > 0) {
        return `${minutes} 分 ${seconds} 秒`;
      }
      return `${seconds} 秒`;
    }
  },
  actions: {
    bindSessionEvents() {
      if (this.sessionEventsBound) {
        return;
      }
      addAuthSessionListener((detail) => {
        this.refreshState = detail.refreshState;
        this.authNotice = detail.message;
        if (detail.traceId) {
          this.traceId = detail.traceId;
        }
        if (detail.type === 'force-logout') {
          this.forceLogoutReason = detail.code
            ? `${detail.code}: ${detail.message}`
            : detail.message;
          this.user = null;
          this.status = 'anonymous';
          this.message = '会话已失效，请重新登录';
        }
      });
      this.sessionEventsBound = true;
    },
    async loadCurrentUser() {
      this.bindSessionEvents();
      this.status = 'checking';
      this.message = getAccessToken() ? '正在读取当前用户' : '未检测到本地访问令牌';

      try {
        const response = await getCurrentUser();
        this.user = response.data;
        this.traceId = response.traceId;
        this.status = 'authenticated';
        this.message = '登录态有效';
        this.forceLogoutReason = '';
      } catch (error) {
        this.user = null;
        if (error instanceof ApiClientError) {
          this.traceId = error.traceId ?? '';
          if (error.code?.startsWith('TOKEN_REFRESH')) {
            this.refreshState = 'failed';
          }
          if (error.code === 'OIDC_DISABLED' || error.status === 503) {
            this.status = 'oidc-disabled';
            this.message = 'OIDC 当前关闭，待办需登录后查看';
            return;
          }
          if (error.status === 401) {
            this.status = 'anonymous';
            this.message = this.forceLogoutReason || '未登录或登录已失效';
            return;
          }
          this.status = 'error';
          this.message = error.message;
          return;
        }
        this.status = 'error';
        this.message = '登录态校验失败';
      }
    },
    async startLogin() {
      this.bindSessionEvents();
      const redirectUri = `${window.location.origin}/auth/callback`;
      const response = await getLoginDescriptor(redirectUri);
      const descriptor = response.data;

      if (!descriptor?.enabled || !descriptor.authorizationUrl) {
        this.status = 'oidc-disabled';
        this.traceId = response.traceId;
        this.message = 'OIDC 当前关闭，无法跳转统一登录';
        return;
      }

      savePendingOidcState(descriptor, redirectUri);
      window.location.assign(descriptor.authorizationUrl);
    },
    async logoutCurrentSession() {
      this.bindSessionEvents();
      this.isLoggingOut = true;
      try {
        const response = await logout();
        this.traceId = response.traceId;
        this.authNotice = '已调用统一登出';
      } catch (error) {
        if (error instanceof ApiClientError) {
          this.traceId = error.traceId ?? this.traceId;
          this.authNotice = error.status === 401 ? '后端会话已失效，本地已清理' : error.message;
        }
      } finally {
        clearTokenPair();
        this.user = null;
        this.status = 'anonymous';
        this.message = '已退出当前会话';
        this.refreshState = 'idle';
        this.forceLogoutReason = '';
        this.isLoggingOut = false;
      }
    }
  }
});
