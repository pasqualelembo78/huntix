using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class SheepEntityModel : EntityModel, IMove
	{
		public readonly Observable<Orientation> LookDirection;
		
		public SheepEntityModel(int index, Orientation lookDirection) : base(index)
		{
			LookDirection = new Observable<Orientation>(lookDirection);
		}

	}
}