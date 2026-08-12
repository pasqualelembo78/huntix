using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace Models
{
	[System.Serializable]
	public class PathfinderModel
	{
		[SerializeField] private Neighbours[] adjacencies;
		[SerializeField] private Scores[] distances;
		[SerializeField] private float[] costs;
		private readonly Func<int, int[]> getNeighbours;
		private readonly Predicate<int> isWalkable;
		private readonly Func<int, float> getTileCost;
		private readonly Func<int, int, float> getDistanceBetween;
		private readonly int count;
		public int Length => count;
		public IReadOnlyList<Neighbours> Adjacencies => adjacencies;

		public PathfinderModel(
			int count,
			Func<int,int[]> getNeighbours,
			Predicate<int> isWalkable,
			Func<int,float> getTileCost,
			Func<int,int,float> getDistanceBetween)
		{
			this.count = count;
			this.getNeighbours =getNeighbours;  
			this.isWalkable =isWalkable;  
			this.getTileCost =getTileCost;  
			this.getDistanceBetween =getDistanceBetween;  
			
			CalculateAdjacencies();
			CalculateCosts();
			CalculateDistances();
		}


		public void CalculateAdjacencies()
		{
			adjacencies = new Neighbours[count];
			for (int i = 0; i < count; i++)
			{
				adjacencies[i] = new Neighbours(Array.FindAll(getNeighbours(i), isWalkable));
			}
		}

		public void CalculateDistances()
		{
			distances = new Scores[count];
			for (int i = 0; i < count; i++)
			{
				distances[i] = new Scores(adjacencies.Length);
			}
			for (var x = 0; x < count; x++)
			{
				for (int y = x+1; y < count; y++)
				{
					var distance = getDistanceBetween(x,y);
					distances[y][x] = distances[x][y] = distance;
				}
			}
		}

		public void CalculateCosts()
		{
			costs = new float[count];
			for (int i = 0; i < count; i++)
			{
				costs[i] = getTileCost(i);
			}
		}

		public int[] GetNeighbours(int index) => adjacencies[index];
		
		[System.Serializable]
		public struct Neighbours : IEnumerable<int>
		{
			public int[] values;

			public Neighbours(int[] toArray)
			{
				values = toArray;
			}

			public IEnumerator<int> GetEnumerator()
			{
				foreach (int value in values)
					yield return value;
			}
			IEnumerator IEnumerable.GetEnumerator() => values.GetEnumerator();

			public static implicit operator int[](Neighbours neighbours) => neighbours.values;
		}

		[System.Serializable]
		public struct Scores
		{
			public float[] values;

			public Scores(int count)
			{
				values = new float[count];
			}

			public float this[int i] { get => values[i]; set => values[i] = value; }

			public static implicit operator float[](Scores scores) => scores.values;
		}

		public float GetDistance(int from, int to) => distances[from][to];

		public float GetCost(int index) => costs[index];
	}
}