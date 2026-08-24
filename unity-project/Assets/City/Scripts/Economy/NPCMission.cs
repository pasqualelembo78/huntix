using System;
using System.Collections.Generic;
using UnityEngine;
using TMPro;
using City.World;
using City.NPC;

namespace City.Economy
{
    public class NPCMission : MonoBehaviour
    {
        public enum MissionType { CollectEggs, WalkDistance }
        public enum MissionState { Available, Active, Completed }

        public string missionId;
        public string description;
        public MissionType type;
        public MissionState state;
        public int targetCount;
        public int currentCount;
        public int reward;

        private NPCController npc;
        private GameObject markerObj;
        private bool focused;

        private static readonly string[] EggQuests = new string[]
        {
            "Trova 5 uova nel quartiere",
            "Raccogli 8 uova colorate",
            "Cerca 3 uova rare per strada",
            "Recupera 10 uova dimenticate",
        };

        private static readonly string[] WalkQuests = new string[]
        {
            "Cammina 100m per esplorare",
            "Percorri 200m nel vicinato",
            "Fai 150m lungo le strade",
        };

        public struct MissionData
        {
            public string missionId;
            public string description;
            public MissionType type;
            public int targetCount;
            public int reward;
        }

        public static MissionData GenerateMissionData(int seed)
        {
            var rng = new System.Random(seed);
            var data = new MissionData();

            bool isEgg = rng.NextDouble() < 0.6;
            if (isEgg)
            {
                data.type = MissionType.CollectEggs;
                data.targetCount = 3 + rng.Next(8);
                data.description = EggQuests[rng.Next(EggQuests.Length)];
                data.reward = data.targetCount * 3 + rng.Next(10);
            }
            else
            {
                data.type = MissionType.WalkDistance;
                data.targetCount = 80 + rng.Next(220);
                data.description = WalkQuests[rng.Next(WalkQuests.Length)];
                data.reward = data.targetCount / 10 + rng.Next(15);
            }

            data.missionId = "mission_" + seed;
            return data;
        }

        public static NPCMission GenerateMission(int seed)
        {
            var data = GenerateMissionData(seed);
            var mission = new GameObject("MissionNPC");
            var nm = mission.AddComponent<NPCMission>();
            nm.ApplyData(data);
            return nm;
        }

        public void ApplyData(MissionData data)
        {
            missionId = data.missionId;
            description = data.description;
            type = data.type;
            targetCount = data.targetCount;
            reward = data.reward;
            state = MissionState.Available;
            currentCount = 0;
        }

        public void AttachToNPC(NPCController npcCtrl)
        {
            npc = npcCtrl;
            missionId = npcCtrl.gameObject.name + "_mission";

            // Create (!) marker above NPC
            CreateMarker();

            // Set initial state
            state = MissionState.Available;
        }

        private void CreateMarker()
        {
            if (npc == null) return;

            markerObj = new GameObject("MissionMarker");
            markerObj.transform.SetParent(npc.transform, false);
            markerObj.transform.localPosition = new Vector3(0f, 2.2f, 0f);

            // (!) text
            var textGo = new GameObject("Exclamation");
            textGo.transform.SetParent(markerObj.transform, false);
            textGo.transform.localPosition = Vector3.zero;
            textGo.transform.localScale = Vector3.one * 0.4f;

            var tmp = textGo.AddComponent<TextMeshPro>();
            tmp.text = "!";
            tmp.fontSize = 8;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = new Color(1f, 0.85f, 0f);
            tmp.font = TMP_Settings.defaultFontAsset;

            // Enable emission for glow
            if (tmp.fontMaterial != null)
            {
                tmp.fontMaterial.EnableKeyword("_EMISSION");
                tmp.fontMaterial.SetColor("_EmissionColor", new Color(1f, 0.85f, 0f, 1f));
            }

            // Background circle
            var bg = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            bg.name = "MarkerBG";
            bg.transform.SetParent(markerObj.transform, false);
            bg.transform.localPosition = new Vector3(0f, 0f, 0.01f);
            bg.transform.localScale = new Vector3(0.5f, 0.5f, 0.05f);
            var bgR = bg.GetComponent<Renderer>();
            Shader sh = Shader.Find("Universal Render Pipeline/Unlit");
            if (sh == null) sh = Shader.Find("Sprites/Default");
            if (sh == null) sh = Shader.Find("Legacy Shaders/Diffuse");
            if (sh != null)
            {
                var bgMat = new Material(sh);
                if (bgMat.HasProperty("_BaseColor"))
                    bgMat.SetColor("_BaseColor", new Color(0.15f, 0.15f, 0.15f, 0.85f));
                if (bgMat.HasProperty("_Color"))
                    bgMat.SetColor("_Color", new Color(0.15f, 0.15f, 0.15f, 0.85f));
                bgR.sharedMaterial = bgMat;
            }
            bgR.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            Destroy(bg.GetComponent<Collider>());
        }

