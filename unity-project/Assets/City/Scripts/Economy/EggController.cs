using System;
using UnityEngine;

namespace City.Economy
{
    [RequireComponent(typeof(Collider))]
    public class EggController : MonoBehaviour
    {
        public enum Rarity { Common, Uncommon, Rare, Epic, Legendary }
        public enum EggType { Strada, Parco, Bosco, Albero, Edificio, Terra, Acqua, Aria, Sabbia, Fango, Breccia, Lavoro, Consegna }

        public Rarity rarity;
        public EggType eggType;
        public int value;
        public float captureMultiplier = 1f;

        private float bobSpeed = 1.5f;
        private float bobHeight = 0.25f;
        private float rotSpeed = 40f;
        private float glowPulseSpeed = 2f;
        private Vector3 startPos;
        private Renderer[] renderers;
        private float glowBase;

        private static readonly Color CommonColor = new Color(0.0f, 0.8f, 0.53f);
        private static readonly Color UncommonColor = new Color(0.0f, 0.71f, 1f);
        private static readonly Color RareColor = new Color(0.66f, 0.33f, 0.97f);
        // EPIC: scala unica a 5 livelli (Common→Legendary) allineata ad Android
        private static readonly Color EpicColor = new Color(1f, 0.42f, 0.21f);
        private static readonly Color LegendaryColor = new Color(1f, 0.84f, 0.0f);
        private static readonly Color CreamColor = new Color(1f, 0.98f, 0.8f);
        private static readonly Color creamGlow = new Color(1f, 0.95f, 0.6f, 1f);

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
        // uova del sistema lavori (stazioni LAVORI e pacchi consegnati)
        private static readonly Color LavoroColor = new Color(0.2f, 0.75f, 0.55f);
        private static readonly Color ConsegnaColor = new Color(0.9f, 0.65f, 0.25f);

        private void Awake()
        {
            Collider col = GetComponent<Collider>();
            col.isTrigger = true;
            col.tag = "Untagged";
        }

        // distanza entro cui il player puo' tentare la cattura con un tap
        public float captureRange = 5f;
        private bool playerNear;
        private bool captured;

        public bool PlayerNear { get { return playerNear; } }
        public bool Captured { get { return captured; } }

        /// <summary>L'uovo e' una preda da cacciare (non ancora catturato e non
        /// ancora raggiunto): il radar lo segnala per guidare la caccia. Quando
        /// e' vicino (playerNear) diventa gia' visibile/evidenziato, quindi non
        /// serve piu' il radar.</summary>
        public bool PlayerNearCanRadar { get { return !captured && !playerNear; } }

