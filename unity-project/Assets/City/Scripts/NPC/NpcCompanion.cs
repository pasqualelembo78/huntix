// Pattern architetturale da SanAndreasUnity — Copyright (c) 2015 James King, MIT.
// Sorgenti consultate: Assets/Scripts/Behaviours/PedAI/FollowState.cs e
// PedAI.cs (Recruit) dell'upstream https://github.com/in0finite/SanAndreasUnity
// (licenza integrale in THIRD_PARTY_NOTICES.md). NON deriva dal fork
// proprietario Grand-Road-Auto.
// Riscritto da zero con tipi Huntix: nessun dato GTA, Mirror, UGameCore,
// NodeReader o arma. Aggiunte preferenza opzionale, bridge Android
// bidirezionale, riflesso uova con posizione e misure PerfProbe/telemetria.
using UnityEngine;
using City.OSM;
using Huntix.Bridge;

namespace City.NPC
{
    /// <summary>
    /// Compagno NPC affiancato: un pedone di Miacitta' puo' seguire il giocatore
    /// dopo aver ricevuto un uovo. Resta inerte finche' Enabled e' false: non
    /// modifica il reclutamento, le missioni, la folla o i default esistenti.
    /// </summary>
    public class NpcCompanion : MonoBehaviour
    {
        private const string PrefsKey = "huntix_npc_companion";
        private const float SpotInterval = 18f;
        private const int MaxSpotsPerSession = 3;
        private const float SpotRange = 12f;

        public static NpcCompanion Instance { get; private set; }
        public static bool Enabled { get; private set; }

        private NPCController _companion;
        private string _companionName = "";
        private string _companionId = "";
        private float _nextSpotAt;
        private int _spots;

        public string CompanionName { get { return _companionName; } }
        public string CompanionId { get { return _companionId; } }
        public bool HasCompanion { get { return _companion != null; } }

        /// <summary>Lettura passiva dello stato salvato da Android: nessun uovo
        /// e nessun evento di ritorno su un ripristino.</summary>
        public static void Initialize()
        {
            Enabled = PlayerPrefs.GetInt(PrefsKey, 0) == 1;
        }

        public static NpcCompanion Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("NpcCompanion");
            DontDestroyOnLoad(go);
            return go.AddComponent<NpcCompanion>();
        }

        public static void SetEnabled(bool on, bool notify)
        {
            Enabled = on;
            PlayerPrefs.SetInt(PrefsKey, on ? 1 : 0);
            PlayerPrefs.Save();

            if (!on && Instance != null) Instance.Dismiss(false);
            if (!notify) return;

            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            string json = "{\"enabled\":" + (on ? "true" : "false") +
                ",\"name\":\"" + Escape(Instance != null ? Instance._companionName : "") +
                "\",\"tsMt\":" + ts + "}";
            Send("NpcCompanionChanged", json);
            Mark("compagno={0}", on ? "on" : "off");
        }

