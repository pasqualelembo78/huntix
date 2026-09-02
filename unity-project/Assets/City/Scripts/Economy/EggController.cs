using System;
using UnityEngine;

namespace City.Economy
{
    [RequireComponent(typeof(Collider))]
    public class EggController : MonoBehaviour
    {
        public enum Rarity { Common, Uncommon, Rare, Legendary }
        public enum EggType { Strada, Parco, Bosco, Albero, Edificio, Terra, Acqua, Aria, Sabbia, Fango, Breccia }

        public Rarity rarity;
        public EggType eggType;
        public int value;

        private float bobSpeed = 1.5f;
        private float bobHeight = 0.25f;
        private float rotSpeed = 40f;
        private float glowPulseSpeed = 2f;
        private Vector3 startPos;
        private Renderer[] renderers;
        private float glowBase;

        private static readonly Color CommonColor = new Color(1f, 0.95f, 0.7f);
        private static readonly Color UncommonColor = new Color(0.4f, 0.9f, 0.4f);
        private static readonly Color RareColor = new Color(0.3f, 0.5f, 1f);
        private static readonly Color LegendaryColor = new Color(1f, 0.6f, 0.1f);

        private static readonly Color StradaColor = new Color(0.55f, 0.55f, 0.55f);
        private static readonly Color ParcoColor = new Color(0.3f, 0.8f, 0.3f);
        private static readonly Color BoscoColor = new Color(0.15f, 0.5f, 0.15f);
        private static readonly Color AlberoColor = new Color(0.2f, 0.7f, 0.2f);
        private static readonly Color EdificioColor = new Color(0.7f, 0.5f, 0.3f);
        private static readonly Color TerraColor = new Color(0.6f, 0.45f, 0.25f);
        private static readonly Color AcquaColor = new Color(0.2f, 0.6f, 1f);
        private static readonly Color AriaColor = new Color(0.7f, 0.85f, 1f);
        private static readonly Color SabbiaColor = new Color(0.9f, 0.8f, 0.5f);
        private static readonly Color FangoColor = new Color(0.45f, 0.35f, 0.2f);
        private static readonly Color BrecciaColor = new Color(0.6f, 0.55f, 0.5f);

        private void Awake()
        {
            Collider col = GetComponent<Collider>();
            col.isTrigger = true;
            col.tag = "Untagged";
        }

        public void Init(Vector3 position, Rarity r, EggType t = EggType.Strada)
        {
            if (transform == null) { UnityEngine.Debug.LogError("[EggController.Init] transform == null"); return; }
            rarity = r;
            eggType = t;
            startPos = position;
            transform.position = position;
            value = GetValue(r);

            BuildModel(r, t);
            renderers = GetComponentsInChildren<Renderer>();
            if (renderers != null && renderers.Length > 0 && renderers[0] != null && renderers[0].sharedMaterial != null)
            {
                try { glowBase = renderers[0].sharedMaterial.GetFloat("_GlossMapScale"); }
                catch (System.Exception) { glowBase = 0f; }
            }
        }

