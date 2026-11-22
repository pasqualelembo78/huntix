using System.IO;
using UnityEditor;
using UnityEngine;
using City.Player;

namespace City.Editor
{
    /// <summary>Verifica temporanea della catena verticale del player Remy.
    /// Solo lettura dei prefab: crea una scena di prova in memoria, stampa le
    /// misure geometriche e salva un PNG con i marcatori della suola vera.
    /// Richiamabile con -executeMethod City.Editor.PlayerSoleAudit.Run.</summary>
    public static class PlayerSoleAudit
    {
        private const string OutPng =
            "/root/giochi/huntix/docs/verifica_suola_player.png";

        public static void Run()
        {
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(
                "Assets/Resources/PlayerHero.prefab");
            if (prefab == null)
            {
                Debug.LogError("[SoleAudit] prefab PlayerHero mancante");
                return;
            }

            var go = Object.Instantiate(prefab);
            go.name = "SoleAudit";
            go.transform.position = Vector3.zero;
            go.transform.rotation = Quaternion.identity;
            go.transform.localScale = Vector3.one;

            float modelMin, modelMax, boundsMin;
            MeasureModel(go, out modelMin, out modelMax, out boundsMin);
            Debug.Log("[SoleAudit] MODELLO vertY=" + F(modelMin) + ".." + F(modelMax) +
                " boundsMin=" + F(boundsMin) +
                " ERRORE_BOUNDS=" + F(modelMin - boundsMin));

            // Scala usata a runtime da PlayerHeroRig.LayFootOnAnchor.
            float scale = 1.8f / (modelMax - boundsMin + (modelMax - modelMin) * 0f + 0.0f);
            // La scala reale e' targetHeight / boundsH ; boundsH qui e' ricavato
            // dai bounds globali (non solo dal minimo).
            Bounds all = new Bounds();
            bool first = true;
            foreach (var smr in go.GetComponentsInChildren<SkinnedMeshRenderer>(true))
            {
                if (smr == null || !smr.gameObject.activeInHierarchy) continue;
                if (first) { all = smr.bounds; first = false; }
                else all.Encapsulate(smr.bounds);
            }
            scale = 1.8f / all.size.y;

            go.transform.localScale = Vector3.one * scale;
            var probe = SoleGeometry.Ensure(go);
            float soleBelowAnkle = probe.SoleBelowAnkle;
            float hipsToSole = probe.HipsToSole;

            Debug.Log("[SoleAudit] RUNTIME scala=" + F(scale) +
                " spessoreScarpa=" + F(soleBelowAnkle) +
                " bacinoSuola=" + F(hipsToSole) +
                " portataGamba=" + F(hipsToSole - soleBelowAnkle));

            float ground = 0f;
            float ankleTarget = ground + soleBelowAnkle - 0.03f;
            float hipsTarget = ground + hipsToSole - 0.03f;
            Debug.Log("[SoleAudit] OBIETTIVO caviglia=" + F(ankleTarget) +
                " bacino=" + F(hipsTarget) +
                " distanzaRichiesta=" + F(hipsTarget - ankleTarget) +
                " portata=" + F(hipsToSole - soleBelowAnkle) +
                " (scarto=" + F((hipsTarget - ankleTarget) -
                    (hipsToSole - soleBelowAnkle)) + ")");

            // POSA GEOMETRICA: sposta il rig in modo che la suola reale cada a
            // ground-0,03 (SoleTouchDrop).QUESTA e' la posa che i due sistemi
            // IK devono raggiungere a runtime. In batch/Edit mode OnAnimatorIK
            // NON viene chiamato (verificato: 0 call su 30 anim.Update), quindi
            // non si puo' simulare qui l'applicazione IK: la si verifica sul
            // device con [SoleCheck] suolo/trueSole. Qui riproduciamo solo la
            // geometria target, che e' cio' che il fix rende raggiungibile.
            float soleNow;
            SoleGeometry.SoleY(go.transform, out soleNow);
            go.transform.position += Vector3.up * (ground - 0.03f - soleNow);

            float posedSole;
            SoleGeometry.SoleY(go.transform, out posedSole);
            float posedBounds;
            MeasureBoundsMin(go, out posedBounds);
            Debug.Log("[SoleAudit] POSA suolaReale=" + F(posedSole) +
                " (target=" + F(ground - 0.03f) + ")" +
                " boundsMin=" + F(posedBounds) +
                " (i bounds restano " + F(posedSole - posedBounds) +
                " SOTTO la suola: per questo il vecchio delta diceva 0)");
            Debug.Log("[SoleAudit] NOTA: applicazione IK (bodyPosition + caviglie)" +
                " verificabile solo a runtime sul device; qui OnAnimatorIK non scatta.");

            Render(go, ground, posedSole, posedBounds);
            Object.DestroyImmediate(go);
        }

