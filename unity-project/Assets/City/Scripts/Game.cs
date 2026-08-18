using System.Collections;
using UnityEngine;
using City.Player;
using City.UI;
using City.World;
using City.Vehicle;
using City.Economy;
using City.Interior;

namespace City
{
    public class Game : MonoBehaviour
    {
        public static Game Instance;

        public PlayerController player;
        public CameraRig rig;
        public UIManager ui;
        public ScreenFader fader;

        private InteractDoor currentDoor;
        private VehicleInteract currentVehicleFocus;
        private NPCMission currentMissionNPC;
        private bool currentVehicleShopOpen;

        // ── Modalita' veicolo ─────────────────────────────────────
        public bool IsDriving { get; private set; }
        public VehicleController CurrentVehicle { get; private set; }

        // ── Modalita' interno ──────────────────────────────────────
        public bool IsInInterior { get { return InteriorManager.Instance != null && InteriorManager.Instance.IsInside; } }
        private BuildingEntrance currentEntrance;

        private void Awake()
        {
            Instance = this;
        }

        private void Start()
        {
            if (player == null) player = FindObjectOfType<PlayerController>();
            if (rig == null) rig = FindObjectOfType<CameraRig>();
            if (ui == null) ui = GetComponentInChildren<UIManager>();
            if (fader == null && ui != null) fader = ui.fader;
        }

        private void Update()
        {
            // Modalita' guida: reindirizza input al veicolo
            if (IsDriving && CurrentVehicle != null && ui != null)
            {
                Vector2 input = ui.joystick != null ? ui.joystick.Value : Vector2.zero;
                CurrentVehicle.SetInput(input.y, input.x, false);
            }

            // Modalita' interno: reindirizza input al player interno
            if (IsInInterior && ui != null)
            {
                Vector2 input = ui.joystick != null ? ui.joystick.Value : Vector2.zero;
                InteriorManager.Instance.OnMoveInput(input);
            }
        }

        // ── Input routing ──────────────────────────────────────────

        public void OnOrbitDelta(float dx)
        {
            if (IsInInterior)
            {
                InteriorManager.Instance.OnLookDelta(dx);
            }
            else if (rig != null)
            {
                rig.Orbit(dx);
            }
        }

        // ── Ingresso edificio (interno 3D) ────────────────────────

        public void OnEntranceFocusChanged(BuildingEntrance entrance)
        {
            if (entrance != null && entrance.IsFocused)
            {
                currentEntrance = entrance;
                if (currentVehicleFocus == null && currentMissionNPC == null)
                {
                    entrance.StartAutoEntry();
                }
            }
            else if (currentEntrance == entrance)
            {
                currentEntrance = null;
                RefreshInteractLabel();
            }
        }

        // ── Porte / interazione edifici ───────────────────────────

        public void OnDoorFocusChanged(InteractDoor door)
        {
            if (door.IsFocused)
            {
                currentDoor = door;
                if (ui != null) ui.ShowInteract(door.label);
            }
            else if (currentDoor == door)
            {
                currentDoor = null;
                RefreshInteractLabel();
            }
        }

        public void OnInteractPressed()
        {
            // Se siamo dentro un interno, gestisci interazioni indoor
            if (IsInInterior)
            {
                HandleInteriorInteract();
                return;
            }

            if (IsDriving)
            {
                ExitVehicle();
                return;
            }
            if (currentVehicleShopOpen)
            {
                if (VehicleShopUI.Instance != null) VehicleShopUI.Instance.HideDialog();
                currentVehicleShopOpen = false;
                return;
            }
            // Priorità: veicolo > porta > missione > ingresso edificio
            if (currentVehicleFocus != null)
            {
                if (currentVehicleFocus.IsOwned())
                {
                    EnterVehicle(currentVehicleFocus.controller);
                }
                else
                {
                    if (ui != null)
                    {
                        ui.ShowVehicleShop(currentVehicleFocus);
                        currentVehicleShopOpen = true;
                    }
                }
                return;
            }
            if (currentDoor != null)
            {
                currentDoor.Interact();
                return;
            }
            if (currentMissionNPC != null)
            {
                currentMissionNPC.OnPlayerInteract();
                return;
            }
            if (currentEntrance != null)
            {
                currentEntrance.Interact();
                return;
            }
        }

        private void HandleInteriorInteract()
        {
            // Cerca trigger attivi nell'interno
            var triggers = FindObjectsOfType<MonoBehaviour>();
            foreach (var t in triggers)
            {
                if (t is StairTrigger st && st.IsFocused)
                {
                    st.Interact();
                    return;
                }
                if (t is ExitTrigger et && et.IsFocused)
                {
                    et.Interact();
                    return;
                }
                if (t is ShopCounterTrigger sct && sct.IsFocused)
                {
                    sct.Interact();
                    return;
                }
            }
        }

        public void OpenShop(Shop shop)
        {
            if (ui != null) ui.OpenShop(shop);
        }

