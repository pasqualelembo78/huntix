using System.Collections;
using UnityEngine;
using City.Player;
using City.UI;
using City.World;

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
                if (ui != null) ui.HideInteract();
            }
        }

        public void OnInteractPressed()
        {
            if (currentDoor != null) currentDoor.Interact();
        }

        public void OpenShop(Shop shop)
        {
            if (ui != null) ui.OpenShop(shop);
        }

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
