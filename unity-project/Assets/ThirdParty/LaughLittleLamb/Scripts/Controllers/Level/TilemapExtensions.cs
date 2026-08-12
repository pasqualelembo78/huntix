using UnityEngine;
using UnityEngine.Tilemaps;

public static class TilemapExtensions
{
	public static Vector3 GetCellCenterWorld(this Tilemap @this, Vector2Int coordinate) => @this.GetCellCenterWorld(new Vector3Int(coordinate.x,coordinate.y));

	public static void Deconstruct(this Vector3Int @this, out Vector2Int coordinate)
	{
		coordinate = new Vector2Int(@this.x,@this.y);

	}
}