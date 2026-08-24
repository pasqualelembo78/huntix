using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Networking;

namespace City.Vehicle
{
    /// <summary>
    /// Client HTTP verso gli endpoint veicoli del backend (/api/vehicles).
    /// Il giocatore e' identificato da un GUID locale (PlayerPrefs): nessun
    /// login, ma il server distingue i compratori.
    ///
    /// Oltre alla proprieta' gestisce:
    ///   • stato completo dei propri veicoli (condizione, antifurti, garage)
    ///   • polling periodico del motore furti lato server: se un'auto viene
    ///     rubata fa partire la "telefonata" (RansomCallUI - cavallo di ritorno)
    ///   • garage (affitto giornaliero / acquisto), officina (riparazione +
    ///     antifurti), odometro mentre si guida (drive-ping)
    /// </summary>
    public class VehicleOwnershipApi : MonoBehaviour
    {
        private const string BaseUrl = City.OSM.TileClient.BaseUrl;
        private const string PlayerIdKey = "huntix_player_id";
        private const float PollIntervalSec = 45f;

        // ── modelli risposta ────────────────────────────────────────
        [Serializable]
        private class OwnedVehicle
        {
            public string code;
            public string owner;
            public string model;
            public float lat;
            public float lon;
            public float heading;
            public int price;
            public float condition;
            public long odometer_m;
            public string[] anti_theft;
            public bool in_garage;
            public bool stolen;
            public int ransom;
            public double ransom_deadline;
            public bool found_abandoned;
            public double abandoned_at_lat;
            public double abandoned_at_lon;
            public bool lost_forever;
        }

        [Serializable]
        private class StateResp { public bool ok; public OwnedVehicle[] owned; }

        [Serializable]
        private class BuyReq { public string code; public string player; }

        [Serializable]
        private class ParkReq
        {
            public string code; public string player;
            public double lat; public double lon; public double heading;
            public long odometer_m;
        }

        [Serializable]
        private class GarageIdReq { public string garage_id; }
        [Serializable]
        private class RentGarageReq { public string player; public string garage_id; }
        [Serializable]
        private class BuyGarageReq
        {
            public string player; public string garage_id;
            public double lat; public double lon;
        }
        [Serializable]
        private class GarageParkReq
        {
            public string code; public string player;
            public string garage_id; public double lat; public double lon;
        }
        [Serializable]
        private class DeviceReq { public string code; public string player; public string device; }
        [Serializable]
        private class PingReq
        {
            public string code; public string player;
            public long odometer_m; public float condition;
        }
        [Serializable]
        private class RansomReq
        {
            public string code; public string player; public bool accept;
            public double officina_lat; public double officina_lon;
        }
        [Serializable]
        private class GarageStatusOwned { public string garage_id; }
        [Serializable]
        private class GarageStatusRental { public string garage_id; public bool valid; }
        [Serializable]
        private class GarageStatusResp
        {
            public bool ok;
            public GarageStatusOwned owned;
            public GarageStatusRental rental;
        }
        [Serializable]
        private class OkResp { public bool ok; public string error; }

        // ── modello pubblico per le UI ──────────────────────────────
        public class MyVehicle
        {
            public string code;
            public string model;
            public int price;
            public float condition = 100f;
            public long odometer_m;
            public string[] anti_theft;
            public bool in_garage;
            public bool stolen;
            public int ransom;
            public double ransom_deadline;
            public bool found_abandoned;
        }

        public class GarageInfo
        {
            public string garage_id;
            public bool valid;
        }
        public class GarageStatus
        {
            public GarageInfo owned;
            public GarageInfo rental;
        }

        public static VehicleOwnershipApi Instance { get; private set; }

        private readonly System.Collections.Generic.HashSet<string> owned =
            new System.Collections.Generic.HashSet<string>();

        /// <summary>
        /// Tutti i veicoli venduti (di chiunque) con posizione di parcheggio:
        /// fonte per farli rinascere nel chunk giusto al posto dello slot
        /// deterministico d'origine.
        /// </summary>
        public class ParkedVehicle
        {
            public string owner;
            public double lat;
            public double lon;
            public double heading;
            public string model = "";
            public int price;
            public bool inGarage;
            public bool stolen;
            public bool foundAbandoned;
            public double abandonedLat;
            public double abandonedLon;
            public float condition = 100f;
            public long odometer_m;
            public string[] anti_theft;
        }

