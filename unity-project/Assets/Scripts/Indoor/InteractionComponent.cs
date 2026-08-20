using UnityEngine;

namespace Huntix.Indoor
{
    /// <summary>
    /// Attach to any GameObject that can be interacted with by the player.
    /// The InteractionManager detects these via raycast when the player is nearby.
    /// </summary>
    public class InteractionComponent : MonoBehaviour
    {
        [Header("Interaction")]
        public string interactionId = "";
        public string interactionName = "Interagisci";
        public string action = "collect";  // collect, buy, talk, use, heal
        public string emoji = "👆";

        [Header("Game Effect")]
        public string need = "";       // hunger, thirst, hygiene, fun, energy
        public int gain = 0;           // how much the need increases
        public string itemId = "";     // item added to inventory

        [Header("Visual Feedback")]
        public bool highlightOnProximity = true;
        public float highlightRange = 2.5f;
        public Color highlightColor = new Color(1f, 0.92f, 0.23f, 0.6f);

        private Renderer[] _renderers;
        private Color[][] _originalColors;
        private bool _isHighlighted;

        private void Awake()
        {
            _renderers = GetComponentsInChildren<Renderer>();
            _originalColors = new Color[_renderers.Length][];
            for (int i = 0; i < _renderers.Length; i++)
            {
                var mats = _renderers[i].materials;
                _originalColors[i] = new Color[mats.Length];
                for (int j = 0; j < mats.Length; j++)
                    _originalColors[i][j] = mats[j].color;
            }
        }

        public void SetHighlight(bool on)
        {
            if (_isHighlighted == on) return;
            _isHighlighted = on;

            for (int i = 0; i < _renderers.Length; i++)
            {
                var mats = _renderers[i].materials;
                for (int j = 0; j < mats.Length; j++)
                {
                    if (on)
                        mats[j].color = Color.Lerp(_originalColors[i][j], highlightColor, 0.4f);
                    else
                        mats[j].color = _originalColors[i][j];
                }
            }
        }

        public string ToJson()
        {
            return $"{{\"id\":\"{interactionId}\",\"name\":\"{interactionName}\",\"action\":\"{action}\",\"emoji\":\"{emoji}\",\"need\":\"{need}\",\"gain\":{gain},\"itemId\":\"{itemId}\"}}";
        }
    }
}
