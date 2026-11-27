#!/usr/bin/env python3
# Test statici dei dettagli naturali additivi di tutto il mondo Huntix.
# Nessun device: verifichiamo asset CC0, ponte bidirezionale, riflesso uova,
# LOD/instancing/streaming e le invarianti del Mandato (MATLAB: solo lettura).
import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))


def read(path):
    with open(os.path.join(ROOT, path), encoding='utf-8', errors='replace') as f:
        return f.read()


class TestAssetNaturali(unittest.TestCase):
    def test_modelli_kenney_cc0_gia_importati(self):
        d = 'unity-project/Assets/Resources/Nature'
        for name in ('grass', 'grass_large', 'grass_leafs',
                     'flower_redA', 'flower_yellowB', 'flower_purpleC',
                     'plant_bush', 'plant_bushSmall',
                     'rock_smallA', 'rock_tallA',
                     'tree_oak', 'tree_default', 'tree_cone'):
            path = os.path.join(ROOT, d, name + '.fbx')
            self.assertTrue(os.path.getsize(path) > 1000, name + '.fbx mancante/troncato')
            self.assertTrue(os.path.exists(path + '.meta'), name + '.meta mancante')
        inv = read('assets/INVENTORY.md')
        self.assertIn('nature', inv)
        self.assertIn('CC0 1.0', inv)
        licenza = read('assets/nature/License.txt')
        self.assertIn('CC0', licenza)

    def test_nessun_nuovo_kit_duplicato(self):
        d = os.path.join(ROOT, 'unity-project/Assets/Resources/Nature')
        fbx = [f for f in os.listdir(d) if f.endswith('.fbx')]
        self.assertGreaterEqual(len(fbx), 100)
        self.assertFalse(os.path.isdir(os.path.join(ROOT, 'unity-project/Assets/Resources/Nature/Quaternius')))


class TestBridgeVegetazione(unittest.TestCase):
    def setUp(self):
        self.bridge = read('unity-project/Assets/City/Scripts/Diagnostics/VegetationFeatureBridge.cs')
        self.menu = read('unity-project/Assets/City/Scripts/UI/HamburgerMenu.cs')
        self.ipc = read('app/src/main/java/com/intelligame/huntix/bridge/UnityIpc.kt')
        self.android = read('app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt')
        self.activity = read('app/src/main/java/com/intelligame/huntix/bridge/BridgeActivity.java')
        self.gm = read('unity-project/Assets/Scripts/Core/GameManager.cs')

    def test_prefisse_evento_e_chiave_dedicati(self):
        self.assertIn('PrefPrefix = "huntix_veg_"', self.bridge)
        self.assertIn('"VegetationFeatureChanged"', self.bridge)
        self.assertIn('"VegetationFeatureChanged"', self.ipc)
        self.assertIn('"VegetationFeatureChanged" -> handleVegetationFeatureChanged', self.android)
        self.assertIn('huntix_veg_$id', self.android)
        self.assertIn('vegetationFeatures', self.activity)
        self.assertIn('vegetationFeatures', self.gm)
        # Nessuna collisione con i due gruppi di preferenze esistenti.
        self.assertNotIn('huntix_veg_', read(
            'unity-project/Assets/City/Scripts/Diagnostics/CinematicFeatureBridge.cs'))
        self.assertNotIn('huntix_veg_', read(
            'unity-project/Assets/City/Scripts/Diagnostics/OpenSourceFeatureBridge.cs'))

    def test_quattro_opzioni_default_on_e_ripristino_passivo(self):
        for feature in ('prati-fioriti', 'cespugli-rocce', 'alberi-parco', 'luci-naturali'):
            self.assertIn('"' + feature + '"', self.bridge)
        self.assertIn('PlayerPrefs.GetInt(PrefPrefix + id, 1) == 1', self.bridge)
        self.assertIn('SetAll(bool on)', self.bridge)
        self.assertIn('RestoreFromAndroid(string flags)', self.bridge)
        self.assertIn('GrantedEggs', self.bridge)  # nessun uovo su restore

    def test_menu_master_e_sottopannello(self):
        self.assertIn('Dettagli naturali: On', self.menu)
        self.assertIn('Dettagli vegetazione', self.menu)
        self.assertIn('ToggleVegetationAll()', self.menu)
        self.assertIn('OpenVegetationPanel()', self.menu)
        self.assertIn('if (idx == 22) _vegLabel = lbl;', self.menu)
        self.assertIn('MakeSubPanel("VegetationPanel"', self.menu)
        self.assertIn('RefreshVegetationDetailLabels()', self.menu)
        self.assertIn('new MenuEntry("Prestazioni adattive: Attive", 21', self.menu)
        # 11 primo livello + 3 uova + 15 impostazioni (due voci nuove).
        self.assertEqual(self.menu.count('new MenuEntry('), 31)
        altezza = re.search(r'MakeSubPanel\("SettingsPanel",\s*700f,\s*(\d+)f\)', self.menu)
        self.assertIsNotNone(altezza)
        self.assertGreaterEqual(int(altezza.group(1)), 1240)


