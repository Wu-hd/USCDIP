import {
  MOCK_CITY_NODES,
  MOCK_FLY_LINES,
  MOCK_NATIONAL_SUMMARY,
  MOCK_PROVINCE_HEALTH_DATA
} from './mockNationalOverviewData';
import type {
  CityNodeData,
  FlyLineConfig,
  NationalOverviewDataAdapter,
  NationalSummary,
  ProvinceHealthData
} from './chinaMapTypes';

export class MockNationalOverviewDataAdapter implements NationalOverviewDataAdapter {
  async loadNationalSummary(): Promise<NationalSummary> {
    return { ...MOCK_NATIONAL_SUMMARY };
  }

  async loadProvinceStatistics(): Promise<ProvinceHealthData[]> {
    return MOCK_PROVINCE_HEALTH_DATA.map((item) => ({ ...item }));
  }

  async loadNetworkConnections(): Promise<FlyLineConfig[]> {
    return MOCK_FLY_LINES.map((item) => ({ ...item, from: [...item.from], to: [...item.to] }));
  }

  async loadCityNodes(): Promise<CityNodeData[]> {
    return MOCK_CITY_NODES.map((item) => ({ ...item, coordinate: [...item.coordinate] }));
  }
}

export const nationalOverviewDataAdapter: NationalOverviewDataAdapter =
  new MockNationalOverviewDataAdapter();

