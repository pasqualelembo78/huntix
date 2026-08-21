using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public class TrafficLightRenderer : MonoBehaviour
    {
        public static TrafficLightRenderer Instance;

        private readonly Dictionary<int, TrafficLightObj> _lights = new Dictionary<int, TrafficLightObj>();
        private Transform _root;

        private class TrafficLightObj
        {
            public GameObject pole;
            public Renderer redRenderer;
            public Renderer yellowRenderer;
            public Renderer greenRenderer;
        }

        private Material _redMat;
        private Material _yellowMat;
        private Material _greenMat;
        private Material _offMat;

        private void Awake()
        {
            Instance = this;
            CreateMaterials();
        }

        public void SetRoot(Transform root) { _root = root; }

        private void CreateMaterials()
        {
            _redMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            _redMat.color = new Color(1f, 0.15f, 0.1f);
            _redMat.SetColor("_EmissionColor", new Color(2f, 0.3f, 0.15f));
            _redMat.EnableKeyword("_EMISSION");

            _yellowMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            _yellowMat.color = new Color(1f, 0.85f, 0.1f);
            _yellowMat.SetColor("_EmissionColor", new Color(2f, 1.7f, 0.2f));
            _yellowMat.EnableKeyword("_EMISSION");

            _greenMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            _greenMat.color = new Color(0.1f, 1f, 0.2f);
            _greenMat.SetColor("_EmissionColor", new Color(0.2f, 2f, 0.4f));
            _greenMat.EnableKeyword("_EMISSION");

            _offMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            _offMat.color = new Color(0.15f, 0.15f, 0.15f);
        }

        public void UpdateLights(List<TrafficClient.TrafficLightUpdate> updates)
        {
            var seen = new HashSet<int>();
            foreach (var tl in updates)
            {
                seen.Add(tl.id);
                if (_lights.TryGetValue(tl.id, out var obj))
                {
                    SetLightState(obj, tl.state);
                }
                else
                {
                    SpawnLight(tl);
                }
            }
        }

        private void SpawnLight(TrafficClient.TrafficLightUpdate tl)
        {
            if (_root == null) return;

            var go = new GameObject($"TrafficLight_{tl.id}");
            go.transform.SetParent(_root, false);
            go.transform.localPosition = new Vector3(tl.x, 0f, tl.z);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.transform.SetParent(go.transform, false);
            pole.transform.localScale = new Vector3(0.12f, 2.0f, 0.12f);
            pole.transform.localPosition = new Vector3(0f, 2.0f, 0f);
            var poleR = pole.GetComponent<Renderer>();
            poleR.material = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            poleR.material.color = new Color(0.2f, 0.2f, 0.2f);

            var box = GameObject.CreatePrimitive(PrimitiveType.Cube);
            box.transform.SetParent(go.transform, false);
            box.transform.localScale = new Vector3(0.3f, 0.7f, 0.2f);
            box.transform.localPosition = new Vector3(0f, 3.8f, 0f);
            var boxR = box.GetComponent<Renderer>();
            boxR.material = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            boxR.material.color = new Color(0.15f, 0.15f, 0.15f);

            var red = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            red.transform.SetParent(go.transform, false);
            red.transform.localScale = new Vector3(0.18f, 0.18f, 0.18f);
            red.transform.localPosition = new Vector3(0f, 4.05f, 0.11f);
            var redR = red.GetComponent<Renderer>();
            redR.material = _offMat;

            var yellow = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            yellow.transform.SetParent(go.transform, false);
            yellow.transform.localScale = new Vector3(0.18f, 0.18f, 0.18f);
            yellow.transform.localPosition = new Vector3(0f, 3.8f, 0.11f);
            var yellowR = yellow.GetComponent<Renderer>();
            yellowR.material = _offMat;

            var green = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            green.transform.SetParent(go.transform, false);
            green.transform.localScale = new Vector3(0.18f, 0.18f, 0.18f);
            green.transform.localPosition = new Vector3(0f, 3.55f, 0.11f);
            var greenR = green.GetComponent<Renderer>();
            greenR.material = _offMat;

            var obj = new TrafficLightObj
            {
                pole = go,
                redRenderer = redR,
                yellowRenderer = yellowR,
                greenRenderer = greenR,
            };
            _lights[tl.id] = obj;
            SetLightState(obj, tl.state);
        }

        private void SetLightState(TrafficLightObj obj, string state)
        {
            obj.redRenderer.material = _offMat;
            obj.yellowRenderer.material = _offMat;
            obj.greenRenderer.material = _offMat;

            switch (state)
            {
                case "red": obj.redRenderer.material = _redMat; break;
                case "yellow": obj.yellowRenderer.material = _yellowMat; break;
                case "green": obj.greenRenderer.material = _greenMat; break;
            }
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }
    }
}
