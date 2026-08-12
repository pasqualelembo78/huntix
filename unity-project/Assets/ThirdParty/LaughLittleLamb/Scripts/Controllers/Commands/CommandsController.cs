using System;
using System.Collections.Generic;
using UnityEngine;

namespace Controllers.Commands
{
	public class CommandsController : MonoBehaviour
	{
		public event Action OnDo;
		public event Action OnRedo;
		public event Action OnUndo;
		
		private readonly Stack<ICommand> historyStack = new Stack<ICommand>();
		private readonly Stack<ICommand> redoStack = new Stack<ICommand>();
		public Stack<ICommand> HistoryStack => historyStack;
		public Stack<ICommand> RedoStack => redoStack;

		public void Initialize()
		{
			historyStack.Clear();
			redoStack.Clear();
		}

	
		public void Terminate()
		{
		}

		public void Do(ICommand command)
		{
			//Debug.Log($"DO:{command}");
			if (redoStack.Count > 0)
			{
				redoStack.Clear();
			}
			historyStack.Push(command);
			command.Do();
			OnDo?.Invoke();
		}

		public void Do(IEnumerable<ICommand> commands)
		{
			foreach (var command in commands)
			{
				Do(command);
			}
		}

		public void Redo()
		{
			if (redoStack.Count > 0)
			{
				var command = redoStack.Pop();
				command.Do();
				//Debug.Log($"Redo:{command}");
				historyStack.Push(command);
				OnRedo?.Invoke();
			}
		}

		public ICommand Undo()
		{
			if (historyStack.Count > 0)
			{
				var command = historyStack.Pop();
				//Debug.Log($"Undo:{command}");
				redoStack.Push(command);
				command.Undo();
				OnUndo?.Invoke();
				return command;
			}
			return null;
		}

		public void UndoTwice()
		{
			for (int i = 0; i < 2; i++) Undo();
		}
		
		public void RedoTwice()
		{
			for (int i = 0; i < 2; i++) Redo();
		}

		public void RedoUntil(Predicate<ICommand> predicate, bool inclusive)
		{
			while (redoStack.TryPeek(out var command) && !predicate(command))
			{
				Redo();
			}
			if (redoStack.Count > 0 && inclusive)
			{
				Redo();
			}
		}
		
		public void UndoUntil(Predicate<ICommand> predicate, bool inclusive)
		{
			//Debug.Log("UndoUntil");
			while (historyStack.TryPeek(out var command) && !predicate(command))
			{
				Undo();
			}
			if (historyStack.Count > 0 && inclusive)
			{
				Undo();
			}
		}
		public void UndoUntil(ICommand command, bool inclusive) => UndoUntil(x => x == command, inclusive);
		public void RedoUntil(ICommand command, bool inclusive) => RedoUntil(x => x == command, inclusive);

		public void UndoUntil<T>(bool inclusive) where T : ICommand => UndoUntil(x => x is T, inclusive);
		public void RedoUntil<T>(bool inclusive) where T : ICommand=> RedoUntil(x => x is T, inclusive);

		public void ClearAll()
		{
			historyStack.Clear();
			redoStack.Clear();			
		}

	}

	public abstract class CommandListener : IComparable<CommandListener> 
	{
		public readonly int Priority;
		public CommandListener(int priority)
		{
			Priority = priority;
		}

		public abstract void Raise(object value);

		public int CompareTo(CommandListener other)
		{
			if (ReferenceEquals(this, other)) return 0;
			if (ReferenceEquals(null, other)) return 1;
			return Priority.CompareTo(other.Priority);
		}
	}
	
	public class CommandListener<T> : CommandListener where T : Command<T>
	{
		private readonly Action<T> action;
		public CommandListener(Action<T> action, int priority = 0) : base(priority)
		{
			this.action = action;
		}
		public override void Raise(object value) => action.Invoke((T)value);
	}

}