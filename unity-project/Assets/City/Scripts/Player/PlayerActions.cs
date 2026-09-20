using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// API runtime per le animazioni-azione importate dalla Universal Animation
    /// Library 2 (UAL2, CC0). Gli stati vengono aggiunti al controller
    /// PlayerLocomotion da UAL2KitSetup; qui si attivano per nome clip.
    ///
    /// Esempio:
    ///   PlayerActions.Trigger(gameObject, "OverhandThrow");  // lancio
    ///   PlayerActions.Trigger(gameObject, "Consume");        // mangia
    ///   PlayerActions.Trigger(gameObject, "Yes");            // emote
    /// Se il parametro non esiste (animazione non importata) ritorna false.
    /// </summary>
    public static class PlayerActions
    {
        public const string ParamPrefix = "Act_";

        public static string ParameterName(string actionClip)
        {
            if (string.IsNullOrEmpty(actionClip)) return null;
            var sb = new System.Text.StringBuilder(actionClip.Length + ParamPrefix.Length);
            sb.Append(ParamPrefix);
            foreach (char ch in actionClip)
                sb.Append(char.IsLetterOrDigit(ch) ? ch : '_');
            return sb.ToString();
        }

        /// <summary>Attiva l'azione sulla radice indicata. True se il parametro esiste.</summary>
        public static bool Trigger(GameObject root, string actionClip)
        {
            if (root == null) return false;
            var animator = root.GetComponentInChildren<Animator>(true);
            return Trigger(animator, actionClip);
        }

        public static bool Trigger(Animator animator, string actionClip)
        {
            if (animator == null) return false;
            string p = ParameterName(actionClip);
            if (string.IsNullOrEmpty(p)) return false;
            foreach (var param in animator.parameters)
            {
                if (param.name != p) continue;
                animator.SetTrigger(p);
                return true;
            }
            return false;
        }
    }
}
