using System.Collections.Generic;
using UnityEngine;

namespace City.Events
{
    /// <summary>
    /// I FBX importati arrivano con materiali generici: qui vengono convertiti
    /// una sola volta a URP/Lit mantenendo texture e colore originali. Nessun
    /// materiale nuovo per renderizzatore, così SRP Batcher resta efficace e
    /// il costo su Android non cresce con il numero di spettatori.
    /// </summary>
    public static class VenueMaterials
    {
        private static readonly Dictionary<int, Material> Patched =
            new Dictionary<int, Material>();
        private static Shader _urp;

        public static void Apply(GameObject go)
        {
            if (go == null) return;
            var renderers = go.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < renderers.Length; i++)
            {
                var mats = renderers[i].sharedMaterials;
                for (int m = 0; m < mats.Length; m++)
                {
                    if (mats[m] != null) mats[m] = Patch(mats[m]);
                }
                renderers[i].sharedMaterials = mats;
            }
        }

        private static Material Patch(Material src)
        {
            if (src.shader != null &&
                src.shader.name.StartsWith("Universal Render Pipeline", System.StringComparison.Ordinal))
                return src;
            int id = src.GetInstanceID();
            Material cached;
            if (Patched.TryGetValue(id, out cached)) return cached;
            if (_urp == null) _urp = Shader.Find("Universal Render Pipeline/Lit");
            if (_urp == null) return src;

            var dst = new Material(_urp);
            dst.name = "URP_" + src.name;
            if (src.HasProperty("_BaseMap") && src.mainTexture != null)
                dst.SetTexture("_BaseMap", src.mainTexture);
            Color c = src.HasProperty("_Color") ? src.color : Color.white;
            dst.SetColor("_BaseColor", c);
            if (src.HasProperty("_EmissionColor"))
            {
                dst.EnableKeyword("_EMISSION");
                dst.SetColor("_EmissionColor", src.GetColor("_EmissionColor"));
            }
            Patched[id] = dst;
            return dst;
        }
    }
}
