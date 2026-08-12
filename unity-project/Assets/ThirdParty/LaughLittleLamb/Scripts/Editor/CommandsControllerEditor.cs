using System;
using System.Collections.Generic;
using Controllers.Commands;
using Controllers.Entities;
using UnityEditor;
using UnityEngine;
using UnityEngine.UIElements;

[CustomEditor(typeof(CommandsController))]
public class CommandsControllerEditor : UnityEditor.Editor
{
	private const float ITEM_HEIGHT = 20;

	private readonly List<ICommand> history = new List<ICommand>();
	private readonly List<ICommand> redo = new List<ICommand>();
	private ListView historyView;
	private ListView redoView;
	
	private CommandsController commandsController;

	public override VisualElement CreateInspectorGUI()
	{
		commandsController = (CommandsController)target;
		
		VisualElement container = new VisualElement();
		
		historyView = new ListView(history, -1, MakeItem, BindHistoryItem)
		{
			headerTitle = "History",
			showFoldoutHeader = true
		};

		redoView = new ListView(redo, -1, MakeItem, BindRedoItem)
		{
			headerTitle = "Redo",
			showFoldoutHeader = true
		};

		VisualElement listsContainer = new VisualElement();
		
		listsContainer.Add(historyView);
		listsContainer.Add(redoView);
		
		container.Add(listsContainer);
		
		Refresh();
		container.RegisterCallback<DetachFromPanelEvent>(OnDetachFromPanel);

		commandsController.OnDo += Refresh;
		commandsController.OnUndo += Refresh;
		commandsController.OnRedo += Refresh;
		
		return container;
	}

	private void OnDetachFromPanel(DetachFromPanelEvent evt)
	{
		commandsController.OnDo -= Refresh;
		commandsController.OnUndo -= Refresh;
		commandsController.OnRedo -= Refresh;
	}

	private void Refresh()
	{
		history.Clear();
		history.AddRange(commandsController.HistoryStack);
		history.Reverse();
		historyView.RefreshItems();
		
		redo.Clear();
		redo.AddRange(commandsController.RedoStack);
		redo.Reverse();
		redoView.RefreshItems();
		
		//Debug.Log("Refresh");
	}

	private VisualElement MakeItem()
	{
		VisualElement element = new VisualElement()
		{
			style =
			{
				flexDirection = FlexDirection.Row,
				justifyContent = Justify.SpaceBetween
			}
		};
		element.Add(new Label(){name = "Label"});
		return element;
	}

	private void BindHistoryItem(VisualElement element, int index)
	{
		var command = history[index];
		
		var label=element.Q<Label>();
		label.text = $"[{index}] {command}";

		var btn = element.Q<Button>();
		btn?.RemoveFromHierarchy();

		if(command is SheepTurnStart or SheepTurnEnd)
		{
			label.style.color = Color.green;
		}
		else if (command is EnemyTurnStart or EnemyTurnEnd)
		{
			label.style.color = Color.magenta;
		}
		else
		{
			label.style.color = Color.white;
			element.Add(new Button(
				() => commandsController.UndoUntil(command, true))
			{
				text = "Undo",
				style = { height = ITEM_HEIGHT}
			});
		}
	}
	
	private void BindRedoItem(VisualElement element, int index)
	{
		var command = redo[index];
		
		var label= element.Q<Label>();
		label.text = $"[{index}] {command}";

		var btn = element.Q<Button>();
		btn?.RemoveFromHierarchy();

		if(command is SheepTurnStart or SheepTurnEnd)
		{
			label.style.color = Color.green;
		}
		else if (command is EnemyTurnStart or EnemyTurnEnd)
		{
			label.style.color = Color.magenta;
		}
		else
		{
			label.style.color = Color.white;
			element.Add(new Button(
				()=> commandsController.RedoUntil(command, true))
			{
				text = "Redo",
				style = { height = ITEM_HEIGHT}
			});
		}
		
	}
	
}