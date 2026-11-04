#!/usr/bin/env python3
"""
Generates Nooki's 3D desk-pet model as a self-contained binary glTF (.glb).

Pure Python 3 standard library, no Blender or numpy needed:

    python3 tools/pet-model/generate_nooki_glb.py
    # -> app/src/main/assets/models/nooki.glb

The model is built from soft "clay" ellipsoids arranged in a node hierarchy, so
every mood animation is a plain node (TRS) animation. No skinning is needed and
Filament/SceneView plays it directly.

Animation contract (NookiPet3D.kt looks these names up):
    Sleeping     resting pose, slow breathing, eyes closed, ears drooped
    Happy        bouncy idle, tail wag, ear wiggle, blinking
    Alert        head turns to camera with an inquisitive tilt, ears perked
    Overwhelmed  restless jitter, head shake, flattened ears, rapid blinks

Every animation drives the same set of channels, so switching clips never leaves
a node stuck in the previous clip's pose. If you swap in your own .glb, keep these
clip names (or add yours to the alias lists in NookiPet3D.kt).
"""

import json
import math
import os
import struct
import sys

# --------------------------------------------------------------------------------------
# Small math helpers
# --------------------------------------------------------------------------------------


def quat_from_euler(x_deg, y_deg, z_deg):
    """XYZ intrinsic euler (degrees) -> glTF quaternion [x, y, z, w]."""
    x, y, z = (math.radians(a) / 2.0 for a in (x_deg, y_deg, z_deg))
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    # q = qx * qy * qz
    qw = cx * cy * cz - sx * sy * sz
    qx = sx * cy * cz + cx * sy * sz
    qy = cx * sy * cz - sx * cy * sz
    qz = cx * cy * sz + sx * sy * cz
    return [qx, qy, qz, qw]


def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_color(h, alpha=1.0):
    h = h.lstrip("#")
    return [srgb_to_linear(int(h[i:i + 2], 16)) for i in (0, 2, 4)] + [alpha]


# --------------------------------------------------------------------------------------
# Geometry: UV ellipsoid with analytic normals
# --------------------------------------------------------------------------------------


def ellipsoid(rx, ry, rz, rings=24, segments=32, taper_top=0.0):
    """Returns (positions, normals, indices). taper_top > 0 narrows the top half (ear tips)."""
    pos, nrm, idx = [], [], []
    for r in range(rings + 1):
        v = r / rings
        theta = v * math.pi  # 0 = top, pi = bottom
        st, ct = math.sin(theta), math.cos(theta)
        taper = 1.0 - taper_top * max(0.0, ct) ** 1.5
        for s in range(segments + 1):
            u = s / segments
            phi = u * 2.0 * math.pi
            sp, cp = math.sin(phi), math.cos(phi)
            ux, uy, uz = st * cp, ct, st * sp
            px, py, pz = rx * ux * taper, ry * uy, rz * uz * taper
            pos.append((px, py, pz))
            # Normal of an ellipsoid: (x/rx^2, y/ry^2, z/rz^2), normalised.
            nx, ny, nz = px / (rx * rx * taper * taper + 1e-9), py / (ry * ry), pz / (rz * rz * taper * taper + 1e-9)
            ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
            nrm.append((nx / ln, ny / ln, nz / ln))
    row = segments + 1
    for r in range(rings):
        for s in range(segments):
            a = r * row + s
            b = a + row
            # Counter-clockwise winding when viewed from outside.
            idx += [a, a + 1, b, a + 1, b + 1, b]
    return pos, nrm, idx


# --------------------------------------------------------------------------------------
# glTF builder
# --------------------------------------------------------------------------------------


