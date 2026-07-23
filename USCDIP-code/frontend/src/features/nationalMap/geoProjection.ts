import * as THREE from 'three';

import { CHINA_CENTER, MAP_WORLD_SCALE } from './chinaMapConfig';
import type {
  ChinaGeoFeature,
  GeoMultiPolygonCoordinates,
  GeoPolygonCoordinates,
  GeoPosition
} from './chinaMapTypes';

const EARTH_RADIUS_KM = 6371;
const MAX_MERCATOR_LATITUDE = 85.05112878;

function toRadians(value: number): number {
  return (value * Math.PI) / 180;
}

function mercatorY(latitude: number): number {
  const clamped = Math.max(-MAX_MERCATOR_LATITUDE, Math.min(MAX_MERCATOR_LATITUDE, latitude));
  const radians = toRadians(clamped);
  return EARTH_RADIUS_KM * Math.log(Math.tan(Math.PI / 4 + radians / 2));
}

const centerX = EARTH_RADIUS_KM * toRadians(CHINA_CENTER.lng);
const centerY = mercatorY(CHINA_CENTER.lat);

export function projectLngLat(longitude: number, latitude: number): THREE.Vector2 {
  const x = (EARTH_RADIUS_KM * toRadians(longitude) - centerX) * MAP_WORLD_SCALE;
  const y = (mercatorY(latitude) - centerY) * MAP_WORLD_SCALE;
  return new THREE.Vector2(x, y);
}

export function projectPosition(position: GeoPosition): THREE.Vector2 {
  return projectLngLat(Number(position[0]), Number(position[1]));
}

export function featurePolygons(feature: ChinaGeoFeature): GeoPolygonCoordinates[] {
  if (feature.geometry.type === 'Polygon') {
    return [feature.geometry.coordinates as GeoPolygonCoordinates];
  }
  return feature.geometry.coordinates as GeoMultiPolygonCoordinates;
}

export function featureCenter(feature: ChinaGeoFeature): [number, number] {
  const preferred = feature.properties.centroid ?? feature.properties.center;
  if (preferred && Number.isFinite(preferred[0]) && Number.isFinite(preferred[1])) {
    return [preferred[0], preferred[1]];
  }

  let longitudeTotal = 0;
  let latitudeTotal = 0;
  let pointCount = 0;
  for (const polygon of featurePolygons(feature)) {
    const outerRing = polygon[0] ?? [];
    for (const point of outerRing) {
      longitudeTotal += Number(point[0]);
      latitudeTotal += Number(point[1]);
      pointCount += 1;
    }
  }

  return pointCount > 0
    ? [longitudeTotal / pointCount, latitudeTotal / pointCount]
    : [CHINA_CENTER.lng, CHINA_CENTER.lat];
}

export function ringToShapePoints(ring: GeoPosition[]): THREE.Vector2[] {
  const points = ring.map(projectPosition);
  if (points.length > 1 && points[0].distanceToSquared(points[points.length - 1]) < 1e-8) {
    points.pop();
  }
  return points;
}

export function createGeoShape(polygon: GeoPolygonCoordinates): THREE.Shape | null {
  const outer = ringToShapePoints(polygon[0] ?? []);
  if (outer.length < 3) return null;

  const shape = new THREE.Shape(outer);
  for (const holeRing of polygon.slice(1)) {
    const hole = ringToShapePoints(holeRing);
    if (hole.length >= 3) {
      shape.holes.push(new THREE.Path(hole));
    }
  }
  return shape;
}

