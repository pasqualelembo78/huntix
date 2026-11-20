using UnityEngine;

namespace City.OSM
{
    /// <summary>Fonte del piano di appoggio rilevato da GroundSnapper.</summary>
    public enum GroundSource
    {
        None,
        ColliderShort,   // sonda corta: superficie fisica sotto i piedi
        ColliderLong,    // sonda lunga: superficie fisica in fondo alla colonna
        Dem              // fallback altimetrico (nessun collider trovato)
    }

    /// <summary>
    /// Risultato del rilievo del terreno sotto una coppia di piedi (world).
    /// `height` e' la quota assoluta del piano di appoggio (i piedi target
    /// stanno a height + clearance); `near` true se la superficie e' stata
    /// colpita dalla sonda CORTA, cioe' e' il piano su cui l'attore sta
    /// davvero camminando, non un fondo di valle lontano.
    /// </summary>
    public struct GroundSample
    {
        public bool found;
        public float height;
        public GroundSource source;
        public bool near;
        public float gapFeet;      // height - livelloPiedi (negativo = superficie sotto i piedi)
        public string surfaceName;

        public static readonly GroundSample Miss = default(GroundSample);

        public bool IsDem { get { return source == GroundSource.Dem; } }
    }

    /// <summary>Stato del follower verticale (uno per attore, non statico:
    /// ogni player/NPC mantiene la propria velocita' di inseguimento).</summary>
    public struct GroundFollowState
    {
        public float vel;        // velocita' verticale attuale del follower
    }

    /// <summary>
    /// SISTEMA PROFESSIONALE DI BINDING AL TERRENO, condiviso da player e NPC.
    ///
    /// Pattern usato dai giochi open-world terza persona per i personaggi su
    /// mondi heightfield/OSM fatti di collider separati:
    ///
    /// 1. PROBE CORTO (5 punti: centro + 4 angoli della capsula, ognuno con
    ///    sphere-cast) dai piedi verso il basso: trova il piano su cui il
    ///    personaggio CAMMINA DAVVERO (strade, marciapiedi, deck dei viadotti,
    ///    rampe, fallback B3). La sfera non riesce a infilarsi nello spiraglio
    ///    fra due MeshCollider adiacenti e i 4 angoli coprono tutto il
    ///    footprint: il suolo non sfugge piu' in nessuna direzione.
    /// 2. PROBE LUNGO (RaycastAll dall'alto, ancorato al DEM): solo se il
    ///    corto fallisce (terreno lontano sotto, es. dislivello/valle). Le
    ///    superfici SOPRA il livello piedi vengono escluse (ponte che passa
    ///    sopra la testa, tetti): si aggancia il fondo di valle, mai il cielo.
    /// 3. FALLBACK DEM (TileElevation): se nessun collider esiste ancora
    ///    (tile in generazione, offline), l'altimetria tiene comunque i piedi
    ///    a quota.
    /// 4. FOLLOW VERTICALE smussato e a velocita' limitata: salita di scalino
    ///    (<= 0,35 m) LENTA, discesa MAGNETICA, dislivelli veri al ritmo
    ///    storico. Niente teleport, niente "saltelli" in montagna, niente
    ///    affondo/volo se il terreno arriva o viene ricostruito sotto di noi.
    ///
    /// La mask esclude gli EDIFICI (layer 8): non si cammina sui tetti e, se
    /// il personaggio finisse per inerzia su un tetto, il sistema NON lo
    /// trascina giu' nel vuoto (la guardia "superficie non vista" sotto).
    /// </summary>
    public static class GroundSnapper
    {
        public const int BuildingLayer = 8;
        public const int WalkableMask = ~(1 << BuildingLayer);

