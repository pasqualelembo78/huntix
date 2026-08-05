// [HuntixAssetGenerator]
// Genera proceduralmente asset grafici mancanti (palazzi low-poly, materiali
// con texture rumore) e li incolla nella scena Outdoor. Tutto in C# puro:
// nessun download esterno, nessuna licenza da gestire, pochi KB su git.
//
// Menu Editor (nella cartella Assets/Editor):
//   Huntix > Generate > Building Assets       -> palazzi + materiali + wiring dati
//   Huntix > Generate > Scatter Buildings     -> baking nella scena Outdoor.unity
//   Huntix > Generate > All Standard Assets   -> tutto in sequenza
//
// Idempotente: AssetDeleteAsset prima di crearli e ri-baking pulisce i
// palazzi esistenti, così si può premere "Generate All" ripetutamente.
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using Huntix.Indoor;

namespace Huntix.EditorTools
{
    public static class HuntixAssetGenerator
    {
        private const string MAT_DIR  = "Assets/Materials/Buildings";
        private const string TEX_DIR  = "Assets/Materials/Buildings/Textures";
        private const string BLDG_DIR = "Assets/Prefabs/Buildings";
        private const string DATA_DIR = "Assets/Data/Buildings";

        private static Material GetMat(string name)
        {
            var obj = AssetDatabase.LoadAssetAtPath<Material>("Assets/Materials/" + name + ".mat");
            if (obj == null) Debug.LogWarning("[HuntixGen] Materiale mancante: " + name);
            return obj;
        }

        private static Material LoadOrCreate(string path, Material fallback)
        {
            var m = AssetDatabase.LoadAssetAtPath<Material>(path);
            return m != null ? m : fallback;
        }

        private static Material NewMaterial(Material src)
        {
            return src != null ? new Material(src) : new Material(Shader.Find("Standard"));
        }

        // Palette pastel per tipo (sovrascritto dal colore del BuildingDefSO se settato)
        private static readonly Dictionary<string, Color> BuildingTint
            = new Dictionary<string, Color>
        {
            { "HOUSE",       new Color(0.92f, 0.74f, 0.60f) },
            { "SCHOOL",      new Color(0.86f, 0.93f, 0.78f) },
            { "RESTAURANT",  new Color(0.92f, 0.66f, 0.56f) },
            { "SUPERMARKET", new Color(0.93f, 0.93f, 0.68f) },
            { "HOSPITAL",    new Color(0.92f, 0.50f, 0.50f) },
            { "GYM",         new Color(0.58f, 0.78f, 0.88f) },
            { "LIBRARY",     new Color(0.70f, 0.82f, 0.66f) },
            { "PARK",        new Color(0.55f, 0.78f, 0.50f) },
            { "OTHER",       new Color(0.85f, 0.85f, 0.85f) }
        };

        // ──────────────────────────────────────────────────────────
        // MESH HELPERS (low-poly)
        // ──────────────────────────────────────────────────────────
        private static Mesh GetCubeMesh()
        {
            var prim = GameObject.CreatePrimitive(PrimitiveType.Cube);
            var mf = prim.GetComponent<MeshFilter>();
            var m = mf.sharedMesh;
            Object.DestroyImmediate(prim);
            return m;
        }

        private static Mesh BuildPyramid(float width, float height)
        {
            var m = new Mesh();
            float h = width / 2f;
            var verts = new Vector3[]
            {
                new(-h, 0, -h), new(h, 0, -h), new(h, 0, h), new(-h, 0, h),
                new(0, height, 0)
            };
            int b = 4;
            var tris = new int[]
            {
                0,2,1, 0,3,2,
                0,1,b, 1,2,b, 2,3,b, 3,0,b
            };
            m.indexFormat = IndexFormat.UInt32;
            m.SetVertices(verts);
            m.SetTriangles(tris, 0);
            m.RecalculateNormals();
            m.RecalculateBounds();
            return m;
        }

