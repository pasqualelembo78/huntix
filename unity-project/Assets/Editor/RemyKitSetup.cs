using System.Collections.Generic;
using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;
using Object = UnityEngine.Object;

namespace City.Editor
{
    /// <summary>
    /// Setup professionale del kit player "Remy" (Mixamo) nel progetto:
    ///
    ///  1. Impostazioni import FBX (Remy = Humanoid + materiali; Walking/Run/Jump =
    ///     Humanoid, sole clip, root motion bloccato, loop corretti).
    ///  2. Estrazione delle texture embedded (Diffuse/Normal/AO/Gloss/Opacity 2048)
    ///     dal Remy.fbx come asset indipendenti, compressione mobile.
    ///  3. Materiale URP/Lit del giocatore (Base + Normal + AO).
    ///  4. Controller "PlayerLocomotion" (BlendTree Idle→Walk→Run su Speed) con
    ///     stato Jump aggiuntivo pilotato dal Bool "IsGrounded".
    ///  5. Prefab "Resources/PlayerHero.prefab" (modello + Animator + materiali),
    ///     usato sia dalla citta' (PlayerController) sia dall'afterlife (MiaCityAvatar).
    ///
    /// Eseguibile dal menu Tools/Huntix/Remy/Setup All o in batchmode via
    /// -executeMethod City.Editor.RemyKitSetup.BatchSetup . Il build hook
    /// PreBuildMixamoSetup chiama EnsureAll() ad ogni build.
    /// </summary>
    public static class RemyKitSetup
    {
        private const string ArtDir = "Assets/Art/Mixamo";
        private const string TexDir = ArtDir + "/Textures";
        private const string MatDir = ArtDir + "/Materials";
        private const string CtrlPath = "Assets/Resources/Mixamo/PlayerLocomotion.controller";
        private const string PrefabPath = "Assets/Resources/PlayerHero.prefab";

        private static readonly string[] HeroFiles =
        {
            "Remy.fbx",
            "Idle.fbx",
            "Walking.fbx",
            "Run.fbx",
            "Jump.fbx",
        };

        public static void BatchSetup()
        {
            EnsureAll();
        }

        /// <summary>Ri-applica le impostazioni di import (incluso il fix del
        /// root height "heightFromFeet") ai soli FBX animazioni del player
        /// senza rifare textures/materiali/controller/prefab.</summary>
        public static void ApplyClipHeightFix()
        {
            foreach (string file in HeroFiles)
            {
                string path = ArtDir + "/" + file;
                if (!File.Exists(path)) continue;
                AssetImporter importer = AssetImporter.GetAtPath(path);
                ConfigureImport(path);
                // Forza la reimportazione: importer e' gia' quello usato
                // sopra, la SaveAndReimport scatta dentro ConfigureImport.
                if (importer == null) continue;
                importer.SaveAndReimport();
                Debug.Log("[RemyKit] Reimportato fix height: " + path);
            }
            AssetDatabase.SaveAssets();
        }

        /// <summary>Ripara le clip di animazione dei FBX del player che si
        /// erano salvate vuote (lastFrame=0): clear dell'override clipAnimations,
        /// reimport automatico del take, poi riapplicale configurazione.
        /// La clip vuota nasce quando ConfigureImport gira col
        /// defaultClipAnimations ancora vuoto (primo import) e scrive
        /// importer.clipAnimations=[clip senza frame], congelando il body su
        /// una bind-pose sprofondata (~1.5 m) durante walk/run.</summary>
        public static void RepairHeroAnimClips()
        {
            foreach (string file in HeroFiles)
            {
                string path = ArtDir + "/" + file;
                if (!File.Exists(path)) continue;
                if (file == "Remy.fbx") continue; // base: nessuna clip

                var importer = AssetImporter.GetAtPath(path) as ModelImporter;
                if (importer == null) continue;

                // Fase 1: libera l'override e rileva il take automaticamente.
                if (importer.clipAnimations != null && importer.clipAnimations.Length > 0)
                {
                    importer.clipAnimations = new ModelImporterClipAnimation[0];
                    importer.SaveAndReimport();
                    Debug.Log("[RemyKit] [" + file + "] clipAnimations resettate, take:");
                    foreach (var c in importer.defaultClipAnimations)
                        Debug.Log("[RemyKit]   take=" + c.name +
                            " frames=" + c.firstFrame + ".." + c.lastFrame);
                }

                // Fase 2: ora defaultClipAnimations ha i frame veri: applica
                // la configurazione (loop/root/heightFromFeet) e reimporta.
                ConfigureImport(path);
                importer.SaveAndReimport();
                Debug.Log("[RemyKit] [" + file + "] riapplicata configurazione.");
            }
            AssetDatabase.SaveAssets();
        }

