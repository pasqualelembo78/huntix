namespace Controllers.Commands
{
	public class SheepTurnStart : Command<SheepTurnStart>, ITurnStartCommand
	{
		protected override void DoAction() { }
		protected override void UndoAction() { }
	}
	
	public class SheepTurnEnd : Command<SheepTurnEnd>, ITurnEndCommand
	{
		protected override void DoAction() { }
		protected override void UndoAction() { }
	}
	
	public class EnemyTurnStart : Command<EnemyTurnStart>, ITurnStartCommand
	{
		protected override void DoAction() { }
		protected override void UndoAction() { }
	}
	
	public class EnemyTurnEnd : Command<EnemyTurnEnd>, ITurnEndCommand
	{
		protected override void DoAction() { }
		protected override void UndoAction() { }
	}

	public interface ITurnStartCommand
	{
	}

	public interface ITurnEndCommand
	{
	}
}