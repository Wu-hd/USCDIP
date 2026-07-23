import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

import { disposeThreeObject } from '@/features/nationalMap/disposeThreeObject';
import type { ChinaFeatureCollection, ChinaGeoFeature, GeoPosition } from '@/features/nationalMap/chinaMapTypes';
import type {
  HunanMapLayerState,
  HunanStationData,
  HunanTerrainMetadata,
  StationHoverPayload
} from './hunanMapTypes';

interface HunanTerrainSceneCallbacks {
  onContextLost(): void;
  onReady(): void;
  onStationHover(payload: StationHoverPayload | null): void;
  onStationSelect(station: HunanStationData): void;
}

interface HunanTerrainSceneOptions {
  geoJson: ChinaFeatureCollection;
  elevations: Float32Array;
  metadata: HunanTerrainMetadata;
  station: HunanStationData | null;
  layers: HunanMapLayerState;
  exaggeration: number;
  callbacks: HunanTerrainSceneCallbacks;
}

const WORLD_WIDTH = 120;
const HEIGHT_UNIT = 0.004;
const ZHUZHOU_ADCODE = '430200';

export class HunanTerrainScene {
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(42, 1, 0.1, 600);
  private readonly renderer: THREE.WebGLRenderer;
  private readonly controls: OrbitControls;
  private readonly raycaster = new THREE.Raycaster();
  private readonly pointer = new THREE.Vector2();
  private readonly terrainLayer = new THREE.Group();
  private readonly boundaryLayer = new THREE.Group();
  private readonly labelLayer = new THREE.Group();
  private readonly stationLayer = new THREE.Group();
  private readonly stationMeshes: THREE.Object3D[] = [];
  private readonly resizeObserver: ResizeObserver;
  private readonly reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  private terrainGeometry: THREE.BufferGeometry | null = null;
  private baseTerrainElevations: Float32Array | null = null;
  private frameId = 0;
  private elapsed = 0;
  private previousFrameTime = performance.now();
  private disposed = false;
  private pageVisible = document.visibilityState !== 'hidden';
  private exaggeration: number;

  constructor(
    private readonly host: HTMLElement,
    private readonly options: HunanTerrainSceneOptions
  ) {
    this.exaggeration = options.exaggeration;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.domElement.className = 'h-full w-full touch-none';
    this.renderer.domElement.setAttribute('aria-label', '湖南省真实高程三维地图');
    this.host.appendChild(this.renderer.domElement);

    this.camera.position.set(0, 88, 102);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.07;
    this.controls.minDistance = 62;
    this.controls.maxDistance = 190;
    this.controls.maxPolarAngle = Math.PI * 0.47;
    this.controls.target.set(0, 3, 0);

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.host);
    this.handlePointerMove = this.handlePointerMove.bind(this);
    this.handlePointerLeave = this.handlePointerLeave.bind(this);
    this.handleClick = this.handleClick.bind(this);
    this.handleContextLost = this.handleContextLost.bind(this);
    this.handleVisibilityChange = this.handleVisibilityChange.bind(this);
    this.renderer.domElement.addEventListener('pointermove', this.handlePointerMove);
    this.renderer.domElement.addEventListener('pointerleave', this.handlePointerLeave);
    this.renderer.domElement.addEventListener('click', this.handleClick);
    this.renderer.domElement.addEventListener('webglcontextlost', this.handleContextLost);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  initialize(): void {
    this.scene.background = null;
    // Orbit zoom must not recolor the terrain by changing its fog distance.
    this.scene.fog = null;
    this.scene.add(this.terrainLayer, this.boundaryLayer, this.labelLayer, this.stationLayer);
    this.addLights();
    this.addGround();
    this.buildTerrain();
    this.rebuildDrapedLayers();
    this.applyLayerState(this.options.layers);
    this.resize();
    this.resetView();
    this.previousFrameTime = performance.now();
    this.renderFrame();
    this.options.callbacks.onReady();
  }

  resetView(): void {
    this.camera.position.set(0, 88, 102);
    this.controls.target.set(0, 3, 0);
    this.controls.update();
  }

  setExaggeration(value: number): void {
    this.exaggeration = Math.max(1, Math.min(3, value));
    if (this.terrainGeometry && this.baseTerrainElevations) {
      const positions = this.terrainGeometry.getAttribute('position') as THREE.BufferAttribute;
      for (let index = 0; index < this.baseTerrainElevations.length; index += 1) {
        positions.setY(index, this.toWorldHeight(this.baseTerrainElevations[index]));
      }
      positions.needsUpdate = true;
      this.terrainGeometry.computeVertexNormals();
    }
    this.rebuildDrapedLayers();
  }

