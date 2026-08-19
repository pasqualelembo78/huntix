using System.Collections.Generic;
using System.IO;
using System.Text;
using UnityEngine;
using UnityEngine.Scripting;
using Huntix.Bridge;

namespace Huntix.Core
{
    [Preserve]
    public class ErrorSurface : MonoBehaviour
    {
        static readonly List<string> Log = new List<string>();
        static readonly List<string> Errors = new List<string>();
        [System.ThreadStatic] static bool _inLogHandler;
        static volatile bool _quitting;

        [Preserve]
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void Install()
        {
            var go = new GameObject("__ErrorSurface__");
            DontDestroyOnLoad(go);
            go.AddComponent<ErrorSurface>();
        }

        void Awake()
        {
            Application.logMessageReceived += OnLogMessage;
            Application.quitting += () => _quitting = true;
            string header = $"[ErrorSurface] avviato. persistentDataPath={Application.persistentDataPath}";
            Log.Add(header);
            Debug.Log(header);
        }

        void OnDestroy()
        {
            _quitting = true;
            Application.logMessageReceived -= OnLogMessage;
        }

        void OnLogMessage(string condition, string stackTrace, LogType type)
        {
            if (_inLogHandler || _quitting) return;
            _inLogHandler = true;
            try
            {
                bool isProblem = type == LogType.Error || type == LogType.Exception || type == LogType.Assert;
                string line = "[" + type + "] " + condition + "\n" + stackTrace;

                Log.Add(line);
                if (Log.Count > 200) Log.RemoveRange(0, Log.Count - 200);
                if (isProblem)
                {
                    Errors.Add(condition);
                    if (Errors.Count > 20) Errors.RemoveAt(0);
                }

                string tag = "Unity";
                if (condition.Length > 0 && condition[0] == '[')
                {
                    int close = condition.IndexOf(']');
                    if (close > 1) tag = condition.Substring(1, close - 1);
                }
                UnityBridge.LogToAndroid(tag, "[" + type + "] " + (condition.Length > 300 ? condition.Substring(0, 300) + "..." : condition));
                if (isProblem && stackTrace.Length > 0)
                    UnityBridge.LogToAndroid(tag, "STACK: " + (stackTrace.Length > 500 ? stackTrace.Substring(0, 500) + "..." : stackTrace));

                try
                {
                    File.AppendAllText(Path.Combine(Application.persistentDataPath, "huntix-log.txt"),
                        System.DateTime.Now.ToString("HH:mm:ss") + " " + line + "\n");
                }
                catch (System.Exception)
                {
                }
            }
            finally
            {
                _inLogHandler = false;
            }
        }

        void OnGUI()
        {
            if (Errors.Count == 0) return;

            var style = new GUIStyle(UnityEngine.GUI.skin.label)
            {
                alignment = TextAnchor.UpperLeft,
                fontSize = 26,
                wordWrap = true
            };
            style.normal.textColor = Color.red;

            int n = Mathf.Min(6, Errors.Count);
            var sb = new StringBuilder();
            for (int i = Errors.Count - n; i < Errors.Count; i++)
            {
                sb.AppendLine(Errors[i]);
                sb.AppendLine();
            }

            float w = Mathf.Min(Screen.width, 900);
            float h = Mathf.Min(Screen.height, 520);
            Rect box = new Rect((Screen.width - w) / 2f, 40, w, h);
            UnityEngine.GUI.Box(box, GUIContent.none);
            UnityEngine.GUI.Label(new Rect(box.x + 12, box.y + 8, box.width - 24, box.height - 16), sb.ToString(), style);
        }
    }
}
