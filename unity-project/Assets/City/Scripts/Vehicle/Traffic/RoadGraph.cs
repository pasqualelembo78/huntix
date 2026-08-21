using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public enum JunctionType
    {
        DeadEnd,
        Simple,
        Real
    }

    public class RoadNode
    {
        public int id;
        public Vector3 position;
        public List<int> arcIds = new List<int>();
        public JunctionType junction;
        public string name;
    }

    public class RoadArc
    {
        public int id;
        public int fromNodeId;
        public int toNodeId;
        public Vector3[] waypoints;
        public string roadName;
        public string highway;
        public float width;
        public float length;
    }

    public class RoadGraph
    {
        public List<RoadNode> nodes = new List<RoadNode>();
        public List<RoadArc> arcs = new List<RoadArc>();
        public Dictionary<int, RoadNode> nodeMap = new Dictionary<int, RoadNode>();
        public Dictionary<int, RoadArc> arcMap = new Dictionary<int, RoadArc>();

        public RoadNode AddNode(int id, Vector3 pos, JunctionType junction, string name = "")
        {
            if (nodeMap.ContainsKey(id)) return nodeMap[id];
            var node = new RoadNode { id = id, position = pos, junction = junction, name = name };
            nodes.Add(node);
            nodeMap[id] = node;
            return node;
        }

        public RoadArc AddArc(int fromId, int toId, Vector3[] waypoints, string roadName, string highway, float width)
        {
            int arcId = arcs.Count;
            float len = 0f;
            for (int i = 0; i < waypoints.Length - 1; i++)
                len += (waypoints[i + 1] - waypoints[i]).magnitude;

            var arc = new RoadArc
            {
                id = arcId,
                fromNodeId = fromId,
                toNodeId = toId,
                waypoints = waypoints,
                roadName = roadName,
                highway = highway,
                width = width,
                length = len
            };
            arcs.Add(arc);
            arcMap[arcId] = arc;

            nodeMap[fromId].arcIds.Add(arcId);
            nodeMap[toId].arcIds.Add(arcId);
            return arc;
        }

        public int FindClosestNode(Vector3 pos)
        {
            int best = -1;
            float bestDist = float.MaxValue;
            for (int i = 0; i < nodes.Count; i++)
            {
                float d = (nodes[i].position - pos).sqrMagnitude;
                if (d < bestDist)
                {
                    bestDist = d;
                    best = nodes[i].id;
                }
            }
            return best;
        }

        public List<int> GetNeighborNodeIds(int nodeId)
        {
            var result = new List<int>();
            if (!nodeMap.TryGetValue(nodeId, out var node)) return result;
            foreach (int arcId in node.arcIds)
            {
                var arc = arcMap[arcId];
                if (arc.fromNodeId == nodeId) result.Add(arc.toNodeId);
                else if (arc.toNodeId == nodeId) result.Add(arc.fromNodeId);
            }
            return result;
        }

        public int GetArcBetween(int fromNodeId, int toNodeId)
        {
            if (!nodeMap.TryGetValue(fromNodeId, out var node)) return -1;
            foreach (int arcId in node.arcIds)
            {
                var arc = arcMap[arcId];
                if ((arc.fromNodeId == fromNodeId && arc.toNodeId == toNodeId) ||
                    (arc.fromNodeId == toNodeId && arc.toNodeId == fromNodeId))
                    return arcId;
            }
            return -1;
        }

        public int GetIncomingArc(int nodeId, int fromNeighborId)
        {
            if (!nodeMap.TryGetValue(nodeId, out var node)) return -1;
            foreach (int arcId in node.arcIds)
            {
                var arc = arcMap[arcId];
                if (arc.toNodeId == nodeId && arc.fromNodeId == fromNeighborId)
                    return arcId;
            }
            return -1;
        }

        public Vector3[] BuildWaypointsForArc(int arcId, bool reverse)
        {
            if (!arcMap.TryGetValue(arcId, out var arc)) return new Vector3[0];
            var wp = arc.waypoints;
            if (!reverse) return wp;
            var rev = new Vector3[wp.Length];
            for (int i = 0; i < wp.Length; i++)
                rev[i] = wp[wp.Length - 1 - i];
            return rev;
        }
    }
}
