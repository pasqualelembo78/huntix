using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Networking;

namespace City.OSM
{
    /// <summary>
    /// Scarica le tile (graph + geo) dal server HUNTIX con cache su disco.
    /// Strategia cache-first: se il file esiste in locale non si tocca la rete.
    /// Il server serve JSON non compresso (il .gz resta solo lato storage).
    /// </summary>
    public static class TileClient
    {
        public const string BaseUrl = "http://82.165.218.56:5100";
        private static string CacheDir =>
            Path.Combine(Application.persistentDataPath, "huntix_tiles");

        /// <summary>Byte di tile effettivamente letti (cache o rete) in questa
        /// sessione di citta': alimenta l'indicatore KB/MB dello splash.</summary>
        public static long TotalBytes;

        // ── Prefetch in memoria SOLO da cache disco ───────────────────────
        // Il World Streaming legge le tile vicine/prima di un teleport su un
        // thread di background: nessuna richiesta di rete, nessun JSON tenuto
        // per sempre. Le voci sono poche (8 file = 4 tile graph+geo) e vengono
        // espulse in order LRU o quando Android segnala poca memoria.
        private const int MaxPrefetchedFiles = 8;
        private static readonly Dictionary<string, string> _prefetched =
            new Dictionary<string, string>();
        private static readonly LinkedList<string> _prefetchOrder =
            new LinkedList<string>();

        public static int PrefetchCount { get { return _prefetched.Count; } }

