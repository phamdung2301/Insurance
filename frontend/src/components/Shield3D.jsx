import { useMemo, useRef, useState } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Sparkles } from '@react-three/drei';
import * as THREE from 'three';

/** Hình khiên 2D → extrude 3D */
function useShieldGeometry() {
  return useMemo(() => {
    const s = new THREE.Shape();
    s.moveTo(-1, 0.85);
    s.lineTo(1, 0.85);
    s.lineTo(1, 0.15);
    s.quadraticCurveTo(1, -0.45, 0, -1.05);
    s.quadraticCurveTo(-1, -0.45, -1, 0.15);
    s.lineTo(-1, 0.85);
    const geo = new THREE.ExtrudeGeometry(s, {
      depth: 0.28, bevelEnabled: true, bevelThickness: 0.09, bevelSize: 0.09, bevelSegments: 4, curveSegments: 24,
    });
    geo.center();
    return geo;
  }, []);
}

function ShieldMesh() {
  const ref = useRef();
  const geo = useShieldGeometry();
  useFrame((state, delta) => {
    const t = state.clock.elapsedTime;
    ref.current.rotation.y = Math.sin(t * 0.55) * 0.38;
    ref.current.rotation.x = Math.sin(t * 0.4) * 0.08;
    ref.current.position.y = Math.sin(t * 0.9) * 0.14;
    // parallax theo chuột
    ref.current.rotation.y += (state.pointer.x * 0.35 - ref.current.rotation.y) * Math.min(delta * 3, 1) * 0.4;
    ref.current.rotation.x += (-state.pointer.y * 0.2 - ref.current.rotation.x) * Math.min(delta * 3, 1) * 0.25;
  });
  return (
    <group ref={ref}>
      <mesh geometry={geo}>
        <meshStandardMaterial color="#1663d9" metalness={0.55} roughness={0.28} emissive="#0b2f6b" emissiveIntensity={0.4} />
      </mesh>
      {/* viền khiên */}
      <mesh geometry={geo} scale={1.001}>
        <meshStandardMaterial color="#eab308" metalness={0.9} roughness={0.25} wireframe transparent opacity={0.16} />
      </mesh>
      {/* dấu check nổi */}
      <mesh position={[0, 0.08, 0.32]} rotation={[0, 0, -0.15]}>
        <torusGeometry args={[0.34, 0.055, 16, 40, Math.PI * 1.55]} />
        <meshStandardMaterial color="#f5d67b" metalness={0.85} roughness={0.2} emissive="#b8860b" emissiveIntensity={0.5} />
      </mesh>
    </group>
  );
}

function OrbitRing({ radius = 1.9, speed = 0.5, color = '#eab308', tilt = 0.5 }) {
  const ref = useRef();
  useFrame((_, delta) => { ref.current.rotation.z += delta * speed; });
  return (
    <group rotation={[Math.PI / 2 - tilt, 0, 0]}>
      <mesh ref={ref}>
        <torusGeometry args={[radius, 0.014, 8, 90]} />
        <meshBasicMaterial color={color} transparent opacity={0.55} />
      </mesh>
    </group>
  );
}

/** Fallback CSS khi không có WebGL (máy cũ / prerender chụp ảnh) */
function ShieldFallback() {
  return (
    <div className="shield-fallback shield-glow" aria-hidden>
      <svg viewBox="0 0 100 110" width="100%" height="100%">
        <defs>
          <linearGradient id="shg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#2b7fff" />
            <stop offset="100%" stopColor="#0b3fa0" />
          </linearGradient>
        </defs>
        <path d="M50 4 L90 18 V52 C90 78 72 96 50 106 C28 96 10 78 10 52 V18 Z"
          fill="url(#shg)" stroke="#eab308" strokeWidth="3" />
        <path d="M35 52 L46 63 L67 40" fill="none" stroke="#f5d67b" strokeWidth="7"
          strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <style>{`.shield-fallback{width:100%;height:100%;animation:shieldFloat 5s ease-in-out infinite}
        @keyframes shieldFloat{0%,100%{transform:translateY(0) rotate(-2deg)}50%{transform:translateY(-12px) rotate(2deg)}}`}</style>
    </div>
  );
}

function hasWebGL() {
  try {
    const c = document.createElement('canvas');
    return !!(c.getContext('webgl') || c.getContext('experimental-webgl'));
  } catch { return false; }
}

/**
 * Khiên 3D trôi + xoay nhẹ, hạt sparkles, parallax theo chuột.
 * Tự fallback sang SVG tĩnh khi không có WebGL.
 */
export default function Shield3D({ height = 420, sparkles = true }) {
  const [ok] = useState(hasWebGL);
  return (
    <div style={{ width: '100%', height, position: 'relative' }}>
      {!ok ? (
        <ShieldFallback />
      ) : (
        <Canvas camera={{ position: [0, 0.2, 5.2], fov: 42 }} dpr={[1, 1.75]} gl={{ antialias: true, alpha: true }}>
          <ambientLight intensity={0.55} />
          <directionalLight position={[4, 5, 6]} intensity={1.4} color="#ffffff" />
          <pointLight position={[-4, -2, 3]} intensity={12} color="#2b7fff" />
          <pointLight position={[3, 3, -2]} intensity={8} color="#eab308" />
          <ShieldMesh />
          <OrbitRing radius={2.0} speed={0.45} color="#eab308" tilt={0.45} />
          <OrbitRing radius={2.45} speed={-0.3} color="#2b7fff" tilt={0.7} />
          {sparkles && <Sparkles count={70} scale={[5, 5, 2]} size={3.2} speed={0.35} color="#f5d67b" opacity={0.7} />}
          {sparkles && <Sparkles count={45} scale={[6, 6, 2]} size={2.4} speed={0.25} color="#2b7fff" opacity={0.55} />}
        </Canvas>
      )}
      {/* bóng đổ mềm dưới khiên */}
      <div style={{
        position: 'absolute', left: '50%', bottom: '6%', width: '46%', height: 26,
        transform: 'translateX(-50%)', borderRadius: '50%',
        background: 'radial-gradient(ellipse at center, rgba(16,185,129,0.35), transparent 70%)',
        filter: 'blur(6px)', pointerEvents: 'none',
      }} />
    </div>
  );
}