  applyLayerState(layers: HunanMapLayerState): void {
    this.boundaryLayer.visible = layers.boundaries;
    this.labelLayer.visible = layers.labels;
    this.stationLayer.visible = layers.station;
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    cancelAnimationFrame(this.frameId);
    this.resizeObserver.disconnect();
    this.renderer.domElement.removeEventListener('pointermove', this.handlePointerMove);
    this.renderer.domElement.removeEventListener('pointerleave', this.handlePointerLeave);
    this.renderer.domElement.removeEventListener('click', this.handleClick);
    this.renderer.domElement.removeEventListener('webglcontextlost', this.handleContextLost);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    this.controls.dispose();
    disposeThreeObject(this.scene);
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }

  private addLights(): void {
    this.scene.add(new THREE.HemisphereLight('#b9e6ff', '#04100b', 2.25));
    const sun = new THREE.DirectionalLight('#fff1cf', 4.2);
    sun.position.set(-55, 92, 35);
    this.scene.add(sun);
    const rim = new THREE.DirectionalLight('#67e8f9', 1.9);
    rim.position.set(65, 36, -72);
    this.scene.add(rim);
  }

  private addGround(): void {
    const grid = new THREE.GridHelper(170, 20, '#164e63', '#0f2530');
    grid.position.y = -0.7;
    const materials = Array.isArray(grid.material) ? grid.material : [grid.material];
    materials.forEach((material) => {
      material.transparent = true;
      material.opacity = 0.2;
    });
    this.scene.add(grid);
  }

