using Controllers.Entities;
using Models;

namespace Controllers.Commands
{
	public class ExitCommand : Command<ExitCommand>
	{
		public readonly int StartPosition;
		public readonly SheepEntityModel SheepModel;
		public readonly DoorEntityModel Door;
		public readonly TilemapModel Model;
		public readonly EntitiesController EntitiesController;
		public ExitCommand(SheepEntityModel sheep, DoorEntityModel door, TilemapModel model, EntitiesController entitiesController)
		{
			this.EntitiesController = entitiesController;
			Door = door;
			Model = model;
			StartPosition = sheep.PositionIndex;
			SheepModel = sheep;
		}

		protected override void DoAction()
		{
			Model.RemoveEntity(SheepModel);
			EntitiesController.RemoveSheep(SheepModel);
			Model.SavedSheeps.Add(SheepModel);
			Model.ActiveSheeps.Remove(SheepModel);
		}

		protected override void UndoAction()
		{
			Model.ActiveSheeps.Add(SheepModel);
			Model.SavedSheeps.Remove(SheepModel);
			EntitiesController.AddSheep(SheepModel);
			Model.AddEntity(SheepModel, StartPosition);
		}
	}


	public class LookMoveAndExitCommand : Command<LookMoveAndExitCommand>
	{
		public readonly MoveCommand MoveCmd;
		public readonly LookCommand LookCmd;
		public readonly ExitCommand ExitCmd;
		public readonly TilemapModel TilemapModel;
		public readonly DoorEntityModel Door;
		public readonly SheepEntityModel Sheep;
		public LookMoveAndExitCommand(SheepEntityModel sheep,
		                          DoorEntityModel door,
		                          TilemapModel model,
		                          Orientation endOrientation,
		                          EntitiesController entitiesController)
		{
			Sheep = sheep;
			Door = door;
			TilemapModel = model;
			var endPosition = door.PositionIndex;
			LookCmd = new LookCommand(sheep, endOrientation);
			MoveCmd = new MoveCommand(sheep, model, endPosition);
			ExitCmd = new ExitCommand(sheep, door, model, entitiesController);
		}
		
		protected override void DoAction()
		{
			LookCmd.Do();
			MoveCmd.Do();
			TilemapModel.SwapEntities(Door.PositionIndex,Sheep.PositionIndex);
			ExitCmd.Do();
		}

		protected override void UndoAction()
		{
			ExitCmd.Undo();
			TilemapModel.SwapEntities(Door.PositionIndex,Sheep.PositionIndex);
			MoveCmd.Undo();
			LookCmd.Undo();
		}
	}
}