using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Probe edit-mode per il kit Inferno: apre InfernoScene, risolve gli
    /// ancoraggi con la stessa logica di InfernusDecorator e verifica che le
    /// risorse in Resources/InfernusKit siano importate e caricabili.
    /// Nessun play mode: sicuro in batch.
    /// </summary>
    public static class InfernoEditProbe
    {
        public static void Run()
        {
            var scene = EditorSceneManager.OpenScene(
                "Assets/City/Scenes/InfernoScene.unity", OpenSceneMode.Single);

            StringBuilder sb = new StringBuilder();
            sb.AppendLine(FloorIsLava.InfernusDecorator.ProbeAnchors());

            // Risorse kit: caricate via Resources.Load come a runtime.
            sb.AppendLine("== INFERNUS RESOURCES ==");
            string[] checks =
            {
                "InfernusKit/Rocks/rock_1", "InfernusKit/Piles/pile_1",
                "InfernusKit/Skulls/skull_1", "InfernusKit/Bones/dragonbones_1",
                "InfernusKit/Graves/grave_1", "InfernusKit/Candles/candles_1",
                "InfernusKit/Fire/brasero", "InfernusKit/Fire/lightsource_1",
                "InfernusKit/Fire/burnercolumn_1", "InfernusKit/Fire/candelabra_1",
                "InfernusKit/Throne/throne_1", "InfernusKit/Altar/altar_1",
                "InfernusKit/Spires/spire_1", "InfernusKit/Giants/giant_1",
                "InfernusKit/Hands/hand_1", "InfernusKit/Wall/wallcandles_1",
                "InfernusKit/Wall/wallsword_1",
            };
            foreach (string name in checks)
            {
                var t = Resources.Load<Texture2D>(name);
                sb.AppendLine((t != null
                    ? "OK  " + name + " " + t.width + "x" + t.height
                    : "MISS " + name));
            }

            // Anche le risorse Remy.
            string[] remy =
            {
                "PlayerHero", "Mixamo/PlayerLocomotion",
                "Characters/Skins/young-boy", "Characters/Skins/figure-smart",
            };
            foreach (string name in remy)
                sb.AppendLine("REM " + name + "=" + (Resources.Load(name) != null ? "ok" : "null"));

            string outPath = "/root/giochi/huntix/unitylic/probe_anchors.log";
            File.WriteAllText(outPath, sb.ToString());
            Debug.Log("[InfernoEditProbe] scrittura " + outPath + " len=" + sb.Length);
        }
    }
}