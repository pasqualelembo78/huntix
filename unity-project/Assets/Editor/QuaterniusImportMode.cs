using UnityEditor;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>
    /// Reimposta l'import FBX degli edifici Quaternius in modalita' IMPORT
    /// materiali embedded (materialImportMode=Import, location=Embedded):
    /// gli FBX definiscono materiali Phong (MI_RedBrick_Pale, MI_Trim, ...)
    /// che referenziano le texture T_*.png tramite path assoluti Windows
    /// inesistenti su questa macchina. L'import attuale e' in modalita'
    /// materialImportMode=0 (None) -> tutti i renderer usano il default
    /// URP "Lit.mat" (grigio, senza texture). In modalita' Import Unity
    /// estrae i materiali embedded e cerca le texture per nome nella
    /// cartella Textures/ accanto all'FBX (gia' presenti).
    /// Eseguibile da CLI:
    ///   -executeMethod Huntix.EditorTools.QuaterniusImportMode.Fix
    /// </summary>
    public static class QuaterniusImportMode
    {
        private static readonly string[] FbxPaths =
        {
            "Assets/Resources/Buildings/Quaternius/Building_Large_2.fbx",
            "Assets/Resources/Buildings/Quaternius/Building_Medium_2_001.fbx",
            "Assets/Resources/Buildings/Quaternius/Building_Small_1.fbx",
        };

        public static void Fix()
        {
            foreach (string path in FbxPaths)
            {
                var imp = (ModelImporter)AssetImporter.GetAtPath(path);
                if (imp == null)
                {
                    Debug.LogError("[QuatImport] NO IMPORTER: " + path);
                    continue;
                }
                Debug.Log("[QuatImport] PRIMA '" + path + "' importMode=" +
                    (int)imp.materialImportMode + " location=" + (int)imp.materialLocation +
                    " search=" + imp.materialSearch);
                // In questa versione Unity (2022.3.52f1) l'enum espone
                // ModelImporterMaterialImportMode.Import (modo Import) e
                // Location InPrefab = 1 (materiali embedded, gia' nel .meta).
                // Il .meta ha importMode=0 = None -> nessun materiale importato,
                // tutti i renderer usano il default URP "Lit". Passiamo a Import.
                imp.materialImportMode = ModelImporterMaterialImportMode.Import;
                imp.materialSearch = ModelImporterMaterialSearch.RecursiveUp;
                imp.SaveAndReimport();
                Debug.Log("[QuatImport] DOPO '" + path + "' importMode=" +
                    (int)imp.materialImportMode + " location=" + (int)imp.materialLocation);
            }
            Debug.Log("[QuatImport] DONE");
        }
    }
}