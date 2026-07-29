import * as THREE from 'three';

import { disposeThreeObject } from '@/features/nationalMap/disposeThreeObject';
import type {
  ChinaFeatureCollection,
  ChinaGeoFeature,
  GeoMultiPolygonCoordinates,
  GeoPolygonCoordinates
} from '@/features/nationalMap/chinaMapTypes';

export const STARTUP_TIMELINE_MS = 10_000;

export type StartupStage =
  | 'assembling'
  | 'rotating'
  | 'focusing'
  | 'revealing'
  | 'ready'
  | 'leaving';

interface StartupGlobeSceneCallbacks {
  onReady(): void;
  onProgress(progress: number, stage: StartupStage): void;
  onComplete(): void;
  onError(message: string): void;
}

interface StartupGlobeSceneOptions {
  geoJson: ChinaFeatureCollection;
  callbacks: StartupGlobeSceneCallbacks;
}

interface ParticleBudget {
  globe: number;
  china: number;
  stars: number;
  pointSize: number;
}

const GLOBE_RADIUS = 2.55;
const CHINA_CENTER: [number, number] = [104.1954, 35.8617];
const BLUE = new THREE.Color('#38bdf8');
const GOLD = new THREE.Color('#f6c85f');

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function rangeProgress(value: number, start: number, end: number): number {
  return clamp01((value - start) / (end - start));
}

function easeInOutCubic(value: number): number {
  return value < 0.5
    ? 4 * value * value * value
    : 1 - Math.pow(-2 * value + 2, 3) / 2;
}

function easeOutCubic(value: number): number {
  return 1 - Math.pow(1 - value, 3);
}

function createSeededRandom(seed = 0x5f3759df): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

function coordinateToVector(
  longitude: number,
  latitude: number,
  radius = GLOBE_RADIUS
): THREE.Vector3 {
  const phi = THREE.MathUtils.degToRad(90 - latitude);
  const theta = THREE.MathUtils.degToRad(longitude + 180);
  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta)
  );
}

function featurePolygons(feature: ChinaGeoFeature): GeoPolygonCoordinates[] {
  if (feature.geometry.type === 'Polygon') {
    return [feature.geometry.coordinates as GeoPolygonCoordinates];
  }
  return feature.geometry.coordinates as GeoMultiPolygonCoordinates;
}

function stageForProgress(progress: number): StartupStage {
  if (progress < 0.15) return 'assembling';
  if (progress < 0.45) return 'rotating';
  if (progress < 0.65) return 'focusing';
  if (progress < 0.82) return 'revealing';
  if (progress < 0.94) return 'ready';
  return 'leaving';
}

export class StartupGlobeScene {
  private readonly host: HTMLElement;
  private readonly options: StartupGlobeSceneOptions;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(38, 1, 0.1, 80);
  private readonly renderer: THREE.WebGLRenderer;
  private readonly globeRoot = new THREE.Group();
  private readonly orbitRoot = new THREE.Group();
  private readonly startQuaternion = new THREE.Quaternion();
  private readonly midQuaternion = new THREE.Quaternion();
  private readonly targetQuaternion = new THREE.Quaternion();
  private readonly workingQuaternion = new THREE.Quaternion();
  private readonly resizeObserver: ResizeObserver;
  private readonly orbitMaterials: THREE.LineBasicMaterial[] = [];
  private globalMaterial: THREE.PointsMaterial | null = null;
  private chinaMaterial: THREE.PointsMaterial | null = null;
  private boundaryMaterial: THREE.LineBasicMaterial | null = null;
  private starMaterial: THREE.PointsMaterial | null = null;
  private globeGridMaterial: THREE.MeshBasicMaterial | null = null;
  private haloMaterial: THREE.SpriteMaterial | null = null;
  private floorMaterial: THREE.MeshBasicMaterial | null = null;
  private particleTexture: THREE.CanvasTexture | null = null;
  private frameId = 0;
  private startTime = 0;
  private initialized = false;
  private running = false;
  private disposed = false;
  private completionSent = false;
  private pageVisible = document.visibilityState !== 'hidden';

