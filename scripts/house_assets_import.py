#!/usr/bin/env python3
"""Converte un sottoinsieme dei modelli CC0 `Cozy Home Interiors` (3dassets.dev)
in FBX importabili da Unity 2022.3.

I GLB del pack usano KHR_mesh_quantization: assimp li interpreta male e produce
verticali/bounds enormi. Qui i vertici vengono de-quantizzati (SHORT normalizzato
/32767), trasformati secondo i nodi glTF ed esportati come OBJ+MTL; assimp converte
poi OBJ→FBX. Nessuna texture: i materiali sono solo colori PBR.

Fonte: https://3dassets.dev/packs/cozy-home-interiors (CC0 1.0).
Uso: python3 scripts/house_assets_import.py [cartella_pack] [destinazione]
"""
import json
import os
import struct
import subprocess
import sys
import uuid

import numpy as np

# Nome Unity -> slug del pack (sottoinsieme scelto: una casa completa, non 129 modelli).
MODELS = {
    "WallStraight2m": "cozy-home-interiors-wall-straight-2m-c96dba78",
    "WallStraight3m": "cozy-home-interiors-wall-straight-3m-761deb7a",
    "WallDoor2m": "cozy-home-interiors-wall-door-2m-cb4fc378",
    "WallFrontDoor2m": "cozy-home-interiors-wall-front-door-2m-067c8287",
    "WallWindow2m": "cozy-home-interiors-wall-window-2m-c5997f2f",
    "WallHalfPartition": "cozy-home-interiors-wall-half-partition-1m-f928ae3a",
    "FloorWood2m": "cozy-home-interiors-floor-wood-2m-91f6fc28",
    "FloorTile2m": "cozy-home-interiors-floor-tile-2m-f92b8556",
    "FloorCarpet2m": "cozy-home-interiors-floor-carpet-2m-772f3eae",
    "CeilingDeck2m": "cozy-home-interiors-ceiling-deck-2m-38748268",
    "StairFlight": "cozy-home-interiors-stair-flight-200d2331",
    "StairLandingRail": "cozy-home-interiors-stair-landing-rail-1m-2b3d9a34",
    "SofaThreeSeat": "cozy-home-interiors-sofa-three-seat-8d2d33f9",
    "Armchair": "cozy-home-interiors-armchair-53de7ce5",
    "CoffeeTable": "cozy-home-interiors-coffee-table-bc8215cc",
    "TVUnit": "cozy-home-interiors-tv-unit-f9dd536d",
    "Television": "cozy-home-interiors-tv-98070018",
    "FloorLamp": "cozy-home-interiors-floor-lamp-3c464081",
    "Bookcase": "cozy-home-interiors-bookcase-ca34c275",
    "LivingRug": "cozy-home-interiors-living-rug-51629599",
    "PlantLarge": "cozy-home-interiors-plant-large-f54433e5",
    "Curtains": "cozy-home-interiors-curtains-29cf6ce1",
    "BaseCabinet": "cozy-home-interiors-base-cabinet-a1f01fe2",
    "DrawerBase": "cozy-home-interiors-drawer-base-457ef3b6",
    "WallCabinet": "cozy-home-interiors-wall-cabinet-4d6f57d2",
    "SinkUnit": "cozy-home-interiors-sink-unit-59e9337d",
    "Oven": "cozy-home-interiors-oven-4e536443",
    "Hob": "cozy-home-interiors-hob-3c6e8228",
    "Fridge": "cozy-home-interiors-fridge-c9568818",
    "DiningTable": "cozy-home-interiors-dining-table-ab8a1c65",
    "DiningChair": "cozy-home-interiors-dining-chair-35257d65",
    "KitchenIsland": "cozy-home-interiors-kitchen-island-aa344597",
    "DoubleBed": "cozy-home-interiors-double-bed-5e14597d",
    "BedsideTable": "cozy-home-interiors-bedside-table-25cc0fd5",
    "Wardrobe": "cozy-home-interiors-wardrobe-50e05964",
    "Dresser": "cozy-home-interiors-dresser-aeda8121",
    "BedroomRug": "cozy-home-interiors-bedroom-rug-be81acde",
    "Bath": "cozy-home-interiors-bath-8ac70b1e",
    "ShowerCubicle": "cozy-home-interiors-shower-cubicle-28d32d99",
    "Toilet": "cozy-home-interiors-toilet-a3169629",
    "BasinUnit": "cozy-home-interiors-basin-unit-268a46dc",
    "TowelRail": "cozy-home-interiors-towel-rail-c664907c",
    "StringLights": "cozy-home-interiors-string-lights-8986b4cd",
}

