using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using Controllers.AI;
using Controllers.Commands;
using Controllers.Entities;
using Cysharp.Threading.Tasks;
using Models;
using UnityEngine;
using Random = UnityEngine.Random;

namespace Controllers.Player
{
	public class AutomaticSheepController : MonoBehaviour, IPlayer
	{
		private readonly List<List<ICommand>> options = new List<List<ICommand>>();

		private CommandsController commandsController;

		private bool failed;
		private EntitiesController entitiesController;
		private int solutionMaxLength = 20;
		public bool IsOutOfOptions => options.Count == 0;

		public int CurrentSolutionLength => options.Count;
		
		public void Initialize(CommandsController commandsController, EntitiesController entitiesController, int solutionMaxLength)
		{
			this.solutionMaxLength = solutionMaxLength;
			this.entitiesController = entitiesController;
			this.commandsController = commandsController;
		}

		public void Backtrack() => failed = true;

		public async UniTask TakeTurnAsync(CancellationToken token)
		{
			//Debug.unityLogger.logEnabled = false;
			//Debug.Log("Start Turn");
			commandsController.Do(new SheepTurnStart());

			var color = failed ? "yellow" : "white";
			List<ICommand> currentOptions;
			if (failed || options.Count > solutionMaxLength)
			{
				//Debug.Log("<");
				failed = false;
				commandsController.Undo();
				commandsController.UndoUntil<SheepTurnStart>(false);
				currentOptions = options[^1];
				while (currentOptions.Count == 0)
				{
					//Debug.Log("<");
					commandsController.Undo();
					commandsController.UndoUntil<SheepTurnStart>(false);
					options.RemoveAt(options.Count-1);
					currentOptions = options[^1];
				}
			}
			else
			{
				currentOptions = CreateOptions();
				options.Add(currentOptions);
			}

			if (options.Count == 0)
			{
				//Debug.unityLogger.logEnabled = true;
				Debug.Log("Cannot find Solution");
				await UniTask.Never(destroyCancellationToken);
			}
			int index = currentOptions.Count-1;
			//int index = Random.Range(0, currentOptions.Count);
			var command = currentOptions[index];

			StringBuilder builder = new StringBuilder();

			builder.Append($"[{options.Count}]	");
			for (int i = 0; i < options.Count; i++)
			{
				builder.Append($"|{options[i].Count}");
			}
			//builder.Append($"|");

			builder.Append($"|{index+1}");
			//Debug.Log($"{options.Count}:{index}");
			Debug.Log($"<color={color}>{builder}</color>");
			
			currentOptions.Remove(command);
		
			commandsController.Do(command);
			commandsController.Do(new SheepTurnEnd());
		
			//Debug.unityLogger.logEnabled = true;
			//Debug.Log($"Solution Current Length:{options.Count}");
		
			//Debug.Log("End Turn");
			//return UniTask.CompletedTask;
		}

		private List<ICommand> CreateOptions()
		{
			List<ICommand> values = new List<ICommand>();

			values.Add(entitiesController.CreateMoveTogetherCommand(
				Array.ConvertAll(entitiesController.SheepEntityModels.ToArray(),
					x => (x, Orientation.Down))));
			values.Add(entitiesController.CreateMoveTogetherCommand(
				Array.ConvertAll(entitiesController.SheepEntityModels.ToArray(),
					x => (x, Orientation.Up))));
			values.Add(entitiesController.CreateMoveTogetherCommand(
				Array.ConvertAll(entitiesController.SheepEntityModels.ToArray(),
					x => (x, Orientation.Left))));
			values.Add(entitiesController.CreateMoveTogetherCommand(
				Array.ConvertAll(entitiesController.SheepEntityModels.ToArray(),
					x => (x, Orientation.Right))));

			values.Add(new CompositeCommand(entitiesController.CreateLookTogetherCommands(entitiesController.SheepEntityModels,Orientation.Up)));
			values.Add(new CompositeCommand(entitiesController.CreateLookTogetherCommands(entitiesController.SheepEntityModels,Orientation.Down)));
			values.Add(new CompositeCommand(entitiesController.CreateLookTogetherCommands(entitiesController.SheepEntityModels,Orientation.Left)));
			values.Add(new CompositeCommand(entitiesController.CreateLookTogetherCommands(entitiesController.SheepEntityModels,Orientation.Right)));
        
			return values;
		}

		public string PrintSolution()
		{
			return string.Empty;
			StringBuilder builder = new StringBuilder();
			List<ICommand> commands = new List<ICommand>(commandsController.HistoryStack);
			commands.Reverse();
			foreach (ICommand command in commands)
			{
				if(command is CompositeCommand composite)
				{
					builder.AppendLine(composite.Commands[0].ToString());
				}
				else
				{
					builder.AppendLine(command.ToString());
				}
			
			}
			return builder.ToString();
		}

		public void Failed() => failed = true;
	}
}
