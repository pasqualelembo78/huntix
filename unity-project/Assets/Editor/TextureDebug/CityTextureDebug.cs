// Copyright (c) 2026 Huntix. Diagnostico edit-mode per bug striature/edifici OSM.
using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using City.OSM;
using System.Collections.Generic;
using System.Linq;

namespace Huntix.EditorTools
{
    public static class CityTextureDebug
    {
        // 0=default, 1=boost shadow bias, 2=soft shadows highres, 3=no shadows
        public static int Mode = 0;

        [MenuItem("Huntix/Debug/01 City render (default)")]
        public static void RenderDefault() { Mode = 0; RenderCity(); }
        [MenuItem("Huntix/Debug/02 City render (boost bias)")]
        public static void RenderBoost() { Mode = 1; RenderCity(); }
        [MenuItem("Huntix/Debug/03 City render (soft shadow)")]
        public static void RenderSoft() { Mode = 2; RenderCity(); }
        [MenuItem("Huntix/Debug/04 City render (no shadow)")]
        public static void RenderNoShadow() { Mode = 3; RenderCity(); }

        // usato via -executeMethod -citytexmode N (CLI) in batchmode
        public static void Run()
        {
            var args = System.Environment.GetCommandLineArgs();
            for (int i = 0; i < args.Length; i++)
                if (args[i] == "-citytexmode" && i + 1 < args.Length) int.TryParse(args[i + 1], out Mode);
            Debug.Log($"[CityTextureDebug] Run invoked, mode={Mode}");
            RenderCity();
        }

        static readonly Color GrassColor = new Color(0.38f, 0.62f, 0.30f);
        static readonly Color RoadColor = new Color(0.27f, 0.27f, 0.30f);
        static readonly Dictionary<Color, Material> mats = new Dictionary<Color, Material>();
        static Material GetMat(Color c)
        {
            if (mats.TryGetValue(c, out var m)) return m;
            // URP/Lit dynamic lights do not bind under headless EditMode Camera.Render (only the
            // Unlit pass is driven). Use URP/Unlit so the generated city is visible here; the same
            // materials can be switched to URP/Lit to render real-time lights + shadows in Playmode.
            var s = Shader.Find("Universal Render Pipeline/Unlit");
            if (s == null) s = Shader.Find("Universal Render Pipeline/Lit");
            m = new Material(s);
            m.SetColor("_BaseColor", c);
            mats[c] = m; return m;
        }
        class BStyle { public Color Body, Roof, Sign; public bool IsShop; public BStyle(Color a, Color b, Color c, bool d) { Body = a; Roof = b; Sign = c; IsShop = d; } }
        static BStyle GetStyle(string kind, string amenity, string shop)
        {
            string a = amenity ?? "", s = shop ?? "";
            if (s.Length > 0) return new BStyle(new Color(1f,0.97f,0.86f), new Color(0.72f,0.53f,0.04f), new Color(0.12f,0.53f,0.90f), true);
            if (a.Length > 0)
            {
                if (a.Contains("restaurant")||a.Contains("cafe")||a.Contains("bar")||a.Contains("fast_food")||a.Contains("pub")||a.Contains("food"))
                    return new BStyle(new Color(1f,0.89f,0.71f), new Color(0.55f,0f,0f), new Color(0.55f,0f,0f), true);
                return new BStyle(new Color(0.93f,0.93f,0.93f), new Color(0.40f,0.40f,0.40f), new Color(0.30f,0.50f,0.90f), true);
            }
            if (kind == "commercial") return new BStyle(new Color(0.83f,0.77f,0.66f), new Color(0.42f,0.36f,0.27f), new Color(0.60f,0.50f,0.30f), false);
            if (kind == "industrial") return new BStyle(new Color(0.69f,0.69f,0.69f), new Color(0.40f,0.40f,0.40f), new Color(0.60f,0.60f,0.60f), false);
            return new BStyle(new Color(0.91f,0.83f,0.72f), new Color(0.55f,0.27f,0.08f), new Color(0.60f,0.50f,0.30f), false);
        }
        static float RoadWidth(string hw)
        {
            if (string.IsNullOrEmpty(hw)) return 5f;
            switch (hw) {
                case "motorway": return 12f; case "primary": return 10f; case "secondary": return 8f;
                case "tertiary": return 7f; case "residential": return 6f; case "service": return 4f;
                case "footway": return 2f; case "pedestrian": return 3f; case "unclassified": return 6f;
                default: return 5f;
            }
        }
        static Vector3 L2P(double lat, double lon, double clat, double clon)
        {
            double mpdLat = 110540.0;
            double mpdLon = 111320.0 * System.Math.Cos(clat * Mathf.Deg2Rad);
            return new Vector3((float)((lon - clon) * mpdLon), 0f, (float)((lat - clat) * mpdLat));
        }
        static void hide(GameObject g) { g.hideFlags = HideFlags.HideAndDontSave; }
        static void CB(Transform parent, string name, Vector3 center, Vector3 scale, Material mat, bool collider, Quaternion? rot = null)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name; go.transform.SetParent(parent, false);
            go.transform.localPosition = center;
            go.transform.localRotation = rot == null ? Quaternion.identity : rot.Value;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            if (!collider) { var c = go.GetComponent<Collider>(); if (c) c.enabled = false; }
            hide(go);
        }

