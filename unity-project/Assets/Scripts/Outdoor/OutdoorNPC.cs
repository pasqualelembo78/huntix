using UnityEngine;
using Huntix.Bridge;

namespace Huntix.Outdoor
{
    /// <summary>
    /// OutdoorNPC — guida turistica statico vicino ai POI in modalità Esplora.
    /// Non richiede NavMeshAgent: resta fermo e rileva prossimità tramite collider trigger.
    /// Mostra prompt UI e dialoghi turistici quando il giocatore si avvicina.
    /// Invia eventi a Android (OutdoorNPCNearby/Far/Dialogue) per tracking.
    /// </summary>
    [RequireComponent(typeof(Collider))]
    public class OutdoorNPC : MonoBehaviour
    {
        [Header("NPC Identity")]
        public string npcId = "";
        public string npcName = "Guida";
        public string role = "guide";
        public string emoji = "🧑‍💼";

        [Header("POI Context")]
        public string poiId = "";
        public string poiName = "";
        public string poiType = "";
        public string buildingType = "";
        public string poiCategory = "";

        [Header("Dialogue")]
        public string[] dialogueLines;

        [Header("Interaction")]
        public float dialogueRange = 6f;

        private Transform _playerTransform;
        private bool _hasSentNearby = false;
        private Collider _col;

        private void Awake()
        {
            _col = GetComponent<Collider>();
            _col.isTrigger = true;

            // Auto-generate dialogue if not assigned
            if (dialogueLines == null || dialogueLines.Length == 0)
            {
                dialogueLines = TourismDialogue.GetDialogues(poiType, buildingType, poiCategory, poiName);
            }
        }

        private void Start()
        {
            var player = GameObject.FindWithTag("Player");
            if (player != null) _playerTransform = player.transform;
        }

        private void Update()
        {
            if (_playerTransform == null) return;

            float dist = Vector3.Distance(transform.position, _playerTransform.position);

            // Proximity detection
            if (dist <= dialogueRange && !_hasSentNearby)
            {
                _hasSentNearby = true;
                NotifyNPCNearby();
                if (OutdoorDialogueUI.Instance != null)
                    OutdoorDialogueUI.ShowPrompt(npcName, emoji, $"[{OutdoorDialogueUI.Instance.interactKey}] Parla con la guida");
            }
            else if (dist > dialogueRange * 1.5f && _hasSentNearby)
            {
                _hasSentNearby = false;
                NotifyNPCFar();
                OutdoorDialogueUI.HidePrompt();
            }

            // Auto-interact key when nearby
            if (_hasSentNearby && OutdoorDialogueUI.Instance != null &&
                Input.GetKeyDown(OutdoorDialogueUI.Instance.interactKey))
            {
                Talk();
            }
        }

        /// <summary>
        /// Called when the player presses interact key near this NPC.
        /// Shows dialogue in Unity UI and sends event to Android.
        /// </summary>
        public void Talk()
        {
            if (dialogueLines == null || dialogueLines.Length == 0) return;

            string line = dialogueLines[Random.Range(0, dialogueLines.Length)];

            // Show dialogue in Unity UI
            OutdoorDialogueUI.ShowDialogue(this, emoji, line);

            // Also notify Android (for tracking / quest triggers)
            string json = BuildDialogueJson(line);
            UnityBridge.SendMessageToAndroid("OutdoorNPCDialogue", json);

            Debug.Log($"[OutdoorNPC] {npcName} dice: {line}");
        }

        /// <summary>
        /// Called from Android: UnitySendMessage("OutdoorNPC", "ShowInfo", id)
        /// Shows info about the POI this guide is associated with.
        /// </summary>
        public void ShowInfo()
        {
            string json = $"{{\"id\":\"{npcId}\",\"name\":\"{npcName}\",\"role\":\"{role}\"," +
                          $"\"emoji\":\"{emoji}\",\"poiId\":\"{poiId}\",\"poiName\":\"{poiName}\"," +
                          $"\"category\":\"{poiCategory}\",\"type\":\"{poiType}\"," +
                          $"\"buildingType\":\"{buildingType}\"}}";
            UnityBridge.SendMessageToAndroid("OutdoorNPCInfo", json);
        }

        private void NotifyNPCNearby()
        {
            string json = $"{{\"id\":\"{npcId}\",\"name\":\"{npcName}\",\"role\":\"{role}\"," +
                          $"\"emoji\":\"{emoji}\",\"poiId\":\"{poiId}\",\"poiName\":\"{poiName}\"," +
                          $"\"category\":\"{poiCategory}\"}}";
            UnityBridge.SendMessageToAndroid("OutdoorNPCNearby", json);
        }

        private void NotifyNPCFar()
        {
            string json = $"{{\"id\":\"{npcId}\"}}";
            UnityBridge.SendMessageToAndroid("OutdoorNPCFar", json);
        }

        private string BuildDialogueJson(string dialogue)
        {
            return $"{{\"id\":\"{npcId}\",\"name\":\"{npcName}\",\"role\":\"{role}\"," +
                   $"\"emoji\":\"{emoji}\",\"dialogue\":\"{dialogue}\"," +
                   $"\"poiId\":\"{poiId}\",\"poiName\":\"{poiName}\"," +
                   $"\"category\":\"{poiCategory}\",\"type\":\"{poiType}\"}}";
        }

        private void OnDisable()
        {
            if (_hasSentNearby)
            {
                _hasSentNearby = false;
                OutdoorDialogueUI.HidePrompt();
            }
        }
    }
}