        public static void EnsureAll()
        {
            ConfigureModelImports();
            ExtractRemyTextures();
            CreateRemyMaterial();
            CreateController();
            CreatePlayerHeroPrefab();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
        }

        /// <summary>Ri-lega i riferimenti delle clip Mixamo dentro il controller
        /// senza rigenerarlo (il controller contiene stati UAL2 che non vanno
        /// creati da qui). Dopo un reimport i clip dei .fbx cambiano fileID;
        /// i vecchi riferimenti nel blend tree restano appesi e l'Animator
        /// gioca clip vuote/NULL (il player congela nella bind-pose).</summary>
        public static void RebindPlayerController()
        {
            var ctrl = AssetDatabase.LoadAssetAtPath<AnimatorController>(CtrlPath);
            if (ctrl == null)
            {
                Debug.LogWarning("[RemyKit] Controller non trovato: " + CtrlPath);
                return;
            }

            // Carica le clip Mixamo aggiornate dai .fbx.
            AnimationClip idle = null, walk = null, run = null, jump = null;
            string[] guids = AssetDatabase.FindAssets("t:AnimationClip", new[] { ArtDir });
            foreach (string guid in guids)
            {
                var clip = AssetDatabase.LoadAssetAtPath<AnimationClip>(
                    AssetDatabase.GUIDToAssetPath(guid));
                if (clip == null) continue;
                string n = clip.name.ToLowerInvariant();
                if (n.Contains("idle") && idle == null) idle = clip;
                else if (n.Contains("walk") && walk == null) walk = clip;
                else if ((n.Contains("run") || n.Contains("running")) && run == null &&
                    !n.Contains("forward") && !n.Contains("backward") && !n.Contains("strafe"))
                    run = clip;
                else if (n.Contains("jump") && jump == null) jump = clip;
            }
            if (idle == null && walk != null) idle = walk;

            int rebound = 0;
            AnimatorStateMachine sm = ctrl.layers[0].stateMachine;
            foreach (var state in sm.states)
                rebound += RebindState(state.state, state.state.motion, idle, walk, run, jump);
            foreach (var sub in sm.stateMachines)
                foreach (var state in sub.stateMachine.states)
                    rebound += RebindState(state.state, state.state.motion, idle, walk, run, jump);

            EditorUtility.SetDirty(ctrl);
            AssetDatabase.SaveAssets();
            Debug.Log("[RemyKit] Rebound controller (" + rebound +
                " motion): idle=" + (idle != null ? idle.name : "null") +
                " walk=" + (walk != null ? walk.name : "null") +
                " run=" + (run != null ? run.name : "null") +
                " jump=" + (jump != null ? jump.name : "null"));
        }

        /// <summary>Va benissimo anche con motion NULL (fileID appesi dopo il
        /// reimport): per i blend tree si legano i child per soglia
        /// (0=idle, 3.2=walk, 7.5=run), per gli stati autonomi per nome della
        /// vecchia clip (recuperata dal nome rispetto alle clip Mixamo note).</summary>
        private static int RebindState(AnimatorState state, Motion motion,
            AnimationClip idle, AnimationClip walk, AnimationClip run, AnimationClip jump)
        {
            if (motion is BlendTree)
            {
                int n = 0;
                var bt = motion as BlendTree;
                var children = bt.children;
                for (int i = 0; i < children.Length; i++)
                {
                    var c = children[i];
                    // Lega per soglia quando e' un LocomotionBlend (0/3.2/7.5).
                    string name = c.motion != null ? c.motion.name.ToLowerInvariant() : "";
                    float th = bt.blendType == BlendTreeType.Simple1D ? c.threshold : 0f;
                    AnimationClip repl = null;
                    if (bt.blendType == BlendTreeType.Simple1D)
                    {
                        if (Mathf.Abs(th) < 0.1f) repl = idle;
                        else if (Mathf.Abs(th - 3.2f) < 0.1f) repl = walk;
                        else if (Mathf.Abs(th - 7.5f) < 0.1f) repl = run;
                    }
                    if (repl == null)
                    {
                        if (name.Contains("idle")) repl = idle;
                        else if (name.Contains("walk")) repl = walk;
                        else if (name.Contains("run")) repl = run;
                        else if (name.Contains("jump")) repl = jump;
                    }
                    if (repl != null)
                    {
                        children[i].motion = repl;
                        n++;
                    }
                }
                bt.children = children;
                return n;
            }
            AnimationClip mc = motion as AnimationClip;
            if (mc != null)
            {
                string name = mc.name.ToLowerInvariant();
                AnimationClip repl = null;
                if (name.Contains("idle")) repl = idle;
                else if (name.Contains("walk")) repl = walk;
                else if (name.Contains("run")) repl = run;
                else if (name.Contains("jump")) repl = jump;
                if (repl != null && !Object.ReferenceEquals(mc, repl))
                {
                    state.motion = repl;
                    return 1;
                }
            }
            return 0;
        }

