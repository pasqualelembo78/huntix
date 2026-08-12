using System;
using System.Collections.Generic;
using Models;
using UnityEngine;

namespace Model
{
	public class Database : ScriptableObject
	{
		public readonly Dictionary<Type, Dictionary<int, DataModel>> Models = new Dictionary<Type, Dictionary<int, DataModel>>();
		
		#if UNITY_EDITOR
		[SerializeField] public List<DataModel> activeList = new List<DataModel>();
		#endif
		
		private static Database instance;

		public static Database Instance
		{
			get
			{
				if (!instance)
				{
					#if UNITY_EDITOR
					var guids = UnityEditor.AssetDatabase.FindAssets($"t:{nameof(Database)}");
					if (guids.Length > 0)
					{
						instance = UnityEditor.AssetDatabase.LoadAssetAtPath<Database>(UnityEditor.AssetDatabase.GUIDToAssetPath(guids[0]));
					}
					else
					{
						Debug.LogError("Database asset not found");
					}
					#endif
				}

				return instance;
			}
		}

		public static bool TryGet<T>(int key, out T value) where T : DataModel<T>
		{
			var secDKey = typeof(T);
			
			if(Instance.Models.TryGetValue(secDKey, out var dict) && 
			   dict.TryGetValue(key, out var baseValue))
			{
				value = (T)baseValue;
				return true;
			}
			value = null;
			return false;
		}

		public static T Get<T>(int key) where T : DataModel<T> => (T)(Instance.Models[typeof(T)][key]);
		
		public static void Add<T>(DataModel<T> dataModel) where T : DataModel<T>
		{
			var secDKey = dataModel.GetSecondaryKey();
			if(!Instance.Models.TryGetValue(secDKey, out var dict))
			{
				dict = instance.Models[secDKey] = new Dictionary<int, DataModel>();
			}
			dict.Add(dataModel.GetPrimaryKey(),dataModel);
		}

		public static void Remove<T>(DataModel<T> dataModel) where T : DataModel<T> => Instance.Models[dataModel.GetSecondaryKey()].Remove(dataModel.GetPrimaryKey());

        private void OnEnable()
        {
	        //Debug.Log("Database Loaded");
	        instance = this;
        }
  
	}
}