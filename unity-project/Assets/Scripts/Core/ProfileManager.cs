using UnityEngine;
using System;
using System.Collections.Generic;

namespace Huntix.Core
{
    [Serializable]
    public class PlayerProfile
    {
        public string playerId;
        public string displayName;
        public int level;
        public int xp;
        public int totalEggsCaught;
        public int totalMVC;
        public string[] collectedEggIds;
        public string[] completedTasks;
        public long lastLogin;
        public PlayerProfileManager.PlayerPreferences preferences;

        public PlayerProfile()
        {
            level = 1;
            xp = 0;
            totalEggsCaught = 0;
            totalMVC = 0;
            collectedEggIds = new string[0];
            completedTasks = new string[0];
            lastLogin = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            preferences = new PlayerProfileManager.PlayerPreferences();
        }

        public int GetXpForNextLevel()
        {
            return level * 100;
        }

        public void AddXp(int amount)
        {
            xp += amount;
            while (xp >= GetXpForNextLevel())
            {
                xp -= GetXpForNextLevel();
                level++;
                Debug.Log($"[ProfileManager] Level up! Now level {level}");
            }
        }
    }

    public class ProfileManager : MonoBehaviour
    {
        public static ProfileManager Instance { get; private set; }

        public PlayerProfile Profile { get; private set; }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            Profile = new PlayerProfile();
        }

        public void LoadProfile(string json)
        {
            try
            {
                Profile = JsonUtility.FromJson<PlayerProfile>(json);
                Debug.Log($"[ProfileManager] Profile loaded for {Profile.displayName}");
            }
            catch (Exception e)
            {
                Debug.LogError($"[ProfileManager] Failed to load profile: {e.Message}");
            }
        }

        public string SaveProfile()
        {
            return JsonUtility.ToJson(Profile);
        }

        public void SyncToFirebase()
        {
            SaveManager.Instance.SaveToFirebase(SaveProfile());
        }

        public void AddEggCaught(string eggId)
        {
            var list = new System.Collections.Generic.List<string>(Profile.collectedEggIds);
            if (!list.Contains(eggId))
            {
                list.Add(eggId);
                Profile.collectedEggIds = list.ToArray();
                Profile.totalEggsCaught++;
            }
        }

        public void AddMVC(int amount)
        {
            Profile.totalMVC += amount;
        }
    }
}