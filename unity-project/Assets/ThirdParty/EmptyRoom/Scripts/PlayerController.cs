using UnityEngine;

namespace EmptyRoom
{
    // Character controller in prima persona: WASD per muoversi e mouse look.
    // Il cursore viene bloccato al centro (crosshair) per mirare con il mouse.
    [RequireComponent(typeof(CharacterController))]
    public class PlayerController : MonoBehaviour
    {
        [Header("Movement")]
        public float moveSpeed = 4f;
        public float mouseSensitivity = 2f;
        public float gravity = 9.81f;

        private CharacterController _cc;
        private Camera _cam;
        private float _pitch;

        private void Awake()
        {
            _cc = GetComponent<CharacterController>();
            _cam = GetComponentInChildren<Camera>();
            if (_cam == null) Debug.LogError("[PlayerController] Camera non trovata nel player.");
            Cursor.lockState = CursorLockMode.Locked;
            Cursor.visible = false;
        }

        private void Update()
        {
            if (_cam == null) return;

            // Mouse look: yaw sul player, pitch sulla camera.
            float mx = Input.GetAxis("Mouse X") * mouseSensitivity;
            float my = Input.GetAxis("Mouse Y") * mouseSensitivity;
            transform.Rotate(0f, mx, 0f);
            _pitch = Mathf.Clamp(_pitch - my, -80f, 80f);
            _cam.transform.localEulerAngles = new Vector3(_pitch, 0f, 0f);

            // Movimento relativo all'orientamento orizzontale del player.
            float x = Input.GetAxis("Horizontal");
            float z = Input.GetAxis("Vertical");
            Vector3 move = (transform.right * x + transform.forward * z).normalized * moveSpeed;
            move.y -= gravity;
            _cc.Move(move * Time.deltaTime);
        }
    }
}
