using UnityEngine;
using System.Collections.Generic;

namespace Huntix.Core
{
    // Registry degli asset "Kenney City Kit" (CC0) referenziati direttamente
    // (no Resources.Load, che non risolve nel build Android di questo progetto).
    // Popolato a build-time da CityKitAssetsSetup e referenziato dal GameManager.
    [CreateAssetMenu(fileName = "CityKitAssetRegistry", menuName = "Huntix/CityKitAssetRegistry")]
    public class CityKitAssetRegistry : ScriptableObject
    {
        public GameObject[] prefabs;

        public Texture2D colormapCommercial;
        public Texture2D colormapSuburban;
        public Texture2D colormapRoads;

        private Dictionary<string, GameObject> _map;

        public GameObject Get(string name)
        {
            if (prefabs == null) return null;
            if (_map == null)
            {
                _map = new Dictionary<string, GameObject>();
                foreach (var p in prefabs)
                    if (p != null && !_map.ContainsKey(p.name))
                        _map[p.name] = p;
            }
            _map.TryGetValue(name, out var go);
            return go;
        }
    }
}