        /// <summary>
        /// Quando un focus a priorità alta viene perso, mostra la label del focus
        /// a priorità più bassa ancora attivo (invece di nascondere tutto).
        /// </summary>
        private void RefreshInteractLabel()
        {
            if (ui == null) return;
            if (currentVehicleFocus != null)
            {
                string code = !string.IsNullOrEmpty(currentVehicleFocus.vehicleCode) ? " [" + currentVehicleFocus.vehicleCode + "]" : "";
                string label = currentVehicleFocus.IsOwned() ? "ENTRA " + currentVehicleFocus.data.vehicleName + code
                    : "COMPRA " + currentVehicleFocus.data.vehicleName + " - \u20ac" + currentVehicleFocus.data.price;
                ui.ShowInteract(label);
            }
            else if (currentDoor != null)
            {
                ui.ShowInteract(currentDoor.label);
            }
            else if (currentMissionNPC != null)
            {
                string label = "";
                if (currentMissionNPC.state == NPCMission.MissionState.Available)
                    label = "MISSIONE";
                else if (currentMissionNPC.state == NPCMission.MissionState.Active)
                    label = currentMissionNPC.description + " (" + currentMissionNPC.currentCount + "/" + currentMissionNPC.targetCount + ")";
                else
                    label = "COMPLETATA";
                ui.ShowInteract(label);
            }
            else if (currentEntrance != null)
            {
                // Niente pulsanti: entrambi gestiti da OnEntranceFocusChanged
            }
            else
            {
                ui.HideInteract();
            }
        }

        // ── Uova ─────────────────────────────────────────────────

        public void OnEggCollected(EggController egg)
        {
            Wallet.Earn(egg.value);

            if (MissionManager.Instance != null)
                MissionManager.Instance.OnEggCollected();

            if (ui != null)
                ui.ShowToast("Uova raccolta! +" + egg.value + " €");

            if (EggSpawnManager.Instance != null)
                EggSpawnManager.Instance.RemoveEgg(egg.gameObject);
        }

        // ── NPC missione: focus ──────────────────────────────────

        public void OnMissionNPCFocusChanged(NPCMission mission, bool isFocused)
        {
            if (isFocused && mission != null)
            {
                currentMissionNPC = mission;
                string label = "";
                if (mission.state == NPCMission.MissionState.Available)
                    label = "MISSIONE";
                else if (mission.state == NPCMission.MissionState.Active)
                    label = mission.description + " (" + mission.currentCount + "/" + mission.targetCount + ")";
                else
                    label = "COMPLETATA";
                if (ui != null) ui.ShowInteract(label);
            }
            else if (!isFocused && currentMissionNPC == mission)
            {
                currentMissionNPC = null;
                RefreshInteractLabel();
            }
        }

        // ── Veicolo: fuoco / focus ────────────────────────────────

        public void OnVehicleFocusChanged(VehicleInteract vi)
        {
            if (vi != null && vi.IsFocused)
            {
                currentVehicleFocus = vi;
                string code = !string.IsNullOrEmpty(vi.vehicleCode) ? " [" + vi.vehicleCode + "]" : "";
                string label = vi.IsOwned() ? "ENTRA " + vi.data.vehicleName + code
                    : "COMPRA " + vi.data.vehicleName + " - \u20ac" + vi.data.price;
                if (ui != null) ui.ShowInteract(label);
            }
            else if (currentVehicleFocus == vi)
            {
                currentVehicleFocus = null;
                if (currentVehicleShopOpen)
                {
                    currentVehicleShopOpen = false;
                    if (VehicleShopUI.Instance != null) VehicleShopUI.Instance.HideDialog();
                }
                RefreshInteractLabel();
            }
        }

        // ── Veicolo: entrare / uscire ─────────────────────────────

        public void EnterVehicle(VehicleController vc)
        {
            if (vc == null || IsDriving) return;
            if (player == null) return;

            CurrentVehicle = vc;
            IsDriving = true;

            CharacterController cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.gameObject.SetActive(false);

            player.transform.SetParent(vc.transform, false);
            player.transform.localPosition = new Vector3(0f, 1.2f, 0f);

            vc.StartDriving();

            if (rig != null) rig.SetDrivingMode(true);

            if (ui != null)
            {
                ui.HideInteract();
                ui.ShowDrivingUI(true);
            }

            Debug.Log("[Game] Entrato nel veicolo: " + vc.data.vehicleName);
        }

        public void ExitVehicle()
        {
            if (!IsDriving || CurrentVehicle == null) return;
            if (player == null) return;

            CurrentVehicle.StopDriving();
            CurrentVehicle.SetInput(0f, 0f, false);

            player.transform.SetParent(null, false);
            player.gameObject.SetActive(true);

            Vector3 exitPos = CurrentVehicle.transform.position
                + CurrentVehicle.transform.right * 2f
                + Vector3.up * 0.1f;
            CharacterController cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = exitPos;
            player.transform.rotation = CurrentVehicle.transform.rotation;
            if (cc != null) cc.enabled = true;

            if (rig != null) rig.SetDrivingMode(false);

            if (ui != null)
            {
                ui.ShowDrivingUI(false);
                ui.HideInteract();
            }

            IsDriving = false;
            CurrentVehicle = null;
            currentVehicleShopOpen = false;
            Debug.Log("[Game] Uscito dal veicolo");
        }

        // ── Teleport ──────────────────────────────────────────────

        public void TeleportPlayer(Vector3 pos, Quaternion rot)
        {
            if (fader == null)
            {
                SetPlayerPosition(pos, rot);
                return;
            }
            StartCoroutine(DoTeleport(pos, rot));
        }

        private IEnumerator DoTeleport(Vector3 pos, Quaternion rot)
        {
            if (player != null) player.Stop();
            fader.gameObject.SetActive(true);
            fader.FadeToBlack(null);
            yield return new WaitForSeconds(fader.duration);
            SetPlayerPosition(pos, rot);
            fader.FadeFromBlack(null);
            yield return new WaitForSeconds(fader.duration);
            fader.gameObject.SetActive(false);
        }

        private void SetPlayerPosition(Vector3 pos, Quaternion rot)
        {
            if (player == null) return;
            CharacterController cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.transform.position = pos;
            player.transform.rotation = rot;
            if (cc != null) cc.enabled = true;
            player.Stop();
            if (rig != null) rig.SetYaw(rot);
        }
    }
}
