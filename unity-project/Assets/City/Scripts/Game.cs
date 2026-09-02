using System.Collections;
using UnityEngine;
using City.Player;
using City.UI;
using City.World;
using City.Vehicle;
using City.Economy;
using City.Interior;
using City.OSM;
using City.Environment;

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

        // buffer riusabile per lo scan dei trigger nell'interno (evita la
        // FindObjectsOfType<MonoBehaviour> che scansionava l'intera scena)
        private readonly Collider[] interiorScan = new Collider[64];

        /// <summary>Zona POI veicoli sotto i piedi (concessionaria/officina/garage).</summary>
        public VehiclePoiZone CurrentPoiZone { get { return currentPoiZone; } }

        /// <summary>Espone i focus correnti per le azioni contestuali
        /// (pulsante centro-basso): porta, veicolo, NPC missione.</summary>
        public InteractDoor CurrentDoor { get { return currentDoor; } }
        public VehicleInteract CurrentVehicleFocus { get { return currentVehicleFocus; } }
        public NPCMission CurrentMissionNPC { get { return currentMissionNPC; } }

        // ── Modalita' veicolo ─────────────────────────────────────
        public bool IsDriving { get; private set; }
        public VehicleController CurrentVehicle { get; private set; }

        /// <summary>Transizione entrata/uscita (fade) in corso: blocca i doppi tap.</summary>
        private bool transitionBusy;

        // ── Modalita' interno ──────────────────────────────────────
        public bool IsInInterior { get { return InteriorManager.Instance != null && InteriorManager.Instance.IsInside; } }
        private BuildingEntrance currentEntrance;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            // Persistente (3.2): il cambio scena Single verso un regno distrugge
            // la City; Game radice contiene UIManager (figlio) e deve sopravvivere
            // insieme a Player/PlayerCamera (già root). Al ritorno in City la
            // scena ricrea i GameObjects del file .unity ma il guard in Awake li
            // distrugge subito, mantenendo questa istanza.
            DontDestroyOnLoad(gameObject);
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
            City.Vehicle.PassengerService.Ensure();
            City.Vehicle.BusMissionService.Ensure();
            City.Vehicle.TaxiService.Ensure();
            if (InteriorManager.Instance == null)
            {
                var imGo = new GameObject("InteriorManager");
                imGo.AddComponent<InteriorManager>();
            }

            // Sistemi economy "scritti ma mai istanziati": solo letture di
            // Instance (UIManager/Game/NPCMission) senza AddComponent, quindi
            // missioni, uova e rewarded ad erano codice morto. Persistenti come
            // gli altri servizi: un reload della scena City non deve perdere le
            // missioni attive.
            if (MissionManager.Instance == null)
            {
                var mmGo = new GameObject("MissionManager");
                DontDestroyOnLoad(mmGo);
                mmGo.AddComponent<MissionManager>();
            }
            if (EggSpawnManager.Instance == null)
            {
                var esGo = new GameObject("EggSpawnManager");
                DontDestroyOnLoad(esGo);
                esGo.AddComponent<EggSpawnManager>();
            }
            if (RewardedAdHelper.Instance == null)
            {
                var raGo = new GameObject("RewardedAdHelper");
                DontDestroyOnLoad(raGo);
                raGo.AddComponent<RewardedAdHelper>();
            }

            EnergySystem.EnsureHud();
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
                if (ui != null) ui.ShowInteract(zone.Label);
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

            // taxi del fischio in attesa: il tap fa salire nel taxi
            if (City.Vehicle.TaxiService.Instance != null &&
                City.Vehicle.TaxiService.Instance.TryBoard(this))
            {
                return;
            }

            // La TUA auto ha priorita' anche dentro il piazzale della
            // concessionaria: appena consegnata ci entri subito, senza che
            // il tap venga dirottato sul negozio.
            if (currentVehicleFocus != null &&
                currentVehicleFocus.data != null &&
                currentVehicleFocus.IsOwned())
            {
                EnterVehicle(currentVehicleFocus.controller);
                return;
            }

            // Priorita: zona POI > veicolo > porta > missione > ingresso edificio.
            // Dentro un concessionaria/officina il tap apre il relativo negozio,
            // anche se il tap cade su un veicolo (mostra) o un altro veicolo parcheggiato.
            if (currentPoiZone != null)
            {
                currentPoiZone.Interact();
                return;
            }
            if (currentVehicleFocus != null)
            {
                if (currentVehicleFocus.data == null)
                {
                    if (ui != null) ui.ShowToast("Veicolo non disponibile al momento");
                    return;
                }
                // niente acquisti per strada: offri le indicazioni
                OfferNearest("dealer", "CONCESSIONARIA",
                    "Puoi comprare le auto solo in concessionaria");
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
            var im = InteriorManager.Instance;
            if (im == null || !im.IsInside) return;

            // scan dei trigger attorno al player interno (buffer riusabile):
            // prima FindObjectsOfType<MonoBehaviour> passava in rassegna TUTTI
            // i componenti della scena a ogni tap (NPC, veicoli, ecc.)
            int n = Physics.OverlapSphereNonAlloc(im.InteriorPlayerPos, 3f, interiorScan);
            for (int i = 0; i < n; i++)
            {
                var c = interiorScan[i];
                if (c == null || !c.isTrigger) continue;
                if (c.GetComponent<StairTrigger>() is StairTrigger st && st.IsFocused)
                {
                    st.Interact();
                    return;
                }
                if (c.GetComponent<ExitTrigger>() is ExitTrigger et && et.IsFocused)
                {
                    et.Interact();
                    return;
                }
                if (c.GetComponent<ShopCounterTrigger>() is ShopCounterTrigger sct && sct.IsFocused)
                {
                    sct.Interact();
                    return;
                }
                if (c.GetComponent<VehicleCounterTrigger>() is VehicleCounterTrigger vct && vct.IsFocused)
                {
                    vct.Interact();
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
        public void RefreshInteractLabelNow()
        {
            RefreshInteractLabel();
        }

        private void RefreshInteractLabel()
        {
            if (ui == null) return;
            // taxista in attesa: priorita' assoluta sulla label
            if (City.Vehicle.TaxiService.Instance != null &&
                City.Vehicle.TaxiService.Instance.HasPrompt)
            {
                ui.ShowInteract("TAXI: ENTRA");
                return;
            }
            if (currentVehicleFocus != null)
            {
                if (currentVehicleFocus.data == null)
                {
                    // spawn degradato: prompt generico invece di
                    // NullReferenceException che rompe la catena di input
                    ui.ShowInteract("VEICOLO");
                }
                else if (currentVehicleFocus.IsOwned())
                {
                    string code = !string.IsNullOrEmpty(currentVehicleFocus.vehicleCode) ? " [" + currentVehicleFocus.vehicleCode + "]" : "";
                    string label = "ENTRA " + currentVehicleFocus.data.vehicleName + code;
                    ui.ShowInteract(label);
                }
                else if (currentVehicleFocus.OwnedByOther)
                {
                    // auto gia' venduta a un altro giocatore: resta informativo
                    ui.ShowInteract(currentVehicleFocus.label);
                }
                else
                {
                    // auto non di proprieta': NESSUNA scritta automatica
                    // sull'avvicinamento. L'offerta va in concessionaria
                    // appare SOLO premendo sull'auto.
                    ui.HideInteract();
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
                ui.ShowInteract(currentPoiZone.Label);
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

            // eco-sistema Huntix: l'uovo va anche nell'inventario uova generale
            // (collezione/evoluzione) lato Android, non solo come monete City.
            SendEggToHuntix(egg);
        }

        /// <summary>Invia l'uovo catturato nell'inventario Huntix (Android).</summary>
        private void SendEggToHuntix(EggController egg)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            string rarityId;
            switch (egg.rarity)
            {
                case City.Economy.EggController.Rarity.Common: rarityId = "common"; break;
                case City.Economy.EggController.Rarity.Uncommon: rarityId = "uncommon"; break;
                case City.Economy.EggController.Rarity.Rare: rarityId = "rare"; break;
                case City.Economy.EggController.Rarity.Legendary: rarityId = "legendary"; break;
                default: rarityId = "common"; break;
            }
            // power/xp derivati dalla rarita' e dal valore dell'uovo
            int power = Mathf.Max(1, egg.value * 3);
            int xp = Mathf.Max(10, egg.value * 6);
            string json = "{\"rarityId\":\"" + rarityId +
                "\",\"fantasyName\":\"Uovo di MiAcittà\"" +
                ",\"power\":" + power + ",\"xpReward\":" + xp + "}";
            try { Huntix.Bridge.UnityBridge.SendMessageToAndroid("EggCapturedInCity", json); }
            catch (System.Exception e) { UnityEngine.Debug.LogWarning("EggCapturedInCity failed: " + e.Message); }
#endif
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
                if (ui == null) return;
                if (vi.IsOwned() || vi.OwnedByOther)
                    ui.ShowInteract(vi.label);
                else
                    // auto non di proprieta': niente scritta automatica
                    // sull'avvicinamento; l'offerta parte SOLO dal tap
                    ui.HideInteract();
            }
            else if (currentVehicleFocus == vi)
            {
                currentVehicleFocus = null;
                RefreshInteractLabel();
            }
        }

        // ── Veicolo: entrare / uscire ─────────────────────────────

        /// <summary>
        /// Tap diretto sulla macchina (raycast di TouchInputHandler): se e'
        /// tua e sei abbastanza vicino ci entri; se e' altrui/vetrina e sei
        /// gia' in concessionaria si apre il negozio, altrimenti si offrono
        /// le indicazioni per comprarla.
        /// </summary>
        public void OnVehicleTapped(VehicleInteract vi)
        {
            if (vi == null) return;
            if (IsDriving) return;
            if (vi.data == null)
            {
                if (ui != null) ui.ShowToast("Veicolo non disponibile al momento");
                return;
            }

            if (vi.IsOwned())
            {
                if (player == null) return;
                Vector3 pa = player.transform.position;
                Vector3 va = vi.transform.position;
                pa.y = 0f; va.y = 0f;
                if ((pa - va).sqrMagnitude > 6f * 6f)
                {
                    if (ui != null) ui.ShowToast("Avvicinati all'auto per entrarci");
                    return;
                }
                EnterVehicle(vi.controller);
                return;
            }

            // auto altrui o in vetrina: gia' in concessionaria? apri il negozio
            if (currentPoiZone != null &&
                currentPoiZone.kind == VehiclePoiZone.PoiKind.Dealer)
            {
                currentPoiZone.Interact();
                return;
            }
            OfferNearest("dealer", "CONCESSIONARIA",
                "Puoi comprare le auto solo in concessionaria");
        }

        /// <summary>
        /// Toast + offerta di navigazione verso il POI piu' vicino del tipo
        /// dato (concessionaria per l'acquisto, officina per le panne).
        /// Dopo il SI' chiede COME raggiungerla (freccetta o teletrasporto).
        /// </summary>
        private void OfferNearest(string kind, string label, string reason)
        {
            if (ui != null) ui.ShowToast(reason);
            if (player == null)
                return;
            GeoCoord g = WorldOrigin.ToGeo(player.transform.position);
            var p = VehiclePoiRegistry.Nearest(kind, g.lat, g.lng);
            if (p == null)
                return;
            Vector3 w = WorldOrigin.ToWorld(p.lat, p.lng);
            float dist = Vector3.Distance(w, player.transform.position);
            string poiName = string.IsNullOrEmpty(p.name) ? label : p.name;
            OfferDialog.Offer(label,
                reason + ".\n" + label + " piu' vicina a " +
                    CompassUI.FormatDist(dist) +
                    "\nVuoi le indicazioni?",
                () =>
                {
                    // seconda domanda: come arrivarci? (si chiude subito)
                    OfferDialog.OfferTravel(poiName,
                        "Come vuoi raggiungere " + poiName + "?",
                        () => NavigateToPoi(poiName, kind, p.lat, p.lng),
                        () => TeleportToPoi(p.lat, p.lng));
                },
                null);
        }

        /// <summary>Navigazione: la freccetta rossa punta al POI.</summary>
        private void NavigateToPoi(string name, string kind,
            double lat, double lng)
        {
            NavigationState.Set(name, kind, lat, lng);
            if (ui != null)
                ui.ShowToast("Indicazioni impostate: " + name);
        }

        /// <summary>Teletrasporto immediato sul POI (con fade nero).</summary>
        private void TeleportToPoi(double lat, double lng)
        {
            if (player == null)
                return;
            Vector3 pos = WorldOrigin.ToWorld(lat, lng);
            pos.y = 0f;
            Quaternion rot = Quaternion.identity;
            if (Camera.main != null)
                rot = Quaternion.Euler(0f, Camera.main.transform.eulerAngles.y, 0f);
            NavigationState.Clear();
            TeleportPlayer(pos, rot);
            if (ui != null)
                ui.ShowToast("Sei arrivato!");
        }

        public void EnterVehicle(VehicleController vc)
        {
            if (vc == null || IsDriving || transitionBusy) return;
            if (player == null) return;

            // panne da chilometraggio (senza danni): si va in officina
            if (!vc.CanStart() && vc.Damage == VehicleDamage.None)
            {
                OfferNearest("repair", "OFFICINA",
                    "Motore in panne! Ripara l'auto in officina");
                return;
            }

            // in fiamme: prima i vigili del fuoco, poi il carro attrezzi
            if (vc.Damage == VehicleDamage.Fire)
            {
                RescueDirector.Ensure().OfferFireThenTow(vc);
                return;
            }

            // incidentata: non parte proprio, serve il carro attrezzi
            if (vc.Damage == VehicleDamage.Wrecked)
            {
                RescueDirector.Ensure().OfferTow(vc);
                return;
            }

            // gomma a terra: scegli fra carro attrezzi e guida zoppicante
            if (vc.Damage == VehicleDamage.Flat)
            {
                RescueDirector.Ensure().OfferFlatChoice(vc,
                    () => StartEnterVehicle(vc));
                return;
            }

            StartEnterVehicle(vc);
        }

        /// <summary>Entrata scenografica: nero, montaggio, via.</summary>
        public void StartEnterVehicle(VehicleController vc)
        {
            if (vc == null || IsDriving || transitionBusy) return;
            if (player == null) return;
            StartCoroutine(EnterRoutine(vc));
        }

        private IEnumerator StartVehicleBob(VehicleController vc, float duration)
        {
            if (vc == null) yield break;
            var t = vc.transform;
            var originalRot = t.localRotation;
            float elapsed = 0f;
            const float ampDeg = 1.8f;
            const float freq = 6f;
            while (elapsed < duration)
            {
                elapsed += Time.deltaTime;
                float pitch = Mathf.Sin(elapsed * freq) * ampDeg;
                t.localRotation = originalRot * Quaternion.Euler(pitch, 0f, 0f);
                yield return null;
            }
            t.localRotation = originalRot;
        }

        private IEnumerator EnterRoutine(VehicleController vc)
        {
            transitionBusy = true;
            var fader = this.fader;
            try
            {
                if (fader != null)
                {
                    fader.gameObject.SetActive(true);
                    fader.FadeToBlack(null);
                    var bob = StartCoroutine(StartVehicleBob(vc, fader.duration));
                    yield return new WaitForSeconds(fader.duration);
                    StopCoroutine(bob);
                }

                CharacterController cc =
                    player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                player.gameObject.SetActive(false);

                player.transform.SetParent(vc.transform, false);
                player.transform.localPosition = new Vector3(0f, 1.2f, 0f);
                // azzera lo yaw residuo della camminata: prima la camera di
                // guida inseguiva questo angolo e se eri entrato di lato la
                // vista restava perpendicolare all'auto
                player.transform.localRotation = Quaternion.identity;

                // riallinea subito la camera dietro al veicolo, guardando
                // nel suo stesso verso: dopo il fade sei gia' in guida
                if (rig != null) rig.SetYaw(vc.transform.rotation);

                vc.StartDriving();

                // se l'auto era stata ritrovata abbandonata, rientrare
                // chiude la pratica di recupero sul server
                var enteredVi = vc.GetComponentInChildren<VehicleInteract>();
                if (enteredVi != null && VehicleOwnershipApi.Instance != null)
                    VehicleOwnershipApi.Instance.ClearAbandonedIfFound(
                        enteredVi.vehicleCode);

                if (rig != null) rig.SetDrivingMode(true);

                CurrentVehicle = vc;
                IsDriving = true;

                if (ui != null)
                {
                    ui.HideInteract();
                    ui.ShowDrivingUI(true);
                }

                if (fader != null)
                {
                    fader.FadeFromBlack(null);
                    if (vc != null)
                    {
                        var bob = StartCoroutine(StartVehicleBob(vc, fader.duration));
                        yield return new WaitForSeconds(fader.duration);
                        StopCoroutine(bob);
                    }
                    fader.gameObject.SetActive(false);
                }
                Debug.Log("[Game] Entrato nel veicolo: " + vc.data.vehicleName);
            }
            finally
            {
                transitionBusy = false;
            }
        }

        public void ExitVehicle()
        {
            if (!IsDriving || CurrentVehicle == null) return;
            if (player == null) return;
            if (transitionBusy) return;
            StartCoroutine(ExitRoutine());
        }

        private IEnumerator ExitRoutine()
        {
            transitionBusy = true;
            var fader = this.fader;
            try
            {
                if (fader != null)
                {
                    fader.gameObject.SetActive(true);
                    fader.FadeToBlack(null);
                }
                if (fader != null)
                {
                    var bob = StartCoroutine(StartVehicleBob(CurrentVehicle, fader.duration));
                    yield return new WaitForSeconds(fader.duration);
                    StopCoroutine(bob);
                }
                var vc = CurrentVehicle;
                vc.StopDriving();
                vc.SetInput(0f, 0f, false);

                player.transform.SetParent(null, false);
                player.gameObject.SetActive(true);

                Vector3 exitPos = vc.transform.position
                    + vc.transform.right * 2f
                    + Vector3.up * 0.1f;
                CharacterController cc =
                    player.GetComponent<CharacterController>();
                if (cc != null) cc.enabled = false;
                player.transform.position = exitPos;
                player.transform.rotation = vc.transform.rotation;
                if (cc != null) cc.enabled = true;

                if (rig != null) rig.SetDrivingMode(false);

                // se il veicolo e' del giocatore, registra il parcheggio
                // sul server: resta dove lo ha lasciato (anche per gli altri)
                var exitedVi = vc.GetComponentInChildren<VehicleInteract>();
                string vcode = exitedVi != null ? exitedVi.vehicleCode : null;
                Vector3 vpos = vc.transform.position;
                float vheading = vc.transform.eulerAngles.y;

                IsDriving = false;
                CurrentVehicle = null;

                if (!string.IsNullOrEmpty(vcode) &&
                    (Inventory.Has("vehicle_" + vcode) ||
                     VehicleOwnershipApi.IsOwnedSafe(vcode)))
                {
                    GeoCoord g = WorldOrigin.ToGeo(vpos);
                    VehicleOwnershipApi.Ensure().Park(
                        vcode, g.lat, g.lng, vheading, null);
                }

                if (ui != null)
                {
                    ui.ShowDrivingUI(false);
                    ui.HideInteract();
                }

                if (fader != null)
                {
                    fader.FadeFromBlack(null);
                    if (vc != null)
                    {
                        var bob = StartCoroutine(StartVehicleBob(vc, fader.duration));
                        yield return new WaitForSeconds(fader.duration);
                        StopCoroutine(bob);
                    }
                    fader.gameObject.SetActive(false);
                }
                Debug.Log("[Game] Uscito dal veicolo");
            }
            finally
            {
                transitionBusy = false;
            }
        }

        /// <summary>Chiamato quando l'auto in guida prende fuoco: fa scendere
        /// il giocatore di corsa e offre i vigili del fuoco.</summary>
        public void OnVehicleCaughtFire(VehicleController vc)
        {
            if (vc == null) return;
            StartCoroutine(FireExitRoutine(vc));
        }

        private IEnumerator FireExitRoutine(VehicleController vc)
        {
            yield return new WaitForSeconds(1.2f);
            if (IsDriving && CurrentVehicle == vc && !transitionBusy)
                ExitVehicle();
            yield return new WaitForSeconds(0.4f);
            if (vc != null && vc.Damage == VehicleDamage.Fire)
                RescueDirector.Ensure().OfferFireThenTow(vc);
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
