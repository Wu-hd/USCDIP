import { apiRequest } from '@/services/api';
import type { DataQualityScoreResponse, PageResponse } from '@/types/api';

export interface DataQualityScoreQuery {
  deviceId?: string;
  metricCode?: string;
  dqLevel?: string;
  minScore?: number;
  maxScore?: number;
  sourceBatchId?: string;
  isBackfill?: boolean;
  startTime?: string;
  endTime?: string;
}

export function getDataQualityScores(page = 1, pageSize = 100, query: DataQualityScoreQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });

  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });

  return apiRequest<PageResponse<DataQualityScoreResponse>>(`/api/dq/scores?${params.toString()}`);
}

export function getDataQualityScore(sourceRecordId: string) {
  return apiRequest<DataQualityScoreResponse>(
    `/api/dq/scores/${encodeURIComponent(sourceRecordId)}`
  );
}
