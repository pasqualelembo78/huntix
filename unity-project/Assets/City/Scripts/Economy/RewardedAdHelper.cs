using System;
using UnityEngine;
using City.World;

namespace City.Economy
{
    public class RewardedAdHelper : MonoBehaviour
    {
        public static RewardedAdHelper Instance;

        private const int REWARD_AMOUNT = 25;
        private Action<bool> onRewardResult;
        private bool waitingForReward;

        private void Awake()
        {
            Instance = this;
        }

        public void ShowRewardedAd(Action<bool> callback)
        {
            onRewardResult = callback;
            waitingForReward = true;

            if (Application.platform == RuntimePlatform.Android)
            {
                ShowAndroidRewardedAd();
            }
            else
            {
                // Editor fallback: grant reward immediately
                Debug.Log("[RewardedAdHelper] Editor mode: granting reward directly");
                OnRewardGranted();
            }
        }

        private void ShowAndroidRewardedAd()
        {
            try
            {
                using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                {
                    jc.CallStatic("showRewardedAd");
                }
                Debug.Log("[RewardedAdHelper] Rewarded ad requested from Android");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[RewardedAdHelper] Failed to show rewarded ad: " + e.Message);
                OnRewardFailed();
            }
        }

        /// <summary>Called from Android via UnitySendMessage when reward is earned</summary>
        public void OnRewardGranted()
        {
            if (!waitingForReward) return;
            waitingForReward = false;

            Wallet.Earn(REWARD_AMOUNT);

            var ui = City.UI.UIManager.Instance;
            if (ui != null)
                ui.ShowToast("+" + REWARD_AMOUNT + " € dal video!");

            onRewardResult?.Invoke(true);
            onRewardResult = null;
        }

        /// <summary>Called from Android when ad fails or is skipped</summary>
        public void OnRewardFailed()
        {
            if (!waitingForReward) return;
            waitingForReward = false;

            var ui = City.UI.UIManager.Instance;
            if (ui != null)
                ui.ShowToast("Video non disponibile");

            onRewardResult?.Invoke(false);
            onRewardResult = null;
        }

        public bool IsAvailable()
        {
            if (Application.platform == RuntimePlatform.Android)
            {
                try
                {
                    using (var jc = new AndroidJavaClass("com.intelligame.huntix.bridge.StoreUnityBridge"))
                    {
                        return jc.CallStatic<bool>("isRewardedAdReady");
                    }
                }
                catch { return false; }
            }
            return true; // Always available in editor
        }
    }
}
