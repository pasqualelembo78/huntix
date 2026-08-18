using UnityEngine;
using City.OSM;
using City.Economy;

namespace City.NPC
{
    [RequireComponent(typeof(Collider))]
    public class NPCController : MonoBehaviour
    {
        public float walkSpeed = 1.5f;
        public float pauseMin = 2f;
        public float pauseMax = 6f;

        private Vector3[] waypoints;
        private int currentTarget;
        private bool walking;
        private float pauseTimer;
        private Animator animator;
        private NPCMission mission;
        private bool focused;

        private static readonly Color[] SkinColors = new Color[]
        {
            new Color(0.87f, 0.72f, 0.58f),
            new Color(0.75f, 0.55f, 0.40f),
            new Color(0.60f, 0.42f, 0.30f),
        };

        private static readonly Color[] ShirtColors = new Color[]
        {
            new Color(0.2f, 0.4f, 0.8f),
            new Color(0.8f, 0.2f, 0.2f),
            new Color(0.2f, 0.7f, 0.3f),
            new Color(0.9f, 0.7f, 0.1f),
            new Color(0.6f, 0.2f, 0.7f),
            new Color(0.1f, 0.6f, 0.7f),
            new Color(0.9f, 0.5f, 0.1f),
        };

        public void Init(Vector3[] path, System.Random rng)
        {
            waypoints = path;
            currentTarget = 0;
            walking = false;
            pauseTimer = Random.Range(0.5f, 2f);

            BuildModel(rng);
            if (waypoints.Length > 0)
                transform.position = waypoints[0];

            // 30% chance to have a mission
            if (rng.NextDouble() < 0.30)
            {
                mission = gameObject.AddComponent<NPCMission>();
                var data = NPCMission.GenerateMissionData(rng.Next(100000));
                mission.ApplyData(data);
                mission.AttachToNPC(this);

                // Add trigger collider for mission interaction
                var triggerCol = gameObject.AddComponent<CapsuleCollider>();
                triggerCol.height = 2.5f;
                triggerCol.radius = 1.5f;
                triggerCol.center = new Vector3(0f, 1f, 0f);
                triggerCol.isTrigger = true;
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            focused = true;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            focused = false;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, false);
        }

        private void Update()
        {
            if (waypoints == null || waypoints.Length < 2) return;

            if (!walking)
            {
                pauseTimer -= Time.deltaTime;
                if (pauseTimer <= 0f)
                {
                    walking = true;
                    currentTarget = (currentTarget + 1) % waypoints.Length;
                }
                if (animator != null) animator.SetFloat("Speed", 0f);
                return;
            }

            Vector3 target = waypoints[currentTarget];
            Vector3 dir = target - transform.position;
            dir.y = 0f;
            float dist = dir.magnitude;

            if (dist < 0.3f)
            {
                walking = false;
                pauseTimer = Random.Range(pauseMin, pauseMax);
                return;
            }

            Vector3 move = dir.normalized * walkSpeed * Time.deltaTime;
            transform.position += move;

            Quaternion look = Quaternion.LookRotation(dir.normalized, Vector3.up);
            transform.rotation = Quaternion.Slerp(transform.rotation, look, 8f * Time.deltaTime);

            if (animator != null) animator.SetFloat("Speed", walkSpeed);
        }

        private void BuildModel(System.Random rng)
        {
            Color skin = SkinColors[rng.Next(SkinColors.Length)];
            Color shirt = ShirtColors[rng.Next(ShirtColors.Length)];
            Color pants = new Color(0.2f, 0.2f, 0.3f);

            float headR = 0.22f;
            float bodyW = 0.4f, bodyH = 0.55f, bodyD = 0.25f;

            // Testa
            var head = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            head.name = "Head";
            head.transform.SetParent(transform, false);
            head.transform.localPosition = new Vector3(0f, 1.45f, 0f);
            head.transform.localScale = Vector3.one * headR * 2f;
            head.GetComponent<Renderer>().sharedMaterial = MakeMat(skin);

            // Corpo
            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            body.transform.SetParent(transform, false);
            body.transform.localPosition = new Vector3(0f, 0.95f, 0f);
            body.transform.localScale = new Vector3(bodyW, bodyH, bodyD);
            body.GetComponent<Renderer>().sharedMaterial = MakeMat(shirt);

            // Gambe
            var ll = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ll.name = "LegL";
            ll.transform.SetParent(transform, false);
            ll.transform.localPosition = new Vector3(-0.1f, 0.4f, 0f);
            ll.transform.localScale = new Vector3(0.12f, 0.5f, 0.14f);
            ll.GetComponent<Renderer>().sharedMaterial = MakeMat(pants);

            var lr = GameObject.CreatePrimitive(PrimitiveType.Cube);
            lr.name = "LegR";
            lr.transform.SetParent(transform, false);
            lr.transform.localPosition = new Vector3(0.1f, 0.4f, 0f);
            lr.transform.localScale = new Vector3(0.12f, 0.5f, 0.14f);
            lr.GetComponent<Renderer>().sharedMaterial = MakeMat(pants);

            // Braccia
            var al = GameObject.CreatePrimitive(PrimitiveType.Cube);
            al.name = "ArmL";
            al.transform.SetParent(transform, false);
            al.transform.localPosition = new Vector3(-bodyW * 0.5f - 0.1f, 1.0f, 0f);
            al.transform.localScale = new Vector3(0.1f, 0.4f, 0.1f);
            al.GetComponent<Renderer>().sharedMaterial = MakeMat(skin);

            var ar = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ar.name = "ArmR";
            ar.transform.SetParent(transform, false);
            ar.transform.localPosition = new Vector3(bodyW * 0.5f + 0.1f, 1.0f, 0f);
            ar.transform.localScale = new Vector3(0.1f, 0.4f, 0.1f);
            ar.GetComponent<Renderer>().sharedMaterial = MakeMat(skin);

            // Disabilita collider su parti del corpo
            DisableCollider(head);
            DisableCollider(body);
            DisableCollider(ll);
            DisableCollider(lr);
            DisableCollider(al);
            DisableCollider(ar);

            // Collider capsule per il NPC
            var cc = gameObject.AddComponent<CapsuleCollider>();
            cc.height = 1.7f;
            cc.radius = 0.2f;
            cc.center = new Vector3(0f, 0.85f, 0f);
        }

        private static void DisableCollider(GameObject go)
        {
            var c = go.GetComponent<Collider>();
            if (c != null) c.enabled = false;
        }

        private static readonly System.Collections.Generic.Dictionary<Color, Material> matCache
            = new System.Collections.Generic.Dictionary<Color, Material>();

        private static Material MakeMat(Color c)
        {
            if (matCache.TryGetValue(c, out var m)) return m;
            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            m = new Material(shader);
            if (shader.name.StartsWith("Universal Render Pipeline/Lit"))
                m.SetColor("_BaseColor", c);
            else
                m.SetColor("_Color", c);
            matCache[c] = m;
            return m;
        }
    }
}