META_TEMPLATE = """fileFormatVersion: 2
guid: {guid}
ModelImporter:
  serializedVersion: 22200
  internalIDToNameTable: []
  externalObjects: {{}}
  materials:
    materialImportMode: 2
    materialName: 0
    materialSearch: 1
    materialLocation: 1
  animations:
    legacyGenerateAnimations: 4
    bakeSimulation: 0
    resampleCurves: 1
    optimizeGameObjects: 0
    removeConstantScaleCurves: 0
    motionNodeName:
    rigImportErrors:
    rigImportWarnings:
    animationImportErrors:
    animationImportWarnings:
    animationRetargetingWarnings:
    animationDoRetargetingWarnings: 0
    importAnimatedCustomProperties: 0
    importConstraints: 0
    animationCompression: 1
    animationRotationError: 0.5
    animationPositionError: 0.5
    animationScaleError: 0.5
    animationWrapMode: 0
    extraExposedTransformPaths: []
    extraUserProperties: []
    clipAnimations: []
    isReadable: 0
  meshes:
    lODScreenPercentages: []
    globalScale: 1
    meshCompression: 0
    addColliders: 0
    useSRGBMaterialColor: 1
    sortHierarchyByName: 1
    importPhysicalCameras: 1
    importVisibility: 1
    importBlendShapes: 1
    importCameras: 1
    importLights: 1
    nodeNameCollisionStrategy: 1
    fileIdsGeneration: 2
    swapUVChannels: 0
    generateSecondaryUV: 0
    useFileUnits: 1
    keepQuads: 0
    weldVertices: 1
    bakeAxisConversion: 0
    preserveHierarchy: 0
    skinWeightsMode: 0
    maxBonesPerVertex: 4
    minBoneWeight: 0.001
    optimizeBones: 1
    meshOptimizationFlags: -1
    indexFormat: 0
    secondaryUVAngleDistortion: 8
    secondaryUVAreaDistortion: 15.000001
    secondaryUVHardAngle: 88
    secondaryUVMarginMethod: 1
    secondaryUVMinLightmapResolution: 40
    secondaryUVMinObjectScale: 1
    secondaryUVPackMargin: 4
    useFileScale: 1
    strictVertexDataChecks: 0
  tangentSpace:
    normalSmoothAngle: 60
    normalImportMode: 0
    tangentImportMode: 3
    normalCalculationMode: 4
    legacyComputeAllNormalsFromSmoothingGroupsWhenMeshHasBlendShapes: 0
    blendShapeNormalImportMode: 1
    normalSmoothingSource: 0
  referencedClips: []
  importAnimation: 0
  humanDescription:
    serializedVersion: 3
    human: []
    skeleton: []
    armTwist: 0.5
    lowerArmTwist: 0.5
    upperLegTwist: 0.5
    lowerLegTwist: 0.5
    armStretch: 0.05
    legStretch: 0.05
    feetSpacing: 0
    globalScale: 1
    rootMotionBoneName:
    hasTranslationDoF: 0
    hasExtraRoot: 0
    skeletonHasParents: 1
  lastHumanDescriptionAvatarSource: {{instanceID: 0}}
  autoGenerateAvatarMappingIfUnspecified: 1
  animationType: 2
  humanoidOversampling: 1
  avatarSetup: 0
  addHumanoidExtraRootOnlyWhenUsingAvatar: 1
  importBlendShapeDeformPercent: 1
  remapMaterialsIfMaterialImportModeIsNone: 0
  additionalBone: 0
  userData:
  assetBundleName:
  assetBundleVariant:
"""


def glb(path):
    with open(path, "rb") as f:
        magic, version, total = struct.unpack("<III", f.read(12))
        if magic != 0x46546C67:
            raise ValueError("non e' un GLB: " + path)
        size, kind = struct.unpack("<II", f.read(8))
        doc = json.loads(f.read(size).decode("utf-8").rstrip("\0 "))
        size, kind = struct.unpack("<II", f.read(8))
        blob = f.read(size)
    return doc, blob


def read_accessor(doc, blob, idx):
    acc = doc["accessors"][idx]
    n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[acc["type"]]
    comp = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}[acc["componentType"]]
    view = doc["bufferViews"][acc["bufferView"]]
    offset = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride", comp[1] * n)
    out = []
    for i in range(acc["count"]):
        vals = struct.unpack_from("<" + comp[0] * n, blob, offset + i * stride)
        if acc.get("normalized"):
            denom = 32767 if comp[0] == "h" else (65535 if comp[0] == "H" else (127 if comp[0] == "b" else 255))
            vals = tuple(max(-1.0, min(1.0, v / denom)) for v in vals)
        out.append(vals)
    return np.asarray(out, dtype=float)


def mat4(node):
    if "matrix" in node:
        return np.asarray(node["matrix"], dtype=float).reshape((4, 4)).T
    m = np.eye(4)
    q = node.get("rotation", [0, 0, 0, 1])
    x, y, z, w = q
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    s = node.get("scale", [1, 1, 1])
    m[:3, :3] = r @ np.diag(s)
    m[:3, 3] = node.get("translation", [0, 0, 0])
    return m


