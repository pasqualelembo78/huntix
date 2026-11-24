// Adattato da bceciliano/ai-npc-quest-framework — Copyright (c) 2025 Elitania
// Ceciliano, MIT (licenza integrale in THIRD_PARTY_NOTICES.md). Sorgente:
// QuestSystem/QuestsObjects/QuestData.cs. Solo separazione dati/obiettivi;
// SQLite, OpenAI e UI coupling del repository NON sono stati importati.
using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.Economy
{
    /// <summary>
    /// Definizione missione opzionale, data-driven. Se non e' assegnata,
    /// GenerateMissionData resta identico. Nessun premio nuovo: il reward e'
    /// solo testuale, l'uovo resta di competenza del flusso canonico.
    /// </summary>
    [CreateAssetMenu(menuName = "Huntix/Missioni/Definizione")]
    public class NpcMissionDefinition : ScriptableObject
    {
        public string id;
        public string matchPrefix;
        public string description;
        public int targetCount;
        public string rewardHint;
        public string eggHintPlace;

        private static readonly List<NpcMissionDefinition> Definitions =
            new List<NpcMissionDefinition>();

        public static bool Enabled { get; private set; }

        public static void Initialize()
        {
            Enabled = City.Diagnostics.OpenSourceFeatureBridge.Enabled("quest-def");
            City.Diagnostics.OpenSourceFeatureBridge.Register("quest-def", on => Enabled = on);
        }

        private void OnEnable()
        {
            if (!Definitions.Contains(this)) Definitions.Add(this);
        }

        private void OnDisable()
        {
            Definitions.Remove(this);
        }

        public static void TryApply(ref NPCMission.MissionData data)
        {
            if (!Enabled || Definitions.Count == 0 || string.IsNullOrEmpty(data.missionId))
                return;
            for (int i = 0; i < Definitions.Count; i++)
            {
                var def = Definitions[i];
                if (def == null) continue;
                bool match = !string.IsNullOrEmpty(def.id) &&
                    def.id.Equals(data.missionId, StringComparison.OrdinalIgnoreCase);
                if (!match && !string.IsNullOrEmpty(def.matchPrefix))
                    match = data.missionId.StartsWith(def.matchPrefix,
                        StringComparison.OrdinalIgnoreCase);
                if (!match) continue;
                if (!string.IsNullOrEmpty(def.description)) data.description = def.description;
                if (def.targetCount > 0) data.targetCount = def.targetCount;
                if (!string.IsNullOrEmpty(def.eggHintPlace))
                {
                    data.description += " · indizio: " + def.eggHintPlace;
                    City.Diagnostics.OpenSourceFeatureBridge.Report(
                        "quest-def", "Uovo della missione", "città→missione→" + def.eggHintPlace,
                        "def=" + def.id, true);
                }
                return;
            }
        }
    }
}
