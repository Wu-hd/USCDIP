import * as THREE from 'three';

function disposeMaterial(material: THREE.Material): void {
  const record = material as unknown as Record<string, unknown>;
  for (const value of Object.values(record)) {
    if (value instanceof THREE.Texture) {
      value.dispose();
    }
  }
  material.dispose();
}

export function disposeThreeObject(root: THREE.Object3D): void {
  root.traverse((object) => {
    const renderable = object as THREE.Object3D & {
      geometry?: THREE.BufferGeometry;
      material?: THREE.Material | THREE.Material[];
    };

    renderable.geometry?.dispose();
    if (Array.isArray(renderable.material)) {
      renderable.material.forEach(disposeMaterial);
    } else if (renderable.material) {
      disposeMaterial(renderable.material);
    }
  });
}

