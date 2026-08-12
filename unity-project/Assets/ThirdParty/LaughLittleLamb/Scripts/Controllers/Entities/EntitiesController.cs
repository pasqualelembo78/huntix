using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using Controllers.Commands;
using Controllers.Level;
using Models;
using UnityEngine;
using Views;
using Views.Entities;

namespace Controllers.Entities
{
	public class EntitiesController : MonoBehaviour
	{
		private CommandsController commandsController;

		[SerializeField] private EnemyEntityView enemyViewPrefab;
		[SerializeField] private SheepEntityView sheepViewPrefab;
		[SerializeField] private DoorEntityView doorViewPrefab;
		[SerializeField] private TreeEntityView treeViewPrefab;
		
		private TilemapModel tilemapModel;
		private List<SheepEntityModel> sheepModels;
        public IReadOnlyList<SheepEntityModel> SheepEntityModels => sheepModels;
		private List<EnemyEntityModel> enemyModels;
        public IReadOnlyList<EnemyEntityModel> EnemyEntityModels => enemyModels;
		 private List<DoorEntityModel> doorModels;
        public IReadOnlyList<DoorEntityModel> DoorModels => doorModels;
		private List<TreeEntityModel> treeModels;
        public IReadOnlyList<TreeEntityModel> TreeModels => treeModels;
        public Dictionary<EntityModel, EntityView> ModelToView => modelToView;

        private readonly Dictionary<EntityModel, EntityView> modelToView = new Dictionary<EntityModel, EntityView>();

		private TilemapController tilemapController;

		[SerializeField] private int lightLength = 3;
		private CommandListener<TurnLightOnCommand> onTurnLightOnListener;
		private CommandListener<TurnLightOffCommand> onTurnLightOffListener;
		private CommandListener<MoveCommand> onMoveCommandListener;

		public void Initialize(CommandsController commands, TilemapModel model, TilemapController tilemapController)
		{
			this.tilemapController = tilemapController;
			commandsController = commands;
			tilemapModel = model;
			
			enemyModels = new List<EnemyEntityModel>();
			sheepModels = new List<SheepEntityModel>();
			doorModels = new List<DoorEntityModel>();
			treeModels = new List<TreeEntityModel>();
			
			foreach (var entity in model.GetAllEntities())
			{
				if (entity is TreeEntityModel treeModel)
				{
					treeModels.Add(treeModel);
					var view = Instantiate(
						treeViewPrefab,
						GetWorldPosition(entity.PositionIndex),
						Quaternion.identity, 
						transform);

					view.name = $"Tree {treeModels.Count}";
					modelToView.Add(entity, view);
				}
				else if (entity is EnemyEntityModel enemy)
				{
					enemyModels.Add(enemy);
					var view = Instantiate(
						enemyViewPrefab,
						GetWorldPosition(entity.PositionIndex),
						Quaternion.identity,
						transform);
					view.name = $"Enemy {enemyModels.Count}";
					modelToView.Add(entity, view);
				}
				else if (entity is SheepEntityModel sheepModel)
				{
					sheepModels.Add(sheepModel);
					var view = Instantiate(
						sheepViewPrefab,
						GetWorldPosition(entity.PositionIndex),
						Quaternion.identity,		
						transform);
					sheepModel.LookDirection.OnUpdate += x =>
					{
						if(x != Orientation.None)
						{view.SetLookDirection((int)x - 1);}
					};
					view.name = $"Sheep {sheepModels.Count}";
					view.SetLookDirection((int)sheepModel.LookDirection.Value-1);
					modelToView.Add(entity, view);
				}
				else if (entity is DoorEntityModel door)
				{
					doorModels.Add(door);
					var view = Instantiate(
						doorViewPrefab,
						GetWorldPosition(entity.PositionIndex),
						Quaternion.identity,			
						transform);
					view.SetOpen(true);
					view.name = $"Door {doorModels.Count}";
					modelToView.Add(entity, view);
				}
			}

			TurnOnSheepLights();

			onTurnLightOnListener = TurnLightOnCommand.OnDo.AddListener(OnTurnLightOnCommand, 120);
			onTurnLightOffListener = TurnLightOffCommand.OnDo.AddListener(OnTurnLightOffCommand, 120);
			onMoveCommandListener = MoveCommand.OnDo.AddListener(OnMoveCommand, 120);
		}


