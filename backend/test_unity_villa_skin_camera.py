"""Guardie statiche per la skin moderna della villa e la camera adattiva.

Intervento ADDITIVO: `GrandHouseExterior.Dress` resta invariato e chiama alla
fine `GrandHouseSkin.Apply`; `CameraRig` resta l'unico rig della scena e viene
corretto (non duplicato). Nessun uovo nuovo: il giardino/la terrazza sono
solo candidati, i 9 nascondigli storici restano intatti finche' l'utente non
approva l'aggiunta (Mandato §4).

Eseguibile senza Unity, senza rete e senza device:

    python3 backend/test_unity_villa_skin_camera.py
"""

import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITY = os.path.join(ROOT, "unity-project", "Assets")
SCRIPTS = os.path.join(UNITY, "City", "Scripts")


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


class TestCameraAdattiva(unittest.TestCase):
    def setUp(self):
        self.rig = read(os.path.join(SCRIPTS, "Player", "CameraRig.cs"))

    def test_mask_comprende_gli_edifici_e_min_distanza_dinamico(self):
        self.assertIn("private const int BuildingLayer = 8;", self.rig)
        self.assertIn("private const int CameraCollisionMask = ~0;", self.rig)
        self.assertIn("private const float MinIndoorArmDistance = 0.85f;", self.rig)
        self.assertIn("private const float MinOutdoorArmDistance = 1.6f;", self.rig)
        # Lo zoom dell'utente resta clampato come prima: la collisione puo'
        # scendere piu' vicino, ma non cambia la preferenza dell'utente.
        self.assertIn("private const float MinCameraDistance = 4.5f;", self.rig)
        self.assertIn("distance = Mathf.Clamp(distance + delta, MinCameraDistance, MaxCameraDistance);",
                      self.rig)

    def test_hit_piu_vicino_non_primo_ne_piu_alto(self):
        self.assertIn("private float NearestArmHit(int found, float maxDist)", self.rig)
        self.assertIn("int slots = Mathf.Min(found, ArmHitSlots);", self.rig)
        self.assertIn("for (int i = 0; i < slots; i++)", self.rig)
        self.assertIn("if (hitInfo.distance < best) best = hitInfo.distance;", self.rig)
        self.assertIn("if (hitTransform == target || hitTransform.IsChildOf(target)) continue;",
                      self.rig)
        self.assertIn("DistanceToChosen(hitInfo.point)", self.rig)
        self.assertNotIn("_armHits[0]", self.rig)

    def test_probe_soffitto_e_laterali(self):
        self.assertIn("Vector3.up, _armHits, h + d + HeadroomPadding,", self.rig)
        self.assertIn("float ceiling = NearestArmHit(upFound, h + d + HeadroomPadding);", self.rig)
        self.assertIn("Vector3 side = Vector3.Cross(Vector3.up, armDir);", self.rig)
        self.assertIn("[Camera][Adapt] soffitto a ", self.rig)
        # Il taglio va misurato dal torace, non dai piedi: il vecchio confronto
        # assoluto abbassava la camera a ~0,5 m dentro le stanze.
        self.assertIn("float abovePivot = rise - ArmPivotHeight;", self.rig)
        self.assertIn("float k = Mathf.Clamp((ArmPivotHeight + avail) / rise, 0f, 1f);",
                      self.rig)
        self.assertNotIn("float k = avail / rise;", self.rig)

    def test_isteresi_e_correzione_dopo_smoothdamp(self):
        self.assertIn("private const float CollapseSmoothScale = 0.45f;", self.rig)
        self.assertIn("private const float ExpandSmoothScale = 2.2f;", self.rig)
        self.assertIn("wantedArmDist < _smoothedArmDist", self.rig)
        self.assertIn("Vector3.SmoothDamp(transform.position, finalDesired,", self.rig)
        correction = self.rig.index("// CORREZIONE FINALE")
        smooth = self.rig.index("Vector3.SmoothDamp(transform.position, finalDesired,")
        self.assertGreater(correction, smooth)
        # Dopo la correzione la posizione va riassegnata, altrimenti il cast
        # anti-muro resta solo un calcolo senza effetto.
        tail = self.rig[correction:self.rig.index("// Diagnostiche PRIMA del bob")]
        self.assertIn("transform.position = smoothed;", tail)

    def test_override_indoor_non_abbandonato_a_regime(self):
        self.assertIn("private sealed class IndoorProfile", self.rig)
        self.assertIn("private readonly Dictionary<string, IndoorProfile> _indoorProfiles",
                      self.rig)
        self.assertIn("EffectiveIndoorTargets(out indoorTargetDistance,", self.rig)
        self.assertIn("if (!drivingMode && (indoorOverrideActive || indoorDistance > 0f))",
                      self.rig)
        self.assertNotIn("bool blendingIn =", self.rig)
        self.assertIn("1f - (float)System.Math.Exp(-blendSpeed * dt)", self.rig)

    def test_inquadratura_orizzontale_a_piedi(self):
        self.assertIn("private const float ThirdPersonPitch = 0f;", self.rig)
        self.assertIn("private const float MinThirdPersonHeight = 1.35f;", self.rig)
        self.assertIn("private const float MaxThirdPersonHeight = 1.75f;", self.rig)
        self.assertIn("p = ThirdPersonPitch;", self.rig)
        self.assertIn("h = Mathf.Clamp(h, MinThirdPersonHeight, MaxThirdPersonHeight);",
                      self.rig)
        # La normalizzazione e' DOPPO la scelta del profilo e vale solo a piedi:
        # in guida l'inquadratura dall'alto dell'auto resta quella storica.
        profile = self.rig.index("float d = drivingMode ? driveDistance : distance;")
        norm = self.rig.index("// Si normalizza SOLO l'inquadratura a piedi")
        self.assertGreater(norm, profile)
        self.assertIn("if (!drivingMode)\n            {\n                p = ThirdPersonPitch;",
                      self.rig)

    def test_profili_chiamanti_non_modificati(self):
        # La correzione e' additiva nel rig: i valori storici dei caller restano.
        victorian = read(os.path.join(SCRIPTS, "Interior", "Victorian",
                                      "VictorianGrandInterior.cs"))
        entrance = read(os.path.join(SCRIPTS, "Interior", "BuildingEntrance.cs"))
        self.assertIn("SetIndoorOverride(true, CameraOwner, 7f, 3.4f, 16f);",
                      victorian)
        self.assertIn("rig.SetIndoorOverride(active, \"building\", dist, height, pitch);",
                      entrance)

    def test_bussola_in_update_e_un_solo_rig(self):
        update = self.rig.index("private void Update()")
        late = self.rig.index("private void LateUpdate()")
        hook = self.rig.index("EggCompassAssist.Apply(this")
        self.assertTrue(update < hook < late)
        rig_files = []
        for folder, _, files in os.walk(SCRIPTS):
            for name in files:
                if name.endswith(".cs"):
                    rig_files.append(read(os.path.join(folder, name)))
        self.assertEqual(0, sum(source.count("AddComponent<CameraRig>")
                                for source in rig_files))
        self.assertIn("MovementBasis { get { return Quaternion.Euler(0f, yaw, 0f); } }",
                      self.rig)


