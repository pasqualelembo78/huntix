using UnityEngine;

namespace Views.Entities
{
	public class SheepEntityView : EntityView
	{
		[SerializeField] private Sprite[] sprites;

		public void SetLookDirection(int index) => spriteRenderer.sprite = sprites[index];

	}
}