        // Albero low-poly: tronco cilindrico + cono fogliame
        private static Mesh BuildTree(float trunkH, float foliageR)
        {
            var m = new Mesh();
            int seg = 8;
            var verts = new List<Vector3>();
            var tris = new List<int>();
            for (int i = 0; i < seg; i++)
            {
                float a = i / (float)seg * Mathf.PI * 2f;
                float nx = Mathf.Cos(a), nz = Mathf.Sin(a);
                verts.Add(new Vector3(nx * 0.18f, 0, nz * 0.18f));
                verts.Add(new Vector3(nx * 0.18f, trunkH, nz * 0.18f));
            }
            for (int i = 0; i < seg; i++)
            {
                int i0 = i * 2, i1 = ((i + 1) % seg) * 2;
                tris.Add(i0); tris.Add(i0 + 1); tris.Add(i1 + 1);
                tris.Add(i0); tris.Add(i1 + 1); tris.Add(i1);
            }
            int apex = verts.Count;
            verts.Add(new Vector3(0, trunkH, 0));
            int firstFoliage = verts.Count;
            for (int i = 0; i < seg; i++)
            {
                float a = i / (float)seg * Mathf.PI * 2f;
                verts.Add(new Vector3(Mathf.Cos(a) * foliageR, trunkH, Mathf.Sin(a) * foliageR));
            }
            for (int i = 0; i < seg; i++)
            {
                tris.Add(apex); tris.Add(firstFoliage + ((i + 1) % seg)); tris.Add(firstFoliage + i);
            }
            m.indexFormat = IndexFormat.UInt32;
            m.SetVertices(verts.ToArray());
            m.SetTriangles(tris.ToArray(), 0);
            m.RecalculateNormals();
            m.RecalculateBounds();
            return m;
        }

        // Mesh del palazzo per tipo (corpo + tetto/distintivo)
        private static Mesh BuildBuildingMesh(string type)
        {
            var cube = GetCubeMesh();
            var instances = new List<CombineInstance>();
            void AddCube(Vector3 pos, Vector3 scale, Quaternion? rot = null)
            {
                instances.Add(new CombineInstance
                {
                    mesh = cube,
                    transform = Matrix4x4.TRS(pos, rot ?? Quaternion.identity, scale)
                });
            }

            float w = 1f, h = 1f, d = 1f;
            switch (type)
            {
                case "HOUSE":
                    w = 1.2f; h = 0.9f; d = 1.2f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    instances.Add(new CombineInstance
                    {
                        mesh = BuildPyramid(w + 0.1f, 0.55f),
                        transform = Matrix4x4.TRS(new(0, h, 0), Quaternion.identity, Vector3.one)
                    });
                    break;
                case "SCHOOL":
                    w = 1.4f; h = 1.2f; d = 1.0f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    AddCube(new(0, h + 0.2f, 0), new(0.4f, 0.6f, 0.4f)); // torre centrale
                    break;
                case "RESTAURANT":
                    w = 1.6f; h = 1.0f; d = 0.9f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    var gable = BuildPyramid(0.9f, 0.45f);
                    instances.Add(new CombineInstance
                    {
                        mesh = gable,
                        transform = Matrix4x4.TRS(new(0, h, 0), Quaternion.Euler(0, 45, 0), Vector3.one)
                    });
                    break;
                case "SUPERMARKET":
                    w = 2.2f; h = 1.4f; d = 1.2f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    break;
                case "HOSPITAL":
                    w = 1.5f; h = 1.4f; d = 1.0f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    // croce rossa in rilievo (due cubi)
                    AddCube(new(0, h * 0.75f, 0.01f), new(0.3f, 0.1f, 0.02f));
                    AddCube(new(0, h * 0.75f, 0.01f), new(0.1f, 0.3f, 0.02f));
                    break;
                case "GYM":
                    w = 1.8f; h = 1.2f; d = 1.8f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    break;
                case "LIBRARY":
                    w = 1.5f; h = 1.3f; d = 1.0f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    AddCube(new(-w / 2 + 0.35f, h, 0.05f), new(0.5f, 0.5f, 0.15f)); // pila libri
                    AddCube(new(w / 2 - 0.35f, h, 0.05f), new(0.5f, 0.5f, 0.15f));
                    break;
                case "PARK":
                    return BuildTree(0.6f, 0.6f);
                case "OTHER":
                    w = 1.3f; h = 1.1f; d = 1.3f;
                    AddCube(new(0, h / 2, 0), new(w, h, d));
                    instances.Add(new CombineInstance
                    {
                        mesh = BuildPyramid(w + 0.1f, 0.4f),
                        transform = Matrix4x4.TRS(new(0, h, 0), Quaternion.identity, Vector3.one)
                    });
                    break;
            }
            var m = new Mesh();
            m.indexFormat = IndexFormat.UInt32;
            m.CombineMeshes(instances.ToArray(), true, true);
            m.RecalculateNormals();
            m.RecalculateBounds();
            return m;
        }