        // ═══════════════════════════════════════════════════════════════
        // 1. Import FBX
        // ═══════════════════════════════════════════════════════════════

        private static void ConfigureModelImports()
        {
            foreach (string file in HeroFiles)
            {
                string path = ArtDir + "/" + file;
                if (!File.Exists(path)) continue;
                ConfigureImport(path);
            }

            // GLB placeholder Mixamo (Idle/Walk/Run/RunForward): humanoid + loop.
            foreach (string guid in AssetDatabase.FindAssets("t:Model", new[] { ArtDir }))
            {
                string path = AssetDatabase.GUIDToAssetPath(guid);
                if (!path.EndsWith(".glb")) continue;
                ConfigureImport(path);
            }
        }

        /// <summary>Wrapper pubblico per l'AssetPostprocessor.</summary>
        public static void ConfigureImportPublic(string path)
        {
            ConfigureImport(path);
        }

        private static void ConfigureImport(string path)
        {
            var importer = AssetImporter.GetAtPath(path) as ModelImporter;
            if (importer == null) return;
            string name = Path.GetFileNameWithoutExtension(path);
            bool isBase = name == "Remy";

            bool dirty = false;
            if (importer.animationType != ModelImporterAnimationType.Human)
            {
                importer.animationType = ModelImporterAnimationType.Human;
                importer.avatarSetup = ModelImporterAvatarSetup.CreateFromThisModel;
                dirty = true;
            }
            if (name == "Remy" && importer.importAnimation)
            {
                importer.importAnimation = false; // la base non ha clip utili
                dirty = true;
            }
            // Materiali: NO per clip/base (li creiamo noi a mano da Textures/),
            // cosi' gli FBX delle animazioni non danno materiali grigi inutilizzati.
            if (importer.materialImportMode != ModelImporterMaterialImportMode.None)
            {
                importer.materialImportMode = ModelImporterMaterialImportMode.None;
                importer.materialLocation = ModelImporterMaterialLocation.External;
                dirty = true;
            }
            // NO "Optimize Game Objects": le ossa del rig devono restare scrivibili,
            // sia per l'IK procedurale sia per il fallback anti-T-pose. Con il rig
            // ottimizzato (hasTransformHierarchy=false) le ossa sono read-only: se
            // l'Animator non riesce a produrre Idle resta la bind pose a T ineluttabile.
            if (importer.optimizeGameObjects)
            {
                importer.optimizeGameObjects = false;
                dirty = true;
            }
            if (Mathf.Abs(importer.globalScale - 1f) > 0.001f)
            {
                importer.globalScale = 1f;
                dirty = true;
            }

            // Clip: nome + loop (walk/run loop, jump one-shot) + root motion bloccato.
            // ATTENZIONE: defaultClipAnimations e' vuoto durante OnPreprocessModel
            // (postprocessor gira a ogni import), quindi se abbiamo gia' clip
            // salvate preferiamo importer.clipAnimations (meta persistito) per
            // NON perdere i frame del take. Creiamo una clip placeholder solo se
            // proprio non c'e' niente (import iniziale senza take rilevato).
            ModelImporterClipAnimation[] src = importer.clipAnimations;
            if (src == null || src.Length == 0)
                src = importer.defaultClipAnimations ?? new ModelImporterClipAnimation[0];
            {
                bool clipsDirty = false;
                // MAI creare clip placeholder senza frame: se il take non e'
                // ancora rilevato (defaultClipAnimations vuoto durante
                // OnPreprocessModel) non toccare clipAnimations, cosi' Unity
                // importa il take automaticamente con i frame reali; il pass
                // successivo li rinomina/riapplica (self-healing).
                var list = src.Length == 0 ? new List<ModelImporterClipAnimation>() : new List<ModelImporterClipAnimation>(src);
                foreach (var c in list)
                    Debug.Log("[RemyKit] srcclip " + path + " -> " + c.name +
                        " frames=" + c.firstFrame + ".." + c.lastFrame);
                for (int i = 0; i < list.Count; i++)
                {
                    var c = list[i];
                    c.name = i == 0 && !isBase
                        ? SanitizeClipName(name)
                        : SanitizeClipName(c.name);
                    c.lockRootRotation = true;
                    c.lockRootHeightY = true;
                    c.lockRootPositionXZ = true;
                    // Normalizza il root height a partire dai piedi: senza
                    // questo, ogni clip Mixamo porta la quota root "baked"
                    // del suo modello (Idle a feet=+0.25, Walking/Run a
                    // feet=-1.5): il blend idle->walk faceva sprofondare il
                    // corpo di ~1.75 m (fino alle spalle) e il Foot IK
                    // (clamp +-0.3) non poteva compensare.
                    c.heightFromFeet = true;
                    c.keepOriginalPositionY = false;
                    c.loopTime = !(name == "Jump" || name.Contains("jump")) || c.name.Contains("land");
                    c.loopPose = c.loopTime;
                    c.cycleOffset = 0f;
                    list[i] = c;
                    clipsDirty = true;
                }
                if (clipsDirty)
                {
                    importer.clipAnimations = list.ToArray();
                    dirty = true;
                }
            }

            if (dirty)
            {
                importer.SaveAndReimport();
                Debug.Log("[RemyKit] Configurato: " + path);
            }
        }

