using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class EnemyEntityModel : EntityModel, IMove
	{
		public readonly Observable<bool> IsLookingRight = new(true);
		private bool isScared = false;

		public bool IsScared
		{
			get => isScared;
			set
			{
				isScared = value;
			}
		}

		public EnemyEntityModel(int index) : base(index) { }
	}
}