using UnityEngine;

namespace City.Interior
{
    /// <summary>
    /// Player in prima persona per gli interni. Muove il player con il joystick
    /// e ruota la camera con l'orbit (touch trascina lato destro).
    /// </summary>
    [RequireComponent(typeof(CharacterController))]
    public class InteriorPlayer : MonoBehaviour
    {
        public float moveSpeed = 3.5f;
        public float lookSensitivity = 2.5f;
        public float gravity = -15f;

        [HideInInspector] public Camera camera;

        private CharacterController cc;
        private Vector2 moveInput;
        private float rotationX;
        private Vector3 velocity;

        private void Awake()
        {
            cc = GetComponent<CharacterController>();
        }

        private void OnEnable()
        {
            rotationX = 0f;
            if (camera != null)
                camera.transform.localRotation = Quaternion.identity;
        }

        public void SetMoveInput(Vector2 input)
        {
            moveInput = input;
        }

        public void OnLookDelta(float dx)
        {
            // Rotazione orizzontale (yaw) sul player
            transform.Rotate(Vector3.up, dx * lookSensitivity);

            // La camera è figlia del player, si muove automaticamente con il yaw.
            // Il pitch è gestito dal drag verticale se aggiunto in futuro.
        }

        private void Update()
        {
            if (!cc.enabled) return;

            // Movimento
            Vector3 move = Vector3.zero;
            if (moveInput.sqrMagnitude > 0.02f)
            {
                Vector3 forward = transform.forward;
                Vector3 right = transform.right;
                Vector3 dir = (forward * moveInput.y + right * moveInput.x).normalized;
                move = dir * moveSpeed;
            }

            // Gravità
            if (cc.isGrounded && velocity.y < 0f) velocity.y = -2f;
            velocity.y += gravity * Time.deltaTime;

            Vector3 motion = move + velocity * Time.deltaTime;
            cc.Move(motion * Time.deltaTime);
        }

        public void Stop()
        {
            moveInput = Vector2.zero;
            velocity = Vector3.zero;
        }
    }
}