        // ── Sonda corta (Giorno 6: 5 punti + sphere-cast) ──
        public const float ShortRadius = 0.22f;     // raggio della sfera: non entra nelle fessure
                                                    // fra due MeshCollider adiacenti (giunti chunk)
        public const float ShortStartAbove = 0.65f; // origine sopra i piedi: 0,65 - 0,22 = 0,43 m
                                                    // sopra la suola, abbastanza per non partire
                                                    // DENTRO un cordolo alto fino a ShortMaxRise
        public const float ShortDepth = 3.40f;      // 0,65 di partenza + 2,6 m sotto i piedi
                                                    // (portata utile invariata rispetto al passato)
        public const float ShortNorm = 0.1f;        // normale minimo (esclude pareti/soffitti)
        public const float ShortSpread = 0.28f;     // semibraccio del footprint: centro + 4 angoli
        public const float ShortMaxRise = 0.35f;    // superfici piu' alte di 0,35 m sopra i piedi
                                                    // vengono scartate (cordoli si, paraurti no;
                                                    // lo step assist e' il Giorno 7)

        // ── Sonda lunga ──
        public const float LongRise = 200f;         // partenza dal tetto del mondo in cima alla colonna
        public const float LongDepth = 420f;        // raggio della colonna
        public const float DemRelief = 150f;        // l'origine sale sempre sopra il DEM noto
        public const float LongNorm = 0.05f;
        public const float AboveFeetTolerance = 0.6f; // superfici sopra i piedi escluse (tetti/deck in volo)

        // ── Follower verticale ──
        public const float FollowDeadzone = 0.02f;
        public const float SmoothRate = 12f;        // /s: inseguimento esponenziale smussato
        public const float MaxClimbSpeed = 10f;     // m/s in salita (dolce)
        public const float MaxDescendSpeed = 30f;   // m/s in discesa (veloce ma controllata)
        public const float FallGraceM = 1.5f;       // oltre questo gap sotto i piedi la fisica si fa carico
        public const float PushUpGraceM = 1.5f;     // oltre questo gap sopra i piedi si rialza (sepolto)

        // ── Step assist / magnetismo (Giorno 7) ──
        public const float StepAssistHeight = 0.35f; // = ShortMaxRise: oltre e' un dislivello vero,
                                                     // non uno scalino da salire da fermi
        public const float StepAssistRate = 5f;      // /s: salita di scalino LENTA e smorzata
                                                     // (SmoothRate=12 dava lo "scatto" sul cordolo)
        public const float MaxStepSpeed = 2f;        // m/s: tetto duro dello step assist
        public const float MagnetRate = 18f;         // /s: discesa "magnetica", piu' rapida del climb
        public const float MagnetRange = 0.5f;       // m: cadute piu' grandi restano alla gravita'
        public const float GroundGraceS = 0.25f;     // s: tolleranza dopo isGrounded=false (cordoli)

        private static bool IsSelf(Transform t, Transform self)
        {
            if (t == null || self == null) return false;
            if (t == self) return true;
            return t.IsChildOf(self);
        }

        private static int _lastSyncFrame = -1;

        // Unity sincronizza i transform con PhysX PRIMA di ogni query solo se
        // autoSyncTransforms e' attivo (default); per essere robusti anche se
        // qualcuno lo spegne forziamo UNA sincronizzazione per frame (non per
        // sonda: con decine di NPC non si deve flushare PhysX a ogni chiamata).
        private static void SyncOnce()
        {
            int f = Time.frameCount;
            if (_lastSyncFrame == f) return;
            _lastSyncFrame = f;
            try { Physics.SyncTransforms(); }
            catch (System.Exception) { }
        }

        // Buffer CONDIVISO e riutilizzato: 5 sphere-cast per chiamata, zero
        // allocazioni per frame. Non e' rientrante (nessun callback gira
        // durante la sonda), quindi e' sicuro usalo per player e NPC.
        private static readonly RaycastHit[] _probeHits = new RaycastHit[24];

        private static Vector3 FootprintPoint(Vector3 center, Vector3 side,
            Vector3 fwd, int i)
        {
            switch (i)
            {
                case 0: return center;
                case 1: return center + side + fwd;
                case 2: return center + side - fwd;
                case 3: return center - side + fwd;
                default: return center - side - fwd;
            }
        }

