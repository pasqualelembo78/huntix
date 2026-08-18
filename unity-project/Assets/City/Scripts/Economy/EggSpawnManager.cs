using System.Collections.Generic;
using UnityEngine;
using City.OSM;

namespace City.Economy
{
    public class EggSpawnManager : MonoBehaviour
    {
        public static EggSpawnManager Instance;

        private const int MAX_EGGS = 30;
        private const float MIN_SPAWN_DIST = 8f;
        private const float EGG_RADIUS = 100f;

        private readonly List<GameObject> eggs = new List<GameObject>();
        private Transform player;
        private Vector3 lastCenter;
        private bool spawned;
        private System.Random rng;

        private void Awake()
        {
            Instance = this;
        }

        public void SpawnEggs(Transform root, OsmCityEnvelope env)
        {
            if (env.roads == null) return;
            player = Game.Instance != null ? Game.Instance.player.transform : null;
            rng = new System.Random(456);

            var positions = FindSpawnPositions(env);
            if (positions.Count == 0) return;

            int count = Mathf.Min(MAX_EGGS, positions.Count);

            for (int i = 0; i < count; i++)
            {
                Vector3 pos = positions[i];
                EggController.Rarity rarity = RollRarity();
                int value = GetValue(rarity);

                var go = new GameObject("Egg_" + i);
                go.transform.SetParent(root, false);

                var egg = go.AddComponent<EggController>();
                egg.Init(pos, rarity);

                eggs.Add(go);
            }

            spawned = true;
            lastCenter = root.position;
            Debug.Log("[EggSpawnManager] Uova create: " + eggs.Count);
        }

        private List<Vector3> FindSpawnPositions(OsmCityEnvelope env)
        {
            var positions = new List<Vector3>();

            // Spawn near roads
            foreach (var road in env.roads)
            {
                if (road.points == null || road.points.Length < 2) continue;
                string hw = road.highway ?? "";
                if (hw == "motorway" || hw == "trunk") continue;

                for (int i = 0; i < road.points.Length; i += 3)
                {
                    if (rng.NextDouble() > 0.15) continue;

                    Vector3 pt = Local(road.points[i]);
                    // Offset from road center
                    Vector3 offset = new Vector3(
                        (float)(rng.NextDouble() - 0.5) * 6f,
                        0f,
                        (float)(rng.NextDouble() - 0.5) * 6f
                    );
                    Vector3 pos = pt + offset;
                    pos.y = 0.3f;

                    // Check min distance from other eggs
                    bool tooClose = false;
                    foreach (var existing in positions)
                    {
                        if (Vector3.Distance(existing, pos) < MIN_SPAWN_DIST)
                        {
                            tooClose = true;
                            break;
                        }
                    }
                    if (!tooClose)
                        positions.Add(pos);

                    if (positions.Count >= MAX_EGGS * 2) break;
                }
                if (positions.Count >= MAX_EGGS * 2) break;
            }

            // Shuffle
            for (int i = positions.Count - 1; i > 0; i--)
            {
                int j = rng.Next(i + 1);
                var tmp = positions[i];
                positions[i] = positions[j];
                positions[j] = tmp;
            }

            return positions;
        }

        private EggController.Rarity RollRarity()
        {
            double roll = rng.NextDouble();
            if (roll < 0.02) return EggController.Rarity.Legendary;
            if (roll < 0.10) return EggController.Rarity.Rare;
            if (roll < 0.30) return EggController.Rarity.Uncommon;
            return EggController.Rarity.Common;
        }

        private int GetValue(EggController.Rarity r)
        {
            switch (r)
            {
                case EggController.Rarity.Common: return 2;
                case EggController.Rarity.Uncommon: return 5;
                case EggController.Rarity.Rare: return 15;
                case EggController.Rarity.Legendary: return 50;
                default: return 2;
            }
        }

        private void Update()
        {
            if (!spawned || player == null) return;

            // Performance: disable distant eggs
            Vector3 pp = player.position;
            for (int i = 0; i < eggs.Count; i++)
            {
                if (eggs[i] == null) continue;
                float d = Vector3.Distance(eggs[i].transform.position, pp);
                eggs[i].SetActive(d < 60f);
            }
        }

        public void RemoveEgg(GameObject egg)
        {
            eggs.Remove(egg);
        }

        private static Vector3 Local(GeoPoint p)
        {
            return new Vector3(CoordinateConverter.LonToX(p.lng), 0f, CoordinateConverter.LatToZ(p.lat));
        }

        public void DespawnAll()
        {
            foreach (var go in eggs)
                if (go != null) Destroy(go);
            eggs.Clear();
            spawned = false;
        }
    }
}
