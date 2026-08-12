using Models;

namespace Controllers.Commands
{
	public class MoveCommand : Command<MoveCommand>
	{
		public readonly int StartPosition;
		public readonly int EndPosition;
		public readonly EntityModel EntityModel;
		public readonly TilemapModel TilemapModel;
		
		public MoveCommand(EntityModel entityModel, TilemapModel tilemapModel, int endPosition)
		{
			this.TilemapModel = tilemapModel;
			this.EndPosition = endPosition;
			this.EntityModel = entityModel;
			StartPosition = entityModel.PositionIndex;
		}
		protected override void DoAction()
		{
			TilemapModel.SwapEntities(StartPosition,EndPosition);
		}
		protected override void UndoAction()
		{
			TilemapModel.SwapEntities(StartPosition,EndPosition);
		}

		//public override string ToString() => $"{EntityModel.GetType().Name} {StartPosition}=>{EndPosition}";
	}
}