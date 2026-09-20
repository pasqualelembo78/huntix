using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;

namespace FloorIsLava
{
    public static class InfernoAtmosphere
    {
        private const string InfernoScene = "InfernoScene";

        private static readonly Color EmberSky     = new Color(0.12f, 0.02f, 0.02f);
        private static readonly Color EmberFog     = new Color(0.12f, 0.02f, 0.02f);
        private static readonly Color FireLight    = new Color(1.00f, 0.40f, 0.15f);
        private static readonly Color EmberAmbient = new Color(0.22f, 0.06f, 0.05f);

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void AutoHook()
        {
            SceneManager.sceneLoaded += OnSceneLoaded;
        }

        private static void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name != InfernoScene) return;
            Apply();
        }

        private static void Apply()
        {
            Camera cam = Object.FindObjectOfType<Camera>();
            PlayerController ball = Object.FindObjectOfType<PlayerController>();
            Vector3 anchor = ball != null ? ball.transform.position : (cam != null ? cam.transform.position + cam.transform.forward * 20f : Vector3.zero);

            if (cam != null)
            {
                cam.clearFlags = CameraClearFlags.SolidColor;
                cam.backgroundColor = EmberSky;
            }

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = EmberFog;
            RenderSettings.fogStartDistance = 6f;
            RenderSettings.fogEndDistance = 45f;
            RenderSettings.ambientLight = EmberAmbient;

            ApplyDirectionalFireLight();
            ApplyPostProcessing();
            ApplyAvatarLight(ball);
            ApplyEmbers(anchor);
            ApplyLavaSky(cam);
        }

        private static void ApplyDirectionalFireLight()
        {
            Light[] lights = Object.FindObjectsOfType<Light>();
            foreach (Light l in lights)
            {
                if (l.type == LightType.Directional)
                {
                    l.color = FireLight;
                    break;
                }
            }
        }

        private static void ApplyPostProcessing()
        {
            try
            {
                var vgo = new GameObject("InfernoPostProcess");
                var vol = vgo.AddComponent<Volume>();
                vol.isGlobal = true;
                vol.priority = 100f;
                var profile = ScriptableObject.CreateInstance<VolumeProfile>();
                vol.sharedProfile = profile;

                var bloom = profile.Add<Bloom>();
                bloom.threshold.Override(1.1f);
                bloom.intensity.Override(0.9f);
                bloom.scatter.Override(0.65f);
                bloom.tint.Override(new Color(1f, 0.85f, 0.7f, 1f));

                var vignette = profile.Add<Vignette>();
                vignette.intensity.Override(0.34f);
                vignette.smoothness.Override(0.3f);
                vignette.color.Override(new Color(0.06f, 0.0f, 0.0f));

                var col = profile.Add<ColorAdjustments>();
                col.saturation.Override(12f);
                col.contrast.Override(8f);
            }
            catch (System.Exception) { }
        }

        private static void ApplyAvatarLight(PlayerController ball)
        {
            if (ball == null) return;
            try
            {
                var lgo = new GameObject("InfernoAvatarLight");
                lgo.transform.SetParent(ball.transform, false);
                lgo.transform.localPosition = new Vector3(0f, 1.2f, 0f);
                var l = lgo.AddComponent<Light>();
                l.type = LightType.Point;
                l.color = new Color(1f, 0.55f, 0.18f);
                l.intensity = 1.6f;
                l.range = 8f;
            }
            catch (System.Exception) { }
        }

        private static void ApplyEmbers(Vector3 center)
        {
            try
            {
                var ego = new GameObject("InfernoEmbers");
                ego.transform.position = center + new Vector3(0f, -3f, 0f);
                var ps = ego.AddComponent<ParticleSystem>();

                var main = ps.main;
                main.duration = 999f;
                main.loop = true;
                main.startLifetime = new ParticleSystem.MinMaxCurve(2.5f, 4.5f);
                main.startSpeed = new ParticleSystem.MinMaxCurve(0.45f, 0.9f);
                main.startSize = new ParticleSystem.MinMaxCurve(0.06f, 0.22f);
                main.startColor = new ParticleSystem.MinMaxGradient(
                    new Color(1f, 0.7f, 0.25f),
                    new Color(0.9f, 0.18f, 0.06f));
                main.gravityModifier = -0.45f;
                main.simulationSpace = ParticleSystemSimulationSpace.World;
                main.maxParticles = 400;

                var emission = ps.emission;
                emission.rateOverTime = 30f;

                var shape = ps.shape;
                shape.shapeType = ParticleSystemShapeType.Box;
                shape.scale = new Vector3(30f, 1.5f, 22f);

                var colLife = ps.colorOverLifetime;
                colLife.enabled = true;
                Gradient g = new Gradient();
                g.SetKeys(new[] { new GradientColorKey(Color.white, 0f), new GradientColorKey(Color.white, 1f) },
                          new[] { new GradientAlphaKey(0f, 0f), new GradientAlphaKey(1f, 0.2f), new GradientAlphaKey(0f, 1f) });
                colLife.color = new ParticleSystem.MinMaxGradient(g);

                var rnd = ps.GetComponent<ParticleSystemRenderer>();
                Shader sh = Shader.Find("Universal Render Pipeline/Particles/Unlit");
                if (sh != null)
                {
                    var mat = new Material(sh);
                    mat.enableInstancing = true;
                    rnd.material = mat;
                }
                rnd.renderMode = ParticleSystemRenderMode.Billboard;
            }
            catch (System.Exception) { }
        }

        private static void ApplyLavaSky(Camera cam)
        {
            if (cam == null) return;
            try
            {
                Renderer[] rends = Object.FindObjectsOfType<Renderer>();
                Material lavaMat = null;
                foreach (Renderer r in rends)
                {
                    if (r == null || r.sharedMaterial == null) continue;
                    if (r.sharedMaterial.name.Contains("Lava"))
                    {
                        lavaMat = r.sharedMaterial;
                        break;
                    }
                }
                if (lavaMat == null) return;

                var mat = Object.Instantiate(lavaMat);
                Texture2D baseTex = mat.GetTexture("_BaseMap") as Texture2D;

                var skyGo = new GameObject("InfernoLavaSky");
                skyGo.transform.SetParent(cam.transform, false);
                float far = cam.farClipPlane;
                skyGo.transform.localPosition = new Vector3(0f, -14f, far * 0.88f);
                skyGo.transform.localRotation = Quaternion.Euler(0f, 180f, 0f);
                skyGo.transform.localScale = new Vector3(far * 2.6f, far * 1.8f, 1f);

                var mf = skyGo.AddComponent<MeshFilter>();
                mf.mesh = CreateQuad();
                var mr = skyGo.AddComponent<MeshRenderer>();
                mr.material = mat;
                mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                mr.receiveShadows = false;

                var anim = skyGo.AddComponent<GlobalTextureAnimator>();
                anim.materialToAnimate = mat;
                anim.animationSecondsInterval = 0.18f;
                anim.cyclingTextures = new System.Collections.Generic.List<Texture> { baseTex };

                if (baseTex != null)
                {
                    Texture2D variant = MakeTintedVariant(baseTex);
                    if (variant != null) anim.cyclingTextures.Add(variant);
                }
            }
            catch (System.Exception) { }
        }

        private static Texture2D MakeTintedVariant(Texture2D src)
        {
            try
            {
                Color[] px = src.GetPixels();
                Color[] tint = new Color[px.Length];
                for (int i = 0; i < px.Length; i++)
                {
                    float r = px[i].r, g = px[i].g, b = px[i].b;
                    tint[i] = new Color(r * 0.80f, g * 0.50f, b * 0.16f, px[i].a);
                }
                var v = new Texture2D(src.width, src.height, src.format, false);
                v.SetPixels(tint);
                v.Apply(true);
                return v;
            }
            catch (System.Exception) { return null; }
        }

        private static Mesh CreateQuad()
        {
            Mesh m = new Mesh();
            m.name = "InfernoSkyQuad";
            m.vertices = new[] {
                new Vector3(-0.5f, -0.5f, 0f),
                new Vector3( 0.5f, -0.5f, 0f),
                new Vector3( 0.5f,  0.5f, 0f),
                new Vector3(-0.5f,  0.5f, 0f)
            };
            m.uv = new[] { new Vector2(0,0), new Vector2(1,0), new Vector2(1,1), new Vector2(0,1) };
            m.triangles = new[] { 0,2,1, 0,3,2 };
            return m;
        }
    }
}
