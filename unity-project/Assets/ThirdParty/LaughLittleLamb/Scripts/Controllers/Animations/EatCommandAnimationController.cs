using System.Collections;
using Controllers.CommandAnimations;
using Controllers.Commands;
using Controllers.Entities;
using UnityEngine;
using Views.Entities;

namespace Controllers.Animations
{
	public class EatCommandAnimationController : CommandAnimationController
	{
		[SerializeField] private AudioClip deadSheepSfx; 
		[SerializeField] private float animDuration = .5f; 
		[SerializeField] private AnimationCurve animCurve = AnimationCurve.EaseInOut(0,0,1,1);
		
		private CommandListener<EatCommand> doListener;
		private CommandListener<EatCommand> undoListener;

		protected override void Initialize()
		{
			doListener = EatCommand.OnDo.AddListener(OnDoCommand);
			undoListener = EatCommand.OnUndo.AddListener(OnUnDoCommand);
		}

		public override void Terminate()
		{
			EatCommand.OnDo.RemoveListener(doListener);
			EatCommand.OnUndo.RemoveListener(undoListener);
		}


		protected void OnDoCommand(EatCommand command)
		{
			var enemyView = ModelToView[command.Enemy];
			var sheepView = ModelToView[command.Sheep];
			var position = command.Sheep;
			AnimationsController.Play(EatAnimation(enemyView, sheepView), command.Sheep,command.Enemy, position);
		}

		private void OnUnDoCommand(EatCommand command)
		{
			var enemyView = ModelToView[command.Enemy];
			var sheepView = ModelToView[command.Sheep];
			var position = command.Sheep;
			AnimationsController.Play(UnEatAnimation(enemyView, sheepView), command.Sheep,command.Enemy, position);
		}
		
		private IEnumerator EatAnimation(EntityView enemyView, EntityView sheepView)
		{
			sheepView.gameObject.SetActive(false);
			float t = 0;
			do
			{
				t += Time.deltaTime / animDuration;
				enemyView.transform.localScale = Vector3.one + Vector3.LerpUnclamped(Vector3.zero,Vector3.one, animCurve.Evaluate(t));
				yield return null;
			} while (t<1);
			AudioSource.PlayClipAtPoint(deadSheepSfx, Vector3.zero, .1f);
		}
		
		private IEnumerator UnEatAnimation(EntityView enemyView, EntityView sheepView)
		{
			float t = 0;
			do
			{
				t += Time.deltaTime / animDuration;
				enemyView.transform.localScale = Vector3.one + Vector3.LerpUnclamped(Vector3.zero,Vector3.one, animCurve.Evaluate(t));
				yield return null;
			} while (t<1);
			sheepView.gameObject.SetActive(true);
		}

	}
}