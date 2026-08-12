using Models;

namespace Controllers.Commands
{
	public class ScareEnemyCommand : Command<ScareEnemyCommand>
	{
		public readonly EnemyEntityModel EnemyEntityModel;

		public ScareEnemyCommand(EnemyEntityModel enemy)
		{
			EnemyEntityModel = enemy;
		}

		protected override void DoAction()
		{
			EnemyEntityModel.IsScared = true;
		}

		protected override void UndoAction()
		{
			EnemyEntityModel.IsScared = false;
		}
	}
}