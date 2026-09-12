using System;
using UnityEngine;

namespace City.Environment
{
    /// <summary>
    /// Casa acquistabile e garage personale (livello 4). La casa e legata a
    /// una porta (ingresso residenziale): comprata una volta, da quel punto
    /// si puo vendere, parcheggiare l auto nel garage (la si ritira dalla
    /// stessa porta) e usarla come punto di rientro.
    /// </summary>
    public static class HomeSystem
    {
        private const string KeyName = "city_home_name";
        public const string KeyLat = "city_home_lat";
        public const string KeyLng = "city_home_lng";
        private const string KeyGarage = "city_home_garage";
        private const string KeyGarageModel = "city_home_garage_model";

        public const int HomeCost = 900;
        public const float SellRefund = 0.6f;

        /// <summary>Soglia di vicinanza (gradi) per considerare la porta
        /// quella di casa propria sulla mappa.</summary>
        private const float SameDoorDistance = 0.0006f;

        // riferimento all'auto parcheggiata (GO eventualmente deattivato)
        private static Vehicle.VehicleController _parkedVcRef;

        public static bool OwnsHome
        {
            get { return PlayerPrefs.HasKey(KeyName) && PlayerPrefs.GetFloat(KeyLat, 0f) != 0f; }
        }

        public static string HomeName
        {
            get { return PlayerPrefs.GetString(KeyName, ""); }
        }

        public static bool HasCarParked
        {
            get { return OwnsHome && PlayerPrefs.GetString(KeyGarage, "") != ""; }
        }

        /// <summary>Distanza tra due punti in gradi (su questa scala la
        /// conversione in metri e approssimabile con 111111 m/grado).</summary>
        private static float GeoDistLngLat(float lat1, float lng1, float lat2, float lng2)
        {
            return Mathf.Sqrt(Mathf.Pow((lat2 - lat1) * 111111f, 2f) +
                              Mathf.Pow((lng2 - lng1) * 111111f, 2f));
        }

        /// <summary>True se la porta indicata e la porta di casa propria.</summary>
        public static bool IsThisHome(Vector3 doorPos)
        {
            if (!OwnsHome) return false;
            var g = OSM.WorldOrigin.ToGeo(doorPos);
            return GeoDistLngLat((float)g.lat, (float)g.lng,
                PlayerPrefs.GetFloat(KeyLat, 0f), PlayerPrefs.GetFloat(KeyLng, 0f)) < 60f;
        }

        /// <summary>Compra la casa alla porta indicata (900 euro).</summary>
        public static bool Buy(string name, Vector3 doorPos)
        {
            if (OwnsHome)
            {
                Toast("Possiedi gia una casa. Vendi prima di comprarne un altra.");
                return false;
            }
            if (!World.Wallet.TrySpend(HomeCost))
            {
                Toast("Non hai abbastanza soldi per la casa (" + HomeCost + " euro).");
                return false;
            }
            var g = OSM.WorldOrigin.ToGeo(doorPos);
            string nm = string.IsNullOrEmpty(name) ? "Casa" : name;
            PlayerPrefs.SetString(KeyName, nm);
            PlayerPrefs.SetFloat(KeyLat, (float)g.lat);
            PlayerPrefs.SetFloat(KeyLng, (float)g.lng);
            PlayerPrefs.Save();
            Toast("Comprata la casa: " + nm + ". Da qui ora puoi parcheggiare l auto.");
            return true;
        }

        /// <summary>Vende la casa restituendo 540 euro (60% del prezzo).</summary>
        public static bool Sell(Vector3 doorPos)
        {
            if (!OwnsHome) return false;
            if (HasCarParked && !Retrieve(doorPos))
            {
                Toast("Ritira prima l auto dal garage per poter vendere la casa.");
                return false;
            }
            int refund = Mathf.RoundToInt(HomeCost * SellRefund);
            World.Wallet.Earn(refund);
            PlayerPrefs.DeleteKey(KeyName);
            PlayerPrefs.DeleteKey(KeyLat);
            PlayerPrefs.DeleteKey(KeyLng);
            PlayerPrefs.DeleteKey(KeyGarage);
            PlayerPrefs.DeleteKey(KeyGarageModel);
            PlayerPrefs.Save();
            Toast("Casa venduta: +" + refund + " euro.");
            return true;
        }

        /// <summary>
        /// Parcheggia l auto posseduta vicino nell auto-cabina di casa propria.
        /// Deve essere la propria auto (accettata o acquistata). Se si
        /// parcheggia il taxi ridipinto col proprio colore, si sblocca lo
        /// sconto al 50 percento per la casa.
        /// </summary>
        public static bool Park(Vehicle.VehicleController vc, string viCode)
        {
            if (vc == null || string.IsNullOrEmpty(viCode)) return false;
            if (!OwnsHome)
            {
                Toast("Comprati prima una casa (azione dal menu sulla porta).");
                return false;
            }
            if (HasCarParked)
            {
                Toast("Il garage e gia occupato. Ritira prima l auto corrente.");
                return false;
            }
            if (vc.IsJobVehicle)
            {
                Toast("Non puoi parcheggiare l auto del lavoro.");
                return false;
            }

            string code = viCode;
            string model = ResolveModel(vc.gameObject);

            var api = Vehicle.VehicleOwnershipApi.Instance;
            if (api != null)
            {
                api.SetInGarageLocal(code, true);
            }

            // conserva il riferimento per il rientro: il GO non viene
            // distrutto ma solo nascosto, cosi non duplica ne deriva
            _parkedVcRef = vc;
            vc.gameObject.SetActive(false);

            PlayerPrefs.SetString(KeyGarage, code);
            PlayerPrefs.SetString(KeyGarageModel, model);
            PlayerPrefs.Save();
            Toast("Auto parcheggiata nel garage di casa.");
            return true;
        }

