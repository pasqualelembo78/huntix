// Mandato §4 · Riflesso della caccia alle uova: la casa grande vista da fuori
// è lo stesso luogo che nasconde le uova; qui non nasce alcuna seconda economia.
// Mandato §3 · nessun sistema/prefab rimosso: la vestizione è ADDITIVA sopra
// l'edificio OSM già istanziato da BuildingPlacer (facciate, collider, porta,
// faro e interno restano esattamente come prima).
using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Rende leggibile da lontano la villa da 300 mq delle case configurate.
    /// L'impronta resta quella OSM (così la porta del registro e le coordinate
    ///Android non si spostano); l'altezza target passa a 3 piani da 3,5 m.
    /// Sopra il prefab esistente aggiunge le fasce dei due piani superiori,
    /// una seconda e terza fila di finestre, due balconi, il cornicione, il
    /// comignolo e il portico d'ingresso. Solo visuale: nessun collider nuovo,
    /// nessuna luce dinamica, nessun materiale scaricato.
    /// </summary>
    public static class GrandHouseExterior
    {
        public const int Floors = 3;
        public const float FloorHeight = 3.5f;
        public const float TotalHeight = Floors * FloorHeight; // 10,5 m
        public const float AreaPerFloor = 100f;                // 10 x 10 m
        public const float TotalArea = Floors * AreaPerFloor;  // 300 mq

        private static readonly System.Collections.Generic.Dictionary<string, Material>
            Mats = new System.Collections.Generic.Dictionary<string, Material>();

        /// <summary>Chiamato da BuildingPlacer dopo aver scalato l'edificio.
        /// centerLocal è il centro dell'impronta in coordinate del chunk,
        /// baseY la quota del terreno sotto la casa, yaw la rotazione OSM.</summary>
        public static void Dress(Transform parent, Vector3 centerLocal, float baseY,
            float w, float d, float yaw, string label)
        {
            if (parent == null) return;
            var root = new GameObject("Villa300mq_Esterno_" +
                (string.IsNullOrEmpty(label) ? "casa" : label));
            root.transform.SetParent(parent, false);
            root.transform.localPosition = new Vector3(centerLocal.x, baseY, centerLocal.z);
            root.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);

            Material cream = Mat("crema", new Color(0.86f, 0.79f, 0.67f));
            Material stone = Mat("pietra", new Color(0.48f, 0.40f, 0.32f));
            Material gold = Mat("oro", new Color(0.74f, 0.56f, 0.20f));
            Material glow = Emissive("vetrina", new Color(1f, 0.93f, 0.74f));

            float hw = Mathf.Max(w, 2f) * 0.5f;
            float hd = Mathf.Max(d, 2f) * 0.5f;

            // Le due linee di divisione dei piani: da strada si contano i
            // tre livelli anche senza avvicinarsi alla porta.
            for (int floor = 1; floor < Floors; floor++)
            {
                float y = floor * FloorHeight;
                Box(root.transform, "FasciaPiano" + floor + "_N",
                    new Vector3(0f, y, -hd), new Vector3(w + 0.5f, 0.38f, 0.35f), gold);
                Box(root.transform, "FasciaPiano" + floor + "_S",
                    new Vector3(0f, y, hd), new Vector3(w + 0.5f, 0.38f, 0.35f), gold);
                Box(root.transform, "FasciaPiano" + floor + "_E",
                    new Vector3(hw, y, 0f), new Vector3(0.35f, 0.38f, d + 0.5f), cream);
                Box(root.transform, "FasciaPiano" + floor + "_O",
                    new Vector3(-hw, y, 0f), new Vector3(0.35f, 0.38f, d + 0.5f), cream);

                // Seconda e terza fila di finestre (il piano terra ha già
                // quelle del prefab: non lo tocchiamo).
                float wy = y + 1.65f;
                for (int i = -2; i <= 2; i++)
                {
                    float t = i * (w / 6f);
                    Box(root.transform, "FinestraPiano" + floor + "_S" + i,
                        new Vector3(t, wy, hd + 0.06f),
                        new Vector3(Mathf.Min(1.6f, w / 8f), 1.9f, 0.12f), glow);
                    Box(root.transform, "FinestraPiano" + floor + "_N" + i,
                        new Vector3(t, wy, -hd - 0.06f),
                        new Vector3(Mathf.Min(1.6f, w / 8f), 1.9f, 0.12f), glow);
                }
                for (int i = -1; i <= 1; i++)
                {
                    float t = i * (d / 4f);
                    Box(root.transform, "FinestraPiano" + floor + "_E" + i,
                        new Vector3(hw + 0.06f, wy, t),
                        new Vector3(0.12f, 1.9f, Mathf.Min(1.6f, d / 5f)), glow);
                    Box(root.transform, "FinestraPiano" + floor + "_O" + i,
                        new Vector3(-hw - 0.06f, wy, t),
                        new Vector3(0.12f, 1.9f, Mathf.Min(1.6f, d / 5f)), glow);
                }

                // Balconi sul lato della porta: il volume della casa si legge
                // anche di profilo (sporto di ~1 m), non solo in altezza.
                float bw = Mathf.Min(w * 0.45f, 6.5f);
                Box(root.transform, "BalconePiano" + floor,
                    new Vector3(0f, y + 0.12f, hd + 0.85f),
                    new Vector3(bw, 0.20f, 1.7f), stone);
                Box(root.transform, "RinghieraPiano" + floor,
                    new Vector3(0f, y + 0.85f, hd + 1.65f),
                    new Vector3(bw, 0.12f, 0.12f), gold);
            }

            // Cornicione e comignolo: chiudono il terzo piano e danno una
            // sagoma "da villa" invece del semplice parallelepipedo prefab.
            Box(root.transform, "CornicioneVilla_N",
                new Vector3(0f, TotalHeight, -hd), new Vector3(w + 1f, 0.55f, 0.6f), stone);
            Box(root.transform, "CornicioneVilla_S",
                new Vector3(0f, TotalHeight, hd), new Vector3(w + 1f, 0.55f, 0.6f), stone);
            Box(root.transform, "CornicioneVilla_E",
                new Vector3(hw, TotalHeight, 0f), new Vector3(0.6f, 0.55f, d + 1f), stone);
            Box(root.transform, "CornicioneVilla_O",
                new Vector3(-hw, TotalHeight, 0f), new Vector3(0.6f, 0.55f, d + 1f), stone);
            Box(root.transform, "ComignoloVilla",
                new Vector3(hw * 0.45f, TotalHeight + 1.1f, -hd * 0.45f),
                new Vector3(1.1f, 2.2f, 1.1f), cream);

            // Portico d'onore sopra la porta reale: la casa appare grande già
            // dall'ingresso, senza spostare il trigger di BuildingEntrance.
            Box(root.transform, "PorticoIngressoVilla",
                new Vector3(0f, 2.95f, hd + 0.9f),
                new Vector3(5.2f, 0.28f, 2f), stone);
            Cylinder(root.transform, "ColonnaPorticoVilla_SX",
                new Vector3(-2.2f, 1.45f, hd + 1.75f),
                new Vector3(0.26f, 1.45f, 0.26f), cream);
            Cylinder(root.transform, "ColonnaPorticoVilla_DX",
                new Vector3(2.2f, 1.45f, hd + 1.75f),
                new Vector3(0.26f, 1.45f, 0.26f), cream);
        }

        private static Transform Box(Transform root, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            go.layer = 8; // BuildingLayer: mai usato come terreno calpestabile
            UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            return go.transform;
        }

        private static Transform Cylinder(Transform root, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            go.name = name;
            go.transform.SetParent(root, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            go.layer = 8;
            UnityEngine.Object.Destroy(go.GetComponent<Collider>());
            return go.transform;
        }

        private static Material Mat(string id, Color c)
        {
            Material m;
            if (Mats.TryGetValue(id, out m) && m != null) return m;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Hidden/InternalErrorShader");
            m = new Material(shader);
            m.name = "Villa300mq_" + id;
            string prop = shader != null && shader.name.StartsWith("Universal Render Pipeline",
                System.StringComparison.Ordinal) ? "_BaseColor" : "_Color";
            if (m.HasProperty(prop)) m.SetColor(prop, c);
            else if (m.HasProperty("_Color")) m.SetColor("_Color", c);
            Mats[id] = m;
            return m;
        }

        private static Material Emissive(string id, Color baseColor)
        {
            Material m = Mat(id, baseColor);
            m.EnableKeyword("_EMISSION");
            if (m.HasProperty("_EmissionColor")) m.SetColor("_EmissionColor", baseColor * 0.8f);
            return m;
        }
    }
}
