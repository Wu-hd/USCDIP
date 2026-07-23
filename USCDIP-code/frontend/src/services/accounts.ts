import { apiRequest } from '@/services/api';
import type {
  AccountCreateRequest,
  AccountOptionsResponse,
  AccountPageResponse,
  AccountResponse,
  AccountUpdateRequest
} from '@/types/api';

export function getAccounts(params: {
  keyword?: string;
  status?: string;
  page?: number;
  pageSize?: number;
} = {}) {
  const search = new URLSearchParams();
  if (params.keyword) search.set('keyword', params.keyword);
  if (params.status) search.set('status', params.status);
  search.set('page', String(params.page ?? 1));
  search.set('pageSize', String(params.pageSize ?? 20));
  return apiRequest<AccountPageResponse>(`/api/auth/admin/accounts?${search.toString()}`);
}

export function getAccountOptions() {
  return apiRequest<AccountOptionsResponse>('/api/auth/admin/accounts/options');
}

export function createAccount(request: AccountCreateRequest) {
  return apiRequest<AccountResponse>('/api/auth/admin/accounts', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export function updateAccount(userId: string, request: AccountUpdateRequest) {
  return apiRequest<AccountResponse>(`/api/auth/admin/accounts/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  });
}

export function resetAccountPassword(userId: string, password: string) {
  return apiRequest<AccountResponse>(`/api/auth/admin/accounts/${encodeURIComponent(userId)}/password`, {
    method: 'POST',
    body: JSON.stringify({ password })
  });
}
