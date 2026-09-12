using UnityEngine;
using UnityEditor;
using City.Vehicle;
using City.Vehicle.Mechanics;

public class ConceptCarSetup
{
    [MenuItem("Tools/Setup Concept Car 006")]
    public static void Setup()
    {
        // 1. Carica l'FBX come prefab
        var fbxPath = "Assets/Art/Vehicles/ConceptCar006/FC-6.fbx";
        var fbxAsset = AssetDatabase.LoadAssetAtPath<GameObject>(fbxPath);
        if (fbxAsset == null) { Debug.LogError("FBX not found at " + fbxPath); return; }

        // 2. Istanzia
        var instance = PrefabUtility.InstantiatePrefab(fbxAsset) as GameObject;
        instance.name = "ConceptCar006";

        // 3. Scala (il modello è in metri? controlliamo la scala)
        // Unity importa a scala 1 di default. Il modello sembra essere in cm (tris alti).
        // Applichiamo una scala per portarlo a dimensioni auto realistiche (~4.5m lunghezza)
        instance.transform.localScale = Vector3.one * 0.01f; // FBX in cm -> metri

        // 4. Aggiungi componenti vehicle
        var vc = instance.AddComponent<VehicleController>();
        vc.SetupStandardPartDamage("ConceptCar006");

        // 5. VehicleData (ScriptableObject)
        var data = ScriptableObject.CreateInstance<VehicleData>();
        data.vehicleName = "Concept Car 006";
        data.price = 25000;
        data.maxSpeed = 35f;
        data.acceleration = 6f;
        data.brakeForce = 18f;
        data.turnSpeed = 90f;
        data.drag = 1.2f;
        data.bodyColor = new Color(0.2f, 0.3f, 0.5f);
        data.wheelColor = new Color(0.1f, 0.1f, 0.1f);
        data.bodyWidth = 1.9f;
        data.bodyLength = 4.5f;
        data.bodyHeight = 1.3f;
        data.category = VehicleCategory.Car;

        // Salva VehicleData come asset
        string dataPath = "Assets/Resources/VehicleData/ConceptCar006.asset";
        System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(dataPath));
        AssetDatabase.CreateAsset(data, dataPath);
        vc.data = data;

        // 6. WheelSpinner per animazione ruote
        instance.AddComponent<WheelSpinner>();

        // 7. VehicleInterior (già creato proceduralmente, ma il modello HA già interno)
        // Il modello ha già parti interne (floormat, SW_base, etc.) - NON aggiungiamo VehicleInterior procedurale
        // ma facciamo in modo che le parti interne del modello siano visibili

        // 8. Configura collider BoxCollider principale
        var boxCol = instance.GetComponent<BoxCollider>();
        if (boxCol == null) boxCol = instance.AddComponent<BoxCollider>();
        boxCol.size = new Vector3(1.9f, 1.3f, 4.5f);
        boxCol.center = new Vector3(0f, 0.65f, 0f);

        // 9. Trigger interazione
        var triggerGo = new GameObject("Trigger");
        triggerGo.transform.SetParent(instance.transform, false);
        triggerGo.transform.localPosition = new Vector3(0f, 0.8f, 0f);
        var triggerCol = triggerGo.AddComponent<BoxCollider>();
        triggerCol.isTrigger = true;
        triggerCol.size = new Vector3(3.9f, 2.5f, 6.5f);
        var vi = triggerGo.AddComponent<VehicleInteract>();
        vi.controller = vc;
        vi.data = data;
        vi.vehicleCode = "CONCEPT006";

        // 10. Tag e layer
        instance.tag = "Vehicle";
        instance.layer = LayerMask.NameToLayer("Default");

        // 11. Salva come prefab
        string prefabPath = "Assets/Resources/Vehicles/ConceptCar006.prefab";
        System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(prefabPath));
        var prefab = PrefabUtility.SaveAsPrefabAsset(instance, prefabPath);
        GameObject.DestroyImmediate(instance);

        Debug.Log("✅ ConceptCar006 prefab creato in: " + prefabPath);
        Debug.Log("✅ VehicleData creato in: " + dataPath);

        AssetDatabase.Refresh();
    }
}