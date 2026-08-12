using System.Collections;
using Controllers.Commands;
using Controllers.Entities;
using UnityEngine;
using Views;
using Views.Entities;

namespace Controllers.CommandAnimations
{
	public class ExitCommandAnimationController : CommandAnimationController
	{
		[SerializeField] private AudioClip audioClip; 
		[SerializeField] private float animDuration = .5f; 
		[SerializeField] private AnimationCurve animCurve = AnimationCurve.EaseInOut(0,0,1,1);
		
		private CommandListener<ExitCommand> doListener;
		private CommandListener<ExitCommand> undoListener;

		protected override void Initialize()
		{
			doListener = ExitCommand.OnDo.AddListener(OnDoCommand);
			undoListener = ExitCommand.OnUndo.AddListener(OnUndoCommand);
		}

		public override void Terminate()
		{
			ExitCommand.OnDo.RemoveListener(doListener);
			ExitCommand.OnUndo.RemoveListener(undoListener);
		}

		protected void OnDoCommand(ExitCommand command)
		{
			//Debug.Log($"OnCommand:{command}");
			var view = ModelToView[command.SheepModel];
			var position = command.Door.PositionIndex;
			var otherEntityModel = command.Model.GetEntity(position);
			if (otherEntityModel != null)
			{
				AnimationsController.Play(DisappearAnimation(view), command.SheepModel, position,
					otherEntityModel);
			}
			else
			{
				AnimationsController.Play(DisappearAnimation(view), command.SheepModel, position);
			}
		}

		private void OnUndoCommand(ExitCommand command)
		{
            Debug.Log("OnUndo Exit Command");
			var view = ModelToView[command.SheepModel];
			var position = command.StartPosition;
			var otherEntityModel = command.Model.GetEntity(position);
			if (otherEntityModel != null)
			{
				AnimationsController.Play(AppearAnimation(view), command.SheepModel, position,
					otherEntityModel);
			}
			else
			{
				AnimationsController.Play(AppearAnimation(view), command.SheepModel, position);
			}
		}
		
		private IEnumerator DisappearAnimation(EntityView view)
		{
			view.gameObject.SetActive(true);
			var startScale = view.transform.localScale;
			
			float t = 0;
			do
			{
				t += Time.deltaTime / animDuration;
				view.transform.localScale = Vector3.LerpUnclamped(startScale,Vector3.zero, animCurve.Evaluate(t));
				yield return null;
			} while (t<1);
			AudioSource.PlayClipAtPoint(audioClip, Vector3.zero, .1f);
		}
		
		private IEnumerator AppearAnimation(EntityView view)
		{
			view.gameObject.SetActive(true);
			var startScale = view.transform.localScale;
			float t = 0;
			do
			{
				t += Time.deltaTime / animDuration;
				view.transform.localScale = Vector3.LerpUnclamped(Vector3.one,startScale, 1-animCurve.Evaluate(t));
				yield return null;
			} while (t<1);
		}
	}
}