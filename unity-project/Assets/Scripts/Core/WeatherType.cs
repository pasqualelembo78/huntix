using UnityEngine;

namespace Huntix.Core
{
    public enum WeatherType
    {
        CLEAR,
        CLOUDY,
        RAIN,
        SNOW,
        FOG,
        STORM,
        WINDY
    }

    [CreateAssetMenu(fileName = "WeatherType", menuName = "Huntix/WeatherType")]
    public class WeatherTypeSO : ScriptableObject
    {
        public WeatherType weatherType;
        public string displayName;
        public Color skyColor;
        public float rarityBoostMultiplier;
        public Sprite icon;
    }
}