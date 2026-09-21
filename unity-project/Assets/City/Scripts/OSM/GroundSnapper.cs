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
    /// 1. PROBE CORTO (3 raggi a grappolo) dai piedi verso il basso: trova il
    ///    piano su cui il personaggio CAMMINA DAVVERO (strade, marciapiedi,
    ///    deck dei viadotti, rampe, fallback B3). Il grappolo (raggio centrale
    ///    + laterali) non scivola nello spiraglio fra due MeshCollider
    ///    adiacenti (giunti di chunk).
    /// 2. PROBE LUNGO (RaycastAll dall'alto, ancorato al DEM): solo se il
    ///    corto fallisce (terreno lontano sotto, es. dislivello/valle). Le
    ///    superfici SOPRA il livello piedi vengono escluse (ponte che passa
    ///    sopra la testa, tetti): si aggancia il fondo di valle, mai il cielo.
    /// 3. FALLBACK DEM (TileElevation): se nessun collider esiste ancora
    ///    (tile in generazione, offline), l'altimetria tiene comunque i piedi
    ///    a quota.
    /// 4. FOLLOW VERTICALE smussato e a velocita' limitata (salita piu' dolce
    ///    della discesa, discesa clamperizzata): niente teleport, niente
    ///    "saltelli" in montagna, niente affondo/volo se il terreno arriva
    ///    o viene ricostruito sotto di noi.
    ///
    /// La mask esclude gli EDIFICI (layer 8): non si cammina sui tetti e, se
    /// il personaggio finisse per inerzia su un tetto, il sistema NON lo
    /// trascina giu' nel vuoto (la guardia "superficie non vista" sotto).
    /// </summary>
    public static class GroundSnapper
    {
        public const int BuildingLayer = 8;
        public const int WalkableMask = ~(1 << BuildingLayer);

        // ── Sonda corta ──
        public const float ShortStartAbove = 0.30f; // partenza sopra il livello piedi (caviglia)
        public const float ShortDepth = 2.6f;       // guarda al massimo fino a ~2.6m sotto i piedi
        public const float ShortNorm = 0.1f;        // normale minimo (esclude pareti/soffitti)
        public const float ShortSpread = 0.28f;     // semibraccio del grappolo di raggi: con 3 raggi
                                                    // laterali il suolo NON sfugge nello spiraglio fra
                                                    // due MeshCollider adiacenti (giunti chunk)

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

        private static bool IsSelf(Transform t, Transform self)
        {
            if (t == null || self == null) return false;
            if (t == self) return true;
            return t.IsChildOf(self);
        }

        /// <summary>Sonda del piano di appoggio sotto il livello piedi `pos`
        /// (world). `self` = radice dell'attore (player/NPC): i suoi collider
        /// e lo SpawnBridge di sicurezza vengono esclusi dalla misura.</summary>
        public static GroundSample SampleGround(Vector3 pos, Transform self)
        {
            try { Physics.SyncTransforms(); }
            catch (System.Exception) { }

            float feetY = pos.y;
            Transform bridge = CityChunkedWorld.Instance != null &&
                CityChunkedWorld.Instance.SpawnBridge != null
                ? CityChunkedWorld.Instance.SpawnBridge.transform : null;

            // ── 1. Sonda corta: la superficie su cui camminiamo davvero ──
            Vector3 feet = new Vector3(pos.x, feetY + ShortStartAbove, pos.z);
            Vector3 side = self != null ? self.right * ShortSpread : Vector3.right * ShortSpread;
            RaycastHit[] n3 = Physics.RaycastAll(feet, Vector3.down, ShortDepth,
                WalkableMask, QueryTriggerInteraction.Ignore);
            RaycastHit[] nL = Physics.RaycastAll(feet - side, Vector3.down, ShortDepth,
                WalkableMask, QueryTriggerInteraction.Ignore);
            RaycastHit[] nR = Physics.RaycastAll(feet + side, Vector3.down, ShortDepth,
                WalkableMask, QueryTriggerInteraction.Ignore);
            RaycastHit near;
            if (PickBest(n3, self, bridge, out near) ||
                PickBest(nL, self, bridge, out near) ||
                PickBest(nR, self, bridge, out near))
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

        /// <summary>Sceglie l'hit del grappolo PIU' VICINO ai piedi, scartando
        /// i collider dell'attore (il suo CharacterController/capsula) e dello
        /// SpawnBridge di sicurezza. Il grappolo a 3 raggi serve a non perdere
        /// il suolo nello spiraglio fra due MeshCollider adiacenti (giunti di
        /// chunk): se il raggio centrale passa di lato, i laterali lo trovano.</summary>
        private static bool PickBest(RaycastHit[] hits, Transform self,
            Transform bridge, out RaycastHit best)
        {
            best = default(RaycastHit);
            int idx = -1;
            for (int i = 0; i < hits.Length; i++)
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
            float alpha = (SmoothRate * dt) / (1f + SmoothRate * dt);
            float maxV = err > 0f ? MaxClimbSpeed : MaxDescendSpeed;
            float delta = Mathf.Clamp(err * alpha, -maxV * dt, maxV * dt);
            st.vel = delta / dt;
            return feetY + delta;
        }
    }
}