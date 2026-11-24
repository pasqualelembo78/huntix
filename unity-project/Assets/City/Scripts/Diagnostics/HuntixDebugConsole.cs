// Adattato da SanAndreasUnity — Copyright (c) 2015 James King, MIT.
// Sorgenti: Assets/Scripts/UI/Console.cs e ConsoleLogEventSubscriber.cs del
// repository upstream https://github.com/in0finite/SanAndreasUnity (licenza
// integrale in THIRD_PARTY_NOTICES.md, sezione 1). NON deriva dal fork
// proprietario Grand-Road-Auto e non carica alcun dato/asset GTA.
// Modifiche Huntix: rimossi PauseMenuWindow, UGameCore.Utilities e IMGUI
// complesso; coda thread-safe con lock, pannello IMGUI locale, filtri e
// comandi collegati a PerfProbe; attivazione opzionale con PlayerPrefs, bridge
// Android bidirezionale, telemetria e riflesso uova. Nessun dato lascia il
// dispositivo: la console e' solo un viewer dei log gia' presenti in sessione.
using System;
using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Diagnostics
{
    /// <summary>Console QA affiancata: non sostituisce SessionTelemetry ne' il
    /// viewer Android; offre solo una vista locale dei log per collaudare il
    /// gioco sul device. Di default e' chiusa.</summary>
    public class HuntixDebugConsole : MonoBehaviour
    {
        private const string PrefsKey = "huntix_qa_console";
        private const int MaxLogs = 200;
        private const int VisibleRows = 14;

        public static HuntixDebugConsole Instance { get; private set; }
        public static bool Enabled { get; private set; }

        private struct Entry
        {
            public int count;
            public string message;
            public string stackTrace;
            public LogType type;
        }

        private readonly Queue<Entry> _queued = new Queue<Entry>();
        private readonly List<Entry> _logs = new List<Entry>();
        private readonly object _gate = new object();
        private bool _visible;
        private bool _collapsed;
        private bool _showDetails;
        private bool _showErrors = true;
        private bool _showWarnings = true;
        private bool _showLogs = true;
        private string _input = "";
        private int _selected = -1;

        public static void Initialize()
        {
            Enabled = PlayerPrefs.GetInt(PrefsKey, 0) == 1;
        }

        public static HuntixDebugConsole Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("HuntixDebugConsole");
            DontDestroyOnLoad(go);
            var console = go.AddComponent<HuntixDebugConsole>();
            // Restore passivo: la preferenza puo' essere On, ma la finestra non
            // deve comparire da sola all'avvio (nessun cambio di default UI).
            console.SetVisible(false);
            return console;
        }

        public static void SetEnabled(bool on, bool notify)
        {
            Enabled = on;
            PlayerPrefs.SetInt(PrefsKey, on ? 1 : 0);
            PlayerPrefs.Save();

            if (!notify) return;
            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            string json = "{\"enabled\":" + (on ? "true" : "false") +
                ",\"tsMt\":" + ts + "}";
            try { UnityBridge.SendMessageToAndroid("QaConsoleChanged", json); }
            catch (System.Exception e)
            {
                City.OSM.OsmDiag.Log("[ConsoleQA] QaConsoleChanged failed: " + e.Message);
            }
            if (SessionTelemetry.Instance != null)
                SessionTelemetry.Instance.Mark("console QA=%s", on ? "on" : "off");

            if (on)
            {
                Ensure().SetVisible(true);
                // Riflesso della caccia alle uova: la prima apertura della
                // console lascia un uovo accanto al player, con posizione e
                // luogo comunicati a Android da Game.GrantFeatureEgg.
                if (City.Game.Instance != null) City.Game.Instance.OnQaConsoleEnabled();
                if (City.UI.UIManager.Instance != null)
                    City.UI.UIManager.Instance.ShowToast("Console QA aperta: uovo denominato!");
            }
            else if (Instance != null) Instance.SetVisible(false);
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            Application.logMessageReceivedThreaded += OnLogThreaded;
        }

        private void OnDestroy()
        {
            Application.logMessageReceivedThreaded -= OnLogThreaded;
            if (Instance == this) Instance = null;
        }

        private void Update()
        {
            Entry item;
            int drained = 0;
            while (drained++ < 64)
            {
                lock (_gate)
                {
                    if (_queued.Count == 0) break;
                    item = _queued.Dequeue();
                }
                ProcessEntry(item);
            }
        }

        private void OnLogThreaded(string message, string stackTrace, LogType type)
        {
            lock (_gate)
            {
                _queued.Enqueue(new Entry
                {
                    count = 1, message = message ?? "", stackTrace = stackTrace ?? "", type = type
                });
            }
        }

        private void AddLocal(string message)
        {
            lock (_gate)
            {
                _queued.Enqueue(new Entry
                {
                    count = 1, message = message ?? "", stackTrace = "", type = LogType.Log
                });
            }
        }

        private void ProcessEntry(Entry item)
        {
            if (_logs.Count > 0)
            {
                Entry last = _logs[_logs.Count - 1];
                if (last.type == item.type && last.message == item.message &&
                    last.stackTrace == item.stackTrace)
                {
                    last.count++;
                    _logs[_logs.Count - 1] = last;
                    return;
                }
            }
            _logs.Add(item);
            if (_logs.Count > MaxLogs) _logs.RemoveRange(0, _logs.Count - MaxLogs);
        }

        public void SetVisible(bool visible)
        {
            _visible = visible;
        }

        private bool IsVisible(LogType type)
        {
            if (type == LogType.Error || type == LogType.Exception || type == LogType.Assert)
                return _showErrors;
            if (type == LogType.Warning) return _showWarnings;
            return _showLogs;
        }

        private void OnGUI()
        {
            if (!Enabled || !_visible) return;

            float x = 16f, y = 16f;
            float w = Mathf.Min(920f, Screen.width - 32f);
            float h = Mathf.Min(640f, Screen.height - 32f);
            if (w < 320f || h < 240f) return;

            GUI.color = new Color(0.04f, 0.05f, 0.08f, 0.94f);
            GUI.Box(new Rect(x, y, w, h), "");
            GUI.color = Color.white;
            GUI.Label(new Rect(x + 14f, y + 10f, w - 28f, 26f),
                "CONSOLE QA HUNTIX — log locali (nessun invio di dati)");

            float rowY = y + 42f;
            _showErrors = GUI.Toggle(new Rect(x + 14f, rowY, 110f, 26f), _showErrors, "Errori");
            _showWarnings = GUI.Toggle(new Rect(x + 130f, rowY, 130f, 26f), _showWarnings, "Avvisi");
            _showLogs = GUI.Toggle(new Rect(x + 266f, rowY, 100f, 26f), _showLogs, "Log");
            _collapsed = GUI.Toggle(new Rect(x + 376f, rowY, 150f, 26f), _collapsed, "Raggruppa");
            _showDetails = GUI.Toggle(new Rect(x + 532f, rowY, 150f, 26f), _showDetails, "Dettagli");
            if (GUI.Button(new Rect(x + w - 120f, rowY, 100f, 26f), "Pulisci"))
            {
                _logs.Clear();
                _selected = -1;
            }

            rowY += 34f;
            float listH = h - 170f;
            GUI.Box(new Rect(x + 14f, rowY, w - 28f, listH), "");
            int shown = 0;
            for (int i = _logs.Count - 1; i >= 0 && shown < VisibleRows; i--)
            {
                Entry entry = _logs[i];
                if (!IsVisible(entry.type)) continue;
                GUI.contentColor = entry.type == LogType.Error || entry.type == LogType.Exception ||
                    entry.type == LogType.Assert ? new Color(1f, 0.45f, 0.45f, 1f) :
                    entry.type == LogType.Warning ? new Color(1f, 0.9f, 0.4f, 1f) : Color.white;
                string label = (_collapsed && entry.count > 1 ? "[" + entry.count + "] " : "") +
                    Truncate(entry.message);
                if (GUI.Button(new Rect(x + 22f, rowY + 6f + shown * 24f, w - 44f, 22f), label))
                    _selected = _selected == i ? -1 : i;
                shown++;
            }
            GUI.contentColor = Color.white;

            if (_showDetails && _selected >= 0 && _selected < _logs.Count)
            {
                Entry sel = _logs[_selected];
                GUI.Label(new Rect(x + 22f, rowY + 6f + VisibleRows * 24f, w - 44f, 22f),
                    Truncate(sel.stackTrace));
            }

            float cmdY = y + h - 104f;
            _input = GUI.TextField(new Rect(x + 14f, cmdY, w - 230f, 32f), _input);
            if (GUI.Button(new Rect(x + w - 208f, cmdY, 90f, 32f), "Esegui")) Submit();
            if (GUI.Button(new Rect(x + w - 110f, cmdY, 90f, 32f), "Chiudi")) SetVisible(false);
            GUI.Label(new Rect(x + 14f, cmdY + 38f, w - 28f, 24f),
                "help = comandi, prof = tempi PerfProbe. Nessun comando modifica il mondo.");
        }

        private void Submit()
        {
            string command = _input == null ? "" : _input.Trim();
            _input = "";
            if (command.Length == 0) return;
            AddLocal("> " + command);
            HuntixCommandManager.Result result;
            using (PerfProbe.Measure("console"))
                result = HuntixCommandManager.Instance.Process(command, true);
            AddLocal(result == null || string.IsNullOrEmpty(result.response)
                ? "OK" : result.response);
        }

        private static string Truncate(string text)
        {
            if (string.IsNullOrEmpty(text)) return "";
            const int max = 160;
            string oneLine = text.Replace("\n", " ").Replace("\r", " ");
            return oneLine.Length <= max ? oneLine : oneLine.Substring(0, max) + "...";
        }
    }
}
