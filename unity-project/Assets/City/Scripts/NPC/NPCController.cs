using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using City.Economy;
using City.Player;
using TMPro;

namespace City.NPC
{
    // Pedone animato del mondo a chunk. Il modello e' il personaggio Kenney
    // (characterMedium) istanziato da NPCPopulator: qui si gestisce camminata
    // lungo i marciapiedi, pause, missione opzionale e l'identita' usata
    // dalla chat IA quando il giocatore tocca il pedone.
    public class NPCController : MonoBehaviour
    {
        public float walkSpeed = 1.4f;
        public float pauseMin = 2f;
        public float pauseMax = 6f;

        private Vector3[] waypoints;
        private int currentTarget;
        private bool walking;
        private float pauseTimer;
        private NPCMission mission;

        /// <summary>Pedoni attualmente in scena (per il rilevatore di travol-
        /// gimenti): aggiunti/rimossi con OnEnable/OnDisable.</summary>
        public static readonly List<NPCController> Active =
            new List<NPCController>();

        private void OnEnable()
        {
            if (!Active.Contains(this)) Active.Add(this);
        }

        private void OnDisable()
        {
            Active.Remove(this);
        }

        // ── identita' per la chat (tap-to-talk) ──
        public string NpcId { get; private set; }
        public string DisplayName { get; private set; }

        // personaggio RealLife associato (roleplay): id reale del backend,
        // usato per la chat cosi' l'IA parla con la personalita' giusta
        public string CharacterId { get; private set; }
        public string CharacterRole { get; private set; }
        public string CharacterAvatar { get; private set; }

        private static readonly string[] Names =
        {
            "Marco", "Giulia", "Luca", "Anna", "Paolo", "Sara", "Franco",
            "Chiara", "Antonio", "Elena", "Giovanni", "Maria", "Salvatore",
            "Rosaria", "Domenico", "Carmela", "Vito", "Nadia",
        };

        public void Init(Vector3[] path, System.Random rng, string npcId)
        {
            waypoints = path;
            currentTarget = 0;
            walking = false;
            pauseTimer = Random.Range(0.5f, 2f);
            NpcId = npcId;
            DisplayName = Names[rng.Next(Names.Length)];

            SetupModel(rng);
            if (waypoints.Length > 0)
                transform.position = waypoints[0];

            // Targhetta col nome anche per i cittadini senza personaggio
            // RealLife: rende evidente chi si puo' toccare per parlare.
            EnsureNameTag();

            // roleplay: collega questo cittadino al personaggio RealLife
            // (immediato se la directory e' in cache, altrimenti appena carica)
            CityCharacterDirectory.Attach(this);

            // 30% chance to have a mission
            if (rng.NextDouble() < 0.30)
            {
                mission = gameObject.AddComponent<NPCMission>();
                try
                {
                    var data = NPCMission.GenerateMissionData(rng.Next(100000));
                    mission.ApplyData(data);
                    mission.AttachToNPC(this);
                }
                catch (System.Exception me)
                {
                    Debug.LogWarning("[NPCController] mission init fallita su " +
                        DisplayName + ": " + me);
                    Destroy(mission);
                    mission = null;
                }

                // Add trigger collider for mission interaction
                var triggerCol = gameObject.AddComponent<CapsuleCollider>();
                triggerCol.height = 2.5f;
                triggerCol.radius = 1.5f;
                triggerCol.center = new Vector3(0f, 1f, 0f);
                triggerCol.isTrigger = true;
            }
        }

        /// <summary>Applica l'identita' del personaggio RealLife (roleplay).</summary>
        public void ApplyCharacter(CityCharacterDirectory.CharacterDef def)
        {
            if (def == null || string.IsNullOrEmpty(def.id)) return;
            CharacterId = def.id;
            CharacterRole = def.role ?? "";
            CharacterAvatar = string.IsNullOrEmpty(def.avatar) ? "\uD83D\uDE42" : def.avatar;
            if (!string.IsNullOrEmpty(def.name))
                DisplayName = def.name;
            EnsureNameTag();
        }

        // ── caos: travolgimento e fuga ──
        private bool _down;
        private float _downUntil;
        private float _knockCd;
        private float _fleeUntil;
        private float _baseSpeed = -1f;
        private Quaternion _rotBeforeFall;
        private Coroutine _fallCo;

