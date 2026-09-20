using UnityEngine;

namespace City.Afterlife
{
    /// <summary>
    /// Applica i pezzi del kit InfernusKit (altare, trono, cancello, fuochi,
    /// candele, tombe, teschi) al regno dell'aldilà appena costruito da
    /// RealmSceneManager.BuildRealm. Pattern identico a PlayerHeroRig.Ensure:
    /// Resources.Load con fallback silenzioso se il prefab non esiste (il
    /// regno resta comunque funzionante) e idempotente (niente duplicati).
    /// </summary>
    public static class InfernusKitInstaller
    {
        private const string KitRoot = "InfernusKit/";

        /// <summary>Chiamato subito dopo PositionPlayerOnRealm(). Dispone i
        /// pezzi attorno al player (0 su x/z, piedi sul pavimento del regno
        /// a y=0). Se un prefab manca skip silenzioso.</summary>
        public static void Apply(GameObject root)
        {
            if (root == null) return;
            // posti canonici del regno: altare davanti al player, trono alle
            // spalle e cancello al limite del cerchio. Tutti piedi a y=0.
            PlaceAt(root, "Altar",   new Vector3( 0f,  0f,  3f), new Vector3(0, 0, 180f));
            PlaceAt(root, "Throne",  new Vector3( 0f,  0f, -2f), Vector3.zero);
            PlaceAt(root, "Fire",    new Vector3( 2f,  0f,  1f), Vector3.zero);
            PlaceAt(root, "Candles", new Vector3(-2f,  0f,  1f), Vector3.zero);
            PlaceAt(root, "Spires",  new Vector3(-3f,  0f,  3f), Vector3.zero);
            PlaceAt(root, "Wall",    new Vector3( 3f,  0f,  3f), Vector3.zero);
        }

        private static void PlaceAt(GameObject root, string piece,
            Vector3 pos, Vector3 euler)
        {
            var prefab = Resources.Load<GameObject>(KitRoot + piece);
            if (prefab == null) return;
            var go = Object.Instantiate(prefab, root.transform, false);
            go.name = "Kit_" + piece;
            go.transform.localPosition = pos;
            go.transform.localRotation = Quaternion.Euler(euler);
            go.transform.localScale = Vector3.one;
            Debug.Log("[InfernusKitInstaller] piazzato " + piece +
                " a " + pos.ToString("F0"));
        }
    }
}
