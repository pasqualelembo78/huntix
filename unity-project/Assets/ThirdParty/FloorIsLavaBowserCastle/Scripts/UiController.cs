using System;
using UnityEngine;
using UnityEngine.UIElements;

namespace FloorIsLava
{
    public class UiController : MonoBehaviour
    {
        public PlayerController playerController;

        private Label timeLbl;
        private Label coinLbl;
        private Label victoryLbl;
        private Label restartLbl;
        private Label soundLbl;

        private void OnEnable()
        {
            TryResolve();
        }

        private void TryResolve()
        {
            try
            {
                var doc = GetComponent<UIDocument>();
                if (doc == null || doc.rootVisualElement == null)
                {
                    Debug.LogWarning("[UiController] UIDocument assente: HUD disattivato.", this);
                    enabled = false;
                    return;
                }

                VisualElement root = doc.rootVisualElement;
                timeLbl = root.Q<Label>("TimeLabel");
                coinLbl = root.Q<Label>("CoinLabel");
                victoryLbl = root.Q<Label>("VictoryLabel");
                restartLbl = root.Q<Label>("RestartLabel");
                soundLbl = root.Q<Label>("SoundLabel");

                if (timeLbl == null || coinLbl == null)
                {
                    Debug.LogWarning("[UiController] Label HUD non trovati nell'UXML: HUD disattivato.", this);
                    enabled = false;
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning("[UiController] Errore inizializzazione HUD: " + e.Message, this);
                enabled = false;
            }
        }

        private void Update()
        {
            if (playerController == null || timeLbl == null || coinLbl == null)
                return;

            timeLbl.text = TimeSpan.FromSeconds(playerController.runTime).ToString(@"mm\:ss\.f");
            coinLbl.text = "$" + playerController.collectedCoins.ToString();

            if (playerController.isGameWon)
            {
                if (victoryLbl != null) victoryLbl.visible = true;
                if (restartLbl != null) restartLbl.visible = false;
                return;
            }

            if (!playerController.allowPlayerMovement)
            {
                if (victoryLbl != null) victoryLbl.visible = false;
                if (restartLbl != null) restartLbl.visible = true;
                return;
            }

            if (victoryLbl != null) victoryLbl.visible = false;
            if (restartLbl != null) restartLbl.visible = false;

            if (soundLbl != null)
                soundLbl.text = playerController.isMute ? "[M] OFF" : "[M] ON";
        }
    }
}