  constructor(host: HTMLElement, options: StartupGlobeSceneOptions) {
    this.host = host;
    this.options = options;
    this.renderer = new THREE.WebGLRenderer({
      antialias: false,
      alpha: true,
      powerPreference: 'high-performance'
    });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.12;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    this.renderer.domElement.className = 'startup-globe-canvas';
    this.renderer.domElement.setAttribute('aria-hidden', 'true');
    this.host.appendChild(this.renderer.domElement);

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.host);
    this.handleContextLost = this.handleContextLost.bind(this);
    this.handleVisibilityChange = this.handleVisibilityChange.bind(this);
    this.renderer.domElement.addEventListener('webglcontextlost', this.handleContextLost);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  initialize(): void {
    if (this.initialized || this.disposed) return;

    try {
      this.scene.background = null;
      this.scene.fog = new THREE.FogExp2('#020713', 0.026);
      this.camera.position.set(0, 0.15, 8.7);
      this.camera.lookAt(0, 0, 0);

      this.scene.add(this.globeRoot, this.orbitRoot);
      this.particleTexture = this.createParticleTexture();
      this.buildHalo();
      this.buildStarField();
      this.buildGlobe();
      this.buildChinaParticles();
      this.buildChinaBoundaries();
      this.buildOrbits();
      this.buildFloorRing();
      this.configureCameraPath();
      this.resize();
      this.renderer.render(this.scene, this.camera);
      this.initialized = true;
      this.options.callbacks.onReady();
    } catch (error) {
      this.options.callbacks.onError(
        error instanceof Error ? error.message : '粒子地球初始化失败'
      );
    }
  }

  start(elapsedOffsetMs = 0): void {
    if (!this.initialized || this.running || this.disposed) return;
    this.running = true;
    this.startTime = performance.now() - Math.min(
      Math.max(0, elapsedOffsetMs),
      STARTUP_TIMELINE_MS - 1_000
    );
    this.frameId = window.requestAnimationFrame(this.renderFrame);
  }

  skip(): void {
    this.complete();
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.running = false;
    window.cancelAnimationFrame(this.frameId);
    this.resizeObserver.disconnect();
    this.renderer.domElement.removeEventListener('webglcontextlost', this.handleContextLost);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    disposeThreeObject(this.scene);
    this.scene.clear();
    this.renderer.dispose();
    this.renderer.forceContextLoss();
    this.renderer.domElement.remove();
  }

  private particleBudget(): ParticleBudget {
    const compact = Math.min(this.host.clientWidth || window.innerWidth, window.innerWidth) < 768;
    return compact
      ? { globe: 8_000, china: 3_000, stars: 700, pointSize: 0.028 }
      : { globe: 16_000, china: 6_000, stars: 1_400, pointSize: 0.019 };
  }

