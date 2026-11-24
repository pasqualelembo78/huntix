// Adattato da dogacankutlu/unity-ai-npc — Copyright (c) 2026 Harun Doğacan
// Kutlu, MIT (licenza integrale in THIRD_PARTY_NOTICES.md). Sorgente:
// Assets/AIConversationSystem/Runtime/NPC/NPCPersona.cs. Solo separazione
// dati/contenuto: NESSUNA rete, NESSUNA API key, NESSUN LLM in Huntix.
using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.NPC
{
    /// <summary>
    /// Persona NPC offline: nome, ruolo, saluto e indizio uovo in testo.
    /// Opt-in (default OFF): senza definizioni assegnate il dialogo resta
    /// esattamente quello attuale. Nessun premio automatico.
    /// </summary>
    [CreateAssetMenu(menuName = "Huntix/NPC/Persona offline")]
    public class CityNpcPersonaDefinition : ScriptableObject
    {
        public string id;
        public string npcName;
        public string role;
        public string greeting;
        public string eggHintLine;
        public string eggPlaceHint;
        public float cooldownSeconds = 30f;

        private static readonly Dictionary<string, CityNpcPersonaDefinition> ByName =
            new Dictionary<string, CityNpcPersonaDefinition>(StringComparer.OrdinalIgnoreCase);
        private static readonly HashSet<string> Revealed =
            new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled("npc-persona");
            City.Diagnostics.OpenSourceFeatureBridge.Register("npc-persona", on => Enabled = on);
        }

        private void OnEnable()
        {
            if (!string.IsNullOrEmpty(npcName)) ByName[npcName] = this;
        }

        private void OnDisable()
        {
            if (!string.IsNullOrEmpty(npcName) && ByName.ContainsKey(npcName))
                ByName.Remove(npcName);
        }

        /// <summary>Aggiunge una sola riga di indizio al dialogo esistente.
        /// Non tocca callback, indice ne' pulsanti.</summary>
        public static string[] Decorate(string npcName, string[] lines)
        {
            if (!Enabled || string.IsNullOrEmpty(npcName) || lines == null || lines.Length == 0)
                return lines;
            CityNpcPersonaDefinition persona;
            if (!ByName.TryGetValue(npcName, out persona)) return lines;
            if (string.IsNullOrEmpty(persona.eggHintLine) || Revealed.Contains(persona.npcName))
                return lines;
            Revealed.Add(persona.npcName);
            var decorated = new string[lines.Length + 1];
            Array.Copy(lines, decorated, lines.Length);
            decorated[lines.Length] = persona.eggHintLine;
            City.Diagnostics.OpenSourceFeatureBridge.Report(
                "npc-persona", "Uovo degli indizi NPC", "città→indizio→" + persona.eggPlaceHint,
                "npc=" + persona.npcName, true);
            return decorated;
        }
    }
}
