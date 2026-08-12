using Controllers.Entities;
using Models;

namespace Controllers.Commands
{
	public class EatCommand : Command<EatCommand>
	{
		public readonly EnemyEntityModel Enemy;
		public readonly SheepEntityModel Sheep;
		public readonly TilemapModel TilemapModel;
		public readonly EntitiesController EntitiesController;
		public readonly int SheepPosition;
		private TurnLightOffCommand turnLightOff;
		public EatCommand(EnemyEntityModel enemy, SheepEntityModel sheep, TilemapModel tilemapModel, EntitiesController entitiesController)
		{
			Enemy = enemy;
			Sheep = sheep;
			SheepPosition = sheep.PositionIndex;
			TilemapModel = tilemapModel;
			EntitiesController = entitiesController;
		}

		protected override void DoAction()
		{
			turnLightOff = new TurnLightOffCommand(Sheep, TilemapModel);
			turnLightOff.Do();
			EntitiesController.KillSheep(Sheep);
		}

		protected override void UndoAction()
		{
			turnLightOff.Undo();
			turnLightOff = null;
			EntitiesController.ReviveSheep(Sheep, SheepPosition);
		}
	}
}