using Models;

namespace Controllers.Commands
{
	public class LookCommand : Command<LookCommand>
	{
		private readonly Orientation startOrientation;
		private readonly Orientation endOrientation;
		private SheepEntityModel target;

		public LookCommand(SheepEntityModel target, Orientation endOrientation)
		{
			startOrientation = target.LookDirection;
			this.endOrientation = endOrientation;
			this.target = target;
		}

		protected override void DoAction()
		{
			target.LookDirection.Set(endOrientation);
		}

		protected override void UndoAction()
		{
			target.LookDirection.Set(startOrientation);
		}
	}
}