        private static void DeleteIfExists(string assetPath)
        {
            if (AssetDatabase.IsValidFolder(Path.GetDirectoryName(assetPath)))
            {
                if (File.Exists(Path.GetFullPath(assetPath)))
                {
                    AssetDatabase.DeleteAsset(assetPath);
                }
            }
        }

        // =================================================================
        [MenuItem("Huntix/Generate/Building Assets")]
        public static void GenerateBuildingAssets()
        {
            Directory.CreateDirectory(MAT_DIR);
            Directory.CreateDirectory(TEX_DIR);
            Directory.CreateDirectory(BLDG_DIR);

            Material baseWall = GetMat("Building_Wall");
            Material baseRoof = GetMat("Building_Roof");

            var defs = AssetDatabase.FindAssets("t:" + typeof(BuildingDefSO).Name, new[] { DATA_DIR })
                .Select(AssetDatabase.GUIDToAssetPath)
                .Select(AssetDatabase.LoadAssetAtPath<BuildingDefSO>)
                .Where(x => x != null);
            var defMap = defs.ToDictionary(d => d.buildingType.ToString(), d => d);

            var types = new[]
            {
                "HOUSE", "SCHOOL", "RESTAURANT", "SUPERMARKET",
                "HOSPITAL", "GYM", "LIBRARY", "PARK", "OTHER"
            };
            int seed = 1337;
            foreach (var type in types)
            {
                Color tint = BuildingTint[type];
                var def = defMap.TryGetValue(type, out var d) ? d : null;
                // rispetta il colore 3D personalizzato dichiarato nel def (se significativo)
                if (def != null && def.color3D != default(Color)) tint = def.color3D;

                // ── Materiali per tipo (wall/roof) con textura rumore ──
                Material wallMat = NewMaterial(baseWall);
                wallMat.name = "BuildingMat_" + type;
                wallMat.SetColor("_Color", tint);
                var wallTex = ProceduralTextureUtility.GenerateNoiseTint(tint, seed++);
                string wallTexPath = AssetDatabase.GenerateUniqueAssetPath(
                    Path.Combine(TEX_DIR, "BuildingMat_" + type + ".png"));
                ProceduralTextureUtility.WriteTexturePNG(wallTex, wallTexPath);
                wallMat.SetTexture("_BaseMap", AssetDatabase.LoadAssetAtPath<Texture2D>(wallTexPath));
                wallMat.SetFloat("_Metallic", 0.05f);
                wallMat.SetFloat("_Glossiness", 0.45f);
                string wallPath = AssetDatabase.GenerateUniqueAssetPath(
                    Path.Combine(MAT_DIR, "BuildingMat_" + type + ".mat"));
                AssetDatabase.CreateAsset(wallMat, wallPath);

                Material roofMat = NewMaterial(baseRoof);
                roofMat.name = "BuildingRoof_" + type;
                Color rtint = Color.Lerp(tint, tint * 0.82f, 0.5f);
                roofMat.SetColor("_Color", rtint);
                var roofTex = ProceduralTextureUtility.GenerateNoiseTint(rtint, seed++);
                string roofTexPath = AssetDatabase.GenerateUniqueAssetPath(
                    Path.Combine(TEX_DIR, "BuildingRoof_" + type + ".png"));
                ProceduralTextureUtility.WriteTexturePNG(roofTex, roofTexPath);
                roofMat.SetTexture("_BaseMap", AssetDatabase.LoadAssetAtPath<Texture2D>(roofTexPath));
                roofMat.SetFloat("_Metallic", 0.1f);
                roofMat.SetFloat("_Glossiness", 0.3f);
                string roofPath = AssetDatabase.GenerateUniqueAssetPath(
                    Path.Combine(MAT_DIR, "BuildingRoof_" + type + ".mat"));
                AssetDatabase.CreateAsset(roofMat, roofPath);

                // ── Mesh + prefab ──
                Mesh mesh = BuildBuildingMesh(type);
                mesh.name = "Building_" + type;
                string meshPath = AssetDatabase.GenerateUniqueAssetPath(
                    Path.Combine(BLDG_DIR, "Building_" + type + ".mesh"));
                AssetDatabase.CreateAsset(mesh, meshPath);

                // prefab: MeshFilter + MeshRenderer (wall su submesh 0, roof submesh 1) + collider
                var go = new GameObject("Building_" + type);
                var mf = go.AddComponent<MeshFilter>();
                mf.sharedMesh = mesh;
                var mr = go.AddComponent<MeshRenderer>();
                mr.sharedMaterials = (type == "PARK")
                    ? new[] { wallMat }
                    : new[] { wallMat, roofMat };
                var col = go.AddComponent<MeshCollider>();
                col.sharedMesh = mesh;
                col.convex = false;
                var prefab = PrefabUtility.SaveAsPrefabAsset(go,
                    Path.Combine(BLDG_DIR, type + ".prefab"));
                Object.DestroyImmediate(go);

                // wiring: assegna prefab al BuildingDefSO corrispondente
                if (def != null)
                {
                    if (def.prefab == null) def.prefab = prefab;
                    EditorUtility.SetDirty(def);
                }

                Debug.Log("[HuntixGen] Generato: " + type);
            }

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[HuntixGen] Building assets creati in " + BLDG_DIR);
        }

