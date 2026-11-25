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
        public GameObject airportsGo;       // piste + velivoli degli aeroporti OSM

        public bool built;
        public int lod = -1;
        public float lastTouch;

        // Cache dei collider edificio: evita il GetComponentsInChildren(true)
        // su migliaia di GameObject a OGNI cambio LOD (Roma: ~6.000 edifici).
        private BoxCollider[] _buildingColliders;

        public static string KeyOf(Vector2Int c) => $"C_{c.x:0000}_{c.y:0000}";

        /// <summary>
        /// LOD 0: tutto visibile (vicino). 1: natura off, collider edifici off.
        /// 2: solo terreno + strade. I marciapiedi seguono gli edifici ma il
        /// loro MeshCollider (cordoli) resta attivo solo al livello 0: da
        /// lontano non serve urtare contro un cordolo invisibile, e risparmia
        /// fisica sui chunk lontani.
        ///
        /// Il TERRENO non si spegne mai (sotto i piedi non puo' mancare), ma il
        /// suo collider si accende solo nei 3x3 chunk attorno al giocatore
        /// (LOD0): un MeshCollider per 49 chunk e' la voce piu' grossa del
        /// broadphase di PhysX e i chunk lontani non li tocca nessuno.
        /// Il centro del LOD e' il chunk del player e si aggiorna ogni 0,25 s,
        /// quindi il suolo calpestabile e' sempre coperto.
        /// </summary>
        public void SetLod(int level)
        {
            if (lod == level || !built) return;
            lod = level;

            if (natureGo != null) natureGo.SetActive(level <= 0);
            if (npcsGo != null) npcsGo.SetActive(level <= 1);
            if (terrainGo != null)
                SetTerrainCollider(terrainGo, level <= TerrainColliderLod);
            if (sidewalksGo != null)
            {
                sidewalksGo.SetActive(level <= 1);
                SetSidewalkCollider(sidewalksGo, level == 0);
            }
            if (buildingsGo != null)
            {
                buildingsGo.SetActive(level <= 1);
                // Collider SEMPRE attivi finche' l'edificio e' visibile (LOD0 e
                // LOD1). Spegnerli a LOD1 creava muri "fantasma" (visibili ma
                // attraversabili) e al passaggio LOD1->LOD0 le pareti si
                // abilitavano SOTTO il player, spingendo il CharacterController
                // fuori dal muro piu' vicino: ne derivava il classico
                // comportamento unidirezionale ("non entro dalla porta, ma esco
                // attraverso i muri"). Fisica simmetrica e stabile a ogni LOD
                // visibile; i collider si spengono solo quando l'edificio e'
                // davvero invisibile (LOD2+, setActive false).
                if (level >= 2)
                    SetBuildingColliders(buildingsGo, false);
                else
                    SetBuildingColliders(buildingsGo, true);
            }
            if (airportsGo != null) airportsGo.SetActive(level <= 1);
        }

        /// <summary>LOD fino al quale il MeshCollider del terreno resta in
        /// PhysX. Il chunk sotto i piedi e' sempre LOD0, quindi il collider
        /// del suolo che calpesti e' SEMPRE attivo. Portandolo da 1 a 0 i
        /// MeshCollider del terreno passano da 49 a 9 (3x3): con la mesh DEM
        /// da 45 m sono ~9.500 triangoli invece di ~51.800 in PhysX.
        /// Nessun'altra query di suolo usa i chunk esterni: NPC e veicoli
        /// lontani seguono TileElevation (DEM), non i collider.</summary>
        public const int TerrainColliderLod = 0;

        private static void SetTerrainCollider(GameObject terrainGo, bool on)
        {
            var col = terrainGo.GetComponent<MeshCollider>();
            if (col != null) col.enabled = on;
        }

        private static void SetSidewalkCollider(GameObject sidewalksGo, bool on)
        {
            var col = sidewalksGo.GetComponent<MeshCollider>();
            if (col != null) col.enabled = on;
        }

        private void SetBuildingColliders(GameObject buildingsGo, bool on)
        {
            // La scansione costosa avviene UNA sola volta: ChunkBuilder chiama
            // SetLod(0) solo a chunk finito, quindi l'albero e' completo.
            if (_buildingColliders == null)
                _buildingColliders = buildingsGo.GetComponentsInChildren<BoxCollider>(true);
            for (int i = 0; i < _buildingColliders.Length; i++)
            {
                var col = _buildingColliders[i];
                if (col == null) continue; // interior lazy puo' aver distrutto figli
                // I trigger porta degli edifici in-place restano SEMPRE attivi:
                // spegnerli ai LOD>0 "mura" fisicamente l'ingresso (muri
                // attraversabili + porta che non risponde). Il giocatore li
                // attraversa lo stesso, quindi il ping-pong collider non serve.
                if (col.isTrigger) continue;
                col.enabled = on;
            }
        }

        public void Destroy()
        {
            _buildingColliders = null;
            if (root != null)
                Object.Destroy(root);
            // Object.Destroy e' DIFFERITO a fine frame: fino ad allora i
            // riferimenti C# restano "validi" e la build in volo poteva
            // continuare a scriverci dentro (NRE durante la guida). Azzerarli
            // subito rende le guardie di ChunkBuilder deterministiche.
            root = null;
            terrainGo = null;
            roadsGo = null;
            sidewalksGo = null;
            buildingsGo = null;
            natureGo = null;
            npcsGo = null;
            airportsGo = null;
        }
    }
}
