using UnityEngine;

namespace Huntix.Core
{
    public enum ZoneType
    {
        UNKNOWN,
        SNOW,
        WATER,
        MOUNTAIN,
        FOREST,
        DESERT,
        CITY,
        BEACH
    }

    [CreateAssetMenu(fileName = "ZoneType", menuName = "Huntix/ZoneType")]
    public class ZoneTypeSO : ScriptableObject
    {
        public ZoneType zoneType;
        public string displayName;
        public Color mapColor;
        public Sprite icon;
    }
}