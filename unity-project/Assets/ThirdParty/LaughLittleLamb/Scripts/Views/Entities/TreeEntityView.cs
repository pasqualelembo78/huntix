using UnityEngine;
using Random = UnityEngine.Random;

namespace Views.Entities
{
	public class TreeEntityView : EntityView
	{
		public Sprite[] sprites;

		private void Awake()
		{
			spriteRenderer.sprite = sprites[Random.Range(0, sprites.Length)];
		}
	}
}