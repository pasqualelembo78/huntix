using System;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;

namespace City.UI
{
    public class ScreenFader : MonoBehaviour
    {
        public Image image;
        public float duration = 0.4f;

        private Coroutine coroutine;

        /// <summary>Runner persistente sempre attivo su cui eseguire le
        /// coroutine del fade. StartCoroutine fallisce con l'errore
        /// "Coroutine couldn't be started because the game object ... is
        /// inactive" quando il GO Fader (o un antenato, es. il canvas) non e'
        /// attivo nella gerarchia - cosa che puo' capitare se un fade parte dal
        /// salvataggio "player sotto il mondo" mentre il canvas/splash sta
        /// ancora cambiando stato. Delegando la coroutine a un runner
        /// DontDestroyOnLoad sempre attivo il fade non dipende piu' dallo stato
        /// attivo del Fader.</summary>
        private static ScreenFader _global;
        private static ScreenFaderRunner _runner;
        private static ScreenFaderRunner Runner
        {
            get
            {
                if (_runner == null)
                {
                    var go = new GameObject("ScreenFaderRunner");
                    DontDestroyOnLoad(go);
                    _runner = go.AddComponent<ScreenFaderRunner>();
                }
                return _runner;
            }
        }

        /// <summary>Runner dedicato: ospita le coroutine del fade indipendenti
        /// dallo stato attivo del Fader.</summary>
        private class ScreenFaderRunner : MonoBehaviour { }

        public void FadeToBlack(Action done)
        {
            StartFade(0f, 1f, done);
        }

        public void FadeFromBlack(Action done)
        {
            StartFade(1f, 0f, done);
        }

        /// <summary>Istanza globale persistente (DontDestroyOnLoad) del fader,
        /// autonoma dalle scene: crea un Canvas dedicato full-screen con
        /// immagine nera. Usata per le transizioni dell'aldila' (morte in
        /// citta' -> Inferno e regno -> regno) dove la UI della citta' viene
        /// distrutta dal cambio scena e la UI del gioco non ha propri fader.</summary>
        public static ScreenFader Global
        {
            get
            {
                if (_global != null) return _global;
                var go = new GameObject("ScreenFaderGlobal");
                DontDestroyOnLoad(go);
                var canvas = go.AddComponent<Canvas>();
                canvas.renderMode = RenderMode.ScreenSpaceOverlay;
                canvas.sortingOrder = 30000;
                var img = go.AddComponent<Image>();
                img.raycastTarget = false;
                img.color = new Color(0f, 0f, 0f, 0f);
                _global = go.AddComponent<ScreenFader>();
                _global.image = img;
                return _global;
            }
        }

        public static void FadeToBlackGlobal(Action done) { Global.FadeToBlack(done); }
        public static void FadeFromBlackGlobal(Action done) { Global.FadeFromBlack(done); }

        private void StartFade(float from, float to, Action done)
        {
            // Attiva il Fader nella gerarchia: l'immagine deve essere visibile
            // affinche' il fade abbia effetto (il colore viene aggiornato in
            // coroutine). Non si forza l'attivazione degli antenati: se un
            // canvas padre e' spento il runner sopravvive comunque e il fade
            // resta coerente (il colore si applica appena la gerarchia sale).
            if (!gameObject.activeSelf) gameObject.SetActive(true);
            if (coroutine != null && _runner != null) _runner.StopCoroutine(coroutine);
            coroutine = Runner.StartCoroutine(DoFade(from, to, done));
        }

        private IEnumerator DoFade(float from, float to, Action done)
        {
            float t = 0f;
            Color c = image.color;
            while (t < duration)
            {
                t += Time.deltaTime;
                c.a = Mathf.Lerp(from, to, Mathf.Clamp01(t / duration));
                image.color = c;
                yield return null;
            }
            c.a = to;
            image.color = c;
            done?.Invoke();
        }
    }
}