        private static string SanitizeClipName(string raw)
        {
            string n = raw;
            if (n.IndexOf('.') >= 0) n = n.Substring(0, n.IndexOf('.'));
            return n;
        }

        // ═══════════════════════════════════════════════════════════════
        // 2. Texture embedded (PNG dentro Remy.fbx)
        // ═══════════════════════════════════════════════════════════════

        private static void ExtractRemyTextures()
        {
            string src = ArtDir + "/Remy.fbx";
            if (!File.Exists(src)) return;
            byte[] data = File.ReadAllBytes(src);

            // Seleziona i blob PNG 2048 (la variante piu' grande di ogni mappa),
            // in ordine di apparizione (Diffuse, AO/Gloss, ... Normal, Opacity).
            var blobs = ExtractPngBlobs(data);
            var big = blobs.FindAll(b => IsBig(b));
            if (big.Count < 3)
            {
                Debug.LogWarning("[RemyKit] blobs 2048 insufficienti: " + big.Count);
                return;
            }

            if (!AssetDatabase.IsValidFolder(TexDir))
                AssetDatabase.CreateFolder(ArtDir, "Textures");

            // Ordine atteso nel FBX Mixamo: [0]=Diffuse, [1], [2]=Normal?, [3].. ,
            // ultimo/before=Normal (blu ~1), penultimi=Oppure Gloss/AO.
            // Classificazione dati (misurata): Diffuse forte caldo, Normal = blue‑channel ~1,
            // AO = silhouette scura su sfondo chiaro, Gloss = grigio "a macchie".
            int idxDiff = FindIndex(big, IsDiffuse);
            int idxNorm = FindIndex(big, IsNormalMap);
            int idxAO   = FindIndex(big, IsOcclusion);
            if (idxDiff < 0) idxDiff = 0;
            if (idxNorm < 0) idxNorm = big.Count - 2;
            if (idxAO < 0 || idxAO == idxDiff || idxAO == idxNorm)
                idxAO = PickFirstDifferent(big, idxDiff, idxNorm);

            SavePng(big[idxDiff], TexDir + "/Remy_BaseAlbedo.png", true);
            SavePng(big[idxNorm], TexDir + "/Remy_Normal.png", false);
            SavePng(big[idxAO],   TexDir + "/Remy_AmbientOcclusion.png", false);
            Debug.Log("[RemyKit] Texture estratte (diff=" + idxDiff +
                " norm=" + idxNorm + " ao=" + idxAO + ")");
        }

