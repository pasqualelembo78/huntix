using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// GIORNO 1 del piano di lavoro: sensore che verifica a runtime che
    /// l'avatar del player sia Humanoid e che Unity chiami davvero
    /// OnAnimatorIK. Non modifica ancora la posa: non puo' rompere
    /// l'animazione esistente.
    ///
    /// Nei giorni successivi qui entreranno, in ordine:
    ///   Giorno 6  - sonda a 5 punti: FATTA in OSM/GroundSnapper.ProbeFootprint
    ///              (non qui), usata anche da TryGroundColumn
    ///   Giorno 7  - step assist: FATTO in OSM/GroundSnapper.Follow (regimi
    ///              step/magnet) e PlayerController.GroundFollow (Move del CC)
    ///   Giorno 8  - foot IK vero: piedi piantati + compensazione bacino
    ///
    /// Il componente viene agganciato a runtime da PlayerHeroRig.Ensure
    /// sull'GameObject che porta l'Animator, quindi NON va nel prefab
    /// Resources/PlayerHero.prefab (che RemyKitSetup riscrive in build).
    /// </summary>
    public class FootIK : MonoBehaviour
    {
        private Animator _anim;
        private bool _ikSeen;

        /// <summary>Aggancia il sensore all'Animator dell'hero, se presente.
        /// Idempotente; ritorna false se non c'e' un Animator (caso in cui
        /// il gioco resta sull'avatar legacy e l'IK non ha senso).</summary>
        public static bool Ensure(GameObject hero)
        {
            if (hero == null) return false;
            var anim = hero.GetComponentInChildren<Animator>(true);
            if (anim == null)
            {
                OsmDiag.Log("[FootIK] Ensure: nessun Animator sotto l'hero, IK non installato");
                return false;
            }
            if (anim.GetComponent<FootIK>() != null) return true;
            anim.gameObject.AddComponent<FootIK>();
            OsmDiag.Log("[FootIK] Ensure: sensore installato su " + anim.gameObject.name);
            return true;
        }

        private void Awake()
        {
            _anim = GetComponent<Animator>();
        }

        private void Start()
        {
            if (_anim == null)
            {
                OsmDiag.Log("[FootIK] ERRORE: nessun Animator su questo GameObject");
                return;
            }
            var av = _anim.avatar;
            var ctrl = _anim.runtimeAnimatorController;
            OsmDiag.Log("[FootIK] VERIFICA avatar=" + (av != null ? av.name : "NULL") +
                " isHuman=" + (av != null && av.isHuman) +
                " controller=" + (ctrl != null ? ctrl.name : "NULL") +
                " applyRootMotion=" + _anim.applyRootMotion);
        }

        /// <summary>Unity chiama questo metodo solo se l'Animator e' valido.
        /// Il log una sola volta e' la prova (dal device) che l'IK e' chiamabile
        /// e che il prerequisito del Giorno 1 e' soddisfatto.</summary>
        private void OnAnimatorIK(int layerIndex)
        {
            if (layerIndex != 0 || _ikSeen) return;
            _ikSeen = true;
            OsmDiag.Log("[FootIK] OnAnimatorIK chiamato (layer 0): IK pronto per Giorno 6-8");
        }
    }
}
