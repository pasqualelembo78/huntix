using Controllers.Entities;
using Models;

namespace Controllers.Commands
{
	public class MoveAndEatCommand : Command<MoveAndEatCommand>
	{
		public EntitiesController EntitiesController { get; }
		public readonly EnemyEntityModel Enemy;
		public readonly SheepEntityModel Sheep;
		public readonly TilemapModel TilemapModel;
		public readonly MoveCommand MoveCmd;
		public readonly EatCommand EatCmd;
		public readonly int SheepPosition;
		public readonly int EnemyPosition;
		public MoveAndEatCommand(EnemyEntityModel enemy, SheepEntityModel sheep, TilemapModel tilemapModel,EntitiesController entitiesController)
		{
			EntitiesController = entitiesController;
			TilemapModel = tilemapModel;
			Enemy = enemy;
			Sheep = sheep;
			SheepPosition = sheep.PositionIndex; 
			EnemyPosition = enemy.PositionIndex; 
			MoveCmd = new MoveCommand(enemy, tilemapModel, sheep.PositionIndex);
			EatCmd = new EatCommand(enemy, sheep, tilemapModel, entitiesController);
		}

		protected override void DoAction()
		{
			MoveCmd.Do();
			TilemapModel.SwapEntities(SheepPosition, EnemyPosition);
			EatCmd.Do();
			TilemapModel.SwapEntities(SheepPosition, EnemyPosition);
		}
		protected override void UndoAction()
		{
			TilemapModel.SwapEntities(SheepPosition, EnemyPosition);
			EatCmd.Undo();
			TilemapModel.SwapEntities(SheepPosition, EnemyPosition);
			MoveCmd.Undo();
		}
	}
}