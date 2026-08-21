using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle.Traffic
{
    public static class NodeSnap
    {
        private const float SNAP_TOLERANCE = 3f;
        private const float SNAP_GRID_SIZE = 5f;

        public static int SnapKey(Vector3 p)
        {
            int x = Mathf.RoundToInt(p.x * 2f);
            int z = Mathf.RoundToInt(p.z * 2f);
            return x * 100007 + z;
        }

        public static Vector3 Local(GeoPoint p)
        {
            return new Vector3(
                CoordinateConverter.LonToX(p.lng),
                0f,
                CoordinateConverter.LatToZ(p.lat));
        }

        private static Vector2Int GridCell(Vector3 wp)
        {
            return new Vector2Int(
                Mathf.FloorToInt(wp.x / SNAP_GRID_SIZE),
                Mathf.FloorToInt(wp.z / SNAP_GRID_SIZE));
        }

        public static Dictionary<long, Vector3> BuildSnapMap(OsmRoad[] roads)
        {
            var result = new Dictionary<long, Vector3>();
            var gridCells = new Dictionary<Vector2Int, List<Vector3>>();

            foreach (var road in roads)
            {
                if (road.points == null) continue;
                foreach (var pt in road.points)
                {
                    Vector3 wp = Local(pt);
                    long gKey = GeoKey(pt);
                    if (result.ContainsKey(gKey)) continue;

                    Vector2Int gc = GridCell(wp);
                    Vector3 rep = wp;
                    bool found = false;

                    for (int dx = -1; dx <= 1 && !found; dx++)
                    for (int dz = -1; dz <= 1 && !found; dz++)
                    {
                        Vector2Int ngc = new Vector2Int(gc.x + dx, gc.y + dz);
                        if (gridCells.TryGetValue(ngc, out var cellReps))
                        {
                            foreach (var existing in cellReps)
                            {
                                if ((wp - existing).sqrMagnitude < SNAP_TOLERANCE * SNAP_TOLERANCE)
                                {
                                    rep = existing;
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }

                    result[gKey] = rep;
                    if (!gridCells.ContainsKey(gc)) gridCells[gc] = new List<Vector3>();
                    if (!gridCells[gc].Contains(rep)) gridCells[gc].Add(rep);
                }
            }
            return result;
        }

        public static Vector3 SnappedLocal(GeoPoint p, Dictionary<long, Vector3> snapMap)
        {
            long gKey = GeoKey(p);
            if (snapMap.TryGetValue(gKey, out var rep)) return rep;
            return Local(p);
        }

        public static long GeoKey(GeoPoint p)
        {
            return ((long)Mathf.RoundToInt((float)p.lng * 1000000) << 32)
                 | (uint)Mathf.RoundToInt((float)p.lat * 1000000);
        }

        public static float CalcRoadLength(OsmRoad road, Dictionary<long, Vector3> snapMap)
        {
            float len = 0f;
            for (int i = 0; i < road.points.Length - 1; i++)
            {
                Vector3 a = SnappedLocal(road.points[i], snapMap);
                Vector3 b = SnappedLocal(road.points[i + 1], snapMap);
                len += (b - a).magnitude;
            }
            return len;
        }

        public static void ComputeJunctionData(
            OsmRoad[] roads,
            Dictionary<long, Vector3> snapMap,
            out Dictionary<int, int> junctionCount,
            out Dictionary<int, List<float>> nodeBearings)
        {
            junctionCount = new Dictionary<int, int>();
            nodeBearings = new Dictionary<int, List<float>>();

            foreach (var road in roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                foreach (var p in road.points)
                {
                    int k = SnapKey(SnappedLocal(p, snapMap));
                    junctionCount[k] = junctionCount.GetValueOrDefault(k) + 1;
                }

                for (int i = 0; i < road.points.Length - 1; i++)
                {
                    Vector3 a = SnappedLocal(road.points[i], snapMap);
                    Vector3 b = SnappedLocal(road.points[i + 1], snapMap);
                    if ((b - a).sqrMagnitude < 0.01f) continue;

                    float bearing = Mathf.Repeat(
                        Mathf.Atan2(b.z - a.z, b.x - a.x) * Mathf.Rad2Deg, 180f);

                    int kA = SnapKey(a);
                    int kB = SnapKey(b);
                    if (!nodeBearings.ContainsKey(kA)) nodeBearings[kA] = new List<float>();
                    if (!nodeBearings.ContainsKey(kB)) nodeBearings[kB] = new List<float>();
                    nodeBearings[kA].Add(bearing);
                    nodeBearings[kB].Add(bearing);
                }
            }
        }

        public static JunctionType ClassifyJunction(List<float> bearings, int roadCount)
        {
            if (roadCount <= 1) return JunctionType.DeadEnd;
            if (IsRealJunction(bearings)) return JunctionType.Real;
            return JunctionType.Simple;
        }

        public static bool IsRealJunction(List<float> bearings, float toleranceDeg = 20f)
        {
            if (bearings == null || bearings.Count < 2) return false;
            float refB = bearings[0];
            foreach (var b in bearings)
            {
                float diff = Mathf.Abs(Mathf.DeltaAngle(refB, b));
                diff = Mathf.Min(diff, 180f - diff);
                if (diff > toleranceDeg) return true;
            }
            return false;
        }

        public static bool IsMinorHighway(string highway)
        {
            return highway == "footway" || highway == "path" || highway == "cycleway"
                || highway == "corridor" || highway == "proposed" || highway == "construction"
                || highway == "raceway" || highway == "steps";
        }

        public static float RoadWidth(string highway)
        {
            switch (highway)
            {
                case "motorway": return 12f;
                case "primary": return 10f;
                case "secondary": return 8f;
                case "tertiary": return 7f;
                case "residential": return 6f;
                case "service": return 4f;
                case "footway": return 2f;
                case "pedestrian": return 3f;
                case "unclassified": return 6f;
                default: return 5f;
            }
        }
    }
}