        /// <summary>
        /// Sonda il footprint dell'attore in 5 punti (centro + 4 angoli).
        /// Ogni punto e' uno SPHERE-CAST corto: la sfera, avendo un raggio,
        /// non riesce a infilarsi nello spiraglio fra due MeshCollider
        /// adiacenti dove passava invece il raggio sottile. Per ogni punto
        /// si prende l'hit PIU' VICINO (PickBest); fra i 5 si prende la quota
        /// piu' alta, che e' il piano di appoggio dell'intero footprint.
        ///
        /// Attenzione: NON e' il vecchio errore "hit piu' alto della colonna".
        /// Qui ogni sonda ha gia' scartato i collider sopra di se' (e gli
        /// edifici layer 8 sono esclusi da WalkableMask); maxSurfaceY scarta
        /// ulteriormente tutto cio' che sta troppo sopra i piedi.
        ///
        /// Usata sia dalla sonda corta (SampleGround) sia dallo snap di
        /// spawn/teletrasporto (CityChunkedWorld.TryGroundColumn): una sola
        /// implementazione di "dove poggia l'attore".
        /// </summary>
        public static bool ProbeFootprint(Vector3 center, Vector3 side,
            Vector3 fwd, float radius, float depth, Transform self,
            float maxSurfaceY, out RaycastHit best)
        {
            best = default(RaycastHit);
            Transform bridge = CityChunkedWorld.Instance != null &&
                CityChunkedWorld.Instance.SpawnBridge != null
                ? CityChunkedWorld.Instance.SpawnBridge.transform : null;
            bool found = false;
            for (int i = 0; i < 5; i++)
            {
                Vector3 origin = FootprintPoint(center, side, fwd, i);
                int n = Physics.SphereCastNonAlloc(origin, radius, Vector3.down,
                    _probeHits, depth, WalkableMask, QueryTriggerInteraction.Ignore);
                RaycastHit local;
                if (!PickBest(_probeHits, n, self, bridge, out local)) continue;
                if (local.point.y > maxSurfaceY) continue;
                if (!found || local.point.y > best.point.y)
                {
                    best = local;
                    found = true;
                }
            }
            return found;
        }

        /// <summary>Sonda del piano di appoggio sotto il livello piedi `pos`
        /// (world). `self` = radice dell'attore (player/NPC): i suoi collider
        /// e lo SpawnBridge di sicurezza vengono esclusi dalla misura.</summary>
        public static GroundSample SampleGround(Vector3 pos, Transform self)
        {
            SyncOnce();

            float feetY = pos.y;
            Transform bridge = CityChunkedWorld.Instance != null &&
                CityChunkedWorld.Instance.SpawnBridge != null
                ? CityChunkedWorld.Instance.SpawnBridge.transform : null;

            // ── 1. Sonda corta a 5 punti: la superficie su cui camminiamo davvero ──
            Vector3 feet = new Vector3(pos.x, feetY + ShortStartAbove, pos.z);
            Vector3 side = self != null ? self.right * ShortSpread : Vector3.right * ShortSpread;
            Vector3 fwd = self != null ? self.forward * ShortSpread : Vector3.forward * ShortSpread;
            RaycastHit near;
            if (ProbeFootprint(feet, side, fwd, ShortRadius, ShortDepth, self,
                    feetY + ShortMaxRise, out near))
            {
                float surfY = near.point.y;
                return SampleFrom(surfY, GroundSource.ColliderShort, true,
                    surfY - feetY, near.collider.name);
            }

            // ── 2. Sonda lunga: fondo della colonna (dislivelli, tail) ──
            float dem = TileElevation.HeightAtWorld(new Vector3(pos.x, pos.y, pos.z));
            float fromY = Mathf.Max(feetY + LongRise, dem + DemRelief);
            RaycastHit[] hits = Physics.RaycastAll(
                new Vector3(pos.x, fromY, pos.z), Vector3.down, LongDepth,
                WalkableMask, QueryTriggerInteraction.Ignore);
            float best = float.MinValue;
            string bestName = "";
            for (int i = 0; i < hits.Length; i++)
            {
                var h = hits[i];
                if (h.collider == null) continue;
                if (IsSelf(h.collider.transform, self)) continue;
                if (IsBridge(h.collider.transform, bridge)) continue;
                if (h.normal.y < LongNorm) continue;
                // esclude le superfici SOPRA i piedi (deck/tetti che volano)
                if (h.point.y > feetY + AboveFeetTolerance) continue;
                if (h.point.y > best) { best = h.point.y; bestName = h.collider.name; }
            }
            if (best > float.MinValue)
                return SampleFrom(best, GroundSource.ColliderLong, false,
                    best - feetY, bestName);

            // ── 3. Fallback DEM (nessun collider: tile in generazione) ──
            if (dem > 0f)
                return SampleFrom(dem, GroundSource.Dem, false, dem - feetY, "DEM");

            return GroundSample.Miss;
        }

