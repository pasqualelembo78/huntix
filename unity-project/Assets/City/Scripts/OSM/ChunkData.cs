using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Stato runtime di un chunk 1x1 km. Tutto il contenuto e' figlio di root,
    /// posizionato al centro geografico del chunk: il rebase sposta SOLO la
    /// position della root, i figli restano in coordinate locali piccole.
    /// </summary>
    public class ChunkData
    {
        public Vector2Int index;
        public string key;                 // "C_0876_0612"
        public GeoCoord center;
        public GameObject root;
        public TileGeoDoc geo;             // documento tile di origine (HUD, minimap)

        public GameObject terrainGo;
        public GameObject roadsGo;
        public GameObject sidewalksGo;      // marciapiedi rialzati + cordoli
        public GameObject buildingsGo;
        public GameObject natureGo;
        public GameObject npcsGo;           // pedoni animati sui marciapiedi

        public bool built;
        public int lod = -1;
        public float lastTouch;

        public static string KeyOf(Vector2Int c) => $"C_{c.x:0000}_{c.y:0000}";

        /// <summary>
        /// LOD 0: tutto visibile (vicino). 1: natura off, collider edifici off.
        /// 2: solo terreno + strade. I marciapiedi seguono gli edifici ma il
        /// loro MeshCollider (cordoli) resta attivo solo al livello 0: da
        /// lontano non serve urtare contro un cordolo invisibile, e risparmia
        /// fisica sui chunk lontani.
        /// </summary>
        public void SetLod(int level)
        {
            if (lod == level || !built) return;
            lod = level;

            if (natureGo != null) natureGo.SetActive(level <= 0);
            if (npcsGo != null) npcsGo.SetActive(level <= 1);
            if (sidewalksGo != null)
            {
                sidewalksGo.SetActive(level <= 1);
                SetSidewalkCollider(sidewalksGo, level == 0);
            }
            if (buildingsGo != null)
            {
                buildingsGo.SetActive(level <= 1);
                if (level == 0)
                    SetBuildingColliders(buildingsGo, true);
                else
                    SetBuildingColliders(buildingsGo, false);
            }
        }

        private static void SetSidewalkCollider(GameObject sidewalksGo, bool on)
        {
            var col = sidewalksGo.GetComponent<MeshCollider>();
            if (col != null) col.enabled = on;
        }

        private static void SetBuildingColliders(GameObject buildingsGo, bool on)
        {
            var cols = buildingsGo.GetComponentsInChildren<BoxCollider>(true);
            for (int i = 0; i < cols.Length; i++)
                cols[i].enabled = on;
        }

        public void Destroy()
        {
            if (root != null)
                Object.Destroy(root);
        }
    }
}
