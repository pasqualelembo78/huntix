using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public static class WaypointBuilder
    {
        private const float LANE_OFFSET_FACTOR = 0.15f;
        private const float MAX_OFFSET = 2.0f;
        private const float BUILDING_PUSH_DIST = 2.5f;
        private const float BUILDING_PUSH_FORCE = 4.0f;
        private const int JUNCTION_SMOOTH_PASSES = 2;

        public static Vector3[] Build(RoadGraph graph, List<int> nodePath,
            float vehicleLength, Bounds[] buildingBounds = null)
        {
            if (nodePath == null || nodePath.Count < 2)
                return new Vector3[0];

            var result = new List<Vector3>();

            for (int i = 0; i < nodePath.Count - 1; i++)
            {
                int fromId = nodePath[i];
                int toId = nodePath[i + 1];
                int arcId = graph.GetArcBetween(fromId, toId);

                if (arcId < 0) continue;

                var arc = graph.arcMap[arcId];
                bool reverse = arc.fromNodeId != fromId;
                var wp = graph.BuildWaypointsForArc(arcId, reverse);

                float offset = Mathf.Min(arc.width * LANE_OFFSET_FACTOR, MAX_OFFSET);
                bool italianTraffic = !reverse;
                var offsetWp = ApplyLaneOffset(wp, offset, italianTraffic);

                offsetWp = ClampToRoadEdges(offsetWp, arc.width);

                int startIdx = (i == 0) ? 0 : 1;
                int endIdx = (i == nodePath.Count - 2) ? offsetWp.Length : offsetWp.Length - 1;

                for (int w = startIdx; w < endIdx; w++)
                    result.Add(offsetWp[w]);
            }

            if (result.Count >= 2)
                result = SmoothJunctions(graph, nodePath, result);

            if (result.Count >= 2 && buildingBounds != null && buildingBounds.Length > 0)
                result = AvoidBuildings(result, buildingBounds);

            return result.ToArray();
        }

        private static Vector3[] ApplyLaneOffset(Vector3[] waypoints, float offset,
            bool rightHandTraffic)
        {
            if (waypoints.Length < 2) return waypoints;

            var result = new Vector3[waypoints.Length];
            for (int i = 0; i < waypoints.Length; i++)
            {
                Vector3 dir;
                if (i == 0)
                    dir = (waypoints[1] - waypoints[0]).normalized;
                else if (i == waypoints.Length - 1)
                    dir = (waypoints[i] - waypoints[i - 1]).normalized;
                else
                    dir = (waypoints[i + 1] - waypoints[i - 1]).normalized;

                if (dir.sqrMagnitude < 0.001f)
                    dir = (waypoints[Mathf.Min(i + 1, waypoints.Length - 1)]
                         - waypoints[Mathf.Max(i - 1, 0)]).normalized;

                Vector3 right = Vector3.Cross(Vector3.up, dir).normalized;

                float sign = rightHandTraffic ? -1f : 1f;
                result[i] = waypoints[i] + right * (offset * sign);
            }
            return result;
        }

        private static Vector3[] ClampToRoadEdges(Vector3[] waypoints, float roadWidth)
        {
            if (waypoints.Length < 2) return waypoints;

            float halfWidth = roadWidth * 0.5f;
            var result = new Vector3[waypoints.Length];

            for (int i = 0; i < waypoints.Length; i++)
            {
                Vector3 pos = waypoints[i];

                Vector3 dir;
                if (i == 0)
                    dir = (waypoints[1] - waypoints[0]).normalized;
                else if (i == waypoints.Length - 1)
                    dir = (waypoints[i] - waypoints[i - 1]).normalized;
                else
                    dir = (waypoints[i + 1] - waypoints[i - 1]).normalized;

                if (dir.sqrMagnitude < 0.001f)
                {
                    result[i] = pos;
                    continue;
                }

                Vector3 right = Vector3.Cross(Vector3.up, dir).normalized;
                float lateralDist = Vector3.Dot(pos - waypoints[0], right);

                if (Mathf.Abs(lateralDist) > halfWidth)
                {
                    float sign = Mathf.Sign(lateralDist);
                    pos = waypoints[i] - right * (sign * (Mathf.Abs(lateralDist) - halfWidth));
                }

                result[i] = pos;
            }
            return result;
        }

        private static List<Vector3> SmoothJunctions(RoadGraph graph, List<int> nodePath,
            List<Vector3> rawWp)
        {
            var result = new List<Vector3>(rawWp.Count);
            int wpIdx = 0;

            for (int n = 0; n < nodePath.Count; n++)
            {
                int nodeId = nodePath[n];
                Vector3 nodePos = graph.nodeMap[nodeId].position;
                bool isFirst = n == 0;

                int arcCount = graph.nodeMap[nodeId].arcIds.Count;
                bool isJunction = arcCount >= 3;

                if (isFirst || !isJunction)
                {
                    if (wpIdx < rawWp.Count)
                    {
                        result.Add(rawWp[wpIdx]);
                        wpIdx++;
                    }
                    continue;
                }

                if (isJunction && wpIdx > 0 && wpIdx < rawWp.Count)
                {
                    Vector3 entryWp = rawWp[wpIdx - 1];
                    Vector3 exitWp = rawWp[wpIdx];

                    Vector3 junctionPt = Vector3.Lerp(entryWp, exitWp, 0.5f);

                    result.Add(junctionPt);
                    wpIdx++;
                }
                else if (wpIdx < rawWp.Count)
                {
                    result.Add(rawWp[wpIdx]);
                    wpIdx++;
                }
            }

            while (wpIdx < rawWp.Count)
            {
                result.Add(rawWp[wpIdx]);
                wpIdx++;
            }

            for (int pass = 0; pass < JUNCTION_SMOOTH_PASSES; pass++)
            {
                for (int i = 1; i < result.Count - 1; i++)
                {
                    int nodeId = FindNearestNode(graph, result[i]);
                    if (nodeId < 0) continue;
                    var node = graph.nodeMap[nodeId];
                    if (node.arcIds.Count < 3) continue;

                    Vector3 smoothed = (result[i - 1] + result[i] + result[i + 1]) / 3f;
                    float nodeDist = Vector3.Distance(smoothed, node.position);
                    if (nodeDist < 3f)
                    {
                        result[i] = Vector3.Lerp(smoothed, node.position, 0.3f);
                    }
                }
            }

            return result;
        }

        private static int FindNearestNode(RoadGraph graph, Vector3 pos)
        {
            int best = -1;
            float bestD = float.MaxValue;
            foreach (var node in graph.nodes)
            {
                float d = (node.position - pos).sqrMagnitude;
                if (d < bestD)
                {
                    bestD = d;
                    best = node.id;
                }
            }
            return bestD < 25f ? best : -1;
        }

        private static List<Vector3> AvoidBuildings(List<Vector3> waypoints, Bounds[] buildings)
        {
            var result = new List<Vector3>(waypoints.Count);

            foreach (var wp in waypoints)
            {
                Vector3 pushed = wp;
                bool insideBuilding = false;

                for (int b = 0; b < buildings.Length; b++)
                {
                    Bounds bld = buildings[b];
                    Vector3 closest = bld.ClosestPoint(wp);
                    float dx = wp.x - closest.x;
                    float dz = wp.z - closest.z;
                    float distSq = dx * dx + dz * dz;

                    if (distSq < 0.01f)
                    {
                        insideBuilding = true;
                        Vector3 center = bld.center;
                        center.y = wp.y;
                        Vector3 away = (wp - center).normalized;
                        if (away.sqrMagnitude < 0.01f)
                            away = Vector3.forward;
                        pushed = wp + away * BUILDING_PUSH_FORCE;
                    }
                    else if (distSq < BUILDING_PUSH_DIST * BUILDING_PUSH_DIST)
                    {
                        float dist = Mathf.Sqrt(distSq);
                        Vector3 pushDir = (wp - closest).normalized;
                        float pushAmount = (BUILDING_PUSH_DIST - dist) * 0.5f;
                        pushed += pushDir * pushAmount;
                    }
                }

                if (insideBuilding)
                {
                    for (int b = 0; b < buildings.Length; b++)
                    {
                        if (buildings[b].Contains(pushed))
                        {
                            Vector3 toWp = (wp - buildings[b].center).normalized;
                            if (toWp.sqrMagnitude < 0.01f) toWp = Vector3.forward;
                            pushed = buildings[b].center + toWp * (buildings[b].extents.magnitude + 3f);
                            pushed.y = wp.y;
                            break;
                        }
                    }
                }

                result.Add(pushed);
            }

            return result;
        }

        public static Vector3 FindEntryDirection(RoadGraph graph, int nodeId, int fromNeighborId)
        {
            var node = graph.nodeMap[nodeId];
            foreach (int arcId in node.arcIds)
            {
                var arc = graph.arcMap[arcId];
                if (arc.fromNodeId == fromNeighborId && arc.toNodeId == nodeId)
                {
                    var wp = arc.waypoints;
                    if (wp.Length >= 2)
                        return (wp[wp.Length - 1] - wp[wp.Length - 2]).normalized;
                }
            }
            return (node.position - graph.nodeMap[fromNeighborId].position).normalized;
        }

        public static float PickTurnAngle(RoadGraph graph, int nodeId, int fromNeighborId)
        {
            var entryDir = FindEntryDirection(graph, nodeId, fromNeighborId);
            var exitDirs = new List<Vector3>();

            foreach (int arcId in graph.nodeMap[nodeId].arcIds)
            {
                var arc = graph.arcMap[arcId];
                if (arc.fromNodeId == nodeId)
                {
                    if (arc.waypoints.Length >= 2)
                        exitDirs.Add((arc.waypoints[1] - arc.waypoints[0]).normalized);
                }
                else if (arc.toNodeId == nodeId && arc.waypoints.Length >= 2)
                {
                    exitDirs.Add((arc.waypoints[arc.waypoints.Length - 2]
                                - arc.waypoints[arc.waypoints.Length - 1]).normalized);
                }
            }

            if (exitDirs.Count == 0) return 0f;

            float bestAngle = 0f;
            float bestDot = -1f;
            foreach (var exitDir in exitDirs)
            {
                float dot = Vector3.Dot(entryDir, exitDir);
                if (dot > bestDot)
                {
                    bestDot = dot;
                    bestAngle = Mathf.Acos(Mathf.Clamp01(dot)) * Mathf.Rad2Deg;
                }
            }
            return bestAngle;
        }
    }
}