  private createParticleTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    canvas.width = 64;
    canvas.height = 64;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('浏览器无法创建粒子纹理');
    const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 31);
    gradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
    gradient.addColorStop(0.32, 'rgba(255, 255, 255, 0.94)');
    gradient.addColorStop(0.68, 'rgba(255, 255, 255, 0.38)');
    gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 64, 64);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  private buildHalo(): void {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('浏览器无法创建粒子光晕');

    const gradient = context.createRadialGradient(128, 128, 24, 128, 128, 128);
    gradient.addColorStop(0, 'rgba(56, 189, 248, 0.16)');
    gradient.addColorStop(0.48, 'rgba(14, 116, 144, 0.10)');
    gradient.addColorStop(0.78, 'rgba(3, 105, 161, 0.035)');
    gradient.addColorStop(1, 'rgba(2, 7, 19, 0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 256, 256);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    this.haloMaterial = new THREE.SpriteMaterial({
      map: texture,
      color: '#7dd3fc',
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false
    });
    const halo = new THREE.Sprite(this.haloMaterial);
    halo.scale.set(8.5, 8.5, 1);
    halo.position.z = -0.7;
    this.scene.add(halo);
  }

  private buildStarField(): void {
    const { stars, pointSize } = this.particleBudget();
    const random = createSeededRandom(0x10c1f3);
    const positions = new Float32Array(stars * 3);
    const colors = new Float32Array(stars * 3);

    for (let index = 0; index < stars; index += 1) {
      const z = random() * 2 - 1;
      const angle = random() * Math.PI * 2;
      const radius = 7 + random() * 8;
      const radial = Math.sqrt(1 - z * z);
      positions[index * 3] = radius * radial * Math.cos(angle);
      positions[index * 3 + 1] = radius * z;
      positions[index * 3 + 2] = radius * radial * Math.sin(angle);
      const brightness = 0.42 + random() * 0.58;
      colors[index * 3] = 0.36 * brightness;
      colors[index * 3 + 1] = 0.77 * brightness;
      colors[index * 3 + 2] = brightness;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    this.starMaterial = new THREE.PointsMaterial({
      size: pointSize * 0.72,
      transparent: true,
      opacity: 0,
      vertexColors: true,
      map: this.particleTexture,
      alphaTest: 0.02,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true
    });
    this.scene.add(new THREE.Points(geometry, this.starMaterial));
  }

  private buildGlobe(): void {
    const { globe, pointSize } = this.particleBudget();
    const random = createSeededRandom(0x2196f3);
    const positions = new Float32Array(globe * 3);
    const colors = new Float32Array(globe * 3);
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));

    for (let index = 0; index < globe; index += 1) {
      const y = 1 - (index / (globe - 1)) * 2;
      const radial = Math.sqrt(1 - y * y);
      const angle = goldenAngle * index;
      const radius = GLOBE_RADIUS + (random() - 0.5) * 0.055;
      positions[index * 3] = Math.cos(angle) * radial * radius;
      positions[index * 3 + 1] = y * radius;
      positions[index * 3 + 2] = Math.sin(angle) * radial * radius;
      const brightness = 0.55 + random() * 0.45;
      colors[index * 3] = 0.06 * brightness;
      colors[index * 3 + 1] = 0.56 * brightness;
      colors[index * 3 + 2] = brightness;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    this.globalMaterial = new THREE.PointsMaterial({
      size: pointSize,
      transparent: true,
      opacity: 0,
      vertexColors: true,
      map: this.particleTexture,
      alphaTest: 0.02,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true
    });
    this.globeRoot.add(new THREE.Points(geometry, this.globalMaterial));

    this.globeGridMaterial = new THREE.MeshBasicMaterial({
      color: '#0e7490',
      wireframe: true,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    const grid = new THREE.Mesh(
      new THREE.SphereGeometry(GLOBE_RADIUS * 0.992, 36, 18),
      this.globeGridMaterial
    );
    this.globeRoot.add(grid);
  }

  private buildChinaParticles(): void {
    const { china, pointSize } = this.particleBudget();
    const canvas = this.createChinaMask();
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) throw new Error('浏览器无法读取中国地图粒子蒙版');
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    const random = createSeededRandom(0x86efac);
    const positions = new Float32Array(china * 3);
    let accepted = 0;
    let attempts = 0;
    const maxAttempts = china * 80;

    while (accepted < china && attempts < maxAttempts) {
      attempts += 1;
      const longitude = 72 + random() * 64;
      const latitude = 17 + random() * 38;
      const x = Math.min(
        canvas.width - 1,
        Math.max(0, Math.floor(((longitude + 180) / 360) * canvas.width))
      );
      const y = Math.min(
        canvas.height - 1,
        Math.max(0, Math.floor(((90 - latitude) / 180) * canvas.height))
      );
      if (pixels[(y * canvas.width + x) * 4 + 3] < 128) continue;

      const vector = coordinateToVector(
        longitude,
        latitude,
        GLOBE_RADIUS + 0.045 + random() * 0.025
      );
      positions[accepted * 3] = vector.x;
      positions[accepted * 3 + 1] = vector.y;
      positions[accepted * 3 + 2] = vector.z;
      accepted += 1;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
      'position',
      new THREE.BufferAttribute(
        accepted === china ? positions : positions.slice(0, accepted * 3),
        3
      )
    );
    this.chinaMaterial = new THREE.PointsMaterial({
      color: BLUE,
      size: pointSize * 1.42,
      transparent: true,
      opacity: 0,
      map: this.particleTexture,
      alphaTest: 0.02,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true
    });
    this.globeRoot.add(new THREE.Points(geometry, this.chinaMaterial));
  }

  private createChinaMask(): HTMLCanvasElement {
    const canvas = document.createElement('canvas');
    canvas.width = 720;
    canvas.height = 360;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('浏览器无法生成中国地图粒子蒙版');
    context.fillStyle = '#ffffff';

    for (const feature of this.options.geoJson.features) {
      if (String(feature.properties.adcode ?? '') === '100000_JD') continue;
      const path = new Path2D();
      for (const polygon of featurePolygons(feature)) {
        for (const ring of polygon) {
          ring.forEach((position, index) => {
            const x = ((position[0] + 180) / 360) * canvas.width;
            const y = ((90 - position[1]) / 180) * canvas.height;
            if (index === 0) path.moveTo(x, y);
            else path.lineTo(x, y);
          });
          path.closePath();
        }
      }
      context.fill(path, 'evenodd');
    }
    return canvas;
  }

  private buildChinaBoundaries(): void {
    const vertices: number[] = [];
    for (const feature of this.options.geoJson.features) {
      if (String(feature.properties.adcode ?? '') === '100000_JD') continue;
      for (const polygon of featurePolygons(feature)) {
        for (const ring of polygon) {
          if (ring.length < 2) continue;
          const step = Math.max(1, Math.ceil(ring.length / 180));
          for (let index = 0; index < ring.length; index += step) {
            const nextIndex = Math.min(index + step, ring.length - 1);
            const from = ring[index];
            const to = ring[nextIndex];
            const fromVector = coordinateToVector(from[0], from[1], GLOBE_RADIUS + 0.082);
            const toVector = coordinateToVector(to[0], to[1], GLOBE_RADIUS + 0.082);
            vertices.push(
              fromVector.x,
              fromVector.y,
              fromVector.z,
              toVector.x,
              toVector.y,
              toVector.z
            );
          }
        }
      }
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(vertices, 3));
    this.boundaryMaterial = new THREE.LineBasicMaterial({
      color: BLUE,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    this.globeRoot.add(new THREE.LineSegments(geometry, this.boundaryMaterial));
  }

  private buildOrbits(): void {
    const rotations: Array<[number, number, number]> = [
      [Math.PI / 2.9, 0.2, -0.32],
      [Math.PI / 2.2, -0.72, 0.48],
      [Math.PI / 1.75, 0.55, 1.1]
    ];

    rotations.forEach((rotation, index) => {
      const curve = new THREE.EllipseCurve(
        0,
        0,
        3.28 + index * 0.14,
        3.28 + index * 0.14,
        0,
        Math.PI * 2,
        false,
        0
      );
      const points = curve.getPoints(220).map(
        (point) => new THREE.Vector3(point.x, point.y, 0)
      );
      const geometry = new THREE.BufferGeometry().setFromPoints(points);
      const material = new THREE.LineBasicMaterial({
        color: index === 1 ? '#f6c85f' : '#38bdf8',
        transparent: true,
        opacity: 0,
        blending: THREE.AdditiveBlending,
        depthWrite: false
      });
      const line = new THREE.LineLoop(geometry, material);
      line.rotation.set(...rotation);
      this.orbitMaterials.push(material);
      this.orbitRoot.add(line);
    });
  }

  private buildFloorRing(): void {
    this.floorMaterial = new THREE.MeshBasicMaterial({
      color: '#38bdf8',
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    const floor = new THREE.Mesh(
      new THREE.RingGeometry(2.7, 3.9, 96, 1),
      this.floorMaterial
    );
    floor.rotation.x = -Math.PI / 2;
    floor.scale.y = 0.3;
    floor.position.y = -3.12;
    this.scene.add(floor);
  }

  private configureCameraPath(): void {
    this.startQuaternion.setFromEuler(new THREE.Euler(0.16, -2.35, -0.06));
    this.midQuaternion.setFromEuler(new THREE.Euler(-0.12, 2.05, 0.08));
    const chinaVector = coordinateToVector(...CHINA_CENTER).normalize();
    this.targetQuaternion.setFromUnitVectors(chinaVector, new THREE.Vector3(0, 0, 1));
    this.globeRoot.quaternion.copy(this.startQuaternion);
  }

  private updateScene(progress: number): void {
    const intro = easeOutCubic(rangeProgress(progress, 0, 0.15));
    const rotation = easeInOutCubic(rangeProgress(progress, 0.15, 0.45));
    const focus = easeInOutCubic(rangeProgress(progress, 0.45, 0.65));
    const reveal = easeOutCubic(rangeProgress(progress, 0.65, 0.82));
    const exit = easeInOutCubic(rangeProgress(progress, 0.94, 1));

    if (progress < 0.45) {
      this.workingQuaternion.copy(this.startQuaternion).slerp(this.midQuaternion, rotation);
    } else {
      this.workingQuaternion.copy(this.midQuaternion).slerp(this.targetQuaternion, focus);
    }
    this.globeRoot.quaternion.copy(this.workingQuaternion);
    this.globeRoot.scale.setScalar(0.84 + intro * 0.16 + focus * 0.045);
    this.globeRoot.position.y = reveal * 0.38;
    this.orbitRoot.rotation.y = progress * Math.PI * 0.72;
    this.orbitRoot.rotation.z = Math.sin(progress * Math.PI * 2) * 0.08;
    this.orbitRoot.position.y = reveal * 0.38;

    const focusDistance = THREE.MathUtils.lerp(8.7, 7.55, focus);
    this.camera.position.z = THREE.MathUtils.lerp(focusDistance, 8.2, reveal);
    this.camera.position.y = THREE.MathUtils.lerp(0.15, -0.02, focus);
    this.camera.lookAt(0, 0, 0);

    if (this.globalMaterial) {
      this.globalMaterial.opacity = (0.1 + intro * 0.72) * (1 - exit);
      this.globalMaterial.size = this.particleBudget().pointSize * (1 + focus * 0.18);
    }
    if (this.globeGridMaterial) {
      this.globeGridMaterial.opacity = (0.01 + intro * 0.035) * (1 - exit);
    }
    if (this.chinaMaterial) {
      this.chinaMaterial.opacity = (0.12 + intro * 0.18 + focus * 0.7) * (1 - exit);
      this.chinaMaterial.color.copy(BLUE).lerp(GOLD, focus);
      this.chinaMaterial.size = this.particleBudget().pointSize * (1.36 + reveal * 0.3);
    }
    if (this.boundaryMaterial) {
      this.boundaryMaterial.opacity = (0.05 + focus * 0.78) * (1 - exit);
      this.boundaryMaterial.color.copy(BLUE).lerp(GOLD, focus);
    }
    if (this.starMaterial) {
      this.starMaterial.opacity = intro * 0.72 * (1 - exit);
    }
    if (this.haloMaterial) {
      this.haloMaterial.opacity = (intro * 0.48 + focus * 0.18) * (1 - exit);
    }
    if (this.floorMaterial) {
      this.floorMaterial.opacity = intro * 0.08 * (1 - exit);
    }
    this.orbitMaterials.forEach((material, index) => {
      material.opacity = (intro * 0.1 + focus * (index === 1 ? 0.36 : 0.22)) * (1 - exit);
    });
  }

  private renderFrame = (timestamp: number): void => {
    if (!this.running || this.disposed || !this.pageVisible) return;
    const progress = clamp01((timestamp - this.startTime) / STARTUP_TIMELINE_MS);
    this.updateScene(progress);
    this.renderer.render(this.scene, this.camera);
    this.options.callbacks.onProgress(progress, stageForProgress(progress));

    if (progress >= 1) {
      this.complete();
      return;
    }
    this.frameId = window.requestAnimationFrame(this.renderFrame);
  };

  private resize(): void {
    const width = Math.max(1, this.host.clientWidth);
    const height = Math.max(1, this.host.clientHeight);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  private complete(): void {
    if (this.completionSent || this.disposed) return;
    this.completionSent = true;
    this.running = false;
    window.cancelAnimationFrame(this.frameId);
    this.options.callbacks.onComplete();
  }

  private handleContextLost(event: Event): void {
    event.preventDefault();
    this.running = false;
    window.cancelAnimationFrame(this.frameId);
    this.options.callbacks.onError('WebGL 上下文已丢失');
  }

  private handleVisibilityChange(): void {
    this.pageVisible = document.visibilityState !== 'hidden';
    if (!this.pageVisible) {
      window.cancelAnimationFrame(this.frameId);
      return;
    }
    if (this.running && !this.disposed) {
      this.frameId = window.requestAnimationFrame(this.renderFrame);
    }
  }
}