class GltfBuilder:
    def __init__(self):
        self.bin = bytearray()
        self.buffer_views = []
        self.accessors = []
        self.meshes = []
        self.materials = []
        self.nodes = []
        self.animations = []
        self._material_ids = {}

    def _align(self):
        while len(self.bin) % 4:
            self.bin += b"\x00"

    def _view(self, data, target=None):
        self._align()
        offset = len(self.bin)
        self.bin += data
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(data)}
        if target is not None:
            view["target"] = target
        self.buffer_views.append(view)
        return len(self.buffer_views) - 1

    def accessor_vec(self, values, kind, with_bounds=False, target=None):
        comps = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[kind]
        flat = []
        for v in values:
            flat.extend(v if isinstance(v, (list, tuple)) else [v])
        data = struct.pack("<%df" % len(flat), *flat)
        view = self._view(data, target)
        acc = {"bufferView": view, "componentType": 5126, "count": len(values), "type": kind}
        if with_bounds:
            cols = [[v[i] if comps > 1 else v for v in values] for i in range(comps)] if comps > 1 else [values]
            acc["min"] = [min(c) for c in cols]
            acc["max"] = [max(c) for c in cols]
        self.accessors.append(acc)
        return len(self.accessors) - 1

    def accessor_indices(self, indices):
        data = struct.pack("<%dH" % len(indices), *indices)
        view = self._view(data, target=34963)
        self.accessors.append(
            {"bufferView": view, "componentType": 5123, "count": len(indices), "type": "SCALAR"}
        )
        return len(self.accessors) - 1

    def material(self, name, color_hex, roughness=0.72, metallic=0.0, emissive=None):
        if name in self._material_ids:
            return self._material_ids[name]
        mat = {
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": hex_color(color_hex),
                "metallicFactor": metallic,
                "roughnessFactor": roughness,
            },
        }
        if emissive:
            mat["emissiveFactor"] = hex_color(emissive)[:3]
        self.materials.append(mat)
        self._material_ids[name] = len(self.materials) - 1
        return self._material_ids[name]

    def mesh(self, name, geometry, material):
        pos, nrm, idx = geometry
        p = self.accessor_vec(pos, "VEC3", with_bounds=True, target=34962)
        n = self.accessor_vec(nrm, "VEC3", target=34962)
        i = self.accessor_indices(idx)
        self.meshes.append(
            {"name": name, "primitives": [{"attributes": {"POSITION": p, "NORMAL": n}, "indices": i, "material": material}]}
        )
        return len(self.meshes) - 1

    def node(self, name, parent=None, mesh=None, t=None, r=None, s=None):
        node = {"name": name}
        if mesh is not None:
            node["mesh"] = mesh
        if t is not None:
            node["translation"] = list(t)
        if r is not None:
            node["rotation"] = list(r)
        if s is not None:
            node["scale"] = list(s)
        self.nodes.append(node)
        nid = len(self.nodes) - 1
        if parent is not None:
            self.nodes[parent].setdefault("children", []).append(nid)
        return nid

    def animation(self, name, duration, fps, tracks):
        """tracks: list of (node_id, path, fn(t) -> value)."""
        frames = int(round(duration * fps))
        times = [i / fps for i in range(frames + 1)]
        time_acc = self.accessor_vec(times, "SCALAR", with_bounds=True)
        samplers, channels = [], []
        for node_id, path, fn in tracks:
            values = [fn(t) for t in times]
            kind = "VEC4" if path == "rotation" else "VEC3"
            out_acc = self.accessor_vec(values, kind)
            samplers.append({"input": time_acc, "output": out_acc, "interpolation": "LINEAR"})
            channels.append({"sampler": len(samplers) - 1, "target": {"node": node_id, "path": path}})
        self.animations.append({"name": name, "samplers": samplers, "channels": channels})

    def write_glb(self, path, root_nodes):
        self._align()
        doc = {
            "asset": {"version": "2.0", "generator": "Nooki procedural pet generator"},
            "scene": 0,
            "scenes": [{"name": "NookiScene", "nodes": root_nodes}],
            "nodes": self.nodes,
            "meshes": self.meshes,
            "materials": self.materials,
            "accessors": self.accessors,
            "bufferViews": self.buffer_views,
            "buffers": [{"byteLength": len(self.bin)}],
            "animations": self.animations,
        }
        js = json.dumps(doc, separators=(",", ":")).encode("utf-8")
        while len(js) % 4:
            js += b" "
        total = 12 + 8 + len(js) + 8 + len(self.bin)
        with open(path, "wb") as f:
            f.write(struct.pack("<III", 0x46546C67, 2, total))
            f.write(struct.pack("<II", len(js), 0x4E4F534A))
            f.write(js)
            f.write(struct.pack("<II", len(self.bin), 0x004E4942))
            f.write(self.bin)
        return total


# --------------------------------------------------------------------------------------
# Nooki
# --------------------------------------------------------------------------------------

PALETTE = {
    "fur": "#EC8F2A",        # warm marigold
    "fur_shade": "#DE8A2C",  # ears / paws, a touch deeper
    "cream": "#FFE2BF",
    "ink": "#241B3A",        # eyes
    "shine": "#FFFFFF",
    "blush": "#F27C8A",
    "inner_ear": "#F6A7A0",
    "nose": "#7A3B2E",
    "bell": "#5CC8F2",       # the reminder bell, Nooki's signature
    "cushion": "#4A3A72",
    "cushion_top": "#5B4A8A",
}

