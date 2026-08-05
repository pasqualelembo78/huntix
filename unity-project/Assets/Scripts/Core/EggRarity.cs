using UnityEngine;

namespace Huntix.Core
{
    public enum EggRarity
    {
        Common,
        Uncommon,
        Rare,
        Epic,
        Legendary
    }

    [CreateAssetMenu(fileName = "EggRarity", menuName = "Huntix/EggRarity")]
    public class EggRaritySO : ScriptableObject
    {
        public EggRarity rarityId;
        public string displayName;
        public Color color;
        public float captureDifficulty;
        public int mvcReward;
        public int minLevelRequired;
        public Sprite icon;
        public GameObject prefab;
    }
}