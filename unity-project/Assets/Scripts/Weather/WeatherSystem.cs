using UnityEngine;
using Huntix.Bridge;

namespace Huntix.Weather
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

    public class WeatherSystem : MonoBehaviour
    {
        public static WeatherSystem Instance { get; private set; }

        public WeatherType CurrentWeather { get; private set; }
        public float WeatherChangeInterval = 600f;

        [Header("Weather Effects")]
        public ParticleSystem rainEffect;
        public ParticleSystem snowEffect;
        public ParticleSystem fogEffect;
        public ParticleSystem stormEffect;
        public Light directionalLight;

        private float _weatherTimer;
        private float _nextWeatherChange;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            CurrentWeather = WeatherType.CLEAR;
            _weatherTimer = 0f;
            _nextWeatherChange = WeatherChangeInterval;
        }

        private void Update()
        {
            _weatherTimer += Time.deltaTime;
            if (_weatherTimer >= _nextWeatherChange)
            {
                _weatherTimer = 0f;
                _nextWeatherChange = WeatherChangeInterval + Random.Range(-120f, 120f);
                ChangeWeather();
            }
        }

        private void ChangeWeather()
        {
            var types = System.Enum.GetValues(typeof(WeatherType));
            CurrentWeather = (WeatherType)types.GetValue(Random.Range(0, types.Length));
            ApplyWeatherEffects();
            Debug.Log($"[WeatherSystem] Weather changed to {CurrentWeather}");
        }

        private void ApplyWeatherEffects()
        {
            if (rainEffect != null) rainEffect.Stop();
            if (snowEffect != null) snowEffect.Stop();
            if (fogEffect != null) fogEffect.Stop();
            if (stormEffect != null) stormEffect.Stop();

            switch (CurrentWeather)
            {
                case WeatherType.RAIN:
                    if (rainEffect != null) rainEffect.Play();
                    break;
                case WeatherType.SNOW:
                    if (snowEffect != null) snowEffect.Play();
                    break;
                case WeatherType.FOG:
                    if (fogEffect != null) fogEffect.Play();
                    break;
                case WeatherType.STORM:
                    if (stormEffect != null) stormEffect.Play();
                    break;
            }

            UnityBridge.SendMessageToAndroid("WeatherChanged", $"{{\"weather\":\"{CurrentWeather}\"}}");
        }

        public float GetRarityBoost(Huntix.Core.EggElement element)
        {
            switch (CurrentWeather)
            {
                case WeatherType.RAIN:
                    return element == Huntix.Core.EggElement.WATER ? 1.5f : 1.0f;
                case WeatherType.SNOW:
                    return element == Huntix.Core.EggElement.EARTH ? 1.5f : 1.0f;
                case WeatherType.WINDY:
                    return element == Huntix.Core.EggElement.AIR ? 1.5f : 1.0f;
                case WeatherType.STORM:
                    return element == Huntix.Core.EggElement.FIRE ? 1.5f : 1.0f;
                default:
                    return 1.0f;
            }
        }
    }
}