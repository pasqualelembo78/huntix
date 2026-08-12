using System;
using System.Collections.Generic;
using System.Linq;
using Controllers.Commands;
using Controllers.Entities;
using Models;
using UnityEditor;
using UnityEngine;
using UnityEngine.Tilemaps;

namespace Controllers.Level
{
	public class TilemapController : MonoBehaviour
	{
		private static readonly TileBase[] AllTiles = new TileBase[1024];

		[SerializeField] private Transform from;
		[SerializeField] private Transform to;
		[SerializeField] private List<Tile> floorTiles;
		[SerializeField] private List<Tile> wallTiles;
		[SerializeField] private List<Tile> playerTiles;
		[SerializeField] private Tile enemyTile;
		[SerializeField] private Tile exitTile;
		[SerializeField] private Tilemap terrainTilemap;
		[SerializeField] private Tilemap contentTilemap;
		[SerializeField] private PathfinderModel pathfinderModel;
		[SerializeField] private TilemapModel tilemapModel;
		[SerializeField] private List<int> path;
		[SerializeField] private int debugNeighbours = 0;
		[SerializeField] private DebugModes debugModes;
		private CommandListener<MoveCommand> doListener;
		private CommandListener<MoveCommand> undoListener;
		public TilemapModel Model => tilemapModel;

		public TilemapModel ProcessTileMaps()
		{
			terrainTilemap.CompressBounds();
	   
			var bounds = terrainTilemap.cellBounds;
			var count = terrainTilemap.GetTilesBlockNonAlloc(bounds, AllTiles);

			List<int> floor = new List<int>(count);
			HashSet<int> isWall = new HashSet<int>(floor);

			for (var index = 0; index < count; index++)
			{
				var tile = AllTiles[index];
				if (floorTiles.Exists( x => x == tile))
				{
					floor.Add(index);
				}
				else if (tile == null)
				{
					isWall.Add(index);
				}
			}

			contentTilemap.GetTilesBlockNonAlloc(bounds, AllTiles);
			EntityModel[] entities = new EntityModel[count];
			for (var index = 0; index < count; index++)
			{
				var tile = AllTiles[index];
				EntityModel model;
				if (wallTiles.Contains(tile))
				{
					model = new TreeEntityModel(index);
				}
				else if (tile == enemyTile)
				{
					model = new EnemyEntityModel(index);
				}
				else if (playerTiles.Contains(tile))
				{
					var tileIndex= playerTiles.IndexOf((Tile)tile);
					model = new SheepEntityModel(index, (Orientation)(tileIndex + 1));
				}
				else if(tile == exitTile)
				{
					model = new DoorEntityModel(index);
				}
				else if (isWall.Contains(index))
				{
					model = new TreeEntityModel(index);
				}
				else
				{
					model = null;
				}
				entities[index] = model;
			}
			
			tilemapModel = new TilemapModel(bounds, floor, entities);
	
			pathfinderModel = new PathfinderModel(
				tilemapModel.Count, 
				index => tilemapModel.GetNeighbours(index).ToArray(),
				IsWalkable,
				x=> 1,
				CalculatedDistance);
			
			 contentTilemap.gameObject.SetActive(false);
			 

			 return tilemapModel;
		}

		private bool IsWalkable(int obj) => tilemapModel.IsFloor(obj) && tilemapModel.IsEmpty(obj);

		private float CalculatedDistance(int fromIndex, int toIndex) =>
			Vector2.Distance(
				tilemapModel.IndexToCoordinate(fromIndex), 
				tilemapModel.IndexToCoordinate(toIndex));

		public EntityModel GetContent(int index) => tilemapModel.GetEntity(index);

		public bool TryFindPath(int fromIndex, int toIndex, ref List<int> resultPath)
		{
			return Pathfinding.AStar.TryFindPath(
				fromIndex, 
				toIndex,
				tilemapModel.IsEmpty,
				pathfinderModel.GetNeighbours,
				x => pathfinderModel.GetDistance(toIndex,x),
				tilemapModel.Count,
				ref resultPath,		
				out _);
		}

	
		public Vector3 GetWorldPosition(int positionIndex) => terrainTilemap.GetCellCenterWorld(tilemapModel.IndexToCoordinate(positionIndex));
		public Vector3 GetWorldPosition(Vector2Int coordinate) => terrainTilemap.GetCellCenterWorld((Vector3Int)coordinate);
		
		
		
		
		[Flags]
		
		public enum DebugModes
		{
			None = 0,
			Index = 1,
			Coordinates = 2,
			WalkableTiles = 4,
			Adjacencies = 8,
			Path = 16,
			Entity = 32,
			Light = 64,
		}

		#region DrawGizmos

