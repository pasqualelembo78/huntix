// Stub dei moduli HUNTIX referenziati dagli script OSM (solo per check sintassi)
using UnityEngine;

namespace Huntix.Core
{
    public class CityKitAssetRegistry : ScriptableObject
    {
        public GameObject[] prefabs;
        public GameObject Get(string name) => null;
    }

    public class ScriptableObject : Object
    {
        public static T[] LoadAll<T>(string path) where T : Object => new T[0];
    }

    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance => null;
        public CityKitAssetRegistry cityKitRegistry;
    }
}

namespace City.OSM
{
    // stub del sistema legacy reale (compilato solo nel check)
    public class CityOSMWorld : UnityEngine.MonoBehaviour
    {
        public static CityOSMWorld Instance => null;
        public void PrepareExit() {}
    }
}

namespace City.Economy
{
    // stub di NPCMission (reale escluso: usa TMPro)
    public class NPCMission : UnityEngine.MonoBehaviour
    {
        public enum MissionType { CollectEggs, WalkDistance }
        public enum MissionState { Available, Active, Completed }
        public string missionId;
        public string title;
        public string description;
        public MissionType type;
        public MissionState state;
        public int targetCount;
        public int currentCount;
        public bool IsActive => false;
        public void OnPlayerInteract() {}
        public static NPCMission.MissionData GenerateMissionData(int seed) => new MissionData();
        public struct MissionData { }
        public void ApplyData(MissionData d) {}
        public void AttachToNPC(City.NPC.NPCController npc) {}
        public void Activate() {}
        public void Complete() {}
    }

    // stub di Economy/MissionManager.cs (reale escluso dal check)
    public class MissionManager : UnityEngine.MonoBehaviour
    {
        public static MissionManager Instance;
        public System.Collections.Generic.List<NPCMission> GetActiveMissions() => null;
        public void OnEggCollected() {}
        public void OnPlayerWalked(float meters) {}
    }

    // stub di Economy/EggSpawnManager.cs (reale escluso dal check)
    public class EggSpawnManager : UnityEngine.MonoBehaviour
    {
        public static EggSpawnManager Instance;
        public void RemoveEgg(GameObject egg) {}
        public void SpawnEggsInChunk(Transform root, City.OSM.TileGeoDoc geo,
            System.Func<City.OSM.GeoLL, UnityEngine.Vector3> toLocal,
            UnityEngine.Rect bounds, int chunkSeed) {}
        public System.Collections.Generic.List<GameObject> ActiveEggs =>
            new System.Collections.Generic.List<GameObject>();
    }
}

namespace Huntix.Bridge
{
    public static class UnityBridge
    {
        public static void LogToAndroid(string tag, string msg) {}
        public static string GetCurrentLocation() { return "{\"lat\":0.0,\"lng\":0.0}"; }
        public static void StartLocationTracking() {}
        public static void SendMessageToAndroid(string type, string json) {}
        public static void ExitCityToHome() {}
        public static double GetMvcBalance() => 0.0;
        public static bool SpendMvc(double amount) => false;
        public static double AddMvc(double amount) => 0.0;
        // Unified player profile (stub per il check: soli no-op)
        public static string GetPlayerName() => "Giocatore";
        public static long GetPlayerXp() => 0L;
        public static int GetPlayerLevel() => 1;
        public static long GetPlayerPower() => 0L;
        public static int GetPlayerGems() => 0;
        public static int GetPlayerEnergy() => 100;
        public static int GetEggCount() => 0;
        public static string GetPlayerProfileJson() => "{}";
        public static void AddXpFromCity(long xp) {}
        public static void AddPowerFromCity(long power) {}
        public static void AddGemsFromCity(int gems) {}
        public static void SyncEnergyFromCity(int energy) {}
        public static void SetPlayerNameFromCity(string name) {}
    }
}

namespace UnityEngine
{
    // classe JNI Android usata solo su device (RewardedAdHelper)
    public class AndroidJavaClass : System.IDisposable
    {
        public AndroidJavaClass(string name) {}
        public void Call(string method) {}
        public void Call(string method, string arg) {}
        public T Call<T>(string method) => default(T);
        public void CallStatic(string method) {}
        public T CallStatic<T>(string method) => default(T);
        public void Dispose() {}
    }
}
