using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Riparazione dei materiali dei prefab Kenney Car Kit (CC0) importati da FBX.
    ///
    /// I file in Resources/Vehicles hanno `materialImportMode: 0`: Unity non
    /// importa i materiali originali e i renderer restano con il materiale
    /// predefinito, cioè grigio. Il vecchio percorso `CityOSMWorld` applicava
    /// già URP/Lit + `Vehicles/Textures/colormap.png`, ma il mondo chunked
    /// attivo non passava da lì: le auto risultavano modellate ma grigie.
    ///
    /// La riparazione è condivisa e usa UN solo materiale cached per tutte le
    /// auto: nessuna istanza nuova per veicolo, nessun costo per frame.
    /// Non tocca fisica, collider, numero di veicoli né i modelli stessi.
    /// </summary>
    public static class VehicleMaterialFix
    {
        private static Material cached;
        private static bool tried;

        public static void Apply(GameObject root)
        {
            if (root == null) return;
            Material mat = Cached();
            if (mat == null) return;
            foreach (Renderer r in root.GetComponentsInChildren<Renderer>(true))
            {
                if (r != null) r.sharedMaterial = mat;
            }
        }

        private static Material Cached()
        {
            if (tried) return cached;
            tried = true;

            Texture2D map = Resources.Load<Texture2D>("Vehicles/Textures/colormap");
            if (map == null)
            {
                OsmDiag.Log("[VehicleMaterialFix] colormap Kenney non trovata: modelli grigi");
                return null;
            }
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return null;

            cached = new Material(shader);
            cached.name = "VehicleKenneyColormap";
            if (cached.HasProperty("_BaseMap")) cached.SetTexture("_BaseMap", map);
            if (cached.HasProperty("_MainTex")) cached.SetTexture("_MainTex", map);
            if (shader.name.Contains("Universal"))
            {
                if (cached.HasProperty("_BaseColor")) cached.SetColor("_BaseColor", Color.white);
            }
            else if (cached.HasProperty("_Color"))
            {
                cached.SetColor("_Color", Color.white);
            }
            City.Cinematic.CinematicGraphics.ApplyVehicle(cached);
            return cached;
        }
    }
}