        private static List<byte[]> ExtractPngBlobs(byte[] data)
        {
            var list = new List<byte[]>();
            int i = 0;
            while (true)
            {
                int p = IndexOf(data, PngMagic, i);
                if (p < 0) break;
                int pos = p + 8;
                int end = data.Length;
                while (pos + 8 <= data.Length)
                {
                    int len = (data[pos] << 24) | (data[pos + 1] << 16) |
                              (data[pos + 2] << 8) | data[pos + 3];
                    if (data[pos + 4] == 'I' && data[pos + 5] == 'E' &&
                        data[pos + 6] == 'N' && data[pos + 7] == 'D')
                    {
                        end = pos + 12 + len;
                        break;
                    }
                    pos += 12 + len;
                }
                var blob = new byte[end - p];
                System.Array.Copy(data, p, blob, 0, blob.Length);
                list.Add(blob);
                i = end;
            }
            return list;
        }

        private static readonly byte[] PngMagic = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

        private static int IndexOf(byte[] hay, byte[] needle, int from)
        {
            for (int i = from; i <= hay.Length - needle.Length; i++)
            {
                bool ok = true;
                for (int j = 0; j < needle.Length; j++)
                    if (hay[i + j] != needle[j]) { ok = false; break; }
                if (ok) return i;
            }
            return -1;
        }

        private static bool IsBig(byte[] b)
        {
            if (b.Length < 24) return false;
            int w = (b[16] << 24) | (b[17] << 16) | (b[18] << 8) | b[19];
            return w >= 2048;
        }

        private static bool IsDiffuse(byte[] b)
        {
            // Sample medio del canale rosso: albedo caldo/medio; mappe normali
            // hanno un forte discostamento canale blu; AO/Gloss sono grigi con
            // R~=B. Qui cerchiamo il blob a distribuzione R>B e non piatto.
            float r = MeanChannel(b, 0), bch = MeanChannel(b, 2);
            return r > bch + 0.04f;
        }

        private static bool IsNormalMap(byte[] b)
        {
            return MeanChannel(b, 2) > 0.90f; // canale blu ~1 nelle normal map
        }

        private static bool IsOcclusion(byte[] b)
        {
            return MeanChannel(b, 0) < 0.55f && MeanChannel(b, 2) < 0.6f;
        }

        private static float MeanChannel(byte[] png, int channel)
        {
            // Decodifica minima: leva su Texture2D LoadImage per leggere i pixel.
            var tex = new Texture2D(2, 2, TextureFormat.RGBA32, false);
            if (!tex.LoadImage(png[0] == 0 ? png : png)) { Object.DestroyImmediate(tex); return -1f; }
            Color[] px = tex.GetPixels();
            float sum = 0f;
            for (int i = 0; i < px.Length; i++)
                sum += channel == 0 ? px[i].r : channel == 2 ? px[i].b : px[i].g;
            float mean = px.Length > 0 ? sum / px.Length : 0f;
            Object.DestroyImmediate(tex);
            return mean;
        }

        private static int PickFirstDifferent(List<byte[]> list, int a, int c)
        {
            for (int i = 0; i < list.Count; i++)
                if (i != a && i != c) return i;
            return c;
        }

        private static int FindIndex(List<byte[]> list, System.Func<byte[], bool> pred)
        {
            for (int i = 0; i < list.Count; i++)
                if (pred(list[i])) return i;
            return -1;
        }

        private static void SavePng(byte[] png, string path, bool sRGB)
        {
            File.WriteAllBytes(path, png);
            AssetDatabase.ImportAsset(path);
            var imp = AssetImporter.GetAtPath(path) as TextureImporter;
            if (imp == null) return;
            imp.textureType = TextureImporterType.Default;
            imp.sRGBTexture = sRGB;
            imp.maxTextureSize = 1024;
            imp.alphaIsTransparency = false;
            imp.mipmapEnabled = true;
            imp.anisoLevel = 4;
            imp.textureCompression = TextureImporterCompression.Compressed;
            imp.SaveAndReimport();
        }

        // ═══════════════════════════════════════════════════════════════
        // 3. Materiale URP/Lit del giocatore
        // ═══════════════════════════════════════════════════════════════

        private static void CreateRemyMaterial()
        {
            string basePath = TexDir + "/Remy_BaseAlbedo.png";
            string normPath = TexDir + "/Remy_Normal.png";
            string aoPath = TexDir + "/Remy_AmbientOcclusion.png";
            if (!File.Exists(basePath)) return;

            var shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null)
            {
                Debug.LogWarning("[RemyKit] Shader URP/Lit mancante, materiale saltato.");
                return;
            }

