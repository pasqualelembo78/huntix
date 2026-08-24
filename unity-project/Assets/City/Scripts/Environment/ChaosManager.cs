using UnityEngine;
using City.OSM;

namespace City.Environment
{
    /// <summary>
    /// Rileva gli impatti volontari del player: se entra a velocita' in un
    /// prop lo calcia (o rompe la vetrina), se travolge un pedone questo
    /// cade e scappa. Guardie anti-falsi positivi: rebase del floating
    /// origin e teletrasporti non contano come impatti.
    /// </summary>
    public class ChaosManager : MonoBehaviour
    {
        public static ChaosManager Instance;

        private const float PropRadiusSq = 1.2f * 1.2f;
        private const float NpcRadiusSq = 0.95f * 0.95f;

        // Camminare (4 m/s) calcia solo i prop piccoli; per abbattere un
        // pedone serve correre davvero, cosi' non ci si multa per sbaglio
        // attraversando la folla.
        private const float NpcKnockSpeed = 5.2f;

        private Transform _player;
        private Vector3 _lastPos;
        private bool _hasLast;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            WorldOrigin.OnRebased += OnRebased;
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
            WorldOrigin.OnRebased -= OnRebased;
        }

        private void OnRebased(Vector3 delta)
        {
            _hasLast = false;   // il rebase sposta tutto: niente falso impatto
        }

        private void Update()
        {
            if (Game.Instance == null || Game.Instance.player == null) return;
            Transform pl = Game.Instance.player.transform;
            if (pl != _player)
            {
                _player = pl;
                _hasLast = false;
            }
            if (!_hasLast)
            {
                _lastPos = pl.position;
                _hasLast = true;
                return;
            }

            Vector3 delta = pl.position - _lastPos;
            delta.y = 0f;
            if (delta.magnitude > 12f)
            {
                // teletrasporto: resetta, non e' un impatto
                _lastPos = pl.position;
                return;
            }
            float dt = Mathf.Max(0.001f, Time.deltaTime);
            float speed = delta.magnitude / dt;
            _lastPos = pl.position;

            if (speed < 3.2f) return;
            TryChaos(pl.position, speed);
        }

        private static void TryChaos(Vector3 pos, float speed)
        {
            for (int i = 0; i < InteractableProp.All.Count; i++)
            {
                var pr = InteractableProp.All[i];
                if (pr == null || pr.Kicked) continue;
                if ((pr.transform.position - pos).sqrMagnitude > PropRadiusSq)
                    continue;
                if (speed < pr.KickSpeedNeeded) return;   // troppo lento

                Vector3 dir = pr.transform.position - pos;
                dir.y = 0f;
                dir.Normalize();

                var w = pr.Window;
                if (w != null && !w.IsBroken)
                    w.Hit(dir);          // prima si rompe la vetrina
                else
                    pr.Kick(dir);        // poi vola il prop
                return;
            }

            var npcs = City.NPC.NPCController.Active;
            for (int i = 0; i < npcs.Count; i++)
            {
                var n = npcs[i];
                if (n == null) continue;
                if ((n.transform.position - pos).sqrMagnitude <= NpcRadiusSq)
                {
                    if (speed >= NpcKnockSpeed)
                    {
                        Vector3 dir = n.transform.position - pos;
                        dir.y = 0f;
                        dir.Normalize();
                        n.KnockDown(dir);
                    }
                    return;
                }
            }
        }
    }
}
