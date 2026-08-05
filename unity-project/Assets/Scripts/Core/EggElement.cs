using UnityEngine;

namespace Huntix.Core
{
    public enum EggElement
    {
        WATER,
        EARTH,
        AIR,
        FIRE,
        NORMAL
    }

    [CreateAssetMenu(fileName = "EggElement", menuName = "Huntix/EggElement")]
    public class EggElementSO : ScriptableObject
    {
        public EggElement elementType;
        public string displayName;
        public Color color;
        public Sprite icon;
    }
}