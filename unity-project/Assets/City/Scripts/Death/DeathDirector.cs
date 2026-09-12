using System.Collections;
using UnityEngine;
using City.Player;
using City.NPC;
using City.Afterlife;
using City.OSM;

namespace City.Death
{
    /// <summary>
    /// Orchestratore della morte scenica:
    ///   1) mostra il menu di scelta della modalita' di morte (via UIManager);
    ///   2) esegue la rappresentazione (auto che investe, raffica, pestaggio,
    ///      caduta dal palazzo) con animazione procedurale e sangue;
    ///   3) a fine sequenza mostra la schermata di scelta: reincarnati ORA
    ///      oppure inizia il viaggio nell'Afterlife (Inferno/Purgatorio/Paradiso).
    /// Il player resta SEMPRE in terza persona: nessuna vista in prima persona.
    /// </summary>
    public class DeathDirector : MonoBehaviour
    {
        public static DeathDirector Instance { get; private set; }

        public bool IsDying { get; private set; }

        private const int BuildingLayer = 8;
        // punto di impatto della morte corrente (usato dalle coroutine per
        // evitare parametri ref, vietati negli iteratori C#)
        private Vector3 _groundPos;

        /// <summary>Assicura che il direttore di morte esista in scena.</summary>
        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("DeathDirector");
            go.AddComponent<DeathDirector>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            SceneHook.Ensure();
        }

        private void OnDestroy() { if (Instance == this) Instance = null; }

        // ── Flusso pubblico ─────────────────────────────────────────

        /// <summary>Avvia il flusso di morte dal pulsante MORI: apre il menu
        /// di scelta della modalita'.</summary>
        public void Begin()
        {
            if (IsDying) return;
            if (FamilyManager.IsDead) return;
            var ui = UI.UIManager.Instance;
            if (ui == null) return;
            IsDying = true;
            ui.ShowDeathMenu();
        }

        /// <summary>Il giocatore ha scelto una modalita' di morte dal menu.</summary>
        public void ChooseDeath(DeathMode mode)
        {
            var ui = UI.UIManager.Instance;
            if (ui != null) ui.CloseDeathMenu();
            StartCoroutine(RunDeath(mode));
        }

        /// <summary>Annulla la scelta (il menu di morte viene chiuso).</summary>
        public void Cancel()
        {
            IsDying = false;
            var ui = UI.UIManager.Instance;
            if (ui != null) ui.CloseDeathMenu();
        }

        /// <summary>Reincarnazione immediata: nuova vita, si torna in citta'.</summary>
        public void ReincarnateNow()
        {
            if (!FamilyManager.IsDead) return;
            var player = PlayerController.Instance;
            RestorePlayerPose();
            if (player != null) player.SetInputLocked(false);
            FamilyManager.ResetPlayerState();
            var rsm = RealmSceneManager.Instance;
            if (rsm != null && rsm.ActiveRealm != null)
                rsm.ReturnToCity();
            bool isMale = !FamilyManager.IsFemale;
            string newName = isMale ? "Marco" : "Giulia";
            var ui = UI.UIManager.Instance;
            if (ui != null)
            {
                ui.ClosePostDeathChoice();
                ui.ShowToast("Nasci nuovamente come " + newName + ". Nuova vita!");
            }
        }

        /// <summary>Inizia il ciclo Afterlife (Inferno -> Purgatorio -> Paradiso).</summary>
        public void GoToAfterlife()
        {
            if (!FamilyManager.IsDead) return;
            var player = PlayerController.Instance;
            RestorePlayerPose();
            if (player != null) player.SetInputLocked(false);
            var ui = UI.UIManager.Instance;
            if (ui != null) ui.ClosePostDeathChoice();
            FamilyManager.StartAfterlife(FamilyManager.lastDeathType);
        }

