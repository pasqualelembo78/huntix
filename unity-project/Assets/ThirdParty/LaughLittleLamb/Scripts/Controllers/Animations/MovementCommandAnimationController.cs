using System.Collections;
using Controllers.CommandAnimations;
using Controllers.Commands;
using Controllers.Entities;
using UnityEngine;
using Views.Entities;

namespace Controllers.Animations
{
	public class MovementCommandAnimationController : CommandAnimationController
	{
		[SerializeField] private AudioClip lambStepClip; 
		[SerializeField] private AudioClip enemyStepClip; 
		
		[SerializeField] private float animMoveSpeed = 3; 
		[SerializeField] private AnimationCurve animMoveCurve = AnimationCurve.EaseInOut(0,0,1,1);
		private CommandListener<MoveCommand> doListener;
		private CommandListener<MoveCommand> undoListener;

		protected override void Initialize()
		{
			doListener = MoveCommand.OnDo.AddListener(OnDoCommand);
			undoListener = MoveCommand.OnUndo.AddListener(OnUnDoCommand);
		}

		public override void Terminate()
		{
			MoveCommand.OnDo.RemoveListener(doListener);
			MoveCommand.OnUndo.RemoveListener(undoListener);
		}

		private void OnUnDoCommand(MoveCommand command)
		{
			var view = ModelToView[command.EntityModel];
			var position = command.StartPosition;
			var otherEntityModel = command.TilemapModel.GetEntity(position);
			if (otherEntityModel != null)
			{
				AnimationsController.Play(
					MoveAnimation(view, TilemapController.GetWorldPosition(position),false),
					view,
					command.EntityModel,
					position,
					otherEntityModel);
			}
			else
			{
				AnimationsController.Play(
					MoveAnimation(view, TilemapController.GetWorldPosition(position),false), 
					view,
					command.EntityModel,
					position);
			}
		}

		protected void OnDoCommand(MoveCommand command)
		{
			//Debug.Log($"OnCommand:{command}");
			var view = ModelToView[command.EntityModel];
			var position = command.EndPosition;
			var otherEntityModel = command.TilemapModel.GetEntity(position);
			if (otherEntityModel != null)
			{
				AnimationsController.Play(
					MoveAnimation(view, TilemapController.GetWorldPosition(position),true),
					view,
					command.EntityModel, 
					position,
					otherEntityModel);
			}
			else
			{
				AnimationsController.Play(
					MoveAnimation(view, TilemapController.GetWorldPosition(position),true),
					view,
					command.EntityModel
					, position);
			}
		}

		private IEnumerator MoveAnimation(EntityView view, Vector3 endPosition, bool playSound)
		{
			//Debug.Log($"MoveAnimation:{view.name} {endPosition}");
			view.gameObject.SetActive(true);
			var startPosition = view.transform.position;

			if(playSound)
			{
				if (view is EnemyEntityView)
				{
					AudioSource.PlayClipAtPoint(enemyStepClip, Vector3.zero, .05f);
				}
				else if (view is SheepEntityView)
				{
					AudioSource.PlayClipAtPoint(lambStepClip, Vector3.zero, 1.5f);
				}
			}
			float t = 0;
			float duration = Vector2.Distance(startPosition, endPosition) / animMoveSpeed; 
			do
			{
				t += Time.deltaTime / duration;
				view.transform.position = Vector3.LerpUnclamped(startPosition,endPosition,animMoveCurve.Evaluate(t));
				yield return null;
			} while (t<1);
		}
	}
}