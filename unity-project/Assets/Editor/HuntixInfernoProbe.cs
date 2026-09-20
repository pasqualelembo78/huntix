using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Huntix.EditorTools
{
    public static class HuntixInfernoProbe
    {
        [MenuItem("Huntix/Probe Inferno 2")]
        public static void Run()
        {
            var sb = new StringBuilder();
            var scene = EditorSceneManager.OpenScene("Assets/City/Scenes/InfernoScene.unity", OpenSceneMode.Single);
            foreach (var go in Object.FindObjectsOfType<GameObject>(true))
            {
                string nm = go.name.ToLowerInvariant();
                if (nm.Contains("coin") || go.tag == "Coin" || nm.Contains("player") || nm == "mario" || nm.Contains("mario") || nm.Contains("ball"))
                    sb.AppendLine("GO '" + go.name + "' tag=" + go.tag + " active=" + go.activeInHierarchy +
                        " parent=" + (go.transform.parent != null ? go.transform.parent.name : "ROOT") +
                        " pos=" + go.transform.position + " scale=" + go.transform.lossyScale);
            }
            var ball = FindBall(scene);
            if (ball != null)
            {
                sb.AppendLine("=== BALLOBBJ ===");
                foreach (var c in ball.GetComponents<Component>()) sb.AppendLine("  comp: " + c.GetType().Name);
                var sc = ball.GetComponent<SphereCollider>();
                if (sc != null) sb.AppendLine("  SphereCollider r=" + sc.radius + " ctr=" + sc.center + " trig=" + sc.isTrigger);
                var cc = ball.GetComponent<CapsuleCollider>();
                if (cc != null) sb.AppendLine("  Capsule r=" + cc.radius + " h=" + cc.height);
                var bc = ball.GetComponent<BoxCollider>();
                if (bc != null) sb.AppendLine("  Box size=" + bc.size + " ctr=" + bc.center + " trig=" + bc.isTrigger);
                var rb = ball.GetComponent<Rigidbody>();
                if (rb != null) sb.AppendLine("  RB mass=" + rb.mass + " drag=" + rb.drag + " angDrag=" + rb.angularDrag + " useGrav=" + rb.useGravity + " constraint=" + rb.constraints);
                var mf = ball.GetComponent<MeshFilter>();
                if (mf != null && mf.sharedMesh != null) sb.AppendLine("  MESH b=" + mf.sharedMesh.bounds + " v=" + mf.sharedMesh.vertexCount);
                var as_ = ball.GetComponent<AudioSource>();
                if (as_ != null) sb.AppendLine("  AudioSource clip=" + (as_.clip != null ? as_.clip.name : "null"));
                var ps = ball.GetComponent<ParticleSystem>();
                if (ps != null) sb.AppendLine("  ParticleSystem present");
                var pc = ball.GetComponent<FloorIsLava.PlayerController>();
                if (pc != null)
                {
                    sb.AppendLine("  PlayerController: joystick=" + (pc.joystick != null) + " groundParticles=" + (pc.groundParticles != null) + " cameraToFadeToBlack=" + (pc.cameraToFadeToBlack != null));
                    foreach (var f in new[] { "pickupCoinsAudioClip", "jumpAudioClip", "deathAudioClip", "bruhMomentAudioClip", "victoryAudioClip", "deathMaterial" })
                        sb.AppendLine("   " + f + "=" + (GetClip(pc, f) ?? "null"));
                }
                else sb.AppendLine("  PlayerController ABSENTE sulla palla");
            }
            var cam = GameObject.Find("Main Camera");
            if (cam != null)
            {
                sb.AppendLine("=== CAMERA ===");
                foreach (var c in cam.GetComponents<Component>()) sb.AppendLine("  comp: " + c.GetType().Name);
                var cf = cam.GetComponent<FloorIsLava.CameraFollow>();
                if (cf != null)
                {
                    var tp = typeof(FloorIsLava.CameraFollow).GetField("target") ?? typeof(FloorIsLava.CameraFollow).GetField("Target");
                    var tpv = tp != null ? tp.GetValue(cf) : null;
                    sb.AppendLine("  CameraFollow.target=" + (tpv != null ? tpv.ToString() : "null/unset"));
                }
            }
            var ui = Object.FindObjectOfType<FloorIsLava.UiController>();
            if (ui != null) sb.AppendLine("UiController.playerController=" + (ui.playerController != null ? ui.playerController.name : "NULL"));
            File.WriteAllText("/root/giochi/huntix/unitylic/probe2_inferno.log", sb.ToString());
            Debug.Log("PROBE2 ok len=" + sb.Length);
        }

        private static string GetClip(FloorIsLava.PlayerController pc, string field)
        {
            var f = typeof(FloorIsLava.PlayerController).GetField(field);
            var v = f != null ? f.GetValue(pc) : null;
            return v != null ? v.ToString() : null;
        }

        private static GameObject FindBall(Scene scene)
        {
            foreach (var go in Object.FindObjectsOfType<GameObject>(true))
                if ((go.name.Contains("Mario") || go.name.Contains("Ball")) && go.GetComponent<Rigidbody>() != null)
                    return go;
            return null;
        }
    }
}