using UnityEngine;
using UnityEditor;
using System.Text;

// Diagnostica una-tantum: per ogni FBX in Resources/Vehicles misura il
// bounding box complessivo e la posizione delle parti "front/rear/wheel"
// per dedurre lasse avanti del modello. Output: /tmp/opencode/vehicle_axes.txt
public static class VehicleAxisProbe
{
    public static void Probe()
    {
        var sb = new StringBuilder();
        string[] guids = AssetDatabase.FindAssets("t:Model",
            new[] { "Assets/Resources/Vehicles" });
        sb.AppendLine("models=" + guids.Length);
        foreach (var g in guids)
        {
            string p = AssetDatabase.GUIDToAssetPath(g);
            var src = AssetDatabase.LoadAssetAtPath<GameObject>(p);
            if (src == null) continue;
            var inst = Object.Instantiate(src);
            inst.transform.position = Vector3.zero;
            inst.transform.rotation = Quaternion.identity;

            Bounds b = new Bounds();
            bool first = true;
            foreach (var r in inst.GetComponentsInChildren<Renderer>())
            {
                if (first) { b = r.bounds; first = false; }
                else b.Encapsulate(r.bounds);
            }
            sb.AppendLine(p + " size=" + b.size.ToString("F3"));

            foreach (var t in inst.GetComponentsInChildren<Transform>())
            {
                string n = t.name.ToLowerInvariant();
                if (n.Contains("front") || n.Contains("rear") ||
                    n.Contains("back") || n.Contains("_fl") ||
                    n.Contains("_fr") || n.Contains("_rl") ||
                    n.Contains("_rr") || n.Contains("headlight") ||
                    n.Contains("windshield") || n.Contains("cabin") ||
                    n.Contains("hood") || n.Contains("bonnet"))
                    sb.AppendLine("    part " + t.name +
                        " pos=" + t.position.ToString("F3"));
            }
            Object.DestroyImmediate(inst);
        }
        System.IO.File.WriteAllText("/tmp/opencode/vehicle_axes.txt", sb.ToString());
        Debug.Log("VehicleAxisProbe done");
    }
}
