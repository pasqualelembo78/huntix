using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using City.Economy;
using City.Player;
using TMPro;

namespace City.NPC
{
    // Pedone animato del mondo a chunk. Il modello e' il personaggio Kenney
    // (characterMedium) istanziato da NPCPopulator: qui si gestisce camminata
    // lungo i marciapiedi, pause, missione opzionale e l'identita' usata
    // dalla chat IA quando il giocatore tocca il pedone.
    public class NPCController : MonoBehaviour
    {
        public float walkSpeed = 1.4f;
        public float pauseMin = 2f;
        public float pauseMax = 6f;

        private Vector3[] waypoints;
        private int currentTarget;
        private bool walking;
        private float pauseTimer;
        private NPCMission mission;

        // vita quotidiana: ogni pedone ha una personalita (shopper o no);
        // chi e shopper nelle fasce orarie di punta fa una tappa verso il
        // negozio piu vicino e si trattiene a guardare le vetrine prima di
        // riprendere la passeggiata. Di notte tutti camminano piu piano.
        private bool _shopper;
        private float _dailyNextAt;
        private int _poiStopIdx = -1;

        // ── routine giornaliera Brookhaven ──
        // Ogni NPC ha una schedule fissa: mattina va al lavoro, pomeriggio
        // shopping/passeggiata, sera torna a casa, notte riposa.
        // Il workspace e' un POI vicino spawnato all'init; la casa e' lo
        // spawn point iniziale.
        private Vector3 _homePos;
        private Vector3 _workPos;
        private bool _hasWork;
        private enum DailyPhase { Morning, Afternoon, Evening, Night }
        private DailyPhase _phase = DailyPhase.Morning;
        private bool _headingHome;
        private bool _atWork;

        // ── bisogni NPC (pattern LifeVerse + ECS-Starter) ──
        // Fame, sete, energia, socialita' calano col tempo. Quando uno
        // scende sotto soglia il NPC cambia comportamento (va a mangiare,
        // si siede, parla con altri). Il bisogno piu' urgente ha priorita'.
        public NPCNeeds Needs { get; private set; } = new NPCNeeds();
        private float _needsTickAt;

        // ── culling per distanza ──
        // Ogni chunk popola fino a 50 pedoni e il mondo porta 49 chunk:
        // senza culling si avrebbero ~2450 NPC animati/fisica/render sempre
        // attivi. Qui invece oltre la soglia disattiviamo renderer + targhetta
        // (il costo dominante): il pedone continua a camminare (logica idle)
        // ma non si disegna ne' pesa sulla fisica grafica.
        private const float CullDistSqr = 280f * 280f;   // ~280 m
        // Inclinazione massima (gradi) del busto sulla pendenza DEM: i pedoni
        // seguono il dislivello senza mai sbilanciarsi del tutto (piedi a terra).
        private const float MaxPedInclineDeg = 22f;
        private Renderer[] _renderers;
        private bool _culled = true;
        private Camera _camCache;
        private float _camRefreshAt;
        private static readonly int MaxActiveNpc = 180;

        /// <summary>Pedoni attualmente in scena (per il rilevatore di travol-
        /// gimenti): aggiunti/rimossi con OnEnable/OnDisable.</summary>
        public static readonly List<NPCController> Active =
            new List<NPCController>();

        private void OnEnable()
        {
            if (!Active.Contains(this)) Active.Add(this);
        }

        private void OnDisable()
        {
            Active.Remove(this);
        }

        // ── identita' per la chat (tap-to-talk) ──
        public string NpcId { get; private set; }
        public string DisplayName { get; private set; }

        // personaggio RealLife associato (roleplay): id reale del backend,
        // usato per la chat cosi' l'IA parla con la personalita' giusta
        public string CharacterId { get; private set; }

        private bool poiWander;
        public string CharacterRole { get; private set; }
        public string CharacterAvatar { get; private set; }

        private static readonly string[] Names =
        {
            "Marco", "Giulia", "Luca", "Anna", "Paolo", "Sara", "Franco",
            "Chiara", "Antonio", "Elena", "Giovanni", "Maria", "Salvatore",
            "Rosaria", "Domenico", "Carmela", "Vito", "Nadia",
        };

