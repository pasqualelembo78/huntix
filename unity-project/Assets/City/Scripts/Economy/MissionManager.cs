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

        private const string PrefEggs = "mm_eggs";
        private const string PrefWalk = "mm_walk";
        private const string PrefActive = "mm_active";

        public int ActiveCount => activeMissions.Count;
        public int CompletedCount => completedMissions.Count;

        private void Awake()
        {
            Instance = this;
            LoadStats();
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
            SaveStats();
        }

        public void CancelMission(NPCMission mission)
        {
            activeMissions.Remove(mission);
            SaveStats();
        }

        public void OnEggCollected()
        {
            eggsCollected++;
            CleanOrphans();
            foreach (var m in activeMissions)
            {
                if (m != null && m.type == NPCMission.MissionType.CollectEggs)
                    m.OnEggCollected();
            }
            SaveStats();
        }

        public void OnPlayerWalked(float meters)
        {
            walkDistance += meters;
            CleanOrphans();
            foreach (var m in activeMissions)
            {
                if (m != null && m.type == NPCMission.MissionType.WalkDistance)
                    m.OnPlayerWalked(meters);
            }
        }

        public List<NPCMission> GetActiveMissions()
        {
            CleanOrphans();
            return new List<NPCMission>(activeMissions);
        }

        public int GetTotalEggsCollected() => eggsCollected;
        public float GetTotalWalkDistance() => walkDistance;

        private void CleanOrphans()
        {
            for (int i = activeMissions.Count - 1; i >= 0; i--)
            {
                if (activeMissions[i] == null)
                    activeMissions.RemoveAt(i);
            }
        }

        private void SaveStats()
        {
            PlayerPrefs.SetInt(PrefEggs, eggsCollected);
            PlayerPrefs.SetFloat(PrefWalk, walkDistance);

            // Serializza missioni attive come JSON minimo (solo dati, non
            // riferimenti MonoBehaviour): al riavvio vengono ricreate come
            // missioni "standalone" senza NPC fisso.
            var list = new List<string>();
            foreach (var m in activeMissions)
            {
                if (m == null) continue;
                list.Add(m.missionId + "|" + (int)m.type + "|"
                    + m.currentCount + "|" + m.targetCount + "|"
                    + m.reward + "|" + m.description.Replace("|", "/"));
            }
            PlayerPrefs.SetString(PrefActive, string.Join(";", list.ToArray()));
            PlayerPrefs.Save();
        }

        private void LoadStats()
        {
            eggsCollected = PlayerPrefs.GetInt(PrefEggs, 0);
            walkDistance = PlayerPrefs.GetFloat(PrefWalk, 0f);

            string raw = PlayerPrefs.GetString(PrefActive, "");
            if (string.IsNullOrEmpty(raw)) return;

            foreach (string entry in raw.Split(';'))
            {
                string[] p = entry.Split('|');
                if (p.Length < 6) continue;
                var data = new NPCMission.MissionData
                {
                    missionId = p[0],
                    type = (NPCMission.MissionType)int.Parse(p[1]),
                    currentCount = int.Parse(p[2]),
                    targetCount = int.Parse(p[3]),
                    reward = int.Parse(p[4]),
                    description = p[5],
                };
                var go = new GameObject("Mission_" + data.missionId);
                var nm = go.AddComponent<NPCMission>();
                nm.ApplyData(data);
                nm.currentCount = data.currentCount;
                activeMissions.Add(nm);
            }
        }
    }
}
