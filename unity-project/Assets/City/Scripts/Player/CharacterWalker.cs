using System.Collections;
using System.Collections.Generic;
using UnityEngine;

namespace City.Player
{
    // Animatore del personaggio Kenney characterMedium senza Animator:
    // il controller non e' creabile a runtime (API solo editor), quindi si usa
    // il componente Animation legacy. Strategia in due stadi:
    //  1) prova le clip importate da Resources/Characters/{idle,run}.fbx
    //     (clip.legacy=true a runtime); dopo 0,5 s verifica che le ossa si
    //     muovano davvero (i percorsi interni delle clip potrebbero non
    //     corrispondere alla gerarchia istanziata);
    //  2) se la verifica fallisce (o le risorse mancano), genera curve
    //     procedurali sui percorsi ossei reali, verificati nella scena City:
    //     radice/Root/HipsCtrl/Hips/{LeftUpLeg|RightUpLeg|Spine/...}.
    // PlayerController (e piu' avanti NPCController) chiamano SetSpeed ogni frame.
    public class CharacterWalker : MonoBehaviour
    {
        private const string IdleState = "Idle";
        private const string WalkState = "Walk";
        private const string RunState = "Run";

        // Percorsi ossei relativi alla radice del modello (characterMedium(Clone))
        private const string HipsPath = "Root/HipsCtrl/Hips";
        private const string SpinePath = HipsPath + "/Spine";
        private const string ChestPath = SpinePath + "/Chest";
        private const string UpChestPath = ChestPath + "/UpperChest";
        private const string LUpLegPath = HipsPath + "/LeftUpLeg";
        private const string LLegPath = LUpLegPath + "/LeftLeg";
        private const string RUpLegPath = HipsPath + "/RightUpLeg";
        private const string RLegPath = RUpLegPath + "/RightLeg";
        private const string LShoulderPath = UpChestPath + "/LeftShoulder";
        private const string RShoulderPath = UpChestPath + "/RightShoulder";
        private const string LArmPath = LShoulderPath + "/LeftArm";
        private const string RArmPath = RShoulderPath + "/RightArm";
        private const string LForeArmPath = LArmPath + "/LeftForeArm";
        private const string RForeArmPath = RArmPath + "/RightForeArm";

        [Header("Regolazione procedurale")]
        public float legSwingDeg = 30f;
        public float kneeBendDeg = 34f;
        public float armSwingDeg = 22f;
        public float elbowBendDeg = 26f;
        // Soglia di velocita' (m/s) oltre la quale si passa da Walk a Run
        // (i pedoni camminano ~1.2-1.5, il player cammina ~4 e corre ~7.5).
        public float runThreshold = 4.2f;
        // Cadenza (Hz) dei cicli: la corsa e' piu' veloce della camminata.
        public float walkCadence = 1.7f;
        public float runCadence = 2.6f;
        // Lunghezza del passo RESA dal gesto (stridia). La clip procedurale
        // viene riprodotta a un tempo che fa combaciare il ritmo delle gambe
        // con la velocita' reale di avanzamento: passo reale = velocita' /
        // cadenza effettiva. Senza questo, a 4 m/s il walker "patina" (~2.7 m
        // a passo) e la corsa sembra sfasata sul terreno.
        public float walkStrideM = 1.1f;
        public float runStrideM = 2.0f;
        // Inclinazione in avanti del busto durante il movimento: la corsa si
        // "tuffa" nel passo con uno sprint lean, allungando visivamente il passo.
        public float forwardLeanDeg = 7f;
        // Rimbalzo verticale del bacino: nelle persone reale e' MINIMO (il
        // busto resta quasi livellato, il COM scende/scende di pochi cm per
        // passo). Valori alti creano il fastidioso "su e giu". Due colpi per
        // ciclo (sin(ph*2)) solo con ampiezza piccola; 0 = bacino livellato.
        public float walkBounceM = 0.004f;
        public float runBounceM = 0.006f;
        // Se sul device il camminatore sembra camminare all'indietro, -1 qui.
        public float swingSign = 1f;