        public void Init(Vector3[] path, System.Random rng, string npcId, int startIndex = 0)
        {
            waypoints = path;
            currentTarget = startIndex;
            walking = false;
            pauseTimer = Random.Range(0.5f, 2f);
            _shopper = rng != null && rng.NextDouble() < 0.45;
            _dailyNextAt = Time.time + Random.Range(4f, 12f);
            NpcId = npcId;
            DisplayName = Names[rng.Next(Names.Length)];

            // casa = punto di spawn (torna a dormire la sera)
            _homePos = (waypoints != null && waypoints.Length > 0)
                ? waypoints[startIndex % waypoints.Length]
                : transform.localPosition;

            // lavoro = POI piu vicino (se esiste, altrimenti gira senza meta)
            _hasWork = false;
            _workPos = Vector3.zero;

            SetupModel(rng);
            if (waypoints.Length > 0)
            {
                // I waypoint dei chunk sono coordinate LOCALI al root del chunk
                // (NPCPopulator li genera con ToLocal): se li scrivessimo su
                // transform.position (world) i pedoni di ogni chunk non-origine
                // nascerebbero spostati di -root.position e dopo un rebase
                // resterebbero appesi nel vuoto. Comunque locali, per gli
                // spawner legacy/taxi (radice a origine) local == world.
                transform.localPosition = waypoints[startIndex % waypoints.Length];
            }

            // Targhetta col nome anche per i cittadini senza personaggio
            // RealLife: rende evidente chi si puo' toccare per parlare.
            EnsureNameTag();

            // roleplay: collega questo cittadino al personaggio RealLife
            // (immediato se la directory e' in cache, altrimenti appena carica)
            CityCharacterDirectory.Attach(this);

            // 30% chance to have a mission
            if (rng.NextDouble() < 0.30)
            {
                mission = gameObject.AddComponent<NPCMission>();
                try
                {
                    var data = NPCMission.GenerateMissionData(rng.Next(100000));
                    mission.ApplyData(data);
                    mission.AttachToNPC(this);
                }
                catch (System.Exception me)
                {
                    Debug.LogWarning("[NPCController] mission init fallita su " +
                        DisplayName + ": " + me);
                    Destroy(mission);
                    mission = null;
                }

                // Add trigger collider for mission interaction
                var triggerCol = gameObject.AddComponent<CapsuleCollider>();
                triggerCol.height = 2.5f;
                triggerCol.radius = 1.5f;
                triggerCol.center = new Vector3(0f, 1f, 0f);
                triggerCol.isTrigger = true;
            }

            CacheRenderers();
            UpdateCull(true);
        }

        /// <summary>Prepara renderer e camera per il culling per distanza.
        /// Vanno inclusi i renderer SKINNED e la targhetta TMP (figli).</summary>
        private void CacheRenderers()
        {
            _renderers = GetComponentsInChildren<Renderer>(true);
            _camCache = Camera.main;
            _camRefreshAt = 0f;
        }

        /// <summary>
        /// Abilita/disabilita renderer + targhetta in base alla distanza dalla
        /// camera (o alla richiesta forzata). Il pedone resta "vivo" (Update,
        /// fisica, missioni) ma non si disegna da lontano.
        /// </summary>
        private void UpdateCull(bool force = false)
        {
            if (_renderers == null || _renderers.Length == 0) return;
            bool shouldCull = true;
            float now = force ? -1f : Time.unscaledTime;
            if (now >= _camRefreshAt)
            {
                if (_camCache == null) _camCache = Camera.main;
                _camRefreshAt = now + 1f;
            }
            if (_camCache != null)
            {
                float dsqr = (_camCache.transform.position - transform.position).sqrMagnitude;
                shouldCull = dsqr > CullDistSqr;
            }
            if (shouldCull == _culled && !force) return;
            _culled = shouldCull;
            for (int i = 0; i < _renderers.Length; i++)
                if (_renderers[i] != null) _renderers[i].enabled = !shouldCull;
            if (_nameTag != null && _nameTagTmp != null)
            {
                bool show = !shouldCull;
                if (_nameTag.gameObject.activeSelf != show)
                    _nameTag.gameObject.SetActive(show);
            }
        }

        /// <summary>Applica l'identita' del personaggio RealLife (roleplay).</summary>
        /// <summary>
        /// Attiva la passeggiata verso POI (in coordinate MONDO): i passeggeri
        /// dei taxi che scendono per strada dirigono verso le aree d'interesse
        /// piu' vicine, come cittadini veri. I pedoni dei chunk non la usano
        /// (i loro waypoint sono locali al chunk).
        /// </summary>
        public void EnablePoiWander(bool on)
        {
            poiWander = on;
        }

        /// <summary>Inserisce nel giro di cammino una tappa verso il POI piu'
        /// vicino (coordinate mondo), se esiste nelle vicinanze.</summary>
        public void WanderToNearestPoi()
        {
            if (!poiWander) return;
            if (waypoints == null || waypoints.Length == 0) return;
            if (Random.Range(0f, 1f) > 0.8f) return;
            GeoCoord g = WorldOrigin.ToGeo(transform.position);
            var poi = City.Vehicle.VehiclePoiRegistry.NearestAny(
                g.lat, g.lng, 1500);
            if (poi == null) return;
            Vector3 wp = WorldOrigin.ToWorld(poi.lat, poi.lng);
            wp.y = TileElevation.HeightAtWorld(wp) + 0.12f;

            // tappa subito dopo quella corrente (non a meta' percorso)
            var list = new List<Vector3>(waypoints);
            int at = (currentTarget + 1) % list.Count;
            list.Insert(at, wp);
            waypoints = list.ToArray();
        }

        public void ApplyCharacter(CityCharacterDirectory.CharacterDef def)
        {
            if (def == null || string.IsNullOrEmpty(def.id)) return;
            CharacterId = def.id;
            CharacterRole = def.role ?? "";
            CharacterAvatar = string.IsNullOrEmpty(def.avatar) ? "*" : def.avatar;
            if (!string.IsNullOrEmpty(def.name))
                DisplayName = def.name;
            EnsureNameTag();
        }

