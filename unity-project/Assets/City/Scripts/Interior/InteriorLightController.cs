using UnityEngine;

namespace City.Interior
{
    /// <summary>
    /// LOD luci interne per edifici IN-PLACE.
    ///
    /// Ogni edificio visitabile ha una luce point interna DISATTIVATA.
    /// Questo componente la accende SOLO quando il giocatore è vicino, così al
    /// massimo 1-2 luci sono vive alla volta (costo runtime costante, adatto
    /// ai telefoni più lenti) anche con molti edifici accessibili. Da lontano
    /// l'interno resta visibile grazie al soffitto EMISSIVO (a costo zero).
    /// </summary>
    public class InteriorLightController : MonoBehaviour
    {
        private const float Radius = 20f;
        private const float CheckInterval = 0.4f;

        private Light light;
        private float timer;

        private void Awake()
        {
            // Trova la luce figlia e parte spenta.
            light = GetComponentInChildren<Light>(true);
            if (light != null) light.enabled = false;
        }

        private void Update()
        {
            if (light == null) { enabled = false; return; }

            timer += Time.deltaTime;
            if (timer < CheckInterval) return;
            timer = 0f;

            Game g = Game.Instance;
            if (g == null || g.player == null) return;

            bool near = Vector3.Distance(transform.position, g.player.transform.position) < Radius;
            light.enabled = near;
        }
    }
}