        /// <summary>Il player e' entrato nella zona: l'uovo diventa catturabile
        /// (evidenziato) ma NON si raccoglie da solo: serve il mini-gioco.</summary>
        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            playerNear = true;
            Highlight(true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            playerNear = false;
            Highlight(false);
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

        /// <summary>Mini-gioco terminato con successo: l'uovo viene raccolto.</summary>
        public void OnCaptured()
        {
            if (captured) return;
            captured = true;
            if (Game.Instance != null)
                Game.Instance.OnEggCollected(this);
            SpawnBurst();
            Destroy(gameObject);
        }

        /// <summary>Avvia il mini-gioco di cattura (chiamato dal tap sull'uovo).</summary>
        public void StartCapture()
        {
            if (captured || !playerNear) return;
            captureMultiplier = 1f;
            if (EggCaptureMinigame.Instance == null)
                EggCaptureMinigame.Ensure();
            EggCaptureMinigame.Instance.Begin(this, result =>
            {
                if (result.success) OnCaptured();
                else OnMissed();
            });
        }

        /// <summary>Mancato: l'uovo resta al suo posto (il mini-gioco si riapre).</summary>
        public void OnMissed()
        {
        }

        private void Highlight(bool on)
        {
            if (captured || renderers == null) return;
            foreach (var r in renderers)
            {
                if (r == null) continue;
                var mat = r.sharedMaterial;
                if (mat == null) continue;
                if (on)
                {
                    mat.EnableKeyword("_EMISSION");
                    mat.SetColor("_EmissionColor", GetColor(eggType) * 1.6f);
                }
                else
                {
                    mat.SetColor("_EmissionColor", GetColor(eggType) * 0.3f);
                }
            }
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
            Color rarityCol = GetRarityColor(r);

            // Egg body: modello 3D reale (DAE decimato) tintato per tipo/rarita.
            var model = Resources.Load<GameObject>("Eggs/Models/EGGZ-simplified");
            if (model != null)
            {
                var egg = UnityEngine.Object.Instantiate(model, transform);
                egg.name = "EggBody";
                float height = (r == Rarity.Legendary) ? 0.70f
                             : (r == Rarity.Rare)     ? 0.62f
                             : (r == Rarity.Epic)     ? 0.60f
                             : (r == Rarity.Uncommon) ? 0.56f
                             :                           0.50f;
                // Il DAE e' lungo ~66 unita' (0..65.7); scala per l'altezza voluta.
                float sc = height / 65.73722076f;
                egg.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                egg.transform.localScale = new Vector3(sc, sc, sc);
                var eggMat = MakeEggMat(col);
                var rends = egg.GetComponentsInChildren<Renderer>();
                for (int i = 0; i < rends.Length; i++)
                {
                    rends[i].shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                    rends[i].receiveShadows = false;
                    rends[i].sharedMaterial = eggMat;
                }

                // Rarity ring + dot legati al root (non scalato): coordinate mondo.
                if (r != Rarity.Common)
                {
                    var ring = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    ring.name = "RarityRing";
                    ring.transform.SetParent(transform, false);
                    ring.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                    ring.transform.localScale = new Vector3(0.42f, 0.012f, 0.42f);
                    var ringMat = MakeEggMat(rarityCol);
                    ringMat.SetFloat("_Smoothness", 0.9f);
                    ringMat.SetFloat("_Metallic", 0.8f);
                    ring.GetComponent<Renderer>().sharedMaterial = ringMat;
                    Destroy(ring.GetComponent<Collider>());

                    var dot = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    dot.name = "RarityDot";
                    dot.transform.SetParent(transform, false);
                    dot.transform.localPosition = new Vector3(0.13f, 0.34f, 0.13f);
                    dot.transform.localScale = Vector3.one * 0.04f;
                    dot.GetComponent<Renderer>().sharedMaterial = MakeEggMat(rarityCol);
                    Destroy(dot.GetComponent<Collider>());
                }
            }
            else
            {
                // Fallback: uovo procedurale a sfere (se il modello manca)
                var egg = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                egg.name = "EggBody";
                egg.transform.SetParent(transform, false);
                egg.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                var bodyScale = (r == Rarity.Legendary) ? new Vector3(0.26f, 0.34f, 0.26f)
                              : (r == Rarity.Rare)     ? new Vector3(0.24f, 0.32f, 0.24f)
                              : (r == Rarity.Epic)     ? new Vector3(0.23f, 0.31f, 0.23f)
                              :                        new Vector3(0.20f, 0.28f, 0.20f);
                egg.transform.localScale = bodyScale;
                egg.GetComponent<Renderer>().sharedMaterial = MakeEggMat(col);
                var childCol = egg.GetComponent<Collider>();
                if (childCol != null) childCol.enabled = false;

                if (r != Rarity.Common)
                {
                    var ring = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    ring.name = "RarityRing";
                    ring.transform.SetParent(transform, false);
                    ring.transform.localPosition = new Vector3(0f, 0.25f, 0f);
                    ring.transform.localScale = new Vector3(0.23f, 0.012f, 0.23f);
                    var ringMat = MakeEggMat(rarityCol);
                    ringMat.SetFloat("_Smoothness", 0.9f);
                    ringMat.SetFloat("_Metallic", 0.8f);
                    ring.GetComponent<Renderer>().sharedMaterial = ringMat;
                    Destroy(ring.GetComponent<Collider>());

                    var dot = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    dot.name = "RarityDot";
                    dot.transform.SetParent(transform, false);
                    dot.transform.localPosition = new Vector3(0.07f, 0.34f, 0.07f);
                    dot.transform.localScale = Vector3.one * 0.045f;
                    dot.GetComponent<Renderer>().sharedMaterial = MakeEggMat(rarityCol);
                    Destroy(dot.GetComponent<Collider>());
                }
            }

            // Glow halo (transparent sphere)
            var halo = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            halo.name = "Glow";
            halo.transform.SetParent(transform, false);
            halo.transform.localPosition = new Vector3(0f, 0.25f, 0f);
            float haloScale = (r == Rarity.Legendary) ? 0.60f : 0.45f;
            halo.transform.localScale = Vector3.one * haloScale;
            var haloR = halo.GetComponent<Renderer>();
            // NB: mai costruire Material con Shader.Find() nullo: il costruttore
            // lancia ArgumentNullException("shader") e il fallback successivo non
            // gira mai. Si risolve lo shader PRIMA e si fallisce sulla catena
            // URP/Unlit -> Unlit/Color -> URP/Lit -> Standard; in assoluto
            // fallback si clona il material default del Primitive (mai nullo).
            Shader haloShader = Shader.Find("Universal Render Pipeline/Unlit");
            if (haloShader == null) haloShader = Shader.Find("Unlit/Color");
            if (haloShader == null) haloShader = Shader.Find("Universal Render Pipeline/Lit");
            if (haloShader == null) haloShader = Shader.Find("Standard");
            var haloMat = haloShader != null
                ? new Material(haloShader)
                : new Material(haloR.sharedMaterial);
            float alpha = (r == Rarity.Legendary) ? 0.28f : 0.15f;
            haloMat.color = new Color(col.r, col.g, col.b, alpha);
            haloMat.EnableKeyword("_EMISSION");
            haloMat.SetColor("_EmissionColor", col * 0.5f);
            haloR.sharedMaterial = haloMat;
            haloR.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            haloR.receiveShadows = false;
            Destroy(halo.GetComponent<Collider>());

            // Point light for glow (stronger for higher rarity)
            var lightGo = new GameObject("EggLight");
            lightGo.transform.SetParent(transform, false);
            lightGo.transform.localPosition = new Vector3(0f, 0.3f, 0f);
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = rarityCol;
            light.range = (r == Rarity.Legendary) ? 3f : (r == Rarity.Epic ? 2.6f : 2f);
            light.intensity = (r == Rarity.Rare || r == Rarity.Epic || r == Rarity.Legendary) ? 2.2f : 1.5f;

            // Legendary: extra glow particles (tiny spheres orbiting)
            if (r == Rarity.Legendary || r == Rarity.Epic)
            {
                for (int i = 0; i < 6; i++)
                {
                    var spark = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                    spark.name = "Spark";
                    spark.transform.SetParent(transform, false);
                    float ang = i * 60f * Mathf.Deg2Rad;
                    var sMat = MakeMat(CreamColor);
                    sMat.EnableKeyword("_EMISSION");
                    sMat.SetColor("_EmissionColor", creamGlow);
                    spark.GetComponent<Renderer>().sharedMaterial = sMat;
                    spark.transform.localPosition = new Vector3(
                        Mathf.Cos(ang) * 0.32f, 0.40f, Mathf.Sin(ang) * 0.32f);
                    spark.transform.localScale = Vector3.one * 0.02f;
                    Destroy(spark.GetComponent<Collider>());
                }
            }
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
                case EggType.Lavoro: return LavoroColor;
                case EggType.Consegna: return ConsegnaColor;
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
                case Rarity.Epic: return EpicColor;
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
                case Rarity.Epic: return 30;
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
