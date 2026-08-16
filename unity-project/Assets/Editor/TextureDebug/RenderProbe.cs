using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using System.Linq;

public static class RenderProbe
{
    [MenuItem("Huntix/Debug/Probe: render un cube")]
    public static void Run()
    {
        try
        {
            Debug.Log("[Probe] s1 start");
            var urp = AssetDatabase.LoadAssetAtPath<RenderPipelineAsset>("Assets/Settings/URP_Pipeline.asset");
            if (urp != null) { GraphicsSettings.renderPipelineAsset = urp; Debug.Log("[Probe] urp set"); }
            else Debug.Log("[Probe] NO URP ASSET");

            var r = GameObject.CreatePrimitive(PrimitiveType.Cube);
            r.transform.position = new Vector3(0, 0, 2);
            r.transform.localScale = new Vector3(2, 3, 1);
            var ms = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            Debug.Log("[Probe] shader found: " + (Shader.Find("Universal Render Pipeline/Lit") != null));
            ms.SetColor("_BaseColor", new Color(0.93f, 0.93f, 0.93f));
            r.GetComponent<Renderer>().sharedMaterial = ms;
            Debug.Log("[Probe] cube created");

            var camGO = new GameObject("Cam");
            var cam = camGO.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.48f, 0.6f, 0.78f);
            cam.fieldOfView = 45; cam.nearClipPlane = 0.1f; cam.farClipPlane = 20;
            cam.transform.position = new Vector3(0, 1, -5);
            cam.transform.LookAt(r.transform);
            cam.gameObject.AddComponent<UnityEngine.Rendering.Universal.UniversalAdditionalCameraData>();
            Debug.Log("[Probe] camera created");

            // render to RT
            var rt = new RenderTexture(320, 180, 16, RenderTextureFormat.ARGB32);
            rt.autoGenerateMips = false;
            cam.targetTexture = rt;
            Debug.Log("[Probe] rendering...");
            cam.Render();
            Debug.Log("[Probe] render done, readpixels...");
            cam.targetTexture = null;
            RenderTexture.active = rt;
            var tex = new Texture2D(rt.width, rt.height, TextureFormat.RGB24, false);
            tex.ReadPixels(new Rect(0, 0, rt.width, rt.height), 0, 0); tex.Apply();
            Debug.Log("[Probe] mean=" + tex.GetPixels().Select(p => (float)(p.r + p.g + p.b) / 3).Average());
            RenderTexture.active = null;
            File.WriteAllBytes("/tmp/opencover/probe.png", tex.EncodeToPNG());
            Debug.Log("[Probe] PNG written");
        }
        catch (System.Exception e)
        {
            Debug.LogError("[Probe] EXC: " + e);
        }
    }
}
