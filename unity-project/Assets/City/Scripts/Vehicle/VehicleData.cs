using UnityEngine;

namespace City.Vehicle
{
    [CreateAssetMenu(fileName = "NewVehicle", menuName = "City/Vehicle Data")]
    public class VehicleData : ScriptableObject
    {
        public string vehicleName = "Auto";
        public int price = 50;
        public float maxSpeed = 14f;
        public float acceleration = 8f;
        public float brakeForce = 16f;
        public float turnSpeed = 100f;
        public float drag = 1.5f;

        [Header("Procedural Model")]
        public Color bodyColor = new Color(0.8f, 0.2f, 0.2f);
        public Color wheelColor = new Color(0.15f, 0.15f, 0.15f);
        public float bodyWidth = 1.8f;
        public float bodyLength = 4f;
        public float bodyHeight = 1.2f;

        [Header("Optional Kenney Prefab")]
        public GameObject kenneyPrefab;

        [Header("Category")]
        public VehicleCategory category = VehicleCategory.Car;
    }

    public enum VehicleCategory
    {
        Car,
        Motorcycle,
        Scooter,
        EBike,
        Van,
        Truck
    }
}
