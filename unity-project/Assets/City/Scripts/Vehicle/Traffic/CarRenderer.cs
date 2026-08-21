using UnityEngine;

namespace City.Vehicle.Traffic
{
    public class CarRenderer : MonoBehaviour
    {
        private Vector3 _targetPos;
        private float _targetRotY;
        private float _currentSpeed;
        private float _smoothing = 10f;

        private static readonly string[] CarPrefabs = new string[]
        {
            "Vehicles/sedan", "Vehicles/suv", "Vehicles/van",
            "Vehicles/taxi", "Vehicles/hatchback-sports", "Vehicles/delivery",
        };
        private static GameObject[] _prefabs;
        private static bool _loaded;

        public void Init(TrafficClient.CarUpdate data)
        {
            _targetPos = new Vector3(data.x, 0, data.z);
            _targetRotY = data.ry;
            _currentSpeed = data.speed;
            transform.position = _targetPos;
            transform.rotation = Quaternion.Euler(0, _targetRotY, 0);

            BuildModel(data.model, data.color);
        }

        public void UpdatePosition(TrafficClient.CarUpdate data)
        {
            _targetPos = new Vector3(data.x, 0, data.z);
            _targetRotY = data.ry;
            _currentSpeed = data.speed;
        }

        private void Update()
        {
            transform.position = Vector3.Lerp(transform.position, _targetPos,
                _smoothing * Time.deltaTime);

            Quaternion target = Quaternion.Euler(0, _targetRotY, 0);
            transform.rotation = Quaternion.Slerp(transform.rotation, target,
                _smoothing * Time.deltaTime);
        }

        private void BuildModel(string model, int colorSeed)
        {
            if (!_loaded)
            {
                _prefabs = new GameObject[CarPrefabs.Length];
                for (int i = 0; i < CarPrefabs.Length; i++)
                    _prefabs[i] = Resources.Load<GameObject>(CarPrefabs[i]);
                _loaded = true;
            }

            int idx = Mathf.Abs(colorSeed) % CarPrefabs.Length;
            if (_prefabs != null && _prefabs[idx] != null)
            {
                var inst = Instantiate(_prefabs[idx], transform);
                inst.transform.localPosition = Vector3.zero;
                inst.transform.localRotation = Quaternion.identity;
                inst.transform.localScale = Vector3.one;
                foreach (var col in inst.GetComponentsInChildren<Collider>())
                    col.enabled = false;
                return;
            }

            BuildFallback(colorSeed);
        }

        private void BuildFallback(int seed)
        {
            Color[] colors = new Color[]
            {
                new Color(0.9f, 0.9f, 0.9f), new Color(0.2f, 0.2f, 0.2f),
                new Color(0.7f, 0.1f, 0.1f), new Color(0.1f, 0.3f, 0.7f),
                new Color(0.9f, 0.8f, 0.1f), new Color(0.4f, 0.4f, 0.4f),
            };
            Color c = colors[Mathf.Abs(seed) % colors.Length];

            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0, 0.7f, 0);
            body.transform.localScale = new Vector3(1.6f, 1f, 3.5f);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(c);

            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "Roof";
            roof.transform.SetParent(transform, false);
            roof.transform.localPosition = new Vector3(0, 1.2f, -0.1f);
            roof.transform.localScale = new Vector3(1.3f, 0.4f, 1.6f);
            roof.GetComponent<Renderer>().sharedMaterial = MakeMat(new Color(0.5f, 0.7f, 0.9f, 0.6f));
        }

        private static readonly System.Collections.Generic.Dictionary<Color, Material> _matCache
            = new System.Collections.Generic.Dictionary<Color, Material>();

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