        // =================================================================
        [MenuItem("Huntix/Generate/Scatter Buildings")]
        public static void ScatterBuildingsInOutdoor()
        {
            string scenePath = "Assets/Scenes/Outdoor.unity";
            string fsPath = Path.GetFullPath(scenePath);
            var scene = EditorSceneManager.OpenScene(fsPath, OpenSceneMode.Single);

            var roots = scene.GetRootGameObjects();
            if (roots == null || roots.Length == 0) { Debug.LogError("Nessuna root nella scena Outdoor."); return; }
            Transform root = roots[0].transform.Find("Buildings");
            if (root == null)
            {
                root = new GameObject("Buildings").transform;
                root.SetParent(roots[0].transform, false);
            }

            // pulisci i palazzi già presenti (idempotente)
            for (int i = root.childCount - 1; i >= 0; i--)
                Object.DestroyImmediate(root.GetChild(i).gameObject);

            var types = new[]
            { "HOUSE", "SCHOOL", "RESTAURANT", "SUPERMARKET", "HOSPITAL",
              "GYM", "LIBRARY", "PARK", "OTHER" };
            float radius = 40f;
            float angleStep = 24f;
            int placed = 0;
            for (int i = 0; i < types.Length; i++)
            {
                string type = types[i];
                var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(
                    Path.Combine(BLDG_DIR, type + ".prefab"));
                if (prefab == null) { Debug.LogWarning("Prefab mancante: " + type); continue; }
                int reps = type == "PARK" ? 1 : 2;
                for (int r = 0; r < reps; r++)
                {
                    float angle = (i * angleStep) + r * 33f + 5f;
                    float rad = Mathf.Deg2Rad * angle;
                    Vector3 pos = new(Mathf.Cos(rad) * (radius + r * 3.5f), 0, Mathf.Sin(rad) * (radius + r * 3.5f));
                    var inst = (GameObject)PrefabUtility.InstantiatePrefab(prefab, root);
                    inst.transform.SetPositionAndRotation(pos, Quaternion.identity);
                    inst.name = "Bldg_" + type + "_" + r;
                    placed++;
                }
            }
            EditorSceneManager.SaveScene(scene);
            Debug.Log("[HuntixGen] Incollati " + placed + " palazzi in Outdoor.unity");
        }

        // =================================================================
        [MenuItem("Huntix/Generate/All Standard Assets")]
        public static void GenerateAll()
        {
            GenerateBuildingAssets();
            ScatterBuildingsInOutdoor();
        }
    }
}