class TestIntegrazioneMondo(unittest.TestCase):
    def setUp(self):
        self.placer = read('unity-project/Assets/City/Scripts/Environment/NatureDetailPlacer.cs')
        self.builder = read('unity-project/Assets/City/Scripts/OSM/ChunkBuilder.cs')
        self.chunk = read('unity-project/Assets/City/Scripts/OSM/ChunkData.cs')
        self.fictional = read('unity-project/Assets/City/Scripts/Game/FictionalCityWorld.cs')
        self.legacy = read('unity-project/Assets/City/Scripts/OSM/CityOSMWorld.cs')
        self.android_city = read('app/src/main/java/com/intelligame/huntix/reallife/OsmCityBuilder.kt')
        self.bridge = read('unity-project/Assets/City/Scripts/Diagnostics/VegetationFeatureBridge.cs')

    def test_innesto_additivo_in_tutti_i_mondi(self):
        self.assertIn('Section("Dettagli naturali")', self.builder)
        self.assertIn('NatureDetailPlacer.Build(\n                        chunk, heights, ToLocal, bounds)', self.builder)
        self.assertIn('NatureDetailPlacer.BuildFlat(_root.transform, parkCenters', self.fictional)
        self.assertIn('NatureDetailPlacer.BuildFlat(parent, detailAnchors', self.legacy)
        self.assertIn('ciuffi', self.android_city.lower())
        self.assertIn('grassDetailMatInst', self.android_city)
        # La sezione Natura esistente resta dov'e'.
        self.assertIn('VegetationPlacer.Build(chunk.natureGo.transform, geo, ToLocal, bounds)', self.builder)

    def test_instancing_streaming_e_lod(self):
        self.assertIn('Graphics.DrawMeshInstanced', self.placer)
        self.assertIn('MaxPerDraw = 1023', self.placer)
        self.assertIn('hasChanged', self.placer)
        self.assertIn('maxDistance', self.placer)
        self.assertIn('SetDetailLod(NatureDetailPlacer.NearGroup, level <= 0', self.chunk)
        self.assertIn('SetDetailLod(NatureDetailPlacer.FarGroup, level <= 1', self.chunk)
        self.assertIn('SetDetailLod(NatureDetailPlacer.LightGroup, level <= 0', self.chunk)
        self.assertIn('MaxLightsPerChunk = 1', self.placer)
        self.assertIn('public void SetDetailPreference(', self.chunk)
        self.assertIn('detailsGo = null', self.chunk)
        for token in ('PORTA_RADIUS2', '.CombineMeshes(combine, true, true)', 'enableInstancing = true',
                      'TryHeightAtWorld', 'HeightNear', '_anchor.hasChanged = false',
                      'c.SetDetailPreference(group, on)', 'FlatRoots'):
            self.assertIn(token, self.placer)
        # Nessun collider solido sulla vegetazione.
        self.assertIn('col.enabled = false', self.placer)
        self.assertIn('ShadowCastingMode.Off', self.placer)

    def test_rebase_gps_e_distanza_porte_in_coordinate_locali(self):
        # Il vecchio codice riscriveva in place le matrici world e azzerava
        # hasChanged sul gruppo condiviso: dopo il rebase GPS i lotti restavano
        # alle vecchie coordinate. Ora le matrici locali restano immutabili.
        self.assertIn('private Matrix4x4[][] _localBlocks', self.placer)
        self.assertIn('private Matrix4x4[][] _worldBlocks', self.placer)
        self.assertIn(
            'bool rootChanged = !_hasRoot || !SameMatrix(root, _lastRoot) ||',
            self.placer)
        self.assertIn('world[i] = root * local[i]', self.placer)
        self.assertIn('private static bool SameMatrix(Matrix4x4 a, Matrix4x4 b)',
                      self.placer)
        self.assertIn('_anchor.hasChanged = false', self.placer)
        # Le porte sono in coordinate mondo, l'erba in coordinate locali.
        self.assertIn('chunk.root.transform.InverseTransformPoint(', self.placer)
        # L'indice di riga del prato si azzera a ogni colonna.
        self.assertIn('int iz = 0;', self.placer)

    def test_quattro_toggle_anche_nei_mondi_piatta(self):
        # Prima i mondi non-chunked creavano solo erba/fiori e cespugli/rocce:
        # «Alberi nei parchi» e «Luci dei parchi» non generavano nulla.
        self.assertIn('var treeGroup = NewGroup(root.transform, TreeGroup)',
                      self.placer)
        self.assertIn('var lights = NewGroup(root.transform, LightGroup)',
                      self.placer)
        self.assertIn(
            'bool treeOn = VegetationFeatureBridge.Enabled('
            'VegetationFeatureBridge.AllIds[2])', self.placer)
        self.assertIn(
            'bool lightOn = VegetationFeatureBridge.Enabled('
            'VegetationFeatureBridge.AllIds[3])', self.placer)
        self.assertIn('AddInstance(treeMats, ParkTreeNames[', self.placer)
        self.assertIn('AddFlatLight(lights, c, groundY)', self.placer)
        self.assertIn('private static void AddFlatLight(', self.placer)

    def test_prati_densi_e_bordi_strada_nella_citta_fittizia(self):
        # Prima della correzione la Citta' Gioco passava a BuildFlat solo i
        # centri dei parchi (uno nel quartiere): 6 ciuffi in 1 km2, invisibili.
        self.assertIn('var vergePoints = new System.Collections.Generic.List<Vector2>()',
                      self.fictional)
        self.assertIn('NatureDetailPlacer.BuildFlat(_root.transform, orderedVerge,',
                      self.fictional)
        self.assertIn('2600f, 2.4f, 2, 4, true', self.fictional)
        self.assertIn('900f, 8f, 8, 14', self.fictional)
        self.assertIn('float scatterRadius = 8f, int minNear = 3, int maxNear = 7,',
                      self.placer)
        self.assertIn('bool vergeMode = false', self.placer)
        self.assertIn('if (!vergeMode && treeOn', self.placer)
        # Il root piatto e' uno solo: il culling dal centro nascondeva l'erba
        # ai bordi, quindi le tre Flush del mondo piatto non hanno distanza.
        self.assertIn('Flush(near, nearMats, 0f, ShadowCastingMode.Off)', self.placer)
        self.assertIn('private const int MaxNearPerChunk = 900;', self.placer)
        self.assertIn('9f, chunk, heights, doorPos, nearMats, ref nNear', self.placer)
        self.assertIn('val grassCount = (fp.width * fp.depth / 90f).toInt()'
                      '.coerceIn(6, 24)', self.android_city)

    def test_riflesso_uova_con_posizione_e_coordinate(self):
        game = read('unity-project/Assets/City/Scripts/Game.cs')
        self.assertIn('public void GrantVegetationEgg(', game)
        self.assertIn('"vegetazione->parco->fioriture"', self.bridge)
        self.assertIn('"vegetazione->tutto"', self.bridge)
        self.assertIn('VegetationFeatureBridge.GrantParkEgg(', self.placer)
        self.assertIn('FeatureEggHidden', self.bridge)
        self.assertIn('WorldOrigin.ToGeo(at)', game)
        # I giardini OSM hanno un luogo testuale dedicato e l'uovo resta nel
        # verde anche se l'utente spegne soltanto i prati.
        self.assertIn('GardenPlace = "città→giardino→cespugli"', self.bridge)
        self.assertIn('nearOn || farOn || treeOn', self.placer)
        self.assertIn('garden ? VegetationFeatureBridge.GardenPlace', self.placer)

    def test_valori_protetti_invarianti(self):
        self.assertIn('public const int LoadRadius = 3;',
                      read('unity-project/Assets/City/Scripts/OSM/ChunkManager.cs'))
        self.assertIn('public const int UnloadRadius = 4;',
                      read('unity-project/Assets/City/Scripts/OSM/ChunkManager.cs'))
        self.assertIn('public const int TerrainColliderLod = 0;', self.chunk)
        self.assertIn('public const float DemSampleStep = 15f;',
                      read('unity-project/Assets/City/Scripts/OSM/TerrainChunk.cs'))
        self.assertIn('const float step = 45f;',
                      read('unity-project/Assets/City/Scripts/OSM/TerrainChunk.cs'))
        self.assertIn('public const int CacheVersion = 5;',
                      read('unity-project/Assets/City/Scripts/OSM/TileClient.cs'))
        self.assertIn('private const int MaxTreesPerChunk = 400;',
                      read('unity-project/Assets/City/Scripts/OSM/VegetationPlacer.cs'))
        self.assertIn('private const int CarsPerTile = 22;',
                      read('unity-project/Assets/City/Scripts/Vehicle/Traffic/TileRoadNetwork.cs'))
        self.assertIn('private const int MaxCars = 180;',
                      read('unity-project/Assets/City/Scripts/Vehicle/Traffic/TileRoadNetwork.cs'))


if __name__ == '__main__':
    unittest.main()
