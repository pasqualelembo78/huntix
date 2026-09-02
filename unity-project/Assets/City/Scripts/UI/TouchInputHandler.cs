using UnityEngine;
using City.Player;
using City.OSM;
using Huntix.Bridge;

namespace City.UI
{
    public class TouchInputHandler : MonoBehaviour
    {
        public static TouchInputHandler Instance;

        private const float LongPressDuration = 2f;
        private const float MoveThreshold = 40f;
        private const float TapMaxDuration = 0.35f;

        private float pressTimer;
        private Vector2 pressStartScreen;
        private bool tracking;
        private Camera mainCam;

        private void Awake()
        {
            Instance = this;
        }

        private void Start()
        {
            mainCam = Camera.main;
        }

        private void Update()
        {
            // mini-gioco di cattura uova attivo: niente move/teleport/talk
            if (City.Economy.EggCaptureMinigame.Instance != null &&
                City.Economy.EggCaptureMinigame.Instance.IsActive) { ResetTracking(); return; }
            // con la mappa espansa aperta lo schermo e' coperto da un overlay:
            // niente teleport/talk sotto la mappa (la selezione e' gestita dalla mappa)
            if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen) { ResetTracking(); return; }
            if (Input.touchCount != 1) { ResetTracking(); return; }
            if (City.Game.Instance != null && City.Game.Instance.IsInInterior) { ResetTracking(); return; }
            if (City.Game.Instance != null && City.Game.Instance.IsDriving) { ResetTracking(); return; }

            Touch t = Input.GetTouch(0);

            switch (t.phase)
            {
                case TouchPhase.Began:
                    pressStartScreen = t.position;
                    pressTimer = 0f;
                    tracking = true;
                    break;

                case TouchPhase.Moved:
                case TouchPhase.Stationary:
                    if (!tracking) break;
                    if (Vector2.Distance(t.position, pressStartScreen) > MoveThreshold)
                    {
                        tracking = false;
                        break;
                    }
                    pressTimer += Time.deltaTime;
                    if (pressTimer >= LongPressDuration)
                    {
                        tracking = false;
                        TryTeleport(t.position);
                    }
                    break;

                case TouchPhase.Ended:
                case TouchPhase.Canceled:
                    // tap breve = parla con il pedone toccato (se c'e' un NPC)
                    if (tracking && pressTimer < TapMaxDuration &&
                        Vector2.Distance(t.position, pressStartScreen) <= MoveThreshold)
                        TryInteract(t.position);
                    ResetTracking();
                    break;
            }
        }

        private void ResetTracking()
        {
            tracking = false;
            pressTimer = 0f;
        }

        private void TryTeleport(Vector2 screenPos)
        {
            if (mainCam == null) mainCam = Camera.main;
            if (mainCam == null) return;

            Ray ray = mainCam.ScreenPointToRay(screenPos);
            int mask = ~(1 << 8);
            if (Physics.Raycast(ray, out RaycastHit hit, 500f, mask))
            {
                Vector3 target = hit.point + Vector3.up * 0.5f;
                var game = City.Game.Instance;
                if (game == null) return;

                UnityBridge.LogToAndroid("TouchInputHandler", $"Teleport a ({target.x:F1},{target.y:F1},{target.z:F1})");
                game.TeleportPlayer(target, Quaternion.LookRotation(ray.direction));
            }
            else
            {
                UnityBridge.LogToAndroid("TouchInputHandler", "Teleport fallito: nessun terreno colpito");
            }
        }

        // Tap-to-talk: un tocco su un pedone apre la chat IA (RealLifeChatActivity
        // lato Android, via Bridge.onUnityMessage("NpcChatRequest")).
        private void TryInteract(Vector2 screenPos)
        {
            // seduto su una panchina? qualunque tap ti fa alzare
            if (City.Environment.SitController.IsSitting)
            {
                City.Environment.SitController.StandUp();
                return;
            }
            if (mainCam == null) mainCam = Camera.main;
            if (mainCam == null) return;

            Ray ray = mainCam.ScreenPointToRay(screenPos);
            if (!Physics.Raycast(ray, out RaycastHit hit, 40f,
                    ~0, QueryTriggerInteraction.Collide)) return;

            // uovo: se il player e' nella zona, avvia il mini-gioco di cattura
            var egg = hit.collider.GetComponentInParent<City.Economy.EggController>();
            if (egg != null)
            {
                egg.StartCapture();
                return;
            }

            // oggetti interattivi dell'ambiente prima dei pedoni
            var prop = City.Environment.InteractableProp.FromHit(
                hit.collider.gameObject);
            if (prop != null)
            {
                UnityBridge.LogToAndroid("TouchInput",
                    "Interagisce con " + prop.title);
                prop.Interact();
                return;
            }

            // tap su un veicolo: entra se e' tuo, altrimenti apre il negozio
            // (il trigger e' figlio della carrozzeria: cerca in entrambi i versi)
            var vi = hit.collider.GetComponentInParent<City.Vehicle.VehicleInteract>();
            if (vi == null)
                vi = hit.collider.GetComponentInChildren<City.Vehicle.VehicleInteract>();
            if (vi != null)
            {
                UnityBridge.LogToAndroid("TouchInput",
                    "Tap sul veicolo: " + (vi.vehicleCode ?? ""));
                if (City.Game.Instance != null)
                    City.Game.Instance.OnVehicleTapped(vi);
                return;
            }

            var npc = hit.collider.GetComponentInParent<City.NPC.NPCController>();
            if (npc == null || string.IsNullOrEmpty(npc.NpcId)) return;

            // roleplay: se il pedone e' mappato su un personaggio RealLife
            // passiamo il suo id cosi' la chat usa la persona giusta
            string chatId = !string.IsNullOrEmpty(npc.CharacterId)
                ? npc.CharacterId : npc.NpcId;
            string json = "{\"npcId\":\"" + npc.NpcId +
                          "\",\"characterId\":\"" + chatId +
                          "\",\"name\":\"" + npc.DisplayName + "\"}";
            // roleplay: la chiacchierata consolida l'amicizia
            City.NPC.RelationshipManager.AddChat(chatId);
            UnityBridge.LogToAndroid("TouchInputHandler",
                "Chat con " + npc.DisplayName + " (" + chatId + ")");
            UnityBridge.SendMessageToAndroid("NpcChatRequest", json);
        }
    }
}