        /// <summary>Ripristina la postura in piedi del player dopo la morte
        /// scenica (eri disteso a terra con il CharacterController disabilitato).</summary>
        private void RestorePlayerPose()
        {
            var player = PlayerController.Instance;
            if (player == null) return;
            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = true;
            player.transform.rotation = Quaternion.identity;
            RaycastHit hit;
            float y = 0f;
            if (Physics.Raycast(player.transform.position + Vector3.up * 2f,
                    Vector3.down, out hit, 10f))
                y = hit.point.y;
            var pos = player.transform.position;
            player.transform.position = new Vector3(pos.x, y + 0.05f, pos.z);
            player.Stop();
        }

        // ── Sequenza di morte ───────────────────────────────────────

        private IEnumerator RunDeath(DeathMode mode)
        {
            var player = PlayerController.Instance;
            Vector3 groundBase = player != null ? player.transform.position : Vector3.zero;
            if (player != null)
            {
                player.SetInputLocked(true);
                player.Stop();
            }
            // Il punto di impatto NON e' a quota fissa: in montagna il terreno
            // DEM sta a centinaia di metri (es. 1200 m). Suolo = collider/TEM,
            // mai y=0.02 hardcoded (seppellirebbe il corpo 1200 m sotto il paese).
            float groundY = GroundY(groundBase);
            _groundPos = new Vector3(groundBase.x, groundY > 0f ? groundY : 0.02f, groundBase.z);

            switch (mode)
            {
                case DeathMode.INVESTIMENTO:
                    yield return CarDeath(player);
                    break;
                case DeathMode.SPARI:
                    yield return ShotDeath(player);
                    break;
                case DeathMode.PESTAGGIO:
                    yield return BeatDeath(player);
                    break;
                case DeathMode.CADUTA:
                    yield return FallDeath(player);
                    break;
            }

            yield return CollapseAndDie(player, mode, _groundPos);

            yield return new WaitForSecondsRealtime(0.7f);
            IsDying = false;
            var ui = UI.UIManager.Instance;
            if (ui != null) ui.ShowPostDeathChoice();
        }

        private IEnumerator CarDeath(PlayerController player)
        {
            if (player == null) { _groundPos.y = 0.02f; yield break; }
            Vector3 p = player.transform.position;
            float deathGroundY = GroundY(p);
            p.y = deathGroundY > 0f ? deathGroundY : 0.02f;
            _groundPos = p;

            Vector3 fwd = player.transform.forward;
            fwd.y = 0f;
            if (fwd.sqrMagnitude < 0.001f) fwd = Vector3.forward;
            fwd.Normalize();
            // l'auto arriva da dietro il player
            Vector3 dirBack = -fwd;

            GameObject car = SpawnCar(p, dirBack, 45f);
            if (car == null)
            {
                // nessuna auto disponibile: crolla comunque
                yield return new WaitForSecondsRealtime(0.6f);
                yield break;
            }

            float totalDist = 45f;
            float speed = totalDist / 2.2f;
            float travelled = 0f;
            while (car != null && travelled < totalDist - 1.6f)
            {
                float step = speed * Time.deltaTime;
                car.transform.position += dirBack * step;
                travelled += step;
                yield return null;
            }
            if (car != null) Object.Destroy(car, 0.1f);
            yield return new WaitForSecondsRealtime(0.15f);
        }

        private IEnumerator ShotDeath(PlayerController player)
        {
            if (player == null) { _groundPos.y = 0.02f; yield break; }
            Vector3 p = player.transform.position;
            float deathGroundY = GroundY(p);
            p.y = deathGroundY > 0f ? deathGroundY : 0.02f;
            _groundPos = p;

            Vector3 fwd = player.transform.forward;
            fwd.y = 0f;
            if (fwd.sqrMagnitude < 0.001f) fwd = Vector3.forward;
            fwd.Normalize();

            var attacker = SpawnAttacker(p + fwd * 4f, p);
            // mira per un attimo
            yield return new WaitForSecondsRealtime(0.7f);
            // lampo di sparo davanti al player
            MuzzleFlash(p + fwd * 1.2f);
            yield return new WaitForSecondsRealtime(0.1f);
            if (attacker != null) Object.Destroy(attacker, 2f);
        }

