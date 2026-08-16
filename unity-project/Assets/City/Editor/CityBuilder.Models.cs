using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

namespace City.Editor
{
    public static partial class CityBuilder
    {
        private static readonly Dictionary<string, GameObject> ModelCache = new Dictionary<string, GameObject>();

        private static Material _grass;
        private static Material _asphalt;
        private static Material _plaza;
        private static Material _wall;
        private static Material _floor;
        private static Material _interiorWall;
        private static Material _ceiling;
        private static Material _door;

        public static GameObject Load(string path)
        {
            if (ModelCache.TryGetValue(path, out GameObject cached)) return cached;
            GameObject go = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            ModelCache[path] = go;
            return go;
        }

        public static GameObject Inst(string path, Vector3 pos, Quaternion rot, Transform parent)
        {
            GameObject prefab = Load(path);
            if (prefab == null)
            {
                Debug.LogWarning("Modello mancante: " + path);
                return null;
            }
            GameObject go = (GameObject)Object.Instantiate(prefab, pos, rot);
            go.transform.SetParent(parent, true);
            float s = ModelScale(path);
            if (s != 1f) go.transform.localScale *= s;
            FixMaterials(go);
            return go;
        }

        public static float ModelScale(string path)
        {
            if (path.Contains("plantSmall")) return 0.85f;
            if (path.Contains("kenney_furniture-kit")) return 0.2f;
            if (path.Contains("kenney_animated-characters-1")) return 0.455f;
            if (path.Contains("kenney_city-kit-roads")) return 8f;
            if (path.Contains("kenney_city-kit-suburban")) return 8f;
            return 1f;
        }

        public static bool IsBuildingModel(string path)
        {
            return path.Contains("kenney_city-kit-suburban") && path.Contains("house_type");
        }

        public static GameObject InstOnGround(string path, Vector3 pos, Quaternion rot, Transform parent)
        {
            GameObject go = Inst(path, pos, rot, parent);
            if (go == null) return null;
            Vector3 p = go.transform.position;
            p.y = -GetBounds(go).min.y;
            go.transform.position = p;
            if (IsBuildingModel(path))
            {
                foreach (MeshFilter mf in go.GetComponentsInChildren<MeshFilter>())
                {
                    if (mf == null || mf.sharedMesh == null) continue;
                    MeshCollider mc = mf.gameObject.AddComponent<MeshCollider>();
                    mc.sharedMesh = mf.sharedMesh;
                }
            }
            return go;
        }

        public static Bounds GetBounds(GameObject go)
        {
            Bounds b = new Bounds();
            bool found = false;
            foreach (Renderer r in go.GetComponentsInChildren<Renderer>())
            {
                if (r == null) continue;
                if (!found) { b = r.bounds; found = true; }
                else b.Encapsulate(r.bounds);
            }
            return b;
        }

        public static Vector3 Size(GameObject go)
        {
            return GetBounds(go).size;
        }

        public static void FixMaterials(GameObject go)
        {
            foreach (Renderer r in go.GetComponentsInChildren<Renderer>())
            {
                if (r == null) continue;
                Material[] mats = r.sharedMaterials;
                for (int i = 0; i < mats.Length; i++)
                {
                    Material m = mats[i];
                    if (m == null)
                    {
                        mats[i] = Lit(Color.white);
                        continue;
                    }
                    if (m.shader == null)
                    {
                        mats[i] = Lit(Color.white);
                        continue;
                    }
                    string sn = m.shader.name;
                    if (sn.StartsWith("Universal Render Pipeline") || sn.StartsWith("Hidden/Universal")) continue;

                    Material lit = NewLit(Color.white);
                    if (m.HasProperty("_MainTex")) lit.SetTexture("_BaseMap", m.GetTexture("_MainTex"));
                    if (m.HasProperty("_Color")) lit.SetColor("_BaseColor", m.GetColor("_Color"));
                    mats[i] = lit;
                }
                r.sharedMaterials = mats;
            }
        }

