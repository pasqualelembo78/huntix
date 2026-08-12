using UnityEngine;

namespace EmptyRoom
{
    // Genera proceduralmente una stanza chiusa: pavimento, soffitto e 4 muri
    // di colori diversi (ben visibili) più un tavolo al centro interagibile.
    public class RoomBuilder : MonoBehaviour
    {
        [Header("Dimensions")]
        public float width = 12f;
        public float depth = 12f;
        public float height = 4f;
        public float wallThickness = 0.3f;

        private void Awake() => Build();

        private void Build()
        {
            CreateBox("Floor", new Vector3(width, wallThickness, depth),
                new Vector3(0f, -wallThickness / 2f, 0f), new Color(0.76f, 0.70f, 0.60f));
            CreateBox("Ceiling", new Vector3(width, wallThickness, depth),
                new Vector3(0f, height + wallThickness / 2f, 0f), new Color(0.85f, 0.85f, 0.88f));

            CreateWall("Wall_North", new Vector3(width, height, wallThickness),
                new Vector3(0f, height / 2f, -depth / 2f), new Color(0.30f, 0.55f, 0.85f));
            CreateWall("Wall_South", new Vector3(width, height, wallThickness),
                new Vector3(0f, height / 2f, depth / 2f), new Color(0.85f, 0.45f, 0.30f));
            CreateWall("Wall_East", new Vector3(wallThickness, height, depth),
                new Vector3(width / 2f, height / 2f, 0f), new Color(0.40f, 0.75f, 0.45f));
            CreateWall("Wall_West", new Vector3(wallThickness, height, depth),
                new Vector3(-width / 2f, height / 2f, 0f), new Color(0.80f, 0.75f, 0.30f));

            BuildTable(Vector3.zero);
        }

        private GameObject CreateBox(string name, Vector3 size, Vector3 pos, Color color)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.position = pos;
            go.transform.localScale = size;
            var rend = go.GetComponent<Renderer>();
            // Compatibile sia con Built-in (_Color) che URP (_BaseColor).
            rend.material.color = color;
            if (rend.material.HasProperty("_BaseColor"))
                rend.material.SetColor("_BaseColor", color);
            return go;
        }

        private GameObject CreateWall(string name, Vector3 size, Vector3 pos, Color color)
        {
            var w = CreateBox(name, size, pos, color);
            w.tag = "Wall";
            return w;
        }

        private void BuildTable(Vector3 center)
        {
            var table = new GameObject("Table");
            table.tag = "Interactable";
            float topY = 1f;

            var top = GameObject.CreatePrimitive(PrimitiveType.Cube);
            top.name = "TableTop";
            top.transform.SetParent(table.transform);
            top.transform.localScale = new Vector3(2f, 0.1f, 1.2f);
            top.transform.position = new Vector3(center.x, topY, center.z);
            top.GetComponent<Renderer>().material.color = new Color(0.55f, 0.36f, 0.20f);

            float legH = topY - 0.05f;
            Vector3[] legOffsets =
            {
                new Vector3(-0.9f, 0f, -0.5f),
                new Vector3(0.9f, 0f, -0.5f),
                new Vector3(-0.9f, 0f, 0.5f),
                new Vector3(0.9f, 0f, 0.5f),
            };
            foreach (var off in legOffsets)
            {
                var leg = GameObject.CreatePrimitive(PrimitiveType.Cube);
                leg.name = "Leg";
                leg.transform.SetParent(table.transform);
                leg.transform.localScale = new Vector3(0.15f, legH, 0.15f);
                leg.transform.position = new Vector3(center.x + off.x, legH / 2f, center.z + off.z);
                leg.GetComponent<Renderer>().material.color = new Color(0.40f, 0.26f, 0.14f);
            }

            table.AddComponent<Interactable>().label = "Tavolo";
        }
    }
}