        private IEnumerator BeatDeath(PlayerController player)
        {
            if (player == null) { _groundPos.y = 0.02f; yield break; }
            Vector3 p = player.transform.position;
            float deathGroundY = GroundY(p);
            p.y = deathGroundY > 0f ? deathGroundY : 0.02f;
            _groundPos = p;

            var attackers = new System.Collections.Generic.List<GameObject>();
            for (int i = 0; i < 3; i++)
            {
                float ang = i * 120f * Mathf.Deg2Rad;
                Vector3 dir = new Vector3(Mathf.Cos(ang), 0f, Mathf.Sin(ang));
                var a = SpawnAttacker(p + dir * 2.8f, p);
                if (a != null) attackers.Add(a);
            }
            // pestaggio: alterna colpi
            for (int i = 0; i < 3; i++)
            {
                FamilyManager.ShowToast("La banda ti picchia a sangue...");
                yield return new WaitForSecondsRealtime(0.3f);
            }
            foreach (var a in attackers)
                if (a != null) Object.Destroy(a, 2f);
        }

        private IEnumerator FallDeath(PlayerController player)
        {
            if (player == null)
            {
                _groundPos.y = 0.02f;
                yield break;
            }
            // In montagna il suolo sta a quota DEM assoluta (es. 1200 m):
            // l'impatto si misura sul terreno reale, non su y=0 fisso.
            float fallGroundY = GroundY(player.transform.position);
            _groundPos = new Vector3(player.transform.position.x,
                fallGroundY > 0f ? fallGroundY : 0.02f,
                player.transform.position.z);

            Vector3 roof = FindRooftop(player.transform.position);
            if (roof.y < player.transform.position.y + 2f)
            {
                // nessun palazzo nei paraggi: crolla sul posto
                FamilyManager.ShowToast("Nessun palazzo vicino... accade comunque.");
                yield return new WaitForSecondsRealtime(0.5f);
                yield break;
            }

            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = roof;
            player.Stop();
            if (cc != null) cc.enabled = true;

            // cadi: aspetta l'impatto al suolo
            float elapsed = 0f;
            while (elapsed < 6f)
            {
                elapsed += Time.deltaTime;
                // isGrounded basta: la quota del suolo e' quella del terreno
                // (in montagna y~1200, mai <=0.6)
                if (cc != null && cc.isGrounded)
                    break;
                yield return null;
            }
            Vector3 p = player.transform.position;
            float landY = GroundY(p);
            p.y = landY > 0f ? landY : 0.02f;
            _groundPos = p;
        }

        // ── Helpers ─────────────────────────────────────────────────

