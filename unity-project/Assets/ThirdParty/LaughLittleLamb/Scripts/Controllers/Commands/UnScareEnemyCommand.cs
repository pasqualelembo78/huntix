using Models;

namespace Controllers.Commands
{
	public class UnScareEnemyCommand : Command<UnScareEnemyCommand>
	{
		public readonly EnemyEntityModel EnemyEntityModel;

		public UnScareEnemyCommand(EnemyEntityModel enemyEntityModel)
		{
			this.EnemyEntityModel = enemyEntityModel;
		}

		protected override void DoAction()
		{
			EnemyEntityModel.IsScared = false;
		}

		protected override void UndoAction()
		{
			EnemyEntityModel.IsScared = true;
		}
	}
}