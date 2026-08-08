using UnityEngine;
using UnityEngine.AI;
using Huntix.Bridge;

namespace Huntix.Indoor
{
    /// <summary>
    /// NPC with patrol behavior and player interaction.
    /// Patrols between waypoints, stops when player is near, and can be talked to.
    /// Uses NavMeshAgent for pathfinding.
    /// </summary>
    [RequireComponent(typeof(NavMeshAgent))]
    public class NPC : MonoBehaviour
    {
        [Header("NPC Identity")]
        public string npcId = "";
        public string npcName = "NPC";
        public string role = "employee";  // employee, customer, doctor, trainer, librarian
        public string emoji = "🧑";

        [Header("Dialogue")]
        public string[] dialogueLines = new string[] {
            "Benvenuto!",
            "Posso aiutarti?"
        };
        public string questText = "";
        public string questRewardNeed = "";
        public int questRewardGain = 0;

        [Header("Patrol")]
        public Transform[] patrolPoints;
        public float patrolWaitTime = 2f;
        public float patrolSpeed = 1.5f;

        [Header("Interaction")]
        public float interactionRange = 2.5f;
        public float dialogueRange = 4f;

        private NavMeshAgent _agent;
        private int _currentPatrolIndex;
        private float _waitTimer;
        private bool _isWaiting;
        private bool _isTalking;
        private Transform _playerTransform;
        private Animator _animator;

        private static readonly int AnimWalking = Animator.StringToHash("Walking");
        private static readonly int AnimTalking = Animator.StringToHash("Talking");

        private void Start()
        {
            _agent = GetComponent<NavMeshAgent>();
            _agent.speed = patrolSpeed;
            _agent.stoppingDistance = 0.3f;

            _animator = GetComponentInChildren<Animator>();

            var player = GameObject.FindWithTag("Player");
            if (player != null) _playerTransform = player.transform;

            // Auto-generate patrol points if none assigned
            if (patrolPoints == null || patrolPoints.Length == 0)
                GeneratePatrolPoints();

            GoToNextPatrolPoint();
        }

        private void Update()
        {
            if (_agent == null || !_agent.isOnNavMesh) return;

            float distToPlayer = _playerTransform != null
                ? Vector3.Distance(transform.position, _playerTransform.position)
                : float.MaxValue;

            // If player is in dialogue range, stop and face them
            if (distToPlayer <= dialogueRange && !_isTalking)
            {
                if (_agent.hasPath) _agent.ResetPath();
                LookAtPlayer();
                SetTalking(true);

                // Notify Android that this NPC is nearby
                UnityBridge.SendMessageToAndroid("IndoorNPCNearby",
                    $"{{\"id\":\"{npcId}\",\"name\":\"{npcName}\",\"role\":\"{role}\",\"emoji\":\"{emoji}\"}}");
            }
            else if (distToPlayer > dialogueRange * 1.5f && _isTalking)
            {
                SetTalking(false);
                GoToNextPatrolPoint();

                UnityBridge.SendMessageToAndroid("IndoorNPCFar",
                    $"{{\"id\":\"{npcId}\"}}");
            }

            // Patrol behavior
            if (!_isTalking)
            {
                if (_isWaiting)
                {
                    _waitTimer -= Time.deltaTime;
                    if (_waitTimer <= 0)
                    {
                        _isWaiting = false;
                        GoToNextPatrolPoint();
                    }
                }
                else if (!_agent.hasPath || _agent.remainingDistance < 0.5f)
                {
                    _isWaiting = true;
                    _waitTimer = patrolWaitTime;
                    SetWalking(false);
                }
            }
        }

        private void GoToNextPatrolPoint()
        {
            if (patrolPoints == null || patrolPoints.Length == 0) return;

            _currentPatrolIndex = (_currentPatrolIndex + 1) % patrolPoints.Length;
            var target = patrolPoints[_currentPatrolIndex];
            if (target != null)
            {
                _agent.SetDestination(target.position);
                SetWalking(true);
            }
        }

        private void GeneratePatrolPoints()
        {
            // Create 2-4 patrol points around the NPC's initial position
            var center = transform.position;
            int count = Random.Range(2, 5);
            patrolPoints = new Transform[count];
            for (int i = 0; i < count; i++)
            {
                var pt = new GameObject($"{npcId}_patrol_{i}");
                float angle = i * (360f / count) * Mathf.Deg2Rad;
                float radius = Random.Range(2f, 4f);
                pt.transform.position = center + new Vector3(Mathf.Cos(angle) * radius, 0, Mathf.Sin(angle) * radius);
                patrolPoints[i] = pt.transform;
            }
        }

        private void LookAtPlayer()
        {
            if (_playerTransform == null) return;
            var dir = _playerTransform.position - transform.position;
            dir.y = 0;
            if (dir.sqrMagnitude > 0.01f)
                transform.rotation = Quaternion.Slerp(transform.rotation,
                    Quaternion.LookRotation(dir), Time.deltaTime * 5f);
        }

        private void SetWalking(bool walking)
        {
            if (_animator != null)
                _animator.SetBool(AnimWalking, walking);
        }

        private void SetTalking(bool talking)
        {
            _isTalking = talking;
            if (_animator != null)
                _animator.SetBool(AnimTalking, talking);
        }

        /// <summary>
        /// Called from Android when the player taps "Talk" on this NPC.
        /// </summary>
        public void Talk()
        {
            if (dialogueLines.Length == 0) return;

            string line = dialogueLines[Random.Range(0, dialogueLines.Length)];
            string json = $"{{\"id\":\"{npcId}\",\"name\":\"{npcName}\",\"dialogue\":\"{line}\"," +
                          $"\"hasQuest\":{(questText.Length > 0 ? "true" : "false")}," +
                          $"\"questText\":\"{questText}\"," +
                          $"\"questNeed\":\"{questRewardNeed}\",\"questGain\":{questRewardGain}}}";

            UnityBridge.SendMessageToAndroid("IndoorNPCDialogue", json);
            Debug.Log($"[NPC] {npcName} says: {line}");
        }

        /// <summary>
        /// Called from Android when the player accepts the quest from this NPC.
        /// </summary>
        public void AcceptQuest()
        {
            if (string.IsNullOrEmpty(questText)) return;

            UnityBridge.SendMessageToAndroid("IndoorNPCQuestAccepted",
                $"{{\"id\":\"{npcId}\",\"need\":\"{questRewardNeed}\",\"gain\":{questRewardGain}}}");
            Debug.Log($"[NPC] Quest accepted from {npcId}: +{questRewardGain} {questRewardNeed}");
        }

        private void OnDrawGizmosSelected()
        {
            Gizmos.color = Color.yellow;
            Gizmos.DrawWireSphere(transform.position, interactionRange);
            Gizmos.color = Color.cyan;
            Gizmos.DrawWireSphere(transform.position, dialogueRange);

            if (patrolPoints != null)
            {
                Gizmos.color = Color.green;
                foreach (var pt in patrolPoints)
                {
                    if (pt != null) Gizmos.DrawSphere(pt.position, 0.2f);
                }
            }
        }
    }
}