        public void OnPlayerInteract()
        {
            if (state == MissionState.Available)
            {
                AcceptMission();
            }
            else if (state == MissionState.Active)
            {
                if (IsComplete())
                {
                    CompleteMission();
                }
                else
                {
                    // Show progress
                    ShowProgress();
                }
            }
        }

        private void AcceptMission()
        {
            state = MissionState.Active;
            currentCount = 0;

            if (markerObj != null)
            {
                var tmp = markerObj.GetComponentInChildren<TextMeshPro>();
                if (tmp != null) tmp.text = "...";
            }

            MissionManager.Instance?.ActivateMission(this);
            ShowToast("Missione accettata: " + description);
        }

        private void CompleteMission()
        {
            state = MissionState.Completed;

            // roleplay: punti amicizia con il personaggio e prezzo amico (+10%)
            int paid = reward;
            string cid = npc != null ? npc.CharacterId : null;
            if (!string.IsNullOrEmpty(cid))
            {
                City.NPC.RelationshipManager.AddMissionComplete(cid);
                if (City.NPC.RelationshipManager.LevelIndex(cid) >=
                    City.NPC.RelationshipManager.FriendLevelForPerk)
                    paid = Mathf.RoundToInt(reward * 1.1f);
            }
            Wallet.Earn(paid);

            if (markerObj != null)
            {
                var tmp = markerObj.GetComponentInChildren<TextMeshPro>();
                if (tmp != null)
                {
                    tmp.text = "OK";
                    tmp.color = Color.green;
                }
            }

            MissionManager.Instance?.CompleteMission(this);
            ShowToast("Missione completata! +€" + paid +
                (paid != reward ? " (prezzo amico)" : ""));

            // Respawn marker after delay for new mission
            StartCoroutine(RespawnMarkerDelayed());
        }

        private System.Collections.IEnumerator RespawnMarkerDelayed()
        {
            yield return new WaitForSeconds(30f);
            if (npc == null || !npc.gameObject.activeInHierarchy) yield break;

            // Nuova missione SULLO STESSO componente: creare un GameObject
            // mission separato e distruggere questo rompeva npc.mission
            // (riferimento a componente distrutto): il pedone non offriva piu'
            // missioni per tutta la sessione.
            if (markerObj != null)
            {
                Destroy(markerObj);
                markerObj = null;
            }
            ApplyData(GenerateMissionData(UnityEngine.Random.Range(0, int.MaxValue)));
            CreateMarker();
        }

        private void ShowProgress()
        {
            string msg = "";
            switch (type)
            {
                case MissionType.CollectEggs:
                    msg = description + " (" + currentCount + "/" + targetCount + ")";
                    break;
                case MissionType.WalkDistance:
                    msg = description + " (" + currentCount + "m/" + targetCount + "m)";
                    break;
            }
            ShowToast(msg);
        }

        public bool IsComplete()
        {
            return currentCount >= targetCount;
        }

        public void ReportProgress(int amount)
        {
            if (state != MissionState.Active) return;
            currentCount = Mathf.Min(currentCount + amount, targetCount);
        }

        private void ShowToast(string msg)
        {
            var ui = City.UI.UIManager.Instance;
            if (ui != null) ui.ShowToast(msg);
        }

        // Called by EggController when egg collected
        public void OnEggCollected()
        {
            if (type == MissionType.CollectEggs && state == MissionState.Active)
                ReportProgress(1);
        }

        // Called when player walks
        public void OnPlayerWalked(float distanceMeters)
        {
            if (type == MissionType.WalkDistance && state == MissionState.Active)
                ReportProgress(Mathf.RoundToInt(distanceMeters));
        }
    }
}
