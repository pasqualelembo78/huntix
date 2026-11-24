#!/usr/bin/env python3
# Test statici della modalita' grafica cinematografica (CC0). Nessun device:
# verificiamo che l'import sia reale, cablato e opt-in, senza duplicare i 99
# test dell'audit open source.
import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))


def read(path):
    with open(os.path.join(ROOT, path), encoding='utf-8', errors='replace') as f:
        return f.read()


class TestCinematicImport(unittest.TestCase):
    def test_textures_pbr_cc0_presenti_e_verificabili(self):
        d = 'unity-project/Assets/Resources/Cinematic/Textures'
        names = ['Asphalt_Diff', 'Asphalt_Normal', 'Asphalt_AO', 'Asphalt_Rough',
                 'AsphaltWorn_Diff', 'AsphaltWorn_Normal', 'Pavement_Diff',
                 'Pavement_Normal', 'Concrete_Diff', 'Concrete_Normal',
                 'BrickWall_Diff', 'BrickWall_Normal', 'Grass_Diff']
        for n in names:
            self.assertTrue(os.path.getsize(os.path.join(ROOT, d, n + '.jpg')) > 100_000,
                            n + '.jpg mancante o troncato')
            self.assertTrue(os.path.exists(os.path.join(ROOT, d, n + '.jpg.meta')),
                            n + '.meta mancante')
        # Le normal map devono essere importate come NormalMap (sRGB 0).
        for n in ['Asphalt_Normal', 'AsphaltWorn_Normal', 'Pavement_Normal',
                  'Concrete_Normal', 'BrickWall_Normal']:
            meta = read(d + '/' + n + '.jpg.meta')
            self.assertIn('textureType: 1', meta)
            self.assertIn('sRGBTexture: 0', meta)

    def test_raw_grezzi_nella_cartella_assets(self):
        for n in ['characters', 'road', 'nature', 'skyboxes', 'car-kit',
                  'city-commercial', 'city-suburban', 'city-industrial',
                  'furniture', 'cube-pets', 'quaternius-downtown']:
            self.assertTrue(os.path.isdir(os.path.join(ROOT, 'assets', n)), n)
        inv = read('assets/INVENTORY.md')
        self.assertIn('CC0 1.0', inv)
        self.assertIn('character1.unitypackage', inv)  # escluso, licenza non verificata
        self.assertTrue(os.access(os.path.join(ROOT, 'assets/restore.sh'), os.X_OK))

    def test_modelli_cc0_importati_in_unity(self):
        d = 'unity-project/Assets/Resources/Cinematic/RawCc0'
        for n in ['construction-cone', 'construction-barrier',
                  'construction-light', 'light-square', 'sign-highway',
                  'road-crossing']:
            self.assertTrue(os.path.getsize(os.path.join(ROOT, d, n + '.fbx')) > 1000, n)
            self.assertTrue(os.path.exists(os.path.join(ROOT, d, n + '.fbx.meta')))
        self.assertIn('CC0', read(d + '/LICENSE.txt'))
        self.assertTrue(os.path.getsize(os.path.join(ROOT, d, 'Textures/colormap.png')) > 1000)


