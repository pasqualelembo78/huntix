using System.Collections;
using Controllers.CommandAnimations;
using Controllers.Commands;
using Models;
using UnityEngine;
using Views.Entities;

namespace Controllers.Animations
{
	public class ScaredEnemyAnimationController : CommandAnimationController
	{
		[SerializeField] private AudioClip scaredClip;
		private CommandListener<ScareEnemyCommand> onDoScareListener;
		private CommandListener<ScareEnemyCommand> onUnScareListener;
		
		private CommandListener<UnScareEnemyCommand> onDoUnScareListener;
		private CommandListener<UnScareEnemyCommand> onUnUnScareListener;

		protected override void Initialize()
		{
			onDoScareListener = ScareEnemyCommand.OnDo.AddListener(OnScareEnemyCommandDo);
			onUnScareListener = ScareEnemyCommand.OnUndo.AddListener(OnScareEnemyCommandUndo);
			onDoUnScareListener = UnScareEnemyCommand.OnDo.AddListener(UnScareEnemyCommandDo);
			onUnUnScareListener = UnScareEnemyCommand.OnUndo.AddListener(UnScareEnemyCommandUndo);
		}

		public override void Terminate()
		{
			ScareEnemyCommand.OnDo.RemoveListener(onDoScareListener);
			ScareEnemyCommand.OnUndo.RemoveListener(onUnScareListener);
			
			UnScareEnemyCommand.OnDo.RemoveListener(onDoUnScareListener);
			UnScareEnemyCommand.OnUndo.RemoveListener(onUnUnScareListener);
		}

		private void OnScareEnemyCommandDo(ScareEnemyCommand obj) => PlayScare(obj.EnemyEntityModel);

		private void OnScareEnemyCommandUndo(ScareEnemyCommand obj) => PlayUnScare(obj.EnemyEntityModel);

		private void UnScareEnemyCommandDo(UnScareEnemyCommand obj)=> PlayUnScare(obj.EnemyEntityModel);
		private void UnScareEnemyCommandUndo(UnScareEnemyCommand obj)=> PlayScare(obj.EnemyEntityModel);


		private void PlayScare(EnemyEntityModel enemyEntityModel)
		{
			var view = (EnemyEntityView)ModelToView[enemyEntityModel];
			AnimationsController.Play(Play(view.ScareAnimation()), view, enemyEntityModel, enemyEntityModel.PositionIndex);
		}

		private void PlayUnScare(EnemyEntityModel enemyEntityModel)
		{
			var view = (EnemyEntityView)ModelToView[enemyEntityModel];
			AnimationsController.Play(view.UnScaredAnimation(), view, enemyEntityModel, enemyEntityModel.PositionIndex);
		}

		private IEnumerator Play(IEnumerator animation)
		{
			AudioSource.PlayClipAtPoint(scaredClip, Vector3.zero, .15f);
			yield return animation;
		}
	}
}