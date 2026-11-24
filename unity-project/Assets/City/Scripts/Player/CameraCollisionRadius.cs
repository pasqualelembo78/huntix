// Adattato da dbrizov/Unity-CharacterController — Copyright (c) 2015 Denis
// Rizov, MIT (licenza integrale in THIRD_PARTY_NOTICES.md). Sorgente:
// Assets/NaughtyCharacter/_Scripts/SpringArm.cs del repository upstream.
// Modifiche Huntix: formula isolata, opt-in (default OFF), nessun cambio del
// raggio 0.22 m esistente, nessuna dipendenza dal controller del repository.
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Raggio della sphere-cast anti-clipping calcolato dal frustum della
    /// camera (near clip + FOV + aspect), invece che dalla costante fissa.
    /// </summary>
    public static class CameraCollisionRadius
    {
        public const string Id = "camera-radius";
        public const float MinRadius = 0.12f;
        public const float MaxRadius = 0.60f;

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled(Id);
            City.Diagnostics.OpenSourceFeatureBridge.Register(Id, on =>
            {
                Enabled = on;
                CameraRig rig = CameraRig.Instance;
                if (rig != null) rig.useFrustumCollisionRadius = on;
            });
        }

        public static float FromCamera(float fovDegrees, float aspect,
                                       float nearClip, float multiplier = 1f)
        {
            if (fovDegrees <= 0f || aspect <= 0f || nearClip <= 0f) return MinRadius;
            float halfFov = fovDegrees * Mathf.Deg2Rad * 0.5f;
            float halfHeight = Mathf.Tan(halfFov) * nearClip;
            float halfWidth = halfHeight * aspect;
            float radius = Mathf.Sqrt(halfWidth * halfWidth + halfHeight * halfHeight);
            return Mathf.Clamp(radius * multiplier, MinRadius, MaxRadius);
        }
    }
}