        private static Material NewLit(Color color)
        {
            Material m = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            if (m.shader == null) m = new Material(Shader.Find("Standard"));
            m.SetColor("_BaseColor", color);
            m.SetFloat("_Smoothness", 0.45f);
            return m;
        }

        private static readonly Dictionary<Color, Material> LitCache = new Dictionary<Color, Material>();

        public static Material Lit(Color color)
        {
            if (!LitCache.TryGetValue(color, out Material m))
            {
                m = NewLit(color);
                LitCache[color] = m;
            }
            return m;
        }

        public static Material Grass()
        {
            if (_grass == null) _grass = Lit(new Color(0.38f, 0.62f, 0.30f));
            return _grass;
        }

        public static Material Asphalt()
        {
            if (_asphalt == null) _asphalt = Lit(new Color(0.22f, 0.22f, 0.23f));
            return _asphalt;
        }

        public static Material Plaza()
        {
            if (_plaza == null) _plaza = Lit(new Color(0.72f, 0.70f, 0.66f));
            return _plaza;
        }

        public static Material Wall()
        {
            if (_wall == null) _wall = Lit(new Color(0.62f, 0.55f, 0.48f));
            return _wall;
        }

        public static Material Floor()
        {
            if (_floor == null) _floor = Lit(new Color(0.55f, 0.42f, 0.30f));
            return _floor;
        }

        public static Material InteriorWall()
        {
            if (_interiorWall == null) _interiorWall = Lit(new Color(0.92f, 0.88f, 0.82f));
            return _interiorWall;
        }

        public static Material Ceiling()
        {
            if (_ceiling == null) _ceiling = Lit(new Color(0.85f, 0.82f, 0.78f));
            return _ceiling;
        }

        public static Material DoorMat()
        {
            if (_door == null) _door = Lit(new Color(0.18f, 0.14f, 0.10f));
            return _door;
        }

        private static Material _window;
        private static Material _roof;

        public static Material Window()
        {
            if (_window == null) _window = Lit(new Color(0.16f, 0.22f, 0.34f));
            return _window;
        }

        public static Material RoofMat()
        {
            if (_roof == null) _roof = Lit(new Color(0.32f, 0.24f, 0.20f));
            return _roof;
        }

        public static Material HouseColor(int seed)
        {
            Color[] colors =
            {
                new Color(0.88f, 0.84f, 0.72f),
                new Color(0.74f, 0.80f, 0.86f),
                new Color(0.84f, 0.74f, 0.66f),
                new Color(0.78f, 0.68f, 0.72f),
            };
            return Lit(colors[Mathf.Abs(seed) % colors.Length]);
        }

        public static Material ShopColor(int index)
        {
            Color[] colors =
            {
                new Color(0.82f, 0.80f, 0.76f),
                new Color(0.80f, 0.78f, 0.66f),
                new Color(0.80f, 0.70f, 0.72f),
            };
            return Lit(colors[Mathf.Abs(index) % colors.Length]);
        }

        public static GameObject CreateCube(string name, Vector3 scale, Vector3 center, Transform parent, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, true);
            go.transform.localRotation = Quaternion.identity;
            go.transform.position = center;
            go.transform.localScale = scale;
            Renderer r = go.GetComponent<Renderer>();
            r.sharedMaterial = mat;
            return go;
        }

        public static GameObject CreateCubeRot(string name, Vector3 scale, Vector3 center, Quaternion rot, Transform parent, Material mat)
        {
            GameObject go = CreateCube(name, scale, center, parent, mat);
            go.transform.rotation = rot;
            return go;
        }

        public static GameObject CreateQuad(string name, float width, float depth, Vector3 center, Transform parent, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Plane);
            go.name = name;
            go.transform.SetParent(parent, true);
            go.transform.position = center;
            go.transform.localScale = new Vector3(width / 10f, 1f, depth / 10f);
            Renderer r = go.GetComponent<Renderer>();
            r.sharedMaterial = mat;
            return go;
        }
    }
}
