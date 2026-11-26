using System.Collections;
using EasingCore;
using UnityEngine;
using UnityEngine.UI;

namespace City.UI
{
    /// <summary>
    /// Scorrimento morbido additivo per le liste gia' esistenti: non crea, non
    /// sostituisce e non disattiva alcuno ScrollRect. Usa EasingCore (MIT,
    /// distribuito con FancyScrollView) e tempo non scalato perche' i pannelli
    /// di gioco (es. negozio) mettono Time.timeScale a 0.
    /// </summary>
    public static class UiScrollTween
    {
        public static IEnumerator ScrollTo(ScrollRect rect, Vector2 target, float duration)
        {
            if (rect == null) yield break;

            float elapsed = 0f;
            Vector2 from = rect.normalizedPosition;
            var ease = Easing.Get(Ease.OutCubic);
            float seconds = Mathf.Max(0.01f, duration);

            while (elapsed < seconds)
            {
                elapsed += Time.unscaledDeltaTime;
                float t = Mathf.Clamp01(elapsed / seconds);
                rect.normalizedPosition = Vector2.Lerp(from, target, ease(t));
                yield return null;
            }

            rect.normalizedPosition = target;
        }
    }
}
