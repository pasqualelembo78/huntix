using UnityEngine;

namespace City.Vehicle
{
    /// <summary>
    /// Fa ruotare le ruote dei modelli Kenney (nodi "wheel-*") in
    /// proporzione alla velocita' sull'asse locale X (l'asse del mozzo).
    /// Attaccare alla radice del veicolo; chiamare Spin(speed) ogni frame
    /// quando il mezzo si muove.
    /// </summary>
    public class WheelSpinner : MonoBehaviour
    {
        public float wheelRadius = 0.34f;
        private Transform[] wheels;

        private void Awake()
        {
            var all = GetComponentsInChildren<Transform>(true);
            var list = new System.Collections.Generic.List<Transform>();
            for (int i = 0; i < all.Length; i++)
            {
                var t = all[i];
                if (t == transform) continue;
                if (t.name.IndexOf("wheel",
                    System.StringComparison.OrdinalIgnoreCase) >= 0)
                    list.Add(t);
            }
            wheels = list.ToArray();
        }

        /// <summary>Ruota le ruote in base alla velocita' lineare (m/s).</summary>
        public void Spin(float linearSpeedMps)
        {
            if (wheels == null || wheels.Length == 0) return;
            float deg = linearSpeedMps / Mathf.Max(0.05f, wheelRadius)
                * 57.29578f * Time.deltaTime;
            if (deg == 0f) return;
            for (int i = 0; i < wheels.Length; i++)
            {
                var w = wheels[i];
                if (w == null) continue;
                Vector3 e = w.localEulerAngles;
                e.x += deg;
                w.localEulerAngles = e;
            }
        }
    }
}