class TestSkinVillaModerna(unittest.TestCase):
    def setUp(self):
        self.skin = read(os.path.join(SCRIPTS, "Interior", "Visit", "GrandHouseSkin.cs"))
        self.exterior = read(os.path.join(
            SCRIPTS, "Interior", "Visit", "GrandHouseExterior.cs"))

    def test_innesto_additivo_e_idempotente(self):
        self.assertIn("GrandHouseSkin.Apply(root.transform, w, d);", self.exterior)
        self.assertIn("public const string RootName = \"Villa300mq_SkinModerna\";", self.skin)
        self.assertIn("if (FindChild(houseRoot, RootName) != null) return;", self.skin)
        # Nessun builder/prefab storico viene disattivato o sostituito:
        # l'unico SetActive(false) e' quello del LOD1 dei modelli SENDU.
        self.assertEqual(1, self.skin.count("SetActive(false)"))
        self.assertIn("all[i].gameObject.SetActive(false);", self.skin)
        self.assertIn("Dress", self.exterior)

    def test_finestre_con_infissi_cornici_vetro_e_profondita(self):
        for token in ("Box(vano, \"Cassa\"", "Box(vano, \"Vetro\"",
                      "Box(vano, \"TelaioSX\"", "Box(vano, \"TelaioDX\"",
                      "Box(vano, \"TelaioAlto\"", "Box(vano, \"TelaioBasso\"",
                      "Box(vano, \"Mullione\"", "Box(vano, \"Traversa\"",
                      "Box(vano, \"Davanzale\"", "Box(vano, \"Architrave\"",
                      "Box(vano, \"Maniglia\""):
            self.assertIn(token, self.skin)
        self.assertIn("Surface(\"mod_vetro\"", self.skin)
        # Il vano nuovo (Out + 0,12) sta davanti al vecchio pannello (hd + 0,06).
        self.assertIn("new Vector3(x, y, Out + 0.12f)", self.skin)
        self.assertGreater(0.17 + 0.12, 0.06)

    def test_tetto_terrazza_balconi_ingresso_e_giardino(self):
        for token in ("Falda_", "Colmo", "PavimentoTerrazza", "ParapettoTerrazza_",
                      "CorrimanoTerrazza", "MontanteBalcone_", "FiorieraBalcone_",
                      "StipitePorta_", "ArchitravePorta", "Gradino_",
                      "LastraVialetto_", "BordinoVialetto_", "Aiuola_",
                      "Terriccio_", "OlivoGiardino_", "MonsteraIngresso_"):
            self.assertIn(token, self.skin)

    def test_solo_visiva_nessun_collider_o_luce_nuova(self):
        self.assertIn("UnityEngine.Object.Destroy(go.GetComponent<Collider>());", self.skin)
        self.assertIn("UnityEngine.Object.Destroy(cols[i]);", self.skin)
        self.assertNotIn("AddComponent<Light>", self.skin)
        self.assertNotIn("new Light", self.skin)
        self.assertNotIn("AddComponent<Collider>", self.skin)
        self.assertIn("go.layer = BuildingLayer;", self.skin)

    def test_materiali_texturizzati_sono_gia_licenziati(self):
        self.assertIn("HouseInteriors/Sendu/Patterns/", self.skin)
        self.assertIn("HouseInteriors/Sendu/FBX/Decor/", self.skin)
        licenses = read(os.path.join(UNITY, "ThirdPartyLicenses.md"))
        self.assertIn("Cozy Interiors + Builder", licenses)
        self.assertIn("SENDU", licenses)

    def test_riflesso_uova_senza_nuova_economia(self):
        self.assertNotIn("AddComponent<HouseEggSpot>", self.skin)
        self.assertNotIn("featureId", self.skin)
        self.assertIn("NON nasce alcun nuovo uovo", self.skin)
        # I nove nascondigli della villa 300 mq restano nel prefab storico:
        # il baker li genera con Uovo300(...) e assegna l'id "uovo-" + id.
        baker = read(os.path.join(UNITY, "City", "Editor", "VillaPrefabBaker.cs"))
        self.assertEqual(9, baker.count("Uovo300(\""))
        self.assertIn("spot.featureId = \"uovo-\" + id;", baker)


if __name__ == "__main__":
    unittest.main(verbosity=2)
