// IndoorTests.cs - Editor validation for the Indoor scene + IndoorManager wiring.
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using Huntix.Indoor;

public static class IndoorTests
{
    [MenuItem("Tools/Tests/Validate Indoor Scene")]
    public static void Run()
    {
        string path = "Assets/Scenes/Indoor.unity";
        var scene = EditorSceneManager.OpenScene(path, OpenSceneMode.Single);
        Debug.Log($"[IndoorTests] Scene loaded name={scene.name} isLoaded={scene.isLoaded}");

        string[] names = { "Main Camera", "Directional Light", "GameManager", "IndoorManager", "Player", "StoreBuilder" };
        foreach (var n in names)
        {
            var go = GameObject.Find(n);
            Debug.Log($"[IndoorTests] Find '{n}': {(go != null ? "OK" : "MISSING")} {(go != null ? "(components=" + go.GetComponents<Component>().Length + ")" : "")}");
        }

        var im = GameObject.Find("IndoorManager")?.GetComponent<IndoorManager>();
        if (im != null)
        {
            Debug.Log($"[IndoorTests] IndoorManager.storeBuilder == null ? {im.storeBuilder == null}");
            Debug.Log($"[IndoorTests] IndoorManager.interactableLayer = {im.interactableLayer}");
        }
        else
        {
            Debug.Log("[IndoorTests] IndoorManager MISSING");
        }

        var sb = GameObject.Find("StoreBuilder")?.GetComponent<StoreBuilder>();
        Debug.Log($"[IndoorTests] StoreBuilder on scene: {(sb != null ? "yes" : "no")}");
        if (sb != null)
        {
            sb.BuildStore("supermarket", true);
            Debug.Log($"[IndoorTests] After BuildStore supermarket(cute): spawned children under StoreInterior={GameObject.Find("StoreInterior")?.GetComponentsInChildren<Renderer>().Length}");
        }

        EditorApplication.Exit(0);
    }
}
