using System;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public enum Orientation
	{
		None = 0,
		Up = 1,
		Right = 2,
		Down = 3,
		Left = 4
	}

	public static class OrientationExtensions
	{
		public static Vector2Int ToVector2Int(this Orientation @this)
		{
			switch (@this)
			{
				case Orientation.None:
					return Vector2Int.zero;
				case Orientation.Up:
					return Vector2Int.up;
				case Orientation.Right:
					return Vector2Int.right;
				case Orientation.Down:
					return Vector2Int.down;
				case Orientation.Left:
					return Vector2Int.left;
				default:
					throw new ArgumentOutOfRangeException(nameof(@this), @this, null);
			}
		}
	}
}