import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

import {
  MAP_EXTRUSION_DEPTH,
  NATIONAL_CAMERA,
  riskColor
} from './chinaMapConfig';
import {
  createGeoShape,
  featureCenter,
  featurePolygons,
  projectLngLat,
  ringToShapePoints
} from './geoProjection';
import { disposeThreeObject } from './disposeThreeObject';
import type {
  ChinaFeatureCollection,
  CityInteractionPayload,
  CityNodeData,
  FlyLineConfig,
  GisContextMarkerData,
  NationalMapLayerState,
  ProvinceHealthData,
  ProvinceInteractionPayload
} from './chinaMapTypes';

interface NationalMapSceneCallbacks {
  onProvinceHover(payload: ProvinceInteractionPayload | null): void;
  onProvinceSelect(province: ProvinceHealthData): void;
  onCityHover(payload: CityInteractionPayload | null): void;
  onCitySelect(city: CityNodeData): void;
  onContextLost(): void;
  onReady(): void;
}

export interface NationalMapSceneOptions {
  geoJson: ChinaFeatureCollection;
  provinces: ProvinceHealthData[];
  flyLines: FlyLineConfig[];
  cities: CityNodeData[];
  contextObject?: GisContextMarkerData | null;
  layers: NationalMapLayerState;
  autoRotate: boolean;
  selectedAdcode?: string | null;
  callbacks: NationalMapSceneCallbacks;
}

interface ProvinceVisual {
  group: THREE.Group;
  topMaterials: THREE.MeshStandardMaterial[];
  sideMaterials: THREE.MeshStandardMaterial[];
  province: ProvinceHealthData;
}

interface FlyLineVisual {
  curve: THREE.QuadraticBezierCurve3;
  marker: THREE.Mesh;
  progress: number;
  speed: number;
}

interface PulseVisual {
  mesh: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>;
  phase: number;
}

interface CameraTween {
  startAt: number;
  duration: number;
  fromPosition: THREE.Vector3;
  toPosition: THREE.Vector3;
  fromTarget: THREE.Vector3;
  toTarget: THREE.Vector3;
}

const EMPTY_PROVINCE: ProvinceHealthData = {
  adcode: '',
  provinceName: '',
  healthScore: 0,
  pipelineLengthKm: 0,
  onlineDeviceCount: 0,
  activeAlertCount: 0,
  openIncidentCount: 0,
  riskLevel: 'LOW'
};

export class NationalMapScene {
  private readonly host: HTMLElement;
  private readonly options: NationalMapSceneOptions;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(
    NATIONAL_CAMERA.fov,
    1,
    NATIONAL_CAMERA.near,
    NATIONAL_CAMERA.far
  );
  private readonly renderer: THREE.WebGLRenderer;
  private readonly controls: OrbitControls;
  private readonly raycaster = new THREE.Raycaster();
  private readonly pointer = new THREE.Vector2();
  private readonly mapRoot = new THREE.Group();
  private readonly provinceLayer = new THREE.Group();
  private readonly boundaryLayer = new THREE.Group();
  private readonly flyLineLayer = new THREE.Group();
  private readonly cityLayer = new THREE.Group();
  private readonly contextObjectLayer = new THREE.Group();
  private readonly scanLayer = new THREE.Group();
  private readonly provinceMeshes: THREE.Mesh[] = [];
  private readonly cityMeshes: THREE.Mesh[] = [];
  private readonly provinceVisuals = new Map<string, ProvinceVisual>();
  private readonly cityById = new Map<string, CityNodeData>();
  private readonly flyLineVisuals: FlyLineVisual[] = [];
  private readonly pulseVisuals: PulseVisual[] = [];
  private readonly resizeObserver: ResizeObserver;
  private readonly reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  private frameId = 0;
  private hoveredAdcode = '';
  private selectedAdcode = '';
  private disposed = false;
  private contextLost = false;
  private pageVisible = document.visibilityState !== 'hidden';
  private cameraTween: CameraTween | null = null;
  private previousFrameTime = performance.now();
  private elapsedTime = 0;

