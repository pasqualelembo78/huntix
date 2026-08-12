using System;
using System.Collections.Generic;
using System.Text;
using Models;
using UnityEngine;

namespace Pathfinding
{
	public static class AStar
	{
		public static bool TryFindPath(int start, int destination, Predicate<int> isWalkable, Func<int,int[]> getAdjacencies, Func<int,float> getScore, int count, ref List<int> path, out int pathCost)
		{
			path.Clear();
			//Debug.Log($"Find Path {start} => {destination}");
			int[] previous = new int[count];
			int[] bestCost = new int[count];
			
			for (int i = 0; i < count; i++)
			{
				bestCost[i] = int.MaxValue;
				previous[i] = -1;
			}
			
			PriorityQueue<int> next = new MinPriorityQueue<int>(count);
			bestCost[start] = 0;
			next.Enqueue(0, start);

			int closestIndex = start;
			
			while (next.Count > 0)
			{
				var (cost, index) = next.Dequeue();

				if (index == destination)
				{
					closestIndex = index;
					//Debug.Log("FOUND");
					break;
				}
				
				var neighbours = getAdjacencies(index);

				//Debug.Log($"{index} neighbours {neighbours.values.Count}");
				foreach (int neighbour in neighbours)
				{
					if (isWalkable(neighbour) || neighbour == destination)
					{
						var score = getScore(neighbour);
						var nCost = cost + 1 + (int)(score);
						if (nCost < bestCost[neighbour])
						{
							if (score < getScore(closestIndex))
							{
								closestIndex = neighbour;
							}
							
							bestCost[neighbour] = nCost;
							previous[neighbour] = index;
							next.Enqueue(nCost, neighbour);
						}
					}
				}
			}


			path = new List<int>();
			bool success = closestIndex == destination;

			if (closestIndex != -1)
			{
				//Debug.Log($"Backtrack Start {closestIndex}");
				pathCost = bestCost[closestIndex];

				while (closestIndex != -1)
				{
					//Debug.Log($"BTrack {closestIndex}");
					path.Add(closestIndex);
					closestIndex = previous[closestIndex];
				}
			}
			else
			{
				pathCost = -1;
			}
			return success;
		}
		
		
		public static bool TryFindMultiPath(
			int startPositions,
			int[] destinations, 
			Predicate<int> isWalkable,
			Func<int,int[]> getAdjacencies,
			Func<int,float> getDistance,
			Func<int,float> getCost,
			int count, 
			ref List<int> path,
			out int pathCost,
			int maxCost = int.MaxValue)
		{
			var result= TryFindMultiPath(destinations, startPositions, isWalkable,getAdjacencies, getDistance, getCost, count,
				ref path, out pathCost);

			path.Reverse();
			return result;
		}


		private static bool TryFindMultiPath(
			int[] startPositions,
			int destination, 
			Predicate<int> isWalkable,
			Func<int,int[]> getAdjacencies,
			Func<int,float> getDistance,
			Func<int,float> getCost,
			int count, 
			ref List<int> path, 
			out int pathCost,
			int maxCost = int.MaxValue)
		{
			path.Clear();
			int[] previous = new int[count];
			float[] bestCost = new float[count];
			
			for (int i = 0; i < count; i++)
			{
				bestCost[i] = int.MaxValue;
				previous[i] = -1;
			}
			
			PriorityQueue<int> next = new MinPriorityQueue<int>(count);

			foreach (int startPosition in startPositions)
			{
				var score = bestCost[startPosition] = getCost(startPosition);
				next.Enqueue((int)score, startPosition);
				//Debug.Log($"Find Path {startPosition} => {destination}");
			}

			int closestIndex = -1;
			float closestDistance = float.MaxValue;


			while (next.Count > 0)
			{
				var (currentScore, index) = next.Dequeue();

				//Debug.Log($"{index}>{currentScore}");
				if (index == destination)
				{
					closestIndex = index;
					//Debug.Log("FOUND");
					break;
				}
				
				var neighbours = getAdjacencies(index);

				//Debug.Log($"{index} neighbours {neighbours.values.Count}");
				foreach (int neighbour in neighbours)
				{
					if(isWalkable(neighbour))
					{
						var distance = getDistance(neighbour);
						var newScore = (int)Mathf.Clamp((currentScore + getCost(neighbour) + distance),0,int.MaxValue);
						if (newScore < bestCost[neighbour] && newScore <= maxCost)
						{
							if (distance < closestDistance)
							{
								closestIndex = neighbour;
								closestDistance = distance;
							}

							//Debug.Log($"{index}=> {neighbour}   {bestCost[neighbour]}:{newScore}");
							bestCost[neighbour] = newScore;
							previous[neighbour] = index;
							next.Enqueue(newScore, neighbour);
						}
					}
				}
			}
			path = new List<int>();
			bool success = closestIndex == destination;

			if (closestIndex != -1)
			{
				pathCost = Mathf.RoundToInt(bestCost[closestIndex]);
				HashSet<int> visited = new HashSet<int>();
				
				while (closestIndex != -1)
				{
					if (!visited.Add(closestIndex))
					{
						//Debug.Break();
						break;
					}
					//Debug.Log(closestIndex);
					path.Add(closestIndex);
					closestIndex = previous[closestIndex];
				}
				path.Reverse();
			}
			else
			{
				pathCost = -1;
			}


			/*
			StringBuilder builder = new StringBuilder();

			builder.Append("Start Positions:");
			for (int i = 0; i < startPositions.Length; i++)
			{
				builder.Append($"{startPositions[i]} ");
			}
			builder.AppendLine();
			builder.AppendLine($"Destination:{destination}");
			builder.Append($"Path:");

			foreach (int i in path)
			{
				builder.Append($"{i} ");
			}
			Debug.Log(builder);*/
			return success;
		}
	}
}