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

        // Fallback ingresso naturale: copre i casi in cui OnTriggerEnter non
        // arriva (collider porta creato mentre il player è già nella fascia,
        // CharacterController disabilitato al momento del contatto). Usa lo
        // STESSO volume del trigger, allargato di pochi cm, quindi non crea
        // ingressi "a distanza" né nuovi segnaposto visivi.
        private const float NATURAL_APPROACH_INTERVAL = 0.2f;
        private const float NATURAL_APPROACH_MARGIN = 0.6f;
        private float _naturalApproachTimer;


        private int currentFloor;
        private int totalFloors;

        // F4: nome dell'amico gia' salutato nella stanza attuale.
        private string _greetedMate;
        private float _sharedAt;
        private const float SHARED_SCAN = 3f;

        public bool IsInside { get; private set; }

        /// <summary>Radice dell'interno dell'edificio in cui ci troviamo
        /// (per letti/divani interattivi). Null fuori dagli edifici.</summary>
        public Transform ActiveInteriorRoot
        {
            get
            {
                // La tasca vittoriana è l'interno attivo finché il player è
                // dentro: ContainerEgg deve cercare le uova nei suoi cassetti,
                // non nel vecchio interno del guscio.
                if (City.Interior.Victorian.VictorianGrandInterior.IsInsidePocket)
                    return City.Interior.Victorian.VictorianGrandInterior.ActiveRoot;
                if (currentEntrance == null) return null;
                var gen = currentEntrance.GetComponentInParent<InteriorGenerator>();
                return gen != null ? gen.InteriorRoot : null;
            }
        }

        /// <summary>Ingresso dell'edificio in cui ci troviamo (per uscire).</summary>
        private BuildingEntrance currentEntrance;

        /// <summary>Ingresso attuale, esposto in sola lettura a chi deve
        /// identificare l'edificio in modo stabile (es. ContainerEgg: chiave
        /// dell'uovo nel cassetto = edificio + coordinate + piano).</summary>
        public BuildingEntrance CurrentEntrance { get { return currentEntrance; } }

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
            totalFloors = Mathf.Clamp(e.floorCount, 1, 4);
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

            var mp4 = City.Multiplayer.MultiplayerManager.Instance;
            if (mp4 != null && g.player != null)
            {
                string mate = mp4.SharedRoomMate(g.player.transform.position, 6f);
                if (mate != null)
                {
                    _greetedMate = mate;
                    Log("Stanza condivisa con " + mate);
                }
            }

            Log("Entrato (in-place, 3a persona): " + e.buildingName);
            // Mandato: la visita lascia un indizio uovo con luogo testuale e
            // coordinate reali della porta (una tantum per area+azione), oltre
            // ai nascondigli già dichiarati dal registro case configurate.
            HouseAccess.NoteEntry(e, e.entryKind);
            ContainerEgg.Ensure();

            // Interni universali: registra la visita con la chiave geo-stabile
            // del locale (snapshot Android + evento UniversalInteriorVisited).
            Transform interiorRoot = ActiveInteriorRoot;
            if (interiorRoot == null && currentEntrance != null)
            {
                var genHere = currentEntrance.GetComponentInParent<InteriorGenerator>();
                if (genHere != null) interiorRoot = genHere.transform;
            }
            var marker = UniversalInteriorBuilder.FindMarker(interiorRoot);
            if (marker != null) UniversalInteriorState.MarkVisited(marker.key, marker.kind);

            // Case visitabili (id OSM configurato): visita stabile + riflesso
            // uova nei quattro nascondigli dichiarati dalla casa visitabile.
            City.Interior.Visit.VisitInteriorManager.OnEntered(e);

            City.OSM.ColliderProbe.ProbeAt(g.player.transform.position, "inside");

            // Tasca vittoriana: se questa porta ha la casa enorme dentro,
            // il player varca subito la soglia con lo stesso teleporto helper.
            City.Interior.Victorian.VictorianGrandInterior.TryEnter(this, e);

            // Ruolo e permessi: la visita non dà diritto di modifica. L'oggetto
            // d'acquisto vive DENTRO la casa (anche nella tasca vittoriana).
            e.MarkNaturalEntryTime();
            HousePurchaseSpot.Install(ActiveInteriorRoot, e);
        }

        /// <summary>Trova l'ingresso di una casa configurata tra gli interni
        /// caricati (usato per lo spawn iniziale dentro la casa). Nessuna
        /// ricerca globale costosa: attraversa solo la coda lazy dei generatori.</summary>
        public BuildingEntrance FindEntranceByOsmId(long osmId)
        {
            for (int i = 0; i < _pendingInteriors.Count; i++)
            {
                var gen = _pendingInteriors[i];
                if (gen == null || gen.osmId != osmId) continue;
                if (gen.Entrance != null) return gen.Entrance;
            }
            return null;
        }

        public void MarkOutside(BuildingEntrance e)
        {
            if (!IsInside) return;
            if (e != null) e.MarkNaturalExitTime();

            // Qualsiasi uscita (pulsante ESCI, trigger o fade prefab) mentre
            // siamo nella tasca vittoriana deve riportare davanti alla porta
            // reale: il normale flusso in-place/prefab non sa dove sia la tasca.
            if (City.Interior.Victorian.VictorianGrandInterior.IsInsidePocket)
            {
                City.Interior.Victorian.VictorianGrandInterior.Exit();
                return;
            }

            Game g = Game.Instance;
            City.Interior.Visit.VisitInteriorManager.OnExited(e);
            City.Interior.Victorian.VictorianGrandInterior.ClearCamera();

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
            _greetedMate = null;

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
            City.Interior.Visit.VisitInteriorManager.OnExited(e);
            City.Interior.Victorian.VictorianGrandInterior.ClearCamera();
            IsInside = false;
            ActiveShop = null;
            ActiveVehicleZone = null;
            currentEntrance = null;

            var gen = e != null ? e.GetComponentInParent<InteriorGenerator>() : null;
            if (gen != null) gen.SetLight(false); // spenta subito, non dopo il fade
            var fader = g != null ? g.fader : null;
            if (fader != null) fader.gameObject.SetActive(true);
            if (fader != null) fader.FadeToBlack(null);

            if (g != null && g.player != null && gen != null)
            {
                Vector3 pos = gen.PrefabExitWorld;
                InteriorTeleport.Move(pos, gen.PrefabExitRotation, g);
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

        // ── Fallback ingresso naturale (senza UI) ─────────────────────

        /// <summary>Controllo periodico della fascia d'ingresso delle case.
        /// Il trigger della porta resta la via principale; qui si rimette a
        /// posto solo il caso in cui il callback non scatti. Nessun avviso, nessun
        /// teleport: si chiama esattamente lo stesso StartAutoEntry del varco
        /// camminato, con fade e visita come ospite.</summary>
        private void NaturalApproachCheck()
        {
            _naturalApproachTimer -= Time.deltaTime;
            if (_naturalApproachTimer > 0f) return;
            _naturalApproachTimer = NATURAL_APPROACH_INTERVAL;

            var g = Game.Instance;
            if (g == null || g.player == null) return;
            var cw = City.OSM.CityChunkedWorld.Instance;
            if (cw != null && !cw.PlayerSpawned) return;
            if (IsInside || g.IsDriving) return;
            if (City.Interior.Victorian.VictorianGrandInterior.IsInsidePocket) return;

            Vector3 pos = g.player.transform.position;
            float range2 = GEO_RANGE * GEO_RANGE;
            for (int i = 0; i < _pendingInteriors.Count; i++)
            {
                var gen = _pendingInteriors[i];
                if (gen == null) continue;
                var e = gen.Entrance;
                if (e == null || !e.naturalEntry || e.inPlace) continue;
                if (e.IsEntryInProgress || e.NaturalExitGraceActive) continue;
                Vector3 d = gen.DoorAnchorWorld - pos;
                if (d.x * d.x + d.z * d.z > range2) continue;
                if (!e.IsNearNaturalApproach(pos, NATURAL_APPROACH_MARGIN)) continue;
                Log("soglia raggiunta (fallback geometrico): " + e.buildingName);
                e.StartAutoEntry();
                break;
            }
        }

        // ── Ticker geometrico entrata/uscita ─────────────────────────

        private void GeoInsideCheck()
        {
            var g = Game.Instance;
            if (g == null || g.player == null) return;
            // Dentro la tasca vittoriana il player è 1000 m sotto il guscio:
            // il controllo geometrico normale lo crederebbe "fuori" e
            // richiamerebbe MarkOutside dopo 0,35 s. La tasca ha il suo
            // trigger di uscita, quindi qui si sospende solo finché è attiva.
            if (City.Interior.Victorian.VictorianGrandInterior.IsInsidePocket) return;
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
            if (currentEntrance == null) return;
            int next = Mathf.Clamp(currentFloor + direction, 0, totalFloors - 1);
            if (next == currentFloor) return;

            var g = Game.Instance;
            if (g == null || g.player == null) return;
            var gen = currentEntrance.GetComponentInParent<InteriorGenerator>();
            if (gen == null) return;

            Transform root = ActiveInteriorRoot ?? gen.transform;
            Vector3 pos = gen.GetStairPosition(root, next);

            // Stesso helper di teleporto di porte e varchi: CC spento durante
            // lo spostamento e guard anti-NaN, senza tre copie divergenti.
            InteriorTeleport.Move(pos, g.player.transform.rotation, g);

            currentFloor = next;
            Log("Cambio piano: " + currentFloor + " (" + totalFloors + " piani)");
            City.OSM.ColliderProbe.ProbeAt(pos, "floor" + currentFloor);
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
            // Ingresso naturale delle case: fallback del trigger porta, senza
            // prompt né riquadro. Va prima del controllo geometrico gusci.
            NaturalApproachCheck();

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

            // Zone vittoriane: prima visita registrata nel diario Android
            // con luogo testuale e coordinate della porta di casa.
            City.Interior.Victorian.VictorianGrandInterior.Tick();

            if (!IsInside) return;

            // Negozi walk-in: avanza l'attività (clienti/incasso/livello) solo
            // dentro il negozio corrente. Nessuna fisica o NavMesh aggiuntiva.
            ShopInteriorBuilder.TickActive(ActiveInteriorRoot, Time.deltaTime);

            var g = Game.Instance;
            if (g == null) return;
            if (g.player == null || g.ui == null) return;
            if (g.IsDriving) return;

            // Stanza condivisa: se un amico entra nella stessa impronta mentre
            // siamo dentro, lo annunciamo una volta per edificio.
            if (Time.time >= _sharedAt)
            {
                _sharedAt = Time.time + SHARED_SCAN;
                var mp = City.Multiplayer.MultiplayerManager.Instance;
                if (mp != null && g.player != null)
                {
                    string mate = mp.SharedRoomMate(g.player.transform.position, 6f);
                    if (mate != null && mate != _greetedMate)
                    {
                        _greetedMate = mate;
                        if (g.ui != null)
                            g.ui.ShowToast(mate + " e' nella stessa stanza con te!");
                    }
                }
            }

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

        /// <summary>Edificio di cui il player e' attualmente ospite (o null).
        /// Usato dal ticker per tenere accesa UNA sola luce interna.</summary>
        private InteriorGenerator InsideGenerator()
        {
            if (!IsInside || currentEntrance == null) return null;
            return currentEntrance.GetComponentInParent<InteriorGenerator>();
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

            // FLUIDITA': prima OGNI edificio in lista veniva acceso a ogni tick
            // (gen.SetLight(true) sotto): con 15.700 edifici erano migliaia di
            // Point Light attive e il frame schizzava a 400 ms. Ora resta
            // accesa SOLO la luce dell'edificio in cui il player e' entrato.
            var insideGen = InsideGenerator();

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
                // Luce interna SOLO dentro l'edificio in cui sei entrato:
                // nessun Update() per edificio, lo fa il ticker unico.
                gen.SetLight(gen == insideGen);
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
                    {
                        try
                        {
                            City.OSM.OsmDiag.Log("[Interior] build lazy: " + gen.name +
                                " shell " + gen.ShellW.ToString("F0") + "x" +
                                gen.ShellD.ToString("F0") + "x" +
                                gen.ShellH.ToString("F0") +
                                " porta=" + gen.DoorAnchorWorld.ToString("F1"));
                            gen.BuildInteriorNow();
                            City.OSM.OsmDiag.Log("[Interior] build OK: " + gen.name);
                        }
                        catch (System.Exception e)
                        {
                            City.OSM.OsmDiag.Log("[Interior] build FALLITO: " + gen.name + " -> " + e.Message);
                        }
                    }
                }
            }
        }
    }
}