class TestCinematicBridge(unittest.TestCase):
    def setUp(self):
        self.bridge = read('unity-project/Assets/City/Scripts/Diagnostics/CinematicFeatureBridge.cs')
        self.menu = read('unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs')
        self.graphics = read('unity-project/Assets/City/Scripts/Cinematic/CinematicGraphics.cs')
        self.android_ipc = read('app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt')
        self.android_bridge = read('app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt')
        self.activity = read('app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java')
        self.gm = read('unity-project/Assets/Scripts/Core/GameManager.cs')

    def test_prefisse_evento_e_chiavi_dedicati(self):
        self.assertIn('PrefPrefix = "huntix_gfx_"', self.bridge)
        self.assertIn('"CinematicFeatureChanged"', self.bridge)
        self.assertIn('"CinematicFeatureChanged"', self.android_ipc)
        self.assertIn('"CinematicFeatureChanged" -> handleCinematicFeatureChanged', self.android_bridge)
        self.assertIn('huntix_gfx_$id', self.android_bridge)
        self.assertIn('cinematicFeatures', self.activity)
        self.assertIn('cinematicFeatures', self.gm)
        # Il gruppo audit resta intatto.
        self.assertNotIn('huntix_gfx_', read(
            'unity-project/Assets/City/Scripts/Diagnostics/OpenSourceFeatureBridge.cs'))

    def test_sei_effetti_più_import_grezzo_opt_in(self):
        for feature in ['asphalt-pbr', 'pavement-pbr', 'facade-pbr',
                        'street-lamps', 'night-atmosphere', 'vehicle-detail',
                        'raw-cc0-assets']:
            self.assertIn('"' + feature + '"', self.bridge)
        self.assertIn('Asset CC0 grezzi', self.bridge)
        self.assertIn('PlayerPrefs.GetInt(PrefPrefix + id, 0) == 1', self.bridge)
        self.assertIn('SetAll(bool on)', self.bridge)
        self.assertIn('RestoreFromAndroid(string flags)', self.bridge)

    def test_menu_master_e_sottopannello(self):
        self.assertIn('Modalità cinematografica: Off', self.menu)
        self.assertIn('Dettagli grafica cinematografica', self.menu)
        self.assertIn('() => ToggleCinematicAll()', self.menu)
        self.assertIn('() => OpenCinematicPanel()', self.menu)
        self.assertIn('if (idx == 17) _cinematicLabel = lbl;', self.menu)
        self.assertIn('CinematicFeatureBridge.AllEnabled', self.menu)
        self.assertIn('RefreshCinematicDetailLabels()', self.menu)
        self.assertIn('MakeSubPanel("CinematicPanel"', self.menu)
        self.assertIn('1470f', self.menu)

    def test_effetti_collegati_senza_toccare_fisica(self):
        self.assertIn('CinematicGraphics.ApplyRoad(mat)', read(
            'unity-project/Assets/City/Scripts/OSM/TerrainChunk.cs'))
        self.assertIn('CinematicGraphics.ApplyFacade(inst)', read(
            'unity-project/Assets/City/Scripts/OSM/BuildingPlacer.cs'))
        self.assertIn('CinematicGraphics.ApplyVehicle(cached)', read(
            'unity-project/Assets/City/Scripts/Vehicle/VehicleMaterialFix.cs'))
        self.assertIn('CinematicGraphics.PlaceRawProps(parent, points, dirs)', read(
            'unity-project/Assets/City/Scripts/Environment/PropSpawner.cs'))
        self.assertIn('cols[c].enabled = false', self.graphics)
        # Tutte le 13 texture importate sono collegate, nessuna resta inutilizzata.
        terrain = read('unity-project/Assets/City/Scripts/OSM/TerrainChunk.cs')
        builder = read('unity-project/Assets/City/Scripts/OSM/ChunkBuilder.cs')
        self.assertIn('ApplyWornRoad(mat)', terrain)
        self.assertIn('ApplyPark(mat)', terrain)
        self.assertIn('RoadMaterialFor(chunk.index)', builder)
        self.assertIn('SharedWornRoadMaterial',
                      read('unity-project/Assets/City/Scripts/OSM/ChunkManager.cs'))
        for token in ['r.asphaltRoughness', 'r.asphaltWornDiffuse', 'r.grassDiffuse',
                      'r.brickWallDiffuse', 'reg.concreteDiffuse',
                      '"_MetallicGlossMap"', 'VegetationPlacer.RefreshParkMaterial()']:
            self.assertIn(token, self.graphics)
        # Gli switch devono essere reversibili: snapshot e restore dei materiali.
        self.assertIn('class MatSnapshot', self.graphics)
        self.assertIn('private static void Save(Material m)', self.graphics)
        self.assertIn('private static void Restore(Material m)', self.graphics)
        self.assertIn('AtmosphereEnabled', self.graphics)
        self.assertIn('CinematicGraphics.UpdateLampLights(isNight)', read(
            'unity-project/Assets/City/Scripts/Environment/DayNightManager.cs'))
        self.assertIn('CinematicGraphics.Register()', read(
            'unity-project/Assets/City/Scripts/Game.cs'))

    def test_registry_e_setup_build(self):
        reg = read('unity-project/Assets/Scripts/Core/CinematicAssetRegistry.cs')
        for field in ['asphaltDiffuse', 'asphaltNormal', 'asphaltAO', 'asphaltRoughness',
                      'pavementDiffuse', 'brickWallDiffuse', 'grassDiffuse',
                      'rawProps', 'rawPropsColormap']:
            self.assertIn(field, reg)
        build = read('unity-project/Assets/Editor/HuntixBuild.cs')
        self.assertIn('CinematicAssetsSetup.Populate()', build)
        self.assertIn('CinematicRawAssetsSetup.Populate()', build)
        self.assertIn('public CinematicAssetRegistry cinematicRegistry',
                      read('unity-project/Assets/Scripts/Core/GameManager.cs'))

    def test_uova_indizio_senza_valute(self):
        self.assertIn('GrantAuditEgg("gfx-all"', self.bridge)
        self.assertIn('GrantAuditEgg("gfx-" + id', self.bridge)
        self.assertNotIn('EggSpawnManager', self.bridge)
        self.assertNotIn('currency', self.bridge)


if __name__ == '__main__':
    unittest.main(verbosity=2)
