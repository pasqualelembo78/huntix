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
        private const string LArmPath = UpChestPath + "/LeftShoulder/LeftArm";
        private const string RArmPath = UpChestPath + "/RightShoulder/RightArm";

        [Header("Regolazione procedurale")]
        public float legSwingDeg = 30f;
        public float kneeBendDeg = 30f;
        public float armSwingDeg = 24f;
        // Se sul device il camminatore sembra camminare all'indietro, -1 qui.
        public float swingSign = 1f;

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
            Transform t = smr.transform;
            while (t.parent != null && t.parent != ownerRoot.transform)
                t = t.parent;
            if (t == ownerRoot.transform) return null;
            return t.gameObject.AddComponent<CharacterWalker>();
        }

        // Su device il probe delle clip autoriali non passa mai (rig Kenney
        // non vincolato dalle clip FBX): si va diretti alle curve procedurali,
        // senza spam di log ne' attesa del VerifyAuthored.
        private const bool USE_AUTHORED_CLIPS = false;

        private void Start()
        {
            // Un Animator senza controller bloccherebbe la riproduzione legacy.
            Animator stale = GetComponentInChildren<Animator>();
            if (stale != null && stale.runtimeAnimatorController == null)
                Destroy(stale);

            if (USE_AUTHORED_CLIPS && TryAuthoredClips())
            {
                current = IdleState;
                StartCoroutine(VerifyAuthored());
            }
            else
            {
                BuildProcedural();
            }
        }

        public void SetSpeed(float metersPerSecond)
        {
            speed = metersPerSecond;
        }

        private void Update()
        {
            if (anim == null) return;
            bool moving = speed > 0.15f;
            string want = moving ? RunState : IdleState;
            if (want != current)
            {
                current = want;
                anim.CrossFade(want, 0.18f);
            }
            // Il ciclo di corsa del rig e' calibrato su ~5,5 m/s.
            AnimationState run = anim[RunState];
            if (run != null) run.speed = Mathf.Clamp(speed / 5.5f, 0.7f, 1.9f);
        }

        // ------------------------------------------------------------------
        // Stadio 1: clip autoriali da Resources

        private bool TryAuthoredClips()
        {
            AnimationClip idle = LoadLoopClip("Characters/idle");
            AnimationClip run = LoadLoopClip("Characters/run");
            if (idle == null || run == null)
            {
                if (idle != null || run != null)
                    Debug.LogWarning("CharacterWalker: clip parziali in Characters/");
                return false;
            }
            anim = gameObject.AddComponent<Animation>();
            anim.AddClip(idle, IdleState);
            anim.AddClip(run, RunState);
            anim.Play(IdleState);
            return true;
        }

        private static AnimationClip LoadLoopClip(string resourcePath)
        {
            Object[] found = Resources.LoadAll(resourcePath, typeof(AnimationClip));
            if (found == null || found.Length == 0) return null;
            AnimationClip clip = found[0] as AnimationClip;
            if (clip == null) return null;
            try { clip.legacy = true; }
            catch (System.Exception) { return null; }
            // Se il flag non resta applicato (clip importata read-only) la
            // clip genererebbe "SetCurve on non Legacy AnimationClips":
            // ricadiamo sulle curve procedurali.
            if (!clip.legacy) return null;
            clip.wrapMode = WrapMode.Loop;
            return clip;
        }

        private IEnumerator VerifyAuthored()
        {
            yield return new WaitForSeconds(0.5f);

            // Il modello puo' essere stato distrutto/scambiato durante l'attesa
            // (despawn chunk, cambio skin): su player IL2CPP l'accesso a
            // transform di un componente morto esplode come NullReference
            // secca. Check esplicito + try/catch: niente piu' errori rossi,
            // e se salta altro lo vediamo nei log con il punto esatto.
            if (this == null || !gameObject) yield break;

            try
            {
                Transform probe = transform.Find(LUpLegPath);
                if (probe == null)
                {
                    FallBackToProcedural();
                    yield break;
                }
                // Se nessun asse e' mai uscito dalla posa base, le clip non stanno
                // animando niente (percorsi interni non compatibili): ricado.
                Vector3 e = probe.localEulerAngles;
                if (Mathf.Abs(AngDiff(e.x, baseX)) < 0.3f &&
                    Mathf.Abs(AngDiff(e.y, baseY)) < 0.3f &&
                    Mathf.Abs(AngDiff(e.z, baseZ)) < 0.3f)
                    FallBackToProcedural();
                else
                    ready = true;
            }
            catch (System.Exception ex)
            {
                // Diagnostica: il messaggio completo finisce in huntix-log.txt
                Debug.LogWarning("[CharacterWalker] VerifyAuthored fallita: " + ex);
                try { FallBackToProcedural(); } catch (System.Exception) { }
            }
        }

        private float baseX, baseY, baseZ;

        private bool _proceduralBuilt;

        private void FallBackToProcedural()
        {
            if (_proceduralBuilt) return;
            _proceduralBuilt = true;
            Debug.Log("CharacterWalker: clip autoriali non vincolate, uso curve procedurali");
            if (anim != null) DestroyImmediate(anim);
            anim = null;
            BuildProcedural();
        }

        private static float AngDiff(float a, float b)
        {
            float d = a - b;
            while (d > 180f) d -= 360f;
            while (d < -180f) d += 360f;
            return d;
        }

        // ------------------------------------------------------------------
        // Stadio 2: curve procedurali

        private void BuildProcedural()
        {
            _proceduralBuilt = true;
            Transform probe = transform.Find(LUpLegPath);
            if (probe != null)
            {
                baseX = probe.localEulerAngles.x;
                baseY = probe.localEulerAngles.y;
                baseZ = probe.localEulerAngles.z;
            }
            anim = gameObject.GetComponent<Animation>();
            if (anim == null) anim = gameObject.AddComponent<Animation>();
            anim.AddClip(BuildWalk(), RunState);
            anim.AddClip(BuildIdle(), IdleState);
            current = IdleState;
            anim.Play(IdleState);
            ready = true;
        }

        private AnimationClip BuildWalk()
        {
            const float dur = 0.85f;
            const int steps = 26;
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
                rot.Rot(t, LUpLegPath, swingSign * (sinL * legSwingDeg - 3f), 0f, 0f);
                rot.Rot(t, RUpLegPath, swingSign * (sinR * legSwingDeg - 3f), 0f, 0f);
                // ginocchio flesso solo nel recupero (gamba che rientra sotto
                // il bacino): col picco sfasato la gamba si piegava gia' a
                // gamba avanti-estesa e il passo sembrava un salto (pogo)
                float kneeL = Mathf.Max(0f, Mathf.Cos(ph));
                float kneeR = Mathf.Max(0f, -Mathf.Cos(ph));
                rot.Rot(t, LLegPath, swingSign * kneeL * kneeBendDeg, 0f, 0f);
                rot.Rot(t, RLegPath, swingSign * kneeR * kneeBendDeg, 0f, 0f);
                // braccia in controfase rispetto alla gamba dello stesso lato
                rot.Rot(t, LArmPath, -sinL * armSwingDeg, 0f, 2f);
                rot.Rot(t, RArmPath, -sinR * armSwingDeg, 0f, -2f);
                // busto: lieve torsione e dondolio laterale
                rot.Rot(t, SpinePath, 3f, sinR * 6f, sinR * 3f);
                rot.Rot(t, UpChestPath, 2f, sinL * 5f, 0f);
                // rimbalzo del bacino, due colpi per ciclo
                pos.Pos(t, HipsPath, hipY + Mathf.Sin(ph * 2f) * 0.008f);
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
                rot.Rot(t, LArmPath, 2f * breathe, 0f, 2f);
                rot.Rot(t, RArmPath, 2f * breathe, 0f, -2f);
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