        // Braccia in posa A calibrate sulle ossa reali: invece di un angolo
        // fisso sull'asse X (che su alcuni rig lascia una braccio davanti e
        // uno dietro), il fold viene misurato a runtime per ogni spalla,
        // cosi' la simmetria e' garantita per costruzione. La direzione
        // laterale si ricava dall'asse destro del modello (transform.right),
        // identico per entrambe le spalle: niente piu' "braccio avanti/dietro".
        private Quaternion _foldL = Quaternion.identity;
        private Quaternion _foldR = Quaternion.identity;
        private bool _foldReady;

        private Animation anim;
        private float speed;
        private string current;
        private bool ready;

        public bool IsReady { get { return ready; } }

        // Aggiunge il walker alla radice del modello Kenney se non c'e' gia'.
        // La radice e' l'antenato piu' alto sotto ownerRoot che porta una
        // SkinnedMeshRenderer (es. characterMedium(Clone) figlio del Player).
        // Ritorna il componente aggiunto (o gia' presente), null altrimenti.
        public static CharacterWalker AttachIfNeeded(GameObject ownerRoot)
        {
            if (ownerRoot == null) return null;
            CharacterWalker existing = ownerRoot.GetComponentInChildren<CharacterWalker>();
            if (existing != null) return existing;
            SkinnedMeshRenderer smr = ownerRoot.GetComponentInChildren<SkinnedMeshRenderer>();
            if (smr == null) return null;

            // Il walker deve stare sulla RADICE del MODELLO Kenney: il GO che
            // ha `Root` come figlio diretto (da li' i percorsi ossei
            // "Root/HipsCtrl/..." risolvono). Questa radice puo' coincidere con
            // ownerRoot (NPC istanziati a runtime da Resources: lo SMR e' sotto
            // un figlio, risalire fermandosi a ownerRoot butterebbe il walker
            // sul GO dello SMR e le ossa non verrebbero piu' trovate) oppure
            // essere un figlio (player montato in editor sotto il Player GO).
            Transform target = null;
            for (Transform t = smr.transform; t != null; t = t.parent)
            {
                if (t.Find("Root") != null) { target = t; break; }
            }
            if (target == null) target = smr.transform;

            var w = target.GetComponent<CharacterWalker>();
            if (w != null) return w;
            return target.gameObject.AddComponent<CharacterWalker>();
        }

        private void Start()
        {
            // Un Animator humanoid SENZA controller (i modelli istanziati a
            // runtime da Resources, come gli NPC) non gioca nulla e, restando
            // attivo, congela il rig in T-pose e blocca la riproduzione legacy.
            // Il Destroy differito non basta: per il resto del frame l'Animator
            // tiene comunque il modello in T-pose e le clip legacy non partono.
            // Qui lo si toglie SUBITO con DestroyImmediate, prima che
            // BuildProcedural acceda alle ossa via transform.Find (le ossa di
            // un rig humanoid ottimizzato sono nascoste finche' l'Animator e'
            // vivo). Un Animator CON controller non viene toccato: e' il caso
            // del player, che anima coi controller Kenney reali.
            var animators = GetComponentsInChildren<Animator>(true);
            for (int i = 0; i < animators.Length; i++)
            {
                if (animators[i] == null) continue;
                if (animators[i].runtimeAnimatorController == null)
                    DestroyImmediate(animators[i]);
            }

            // Solo curve procedurali: calibrate a runtime sugli assi ossei reali
            // (braccia simmetriche, gambe/ginocchia non storte).
            BuildProcedural();
        }

        public void SetSpeed(float metersPerSecond)
        {
            speed = metersPerSecond;
        }

        private void Update()
        {
            if (anim == null) return;
            string want = IdleState;
            if (speed > 0.15f)
                want = speed > runThreshold ? RunState : WalkState;
            if (want != current)
            {
                current = want;
                anim.CrossFade(want, 0.18f);
            }
            AnimationState st = anim[current];
            if (st != null)
            {
                // Cadenza agganciata alla velocita' reale: il ritmo delle gambe
                // deve equivalere (velocita' / lunghezza del passo). L'eventuale
                // clamp mantiene il gesto credibile tra idle e sprint.
                bool running = current == RunState;
                float baseCad = running ? runCadence : walkCadence;
                float stride = running ? runStrideM : walkStrideM;
                float cad = speed / Mathf.Max(0.1f, stride);
                st.speed = Mathf.Clamp(cad / Mathf.Max(0.1f, baseCad), 0.4f, 2.1f);
            }
        }