		public void Terminate()
		{
			MoveCommand.OnDo.RemoveListener(onMoveCommandListener);
			TurnLightOnCommand.OnDo.RemoveListener(onTurnLightOnListener);
			TurnLightOffCommand.OnDo.RemoveListener(onTurnLightOffListener);
		}

		private void OnMoveCommand(MoveCommand obj)
		{
			if (obj.EntityModel is EnemyEntityModel enemyEntityModel)
			{
				if (enemyEntityModel.IsScared && !tilemapModel.IsIlluminated(enemyEntityModel.PositionIndex, out _))
				{
					//Debug.Log($"Reacting to {obj.GetType().Name}");
					commandsController.Do(new UnScareEnemyCommand(enemyEntityModel));
				}
			}
		}


		private void OnTurnLightOffCommand(TurnLightOffCommand obj)
		{
			foreach (var tilePosition in obj.LightBeam.Positions)
			{
				if (!tilemapModel.IsIlluminated(tilePosition, out _) && 
				    tilemapModel.GetEntity(tilePosition) is EnemyEntityModel enemyEntityModel &&
				    enemyEntityModel.IsScared)
				{
					//Debug.Log($"Reacting to {obj.GetType().Name}");
					commandsController.Do(new UnScareEnemyCommand(enemyEntityModel));
				}
			}
		}

		private void OnTurnLightOnCommand(TurnLightOnCommand obj)
		{
			foreach (var tilePosition in obj.LightBeam.Positions)
			{
				if (tilemapModel.GetEntity(tilePosition) is EnemyEntityModel enemyEntityModel &&
				    !enemyEntityModel.IsScared)
				{
					//Debug.Log($"Reacting to {obj.GetType().Name}");
					commandsController.Do(new ScareEnemyCommand(enemyEntityModel));
				}
			}
		}

		private void TurnOnSheepLights()
		{
			List<TurnLightOnCommand> turnOnLightsCommands = new List<TurnLightOnCommand>();
			foreach (SheepEntityModel sheepEntityModel in sheepModels)
			{
				turnOnLightsCommands.Add(new TurnLightOnCommand(sheepEntityModel, tilemapModel, lightLength));
			}
			commandsController.Do(turnOnLightsCommands);
			commandsController.ClearAll();
		}

		private Vector3 GetWorldPosition(int positionIndex) => tilemapController.GetWorldPosition(positionIndex);
		private Vector3 GetWorldPosition(Vector2Int coordinate) => tilemapController.GetWorldPosition(coordinate);
		
		public ICommand CreateCommand(EntityModel entityModel, Orientation direction)
		{
			//Debug.Log($"CreateCommand:{entityModel} {direction}");
			var coordinate = tilemapModel.IndexToCoordinate(entityModel.PositionIndex);

			if (direction == Orientation.None)
			{
				return new WaitCommand(entityModel);
			}
			
			var targetCoordinate = coordinate + direction.ToVector2Int();
			var targetPosition = tilemapModel.CoordinateToIndex(targetCoordinate);
			
			if (tilemapModel.Contains(targetPosition) && CanWalk(entityModel,targetPosition))
			{
				var targetEntity = tilemapModel.GetEntity(targetPosition);
				
				if (entityModel is SheepEntityModel sheepEntityModel)
				{
					if (targetEntity is DoorEntityModel door)
					{
						return
							new CompositeCommand(
								new TurnLightOffCommand(sheepEntityModel, tilemapModel),
								new LookMoveAndExitCommand(sheepEntityModel, door,tilemapModel,direction, this)
								);
					}
					
					return new CompositeCommand(
						new TurnLightOffCommand(sheepEntityModel, tilemapModel),
						new MoveAndLookCommand(
						sheepEntityModel,
						tilemapModel,
						direction,
						targetPosition),
						new TurnLightOnCommand(sheepEntityModel, tilemapModel, lightLength)
						);
				}
				if (entityModel is EnemyEntityModel enemyEntityModel && targetEntity is SheepEntityModel sheep)
				{
					return new MoveAndEatCommand(enemyEntityModel, sheep, tilemapModel,this);
				}
				return new MoveCommand(
					entityModel,
					tilemapModel,
					targetPosition);
			}
			else
			{
				if (entityModel is SheepEntityModel sheepEntityModel)
				{
					return CreateLookCommand(sheepEntityModel, direction);
				}
				return new WaitCommand(entityModel);
			}
		}