        private void Update()
        {
            // Bob up and down
            float newY = startPos.y + Mathf.Sin(Time.time * bobSpeed + startPos.x) * bobHeight;
            Vector3 pos = transform.position;
            pos.y = newY;
            transform.position = pos;

            // Slow rotation
            transform.Rotate(Vector3.up, rotSpeed * Time.deltaTime);

            // Glow pulse
            float pulse = (Mathf.Sin(Time.time * glowPulseSpeed) + 1f) * 0.5f;
            if (renderers != null)
            {
                foreach (var r in renderers)
                {
                    if (r == null) continue;
                    Color c = r.sharedMaterial.GetColor("_BaseColor");
                    float emission = pulse * 0.8f;
                    r.sharedMaterial.SetColor("_EmissionColor", c * emission);
                }
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;

            // Notify game
            if (Game.Instance != null)
                Game.Instance.OnEggCollected(this);

            // Particle burst (simple)
            SpawnBurst();

            Destroy(gameObject);
        }

        private void SpawnBurst()
        {
            int count = 8;
            Color col = GetColor(eggType);
            for (int i = 0; i < count; i++)
            {
                var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                go.name = "EggBurst";
                go.transform.position = transform.position;
                go.transform.localScale = Vector3.one * 0.08f;
                go.GetComponent<Renderer>().sharedMaterial = MakeMat(col);
                Destroy(go.GetComponent<Collider>());

                var rb = go.AddComponent<Rigidbody>();
                rb.useGravity = true;
                rb.velocity = UnityEngine.Random.insideUnitSphere * 3f + Vector3.up * 4f;

                Destroy(go, 0.6f);
            }
        }

        private void BuildModel(Rarity r, EggType t)
        {
            Color col = GetColor(t);

            // Egg body (squashed sphere)
            var egg = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            egg.name = "EggBody";
            egg.transform.SetParent(transform, false);
            egg.transform.localPosition = new Vector3(0f, 0.25f, 0f);
            egg.transform.localScale = new Vector3(0.2f, 0.28f, 0.2f);
            egg.GetComponent<Renderer>().sharedMaterial = MakeEggMat(col);

            // Disable child colliders
            var childCol = egg.GetComponent<Collider>();
            if (childCol != null) childCol.enabled = false;

            // Glow halo (transparent sphere)
            var halo = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            halo.name = "Glow";
            halo.transform.SetParent(transform, false);
            halo.transform.localPosition = new Vector3(0f, 0.25f, 0f);
            halo.transform.localScale = Vector3.one * 0.45f;
            var haloR = halo.GetComponent<Renderer>();
            var haloMat = new Material(Shader.Find("Universal Render Pipeline/Unlit"));
            if (haloMat.shader == null) haloMat = new Material(Shader.Find("Unlit/Color"));
            haloMat.color = new Color(col.r, col.g, col.b, 0.15f);
            haloMat.EnableKeyword("_EMISSION");
            haloMat.SetColor("_EmissionColor", col * 0.5f);
            haloR.sharedMaterial = haloMat;
            haloR.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            haloR.receiveShadows = false;
            Destroy(halo.GetComponent<Collider>());

            // Point light for glow
            var lightGo = new GameObject("EggLight");
            lightGo.transform.SetParent(transform, false);
            lightGo.transform.localPosition = new Vector3(0f, 0.3f, 0f);
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = col;
            light.range = 2f;
            light.intensity = 1.5f;
        }

        private static Color GetColor(EggType t)
        {
            switch (t)
            {
                case EggType.Strada: return StradaColor;
                case EggType.Parco: return ParcoColor;
                case EggType.Bosco: return BoscoColor;
                case EggType.Albero: return AlberoColor;
                case EggType.Edificio: return EdificioColor;
                case EggType.Terra: return TerraColor;
                case EggType.Acqua: return AcquaColor;
                case EggType.Aria: return AriaColor;
                case EggType.Sabbia: return SabbiaColor;
                case EggType.Fango: return FangoColor;
                case EggType.Breccia: return BrecciaColor;
                default: return TerraColor;
            }
        }

        private static Color GetRarityColor(Rarity r)
        {
            switch (r)
            {
                case Rarity.Common: return CommonColor;
                case Rarity.Uncommon: return UncommonColor;
                case Rarity.Rare: return RareColor;
                case Rarity.Legendary: return LegendaryColor;
                default: return CommonColor;
            }
        }

        private static int GetValue(Rarity r)
        {
            switch (r)
            {
                case Rarity.Common: return 2;
                case Rarity.Uncommon: return 5;
                case Rarity.Rare: return 15;
                case Rarity.Legendary: return 50;
                default: return 2;
            }
        }

        private static readonly System.Collections.Generic.Dictionary<Color, Material> matCache
            = new System.Collections.Generic.Dictionary<Color, Material>();

        private static Material MakeMat(Color c)
        {
            if (matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            matCache[c] = m;
            return m;
        }

        private static Material MakeEggMat(Color c)
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            var m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
            {
                m.SetColor("_BaseColor", c);
                m.SetFloat("_Smoothness", 0.9f);
                m.SetFloat("_Metallic", 0.1f);
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", c * 0.3f);
            }
            else
            {
                m.SetColor("_Color", c);
                m.SetFloat("_Glossiness", 0.9f);
                m.SetFloat("_Metallic", 0.1f);
            }
            return m;
        }
    }
}
