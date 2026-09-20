using UnityEngine;

namespace FloorIsLava
{
    /// <summary>
    /// Sostituisce la visuale dell'anima del minigioco Inferno con il
    /// personaggio moderno del giocatore (prefab "PlayerHero" = Remy Mixamo,
    /// potenziato dal RemyKitSetup: Animator humanoid + controller
    /// PlayerLocomotion con BlendTree Speed e stato Jump).
    ///
    /// Il corpo fisico dell'anima (Rigidbody + collider + PlayerController)
    /// resta ABILITATO e invisibile: gameplay invariato, visual = MiaCitta'.
    /// Se manca qualcosa si disattiva senza rompere il minigioco.
    /// </summary>
    [DisallowMultipleComponent]
    public class MiaCityAvatar : MonoBehaviour
    {
        [Tooltip("Altezza finale approssimativa del personaggio in unita' mondo (polo sul basso).")]
        public float targetHeight = 1.55f;

        [Tooltip("Punto d'appoggio dei piedi: fondo della sfera fisica invisibile (raggio 0.5).")]
        public Vector3 feetLocal = new Vector3(0f, -0.52f, 0f);

        [Tooltip("Velocita' di corsa usata per normalizzare il parametro Speed.")]
        public float runSpeed = 7.5f;

        private Transform avatarRoot;
        private Rigidbody rb;
        private Vector3 lastFacing = Vector3.forward;
        private Animator animator;
        private float footScale = 1f;

        private void Start()
        {
            Setup();
        }

        private void Setup()
        {
            rb = GetComponent<Rigidbody>();
            var player = GetComponent<FloorIsLava.PlayerController>();
            if (player == null)
            {
                Debug.LogWarning("[MiaCityAvatar] PlayerController mancante: avatar disattivato.", this);
                enabled = false;
                return;
            }

            var prefab = Resources.Load<GameObject>("PlayerHero");
            if (prefab == null)
            {
                Debug.LogWarning("[MiaCityAvatar] modello 'PlayerHero' non trovato, fallback del gioco.", this);
                enabled = false;
                return;
            }

            var model = Instantiate(prefab, transform, false);
            model.name = "MiaCittaAvatar";

            var mr = GetComponent<MeshRenderer>();
            if (mr != null) mr.enabled = false;

            avatarRoot = model.transform;
            animator = model.GetComponent<Animator>();

            // Scala misurando i bounds reali (niente magia) e appoggia i
            // piedi su feetLocal.
            Bounds b = WorldBounds(model);
            float h = b.size.y > 0.001f ? b.size.y : 1.5f;
            footScale = targetHeight / h;
            avatarRoot.localScale = Vector3.one * footScale;
            avatarRoot.localPosition = Vector3.zero;
            avatarRoot.localRotation = Quaternion.identity;
            Vector3 bottom = avatarRoot.InverseTransformPoint(
                new Vector3(b.center.x, b.min.y, b.center.z));
            avatarRoot.localPosition += feetLocal - bottom;
        }

        private static Bounds WorldBounds(GameObject root)
        {
            var renderers = root.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            Bounds b = new Bounds();
            bool any = false;
            for (int i = 0; i < renderers.Length; i++)
            {
                if (renderers[i] == null) continue;
                if (!any) { b = renderers[i].bounds; any = true; }
                else b.Encapsulate(renderers[i].bounds);
            }
            return b;
        }

        private void Update()
        {
            if (avatarRoot == null) return;

            // Direzione: segue la velocita' orizzontale dell'anima.
            if (rb != null)
            {
                Vector3 v = rb.velocity;
                Vector3 h = new Vector3(v.x, 0f, v.z);
                if (h.sqrMagnitude > 0.001f) lastFacing = h.normalized;
            }
            Quaternion target = Quaternion.LookRotation(lastFacing, Vector3.up);
            float k = 1f - Mathf.Exp(-14f * Time.deltaTime);
            avatarRoot.rotation = Quaternion.Slerp(avatarRoot.rotation, target, k);

            // Animatore: velocita' + stato a terra (guida il salto).
            if (animator != null)
            {
                Vector3 vel = rb != null ? rb.velocity : Vector3.zero;
                float speed = new Vector3(vel.x, 0f, vel.z).magnitude;
                animator.SetFloat("Speed", speed / Mathf.Max(0.5f, runSpeed));
                var pc = GetComponent<FloorIsLava.PlayerController>();
                bool grounded = pc != null && (pc.IsGrounded || pc.IsHovering);
                animator.SetBool("IsGrounded", grounded);
                animator.SetBool("IsMoving", speed > 0.15f);
            }
        }
    }
}