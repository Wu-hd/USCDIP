import { apiRequest } from '@/services/api';
import type { DeviceLedgerResponse, PageResponse } from '@/types/api';

export interface DeviceLedgerQuery {
  status?: string;
  regionId?: string;
  segmentId?: string;
  nodeId?: string;
  facilityId?: string;
  protocolType?: string;
  calibrationExpired?: boolean;
}

export function getDeviceLedgerDevices(page = 1, pageSize = 50, query: DeviceLedgerQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });

  return apiRequest<PageResponse<DeviceLedgerResponse>>(`/api/device-ledger/devices?${params.toString()}`);
}

export function getDeviceLedgerDevice(deviceId: string) {
  return apiRequest<DeviceLedgerResponse>(
    `/api/device-ledger/devices/${encodeURIComponent(deviceId)}`
  );
}