        private static readonly System.Collections.Generic.Dictionary<string, ParkedVehicle>
            sold = new System.Collections.Generic.Dictionary<string, ParkedVehicle>();

        /// <summary>Ultimo stato noto dei MIEI veicoli (dopo ogni Refresh).</summary>
        private static readonly System.Collections.Generic.Dictionary<string, MyVehicle>
            myState = new System.Collections.Generic.Dictionary<string, MyVehicle>();

        private float nextPoll;
        private readonly System.Collections.Generic.HashSet<string> lostNotified =
            new System.Collections.Generic.HashSet<string>();

        public static string PlayerId
        {
            get
            {
                string id = PlayerPrefs.GetString(PlayerIdKey, "");
                if (string.IsNullOrEmpty(id))
                {
                    id = Guid.NewGuid().ToString("N");
                    PlayerPrefs.SetString(PlayerIdKey, id);
                    PlayerPrefs.Save();
                }
                return id;
            }
        }

        public static VehicleOwnershipApi Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("VehicleOwnershipApi");
            DontDestroyOnLoad(go);
            return go.AddComponent<VehicleOwnershipApi>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Update()
        {
            if (Time.unscaledTime < nextPoll) return;
            nextPoll = Time.unscaledTime + PollIntervalSec;
            Refresh(null);
        }

        // ── possesso ────────────────────────────────────────────────

        public bool IsOwned(string code)
        {
            return !string.IsNullOrEmpty(code) && owned.Contains(code);
        }

        public void MarkOwned(string code, double lat, double lon, double heading)
        {
            if (string.IsNullOrEmpty(code)) return;
            owned.Add(code);
            sold[code] = new ParkedVehicle
            {
                owner = PlayerId,
                lat = lat,
                lon = lon,
                heading = heading,
            };
        }

        public void MarkSold(string code)
        {
            if (string.IsNullOrEmpty(code)) return;
            owned.Remove(code);
            sold.Remove(code);
            myState.Remove(code);
        }

        public static bool IsSold(string code)
        {
            return !string.IsNullOrEmpty(code) && sold.ContainsKey(code);
        }

        public static System.Collections.Generic.List<
            System.Collections.Generic.KeyValuePair<string, ParkedVehicle>> SoldSnapshot()
        {
            return new System.Collections.Generic.List<
                System.Collections.Generic.KeyValuePair<string, ParkedVehicle>>(sold);
        }

        public static bool IsOwnedSafe(string code)
        {
            return Instance != null && Instance.IsOwned(code);
        }

        public ParkedVehicle GetParkedInfo(string code)
        {
            ParkedVehicle pv;
            sold.TryGetValue(code, out pv);
            return pv;
        }

        public void UpdateParkedPosition(string code, double lat, double lon,
            double heading)
        {
            if (sold.TryGetValue(code, out var pv))
            {
                pv.lat = lat; pv.lon = lon; pv.heading = heading;
            }
        }

        public void SetInGarageLocal(string code, bool value)
        {
            if (sold.TryGetValue(code, out var pv)) pv.inGarage = value;
        }

        /// <summary>Registra modello/prezzo locale per auto comprate nuove
        /// (il server conosce gia' entrambi, serve la copia client).</summary>
        public void SetLocalState(string code, string model, int price)
        {
            if (!sold.TryGetValue(code, out var pv))
                pv = sold[code] = new ParkedVehicle { owner = PlayerId };
            pv.model = model;
            pv.price = price;
        }

        /// <summary>Applica condizione/odometro noti a un GameObject veicolo
        /// appena spawnato (consegna concessionaria, uscita dal garage).</summary>
        public void ApplyOwnedState(GameObject vehicleGo, string code)
        {
            if (vehicleGo == null) return;
            var vc = vehicleGo.GetComponent<VehicleController>();
            if (vc == null) vc = vehicleGo.GetComponentInChildren<VehicleController>();
            if (vc == null) return;
            if (myState.TryGetValue(code, out var mv))
                vc.SetServiceState(mv.condition, mv.odometer_m);
            else
                vc.SetServiceState(100f, 0L);
        }