		private bool CanWalk(EntityModel entityModel, int targetPosition)
		{
			var targetEntity = tilemapModel.GetEntity(targetPosition);
			if (entityModel is SheepEntityModel sheep && targetEntity is DoorEntityModel)
			{
				return true;
			}
			if (entityModel is EnemyEntityModel enemy && targetEntity is SheepEntityModel)
			{
				return true;
			}
			return tilemapModel.IsEmpty(targetPosition);
		}

		public void MoveTogether<T>(T[] entityModels, Orientation[] direction) where T : EntityModel, IMove
		{
			ICommand[] commands = new ICommand[entityModels.Length];
			for (int i = 0; i < entityModels.Length; i++)
			{
				commands[i]= CreateCommand(entityModels[i], direction[i]);
				Debug.Log(commands[i]);
			}
			commandsController.Do(new CompositeCommand(commands.ToArray()));
		}
		
		public void LookTogether(IReadOnlyList<SheepEntityModel> sheep, Orientation orientation)
		{
			var commands = CreateLookTogetherCommands(sheep, orientation);
			commandsController.Do(new CompositeCommand(commands));
		}

		public List<CompositeCommand> CreateLookTogetherCommands(IEnumerable<SheepEntityModel> sheep, Orientation orientation)
		{
			List<CompositeCommand> commands = new List<CompositeCommand>();
			foreach (var entityModel in sheep)
			{
				commands.Add(CreateLookCommand(entityModel, orientation));
			}
			return commands;
		}

		private CompositeCommand CreateLookCommand(SheepEntityModel entityModel, Orientation orientation)
		{
			return new CompositeCommand(
				new TurnLightOffCommand(entityModel, tilemapModel),
				new LookCommand(entityModel, orientation),
				new TurnLightOnCommand(entityModel, tilemapModel, lightLength)
			);
		}

		public void MoveTogether<T>(IEnumerable<T> entityModels, Orientation direction) where T : EntityModel, IMove
		{
			MoveTogether<T>(Array.ConvertAll(entityModels.ToArray(),x=>(x,direction)));
		}
		
		public void MoveTogether<T>(IEnumerable<(T Entity, Orientation Direction)> values) where T : EntityModel, IMove
		{
			CompositeCommand command = CreateMoveTogetherCommand(values);
			commandsController.Do(command);
		}

		public CompositeCommand CreateMoveTogetherCommand<T>(IEnumerable<(T Entity, Orientation Direction)> values) where T : EntityModel, IMove
		{
			return new CompositeCommand(Array.ConvertAll(values.ToArray(), x => CreateCommand(x.Entity, x.Direction)));
		}


		public void Move(EntityModel model, Orientation moveDirection) => commandsController.Do(CreateCommand(model, moveDirection));

		public EntityView GetView(EntityModel model) => modelToView[model];

		public IEnumerable<EntityView> GetSheepViews() => GetViews(sheepModels);
		public IEnumerable<EntityView> GetEnemyViews() => GetViews(enemyModels);
		public IEnumerable<EntityView> GetDoorViews() => GetViews(doorModels);

		public IEnumerable<EntityView> GetViews(IEnumerable<EntityModel> models)
		{
			foreach (EntityModel entityModel in models)
			{
				yield return modelToView[entityModel];
			}
		}

		public void RemoveSheep(SheepEntityModel sheepModel) => sheepModels.Remove(sheepModel);
		public void AddSheep(SheepEntityModel sheepModel) => sheepModels.Add(sheepModel);

		public void KillSheep(SheepEntityModel sheep)
		{
			tilemapModel.KillSheep(sheep);
			RemoveSheep(sheep);
		}

		public void ReviveSheep(SheepEntityModel sheep, int sheepPosition)
		{
			tilemapModel.ReviveSheep(sheep, sheepPosition);
			AddSheep(sheep);
		}

		public void Wait(IReadOnlyList<EntityModel> entityModels)
		{
			List<WaitCommand> commands = new List<WaitCommand>();
			foreach (var entity in entityModels)
			{
				commands.Add(new WaitCommand(entity));
			}
			commandsController.Do(commands);
		}

		
	}

}