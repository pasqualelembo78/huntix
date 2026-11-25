using System;
using UnityEngine;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Installa il negozio PREFABBRICATO CC0 (`Resources/ShopPrefabs/
    /// NegozioProntoCC0.prefab`) dentro il `Floor_0` già generato. È l'altra
    /// metà dello switch A/B: `InteriorGenerator` chiama questo oppure
    /// `UniversalInteriorBuilder`, senza eliminare nessuno dei due.
    ///
    /// Il prefab è un vero asset Unity creato in editor da
    /// `Assets/Editor/ShopPrefabSetup.cs` con i kit Kenney già CC0 del progetto.
    /// Nessun collider sui mobili; le uova restano in `ContainerEgg` tramite
    /// `UniversalContainerMarker`. Se il prefab non è presente si ricade sul
    /// sistema universale invece di lasciare la stanza vuota.
    /// </summary>
    public static class ShopPrefabInstaller
    {
        public const string Tag = "ShopPrefab";

        public static bool Install(Transform parent, string type, Shop shop,
            float w, float d, float floorH, int floors, string buildingName)
        {
            if (parent == null) return false;
            Transform floor = FindChild(parent, "Floor_0");
            if (floor == null) return false;
            if (FindChild(floor, "NegozioProntoCC0") != null) return true; // idempotente

            GameObject prefab = Resources.Load<GameObject>(ShopStyle.PrefabResource);
            if (prefab == null)
            {
                // Prefab non ancora generato (build vecchia): nessun vuoto.
                UniversalInteriorBuilder.Build(parent, type, shop, w, d,
                    floorH, floors, buildingName);
                return false;
            }

            GameObject inst = (GameObject)UnityEngine.Object.Instantiate(prefab, floor, false);
            inst.name = "NegozioProntoCC0";
            inst.transform.localPosition = Vector3.zero;
            inst.transform.localRotation = Quaternion.identity;
            // Il prefab è progettato per un'impronta 6x6 m: si adatta
            // all'impronta reale senza deformare le altezze.
            inst.transform.localScale = new Vector3(
                Mathf.Clamp(Mathf.Max(4f, w) / 6f, 0.6f, 3f), 1f,
                Mathf.Clamp(Mathf.Max(4f, d) / 6f, 0.6f, 3f));

            foreach (var col in inst.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.Destroy(col);

            string kind = UniversalInteriorRegistry.Classify(type, shop, buildingName);
            var marker = inst.GetComponent<UniversalInteriorMarker>();
            if (marker == null) marker = inst.AddComponent<UniversalInteriorMarker>();
            marker.key = UniversalInteriorRegistry.StableKey(
                parent.position, kind, shop != null ? shop.shopName : buildingName);
            marker.kind = kind;
            marker.label = "Negozio prefabbricato CC0";
            marker.eggHint = UniversalInteriorRegistry.EggHint(kind);

            // I contenitori del prefab portano già il marker salvato in editor;
            // qui lo si garantisce anche se il prefab è stato rigenerato senza.
            Transform[] all = inst.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
            {
                Transform t = all[i];
                if (t == null || t == inst.transform) continue;
                string n = t.name;
                if (n.IndexOf("Cassetto", StringComparison.OrdinalIgnoreCase) < 0 &&
                    n.IndexOf("Dispensa", StringComparison.OrdinalIgnoreCase) < 0) continue;
                var cm = t.GetComponent<UniversalContainerMarker>();
                if (cm == null) cm = t.gameObject.AddComponent<UniversalContainerMarker>();
                cm.room = n.StartsWith("Dispensa", StringComparison.OrdinalIgnoreCase)
                    ? "magazzino prefabbricato" : "banco negozio prefabbricato";
                cm.interiorKey = marker.key;
            }

            try
            {
                UnityBridge.LogToAndroid(Tag,
                    "prefab CC0 installato: " + UniversalInteriorRegistry.ItalianName(kind) +
                    " (" + marker.key + ") — " + marker.eggHint);
            }
            catch (Exception) { }
            return true;
        }

        private static Transform FindChild(Transform root, string name)
        {
            if (root == null) return null;
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i] != null && all[i].name == name) return all[i];
            return null;
        }
    }
}