        /// <summary>Ritira l auto dal garage di casa, facendola comparire sulla
        /// strada davanti alla porta. Fallback: ricostruisce l auto se la
        /// versione nascosta era stata distrutta dal caricamento del mondo.</summary>
        public static bool Retrieve(Vector3 doorPos)
        {
            if (!HasCarParked) return false;
            string code = PlayerPrefs.GetString(KeyGarage, "");
            string model = PlayerPrefs.GetString(KeyGarageModel, "");
            PlayerPrefs.DeleteKey(KeyGarage);
            PlayerPrefs.DeleteKey(KeyGarageModel);
            PlayerPrefs.Save();

            Vector3 pos = SpawnPointOnRoad(doorPos);
            var api = Vehicle.VehicleOwnershipApi.Instance;

            if (_parkedVcRef != null)
            {
                var go = _parkedVcRef.gameObject;
                _parkedVcRef = null;
                if (go != null)
                {
                    go.transform.position = pos;
                    go.transform.rotation = Quaternion.Euler(0f, UnityEngine.Random.Range(0f, 360f), 0f);
                    go.SetActive(true);
                    Vehicle.VehicleSpawnManager.RegisterActiveOwned(code, go);
                    if (api != null)
                    {
                        api.SetInGarageLocal(code, false);
                        var g = OSM.WorldOrigin.ToGeo(pos);
                        api.MarkOwned(code, g.lat, g.lng, go.transform.eulerAngles.y);
                    }
                    Toast("Auto ritirata dal garage.");
                    return true;
                }
            }

            // l'oggetto parcheggiato e stato distrutto dal mondo:
            // ricostruiscilo identico da capo
            Vehicle.VehicleSpawnManager.VehicleDef def;
            if (string.IsNullOrEmpty(model) ||
                !Vehicle.VehicleSpawnManager.TryGetDef(model, out def))
            {
                Toast("Modello auto in garage non disponibile.");
                return false;
            }
            float angle = UnityEngine.Random.Range(0f, 360f);
            var rebuilt = Vehicle.VehicleSpawnManager.BuildVehicle(null, def, pos, angle, code);
            if (rebuilt == null)
            {
                Toast("Impossibile far uscire l auto dal garage adesso.");
                return false;
            }
            if (api != null)
            {
                api.SetInGarageLocal(code, false);
                var g = OSM.WorldOrigin.ToGeo(pos);
                api.MarkOwned(code, g.lat, g.lng, angle);
                api.ApplyOwnedState(rebuilt, code);
            }
            // Anche la ricostruzione rientra nei "materializzati fuori dal
            // flusso a chunk": senza registrazione il popolatore la
            // rispawnava doppia davanti a casa.
            Vehicle.VehicleSpawnManager.RegisterActiveOwned(code, rebuilt);
            Toast("Auto ritirata dal garage.");
            return true;
        }

        /// <summary>Trova un punto sulla strada percorsa piu vicino alla porta,
        /// salvo essere raggruppata con altri veicoli confusi. Ripiega sul
        /// punto esatto della porta se la rete stradale non ha tile.</summary>
        private static Vector3 SpawnPointOnRoad(Vector3 nearPos)
        {
            var rn = Vehicle.Traffic.TileRoadNetwork.Instance;
            if (rn != null && rn.HasTiles)
            {
                var node = rn.NearestNodeWithOut(nearPos);
                if (node != null && (node.position - nearPos).sqrMagnitude <= 200f * 200f)
                    return node.position + Vector3.up * 0.3f;
            }
            return nearPos + Vector3.up * 0.3f;
        }

        /// <summary>Risolve il modello dell'auto dal nome del GameObject
        /// (formato "codice_modello" creato da BuildVehicle).</summary>
        private static string ResolveModel(GameObject go)
        {
            if (go == null) return "Hatchback";
            string gn = go.name;
            int us = gn.LastIndexOf('_');
            if (us < 0 || us == gn.Length - 1) return "Hatchback";
            string cand = gn.Substring(us + 1);
            var defs = Vehicle.VehicleSpawnManager.Catalogue;
            for (int i = 0; i < defs.Length; i++)
            {
                if (defs[i].name == cand) return cand;
            }
            return "Hatchback";
        }

        private static void Toast(string msg)
        {
            Game g = Game.Instance;
            if (g != null && g.ui != null) g.ui.ShowToast(msg);
        }
    }
}