        // ── caos: travolgimento e fuga ──
        private bool _down;
        private float _downUntil;
        private float _knockCd;
        private float _fleeUntil;
        private float _baseSpeed = -1f;
        private Quaternion _rotBeforeFall;
        private Coroutine _fallCo;
        private Coroutine _bloodCoroutine;

        /// <summary>Il player ha travolto il pedone: cade, si lamenta, poi
        /// si rialza e scappa al doppio della velocita'. Se era un amico,
        /// l'amicizia ne risente (-3).</summary>
        public void KnockDown(Vector3 pushDir)
        {
            if (_down || Time.unscaledTime < _knockCd) return;
            _knockCd = Time.unscaledTime + 8f;
            _down = true;
            _downUntil = Time.unscaledTime + 3.2f;
            _rotBeforeFall = transform.rotation;
            walking = false;
            SetAnimSpeed(0f);

            var cap = GetComponent<CapsuleCollider>();
            if (cap != null) cap.enabled = false;

            SpawnBlood();

            if (!string.IsNullOrEmpty(CharacterId))
            {
                RelationshipManager.RemovePoints(CharacterId, 3);
                Toast("\ud83d\ude20 " + DisplayName +
                    " ti ha visto! Amicizia in pezzi...");
            }
            else
            {
                Toast("Hai fatto cadere un pedone!");
            }
            City.Environment.ChaosTracker.AddChaos(1);
            OsmDiag.Log("[Audit] Pedone travolto: " + DisplayName + " @" +
                transform.position.x.ToString("F1") + "," +
                transform.position.z.ToString("F1") + " y=" +
                transform.position.y.ToString("F2") + " dem=" +
                TileElevation.HeightAtWorld(transform.position).ToString("F2"));

            if (_fallCo != null) StopCoroutine(_fallCo);
            _fallCo = StartCoroutine(FallAndFlee(pushDir));
        }

        /// <summary>Crea una macchia di sangue a terra (decals piatte) piu' un
        /// piccolo burst di particelle rosse nel punto di impatto. Sparisce da
        /// sola dopo qualche secondo.</summary>
        private void SpawnBlood()
        {
            Vector3 ground = transform.position;
            ground.y = 0.02f;
            Vector3 fwd = transform.forward;
            fwd.y = 0f;

            int splats = Random.Range(2, 4);
            for (int i = 0; i < splats; i++)
            {
                var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
                q.name = "Sangue";
                UnityEngine.Object.Destroy(q.GetComponent<Collider>());
                q.transform.position = ground + fwd * (Random.Range(-0.35f, 0.45f))
                    + Vector3.Cross(Vector3.up, fwd) * (Random.Range(-0.25f, 0.25f));
                q.transform.rotation = Quaternion.Euler(90f, Random.Range(0f, 360f), 0f);
                float s = Random.Range(0.3f, 0.55f);
                q.transform.localScale = new Vector3(s, s, 1f);

                var mr = q.GetComponent<Renderer>();
                var mat = MakeBloodMat(new Color(0.5f, 0.02f, 0.02f, 0.9f));
                bool urp = mat.shader.name.StartsWith("Universal Render Pipeline/Lit");
                mr.sharedMaterial = mat;

                if (_bloodCoroutine != null) StopCoroutine(_bloodCoroutine);
                _bloodCoroutine = StartCoroutine(FadeBlood(q, mat, urp));
            }

            // piccolo burst di gocce rosse proiettate all'indietro
            Vector3 origin = ground + Vector3.up * 0.6f;
            Vector3 back = -fwd;
            int drops = Random.Range(8, 14);
            for (int i = 0; i < drops; i++)
            {
                var drop = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                drop.name = "SangueGoccia";
                UnityEngine.Object.Destroy(drop.GetComponent<Collider>());
                drop.transform.position = origin;
                float s = Random.Range(0.04f, 0.1f);
                drop.transform.localScale = Vector3.one * s;
                drop.GetComponent<Renderer>().sharedMaterial =
                    MakeBloodMat(Color.red);
                Vector3 v = back * Random.Range(1f, 3f)
                    + Vector3.up * Random.Range(0.6f, 2.2f)
                    + Vector3.Cross(Vector3.up, fwd) * Random.Range(-0.8f, 0.8f);
                StartCoroutine(BloodDropFlight(drop, v));
            }
        }

        private IEnumerator BloodDropFlight(GameObject drop, Vector3 vel)
        {
            float t = 0f;
            float dur = Random.Range(0.5f, 0.8f);
            while (t < dur)
            {
                t += Time.deltaTime;
                vel.y -= 6f * Time.deltaTime;   // gravita'
                drop.transform.position += vel * Time.deltaTime;
                yield return null;
            }
            if (drop != null) Object.Destroy(drop);
        }

