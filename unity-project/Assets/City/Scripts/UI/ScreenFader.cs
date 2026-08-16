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

        public void FadeToBlack(Action done)
        {
            StartFade(0f, 1f, done);
        }

        public void FadeFromBlack(Action done)
        {
            StartFade(1f, 0f, done);
        }

        private void StartFade(float from, float to, Action done)
        {
            if (coroutine != null) StopCoroutine(coroutine);
            coroutine = StartCoroutine(DoFade(from, to, done));
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
