using UnityEngine;

namespace City.Afterlife
{
    /// <summary>
    /// Piattaforma animata per il regno del Purgatorio (3.4): oscilla su/giu
    /// attorno alla propria posizione base con ampiezza/periodo/fase regolabili.
    /// Se il player le sta sopra viene trascinato assieme (delta applicato al
    /// suo transform), cosi' la salita non "scivola" sotto i piedi con il
    /// CharacterController.
    /// </summary>
    public class MovingPlatform : MonoBehaviour
    {
        public Vector3 basePos;
        public float amplitude = 1.2f;
        public float period = 2.4f;
        public float phase;

        private Vector3 _lastPos;

        private void Start()
        {
            _lastPos = transform.position;
        }

        private void Update()
        {
            if (period <= 0f) return;
            float t = Time.time / period;
            float offY = Mathf.Sin((t + phase) * Mathf.PI * 2f) * amplitude;
            Vector3 newPos = basePos + Vector3.up * offY;
            Vector3 delta = newPos - _lastPos;
            transform.position = newPos;
            _lastPos = newPos;

            // trascina il player se sta sopra questa piattaforma
            var pc = City.Player.PlayerController.Instance;
            if (pc == null) return;
            if (pc.IsFlying) return;
            Vector3 playerPos = pc.transform.position;
            float playerY = playerPos.y;
            float top = newPos.y + Mathf.Max(0.05f, transform.localScale.y * 0.5f);
            bool above = playerY >= top - 0.6f && playerY <= top + 0.8f;
            bool insideX = Mathf.Abs(playerPos.x - newPos.x) <= Mathf.Max(0.5f, transform.localScale.x * 0.5f) + 0.6f;
            bool insideZ = Mathf.Abs(playerPos.z - newPos.z) <= Mathf.Max(0.5f, transform.localScale.z * 0.5f) + 0.6f;
            if (above && insideX && insideZ)
            {
                pc.transform.position += delta;
            }
        }
    }
}