        private IEnumerator FadeBlood(GameObject q, Material mat, bool urp)
        {
            float t = 0f;
            float dur = 4f;
            while (t < dur)
            {
                t += Time.deltaTime;
                float a = Mathf.Lerp(0.9f, 0f, t / dur);
                if (urp) mat.SetColor("_BaseColor",
                    new Color(0.5f, 0.02f, 0.02f, Mathf.Clamp01(a)));
                else mat.SetColor("_Color",
                    new Color(0.5f, 0.02f, 0.02f, Mathf.Clamp01(a)));
                yield return null;
            }
            if (q != null) Object.Destroy(q);
        }

        private IEnumerator FallAndFlee(Vector3 dir)
        {
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.001f) dir = Vector3.forward;
            dir.Normalize();
            Vector3 axis = Vector3.Cross(Vector3.up, dir);
            Vector3 pivot = transform.position + dir * 0.15f;
            pivot.y = Mathf.Max(0f, transform.position.y - 0.85f);

            const float totalDeg = 86f;
            float done = 0f;
            while (done < totalDeg)
            {
                float step = 420f * Time.deltaTime;
                if (done + step > totalDeg) step = totalDeg - done;
                transform.RotateAround(pivot, axis, step);
                done += step;
                yield return null;
            }

            while (Time.unscaledTime < _downUntil) yield return null;

            // si rialza
            done = 0f;
            while (done < totalDeg)
            {
                float step = 260f * Time.deltaTime;
                if (done + step > totalDeg) step = totalDeg - done;
                transform.RotateAround(pivot, axis, -step);
                done += step;
                yield return null;
            }
            transform.rotation = _rotBeforeFall;

            var cap = GetComponent<CapsuleCollider>();
            if (cap != null) cap.enabled = true;

            // fuga al doppio della velocita' verso il waypoint piu' lontano
            if (_baseSpeed < 0f) _baseSpeed = walkSpeed;
            walkSpeed = _baseSpeed * 2f;
            _fleeUntil = Time.unscaledTime + 6f;
            if (waypoints != null && waypoints.Length > 0)
            {
                int far = 0;
                float best = -1f;
                for (int i = 0; i < waypoints.Length; i++)
                {
                    // waypoint in coordinate locali -> confronto col localPosition
                    float d = (waypoints[i] - transform.localPosition).sqrMagnitude;
                    if (d > best) { best = d; far = i; }
                }
                currentTarget = far;
            }
            walking = true;
        }

        // ── targhetta roleplay (nome, ruolo, livello amicizia) ──
        private Transform _nameTag;
        private TextMeshPro _nameTagTmp;
        private float _labelRefresh;

        private void EnsureNameTag()
        {
            if (_nameTag != null) return;
            var go = new GameObject("NameTag");
            _nameTag = go.transform;
            _nameTag.SetParent(transform, false);
            _nameTag.localPosition = new Vector3(0f, 2.4f, 0f);
            // compensa la scala ridotta del modello (0.455): testo a dimensione mondo costante
            float sc = Mathf.Max(0.01f, transform.lossyScale.x);
            _nameTag.localScale = Vector3.one * (1f / sc);
            var rt = go.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(7f, 2f);
            _nameTagTmp = go.AddComponent<TextMeshPro>();
            _nameTagTmp.fontSize = 2.6f;
            _nameTagTmp.alignment = TextAlignmentOptions.Center;
            _nameTagTmp.color = Color.white;
            go.SetActive(false);
        }

