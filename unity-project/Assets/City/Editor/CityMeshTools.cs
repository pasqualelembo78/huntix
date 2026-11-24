// Adattato da Zone Zero — Copyright (c) 2026 Mishal Bhasim, MIT.
// Sorgenti: Assets/Editor/BakeTransformIntoMesh.cs e
// Assets/Editor/EnableGPUInstancing.cs del repository
// https://github.com/mishal-bhasim/Zone-Zero (licenza MIT integrale in
// THIRD_PARTY_NOTICES.md, sezione 10).
// Modifiche Huntix: voci menu italiane sotto Huntix/Strumenti, messaggi e
// Undo espliciti, nessuna esecuzione automatica, nessun asset importato.
// Sono STRUMENTI EDITOR, non componenti runtime: non toccano profilo, uova,
// PlayerPrefs o stato Android, quindi non hanno bridge bidirezionale.
using System.IO;
using System.Text;
using UnityEditor;
using UnityEngine;

namespace City.EditorTools
{
    public static class CityMeshTools
    {
        [MenuItem("Huntix/Strumenti (MIT Zone Zero)/Cuoce transform nel mesh")]
        private static void BakeSelected()
        {
            GameObject[] selection = Selection.gameObjects;
            if (selection == null || selection.Length == 0)
            {
                EditorUtility.DisplayDialog(
                    "Cuoce transform nel mesh",
                    "Seleziona uno o piu' GameObject con MeshFilter, poi riprova.",
                    "OK");
                return;
            }

            int done = 0;
            var log = new StringBuilder();
            foreach (var go in selection)
            {
                string result = BakeOne(go);
                if (result == null) continue;
                done++;
                log.AppendLine(go.name + " -> " + result);
            }
            AssetDatabase.Refresh();
            EditorUtility.DisplayDialog(
                "Cuoce transform nel mesh",
                "Mesh cucinati: " + done + " di " + selection.Length + ".\n\n" + log,
                "OK");
        }

        private static string BakeOne(GameObject go)
        {
            var mf = go.GetComponent<MeshFilter>();
            if (mf == null || mf.sharedMesh == null) return null;

            Mesh original = mf.sharedMesh;
            var baked = (Mesh)Object.Instantiate(original);
            baked.name = original.name + "_Baked";

            // Rotazione e scala (non posizione) nei vertici; inversa-trasposta
            // per conservare le normali anche con scala non uniforme.
            Matrix4x4 vertexMatrix = Matrix4x4.Rotate(go.transform.localRotation) *
                                     Matrix4x4.Scale(go.transform.localScale);
            Matrix4x4 normalMatrix = vertexMatrix.inverse.transpose;
            Vector3[] verts = baked.vertices;
            for (int i = 0; i < verts.Length; i++)
                verts[i] = vertexMatrix.MultiplyPoint3x4(verts[i]);

            // Pivot ribassato al punto piu' basso: il modello poggia sul suolo.
            float minY = float.MaxValue;
            for (int i = 0; i < verts.Length; i++)
                if (verts[i].y < minY) minY = verts[i].y;
            for (int i = 0; i < verts.Length; i++)
                verts[i].y -= minY;
            baked.vertices = verts;

            Vector3[] normals = baked.normals;
            if (normals != null && normals.Length == verts.Length)
            {
                for (int i = 0; i < normals.Length; i++)
                    normals[i] = normalMatrix.MultiplyVector(normals[i]).normalized;
                baked.normals = normals;
            }
            baked.RecalculateBounds();

            string folder = "Assets/BakedMeshes";
            string originalPath = AssetDatabase.GetAssetPath(original);
            if (!string.IsNullOrEmpty(originalPath))
            {
                string dir = Path.GetDirectoryName(originalPath);
                if (!string.IsNullOrEmpty(dir)) folder = dir;
            }
            if (!AssetDatabase.IsValidFolder(folder))
            {
                Directory.CreateDirectory(folder);
                AssetDatabase.Refresh();
            }
            string assetPath = AssetDatabase.GenerateUniqueAssetPath(
                Path.Combine(folder, baked.name + ".asset"));
            AssetDatabase.CreateAsset(baked, assetPath);
            AssetDatabase.SaveAssets();

            Undo.RecordObject(mf, "Cuoce transform nel mesh");
            Undo.RecordObject(go.transform, "Azzera transform dopo bake");
            mf.sharedMesh = baked;
            go.transform.localRotation = Quaternion.identity;
            go.transform.localScale = Vector3.one;
            go.transform.localPosition = Vector3.zero;
            EditorUtility.SetDirty(mf);
            return assetPath;
        }

        [MenuItem("Huntix/Strumenti (MIT Zone Zero)/Abilita GPU instancing (misurare)")]
        private static void EnableInstancing()
        {
            string[] guids = AssetDatabase.FindAssets("t:Material");
            int count = 0;
            foreach (string guid in guids)
            {
                string path = AssetDatabase.GUIDToAssetPath(guid);
                var mat = AssetDatabase.LoadAssetAtPath<Material>(path);
                if (mat == null || mat.enableInstancing) continue;
                Undo.RecordObject(mat, "Abilita GPU instancing");
                mat.enableInstancing = true;
                count++;
            }
            AssetDatabase.SaveAssets();
            Debug.Log("GPU instancing abilitato su " + count +
                " materiali. Con URP il SRP batcher puo' bastare: misurare con PerfProbe prima di tenere la modifica.");
        }
    }
}
