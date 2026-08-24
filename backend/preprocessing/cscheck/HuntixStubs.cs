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
        public string missionId;
        public string title;
        public bool IsActive => false;
        public static NPCMission.MissionData GenerateMissionData(int seed) => new MissionData();
        public struct MissionData { }
        public void ApplyData(MissionData d) {}
        public void AttachToNPC(City.NPC.NPCController npc) {}
        public void Activate() {}
        public void Complete() {}
    }
}

namespace City.Interior
{
    // stub di InteriorGenerator (reale escluso: usa TMPro)
    public class InteriorGenerator : UnityEngine.MonoBehaviour
    {
        public static InteriorGenerator Instance => null;
        public void BuildInterior(Transform parent, string type, float extW,
            float extD, float extH, int floors, City.World.Shop shop) {}
        public Vector3 GetStairPosition(Transform root, int floor) => Vector3.zero;
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
