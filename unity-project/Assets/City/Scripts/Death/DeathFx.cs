using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace City.Death
{
    /// <summary>
    /// Effetti visivi di morte procedurali (nessun ragdoll fisico): sangue a
    /// terra e gocce, riusando lo stesso pattern dei pedoni travolti
    /// (NPCController.SpawnBlood). Materiali in cache per non allocare.
    /// </summary>
    public static class DeathFx
    {
        private static readonly Dictionary<Color, Material> MatCache =
            new Dictionary<Color, Material>();

        public static Material BloodMat(Color c)
        {
            if (MatCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
            {
                m.SetColor("_BaseColor", c);
                m.SetFloat("_Surface", 1f);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            }
            else
            {
                m.SetColor("_Color", c);
                m.SetFloat("_Mode", 3f);
                m.SetFloat("_ZWrite", 0f);
                m.EnableKeyword("_ALPHABLEND_ON");
            }
            MatCache[c] = m;
            return m;
        }

        /// <summary>Crea una pozza di sangue a terra + spruzzo di gocce.</summary>
        public static void SpawnBlood(Vector3 groundPos, Vector3 fwd)
        {
            SceneHook.Ensure();
            Vector3 ground = groundPos;
            ground.y = 0.02f;
            Vector3 dir = fwd;
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.001f) dir = Vector3.forward;

            int splats = Random.Range(2, 4);
            for (int i = 0; i < splats; i++)
            {
                var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
                q.name = "Sangue";
                Object.Destroy(q.GetComponent<Collider>());
                q.transform.position = ground + dir * Random.Range(-0.35f, 0.45f)
                    + Vector3.Cross(Vector3.up, dir) * Random.Range(-0.25f, 0.25f);
                q.transform.rotation = Quaternion.Euler(90f, Random.Range(0f, 360f), 0f);
                float s = Random.Range(0.4f, 0.7f);
                q.transform.localScale = new Vector3(s, s, 1f);
                var mr = q.GetComponent<Renderer>();
                var mat = BloodMat(new Color(0.5f, 0.02f, 0.02f, 0.9f));
                mr.sharedMaterial = mat;
                Run(FadeBlood(q, mat));
            }

            Vector3 origin = ground + Vector3.up * 0.6f;
            Vector3 back = -dir;
            int drops = Random.Range(10, 16);
            for (int i = 0; i < drops; i++)
            {
                var drop = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                drop.name = "SangueGoccia";
                Object.Destroy(drop.GetComponent<Collider>());
                drop.transform.position = origin;
                float s = Random.Range(0.05f, 0.11f);
                drop.transform.localScale = Vector3.one * s;
                drop.GetComponent<Renderer>().sharedMaterial = BloodMat(Color.red);
                Vector3 v = back * Random.Range(1f, 3.5f)
                    + Vector3.up * Random.Range(0.6f, 2.4f)
                    + Vector3.Cross(Vector3.up, dir) * Random.Range(-0.9f, 0.9f);
                Run(BloodDropFlight(drop, v));
            }
        }

        private static IEnumerator BloodDropFlight(GameObject drop, Vector3 vel)
        {
            float t = 0f;
            float dur = Random.Range(0.5f, 0.8f);
            while (t < dur)
            {
                t += Time.deltaTime;
                vel.y -= 6f * Time.deltaTime;
                drop.transform.position += vel * Time.deltaTime;
                yield return null;
            }
            if (drop != null) Object.Destroy(drop);
        }

        private static IEnumerator FadeBlood(GameObject q, Material mat)
        {
            float t = 0f;
            float dur = 4f;
            bool urp = mat.shader.name.StartsWith("Universal Render Pipeline/Lit");
            while (t < dur)
            {
                t += Time.deltaTime;
                float a = Mathf.Lerp(0.9f, 0f, t / dur);
                if (urp) mat.SetColor("_BaseColor",
                    new Color(0.5f, 0.02f, 0.02f, Mathf.Clamp01(a)));
                else mat.SetColor("_Color",
                    new Color(0.5f, 0.02f, 0.02f, Mathf.Clamp01(a)));
                yield return null;
            }
            if (q != null) Object.Destroy(q);
        }

        private static void Run(IEnumerator routine)
        {
            var host = SceneHook.Instance;
            if (host != null) host.StartDeathCoroutine(routine);
        }
    }

    /// <summary>Ospite di coroutine persistente per gli effettive FX di morte.</summary>
    public class SceneHook : MonoBehaviour
    {
        public static SceneHook Instance;
        private void Awake() { Instance = this; }
        private void OnDestroy() { if (Instance == this) Instance = null; }

        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("DeathFxHost");
            DontDestroyOnLoad(go);
            go.AddComponent<SceneHook>();
        }

        public void StartDeathCoroutine(IEnumerator routine)
        {
            StartCoroutine(routine);
        }
    }
}
