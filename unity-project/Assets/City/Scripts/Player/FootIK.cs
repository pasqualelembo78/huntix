using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// GIORNO 1 del piano di lavoro: sensore che verifica a runtime che
    /// l'avatar del player sia Humanoid e che Unity chiami davvero
    /// OnAnimatorIK. Gate gia' verificato (isHuman=True, m_IKPass:1).
    ///
    /// GIORNO 8: IK vero. I piedi vengono piantati sulla superficie reale
    /// sotto ciascun osso del piede (2 raycast/frame, uno per piede, con la
    /// stessa WalkableMask del Giorno 6: mai gli edifici layer 8) e il bacino
    /// scende quanto basta per far appoggiare anche la gamba piu' corta.
    /// Niente root motion: il movimento resta al CharacterController.
    ///
    ///cronologia dei pezzi collegati:
    ///   Giorno 6  - sonda a 5 punti: in OSM/GroundSnapper.ProbeFootprint
    ///   Giorno 7  - step assist: in OSM/GroundSnapper.Follow e
    ///               PlayerController.GroundFollow (Move del CC)
    ///   Giorno 8  - qui: piedi piantati + compensazione bacino
    ///
    /// Il componente viene agganciato a runtime da PlayerHeroRig.Ensure
    /// sull'GameObject che porta l'Animator, quindi NON va nel prefab
    /// Resources/PlayerHero.prefab (che RemyKitSetup riscrive in build).
    /// </summary>
    public class FootIK : MonoBehaviour
    {
        // ── Sonda IK: 2 raycast/frame totali (uno per piede) ──
        private const float ProbeUp = 0.45f;    // partenza sopra l'osso caviglia
        private const float ProbeDown = 0.85f;  // portata sotto il piede
        private const float MaxPlant = GroundSnapper.StepAssistHeight; // 0,35 m
        private const float IkRate = 12f;       // /s smorzamento posa piedi/bacino
        private const float MaxPelvisDrop = 0.18f;
        private const float FootWeight = 0.85f;
        private const float RotWeight = 0.45f;

        // ── Riflesso uova: dentro l'edificio, davanti al cassetto ──
        private const float DrawerDist = 1.8f;
        private const float DrawerLean = 0.12f;

        private Animator _anim;
        private CharacterController _cc;
        private CharacterWalker _walker;
        private bool _ikSeen;
        private int _lastFrame = -1;
        private float _leftOff, _rightOff;
        private bool _seeded;
        private float _pelvis;

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
            _cc = GetComponentInParent<CharacterController>();
            _walker = GetComponentInParent<CharacterWalker>();
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
            if (layerIndex != 0 || _anim == null) return;
            if (!_ikSeen)
            {
                _ikSeen = true;
                OsmDiag.Log("[FootIK] OnAnimatorIK chiamato (layer 0): IK attivo (Giorno 8)");
            }

            // Con piu' passate d'animazione OnAnimatorIK puo' essere chiamato
            // due volte nello stesso frame: il budget resta 2 raycast/frame.
            if (_lastFrame == Time.frameCount) return;
            _lastFrame = Time.frameCount;

            if (!Active())
            {
                Weight(AvatarIKGoal.LeftFoot, 0f);
                Weight(AvatarIKGoal.RightFoot, 0f);
                _seeded = false;
                _pelvis = 0f;
                return;
            }

            Transform lb = _anim.GetBoneTransform(HumanBodyBones.LeftFoot);
            Transform rb = _anim.GetBoneTransform(HumanBodyBones.RightFoot);
            Quaternion lr = Quaternion.identity, rr = Quaternion.identity;
            float lo = lb != null ? PlantOffset(lb, out lr) : 0f;
            float ro = rb != null ? PlantOffset(rb, out rr) : 0f;

            float dt = Mathf.Min(Time.deltaTime, PlayerController.MaxFrameStepS);
            float a = (IkRate * dt) / (1f + IkRate * dt);
            if (!_seeded)
            {
                _leftOff = lo;
                _rightOff = ro;
                _seeded = true;
            }
            else
            {
                _leftOff += (lo - _leftOff) * a;
                _rightOff += (ro - _rightOff) * a;
            }

            if (lb != null)
            {
                _anim.SetIKPosition(AvatarIKGoal.LeftFoot,
                    lb.position + Vector3.up * _leftOff);
                _anim.SetIKRotation(AvatarIKGoal.LeftFoot, lr);
                Weight(AvatarIKGoal.LeftFoot, FootWeight);
            }
            else Weight(AvatarIKGoal.LeftFoot, 0f);

            if (rb != null)
            {
                _anim.SetIKPosition(AvatarIKGoal.RightFoot,
                    rb.position + Vector3.up * _rightOff);
                _anim.SetIKRotation(AvatarIKGoal.RightFoot, rr);
                Weight(AvatarIKGoal.RightFoot, FootWeight);
            }
            else Weight(AvatarIKGoal.RightFoot, 0f);

            // Compensazione del bacino: scende quanto serve al piede PIU'
            // BASSO, altrimenti la gamba piu' corta resta sospesa.
            float need = -Mathf.Min(0f, Mathf.Min(_leftOff, _rightOff));

            // Riflesso uova: davanti al cassetto ci si piega un poco. La
            // distanza e' sola lettura: ContainerEgg non viene modificato.
            var g = City.Game.Instance;
            if (g != null && g.player != null)
            {
                float d = City.Interior.ContainerEgg.DistanceToChosen(
                    g.player.transform.position);
                if (d <= DrawerDist) need += DrawerLean * (1f - d / DrawerDist);
            }

            need = Mathf.Clamp(need, 0f, MaxPelvisDrop);
            _pelvis += (need - _pelvis) * a;

            // bodyPosition e' la posa animata "pulita" di QUESTO frame: leggerla
            // qui e riscriverla col solo delta evita l'accumulo frame su frame.
            Vector3 body = _anim.bodyPosition;
            _anim.bodyPosition = body - Vector3.up * _pelvis;
        }

        /// <summary>Una sola sonda corta per piede. WalkableMask esclude gli
        /// edifici (layer 8): i piedi non si aggrappano ai muri. Oltre
        /// MaxPlant la superficie e' un gradino vero (Giorno 7): non piegare
        /// le gambe, ci pensa lo step assist a salirci.</summary>
        private float PlantOffset(Transform bone, out Quaternion footRot)
        {
            footRot = bone.rotation;
            Vector3 p = bone.position;
            RaycastHit hit;
            if (!Physics.Raycast(p + Vector3.up * ProbeUp, Vector3.down, out hit,
                    ProbeUp + ProbeDown, GroundSnapper.WalkableMask,
                    QueryTriggerInteraction.Ignore))
                return 0f;
            if (hit.normal.y < GroundSnapper.ShortNorm) return 0f;

            // p e' l'osso caviglia: la suola reale sta SoleBelowAnkle piu'
            // in basso. Piantare la caviglia sull'asfalto faceva galleggiare
            // la scarpa di tutto lo spessore della suola.
            float ankleTarget = hit.point.y +
                (_walker != null ? _walker.SoleBelowAnkle : 0f) -
                PlayerHeroRig.SoleTouchDrop;
            float off = ankleTarget - p.y;
            if (off > MaxPlant || off < -MaxPlant) return 0f;
            footRot = Quaternion.FromToRotation(Vector3.up, hit.normal) * bone.rotation;
            return off;
        }

        private void Weight(AvatarIKGoal goal, float w)
        {
            _anim.SetIKPositionWeight(goal, w);
            _anim.SetIKRotationWeight(goal, w > 0f ? RotWeight : 0f);
        }

        private bool Active()
        {
            if (_cc != null && !_cc.enabled) return false;
            // Grounded e' lo stato "stabile" del Giorno 8: non fa flip sui
            // cordoli durante lo step assist, quindi l'IK non pianta i piedi
            // in aria per un frame.
            if (_walker != null && !_walker.Grounded) return false;
            var g = City.Game.Instance;
            if (g != null && g.IsDriving) return false;
            if (City.Economy.EggCaptureMinigame.Instance != null &&
                City.Economy.EggCaptureMinigame.Instance.IsActive) return false;
            return true;
        }
    }
}
