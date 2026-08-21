using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public static class RoadPathfinder
    {
        public static List<int> FindPath(RoadGraph graph, int startNodeId, int endNodeId)
        {
            if (startNodeId == endNodeId)
                return new List<int> { startNodeId };

            int nodeCount = graph.nodes.Count;
            var gScore = new float[nodeCount];
            var fScore = new float[nodeCount];
            var cameFrom = new int[nodeCount];
            var closed = new HashSet<int>();

            for (int i = 0; i < nodeCount; i++)
            {
                gScore[i] = float.MaxValue;
                fScore[i] = float.MaxValue;
                cameFrom[i] = -1;
            }

            gScore[startNodeId] = 0f;
            fScore[startNodeId] = Heuristic(graph, startNodeId, endNodeId);

            var open = new List<int> { startNodeId };

            int safety = 0;
            while (open.Count > 0 && safety++ < 10000)
            {
                int current = -1;
                float bestF = float.MaxValue;
                for (int i = 0; i < open.Count; i++)
                {
                    if (fScore[open[i]] < bestF)
                    {
                        bestF = fScore[open[i]];
                        current = open[i];
                    }
                }

                if (current == endNodeId)
                    return ReconstructPath(cameFrom, current);

                open.Remove(current);
                closed.Add(current);

                foreach (int neighborId in graph.GetNeighborNodeIds(current))
                {
                    if (closed.Contains(neighborId)) continue;

                    int arcId = graph.GetArcBetween(current, neighborId);
                    if (arcId < 0) continue;

                    float tentativeG = gScore[current] + graph.arcMap[arcId].length;

                    if (tentativeG < gScore[neighborId])
                    {
                        cameFrom[neighborId] = current;
                        gScore[neighborId] = tentativeG;
                        fScore[neighborId] = tentativeG + Heuristic(graph, neighborId, endNodeId);

                        if (!open.Contains(neighborId))
                            open.Add(neighborId);
                    }
                }
            }

            return null;
        }

        private static float Heuristic(RoadGraph graph, int nodeId, int endNodeId)
        {
            return Vector3.Distance(
                graph.nodeMap[nodeId].position,
                graph.nodeMap[endNodeId].position);
        }

        private static List<int> ReconstructPath(int[] cameFrom, int current)
        {
            var path = new List<int> { current };
            while (cameFrom[current] != -1)
            {
                current = cameFrom[current];
                path.Add(current);
            }
            path.Reverse();
            return path;
        }
    }
}
