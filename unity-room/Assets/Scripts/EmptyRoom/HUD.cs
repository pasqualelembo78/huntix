using UnityEngine;

namespace EmptyRoom
{
    // HUD minimale basato su IMGUI (built-in, nessuna dipendenza da UGUI).
    // Mostra messaggi temporanei (es. selezione oggetti).
    public class HUD : MonoBehaviour
    {
        public static HUD Instance { get; private set; }

        private string _msg = "";
        private float _hideAt;

        private void Awake() => Instance = this;

        public void Show(string msg)
        {
            _msg = msg;
            _hideAt = Time.time + 2.5f;
        }

        private void OnGUI()
        {
            if (Time.time > _hideAt) return;
            var style = new GUIStyle(GUI.skin.label)
            {
                fontSize = 28,
                alignment = TextAnchor.UpperCenter,
                normal = { textColor = Color.white }
            };
            GUI.Label(new Rect(0f, 20f, Screen.width, 40f), _msg, style);
        }
    }
}