        static void RenderCity()
        {
            Debug.Log($"[CityTextureDebug] START mode={Mode}");
            URPSetup.Setup();
            var scene = UnityEditor.SceneManagement.EditorSceneManager.NewScene(new UnityEditor.SceneManagement.NewSceneSetup(), UnityEditor.SceneManagement.NewSceneMode.Single);
            UnityEditor.SceneManagement.EditorSceneManager.SetActiveScene(scene);
            QualitySettings.shadowDistance = (Mode == 2) ? 60f : 40f;
            RenderSettings.ambientMode = AmbientMode.Flat;
            RenderSettings.ambientLight = new Color(0.42f, 0.42f, 0.42f);

            var json = File.ReadAllText(Path.Combine(Application.dataPath, "City/EditorData/foggia_envelope.json"));
            var env = JsonUtility.FromJson<OsmCityEnvelope>(json);
            double clat = env.centerLat, clon = env.centerLng;

            var camGO = new GameObject("Cam"); hide(camGO);
            var cam = camGO.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.48f, 0.60f, 0.78f);
            cam.cameraType = CameraType.Game;
            cam.cullingMask = ~0;
            cam.fieldOfView = 45f; cam.nearClipPlane = 0.1f; cam.farClipPlane = 600f;
            cam.transform.position = new Vector3(0f, 5f, -24f);
            cam.transform.rotation = Quaternion.Euler(20f, 0f, 0f);
            var ud = cam.gameObject.AddComponent<UniversalAdditionalCameraData>();
            ud.renderPostProcessing = false;

            var lightGO = new GameObject("Sun");
            var L = lightGO.AddComponent<Light>();
            L.type = LightType.Directional; L.intensity = 1.1f; L.enabled = true;
            // sun shining onto ground tops (forward has +Y)
            L.transform.rotation = Quaternion.Euler(-52f, 165f, 0f);
            L.shadowNearPlane = 1f;
            if (Mode == 3) L.shadows = LightShadows.None;
            else if (Mode == 2) { L.shadows = LightShadows.Soft; L.shadowResolution = LightShadowResolution.VeryHigh; }
            else L.shadows = LightShadows.Hard;
            L.shadowBias = (Mode == 1) ? 3.0f : 1.0f;
            L.shadowNormalBias = (Mode == 1) ? 4.0f : 1.0f;

            var ground = GameObject.CreatePrimitive(PrimitiveType.Quad); hide(ground);
            ground.transform.rotation = Quaternion.Euler(90f, 0f, 0f);
            ground.transform.localScale = new Vector3(600f, 600f, 1f);
            ground.transform.position = new Vector3(0f, -0.02f, 0f);
            ground.GetComponent<Renderer>().sharedMaterial = GetMat(GrassColor);

            if (env.roads != null) foreach (var r in env.roads) {
                if (r.points == null || r.points.Length < 2) continue;
                var p0 = L2P(r.points[0].lat, r.points[0].lng, clat, clon);
                var p1 = L2P(r.points[1].lat, r.points[1].lng, clat, clon);
                float len = Vector3.Distance(p0, p1); if (len < 0.5f) continue;
                float ang = Mathf.Atan2(p1.z - p0.z, p1.x - p0.x);
                var mid = (p0 + p1) * 0.5f + Vector3.up * -0.03f;
                var bx = GameObject.CreatePrimitive(PrimitiveType.Cube); hide(bx);
                bx.transform.position = mid;
                bx.transform.rotation = Quaternion.Euler(0f, ang * Mathf.Rad2Deg, 0f);
                bx.transform.localScale = new Vector3(len, 0.06f, RoadWidth(r.highway));
                bx.GetComponent<Renderer>().sharedMaterial = GetMat(RoadColor);
                var col = bx.GetComponent<Collider>(); if (col) col.enabled = false;
            }

