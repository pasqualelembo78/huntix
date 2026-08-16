using System.Collections;
using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Huntix.EditorTools
{
    public static class CitySceneCapture
    {
        public static void CaptureScreenshot()
        {
            var scene = EditorSceneManager.OpenScene("Assets/City/Scenes/City.unity", OpenSceneMode.Single);
            var go = new GameObject("__CaptureHelper__");
            go.AddComponent<CaptureHelper>();
            EditorApplication.isPlaying = true;
        }
    }

    public class CaptureHelper : MonoBehaviour
    {
        IEnumerator Start()
        {
            for (int i = 0; i < 12; i++) yield return null;

            var cam = Camera.main;
            var light = FindObjectOfType<Light>();
            var player = GameObject.Find("Player");
            Debug.Log("[Capture] camera=" + (cam != null ? cam.gameObject.name + " active=" + cam.gameObject.activeInHierarchy + " pos=" + cam.transform.position + " rot=" + cam.transform.eulerAngles : "NULL")
                + " | light=" + (light != null ? light.type + " pos=" + light.transform.position + " range=" + light.range : "NULL")
                + " | player=" + (player != null ? player.transform.position.ToString() : "NULL"));

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
                string path = "/tmp/opencode/city_play_" + Application.productName.Replace(" ", "") + ".png";
                File.WriteAllBytes(path, tex.EncodeToPNG());
                Debug.Log("[Capture] saved " + path);
            }

            yield return null;
            EditorApplication.isPlaying = false;
            EditorApplication.Exit(0);
        }
    }
}
