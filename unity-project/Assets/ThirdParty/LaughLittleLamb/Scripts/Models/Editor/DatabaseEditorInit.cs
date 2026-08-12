using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

namespace Model.Editor
{
	[InitializeOnLoad]
	public static class DatabaseEditorInit
	{
		static DatabaseEditorInit()
		{
			var guids = AssetDatabase.FindAssets($"t:{nameof(Database)}");
			Database database;
			if (guids.Length == 0)
			{
				database = ScriptableObject.CreateInstance<Database>();
				AssetDatabase.CreateAsset(database, $"Assets/Database.Asset");
			}
			else
			{
				database = AssetDatabase.LoadAssetAtPath<Database>(AssetDatabase.GUIDToAssetPath(guids[0]));
			}
			List<Object> preloadedAssets = new List<Object>(PlayerSettings.GetPreloadedAssets());

			if (!preloadedAssets.Contains(database))
			{
				preloadedAssets.Add(database);
				preloadedAssets.RemoveAll(x => x == null);
				PlayerSettings.SetPreloadedAssets(preloadedAssets.ToArray());
			}
		}
	}
}