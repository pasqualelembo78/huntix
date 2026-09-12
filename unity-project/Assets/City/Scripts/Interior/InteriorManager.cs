using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.Player;
using City.UI;
using City.Vehicle;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Gestisce gli interni IN-PLACE degli edifici enterabili, in TERZA persona.
    ///
    /// Gli edifici visitabili vengono costruiti direttamente nel mondo (guscio
    /// con porta e finestre reali + arredi interni): NON esiste nessun
    /// teletrasporto né scena dedicata a y=500. Si entra semplicemente
    /// attraversando la soglia della porta (BuildingEntrance) e si esce
    /// riattraversandola. Questo manager tiene traccia dello stato
    /// dentro/fuori e stringe la CameraRig terza-persone dietro al giocatore
    /// mentre è all'interno.
    ///
    /// Il giocatore resta SEMPRE in terza persona (mai prima persona).
    /// </summary>
    public class InteriorManager : MonoBehaviour
    {
        public static InteriorManager Instance;

        private const float INTERIOR_DIST = 4.5f;
        private const float INTERIOR_HEIGHT = 2.5f;
        private const float INTERIOR_PITCH = 18f;
        private const string INTERIOR_OWNER = "interior";

        // ── INTERNI LAZY ─────────────────────────────────────────────
        // Gli interni in-place vengono costruiti solo quando il player si
        // avvicina all'edificio. Un SOLO ticker globale (questo Update, con
        // intervallo) gestisce tutti gli edifici: nessun componente Update per
        // edificio. Raggio generoso così si costruisce mentre ci si avvicina.
        private const float LAZY_INTERVAL = 0.25f;
        private const float LAZY_RADIUS = 30f;

        private readonly List<InteriorGenerator> _pendingInteriors =
            new List<InteriorGenerator>();
        private float lazyTimer;

        // ── ENTRATA/USCITA GEOMETRICA ─────────────────────────────
        // I trigger porta in-place restano attivi, ma il teletrasporto
        // (TouchInputHandler) disabilita il CharacterController → OnTriggerEnter
        // non scatta mai e il player resta "fuori" anche quando è fisicamente
        // dentro il guscio. Questo ticker misura geometricamente l'impronta del
        // guscio (XZ + quota, InteriorGenerator.Contains) e marca dentro/fuori
        // con una breve latenza di commit (isteresi), coprendo il teleport
        // dentro l'edificio e le uscite dai muri ai LOD col collider spento.
        private const float GEO_COMMIT = 0.35f;
        private const float GEO_RANGE = 40f;
        private bool _geoInside;
        private float _geoSince = -1f;


        private int currentFloor;
        private int totalFloors;

        public bool IsInside { get; private set; }

        /// <summary>Radice dell'interno dell'edificio in cui ci troviamo
        /// (per letti/divani interattivi). Null fuori dagli edifici.</summary>
        public Transform ActiveInteriorRoot
        {
            get
            {
                if (currentEntrance == null) return null;
                var gen = currentEntrance.GetComponentInParent<InteriorGenerator>();
                return gen != null ? gen.InteriorRoot : null;
            }
        }

        /// <summary>Ingresso dell'edificio in cui ci troviamo (per uscire).</summary>
        private BuildingEntrance currentEntrance;

        /// <summary>Negozio associato all'interno attuale (se commerciale).</summary>
        public Shop ActiveShop { get; private set; }

        /// <summary>Zona POI veicolo associata all'interno attuale (per i menù
        /// veicolo del bancone interno). Null per edifici non veicolo.</summary>
        public VehiclePoiZone ActiveVehicleZone { get; private set; }

        /// <summary>Azione contestuale corrente DENTRO l'edificio (es. bancone
        /// negozio). Viene impostata dai trigger interni quando il player è a
        /// contatto e consumata dal tap in Game.OnInteractPressed.</summary>
        public System.Action CurrentInteriorAction { get; private set; }

        private readonly System.Collections.Generic.List<System.Action> _interiorActions =
            new System.Collections.Generic.List<System.Action>();
        private readonly List<string> _interiorLabels = new List<string>();

        /// <summary>Registra un'azione contestuale interna (uscita, bancone
        /// negozio, bancone veicolo, scale) quando il player entra nel
        /// relativo trigger. L'ultima registrata vince: con trigger
        /// sovrapposti uscire da uno non spegne il prompt dell'altro.
        /// Anche la label del prompt è gestita qui (unica fonte).</summary>
        public void RegisterInteriorAction(System.Action a, string label)
        {
            for (int i = 0; i < _interiorActions.Count; i++)
            {
                if (_interiorActions[i] == a)
                {
                    _interiorLabels[i] = label;
                    return;
                }
            }
            _interiorActions.Add(a);
            _interiorLabels.Add(label);
            ApplyInteriorAction();
        }

        /// <summary>Rimuove l'azione quando il player esce dal trigger.
        /// Se ne resta un'altra attiva, il suo prompt torna subito
        /// visibile invece di spegnere tutto.</summary>
        public void UnregisterInteriorAction(System.Action a)
        {
            for (int i = 0; i < _interiorActions.Count; i++)
            {
                if (_interiorActions[i] == a)
                {
                    _interiorActions.RemoveAt(i);
                    _interiorLabels.RemoveAt(i);
                    break;
                }
            }
            ApplyInteriorAction();
        }

        private void ApplyInteriorAction()
        {
            if (_interiorActions.Count > 0)
            {
                int idx = _interiorActions.Count - 1;
                CurrentInteriorAction = _interiorActions[idx];
                if (UIManager.Instance != null)
                    UIManager.Instance.ShowInteract(_interiorLabels[idx]);
            }
            else
            {
                CurrentInteriorAction = null;
                if (UIManager.Instance != null)
                    UIManager.Instance.HideInteract();
            }
        }

        private void Awake()
        {
            Instance = this;
        }

        private void Log(string msg)
        {
            Debug.Log("[InteriorManager] " + msg);
            UnityBridge.LogToAndroid("InteriorManager", msg);
        }

        // ── Entrata/uscita (soglia della porta reale) ──────────────────

        public void MarkInside(BuildingEntrance e)
        {
            if (e == null) return;
            if (IsInside) { Log("MarkInside: già dentro, ignoro (" + e.buildingName + ")"); return; }

            Game g = Game.Instance;
            if (g == null) return;

            currentEntrance = e;
            ActiveShop = e.shop;
            ActiveVehicleZone = e.poiZone;
            totalFloors = Mathf.Clamp(e.floorCount, 1, 1); // in-place a piano unico
            currentFloor = 0;
            IsInside = true;

            // La CameraRig resta attiva e segue il giocatore (terza persona);
            // l'override indoor stringe la camera dietro di lui dentro l'edificio.
            // La distanza è scalata sulla profondità reale della stanza: nei
            // locali piccoli (fino a ~4-6 m) una distanza fissa di 4.5 m farebbe
            // sbucare la camera fuori dal muro di fondo (il clamp sul tetto non
            // scatta se l'impatto è troppo vicino). Con queste soglie resta
            // sempre dentro il guscio anche per gli edifici più stretti.
            float depth = Mathf.Max(4f, e.buildingDepth);
            float camDist = Mathf.Min(4.5f, Mathf.Max(3.2f, depth * 0.7f));
            if (g.rig != null)
                g.rig.SetIndoorOverride(true, INTERIOR_OWNER,
                    camDist, INTERIOR_HEIGHT, INTERIOR_PITCH);

            if (g.player != null)
                g.player.Stop();

            Log("Entrato (in-place, 3a persona): " + e.buildingName);
            City.OSM.ColliderProbe.ProbeAt(g.player.transform.position, "inside");
        }

        public void MarkOutside(BuildingEntrance e)
        {
            if (!IsInside) return;

            Game g = Game.Instance;

            // Edificio PREFAB (Quaternius): uscita scenografata con fade nero
            // per ripristinare l'esterno e riportare il player fuori dalla soglia.
            if (e != null && !e.inPlace)
            {
                StartCoroutine(ExitPrefabRoutine(e, g));
                return;
            }

            IsInside = false;
            ActiveShop = null;
            ActiveVehicleZone = null;
            currentEntrance = null;

            if (g == null) return;

            // Rimuove l'override indoor: la camera torna alla distanza normale.
            if (g.rig != null)
                g.rig.SetIndoorOverride(false, INTERIOR_OWNER);

            if (g.player != null)
                g.player.Stop();

            Log("Uscito dall'edificio");
            if (g.player != null)
                City.OSM.ColliderProbe.ProbeAt(g.player.transform.position, "outside");
        }

        /// <summary>Uscita dagli edifici PREFAB: fade nero, lo stato va fuori
        /// SUBITO (blocca il ticker geometrico e il safety reset), poi si
        /// ripristina l'esterno e il player viene riposizionato fuori dalla
        /// soglia. La camera indoor si spegne e si sfuma.</summary>
        private IEnumerator ExitPrefabRoutine(BuildingEntrance e, Game g)
        {
            IsInside = false;
            ActiveShop = null;
            ActiveVehicleZone = null;
            currentEntrance = null;

            var gen = e != null ? e.GetComponentInParent<InteriorGenerator>() : null;
            var fader = g != null ? g.fader : null;
            if (fader != null) fader.gameObject.SetActive(true);
            if (fader != null) fader.FadeToBlack(null);

            if (g != null && g.player != null && gen != null)
            {
                Vector3 pos = gen.transform.TransformPoint(gen.PrefabExitLocal);
                SetPlayerPos(pos, gen.transform.rotation, g);
                gen.SetPrefabExteriorVisible(true); // esterno di nuovo visibile
            }

            if (g != null && g.rig != null)
                g.rig.SetIndoorOverride(false, INTERIOR_OWNER);
            if (g != null && g.player != null)
                City.OSM.ColliderProbe.ProbeAt(g.player.transform.position, "outside");

            Log("Uscito dall'edificio (prefab)");
            if (fader != null)
            {
                yield return new WaitForSeconds(fader.duration);
                fader.FadeFromBlack(null);
                yield return new WaitForSeconds(fader.duration);
                fader.gameObject.SetActive(false);
            }
        }

        private static void SetPlayerPos(Vector3 pos, Quaternion rot, Game g)
        {
            var player = g.player;
            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = pos;
            player.transform.rotation = rot;
            if (cc != null) cc.enabled = true;
            player.Stop();
            if (g.rig != null) g.rig.SetYaw(rot);
        }

        /// <summary>Uscita esplicita (pulsante ESCI): riattraversa la soglia
        /// della porta attuale senza bisogno di muovere il giocatore.</summary>
        public void ExitInterior()
        {
            if (!IsInside) return;
            if (currentEntrance != null)
                MarkOutside(currentEntrance);
            else
                MarkOutside(null);
        }

        /// <summary>Entrata dal teletrasporto GEOMETRICO: il player è dentro il
        /// guscio senza aver attraversato la porta fisicamente (teleport alla
        /// soglia con CC disabilitato). Risolve l'ingresso (possibile rientro)
        /// senza dipendere da OnTriggerEnter, che non scatta perché il CC è
        /// spento in quel momento.</summary>
        public void MarkInsideShell(InteriorGenerator gen)
        {
            if (gen == null) return;
            BuildingEntrance e = gen.Entrance;
            if (e == null)
                e = gen.GetComponentInChildren<BuildingEntrance>();
            if (e == null)
            {
                Log("entrata geometrica: edificio senza ingresso: " + gen.name);
                return;
            }
            MarkInside(e);
        }

        // ── Ticker geometrico entrata/uscita ─────────────────────────

        private void GeoInsideCheck()
        {
            var g = Game.Instance;
            if (g == null || g.player == null) return;
            Vector3 pos = g.player.transform.position;

            bool inside = false;
            InteriorGenerator inGen = null;
            if (!g.IsDriving)
            {
                float range2 = GEO_RANGE * GEO_RANGE;
                for (int i = 0; i < _pendingInteriors.Count; i++)
                {
                    var gen = _pendingInteriors[i];
                    if (gen == null) continue;
                    Vector3 d = gen.transform.position - pos;
                    if (d.x * d.x + d.z * d.z > range2) continue;
                    if (gen.Contains(pos))
                    {
                        inside = true;
                        inGen = gen;
                        break;
                    }
                }
            }

            if (inside == _geoInside)
            {
                _geoSince = -1f;
                return;
            }

            // Transizione (dentro↔fuori): latenza di commit per isteresi sulla
            // soglia, poi esegue davvero l'entrata/uscita.
            if (_geoSince < 0f)
                _geoSince = Time.time;
            else if (Time.time - _geoSince >= GEO_COMMIT)
            {
                _geoSince = -1f;
                _geoInside = inside;
                if (inside)
                {
                    // Ingregeo SOLO per i gusci in-place. Gli edifici PREFAB
                    // (Quaternius) si entrano ESCLUSIVAMENTE dal tap sulla porta
                    // con fade (BuildingEntrance.Enter -> EnterPrefab): l'impronta
                    // è tutta dentro il collider esterno del prefab, quindi il
                    // solo punto "nella footprint" raggiungibile da fuori è la
                    // soglia della porta. Marchiare "dentro" lì per errore (senza
                    // fade né cambio esterno) lascia IsInside bloccato = true e
                    // ogni successivo tap su APRI finisce nel yield break di
                    // EnterPrefab: "non succede nulla". L'uscita varcando la
                    // soglia resta attiva (ramo else -> MarkOutside).
                    if (!IsInside && (inGen == null || !inGen.IsPrefabExterior))
                        MarkInsideShell(inGen);
                }
                else if (IsInside)
                {
                    MarkOutside(currentEntrance);
                }
            }
        }

        // ── Cambio piano (solo modalità multi-piano on-demand; in-place no-op) ──

        public void ChangeFloor(int direction)
        {
            // In-place a piano unico: nessun cambio piano.
        }

        public int GetCurrentFloor() { return currentFloor; }
        public int GetTotalFloors() { return totalFloors; }

        // ── Input (terza persona) ──────────────────────────────────

        /// <summary>Mentre ci si è dentro, instrada il joystick allo stesso
        /// PlayerController della città (che muove il giocatore in terza
        /// persona). UIManager non alimenta il player quando IsInInterior è
        /// true, quindi qui lo facciamo noi.</summary>
        private void Update()
        {
            // Entrata/uscita geometrica: va misurata anche da fuori e prima del
            // vecchio flusso joystick (che ha una early-return quando !IsInside).
            GeoInsideCheck();

            // SAFETY: se siamo "dentro" ma l'ingresso è stato distrutto (chunk
            // scaricato, scena cambiata, edificio rimosso) resetta lo stato
            // per non restare bloccati dentro con camera/joystick forzati.
            if (IsInside && currentEntrance == null)
            {
                Log("ingresso distrutto mentre dentro: reset stato");
                MarkOutside(null);
            }

            if (!IsInside) return;
            var g = Game.Instance;
            if (g == null) return;
            if (g.player == null || g.ui == null) return;
            if (g.IsDriving) return;

            Vector2 input = g.ui.joystick != null ? g.ui.joystick.Value : Vector2.zero;
            g.player.SetMoveInput(input);
        }

        public void RegisterInterior(InteriorGenerator gen)
        {
            if (gen != null && !_pendingInteriors.Contains(gen))
                _pendingInteriors.Add(gen);
        }

        public void UnregisterInterior(InteriorGenerator gen)
        {
            _pendingInteriors.Remove(gen);
        }

        private void LateUpdate()
        {
            lazyTimer += Time.deltaTime;
            if (lazyTimer < LAZY_INTERVAL) return;
            lazyTimer = 0f;
            if (_pendingInteriors.Count == 0) return;

            var g = Game.Instance;
            if (g == null || g.player == null) return;

            Vector3 pp = g.player.transform.position;
            float r2 = LAZY_RADIUS * LAZY_RADIUS;

            // Iterazione all'indietro: BuildInteriorNow può distruggere oggetti
            // del chunk; gli edifici distrutti vengono rimossi.
            for (int i = _pendingInteriors.Count - 1; i >= 0; i--)
            {
                var gen = _pendingInteriors[i];
                if (gen == null)
                {
                    _pendingInteriors.RemoveAt(i);
                    continue;
                }
                // Luce interna: sempre accesa (nessun toggle distanza che
                // causava cambi improvvisi di luminosità). Nessun Update() per
                // edificio: il ticker unico la mantiene nello stato corretto.
                gen.SetLight(true);
                // L'interior resta LAZY: costruito solo quando il player è
                // entro LAZY_RADIUS. Senza questo check TUTTI gli edifici del
                // chunk costruirebbero l'interno (arredi + materiali) appena
                // caricati → di nuovo il lag originale. L'edificio resta in
                // lista finché esiste (rimozione solo quando distrutto),
                // quindi l'interno viene costruito una sola volta.
                if (!gen.InteriorBuilt)
                {
                    // Distanza dall'area della PORTA (non dal centro del guscio):
                    // per gli edifici grandi (fino a 60 m di lato) stare sulla
                    // soglia può essere a >30 m dal centro → l'interno restava
                    // "vuoto". Con l'ancora porta si costruisce appena il player
                    // si avvicina all'ingresso.
                    Vector3 delta = gen.DoorAnchorWorld - pp;
                    if (delta.x * delta.x + delta.z * delta.z <= r2)
                        gen.BuildInteriorNow();
                }
            }
        }
    }
}
