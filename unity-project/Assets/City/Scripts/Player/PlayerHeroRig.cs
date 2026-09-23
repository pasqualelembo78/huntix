using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// Installa il modello del giocatore "PlayerHero" (Remy Mixamo, vedi
    /// RemyKitSetup) sotto il GameObject del player della citta', al posto
    /// del vecchio characterMedium Kenney. Modello disabilitato quello legacy.
    ///
    /// La preferenza alle altezza e al posizionamento avviene misurando i
    /// bounds reali degli SkinnedMeshRenderer dopo l'instantiate: niente
    /// magie, resta corretto anche se in futuro si cambia modello/scala.
    ///
    /// Il play animato passa da CharacterWalker (modalita' Animator) con il
    /// controller PlayerLocomotion (Speed + IsGrounded/Jump).
    /// </summary>
    public class PlayerHeroRig : MonoBehaviour
    {
        [Tooltip("Altezza finale in metri del personaggio (polo a terra).")]
        public float targetHeight = 1.8f;

        [Tooltip("Punto d'appoggio dei piedi in coordinate locali del player "
            + "(contiene CharacterController con capsule center=0, height=2 "
            + "-> i piedi stanno a y=-1).")]
        public Vector3 feetAnchor = new Vector3(0f, -1f, 0f);

        /// <summary>Abbassamento extra dei piedi visivi sotto la base della
        /// capsula (compensa skin + clearance del GroundFollow): la suola
        /// finisce ESATTAMENTE sulla superficie calpestata, senza il classico
        /// "galleggiamento" di qualche centimetro sopra asfalto/marciapiede.
        /// In flat-world il collider stradale e' l'asfalto stesso (0.03),
        /// quindi base = superficie + clearance e suola = superficie.</summary>
        public const float SoleTouchDrop = 0.03f;

        private static readonly string PrefabResource = "PlayerHero";
        private static readonly string RigName = "PlayerHeroRig";

        /// <summary>Vero se il giocatore (o oggetto dato) ha gia' l'hero rig.</summary>
        public static bool ActiveFor(GameObject root)
        {
            return root != null &&
                root.GetComponentInChildren<PlayerHeroRig>(true) != null;
        }

        /// <summary>
        /// Assicura che l'hero rig sia attivo sotto root. Idempotente e
        /// silenziosamente no-op se il prefab non e' presente (fallback =
        /// resta il personaggio legacy della scena).
        /// </summary>
        public static void Ensure(GameObject root)
        {
            if (root == null || ActiveFor(root))
            {
                OsmDiag.Log("[PlayerHeroRig][Ensure] skip: root null o rig gia' attivo (" +
                    (root != null ? root.name : "null") + ")");
                return;
            }

            OsmDiag.Log("[PlayerHeroRig][Ensure] carico prefab Resources/" + PrefabResource);
            var prefab = Resources.Load<GameObject>(PrefabResource);
            if (prefab == null)
            {
                OsmDiag.Log("[PlayerHeroRig] prefab '" + PrefabResource +
                    "' mancante: resta l'avatar legacy.");
                return;
            }
            OsmDiag.Log("[PlayerHeroRig][Ensure] prefab caricato, instantiate sotto " + root.name);

            var go = Instantiate(prefab, root.transform, false);
            go.name = RigName;
            go.transform.localPosition = Vector3.zero;
            go.transform.localRotation = Quaternion.identity;
            go.transform.localScale = Vector3.one;
            OsmDiag.Log("[PlayerHeroRig][Ensure] hero instanziato: name=" + go.name +
                " children=" + go.transform.childCount);

            // Nasconde il vecchio modello (characterMedium) della scena, se c'e'.
            HideLegacyModels(root);

            var rig = go.GetComponent<PlayerHeroRig>();
            if (rig == null) rig = go.AddComponent<PlayerHeroRig>();

            // Diagnostica: elenco figli dell'hero rig prima del lay
            var animPre = go.GetComponentInChildren<Animator>(true);
            var smrsPre = go.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            OsmDiag.Log("[PlayerHeroRig][Ensure] pre-LayFoot: animator=" + (animPre != null) +
                " smrs=" + smrsPre.Length);

            rig.LayFootOnAnchor();

            // Allinea la capsula del CharacterController ai piedi VISUALI del
            // modello (feetAnchor y=-1): se il CC della scena ha center.y =
            // height/2 il fondo capsula cade al pivot e i piedi dell'hero
            // (poggiati a localY=-1) restano ~1 m sotto il collider, con il
            // player che sembra affondato nel terreno (feetFromPivot=0 nei
            // log di CharacterWalker). Room.unity e' gia' corretta (2/0);
            // qui si normalizza a runtime qualunque scena.
            var cc = root.GetComponent<CharacterController>();
            if (cc != null)
            {
                float feet0 = rig.feetAnchor.y;
                if (Mathf.Abs(feet0) > 0.05f)
                {
                    cc.height = 2f * Mathf.Abs(feet0);
                    cc.center = new Vector3(0f, feet0 + cc.height * 0.5f, 0f);
                }
                OsmDiag.Log("[PlayerHeroRig] CC allineato ai piedi: height=" +
                    cc.height.ToString("F2") + " centerY=" + cc.center.y.ToString("F2") +
                    " feetFromPivot=" + (cc.height * 0.5f - cc.center.y).ToString("F2"));
            }

            // Anti T-pose: se l'Animator non valuta (avatar/controller/clip
            // mancanti) Remy sta in bind pose = T-pose; il fallback lo porta
            // in A-pose finche' l'animazione non prende il controllo.
            ArmsPoseFallback.Ensure(go);

            // Crescita fisiologica (FASE 1 - test isolato): se i morph del
            // personaggio sono stati "cotti" da RemyMorphBaker installa il
            // GrowthMorphController sull'hero rig. Guardato e idempotente:
            // senza morph cotti e' un no-op, il gioco procede come prima.
            // NON tocca il sistema XP esistente.
            GrowthMorphController.Ensure(go);

            // FASE 2: guida la crescita col livello XP esistente (un'unica XP
            // condivisa via bridge). Se il bridge manca resta il livello 1 e
            // l'aspetto base. Era: solo morph a mano, ora Xp->morph automatico.
            GrowthXpDriver.Ensure(go);

            // Diagnostica: verifica finale della filiera
            var anim = go.GetComponentInChildren<Animator>(true);
            var smrs = go.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            var activeSmrs = 0;
            for (int i = 0; i < smrs.Length; i++)
                if (smrs[i] != null && smrs[i].gameObject.activeInHierarchy) activeSmrs++;
            OsmDiag.Log("[PlayerHeroRig] hero attivo GO=" + go.name +
                " children=" + go.transform.childCount +
                " animator=" + (anim != null) +
                " animatorGO=" + (anim != null ? anim.gameObject.name : "-") +
                " smrs=" + smrs.Length + " attivi=" + activeSmrs +
                " rootChildren=" + root.transform.childCount);
        }

        /// <summary>Disattiva i modelli figlio che non appartengono all'hero rig.</summary>
        private static void HideLegacyModels(GameObject root)
        {
            var rig = root.GetComponentInChildren<PlayerHeroRig>(true);
            Transform rigTr = rig != null ? rig.transform : null;
            var smrs = root.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            for (int i = 0; i < smrs.Length; i++)
            {
                var smr = smrs[i];
                if (smr == null) continue;
                if (rigTr != null && smr.transform.IsChildOf(rigTr)) continue;
                if (smr.GetComponentInParent<PlayerHeroRig>(true) != null) continue;
                // Eredita animatore/controller separati (es. pet): lascia stare.
                var anim = smr.transform.GetComponentInParent<Animator>(true);
                if (anim != null && smr.transform.IsChildOf(anim.transform))
                    continue;
                smr.gameObject.SetActive(false);
            }
        }

        /// <summary>
        /// Scala il modello a targetHeight e sposta il rig in modo che i
        /// piedi stiano su feetAnchor. Misura i bounds dell'SMR nel mondo:
        /// corretto per qualsiasi posa base/rotazione.
        /// </summary>
        private void LayFootOnAnchor()
        {
            Bounds b;
            if (!WorldBoundsOfModel(out b)) return;

            float h = b.size.y;
            if (h <= 0.001f) h = 1.8f;
            float s = targetHeight / h;
            transform.localScale = Vector3.one * s;

            // bounds center/botton in spazio locale del rig (scala inclusa).
            Vector3 centerLocal = transform.InverseTransformPoint(b.center);
            Vector3 bottomLocal = transform.InverseTransformPoint(
                new Vector3(b.center.x, b.min.y, b.center.z));
            transform.localPosition += feetAnchor - bottomLocal +
                Vector3.down * SoleTouchDrop;

            OsmDiag.Log("[PlayerHeroRig] modello attivo boundsH=" + b.size.y.ToString("F2") +
                " scale=" + s.ToString("F2") + " foot=" + feetAnchor.ToString("F1"));
        }

        private bool WorldBoundsOfModel(out Bounds b)
        {
            var smrs = GetComponentsInChildren<SkinnedMeshRenderer>(true);
            b = new Bounds();
            bool any = false;
            for (int i = 0; i < smrs.Length; i++)
            {
                if (smrs[i] == null || !smrs[i].gameObject.activeInHierarchy) continue;
                if (!any) { b = smrs[i].bounds; any = true; }
                else b.Encapsulate(smrs[i].bounds);
            }
            return any;
        }
    }
}