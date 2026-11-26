using System.Collections;
using UnityEngine;
using City.UI;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    [RequireComponent(typeof(Collider))]
    public class BuildingEntrance : MonoBehaviour
    {
        public string buildingName = "Edificio";
        public string buildingType = "house";
        public float buildingWidth = 8f;
        public float buildingDepth = 8f;
        public float buildingHeight = 8f;
        public int floorCount = 1;
        public Shop shop;

        /// <summary>Id OSM dell'edificio reale associato. Zero per gli ingressi
        /// storici: solo gli id presenti in VisitInteriorRegistry diventano
        /// case visitabili, tutti gli altri conservano il flusso attuale.</summary>
        public long osmId;

        /// <summary>Zona POI veicolo associata (concessionaria/officina/garage):
        /// passata all'interno per il bancone che apre i menu veicolo.</summary>
        public City.Vehicle.VehiclePoiZone poiZone;

        /// <summary>True se l'edificio è costruito IN-PLACE (guscio reale con
        /// porta e finestre): si entra/esce attraversando fisicamente la porta,
        /// senza tooltip/tap/teletrasporto.</summary>
        public bool inPlace;

        /// <summary>Per gli ingresso in-place: true per il trigger TUTTO DENTRO
        /// (che marchia il giocatore come "dentro"), false per il trigger TUTTO
        /// FUORI (che lo marchia "fuori" quando riattraversa la porta).</summary>
        public bool isExit;

        private bool focused;
        public bool IsFocused => focused;

        public bool IsShop => buildingType == "shop";

        private bool autoEntryStarted;
        private bool _enteringPrefab;
        private GameObject _doorMark;

        /// <summary>True solo per le case visitabili del registro: il portale
        /// deve essere visibile appena il chunk carica, non solo quando il
        /// lazy dell'interno si attiva vicino al player.</summary>
        public bool forceDoorMarker;

        /// <summary>Crea il segnaposto visivo della porta SOLO quando serve
        /// (chiamato da InteriorGenerator.BuildInteriorNow, entro 30 m dal
        /// player). Prima era un Quad/Renderer creato per ogni edificio della
        /// citta' in fase di build chunk: ~15.700 Renderer in piu'.</summary>
        public void EnsureDoorMarker()
        {
            // REVIEW: non escludere gli edifici in-place: prima il Quad veniva
            // creato per OGNI ingresso da BuildingPlacer.MaybeAddEntrance,
            // anche in-place. Il varco geometrico e il segnaposto coesistono.
            if (_doorMark != null) return;
            var mark = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(mark.GetComponent<Collider>());
            mark.name = "PortaSegnaposto";
            mark.transform.SetParent(transform, false);
            mark.transform.localPosition = Vector3.zero;
            // Il quad guarda lungo il +Z locale del trigger: BuildingPlacer
            // ruota il trigger verso la porta vera del registro, non verso il
            // facciata presunta del prefab.
            mark.transform.localRotation = Quaternion.identity;
            Vector3 s = transform.lossyScale;
            float w = forceDoorMarker ? 2.9f : 2.3f;
            float h = forceDoorMarker ? 2.5f : 2.1f;
            mark.transform.localScale = new Vector3(
                s.x != 0f ? w / s.x : w,
                s.y != 0f ? h / s.y : h, 1f);
            var mat = forceDoorMarker
                ? City.OSM.BuildingPlacer.DoorMarkMaterial(true)
                : City.OSM.BuildingPlacer.DoorMarkMaterial();
            mark.GetComponent<Renderer>().sharedMaterial = mat;
            _doorMark = mark;
            if (!forceDoorMarker) return;
            // Solo le due case configurate: tre stipiti luminosi rendono
            // l'ingresso riconoscibile anche su una facciata chiara. Nessun
            // collider e nessun Update, costo trascurabile (2 portali).
            DoorFramePart("StipiteSx", -w * 0.5f, 0f, 0.16f, h, 0.18f, s, mat);
            DoorFramePart("StipiteDx", w * 0.5f, 0f, 0.16f, h, 0.18f, s, mat);
            DoorFramePart("Architrave", 0f, h * 0.5f, w + 0.16f, 0.18f, 0.18f, s, mat);
        }

        private void DoorFramePart(string name, float x, float y,
            float worldW, float worldH, float worldD, Vector3 s, Material mat)
        {
            var part = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(part.GetComponent<Collider>());
            part.name = name;
            part.transform.SetParent(transform, false);
            part.transform.localPosition = new Vector3(
                s.x != 0f ? x / s.x : x,
                s.y != 0f ? y / s.y : y,
                s.z != 0f ? 0.04f / s.z : 0.04f);
            part.transform.localScale = new Vector3(
                s.x != 0f ? worldW / s.x : worldW,
                s.y != 0f ? worldH / s.y : worldH,
                s.z != 0f ? worldD / s.z : worldD);
            part.GetComponent<Renderer>().sharedMaterial = mat;
        }

        private static void LogEntrance(string msg)
        {
            Debug.Log("[BuildingEntrance] " + msg);
            UnityBridge.LogToAndroid("BuildingEntrance", msg);
        }

        private void Awake()
        {
            var rb = gameObject.GetComponent<Rigidbody>();
            if (rb == null)
            {
                rb = gameObject.AddComponent<Rigidbody>();
                rb.isKinematic = true;
                rb.useGravity = false;
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;

            // Player NON ancora spawnato (gate di avvio: CC spento, in attesa
            // dei primi chunk): ignora i trigger porta. Un edificio puo' coprire
            // l'origine e il suo trigger d'ingresso includere lo spawn, marcando
            // il giocatore come "dentro" una casa prima ancora di muoversi
            // (finto ingresso automatico alla partenza).
            var cw = City.OSM.CityChunkedWorld.Instance;
            if (cw != null && !cw.PlayerSpawned) return;

            var mgr = InteriorManager.Instance;

            // Edificio IN-PLACE: il passaggio fisico attraverso la porta è
            // l'ingresso/uscita. Il trigger "entry" (dentro) marca dentro, il
            // trigger "exit" (fuori) marca fuori quando ci si affaccia.
            if (inPlace)
            {
                if (isExit)
                {
                    // Teleport alla soglia con CC riabilitato: si attraversano
                    // entrambi i trigger insieme → falso "uscito" un attimo dopo
                    // l'ingresso. Se il player è ancora chiaramente DENTRO
                    // l'impronta non è uscito davvero (l'uscita vera viene
                    // comunque rilevata dal ticker geometrico oltre il muro).
                    var gen = GetComponentInParent<InteriorGenerator>();
                    if (gen != null && gen.DeepInside(other.transform.position))
                        return;
                    if (mgr != null && mgr.IsInside) mgr.MarkOutside(this);
                }
                else
                {
                    if (mgr != null && !mgr.IsInside) mgr.MarkInside(this);
                }
                return;
            }

            if (mgr != null && mgr.IsInside)
            {
                focused = false;
                if (Game.Instance != null)
                    Game.Instance.OnEntranceFocusChanged(this);
                return;
            }
            LogEntrance("OnTriggerEnter: " + buildingName + " (" + buildingType + ")");
            if (autoEntryStarted) return;
            focused = true;
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(this);
            ApplyCameraOverride(true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (inPlace) return; // l'uscita è gestita dal trigger "exit" fuori

            focused = false;
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(this);
            ApplyCameraOverride(false);
        }

        public void StartAutoEntry()
        {
            if (inPlace) return;
            if (autoEntryStarted) return;
            autoEntryStarted = true;
            Enter();
            autoEntryStarted = false;
        }

        public void Interact()
        {
            if (inPlace)
            {
                LogEntrance("in-place: si entra attraversando la porta, tappare non serve (" + buildingName + ")");
                return;
            }
            if (autoEntryStarted) return;
            Enter();
        }

        private void Enter()
        {
            if (City.Vehicle.VehiclePoiZone.PlayerInOpenRoomVehicle())
            {
                LogEntrance("ingresso ignorato: dentro zona aperta veicolo (concessionaria/officina/garage, vista 3a persona)");
                return;
            }
            if (inPlace)
            {
                LogEntrance("in-place: si entra attraversando la porta, tappare non serve (" + buildingName + ")");
                return;
            }
            // Edifici PREFAB (Quaternius): interno reale nel mondo, ingresso con
            // fade scenografico (esterno nascosto, player dentro l'impronta).
            var gen = GetComponentInParent<InteriorGenerator>();
            if (gen == null || !gen.IsPrefabExterior)
            {
                LogEntrance("entrata non supportata: edificio senza interno prefab (" + buildingName + ")");
                return;
            }
            StartCoroutine(EnterPrefab(gen));
        }

        private IEnumerator EnterPrefab(InteriorGenerator gen)
        {
            // flag anti doppio-ingresso: un doppio tap su "APRI" (o un tap +
            // auto-entry nello stesso frame) avviava due coroutine sullo stesso
            // ScreenFader -> doppio teleport e possibile fade nero bloccato.
            if (_enteringPrefab) yield break;
            _enteringPrefab = true;
            try
            {
                Game g = Game.Instance;
                if (g == null || g.player == null) yield break;
                var mgr = InteriorManager.Instance;
                if (mgr != null && mgr.IsInside) yield break;

                var fader = g.fader;
                if (fader != null) fader.gameObject.SetActive(true);
                if (fader != null) fader.FadeToBlack(null);

                if (!gen.InteriorBuilt) gen.BuildInteriorNow();
                gen.SetLight(true); // subito, senza attendere il ticker 0,25s
                gen.SetPrefabExteriorVisible(false);

                // dentro l'impronta, poco oltre la soglia, rivolto verso la stanza.
                // La soglia e' quella del trigger porta (lato giusto del registro).
                Vector3 pos = gen.PrefabEnterWorld;
                Quaternion rot = gen.PrefabEnterRotation;
                InteriorTeleport.Move(pos, rot, g);

                if (mgr != null) mgr.MarkInside(this);
                if (fader != null)
                {
                    yield return new WaitForSeconds(fader.duration);
                    fader.FadeFromBlack(null);
                    yield return new WaitForSeconds(fader.duration);
                    fader.gameObject.SetActive(false);
                }
            }
            finally
            {
                _enteringPrefab = false;
            }
        }

        private void ApplyCameraOverride(bool active)
        {
            if (City.Vehicle.VehiclePoiZone.PlayerInOpenRoomVehicle()) return;
            var rig = Game.Instance != null ? Game.Instance.rig : null;
            if (rig == null) return;
            float dist = 6.0f;
            float height = 2.6f;
            float pitch = 20f;
            rig.SetIndoorOverride(active, "building", dist, height, pitch);
        }

        private void OnDisable()
        {
            if (focused) ApplyCameraOverride(false);
        }
    }
}
