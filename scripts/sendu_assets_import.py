#!/usr/bin/env python3
"""Importa il kit SENDU `Cozy Interiors + Builder` (modelli FBX + palette/pattern PNG).

Il kit è stato scaricato legalmente da https://sendudev.itch.io/cozy-interiors
senza account, con licenza gratuita commerciale non CC0 che consente
esplicitamente il shipping dentro un gioco finito ma vieta la rivendita dei
sorgenti come asset separati. Testo integrale: `README_models.txt`, copiato
insieme agli asset e riportato in `unity-project/Assets/ThirdPartyLicenses.md`.

Vengono importati SOLO i FBX engine-agnostic e le PNG: gli shader/scripts del
kit Unity richiedono Unity 6000.6+/URP 17.6+ e NON sono compatibili con la
2022.3 / URP 14.0.11 di Huntix. I materiali vengono ricreati a runtime con
URP/Lit usando la palette a 8x8 celle (filtro nearest, no mipmap).

Uso: python3 scripts/sendu_assets_import.py [cartella_sorgente] [destinazione]
"""
import importlib.util
import os
import shutil
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))

# Riutilizza il template ModelImporter già usato per i FBX CC0: stessi setting
# (nessun collider automatico, nessun materiale importato, 1 unit = 1 m).
_spec = importlib.util.spec_from_file_location(
    "house_assets_import", os.path.join(HERE, "house_assets_import.py"))
_house = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_house)
MODEL_META = _house.META_TEMPLATE

FOLDER_META = """fileFormatVersion: 2
guid: {guid}
folderAsset: yes
DefaultImporter:
  externalObjects: {{}}
  userData: 
  assetBundleName: 
  assetBundleVariant: 
"""

# Per la palette 256x256 a 8x8 celle: Point filter e mipmap disattivate,
# altrimenti le celle vicine si mescolano e ogni faccia perde il colore piatto.
TEXTURE_META = """fileFormatVersion: 2
guid: {guid}
TextureImporter:
  internalIDToNameTable: []
  externalObjects: {{}}
  serializedVersion: 13
  mipmaps:
    mipMapMode: 0
    enableMipMap: 0
    sRGBTexture: 1
    linearTexture: 0
    fadeOut: 0
    borderMipMap: 0
    mipMapsPreserveCoverage: 0
    alphaTestReferenceValue: 0.5
    mipMapFadeDistanceStart: 1
    mipMapFadeDistanceEnd: 3
  bumpmap:
    convertToNormalMap: 0
    externalNormalMap: 0
    heightScale: 0.25
    normalMapFilter: 0
    flipGreenChannel: 0
  isReadable: 0
  streamingMipmaps: 0
  streamingMipmapsPriority: 0
  vTOnly: 0
  ignoreMipmapLimit: 0
  grayScaleToAlpha: 0
  generateCubemap: 6
  cubemapConvolution: 0
  seamlessCubemap: 0
  textureFormat: 1
  maxTextureSize: 256
  textureSettings:
    serializedVersion: 2
    filterMode: 0
    aniso: 1
    mipBias: 0
    wrapU: 0
    wrapV: 0
    wrapW: 0
  nPOTScale: 0
  lightmap: 0
  compressionQuality: 50
  spriteMode: 0
  spriteExtrude: 1
  spriteMeshType: 1
  alignment: 0
  spritePivot: {{x: 0.5, y: 0.5}}
  spritePixelsToUnits: 100
  spriteBorder: {{x: 0, y: 0, z: 0, w: 0}}
  spriteGenerateFallbackPhysicsShape: 1
  alphaUsage: 1
  alphaIsTransparency: 0
  spriteTessellationDetail: -1
  textureType: 0
  textureShape: 1
  singleChannelComponent: 0
  flipbookRows: 1
  flipbookColumns: 1
  maxTextureSizeSet: 0
  compressionQualitySet: 0
  textureFormatSet: 0
  ignorePngGamma: 0
  applyGammaDecoding: 0
  swizzle: 50462976
  cookieLightType: 0
  platformSettings:
  - serializedVersion: 3
    buildTarget: DefaultTexturePlatform
    maxTextureSize: 256
    resizeAlgorithm: 0
    textureFormat: -1
    textureCompression: 1
    compressionQuality: 50
    crunchedCompression: 0
    allowsAlphaSplitting: 0
    overridden: 0
    ignorePlatformSupport: 0
    androidETC2FallbackOverride: 0
    forceMaximumCompressionQuality_BC6H_BC7: 0
  - serializedVersion: 3
    buildTarget: Android
    maxTextureSize: 256
    resizeAlgorithm: 0
    textureFormat: -1
    textureCompression: 1
    compressionQuality: 50
    crunchedCompression: 0
    allowsAlphaSplitting: 0
    overridden: 0
    ignorePlatformSupport: 0
    androidETC2FallbackOverride: 0
    forceMaximumCompressionQuality_BC6H_BC7: 0
  spriteSheet:
    serializedVersion: 2
    sprites: []
    outline: []
    physicsShape: []
    bones: []
    spriteID: 
    internalID: 0
    vertices: []
    indices: 
    edges: []
    weights: []
  mascotSprite: {{fileID: 0}}
"""


def ensure_meta(path, template):
    meta = path + ".meta"
    if os.path.isfile(meta):
        return False
    with open(meta, "w", encoding="utf-8") as handle:
        handle.write(template.format(guid=uuid.uuid4().hex))
    return True


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else \
        "/tmp/opencode/research/interiors/sendu_models/CozyInteriors_Models"
    dest = sys.argv[2] if len(sys.argv) > 2 else \
        "unity-project/Assets/Resources/HouseInteriors/Sendu"
    if not os.path.isdir(src):
        raise SystemExit("Cartella sorgente non trovata: " + src)
    os.makedirs(dest, exist_ok=True)

    copied_fbx = copied_png = 0
    for room in sorted(os.listdir(os.path.join(src, "FBX"))):
        sdir = os.path.join(src, "FBX", room)
        if not os.path.isdir(sdir):
            continue
        ddir = os.path.join(dest, "FBX", room)
        os.makedirs(ddir, exist_ok=True)
        for name in sorted(os.listdir(sdir)):
            if not name.lower().endswith(".fbx"):
                continue
            target = os.path.join(ddir, name)
            shutil.copy2(os.path.join(sdir, name), target)
            ensure_meta(target, MODEL_META)
            copied_fbx += 1

    for group in ("Palettes", "Patterns"):
        ddir = os.path.join(dest, group)
        os.makedirs(ddir, exist_ok=True)
        for name in sorted(os.listdir(os.path.join(src, group))):
            if not name.lower().endswith(".png"):
                continue
            target = os.path.join(ddir, name)
            shutil.copy2(os.path.join(src, group, name), target)
            ensure_meta(target, TEXTURE_META)
            copied_png += 1

    # Prova della licenza: il testo originale viaggia con gli asset.
    readme = os.path.join(dest, "README_models.txt")
    shutil.copy2(os.path.join(src, "README_models.txt"), readme)
    ensure_meta(readme, FOLDER_META.replace("folderAsset: yes\n", ""))

    # .meta delle cartelle (Unity li ricrea in batchmode, qui sono deterministici).
    ensure_meta(dest, FOLDER_META)
    for root, dirs, _ in os.walk(dest):
        for name in dirs:
            ensure_meta(os.path.join(root, name), FOLDER_META)

    print("SENDU: %d FBX, %d PNG importati in %s" % (copied_fbx, copied_png, dest))


if __name__ == "__main__":
    main()
