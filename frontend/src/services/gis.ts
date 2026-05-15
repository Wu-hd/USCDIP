import { apiRequest } from '@/services/api';
import type {
  GisBboxQuery,
  GisObjectPickRequest,
  GisObjectPickResponse,
  GisObjectRecordResponse,
  PageResponse
} from '@/types/api';

export function queryGisBbox(query: GisBboxQuery) {
  const params = new URLSearchParams({
    minX: String(query.minX),
    minY: String(query.minY),
    maxX: String(query.maxX),
    maxY: String(query.maxY),
    authoritySrid: query.authoritySrid,
    displaySrid: query.displaySrid,
    page: String(query.page),
    pageSize: String(query.pageSize)
  });

  if (query.objectType) {
    params.set('objectType', query.objectType);
  }

  return apiRequest<PageResponse<GisObjectRecordResponse>>(
    `/api/gis/objects/bbox?${params.toString()}`
  );
}

export function pickGisObject(request: GisObjectPickRequest) {
  return apiRequest<GisObjectPickResponse>('/api/gis/objects/pick', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export function getGisObjectDetail(
  objectType: string,
  objectId: string,
  displaySrid = 'EPSG:4490'
) {
  const params = new URLSearchParams({ displaySrid });
  return apiRequest<GisObjectRecordResponse>(
    `/api/gis/objects/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}?${params.toString()}`
  );
}
