using System.Collections.Generic;
using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Migliorie scena Inferno:
    ///  1) Crea una copia "smoothed" della mesh EGGZ (normali/UBV rigenerati
    ///     perche' il .ply/.dae del kit non ha normali) salvata come asset,
    ///     + materiale URP/Lit dorato.
    ///  2) Modifica il prefab condiviso "SMB3 Coin" (guid 61e4b337): rimuove
    ///     lo SpriteRenderer e aggiunge il figlio "EggVisual" (mesh uovo) con
    ///     uovo "in piedi" (asse lungo su Y), ridimensiona la BoxCollider
    ///     trigger alle proporzioni dell'uovo. Tag "Coin" resta ereditato dal
    ///     prefab: TUTTE le ~140 istanze in InfernoScene diventano uova
    ///     senza toccare le trasformate.
    ///  3) Attacca FloorIsLava.MiaCityAvatar alla palla "Mario Ball (Player)"
    ///     (visual = personaggio Miacitta', fisica invariata).
    /// </summary>
    public static class InfernoImprovements
    {
        internal const string EggModelPath = "Assets/Resources/Eggs/Models/EGGZ-simplified.dae";
        internal const string CoinPrefabPath = "Assets/ThirdParty/FloorIsLavaBowserCastle/Prefabs/SMB3 Coin.prefab";
        internal const string ScenePath = "Assets/City/Scenes/InfernoScene.unity";
        internal const string EggMatPath = "Assets/ThirdParty/FloorIsLavaBowserCastle/Materials/EasterEgg.mat";
        internal const string EggMeshPath = "Assets/ThirdParty/FloorIsLavaBowserCastle/Models/EGGZ-smoothed.asset";
        internal const string ReportPath = "Assets/Editor/inferno_improvements_report.txt";

        public static void SetupInferno()
        {
            Run();
        }

        [MenuItem("Huntix/Setup Inferno Improvements")]
        public static void Run()
        {
            var sb = new StringBuilder();
            sb.AppendLine("== INFERNO IMPROVEMENTS ==");
            int errors = 0;
            try
            {
                Mesh eggMesh = PrepareEggMesh(sb);
                sb.AppendLine();
                Material eggMat = PrepareEggMaterial(sb);
                sb.AppendLine();
                RebuildCoinPrefab(sb, eggMesh, eggMat);
                sb.AppendLine();
                int coins = AttachAvatar(sb);
                sb.AppendLine();
                sb.AppendLine("Coin attive rilevate in scena: " + coins);
            }
            catch (System.Exception e)
            {
                errors++;
                sb.AppendLine();
                sb.AppendLine("FATAL: " + e);
            }

            sb.AppendLine();
            sb.AppendLine("ERRORI: " + errors);
            sb.AppendLine(EditorSceneManager.GetActiveScene() != null && EditorSceneManager.GetActiveScene().isDirty
                ? "ATTENZIONE: scena ancora dirty (non salvata)." : "Scena salvata OK.");

            File.WriteAllText(ReportPath, sb.ToString());
            AssetDatabase.SaveAssets();
            Debug.Log(sb.ToString());
        }

        private static Mesh PrepareEggMesh(StringBuilder sb)
        {
            var eggRoot = AssetDatabase.LoadMainAssetAtPath(EggModelPath) as GameObject;
            if (eggRoot == null) throw new System.Exception("Modello egg non trovato: " + EggModelPath);
            var mf = eggRoot.GetComponentInChildren<MeshFilter>();
            if (mf == null || mf.sharedMesh == null) throw new System.Exception("Nessuna mesh nel modello egg.");
            var src = mf.sharedMesh;

            var mesh = Object.Instantiate(src);
            mesh.name = "EGGZ-smoothed";
            if (mesh.normals == null || mesh.normals.Length != mesh.vertexCount) mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            AssetDatabase.DeleteAsset(EggMeshPath);
            AssetDatabase.CreateAsset(mesh, EggMeshPath);
            AssetDatabase.SaveAssets();

            sb.AppendLine("[1] Mesh egg: verts=" + mesh.vertexCount + " tris=" + mesh.triangles.Length / 3);
            sb.AppendLine("    bounds center=" + mesh.bounds.center + " size=" + mesh.bounds.size);
            return mesh;
        }

        private static Material PrepareEggMaterial(StringBuilder sb)
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            var mat = AssetDatabase.LoadAssetAtPath<Material>(EggMatPath);
            if (mat == null)
            {
                mat = new Material(shader);
                AssetDatabase.CreateAsset(mat, EggMatPath);
            }
            else
            {
                mat.shader = shader;
            }
            if (mat.shader.name.StartsWith("Universal Render Pipeline"))
            {
                mat.SetColor("_BaseColor", new Color(1.0f, 0.83f, 0.34f));
                mat.SetFloat("_Smoothness", 0.35f);
                mat.SetFloat("_Metallic", 0.0f);
            }
            else
            {
                mat.SetColor("_Color", new Color(1.0f, 0.83f, 0.34f));
            }
            EditorUtility.SetDirty(mat);
            sb.AppendLine("[2] Material egg: " + EggMatPath + " (shader " + mat.shader.name + ")");
            return mat;
        }

        private static void RebuildCoinPrefab(StringBuilder sb, Mesh eggMesh, Material eggMat)
        {
            var root = PrefabUtility.LoadPrefabContents(CoinPrefabPath);
            try
            {
                var sr = root.GetComponent<SpriteRenderer>();
                if (sr != null) Object.DestroyImmediate(sr);
                if (root.GetComponent<MeshFilter>() != null)
                    Object.DestroyImmediate(root.GetComponent<MeshFilter>());
                if (root.GetComponent<MeshRenderer>() != null)
                    Object.DestroyImmediate(root.GetComponent<MeshRenderer>());

                float rootScale = root.transform.localScale.x;
                if (rootScale <= 0f) rootScale = 1f;

                float longAxis = 0.7f; // dimensione mondo finale asse lungo (~0.7 u)
                float s = longAxis / eggMesh.bounds.size.z / rootScale;
                var egg = new GameObject("EggVisual");
                egg.transform.SetParent(root.transform, false);
                egg.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
                egg.transform.localScale = Vector3.one * s;
                egg.transform.localPosition = Vector3.zero;
                egg.AddComponent<MeshFilter>().sharedMesh = eggMesh;
                egg.AddComponent<MeshRenderer>().sharedMaterial = eggMat;

                var bc = root.GetComponent<BoxCollider>();
                if (bc == null) bc = root.AddComponent<BoxCollider>();
                bc.isTrigger = true;
                Vector3 world = new Vector3(eggMesh.bounds.size.x, eggMesh.bounds.size.z, eggMesh.bounds.size.y) * s * rootScale;
                bc.size = new Vector3(world.x / rootScale, world.y / rootScale, world.z / rootScale);
                bc.center = new Vector3(0f, world.y / rootScale * 0.5f, 0f);

                PrefabUtility.SaveAsPrefabAsset(root, CoinPrefabPath);
                sb.AppendLine("[3] Prefab " + CoinPrefabPath + " aggiornato.");
                sb.AppendLine("    egg scale=" + s + " collider(local) size=" + bc.size + " center=" + bc.center);
            }
            finally
            {
                PrefabUtility.UnloadPrefabContents(root);
            }
        }

        private static int AttachAvatar(StringBuilder sb)
        {
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            var balls = Object.FindObjectsOfType<FloorIsLava.PlayerController>();
            GameObject ball = null;
            foreach (var b in balls)
            {
                if (b.name.Contains("Mario Ball")) { ball = b.gameObject; break; }
            }
            if (ball == null && balls.Length > 0) ball = balls[0].gameObject;

            int coinCount = 0;
            var coins = Object.FindObjectsOfType<GameObject>();
            foreach (var c in coins) if (c.CompareTag("Coin")) coinCount++;

            if (ball == null)
            {
                sb.AppendLine("[4] Palla NON trovata: avatar NON attaccato.");
            }
            else
            {
                if (ball.GetComponent<FloorIsLava.MiaCityAvatar>() == null)
                    ball.AddComponent<FloorIsLava.MiaCityAvatar>();
                sb.AppendLine("[4] Avatar MiaCityAvatar attaccato a: " + ball.name);
            }

            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene);
            return coinCount;
        }
    }
}