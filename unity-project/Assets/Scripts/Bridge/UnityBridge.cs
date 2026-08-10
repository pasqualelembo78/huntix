using UnityEngine;
using System.Collections;
using System.Collections.Generic;

namespace Huntix.Bridge
{
    public static class UnityBridge
    {
        private static AndroidJavaObject _bridge;

        public static void Init()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                {
                    var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                    _bridge = activity.Call<AndroidJavaObject>("getBridge");
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[UnityBridge] getBridge() non disponibile, continuo senza bridge: " + e.Message);
                _bridge = null;
            }
            #endif
        }

        // Modalità con cui è stata lanciata l'Activity Unity (extra "unity_mode"
        // di BridgeActivity: esplora/outdoor/indoor/reallife). Vuota se assente.
        public static string GetMode()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                using (var intent = activity.Call<AndroidJavaObject>("getIntent"))
                {
                    return intent.Call<string>("getStringExtra", "unity_mode") ?? "";
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[UnityBridge] GetMode: " + e.Message);
            }
            #endif
            return "";
        }

        public static void SaveData(string json)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("saveData", json);
            #endif
        }

        public static string LoadData()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            return _bridge?.Call<string>("loadData");
            #endif
            return "{}";
        }

        public static void ShowToast(string message)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("showToast", message);
            #endif
        }

        public static void OpenAndroidActivity(string className)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("openAndroidActivity", className);
            #endif
        }

        public static string GetSharedPreference(string key)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            return _bridge?.Call<string>("getSharedPreference", key);
            #endif
            return "";
        }

        public static void SetSharedPreference(string key, string value)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("setSharedPreference", key, value);
            #endif
        }

        public static void SendMessageToAndroid(string eventName, string jsonData)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.Bridge");
            jc.CallStatic("onUnityMessage", eventName, jsonData);
            #endif
        }

        public static void QuitToAndroid()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            OpenAndroidActivity("com.intelligame.huntix.ui.OutdoorWorldActivity");
            #endif
        }

        // ── Huntix POI (clone rewrite, Kotlin bridge) ──────────────
        // Coordinate corrente (reale o mock-walk) della mappa 3D.
        // Ritorna JSON: {"lat":..,"lng":..,"mock":bool,"acc":float}
        public static string GetCurrentLocation()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
            {
                return jc.CallStatic<string>("getCurrentLocation") ?? "{\"lat\":0.0,\"lng\":0.0,\"mock\":false}";
            }
            #endif
            return "{\"lat\":0.0,\"lng\":0.0,\"mock\":false}";
        }

        // Attiva/disattiva la simulazione di camminata (levetta "cammina senza muoversi").
        public static void SetMockWalk(bool enable)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("setMockWalk", enable);
            #endif
        }

        // Cattura la creatura/POI con storeId (chiamato ad es. da AR proximity).
        // La risposta arriva Unity → Android via Bridge.tryCatch, che a sua volta
        // riconosce con GameManager.SendMessage("OnEvent", "CatchResult|{json}")
        public static void TryCatch(string storeId)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            _bridge?.Call("tryCatch", storeId);
            #endif
        }

        // ── Esplora: richiede POI OSM intorno a (lat,lng) entro [radiusMeters]. ──
        // Il risultato arriva da Android via UnitySendMessage("GameManager",
        // "OnPoisReceived", "{\"pois\":[...],\"count\":N,...}").
        public static void RequestPoisNearby(double lat, double lng, int radiusMeters)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
            {
                jc.CallStatic("requestPoisNearby", lat, lng, radiusMeters);
            }
            #endif
        }

        // ── Esplora: bisogni (LocalNeeds). Ritorna JSON {"hunger":..,"sleep":..,"hygiene":..,"fun":..,"thirst":..}.
        // In editor (nessun Android) ritorna "{}" e l'HUD usa il fallback PlayerPrefs.
        public static string GetNeedsJson()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<string>("getNeedsJson") ?? "{}";
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[UnityBridge] GetNeedsJson: " + e.Message);
            }
            #endif
            return "{}";
        }

        // Applica un'azione a un bisogno (needKey: hunger/sleep/hygiene/fun/thirst) e
        // restituisce il JSON aggiornato.
        public static string ApplyNeedAction(string needKey, float gain)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<string>("applyNeedAction", needKey, gain) ?? "{}";
                }
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[UnityBridge] ApplyNeedAction: " + e.Message);
            }
            #endif
            return "{}";
        }

        // Apre la pagina del POI (custom JSON / web / JSON sintetico OSM).
        public static void OpenPoiPage(string osmId, string name, string buildingType,
                                       string poiType, string pageType, string url,
                                       double lat, double lng, string category)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
            {
                jc.CallStatic("openPoiPage", osmId, name, buildingType, poiType,
                              url, pageType, lat, lng, category);
            }
            #endif
        }
    }
}