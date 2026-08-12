using System.Collections.Generic;
using Models;

namespace Controllers.Commands
{
	public class TurnLightOnCommand : Command<TurnLightOnCommand>
	{
		public readonly SheepEntityModel SourceEntity;
		public Orientation LightDirection;
		public readonly TilemapModel TilemapModel;
		public readonly int LightLength;
		public LightBeamModel LightBeam;

		public TurnLightOnCommand(SheepEntityModel sourceEntity, TilemapModel tilemapModel, int lightLength)
		{
			LightLength = lightLength;
			this.TilemapModel = tilemapModel;
			SourceEntity = sourceEntity;
		}

		protected override void DoAction()
		{
			LightDirection = SourceEntity.LookDirection;
			LightBeam = TilemapModel.CreateLightBeam(SourceEntity, LightDirection, LightLength);
		}

		protected override void UndoAction()
		{
			TilemapModel.RemoveLightBeam(LightBeam);
		}
	}
}