		private void OnDrawGizmos()
		{
			if (terrainTilemap && contentTilemap)
			{
				Gizmos.color = Color.magenta;
				terrainTilemap.CompressBounds();
				var bounds = terrainTilemap.cellBounds;
				Gizmos.DrawLineStrip(new[]
				{
					terrainTilemap.CellToWorld(new Vector3Int(bounds.min.x,bounds.min.y)),
					terrainTilemap.CellToWorld(new Vector3Int(bounds.min.x,bounds.max.y)),
					terrainTilemap.CellToWorld(new Vector3Int(bounds.max.x,bounds.max.y)),
					terrainTilemap.CellToWorld(new Vector3Int(bounds.max.x,bounds.min.y)),
				}, true);

				var count = terrainTilemap.GetTilesBlockNonAlloc(terrainTilemap.cellBounds, AllTiles);

				if ((debugModes & DebugModes.Coordinates) != 0)
				{
					for (var index = 0; index < count; index++)
					{
						var coordinate = bounds.min + new Vector3Int(index % bounds.size.x, index / bounds.size.x);
						var center = terrainTilemap.GetCellCenterWorld(coordinate);
						DrawLabel(center,$"{coordinate.x},{coordinate.y}");
					}  
				}

				if ((debugModes & DebugModes.Index) != 0)
				{
					for (var index = 0; index < count; index++)
					{
						var coordinate = bounds.min + new Vector3Int(index % bounds.size.x, index / bounds.size.x);
						var center = terrainTilemap.GetCellCenterWorld(coordinate);
						DrawLabel(center,index.ToString());
					}  
				}
				
				if ((debugModes & DebugModes.Entity) != 0)
				{
					for (var index = 0; index < count; index++)
					{
						var coordinate = bounds.min + new Vector3Int(index % bounds.size.x, index / bounds.size.x);
						var center = terrainTilemap.GetCellCenterWorld(coordinate);

						var model = tilemapModel.GetEntity(index);
						DrawLabel(center, model == null ? "-" : model.GetType().Name);
					}  
				}

				if (tilemapModel != null && (debugModes & DebugModes.WalkableTiles) != 0)
				{
					var color = new Color(0,1,0,.5f);
					Gizmos.color = color;
					foreach (int index in tilemapModel.FloorTiles)
					{
						var coordinate = tilemapModel.IndexToCoordinate(index);
						terrainTilemap.GetCellCenterWorld(coordinate);
						var center = terrainTilemap.GetCellCenterWorld(coordinate);
						Gizmos.DrawCube(center, Vector3.one);
					}
				}
				
				if (tilemapModel != null && (debugModes & DebugModes.Light) != 0)
				{
					var color = new Color(1,1,0,.65f);
					Gizmos.color = color;
					foreach (var index in tilemapModel.LightBeamModels)
					{
						foreach (int indexPosition in index.Positions)
						{
							var coordinate = tilemapModel.IndexToCoordinate(indexPosition);
							terrainTilemap.GetCellCenterWorld(coordinate);
							var center = terrainTilemap.GetCellCenterWorld(coordinate);
							Gizmos.DrawCube(center, Vector3.one);
						}
					}
				}

				List<Vector3> points = new List<Vector3>();
				if (tilemapModel != null && pathfinderModel != null && (debugModes & DebugModes.Adjacencies) != 0)
				{
					var color = new Color(1,1,1,1f);
					Gizmos.color = color;

					for (var index = 0; index < pathfinderModel.Adjacencies.Count; index++)
					{
						var coordinate = tilemapModel.IndexToCoordinate(index);
						var worldPos = terrainTilemap.GetCellCenterWorld(coordinate);

						var neighbours = pathfinderModel.Adjacencies[index];

						foreach (int neighbour in neighbours)
						{
							points.Add(worldPos);
							points.Add(terrainTilemap.GetCellCenterWorld(tilemapModel.IndexToCoordinate(neighbour)));
						}
					}
				}
				Gizmos.DrawLineList(points.ToArray());
			}


			if ((debugModes & DebugModes.Path) != 0 && tilemapModel != null && pathfinderModel != null)
			{
				var fromIndex = tilemapModel.CoordinateToIndex((Vector2Int)terrainTilemap.WorldToCell(from.position));
				var toIndex = tilemapModel.CoordinateToIndex((Vector2Int)terrainTilemap.WorldToCell(to.position));

				//Debug.Log($"{fromIndex}>{toIndex}");
				if (fromIndex >= 0 && fromIndex < tilemapModel.Count &&
				    toIndex >= 0 && toIndex < tilemapModel.Count)
				{
					bool success = TryFindPath(fromIndex, toIndex, ref path);

					//Debug.Log(path.Count);
					Gizmos.color = success ? Color.yellow : Color.red;
					if (path != null && path.Count > 0)
					{
						Gizmos.DrawLineStrip(
							path.ConvertAll(x => terrainTilemap.GetCellCenterWorld(tilemapModel.IndexToCoordinate(x)))
							    .ToArray(),
							false);
					}
				}

				if (debugNeighbours >= 0 && debugNeighbours < pathfinderModel.Adjacencies.Count)
				{
					Gizmos.color = Color.cyan;
					var center = terrainTilemap.GetCellCenterWorld(tilemapModel.IndexToCoordinate(debugNeighbours));
					foreach (var neighbour in pathfinderModel.Adjacencies[debugNeighbours])
					{
						var other = terrainTilemap.GetCellCenterWorld(tilemapModel.IndexToCoordinate(neighbour));
						Gizmos.DrawLine(center, other);
					}
				}
			}
		}

		private static void DrawLabel(Vector3 center, string s)
		{
#if UNITY_EDITOR
			Handles.Label(center, s);
#endif
		}
		#endregion

		public void SetTileMaps(Tilemap tilemap, Tilemap entitiesTilemap)
		{
			terrainTilemap = tilemap;
			contentTilemap = entitiesTilemap;
		}
	}
}