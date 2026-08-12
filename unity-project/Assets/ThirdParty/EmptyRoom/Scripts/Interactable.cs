using UnityEngine;

namespace EmptyRoom
{
    // Oggetto interagibile via click del mouse. Evidenzia (emissive) al tocco e
    // mostra un messaggio nell'HUD.
    [RequireComponent(typeof(Collider))]
    public class Interactable : MonoBehaviour
    {
        public string label = "Oggetto";
        private bool _highlighted;

        public void OnInteract()
        {
            _highlighted = !_highlighted;
            foreach (var r in GetComponentsInChildren<Renderer>())
            {
                foreach (var m in r.materials)
                {
                    m.EnableKeyword("_EMISSION");
                    m.SetColor("_EmissionColor", _highlighted ? new Color(0.2f, 1f, 0.3f) : Color.black);
                }
            }
            HUD.Instance?.Show($"{label} {(_highlighted ? "selezionato" : "rilasciato")}");
        }
    }
}
