export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface NationalSummary {
  healthScore: number;
  coveredProvinceCount: number;
  pipelineLengthKm: number;
  onlineDeviceCount: number;
  activeAlertCount: number;
  openIncidentCount: number;
  activeWorkOrderCount: number;
  updatedAt: string;
}

export interface ProvinceHealthData {
  adcode: string;
  provinceName: string;
  healthScore: number;
  pipelineLengthKm: number;
  onlineDeviceCount: number;
  activeAlertCount: number;
  openIncidentCount: number;
  riskLevel: RiskLevel;
}

export interface FlyLineConfig {
  id: string;
  from: [number, number];
  to: [number, number];
  fromName: string;
  toName: string;
  riskLevel: RiskLevel;
  volume: number;
}

export interface CityNodeData {
  id: string;
  cityName: string;
  coordinate: [number, number];
  deviceCount: number;
  alertCount: number;
  healthScore: number;
  riskLevel: RiskLevel;
}

export interface GisContextMarkerData {
  objectType: string;
  objectId: string;
  objectName: string;
  coordinate: [number, number];
  sourceSrid: 'EPSG:4490';
}

export interface NationalOverviewDataAdapter {
  loadNationalSummary(): Promise<NationalSummary>;
  loadProvinceStatistics(): Promise<ProvinceHealthData[]>;
  loadNetworkConnections(): Promise<FlyLineConfig[]>;
  loadCityNodes(): Promise<CityNodeData[]>;
}

export type GeoPosition = [number, number] | [number, number, number];
export type GeoLinearRing = GeoPosition[];
export type GeoPolygonCoordinates = GeoLinearRing[];
export type GeoMultiPolygonCoordinates = GeoPolygonCoordinates[];

export interface ChinaGeoProperties {
  adcode?: number | string;
  name?: string;
  center?: [number, number];
  centroid?: [number, number];
  [key: string]: unknown;
}

export interface ChinaGeoFeature {
  type: 'Feature';
  properties: ChinaGeoProperties;
  geometry: {
    type: 'Polygon' | 'MultiPolygon';
    coordinates: GeoPolygonCoordinates | GeoMultiPolygonCoordinates;
  };
}

export interface ChinaFeatureCollection {
  type: 'FeatureCollection';
  features: ChinaGeoFeature[];
}

export interface ProvinceInteractionPayload {
  province: ProvinceHealthData;
  screenX: number;
  screenY: number;
}

export interface CityInteractionPayload {
  city: CityNodeData;
  screenX: number;
  screenY: number;
}

export interface NationalMapLayerState {
  provinces: boolean;
  boundaries: boolean;
  flyLines: boolean;
  cityNodes: boolean;
  scanLight: boolean;
  labels: boolean;
}
