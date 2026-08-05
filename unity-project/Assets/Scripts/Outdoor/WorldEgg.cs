using UnityEngine;
using Huntix.Core;

namespace Huntix.Outdoor
{
    [CreateAssetMenu(fileName = "WorldEgg", menuName = "Huntix/WorldEgg")]
    public class WorldEggSO : ScriptableObject
    {
        public string id;
        public string name;
        public float latitude;
        public float longitude;
        public EggRarity rarity;
        public EggElement element;
        public bool isHatched;
        public bool isCaptured;
        public float captureRadius;
        public float spawnInterval;
        public WeatherType weatherBoost;
        public ZoneType zoneType;
        public GameObject prefab;
        public Sprite icon;
    }
}