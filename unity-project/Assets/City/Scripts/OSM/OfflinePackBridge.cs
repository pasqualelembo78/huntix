using System;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Ponte bidirezionale per i pacchetti offline di MiCittà.
    ///
    /// Unity -> Android: legge lo stato (flag utente + file presenti), chiede
    /// di cambiare un flag o di scaricare un pacchetto.
    /// Android -> Unity: al termine di un download invia lo snapshot aggiornato
    /// a GameManager.OnOfflinePackChanged, così il menu di MiCittà può
    /// mostrare subito "installato" senza riavviare la scena.
    ///
    /// Nessun cambiamento a TileClient: i file restano i soliti .v5.*.json
    /// nella cache già usata dal preload 3x3.
    /// </summary>
    public static class OfflinePackBridge
    {
        private const string BridgeClass = "com.intelligame.huntix.bridge.StoreUnityBridge";
        private static bool _loggedOnce;
        private static bool _eggGranted;

        public static string StatusJson()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    return jc.CallStatic<string>("getOfflinePackStatus") ?? "";
            }
            catch (Exception e)
            {
                Debug.LogWarning("[OfflinePack] StatusJson: " + e.Message);
            }
#endif
            return "";
        }

        public static bool IsTileOffline(string key)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    return jc.CallStatic<bool>("isTileOffline", key);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[OfflinePack] IsTileOffline: " + e.Message);
            }
#endif
            return false;
        }

        public static void SetFlag(string id, bool enabled)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    jc.CallStatic("setOfflinePackFlag", id, enabled);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[OfflinePack] SetFlag: " + e.Message);
            }
#endif
        }

        public static void RequestPack(string id)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    jc.CallStatic("requestOfflinePack", id);
            }
            catch (Exception e)
            {
                Debug.LogWarning("[OfflinePack] RequestPack: " + e.Message);
            }
#endif
        }

        /// <summary>Chiamato una volta all'avvio della scena City.</summary>
        public static void LogStatusOnce()
        {
            if (_loggedOnce) return;
            _loggedOnce = true;
            string json = StatusJson();
            OsmDiag.Log("[OfflinePack] stato: " +
                (string.IsNullOrEmpty(json) ? "non disponibile" : Summarize(json)));
        }

        /// <summary>Callback Android dopo un download completato.</summary>
        public static void OnStatusFromAndroid(string json)
        {
            OsmDiag.Log("[OfflinePack] aggiornato da Android: " + Summarize(json ?? ""));

            // Riflesso della caccia alle uova: il primo pacchetto installato
            // sblocca un uovo-feature "Cacciatore Offline" con lo stesso canale
            // FeatureEggHidden / EggCapturedInCity delle altre novelty.
            if (_eggGranted || City.Game.Instance == null) return;
            _eggGranted = true;
            try
            {
                City.Game.Instance.GrantAuditEgg(
                    "offline_pack_ready", "Cacciatore Offline", "Mondo offline");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[OfflinePack] uovo offline non piazzato: " + e.Message);
            }
        }

        private static string Summarize(string json)
        {
            // Lo snapshot completo ha 39 pacchetti: in logcat teniamo solo
            // l'inizio, il resto e' consultabile con StatusJson().
            return json.Length <= 240 ? json : json.Substring(0, 240) + "…";
        }
    }
}
