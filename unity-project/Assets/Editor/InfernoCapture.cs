using System.Collections;
using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Probe di validazione per il minigioco Inferno: apre InfernoScene ed entra
    /// in Play Mode, lascia un paio di secondi alla builds (avatar Remy +
    /// InfernusDecorator con billboard/fiamme/luci), fotografa la Main Camera
    /// e scrive un log con lo stato dei decori.
    /// Uso: -executeMethod Huntix.EditorTools.InfernoCapture.Capture
    /// </summary>
    public static class InfernoCapture
    {
        public static void Capture()
        {
            EditorApplication.playModeStateChanged += OnPlayModeChanged;
            var scene = EditorSceneManager.OpenScene(
                "Assets/City/Scenes/InfernoScene.unity", OpenSceneMode.Single);
            EditorApplication.isPlaying = true;
        }

        private static void OnPlayModeChanged(PlayModeStateChange state)
        {
            if (state != PlayModeStateChange.EnteredPlayMode) return;
            EditorApplication.playModeStateChanged -= OnPlayModeChanged;
            new GameObject("__InfernoCaptureHelper__").AddComponent<InfernoCaptureHelper>();
        }
    }

    public class InfernoCaptureHelper : MonoBehaviour
    {
        IEnumerator Start()
        {
            for (int i = 0; i < 90; i++) yield return null; // ~1.5s di play

            StringBuilder sb = new StringBuilder();
            sb.AppendLine("== INFERNO CAPTURE ==");

            var cam = Camera.main;
            sb.AppendLine("camera=" + (cam != null ? cam.name + " active=" + cam.gameObject.activeInHierarchy : "NULL"));

            var dec = FindObjectOfType<FloorIsLava.InfernusDecorator>();
            sb.AppendLine("decorator=" + (dec != null ? dec.name : "NULL"));
            if (dec != null)
            {
                int props = 0;
                var billboards = FindObjectsOfType<FloorIsLava.GoBillboard>();
                foreach (var b in billboards) props++;
                sb.AppendLine("  billboards=" + props);
                sb.AppendLine("  childCount=" + dec.transform.childCount);
                int flames = 0, lights = 0;
                foreach (Transform c in dec.transform)
                {
                    if (c.gameObject.name == "flame") flames++;
                    if (c.gameObject.name != "flame light" && c.GetComponent<Light>() != null) lights++;
                    if (c.gameObject.name == "flame light") lights++;
                }
                sb.AppendLine("  flames=" + flames + " lights=" + lights);
            }

            var avatar = FindObjectOfType<FloorIsLava.MiaCityAvatar>();
            sb.AppendLine("miaCityAvatar=" + (avatar != null ? "present height=" + avatar.targetHeight : "NULL"));
            if (avatar != null && avatar.transform.childCount > 0)
            {
                var child = avatar.transform.GetChild(0);
                sb.AppendLine("  avatarChild=" + child.name +
                    " animator=" + (child.GetComponent<Animator>() != null ? "yes" : "no") +
                    " scale=" + child.localScale + " pos=" + child.localPosition);
            }

            if (cam != null)
            {
                RenderTexture rt = new RenderTexture(1280, 720, 24, RenderTextureFormat.Default);
                var prev = cam.targetTexture;
                cam.targetTexture = rt;
                cam.Render();
                RenderTexture.active = rt;
                Texture2D tex = new Texture2D(rt.width, rt.height, TextureFormat.RGB24, false);
                tex.ReadPixels(new Rect(0, 0, rt.width, rt.height), 0, 0);
                tex.Apply();
                cam.targetTexture = prev;
                RenderTexture.active = null;
                string path = "/tmp/opencode/inferno_play_" + Application.productName.Replace(" ", "") + ".png";
                File.WriteAllBytes(path, tex.EncodeToPNG());
                sb.AppendLine("saved " + path);
            }

            File.WriteAllText("/root/giochi/huntix/unitylic/probe_inferno_capture.log", sb.ToString());
            Debug.Log("[InfernoCapture]\n" + sb);

            yield return null;
            EditorApplication.isPlaying = false;
            EditorApplication.Exit(0);
        }
    }
}