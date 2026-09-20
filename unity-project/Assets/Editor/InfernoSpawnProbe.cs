using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Huntix.EditorTools
{
    /// <summary>Diagnostica spawn Inferno: collider/sotto-superficie/tag Death
    /// vicino alla palla, per capire la morte immediata al primo tick.
    /// Riga finale: "PROBE3_OK" se riuscita.</summary>
    public static class InfernoSpawnProbe
    {
        public static void Run()
        {
            var sb = new StringBuilder();
            sb.AppendLine("== INFERNO SPAWN PROBE ==");
            try
            {
                var scene = EditorSceneManager.OpenScene("Assets/City/Scenes/InfernoScene.unity",
                    OpenSceneMode.Single);
                var ball = GameObject.Find("Mario Ball (Player)");
                sb.AppendLine("Ball: " + (ball != null ? ball.transform.position.ToString() : "NON TROVATA"));

                int deathTags = 0;
                int floorTags = 0;
                var all = Object.FindObjectsOfType<GameObject>();
                foreach (var g in all)
                {
                    if (!g.activeInHierarchy) continue;
                    if (g.CompareTag("Death")) { deathTags++; sb.AppendLine("  DEATH: " + g.name + " pos=" + g.transform.position + " scl=" + g.transform.lossyScale); }
                    if (g.CompareTag("Floor") || g.CompareTag("FloorVictory")) floorTags++;
                }
                sb.AppendLine("Death attivi=" + deathTags + " Floor/FloorVictory attivi=" + floorTags);

                if (ball != null)
                {
                    Vector3 p = ball.transform.position;
                    var cols = Physics.OverlapSphere(p, 12f);
                    sb.AppendLine("Collider entro 12 dallo spawn: " + cols.Length);
                    foreach (var c in cols)
                    {
                        if (c == null) continue;
                        sb.AppendLine(string.Format("   {0} tag={1} trig={2} en={3} pos={4} scl={5}",
                            c.name, c.gameObject.tag, c.isTrigger,
                            c.enabled, c.transform.position, c.transform.lossyScale));
                        if (c is BoxCollider bc) sb.AppendLine("      BOX size=" + bc.size);
                        if (c is SphereCollider sc) sb.AppendLine("      SPHERE r=" + sc.radius);
                    }
                    RaycastHit hit;
                    bool cast = Physics.Raycast(p + Vector3.up * 1f, Vector3.down, out hit, 300f);
                    sb.AppendLine("Raycast giu' dal ball: " + cast
                        + (cast ? " -> " + hit.collider.name + " hitPos=" + hit.point + " dist=" + hit.distance : string.Empty));

                    var cols2 = Physics.OverlapSphere(p, 3f);
                    sb.AppendLine("Collider entro 3 dallo spawn: " + cols2.Length);
                    foreach (var c in cols2) if (c != null)
                        sb.AppendLine("   " + c.name + " tag=" + c.gameObject.tag + " trig=" + c.isTrigger +
                            " en=" + c.enabled + " pos=" + c.transform.position);
                }
            }
            catch (System.Exception e)
            {
                sb.AppendLine("FATAL: " + e);
            }
            sb.AppendLine("PROBE3_OK");
            System.IO.File.WriteAllText("Assets/Editor/inferno_spawn_report.txt", sb.ToString());
            Debug.Log(sb.ToString());
        }
    }
}