using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace Views.Entities
{
	public class LightBeamView : MonoBehaviour
	{
		[SerializeField] private SpriteRenderer prefab;
		[SerializeField] private Color lightColor;
		[SerializeField] private float turnOnAnimDuration = .5f;
		[SerializeField] private float turnOffAnimDuration = .5f;
		private Color clearColor;

		private readonly List<SpriteRenderer> activeSprites = new List<SpriteRenderer>();

		private void Awake()
		{
			clearColor = lightColor;
			clearColor.a = 0;
		}

		public IEnumerator TurnOnAnimation(Vector3[] positions)
		{
			for (int i = 0; i < positions.Length; i++)
			{
				var spriteRenderer = Instantiate(prefab, positions[i], Quaternion.identity, transform);
				activeSprites.Add(spriteRenderer);
				spriteRenderer.color = clearColor;
			}

			float t = 0;
			float stepDuration = turnOnAnimDuration;
			do
			{
				t += Time.deltaTime / turnOnAnimDuration;
				for (int i = 0; i < activeSprites.Count; i++)
				{
					activeSprites[i].color = Color.Lerp(clearColor, lightColor, t/(i * stepDuration));
				}

				yield return null;
			} while (t < 1);
		}

		public IEnumerator TurnOffAnimation(Action callback)
		{
			float t = 1;
			float stepDuration = turnOffAnimDuration;
			do
			{
				t -= Time.deltaTime / turnOffAnimDuration;
				for (int i = 0; i < activeSprites.Count; i++)
				{
					activeSprites[i].color = Color.Lerp(clearColor, lightColor, t/(i * stepDuration));
				}

				yield return null;
			} while (t > 0);

			for (int i = activeSprites.Count - 1; i >= 0; i--)
			{
				Destroy(activeSprites[i].gameObject);
			}
			activeSprites.Clear();
			callback.Invoke();
		}
	}
}
