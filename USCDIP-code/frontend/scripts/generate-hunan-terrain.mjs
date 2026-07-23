import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

import { PNG } from 'pngjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const geoPath = path.join(root, 'src/assets/geo/hunan.json');
const outputPath = path.join(root, 'src/assets/geo/hunan-dem.bin');
const metaPath = path.join(root, 'src/assets/geo/hunan-dem.json');
const tileZoom = 9;
const outputSize = 513;
const tileSize = 256;
const sourceTemplate = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png';

const geoJson = JSON.parse(await readFile(geoPath, 'utf8'));
const positions = [];
for (const feature of geoJson.features ?? []) collectPositions(feature.geometry?.coordinates, positions);
if (!positions.length) throw new Error('Hunan GeoJSON contains no coordinates.');

const bounds = positions.reduce((result, [longitude, latitude]) => ({
  minLongitude: Math.min(result.minLongitude, longitude),
  minLatitude: Math.min(result.minLatitude, latitude),
  maxLongitude: Math.max(result.maxLongitude, longitude),
  maxLatitude: Math.max(result.maxLatitude, latitude)
}), {
  minLongitude: Number.POSITIVE_INFINITY,
  minLatitude: Number.POSITIVE_INFINITY,
  maxLongitude: Number.NEGATIVE_INFINITY,
  maxLatitude: Number.NEGATIVE_INFINITY
});

const topLeft = lngLatToTile(bounds.minLongitude, bounds.maxLatitude, tileZoom);
const bottomRight = lngLatToTile(bounds.maxLongitude, bounds.minLatitude, tileZoom);
const tileCoordinates = [];
for (let y = Math.floor(topLeft.y); y <= Math.floor(bottomRight.y); y += 1) {
  for (let x = Math.floor(topLeft.x); x <= Math.floor(bottomRight.x); x += 1) {
    tileCoordinates.push({ x, y });
  }
}

const tiles = new Map();
await runPool(tileCoordinates, 8, async ({ x, y }) => {
  const url = sourceTemplate.replace('{z}', tileZoom).replace('{x}', x).replace('{y}', y);
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Terrain tile failed: ${response.status} ${url}`);
  const png = PNG.sync.read(Buffer.from(await response.arrayBuffer()));
  tiles.set(`${x}/${y}`, png);
});

const elevations = new Float32Array(outputSize * outputSize);
let minimumElevation = Number.POSITIVE_INFINITY;
let maximumElevation = Number.NEGATIVE_INFINITY;
for (let row = 0; row < outputSize; row += 1) {
  const latitude = bounds.maxLatitude - (row / (outputSize - 1)) * (bounds.maxLatitude - bounds.minLatitude);
  for (let column = 0; column < outputSize; column += 1) {
    const longitude = bounds.minLongitude + (column / (outputSize - 1)) * (bounds.maxLongitude - bounds.minLongitude);
    const tile = lngLatToTile(longitude, latitude, tileZoom);
    const tileX = Math.floor(tile.x);
    const tileY = Math.floor(tile.y);
    const png = tiles.get(`${tileX}/${tileY}`);
    if (!png) throw new Error(`Missing downloaded terrain tile ${tileX}/${tileY}.`);
    const pixelX = clamp(Math.floor((tile.x - tileX) * tileSize), 0, tileSize - 1);
    const pixelY = clamp(Math.floor((tile.y - tileY) * tileSize), 0, tileSize - 1);
    const offset = (pixelY * png.width + pixelX) * 4;
    const elevation = png.data[offset] * 256 + png.data[offset + 1] + png.data[offset + 2] / 256 - 32768;
    elevations[row * outputSize + column] = elevation;
    minimumElevation = Math.min(minimumElevation, elevation);
    maximumElevation = Math.max(maximumElevation, elevation);
  }
}

const binary = Buffer.alloc(elevations.length * Float32Array.BYTES_PER_ELEMENT);
for (let index = 0; index < elevations.length; index += 1) {
  binary.writeFloatLE(elevations[index], index * Float32Array.BYTES_PER_ELEMENT);
}
await writeFile(outputPath, binary);
await writeFile(metaPath, `${JSON.stringify({
  width: outputSize,
  height: outputSize,
  bounds,
  minimumElevation,
  maximumElevation,
  tileZoom,
  source: 'AWS Open Data Terrain Tiles / Mapzen',
  sourceUrl: 'https://registry.opendata.aws/terrain-tiles/',
  generatedAt: new Date().toISOString()
}, null, 2)}\n`);

function collectPositions(value, result) {
  if (!Array.isArray(value)) return;
  if (value.length >= 2 && Number.isFinite(value[0]) && Number.isFinite(value[1])) {
    result.push([Number(value[0]), Number(value[1])]);
    return;
  }
  for (const item of value) collectPositions(item, result);
}

function lngLatToTile(longitude, latitude, zoom) {
  const scale = 2 ** zoom;
  const radians = latitude * Math.PI / 180;
  return {
    x: ((longitude + 180) / 360) * scale,
    y: ((1 - Math.log(Math.tan(radians) + 1 / Math.cos(radians)) / Math.PI) / 2) * scale
  };
}

async function runPool(items, concurrency, worker) {
  let nextIndex = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (nextIndex < items.length) {
      const item = items[nextIndex];
      nextIndex += 1;
      await worker(item);
    }
  }));
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, value));
}