  constructor(host: HTMLElement, options: NationalMapSceneOptions) {
    this.host = host;
    this.options = options;
    this.selectedAdcode = options.selectedAdcode ?? '';

    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance'
    });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.domElement.className = 'h-full w-full touch-none';
    this.renderer.domElement.setAttribute('aria-label', '中国地下管网三维健康态势地图');
    this.host.appendChild(this.renderer.domElement);

    this.camera.position.set(...NATIONAL_CAMERA.position);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.07;
    this.controls.zoomSpeed = 0.55;
    this.controls.panSpeed = 0.65;
    this.controls.rotateSpeed = 0.48;
    this.controls.minDistance = NATIONAL_CAMERA.minDistance;
    this.controls.maxDistance = NATIONAL_CAMERA.maxDistance;
    this.controls.maxPolarAngle = NATIONAL_CAMERA.maxPolarAngle;
    this.controls.target.set(...NATIONAL_CAMERA.target);

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.host);

    this.handlePointerMove = this.handlePointerMove.bind(this);
    this.handlePointerLeave = this.handlePointerLeave.bind(this);
    this.handlePointerClick = this.handlePointerClick.bind(this);
    this.handleContextLost = this.handleContextLost.bind(this);
    this.handleVisibilityChange = this.handleVisibilityChange.bind(this);
    this.renderer.domElement.addEventListener('pointermove', this.handlePointerMove);
    this.renderer.domElement.addEventListener('pointerleave', this.handlePointerLeave);
    this.renderer.domElement.addEventListener('click', this.handlePointerClick);
    this.renderer.domElement.addEventListener('webglcontextlost', this.handleContextLost);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  initialize(): void {
    this.scene.background = null;
    this.scene.fog = new THREE.FogExp2('#050b14', 0.0028);
    this.mapRoot.rotation.x = -Math.PI / 2;
    this.scene.add(this.mapRoot);
    this.mapRoot.add(
      this.provinceLayer,
      this.boundaryLayer,
      this.flyLineLayer,
      this.cityLayer,
      this.contextObjectLayer,
      this.scanLayer
    );

    this.addLights();
    this.addGroundGrid();
    this.buildProvinceLayer();
    this.buildNationalBoundary();
    this.buildFlyLines();
    this.buildCities();
    this.buildContextObject();
    this.buildScanLight();
    this.applyLayerState(this.options.layers);
    this.setAutoRotate(this.options.autoRotate);
    this.updateProvinceStyles();
    this.resize();
    this.resetView(false);
    this.previousFrameTime = performance.now();
    this.renderFrame();
    this.options.callbacks.onReady();
  }

  private addLights(): void {
    this.scene.add(new THREE.HemisphereLight('#8ec5ff', '#030712', 2.4));
    const key = new THREE.DirectionalLight('#dbeafe', 3.2);
    key.position.set(-45, 90, 80);
    this.scene.add(key);
    const rim = new THREE.PointLight('#22d3ee', 28, 420, 2);
    rim.position.set(90, 65, -80);
    this.scene.add(rim);
  }

  private addGroundGrid(): void {
    const grid = new THREE.GridHelper(360, 18, '#0e7490', '#172033');
    grid.position.y = -2.2;
    const materials = Array.isArray(grid.material) ? grid.material : [grid.material];
    materials.forEach((material) => {
      material.transparent = true;
      material.opacity = 0.18;
    });
    this.scene.add(grid);
  }

  private buildProvinceLayer(): void {
    const provinceByAdcode = new Map(this.options.provinces.map((item) => [item.adcode, item]));
    for (const feature of this.options.geoJson.features) {
      const name = String(feature.properties.name ?? '').trim();
      const adcode = String(feature.properties.adcode ?? '');
      if (!name || !adcode || adcode === '100000_JD') continue;

      const province = provinceByAdcode.get(adcode) ?? {
        ...EMPTY_PROVINCE,
        adcode,
        provinceName: name
      };
      const visual: ProvinceVisual = {
        group: new THREE.Group(),
        topMaterials: [],
        sideMaterials: [],
        province
      };
      visual.group.name = `province-${adcode}`;
      const color = new THREE.Color(riskColor(province.riskLevel));

      for (const polygon of featurePolygons(feature)) {
        const shape = createGeoShape(polygon);
        if (!shape) continue;
        const geometry = new THREE.ExtrudeGeometry(shape, {
          depth: MAP_EXTRUSION_DEPTH,
          bevelEnabled: false,
          curveSegments: 1
        });
        geometry.computeVertexNormals();
        const topMaterial = new THREE.MeshStandardMaterial({
          color: color.clone().multiplyScalar(0.48),
          emissive: color.clone().multiplyScalar(0.08),
          emissiveIntensity: 0.7,
          metalness: 0.26,
          roughness: 0.52,
          transparent: true,
          opacity: 0.96
        });
        const sideMaterial = new THREE.MeshStandardMaterial({
          color: color.clone().multiplyScalar(0.2),
          emissive: color.clone().multiplyScalar(0.04),
          metalness: 0.42,
          roughness: 0.64,
          transparent: true,
          opacity: 0.95
        });
        const mesh = new THREE.Mesh(geometry, [topMaterial, sideMaterial]);
        mesh.userData = { kind: 'province', adcode };
        visual.group.add(mesh);
        this.provinceMeshes.push(mesh);
        visual.topMaterials.push(topMaterial);
        visual.sideMaterials.push(sideMaterial);

        const edges = new THREE.LineSegments(
          new THREE.EdgesGeometry(geometry, 24),
          new THREE.LineBasicMaterial({
            color: '#8be9ff',
            transparent: true,
            opacity: 0.42,
            blending: THREE.AdditiveBlending
          })
        );
        edges.position.z = 0.04;
        visual.group.add(edges);
      }

      const center = projectLngLat(...featureCenter(feature));
      visual.group.userData.center = new THREE.Vector3(center.x, center.y, MAP_EXTRUSION_DEPTH);
      this.provinceLayer.add(visual.group);
      this.provinceVisuals.set(adcode, visual);
    }
  }

  private buildNationalBoundary(): void {
    const outlineFeature = this.options.geoJson.features.find(
      (feature) => String(feature.properties.adcode ?? '') === '100000_JD'
    );
    const source = outlineFeature ? [outlineFeature] : this.options.geoJson.features;
    for (const feature of source) {
      for (const polygon of featurePolygons(feature)) {
        for (const ring of polygon) {
          const points = ringToShapePoints(ring).map(
            (point) => new THREE.Vector3(point.x, point.y, MAP_EXTRUSION_DEPTH + 0.5)
          );
          if (points.length < 2) continue;
          const geometry = new THREE.BufferGeometry().setFromPoints(points);
          const glow = new THREE.LineLoop(
            geometry,
            new THREE.LineBasicMaterial({
              color: '#67e8f9',
              transparent: true,
              opacity: 0.72,
              blending: THREE.AdditiveBlending,
              depthTest: false
            })
          );
          this.boundaryLayer.add(glow);
        }
      }
    }
  }

  private buildFlyLines(): void {
    this.options.flyLines.forEach((line, index) => {
      const start2 = projectLngLat(...line.from);
      const end2 = projectLngLat(...line.to);
      const start = new THREE.Vector3(start2.x, start2.y, MAP_EXTRUSION_DEPTH + 2.4);
      const end = new THREE.Vector3(end2.x, end2.y, MAP_EXTRUSION_DEPTH + 2.4);
      const distance = start.distanceTo(end);
      const middle = start.clone().lerp(end, 0.5);
      middle.z += Math.max(8, distance * 0.28);
      const curve = new THREE.QuadraticBezierCurve3(start, middle, end);
      const points = curve.getPoints(72);
      const color = riskColor(line.riskLevel);
      const path = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints(points),
        new THREE.LineBasicMaterial({
          color,
          transparent: true,
          opacity: 0.42,
          blending: THREE.AdditiveBlending,
          depthWrite: false
        })
      );
      this.flyLineLayer.add(path);

      const marker = new THREE.Mesh(
        new THREE.SphereGeometry(0.82, 14, 14),
        new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.95,
          blending: THREE.AdditiveBlending,
          depthWrite: false
        })
      );
      marker.position.copy(start);
      this.flyLineLayer.add(marker);
      this.flyLineVisuals.push({
        curve,
        marker,
        progress: index / Math.max(this.options.flyLines.length, 1),
        speed: 0.055 + line.volume / 2400
      });
    });
  }

  private buildCities(): void {
    for (const city of this.options.cities) {
      const projected = projectLngLat(...city.coordinate);
      const group = new THREE.Group();
      group.position.set(projected.x, projected.y, MAP_EXTRUSION_DEPTH + 1.6);
      const color = riskColor(city.riskLevel);
      const node = new THREE.Mesh(
        new THREE.SphereGeometry(1.25, 18, 18),
        new THREE.MeshStandardMaterial({
          color,
          emissive: color,
          emissiveIntensity: 1.7,
          roughness: 0.28
        })
      );
      node.userData = { kind: 'city', cityId: city.id };
      group.add(node);
      this.cityMeshes.push(node);
      this.cityById.set(city.id, city);

      const ringMaterial = new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.72,
        side: THREE.DoubleSide,
        blending: THREE.AdditiveBlending,
        depthWrite: false
      });
      const ring = new THREE.Mesh(new THREE.RingGeometry(1.7, 2.05, 40), ringMaterial);
      ring.position.z = -0.75;
      group.add(ring);
      this.pulseVisuals.push({ mesh: ring, phase: this.pulseVisuals.length * 0.64 });
      this.cityLayer.add(group);
    }
  }

  private buildContextObject(): void {
    const contextObject = this.options.contextObject;
    if (!contextObject) return;

    const projected = projectLngLat(...contextObject.coordinate);
    const group = new THREE.Group();
    group.name = `gis-context-${contextObject.objectType}-${contextObject.objectId}`;
    group.position.set(projected.x, projected.y, MAP_EXTRUSION_DEPTH + 1.2);

    const beaconMaterial = new THREE.MeshBasicMaterial({
      color: '#f8fafc',
      transparent: true,
      opacity: 0.96,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    const beacon = new THREE.Mesh(new THREE.OctahedronGeometry(1.7, 0), beaconMaterial);
    beacon.position.z = 8.5;
    group.add(beacon);

    const stem = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(0, 0, 8.5)
      ]),
      new THREE.LineBasicMaterial({
        color: '#f8fafc',
        transparent: true,
        opacity: 0.72,
        blending: THREE.AdditiveBlending,
        depthWrite: false
      })
    );
    group.add(stem);

    const ringMaterial = new THREE.MeshBasicMaterial({
      color: '#f8fafc',
      transparent: true,
      opacity: 0.9,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    const ring = new THREE.Mesh(new THREE.RingGeometry(2.4, 2.9, 48), ringMaterial);
    group.add(ring);
    this.pulseVisuals.push({ mesh: ring, phase: 0.18 });
    this.contextObjectLayer.add(group);
  }

  private buildScanLight(): void {
    const material = new THREE.MeshBasicMaterial({
      color: '#22d3ee',
      transparent: true,
      opacity: 0.22,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });
    const ring = new THREE.Mesh(new THREE.RingGeometry(22, 23.4, 96), material);
    ring.position.z = MAP_EXTRUSION_DEPTH + 0.8;
    this.scanLayer.add(ring);
  }

  private renderFrame = (frameTime = performance.now()): void => {
    if (this.disposed) return;
    this.frameId = window.requestAnimationFrame(this.renderFrame);
    if (!this.pageVisible) return;

    const delta = Math.min(Math.max((frameTime - this.previousFrameTime) / 1000, 0), 0.05);
    this.previousFrameTime = frameTime;
    this.elapsedTime += delta;
    const elapsed = this.elapsedTime;
    this.controls.update();
    this.updateCameraTween();

    if (!this.reducedMotionQuery.matches) {
      for (const visual of this.flyLineVisuals) {
        visual.progress = (visual.progress + delta * visual.speed) % 1;
        visual.marker.position.copy(visual.curve.getPointAt(visual.progress));
      }
      for (const pulse of this.pulseVisuals) {
        const progress = (elapsed * 0.55 + pulse.phase) % 1;
        pulse.mesh.scale.setScalar(1 + progress * 2.8);
        pulse.mesh.material.opacity = (1 - progress) * 0.68;
      }
      const scan = this.scanLayer.children[0] as THREE.Mesh | undefined;
      if (scan) {
        scan.rotation.z += delta * 0.16;
        const scale = 0.84 + ((elapsed * 0.16) % 1) * 3.2;
        scan.scale.setScalar(scale);
        const material = scan.material as THREE.MeshBasicMaterial;
        material.opacity = Math.max(0.04, 0.26 * (1 - ((elapsed * 0.16) % 1)));
      }
    }
    this.renderer.render(this.scene, this.camera);
  };

  private updateCameraTween(): void {
    if (!this.cameraTween) return;
    const elapsed = performance.now() - this.cameraTween.startAt;
    const raw = Math.min(1, elapsed / this.cameraTween.duration);
    const eased = 1 - Math.pow(1 - raw, 3);
    this.camera.position.lerpVectors(
      this.cameraTween.fromPosition,
      this.cameraTween.toPosition,
      eased
    );
    this.controls.target.lerpVectors(
      this.cameraTween.fromTarget,
      this.cameraTween.toTarget,
      eased
    );
    if (raw >= 1) this.cameraTween = null;
  }

  private startCameraTween(target: THREE.Vector3, distance: number, animate: boolean): void {
    const direction = new THREE.Vector3(0, 0.62, 1).normalize();
    const destination = target.clone().add(direction.multiplyScalar(distance));
    if (!animate || this.reducedMotionQuery.matches) {
      this.camera.position.copy(destination);
      this.controls.target.copy(target);
      this.controls.update();
      return;
    }
    this.cameraTween = {
      startAt: performance.now(),
      duration: 760,
      fromPosition: this.camera.position.clone(),
      toPosition: destination,
      fromTarget: this.controls.target.clone(),
      toTarget: target.clone()
    };
  }

  resetView(animate = true): void {
    const bounds = new THREE.Box3().setFromObject(this.provinceLayer);
    const center = bounds.getCenter(new THREE.Vector3());
    const size = bounds.getSize(new THREE.Vector3());
    const maxSize = Math.max(size.x, size.z, 120);
    const distance = Math.min(NATIONAL_CAMERA.maxDistance - 8, Math.max(154, maxSize * 1.42));
    this.startCameraTween(center, distance, animate);
  }

  focusProvince(adcode: string): void {
    const visual = this.provinceVisuals.get(adcode);
    if (!visual) return;
    const bounds = new THREE.Box3().setFromObject(visual.group);
    const center = bounds.getCenter(new THREE.Vector3());
    const size = bounds.getSize(new THREE.Vector3());
    const distance = Math.max(62, Math.max(size.x, size.z) * 2.4);
    this.startCameraTween(center, distance, true);
  }

  setSelectedProvince(adcode: string | null | undefined): void {
    this.selectedAdcode = adcode ?? '';
    this.updateProvinceStyles();
  }

  setAutoRotate(value: boolean): void {
    this.controls.autoRotate = value && !this.reducedMotionQuery.matches;
    this.controls.autoRotateSpeed = 0.48;
  }

  applyLayerState(layers: NationalMapLayerState): void {
    this.provinceLayer.visible = layers.provinces;
    this.boundaryLayer.visible = layers.boundaries;
    this.flyLineLayer.visible = layers.flyLines;
    this.cityLayer.visible = layers.cityNodes;
    this.scanLayer.visible = layers.scanLight;
  }

  private updateProvinceStyles(): void {
    for (const [adcode, visual] of this.provinceVisuals) {
      const selected = adcode === this.selectedAdcode;
      const hovered = adcode === this.hoveredAdcode;
      const base = new THREE.Color(riskColor(visual.province.riskLevel));
      const topColor = selected
        ? new THREE.Color('#fbbf24')
        : hovered
          ? base.clone().lerp(new THREE.Color('#e0f2fe'), 0.52)
          : base.clone().multiplyScalar(0.48);
      const sideColor = selected
        ? new THREE.Color('#92400e')
        : hovered
          ? base.clone().multiplyScalar(0.38)
          : base.clone().multiplyScalar(0.2);
      visual.topMaterials.forEach((material) => {
        material.color.copy(topColor);
        material.emissive.copy(selected || hovered ? topColor.clone().multiplyScalar(0.36) : base.clone().multiplyScalar(0.08));
        material.emissiveIntensity = selected ? 1.8 : hovered ? 1.25 : 0.7;
      });
      visual.sideMaterials.forEach((material) => material.color.copy(sideColor));
    }
  }

  private setPointer(event: MouseEvent): void {
    const rect = this.renderer.domElement.getBoundingClientRect();
    this.pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  }

  private pick(event: MouseEvent): THREE.Intersection | null {
    this.setPointer(event);
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const intersections = this.raycaster.intersectObjects(
      [...this.cityMeshes, ...this.provinceMeshes],
      false
    );
    return intersections[0] ?? null;
  }

  private handlePointerMove(event: PointerEvent): void {
    const hit = this.pick(event);
    const data = hit?.object.userData as { kind?: string; adcode?: string; cityId?: string } | undefined;
    const rect = this.renderer.domElement.getBoundingClientRect();
    const screenX = event.clientX - rect.left;
    const screenY = event.clientY - rect.top;

    if (data?.kind === 'city' && data.cityId) {
      const city = this.cityById.get(data.cityId);
      this.renderer.domElement.style.cursor = city ? 'pointer' : '';
      this.options.callbacks.onProvinceHover(null);
      this.hoveredAdcode = '';
      this.updateProvinceStyles();
      this.options.callbacks.onCityHover(city ? { city, screenX, screenY } : null);
      return;
    }

    this.options.callbacks.onCityHover(null);
    if (data?.kind === 'province' && data.adcode) {
      const visual = this.provinceVisuals.get(data.adcode);
      this.renderer.domElement.style.cursor = visual ? 'pointer' : '';
      this.hoveredAdcode = data.adcode;
      this.updateProvinceStyles();
      this.options.callbacks.onProvinceHover(
        visual ? { province: visual.province, screenX, screenY } : null
      );
      return;
    }

    this.renderer.domElement.style.cursor = '';
    this.hoveredAdcode = '';
    this.updateProvinceStyles();
    this.options.callbacks.onProvinceHover(null);
  }

  private handlePointerLeave(): void {
    this.renderer.domElement.style.cursor = '';
    this.hoveredAdcode = '';
    this.updateProvinceStyles();
    this.options.callbacks.onProvinceHover(null);
    this.options.callbacks.onCityHover(null);
  }

  private handlePointerClick(event: MouseEvent): void {
    const hit = this.pick(event);
    const data = hit?.object.userData as { kind?: string; adcode?: string; cityId?: string } | undefined;
    if (data?.kind === 'city' && data.cityId) {
      const city = this.cityById.get(data.cityId);
      if (city) this.options.callbacks.onCitySelect(city);
      return;
    }
    if (data?.kind === 'province' && data.adcode) {
      const visual = this.provinceVisuals.get(data.adcode);
      if (!visual) return;
      this.setSelectedProvince(data.adcode);
      this.focusProvince(data.adcode);
      this.options.callbacks.onProvinceSelect(visual.province);
    }
  }

  private handleContextLost(event: Event): void {
    event.preventDefault();
    this.contextLost = true;
    this.options.callbacks.onContextLost();
  }

  private handleVisibilityChange(): void {
    this.pageVisible = document.visibilityState !== 'hidden';
    if (this.pageVisible) this.previousFrameTime = performance.now();
  }

  private resize(): void {
    if (this.disposed) return;
    const width = Math.max(1, this.host.clientWidth);
    const height = Math.max(1, this.host.clientHeight);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    if (this.frameId) window.cancelAnimationFrame(this.frameId);
    this.frameId = 0;
    this.cameraTween = null;
    this.resizeObserver.disconnect();
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    this.renderer.domElement.removeEventListener('pointermove', this.handlePointerMove);
    this.renderer.domElement.removeEventListener('pointerleave', this.handlePointerLeave);
    this.renderer.domElement.removeEventListener('click', this.handlePointerClick);
    this.renderer.domElement.removeEventListener('webglcontextlost', this.handleContextLost);
    this.controls.dispose();
    disposeThreeObject(this.scene);
    this.scene.clear();
    this.renderer.renderLists.dispose();
    this.renderer.dispose();
    if (!this.contextLost) this.renderer.forceContextLoss();
    this.renderer.domElement.remove();
  }
}