        /// <summary>Il player ha travolto il pedone: cade, si lamenta, poi
        /// si rialza e scappa al doppio della velocita'. Se era un amico,
        /// l'amicizia ne risente (-3).</summary>
        public void KnockDown(Vector3 pushDir)
        {
            if (_down || Time.unscaledTime < _knockCd) return;
            _knockCd = Time.unscaledTime + 8f;
            _down = true;
            _downUntil = Time.unscaledTime + 3.2f;
            _rotBeforeFall = transform.rotation;
            walking = false;
            SetAnimSpeed(0f);

            var cap = GetComponent<CapsuleCollider>();
            if (cap != null) cap.enabled = false;

            if (!string.IsNullOrEmpty(CharacterId))
            {
                RelationshipManager.RemovePoints(CharacterId, 3);
                Toast("\ud83d\ude20 " + DisplayName +
                    " ti ha visto! Amicizia in pezzi...");
            }
            else
            {
                Toast("Hai fatto cadere un pedone!");
            }
            City.Environment.ChaosTracker.AddChaos(1);

            if (_fallCo != null) StopCoroutine(_fallCo);
            _fallCo = StartCoroutine(FallAndFlee(pushDir));
        }

        private IEnumerator FallAndFlee(Vector3 dir)
        {
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.001f) dir = Vector3.forward;
            dir.Normalize();
            Vector3 axis = Vector3.Cross(Vector3.up, dir);
            Vector3 pivot = transform.position + dir * 0.15f;
            pivot.y = Mathf.Max(0f, transform.position.y - 0.85f);

            const float totalDeg = 86f;
            float done = 0f;
            while (done < totalDeg)
            {
                float step = 420f * Time.deltaTime;
                if (done + step > totalDeg) step = totalDeg - done;
                transform.RotateAround(pivot, axis, step);
                done += step;
                yield return null;
            }

            while (Time.unscaledTime < _downUntil) yield return null;

            // si rialza
            done = 0f;
            while (done < totalDeg)
            {
                float step = 260f * Time.deltaTime;
                if (done + step > totalDeg) step = totalDeg - done;
                transform.RotateAround(pivot, axis, -step);
                done += step;
                yield return null;
            }
            transform.rotation = _rotBeforeFall;

            var cap = GetComponent<CapsuleCollider>();
            if (cap != null) cap.enabled = true;

            // fuga al doppio della velocita' verso il waypoint piu' lontano
            if (_baseSpeed < 0f) _baseSpeed = walkSpeed;
            walkSpeed = _baseSpeed * 2f;
            _fleeUntil = Time.unscaledTime + 6f;
            if (waypoints != null && waypoints.Length > 0)
            {
                int far = 0;
                float best = -1f;
                for (int i = 0; i < waypoints.Length; i++)
                {
                    float d = (waypoints[i] - transform.position).sqrMagnitude;
                    if (d > best) { best = d; far = i; }
                }
                currentTarget = far;
            }
            walking = true;
        }

        // ── targhetta roleplay (nome, ruolo, livello amicizia) ──
        private Transform _nameTag;
        private TextMeshPro _nameTagTmp;
        private float _labelRefresh;

        private void EnsureNameTag()
        {
            if (_nameTag != null) return;
            var go = new GameObject("NameTag");
            _nameTag = go.transform;
            _nameTag.SetParent(transform, false);
            _nameTag.localPosition = new Vector3(0f, 2.4f, 0f);
            // compensa la scala ridotta del modello (0.455): testo a dimensione mondo costante
            float sc = Mathf.Max(0.01f, transform.lossyScale.x);
            _nameTag.localScale = Vector3.one * (1f / sc);
            var rt = go.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(7f, 2f);
            _nameTagTmp = go.AddComponent<TextMeshPro>();
            _nameTagTmp.fontSize = 2.6f;
            _nameTagTmp.alignment = TextAlignmentOptions.Center;
            _nameTagTmp.color = Color.white;
            go.SetActive(false);
        }

