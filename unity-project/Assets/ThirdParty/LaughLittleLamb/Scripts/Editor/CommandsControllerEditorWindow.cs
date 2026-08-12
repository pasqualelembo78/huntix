using System;
using Controllers.Commands;
using UnityEditor;
using UnityEditor.UIElements;
using UnityEngine.UIElements;

namespace Game.Editor
{
	public class CommandsControllerEditorWindow : EditorWindow
	{
		private UnityEditor.Editor cacheEditor;

		[MenuItem("Window/Commands")]
		public static void Open()
		{
			GetWindow<CommandsControllerEditorWindow>().Show();
		}

		private void CreateGUI()
		{
			rootVisualElement.Clear();
			CommandsController commandsController = FindFirstObjectByType<CommandsController>();

			if (commandsController == null)
			{
				rootVisualElement.Add(new Button(CreateGUI) { text = "Refresh" });
			}
			else
			{
				UnityEditor.Editor.CreateCachedEditor(commandsController, null,ref cacheEditor);
				var inspectorGUI = cacheEditor.CreateInspectorGUI();
				inspectorGUI.Bind(new SerializedObject(commandsController));
				rootVisualElement.Add(inspectorGUI);
			}
		}
	}
}