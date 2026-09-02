using System.Collections;
using UnityEngine;
using City.OSM;
using City.World;

namespace City.Vehicle
{
    /// <summary>
    /// Il "fischio": il giocatore chiama il taxi libero piu' vicino, sale,
    /// sceglie una destinazione (quella gia' impostata, altrimenti un POI a
    /// caso) e paga la corsa al momento dell'arrivo. Il taxi e' autonomo:
    /// non usa Game.IsDriving, e' il veicolo stesso a guidare.
    /// </summary>
    public class TaxiService : MonoBehaviour
    {
        public static TaxiService Instance { get; private set; }

        public static TaxiService Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("TaxiService");
            DontDestroyOnLoad(go);
            return go.AddComponent<TaxiService>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
        }

        // ── stato ───────────────────────────────────────────────
        private const float WhistleCooldown = 2f;
        private const float MaxCallRange = 350f;
        private const float BoardRange = 5f;
        private const float HireTimeout = 30f;
        private const float FareBase = 6f;
        private const float FarePerHundred = 2f;

        private TrafficCar hired;      // taxi del fischio attuale
        private bool boarded;          // giocatore a bordo del taxi
        private float lastWhistle;
        private float hiredAt;
        private Vector3 destWorld;
        private string destName;
        private float fare;
        private bool promptActive;
        private bool riding;

        public bool HasPrompt
        {
            get
            {
                return hired != null && !boarded
                    && hired.IsWaitingPickup && PlayerNear(hired);
            }
        }

        public bool IsRiding { get { return riding; } }

        /// <summary>Fischio dal menu' Fai Azione: chiama il taxi piu' vicino.</summary>
        public void Whistle(Game game)
        {
            if (game == null || game.player == null) return;
            if (Time.time - lastWhistle < WhistleCooldown) return;
            lastWhistle = Time.time;

            if (hired != null)
            {
                Toast("Il taxi che hai chiamato sta arrivando...");
                return;
            }
            if (riding)
            {
                Toast("Sei gia' in taxi!");
                return;
            }

            TrafficCar best = null;
            float bestD = MaxCallRange;
            Vector3 p = game.player.transform.position;
            for (int i = 0; i < TrafficCar.AllTaxis.Count; i++)
            {
                var t = TrafficCar.AllTaxis[i];
                if (t == null) continue;
                if (t.gameObject == null || !t.gameObject.activeInHierarchy) continue;
                if (t.Hired || t.PlayerOnBoard || t.HasPassenger) continue;
                float d = FlatDist(t.transform.position, p);
                if (d < bestD) { bestD = d; best = t; }
            }

            if (best == null)
            {
                Toast("Nessun taxi libero in zona");
                return;
            }

            hired = best;
            hiredAt = Time.time;
            hired.Hire(p);
            promptActive = false;
            Toast("Taxi in arrivo: " + CompassUI.FormatDist(bestD));
            RefreshPrompt();
        }

        private void Update()
        {
            if (Game.Instance == null) return;
            if (hired == null) return;

            // in transizione di salita: non fare altro
            if (boarding) return;

            // corsa in corso: retarget se il giocatore sposta la destinazione
            if (riding)
            {
                if (NavigationState.Current != null)
                {
                    Vector3 wp = NavigationState.Current.WorldPos;
                    wp.y = 0f;
                    if (Vector3.Distance(wp, destWorld) > 10f)
                    {
                        destWorld = wp;
                        destName = NavigationState.Current.name;
                        hired.ServiceTarget = wp;
                    }
                }
                return;
            }

            // timeout di attesa: il taxi non aspetta in eterno
            if (Time.time - hiredAt > HireTimeout)
            {
                Toast("Il taxi se ne va...");
                hired.ResetHire();
                hired = null;
                promptActive = false;
                RefreshPrompt();
                return;
            }

            bool near = HasPrompt;
            if (near != promptActive)
            {
                promptActive = near;
                RefreshPrompt();
            }
        }

        // ── salita a bordo ───────────────────────────────────────

        /// <summary>Chiamato da Game.OnInteractPressed quando il prompt taxi e' attivo.</summary>
        public bool TryBoard(Game game)
        {
            if (!HasPrompt || boarded) return false;
            if (game == null || game.player == null) return false;

            // destinazione: quella gia' impostata, altrimenti un POI a caso
            Vector3 wp;
            if (NavigationState.Current != null)
            {
                wp = NavigationState.Current.WorldPos;
                destName = NavigationState.Current.name;
            }
            else
            {
                var poi = PickRandomPoi(game.player.transform.position);
                if (poi == null)
                {
                    Toast("Scegli una destinazione dalla mappa, poi risali");
                    return false;
                }
                wp = WorldOrigin.ToWorld(poi.lat, poi.lng);
                destName = poi.name;
            }
            wp.y = 0f;
            destWorld = wp;

            float dist = Vector3.Distance(
                game.player.transform.position, wp);
            fare = FareBase +
                Mathf.CeilToInt(dist / 100f) * FarePerHundred;
            StartCoroutine(BoardRoutine(game, wp));
            return true;
        }

