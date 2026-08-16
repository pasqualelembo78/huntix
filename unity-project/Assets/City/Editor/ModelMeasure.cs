using UnityEditor;
using UnityEngine;

namespace City.Editor
{
    public static class ModelMeasure
    {
        [MenuItem("Citta/Log Misure Modelli")]
        public static void Log()
        {
            string[] paths =
            {
                "Assets/Art/kenney_furniture-kit/Models/FBX format/chair.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/plantSmall1.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/plantSmall2.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/stoolBar.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/tableCoffee.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/counterBar.fbx",
                "Assets/Art/kenney_city-kit-roads/Models/FBX format/light_curved.fbx",
                "Assets/Art/kenney_city-kit-roads/Models/FBX format/road_square.fbx",
                "Assets/Art/kenney_city-kit-suburban/Models/FBX format/driveway_long.fbx",
                "Assets/Art/kenney_city-kit-suburban/Models/FBX format/path_long.fbx",
                "Assets/Art/kenney_furniture-kit/Models/FBX format/shelf.fbx",
            };
            foreach (string p in paths)
            {
                GameObject go = AssetDatabase.LoadAssetAtPath<GameObject>(p);
                if (go == null)
                {
                    Debug.Log("MISURA " + p + " -> NULL");
                    continue;
                }
                Bounds b = new Bounds();
                bool found = false;
                foreach (Renderer r in go.GetComponentsInChildren<Renderer>())
                {
                    if (!found) { b = r.bounds; found = true; }
                    else b.Encapsulate(r.bounds);
                }
                Debug.Log("MISURA " + p + " -> size=" + b.size + " center=" + b.center);
            }
        }
    }
}