        /// <summary>Ripristino da Android: la scelta torna a Unity, ma nessun
        /// compagno viene generato automaticamente e nessun uovo viene dato.
        /// Il pedone viene riconosciuto alla prossima interazione.</summary>
        public static void RestoreFromAndroid(string name, string npcId)
        {
            Enabled = true;
            PlayerPrefs.SetInt(PrefsKey, 1);
            PlayerPrefs.Save();
            if (Instance == null) return;
            Instance._companionName = name ?? "";
            Instance._companionId = npcId ?? "";
            Mark("compagno ripristinato={0}", Instance._companionName);
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        private void Update()
        {
            if (!Enabled || _companion == null) return;
            if (_companion == null || !_companion.gameObject.activeInHierarchy)
            {
                // Il chunk ha rimosso il pedone: il compagno viene congedato
                // senza cancellare la preferenza ne' le uova gia' raccolte.
                _companion = null;
                _companionName = "";
                _companionId = "";
                return;
            }

            Game game = Game.Instance;
            if (game == null || game.player == null) return;
            using (City.Diagnostics.PerfProbe.Measure("companion"))
                _companion.FollowTarget = game.player.transform.position;

            if (Time.unscaledTime < _nextSpotAt || _spots >= MaxSpotsPerSession) return;
            if (game.IsDriving || game.IsInInterior) return;
            if (Vector3.Distance(_companion.transform.position,
                    game.player.transform.position) > SpotRange) return;

            _nextSpotAt = Time.unscaledTime + SpotInterval;
            _spots++;
            SpotEgg(_companion.transform.position);
        }

        /// <summary>Recluta il pedone indicato dall'azione contestuale. Restituisce
        /// false senza effetti se il sistema e' disattivato o c'e' gia' un
        /// compagno: nessun comportamento esistente viene sovrascritto.</summary>
        public bool TryRecruit(NPCController npc)
        {
            if (!Enabled || npc == null || _companion != null) return false;
            Game game = Game.Instance;
            if (game == null || game.player == null) return false;

            _companion = npc;
            _companionName = string.IsNullOrEmpty(npc.DisplayName)
                ? "compagno" : npc.DisplayName;
            _companionId = npc.NpcId ?? "";
            npc.FollowTarget = game.player.transform.position;
            npc.FollowMode = true;
            _nextSpotAt = Time.unscaledTime + SpotInterval;

            Vector3 at = npc.transform.position + Vector3.right * 1.2f + Vector3.up * 0.4f;
            string place = "città→pedone→compagno→" + _companionName;
            game.GrantCompanionEgg("companion-recruit",
                "Uovo del compagno", at, place);

            string lat = "0", lng = "0";
            if (WorldOrigin.Initialized)
            {
                var g = WorldOrigin.ToGeo(at);
                lat = g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            Send("CompanionRecruited", "{\"npcId\":\"" + Escape(_companionId) +
                "\",\"name\":\"" + Escape(_companionName) +
                "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"place\":\"" + Escape(place) + "\",\"tsMt\":" + ts + "}");
            Mark("compagno reclutato={0}", _companionName);

            if (game.ui != null)
                game.ui.ShowToast(_companionName + " ti segue. Ha lasciato un uovo!");
            return true;
        }

        /// <summary>Il compagno segnala un uovo scovato durante il cammino.
        /// L'uovo e' reale (EggController) e passa dalla cattura canonica:
        /// Android riceve sia l'evento di avvistamento sia FeatureEggHidden.</summary>
        private void SpotEgg(Vector3 at)
        {
            Game game = Game.Instance;
            if (game == null) return;
            string place = "città→compagno→avvistamento→" + _companionName;
            game.GrantCompanionEgg("companion-spot-" + _spots,
                "Uovo segnalato dal compagno", at + Vector3.up * 0.2f, place);

            string lat = "0", lng = "0";
            if (WorldOrigin.Initialized)
            {
                var g = WorldOrigin.ToGeo(at);
                lat = g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            Send("CompanionEggSpotted", "{\"npcId\":\"" + Escape(_companionId) +
                "\",\"name\":\"" + Escape(_companionName) +
                "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"place\":\"" + Escape(place) + "\",\"tsMt\":" + ts + "}");
            Mark("compagno avvista uovo #{0}", _spots);
        }

        /// <summary>Congeda il compagno ripristinando il modo pedone originario.
        /// Le uova gia' piazzate e la preferenza restano intatte.</summary>
        public void Dismiss(bool notify)
        {
            if (_companion != null)
            {
                _companion.FollowMode = false;
                _companion = null;
            }
            _companionName = "";
            _companionId = "";
            _spots = 0;
            if (!notify) return;
            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            Send("CompanionRecruited", "{\"npcId\":\"\",\"name\":\"\",\"tsMt\":" + ts + "}");
            Mark("compagno congedato");
        }

        private static void Send(string eventName, string json)
        {
            try { UnityBridge.SendMessageToAndroid(eventName, json); }
            catch (System.Exception e)
            {
                OsmDiag.Log("[Compagno] " + eventName + " failed: " + e.Message);
            }
        }

        private static void Mark(string format, params object[] args)
        {
            if (City.Diagnostics.SessionTelemetry.Instance == null) return;
            City.Diagnostics.SessionTelemetry.Instance.Mark(format, args);
        }

        private static string Escape(string text)
        {
            if (string.IsNullOrEmpty(text)) return "";
            return text.Replace("\\", "\\\\").Replace("\"", "\\\"")
                .Replace("\n", " ").Replace("\r", " ");
        }
    }
}
