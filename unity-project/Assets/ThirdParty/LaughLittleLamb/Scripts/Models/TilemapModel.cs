using System;
using System.Collections.Generic;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class TilemapModel
	{
		public event Action OnLightCalculation;
		[SerializeField] private List<int> floorTiles;
		[SerializeField] private List<LightBeamModel> lightBeamModels;
		[SerializeField] private EntityModel[] entities;
		[SerializeField] private BoundsInt bounds;
		
		[SerializeField] public List<SheepEntityModel> SavedSheeps = new List<SheepEntityModel>();
		[SerializeField] public List<SheepEntityModel> DeadSheeps = new List<SheepEntityModel>();
		[SerializeField] public List<SheepEntityModel> ActiveSheeps = new List<SheepEntityModel>();
		
		public IReadOnlyList<int> FloorTiles => floorTiles;

		public TilemapModel(BoundsInt bounds, List<int> floorTiles, EntityModel[] entities) 
		{
			this.bounds = bounds;
			this.floorTiles = floorTiles;
			this.entities = entities;
			lightBeamModels = new List<LightBeamModel>();

			foreach (EntityModel entityModel in this.entities)
			{
				if (entityModel is SheepEntityModel sheepEntityModel)
				{
					ActiveSheeps.Add(sheepEntityModel);
				}
			}
			//CalculateLights();
		}

		public bool IsFloor(int index) => floorTiles.Contains(index);

		public int Count => bounds.size.x * bounds.size.y;
		public IEnumerable<LightBeamModel> LightBeamModels => lightBeamModels;

		public int CoordinateToIndex(Vector2Int coordinate) => coordinate.x - bounds.x + (coordinate.y - bounds.y) * bounds.size.x;
		public Vector2Int IndexToCoordinate(int index) => new Vector2Int(index % bounds.size.x, index / bounds.size.x) + new Vector2Int(bounds.min.x,bounds.min.y);

		public IEnumerable<int> GetNeighbours(int index)
		{
			var center = IndexToCoordinate(index);
			var neighbour = center + Vector2Int.left;
			if (bounds.Contains((Vector3Int)neighbour))
			{
				yield return CoordinateToIndex(neighbour);
			}

			neighbour = center + Vector2Int.right;	
			if (bounds.Contains((Vector3Int)neighbour))
			{
				yield return CoordinateToIndex(center + Vector2Int.right);
			}

			neighbour = center + Vector2Int.up;	
			if (bounds.Contains((Vector3Int)neighbour))
			{
				yield return CoordinateToIndex(center + Vector2Int.up);
			}

			neighbour = center + Vector2Int.down;
			if (bounds.Contains((Vector3Int)neighbour))
			{
				yield return CoordinateToIndex(center + Vector2Int.down);
			}
		}


		public EntityModel GetEntity(int index) => entities[index];


		public IEnumerable<EntityModel> GetAllEntities()
		{
			foreach (EntityModel entity in entities)
			{
				if (entity != null)
				{
					yield return entity;
				}
			}
		}

		public bool TryGetEntity(Vector2Int coordinate, out EntityModel content)
		{
			if (bounds.Contains((Vector3Int)coordinate))
			{
				var index = CoordinateToIndex(coordinate);
				content = entities[index];
				return true;
			}
			content = null;
			return false;
		}

		public bool TryMoveEntity(EntityModel entityModel, int targetPosition)
		{
			if (IsEmpty(targetPosition))
			{
				entities[targetPosition] = entityModel;
				entityModel.PositionIndex = -1;
				return true;
			}
			return false;
		}

		public void RemoveEntity(EntityModel entityModel)
		{
			entities[entityModel.PositionIndex] = null;
			entityModel.PositionIndex = -1;
		}

		public void AddEntity(SheepEntityModel entityModel, int startPosition)
		{
			entities[startPosition] = entityModel;
			entityModel.PositionIndex = startPosition;
		}
		
		public bool IsEmpty(int index) => entities[index] == null;

		public void SwapEntities(int startPosition, int endPosition)
		{
			(entities[startPosition], entities[endPosition]) = (entities[endPosition], entities[startPosition]);

			if (entities[startPosition] != null)
			{
				entities[startPosition].PositionIndex = startPosition;
			}

			if (entities[endPosition] != null)
			{
				entities[endPosition].PositionIndex = endPosition;
			}
		}

		public bool IsEmpty(Vector2Int targetCoordinate) => IsEmpty(CoordinateToIndex(targetCoordinate));


		public LightBeamModel CreateLightBeam(EntityModel entity, Orientation direction, int distance)
		{
			List<int> positions = new List<int>();
			var coordinate = IndexToCoordinate(entity.PositionIndex);
			var offset = direction.ToVector2Int();
			for (int i = 0; i < distance; i++)
			{
				coordinate += offset;
				int index = CoordinateToIndex(coordinate);

				if(bounds.Contains((Vector3Int)coordinate))
				{
					var otherEntity = GetEntity(index);
					if (otherEntity is not TreeEntityModel)
					{
						positions.Add(index);
					}
					else
					{
						break;
					}
				}
				else
				{
					break;
				}
			}
			
			var lightBeam = new LightBeamModel(entity,positions.ToArray());
			AddLightBeam(lightBeam);
			return lightBeam;
		}

		public void AddLightBeam(LightBeamModel lightBeam)
		{
			lightBeamModels.Add(lightBeam);
		}

		public void RemoveLightBeam(LightBeamModel beamModel) => lightBeamModels.Remove(beamModel);

		public bool IsIlluminated(int index) => IsIlluminated(index, out _);
		public bool IsIlluminated(int index, out float intensity)
		{
			foreach (LightBeamModel lightBeamModel in lightBeamModels)
			{
				if (lightBeamModel.Contains(index))
				{
					intensity = 1f - (float)Array.IndexOf(lightBeamModel.Positions, index) / (lightBeamModel.Positions.Length - 1);
					return true;
				}
			}

			intensity = 0;
			return false;
		}

		public bool Contains(int targetPosition) => bounds.Contains((Vector3Int)IndexToCoordinate(targetPosition));

		public void KillSheep(SheepEntityModel sheep)
		{
			//Debug.Log($"KillSheep:{sheep}");
			RemoveEntity(sheep);
			ActiveSheeps.Remove(sheep);
			DeadSheeps.Add(sheep);
		}

		public void ReviveSheep(SheepEntityModel sheep, int sheepPosition)
		{
			//Debug.Log($"SheepRevived:{sheep} at {sheepPosition} {IndexToCoordinate(sheepPosition)}");
			DeadSheeps.Remove(sheep);
			ActiveSheeps.Add(sheep);
			AddEntity(sheep, sheepPosition);
		}

		/*
		[Button]
		public void CalculateLights()
		{
			Debug.Log("CalculateLights");
			illuminatedTiles.Clear();
			foreach (SheepEntityModel sheepEntityModel in ActiveSheeps)
			{
				var coordinate = IndexToCoordinate(sheepEntityModel.PositionIndex);
				List<int> positions = new List<int>();
				for (int i = 0; i < LIGHT_LENGTH; i++)
				{
					coordinate += sheepEntityModel.LookDirection.Value.ToVector2Int();
					int index = CoordinateToIndex(coordinate);
					if (bounds.Contains((Vector3Int)coordinate) && IsEmpty(index))
					{
						positions.Add(index);
					}
					else
					{
						break;
					}
				}
				illuminatedTiles.Add(new LightBeamModel(positions.ToArray()));
			}
			OnLightCalculation?.Invoke();
		}*/
		public LightBeamModel GetLightBeam(EntityModel sourceEntity) =>
			lightBeamModels.Find(x => x.SourceEntity == sourceEntity);
	}
}