        private void UpdateNameTag()
        {
            if (_nameTag == null || _nameTagTmp == null) return;
            if (_culled)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            var cam = _camCache != null ? _camCache : Camera.main;
            if (cam == null)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            bool show = (cam.transform.position - transform.position).sqrMagnitude < 400f;
            if (!show)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            if (!_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(true);
            _nameTag.rotation = cam.transform.rotation;
            if (Time.unscaledTime < _labelRefresh) return;
            _labelRefresh = Time.unscaledTime + 0.5f;
            string txt = CharacterAvatar + " " + DisplayName;
            if (!string.IsNullOrEmpty(CharacterRole))
                txt += "\n<size=60%>" + CharacterRole + "</size>";
            if (!string.IsNullOrEmpty(CharacterId))
            {
                string lvl = RelationshipManager.LevelLabel(
                    RelationshipManager.LevelIndex(CharacterId));
                txt += "\n<size=50%><color=#9fdcae>" + lvl + "</color></size>";
            }
            // indicatore bisogni critici (rosso se fame/sete/energia, giallo se social basso)
            string needLabel = Needs.StatusLabel();
            if (!string.IsNullOrEmpty(needLabel))
            {
                bool crit = Needs.HungerCritical || Needs.ThirstCritical || Needs.EnergyCritical;
                string col = crit ? "#ff4444" : "#ffcc44";
                txt += "\n<size=50%><color=" + col + ">" + needLabel + "</color></size>";
            }
            _nameTagTmp.text = txt;
        }

        private void Toast(string msg)
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(msg);
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, false);
        }

        /// <summary>Di notte i pedoni camminano a velocita ridotta.</summary>
        private bool NightNow()
        {
            var dnm = City.Environment.DayNightManager.Instance;
            if (dnm == null) return false;
            float h = dnm.ClockHours;
            return h < 7f || h >= 22f;
        }

        /// <summary>Routine quotidiana Brookhaven-style: ogni NPC ha una
        /// schedule fissa in 4 fasi (mattina=lavoro, pomeriggio=shopping/
        /// passeggiata, sera=torna a casa, notte=riposa).
        /// I bisogni NPC (fame/sete/energia/socialita') sovrascrivono la
        /// schedule quando uno scende sotto soglia critica.</summary>
        private void MaybeDailyLife()
        {
            float now = Time.time;
            if (now < _dailyNextAt) return;
            _dailyNextAt = now + Random.Range(10f, 24f);

            // ── priorita' ai bisogni (pattern ECS-Starter) ──
            // se fame/sete/energia sono critici, interrompi la routine
            // normale e soddisfa il bisogno prima.
            var urgent = Needs.MostUrgent;
            if (urgent != NPCNeeds.Urgency.None)
            {
                HandleUrgentNeed(urgent);
                return;
            }

            var dnm = City.Environment.DayNightManager.Instance;
            if (dnm == null) return;
            float h = dnm.ClockHours;

            // ── determina la fase corrente ──
            DailyPhase newPhase;
            if (h >= 7f && h < 12f)
                newPhase = DailyPhase.Morning;     // 7-12: lavoro
            else if (h >= 12f && h < 18f)
                newPhase = DailyPhase.Afternoon;   // 12-18: shopping/passeggiata
            else if (h >= 18f && h < 22f)
                newPhase = DailyPhase.Evening;     // 18-22: torna a casa
            else
                newPhase = DailyPhase.Night;       // 22-7: riposo a casa

            // ── cambio di fase: esegui l'azione di transizione ──
            if (newPhase != _phase)
            {
                OnPhaseChange(_phase, newPhase);
                _phase = newPhase;
            }

            // ── azione continua durante la fase ──
            switch (_phase)
            {
                case DailyPhase.Morning:
                    // al lavoro: se non ci sei ancora, cammina verso il POI
                    if (!_atWork && _hasWork && Random.value > 0.3f)
                        InsertWorkStop();
                    break;

                case DailyPhase.Afternoon:
                    // shopping/passeggiata: tappa casuale verso negozio
                    if (_shopper && Random.value > 0.4f)
                        InsertDailyPoiStop();
                    break;

                case DailyPhase.Evening:
                    // torna a casa se sei lontano
                    if (!_headingHome && Random.value > 0.2f)
                        InsertHomeStop();
                    break;

                case DailyPhase.Night:
                    // fermi a casa (velocita ridotta se cammini ancora)
                    break;
            }
        }

        /// <summary>Gestisce il cambio di fase: ferma il NPC quando serve,
        /// cambia velocita', resetta le bandiere.</summary>
        private void OnPhaseChange(DailyPhase from, DailyPhase to)
        {
            _atWork = false;
            _headingHome = false;

            switch (to)
            {
                case DailyPhase.Morning:
                    // mattina: riprendi a camminare verso il lavoro
                    walking = true;
                    break;

                case DailyPhase.Afternoon:
                    // pomeriggio: libera, cammina verso i negozi
                    walking = true;
                    break;

                case DailyPhase.Evening:
                    // sera: cammina verso casa
                    walking = true;
                    break;

                case DailyPhase.Night:
                    // notte: ferma il NPC (simula riposo)
                    walking = false;
                    pauseTimer = 999f;  // resta fermo fino alla mattina
                    SetAnimSpeed(0f);
                    break;
            }
        }

        /// <summary>Gestisce un bisogno urgente: interrompe la routine e
        /// soddisfa il bisogno (va al bar, si siede, ecc.).
        /// Usa POI specifici: bar per fame/sete, panchina per energia.</summary>
        private void HandleUrgentNeed(NPCNeeds.Urgency urgent)
        {
            switch (urgent)
            {
                case NPCNeeds.Urgency.Hunger:
                case NPCNeeds.Urgency.Thirst:
                    // cerca il bar piu vicino come waypoint
                    InsertBarStop();
                    if (urgent == NPCNeeds.Urgency.Hunger)
                        Needs.Feed(40f);
                    else
                        Needs.Drink(50f);
                    OsmDiag.Log("[NPC] " + DisplayName +
                        (urgent == NPCNeeds.Urgency.Hunger ? " ha fame -> bar" : " ha sete -> bar"));
                    break;

                case NPCNeeds.Urgency.Energy:
                    // si siede a riposare (come sedersi su panchina)
                    walking = false;
                    pauseTimer = Random.Range(8f, 15f);
                    Needs.Rest(35f);
                    SetAnimSpeed(0f);
                    OsmDiag.Log("[NPC] " + DisplayName + " e' stanco -> riposa");
                    break;

                case NPCNeeds.Urgency.Social:
                    // cerca un altro NPC vicino per parlare
                    TryTalkToNearby();
                    break;
            }
        }

        /// <summary>Cerca un NPC vicino e scambia due chiacchiere
        /// (soddisfa il bisogno social per entrambi).</summary>
        private void TryTalkToNearby()
        {
            float range = 5f;
            var all = Active;
            for (int i = 0; i < all.Count; i++)
            {
                var other = all[i];
                if (other == null || other == this) continue;
                float d = Vector3.Distance(transform.position, other.transform.position);
                if (d > range) continue;
                // trovato: entrambi socializzano
                Needs.Talk(30f);
                other.Needs.Talk(30f);
                // pausa brevemente per "parlare"
                walking = false;
                pauseTimer = Random.Range(3f, 6f);
                SetAnimSpeed(0f);
                return;
            }
        }

        /// <summary>Social interaction periodica: se un altro NPC e' vicino
        /// e entrambi hanno il social basso, scambiano due parole.</summary>
        private void MaybeSocialInteraction()
        {
            if (!Needs.SocialLow) return;
            if (Random.value > 0.15f) return; // raro, non ogni tick
            TryTalkToNearby();
        }

        /// <summary>Quando il player dorme, tutti gli NPC vicini (50m)
        /// recuperano energia. Chiamato da SleepSystem.Restore.</summary>
        public static void RestAllNearby(Vector3 playerPos, float radius, float amount)
        {
            float r2 = radius * radius;
            for (int i = 0; i < Active.Count; i++)
            {
                var npc = Active[i];
                if (npc == null) continue;
                if ((npc.transform.position - playerPos).sqrMagnitude > r2) continue;
                npc.Needs.Rest(amount);
            }
        }

        /// <summary>Inserisce nel percorso una tappa verso il luogo di lavoro
        // (POI vicino, tipo office/shop). Coordinate locali chunk.</summary>
        private void InsertWorkStop()
        {
            if (waypoints == null || waypoints.Length < 2) return;
            if (_poiStopIdx >= 0) return;

            // prima volta: cerca il POI piu vicino e lo salva
            if (!_hasWork)
            {
                GeoCoord g = WorldOrigin.ToGeo(transform.position);
                var poi = City.Vehicle.VehiclePoiRegistry.NearestAny(
                    g.lat, g.lng, 2000);
                if (poi == null) return;
                Vector3 wp = WorldOrigin.ToWorld(poi.lat, poi.lng);
                wp.y = TileElevation.HeightAtWorld(wp) + 0.12f;
                Transform rootT = transform.root;
                if (rootT != transform)
                    wp -= new Vector3(rootT.position.x, 0f, rootT.position.z);
                _workPos = wp;
                _hasWork = true;
            }

            // aggiungi work come waypoint temporaneo
            var list = new List<Vector3>(waypoints);
            int at = (currentTarget + 1) % list.Count;
            list.Insert(at, _workPos);
            waypoints = list.ToArray();
            _poiStopIdx = at;
            _atWork = true;
        }

        /// <summary>Inserisce nel percorso una tappa verso casa (spawn
        // point iniziale). Coordinate locali chunk.</summary>
        private void InsertHomeStop()
        {
            if (waypoints == null || waypoints.Length < 2) return;
            if (_poiStopIdx >= 0) return;
            if (Vector3.Distance(transform.localPosition, _homePos) < 1f)
            {
                _headingHome = true;
                return;
            }

            var list = new List<Vector3>(waypoints);
            int at = (currentTarget + 1) % list.Count;
            list.Insert(at, _homePos);
            waypoints = list.ToArray();
            _poiStopIdx = at;
            _headingHome = true;
        }

        /// <summary>Aggiunge al percorso una tappa verso il POI piu vicino
        /// (negozio/parco/bancomat), convertita nel frame LOCALE del chunk
        /// cosi funziona anche per i pedoni dei chunk (i loro waypoint sono
        /// locali). Una sola tappa per volta (bandiera _poiStopIdx).</summary>
        private void InsertDailyPoiStop()
        {
            if (waypoints == null || waypoints.Length < 2) return;
            if (_poiStopIdx >= 0 || Random.value > 0.6f) return;
            GeoCoord g = WorldOrigin.ToGeo(transform.position);
            var poi = City.Vehicle.VehiclePoiRegistry.NearestAny(
                g.lat, g.lng, 1500);
            if (poi == null) return;
            Vector3 wp = WorldOrigin.ToWorld(poi.lat, poi.lng);
            wp.y = TileElevation.HeightAtWorld(wp) + 0.12f;
            Transform rootT = transform.root;
            if (rootT != transform)
                wp -= new Vector3(rootT.position.x, 0f, rootT.position.z);
            var list = new List<Vector3>(waypoints);
            int at = (currentTarget + 1) % list.Count;
            list.Insert(at, wp);
            waypoints = list.ToArray();
            _poiStopIdx = at;
        }

        /// <summary>Inserisce nel percorso una tappa verso il bar piu vicino
        /// (POI di tipo bar), convertita nel frame LOCALE del chunk.</summary>
        private void InsertBarStop()
        {
            if (waypoints == null || waypoints.Length < 2) return;
            if (_poiStopIdx >= 0) return;
            GeoCoord g = WorldOrigin.ToGeo(transform.position);
            var poi = City.Vehicle.VehiclePoiRegistry.Nearest("bar", g.lat, g.lng);
            if (poi == null) return;
            Vector3 wp = WorldOrigin.ToWorld(poi.lat, poi.lng);
            wp.y = TileElevation.HeightAtWorld(wp) + 0.12f;
            Transform rootT = transform.root;
            if (rootT != transform)
                wp -= new Vector3(rootT.position.x, 0f, rootT.position.z);
            var list = new List<Vector3>(waypoints);
            int at = (currentTarget + 1) % list.Count;
            list.Insert(at, wp);
            waypoints = list.ToArray();
            _poiStopIdx = at;
        }

        private void Update()
        {
            UpdateCull();
            UpdateNameTag();
            if (_down) return;
            if (_fleeUntil > 0f && Time.unscaledTime >= _fleeUntil)
            {
                walkSpeed = _baseSpeed > 0f ? _baseSpeed : walkSpeed * 0.5f;
                _fleeUntil = 0f;
            }

            // tick bisogni NPC ogni 0.5s (non ogni frame per performance)
            if (Time.time >= _needsTickAt)
            {
                _needsTickAt = Time.time + 0.5f;
                Needs.Tick(0.5f);
                MaybeSocialInteraction();
            }

            if (waypoints == null || waypoints.Length < 2) return;
            MaybeDailyLife();

            // Elevazione: sempre aggiornata (anche da fermo), cosi' i pedoni
            // non restano appesi a y=0.12 quando si fermano sul marciapiede.
            SnapToGround();

            if (!walking)
            {
                pauseTimer -= Time.deltaTime;
                if (pauseTimer <= 0f)
                {
                    walking = true;
                    currentTarget = (currentTarget + 1) % waypoints.Length;
                }
                SetAnimSpeed(0f);
                return;
            }

            Vector3 target = waypoints[currentTarget];
            Vector3 npcLocal = transform.localPosition;
            Vector3 dir = target - npcLocal;
            dir.y = 0f;
            float dist = dir.magnitude;

            if (dist < 0.3f)
            {
                walking = false;
                bool atPoi = _poiStopIdx >= 0 && currentTarget == _poiStopIdx;
                pauseTimer = atPoi
                    ? Random.Range(8f, 14f)
                    : Random.Range(pauseMin, pauseMax);
                if (atPoi) _poiStopIdx = -1;
                return;
            }

            float nightMul = _phase == DailyPhase.Night ? 0.3f
                : _phase == DailyPhase.Evening ? 0.7f : 1f;
            float effSpeed = walkSpeed * nightMul;
            Vector3 move = dir.normalized * effSpeed * Time.deltaTime;
            npcLocal += move;
            transform.localPosition = npcLocal;

            Quaternion look = Quaternion.LookRotation(dir.normalized,
                TileElevation.SlopeUpAtWorld(transform.position, MaxPedInclineDeg));
            transform.rotation = Quaternion.Slerp(transform.rotation, look, 8f * Time.deltaTime);

            SetAnimSpeed(walking ? effSpeed : 0f);
        }

        /// <summary>Quota del terreno sotto il pedone: prima prova la
        /// superficie fisica (raycast verso il basso sui collider) ignorando
        /// se stesso, gli altri NPC, i trigger e il layer 8 (edifici/props,
        /// cosi' non finisce su un tetto); se la colonna non trova nulla usa
        /// l'altimetria DEM (TileElevation). L'origine e' ancorata alla quota
        /// DEM come nello spawn del player: se la superficie sta SOPRA di noi
        /// (pedone sotto un dosso del terreno proxy) la colonna la raggiunge
        /// comunque e il pedone risale, non passa attraverso.</summary>
        private float GroundHeightAt(Vector3 pos)
        {
            float elev = TileElevation.HeightAtWorld(pos);
            const int GroundMask = ~(1 << 8);
            if (!_culled)
            {
                Vector3 from = pos + Vector3.up * 220f;
                float demProbe = elev + 150f;
                if (demProbe > from.y) from.y = demProbe;
                RaycastHit[] hits = Physics.RaycastAll(from, Vector3.down,
                    700f, GroundMask, QueryTriggerInteraction.Ignore);
                int best = -1;
                float bestSqr = float.PositiveInfinity;
                for (int i = 0; i < hits.Length; i++)
                {
                    var h = hits[i];
                    if (h.collider == null) continue;
                    var t = h.collider.transform;
                    if (t == transform || t.IsChildOf(transform)) continue;
                    if (h.collider.GetComponentInParent<NPCController>() != null)
                        continue;
                    float dsqr = h.distance * h.distance;
                    if (dsqr < bestSqr) { bestSqr = dsqr; best = i; }
                }
                if (best >= 0) return hits[best].point.y;
            }
            return elev;
        }

        /// <summary>Aggiorna la quota del pedone sulla superficie del terreno.
        /// Chiamato OGNI frame (anche quando il pedone e' fermo), cosi' non
        /// resta appeso a y=0.12 se il terreno e' a quota DEM.</summary>
        private void SnapToGround()
        {
            Transform rootT = transform.root;
            bool rootLevel = rootT == transform;
            Vector3 npcLocal = transform.localPosition;
            Vector3 npcWorld = rootLevel
                ? npcLocal
                : npcLocal + new Vector3(rootT.position.x, 0f, rootT.position.z);
            npcWorld.y = GroundHeightAt(npcWorld) + 0.12f;
            npcLocal.y = rootLevel ? npcWorld.y : npcWorld.y - rootT.position.y;
            transform.localPosition = npcLocal;
        }

        private CharacterWalker walker;
        private Animator animator;

        private void SetAnimSpeed(float s)
        {
            if (walker != null) walker.SetSpeed(s);
            if (animator != null) animator.SetFloat("Speed", s);
        }

        // Il modello Kenney arriva gia' montato da NPCPopulator: qui si aggiunge
        // solo l'animazione e il collider solido. Se manca il prefab si ricade
        // sul vecchio omino di cubi cosi' gli NPC non spariscono mai.
        private void SetupModel(System.Random rng)
        {
            bool kenney = GetComponentInChildren<SkinnedMeshRenderer>(true) != null;
            if (!kenney)
                BuildCubeModel();

            walker = CharacterWalker.AttachIfNeeded(gameObject);
            animator = GetComponentInChildren<Animator>();

            var cc = gameObject.AddComponent<CapsuleCollider>();
            cc.height = 1.7f;
            cc.radius = 0.25f;
            cc.center = new Vector3(0f, 0.85f, 0f);

            // Corpo cinematico: il pedone resta un ostacolo SOLIDO per le auto
            // (giocatore e traffico) anche se si muove via transform; senza un
            // Rigidbody qui la fisica non garantisce il blocco contro la car.
            var rb = gameObject.AddComponent<Rigidbody>();
            rb.isKinematic = true;
            rb.useGravity = false;
            rb.interpolation = RigidbodyInterpolation.Interpolate;
        }

        private static readonly Color[] SkinColors = new Color[]
        {
            new Color(0.87f, 0.72f, 0.58f),
            new Color(0.75f, 0.55f, 0.40f),
            new Color(0.60f, 0.42f, 0.30f),
        };

        private static readonly Color[] ShirtColors = new Color[]
        {
            new Color(0.2f, 0.4f, 0.8f),
            new Color(0.8f, 0.2f, 0.2f),
            new Color(0.2f, 0.7f, 0.3f),
            new Color(0.9f, 0.7f, 0.1f),
            new Color(0.6f, 0.2f, 0.7f),
            new Color(0.1f, 0.6f, 0.7f),
            new Color(0.9f, 0.5f, 0.1f),
        };

        // Fallback low-poly (stato originale del sistema NPC).
        private void BuildCubeModel()
        {
            System.Random rng = new System.Random(NpcId != null ? NpcId.GetHashCode() : 0);
            Color skin = SkinColors[rng.Next(SkinColors.Length)];
            Color shirt = ShirtColors[rng.Next(ShirtColors.Length)];
            Color pants = new Color(0.2f, 0.2f, 0.3f);
            float bodyW = 0.4f, bodyH = 0.55f, bodyD = 0.25f;

            Part("Head", PrimitiveType.Sphere, new Vector3(0f, 1.45f, 0f),
                Vector3.one * 0.44f, skin);
            Part("Body", PrimitiveType.Cube, new Vector3(0f, 0.95f, 0f),
                new Vector3(bodyW, bodyH, bodyD), shirt);
            Part("LegL", PrimitiveType.Cube, new Vector3(-0.1f, 0.4f, 0f),
                new Vector3(0.12f, 0.5f, 0.14f), pants);
            Part("LegR", PrimitiveType.Cube, new Vector3(0.1f, 0.4f, 0f),
                new Vector3(0.12f, 0.5f, 0.14f), pants);
            Part("ArmL", PrimitiveType.Cube,
                new Vector3(-bodyW * 0.5f - 0.1f, 1.0f, 0f),
                new Vector3(0.1f, 0.4f, 0.1f), skin);
            Part("ArmR", PrimitiveType.Cube,
                new Vector3(bodyW * 0.5f + 0.1f, 1.0f, 0f),
                new Vector3(0.1f, 0.4f, 0.1f), skin);
        }

        private void Part(string name, PrimitiveType type, Vector3 pos,
            Vector3 scale, Color color)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            go.transform.SetParent(transform, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = MakeMat(color);
            var col = go.GetComponent<Collider>();
            if (col != null) col.enabled = false;
        }

        private static readonly System.Collections.Generic.Dictionary<Color, Material> matCache
            = new System.Collections.Generic.Dictionary<Color, Material>();

        private static Material MakeBloodMat(Color c)
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            bool urp = shader != null && shader.name.StartsWith("Universal Render Pipeline/Lit");
            var m = new Material(shader);
            if (urp)
            {
                m.SetColor("_BaseColor", c);
                m.SetFloat("_Surface", 1f);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            }
            else
            {
                m.SetColor("_Color", c);
                m.SetFloat("_Mode", 3f);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_ALPHABLEND_ON");
            }
            return m;
        }

        private static Material MakeMat(Color c)
        {
            if (matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            matCache[c] = m;
            return m;
        }
    }
}