        /// <summary>Solo DEM, senza costi fisici: per gli attori cullati o
        /// lontani (NPC oltre il raggio di probe) che devono solo non restare
        /// Appesi a quota vecchia.</summary>
        public static GroundSample DemOnly(Vector3 pos)
        {
            float dem = TileElevation.HeightAtWorld(pos);
            if (dem > 0f)
                return SampleFrom(dem, GroundSource.Dem, false, dem - pos.y, "DEM");
            return GroundSample.Miss;
        }

        private static GroundSample SampleFrom(float height, GroundSource source,
            bool near, float gap, string name)
        {
            GroundSample s;
            s.found = true;
            s.height = height;
            s.source = source;
            s.near = near;
            s.gapFeet = gap;
            s.surfaceName = name ?? "";
            return s;
        }

        private static bool IsBridge(Transform t, Transform bridge)
        {
            if (bridge == null || t == null) return false;
            return t == bridge || t.IsChildOf(bridge);
        }

        /// <summary>Sceglie, dentro UNA singola sonda, l'hit PIU' VICINO
        /// (min distance), scartando i collider dell'attore (CharacterController/
        /// capsula), lo SpawnBridge di sicurezza e le pareti. La scelta fra i
        /// 5 punti del footprint avviene in ProbeFootprint, non qui.</summary>
        private static bool PickBest(RaycastHit[] hits, int count, Transform self,
            Transform bridge, out RaycastHit best)
        {
            best = default(RaycastHit);
            int idx = -1;
            for (int i = 0; i < count && i < hits.Length; i++)
            {
                var h = hits[i];
                if (h.collider == null) continue;
                if (IsSelf(h.collider.transform, self)) continue;
                if (IsBridge(h.collider.transform, bridge)) continue;
                if (h.normal.y < ShortNorm) continue;
                if (idx < 0 || h.distance < hits[idx].distance) idx = i;
            }
            if (idx < 0) return false;
            best = hits[idx];
            return true;
        }

        /// <summary>
        /// Esegue il follower verticale: porta `feetY` verso `targetFeet`
        /// con inseguimento esponenziale smussato, velocita' verticale limitata
        /// (salita dolce, discesa controllata). Ritorna il nuovo livello piedi.
        /// </summary>
        public static float Follow(ref GroundFollowState st, float feetY,
            float targetFeet, float dt)
        {
            if (dt <= 0f) return feetY;
            float err = targetFeet - feetY;
            if (Mathf.Abs(err) < FollowDeadzone)
            {
                st.vel = 0f;
                return feetY;
            }
            // GIORNO 7: tre regimi, tutti inseguimento esponenziale + clamp
            // (mai salti del target in un solo frame):
            //  - salita di uno scalino (<= 0,35 m): LENTA e smorzata, sembra
            //    un passo sul marciapiede, non un "teletrasporto" di 35 cm;
            //  - discesa: MAGNETICA, piu' rapida del climb ma limitata, cosi'
            //    il player resta incollato al cordolo invece di fare un salto;
            //  - dislivelli veri (> 0,35 m o risalita DEM): follower storico.
            float rate, maxV;
            if (err > 0f && err <= StepAssistHeight)
            {
                rate = StepAssistRate;
                maxV = MaxStepSpeed;
            }
            else if (err < 0f)
            {
                rate = MagnetRate;
                maxV = MaxDescendSpeed;
            }
            else
            {
                rate = SmoothRate;
                maxV = MaxClimbSpeed;
            }
            float alpha = (rate * dt) / (1f + rate * dt);
            float delta = Mathf.Clamp(err * alpha, -maxV * dt, maxV * dt);
            st.vel = delta / dt;
            return feetY + delta;
        }
    }
}