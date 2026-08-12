using System.Collections.Generic;
using Models;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEditor.UIElements;
using UnityEngine;
using UnityEngine.UIElements;

public class PlayLevelWindow : UnityEditor.EditorWindow
{
	private GameSettings gameSettings;
	private Editor cacheEditor;
	private List<string> labels;
	private DropdownField dropdown;

	[MenuItem("Window/Levels")]
	public static void Open()
	{
		GetWindow<PlayLevelWindow>().Show();
	}
	private void CreateGUI()
	{
		rootVisualElement.Clear();
		var guids= AssetDatabase.FindAssets($"t:{nameof(GameSettings)}");
		
		if(guids.Length > 0)
		{
			gameSettings = AssetDatabase.LoadAssetAtPath<GameSettings>(AssetDatabase.GUIDToAssetPath(guids[0]));
		}
		//Debug.Log(gameSettings == null);
		if (gameSettings)
		{
			var serializedObject = new SerializedObject(gameSettings);

			RefreshDropdown();
			rootVisualElement.Add(dropdown);
			rootVisualElement.Add(new PropertyField(serializedObject.FindProperty(nameof(GameSettings.mode))));
			
			rootVisualElement.Add(new PropertyField(serializedObject.FindProperty(nameof(GameSettings.levelPrefabs))));
			
			rootVisualElement.Bind(serializedObject);
		}
		
		rootVisualElement.Add(new Button(CreateGUI){text = "Refresh"});
	}

	private void RefreshDropdown()
	{
		dropdown?.RemoveFromHierarchy();
		labels = gameSettings.levelPrefabs.ConvertAll(x => x.editorAsset.name);
		dropdown = new DropdownField(labels,-1, OnSelection);
	}

	private string OnSelection(string arg)
	{
		int index = gameSettings.levelPrefabs.FindIndex(x => x.editorAsset.name == arg);
		if (index != -1)
		{
			EditorSceneManager.OpenScene(EditorBuildSettings.scenes[0].path, OpenSceneMode.Single);
			gameSettings.quickLoadLevelIndex = index;
			EditorApplication.EnterPlaymode();
		}
		return arg;
	}
}