        /// <summary>All'ingresso in guida: se conosciamo lo stato server del
        /// veicolo lo usa come base per usura/condizione locale.</summary>
        public void SyncBaseState(string code, VehicleController vc)
        {
            if (vc == null || string.IsNullOrEmpty(code)) return;
            if (myState.TryGetValue(code, out var mv))
                vc.SetServiceState(mv.condition,
                    System.Math.Max(mv.odometer_m, VehicleController.StoredOdometer(code)));
        }

        // ── refresh stato + motore eventi furto ─────────────────────

        public void Refresh(Action onDone = null)
        {
            StartCoroutine(RefreshCo(onDone));
        }

        private IEnumerator RefreshCo(Action onDone)
        {
            using (var req = UnityWebRequest.Get(BaseUrl + "/api/vehicles/state"))
            {
                yield return req.SendWebRequest();
                if (req.result == UnityWebRequest.Result.Success)
                {
                    try
                    {
                        var resp = JsonUtility.FromJson<StateResp>(
                            req.downloadHandler.text);
                        if (resp?.owned != null)
                        {
                            ProcessState(resp.owned);
                        }
                    }
                    catch (Exception e)
                    {
                        City.OSM.OsmDiag.Log("[VehApi] parse state err: " + e.Message);
                    }
                }
            }
            onDone?.Invoke();
        }

        private void ProcessState(OwnedVehicle[] list)
        {
            string me = PlayerId;
            foreach (var v in list)
            {
                if (string.IsNullOrEmpty(v.code)) continue;

                // registro completo venduti (rinascita allo slot giusto);
                // le rubate NON ricompaiono per strada, quelle abbandonate
                // ricompaiono dove sono state lasciate dal ladro
                if (!sold.TryGetValue(v.code, out var pv))
                    pv = sold[v.code] = new ParkedVehicle();
                pv.owner = v.owner;
                pv.lat = v.found_abandoned ? v.abandoned_at_lat : v.lat;
                pv.lon = v.found_abandoned ? v.abandoned_at_lon : v.lon;
                pv.heading = v.heading;
                pv.model = v.model;
                pv.price = v.price;
                pv.inGarage = v.in_garage;
                pv.stolen = v.stolen && !v.found_abandoned;
                pv.foundAbandoned = v.found_abandoned;
                pv.abandonedLat = v.abandoned_at_lat;
                pv.abandonedLon = v.abandoned_at_lon;
                pv.condition = v.condition;
                pv.odometer_m = v.odometer_m;
                pv.anti_theft = v.anti_theft;

                if (v.lost_forever)
                {
                    // persa per sempre: esce dal registro locale (toast una
                    // volta sola, non a ogni poll)
                    if (lostNotified.Add(v.code))
                        Toast("\uD83D\uDEA8 La tua " + v.model +
                              " \u00e8 scomparsa nel nulla. Non torner\u00e0.");
                    MarkSold(v.code);
                    continue;
                }

                if (v.owner != me) continue;

                owned.Add(v.code);

                // ── eventi per le MIE auto ──
                bool wasStolen = myState.TryGetValue(v.code, out var prev) &&
                                 prev.stolen;
                bool wasFound = prev != null && prev.found_abandoned;
                myState[v.code] = new MyVehicle
                {
                    code = v.code,
                    model = v.model,
                    price = v.price,
                    condition = v.condition,
                    odometer_m = v.odometer_m,
                    anti_theft = v.anti_theft,
                    in_garage = v.in_garage,
                    stolen = v.stolen,
                    ransom = v.ransom,
                    ransom_deadline = v.ransom_deadline,
                    found_abandoned = v.found_abandoned,
                };

                if (v.found_abandoned && !wasFound)
                {
                    // ritrovata dopo il rifiuto del riscatto
                    RansomCallUI.ShowRecovered(v.model);
                }
                else if (v.stolen && !wasStolen && !v.found_abandoned)
                {
                    // nuovo furto: telefonata del ladro
                    RansomCallUI.ShowTheft(v.code, v.model, v.ransom,
                        v.ransom_deadline);
                }
            }
        }

