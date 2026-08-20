using UnityEngine;

namespace Huntix.Indoor
{
    /// <summary>
    /// Riceve messaggi da Android (UnitySendMessage "NPC", "Talk"/"AcceptQuest")
    /// e li instrada all'NPC corretto cercando per npcId.
    /// </summary>
    public class NPCDispatcher : MonoBehaviour
    {
        public void Talk(string npcId)
        {
            var npc = FindNPCById(npcId);
            if (npc != null) npc.Talk();
            else Debug.LogWarning($"[NPCDispatcher] NPC not found: {npcId}");
        }

        public void AcceptQuest(string npcId)
        {
            var npc = FindNPCById(npcId);
            if (npc != null) npc.AcceptQuest();
            else Debug.LogWarning($"[NPCDispatcher] NPC not found: {npcId}");
        }

        private static NPC FindNPCById(string npcId)
        {
            foreach (var npc in Object.FindObjectsOfType<NPC>())
            {
                if (npc.npcId == npcId)
                    return npc;
            }
            return null;
        }
    }
}