        /// <summary>
        /// Misura sulle ossa reali (posa di riposo) il delta che porta ogni
        /// braccio dritto lungo il corpo con una leggera apertura (A-pose).
        /// La direzione laterale e' DERIVATA dall'asse destro del modello
        /// (transform.right), che e' lo stesso per entrambe le spalle: cosi'
        /// la piega e' simmetrica per costruzione (niente piu' braccio avanti
        /// e uno dietro tipici di un fold speculare fisso o di un asse locale
        /// spalla non allineato).
        /// </summary>
        private void CalibrateFold()
        {
            Transform lSh = transform.Find(LShoulderPath);
            Transform rSh = transform.Find(RShoulderPath);
            Transform lArm = transform.Find(LArmPath);
            Transform rArm = transform.Find(RArmPath);
            if (lSh == null || rSh == null || lArm == null || rArm == null) return;
            try
            {
                Quaternion lShWorld = lSh.parent != null
                    ? lSh.parent.rotation * lSh.localRotation
                    : lSh.localRotation;
                Quaternion rShWorld = rSh.parent != null
                    ? rSh.parent.rotation * rSh.localRotation
                    : rSh.localRotation;

                Vector3 lDir = (lArm.position - lSh.position).normalized;
                Vector3 rDir = (rArm.position - rSh.position).normalized;
                if (lDir.sqrMagnitude < 0.0001f || rDir.sqrMagnitude < 0.0001f) return;

                // laterali simmetrici: sinistra = -destra del modello (X mondo)
                Vector3 lTgt = FoldTarget(transform.right * -1f);
                Vector3 rTgt = FoldTarget(transform.right * 1f);

                // nel locale della spalla: delta applicato (rest * delta) porta
                // il braccio sul target; il fold resta per lato, ognuno calibro'
                _foldL = SideFold(lShWorld, lDir, lTgt);
                _foldR = SideFold(rShWorld, rDir, rTgt);
                _foldReady = true;

                // anche gli assi di oscillazione (laterale, orizzontale) sono
                // derivati dal modello: braccia e gomiti si muovono di pari passo
                _swingAxisL = Quaternion.Inverse(lShWorld) * (transform.right * -1f);
                _swingAxisR = Quaternion.Inverse(rShWorld) * (transform.right * 1f);
                _swingReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione braccia: " + ex);
            }
        }

        private static Quaternion SideFold(Quaternion shWorld, Vector3 dirW, Vector3 tgtW)
        {
            Vector3 dirL = Quaternion.Inverse(shWorld) * dirW;
            Vector3 tgtL = Quaternion.Inverse(shWorld) * tgtW;
            if (dirL.sqrMagnitude < 0.0001f || tgtL.sqrMagnitude < 0.0001f)
                return Quaternion.identity;
            return Quaternion.FromToRotation(dirL.normalized, tgtL.normalized);
        }

        // Giu' lungo il corpo con una leggera apertura laterale verso l'esterno:
        // braccia appena staccate dai fianchi, simmetriche e NON avanti/dietro.
        private static Vector3 FoldTarget(Vector3 lateral)
        {
            lateral.y = 0f;
            if (lateral.sqrMagnitude < 0.001f) lateral = Vector3.right;
            lateral.Normalize();
            return (Vector3.down * 0.955f + lateral * 0.30f).normalized;
        }

        private bool _swingReady;
        private Vector3 _swingAxisL = Vector3.right;
        private Vector3 _swingAxisR = Vector3.right;

        // Assi di flessione reali di anca/ginocchio/gomito, misurati A RUNTIME
        // dal modello (stesso approccio delle spalle). Le ossa Kenney hanno una
        // posa di riposo NON allineata: applicare Euler(x,0,0) sull'asse locale
        // presunto stortura il movimento (gambe/braccia "storte"). Ruotando
        // attorno all'asse laterale vero (transform.right proiettato nel frame
        // osseo) la flessione resta nel piano sagittale per costruzione.
        private bool _legAxisReady;
        private Vector3 _hipAxisL = Vector3.right;
        private Vector3 _hipAxisR = Vector3.right;
        private Vector3 _kneeAxisL = Vector3.right;
        private Vector3 _kneeAxisR = Vector3.right;
        private Vector3 _elbowAxisL = Vector3.right;
        private Vector3 _elbowAxisR = Vector3.right;

