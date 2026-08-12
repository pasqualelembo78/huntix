using System;
using System.Collections.Generic;

namespace Controllers.Commands
{
	public abstract class CommandBroadcaster
	{
	}
	
	public class CommandBroadcaster<T> : CommandBroadcaster where T : Command<T>
	{
		private readonly List<CommandListener<T>> listeners = new List<CommandListener<T>>();
		public CommandListener<T> AddListener(CommandListener<T> listener)
		{
			var index = listeners.BinarySearch(listener);
			if (index < 0)
				index = ~index;
			listeners.Insert(index, listener);
			return listener;
		}

		public void RemoveListener(CommandListener<T> listener)
		{
			listeners.Remove(listener);
		}

		public CommandListener<T> AddListener(Action<T> action, int priority = 0) =>
			AddListener(new CommandListener<T>(action, priority));

		public void Raise(T command)
		{
			foreach (var listener in listeners)
			{
				listener.Raise(command);
			}
		}
	}
}