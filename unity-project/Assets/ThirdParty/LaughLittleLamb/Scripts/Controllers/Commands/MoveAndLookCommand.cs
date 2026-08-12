using Models;

namespace Controllers.Commands
{
	public class MoveAndLookCommand : Command<MoveAndLookCommand>
	{
		public readonly MoveCommand MoveCommand;
		public readonly LookCommand LookCommand;

		public MoveAndLookCommand(
			SheepEntityModel target,
			TilemapModel model,
			Orientation endOrientation,
			int endPosition)
		{
			LookCommand = new LookCommand(target, endOrientation);
			MoveCommand = new MoveCommand(target, model, endPosition);
		}
		protected override void DoAction()
		{
			LookCommand.Do();
			MoveCommand.Do();
		}
		protected override void UndoAction()
		{
			MoveCommand.Undo();
			LookCommand.Undo();
		}
	}
}