            if (!AssetDatabase.IsValidFolder(MatDir))
                AssetDatabase.CreateFolder(ArtDir, "Materials");

            string matPath = MatDir + "/PlayerHero.mat";
            Material mat = AssetDatabase.LoadAssetAtPath<Material>(matPath);
            if (mat == null) mat = new Material(shader);

            mat.SetTexture("_BaseMap", AssetDatabase.LoadAssetAtPath<Texture2D>(basePath));
            mat.SetTexture("_BumpMap", AssetDatabase.LoadAssetAtPath<Texture2D>(normPath));
            mat.SetTexture("_OcclusionMap", AssetDatabase.LoadAssetAtPath<Texture2D>(aoPath));
            mat.SetFloat("_BumpScale", 1f);
            mat.SetFloat("_Smoothness", 0.4f);
            mat.SetFloat("_Metallic", 0f);
            mat.SetFloat("_OcclusionStrength", 1f);

            if (string.IsNullOrEmpty(AssetDatabase.GetAssetPath(mat)))
                AssetDatabase.CreateAsset(mat, matPath);
            EditorUtility.SetDirty(mat);
            AssetDatabase.SaveAssets();
            Debug.Log("[RemyKit] Materiale URP: " + matPath);
        }

        // ═══════════════════════════════════════════════════════════════
        // 4. Controller PlayerLocomotion (BlendTree + Jump)
        // ═══════════════════════════════════════════════════════════════

        private static string IdleAnimPath = ArtDir + "/Animations/Idle_Static.anim";

        // Cuce una clip statica che tiene fissa la posa del frame 0 della
        // sorgente (usata come idle quando il personaggio non ha una vera
        // clip idle). Salva in asset cosi' il riferimento del controller
        // e' persistente.
        private static AnimationClip CreateStaticIdle(AnimationClip source)
        {
            if (source == null) return null;
            AssetDatabase.SaveAssets();
            var clip = AssetDatabase.LoadAssetAtPath<AnimationClip>(IdleAnimPath);
            if (clip != null) return clip;

            clip = new AnimationClip();
            clip.name = "Idle_Static";
            clip.frameRate = source.frameRate > 0f ? source.frameRate : 30f;
            clip.SetCurve("", typeof(SpriteRenderer), "m_Enabled", AnimationCurve.Constant(0f, 0.1f, 0f));

            foreach (var binding in AnimationUtility.GetCurveBindings(source))
            {
                var curve = AnimationUtility.GetEditorCurve(source, binding);
                if (curve == null || curve.length < 1) continue;
                float v = curve.Evaluate(0f);
                var keys = new Keyframe[2];
                keys[0] = new Keyframe(0f, v);
                keys[1] = new Keyframe(0.1f, v);
                clip.SetCurve(binding.path, binding.type, binding.propertyName,
                    new AnimationCurve(keys));
            }
            foreach (var binding in AnimationUtility.GetObjectReferenceCurveBindings(source))
            {
                var srcKeys = AnimationUtility.GetObjectReferenceCurve(source, binding);
                if (srcKeys == null || srcKeys.Length < 1) continue;
                var keys = new ObjectReferenceKeyframe[2];
                keys[0] = new ObjectReferenceKeyframe { time = 0f, value = srcKeys[0].value };
                keys[1] = new ObjectReferenceKeyframe { time = 0.1f, value = srcKeys[0].value };
                AnimationUtility.SetObjectReferenceCurve(clip, binding, keys);
            }

            if (!AssetDatabase.IsValidFolder(ArtDir + "/Animations"))
                AssetDatabase.CreateFolder(ArtDir, "Animations");
            AssetDatabase.CreateAsset(clip, IdleAnimPath);
            AssetDatabase.SaveAssets();
            return clip;
        }

