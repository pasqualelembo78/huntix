// Stub del Game reale (escluso dal check perche' usa UIManager/TMPro).
// I membri che referenziano tipi City.Vehicle sono attivi solo in full mode
// (check.sh senza argomenti definisce HUNTIX_FULL e compila anche Vehicle/).
using UnityEngine;

namespace City
{
    public class Game : UnityEngine.MonoBehaviour
    {
        public static Game Instance => null;
        public City.Player.PlayerController player;
        public City.UI.UIManager ui;
        public City.UI.ScreenFader fader;
        public City.Player.CameraRig rig;
        public bool IsDriving => false;
        public bool IsInInterior => false;
#if HUNTIX_FULL
        public City.Vehicle.VehicleController CurrentVehicle => null;
        public City.Vehicle.VehiclePoiZone CurrentPoiZone => null;
        public void EnterVehicle(City.Vehicle.VehicleController vc) {}
        public void OnVehicleFocusChanged(City.Vehicle.VehicleInteract vi) {}
        public void OnPoiZoneFocusChanged(City.Vehicle.VehiclePoiZone zone) {}
#endif
        public void TeleportPlayer(Vector3 pos, Quaternion rot) {}
        public void ExitVehicle() {}
        public void OnMissionNPCFocusChanged(City.Economy.NPCMission mission, bool focused) {}
        public void OnEntranceFocusChanged(City.Interior.BuildingEntrance entrance) {}
        public void OpenShop() {}
        public void OpenShop(City.World.Shop shop) {}
    }
}

namespace City.UI
{
    // stub di UIManager (reale escluso: usa TMPro)
    public class UIManager : UnityEngine.MonoBehaviour
    {
        public static UIManager Instance;
        public ScreenFader fader;
        public void ShowToast(string message) {}
        public void HideInteract() {}
        public void ShowInteract(string label) {}
        public void OpenShop(City.World.Shop shop) {}
        public void ShowVehicleShop(City.Vehicle.VehicleInteract vi) {}
        public void ShowDrivingUI(bool show) {}
    }

    public class ScreenFader : UnityEngine.MonoBehaviour
    {
        public void FadeToBlack(System.Action done) {}
        public void FadeFromBlack(System.Action done) {}
    }
}
