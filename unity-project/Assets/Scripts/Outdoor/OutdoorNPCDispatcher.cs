using UnityEngine;
using System.Collections.Generic;
using System.Linq;

namespace Huntix.Outdoor
{
    /// <summary>
    /// OutdoorNPCDispatcher — GameObject "OutdoorNPC" che riceve comandi
    /// da Android tramite UnitySendMessage("OutdoorNPC", "Talk", id).
    /// Trova l'NPC con npcId corrispondente e ne attiva l'interazione.
    /// </summary>
    public class OutdoorNPCDispatcher : MonoBehaviour
    {
        private static OutdoorNPCDispatcher _instance;
        private readonly Dictionary<string, OutdoorNPC> _npcs = new Dictionary<string, OutdoorNPC>();

        private void Awake()
        {
            if (_instance != null && _instance != this)
            {
                Destroy(gameObject);
                return;
            }
            _instance = this;
            DontDestroyOnLoad(gameObject);
            gameObject.name = "OutdoorNPC";
        }

        /// <summary>
        /// Registra un NPC appena spawnato. Chiamato da ExploreManager.
        /// </summary>
        public void RegisterNPC(OutdoorNPC npc)
        {
            if (npc == null || string.IsNullOrEmpty(npc.npcId)) return;
            _npcs[npc.npcId] = npc;
        }

        /// <summary>
        /// De-registra un NPC (es. quando viene distrutto).
        /// </summary>
        public void UnregisterNPC(OutdoorNPC npc)
        {
            if (npc == null) return;
            _npcs.Remove(npc.npcId);
        }

        /// <summary>
        /// Chiamato da Android: UnitySendMessage("OutdoorNPC", "Talk", npcId).
        /// Attiva la conversazione con l'NPC specificato.
        /// </summary>
        public void Talk(string npcId)
        {
            if (string.IsNullOrEmpty(npcId))
            {
                Debug.LogWarning("[OutdoorNPCDispatcher] Talk chiamato con npcId vuoto");
                return;
            }

            if (_npcs.TryGetValue(npcId, out var npc))
            {
                npc.Talk();
            }
            else
            {
                Debug.LogWarning($"[OutdoorNPCDispatcher] NPC non trovato con id={npcId}. " +
                    $"NPC registrati: {string.Join(", ", _npcs.Keys)}");
            }
        }

        /// <summary>
        /// Chiamato da Android: UnitySendMessage("OutdoorNPC", "ShowInfo", npcId).
        /// Mostra le informazioni dettagliate del NPC/POI.
        /// </summary>
        public void ShowInfo(string npcId)
        {
            if (_npcs.TryGetValue(npcId, out var npc))
            {
                npc.ShowInfo();
            }
            else
            {
                Debug.LogWarning($"[OutdoorNPCDispatcher] NPC non trovato per ShowInfo: {npcId}");
            }
        }

        /// <summary>
        /// Trova l'NPC più vicino al giocatore entro un certo raggio.
        /// Utile per interazioni rapide senza conoscere l'ID.
        /// </summary>
        public OutdoorNPC FindNearestNPC(Vector3 playerPos, float maxDist = 5f)
        {
            OutdoorNPC nearest = null;
            float minDist = float.MaxValue;

            foreach (var npc in _npcs.Values)
            {
                if (npc == null) continue;
                float d = Vector3.Distance(playerPos, npc.transform.position);
                if (d < minDist && d <= maxDist)
                {
                    minDist = d;
                    nearest = npc;
                }
            }
            return nearest;
        }
    }
}
