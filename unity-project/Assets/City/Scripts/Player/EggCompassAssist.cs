// Adattato da Android_Unity3D_OpenWorld — repository SENZA licenza: solo il
// principio generale della soglia di velocita' per la rotazione della camera,
// riscritto da zero. Nessun codice o asset del repository e' stato copiato.
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Bussola uova a bassa velocita': opt-in (default OFF). Solo in guida e
    /// sotto 2 m/s ruota lo yaw verso l'uovo attivo piu' vicino, al massimo
    /// 15 gradi/s. Sopra soglia non corregge nulla: la guida ha sempre priorita'.
    /// </summary>
    public static class EggCompassAssist
    {
        public const string Id = "egg-compass";
        public const float SpeedThresholdMps = 2f;
        public const float MaxDegreesPerSecond = 15f;
        public const float MaxTargetDistanceM = 120f;

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled(Id);
            City.Diagnostics.OpenSourceFeatureBridge.Register(Id, on => Enabled = on);
        }

        /// <summary>Chiamato a fine CameraRig.Update, prima del LateUpdate.
        /// Nessuna query nuova per frame quando e' disattivato.</summary>
        public static void Apply(CameraRig rig, float speedMps, float dt)
        {
            if (!Enabled || rig == null || rig.firstPerson || dt <= 0f) return;
            if (speedMps >= SpeedThresholdMps) return;
            var spawner = City.Economy.EggSpawnManager.Instance;
            if (spawner == null || Game.Instance == null || !Game.Instance.IsDriving) return;
            var eggs = spawner.ActiveEggs;
            Transform player = Game.Instance.player == null ? null : Game.Instance.player.transform;
            if (player == null || eggs.Count == 0) return;
            Vector3 from = player.position;
            GameObject best = null;
            float bestDist = MaxTargetDistanceM;
            for (int i = 0; i < eggs.Count; i++)
            {
                var egg = eggs[i];
                if (egg == null || !egg.activeSelf) continue;
                float d = Vector3.Distance(egg.transform.position, from);
                if (d < bestDist) { bestDist = d; best = egg; }
            }
            if (best == null) return;
            using (City.Diagnostics.PerfProbe.Measure("camera"))
            {
                Vector3 dir = best.transform.position - from;
                dir.y = 0f;
                if (dir.sqrMagnitude < 0.01f) return;
                Quaternion target = Quaternion.LookRotation(dir.normalized, Vector3.up);
                Quaternion next = Quaternion.RotateTowards(rig.MovementBasis, target,
                    MaxDegreesPerSecond * dt);
                rig.SetYaw(next);
            }
        }
    }
}
