using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using City.World;
using TMPro;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Genera interni 3D procedurali per edifici della città.
    /// Tipi supportati: casa, negozio, palazzo.
    /// Usa modelli Kenney Furniture Kit caricati da Resources, altrimenti primitives.
    /// </summary>
    public class InteriorGenerator : MonoBehaviour
    {
        private const float WALL_THICK = 0.2f;
        // Shell in-place: altezze di porta/finestre (mondo, metro)
        private const float SHELL_DOOR_H = 2.2f;
        private const float SHELL_DOOR_W = 2.4f;
        private const float SHELL_WIN_BASE = 0.85f;  // davanzale
        private const float SHELL_WIN_TOP = 1.95f;   // architrave

        // Vero se l'interno e' costruito IN-PLACE dentro l'edificio reale
        // (muri perimetrali del guscio gia' presenti). Sopprime i muri
        // perimetrali per-piano e la porta piena (il varco e' reale nel guscio).
        private bool _inPlace;

        private static Dictionary<string, GameObject> _furnitureMap;
        private static bool _furnitureLoaded;

        private static readonly Dictionary<string, string> FBX_NAME = new Dictionary<string, string>
        {
            ["Divano"] = "loungeSofa", ["DivanoA"] = "loungeSofa", ["DivanoB"] = "loungeSofa",
            ["Tavolino"] = "tableCoffee", ["TavoloA"] = "tableCoffee", ["TavoloB"] = "tableCoffee",
            ["TV"] = "televisionModern", ["TVA"] = "televisionModern", ["TVB"] = "televisionModern",
            ["Lampada"] = "lampRoundFloor",
            ["Frigo"] = "kitchenFridge", ["Forno"] = "kitchenStove", ["Lavello"] = "kitchenSink",
            ["Tavolo"] = "table", ["TavoloUff"] = "desk",
            ["Sedia1"] = "chair", ["Sedia2"] = "chair", ["SediaScriv"] = "chairDesk", ["SediaUff"] = "chair",
            ["LettoMat"] = "bedDouble", ["LettoA"] = "bedDouble", ["LettoB"] = "bedDouble",
            ["LettoSing"] = "bedSingle",
            ["Comodino1"] = "sideTable", ["LampCom1"] = "lampRoundTable",
            ["Armadio1"] = "bookcaseClosed", ["ArmadioA"] = "bookcaseClosed", ["ArmadioB"] = "bookcaseClosed",
            ["Scrivania"] = "desk", ["Libreria"] = "bookcaseOpen",
            ["WC"] = "toilet", ["WCA"] = "toilet", ["WCB"] = "toilet",
            ["Lavandino"] = "bathroomSink", ["LavA"] = "bathroomSink", ["LavB"] = "bathroomSink",
            ["Bancone"] = "kitchenBar", ["Panca"] = "bench",
            ["PC"] = "laptop",
        };

        // Materiali (colori semplici, nessuna dipendenza esterna)
        private static Material _wallMat;
        private static Material _floorMat;
        private static Material _ceilingMat;
        private static Material _woodMat;
        private static Material _darkMat;
        private static Material _glassMat;
        private static Material _tileMat;
        private static Material _shelfMat;
        private static Material _doorMat;

        // Soffitto EMISSIVO degli interni in-place: brilla con costo zero così
        // l'interno si intravede acceso anche da lontano (attraverso finestre/
        // porta) senza richiedere luci reali sempre accese.
        private static Material _glowMat;

        /// <summary>True se il punto è ancora chiaramente DENTRO l'edificio (privo di
        /// margini: nel footprint reale). World-space.</summary>
        public bool DeepInside(Vector3 worldPos)
        {
            Vector3 local = transform.InverseTransformPoint(worldPos);
            if (local.y < -0.5f || local.y > ShellH + 1f) return false;
            return Mathf.Abs(local.x) < ShellW * 0.5f && Mathf.Abs(local.z) < ShellD * 0.5f;
        }

        // ── COSTRUZIONE LAZY (interni on-demand) ─────────────────────
        // A build-time viene creato SOLO il guscio esterno (shell) con porta e
        // finestre reali. L'interno (arredi, pavimenti, muri divisori) viene
        // costruito quando il giocatore si avvicina all'edificio (ticker unico
        // in InteriorManager). Così il chunk build è immediato e si paga solo
        // gli interni che vengono davvero visitati.
        private string _lazyType;
        private float _lazyW, _lazyD, _lazyH;
        private Shop _lazyShop;

        /// <summary>True quando l'interno è già stato costruito.</summary>
        public bool InteriorBuilt { get; private set; }

        // ── GEOMETRIA GUSCIO (per l'entrata/uscita geometrica) ─────────
        // InteriorManager usa contenimento XZ + quota per sapere SE il player
        // è dentro l'edificio, senza dipendere dagli eventi dei trigger porta.
        public float ShellW { get; private set; }
        public float ShellD { get; private set; }
        public float ShellH { get; private set; }

        // ── Modalita' PREFAB (esterno Quaternius + interno reale) ──
        // L'edificio e' un prefab pieno (kit Quaternius/Kenney): l'interno
        // (pavimento, muri, arredi, luci) viene generato lazy DENTRO l'impronta
        // e mostrato solo quando il player e' dentro; durante l'interno il
        // prefab esterno viene nascosto. L'ingresso avviene con il fade dalla
        // porta (BuildingEntrance), l'uscita dal trigger interno "USCITA".
        private bool _prefabExterior;
        private Transform _interiorRoot;
        private Collider _prefabCollider;

        /// <summary>Ingresso canonico dell'edificio (in-place, quello "dentro").
        /// Impostato da BuildingPlacer dopo la creazione dei trigger porta.</summary>
        public BuildingEntrance Entrance { get; private set; }
        public void SetEntrance(BuildingEntrance e) { Entrance = e; }

        /// <summary>Ancora in world-space sul lato della PORTA (+Z locale):
        /// il ticker lazy misura la distanza da qui invece che dal centro, così
        /// per gli edifici grandi l'interno viene costruito appena il player si
        /// avvicina alla soglia (un edificio 60 m sarebbe a >30 m dal centro
        /// anche stando sulla porta).</summary>
        public Vector3 DoorAnchorWorld
        {
            get
            {
                Vector3 f = transform.forward;
                return transform.position + f * (ShellD * 0.5f + 0.5f);
            }
        }

        /// <summary>True se il punto gioca è dentro il volume del guscio
        /// (XZ entro l'impronta, quota entro [0, shellH]). World-space.</summary>
        public bool Contains(Vector3 worldPos)
        {
            if (ShellW <= 0f || ShellD <= 0f) return false;
            Vector3 local = transform.InverseTransformPoint(worldPos);
            if (local.y < -0.5f || local.y > ShellH + 1f) return false;
            return Mathf.Abs(local.x) <= ShellW * 0.5f &&
                   Mathf.Abs(local.z) <= ShellD * 0.5f;
        }

        /// <summary>Luce interna point dell'edificio (creata da BuildingPlacer).
        /// Il ticker unico di InteriorManager la mantiene SEMPRE ACCESA
        /// (niente toggle distanza → nessun cambiamento improvviso di
        /// luminosità), sostituendo l'Update() per-edificio (costo N× per frame).</summary>
        private Light _interiorLight;

        public void AttachLight(Light l) { _interiorLight = l; }

        public void SetLight(bool on)
        {
            if (_interiorLight != null && _interiorLight.enabled != on)
                _interiorLight.enabled = on;
        }

        private void EnsureMaterials()
        {
            if (_wallMat != null) return;
            _wallMat = Lit(new Color(0.92f, 0.88f, 0.82f));
            _floorMat = Lit(new Color(0.55f, 0.42f, 0.30f));
            _ceilingMat = Lit(new Color(0.88f, 0.85f, 0.80f));
            _woodMat = Lit(new Color(0.55f, 0.35f, 0.18f));
            _darkMat = Lit(new Color(0.22f, 0.22f, 0.22f));
            _glassMat = Lit(new Color(0.6f, 0.75f, 0.85f, 0.5f));
            _tileMat = Lit(new Color(0.85f, 0.85f, 0.82f));
            _shelfMat = Lit(new Color(0.6f, 0.4f, 0.25f));
            _doorMat = Lit(new Color(0.18f, 0.14f, 0.10f));
            _glowMat = LitEmissive(new Color(1f, 0.96f, 0.86f), new Color(0.75f, 0.7f, 0.6f));
        }

        private Material Lit(Color c)
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) { LogW("No shader found, using magenta"); shader = Shader.Find("Hidden/InternalErrorShader") ?? Shader.Find("Standard"); }
            var m = new Material(shader);
            if (shader.name.Contains("Universal") || shader.name.Contains("URP"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            m.SetFloat("_Smoothness", 0.3f);
            m.SetInt("_Cull", (int)CullMode.Off);
            if (_wallMat == null)
                Log("Lit: shader=" + shader.name + " cull=Off(" + (int)CullMode.Off + ")");
            return m;
        }

        private Material LitEmissive(Color baseColor, Color emission)
        {
            var m = Lit(baseColor);
            var shader = m.shader;
            // URP: emissione tramite _EmissionColor + keyword _EMISSION;
            // Standard: sola _EmissionColor (keyword gestita da GI).
            if (shader != null && shader.name.StartsWith("Universal Render Pipeline"))
            {
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", emission);
            }
            else
            {
                m.SetColor("_EmissionColor", emission);
                m.EnableKeyword("_EMISSION");
            }
            return m;
        }

        /// <summary>
        /// Costruisce l'intero interno come figli di parent.
        /// Ogni piano è un GameObject "Floor_N" attivabile/disattivabile.
        /// </summary>
        public void BuildInterior(Transform parent, string type,
            float extW, float extD, float extH, int floors, Shop shop)
        {
            Log("BuildInterior: type=" + type + " size=" + extW + "x" + extD + "x" + extH + " floors=" + floors);
            EnsureMaterials();

            // Impronta condivisa con il guscio: la dimensione REALE dell'edificio
            // (minimo 4 m), così pavimento e arredi coprono l'intera impronta
            // anche per i gusci grandi (fino a 60 m di lato).
            float w = Mathf.Max(4f, extW);
            float d = Mathf.Max(4f, extD);
            float floorH = 3f;

            switch (type)
            {
                case "shop":
                    BuildShop(parent, w, d, floorH, floors, shop);
                    break;
                case "apartment":
                    BuildApartment(parent, w, d, floorH, floors, shop);
                    break;
                case "bar":
                    BuildBar(parent, w, d, floorH, floors);
                    break;
                case "hospital":
                    BuildHospital(parent, w, d, floorH, floors);
                    break;
                case "dealer":
                    BuildDealer(parent, w, d, floorH, floors);
                    break;
                case "repair":
                case "garage":
                case "fuel":
                    BuildWorkshop(parent, w, d, floorH, floors);
                    break;
                case "bank":
                    BuildBank(parent, w, d, floorH, floors);
                    break;
                case "school":
                    BuildSchool(parent, w, d, floorH, floors);
                    break;
                case "hotel":
                    BuildHotel(parent, w, d, floorH, floors);
                    break;
                default:
                    BuildHouse(parent, w, d, floorH, floors, shop);
                    break;
            }
            Log("BuildInterior DONE");
        }

        // ── GUSCIO IN-PLACE (edifici enterabili reali) ────────────────
        // Costruisce l'edificio vero e proprio (muri perimetrali a tutta
        // altezza con porta e finestre REALI) direttamente nella posizione
        // del mondo, da cui il nome "in-place". L'interno (arredi) viene poi
        // generato nello stesso vuoto. Niente teletrasporto: si entra
        // attraversando la soglia della porta.

        public void BuildInPlace(Transform parent, string type,
            float w, float d, float extH, Shop shop)
        {
            EnsureMaterials();
            _inPlace = true;
            try
            {
                // Il guscio e l'interno devono condividere la stessa impronta:
                // BuildInterior usa la dimensione reale (minimo 4 m), quindi qui
                // facciamo lo stesso così il pavimento interno arriva fino ai
                // muri del guscio (nessun vuoto sotto il perimetro).
                w = Mathf.Max(4f, w);
                d = Mathf.Max(4f, d);
                float shellH = Mathf.Clamp(extH, 2.8f, 4.2f);
                BuildShell(parent, w, d, shellH);
                // Edificio in-place a PIANO UNICO (l'ingresso reale è a terra);
                // i piani multipli restano per la vecchia modalità on-demand.
                BuildInterior(parent, type, w, d, shellH, 1, shop);
            }
            finally
            {
                _inPlace = false;
            }
            InteriorBuilt = true;
        }

        /// <summary>Costruisce SOLO il guscio esterno (muri perimetrali, porta e
        /// finestre reali, tetto). A differenza di BuildInPlace non genera gli
        /// arredi: l'interno sarà costruito al primo avvicinamento del player.
        /// Ritorna l'altezza del guscio effettivamente usata (clampata).</summary>
        public float BuildShellOnly(Transform parent, float w, float d, float extH)
        {
            EnsureMaterials();
            _inPlace = true;
            try
            {
                // Impronta REALE dell'edificio (minimo 4 m, nessun cap: gli
                // edifici fino a 60 m di lato sono enterabili in-place).
                ShellW = Mathf.Max(4f, w);
                ShellD = Mathf.Max(4f, d);
                float shellH = Mathf.Clamp(extH, 2.8f, 4.2f);
                ShellH = shellH;
                BuildShell(parent, ShellW, ShellD, shellH);
                return shellH;
            }
            finally
            {
                _inPlace = false;
            }
        }

        /// <summary>Memorizza i parametri per la costruzione lazy dell'interno.
        /// Deve essere chiamato dopo BuildShellOnly, sul genitore del guscio.</summary>
        public void PrepareLazy(string type, float w, float d, float shellH, Shop shop)
        {
            _lazyType = type;
            _lazyW = Mathf.Max(4f, w);
            _lazyD = Mathf.Max(4f, d);
            _lazyH = shellH;
            _lazyShop = shop;
        }

        /// <summary>Costruisce l'interno lazy (arredi) ora, se non già fatto.
        /// Invocato dal ticker di InteriorManager quando il player è vicino.</summary>
        public void BuildInteriorNow()
        {
            if (InteriorBuilt) return;
            InteriorBuilt = true;
            // Modalita' PREFAB: l'interno genera i SUOI muri perimetrali e il
            // trigger "USCITA" (via _inPlace=false). I gusci in-place invece
            // non duplicano i muri (il guscio esterno e' gia' la struttura).
            // Con i prefab l'arredo vive in un figlio contro-scalato
            // (_interiorRoot) cosi' la scala non uniforme del prefab (che e'
            // scalato sull'impronta OSM) non deforma pareti e arredi.
            _inPlace = !_prefabExterior;
            Log("BuildInteriorNow tipo='" + (_lazyType ?? "?") + "' " +
                _lazyW.ToString("F1") + "x" + _lazyD.ToString("F1") + "x" +
                _lazyH.ToString("F1") + " prefab=" + _prefabExterior);
            try
            {
                Transform dest = _prefabExterior && _interiorRoot != null ? _interiorRoot : transform;
                BuildInterior(dest, _lazyType, _lazyW, _lazyD, _lazyH, 1, _lazyShop);
                if (_prefabExterior)
                    SetInteriorRootActive(false); // nascosto fin quando non si entra
                Log("BuildInteriorNow OK tipo='" + _lazyType + "'");
            }
            finally
            {
                _inPlace = false;
            }
        }

        /// <summary>Imposta questo generatore per un edificio PREFAB (esterno
        /// Quaternius pieno, interno reale generato lazy dentro l'impronta).
        /// Deve essere chiamato una sola volta, subito dopo il posizionamento
        /// del prefab, con l'impronta OSM in metri reali.</summary>
        public void PreparePrefabExterior(string type, float w, float d,
            float shellH, Shop shop, Collider buildingCollider)
        {
            _prefabExterior = true;
            ShellW = Mathf.Max(4f, w);
            ShellD = Mathf.Max(4f, d);
            ShellH = Mathf.Max(2.8f, shellH);
            _lazyType = type;
            _lazyW = ShellW;
            _lazyD = ShellD;
            _lazyH = ShellH;
            _lazyShop = shop;
            _prefabCollider = buildingCollider;

            // Figlio contro-scalato: neutralizza la scala non uniforme del
            // prefab. I builder interni ragionano gia' in metri reali, quindi
            // _interiorRoot deve avere localToWorld = identita' (scala 1).
            var inv = new GameObject("Interno");
            inv.transform.SetParent(transform, false);
            Vector3 lossy = transform.lossyScale;
            inv.transform.localScale = new Vector3(
                lossy.x != 0f ? 1f / lossy.x : 1f,
                lossy.y != 0f ? 1f / lossy.y : 1f,
                lossy.z != 0f ? 1f / lossy.z : 1f);
            _interiorRoot = inv.transform;

            // Luce interna: sotto _interiorRoot cosi' resta spenta/nascosta
            // con l'interno (nessuna luce che filtra in strada col prefab).
            var lightGo = new GameObject("LuceInterna");
            lightGo.transform.SetParent(_interiorRoot, false);
            lightGo.transform.localPosition = new Vector3(0f, Mathf.Max(1.8f, ShellH - 0.6f), 0f);
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1f, 0.96f, 0.85f);
            light.intensity = 0.9f;
            light.range = Mathf.Max(ShellW, ShellD) * 0.7f;
            light.enabled = false;
            AttachLight(light);
            Log("PreparePrefabExterior done: type=" + type +
                " shell=" + ShellW.ToString("F1") + "x" + ShellD.ToString("F1") + "x" + ShellH.ToString("F1") +
                " shop=" + (_lazyShop != null) + " collider=" + (_prefabCollider != null));
        }

        public bool IsPrefabExterior
        {
            get { return _prefabExterior; }
        }

        /// <summary>Punto interno della soglia: poco dentro la porta.</summary>
        public Vector3 PrefabEnterLocal
        {
            get { return new Vector3(0f, 1f, ShellD * 0.5f - 1.3f); }
        }

        /// <summary>Punto esterno di uscita: poco oltre la porta (fuori).</summary>
        public Vector3 PrefabExitLocal
        {
            get { return new Vector3(0f, 1f, ShellD * 0.5f + 1.6f); }
        }

        /// <summary>Mostra l'esterno (prefab + segnaposto porta) e nasconde
        /// l'interno, oppure (show=false) nasconde l'esterno e mostra l'interno
        /// (utile mentre il player e' dentro l'edificio).</summary>
        public void SetPrefabExteriorVisible(bool show)
        {
            SetInteriorRootActive(!show);
            var rends = GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rends.Length; i++)
            {
                if (_interiorRoot != null && rends[i].transform.IsChildOf(_interiorRoot)) continue;
                rends[i].enabled = show;
            }
            if (_prefabCollider != null) _prefabCollider.enabled = show;
        }

        private void SetInteriorRootActive(bool on)
        {
            if (_interiorRoot != null && _interiorRoot.gameObject.activeSelf != on)
                _interiorRoot.gameObject.SetActive(on);
        }

        /// <summary>Radice dell'interno in-place (oggetto Interno), usata
        /// per rilevare gli arredi interattivi (letti, divani...).</summary>
        public Transform InteriorRoot { get { return _interiorRoot; } }

        // Registrazione/rimozione dalla coda lazy di InteriorManager. OnEnable
        // registra, OnDisable rimuove (chunk scaricato → edificio distrutto →
        // si pulisce da solo, niente riferimenti pendenti).
        private void OnEnable()
        {
            var m = InteriorManager.Instance;
            if (m != null) m.RegisterInterior(this);
        }

        private void OnDisable()
        {
            var m = InteriorManager.Instance;
            if (m != null) m.UnregisterInterior(this);
        }

        /// <summary>Costruisce il guscio perimetrale: 4 muri a tutta altezza
        /// con aperture reali (porta frontale + finestre sui 4 lati) e tetto.
        /// Coordinate locali centrate sull'impronta w×d.</summary>
        private void BuildShell(Transform parent, float w, float d, float shellH)
        {
            float halfW = w * 0.5f;
            float halfD = d * 0.5f;
            float thick = WALL_THICK;

            // Muro posteriore (z = -halfD), lunga w, 2 finestre
            BuildWindowedWall(parent, w, new Vector3(0f, 0f, -halfD),
                Vector3.forward, thick, shellH, windows: 2, needDoor: false);
            // Muro frontale (z = +halfD), lunga w, porta centrale + 2 finestre
            BuildWindowedWall(parent, w, new Vector3(0f, 0f, halfD),
                Vector3.forward, thick, shellH, windows: 2, needDoor: true);
            // Muro sinistro (x = -halfW), lunga d, 1 finestra
            BuildWindowedWall(parent, d, new Vector3(-halfW, 0f, 0f),
                Vector3.right, thick, shellH, windows: 1, needDoor: false);
            // Muro destro (x = +halfW), lunga d, 1 finestra
            BuildWindowedWall(parent, d, new Vector3(halfW, 0f, 0f),
                Vector3.right, thick, shellH, windows: 1, needDoor: false);

            // Tetto (lastricato opaco, conserva collider per nome "Soffitto").
            // In URP il materiale EMISSIVO fa brillare il volume interno anche
            // senza luci: visibile dall'esterno attraverso finestre/porta.
            // Lastra a tutta impronta con un piccolo aggetto (eave): da strada
            // e dall'alto l'edificio si legge come COPERTO (tetto vero) e non
            // come scatola scavata aperta.
            float roofOver = Mathf.Min(0.40f, Mathf.Max(0.15f, (w + d) * 0.03f));
            Box(parent, "Soffitto", new Vector3(0f, shellH + 0.05f, 0f),
                new Vector3(w + thick + roofOver * 2f, 0.30f,
                    d + thick + roofOver * 2f), _ceilingMat);

            // Pannello soffitto EMISSIVO appena sotto il tetto: illumina l'area
            // interna (respiro visivo) a costo quasi zero.
            Box(parent, "GlowCeiling", new Vector3(0f, shellH - 0.25f, 0f),
                new Vector3(w * 0.9f, 0.05f, d * 0.9f), _glowMat);
        }

        /// <summary>
        /// Costruisce un muro perimetrale pieno con aperture rettangolari reali.
        /// axis: verso in cui si estende la lunghezza del muro (forward per i
        /// muri a ±Z, right per quelli a ±X). needDoor: apre un varco centrale
        /// a terra largo quanto una porta. Le finestre sono varchi a mezza
        /// altezza (fra davanzale e architrave) delimitati da pilastri pieni
        /// laterali; la porta è un varco completo a terra con architrave sopra.
        /// I varchi restano APERTI (niente collider) così dall'esterno si vede
        /// l'interno e viceversa.
        /// </summary>
        private void BuildWindowedWall(Transform parent, float length,
            Vector3 center, Vector3 axis, float thick, float shellH,
            int windows, bool needDoor)
        {
            bool alongX = Mathf.Abs(axis.x) > 0.5f;
            float half = length * 0.5f;
            float winW = Mathf.Min(1.8f, length * 0.22f);

            // Varchi lungo l'asse: (lo, hi, isDoor). Serve per poi costruire
            // pilastri pieni tra i varchi e davanzale/architrave per ogni varco.
            var gaps = new List<(float, float, bool)>();
            if (needDoor)
            {
                float doorHalf = SHELL_DOOR_W * 0.5f;
                gaps.Add((-doorHalf, doorHalf, true));
            }
            float winHalf = winW * 0.5f;
            for (int i = 0; i < windows; i++)
            {
                float c = (i + 1) * length / (windows + 1f) - half;
                if (needDoor && Mathf.Abs(c) < SHELL_DOOR_W) continue;
                gaps.Add((c - winHalf, c + winHalf, false));
            }

            gaps.Sort((a, b) => a.Item1.CompareTo(b.Item1));

            // Pilastri pieni (a tutta altezza) tra i varchi e ai bordi.
            float start = -half;
            for (int g = 0; g < gaps.Count; g++)
            {
                float lo = gaps[g].Item1;
                if (lo > start + 0.05f)
                {
                    float segLen = lo - start;
                    float segCenter = (start + lo) * 0.5f;
                    AddSolidWall(parent, alongX, center, segCenter, segLen,
                        thick, shellH);
                }
                start = gaps[g].Item2 > start ? gaps[g].Item2 : start;
            }
            if (start < half - 0.05f)
            {
                float segLen = half - start;
                float segCenter = (start + half) * 0.5f;
                AddSolidWall(parent, alongX, center, segCenter, segLen,
                    thick, shellH);
            }

            // Per ogni varco: davanzale + architrave (finestre) oppure solo
            // architrave (porta, aperto a terra).
            foreach (var gap in gaps)
            {
                float lo = gap.Item1;
                float hi = gap.Item2;
                float mid = (lo + hi) * 0.5f;
                float w2 = (hi - lo) * 0.5f;

                if (gap.Item3) // porta: architrave sopra SHELL_DOOR_H
                {
                    float above = shellH - SHELL_DOOR_H;
                    if (above > 0.05f)
                        AddSolidWallBand(parent, alongX, center, mid, w2,
                            SHELL_DOOR_H + above * 0.5f, above, thick);
                }
                else // finestra: davanzale sopra e architrave sotto, varco a metà
                {
                    float sillH = SHELL_WIN_BASE;
                    if (sillH > 0.05f)
                        AddSolidWallBand(parent, alongX, center, mid, w2,
                            sillH * 0.5f, sillH, thick);
                    float topThick = shellH - SHELL_WIN_TOP;
                    if (topThick > 0.05f)
                        AddSolidWallBand(parent, alongX, center, mid, w2,
                            SHELL_WIN_TOP + topThick * 0.5f, topThick, thick);
                }
            }
        }

        /// <summary>Segmento di muro a tutta altezza (pilastro / bordo).</summary>
        private void AddSolidWall(Transform parent, bool alongX, Vector3 center,
            float segCenter, float segLen, float thick, float shellH)
        {
            if (segLen <= 0.01f) return;
            Vector3 pos = alongX
                ? new Vector3(center.x + segCenter, shellH * 0.5f, center.z)
                : new Vector3(center.x, shellH * 0.5f, center.z + segCenter);
            Vector3 scale = alongX
                ? new Vector3(segLen, shellH, thick)
                : new Vector3(thick, shellH, segLen);
            Box(parent, "MuroShell", pos, scale, _wallMat);
        }

        /// <summary>Fascia di muro piena con altezza bandH centrata su bandY
        /// (davanzali e architravi sopra/sotto i varchi).</summary>
        private void AddSolidWallBand(Transform parent, bool alongX, Vector3 center,
            float segCenter, float halfLen, float bandY, float bandH, float thick)
        {
            if (bandH <= 0.01f) return;
            Vector3 pos = alongX
                ? new Vector3(center.x + segCenter, bandY, center.z)
                : new Vector3(center.x, bandY, center.z + segCenter);
            Vector3 scale = alongX
                ? new Vector3(halfLen * 2f, bandH, thick)
                : new Vector3(thick, bandH, halfLen * 2f);
            Box(parent, "MuroShell", pos, scale, _wallMat);
        }


        public Vector3 GetStairPosition(Transform interiorRoot, int floor)
        {
            Transform floorObj = interiorRoot.Find("Floor_" + floor);
            if (floorObj == null) return new Vector3(0f, 500f + 0.1f, 0f);

            Transform stair = floorObj.Find("Scala");
            if (stair != null)
            {
                // Usa world position (gia' scalata dal root) per X e Z,
                // e calcola Y in base all'altezza reale del piano
                return new Vector3(stair.position.x, stair.position.y + 0.1f, stair.position.z);
            }

            // Fallback: usa la posizione del Floor object (gia' scalata dal root)
            return new Vector3(1f, floorObj.position.y + 0.1f, -1f);
        }

        // ── HOUSE ──────────────────────────────────────────────────

        private void BuildHouse(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 3);

            for (int f = 0; f < totalFloors; f++)
            {
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                float yBase = 0f;

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yBase, 0f),
                    new Vector3(w, 0.15f, d), _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yBase + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yBase, true);

                if (f == 0)
                {
                    // ── Piano terra: sogorno + cucina + bagno ──

                    // Parete divisoria sogorno/cucina (a metà depth)
                    float divZ = -d * 0.1f;
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yBase + floorH * 0.5f, divZ),
                        new Vector3(w * 0.9f, floorH, WALL_THICK), _wallMat);

                    // Porta tra sogorno e cucina
                    Box(floorGo.transform, "PortaInt", new Vector3(w * 0.2f, yBase + 1.1f, divZ),
                        new Vector3(1.0f, 2.2f, WALL_THICK + 0.05f), _doorMat);

                    // ── Sogorno (fronte) ──
                    float sogornoZ = d * 0.25f;

                    // Divano
                    Furniture(floorGo.transform, "Divano", new Vector3(-w * 0.25f, yBase + 0.4f, sogornoZ + 0.3f),
                        new Vector3(2.5f, 0.8f, 1.0f));

                    // Tavolino
                    Furniture(floorGo.transform, "Tavolino", new Vector3(0f, yBase + 0.35f, sogornoZ),
                        new Vector3(1.0f, 0.3f, 0.6f));

                    // TV (parete laterale)
                    Furniture(floorGo.transform, "TV", new Vector3(w * 0.45f, yBase + 1.3f, sogornoZ),
                        new Vector3(0.6f, 0.6f, 1.0f));

                    // Lampada
                    Furniture(floorGo.transform, "Lampada", new Vector3(-w * 0.4f, yBase + 1.0f, sogornoZ),
                        new Vector3(0.5f, 1.0f, 0.5f));

                    // ── Cucina (retro) ──
                    float cucinaZ = -d * 0.3f;

                    // Frigo
                    Furniture(floorGo.transform, "Frigo", new Vector3(-w * 0.4f, yBase + 0.9f, cucinaZ),
                        new Vector3(0.8f, 1.8f, 0.7f));

                    // Piano cottura
                    Furniture(floorGo.transform, "Forno", new Vector3(0f, yBase + 0.45f, cucinaZ - d * 0.15f),
                        new Vector3(1.2f, 0.9f, 0.6f));

                    // Lavello
                    Furniture(floorGo.transform, "Lavello", new Vector3(w * 0.3f, yBase + 0.45f, cucinaZ - d * 0.15f),
                        new Vector3(0.8f, 0.5f, 0.5f));

                    // Tavolo da pranzo
                    Furniture(floorGo.transform, "Tavolo", new Vector3(w * 0.15f, yBase + 0.38f, cucinaZ + 0.5f),
                        new Vector3(1.4f, 0.7f, 0.8f));

                    // Sedie (2)
                    Furniture(floorGo.transform, "Sedia1", new Vector3(w * 0.15f - 0.5f, yBase + 0.22f, cucinaZ + 0.5f + 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "Sedia2", new Vector3(w * 0.15f + 0.5f, yBase + 0.22f, cucinaZ + 0.5f - 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // ── Scala (angolo) ──
                    BuildStairs(floorGo.transform, w * 0.35f, yBase, floorH, d * 0.35f);

                    // Trigger scala (piano terra, come shop/apartment)
                    if (totalFloors > 1)
                        BuildStairTrigger(floorGo.transform, w * 0.35f, yBase, d * 0.35f, 0, totalFloors);

                    // ── Bagno (angolo opposto) ──
                    float bagnoX = -w * 0.35f;
                    float bagnoZ = -d * 0.35f;

                    // Parete bagno
                    Box(floorGo.transform, "PareteBagno1", new Vector3(bagnoX + 0.8f, yBase + floorH * 0.5f, bagnoZ + 0.6f),
                        new Vector3(WALL_THICK, floorH, 1.2f), _wallMat);
                    Box(floorGo.transform, "PareteBagno2", new Vector3(bagnoX, yBase + floorH * 0.5f, bagnoZ + 1.2f),
                        new Vector3(1.6f, floorH, WALL_THICK), _wallMat);

                    // Pavimento bagno (piastrelle)
                    Box(floorGo.transform, "PavBagno", new Vector3(bagnoX, yBase + 0.08f, bagnoZ + 0.6f),
                        new Vector3(1.4f, 0.08f, 1.0f), _tileMat);

                    // WC
                    Furniture(floorGo.transform, "WC", new Vector3(bagnoX - 0.3f, yBase + 0.25f, bagnoZ + 0.3f),
                        new Vector3(0.4f, 0.5f, 0.5f));

                    // Lavandino
                    Furniture(floorGo.transform, "Lavandino", new Vector3(bagnoX + 0.3f, yBase + 0.4f, bagnoZ + 0.3f),
                        new Vector3(0.5f, 0.5f, 0.4f));

                    // ── Porta d'ingresso (sul muro frontale) ──
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yBase + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.2f, 2.2f, 0.15f), _doorMat);

                    // Etichetta "USCITA"
                    AddLabel(floorGo.transform, "USCITA", new Vector3(0f, yBase + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(0.8f, 0.3f, 0.02f));

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yBase);
                }
                else
                {
                    // ── Piani superiori: 2 camere ──

                    // Parete divisoria
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yBase + floorH * 0.5f, 0f),
                        new Vector3(w * 0.9f, floorH, WALL_THICK), _wallMat);

                    // Porta camera 1
                    Box(floorGo.transform, "Porta1", new Vector3(w * 0.2f, yBase + 1.1f, 0f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Porta camera 2
                    Box(floorGo.transform, "Porta2", new Vector3(-w * 0.2f, yBase + 1.1f, 0f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // ── Camera 1 (fronte) ──
                    float cam1Z = d * 0.25f;

                    // Letto matrimoniale
                    Furniture(floorGo.transform, "LettoMat", new Vector3(-w * 0.15f, yBase + 0.3f, cam1Z),
                        new Vector3(1.8f, 0.6f, 2.0f));

                    // Comodino
                    Furniture(floorGo.transform, "Comodino1", new Vector3(-w * 0.4f, yBase + 0.35f, cam1Z + 0.5f),
                        new Vector3(0.4f, 0.5f, 0.35f));

                    // Lampada comodino
                    Furniture(floorGo.transform, "LampCom1", new Vector3(-w * 0.4f, yBase + 0.7f, cam1Z + 0.5f),
                        new Vector3(0.3f, 0.4f, 0.3f));

                    // Armadio
                    Furniture(floorGo.transform, "Armadio1", new Vector3(w * 0.4f, yBase + 0.9f, cam1Z),
                        new Vector3(1.0f, 1.8f, 0.5f));

                    // ── Camera 2 (retro) ──
                    float cam2Z = -d * 0.25f;

                    // Letto singolo
                    Furniture(floorGo.transform, "LettoSing", new Vector3(w * 0.15f, yBase + 0.25f, cam2Z),
                        new Vector3(1.0f, 0.5f, 2.0f));

                    // Scrivania
                    Furniture(floorGo.transform, "Scrivania", new Vector3(-w * 0.3f, yBase + 0.38f, cam2Z - 0.3f),
                        new Vector3(1.2f, 0.7f, 0.6f));

                    // Sedia scrivania
                    Furniture(floorGo.transform, "SediaScriv", new Vector3(-w * 0.3f, yBase + 0.22f, cam2Z + 0.3f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // Libreria
                    Furniture(floorGo.transform, "Libreria", new Vector3(-w * 0.4f, yBase + 0.9f, cam2Z + 0.4f),
                        new Vector3(0.8f, 1.8f, 0.35f));

                    // Scala (stessa posizione del piano terra)
                    BuildStairs(floorGo.transform, w * 0.35f, yBase, floorH, d * 0.35f);

                    // Trigger scale (su/giù)
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yBase, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── SHOP ───────────────────────────────────────────────────

        private void BuildShop(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 3);

            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yf, 0f),
                    new Vector3(w, 0.15f, d), _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yf + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yf, true);

                if (f == 0)
                {
                    // ── Piano terra: sala vendita + bancone ──

                    // Bancone cassa (retro)
                    Furniture(floorGo.transform, "Bancone", new Vector3(0f, yf + 0.5f, -d * 0.35f),
                        new Vector3(w * 0.6f, 1.0f, 0.7f));

                    // Scaffali (lungo le pareti laterali)
                    int numShelves = Mathf.Max(2, Mathf.FloorToInt(d / 3f));
                    float shelfSpacing = d * 0.6f / numShelves;
                    for (int s = 0; s < numShelves; s++)
                    {
                        float sz = -d * 0.1f + s * shelfSpacing;
                        Box(floorGo.transform, "ScaffaleS" + s,
                            new Vector3(-w * 0.38f, yf + 0.9f, sz),
                            new Vector3(0.5f, 1.8f, 0.3f), _shelfMat);

                        // Prodotti sugli scaffali (cubetti colorati)
                        for (int p = 0; p < 3; p++)
                        {
                            float px = -w * 0.38f + (p - 1) * 0.12f;
                            float py = yf + 0.6f + p * 0.5f;
                            Material prodMat = Lit(new Color(
                                0.3f + (s * 0.15f + p * 0.1f) % 0.7f,
                                0.5f + (s * 0.1f) % 0.4f,
                                0.4f + (p * 0.2f) % 0.5f));
                            Box(floorGo.transform, "Prodotto" + s + "_" + p,
                                new Vector3(px, py, sz),
                                new Vector3(0.18f, 0.25f, 0.15f), prodMat);
                        }
                    }

                    // Vetrina (fronte, parete trasparente)
                    Box(floorGo.transform, "Vetrina", new Vector3(0f, yf + 1.5f, d * 0.49f),
                        new Vector3(w * 0.7f, 1.8f, 0.05f), _glassMat);

                    // Porta d'ingresso
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yf + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.2f, 2.2f, 0.15f), _doorMat);

                    // Etichetta nome negozio
                    string shopName = shop != null ? shop.shopName : "NEGOZIO";
                    AddLabel(floorGo.transform, shopName, new Vector3(0f, yf + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(Mathf.Max(1.5f, shopName.Length * 0.25f), 0.35f, 0.02f));

                    // Se c'è un shop, crea un counter per aprire lo shop UI
                    if (shop != null)
                    {
                        var counterGo = new GameObject("ShopCounter");
                        counterGo.transform.SetParent(floorGo.transform, false);
                        counterGo.transform.localPosition = new Vector3(0f, yf + 0.5f, -d * 0.15f);
                        var col = counterGo.AddComponent<BoxCollider>();
                        col.isTrigger = true;
                        col.size = new Vector3(2.5f, 2f, 1.5f);
                        var shopTrigger = counterGo.AddComponent<ShopCounterTrigger>();
                        shopTrigger.shop = shop;
                    }

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yf);

                    // Scala se ci sono piani superiori
                    if (totalFloors > 1)
                    {
                        BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                        BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, 0, totalFloors);
                    }
                }
                else
                {
                    // ── Piani superiori: magazzino / ufficio ──

                    // Scaffali magazzino
                    for (int s = 0; s < 4; s++)
                    {
                        float sz = -d * 0.3f + s * d * 0.2f;
                        Box(floorGo.transform, "MagScaffale" + s,
                            new Vector3(-w * 0.35f, yf + 0.9f, sz),
                            new Vector3(0.6f, 1.8f, 0.4f), _shelfMat);
                    }

                    // Tavolo ufficio
                    Furniture(floorGo.transform, "TavoloUff", new Vector3(w * 0.2f, yf + 0.38f, 0f),
                        new Vector3(1.2f, 0.7f, 0.7f));

                    // Sedia
                    Furniture(floorGo.transform, "SediaUff", new Vector3(w * 0.2f, yf + 0.22f, 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // Computer
                    Furniture(floorGo.transform, "PC", new Vector3(w * 0.2f, yf + 0.5f, -0.15f),
                        new Vector3(0.5f, 0.35f, 0.4f));

                    // Scala
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── APARTMENT ──────────────────────────────────────────────

        private void BuildApartment(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 2, 5);

            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yf, 0f),
                    new Vector3(w, 0.15f, d), f == 0 ? _tileMat : _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yf + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yf, true);

                // Scala al centro
                BuildStairs(floorGo.transform, 0f, yf, floorH, d * 0.2f);

                if (f == 0)
                {
                    // ── Piano terra: ingresso + portineria ──

                    // Parete interna (separa ingresso da scala)
                    Box(floorGo.transform, "PareteInterna", new Vector3(-w * 0.15f, yf + floorH * 0.5f, 0f),
                        new Vector3(WALL_THICK, floorH, d * 0.6f), _wallMat);

                    // Porta scala
                    Box(floorGo.transform, "PortaScala", new Vector3(-w * 0.15f, yf + 1.1f, d * 0.15f),
                        new Vector3(WALL_THICK + 0.05f, 2.2f, 1.0f), _doorMat);

                    // Cassetta postale
                    Box(floorGo.transform, "Poste", new Vector3(w * 0.35f, yf + 1.2f, -d * 0.35f),
                        new Vector3(0.8f, 1.2f, 0.2f), _woodMat);

                    // Panca
                    Furniture(floorGo.transform, "Panca", new Vector3(w * 0.35f, yf + 0.22f, -d * 0.1f),
                        new Vector3(1.2f, 0.44f, 0.4f));

                    // Porta d'ingresso
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yf + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.4f, 2.4f, 0.15f), _doorMat);

                    // Etichetta
                    AddLabel(floorGo.transform, "INGRESSO", new Vector3(0f, yf + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(1.2f, 0.3f, 0.02f));

                    // Trigger scale (solo su)
                    BuildStairTrigger(floorGo.transform, 0f, yf, d * 0.2f, 0, totalFloors);

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yf);
                }
                else
                {
                    // ── Piani superiori: 2 appartamenti per piano ──

                    // Parete divisoria centrale (separa appartamento A da B)
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yf + floorH * 0.5f, 0f),
                        new Vector3(WALL_THICK, floorH, d * 0.9f), _wallMat);

                    // ── Appartamento A (destro) ──
                    float aptW = w * 0.45f;
                    float aptX = w * 0.25f;

                    // Porta ingresso A
                    Box(floorGo.transform, "PortaA", new Vector3(aptX, yf + 1.1f, d * 0.15f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Sogorno A
                    Furniture(floorGo.transform, "DivanoA", new Vector3(aptX, yf + 0.35f, -d * 0.15f),
                        new Vector3(1.8f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "TavoloA", new Vector3(aptX, yf + 0.32f, -d * 0.05f),
                        new Vector3(0.8f, 0.4f, 0.5f));
                    Furniture(floorGo.transform, "TVA", new Vector3(aptX + aptW * 0.4f, yf + 1.3f, -d * 0.15f),
                        new Vector3(0.5f, 0.5f, 0.8f));

                    // Camera A
                    Furniture(floorGo.transform, "LettoA", new Vector3(aptX, yf + 0.28f, d * 0.3f),
                        new Vector3(1.5f, 0.55f, 1.8f));
                    Furniture(floorGo.transform, "ArmadioA", new Vector3(aptX + aptW * 0.35f, yf + 0.9f, d * 0.3f),
                        new Vector3(0.8f, 1.8f, 0.4f));

                    // Bagno A
                    Furniture(floorGo.transform, "WCA", new Vector3(aptX + aptW * 0.35f, yf + 0.25f, -d * 0.35f),
                        new Vector3(0.4f, 0.5f, 0.45f));
                    Furniture(floorGo.transform, "LavA", new Vector3(aptX + aptW * 0.15f, yf + 0.4f, -d * 0.35f),
                        new Vector3(0.45f, 0.5f, 0.35f));

                    // ── Appartamento B (sinistro) ──
                    float aptBX = -w * 0.25f;

                    // Porta ingresso B
                    Box(floorGo.transform, "PortaB", new Vector3(aptBX, yf + 1.1f, d * 0.15f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Sogorno B
                    Furniture(floorGo.transform, "DivanoB", new Vector3(aptBX, yf + 0.35f, -d * 0.15f),
                        new Vector3(1.8f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "TavoloB", new Vector3(aptBX, yf + 0.32f, -d * 0.05f),
                        new Vector3(0.8f, 0.4f, 0.5f));

                    // Camera B
                    Furniture(floorGo.transform, "LettoB", new Vector3(aptBX, yf + 0.28f, d * 0.3f),
                        new Vector3(1.5f, 0.55f, 1.8f));
                    Furniture(floorGo.transform, "ArmadioB", new Vector3(aptBX - aptW * 0.35f, yf + 0.9f, d * 0.3f),
                        new Vector3(0.8f, 1.8f, 0.4f));

                    // Bagno B
                    Furniture(floorGo.transform, "WCB", new Vector3(aptBX - aptW * 0.35f, yf + 0.25f, -d * 0.35f),
                        new Vector3(0.4f, 0.5f, 0.45f));

                    // Trigger scale (su e giù)
                    BuildStairTrigger(floorGo.transform, 0f, yf, d * 0.2f, f, totalFloors);
                }
            }
        }

        // ── SHARED SHELL (POI) ─────────────────────────────────────
        // I layout dei POI condividono lo stesso involucro (pavimento,
        // soffitto, muri perimetrali, porta ingresso, etichetta USCITA,
        // trigger uscita) e cambiano solo gli arredi interni.

        private void BuildPoiShell(Transform parent, float w, float d,
            float floorH, float yBase, int totalFloors, string title)
        {
            Box(parent, "Pavimento", new Vector3(0f, yBase, 0f),
                new Vector3(w, 0.15f, d), _floorMat);
            Box(parent, "Soffitto", new Vector3(0f, yBase + floorH, 0f),
                new Vector3(w, 0.1f, d), _ceilingMat);
            BuildWalls(parent, w, d, floorH, yBase, true);
            Box(parent, "PortaIngresso", new Vector3(0f, yBase + 1.1f, d * 0.5f + 0.05f),
                new Vector3(1.2f, 2.2f, 0.15f), _doorMat);
            AddLabel(parent, title, new Vector3(0f, yBase + 2.8f, d * 0.5f + 0.1f),
                new Vector3(Mathf.Max(1.5f, title.Length * 0.25f), 0.35f, 0.02f));
            BuildExitTrigger(parent, w, d, yBase);
        }

        // ── BAR ────────────────────────────────────────────────────

        private void BuildBar(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 2);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                if (f == 0)
                {
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "BAR");

                    // Bancone bar (lungo il lato destro)
                    Furniture(floorGo.transform, "Bancone", new Vector3(w * 0.35f, yf + 0.5f, -d * 0.1f),
                        new Vector3(w * 0.3f, 1.0f, d * 0.6f));

                    // Scaffale bottiglie dietro al bancone
                    Furniture(floorGo.transform, "Scaffale", new Vector3(w * 0.45f, yf + 0.9f, -d * 0.25f),
                        new Vector3(0.4f, 1.8f, d * 0.4f));

                    // Tavolini con sedie disseminate
                    Furniture(floorGo.transform, "Tavolo", new Vector3(-w * 0.25f, yf + 0.38f, d * 0.15f),
                        new Vector3(1.2f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "Sedia1", new Vector3(-w * 0.25f - 0.5f, yf + 0.22f, d * 0.15f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "Sedia2", new Vector3(-w * 0.25f + 0.5f, yf + 0.22f, d * 0.15f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "TavoloA", new Vector3(-w * 0.25f, yf + 0.38f, -d * 0.35f),
                        new Vector3(1.2f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "SediaA1", new Vector3(-w * 0.25f - 0.5f, yf + 0.22f, -d * 0.35f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "SediaA2", new Vector3(-w * 0.25f + 0.5f, yf + 0.22f, -d * 0.35f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    if (totalFloors > 1)
                    {
                        BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                        BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, 0, totalFloors);
                    }
                }
                else
                {
                    // Piano superiore: saletta privata
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "PRIVATO");
                    Furniture(floorGo.transform, "Divano", new Vector3(-w * 0.2f, yf + 0.35f, 0f),
                        new Vector3(2.0f, 0.8f, 0.9f));
                    Furniture(floorGo.transform, "Tavolino", new Vector3(-w * 0.2f, yf + 0.3f, 0.5f),
                        new Vector3(0.8f, 0.4f, 0.5f));
                    Furniture(floorGo.transform, "Tavolo", new Vector3(w * 0.25f, yf + 0.38f, 0f),
                        new Vector3(1.4f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "Sedia1", new Vector3(w * 0.25f, yf + 0.22f, 0.5f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "Sedia2", new Vector3(w * 0.25f, yf + 0.22f, -0.5f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── HOSPITAL ───────────────────────────────────────────────

        private void BuildHospital(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 2);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors,
                    f == 0 ? "OSPEDALE" : "REPARTO");

                if (f == 0)
                {
                    // Accettazione (davanti)
                    Furniture(floorGo.transform, "Bancone", new Vector3(0f, yf + 0.5f, d * 0.3f),
                        new Vector3(w * 0.5f, 1.0f, 0.6f));

                    // Parete divisoria accettazione/reparto
                    float divZ = -d * 0.1f;
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yf + floorH * 0.5f, divZ),
                        new Vector3(w * 0.9f, floorH, WALL_THICK), _wallMat);

                    // Due letti da visita (retro)
                    Furniture(floorGo.transform, "LettoA", new Vector3(-w * 0.25f, yf + 0.28f, -d * 0.3f),
                        new Vector3(1.0f, 0.5f, 2.0f));
                    Furniture(floorGo.transform, "LettoB", new Vector3(w * 0.25f, yf + 0.28f, -d * 0.3f),
                        new Vector3(1.0f, 0.5f, 2.0f));

                    // Armadietti medicinali
                    Furniture(floorGo.transform, "Armadio1", new Vector3(-w * 0.42f, yf + 0.9f, -d * 0.2f),
                        new Vector3(0.6f, 1.8f, 0.4f));
                    Furniture(floorGo.transform, "Armadio2", new Vector3(w * 0.42f, yf + 0.9f, -d * 0.2f),
                        new Vector3(0.6f, 1.8f, 0.4f));
                }
                else
                {
                    // Reparto con 3 letti
                    for (int i = 0; i < 3; i++)
                    {
                        float bx = -w * 0.3f + i * w * 0.3f;
                        Furniture(floorGo.transform, "Letto" + i, new Vector3(bx, yf + 0.28f, -d * 0.15f),
                            new Vector3(1.0f, 0.5f, 2.0f));
                        Furniture(floorGo.transform, "Comodino" + i, new Vector3(bx, yf + 0.35f, d * 0.2f),
                            new Vector3(0.4f, 0.5f, 0.35f));
                    }
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── DEALER (concessionaria) ────────────────────────────────

        private void BuildDealer(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 2);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                if (f == 0)
                {
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "CONCESSIONARIA");

                    // Ufficio vendite (retro)
                    Furniture(floorGo.transform, "Bancone", new Vector3(0f, yf + 0.5f, -d * 0.3f),
                        new Vector3(w * 0.5f, 1.0f, 0.6f));
                    Furniture(floorGo.transform, "Scrivania", new Vector3(-w * 0.3f, yf + 0.38f, -d * 0.15f),
                        new Vector3(1.2f, 0.7f, 0.6f));
                    Furniture(floorGo.transform, "SediaUff", new Vector3(-w * 0.3f, yf + 0.22f, d * 0.05f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // Showroom: 2 pedane con auto in vetrina
                    Furniture(floorGo.transform, "Libreria", new Vector3(w * 0.4f, yf + 0.35f, d * 0.2f),
                        new Vector3(2.0f, 0.7f, 0.3f));
                    Furniture(floorGo.transform, "Divano", new Vector3(w * 0.25f, yf + 0.4f, d * 0.35f),
                        new Vector3(2.0f, 0.8f, 0.9f));
                    Furniture(floorGo.transform, "Tavolino", new Vector3(w * 0.25f, yf + 0.35f, d * 0.0f),
                        new Vector3(0.8f, 0.3f, 0.5f));
                    BuildVehicleCounter(floorGo.transform, w, d, yf);
                }
                else
                {
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "UFFICI");
                    Furniture(floorGo.transform, "TavoloUff", new Vector3(-w * 0.2f, yf + 0.38f, 0f),
                        new Vector3(1.4f, 0.7f, 0.7f));
                    Furniture(floorGo.transform, "SediaUff", new Vector3(-w * 0.2f, yf + 0.22f, 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "Divano", new Vector3(w * 0.3f, yf + 0.35f, 0f),
                        new Vector3(2.0f, 0.8f, 0.9f));
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── REPAIR / GARAGE (officina) ─────────────────────────────

        private void BuildWorkshop(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 2);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                if (f == 0)
                {
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "OFFICINA");

                    // Ponte di sollevamento con vettura in lavorazione (scatola scura)
                    Box(floorGo.transform, "Ponte", new Vector3(-w * 0.25f, yf + 0.5f, 0f),
                        new Vector3(0.8f, 1.0f, 1.8f), _darkMat);
                    Box(floorGo.transform, "AutoInLavoro", new Vector3(-w * 0.25f, yf + 0.2f, 0f),
                        new Vector3(2.0f, 0.4f, 1.0f), _darkMat);

                    // Banco attrezzi
                    Furniture(floorGo.transform, "Bancone", new Vector3(w * 0.35f, yf + 0.45f, 0f),
                        new Vector3(w * 0.3f, 0.9f, 0.6f));

                    // Scaffali ricambi
                    Furniture(floorGo.transform, "Scaffale", new Vector3(-w * 0.42f, yf + 0.9f, -d * 0.25f),
                        new Vector3(0.5f, 1.8f, d * 0.35f));
                    Furniture(floorGo.transform, "ScaffaleA", new Vector3(w * 0.42f, yf + 0.9f, -d * 0.3f),
                        new Vector3(0.5f, 1.8f, d * 0.3f));

                    // Scrivania accettazione
                    Furniture(floorGo.transform, "Scrivania", new Vector3(0f, yf + 0.38f, -d * 0.4f),
                        new Vector3(1.4f, 0.7f, 0.6f));
                    Furniture(floorGo.transform, "SediaUff", new Vector3(0f, yf + 0.22f, -d * 0.4f + 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    BuildVehicleCounter(floorGo.transform, w, d, yf);
                }
                else
                {
                    BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors, "DEPOSITO");
                    for (int i = 0; i < 4; i++)
                    {
                        Furniture(floorGo.transform, "Scaffale" + i, new Vector3(-w * 0.35f, yf + 0.9f, -d * 0.3f + i * d * 0.2f),
                            new Vector3(0.6f, 1.8f, 0.4f));
                    }
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── BANK ───────────────────────────────────────────────────

        private void BuildBank(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 2);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors,
                    f == 0 ? "BANCA" : "UFFICI");

                if (f == 0)
                {
                    // Bancone cassa con vetro protettivo
                    Furniture(floorGo.transform, "Bancone", new Vector3(-w * 0.3f, yf + 0.6f, -d * 0.3f),
                        new Vector3(w * 0.4f, 1.2f, 0.6f));

                    // Postazioni ATM / scrivanie consulenti
                    for (int i = 0; i < 2; i++)
                    {
                        float zx = -w * 0.2f + i * w * 0.4f;
                        Furniture(floorGo.transform, "Scrivania" + i, new Vector3(zx, yf + 0.38f, d * 0.25f),
                            new Vector3(1.2f, 0.7f, 0.6f));
                        Furniture(floorGo.transform, "Sedia" + i, new Vector3(zx, yf + 0.22f, d * 0.5f),
                            new Vector3(0.5f, 0.44f, 0.5f));
                    }

                    // Sala d'attesa con sedie
                    Furniture(floorGo.transform, "Divano", new Vector3(w * 0.35f, yf + 0.35f, -d * 0.1f),
                        new Vector3(1.8f, 0.7f, 0.8f));
                }
                else
                {
                    Furniture(floorGo.transform, "TavoloUff", new Vector3(-w * 0.2f, yf + 0.38f, 0f),
                        new Vector3(1.4f, 0.7f, 0.7f));
                    Furniture(floorGo.transform, "SediaUff", new Vector3(-w * 0.2f, yf + 0.22f, 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── SCHOOL ─────────────────────────────────────────────────

        private void BuildSchool(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 3);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors,
                    f == 0 ? "SCUOLA" : "CLASSE " + f);

                if (f == 0)
                {
                    // Atrio + banco docente
                    Furniture(floorGo.transform, "Bancone", new Vector3(-w * 0.35f, yf + 0.5f, d * 0.3f),
                        new Vector3(w * 0.3f, 1.0f, 0.6f));
                    Furniture(floorGo.transform, "Libreria", new Vector3(w * 0.35f, yf + 0.9f, d * 0.3f),
                        new Vector3(0.8f, 1.8f, 0.4f));
                }
                else
                {
                    // Aula: cattedra + banchi in fila
                    Furniture(floorGo.transform, "Scrivania", new Vector3(0f, yf + 0.42f, d * 0.3f),
                        new Vector3(1.6f, 0.8f, 0.6f));
                    for (int row = 0; row < 2; row++)
                    {
                        for (int col = 0; col < 2; col++)
                        {
                            float bx = -w * 0.2f + col * w * 0.35f;
                            float bz = -d * 0.1f - row * d * 0.3f;
                            Furniture(floorGo.transform, "Scr" + row + "_" + col,
                                new Vector3(bx, yf + 0.38f, bz),
                                new Vector3(0.9f, 0.7f, 0.5f));
                            Furniture(floorGo.transform, "Sed" + row + "_" + col,
                                new Vector3(bx, yf + 0.22f, bz - 0.5f),
                                new Vector3(0.5f, 0.44f, 0.5f));
                        }
                    }
                    // Lavagna sulla parete frontale
                    Box(floorGo.transform, "Lavagna", new Vector3(0f, yf + 1.6f, d * 0.5f + 0.03f),
                        new Vector3(w * 0.6f, 1.2f, 0.05f), _darkMat);
                }

                if (totalFloors > 1 && f < totalFloors - 1)
                {
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── HOTEL ──────────────────────────────────────────────────

        private void BuildHotel(Transform parent, float w, float d, float floorH, int floors)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 4);
            for (int f = 0; f < totalFloors; f++)
            {
                float yf = f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                BuildPoiShell(floorGo.transform, w, d, floorH, yf, totalFloors,
                    f == 0 ? "HOTEL" : "PIANO " + f);

                if (f == 0)
                {
                    // Reception (retro)
                    Furniture(floorGo.transform, "Bancone", new Vector3(0f, yf + 0.5f, -d * 0.35f),
                        new Vector3(w * 0.5f, 1.0f, 0.6f));

                    // Lobby: divani + tavolino
                    Furniture(floorGo.transform, "Divano", new Vector3(-w * 0.3f, yf + 0.35f, d * 0.2f),
                        new Vector3(2.0f, 0.8f, 0.9f));
                    Furniture(floorGo.transform, "Tavolino", new Vector3(-w * 0.3f, yf + 0.3f, -0.05f),
                        new Vector3(0.8f, 0.4f, 0.5f));
                    Furniture(floorGo.transform, "DivanoA", new Vector3(w * 0.3f, yf + 0.35f, d * 0.2f),
                        new Vector3(2.0f, 0.8f, 0.9f));
                }
                else
                {
                    // Corridoio con 2-3 camere
                    for (int i = 0; i < 3; i++)
                    {
                        float x = -w * 0.3f + i * w * 0.3f;
                        Furniture(floorGo.transform, "Letto" + i, new Vector3(x, yf + 0.28f, -d * 0.15f),
                            new Vector3(1.0f, 0.5f, 1.8f));
                        Furniture(floorGo.transform, "Comodino" + i, new Vector3(x, yf + 0.35f, d * 0.2f),
                            new Vector3(0.4f, 0.5f, 0.35f));
                        Furniture(floorGo.transform, "Armadio" + i, new Vector3(x, yf + 0.9f, d * 0.35f),
                            new Vector3(0.6f, 1.8f, 0.4f));
                    }
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── Bancone POI veicolo (apre il menu veicolo) ─────────────

        private void BuildVehicleCounter(Transform floorGo, float w, float d, float yBase)
        {
            var counterGo = new GameObject("VehicleCounter");
            counterGo.transform.SetParent(floorGo.transform, false);
            counterGo.transform.localPosition = new Vector3(0f, yBase + 0.5f, -d * 0.35f);
            var col = counterGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(2.5f, 2f, 1.5f);
            counterGo.AddComponent<VehicleCounterTrigger>();
        }

        // ── MURI PERIMETRALI ───────────────────────────────────────

        private void BuildWalls(Transform floorGo, float w, float d, float floorH, float yBase, bool withDoorGap)
        {
            // In-place: i muri perimetrali sono gia' costruiti dal guscio
            // (BuildShell), completo di porte e finestre reali. Niente muri
            // per-piano, altrimenti chiuderebbero le viste.
            if (_inPlace) return;

            float ht = floorH * 0.5f;
            float wallY = yBase + ht;
            float halfW = w * 0.5f;
            float halfD = d * 0.5f;
            float thick = 0.2f;

            // Parete posteriore (nord)
            Box(floorGo, "MuroN", new Vector3(0f, wallY, -halfD),
                new Vector3(w, floorH, thick), _wallMat);

            // Parete sinistra
            Box(floorGo, "MuroO", new Vector3(-halfW, wallY, 0f),
                new Vector3(thick, floorH, d), _wallMat);

            // Parete destra
            Box(floorGo, "MuroE", new Vector3(halfW, wallY, 0f),
                new Vector3(thick, floorH, d), _wallMat);

            // Parete frontale (sud) con finestre
            if (withDoorGap)
            {
                // Lato sinistro del muro frontale
                // Larghezza muro ridotta e baricentro spostato verso gli
                // spigoli: il varco della porta passa da 0.25*halfW a 0.60*halfW
                // (0.30*w) per lasciar passare la capsula del player (r=0.5).
                Box(floorGo, "MuroS1", new Vector3(-halfW * 0.65f, wallY, halfD),
                    new Vector3(halfW * 0.70f, floorH, thick), _wallMat);

                // Lato destro del muro frontale
                Box(floorGo, "MuroS2", new Vector3(halfW * 0.65f, wallY, halfD),
                    new Vector3(halfW * 0.70f, floorH, thick), _wallMat);

                // Finestra sopra la porta
                Box(floorGo, "Finestra", new Vector3(0f, yBase + floorH * 0.75f, halfD),
                    new Vector3(w * 0.3f, floorH * 0.35f, 0.05f), _glassMat);
            }
            else
            {
                Box(floorGo, "MuroS", new Vector3(0f, wallY, halfD),
                    new Vector3(w, floorH, thick), _wallMat);
            }
        }

        // ── SCALE ──────────────────────────────────────────────────

        private void BuildStairs(Transform floorGo, float x, float yBase, float floorH, float z)
        {
            int numSteps = 10;
            float stepH = floorH / numSteps;
            float stepD = 0.3f;
            float stairW = 0.9f;

            for (int i = 0; i < numSteps; i++)
            {
                float sy = yBase + stepH * (i + 0.5f);
                float sz = z - (i * stepD * 0.5f) + numSteps * stepD * 0.25f;

                Box(floorGo, "Step_" + i, new Vector3(x, sy, sz),
                    new Vector3(stairW, stepH, stepD), _woodMat);
            }

            // Balaustre
            float railH = 0.9f;
            float railY = yBase + floorH * 0.5f;
            Box(floorGo, "Balaustreira", new Vector3(x + stairW * 0.5f, railY, z),
                new Vector3(0.06f, railH, numSteps * stepD * 0.5f), _woodMat);
        }

        // ── TRIGGER SCALE ──────────────────────────────────────────

        private void BuildStairTrigger(Transform floorGo, float x, float yBase, float z,
            int currentFloor, int totalFloors)
        {
            var triggerGo = new GameObject("StairTrigger");
            triggerGo.transform.SetParent(floorGo.transform, false);
            triggerGo.transform.localPosition = new Vector3(x, yBase + 1f, z);

            var col = triggerGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(1.5f, 2.5f, 1.5f);

            var st = triggerGo.AddComponent<StairTrigger>();
            st.currentFloor = currentFloor;
            st.totalFloors = totalFloors;
        }

        // ── TRIGGER USCITA ─────────────────────────────────────────

        private void BuildExitTrigger(Transform floorGo, float w, float d, float yBase)
        {
            // In-place: l'uscita è gestita fisicamente dal varco della porta
            // (BuildingEntrance.PortaFuori) e dal pulsante ESCI. L'ExitTrigger
            // "USCITA" sarebbe ridondante proprio sulla soglia, quindi si omette.
            if (_inPlace) return;

            var exitGo = new GameObject("ExitTrigger");
            exitGo.transform.SetParent(floorGo.transform, false);
            exitGo.transform.localPosition = new Vector3(0f, yBase + 1f, d * 0.5f);

            var col = exitGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(2f, 2.5f, 2f);

            exitGo.AddComponent<ExitTrigger>();
        }

        // ── HELPER ──────────────────────────────────────────────────

        private void Box(Transform parent, string name, Vector3 center, Vector3 scale, Material mat)
        {
            // In-place: la porta d'ingresso e' un varco REALE nel guscio
            // (aperto), quindi non deve essere riempita dal parallelepipedo
            // pieno che i builder interni piazzavano sulla soglia.
            if (_inPlace && name.StartsWith("PortaIngresso"))
                return;
            // In-place: la "Vetrina" del negozio è un pannello a tutta larghezza
            // posto esattamente sulla soglia della porta reale (z=d*0.49): il
            // suo collider (glass) bloccherebbe l'ingresso. Il guscio ha già le
            // aperture reali, quindi la vetrina interna è ridondante e va tolta.
            if (_inPlace && name == "Vetrina")
                return;

            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = center;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            // Rimuovi collider dai mobili (solo le pareti esterne e il pavimento hanno collider)
            if (name != "Pavimento" && name != "Soffitto"
                && !name.StartsWith("Muro") && !name.StartsWith("Parete"))
            {
                var c = go.GetComponent<Collider>();
                if (c != null) Destroy(c);
            }
        }

        private void Furniture(Transform parent, string name, Vector3 center, Vector3 scale)
        {
            EnsureFurniture();

            string fbxKey = name;
            if (FBX_NAME.ContainsKey(name)) fbxKey = FBX_NAME[name];

            GameObject prefab;
            _furnitureMap.TryGetValue(fbxKey, out prefab);
            if (prefab != null)
            {
                var inst = Instantiate(prefab, parent, false);
                inst.name = name;
                inst.transform.localPosition = center;
                inst.transform.localScale = Vector3.one;

                var renderers = inst.GetComponentsInChildren<Renderer>();
                if (renderers.Length > 0)
                {
                    Bounds combined = renderers[0].bounds;
                    for (int i = 1; i < renderers.Length; i++)
                        combined.Encapsulate(renderers[i].bounds);
                    Vector3 sz = combined.size;
                    if (sz.x > 0.001f && sz.y > 0.001f && sz.z > 0.001f)
                    {
                        inst.transform.localScale = new Vector3(
                            scale.x / sz.x, scale.y / sz.y, scale.z / sz.z);
                    }
                }
                else
                {
                    inst.transform.localScale = scale;
                }

                foreach (var col in inst.GetComponentsInChildren<Collider>())
                    Destroy(col);
            }
            else
            {
                LogW("FBX not found for '" + name + "' (key='" + fbxKey + "'), using fallback box");
                Material fallback = _woodMat != null ? _woodMat : Lit(new Color(0.55f, 0.35f, 0.18f));
                Box(parent, name, center, scale, fallback);
            }
        }

        private void Log(string msg)
        {
            Debug.Log("[InteriorGenerator] " + msg);
            UnityBridge.LogToAndroid("InteriorGenerator", msg);
        }

        private void LogW(string msg)
        {
            Debug.LogWarning("[InteriorGenerator] " + msg);
            UnityBridge.LogToAndroid("InteriorGenerator", "WARN: " + msg);
        }

        private void EnsureFurniture()
        {
            if (_furnitureLoaded) return;
            _furnitureLoaded = true;
            _furnitureMap = new Dictionary<string, GameObject>();

            var keys = new HashSet<string>();
            foreach (var kv in FBX_NAME)
                keys.Add(kv.Value);

            Log("EnsureFurniture: loading " + keys.Count + " FBX from Resources/Furniture/...");

            int found = 0, missing = 0;
            string missingNames = "";
            foreach (var key in keys)
            {
                var go = Resources.Load<GameObject>("Furniture/" + key);
                if (go != null)
                {
                    _furnitureMap[key] = go;
                    found++;
                }
                else
                {
                    missing++;
                    if (missing <= 10) missingNames += key + " ";
                }
            }

            Log("loaded: " + found + "/" + keys.Count);
            if (missing > 0)
                LogW("missing (" + missing + "): " + missingNames.Trim());
        }

        private void AddLabel(Transform parent, string text, Vector3 pos, Vector3 scale)
        {
            var labelGo = new GameObject("Label_" + text);
            labelGo.transform.SetParent(parent, false);
            labelGo.transform.localPosition = pos;
            labelGo.transform.localRotation = Quaternion.identity;

            var tmp = labelGo.AddComponent<TextMeshPro>();
            tmp.font = TMP_Settings.defaultFontAsset;
            tmp.text = text;
            tmp.fontSize = 2.0f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            tmp.outlineWidth = 0.1f;
            tmp.outlineColor = new Color(0f, 0f, 0f, 0.6f);
            tmp.enableWordWrapping = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
            tmp.raycastTarget = false;
            tmp.rectTransform.sizeDelta = new Vector2(scale.x * 10f, scale.y * 10f);
        }
    }
}