        private static void MeasureModel(GameObject go, out float min,
            out float max, out float boundsMin)
        {
            min = float.MaxValue; max = float.MinValue;
            foreach (var smr in go.GetComponentsInChildren<SkinnedMeshRenderer>(true))
            {
                if (smr == null || !smr.gameObject.activeInHierarchy) continue;
                var mesh = smr.sharedMesh;
                if (mesh == null) continue;
                var verts = mesh.vertices;
                var w = mesh.boneWeights;
                var bones = smr.bones;
                var bind = mesh.bindposes;
                for (int i = 0; i < verts.Length; i++)
                {
                    float y = SkinY(verts[i], w[i], bones, bind);
                    if (y < min) min = y;
                    if (y > max) max = y;
                }
            }
            MeasureBoundsMin(go, out boundsMin);
        }

        private static void MeasureBoundsMin(GameObject go, out float min)
        {
            min = float.MaxValue;
            foreach (var smr in go.GetComponentsInChildren<SkinnedMeshRenderer>(true))
            {
                if (smr == null || !smr.gameObject.activeInHierarchy) continue;
                if (smr.bounds.min.y < min) min = smr.bounds.min.y;
            }
        }

        private static float SkinY(Vector3 v, BoneWeight w, Transform[] bones,
            Matrix4x4[] bind)
        {
            Vector3 p = Vector3.zero;
            float s = 0f;
            Add(ref p, ref s, v, w.weight0, w.boneIndex0, bones, bind);
            Add(ref p, ref s, v, w.weight1, w.boneIndex1, bones, bind);
            Add(ref p, ref s, v, w.weight2, w.boneIndex2, bones, bind);
            Add(ref p, ref s, v, w.weight3, w.boneIndex3, bones, bind);
            return s > 0.0001f ? p.y / s : v.y;
        }

        private static void Add(ref Vector3 p, ref float s, Vector3 v, float w,
            int i, Transform[] bones, Matrix4x4[] bind)
        {
            if (w <= 0.0001f || i < 0 || i >= bones.Length || i >= bind.Length ||
                bones[i] == null) return;
            p += bones[i].localToWorldMatrix.MultiplyPoint3x4(
                bind[i].MultiplyPoint3x4(v)) * w;
            s += w;
        }

        private static void Render(GameObject hero, float ground,
            float soleY, float boundsY)
        {
            var plane = GameObject.CreatePrimitive(PrimitiveType.Plane);
            plane.name = "Suolo";
            plane.transform.position = new Vector3(0f, ground, 0f);
            plane.transform.localScale = new Vector3(0.5f, 1f, 0.5f);

            var camGo = new GameObject("Camera");
            var cam = camGo.AddComponent<Camera>();
            camGo.transform.position = new Vector3(2.6f, 1.15f, -2.9f);
            camGo.transform.LookAt(new Vector3(0f, 0.85f, 0f));
            cam.fieldOfView = 42f;
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.72f, 0.78f, 0.86f);

            var lightGo = new GameObject("Luce");
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.intensity = 1.5f;
            lightGo.transform.rotation = Quaternion.Euler(45f, -35f, 0f);

            Marker("VERDE suola reale", new Vector3(0f, soleY, 0.12f), Color.green, 0.045f);
            Marker("GIALLO bounds SMR (vecchia sonda)",
                new Vector3(0.22f, boundsY, 0.12f), Color.yellow, 0.045f);
            Marker("ROSSO suolo", new Vector3(-0.22f, ground, 0.12f), Color.red, 0.045f);

            var anim = hero.GetComponentInChildren<Animator>(true);
            if (anim != null)
            {
                var lf = anim.GetBoneTransform(HumanBodyBones.LeftFoot);
                if (lf != null)
                    Marker("CYAN caviglia", lf.position, Color.cyan, 0.035f);
                var hips = anim.GetBoneTransform(HumanBodyBones.Hips);
                if (hips != null)
                    Marker("MAGENTA bacino", hips.position, Color.magenta, 0.035f);
            }

            var rt = new RenderTexture(900, 900, 24);
            cam.targetTexture = rt;
            cam.Render();
            RenderTexture.active = rt;
            var tex = new Texture2D(900, 900, TextureFormat.RGB24, false);
            tex.ReadPixels(new Rect(0, 0, 900, 900), 0, 0);
            tex.Apply();
            File.WriteAllBytes(OutPng, tex.EncodeToPNG());
            RenderTexture.active = null;
            cam.targetTexture = null;
            Object.DestroyImmediate(tex);
            Object.DestroyImmediate(rt);
            Debug.Log("[SoleAudit] PNG scritto: " + OutPng);
        }

        private static void Marker(string name, Vector3 pos, Color color, float size)
        {
            var m = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            m.name = name;
            m.transform.position = pos;
            m.transform.localScale = Vector3.one * size;
            var mat = new Material(Shader.Find("Universal Render Pipeline/Unlit") != null
                ? Shader.Find("Universal Render Pipeline/Unlit")
                : Shader.Find("Standard"));
            mat.color = color;
            m.GetComponent<Renderer>().sharedMaterial = mat;
        }

        private static string F(float v)
        {
            return v.ToString("F4", System.Globalization.CultureInfo.InvariantCulture);
        }
    }
}