# Rest pose constants (also used as the base values of every animation track).
BODY_T = (0.0, 0.40, 0.0)
HEAD_T = (0.0, 0.34, 0.03)
EAR_L_T = (-0.165, 0.17, -0.01)
EAR_R_T = (0.165, 0.17, -0.01)
EAR_SPLAY = 16.0  # degrees outward at rest
TAIL_T = (0.0, -0.14, -0.27)
BELL_T = (0.0, 0.17, 0.255)


def build():
    g = GltfBuilder()
    m = {k: g.material(k, v, roughness=0.78 if k not in ("ink", "shine", "bell") else 0.25,
                       metallic=0.35 if k == "bell" else 0.0)
         for k, v in PALETTE.items()}

    root = g.node("Nooki")

    # Desk cushion (static)
    cushion = g.node("Cushion", root, g.mesh("CushionMesh", ellipsoid(0.48, 0.07, 0.42), m["cushion"]), t=(0, 0.06, 0))
    g.node("CushionTop", cushion, g.mesh("CushionTopMesh", ellipsoid(0.40, 0.035, 0.35), m["cushion_top"]), t=(0, 0.045, 0))

    # Body
    body = g.node("Body", root, g.mesh("BodyMesh", ellipsoid(0.33, 0.29, 0.30), m["fur"]), t=BODY_T)
    g.node("Belly", body, g.mesh("BellyMesh", ellipsoid(0.175, 0.165, 0.09), m["cream"]), t=(0, -0.06, 0.225))
    paw = g.mesh("PawMesh", ellipsoid(0.085, 0.06, 0.10), m["fur_shade"])
    g.node("PawL", body, paw, t=(-0.15, -0.25, 0.17))
    g.node("PawR", body, paw, t=(0.15, -0.25, 0.17))

    tail = g.node("Tail", body, t=TAIL_T)
    g.node("TailMesh", tail, g.mesh("TailMeshGeo", ellipsoid(0.075, 0.17, 0.075, taper_top=0.35), m["fur_shade"]),
           t=(0.0, 0.13, -0.05), r=quat_from_euler(-28, 0, 0))

    bell = g.node("Bell", body, t=BELL_T)
    g.node("BellMesh", bell, g.mesh("BellGeo", ellipsoid(0.05, 0.05, 0.05), m["bell"]), t=(0, -0.04, 0.02))

    # Head
    head = g.node("Head", body, g.mesh("HeadMesh", ellipsoid(0.30, 0.255, 0.27), m["fur"]), t=HEAD_T)
    g.node("Muzzle", head, g.mesh("MuzzleMesh", ellipsoid(0.14, 0.085, 0.08), m["cream"]), t=(0, -0.08, 0.215))
    g.node("Nose", head, g.mesh("NoseMesh", ellipsoid(0.035, 0.025, 0.025), m["nose"]), t=(0, -0.045, 0.29))
    cheek = g.mesh("CheekMesh", ellipsoid(0.05, 0.03, 0.015), m["blush"])
    g.node("CheekL", head, cheek, t=(-0.19, -0.06, 0.205), r=quat_from_euler(0, -38, 0))
    g.node("CheekR", head, cheek, t=(0.19, -0.06, 0.205), r=quat_from_euler(0, 38, 0))

    eye_mesh = g.mesh("EyeMesh", ellipsoid(0.042, 0.055, 0.03), m["ink"])
    shine_mesh = g.mesh("ShineMesh", ellipsoid(0.013, 0.015, 0.008), m["shine"])
    eye_l = g.node("EyeL", head, eye_mesh, t=(-0.105, 0.02, 0.238), r=quat_from_euler(0, -22, 0))
    eye_r = g.node("EyeR", head, eye_mesh, t=(0.105, 0.02, 0.238), r=quat_from_euler(0, 22, 0))
    g.node("ShineL", eye_l, shine_mesh, t=(-0.012, 0.02, 0.026))
    g.node("ShineR", eye_r, shine_mesh, t=(-0.012, 0.02, 0.026))

    ear_mesh = g.mesh("EarMesh", ellipsoid(0.08, 0.15, 0.05, taper_top=0.55), m["fur_shade"])
    inner_mesh = g.mesh("InnerEarMesh", ellipsoid(0.045, 0.095, 0.02, taper_top=0.55), m["inner_ear"])
    ear_l = g.node("EarL", head, t=EAR_L_T)
    ear_r = g.node("EarR", head, t=EAR_R_T)
    for ear in (ear_l, ear_r):
        e = g.node("EarMesh", ear, ear_mesh, t=(0, 0.12, 0))
        g.node("InnerEar", e, inner_mesh, t=(0, -0.01, 0.035))

    animate(g, body=body, head=head, ear_l=ear_l, ear_r=ear_r, eye_l=eye_l, eye_r=eye_r, tail=tail, bell=bell)
    return g, root


