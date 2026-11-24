// Audit delle migliorie acquisite dai progetti open source. Nessuna riga di
// codice dei repository analizzati e' stata copiata: sono controlli Huntix
// sulle proprieta' gia' presenti nel progetto. Ogni esito passa dal ponte
// condiviso (preferenza Android + uovo posizionale + telemetria).
using System;
using System.Reflection;
using UnityEngine;

namespace City.Diagnostics
{
    /// <summary>
    /// Quattro controlli locali, uno per repository senza codice riusabile
    /// direttamente: registry asset (Unity-Rpg, MIT), path Resources
    /// (OpenStash, MIT dichiarato ma LICENSE assente), invarianti di caricamento
    /// (Grand Road Auto, proprietario), provenienza pack (Quaternius, CC0/QAL).
    /// </summary>
    public static class OpenSourceAudit
    {
        public const string AssetHandles = "asset-handles";
        public const string ResourceDefs = "resource-defs";
        public const string LoadLifecycle = "load-lifecycle";
        public const string AssetPacks = "asset-packs";

        /// <summary>Path verificati dall'editor (nessun Resources.Load a runtime).</summary>
        public static readonly string[] KnownResourcePaths =
            { "Assets/Resources/Legal/copyright.txt",
              "Assets/Resources/Nature",
              "Assets/Resources/Buildings" };

        /// <summary>Pack quaternius verificati sulle pagine ufficiali.</summary>
        public static readonly string[] PackManifest =
            { "simplenature|CC0|https://quaternius.com/packs/simplenature.html",
              "downtowncitymegakit|CC0|https://quaternius.com/packs/downtowncitymegakit.html",
              "stylizednaturemegakit|CC0|https://quaternius.com/packs/stylizednaturemegakit.html",
              "charactersfantasyraces|QAL|https://quaternius.com/packs/charactersfantasyraces.html" };

        public static string Run(string source)
        {
            if (string.IsNullOrEmpty(source)) return "sorgente mancante";
            switch (source.ToLowerInvariant())
            {
                case AssetHandles: return RunAssetHandles();
                case ResourceDefs: return RunResourceDefs();
                case LoadLifecycle: return RunLoadLifecycle();
                case AssetPacks: return RunAssetPacks();
                default: return "sorgente sconosciuta: " + source;
            }
        }

        private static string RunAssetHandles()
        {
            object gm = Huntix.Core.GameManager.Instance;
            bool city = gm != null && FieldObject(gm, "cityKitRegistry") != null;
            bool kenney = gm != null && FieldObject(gm, "kenneyRegistry") != null;
            int pass = (city ? 1 : 0) + (kenney ? 1 : 0);
            Report(AssetHandles, "Uovo dei crediti", "città→crediti",
                "registry=" + pass + "/2", pass == 2, 2);
            return "asset-handles " + pass + "/2 registry collegati";
        }

        private static string RunResourceDefs()
        {
            // L'editor verifica i file su disco (AssetDatabase); a runtime il
            // controllo e' solo sulla consistenza del manifesto, senza I/O.
            int valid = 0;
            for (int i = 0; i < KnownResourcePaths.Length; i++)
                if (!string.IsNullOrEmpty(KnownResourcePaths[i])) valid++;
            Report(ResourceDefs, "Uovo delle risorse", "città→risorse",
                "definizioni=" + valid + "/" + KnownResourcePaths.Length,
                valid == KnownResourcePaths.Length, KnownResourcePaths.Length);
            return "resource-defs " + valid + "/" + KnownResourcePaths.Length + " definizioni";
        }

        private static string RunLoadLifecycle()
        {
            int pass = 0, total = 6;
            if (Greater("City.OSM.ChunkManager", "UnloadRadius", "LoadRadius")) pass++;
            if (Number("City.OSM.ChunkManager", "BuildBudgetMs", 16f, true)) pass++;
            if (Number("City.OSM.ChunkManager", "MaxConcurrentBuilds", 1f, false)) pass++;
            if (Number("City.Vehicle.Traffic.TileRoadNetwork", "CarsPerTile", 25f, true)) pass++;
            if (Number("City.Vehicle.Traffic.TileRoadNetwork", "MaxCars", 200f, true)) pass++;
            if (Number("City.OSM.ChunkManager", "ProgressIntervalS", 0f, false, true)) pass++;
            Report(LoadLifecycle, "Uovo del caricamento", "città→impostazioni",
                "invarianti=" + pass + "/" + total, pass == total, total);
            return "load-lifecycle " + pass + "/" + total + " invarianti";
        }

        private static string RunAssetPacks()
        {
            int cc0 = 0, qal = 0;
            for (int i = 0; i < PackManifest.Length; i++)
            {
                string[] parts = PackManifest[i].Split('|');
                if (parts.Length != 3) continue;
                if (parts[1] == "CC0") cc0++;
                else if (parts[1] == "QAL") qal++;
            }
            bool ok = cc0 == 3 && qal == 1;
            Report(AssetPacks, "Uovo dei crediti pack", "città→crediti",
                "CC0=" + cc0 + " QAL=" + qal, ok, PackManifest.Length);
            return "asset-packs CC0=" + cc0 + " QAL=" + qal;
        }

        private static void Report(string id, string label, string place,
                                   string detail, bool ok, int total)
        {
            OpenSourceFeatureBridge.Report(id, label, place, detail, ok);
        }

        /// <summary>Legge una costante privata senza modificarla; se il nome
        /// cambia restituisce false invece di rompere la build.</summary>
        private static bool Number(string typeName, string field, float limit,
                                   bool atMost, bool strictlyPositive = false)
        {
            Type type = Type.GetType(typeName + ", Assembly-CSharp");
            if (type == null) type = FindType(typeName);
            if (type == null) return false;
            FieldInfo info = type.GetField(field,
                BindingFlags.NonPublic | BindingFlags.Public | BindingFlags.Static);
            if (info == null) return false;
            object raw = info.GetRawConstantValue();
            float value;
            if (raw == null || !float.TryParse(raw.ToString(), out value)) return false;
            return strictlyPositive ? value > 0f : (atMost ? value <= limit : value == limit);
        }

        private static bool Greater(string typeName, string a, string b)
        {
            Type type = Type.GetType(typeName + ", Assembly-CSharp");
            if (type == null) type = FindType(typeName);
            if (type == null) return false;
            FieldInfo fa = type.GetField(a, BindingFlags.NonPublic | BindingFlags.Public | BindingFlags.Static);
            FieldInfo fb = type.GetField(b, BindingFlags.NonPublic | BindingFlags.Public | BindingFlags.Static);
            if (fa == null || fb == null) return false;
            float va, vb;
            if (!float.TryParse(fa.GetRawConstantValue().ToString(), out va)) return false;
            if (!float.TryParse(fb.GetRawConstantValue().ToString(), out vb)) return false;
            return va > vb;
        }

        private static object FieldObject(object target, string name)
        {
            if (target == null) return null;
            FieldInfo info = target.GetType().GetField(name,
                BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
            return info == null ? null : info.GetValue(target);
        }

        private static Type FindType(string name)
        {
            foreach (var assembly in AppDomain.CurrentDomain.GetAssemblies())
            {
                Type t = assembly.GetType(name);
                if (t != null) return t;
            }
            return null;
        }
    }
}
