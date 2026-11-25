using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Economy
{
    /// <summary>
    /// Stato economico dei negozi walk-in (contatori stile tycoon).
    ///
    /// NON tocca Wallet: i ricavi sono un riepilogo di gestione, non una
    /// stampella di valuta. Ogni record ha una chiave stabile (edificio +
    /// coordinate + tipologia), viaggia nello snapshot CityWorldSync verso
    /// Android e torna indietro con merge non distruttivo (il massimo vince).
    /// La rarita' resta quella unica COMMON..LEGENDARY: qui non ne nasce una
    /// parallela, il riflesso uova resta in ContainerEgg.
    /// </summary>
    public static class ShopState
    {
        public const string BridgeEvent = "ShopActivity";

        [Serializable]
        public sealed class Record
        {
            public string key = "";
            public string kind = "";
            public int served;
            public int revenue;
            public int level = 1;
            public int stock = 10;
            public long lastTs;
        }

        [Serializable]
        private sealed class Store
        {
            public List<Record> shops = new List<Record>();
        }

        private const string PrefKey = "huntix_shop_states_v1";
        private const float CustomerInterval = 6f;

        private static Store _store;
        private static float _timer;

        private static Store Data
        {
            get
            {
                if (_store == null) _store = Load();
                return _store;
            }
        }

        private static Store Load()
        {
            Store s = null;
            try
            {
                string json = PlayerPrefs.GetString(PrefKey, "");
                if (!string.IsNullOrEmpty(json)) s = JsonUtility.FromJson<Store>(json);
            }
            catch (Exception) { }
            if (s == null || s.shops == null) s = new Store();
            return s;
        }

        private static void Save()
        {
            try
            {
                PlayerPrefs.SetString(PrefKey, JsonUtility.ToJson(Data));
                PlayerPrefs.Save();
            }
            catch (Exception) { }
        }

        /// <summary>Snapshot completo per CityWorldSync (sempre stringa non null).</summary>
        public static string Export()
        {
            string json = JsonUtility.ToJson(Data);
            return string.IsNullOrEmpty(json) ? "{\"shops\":[]}" : json;
        }

        /// <summary>Merge non distruttivo: per ogni chiave il valore locale piu'
        /// avanzato vince. Chiamato da ApplySnapshot con i dati tornati da Android.</summary>
        public static void Import(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            Store incoming;
            try { incoming = JsonUtility.FromJson<Store>(json); }
            catch (Exception) { return; }
            if (incoming == null || incoming.shops == null) return;

            bool changed = false;
            for (int i = 0; i < incoming.shops.Count; i++)
            {
                Record r = incoming.shops[i];
                if (r == null || string.IsNullOrEmpty(r.key)) continue;
                Record local = Find(r.key);
                if (local == null)
                {
                    Data.shops.Add(r);
                    changed = true;
                    continue;
                }
                if (r.served > local.served) { local.served = r.served; changed = true; }
                if (r.revenue > local.revenue) { local.revenue = r.revenue; changed = true; }
                if (r.level > local.level) { local.level = r.level; changed = true; }
                if (r.stock > local.stock) { local.stock = r.stock; changed = true; }
                if (r.lastTs > local.lastTs) { local.lastTs = r.lastTs; changed = true; }
                if (string.IsNullOrEmpty(local.kind) && !string.IsNullOrEmpty(r.kind))
                {
                    local.kind = r.kind;
                    changed = true;
                }
            }
            if (changed) Save();
        }

        private static Record Find(string key)
        {
            for (int i = 0; i < Data.shops.Count; i++)
                if (Data.shops[i] != null && Data.shops[i].key == key) return Data.shops[i];
            return null;
        }

        public static Record GetOrCreate(string key, string kind)
        {
            if (string.IsNullOrEmpty(key)) return null;
            Record r = Find(key);
            if (r == null)
            {
                r = new Record();
                r.key = key;
                r.kind = string.IsNullOrEmpty(kind) ? "negozio" : kind;
                r.lastTs = DateTime.UtcNow.Ticks;
                Data.shops.Add(r);
                Save();
            }
            return r;
        }

        /// <summary>Avanza la simulazione solo mentre il player e' dentro un
        /// negozio. Nessun NavMesh, nessuna fisica, al massimo un cliente ogni
        /// 6 secondi: costo trascurabile anche con 49 chunk caricati.</summary>
        public static void Tick(float deltaTime, bool insideShop, string key, string kind)
        {
            if (!insideShop || string.IsNullOrEmpty(key))
            {
                _timer = 0f;
                return;
            }
            _timer += Mathf.Clamp(deltaTime, 0f, 0.25f);
            if (_timer < CustomerInterval) return;
            _timer -= CustomerInterval;
            RegisterCustomer(key, kind);
        }

        /// <summary>Un cliente servito: incasso deterministico per tipologia,
        /// scorte che si esauriscono e si riassorbono, livello ogni 10 clienti.
        /// Ritorna il record aggiornato (usato dall'etichetta in negozio).</summary>
        public static Record RegisterCustomer(string key, string kind)
        {
            Record r = GetOrCreate(key, kind);
            if (r == null) return null;

            int price;
            switch (kind)
            {
                case "concessionaria": case "auto": price = 40; break;
                case "abbigliamento": price = 12; break;
                case "parruccheria": price = 10; break;
                case "lavaggiocani": price = 8; break;
                case "autolavaggio": price = 9; break;
                case "biglietteria": price = 6; break;
                case "bar": price = 4; break;
                default: price = 3; break;
            }
            r.served++;
            r.revenue += price;
            if (r.stock > 0) r.stock--;
            else if (r.served % 3 == 0) r.stock += 2;
            r.level = 1 + r.served / 10;
            r.lastTs = DateTime.UtcNow.Ticks;

            Save();
            try { UnityBridge.SendMessageToAndroid(BridgeEvent, Export()); }
            catch (Exception) { }
            return r;
        }

        /// <summary>Linea italiana per l'etichetta dentro il negozio.</summary>
        public static string StatusLine(string key, string kind)
        {
            Record r = GetOrCreate(key, kind);
            if (r == null) return "";
            return "CLIENTI " + r.served + " · INCASSO " + r.revenue +
                   " MVC · LIVELLO " + r.level + " · SCORTE " + r.stock;
        }

        /// <summary>Indizio uovo coerente con il Riflesso: la chiave è stabile e
        /// il contenitore reale e' creato da ShopInteriorBuilder.</summary>
        public static string EggHint(string kind)
        {
            switch (kind)
            {
                case "bar": return "Uovo nel cassetto sotto il bancone del bar.";
                case "concessionaria": case "auto": return "Uovo nella scrivania dell'ufficio vendite.";
                case "parruccheria": return "Uovo nella credenza degli asciugamani.";
                case "lavaggiocani": return "Uovo nel carrello della dispensa.";
                case "autolavaggio": return "Uovo nella dispensa degli asciugamani.";
                case "biglietteria": return "Uovo nel cassetto della biglietteria.";
                default: return "Uovo nel cassetto della cassa o nella dispensa.";
            }
        }
    }
}
