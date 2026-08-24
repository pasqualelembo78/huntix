using System.Collections;
using UnityEngine;
using City.UI;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    [RequireComponent(typeof(Collider))]
    public class BuildingEntrance : MonoBehaviour
    {
        public string buildingName = "Edificio";
        public string buildingType = "house";
        public float buildingWidth = 8f;
        public float buildingDepth = 8f;
        public float buildingHeight = 8f;
        public int floorCount = 1;
        public Shop shop;

        private bool focused;
        public bool IsFocused => focused;

        public bool IsShop => buildingType == "shop";

        private bool autoEntryStarted;

        private static void LogEntrance(string msg)
        {
            Debug.Log("[BuildingEntrance] " + msg);
            UnityBridge.LogToAndroid("BuildingEntrance", msg);
        }

        private void Awake()
        {
            var rb = gameObject.GetComponent<Rigidbody>();
            if (rb == null)
            {
                rb = gameObject.AddComponent<Rigidbody>();
                rb.isKinematic = true;
                rb.useGravity = false;
                // log rimosso: spammava per ogni ingresso di ogni chunk
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            LogEntrance("OnTriggerEnter: " + buildingName + " (" + buildingType + ")");
            if (autoEntryStarted) return;
            focused = true;
            LogEntrance("focused=" + buildingName + " → calling OnEntranceFocusChanged");
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(this);
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            focused = false;
            if (Game.Instance != null)
                Game.Instance.OnEntranceFocusChanged(null);
        }

        public void StartAutoEntry()
        {
            if (autoEntryStarted) return;
            autoEntryStarted = true;
            StartCoroutine(AutoEntryCoroutine());
        }

        private IEnumerator AutoEntryCoroutine()
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.HideInteract();
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(buildingName + " - entrando...");

            yield return new WaitForSecondsRealtime(0.8f);

            if (Game.Instance != null && Game.Instance.fader != null && Game.Instance.fader.gameObject != null)
            {
                Game.Instance.fader.gameObject.SetActive(true);
                bool done = false;
                Game.Instance.fader.FadeToBlack(() =>
                {
                    Enter();
                    Game.Instance.fader.FadeFromBlack(() =>
                    {
                        Game.Instance.fader.gameObject.SetActive(false);
                        done = true;
                    });
                });
                yield return new WaitUntil(() => done);
            }
            else
            {
                Enter();
            }
        }

        public void Interact()
        {
            if (autoEntryStarted) return;
            Enter();
        }

        private void Enter()
        {
            if (InteriorManager.Instance == null)
            {
                LogEntrance("Enter FAILED: InteriorManager.Instance is null — cannot enter " + buildingName);
                return;
            }
            InteriorManager.Instance.EnterInterior(
                buildingType,
                buildingName,
                buildingWidth,
                buildingDepth,
                buildingHeight,
                floorCount,
                transform.position,
                transform.rotation,
                shop
            );
        }
    }
}
