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
            OpenAndroidActivity("com.intelligame.huntix.HomeActivity");
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

        // ── MiAcitma: città OSM reale (scena City) ──────────────────

        // Scrive un log nel sistema AppLog dell'app (logcat + storico app),
        // così il comportamento della sezione MiAcitma è visibile nel viewer
        // dei log dell'app anche se lato Android nulla arriva.
        public static void LogToAndroid(string tag, string message)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("logFromUnity", tag, message);
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] LogToAndroid: " + e.Message);
            }
            #endif
        }

        // Avvia il tracking GPS (legacy OutdoorManager) così la città OSM
        // può seguire il giocatore reale via streaming.
        public static void StartLocationTracking()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("startLocationTracking");
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] StartLocationTracking: " + e.Message);
            }
            #endif
        }

        // Esce dalla scena City (La Mia Città) e torna alla Home nativa:
        // chiude l'Activity Unity (BridgeActivity) che sta sopra HomeActivity
        // nel back stack (stesso meccanismo di exitIndoor per il negozio).
        public static void ExitCityToHome()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("exitMiacitta");
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] ExitCityToHome: " + e.Message);
            }
            #endif
        }

        // Richiede l'envelope OSM completo (strade/edifici/alberi/parchi) per
        // (lat,lng) entro [radiusMeters]. La risposta arriva da Android via
        // UnitySendMessage("GameManager", "OnOsmCityReceived", json).
        public static void RequestOsmCity(double lat, double lng, int radiusMeters)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("requestOsmCity", lat, lng, radiusMeters);
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] RequestOsmCity: " + e.Message);
            }
            #endif
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
        // ── MiAcitma: cambio MVC (Huntix Coins) ↔ soldi citta ─────────────
        // L'MVC e' la valuta guadagnata fuori da MiAcitma (minigiochi, lavori,
        // battle, presenza): vive in SavedManager lato Android. Questi metodi
        // espongono saldo e movimenti per lo scambio in banca/bancomat.

        /// <summary>Saldo MVC attuale (0 se fuori da Android o bridge assente).</summary>
        public static double GetMvcBalance()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return (double)jc.CallStatic<double>("getMvcBalance");
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] GetMvcBalance: " + e.Message);
            }
            #endif
            return 0.0;
        }

        /// <summary>Prova a spendere [amount] MVC. True se il saldo bastava.</summary>
        public static bool SpendMvc(double amount)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<bool>("spendMvc", amount);
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] SpendMvc: " + e.Message);
            }
            #endif
            return false;
        }

        /// <summary>Accredita [amount] MVC; ritorna il nuovo saldo.</summary>
        public static double AddMvc(double amount)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return (double)jc.CallStatic<double>("addMvc", amount);
                }
            }
            catch (System.Exception e)
            {
                UnityEngine.Debug.LogWarning("[UnityBridge] AddMvc: " + e.Message);
            }
            #endif
            return 0.0;
        }

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

        // ── Unified player profile (unico giocatore, unico universo) ──
        // Miacitta legge/scrive LO STESSO profilo Huntix (fonte di verita'),
        // non un profilo separato: stesso nome, stessa XP, stessa classifica.

        /// <summary>Nome del player Huntix (Unified profile).</summary>
        public static string GetPlayerName()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<string>("getPlayerName") ?? "Giocatore";
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerName: " + e.Message); }
            #endif
            return "Giocatore";
        }

        /// <summary>XP totale cumulata del player Huntix.</summary>
        public static long GetPlayerXp()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<long>("getPlayerXp");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerXp: " + e.Message); }
            #endif
            return 0L;
        }

        /// <summary>Livello del player Huntix (dalla XP).</summary>
        public static int GetPlayerLevel()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<int>("getPlayerLevel");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerLevel: " + e.Message); }
            #endif
            return 1;
        }

        /// <summary>Potere totale del player Huntix.</summary>
        public static long GetPlayerPower()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<long>("getPlayerPower");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerPower: " + e.Message); }
            #endif
            return 0L;
        }

        /// <summary>Gemme premium del player Huntix.</summary>
        public static int GetPlayerGems()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<int>("getPlayerGems");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerGems: " + e.Message); }
            #endif
            return 0;
        }

        /// <summary>Energia corrente del player Huntix (0..100).</summary>
        public static int GetPlayerEnergy()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<int>("getPlayerEnergy");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerEnergy: " + e.Message); }
            #endif
            return 100;
        }

        /// <summary>Conteggio uova nell'inventario Huntix del player.</summary>
        public static int GetEggCount()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<int>("getEggCount");
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetEggCount: " + e.Message); }
            #endif
            return 0;
        }

        /// <summary>Snapshot completo del profilo Huntix come JSON.</summary>
        public static string GetPlayerProfileJson()
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    return jc.CallStatic<string>("getPlayerProfileJson") ?? "{}";
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] GetPlayerProfileJson: " + e.Message); }
            #endif
            return "{}";
        }

        /// <summary>Accredita XP guadagnati in Miacitta al profilo Huntix.</summary>
        public static void AddXpFromCity(long xpAmount)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("addXpFromCity", xpAmount);
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] AddXpFromCity: " + e.Message); }
            #endif
        }

        /// <summary>Accredita potere guadagnato in Miacitta al profilo Huntix.</summary>
        public static void AddPowerFromCity(long powerAmount)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("addPowerFromCity", powerAmount);
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] AddPowerFromCity: " + e.Message); }
            #endif
        }

        /// <summary>Accredita gemme guadagnate in Miacitta al profilo Huntix.</summary>
        public static void AddGemsFromCity(int amount)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("addGemsFromCity", amount);
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] AddGemsFromCity: " + e.Message); }
            #endif
        }

        /// <summary>Sincronizza l'energia del player dalla citta' al profilo Huntix.</summary>
        public static void SyncEnergyFromCity(int energy)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("syncEnergyFromCity", energy);
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] SyncEnergyFromCity: " + e.Message); }
            #endif
        }

        /// <summary>Cambia il nome del player da Miacitta (es. reincarnazione) sul profilo Huntix.</summary>
        public static void SetPlayerNameFromCity(string newName)
        {
            #if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("setPlayerNameFromCity", newName);
                }
            }
            catch (System.Exception e) { Debug.LogWarning("[UnityBridge] SetPlayerNameFromCity: " + e.Message); }
            #endif
        }
    }
}