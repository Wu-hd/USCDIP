export interface HunanTerrainMetadata {
  width: number;
  height: number;
  bounds: {
    minLongitude: number;
    minLatitude: number;
    maxLongitude: number;
    maxLatitude: number;
  };
  minimumElevation: number;
  maximumElevation: number;
  tileZoom: number;
  source: string;
  sourceUrl: string;
  generatedAt: string;
}

export interface HunanStationData {
  stationId: string;
  stationName: string;
  coordinate: [number, number];
  status: string;
}

export interface HunanMapLayerState {
  boundaries: boolean;
  labels: boolean;
  station: boolean;
}

export interface StationHoverPayload {
  station: HunanStationData;
  screenX: number;
  screenY: number;
}
