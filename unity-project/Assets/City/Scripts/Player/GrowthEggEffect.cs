using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// EGG OF GROWTH — Riflesso della caccia alle uova sulla crescita.
    ///
    /// Ai livelli chiave (5, 10, 20, 35, 50) il salto di livello "schiude"
    /// un uovo dorato che appare davanti al personaggio, pulsa e si spegne
    /// in scintille mentre la crescita (morph) viene applicata dal driver XP.
    ///
    /// Visivo 100% procedurale (stessa ricetta di EggController: modello
    /// Resources/Eggs/Models/EGGZ-simplified, URP/Lit, point light, spark):
    /// budget zero, nessun asset nuovo.
    ///
    /// Registrazione (bridge Unity→Android): il GrowthXpDriver invia
    /// "GrowthStateSync"; StoreUnityBridge.onGrowthStateSync registra l'uovo
    /// in inventario + EggWhereLog ("Livello X → crescita"), idempotente per
    /// livello. Qui c'è solo il guscio visivo: divertimento, zero economia.
    ///
    /// Sicurezza: se il modello manca usa una sfera procedurale; in editor
    /// (test batchmode) non si auto-distrugge e i collider vengono rimossi
    /// con DestroyImmediate (mai Destroy fuori play mode).
    /// </summary>
    public class GrowthEggEffect : MonoBehaviour
    {
        /// <summary>Livelli chiave della crescita: qui nascono le uova.</summary>
        public static readonly int[] KeyLevels = { 5, 10, 20, 35, 50 };

        private const string ModelPath = "Eggs/Models/EGGZ-simplified";
        private const float BodyHeight = 0.55f;
        private const float ModelHeight = 65.73722076f;
        private const float Life = 4.0f;
        private const float PopIn = 0.4f;
        private const float BurstAt = 3.2f;

        private static readonly Color GrowthGold = new Color(1f, 0.84f, 0f);
        private static readonly Color Cream = new Color(1f, 0.97f, 0.75f);

        private int _level;
        private float _age;
        private bool _burst;
        private Transform _body;
        private Material _bodyMat;
        private Light _light;
        private float _baseLight;
        private readonly List<Transform> _fragments = new List<Transform>();
        private readonly List<Vector3> _fragmentVel = new List<Vector3>();

        public static bool IsKeyLevel(int level)
        {
            return System.Array.IndexOf(KeyLevels, level) >= 0;
        }

        /// <summary>Livelli chiave attraversati nel passaggio (from, to]
        /// (from &lt;= 0 = nessuno: stato iniziale/sconosciuto).</summary>
        public static List<int> CrossedKeyLevels(int fromLevel, int toLevel)
        {
            var result = new List<int>();
            if (fromLevel <= 0) return result;
            for (int i = 0; i < KeyLevels.Length; i++)
            {
                int k = KeyLevels[i];
                if (k > fromLevel && k <= toLevel) result.Add(k);
            }
            return result;
        }

        /// <summary>Schiusa in air: l'uovo appare davanti al personaggio.
        /// slot = posizione laterale (più schiuse in un colpo solo).</summary>
        public static GrowthEggEffect Spawn(Transform parent, int level, int slot = 0)
        {
            var go = new GameObject("GrowthEgg_Lv" + level);
            if (parent != null)
            {
                go.transform.SetParent(parent, false);
                // Altezza testa del personaggio, poco avanti, sfalsato a destra
                // se in un salto di livello se ne aprono più di una.
                go.transform.localPosition = new Vector3(slot * 0.55f, 1.05f, 0.55f);
            }
            var fx = go.AddComponent<GrowthEggEffect>();
            fx.Build(level);
            OsmDiag.Log("[GrowthEggEffect] uovo di crescita schiuso: livello " + level +
                (slot > 0 ? " (slot " + slot + ")" : ""));
            return fx;
        }

        public int Level { get { return _level; } }

        private void Build(int level)
        {
            _level = level;

            // Scala di luce in base alla chiave (5 = "comune" ... 50 = "legendary").
            int idx = System.Array.IndexOf(KeyLevels, level);
            if (idx < 0) idx = 0;
            float keyT = KeyLevels.Length <= 1 ? 0f :
                (float)idx / (KeyLevels.Length - 1f);

            // Corpo: modello DAE uovo (o sfera procedurale se manca).
            GameObject body = null;
            var model = Resources.Load<GameObject>(ModelPath);
            if (model != null)
            {
                body = Instantiate(model, transform, false);
                body.name = "GrowthEggBody";
                float sc = BodyHeight / ModelHeight;
                body.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                body.transform.localScale = new Vector3(sc, sc, sc);
            }
            else
            {
                body = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                body.name = "GrowthEggBody";
                body.transform.SetParent(transform, false);
                body.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                body.transform.localScale = new Vector3(0.20f, 0.27f, 0.20f);
                RemoveCollider(body);
            }
            _body = body.transform;
            _bodyMat = MakeMat(GrowthGold);
            var bodyRends = body.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < bodyRends.Length; i++)
            {
                if (bodyRends[i] == null) continue;
                bodyRends[i].sharedMaterial = _bodyMat;
                RemoveCollider(bodyRends[i].gameObject);
            }

            // Scintille in orbita (come le uova leggendarie).
            for (int i = 0; i < 6; i++)
            {
                var spark = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                spark.name = "GrowthSpark";
                spark.transform.SetParent(transform, false);
                float ang = i * 60f * Mathf.Deg2Rad;
                var sMat = MakeMat(Cream);
                sMat.EnableKeyword("_EMISSION");
                sMat.SetColor("_EmissionColor", Cream * 1.4f);
                spark.GetComponent<Renderer>().sharedMaterial = sMat;
                spark.transform.localPosition = new Vector3(
                    Mathf.Cos(ang) * 0.32f, 0.40f, Mathf.Sin(ang) * 0.32f);
                spark.transform.localScale = Vector3.one * (0.02f + keyT * 0.012f);
                RemoveCollider(spark);
            }

            // Glow: nessun materiale trasparente => solo light point (URP).
            var lightGo = new GameObject("GrowthEggLight");
            lightGo.transform.SetParent(transform, false);
            lightGo.transform.localPosition = new Vector3(0f, 0.30f, 0f);
            _light = lightGo.AddComponent<Light>();
            _light.type = LightType.Point;
            _light.color = GrowthGold;
            _light.range = 2f + keyT * 1.5f;
            _baseLight = 1.6f + keyT * 1.1f;
            _light.intensity = _baseLight;
        }

        private void Update()
        {
            if (_age >= Life) return;
            _age += Time.deltaTime;
            float t = _age;

            // Pop-in con overshoot morbido + ritiro finale.
            float s = 1f;
            if (t < PopIn)
                s = EaseOutBack(Mathf.Clamp01(t / PopIn));
            else if (t > BurstAt + 0.05f)
            {
                float u = Mathf.Clamp01((t - (BurstAt + 0.05f)) /
                    (Life - BurstAt - 0.05f));
                s = 1f - u;
            }
            transform.localScale = Vector3.one * Mathf.Max(0.0001f, s);

            transform.Rotate(Vector3.up, 40f * Time.deltaTime);

            // Bob leggero del corpo.
            if (_body != null)
            {
                var lp = _body.localPosition;
                lp.y = 0.25f + Mathf.Sin(t * 3f) * 0.05f;
                _body.localPosition = lp;
            }

            // Pulse di luce + emission.
            float pulse = (Mathf.Sin(t * 6f) + 1f) * 0.5f;
            if (_light != null)
                _light.intensity = _baseLight * (0.7f + 0.6f * pulse);
            if (_bodyMat != null)
            {
                _bodyMat.EnableKeyword("_EMISSION");
                _bodyMat.SetColor("_EmissionColor", GrowthGold * (0.15f + 0.55f * pulse));
            }

            if (!_burst && t >= BurstAt)
            {
                _burst = true;
                SpawnBurst();
            }

            // Scintille in volo: integrazione manuale (nessun Rigidbody).
            for (int i = 0; i < _fragments.Count; i++)
            {
                var tr = _fragments[i];
                if (tr == null) continue;
                tr.localPosition += _fragmentVel[i] * Time.deltaTime;
                _fragmentVel[i] *= 1f - 1.2f * Time.deltaTime;
            }

            if (t >= Life) Destroy(gameObject);
        }

        private void SpawnBurst()
        {
            int count = 8;
            for (int i = 0; i < count; i++)
            {
                var frag = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                frag.name = "GrowthBurst";
                frag.transform.SetParent(transform, false);
                frag.transform.localPosition = new Vector3(0f, 0.3f, 0f);
                frag.transform.localScale = Vector3.one * 0.03f;
                var m = MakeMat(Cream);
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", Cream * 1.4f);
                frag.GetComponent<Renderer>().sharedMaterial = m;
                RemoveCollider(frag);

                _fragments.Add(frag.transform);
                float ang = i * (360f / count) * Mathf.Deg2Rad;
                _fragmentVel.Add(new Vector3(Mathf.Cos(ang), 0.6f + (i % 3) * 0.2f,
                    Mathf.Sin(ang)) * 1.8f);
            }
        }

        private static float EaseOutBack(float x)
        {
            const float c1 = 1.70158f;
            const float c3 = c1 + 1f;
            float xm = x - 1f;
            return 1f + c3 * xm * xm * xm + c1 * xm * xm;
        }

        private static Material MakeMat(Color c)
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            var m = new Material(shader);
            if (shader != null && shader.name.StartsWith("Universal Render Pipeline/Lit"))
            {
                m.SetColor("_BaseColor", c);
                m.SetFloat("_Smoothness", 0.9f);
                m.SetFloat("_Metallic", 0.1f);
            }
            else
            {
                m.SetColor("_Color", c);
            }
            return m;
        }

        private static void RemoveCollider(GameObject go)
        {
            if (go == null) return;
            var cols = go.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < cols.Length; i++)
            {
                if (cols[i] == null) continue;
                if (Application.isPlaying) Destroy(cols[i]);
                else DestroyImmediate(cols[i]);
            }
        }
    }
}