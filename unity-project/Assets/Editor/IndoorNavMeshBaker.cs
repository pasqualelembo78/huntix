using UnityEngine;
using UnityEditor;
using UnityEngine.AI;
using UnityEditor.SceneManagement;
using UnityEngine.SceneManagement;

/// <summary>
/// IndoorNavMeshBaker — tool Editor per bake il NavMesh nella scena Indoor.
/// Menu: Huntix > Indoors > Bake Indoor NavMesh
///
/// Prima di buildare:
/// 1. Apri la scena Indoor (Assets/Scenes/Indoor.unity)
/// 2. Assicurati che il Floor sia marcato come "Navigation Static"
/// 3. Window > AI > Navigation > Bake, oppure usa questo menu
///
/// Il NavMesh permette agli NPC (NPC.cs) di pattugliare.
/// Senza NavMesh gli NPC restano fermi ma il dialogo/prossimità funziona.
/// </summary>
public static class IndoorNavMeshBaker
{
    private static readonly string ScenePath = "Assets/Scenes/Indoor.unity";

    [MenuItem("Huntix/Indoors/Bake Indoor NavMesh")]
    public static void BakeIndoorNavMesh()
    {
        // Ensure the Indoor scene is open
        if (SceneManager.GetActiveScene().path != ScenePath)
        {
            EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
        }

        // Find Floor and mark as Navigation Static
        var floor = GameObject.Find("Floor");
        if (floor == null)
        {
            Debug.LogError("[IndoorNavMeshBaker] GameObject 'Floor' non trovato");
            EditorUtility.DisplayDialog("Errore",
                "Impossibile trovare il GameObject 'Floor' nella scena Indoor.", "OK");
            return;
        }

        // Set Navigation Static flag (bit 2 = 4)
        var flags = GameObjectUtility.GetStaticEditorFlags(floor);
        flags |= StaticEditorFlags.NavigationStatic;
        flags |= StaticEditorFlags.LightmapStatic;
        GameObjectUtility.SetStaticEditorFlags(floor, flags);

        Debug.Log($"[IndoorNavMeshBaker] Floor marcato Navigation Static. Static flags: {flags}");

        // Bake using the scene NavMeshSettings (Window > AI > Navigation)
        UnityEditor.AI.NavMeshBuilder.BuildNavMesh();

        // Save scene
        if (SceneManager.GetActiveScene().isDirty)
        {
            EditorSceneManager.SaveOpenScenes();
        }

        AssetDatabase.SaveAssets();
        AssetDatabase.Refresh();

        Debug.Log("[IndoorNavMeshBaker] NavMesh baked con successo in Indoor.unity!");
        EditorUtility.DisplayDialog("Successo",
            "NavMesh è stato bake nella scena Indoor.unity!\n" +
            "Gli NPC ora possono pattugliare.", "OK");
    }

    [MenuItem("Huntix/Indoors/Bake Indoor NavMesh (Quick)")]
    public static void BakeQuick()
    {
        // Quick: just mark floor as static and show instructions
        if (SceneManager.GetActiveScene().path != ScenePath)
        {
            EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
        }

        var floor = GameObject.Find("Floor");
        if (floor != null)
        {
            var flags = GameObjectUtility.GetStaticEditorFlags(floor);
            flags |= StaticEditorFlags.NavigationStatic;
            GameObjectUtility.SetStaticEditorFlags(floor, flags);
            EditorSceneManager.SaveOpenScenes();
            Debug.Log("[IndoorNavMeshBaker] Floor marcato Navigation Static + scena salvata. Bake via Window > AI > Navigation.");
        }
    }
}
