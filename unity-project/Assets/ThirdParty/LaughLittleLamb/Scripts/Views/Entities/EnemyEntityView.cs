using System.Collections;
using UnityEngine;

namespace Views.Entities
{
	public class EnemyEntityView : EntityView
	{
		[SerializeField] private Sprite scaredSprite;
		[SerializeField] private Color scaredColor;
		[SerializeField] private float scareDuration = .5f;
		[SerializeField] private AnimationCurve scareScaleCurve;

		[SerializeField] private Sprite normalSprite;
		[SerializeField] private Color normalColor;
		[SerializeField] private float unScareDuration = .5f;
		[SerializeField] private AnimationCurve unScareScaleCurve;
		
		public void LookRight() => spriteRenderer.flipX = false;
		public void LookLeft() => spriteRenderer.flipX = true;

		public IEnumerator ScareAnimation()
		{
			spriteRenderer.sprite = scaredSprite;
			var startColor = spriteRenderer.color;
			
			float t = 0;
			do
			{
				t += Time.deltaTime / scareDuration;
				transform.localScale = Vector3.one * scareScaleCurve.Evaluate(t);
				spriteRenderer.color = Color.Lerp(startColor,scaredColor, t);

				yield return null;
			} while (t<1);
		}
		
		public IEnumerator UnScaredAnimation()
		{
			spriteRenderer.sprite = normalSprite;
			var startColor = spriteRenderer.color;
			float t = 0;
			do
			{
				t += Time.deltaTime / unScareDuration;
				transform.localScale = Vector3.one * unScareScaleCurve.Evaluate(t);
				spriteRenderer.color = Color.Lerp(startColor,normalColor, t);
				yield return null;
			} while (t<1);
		}
	}
}