        private void UpdateNameTag()
        {
            if (_nameTag == null || _nameTagTmp == null) return;
            var cam = Camera.main;
            if (cam == null)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            bool show = (cam.transform.position - transform.position).sqrMagnitude < 400f;
            if (!show)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            if (!_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(true);
            _nameTag.rotation = cam.transform.rotation;
            if (Time.unscaledTime < _labelRefresh) return;
            _labelRefresh = Time.unscaledTime + 0.5f;
            string txt = CharacterAvatar + " " + DisplayName;
            if (!string.IsNullOrEmpty(CharacterRole))
                txt += "\n<size=60%>" + CharacterRole + "</size>";
            if (!string.IsNullOrEmpty(CharacterId))
            {
                string lvl = RelationshipManager.LevelLabel(
                    RelationshipManager.LevelIndex(CharacterId));
                txt += "\n<size=50%><color=#9fdcae>" + lvl + "</color></size>";
            }
            _nameTagTmp.text = txt;
        }

        private void Toast(string msg)
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(msg);
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, true);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (mission == null) return;
            if (Game.Instance != null)
                Game.Instance.OnMissionNPCFocusChanged(mission, false);
        }

        private void Update()
        {
            UpdateNameTag();
            if (_down) return;
            if (_fleeUntil > 0f && Time.unscaledTime >= _fleeUntil)
            {
                walkSpeed = _baseSpeed > 0f ? _baseSpeed : walkSpeed * 0.5f;
                _fleeUntil = 0f;
            }
            if (waypoints == null || waypoints.Length < 2) return;

            if (!walking)
            {
                pauseTimer -= Time.deltaTime;
                if (pauseTimer <= 0f)
                {
                    walking = true;
                    currentTarget = (currentTarget + 1) % waypoints.Length;
                }
                SetAnimSpeed(0f);
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

            SetAnimSpeed(walking ? walkSpeed : 0f);
        }

        private CharacterWalker walker;
        private Animator animator;

        private void SetAnimSpeed(float s)
        {
            if (walker != null) walker.SetSpeed(s);
            if (animator != null) animator.SetFloat("Speed", s);
        }

        // Il modello Kenney arriva gia' montato da NPCPopulator: qui si aggiunge
        // solo l'animazione e il collider solido. Se manca il prefab si ricade
        // sul vecchio omino di cubi cosi' gli NPC non spariscono mai.
        private void SetupModel(System.Random rng)
        {
            bool kenney = GetComponentInChildren<SkinnedMeshRenderer>(true) != null;
            if (!kenney)
                BuildCubeModel();

            walker = CharacterWalker.AttachIfNeeded(gameObject);
            animator = GetComponentInChildren<Animator>();

            var cc = gameObject.AddComponent<CapsuleCollider>();
            cc.height = 1.7f;
            cc.radius = 0.25f;
            cc.center = new Vector3(0f, 0.85f, 0f);
        }

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

        // Fallback low-poly (stato originale del sistema NPC).
        private void BuildCubeModel()
        {
            System.Random rng = new System.Random(NpcId != null ? NpcId.GetHashCode() : 0);
            Color skin = SkinColors[rng.Next(SkinColors.Length)];
            Color shirt = ShirtColors[rng.Next(ShirtColors.Length)];
            Color pants = new Color(0.2f, 0.2f, 0.3f);
            float bodyW = 0.4f, bodyH = 0.55f, bodyD = 0.25f;

            Part("Head", PrimitiveType.Sphere, new Vector3(0f, 1.45f, 0f),
                Vector3.one * 0.44f, skin);
            Part("Body", PrimitiveType.Cube, new Vector3(0f, 0.95f, 0f),
                new Vector3(bodyW, bodyH, bodyD), shirt);
            Part("LegL", PrimitiveType.Cube, new Vector3(-0.1f, 0.4f, 0f),
                new Vector3(0.12f, 0.5f, 0.14f), pants);
            Part("LegR", PrimitiveType.Cube, new Vector3(0.1f, 0.4f, 0f),
                new Vector3(0.12f, 0.5f, 0.14f), pants);
            Part("ArmL", PrimitiveType.Cube,
                new Vector3(-bodyW * 0.5f - 0.1f, 1.0f, 0f),
                new Vector3(0.1f, 0.4f, 0.1f), skin);
            Part("ArmR", PrimitiveType.Cube,
                new Vector3(bodyW * 0.5f + 0.1f, 1.0f, 0f),
                new Vector3(0.1f, 0.4f, 0.1f), skin);
        }

        private void Part(string name, PrimitiveType type, Vector3 pos,
            Vector3 scale, Color color)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            go.transform.SetParent(transform, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = MakeMat(color);
            var col = go.GetComponent<Collider>();
            if (col != null) col.enabled = false;
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