def animate(g, body, head, ear_l, ear_r, eye_l, eye_r, tail, bell):
    two_pi = 2.0 * math.pi
    fps = 30

    def blink(t, period, at=0.15, width=0.12, closed=0.08):
        """1.0 open, dips to `closed` around phase `at` (fraction of period)."""
        phase = (t % period) / period
        d = abs(phase - at) / width
        return closed + (1.0 - closed) * min(1.0, d * d) if d < 1.0 else 1.0

    def tracks(body_t, body_r, body_s, head_r, head_s, ear_l_r, ear_r_r, eye_s, tail_r, bell_r):
        return [
            (body, "translation", body_t),
            (body, "rotation", lambda t: quat_from_euler(*body_r(t))),
            (body, "scale", body_s),
            (head, "rotation", lambda t: quat_from_euler(*head_r(t))),
            (head, "scale", head_s),
            (ear_l, "rotation", lambda t: quat_from_euler(*ear_l_r(t))),
            (ear_r, "rotation", lambda t: quat_from_euler(*ear_r_r(t))),
            (eye_l, "scale", eye_s),
            (eye_r, "scale", eye_s),
            (tail, "rotation", lambda t: quat_from_euler(*tail_r(t))),
            (bell, "rotation", lambda t: quat_from_euler(*bell_r(t))),
        ]

    # ---- Sleeping: 4s, one slow breath cycle --------------------------------------------
    D = 4.0

    def breath(t):
        return math.sin(two_pi * t / D)

    g.animation("Sleeping", D, fps, tracks(
        body_t=lambda t: (BODY_T[0], BODY_T[1] - 0.03 + 0.008 * breath(t), BODY_T[2]),
        body_r=lambda t: (4.0, 0.0, 0.0),
        body_s=lambda t: (1.04 - 0.012 * breath(t), 0.94 + 0.035 * breath(t), 1.04 - 0.012 * breath(t)),
        head_r=lambda t: (16.0 + 2.0 * breath(t), 0.0, 9.0),
        head_s=lambda t: (1.0, 1.0, 1.0),
        ear_l_r=lambda t: (18.0, 0.0, EAR_SPLAY + 34.0 + 2.0 * breath(t)),
        ear_r_r=lambda t: (18.0, 0.0, -(EAR_SPLAY + 34.0 + 2.0 * breath(t))),
        eye_s=lambda t: (1.15, 0.1, 1.0),
        tail_r=lambda t: (-20.0, 55.0, 0.0),
        bell_r=lambda t: (0.0, 0.0, 0.0),
    ))

    # ---- Happy: 1.2s, two bounces ----------------------------------------------------------
    D = 1.2

    def hop(t):
        return abs(math.sin(two_pi * t / D))  # two hops per loop

    def squash(t):
        # Squash when landing (hop near 0), stretch at the apex.
        return 1.0 + 0.10 * (hop(t) - 0.5)

    g.animation("Happy", D, fps, tracks(
        body_t=lambda t: (BODY_T[0], BODY_T[1] + 0.07 * hop(t), BODY_T[2]),
        body_r=lambda t: (0.0, 0.0, 4.0 * math.sin(two_pi * t / D)),
        body_s=lambda t: (1.0 / math.sqrt(squash(t)), squash(t), 1.0 / math.sqrt(squash(t))),
        head_r=lambda t: (-4.0, 6.0 * math.sin(two_pi * t / D), 8.0 * math.sin(two_pi * t / D)),
        head_s=lambda t: (1.0, 1.0, 1.0),
        ear_l_r=lambda t: (-6.0, 0.0, EAR_SPLAY + 10.0 * math.sin(2 * two_pi * t / D)),
        ear_r_r=lambda t: (-6.0, 0.0, -(EAR_SPLAY + 10.0 * math.sin(2 * two_pi * t / D + 1.2))),
        eye_s=lambda t: (1.0, blink(t, D, at=0.55, width=0.08), 1.0),
        tail_r=lambda t: (-10.0, 38.0 * math.sin(2 * two_pi * t / D), 0.0),
        bell_r=lambda t: (0.0, 0.0, 14.0 * math.sin(2 * two_pi * t / D + 0.6)),
    ))

    # ---- Alert: 3s, glance aside, snap to camera, curious tilt, hold ----------------------
    D = 3.0

    def ease(x):
        x = max(0.0, min(1.0, x))
        return x * x * (3 - 2 * x)

    def alert_yaw(t):
        # 0-0.6s look slightly away, 0.6-0.9s snap to camera, hold, 2.6-3.0 drift back.
        if t < 0.6:
            return -24.0 * ease(t / 0.6)
        if t < 0.95:
            return -24.0 + 24.0 * ease((t - 0.6) / 0.35)
        return 0.0  # holds eye contact, then the loop restarts with the glance away

    def alert_tilt(t):
        # Inquisitive head tilt that settles in after facing the camera, then releases.
        return 15.0 * ease((t - 0.9) / 0.45) * (1.0 - ease((t - 2.5) / 0.5))

    def perk(t):
        return 1.0 - 0.5 * (1.0 - ease((t - 0.6) / 0.3)) if t < 0.9 else 1.0

    g.animation("Alert", D, fps, tracks(
        body_t=lambda t: (BODY_T[0], BODY_T[1] + 0.012 * ease((t - 0.6) / 0.3) * (1 - ease((t - 2.6) / 0.4)), BODY_T[2]),
        body_r=lambda t: (-5.0, alert_yaw(t) * 0.25, 0.0),
        body_s=lambda t: (1.0, 1.0 + 0.02 * ease((t - 0.6) / 0.3), 1.0),
        head_r=lambda t: (-6.0, alert_yaw(t), alert_tilt(t)),
        head_s=lambda t: (1.0, 1.0, 1.0),
        ear_l_r=lambda t: (-14.0 * perk(t), 0.0, EAR_SPLAY - 8.0 * perk(t) + 3.0 * math.sin(two_pi * 3 * t / D)),
        ear_r_r=lambda t: (-14.0 * perk(t), 0.0, -(EAR_SPLAY - 8.0 * perk(t))),
        eye_s=lambda t: (1.18, 1.25 * blink(t, D, at=0.75, width=0.04), 1.0),
        tail_r=lambda t: (-35.0, 10.0 * math.sin(two_pi * t / D), 0.0),
        bell_r=lambda t: (0.0, 0.0, 22.0 * math.sin(two_pi * 4 * t / D) * math.exp(-1.2 * ((t - 0.75) % D))),
    ))

    # ---- Overwhelmed: 1.6s, jitter + head shake ---------------------------------------------
    D = 1.6

    def w(n, t, phase=0.0):
        return math.sin(two_pi * n * t / D + phase)

    g.animation("Overwhelmed", D, fps, tracks(
        body_t=lambda t: (0.012 * w(8, t), BODY_T[1] - 0.015 + 0.01 * abs(w(4, t)), BODY_T[2]),
        body_r=lambda t: (6.0, 0.0, 4.5 * w(8, t, 0.4)),
        body_s=lambda t: (1.02 + 0.015 * w(8, t), 0.97 - 0.015 * w(8, t), 1.02),
        head_r=lambda t: (8.0, 16.0 * w(2, t), 5.0 * w(4, t, 1.0)),
        head_s=lambda t: (1.0, 1.0, 1.0),
        ear_l_r=lambda t: (-55.0 + 6.0 * w(8, t), 0.0, EAR_SPLAY + 26.0),
        ear_r_r=lambda t: (-55.0 + 6.0 * w(8, t, 0.7), 0.0, -(EAR_SPLAY + 26.0)),
        eye_s=lambda t: (0.9, 0.85 * min(blink(t, D / 2, at=0.3, width=0.1), 1.0), 1.0),
        tail_r=lambda t: (-15.0, 60.0 * w(6, t), 0.0),
        bell_r=lambda t: (0.0, 0.0, 28.0 * w(10, t)),
    ))


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    repo = os.path.abspath(os.path.join(here, "..", ".."))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(repo, "app", "src", "main", "assets", "models", "nooki.glb")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    g, root = build()
    size = g.write_glb(out, [root])
    print(f"Wrote {out} ({size / 1024:.1f} KiB, {len(g.nodes)} nodes, {len(g.animations)} animations: "
          f"{', '.join(a['name'] for a in g.animations)})")


if __name__ == "__main__":
    main()
