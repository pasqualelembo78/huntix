using UnityEngine;
using Huntix.Bridge;
using System.Collections.Generic;

namespace Huntix.Indoor
{
    /// <summary>
    /// Manages multiplayer sync for indoor store scenes.
    /// Hosts the store layout + Cloud Anchor; guests resolve it.
    /// Syncs collected items and NPC interactions via Firebase (Android bridge).
    /// </summary>
    public class IndoorMultiplayerManager : MonoBehaviour
    {
        public static IndoorMultiplayerManager Instance { get; private set; }

        [Header("Multiplayer")]
        public bool isHost;
        public string roomCode = "";
        public string cloudAnchorId = "";

        [Header("Sync State")]
        public List<string> collectedItems = new List<string>();
        public List<string> interactedNPCs = new List<string>();

        private bool _syncing;

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        /// <summary>
        /// Initialize multiplayer session. Called from Android after room creation.
        /// </summary>
        public void InitSession(string json)
        {
            // json: {"isHost":bool,"roomCode":"xxx","cloudAnchorId":"xxx"}
            var data = JsonUtility.FromJson<SessionData>(json);
            isHost = data.isHost;
            roomCode = data.roomCode;
            cloudAnchorId = data.cloudAnchorId;

            Debug.Log($"[IndoorMultiplayer] Session init: host={isHost}, room={roomCode}");

            if (isHost)
                HostStore();
            else
                GuestJoin();
        }

        private void HostStore()
        {
            // Host builds the store and creates a Cloud Anchor
            Debug.Log("[IndoorMultiplayer] Hosting store — waiting for AR plane...");

            // Notify Android to host the Cloud Anchor
            UnityBridge.SendMessageToAndroid("IndoorMultiplayerHost",
                $"{{\"roomCode\":\"{roomCode}\"}}");
        }

        private void GuestJoin()
        {
            // Guest resolves the Cloud Anchor to get the store position
            Debug.Log($"[IndoorMultiplayer] Joining store — resolving anchor: {cloudAnchorId}");

            UnityBridge.SendMessageToAndroid("IndoorMultiplayerJoin",
                $"{{\"roomCode\":\"{roomCode}\",\"cloudAnchorId\":\"{cloudAnchorId}\"}}");
        }

        /// <summary>
        /// Called from Android when the Cloud Anchor is resolved.
        /// Repositions the store to the host's position.
        /// </summary>
        public void OnAnchorResolved(string json)
        {
            // json: {"position":"x,y,z","rotation":"x,y,z,w"}
            try
            {
                var data = JsonUtility.FromJson<AnchorData>(json);
                var pos = ParseVector3(data.position);
                var rot = ParseQuaternion(data.rotation);

                var store = GameObject.Find("StoreInterior");
                if (store != null)
                {
                    store.transform.position = pos;
                    store.transform.rotation = rot;
                    Debug.Log($"[IndoorMultiplayer] Store repositioned at {pos}");
                }
            }
            catch (System.Exception e)
            {
                Debug.LogError($"[IndoorMultiplayer] Anchor resolve error: {e.Message}");
            }
        }

        /// <summary>
        /// Called from Android when a remote player collects an item.
        /// </summary>
        public void OnRemoteItemCollected(string json)
        {
            // json: {"itemId":"bread","playerName":"Player2"}
            var data = JsonUtility.FromJson<ItemEvent>(json);
            collectedItems.Add(data.itemId);
            Debug.Log($"[IndoorMultiplayer] Remote collected: {data.itemId} by {data.playerName}");

            // Disable the interactable in scene
            var interactables = FindObjectsOfType<InteractionComponent>();
            foreach (var ic in interactables)
            {
                if (ic.itemId == data.itemId)
                {
                    ic.gameObject.SetActive(false);
                    break;
                }
            }
        }

        /// <summary>
        /// Called from Android when a remote player interacts with an NPC.
        /// </summary>
        public void OnRemoteNPCInteraction(string json)
        {
            var data = JsonUtility.FromJson<NPCEvent>(json);
            interactedNPCs.Add(data.npcId);
            Debug.Log($"[IndoorMultiplayer] Remote NPC interaction: {data.npcId} by {data.playerName}");
        }

        /// <summary>
        /// Called from InteractionManager when local player collects an item.
        /// Broadcasts to other players via Firebase.
        /// </summary>
        public void BroadcastItemCollection(string itemId)
        {
            if (string.IsNullOrEmpty(roomCode)) return;

            collectedItems.Add(itemId);
            UnityBridge.SendMessageToAndroid("IndoorMultiplayerBroadcast",
                $"{{\"roomCode\":\"{roomCode}\",\"event\":\"item_collected\",\"itemId\":\"{itemId}\"}}");
        }

        /// <summary>
        /// Called when local player talks to an NPC. Broadcasts to other players.
        /// </summary>
        public void BroadcastNPCInteraction(string npcId)
        {
            if (string.IsNullOrEmpty(roomCode)) return;

            interactedNPCs.Add(npcId);
            UnityBridge.SendMessageToAndroid("IndoorMultiplayerBroadcast",
                $"{{\"roomCode\":\"{roomCode}\",\"event\":\"npc_interaction\",\"npcId\":\"{npcId}\"}}");
        }

        // ── Helpers ──

        private Vector3 ParseVector3(string s)
        {
            var parts = s.Split(',');
            return new Vector3(float.Parse(parts[0]), float.Parse(parts[1]), float.Parse(parts[2]));
        }

        private Quaternion ParseQuaternion(string s)
        {
            var parts = s.Split(',');
            return new Quaternion(float.Parse(parts[0]), float.Parse(parts[1]),
                                  float.Parse(parts[2]), float.Parse(parts[3]));
        }

        [System.Serializable]
        private class SessionData
        {
            public bool isHost;
            public string roomCode;
            public string cloudAnchorId;
        }

        [System.Serializable]
        private class AnchorData
        {
            public string position;
            public string rotation;
        }

        [System.Serializable]
        private class ItemEvent
        {
            public string itemId;
            public string playerName;
        }

        [System.Serializable]
        private class NPCEvent
        {
            public string npcId;
            public string playerName;
        }
    }
}
