using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using City.OSM;

namespace City.Vehicle.Traffic
{
    public static class RoadGraphBuilder
    {
        public static RoadGraph Build(OsmCityEnvelope env)
        {
            var graph = new RoadGraph();
            if (env.roads == null || env.roads.Length == 0) return graph;

            var snapMap = NodeSnap.BuildSnapMap(env.roads);
            NodeSnap.ComputeJunctionData(env.roads, snapMap,
                out var junctionCount, out var nodeBearings);

            var keyToNodeId = new Dictionary<int, int>();
            int nextNodeId = 0;

            int GetOrCreateNode(int snapKey, Vector3 pos, string name = "")
            {
                if (keyToNodeId.TryGetValue(snapKey, out int existing))
                    return existing;

                int count = junctionCount.GetValueOrDefault(snapKey);
                var bearings = nodeBearings.GetValueOrDefault(snapKey);
                var jType = NodeSnap.ClassifyJunction(bearings, count);

                int nodeId = nextNodeId++;
                graph.AddNode(nodeId, pos, jType, name);
                keyToNodeId[snapKey] = nodeId;
                return nodeId;
            }

            var processedArcs = new HashSet<long>();

            foreach (var road in env.roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                if (NodeSnap.IsMinorHighway(road.highway ?? "")) continue;

                float width = NodeSnap.RoadWidth(road.highway);

                var junctionPts = new List<int>();
                for (int i = 0; i < road.points.Length; i++)
                {
                    Vector3 pos = NodeSnap.SnappedLocal(road.points[i], snapMap);
                    int snapKey = NodeSnap.SnapKey(pos);
                    int count = junctionCount.GetValueOrDefault(snapKey);
                    if (count >= 2)
                        junctionPts.Add(i);
                }

                if (junctionPts.Count == 0)
                {
                    int aKey = NodeSnap.SnapKey(NodeSnap.SnappedLocal(road.points[0], snapMap));
                    int bKey = NodeSnap.SnapKey(NodeSnap.SnappedLocal(road.points[road.points.Length - 1], snapMap));
                    junctionPts.Add(0);
                    if (bKey != aKey) junctionPts.Add(road.points.Length - 1);
                }

                if (junctionPts.Count < 2)
                {
                    if (junctionPts.Count == 1)
                    {
                        junctionPts.Add(road.points.Length - 1);
                    }
                    else
                    {
                        junctionPts.Add(0);
                        junctionPts.Add(road.points.Length - 1);
                    }
                }

                for (int j = 0; j < junctionPts.Count - 1; j++)
                {
                    int segStart = junctionPts[j];
                    int segEnd = junctionPts[j + 1];
                    if (segStart == segEnd) continue;

                    Vector3 startPos = NodeSnap.SnappedLocal(road.points[segStart], snapMap);
                    Vector3 endPos = NodeSnap.SnappedLocal(road.points[segEnd], snapMap);
                    if ((endPos - startPos).sqrMagnitude < 0.01f) continue;

                    int startKey = NodeSnap.SnapKey(startPos);
                    int endKey = NodeSnap.SnapKey(endPos);
                    long arcGeoKey = ((long)startKey << 32) | (uint)endKey;
                    long arcGeoKeyRev = ((long)endKey << 32) | (uint)startKey;

                    if (processedArcs.Contains(arcGeoKey) || processedArcs.Contains(arcGeoKeyRev))
                        continue;

                    int startNodeId = GetOrCreateNode(startKey, startPos, road.name ?? "");
                    int endNodeId = GetOrCreateNode(endKey, endPos, road.name ?? "");

                    var waypoints = new Vector3[segEnd - segStart + 1];
                    for (int w = 0; w < waypoints.Length; w++)
                        waypoints[w] = NodeSnap.SnappedLocal(road.points[segStart + w], snapMap);

                    graph.AddArc(startNodeId, endNodeId, waypoints, road.name ?? "", road.highway ?? "", width);
                    processedArcs.Add(arcGeoKey);
                }
            }

            Debug.Log($"[RoadGraph] Built: {graph.nodes.Count} nodes, {graph.arcs.Count} arcs " +
                      $"(dead-end={graph.nodes.Count(n => n.junction == JunctionType.DeadEnd)}, " +
                      $"simple={graph.nodes.Count(n => n.junction == JunctionType.Simple)}, " +
                      $"real={graph.nodes.Count(n => n.junction == JunctionType.Real)})");

            return graph;
        }
    }
}
