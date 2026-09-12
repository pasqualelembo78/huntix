using UnityEngine;
using UnityEditor;

public class ModelInspector
{
    [MenuItem("Tools/Inspect ConceptCar FBX")]
    public static void Inspect()
    {
        var go = AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Art/Vehicles/ConceptCar006/FC-6.fbx");
        if (go == null) { Debug.LogError("FBX not found"); return; }
        var instance = PrefabUtility.InstantiatePrefab(go) as GameObject;
        Debug.Log("=== FBX Root: " + instance.name + " ===");
        PrintHierarchy(instance.transform, 0, true);
        GameObject.DestroyImmediate(instance);
    }

    static void PrintHierarchy(Transform t, int depth, bool logMeshOnly)
    {
        string indent = new string(' ', depth * 2);
        var mr = t.GetComponent<MeshRenderer>();
        var mf = t.GetComponent<MeshFilter>();
        bool hasMesh = mr != null || mf != null;
        if (!logMeshOnly || hasMesh || t.childCount == 0)
        {
            string info = "";
            if (mr != null) info += " [Renderer:" + (mr.sharedMaterial != null ? mr.sharedMaterial.name : "null") + "]";
            if (mf != null && mf.sharedMesh != null) info += " [Mesh:" + mf.sharedMesh.name + " tris:" + (mf.sharedMesh.triangles.Length / 3) + "]";
            Debug.Log(indent + t.name + info);
        }
        for (int i = 0; i < t.childCount; i++) PrintHierarchy(t.GetChild(i), depth + 1, logMeshOnly);
    }
}