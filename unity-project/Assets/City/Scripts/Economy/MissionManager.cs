using System.Collections.Generic;
using UnityEngine;

namespace City.Economy
{
    public class MissionManager : MonoBehaviour
    {
        public static MissionManager Instance;

        private readonly List<NPCMission> activeMissions = new List<NPCMission>();
        private readonly List<NPCMission> completedMissions = new List<NPCMission>();
        private int eggsCollected;
        private float walkDistance;

        public int ActiveCount => activeMissions.Count;
        public int CompletedCount => completedMissions.Count;

        private void Awake()
        {
            Instance = this;
        }

        public void ActivateMission(NPCMission mission)
        {
            if (!activeMissions.Contains(mission))
                activeMissions.Add(mission);
        }

        public void CompleteMission(NPCMission mission)
        {
            activeMissions.Remove(mission);
            if (!completedMissions.Contains(mission))
                completedMissions.Add(mission);
        }

        public void OnEggCollected()
        {
            eggsCollected++;
            foreach (var m in activeMissions)
            {
                if (m != null && m.type == NPCMission.MissionType.CollectEggs)
                    m.OnEggCollected();
            }
        }

        public void OnPlayerWalked(float meters)
        {
            walkDistance += meters;
            foreach (var m in activeMissions)
            {
                if (m != null && m.type == NPCMission.MissionType.WalkDistance)
                    m.OnPlayerWalked(meters);
            }
        }

        public List<NPCMission> GetActiveMissions()
        {
            return activeMissions;
        }

        public int GetTotalEggsCollected() => eggsCollected;
        public float GetTotalWalkDistance() => walkDistance;
    }
}
