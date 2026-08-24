using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.Networking;

namespace City.NPC
{
    /// <summary>
    /// Directory dei personaggi del backend RealLife (:5100/characters).
    /// Ogni cittadino procedurale della citta' viene mappato in modo
    /// deterministico (hash stabile dell'id) su un personaggio reale del
    /// server: cosi' la chat IA risponde con personalita', nome e memoria
    /// che il backend gia' gestisce per quell'id, senza toccare il server.
    /// Lista cachata su disco (persistentDataPath) con fallback offline.
    /// </summary>
    public static class CityCharacterDirectory
    {
        [Serializable]
        public class CharacterDef
        {
            public string id;
            public string name;
            public string role;
            public string category;
            public string avatar;
        }

        [Serializable] private class Wrapper { public CharacterDef[] items; }

        private const string BaseUrl = "http://82.165.218.56:5100";
        private const int MaxChars = 200;

        private static readonly List<CharacterDef> _chars = new List<CharacterDef>();
        private static bool _loaded;
        private static bool _loading;
        private static readonly List<NPCController> _pending = new List<NPCController>();

        // La coroutine di caricamento gira su un host persistente dedicato:
        // se partisse da un NPC verrebbe uccisa dall'unload del chunk che lo
        // contiene, lasciando _loading=true per sempre (directory mai pronta:
        // pedoni senza nome/personaggio e chat IA non collegata).
        private static MonoBehaviour _host;
        private static int _attempts;

        private sealed class LoadHost : MonoBehaviour { }

        private static MonoBehaviour HostGo()
        {
            if (_host == null)
            {
                var go = new GameObject("CityCharacterDirectoryHost");
                UnityEngine.Object.DontDestroyOnLoad(go);
                _host = go.AddComponent<LoadHost>();
            }
            return _host;
        }

        private static string CachePath =>
            Path.Combine(Application.persistentDataPath, "huntix_city_characters.json");

        /// <summary>Personaggio stabile per un npcId, null se directory non pronta.</summary>
        public static CharacterDef ForNpc(string npcId)
        {
            if (!_loaded || _chars.Count == 0 || string.IsNullOrEmpty(npcId)) return null;
            return _chars[StableHash(npcId) % _chars.Count];
        }

        /// <summary>
        /// Collega l'NPC al suo personaggio. Se la directory non e' ancora
        /// caricata l'NPC entra in lista d'attesa e viene collegato appena
        /// possibile (anche dopo distruzione: le ref morte vengono scartate).
        /// </summary>
        public static void Attach(NPCController npc)
        {
            if (npc == null || string.IsNullOrEmpty(npc.NpcId)) return;
            var def = ForNpc(npc.NpcId);
            if (def != null)
            {
                npc.ApplyCharacter(def);
                return;
            }
            if (!_pending.Contains(npc)) _pending.Add(npc);
            EnsureLoading(npc);
        }

        private static void EnsureLoading(NPCController host)
        {
            if (_loaded || _loading) return;
            _loading = true;
            _attempts++;
            HostGo().StartCoroutine(Load(_attempts));
        }

        private static IEnumerator Load(int attempt)
        {
            try
            {
                // 1) cache su disco (istantanea)
                try
                {
                    if (File.Exists(CachePath))
                        Parse(File.ReadAllText(CachePath));
                }
                catch (Exception) { }

                // 2) rete solo se la cache non basta
                if (_chars.Count == 0)
                {
                    using (var req = UnityWebRequest.Get(
                        BaseUrl + "/characters?limit=" + MaxChars))
                    {
                        req.timeout = 20;
                        yield return req.SendWebRequest();
                        if (req.result == UnityWebRequest.Result.Success)
                        {
                            try
                            {
                                Parse(req.downloadHandler.text);
                                File.WriteAllText(CachePath, req.downloadHandler.text);
                            }
                            catch (Exception e)
                            {
                                Debug.LogWarning("[CityChar] parse/cache: " + e.Message);
                            }
                        }
                        else
                        {
                            Debug.LogWarning("[CityChar] download personaggi: " + req.error);
                        }
                    }
                }
            }
            finally
            {
                _loaded = _chars.Count > 0;
                _loading = false;
            }

            if (_loaded)
            {
                for (int i = _pending.Count - 1; i >= 0; i--)
                {
                    var n = _pending[i];
                    if (n == null) { _pending.RemoveAt(i); continue; }
                    var def = ForNpc(n.NpcId);
                    if (def != null) n.ApplyCharacter(def);
                }
                _pending.Clear();
            }
            else if (attempt < 6)
            {
                // Server offline o timeout: riprova piu' tardi invece di
                // rinunciare per l'intera sessione.
                HostGo().StartCoroutine(RetryLater(attempt));
            }
        }

        private static IEnumerator RetryLater(int attempt)
        {
            yield return new WaitForSeconds(20f);
            if (_loaded || _loading) yield break;
            _loading = true;
            HostGo().StartCoroutine(Load(attempt));
        }

        private static void Parse(string json)
        {
            // l'endpoint restituisce un array puro: lo avvolgiamo per JsonUtility
            var w = JsonUtility.FromJson<Wrapper>("{\"items\":" + json + "}");
            if (w?.items == null) return;
            _chars.Clear();
            foreach (var c in w.items)
            {
                if (c == null || string.IsNullOrEmpty(c.id)) continue;
                _chars.Add(c);
            }
        }

        private static int StableHash(string s)
        {
            uint h = 2166136261u;
            for (int i = 0; i < s.Length; i++)
            {
                h ^= s[i];
                h *= 16777619u;
            }
            return (int)(h % int.MaxValue);
        }
    }
}
