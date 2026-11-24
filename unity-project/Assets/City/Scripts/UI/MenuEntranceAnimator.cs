// Adattato da Zone Zero — Copyright (c) 2026 Mishal Bhasim, MIT.
// Sorgente: Assets/_Project/Scripts/UI/MainMenu/MainMenuAnimator.cs del
// repository https://github.com/mishal-bhasim/Zone-Zero (licenza MIT integrale
// in THIRD_PARTY_NOTICES.md, sezione 10).
// Modifiche Huntix: rimosso TMPro (inutilizzato) e il pulse infinito del
// pulsante; target generico invece dei tre riferimenti fissi; attivazione
// opzionale con PlayerPrefs/bridge Android; misura PerfProbe e ripristino
// esplicito degli alpha quando il pannello viene chiuso.
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    /// <summary>
    /// Ingresso a scorrimento/fade del pannello menu, derivato dal pattern di
    /// Zone Zero (MIT). Resta inerte finche' Enabled e' false: il pannello e la
    /// HUD esistenti non vengono sostituiti ne' modificati nel default.
    /// </summary>
    public class MenuEntranceAnimator : MonoBehaviour
    {
        private const string PrefsKey = "huntix_menu_anim";

        [SerializeField] private float fadeTime = 0.35f;
        [SerializeField] private float stagger = 0.06f;
        [SerializeField] private float slidePixels = 26f;

        private static bool _enabled;
        private RectTransform[] _targets;
        private Coroutine _sequence;

        public static bool Enabled { get { return _enabled; } }

        /// <summary>Lettura passiva dello stato salvato da Android: nessun
        /// evento di ritorno e nessun uovo su un ripristino.</summary>
        public static void Initialize()
        {
            _enabled = PlayerPrefs.GetInt(PrefsKey, 0) == 1;
        }

        public static void SetEnabled(bool on, bool notify)
        {
            _enabled = on;
            PlayerPrefs.SetInt(PrefsKey, on ? 1 : 0);
            PlayerPrefs.Save();
            if (!notify) return;

            long ts = System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            string json = "{\"enabled\":" + (on ? "true" : "false") +
                ",\"tsMt\":" + ts + "}";
            try { UnityBridge.SendMessageToAndroid("MenuAnimationChanged", json); }
            catch (System.Exception e)
            {
                OsmDiag.Log("[MenuAnim] MenuAnimationChanged failed: " + e.Message);
            }
            if (City.Diagnostics.SessionTelemetry.Instance != null)
                City.Diagnostics.SessionTelemetry.Instance.Mark("menu anim=%s",
                    on ? "zone-zero" : "huntix");
            // Riflesso della caccia alle uova: il primo passaggio all'animazione
            // lascia un uovo accanto al player, con luogo e coordinate nel bridge.
            if (on && Game.Instance != null) Game.Instance.OnMenuAnimationEnabled();
        }

        /// <summary>Anima le righe "M_*" di un pannello gia' costruito. Non crea
        /// nuovi elementi UI e non tocca le altre HUD.</summary>
        public void Play(RectTransform panel)
        {
            if (!_enabled || panel == null) return;
            if (_sequence != null) StopCoroutine(_sequence);

            var rows = new List<RectTransform>();
            for (int i = 0; i < panel.childCount; i++)
            {
                var child = panel.GetChild(i) as RectTransform;
                if (child != null && child.name.StartsWith("M_", System.StringComparison.Ordinal))
                    rows.Add(child);
            }
            _targets = rows.ToArray();

            using (City.Diagnostics.PerfProbe.Measure("menu"))
            {
                foreach (var rt in _targets)
                {
                    var cg = Group(rt);
                    if (cg != null) cg.alpha = 0f;
                }
            }
            _sequence = StartCoroutine(Entrance());
        }

        private IEnumerator Entrance()
        {
            float duration = Mathf.Max(0.05f, fadeTime);
            float gap = Mathf.Max(0f, stagger);
            foreach (var rt in _targets)
            {
                if (rt == null) continue;
                var cg = Group(rt);
                if (cg == null) continue;

                Vector2 end = rt.anchoredPosition;
                Vector2 start = end + new Vector2(-Mathf.Max(0f, slidePixels), 0f);
                rt.anchoredPosition = start;
                float t = 0f;
                while (t < duration)
                {
                    t += Time.unscaledDeltaTime;
                    float k = Mathf.Clamp01(t / duration);
                    float smooth = k * k * (3f - 2f * k); // smoothstep (Zone Zero)
                    cg.alpha = smooth;
                    rt.anchoredPosition = Vector2.Lerp(start, end, smooth);
                    yield return null;
                }
                cg.alpha = 1f;
                rt.anchoredPosition = end;
                if (gap > 0f) yield return new WaitForSecondsRealtime(gap);
            }
            _sequence = null;
        }

        private static CanvasGroup Group(RectTransform rt)
        {
            if (rt == null) return null;
            var cg = rt.GetComponent<CanvasGroup>();
            if (cg == null) cg = rt.gameObject.AddComponent<CanvasGroup>();
            return cg;
        }

        private void OnDisable()
        {
            if (_sequence != null) StopCoroutine(_sequence);
            _sequence = null;
            if (_targets == null) return;
            // Evita righe invisibili se l'utente chiude il menu a meta' animazione.
            foreach (var rt in _targets)
            {
                var cg = Group(rt);
                if (cg != null) cg.alpha = 1f;
            }
        }
    }
}
