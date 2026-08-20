using UnityEngine;

namespace Huntix.Indoor
{
    [CreateAssetMenu(fileName = "BuildingDef", menuName = "Huntix/BuildingDef")]
    public class BuildingDefSO : ScriptableObject
    {
        public string buildingId;
        public BuildingType buildingType;
        public string name;
        public string emoji;
        public Color color3D;
        public Vector3 position;
        public string interiorSceneName;
        public GameObject prefab;
        public Sprite icon;
        public string[] actions;
    }

    public enum BuildingType
    {
        HOUSE,
        SCHOOL,
        RESTAURANT,
        SUPERMARKET,
        HOSPITAL,
        GYM,
        LIBRARY,
        PARK,
        OTHER
    }
}
