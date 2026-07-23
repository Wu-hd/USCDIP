import type { NationalMapLayerState, RiskLevel } from './chinaMapTypes';

export const CHINA_CENTER = {
  lng: 104.1954,
  lat: 35.8617
} as const;

export const MAP_EXTRUSION_DEPTH = 2.4;
export const MAP_WORLD_SCALE = 0.022;

export const RISK_COLORS: Record<RiskLevel, string> = {
  LOW: '#22c55e',
  MEDIUM: '#38bdf8',
  HIGH: '#f59e0b',
  CRITICAL: '#f43f5e'
};

export const RISK_LABELS: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '关注',
  HIGH: '高风险',
  CRITICAL: '严重'
};

export const DEFAULT_LAYER_STATE: NationalMapLayerState = {
  provinces: true,
  boundaries: true,
  flyLines: true,
  cityNodes: true,
  scanLight: true,
  labels: true
};

export const NATIONAL_CAMERA = {
  position: [0, -118, 156] as const,
  target: [0, 0, 0] as const,
  fov: 44,
  near: 0.1,
  far: 1200,
  minDistance: 72,
  maxDistance: 310,
  maxPolarAngle: Math.PI * 0.48
};

export function riskColor(level: RiskLevel): string {
  return RISK_COLORS[level];
}