        public void GetMyVehicle(Action<MyVehicle> done)
        {
            // usa la cache fresca se c'e', altrimenti scarica subito
            MyVehicle cached = null;
            foreach (var kv in myState)
                if (cached == null || kv.Value.condition < cached.condition)
                    cached = kv.Value;
            if (cached != null) { done?.Invoke(cached); return; }
            StartCoroutine(GetMyVehicleCo(done));
        }

        private IEnumerator GetMyVehicleCo(Action<MyVehicle> done)
        {
            yield return RefreshCo(null);
            MyVehicle best = null;
            foreach (var kv in myState)
                if (best == null || kv.Value.condition < best.condition)
                    best = kv.Value;
            done?.Invoke(best);
        }

        /// <summary>Recupero fisico di un'auto ritrovata abbandonata:
        /// chiamare quando il player la rientra (cancella il flag server).</summary>
        public void RecoverAbandoned(string code, Action<bool> done)
        {
            StartCoroutine(PostOk("/abandoned/recover", new BuyReq
            {
                code = code, player = PlayerId,
            }, done));
        }

        /// <summary>All'ingresso nel veicolo: se era "ritrovata abbandonata"
        /// chiude la pratica sul server (torna pienamente tua).</summary>
        public void ClearAbandonedIfFound(string code)
        {
            if (string.IsNullOrEmpty(code)) return;
            if (!myState.TryGetValue(code, out var mv) || !mv.found_abandoned)
                return;
            RecoverAbandoned(code, ok =>
            {
                if (ok)
                {
                    if (myState.TryGetValue(code, out var cur))
                        cur.found_abandoned = false;
                    Toast("Auto recuperata: di nuovo tua!");
                }
            });
        }

        // ── acquisto/vendita/parcheggio (esistenti) ─────────────────

        public void Buy(string code, Action<bool, string> done)
        {
            StartCoroutine(PostCo("/buy", new BuyReq { code = code, player = PlayerId }, done));
        }

        public void Sell(string code, Action<bool, string> done)
        {
            StartCoroutine(PostCo("/sell", new BuyReq { code = code, player = PlayerId }, done));
        }

        public void Park(string code, double lat, double lon, double heading,
            Action<bool, string> done)
        {
            var body = new ParkReq
            {
                code = code,
                player = PlayerId,
                lat = lat,
                lon = lon,
                heading = heading,
                odometer_m = VehicleController.StoredOdometer(code),
            };
            StartCoroutine(PostCo("/park", body, done));
        }

        // ── officina ────────────────────────────────────────────────

        public void Repair(string code, Action<bool> done)
        {
            StartCoroutine(PostOk("/service/repair", new DeviceReq
            {
                code = code, player = PlayerId
            }, done));
        }

        public void InstallAntitheft(string code, string device, Action<bool> done)
        {
            StartCoroutine(PostOk("/service/antitheft", new DeviceReq
            {
                code = code, player = PlayerId, device = device
            }, done));
        }

        // ── telemetria di guida ─────────────────────────────────────

        public void DrivePing(string code, long odometerM, float condition)
        {
            StartCoroutine(PostOk("/drive-ping", new PingReq
            {
                code = code, player = PlayerId,
                odometer_m = odometerM, condition = condition,
            }, null));
        }

        // ── garage ──────────────────────────────────────────────────

        public void GetGarageStatus(Action<GarageStatus> done)
        {
            StartCoroutine(UnityWebRequestGet(
                BaseUrl + "/api/vehicles/garage/status?player=" +
                UnityWebRequest.EscapeURL(PlayerId), text =>
            {
                try
                {
                    var resp = JsonUtility.FromJson<GarageStatusResp>(text);
                    var gs = new GarageStatus();
                    if (resp?.owned != null)
                        gs.owned = new GarageInfo
                            { garage_id = resp.owned.garage_id, valid = true };
                    if (resp?.rental != null)
                        gs.rental = new GarageInfo
                            { garage_id = resp.rental.garage_id, valid = resp.rental.valid };
                    done?.Invoke(gs);
                }
                catch (Exception)
                {
                    done?.Invoke(null);
                }
            }));
        }

        public void RentGarage(string garageId, Action<bool> done)
        {
            StartCoroutine(PostOk("/garage/rent", new RentGarageReq
            {
                player = PlayerId,
                garage_id = garageId,
            }, done));
        }