def convert_one(glb_path, obj_path, name):
    doc, blob = glb(glb_path)
    mats = []
    for m in doc.get("materials", []):
        c = m.get("pbrMetallicRoughness", {}).get("baseColorFactor", [0.8, 0.8, 0.8, 1])
        mats.append(c[:3])
    globals_ = {}

    def walk(node_idx, parent):
        node = doc["nodes"][node_idx]
        world = parent @ mat4(node)
        if "mesh" in node:
            for prim in doc["meshes"][node["mesh"]]["primitives"]:
                if "POSITION" not in prim["attributes"]:
                    continue
                pos = read_accessor(doc, blob, prim["attributes"]["POSITION"])
                nrm = read_accessor(doc, blob, prim["attributes"]["NORMAL"]) if "NORMAL" in prim["attributes"] else None
                idx = read_accessor(doc, blob, prim["indices"]) if "indices" in prim else np.arange(len(pos)).reshape(-1, 1)
                idx = idx.reshape(-1).astype(int)
                globals_.setdefault("verts", []).extend((pos @ world[:3, :3].T + world[:3, 3]).tolist())
                if nrm is not None:
                    normal_mat = np.linalg.inv(world[:3, :3]).T
                    globals_.setdefault("norms", []).extend((nrm @ normal_mat.T).tolist())
                else:
                    globals_.setdefault("norms", []).extend([[0, 1, 0]] * len(pos))
                base = globals_.get("count", 0)
                globals_["count"] = base + len(pos)
                mat_i = prim.get("material")
                mat_name = "mat%d" % mat_i if mat_i is not None else "matDefault"
                faces = []
                for k in range(0, len(idx), 3):
                    tri = idx[k:k + 3]
                    if len(tri) == 3:
                        faces.append((base + int(tri[0]), base + int(tri[1]), base + int(tri[2]), mat_name))
                globals_.setdefault("faces", []).extend(faces)
        for child in node.get("children", []):
            walk(child, world)

    roots = doc.get("scenes", [{}])[doc.get("scene", 0)].get("nodes", [])
    for root in roots:
        walk(root, np.eye(4))
    if not globals_.get("faces"):
        raise ValueError("GLB senza facce: " + glb_path)
    with open(obj_path, "w", encoding="utf-8") as f:
        f.write("mtllib %s.mtl\n" % os.path.basename(name))
        f.write("o %s\n" % name)
        for v in globals_["verts"]:
            f.write("v %.6f %.6f %.6f\n" % tuple(v))
        for n in globals_["norms"]:
            f.write("vn %.6f %.6f %.6f\n" % tuple(n))
        current = None
        for a, b, c, mat_name in globals_["faces"]:
            if mat_name != current:
                f.write("usemtl %s\n" % mat_name)
                current = mat_name
            f.write("f %d//%d %d//%d %d//%d\n" % (a + 1, a + 1, b + 1, b + 1, c + 1, c + 1))
    with open(obj_path + ".mtl", "w", encoding="utf-8") as f:
        for i, c in enumerate(mats):
            f.write("newmtl mat%d\nKd %.4f %.4f %.4f\nKs 0.05 0.05 0.05\nNs 20\n" % (i, c[0], c[1], c[2]))
        f.write("newmtl matDefault\nKd 0.75 0.72 0.68\n")
    v = np.asarray(globals_["verts"])
    return [round(float(x), 3) for x in (v.max(axis=0) - v.min(axis=0))]


def main():
    pack = sys.argv[1] if len(sys.argv) > 1 else "/tmp/opencode/research/interiors/3dassets_cozy_home_interiors"
    dest = sys.argv[2] if len(sys.argv) > 2 else "unity-project/Assets/Resources/HouseInteriors/CozyHome"
    os.makedirs(dest, exist_ok=True)
    cache = "/tmp/opencode/house_obj"
    os.makedirs(cache, exist_ok=True)
    ok = 0
    for name, slug in MODELS.items():
        src = os.path.join(pack, slug + ".glb")
        if not os.path.isfile(src):
            print("MANCANTE", slug)
            continue
        obj = os.path.join(cache, name + ".obj")
        size = convert_one(src, obj, name)
        fbx = os.path.join(dest, name + ".fbx")
        p = subprocess.run(["assimp", "export", obj, fbx], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if p.returncode != 0 or not os.path.isfile(fbx):
            print("ERRORE assimp", name, p.stderr.decode()[:200])
            continue
        meta = os.path.join(dest, name + ".fbx.meta")
        if not os.path.isfile(meta):
            with open(meta, "w", encoding="utf-8") as f:
                f.write(META_TEMPLATE.format(guid=uuid.uuid4().hex))
        print("%-20s %7d byte  bbox=%s" % (name, os.path.getsize(fbx), size))
        ok += 1
    print("Convertiti", ok, "modelli in", dest)


if __name__ == "__main__":
    main()
