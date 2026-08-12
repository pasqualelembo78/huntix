using UnityEngine;
using System.Collections.Generic;

namespace Huntix.Core
{
    // Registry degli asset "Kenney Mini Market" referenziati direttamente (no
    // Resources.Load, che non risolve nel build Android di questo progetto).
    // Popolato a build-time da KenneyAssetsSetup e referenziato dal GameManager.
    [CreateAssetMenu(fileName = "KenneyAssetRegistry", menuName = "Huntix/KenneyAssetRegistry")]
    public class KenneyAssetRegistry : ScriptableObject
    {
        public GameObject[] prefabs;
        public Texture2D colormap;

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
