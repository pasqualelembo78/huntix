using UnityEngine;

namespace Views.Entities
{
	public class DoorEntityView : EntityView
	{
		[SerializeField] private Sprite openSprite;
		[SerializeField] private Sprite closeSprite;

		public void SetOpen(bool value)
		{
			if (value)
				SetOpen();
			else
				SetClose();
		}
		public void SetOpen() => spriteRenderer.sprite = openSprite;
		public void SetClose() => spriteRenderer.sprite = closeSprite;

		
	}
}