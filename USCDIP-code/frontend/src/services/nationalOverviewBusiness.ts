import { getAlerts } from '@/services/alerts';
import { getDeviceLedgerDevices } from '@/services/deviceLedger';
import { getIncidents } from '@/services/incidents';
import { checkPermissions } from '@/services/permissions';
import { getWorkOrders } from '@/services/workorders';
import type {
  AlertRecordResponse,
  AuthMePayload,
  DeviceLedgerResponse,
  IncidentResponse,
  PageResponse,
  WorkOrderResponse
} from '@/types/api';

export type AuthorizedModuleStatus = 'locked' | 'ready' | 'empty' | 'error';

export interface AuthorizedModuleSnapshot<T> {
  status: AuthorizedModuleStatus;
  total: number | null;
  items: T[];
  message: string;
  traceId: string;
}

export interface AuthorizedBusinessOverview {
  devices: AuthorizedModuleSnapshot<DeviceLedgerResponse>;
  alerts: AuthorizedModuleSnapshot<AlertRecordResponse>;
  incidents: AuthorizedModuleSnapshot<IncidentResponse>;
  workOrders: AuthorizedModuleSnapshot<WorkOrderResponse>;
}

function lockedSnapshot<T>(message: string): AuthorizedModuleSnapshot<T> {
  return { status: 'locked', total: null, items: [], message, traceId: '' };
}

async function loadSnapshot<T>(
  allowed: boolean,
  lockedMessage: string,
  loader: () => Promise<{ data: PageResponse<T> | null; traceId: string }>
): Promise<AuthorizedModuleSnapshot<T>> {
  if (!allowed) return lockedSnapshot(lockedMessage);
  try {
    const response = await loader();
    const page = response.data;
    if (!page) {
      return { status: 'error', total: null, items: [], message: '接口未返回分页数据', traceId: response.traceId };
    }
    return {
      status: page.items.length ? 'ready' : 'empty',
      total: page.total,
      items: page.items,
      message: page.items.length ? '授权范围数据已更新' : '授权范围暂无数据',
      traceId: response.traceId
    };
  } catch (error) {
    return {
      status: 'error',
      total: null,
      items: [],
      message: error instanceof Error ? error.message : '业务数据加载失败',
      traceId: ''
    };
  }
}

export async function loadAuthorizedBusinessOverview(
  user: AuthMePayload | null
): Promise<AuthorizedBusinessOverview> {
  const mgmt = checkPermissions(user, ['ENTRY:MGMT', 'MENU:ASSET:READ'], '综合管理数据');
  const emgc = checkPermissions(user, ['ENTRY:EMGC', 'MENU:WORKORDER:READ'], '应急工单数据');
  const [devices, alerts, incidents, workOrders] = await Promise.all([
    loadSnapshot(mgmt.allowed, mgmt.reason, () => getDeviceLedgerDevices(1, 5)),
    loadSnapshot(mgmt.allowed, mgmt.reason, () => getAlerts(1, 5)),
    loadSnapshot(mgmt.allowed, mgmt.reason, () => getIncidents(1, 5, { status: 'OPEN' })),
    loadSnapshot(emgc.allowed, emgc.reason, () => getWorkOrders(1, 5))
  ]);
  return { devices, alerts, incidents, workOrders };
}

