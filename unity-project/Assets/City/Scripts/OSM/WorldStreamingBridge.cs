using System;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Ponte bidirezionale del World Streaming (obbligatorio per il Mandato).
    ///
    /// Unity -> Android: ready del catalogo, ingresso in un settore, uovo di
    /// scoperta (canale FeatureEggHidden gia' whitelistato).
    /// Android -> Unity: stato dei settori visitati, richiesta di prefetch e
    /// informazioni sulla memoria del dispositivo.
    ///
    /// Nessun evento tocca inventario/progressi/veicoli/proprieta': il mirror
    /// Android usa pref dedicati, pertanto scaricare un settore non puo'
    /// cancellare dati persistenti del giocatore.
    /// </summary>
    public static class WorldStreamingBridge
    {
        private const string BridgeClass = "com.intelligame.huntix.bridge.StoreUnityBridge";

        public static string GetStatus()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    return jc.CallStatic<string>("getWorldStreamingStatus") ?? "";
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] getWorldStreamingStatus: " + e.Message);
            }
#endif
            return "";
        }

        public static string GetMemoryInfo()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    return jc.CallStatic<string>("getDeviceMemoryInfo") ?? "";
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] getDeviceMemoryInfo: " + e.Message);
            }
#endif
            return "";
        }

        public static void RequestSector(string key)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass(BridgeClass))
                    jc.CallStatic("requestWorldSector", key ?? "");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] requestWorldSector: " + e.Message);
            }
#endif
        }

        public static void NotifyReady(int sectors)
        {
            string json = "{\"sectors\":" + sectors + ",\"cacheVersion\":" +
                TileClient.CacheVersion + "}";
            Send("WorldStreamingReady", json);
        }

        public static void NotifySectorChanged(string json)
        {
            Send("WorldSectorChanged", json ?? "{}");
        }

        public static void OnStatusFromAndroid(string json)
        {
            OsmDiag.Log("[WorldStreaming] stato Android: " +
                (string.IsNullOrEmpty(json) ? "vuoto" : json));
        }

        /// <summary>Riflesso della caccia alle uova: prima scoperta di un settore
        /// di streaming. Passa dal canale canonico FeatureEggHidden, quindi
        /// Android registra id, lat/lng e timestamp come per ogni altra uovo.</summary>
        public static bool GrantDiscoveryEgg()
        {
            var game = City.Game.Instance;
            if (game == null || game.player == null) return false;
            try
            {
                game.GrantAuditEgg("world_sector_discovered",
                    "Esploratore del Settore", "MiCitta");
                OsmDiag.Log("[WorldStreaming] uovo Esploratore del Settore piazzato");
                return true;
            }
            catch (Exception e)
            {
                Debug.LogWarning("[WorldStreaming] uovo settore: " + e.Message);
                return false;
            }
        }

        private static void Send(string ev, string json)
        {
            try { Huntix.Bridge.UnityBridge.SendMessageToAndroid(ev, json); }
            catch (Exception e) { Debug.LogWarning("[WorldStreaming] " + ev + ": " + e.Message); }
        }
    }
}
