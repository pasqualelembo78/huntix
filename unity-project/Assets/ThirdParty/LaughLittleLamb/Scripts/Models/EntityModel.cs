using System;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public abstract class EntityModel : DataModel<EntityModel>, ITileEntity
	{
		public event Action<ITileEntity,int> OnPositionChange;
		private int positionIndex;
		public int PositionIndex
		{
			get => positionIndex;
			internal set
			{
				if (positionIndex != value)
				{
					positionIndex = value;
					OnPositionChange?.Invoke(this, value);
				}
			}
		}
		protected EntityModel(int index)
		{
			positionIndex = index;
		}

		public override string ToString() => $"{GetType().Name} {Index}";
	}

	public interface IMove { }

	public interface ITileEntity
	{
		public event Action<ITileEntity,int> OnPositionChange;
		public int PositionIndex { get; }
	}
}


