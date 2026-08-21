using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Vehicle
{
    public class TrafficManager : MonoBehaviour
    {
        public static TrafficManager Instance;

        private const int MAX_TRAFFIC = 25;
        private const float MIN_ROAD_LEN = 30f;
        private const float DESPAWN_PLAYER_DIST = 120f;

        private readonly List<GameObject> cars = new List<GameObject>();
        private Transform player;
        private bool spawned;

        private void Awake()
        {
            Instance = this;
        }

        public void SpawnTraffic(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            var game = City.Game.Instance;
            player = game != null && game.player != null ? game.player.transform : null;

            var rng = new System.Random(999);
            int spawned = 0;

            foreach (var road in env.roads)
            {
                if (spawned >= MAX_TRAFFIC) break;
                if (road.points == null || road.points.Length < 2) continue;
                string hw = road.highway ?? "";
                if (hw == "footway" || hw == "path" || hw == "cycleway" || hw == "steps") continue;

                float roadLen = CalcRoadLength(road);
                if (roadLen < MIN_ROAD_LEN) continue;
                if (rng.NextDouble() > 0.3) continue;

                var pts = new List<Vector3>();
                foreach (var p in road.points)
                    pts.Add(Local(p));

                var go = new GameObject("Traffic_" + spawned);
                go.transform.SetParent(root, false);

                var tc = go.AddComponent<TrafficCar>();
                float spd = 4f + (float)(rng.NextDouble() * 6f);
                tc.Init(pts.ToArray(), spd, spawned);

                // Disabilita collider di interazione (le auto AI non si possono guidare)
                var vi = go.GetComponentInChildren<VehicleInteract>();
                if (vi != null) Destroy(vi);

                cars.Add(go);
                spawned++;
            }

            this.spawned = true;
            UnityEngine.Debug.Log("[TrafficManager] Auto traffico: " + cars.Count);
        }

        private void Update()
        {
            if (!spawned || player == null) return;

            Vector3 pp = player.position;
            for (int i = 0; i < cars.Count; i++)
            {
                if (cars[i] == null) continue;
                float d = Vector3.Distance(cars[i].transform.position, pp);
                cars[i].SetActive(d < DESPAWN_PLAYER_DIST);
            }
        }

        private static Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        private static float CalcRoadLength(OsmRoad road)
        {
            float len = 0f;
            for (int i = 0; i < road.points.Length - 1; i++)
            {
                Vector3 a = Local(road.points[i]);
                Vector3 b = Local(road.points[i + 1]);
                len += (b - a).magnitude;
            }
            return len;
        }

        public void DespawnAll()
        {
            foreach (var go in cars)
                if (go != null) Destroy(go);
            cars.Clear();
            spawned = false;
        }
    }
}
