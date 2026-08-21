using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public enum CarAgentState
    {
        Idle,
        Driving,
        Parked,
        OffMap
    }

    public class CarAgent : MonoBehaviour
    {
        public CarAgentState state = CarAgentState.Idle;
        public float maxSpeed = 10f;
        public float currentSpeed;
        public float acceleration = 6f;
        public float deceleration = 8f;
        public float turnSpeed = 120f;

        private Vector3[] _waypoints;
        private int _wpIndex;
        private float _stopTimer;
        private CarAgent _leader;
        private float _leaderDist;
        public bool looping;

        private static readonly string[] CarPrefabs = new string[]
        {
            "Vehicles/sedan", "Vehicles/suv", "Vehicles/van",
            "Vehicles/taxi", "Vehicles/hatchback-sports", "Vehicles/delivery",
        };
        private static GameObject[] _prefabs;
        private static bool _prefabsLoaded;

        public static void PreloadPrefabs()
        {
            if (_prefabsLoaded) return;
            _prefabs = new GameObject[CarPrefabs.Length];
            for (int i = 0; i < CarPrefabs.Length; i++)
                _prefabs[i] = Resources.Load<GameObject>(CarPrefabs[i]);
            _prefabsLoaded = true;
        }

        public void Init(Vector3[] waypoints, float speed, int colorSeed)
        {
            _waypoints = waypoints;
            _wpIndex = 0;
            maxSpeed = speed;
            currentSpeed = speed * 0.5f;
            state = CarAgentState.Driving;
            _leader = null;

            if (waypoints.Length > 0)
            {
                transform.position = waypoints[0];
                if (waypoints.Length > 1)
                {
                    Vector3 dir = (waypoints[1] - waypoints[0]).normalized;
                    if (dir.sqrMagnitude > 0.01f)
                        transform.rotation = Quaternion.LookRotation(dir, Vector3.up);
                }
            }

            BuildModel(colorSeed);
        }

        public void SetLeader(CarAgent leader, float distance)
        {
            _leader = leader;
            _leaderDist = distance;
        }

        public void ClearLeader()
        {
            _leader = null;
        }

        public Vector3 GetPosition() => transform.position;

        public float GetLeaderDistance() => _leaderDist;

        private void Update()
        {
            if (state != CarAgentState.Driving) return;
            if (_waypoints == null || _waypoints.Length < 2) return;

            AdvanceWaypoint();
            FollowLeader();
            Drive();
        }

        private void AdvanceWaypoint()
        {
            if (_wpIndex >= _waypoints.Length) return;
            Vector3 target = _waypoints[_wpIndex];
            Vector3 toTarget = target - transform.position;
            toTarget.y = 0f;

            if (toTarget.sqrMagnitude < 1f)
                _wpIndex++;
        }

        private void FollowLeader()
        {
            if (_leader == null || !_leader.gameObject.activeInHierarchy)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, maxSpeed, acceleration * Time.deltaTime);
                return;
            }

            float dist = Vector3.Distance(transform.position, _leader.transform.position);
            _leaderDist = dist;

            float safeFollow = 4f + currentSpeed * 0.3f;
            if (dist < safeFollow)
            {
                float brakeTarget = Mathf.Min(_leader.currentSpeed, maxSpeed * 0.5f);
                currentSpeed = Mathf.MoveTowards(currentSpeed, brakeTarget, deceleration * 2f * Time.deltaTime);
            }
            else if (dist < safeFollow * 2f)
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, _leader.currentSpeed, acceleration * 0.5f * Time.deltaTime);
            }
            else
            {
                currentSpeed = Mathf.MoveTowards(currentSpeed, maxSpeed, acceleration * Time.deltaTime);
            }
        }

        private void Drive()
        {
            if (_wpIndex >= _waypoints.Length)
            {
                if (looping)
                {
                    _wpIndex = 0;
                    return;
                }
                state = CarAgentState.Idle;
                currentSpeed = 0f;
                return;
            }

            Vector3 target = _waypoints[_wpIndex];
            Vector3 toTarget = target - transform.position;
            toTarget.y = 0f;

            if (toTarget.sqrMagnitude < 0.01f)
            {
                _wpIndex++;
                return;
            }

            Vector3 moveDir = toTarget.normalized;

            Quaternion targetRot = Quaternion.LookRotation(moveDir, Vector3.up);
            transform.rotation = Quaternion.Slerp(transform.rotation, targetRot,
                turnSpeed * Time.deltaTime / Mathf.Max(1f, Vector3.Angle(transform.forward, moveDir)));

            float step = currentSpeed * Time.deltaTime;
            transform.position += moveDir * Mathf.Min(step, toTarget.magnitude);
        }

        private void BuildModel(int seed)
        {
            PreloadPrefabs();

            if (_prefabs != null && _prefabs.Length > 0)
            {
                int idx = seed % _prefabs.Length;
                if (_prefabs[idx] != null)
                {
                    var inst = Instantiate(_prefabs[idx], transform);
                    inst.transform.localPosition = Vector3.zero;
                    inst.transform.localRotation = Quaternion.identity;
                    inst.transform.localScale = Vector3.one;

                    foreach (var col in inst.GetComponentsInChildren<Collider>())
                        col.enabled = false;
                    return;
                }
            }

            BuildFallback(seed);
        }

        private void BuildFallback(int seed)
        {
            Color[] colors = new Color[]
            {
                new Color(0.9f, 0.9f, 0.9f), new Color(0.2f, 0.2f, 0.2f),
                new Color(0.7f, 0.1f, 0.1f), new Color(0.1f, 0.3f, 0.7f),
                new Color(0.9f, 0.8f, 0.1f), new Color(0.4f, 0.4f, 0.4f),
            };
            Color c = colors[seed % colors.Length];

            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0f, 0.7f, 0f);
            body.transform.localScale = new Vector3(1.6f, 1f, 3.5f);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(c);

            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "Roof";
            roof.transform.SetParent(transform, false);
            roof.transform.localPosition = new Vector3(0f, 1.2f, -0.1f);
            roof.transform.localScale = new Vector3(1.3f, 0.4f, 1.6f);
            roof.GetComponent<Renderer>().sharedMaterial = MakeMat(new Color(0.5f, 0.7f, 0.9f, 0.6f));
        }

        private static readonly Dictionary<Color, Material> _matCache = new Dictionary<Color, Material>();
        private static Material MakeMat(Color c)
        {
            if (_matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            _matCache[c] = m;
            return m;
        }
    }
}