        private static void CreateController()
        {
            if (!AssetDatabase.IsValidFolder("Assets/Resources/Mixamo"))
            {
                if (!AssetDatabase.IsValidFolder("Assets/Resources"))
                    AssetDatabase.CreateFolder("Assets", "Resources");
                if (!AssetDatabase.IsValidFolder("Assets/Resources/Mixamo"))
                    AssetDatabase.CreateFolder("Assets/Resources", "Mixamo");
            }

            AnimationClip idle = null, walk = null, run = null, jump = null;
            string[] guids = AssetDatabase.FindAssets("t:AnimationClip", new[] { ArtDir });
            foreach (string guid in guids)
            {
                var clip = AssetDatabase.LoadAssetAtPath<AnimationClip>(
                    AssetDatabase.GUIDToAssetPath(guid));
                if (clip == null) continue;
                string n = clip.name.ToLowerInvariant();
                if (n.Contains("idle") && idle == null) idle = clip;
                else if (n.Contains("walk") && walk == null) walk = clip;
                else if ((n.Contains("run") || n.Contains("running")) && run == null &&
                    !n.Contains("forward") && !n.Contains("backward") && !n.Contains("strafe"))
                    run = clip;
                else if (n.Contains("jump") && jump == null) jump = clip;
            }

            if (walk == null || run == null)
            {
                Debug.LogWarning("[RemyKit] Clip mancanti: walk=" +
                    (walk != null) + " run=" + (run != null));
                if (walk == null && run != null) walk = run;
                if (run == null && walk != null) run = walk;
            }

            // Clip Humanoid Mixamo non hanno curve generiche: l'idle
            // sintetico ("Idle_Static") e' vuoto e causa T-pose.
            // Soluzione: usa la clip Walk come idle a Speed=0.
            if (idle == null && walk != null) idle = walk;

            Debug.Log("[RemyKit] Clips: idle=" + (idle != null ? idle.name : "null") +
                " walk=" + (walk != null ? walk.name : "null") +
                " run=" + (run != null ? run.name : "null"));

            bool hasJump = jump != null;

            Debug.Log("[RemyKit] Creating controller at " + CtrlPath);
            if (File.Exists(CtrlPath))
                AssetDatabase.DeleteAsset(CtrlPath);
            string dir = Path.GetDirectoryName(CtrlPath);
            Debug.Log("[RemyKit] Dir: " + dir + " valid=" + AssetDatabase.IsValidFolder(dir));
            if (!AssetDatabase.IsValidFolder(dir))
            {
                string parent = Path.GetDirectoryName(dir);
                string child = Path.GetFileName(dir);
                Debug.Log("[RemyKit] Creating folder: " + parent + "/" + child);
                AssetDatabase.CreateFolder(parent, child);
            }
            AnimatorController ctrl = null;
            try
            {
                ctrl = AnimatorController.CreateAnimatorControllerAtPath(CtrlPath);
            }
            catch (System.Exception ex)
            {
                Debug.LogError("[RemyKit] Controller creation FAILED: " + ex);
                return;
            }
            if (ctrl == null)
            {
                Debug.LogError("[RemyKit] Controller creation returned null");
                return;
            }
            ctrl.name = "PlayerLocomotion";
            ctrl.AddParameter("Speed", AnimatorControllerParameterType.Float);
            if (hasJump)
                ctrl.AddParameter("IsGrounded", AnimatorControllerParameterType.Bool);

            // NB: AnimatorController.layers restituisce una COPIA dell'array:
            // "ctrl.layers[0] = layer" scrive sulla copia e il flag iKPass si
            // perde (il controller rigenerato a ogni build aveva m_IKPass:0 e
            // OnAnimatorIK non scattava mai -> player affondato). Rileggi e
            // riassegna l'array intero, poi SetDirty per persistere.
            AnimatorControllerLayer[] layers = ctrl.layers;
            layers[0].name = "Base Layer";
            layers[0].iKPass = true;
            layers[0].defaultWeight = 1f;
            ctrl.layers = layers;
            EditorUtility.SetDirty(ctrl);
            AnimatorControllerLayer layer = ctrl.layers[0];

            AnimatorStateMachine sm = layer.stateMachine;

            BlendTree blendTree;
            AnimatorState blendState = ctrl.CreateBlendTreeInController(
                "LocomotionBlend", out blendTree);
            blendTree.blendType = BlendTreeType.Simple1D;
            blendTree.blendParameter = "Speed";
            blendTree.useAutomaticThresholds = false;
            blendTree.AddChild(idle, 0f);
            blendTree.AddChild(walk, 3.2f);
            blendTree.AddChild(run, 7.5f);
            blendState.name = "LocomotionBlend";
            sm.defaultState = blendState;

            if (hasJump)
            {
                var jumpState = sm.AddState("Jump", new Vector3(260f, -80f, 0f));
                jumpState.motion = jump;
                jumpState.writeDefaultValues = true;

                // Blend -> Jump quando si lascia il terreno.
                var toJump = blendState.AddTransition(jumpState);
                toJump.hasExitTime = false;
                toJump.duration = 0.08f;
                toJump.AddCondition(AnimatorConditionMode.IfNot, 0f, "IsGrounded");

                // Jump -> Blend al ritorno a terra.
                var toBlend = jumpState.AddTransition(blendState);
                toBlend.hasExitTime = false;
                toBlend.duration = 0.15f;
                toBlend.AddCondition(AnimatorConditionMode.If, 0f, "IsGrounded");
            }

            AssetDatabase.SaveAssets();
            Debug.Log("[RemyKit] Controller creato: " + CtrlPath +
                " (Idle=" + idle.name + " Walk=" + walk.name + " Run=" + run.name +
                " Jump=" + (jump != null ? jump.name : "manco") + ")");
        }

