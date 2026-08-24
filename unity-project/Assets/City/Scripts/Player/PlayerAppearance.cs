using System.Collections.Generic;
using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Skin del personaggio del giocatore nella città (fase 6).
    /// La skin scelta nel profilo Android arriva via BridgeActivity
    /// ("skin" nel JSON setMode), viene persistita in PlayerPrefs
    /// e applicata ai materiali dei SkinnedMeshRenderer del modello.
    /// </summary>
    public class PlayerAppearance : MonoBehaviour
    {
        public const string PrefKey = "city_skin";
        public const string DefaultSkin = "humanMaleA";

        /// <summary>Skin correntemente salvata sul device.</summary>
        public static string SavedSkin
        {
            get { return PlayerPrefs.GetString(PrefKey, DefaultSkin); }
        }

        private string _applied;

        private void Start()
        {
            Apply(SavedSkin);
        }

        /// <summary>Applica la skin se diversa da quella gia' in uso.</summary>
        public void Apply(string skinName)
        {
            if (string.IsNullOrEmpty(skinName)) skinName = DefaultSkin;
            if (skinName == _applied) return;
            if (ApplyTo(gameObject, skinName)) _applied = skinName;
        }

        /// <summary>
        /// Applica la texture skin a tutti gli SMR sotto root.
        /// Stessa ricetta di NPCPopulator.ApplySkin (materiale URP/Lit).
        /// </summary>
        public static bool ApplyTo(GameObject root, string skinName)
        {
            var skinTex = Resources.Load<Texture2D>("Characters/Skins/" + skinName);
            if (skinTex == null || root == null)
            {
                Debug.LogWarning("[PlayerAppearance] skin non trovata: " + skinName);
                return false;
            }
            var renderers = root.GetComponentsInChildren<SkinnedMeshRenderer>();
            foreach (var mr in renderers)
            {
                var mats = mr.sharedMaterials;
                for (int m = 0; m < mats.Length; m++)
                {
                    var mat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
                    if (mat.shader == null) mat = new Material(Shader.Find("Standard"));
                    if (mat.shader.name.StartsWith("Universal Render Pipeline"))
                    {
                        mat.SetColor("_BaseColor", Color.white);
                        mat.SetTexture("_BaseMap", skinTex);
                    }
                    else
                    {
                        mat.SetColor("_Color", Color.white);
                        mat.SetTexture("_MainTex", skinTex);
                    }
                    mats[m] = mat;
                }
                mr.sharedMaterials = mats;
            }
            Debug.Log("[PlayerAppearance] skin applicata: " + skinName);
            return true;
        }
    }
}
