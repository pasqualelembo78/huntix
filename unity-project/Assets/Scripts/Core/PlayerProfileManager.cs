using UnityEngine;

namespace Huntix.Core
{
    public class PlayerProfileManager : MonoBehaviour
    {
        public static PlayerProfileManager Instance { get; private set; }

        [Header("Profile Settings")]
        public string playerId = "";
        public string displayName = "Player";
        public int currentLevel = 1;
        public int currentXp = 0;
        public int totalEggsCaught = 0;
        public int totalMVC = 0;
        public string[] collectedEggIds = new string[0];
        public string[] completedTasks = new string[0];
        public long lastLogin = 0;
        public PlayerPreferences preferences;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);

            if (preferences == null)
            {
                preferences = new PlayerPreferences();
            }
        }

        [System.Serializable]
        public class PlayerPreferences
        {
            public bool notificationsEnabled = true;
            public bool soundEnabled = true;
            public bool hapticsEnabled = true;
            public string language = "en";
            public int graphicsQuality = 2;
            public float musicVolume = 1.0f;
            public float sfxVolume = 1.0f;
        }

        public void UpdateProfileFromJson(string json)
        {
            try
            {
                PlayerProfile profile = JsonUtility.FromJson<PlayerProfile>(json);
                UpdateProfile(profile);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"[PlayerProfileManager] Error updating profile: {e.Message}");
            }
        }

        public void UpdateProfile(PlayerProfile profile)
        {
            playerId = profile.playerId;
            displayName = profile.displayName;
            currentLevel = profile.level;
            currentXp = profile.xp;
            totalEggsCaught = profile.totalEggsCaught;
            totalMVC = profile.totalMVC;
            collectedEggIds = profile.collectedEggIds;
            completedTasks = profile.completedTasks;
            lastLogin = profile.lastLogin;
            preferences = profile.preferences;
        }

        public string GetSaveJson()
        {
            PlayerProfile profile = new PlayerProfile
            {
                playerId = playerId,
                displayName = displayName,
                level = currentLevel,
                xp = currentXp,
                totalEggsCaught = totalEggsCaught,
                totalMVC = totalMVC,
                collectedEggIds = collectedEggIds,
                completedTasks = completedTasks,
                lastLogin = lastLogin,
                preferences = preferences
            };
            return JsonUtility.ToJson(profile);
        }

        public bool AddCollectedEgg(string eggId)
        {
            if (System.Array.IndexOf(collectedEggIds, eggId) < 0)
            {
                var list = new System.Collections.Generic.List<string>(collectedEggIds);
                list.Add(eggId);
                collectedEggIds = list.ToArray();
                totalEggsCaught++;
                Debug.Log($"[PlayerProfileManager] Added egg {eggId}. Total: {totalEggsCaught}");
                return true;
            }
            return false;
        }

        public void AddXp(int amount)
        {
            currentXp += amount;
            while (currentXp >= currentLevel * 100)
            {
                currentXp -= currentLevel * 100;
                currentLevel++;
                Debug.Log($"[PlayerProfileManager] Level up! Now level {currentLevel}");
            }
        }

        public void AddMVC(int amount)
        {
            totalMVC += amount;
            Debug.Log($"[PlayerProfileManager] Added {amount} MVC. Total: {totalMVC}");
        }

        public bool IsEggCollected(string eggId)
        {
            return System.Array.IndexOf(collectedEggIds, eggId) >= 0;
        }

        public int GetTotalEggsCollected()
        {
            return totalEggsCaught;
        }

        public int GetTotalMVC()
        {
            return totalMVC;
        }

        public int GetLevel()
        {
            return currentLevel;
        }

        public int GetCurrentXp()
        {
            return currentXp;
        }

        public int GetXpForNextLevel()
        {
            return currentLevel * 100;
        }

        public PlayerPreferences GetPreferences()
        {
            return preferences;
        }

        public void UpdateLastLogin()
        {
            lastLogin = System.DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        }
    }
}