  private buildTerrain(): void {
    const segments = this.host.clientWidth < 700 ? 128 : 256;
    const columns = segments + 1;
    const rows = segments + 1;
    const aspect = this.worldAspect();
    const worldDepth = WORLD_WIDTH * aspect;
    const positions = new Float32Array(columns * rows * 3);
    const elevations = new Float32Array(columns * rows);
    const mask = this.createBoundaryMask(columns, rows);
    const indices: number[] = [];

    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        const index = row * columns + column;
        const u = column / segments;
        const v = row / segments;
        const elevation = Math.max(0, this.sampleElevationByUv(u, v));
        elevations[index] = elevation;
        positions[index * 3] = (u - 0.5) * WORLD_WIDTH;
        positions[index * 3 + 1] = this.toWorldHeight(elevation);
        positions[index * 3 + 2] = (v - 0.5) * worldDepth;
      }
    }

    for (let row = 0; row < segments; row += 1) {
      for (let column = 0; column < segments; column += 1) {
        const centerMask = mask[(row * 2 + 1) * (segments * 2) + column * 2 + 1];
        if (!centerMask) continue;
        const topLeft = row * columns + column;
        const topRight = topLeft + 1;
        const bottomLeft = (row + 1) * columns + column;
        const bottomRight = bottomLeft + 1;
        indices.push(topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight);
      }
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setIndex(indices);
    geometry.computeVertexNormals();
    const material = new THREE.MeshStandardMaterial({
      color: '#1f6f5d',
      emissive: '#062e2f',
      emissiveIntensity: 0.42,
      metalness: 0.08,
      roughness: 0.76,
      side: THREE.DoubleSide
    });
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = 'hunan-dem-terrain';
    this.terrainGeometry = geometry;
    this.baseTerrainElevations = elevations;
    this.terrainLayer.add(mesh);
  }

  private rebuildDrapedLayers(): void {
    disposeThreeObject(this.boundaryLayer);
    disposeThreeObject(this.labelLayer);
    disposeThreeObject(this.stationLayer);
    this.boundaryLayer.clear();
    this.labelLayer.clear();
    this.stationLayer.clear();
    this.stationMeshes.length = 0;
    this.buildCityBoundaries();
    this.buildCityLabels();
    this.buildStation();
  }

  private buildCityBoundaries(): void {
    for (const feature of this.options.geoJson.features) {
      const adcode = String(feature.properties.adcode ?? '');
      const color = adcode === ZHUZHOU_ADCODE ? '#fbbf24' : '#7dd3fc';
      for (const ring of featureOuterRings(feature)) {
        const points = ring.map(([longitude, latitude]) => {
          const projected = this.project(Number(longitude), Number(latitude));
          return new THREE.Vector3(projected.x, this.toWorldHeight(this.sampleElevation(Number(longitude), Number(latitude))) + 0.18, projected.z);
        });
        if (points.length < 2) continue;
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        this.boundaryLayer.add(new THREE.Line(geometry, new THREE.LineBasicMaterial({
          color,
          transparent: true,
          opacity: adcode === ZHUZHOU_ADCODE ? 1 : 0.58
        })));
      }
    }
  }

  private buildCityLabels(): void {
    for (const feature of this.options.geoJson.features) {
      const name = String(feature.properties.name ?? '');
      const center = feature.properties.centroid ?? feature.properties.center;
      if (!name || !center) continue;
      const longitude = Number(center[0]);
      const latitude = Number(center[1]);
      const projected = this.project(longitude, latitude);
      const sprite = this.createTextSprite(name, String(feature.properties.adcode ?? '') === ZHUZHOU_ADCODE);
      sprite.position.set(projected.x, this.toWorldHeight(this.sampleElevation(longitude, latitude)) + 2, projected.z);
      this.labelLayer.add(sprite);
    }
  }

  private buildStation(): void {
    const station = this.options.station;
    if (!station) return;
    const [longitude, latitude] = station.coordinate;
    const projected = this.project(longitude, latitude);
    const terrainHeight = this.toWorldHeight(this.sampleElevation(longitude, latitude));
    const group = new THREE.Group();
    group.position.set(projected.x, terrainHeight + 0.4, projected.z);
    group.userData.station = station;

    const marker = new THREE.Mesh(
      new THREE.SphereGeometry(1.05, 24, 16),
      new THREE.MeshStandardMaterial({ color: '#fbbf24', emissive: '#f59e0b', emissiveIntensity: 1.35, roughness: 0.3 })
    );
    marker.userData.station = station;
    marker.position.y = 1.8;
    const beam = new THREE.Mesh(
      new THREE.CylinderGeometry(0.12, 0.22, 4.2, 12),
      new THREE.MeshBasicMaterial({ color: '#67e8f9', transparent: true, opacity: 0.72 })
    );
    beam.userData.station = station;
    beam.position.y = 3.6;
    const pulse = new THREE.Mesh(
      new THREE.RingGeometry(1.25, 1.55, 40),
      new THREE.MeshBasicMaterial({ color: '#fbbf24', transparent: true, opacity: 0.8, side: THREE.DoubleSide })
    );
    pulse.rotation.x = -Math.PI / 2;
    pulse.userData.station = station;
    group.add(marker, beam, pulse);
    this.stationMeshes.push(marker, beam, pulse);
    this.stationLayer.add(group);

    const label = this.createTextSprite(station.stationName, true, 420);
    label.position.set(projected.x, terrainHeight + 8.2, projected.z);
    this.stationLayer.add(label);
  }

  private createBoundaryMask(width: number, height: number): Uint8Array {
    const canvas = document.createElement('canvas');
    canvas.width = (width - 1) * 2;
    canvas.height = (height - 1) * 2;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) return new Uint8Array(canvas.width * canvas.height).fill(1);
    context.fillStyle = '#fff';
    for (const feature of this.options.geoJson.features) {
      const path = new Path2D();
      for (const polygon of featurePolygons(feature)) {
        for (const ring of polygon) {
          ring.forEach(([longitude, latitude], index) => {
            const uv = this.lngLatToUv(Number(longitude), Number(latitude));
            const x = uv.u * canvas.width;
            const y = uv.v * canvas.height;
            if (index === 0) path.moveTo(x, y);
            else path.lineTo(x, y);
          });
          path.closePath();
        }
      }
      context.fill(path, 'evenodd');
    }
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    const mask = new Uint8Array(canvas.width * canvas.height);
    for (let index = 0; index < mask.length; index += 1) mask[index] = pixels[index * 4 + 3] > 0 ? 1 : 0;
    return mask;
  }

  private createTextSprite(text: string, emphasized: boolean, width = 180): THREE.Sprite {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = 64;
    const context = canvas.getContext('2d');
    if (context) {
      context.fillStyle = emphasized ? 'rgba(8, 15, 25, 0.92)' : 'rgba(8, 15, 25, 0.72)';
      context.fillRect(0, 8, canvas.width, 48);
      context.strokeStyle = emphasized ? '#fbbf24' : '#38bdf8';
      context.strokeRect(1, 9, canvas.width - 2, 46);
      context.font = emphasized ? '600 24px sans-serif' : '500 22px sans-serif';
      context.fillStyle = '#f8fafc';
      context.textAlign = 'center';
      context.textBaseline = 'middle';
      context.fillText(text, canvas.width / 2, canvas.height / 2);
    }
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true, depthTest: false }));
    sprite.scale.set(width / 24, 2.7, 1);
    return sprite;
  }

  private project(longitude: number, latitude: number): { x: number; z: number } {
    const uv = this.lngLatToUv(longitude, latitude);
    return {
      x: (uv.u - 0.5) * WORLD_WIDTH,
      z: (uv.v - 0.5) * WORLD_WIDTH * this.worldAspect()
    };
  }

  private lngLatToUv(longitude: number, latitude: number): { u: number; v: number } {
    const { bounds } = this.options.metadata;
    const minY = mercatorY(bounds.minLatitude);
    const maxY = mercatorY(bounds.maxLatitude);
    return {
      u: (longitude - bounds.minLongitude) / (bounds.maxLongitude - bounds.minLongitude),
      v: (maxY - mercatorY(latitude)) / (maxY - minY)
    };
  }

  private worldAspect(): number {
    const { bounds } = this.options.metadata;
    const xSpan = bounds.maxLongitude - bounds.minLongitude;
    const ySpan = (mercatorY(bounds.maxLatitude) - mercatorY(bounds.minLatitude)) * 180 / Math.PI;
    return ySpan / xSpan;
  }

  private sampleElevation(longitude: number, latitude: number): number {
    const uv = this.lngLatToUv(longitude, latitude);
    return this.sampleElevationByUv(uv.u, uv.v);
  }

  private sampleElevationByUv(u: number, v: number): number {
    const { width, height } = this.options.metadata;
    const x = Math.max(0, Math.min(width - 1, u * (width - 1)));
    const y = Math.max(0, Math.min(height - 1, v * (height - 1)));
    const x0 = Math.floor(x);
    const y0 = Math.floor(y);
    const x1 = Math.min(width - 1, x0 + 1);
    const y1 = Math.min(height - 1, y0 + 1);
    const tx = x - x0;
    const ty = y - y0;
    const top = lerp(this.options.elevations[y0 * width + x0], this.options.elevations[y0 * width + x1], tx);
    const bottom = lerp(this.options.elevations[y1 * width + x0], this.options.elevations[y1 * width + x1], tx);
    return Math.max(0, lerp(top, bottom, ty));
  }

  private toWorldHeight(elevation: number): number {
    return Math.max(0, elevation) * HEIGHT_UNIT * this.exaggeration;
  }

  private resize(): void {
    const width = Math.max(1, this.host.clientWidth);
    const height = Math.max(1, this.host.clientHeight);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  private pointerIntersections(event: PointerEvent): THREE.Intersection[] {
    const bounds = this.renderer.domElement.getBoundingClientRect();
    this.pointer.x = ((event.clientX - bounds.left) / bounds.width) * 2 - 1;
    this.pointer.y = -((event.clientY - bounds.top) / bounds.height) * 2 + 1;
    this.raycaster.setFromCamera(this.pointer, this.camera);
    return this.raycaster.intersectObjects(this.stationMeshes, true);
  }

  private handlePointerMove(event: PointerEvent): void {
    const hit = this.stationLayer.visible ? this.pointerIntersections(event)[0] : undefined;
    const station = hit?.object.userData.station as HunanStationData | undefined;
    this.renderer.domElement.style.cursor = station ? 'pointer' : '';
    this.options.callbacks.onStationHover(station ? {
      station,
      screenX: event.offsetX,
      screenY: event.offsetY
    } : null);
  }

  private handlePointerLeave(): void {
    this.renderer.domElement.style.cursor = '';
    this.options.callbacks.onStationHover(null);
  }

  private handleClick(event: MouseEvent): void {
    if (!this.stationLayer.visible) return;
    const hit = this.pointerIntersections(event as PointerEvent)[0];
    const station = hit?.object.userData.station as HunanStationData | undefined;
    if (station) this.options.callbacks.onStationSelect(station);
  }

  private handleContextLost(event: Event): void {
    event.preventDefault();
    this.options.callbacks.onContextLost();
  }

  private handleVisibilityChange(): void {
    this.pageVisible = document.visibilityState !== 'hidden';
    this.previousFrameTime = performance.now();
  }

  private renderFrame = (): void => {
    if (this.disposed) return;
    this.frameId = requestAnimationFrame(this.renderFrame);
    if (!this.pageVisible) return;
    const now = performance.now();
    const delta = Math.min(0.05, (now - this.previousFrameTime) / 1000);
    this.previousFrameTime = now;
    this.elapsed += delta;
    if (!this.reducedMotionQuery.matches && this.stationLayer.visible) {
      const scale = 1 + (Math.sin(this.elapsed * 3) + 1) * 0.22;
      this.stationLayer.children.forEach((child) => {
        const ring = child instanceof THREE.Group ? child.children[2] : null;
        if (ring) ring.scale.setScalar(scale);
      });
    }
    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  };
}

function featurePolygons(feature: ChinaGeoFeature): GeoPosition[][][] {
  return feature.geometry.type === 'Polygon'
    ? [feature.geometry.coordinates as GeoPosition[][]]
    : feature.geometry.coordinates as GeoPosition[][][];
}

function featureOuterRings(feature: ChinaGeoFeature): GeoPosition[][] {
  return featurePolygons(feature).map((polygon) => polygon[0] ?? []).filter((ring) => ring.length > 1);
}

function mercatorY(latitude: number): number {
  const radians = Math.max(-85, Math.min(85, latitude)) * Math.PI / 180;
  return Math.log(Math.tan(Math.PI / 4 + radians / 2));
}

function lerp(start: number, end: number, amount: number): number {
  return start + (end - start) * amount;
}