        // ═══════════════════════════════════════════════════════════════
        // 5. Prefab Resources/PlayerHero
        // ═══════════════════════════════════════════════════════════════

        private static void CreatePlayerHeroPrefab()
        {
            string modelPath = ArtDir + "/Remy.fbx";
            var model = AssetDatabase.LoadAssetAtPath<GameObject>(modelPath);
            var mat = AssetDatabase.LoadAssetAtPath<Material>(MatDir + "/PlayerHero.mat");
            var ctrl = AssetDatabase.LoadAssetAtPath<RuntimeAnimatorController>(CtrlPath);
            if (model == null)
            {
                Debug.LogWarning("[RemyKit] Model Remy.fbx non importato.");
                return;
            }

            if (!AssetDatabase.IsValidFolder("Assets/Resources"))
                AssetDatabase.CreateFolder("Assets", "Resources");

            // Stacca dal file model (altrimenti il prefab eredita le impostazioni
            // a runtime): istanziamo il modello come child.
            GameObject root = new GameObject("PlayerHero");
            GameObject modelGo;
            {
                Object prefabLike = PrefabUtility.InstantiatePrefab(model, root.transform);
                modelGo = prefabLike as GameObject;
            }
            if (modelGo == null)
            {
                modelGo = Object.Instantiate(model, root.transform, false);
            }
            modelGo.name = "Remy";

            // Materiale URP su tutti gli SMR.
            if (mat != null)
            {
                var smrs = modelGo.GetComponentsInChildren<SkinnedMeshRenderer>();
                foreach (var smr in smrs)
                {
                    var mats = new Material[smr.sharedMaterials.Length];
                    for (int i = 0; i < mats.Length; i++) mats[i] = mat;
                    smr.sharedMaterials = mats;
                }
            }

            // Avatar humanoid + controller.
            var anim = root.GetComponent<Animator>();
            if (anim == null) anim = root.AddComponent<Animator>();
            anim.applyRootMotion = false;
            if (ctrl != null)
            {
                anim.runtimeAnimatorController = ctrl;
                Avatar avatar = FindModelAvatar(modelPath);
                if (avatar != null) anim.avatar = avatar;
            }

            if (File.Exists(PrefabPath))
                AssetDatabase.DeleteAsset(PrefabPath);
            PrefabUtility.SaveAsPrefabAsset(root, PrefabPath);
            Object.DestroyImmediate(root);
            Debug.Log("[RemyKit] Prefab creato: " + PrefabPath);
        }

        private static Avatar FindModelAvatar(string modelPath)
        {
            foreach (Object o in AssetDatabase.LoadAllAssetsAtPath(modelPath))
                if (o is Avatar) return (Avatar)o;
            return null;
        }
    }

    /// <summary>
    /// Applica le impostazioni di import automaticamente anche quando i file
    /// vengono aggiunti/aggiornati dalla cartella senza un build/build hook.
    /// </summary>
    public class RemyPostprocessor : AssetPostprocessor
    {
        private void OnPreprocessModel()
        {
            string a = assetPath.Replace('\\', '/');
            if (!a.StartsWith("Assets/Art/Mixamo/")) return;
            if (a.EndsWith(".fbx") && (
                    a.EndsWith("Remy.fbx") || a.EndsWith("Walking.fbx") ||
                    a.EndsWith("Run.fbx") || a.EndsWith("Jump.fbx")))
            {
                RemyKitSetup.ConfigureImportPublic(a);
            }
        }
    }
}