        // Calibra gli assi di flessione di anca/ginocchio/gomito a runtime,
        // nello stesso modo delle spalle (asse laterale del modello proiettato
        // nel frame mondo dell'osso). Se l'osso manca o l'accesso fallisce, si
        // resta sugli assi di default (destra) — degrado grazioso, mai crash.
        private void CalibrateLimbAxes()
        {
            Vector3 lateral = transform.right;
            try
            {
                _hipAxisL = LocalFlexAxis(LUpLegPath, lateral);
                _hipAxisR = LocalFlexAxis(RUpLegPath, lateral);
                _kneeAxisL = LocalFlexAxis(LLegPath, lateral);
                _kneeAxisR = LocalFlexAxis(RLegPath, lateral);
                _elbowAxisL = LocalFlexAxis(LForeArmPath, lateral);
                _elbowAxisR = LocalFlexAxis(RForeArmPath, lateral);
                _legAxisReady = true;
            }
            catch (System.Exception ex)
            {
                Debug.LogWarning("[CharacterWalker] calibrazione arti: " + ex);
            }
        }

        // Direzione dell'asse laterale del modello nel frame dell'osso (che
        // include la posa di riposo): mi serve per comporre rest * angAxis.
        private Vector3 LocalFlexAxis(string bonePath, Vector3 worldLateral)
        {
            Transform bone = transform.Find(bonePath);
            if (bone == null) return worldLateral;
            Transform root = bone;
            while (root.parent != null && root != transform)
                root = root.parent;
            // rotazione mondo dell'osso (dalla radice al figlio)
            Quaternion world = bone.rotation;
            // se il walker non sta sulla radice del modello, riallinea comunque
            return Quaternion.Inverse(world) * worldLateral;
        }

