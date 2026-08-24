using System;
using System.Collections;
using System.IO;
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

        private static string CachePath(string key, string suffix)
        {
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
        // serviti (es. v2 = aggiunti i civici) cosi' i device riscaricano.
        public const int CacheVersion = 2;
        public static string TileGraphCachePath(string key) =>
            CachePath(key, ".v" + CacheVersion + ".graph.json");
        public static string TileGeoCachePath(string key) =>
            CachePath(key, ".v" + CacheVersion + ".geo.json");

        private static IEnumerator Fetch(string cacheFile, string url, Action<string> parse)
        {
            try
            {
                if (File.Exists(cacheFile))
                {
                    string cached = ReadSafe(cacheFile);
                    if (cached != null)
                    {
                        OsmDiag.Log("[TileClient] cache hit " + Path.GetFileName(cacheFile));
                        parse(cached);
                        yield break;
                    }
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[TileClient] cache read " + cacheFile + ": " + e.Message);
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
                OsmDiag.Log("[TileClient] OK " + Path.GetFileName(cacheFile) +
                    " (" + (json.Length / 1024) + "KB)");
                try { File.WriteAllText(cacheFile, json); } catch (IOException e)
                {
                    Debug.LogWarning("[TileClient] cache write: " + e.Message);
                }
                parse(json);
            }
        }

        private static string ReadSafe(string path)
        {
            try { return File.ReadAllText(path); }
            catch (IOException) { return null; }
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
            UnityEngine.Debug.Log(msg);
            try { Huntix.Bridge.UnityBridge.LogToAndroid("OSM", msg); }
            catch (Exception) {}
        }
    }
}
