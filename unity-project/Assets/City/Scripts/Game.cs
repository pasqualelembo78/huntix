using System.Collections;
using UnityEngine;
using City.Player;
using City.UI;
using City.World;
using City.Vehicle;
using City.Economy;
using City.Interior;
using City.OSM;

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
        private VehiclePoiZone currentPoiZone;

        /// <summary>Zona POI veicoli sotto i piedi (concessionaria/officina/garage).</summary>
        public VehiclePoiZone CurrentPoiZone { get { return currentPoiZone; } }

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

            if (TouchInputHandler.Instance == null)
            {
                var go = new GameObject("TouchInputHandler");
                go.AddComponent<TouchInputHandler>();
            }

            // Il gestore degli interni non e' in scena: si auto-costruisce qui.
            // Non DontDestroyOnLoad: riferisce player/rig di QUESTA scena.
            if (InteriorManager.Instance == null)
            {
                var imGo = new GameObject("InteriorManager");
                imGo.AddComponent<InteriorManager>();
            }
        }

        private void Update()
        {
            // Guida con gli stessi controlli della camminata: joystick
            // sinistro (su/giu = gas/retro, dx/sx = sterzo); la camera si
            // ruota trascinando la meta' destra dello schermo
            if (IsDriving && CurrentVehicle != null && ui != null &&
                ui.joystick != null)
            {
                Vector2 v = ui.joystick.Value;
                CurrentVehicle.SetInput(v.y, v.x, false);
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
                bool blocked = currentVehicleFocus != null || currentMissionNPC != null;
                Debug.Log("[Game] OnEntranceFocusChanged: " + entrance.buildingName + " (" + entrance.buildingType + ") blocked=" + blocked);
                if (!blocked)
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

        // ── Zone POI veicoli (concessionaria / officina / garage) ──

        public void OnPoiZoneFocusChanged(VehiclePoiZone zone)
        {
            if (zone != null && zone.IsFocused)
            {
                currentPoiZone = zone;
                if (ui != null) ui.ShowInteract(zone.PromptLabel());
            }
            else if (currentPoiZone == zone)
            {
                currentPoiZone = null;
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
            // Priorità: veicolo > zona POI > porta > missione > ingresso edificio
            if (currentVehicleFocus != null)
            {
                if (currentVehicleFocus.data == null)
                {
                    if (ui != null) ui.ShowToast("Veicolo non disponibile al momento");
                    return;
                }
                if (currentVehicleFocus.IsOwned())
                {
                    EnterVehicle(currentVehicleFocus.controller);
                }
                else
                {
                    // niente acquisti per strada: solo in concessionaria
                    if (ui != null)
                        ui.ShowToast("Puoi comprare le auto solo in concessionaria");
                }
                return;
            }
            if (currentPoiZone != null)
            {
                currentPoiZone.Interact();
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
                if (currentVehicleFocus.data == null)
                {
                    // spawn degradato: prompt generico invece di
                    // NullReferenceException che rompe la catena di input
                    ui.ShowInteract("VEICOLO");
                }
                else
                {
                    string code = !string.IsNullOrEmpty(currentVehicleFocus.vehicleCode) ? " [" + currentVehicleFocus.vehicleCode + "]" : "";
                    string label = currentVehicleFocus.IsOwned() ? "ENTRA " + currentVehicleFocus.data.vehicleName + code
                        : currentVehicleFocus.data.vehicleName + " - in vendita in concessionaria";
                    ui.ShowInteract(label);
                }
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
            else if (currentPoiZone != null)
            {
                ui.ShowInteract(currentPoiZone.PromptLabel());
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
                // l'etichetta la calcola VehicleInteract (gestisce anche
                // "venduta ad altro giocatore" e vendita)
                if (ui != null) ui.ShowInteract(vi.label);
            }
            else if (currentVehicleFocus == vi)
            {
                currentVehicleFocus = null;
                RefreshInteractLabel();
            }
        }

        // ── Veicolo: entrare / uscire ─────────────────────────────

        public void EnterVehicle(VehicleController vc)
        {
            if (vc == null || IsDriving) return;
            if (player == null) return;

            // panne: senza condizione (o con gomma a terra) non parte
            if (!vc.CanStart())
            {
                if (ui != null)
                {
                    if (vc.FlatTire)
                        ui.ShowToast("Gomma a terra! Riparla in officina o lascia perdere questa corsa.");
                    else
                        ui.ShowToast("Motore in panne! Ripara l'auto in officina.");
                }
                return;
            }

            CurrentVehicle = vc;
            IsDriving = true;

            CharacterController cc = player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            player.gameObject.SetActive(false);

            player.transform.SetParent(vc.transform, false);
            player.transform.localPosition = new Vector3(0f, 1.2f, 0f);
            // azzera lo yaw residuo della camminata: prima la camera di
            // guida inseguiva questo angolo e se eri entrato di lato la
            // vista restava perpendicolare all'auto
            player.transform.localRotation = Quaternion.identity;

            vc.StartDriving();

            // se l'auto era stata ritrovata abbandonata, rientrare chiude
            // la pratica di recupero sul server
            var enteredVi = vc.GetComponentInChildren<VehicleInteract>();
            if (enteredVi != null && VehicleOwnershipApi.Instance != null)
                VehicleOwnershipApi.Instance.ClearAbandonedIfFound(
                    enteredVi.vehicleCode);

            if (rig != null)
            {
                rig.SetDrivingMode(true);
                // riallinea subito la camera dietro al veicolo,
                // guardando nel suo stesso verso
                rig.SetYaw(vc.transform.rotation);
            }

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

            // se il veicolo e' del giocatore, registra il parcheggio sul
            // server: resta dove lo ha lasciato (visibile anche agli altri)
            var exitedVi = CurrentVehicle.GetComponentInChildren<VehicleInteract>();
            string vcode = exitedVi != null ? exitedVi.vehicleCode : null;
            Vector3 vpos = CurrentVehicle.transform.position;
            float vheading = CurrentVehicle.transform.eulerAngles.y;

            IsDriving = false;
            CurrentVehicle = null;

            if (!string.IsNullOrEmpty(vcode) &&
                (Inventory.Has("vehicle_" + vcode) || VehicleOwnershipApi.IsOwnedSafe(vcode)))
            {
                GeoCoord g = WorldOrigin.ToGeo(vpos);
                VehicleOwnershipApi.Ensure().Park(vcode, g.lat, g.lng, vheading, null);
            }

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
