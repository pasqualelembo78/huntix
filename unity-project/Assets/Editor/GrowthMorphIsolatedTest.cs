using System.Reflection;
using UnityEngine;
using UnityEditor;
using City.Player;

namespace City.Editor
{
    /// <summary>
    /// TEST ISOLATO FASE 1 (batchmode) — verifica numerica del motore morph
    /// runtime senza entrare in play mode:
    ///  - installa il controller esattamente come fa PlayerHeroRig.Ensure;
    ///  - forza il binding mesh<->blend shape cotte;
    ///  - applica profili estremi e legge i pesi effettivi sugli SMR.
    /// Se fallisce, il personaggio NON viene toccato: e' un no-op sicuro.
    /// </summary>
    public static class GrowthMorphIsolatedTest
    {
        private const string PrefabPath = "Assets/Resources/PlayerHero.prefab";

        public static void Run()
        {
            Debug.Log("[MorphTest] == TEST ISOLATO GROWTH == HasMorphData=" +
                GrowthMorphController.HasMorphData());

            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(PrefabPath);
            if (prefab == null)
            {
                Debug.LogError("[MorphTest] prefab non trovato: " + PrefabPath);
                return;
            }
            var inst = (GameObject)PrefabUtility.InstantiatePrefab(prefab);
            if (inst == null)
            {
                Debug.LogError("[MorphTest] instantiate fallito");
                return;
            }
            inst.hideFlags = HideFlags.HideAndDontSave;

            try
            {
                var ctrl = GrowthMorphController.Ensure(inst);
                if (ctrl == null)
                {
                    Debug.LogError("[MorphTest] Ensure ha restituito null: morph non rilevati!");
                    return;
                }

                var mi = typeof(GrowthMorphController)
                    .GetMethod("BindMeshSlots", BindingFlags.Instance | BindingFlags.NonPublic);
                mi.Invoke(ctrl, null);

                var propHasMorphs = typeof(GrowthMorphController)
                    .GetProperty("HasMorphs", BindingFlags.Instance | BindingFlags.Public);
                bool has = (bool)propHasMorphs.GetValue(ctrl);
                Debug.Log("[MorphTest] HasMorphs dopo binding = " + has);
                if (!has)
                {
                    Debug.LogError("[MorphTest] nessun morph registrato: FAIL");
                    return;
                }

                var fCurrent = typeof(GrowthMorphController)
                    .GetField("_current", BindingFlags.Instance | BindingFlags.NonPublic);
                var fTarget = typeof(GrowthMorphController)
                    .GetField("_target", BindingFlags.Instance | BindingFlags.NonPublic);
                var mApplyWeights = typeof(GrowthMorphController)
                    .GetMethod("ApplyWeights", BindingFlags.Instance | BindingFlags.NonPublic);

                // Profilo 1: alto + robusto + anziano + gambe lunghe.
                var p1 = new GrowthMorphProfile
                {
                    age = 1f, height = 1f, proportion = 1f, shape = 1f
                };
                SetAndApply(ctrl, fCurrent, fTarget, mApplyWeights, p1, "ALTO-ROBUSTO-ANZIANO");
                DumpWeights(inst, "profilo1");

                // Profilo 2: basso + esile + giovane + compatto (testa grande).
                var p2 = new GrowthMorphProfile
                {
                    age = 0f, height = 0f, proportion = 0f, shape = 0f
                };
                SetAndApply(ctrl, fCurrent, fTarget, mApplyWeights, p2, "BASSO-ESILE-GIOVANE");
                DumpWeights(inst, "profilo2");

                // Profilo neutro.
                SetAndApply(ctrl, fCurrent, fTarget, mApplyWeights,
                    GrowthMorphProfile.Neutral, "NEUTRO");
                DumpWeights(inst, "profilo neutro");

                Debug.Log("[MorphTest] TEST COMPLETATO OK");
            }
            catch (System.Exception ex)
            {
                Debug.LogError("[MorphTest] ECCEZIONE: " + ex);
            }
            finally
            {
                Object.DestroyImmediate(inst);
            }
        }

