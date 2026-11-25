using System;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Interruttore A/B dei negozi: "Huntix" (strato universale procedurale,
    /// default) oppure "Prefabbricato" (prefab CC0 pronto). Non rimuove nessun
    /// sistema: entrambi restano nel progetto e lo switch sceglie solo quale
    /// strato di arredi costruire dentro l'edificio già enterabile.
    ///
    /// Lo stato viaggia nello snapshot CityWorldSync e torna da Android con
    /// merge per timestamp, così il confronto sopravvive al riavvio. Nessuna
    /// valuta, nessuna rarità, nessun cambio geo/graph.
    /// </summary>
    public static class ShopStyle
    {
        public const string BridgeEvent = "ShopStyleChanged";
        public const string PrefabResource = "ShopPrefabs/NegozioProntoCC0";

        private const string PrefKey = "huntix_shop_style_v1";
        private const string TsKey = "huntix_shop_style_ts";

        private static int _state = -1;

        [Serializable]
        private sealed class Payload
        {
            public bool usePrefab;
            public long ts;
        }

        public static bool UsePrefab
        {
            get
            {
                if (_state < 0) _state = PlayerPrefs.GetInt(PrefKey, 0);
                return _state == 1;
            }
        }

        public static void Set(bool usePrefab)
        {
            _state = usePrefab ? 1 : 0;
            PlayerPrefs.SetInt(PrefKey, _state);
            PlayerPrefs.SetString(TsKey, DateTime.UtcNow.Ticks.ToString());
            PlayerPrefs.Save();
            try
            {
                UnityBridge.SendMessageToAndroid(BridgeEvent, Export());
            }
            catch (Exception) { }
        }

        public static string Export()
        {
            var p = new Payload();
            p.usePrefab = UsePrefab;
            long ts;
            long.TryParse(PlayerPrefs.GetString(TsKey, "0"), out ts);
            p.ts = ts;
            string json = JsonUtility.ToJson(p);
            return string.IsNullOrEmpty(json) ? "{\"usePrefab\":false,\"ts\":0}" : json;
        }

        /// <summary>Importo non distruttivo: vince il valore con timestamp più
        /// recente. In install fresca (ts=0) lo stato Android viene adottato.</summary>
        public static void Import(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            Payload p;
            try { p = JsonUtility.FromJson<Payload>(json); }
            catch (Exception) { return; }
            if (p == null) return;

            long local;
            long.TryParse(PlayerPrefs.GetString(TsKey, "0"), out local);
            if (p.ts <= local) return;
            Set(p.usePrefab);
            PlayerPrefs.SetString(TsKey, p.ts.ToString());
            PlayerPrefs.Save();
        }

        public static string Label
        {
            get { return UsePrefab ? "Prefabbricato" : "Huntix"; }
        }
    }
}
