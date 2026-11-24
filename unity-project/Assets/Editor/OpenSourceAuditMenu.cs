// Menu Editor dell'audit open source: controlla i path Resources dichiarati
// e i file di licenza dei pack, senza importare alcun asset dei repository.
using System;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace Huntix.Editor
{
    public static class OpenSourceAuditMenu
    {
        [MenuItem("Huntix/Audit asset handles")]
        public static void AssetHandles()
        {
            Debug.Log("[OpenSourceAudit] " +
                City.Diagnostics.OpenSourceAudit.Run("asset-handles"));
        }

        [MenuItem("Huntix/Audit resource definitions")]
        public static void ResourceDefinitions()
        {
            string[] paths = City.Diagnostics.OpenSourceAudit.KnownResourcePaths;
            int found = 0;
            for (int i = 0; i < paths.Length; i++)
            {
                string full = Path.Combine(Directory.GetCurrentDirectory(), paths[i]);
                bool ok = File.Exists(full) || Directory.Exists(full);
                Debug.Log("[OpenSourceAudit] " + paths[i] + " -> " + (ok ? "OK" : "MANCANTE"));
                if (ok) found++;
            }
            Debug.Log("[OpenSourceAudit] resource-defs " + found + "/" + paths.Length);
            City.Diagnostics.OpenSourceFeatureBridge.Report(
                "resource-defs", "Uovo delle risorse", "città→risorse",
                "file presenti=" + found + "/" + paths.Length,
                found == paths.Length);
        }

        [MenuItem("Huntix/Audit load lifecycle")]
        public static void LoadLifecycle()
        {
            Debug.Log("[OpenSourceAudit] " +
                City.Diagnostics.OpenSourceAudit.Run("load-lifecycle"));
        }

        [MenuItem("Huntix/Audit pack licenses")]
        public static void PackLicenses()
        {
            string[] packs = City.Diagnostics.OpenSourceAudit.PackManifest;
            for (int i = 0; i < packs.Length; i++)
            {
                string[] parts = packs[i].Split('|');
                if (parts.Length == 3)
                    Debug.Log("[OpenSourceAudit] " + parts[0] + " " + parts[1] + " " + parts[2]);
            }
            Debug.Log("[OpenSourceAudit] " +
                City.Diagnostics.OpenSourceAudit.Run("asset-packs"));
        }
    }
}