        /// <summary>TEST FASE 2 — driver XP -> morph.</summary>
        public static void RunXpDriver()
        {
            Debug.Log("[XpTest] == TEST DRIVER XP->MORPH ==");
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(PrefabPath);
            if (prefab == null) { Debug.LogError("[XpTest] prefab mancante"); return; }
            var inst = (GameObject)PrefabUtility.InstantiatePrefab(prefab);
            if (inst == null) { Debug.LogError("[XpTest] instantiate fallito"); return; }
            inst.hideFlags = HideFlags.HideAndDontSave;

            try
            {
                var ctrl = GrowthMorphController.Ensure(inst);
                if (ctrl == null) { Debug.LogError("[XpTest] controller null: FAIL"); return; }
                var mBind = typeof(GrowthMorphController)
                    .GetMethod("BindMeshSlots", BindingFlags.Instance | BindingFlags.NonPublic);
                mBind.Invoke(ctrl, null);

                var driver = GrowthXpDriver.Ensure(inst);
                if (driver == null) { Debug.LogError("[XpTest] driver null: FAIL"); return; }
                var fLevel = typeof(GrowthXpDriver)
                    .GetField("debugLevelOverride", BindingFlags.Instance | BindingFlags.Public);
                var fTarget = typeof(GrowthMorphController)
                    .GetField("_target", BindingFlags.Instance | BindingFlags.NonPublic);
                var fCurrent = typeof(GrowthMorphController)
                    .GetField("_current", BindingFlags.Instance | BindingFlags.NonPublic);
                var mApplyWeights = typeof(GrowthMorphController)
                    .GetMethod("ApplyWeights", BindingFlags.Instance | BindingFlags.NonPublic);

                // Anteprima della curva: profilo atteso a vari livelli.
                int[] levels = { 1, 5, 12, 25, 50 };
                var sb = new System.Text.StringBuilder();
                for (int i = 0; i < levels.Length; i++)
                    sb.Append(levels[i] + ":{" + GrowthXpDriver
                        .ProfileFromLevel(levels[i]) + "} ");
                Debug.Log("[XpTest] curva level->profilo: " + sb);

                // Applica una progressione e verifica che i pesi si muovano.
                for (int i = 0; i < levels.Length; i++)
                {
                    fLevel.SetValue(driver, levels[i]);
                    driver.ApplyNow();
                    Debug.Log("[XpTest] livello " + levels[i] + " -> profilo atteso " +
                        driver.ExpectedProfile());
                    // In batchmode Update non gira: aggiorna i pesi subito
                    // (come fa il controller in play mode a transizione finita).
                    fCurrent.SetValue(ctrl, fTarget.GetValue(ctrl));
                    mApplyWeights.Invoke(ctrl, null);
                }
                DumpWeights(inst, "livello 50");

                // Riflesso uova: verifica che ai livelli chiave si schiuda
                // l'Egg of Growth (effetto spawnato su cross).
                CheckEggHatch(inst, "progressione 1..50");

                // Salto in un colpo solo da 4 a 50: il driver deve generare
                // TUTTI i livelli chiave attraversati, senza dicotomia.
                fLevel.SetValue(driver, 4);
                driver.ApplyNow();
                var preJump = CountEggs(inst);
                fLevel.SetValue(driver, 50);
                driver.ApplyNow();
                Debug.Log("[XpTest] salto 4→50: uova prima=" + preJump +
                    " dopo=" + CountEggs(inst) + " attese=5");

                Debug.Log("[XpTest] TEST DRIVER COMPLETATO OK");
            }
            catch (System.Exception ex)
            {
                Debug.LogError("[XpTest] ECCEZIONE: " + ex);
            }
            finally
            {
                Object.DestroyImmediate(inst);
            }
        }

        private static int CountEggs(GameObject root)
        {
            return root.GetComponentsInChildren<GrowthEggEffect>(true).Length;
        }

        private static void CheckEggHatch(GameObject root, string label)
        {
            int n = CountEggs(root);
            Debug.Log("[XpTest][" + label + "] uova di crescita schiuse = " + n +
                " (attese 5: 5,10,20,35,50)");
            if (n != 5)
            {
                Debug.LogError("[XpTest] schiusa uova NON conforme: FAIL");
                return;
            }
            int[] levels = GrowthEggEffect.KeyLevels;
            for (int i = 0; i < levels.Length; i++)
            {
                if (IsEggShown(root, levels[i])) continue;
                Debug.LogError("[XpTest] manca l'uovo di livello " + levels[i] + ": FAIL");
                return;
            }
            Debug.Log("[XpTest] schiusa uova OK (5 livelli chiave presenti)");
        }

        private static bool IsEggShown(GameObject root, int level)
        {
            var fx = root.GetComponentsInChildren<GrowthEggEffect>(true);
            for (int i = 0; i < fx.Length; i++)
                if (fx[i].Level == level) return true;
            return false;
        }

        private static void SetAndApply(GrowthMorphController ctrl,
            FieldInfo fCurrent, FieldInfo fTarget, MethodInfo mApplyWeights,
            GrowthMorphProfile profile, string label)
        {
            fCurrent.SetValue(ctrl, profile);
            fTarget.SetValue(ctrl, profile);
            mApplyWeights.Invoke(ctrl, null);
            Debug.Log("[MorphTest] applicato profilo: " + label);
        }

        private static void DumpWeights(GameObject root, string label)
        {
            var smrs = root.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            int total = 0;
            for (int i = 0; i < smrs.Length; i++)
            {
                var smr = smrs[i];
                if (smr == null || smr.sharedMesh == null) continue;
                int count = smr.sharedMesh.blendShapeCount;
                if (count <= 0) continue;
                var names = new System.Text.StringBuilder();
                for (int b = 0; b < count; b++)
                {
                    float w = smr.GetBlendShapeWeight(b);
                    names.Append(smr.sharedMesh.GetBlendShapeName(b)
                        + "=" + w.ToString("F1") + " ");
                    total++;
                }
                Debug.Log("[MorphTest][" + label + "] '" + smr.sharedMesh.name +
                    "' (" + count + ") " + names);
            }
            Debug.Log("[MorphTest][" + label + "] totale blend shape lette: " + total);
        }
    }
}