            int built = 0;
            if (env.buildings != null) foreach (var b in env.buildings) {
                float minX=1e9f,maxX=-1e9f,minZ=1e9f,maxZ=-1e9f;
                foreach (var p in b.points) { var v = L2P(p.lat,p.lng,clat,clon); if(v.x<minX)minX=v.x; if(v.x>maxX)maxX=v.x; if(v.z<minZ)minZ=v.z; if(v.z>maxZ)maxZ=v.z; }
                float w = maxX-minX, d = maxZ-minZ;
                if (w<3f||d<3f) continue;
                w=Mathf.Clamp(w,3f,40f); d=Mathf.Clamp(d,3f,40f);
                float h=(float)b.height; if(h<=0.5f)h=8f; h=Mathf.Clamp(h,3f,40f);
                var st=GetStyle(b.kind,b.amenity,b.shop);
                var bgo=new GameObject("Edificio "+b.id); hide(bgo);
                bgo.transform.position = new Vector3(minX+w*0.5f,0f,minZ+d*0.5f);
                CB(bgo.transform,"Corpo",new Vector3(0f,h*0.49f,0f),new Vector3(w*0.98f,h*0.98f,d*0.98f),GetMat(st.Body),true);
                if (h<7f) {
                    float eave=w*0.5f+0.15f; const float slope=0.5f;
                    float slopeAngle=Mathf.Atan(slope)*Mathf.Rad2Deg;
                    float panelLen=Mathf.Sqrt(eave*eave+(eave*slope)*(eave*slope));
                    float midY=h+eave*slope*0.5f;
                    CB(bgo.transform,"Tetto S",new Vector3(0f,midY,eave*0.5f),new Vector3(w+0.4f,0.15f,panelLen*2f),GetMat(st.Roof),false,Quaternion.Euler(slopeAngle,0f,0f));
                    CB(bgo.transform,"Tetto N",new Vector3(0f,midY,-eave*0.5f),new Vector3(w+0.4f,0.15f,panelLen*2f),GetMat(st.Roof),false,Quaternion.Euler(-slopeAngle,0f,0f));
                } else {
                    CB(bgo.transform,"Tetto",new Vector3(0f,h+0.125f,0f),new Vector3(w+0.4f,0.25f,d+0.4f),GetMat(st.Roof),false);
                }
                built++;
            }
             Debug.Log($"[CityTextureDebug] built {built} buildings");


            // aspetta un frame di rendering
            // (edit mode: niente yield)

            var rt = new RenderTexture(1280,720,24,RenderTextureFormat.ARGB32); rt.autoGenerateMips=false;
            var prev=cam.targetTexture; cam.targetTexture=rt;
            cam.pixelRect = new Rect(0,0,rt.width,rt.height);
            cam.Render(); cam.targetTexture=prev;
            RenderTexture.active=rt;
            var tex=new Texture2D(rt.width,rt.height,TextureFormat.RGB24,false);
            tex.ReadPixels(new Rect(0,0,rt.width,rt.height),0,0); tex.Apply();
            Debug.Log($"[CityTextureDebug] sample TL={tex.GetPixel(4,4)} C={tex.GetPixel(rt.width/2,rt.height/2)} BR={tex.GetPixel(rt.width-4,rt.height-4)}");
            RenderTexture.active=null; Object.DestroyImmediate(rt);
            string outPath = $"/tmp/opencover/city_mode{Mode}.png";
            File.WriteAllBytes(outPath,tex.EncodeToPNG());
            float mean=0; var px=tex.GetPixels(); foreach(var p in px){float v=(p.r+p.g+p.b)/3f; mean+=v;} mean/=px.Length;
            int ncolors = px.Distinct().Count();
            Debug.Log($"[CityTextureDebug] saved {outPath} mean={mean:F3} colors={ncolors}");
        }
    }
}
