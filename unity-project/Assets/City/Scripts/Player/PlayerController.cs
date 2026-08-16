using UnityEngine;

namespace City.Player
{
    [RequireComponent(typeof(CharacterController))]
    public class PlayerController : MonoBehaviour
    {
        public float walkSpeed = 4f;
        public float runSpeed = 7.5f;
        public float turnSpeed = 12f;
        public float gravity = -25f;

        private CharacterController controller;
        private Animator animator;
        private Vector2 moveInput;
        private Vector3 velocity;
        private float currentSpeed;

        public bool IsMoving { get; private set; }

        public void SetMoveInput(Vector2 input)
        {
            moveInput = input;
        }

        public void Stop()
        {
            moveInput = Vector2.zero;
            velocity = Vector3.zero;
            currentSpeed = 0f;
        }

        private void Awake()
        {
            controller = GetComponent<CharacterController>();
            animator = GetComponentInChildren<Animator>();
        }

        private void Update()
        {
            Vector3 move = Vector3.zero;
            float targetSpeed = 0f;

            if (moveInput.sqrMagnitude > 0.02f)
            {
                CameraRig rig = CameraRig.Instance;
                Vector3 forward = rig != null
                    ? Vector3.ProjectOnPlane(rig.transform.forward, Vector3.up).normalized
                    : transform.forward;
                Vector3 right = Vector3.Cross(Vector3.up, forward);
                Vector3 dir = forward * moveInput.y + right * moveInput.x;
                if (dir.sqrMagnitude > 1f) dir.Normalize();

                move = dir;
                targetSpeed = Mathf.Lerp(walkSpeed, runSpeed, Mathf.InverseLerp(0.35f, 1f, moveInput.magnitude));

                Quaternion targetRotation = Quaternion.LookRotation(dir, Vector3.up);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRotation, turnSpeed * Time.deltaTime);
            }

            currentSpeed = Mathf.MoveTowards(currentSpeed, targetSpeed, 10f * Time.deltaTime);
            IsMoving = currentSpeed > 0.05f;

            // Player congelato (CityOSMWorld nasconde la seed e disattiva il CC
            // finche' il terreno OSM non e' pronto): niente gravita' e niente
            // Move su un controller disabilitato (errore Unity).
            if (!controller.enabled) return;
            velocity = move * currentSpeed;
            if (controller.isGrounded && velocity.y < 0f) velocity.y = -1f;
            velocity.y += gravity * Time.deltaTime;
            controller.Move(velocity * Time.deltaTime);

            if (animator != null) animator.SetFloat("Speed", currentSpeed);
        }
    }
}
