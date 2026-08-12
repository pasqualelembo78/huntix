using System;
using System.Collections.Generic;
using System.Text;
using Controllers.Commands;
using UnityEngine;

namespace Controllers.Commands
{
	public abstract class Command<T> : ICommand where T : Command<T>
	{
		private static int count=0;
		private static StringBuilder builder = new StringBuilder();
		public static readonly CommandBroadcaster<T> OnDo = new CommandBroadcaster<T>();
		public static readonly CommandBroadcaster<T> OnUndo = new CommandBroadcaster<T>();
		public void Do()
		{
			//Debug.Log($"{InArrows()} {GetType().Name}.Do");
			DoAction();
			OnDo.Raise((T)this);
			//Debug.Log($"{OutArrows()} {GetType().Name}.Do End");
		}

		private string InArrows()
		{
			builder.Clear();
			count++;
			for (int i = 0; i < count; i++)
			{
				builder.Append('>');
			}
			return builder.ToString();
		}
		private string OutArrows()
		{
			builder.Clear();
			for (int i = 0; i < count; i++)
			{
				builder.Append('>');
			}
			count--;
			return builder.ToString();
		}

		public void Undo()
		{
			//Debug.Log($"<<< {GetType().Name}.Undo()");
			UndoAction();
			OnUndo.Raise((T)this);
		}

		protected abstract void DoAction();
		protected abstract void UndoAction();
	}


	public interface ICommand
	{
		void Do();
		void Undo();
	}
}