        // Delta di flessione di un'anca: AngAxis(angle, asseLaterale) che,
        // composto come rest * delta, oscilla la coscia avanti/indietro senza
        // sbilanciarla lateralmente. Se la calibrazione non e' pronta si
        // restituisce l'identita' (arto fermo) piuttosto che un asse incerto.
        private Quaternion HipFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _hipAxisR : _hipAxisL);
        }
        private Quaternion KneeFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _kneeAxisR : _kneeAxisL);
        }
        private Quaternion ElbowFlex(bool right, float angle)
        {
            if (!_legAxisReady) return Quaternion.AngleAxis(angle, right ? Vector3.right : -Vector3.right);
            return Quaternion.AngleAxis(angle, right ? _elbowAxisR : _elbowAxisL);
        }

        private Quaternion FoldDelta(bool right)
        {
            if (_foldReady) return right ? _foldR : _foldL;
            return Quaternion.Euler(right ? 80f : -80f, 0f, 0f);
        }

        // Rotazione di oscillazione (avanti/indietro) attorno all'asse laterale
        // della spalla, composta DOPO il fold: il braccio resta ai lati.
        private Quaternion SwingDelta(bool right, float angle)
        {
            if (!_swingReady) return Quaternion.identity;
            return Quaternion.AngleAxis(angle,
                right ? _swingAxisR : _swingAxisL);
        }

        // ------------------------------------------------------------------
        // Curve procedurali

        private void BuildProcedural()
        {
            CalibrateFold();
            CalibrateLimbAxes();
            anim = gameObject.GetComponent<Animation>();
            if (anim == null) anim = gameObject.AddComponent<Animation>();
            anim.AddClip(BuildLocomotion("walk", legSwingDeg, kneeBendDeg,
                armSwingDeg, walkCadence, walkBounceM, forwardLeanDeg * 0.4f, 3f), WalkState);
            anim.AddClip(BuildLocomotion("run", legSwingDeg * 1.5f,
                kneeBendDeg * 1.35f, armSwingDeg * 1.5f, runCadence, runBounceM,
                forwardLeanDeg, 8f), RunState);
            anim.AddClip(BuildIdle(), IdleState);
            current = IdleState;
            anim.Play(IdleState);

            // Diagnostica per i PNG in T-pose: se le ossa chiave non sono
            // raggiunte via transform.Find (es. rig humanoid ottimizzato o
            // Animator ancora vivo) le clip procedurali girano "a vuoto" e il
            // modello resta congelato nella posa bind. Si logga SOLO in caso
            // di anomalia, per non inondare la logcat su build con molti NPC.
            if (transform.Find(HipsPath) == null ||
                transform.Find(LUpLegPath) == null)
            {
                Debug.LogWarning("[CharacterWalker] ossa non raggiunte su " +
                    gameObject.name + " hips=" +
                    (transform.Find(HipsPath) != null) + " lLeg=" +
                    (transform.Find(LUpLegPath) != null) + " animator=" +
                    (GetComponentInChildren<Animator>(true) != null));
            }
            ready = true;
        }

        // Costruisce un ciclo di avanzamento (camminata o corsa) con:
        //  - gambe: oscillazione delle cosce (avanti/indietro) + ginocchio
        //    (LeftLeg/RightLeg) che si flette nella fase di recupero, in modo
        //    chiaramente visibile (il ginocchio "esiste").
        //  - braccia: spalle piegate giu' al fianco (fold simmetrico) e
        //    oscillazione attorno all'asse laterale (SwingDelta) + gomiti
        //    (LeftForeArm/RightForeArm) che si piegano di pari passo.
        private AnimationClip BuildLocomotion(string tag, float legSwing,
            float kneeBend, float armSwing, float cadence, float bounce,
            float leanDeg, float thighBiasDeg)
        {
            float dur = 1f / Mathf.Max(0.1f, cadence);
            const int steps = 28;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float sinL = Mathf.Sin(ph);          // gamba sx avanti quando > 0
                float sinR = Mathf.Sin(ph + Mathf.PI);

                // cosce: oscillazione avanti/indietro attorno all'asse laterale
                // calibrato a runtime (niente piu' divaricazione "a papera").
                // thighBias sposta ogni passo piu' in avanti (>=0, stride lungo).
                rot.RotQ(t, LUpLegPath,
                    HipFlex(false, swingSign * (sinL * legSwing + thighBiasDeg)));
                rot.RotQ(t, RUpLegPath,
                    HipFlex(true, swingSign * (sinR * legSwing + thighBiasDeg)));

                // ginocchio: flessione visibile durante il recupero della gamba
                // che risale (fase "indietro+su") -> il piede si stacca dal suolo
                float kneeL = Mathf.Clamp01(-sinL) * kneeBend;
                float kneeR = Mathf.Clamp01(-sinR) * kneeBend;
                rot.RotQ(t, LLegPath, KneeFlex(false, swingSign * kneeL));
                rot.RotQ(t, RLegPath, KneeFlex(true, swingSign * kneeR));

                // braccia: fold al fianco + oscillazione attorno all'asse
                // laterale in controfase alla gamba dello stesso lato; gomito
                // piegato leggermente durante l'oscillazione. Ordine di
                // composizione: prima il fold (braccio ai lati), poi lo swing.
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, swingSign * (sinL * armSwing)) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, swingSign * (sinR * armSwing)) * FoldDelta(true));
                float elbowL = Mathf.Clamp01(Mathf.Abs(sinL)) * elbowBendDeg;
                float elbowR = Mathf.Clamp01(Mathf.Abs(sinR)) * elbowBendDeg;
                rot.RotQ(t, LForeArmPath, ElbowFlex(false, swingSign * elbowL));
                rot.RotQ(t, RForeArmPath, ElbowFlex(true, swingSign * elbowR));

                // busto: lieve torsione e dondolio laterale + inclinazione
                // in avanti (lean) che rende il passo lungo e "tuffato"
                rot.Rot(t, SpinePath, leanDeg, sinR * 6f, sinR * 3f);
                rot.Rot(t, UpChestPath, leanDeg * 0.45f, sinL * 5f, 0f);
                // rimbalzo del bacino, due colpi per ciclo
                pos.Pos(t, HipsPath, hipY + Mathf.Sin(ph * 2f) * bounce);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        private AnimationClip BuildIdle()
        {
            const float dur = 3.4f;
            const int steps = 20;
            Transform hips = transform.Find(HipsPath);
            float hipY = hips != null ? hips.localPosition.y : 0f;

            RotBaker rot = new RotBaker(BoneRest);
            PosBaker pos = new PosBaker();
            for (int i = 0; i <= steps; i++)
            {
                float t = dur * i / steps;
                float ph = t / dur * Mathf.PI * 2f;
                float breathe = Mathf.Sin(ph);
                rot.Rot(t, SpinePath, 1.2f * breathe, 0f, 0f);
                // spalle piegate verso il basso (fold calibrato): braccia quasi
                // parallele al corpo, simmetriche
                rot.RotQ(t, LShoulderPath,
                    SwingDelta(false, 3f * breathe) * FoldDelta(false));
                rot.RotQ(t, RShoulderPath,
                    SwingDelta(true, 3f * breathe) * FoldDelta(true));
                pos.Pos(t, HipsPath, hipY + breathe * 0.004f);
            }
            AnimationClip clip = new AnimationClip { wrapMode = WrapMode.Loop, legacy = true };
            rot.Flush(clip);
            pos.Flush(clip);
            return clip;
        }

        // Posa di riposo locale dell'osso (null se assente su questo rig):
        // serve al RotBaker per comporre rotazioni relative invece che assolute.
        private Quaternion? BoneRest(string path)
        {
            Transform t = transform.Find(path);
            return t != null ? (Quaternion?)t.localRotation : null;
        }

        // Accumula chiavi (tempo, quat) per percorso osseo e le scrive come
        // quattro curve m_LocalRotation.{x,y,z,w} sulla clip.
        private sealed class RotBaker
        {
            private readonly Dictionary<string, List<float[]>> keys = new Dictionary<string, List<float[]>>();
            private readonly System.Func<string, Quaternion?> restLookup;

            // Le ossa del rig hanno una posa di riposo NON identita': le curve
            // devono essere RELATIVE (rest * delta). Sovrascriverle con Eulero
            // assoluto distrugge il bind pose (personaggio contorto, gambe al
            // collo). Se l'osso manca su questo rig, niente curve per esso.
            public RotBaker(System.Func<string, Quaternion?> restLookup)
            {
                this.restLookup = restLookup;
            }

            public void Rot(float time, string path, float x, float y, float z)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * Quaternion.Euler(x, y, z);
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            // Come Rot ma con un delta gia' calcolato (per il fold calibrato
            // delle braccia, misurato sulle ossa a runtime).
            public void RotQ(float time, string path, Quaternion delta)
            {
                Quaternion? rest = restLookup != null ? restLookup(path) : null;
                if (rest == null) return;
                Quaternion q = rest.Value * delta;
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, q.x, q.y, q.z, q.w });
            }

            public void Flush(AnimationClip clip)
            {
                // SetCurve e' legale solo su clip legacy: senza questa guardia
                // Unity sputa l'errore rosso a ogni creazione NPC/player.
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    List<float[]> list = kv.Value;
                    const string comps = "xyzw";
                    for (int c = 0; c < 4; c++)
                    {
                        AnimationCurve curve = new AnimationCurve();
                        foreach (float[] k in list)
                            curve.AddKey(k[0], k[1 + c]);
                        clip.SetCurve(kv.Key, typeof(Transform),
                            "m_LocalRotation." + comps[c], curve);
                    }
                }
            }
        }

        // Come RotBaker ma per la sola coordinata y locale.
        private sealed class PosBaker
        {
            private readonly Dictionary<string, List<float[]>> keys = new Dictionary<string, List<float[]>>();

            public void Pos(float time, string path, float localY)
            {
                List<float[]> list;
                if (!keys.TryGetValue(path, out list))
                {
                    list = new List<float[]>();
                    keys[path] = list;
                }
                list.Add(new[] { time, localY });
            }

            public void Flush(AnimationClip clip)
            {
                // SetCurve e' legale solo su clip legacy: senza questa guardia
                // Unity sputa l'errore rosso a ogni creazione NPC/player.
                if (clip == null || !clip.legacy) return;
                foreach (KeyValuePair<string, List<float[]>> kv in keys)
                {
                    AnimationCurve curve = new AnimationCurve();
                    foreach (float[] k in kv.Value)
                        curve.AddKey(k[0], k[1]);
                    clip.SetCurve(kv.Key, typeof(Transform), "m_LocalPosition.y", curve);
                }
            }
        }
    }
}
