using Models;

namespace Controllers.Commands
{
	public class TurnLightOffCommand : Command<TurnLightOffCommand>
	{
		public readonly EntityModel SourceEntity;
		public readonly TilemapModel TilemapModel;
		public LightBeamModel LightBeam;

		public TurnLightOffCommand(EntityModel sourceEntity, TilemapModel tilemapModel)
		{
			this.TilemapModel = tilemapModel;
			SourceEntity = sourceEntity;
		}

		protected override void DoAction()
		{
			LightBeam = TilemapModel.GetLightBeam(SourceEntity);
			TilemapModel.RemoveLightBeam(LightBeam);
		}

		protected override void UndoAction()
		{
			TilemapModel.AddLightBeam(LightBeam);
		}
	}
}