        public void BuyGarage(string garageId, double lat, double lon,
            Action<bool> done)
        {
            StartCoroutine(PostOk("/garage/buy", new BuyGarageReq
            {
                player = PlayerId,
                garage_id = garageId,
                lat = lat,
                lon = lon,
            }, done));
        }

        public void GaragePark(string code, double lat, double lon, Action<bool> done)
        {
            StartCoroutine(PostOk("/garage/park", new GarageParkReq
            {
                code = code, player = PlayerId,
                garage_id = CurrentGarageId ?? "",
                lat = lat, lon = lon,
            }, done));
        }

        public void GarageExit(Action<bool> done)
        {
            StartCoroutine(PostOk("/garage/exit", new BuyReq
            {
                player = PlayerId,
            }, done));
        }

        // ── cavallo di ritorno ──────────────────────────────────────

        public void RansomRespond(string code, bool accept, double offLat,
            double offLon, Action<bool, string> done)
        {
            StartCoroutine(RansomCo(code, accept, offLat, offLon, done));
        }

        public void RefuseRansom(string code)
        {
            StartCoroutine(RansomCo(code, false, 0, 0, null));
        }

        private IEnumerator RansomCo(string code, bool accept, double offLat,
            double offLon, Action<bool, string> done)
        {
            yield return PostCo("/ransom/respond", new RansomReq
            {
                code = code, player = PlayerId, accept = accept,
                officina_lat = offLat, officina_lon = offLon,
            }, (ok, err) => done?.Invoke(ok, err));

            if (accept) Refresh(null);   // aggiorna subito posizione/condizione
        }

        /// <summary>Officina nota piu' vicina all'ultima posizione dell'auto.
        /// Fallback: coordinate (0,0) se non ne abbiamo incontrate ancora.</summary>
        public void NearestRepairOf(string code, Action<double, double, string> done)
        {
            double lat = 0, lng = 0;
            if (sold.TryGetValue(code, out var pv)) { lat = pv.lat; lng = pv.lon; }
            var poi = VehiclePoiRegistry.NearestRepair(lat, lng);
            done?.Invoke(poi?.lat ?? lat, poi?.lng ?? lng, poi?.name ?? "officina");
        }

        // ── plumbing HTTP ───────────────────────────────────────────

        private string currentGarageIdField;
        private string CurrentGarageId
        {
            get
            {
                // il garage in cui siamo ora: lo scrive GarageUI quando apre
                return currentGarageIdField;
            }
        }

        /// <summary>Chiamato da GarageUI prima di GaragePark.</summary>
        public void SetCurrentGarageId(string id)
        {
            currentGarageIdField = id;
        }

        private IEnumerator UnityWebRequestGet(string url, Action<string> done)
        {
            using (var req = UnityWebRequest.Get(url))
            {
                req.timeout = 8;
                yield return req.SendWebRequest();
                done?.Invoke(req.result == UnityWebRequest.Result.Success
                    ? req.downloadHandler.text : null);
            }
        }

        private IEnumerator PostOk(string path, object body, Action<bool> done)
        {
            yield return PostCo(path, body, (ok, err) => done?.Invoke(ok));
        }

        private IEnumerator PostCo(string path, object body, Action<bool, string> done)
        {
            string json = JsonUtility.ToJson(body);
            using (var req = new UnityWebRequest(BaseUrl + "/api/vehicles" + path, "POST"))
            {
                byte[] data = System.Text.Encoding.UTF8.GetBytes(json);
                req.uploadHandler = new UploadHandlerRaw(data);
                req.downloadHandler = new DownloadHandlerBuffer();
                req.SetRequestHeader("Content-Type", "application/json");
                req.timeout = 8;

                yield return req.SendWebRequest();

                bool ok = req.result == UnityWebRequest.Result.Success;
                string err = ok ? null : req.error;
                if (ok)
                {
                    try
                    {
                        var resp = JsonUtility.FromJson<OkResp>(req.downloadHandler.text);
                        if (resp != null && !resp.ok)
                        {
                            ok = false;
                            err = resp.error ?? "errore";
                        }
                    }
                    catch (Exception e) { ok = false; err = e.Message; }
                }
                done?.Invoke(ok, err);
            }
        }

        private static void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }
    }
}