        // Registra il persistentDataPath verso Android UNA volta: il preloader
        // di avvio dell'app (CityTilePreloader) scrive le tile ESATTAMENTE
        // dove Unity le cerca, cosi' all'ingresso il primo chunk e' gia' su
        // disco (cache-first di Fetch). Scope: solo registrazione, nessun
        // ritocco alla logica di fetch/retry.
        private static bool _cacheDirRegistered;
        private static void RegisterCacheDirOnce()
        {
            if (_cacheDirRegistered) return;
            _cacheDirRegistered = true;
            try
            {
                using (var cls = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    cls.CallStatic("setTileCacheDir", Application.persistentDataPath);
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[TileClient] setTileCacheDir: " + e.Message);
            }
        }

        private static string CachePath(string key, string suffix)
        {
            RegisterCacheDirOnce();
            Directory.CreateDirectory(CacheDir);
            return Path.Combine(CacheDir, key + suffix);
        }

        /// <summary>Cache-first; onMiss scarica da rete. callback mai null-safe: controllare result != null.</summary>
        public static IEnumerator FetchGraph(string tileKey, Action<TileGraphDoc> done)
        {
            yield return Fetch(TileGraphCachePath(tileKey),
                BaseUrl + "/api/tiles/" + tileKey + "/graph",
                json => done(JsonUtility.FromJson<TileGraphDoc>(json)));
        }

        public static IEnumerator FetchGeo(string tileKey, Action<TileGeoDoc> done)
        {
            yield return Fetch(TileGeoCachePath(tileKey),
                BaseUrl + "/api/tiles/" + tileKey + "/geo",
                json => done(JsonUtility.FromJson<TileGeoDoc>(json)));
        }

        // Versione cache: incrementare quando cambia il FORMATO dei dati
        // serviti cosi' i device riscaricano.
        //  - v2 = aggiunti i civici
        //  - v3 = griglia DEM 'ele'/'ele_nrow'/'ele_ncol' nelle geo (senza, le
        //        tile vecchie restano piatte: strade sotto il terreno, niente
        //        fisica altimetrica per auto/pedoni/player)
        //  - v4 = strade su viadotto/galleria (campi br/tu/dh/h0/h1/s0/s1)
        //        nelle geo: il renderer le sospende/tunnelizza, non le
        //        drappa piu' sui rialzi (senza bump i device riusano le
        //        vecchie .v3.geo.json drappate)
        //  - v5 = griglia DEM RIGENERATA lato server: passo 1000 -> 250 m
        //        allineato ai confini dei chunk (5x5 celle per chunk, prima
        //        1x1 = un piano per chunk con piega secca al confine) e void
        //        SRTM riempiti col valore valido piu' vicino invece che con 0.
        //        Quel 0 era livello del mare: su ogni tile costiera (Bari:
        //        129 celle su 132 a 0.0) produceva una scogliera artificiale
        //        fra citta' e mare. Obbligatorio: senza bump i device riusano
        //        le .v4.geo.json con la griglia rotta in memoria.
        public const int CacheVersion = 5;
        public static string TileGraphCachePath(string key) =>
            CachePath(key, ".v" + CacheVersion + ".graph.json");
        public static string TileGeoCachePath(string key) =>
            CachePath(key, ".v" + CacheVersion + ".geo.json");

        /// <summary>True se la geo della tile e' gia' su disco (nessuna rete).</summary>
        public static bool HasCachedGeo(string tileKey)
        {
            try { return File.Exists(TileGeoCachePath(tileKey)); }
            catch (Exception) { return false; }
        }

        /// <summary>JSON gia' prefetchato in memoria, per skyline/pronto teleport.</summary>
        public static bool TryGetGeoJson(string tileKey, out string json)
        {
            return _prefetched.TryGetValue(TileGeoCachePath(tileKey), out json);
        }

        /// <summary>
        /// Legge graph+geo di UNA tile dalla cache su thread di background.
        /// Se il file non c'e' non succede nulla: MAI rete da questo percorso.
        /// </summary>
        public static IEnumerator PrefetchSector(string tileKey)
        {
            if (string.IsNullOrEmpty(tileKey)) yield break;
            string[] paths = { TileGraphCachePath(tileKey), TileGeoCachePath(tileKey) };
            for (int i = 0; i < paths.Length; i++)
            {
                string path = paths[i];
                if (_prefetched.ContainsKey(path)) continue;
                bool exists;
                try { exists = File.Exists(path); }
                catch (Exception) { exists = false; }
                if (!exists) continue;
                var read = Task.Run(() => ReadSafe(path));
                yield return new WaitUntil(() => read.IsCompleted);
                string json = read.Result;
                if (json != null) Remember(path, json);
            }
        }

        private static void Remember(string path, string json)
        {
            if (_prefetched.ContainsKey(path))
            {
                _prefetchOrder.Remove(path);
                _prefetched.Remove(path);
            }
            _prefetched[path] = json;
            _prefetchOrder.AddLast(path);
            int keep = Diagnostics.AdaptivePerformanceProfile.Instance != null
                ? Diagnostics.AdaptivePerformanceProfile.Instance.PrefetchKeep
                : MaxPrefetchedFiles;
            while (_prefetched.Count > keep && _prefetchOrder.Count > 0)
            {
                string oldest = _prefetchOrder.First.Value;
                _prefetchOrder.RemoveFirst();
                _prefetched.Remove(oldest);
            }
        }

        /// <summary>Riduce la memoria tenuta dal prefetch (device low-memory).</summary>
        public static void TrimPrefetch(int keep)
        {
            if (keep < 0) keep = 0;
            while (_prefetched.Count > keep && _prefetchOrder.Count > 0)
            {
                string oldest = _prefetchOrder.First.Value;
                _prefetchOrder.RemoveFirst();
                _prefetched.Remove(oldest);
            }
        }

        private static IEnumerator Fetch(string cacheFile, string url, Action<string> parse)
        {
            // Prefetch del World Streaming: evita una lettura disco sincrona
            // quando la tile e' gia' in memoria (teleport/prefetch direzionale).
            string prefetched;
            if (_prefetched.TryGetValue(cacheFile, out prefetched))
            {
                CountBytes(prefetched);
                parse(prefetched);
                yield break;
            }

            // Cache-first ma con I/O ASYNC su thread di background: le tile
            // geo di zone dense arrivano a molti MB; leggerle/scriverle sul
            // main thread causava frame hitch nel bel mezzo dello streaming.
            // NB: nessun yield dentro try/catch (vietato da C#): le eccezioni
            // di I/O sono gia' gestite da ReadSafe/WriteSafe.
            bool exists;
            try { exists = File.Exists(cacheFile); }
            catch (Exception e)
            {
                Debug.LogWarning("[TileClient] cache check " + cacheFile + ": " + e.Message);
                exists = false;
            }
            if (exists)
            {
                var read = Task.Run(() => ReadSafe(cacheFile));
                yield return new WaitUntil(() => read.IsCompleted);
                string cached = read.Result;
                if (cached != null)
                {
                    CountBytes(cached);
                    OsmDiag.Log("[TileClient] cache hit " + Path.GetFileName(cacheFile));
                    parse(cached);
                    yield break;
                }
            }

            using (var req = UnityWebRequest.Get(url))
            {
                // la prima generazione on-demand lato server puo' richiedere
                // 1-3 minuti (osmium extract); le successive sono istantanee
                req.timeout = 240;
                OsmDiag.Log("[TileClient] GET " + url);
                yield return req.SendWebRequest();

#if UNITY_2020_1_OR_NEWER
                bool ok = req.result == UnityWebRequest.Result.Success;
#else
                bool ok = !req.isNetworkError && !req.isHttpError;
#endif
                if (!ok)
                {
                    OsmDiag.Log("[TileClient] FAIL " + url + " : " + req.error);
                    parse(null);
                    yield break;
                }
                string json = req.downloadHandler.text;
                CountBytes(json);
                OsmDiag.Log("[TileClient] OK " + Path.GetFileName(cacheFile) +
                    " (" + (json.Length / 1024) + "KB)");
                // scrittura su thread di background per non bloccare il frame
                var write = Task.Run(() => WriteSafe(cacheFile, json));
                yield return new WaitUntil(() => write.IsCompleted);
                parse(json);
            }
        }

        private static string ReadSafe(string path)
        {
            try { return File.ReadAllText(path); }
            catch (IOException) { return null; }
            catch (System.Exception e) { Debug.LogWarning("[TileClient] read " + path + ": " + e.Message); return null; }
        }

        private static void WriteSafe(string path, string content)
        {
            try { File.WriteAllText(path, content); }
            catch (IOException e) { Debug.LogWarning("[TileClient] cache write " + path + ": " + e.Message); }
            catch (System.Exception e) { Debug.LogWarning("[TileClient] cache write " + path + ": " + e.Message); }
        }

        private static void CountBytes(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            try { TotalBytes += Encoding.UTF8.GetByteCount(json); }
            catch (System.Exception e) { Debug.LogWarning("[TileClient] count bytes: " + e.Message); }
        }
    }

