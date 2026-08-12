using System.Collections;
using UnityEngine;

namespace Views.Entities
{
	public class EntityView : MonoBehaviour
	{
		[SerializeField] protected SpriteRenderer spriteRenderer;

		void Reset()
		{
			spriteRenderer = GetComponentInChildren<SpriteRenderer>();
		}

	}
}