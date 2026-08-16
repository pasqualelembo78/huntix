using UnityEngine;

namespace EmptyRoom
{
    // Verifica runtime: Resources.Load funziona? Logga su Player.log, poi esce.
    public class ResourcesRuntimeTest : MonoBehaviour
    {
        string result = "Loading...";

        private void Start()
        {
            var t = Resources.Load<TextAsset>("Sample/sample");
            result = (t != null ? "OK len=" + t.text.Length : "NULL");
            Debug.Log($"[ResourcesTest] Load(\"Sample/sample\") = {result}");
        }

        private void OnGUI()
        {
            var style = new GUIStyle(UnityEngine.GUI.skin.label)
            {
                alignment = TextAnchor.MiddleCenter,
                fontSize = 64
            };
            style.normal.textColor = result.StartsWith("OK") ? Color.green : (result == "Loading..." ? Color.yellow : Color.red);
            float w = Mathf.Min(Screen.width, 900);
            float h = 240;
            GUI.Label(new Rect((Screen.width - w) / 2, (Screen.height - h) / 2, w, h),
                "Resources.Load(\"Sample/sample\")\n" + result, style);
        }
    }
}