        private GameObject SpawnCar(Vector3 at, Vector3 dir, float behind)
        {
            try
            {
                var prefab = Resources.Load<GameObject>("Vehicles/sedan");
                if (prefab == null) prefab = Resources.Load<GameObject>("Vehicles/taxi");
                if (prefab == null) return null;
                GameObject car = Object.Instantiate(prefab);
                car.name = "AutoAssassina";
                Vector3 pos = at + dir * behind;
                pos.y = GroundY(pos) + 0.05f;
                car.transform.position = pos;
                car.transform.rotation = Quaternion.LookRotation(dir, Vector3.up);
                var c = car.GetComponent<Collider>();
                if (c != null) c.enabled = false;
                return car;
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Death] SpawnCar: " + e.Message);
                return null;
            }
        }

        private GameObject SpawnAttacker(Vector3 at, Vector3 toward)
        {
            try
            {
                var model = CityCharacterFactory.SpawnPassengerModel(null, false, new System.Random());
                if (model == null || model.go == null) return null;
                GameObject go = model.go;
                go.name = "Attaccante";
                float y = GroundY(at);
                go.transform.position = at + Vector3.up * (y > 0.0f ? 0f : 0f);
                go.transform.position = new Vector3(at.x, y + 0.12f, at.z);
                Vector3 fwd = toward - go.transform.position;
                fwd.y = 0f;
                if (fwd.sqrMagnitude > 0.001f) go.transform.rotation = Quaternion.LookRotation(fwd.normalized, Vector3.up);
                return go;
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[Death] SpawnAttacker: " + e.Message);
                return null;
            }
        }

        private Vector3 FindRooftop(Vector3 center)
        {
            var cols = Physics.OverlapSphere(center, 90f);
            float bestH = -1f;
            Vector3 best = center;
            for (int i = 0; i < cols.Length; i++)
            {
                var col = cols[i];
                if (col == null) continue;
                if (col.gameObject.layer != BuildingLayer) continue;
                var bc = col as BoxCollider;
                if (bc == null) continue;
                // altezza del tetto in coordinate mondo (stub Unity: niente
                // BoxCollider.bounds, lo calcolo da center+size*scale)
                Vector3 worldCenter = bc.transform.TransformPoint(bc.center);
                float worldH = Mathf.Abs(bc.size.y * bc.transform.lossyScale.y);
                float topY = worldCenter.y + worldH * 0.5f;
                if (topY < center.y + 3f) continue;
                if (topY > bestH)
                {
                    bestH = topY;
                    best = new Vector3(center.x, topY + 0.6f, center.z);
                }
            }
            return best;
        }

        private static void MuzzleFlash(Vector3 at)
        {
            var sphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            sphere.name = "LampoSparo";
            sphere.transform.position = at + Vector3.up * 1.1f;
            float s = 0.18f;
            sphere.transform.localScale = Vector3.one * s;
            sphere.GetComponent<Renderer>().sharedMaterial = DeathFx.BloodMat(new Color(1f, 0.85f, 0.3f, 1f));
            Object.Destroy(sphere.GetComponent<Collider>());
            Object.Destroy(sphere, 0.25f);
        }

        private static float GroundY(Vector3 pos)
        {
            RaycastHit hit;
            Vector3 origin = pos + Vector3.up * 20f;
            if (Physics.Raycast(origin, Vector3.down, out hit, 80f))
                return hit.point.y;
            // Nessun collider entro 80 m: in montagna il terreno DEM sta a
            // quota assoluta (es. 1200 m s.l.m.): usiamo l'altimetria se la
            // griglia copre il punto (stessa fonte della mesh), senno' 0.
            float dem = TileElevation.HeightAtWorld(pos);
            return dem > 0f ? dem : 0f;
        }

        private static IEnumerator CollapseAndDie(PlayerController player, DeathMode mode, Vector3 groundPos)
        {
            DeathFx.SpawnBlood(groundPos, player != null ? player.transform.forward : Vector3.forward);
            if (player == null)
            {
                FamilyManager.MarkDead(DeathModeInfo.Outcome(mode));
                yield break;
            }

            yield return LieFlat(player, groundPos);

            FamilyManager.MarkDead(DeathModeInfo.Outcome(mode));
        }

        /// <summary>Fa crollare il corpo disteso a terra (rotazione + lieve
        /// abbassamento), in terza persona.</summary>
        private static IEnumerator LieFlat(PlayerController player, Vector3 groundPos)
        {
            var cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.Stop();

            var anim = player.GetComponentInChildren<Animator>();
            if (anim != null) anim.SetFloat("Speed", 0f);

            Quaternion start = player.transform.rotation;
            Quaternion goal = start * Quaternion.Euler(-90f, 0f, 0f);
            float t = 0f;
            float dur = 0.35f;
            float startY = player.transform.position.y;
            // Il corpo si distende sul SUOLO reale (collider/DEM): in montagna
            // e' a quota assoluta, non a 0.06 fissato (avrebbe sepolto il corpo
            // a marco basso del paese). Piccolo offset per non z-fightare.
            float floorY = groundPos.y + 0.06f;
            while (t < dur)
            {
                t += Time.deltaTime;
                float k = Mathf.Clamp01(t / dur);
                player.transform.rotation = Quaternion.Slerp(start, goal, k);
                float ny = Mathf.Lerp(startY, floorY, k);
                player.transform.position =
                    new Vector3(player.transform.position.x, ny, player.transform.position.z);
                yield return null;
            }
            player.transform.rotation = goal;
            player.transform.position =
                new Vector3(player.transform.position.x, floorY, player.transform.position.z);
        }
    }
}