        private bool boarding;
        private IEnumerator BoardRoutine(Game game, Vector3 wp)
        {
            boarding = true;
            promptActive = false;
            RefreshPrompt();
            var fader = game.fader;
            try
            {
                if (fader != null)
                {
                    fader.gameObject.SetActive(true);
                    fader.FadeToBlack(null);
                    yield return new WaitForSeconds(fader.duration);
                }

                var cc = game.player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                game.player.Stop();
                game.player.gameObject.SetActive(false);

                game.player.transform.SetParent(hired.transform, false);
                game.player.transform.localPosition = new Vector3(0f, 1.3f, 0f);
                game.player.transform.localRotation = Quaternion.identity;

                boarded = true;
                hired.SetPlayerOnBoard(true);
                hired.ServiceTarget = wp;
                riding = true;

                if (fader != null)
                    fader.FadeFromBlack(null);

                if (game.ui != null)
                    game.ui.ShowToast("Taxi verso: " + destName);
            }
            finally
            {
                boarding = false;
            }
        }

        /// <summary>Il tassista e' arrivato a destinazione con il giocatore a bordo.</summary>
        public void NotifyArrived(TrafficCar taxi)
        {
            if (!riding || taxi != hired) return;
            StartCoroutine(ArriveRoutine(taxi));
        }

        private IEnumerator ArriveRoutine(TrafficCar taxi)
        {
            var game = Game.Instance;
            var ui = game != null ? game.ui : null;
            var fader = game != null ? game.fader : null;
            try
            {
                if (fader != null)
                {
                    fader.gameObject.SetActive(true);
                    fader.FadeToBlack(null);
                    yield return new WaitForSeconds(fader.duration);
                }

                if (game != null && game.player != null)
                {
                    var pl = game.player;
                    pl.gameObject.SetActive(true);
                    pl.transform.SetParent(null, true);

                    Vector3 beside = taxi.transform.position;
                    beside.y = 0.12f;
                    pl.transform.position = beside;
                    pl.transform.localScale = Vector3.one;
                    pl.transform.rotation = Quaternion.identity;

                    var cc = pl.GetComponent<CharacterController>();
                    if (cc != null) cc.enabled = true;

                    if (game.rig != null) game.rig.SetDrivingMode(false);
                }

                // tariffa alla consegna
                if (Wallet.TrySpend(Mathf.Max(1, (int)fare)))
                {
                    if (ui != null)
                        ui.ShowToast("Sei arrivato a " + destName
                            + " · Corsa: " + Mathf.Max(1, (int)fare) + " €");
                }
                else if (ui != null)
                {
                    ui.ShowToast("Sei arrivato a " + destName + " (saldo insufficiente)");
                }
                NavigationState.Clear();

                riding = false;
                boarded = false;
                if (hired != null) hired.ResetHire();
                hired = null;
                promptActive = false;
                RefreshPrompt();

                if (fader != null) fader.FadeFromBlack(null);
            }
            finally { }
        }

        // ── helper ──────────────────────────────────────────────

        private static float FlatDist(Vector3 a, Vector3 b)
        {
            a.y = 0f; b.y = 0f;
            return Vector3.Distance(a, b);
        }

        private bool PlayerNear(TrafficCar taxi)
        {
            if (Game.Instance == null || Game.Instance.player == null) return false;
            return FlatDist(taxi.transform.position,
                Game.Instance.player.transform.position) < BoardRange;
        }

        private static VehiclePoiRegistry.PoiInfo PickRandomPoi(Vector3 nearWorld)
        {
            var list = new System.Collections.Generic.List<VehiclePoiRegistry.PoiInfo>();
            foreach (var poi in VehiclePoiRegistry.All())
            {
                if (poi == null || string.IsNullOrEmpty(poi.kind)) continue;
                if (poi.kind == "rampa") continue; // non e' una meta sensata
                list.Add(poi);
            }
            if (list.Count == 0) return null;
            // preferenza: POI ragionevolmente vicino (entro ~1.2 km)
            for (int i = 0; i < 5; i++)
            {
                var cand = list[UnityEngine.Random.Range(0, list.Count)];
                var wp = WorldOrigin.ToWorld(cand.lat, cand.lng);
                if (FlatDist(wp, nearWorld) < 1200f) return cand;
            }
            return list[UnityEngine.Random.Range(0, list.Count)];
        }

        private void RefreshPrompt()
        {
            if (Game.Instance == null) return;
            if (promptActive)
            {
                if (Game.Instance.ui != null)
                    Game.Instance.ui.ShowInteract("TAXI: ENTRA");
            }
            else
            {
                Game.Instance.RefreshInteractLabelNow();
            }
        }

        private static void Toast(string msg)
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(msg);
        }
    }
}
