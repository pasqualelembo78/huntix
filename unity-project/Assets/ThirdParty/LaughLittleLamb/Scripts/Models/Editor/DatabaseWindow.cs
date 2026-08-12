using System;
using System.Collections.Generic;
using Model;
using UnityEditor;
using UnityEditor.UIElements;
using UnityEngine.UIElements;

namespace Models.Editor
{
	public class DatabaseWindow : EditorWindow
	{
		private SerializedObject serializedObject;

		private VisualElement root;
		private List<Type> keysTypes;
		private List<string> labels;

		private VisualElement listContainer;
		private ListView listView;

		[MenuItem("Window/Database")]
		public static void Open()
		{
			GetWindow<DatabaseWindow>().Show();
		}

		private void CreateGUI()
		{
			InitializeData();
			InitializeGUI();
		}

		private void InitializeGUI()
		{
			serializedObject = new SerializedObject(Database.Instance);
			rootVisualElement.Bind(serializedObject);

			DropdownField dropdownField = new DropdownField("Type",labels, -1, OnSelection);
			
			rootVisualElement.Add(dropdownField);

			var serializedProperty = serializedObject.FindProperty(nameof(Database.activeList));
			rootVisualElement.Add(new PropertyField(serializedProperty));
		}

		private string OnSelection(string arg)
		{
			var index = labels.IndexOf(arg);
			if (index != -1)
			{
				Database.Instance.activeList = new List<DataModel>(Database.Instance.Models[keysTypes[index]].Values);
			}
			return arg;
		}

		private void InitializeData()
		{
			keysTypes = new List<Type>(Database.Instance.Models.Keys);
			keysTypes.Sort((x,y) => String.Compare(x.Name, y.Name, StringComparison.Ordinal));
			labels = keysTypes.ConvertAll(x => x.Name);

		}
	}
}