    /// <summary>Log diagnostico OSM: oltre alla console Unity viene inviato
    /// DIRETTAMENTE ad AppLog Android (UnityBridge.LogToAndroid). Serve come
    /// scatola nera: se il processo muore di crash nativo, le ultime righe
    /// restano scritte sul lato Kotlin.</summary>
    internal static class OsmDiag
    {
        public static void Log(string msg)
        {
            City.Diagnostics.SessionTelemetry.NoteLast(msg);
            // Debug.Log passa da Application.logMessageReceived (ErrorSurface):
            // senza soppressione il messaggio arriva due volte ad Android
            // (copia col tag "OSM" qui sotto + re-forward col tag [..]/[Log]).
            Huntix.Core.ErrorSurface.SuppressNextAndroid();
            UnityEngine.Debug.Log(msg);
            try { Huntix.Bridge.UnityBridge.LogToAndroid("OSM", msg); }
            catch (Exception) {}
        }

#if UNITY_EDITOR || DEVELOPMENT_BUILD
        public const bool VerboseDiagnostics = true;
#else
        public const bool VerboseDiagnostics = false;
#endif

        /// <summary>Log ad alto volume (per edificio, per prefab, per way):
        /// in release e' disattivato alla compilazione per non pagare JNI e
        /// allocazioni di stringhe migliaia di volte durante il build dei chunk.
        /// Errori, warning e riepiloghi restano su Log/LogThrottled.</summary>
        public static void LogDebug(string msg)
        {
            if (VerboseDiagnostics) Log(msg);
        }

        /// <summary>Log con soglia temporale per chiave: lo stesso messaggio
        /// (categoria + inizio testo) non viene ripetuto piu' spesso di
        /// minIntervalSec. Evita di inondare la logcat Android su controlli
        /// periodici (audit fisica/traffico) mantenendo l'evento tracciabile.</summary>
        private static readonly System.Collections.Generic.Dictionary<string, float>
            _throttle = new System.Collections.Generic.Dictionary<string, float>();

        public static void LogThrottled(string category, string msg,
            float minIntervalSec = 10f)
        {
            string key;
            try
            {
                key = category + "|" +
                    (msg.Length > 48 ? msg.Substring(0, 48) : msg);
            }
            catch (Exception)
            {
                key = category + "|err";
            }
            float now = UnityEngine.Time.time;
            float last;
            if (_throttle.TryGetValue(key, out last) && now - last < minIntervalSec)
                return;
            _throttle[key] = now;
            Huntix.Core.ErrorSurface.SuppressNextAndroid();
            UnityEngine.Debug.Log(msg);
            try { Huntix.Bridge.UnityBridge.LogToAndroid(category, msg); }
            catch (Exception) {}
        }
    }
}
