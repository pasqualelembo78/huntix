using UnityEngine;

namespace FloorIsLava
{
    public class CameraFollow : MonoBehaviour
    {
        public Transform target;
        public Vector3 offset;

        /// <summary>Latenza camera in stile citta': 0 = rigida (originale),
        /// 0.12–0.18 = smorzata morbida con SmoothDamp.</summary>
        [Tooltip("Tempo di smoothing della camera (0 = rigida originale).")]
        public float smoothTime = 0.15f;

        private Vector3 _vel;

        void Start()
        {
            if (target != null) transform.position = target.position + offset;
        }

        void Update()
        {
            if (target == null) return;
            Vector3 goal = target.position + offset;
            if (smoothTime <= 0f)
                transform.position = goal;
            else
                transform.position = Vector3.SmoothDamp(
                    transform.position, goal, ref _vel, smoothTime);
        }
    }
}
