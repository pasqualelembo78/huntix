using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

namespace City.Editor
{
    /// <summary>
    /// REMY MORPH BAKER — cuce i morph target sulla mesh del personaggio
    /// attuale (PlayerHero/Remy, Mixamo) come BLEND SHAPE native.
    ///
    /// Perche' esiste: la mesh attuale NON ha morph nativi (verificato:
    /// BlendShapeChannel = 0). La crescita per "Morph/Blend Shape Controllers"
    /// richiede quindi un bake di morph reali sul modello, con la tecnica
    /// professionale usata dai tool commerciali: si simulano le modifiche
    /// scheletriche (lunghezza segmenti, girometri, rotazioni agevoli) e si
    /// "arrostiscono" nelle delta dei vertici.
    ///
    /// GARANZIE:
    ///  - il modello originale NON viene toccato: ossatura, bindpose, rig,
    ///    animazioni e controller restano identici;
    ///  - ogni mesh producer una variante asset in Assets/Resources/Morphs/Remy/
    ///    con le STESSE bones/bindpose + canali blend shape;
    ///  - a runtime e' il GrowthMorphController a pilotare i pesi (no-op se i
    ///    morph non sono cotti);
    ///  - il sistema XP esistente resta del tutto intatto.
    ///
    /// NOTA: per leggere i vertici abilita temporaneamente Read/Write sull'import
    /// di Remy.fbx e alla fine lo ripristina (impatto zero a runtime).
    /// </summary>
    public static class RemyMorphBaker
    {
        public const string OutputRoot = "Assets/Resources/Morphs/Remy";
        public const string PrefabPath = "Assets/Resources/PlayerHero.prefab";
        public const string RemyFbxPath = "Assets/Art/Mixamo/Remy.fbx";

        // Nomi allineati al GrowthMorphController a runtime.
        public const string MorphAgeYoung = "Age_Young";
        public const string MorphAgeOld = "Age_Old";
        public const string MorphHeightShort = "Height_Short";
        public const string MorphHeightTall = "Height_Tall";
        public const string MorphLegsShort = "Prop_LegsShort";
        public const string MorphLegsLong = "Prop_LegsLong";
        public const string MorphArmsShort = "Prop_ArmsShort";
        public const string MorphArmsLong = "Prop_ArmsLong";
        public const string MorphHeadBig = "Prop_HeadBig";
        public const string MorphHeadSmall = "Prop_HeadSmall";
        public const string MorphShapeLean = "Shape_Lean";
        public const string MorphShapeStout = "Shape_Stout";

        // ── Gruppi ossei (Mixamo: nome contiene la radice, prefisso mixamorig:) ──
        private static readonly string[] GThigh = { "LeftUpLeg", "RightUpLeg" };
        private static readonly string[] GShin = { "LeftLeg", "RightLeg" };
        private static readonly string[] GFoot = { "LeftFoot", "RightFoot" };
        private static readonly string[] GTorso = { "Spine", "Spine1", "Spine2" };
        private static readonly string[] GNeck = { "Neck" };
        private static readonly string[] GHead = { "Head" };
        private static readonly string[] GUpperArm = { "LeftArm", "RightArm" };
        private static readonly string[] GForearm = { "LeftForeArm", "RightForeArm" };
        private static readonly string[] GHand = { "LeftHand", "RightHand" };
        private static readonly string[] GShoulder = { "LeftShoulder", "RightShoulder" };

        private struct BoneState
        {
            public Vector3 localPosition;
            public Vector3 localScale;
            public Quaternion localRotation;
        }

        private sealed class Op
        {
            public string contains;        // sottostringa del nome osso
            public float lengthFactor;     // scala |localPosition| del giunto (allunga/all.zeroa un segmento senza deformare i discendenti)
            public float girthXZ;          // scala trasversale (1 = invariato)
            public float headScale;        // scala teste uniforme (1 = invariato)
            public float eulerX;           // rotazione aggiuntiva (gradi) sul giunto
        }

        private sealed class MorphDef
        {
            public string name;
            public List<Op> ops = new List<Op>();
        }

        [MenuItem("Tools/Huntix/Morph/Bake Remy Morphs")]
        public static void BakeAllMenu()
        {
            BakeAll();
        }

        /// <summary>Punto d'ingresso anche da batchmode:
        /// -executeMethod City.Editor.RemyMorphBaker.BakeAll</summary>
        public static void BakeAll()
        {
            EnsureFolder(OutputRoot);

            GameObject prefab = AssetDatabase.LoadAssetAtPath<GameObject>(PrefabPath);
            if (prefab == null)
            {
                Debug.LogError("[RemyMorphBaker] prefab non trovato: " + PrefabPath);
                return;
            }

            GameObject inst = (GameObject)PrefabUtility.InstantiatePrefab(prefab);
            if (inst == null)
            {
                Debug.LogError("[RemyMorphBaker] instantiate fallito.");
                return;
            }
            inst.hideFlags = HideFlags.HideAndDontSave;

            bool readabilityWasChanged = false;
            try
            {
                readabilityWasChanged = EnsureReadable();
                AssetDatabase.Refresh();

                var smrs = inst.GetComponentsInChildren<SkinnedMeshRenderer>(true);
                int bakedCount = 0;
                for (int i = 0; i < smrs.Length; i++)
                {
                    if (BakeMesh(smrs[i])) bakedCount++;
                }

                Debug.Log("[RemyMorphBaker] bake completato su " + bakedCount +
                    " mesh su " + smrs.Length + " (sono saltate quelle non skinnate/icon eliminate).");
                AssetDatabase.SaveAssets();
                AssetDatabase.Refresh();
            }
            finally
            {
                Object.DestroyImmediate(inst);
                if (readabilityWasChanged) RestoreReadability();
                AssetDatabase.SaveAssets();
                AssetDatabase.Refresh();
            }
        }

        // ── Creazione ricorsiva delle cartelle di output ──
        private static void EnsureFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path)) return;
            int idx = path.LastIndexOf('/');
            if (idx <= 0) return;
            string parent = path.Substring(0, idx);
            EnsureFolder(parent);
            AssetDatabase.CreateFolder(parent, path.Substring(idx + 1));
        }

        // ── Accesso ai vertici: Read/Write temporaneo sull'import FBX ──
        private static bool EnsureReadable()
        {
            ModelImporter imp = AssetImporter.GetAtPath(RemyFbxPath) as ModelImporter;
            if (imp == null)
            {
                Debug.LogWarning("[RemyMorphBaker] importer FBX non trovato: " + RemyFbxPath);
                return false;
            }
            if (imp.isReadable) return false;
            imp.isReadable = true;
            imp.SaveAndReimport();
            Debug.Log("[RemyMorphBaker] Read/Write abilitato temporaneamente su " +
                RemyFbxPath + " (verra' ripristinato).");
            return true;
        }

        private static void RestoreReadability()
        {
            ModelImporter imp = AssetImporter.GetAtPath(RemyFbxPath) as ModelImporter;
            if (imp == null) return;
            imp.isReadable = false;
            imp.SaveAndReimport();
            Debug.Log("[RemyMorphBaker] Read/Write ripristinato a false su " + RemyFbxPath);
        }

        // ── Bake di UNA mesh ──
        private static bool BakeMesh(SkinnedMeshRenderer smr)
        {
            if (smr == null || smr.sharedMesh == null || smr.bones == null ||
                smr.bones.Length == 0)
            {
                Debug.Log("[RemyMorphBaker] SMR ignorato: " +
                    (smr != null ? smr.name : "null"));
                return false;
            }

            Mesh src = smr.sharedMesh;
            if (src.vertexCount < 3 || src.blendShapeCount > 0)
            {
                Debug.Log("[RemyMorphBaker] mesh saltata (non idonea): " + src.name +
                    " vertici=" + src.vertexCount + " blendShape=" + src.blendShapeCount);
                return false;
            }

            Vector3[] vertices = src.vertices;
            Vector3[] normals = src.normals;
            BoneWeight[] weights = src.boneWeights;
            Matrix4x4[] bindposes = src.bindposes;
            Transform[] bones = smr.bones;
            if (vertices == null || normals == null || weights == null ||
                bindposes == null || bones == null)
            {
                Debug.LogWarning("[RemyMorphBaker] dati mesh incompleti per " + src.name);
                return false;
            }

            // Mappa nome -> osso.
            var boneByName = new Dictionary<string, Transform>();
            for (int i = 0; i < bones.Length; i++)
            {
                if (bones[i] != null && !boneByName.ContainsKey(bones[i].name))
                    boneByName[bones[i].name] = bones[i];
            }

            // Stato di "rest" di tutti gli ossi dell'istanza (per reset).
            var restMap = new Dictionary<Transform, BoneState>();
            var allBones = instBones(smr);
            for (int i = 0; i < allBones.Length; i++)
            {
                var t = allBones[i];
                if (t == null) continue;
                BoneState st;
                st.localPosition = t.localPosition;
                st.localScale = t.localScale;
                st.localRotation = t.localRotation;
                if (!restMap.ContainsKey(t)) restMap.Add(t, st);
            }

            Transform meshT = smr.transform;
            Matrix4x4 meshInv = meshT.worldToLocalMatrix;

            // Posizioni skinnate a resto (per sottrazione, cancella ogni pose statica).
            Vector3[] restPos = SkinPose(vertices, weights, bones, bindposes, meshInv);
            Vector3[] restNrm = SkinPoseNormals(normals, weights, bones, bindposes, meshInv);

            Mesh baked = Object.Instantiate(src);
            baked.name = src.name;

            int morphs = 0;
            var defs = BuildMorphDefs();
            var reportedMissing = new HashSet<string>();
            var morphSummary = new System.Text.StringBuilder();
            foreach (MorphDef def in defs)
            {
                ResetToRest(restMap);
                bool anyApplied = ApplyOps(def, boneByName, out string missing);
                if (!anyApplied)
                {
                    Debug.LogWarning("[RemyMorphBaker] morph '" + def.name +
                        "': nessun osso disponibile su '" + src.name + "'");
                    continue;
                }
                // Sub-vincolo: se qualche osso manca, applica il morph sui soli
                // ossi presenti (mesh parziali: mani/piedi/testa). Segnaliamo
                // solo la prima occorrenza per mesh per non spammare il log.
                if (!string.IsNullOrEmpty(missing))
                {
                    if (!reportedMissing.Contains(src.name + "|" + def.name))
                    {
                        reportedMissing.Add(src.name + "|" + def.name);
                        Debug.Log("[RemyMorphBaker] morph '" + def.name + "' su '" +
                            src.name + "': ossi non in peso, applicati solo i restanti: " +
                            missing);
                    }
                }

                Vector3[] simPos = SkinPose(vertices, weights, bones, bindposes, meshInv);
                Vector3[] simNrm = SkinPoseNormals(normals, weights, bones, bindposes, meshInv);

                var dv = new Vector3[vertices.Length];
                var dn = new Vector3[vertices.Length];
                float maxD = 0f;
                int count = vertices.Length;
                for (int v = 0; v < count; v++)
                {
                    dv[v] = simPos[v] - restPos[v];
                    dn[v] = simNrm[v] - restNrm[v];
                    float m = dv[v].magnitude;
                    if (m > maxD) maxD = m;
                }
                if (maxD < 0.002f)
                {
                    Debug.LogWarning("[RemyMorphBaker] morph '" + def.name +
                        "' su '" + src.name + "' ha spostamento quasi nullo (" +
                        maxD + "): verificare i nomi ossei.");
                    continue;
                }

                baked.AddBlendShapeFrame(def.name, 100f, dv, dn, null);
                morphs++;
                if (morphSummary.Length > 0) morphSummary.Append(", ");
                morphSummary.Append(def.name + "=" + maxD.ToString("F4"));
                if (morphs == 1)
                    Debug.Log("[RemyMorphBaker] primo morph '" + def.name +
                        "' su '" + src.name + "' maxDelta=" + maxD.ToString("F4") +
                        " (bounds " + src.bounds.size.y.ToString("F2") + "m)");
            }
            ResetToRest(restMap);

            if (morphs == 0)
            {
                Object.DestroyImmediate(baked);
                return false;
            }

            Debug.Log("[RemyMorphBaker] SUM " + src.name + ": " + morphSummary);

            string path = OutputRoot + "/" + src.name + ".asset";
            AssetDatabase.DeleteAsset(path);
            AssetDatabase.CreateAsset(baked, path);
            Debug.Log("[RemyMorphBaker] salvata mesh morphizzata: " + path +
                " blendShape=" + morphs + " (bonename=" + src.name + ")");
            return true;
        }

        private static Transform[] instBones(SkinnedMeshRenderer smr)
        {
            var list = new List<Transform>();
            for (int i = 0; i < smr.bones.Length; i++)
            {
                var b = smr.bones[i];
                if (b != null && !list.Contains(b)) list.Add(b);
            }
            return list.ToArray();
        }

        // ── Skin ──
        private static Vector3[] SkinPose(Vector3[] vertices, BoneWeight[] weights,
            Transform[] bones, Matrix4x4[] bindposes, Matrix4x4 meshInv)
        {
            int count = vertices.Length;
            var outV = new Vector3[count];
            var boneMats = new Matrix4x4[bones.Length];
            for (int i = 0; i < bones.Length; i++)
            {
                if (bones[i] == null) { boneMats[i] = Matrix4x4.identity; continue; }
                Matrix4x4 boneMesh = meshInv * bones[i].localToWorldMatrix;
                boneMats[i] = i < bindposes.Length ? boneMesh * bindposes[i] : boneMesh;
            }
            for (int v = 0; v < count; v++)
            {
                BoneWeight bw = weights[v];
                Vector3 p = Vector3.zero;
                p += boneMats[bw.boneIndex0].MultiplyPoint(vertices[v]) * bw.weight0;
                if (bw.weight1 > 0f)
                    p += boneMats[bw.boneIndex1].MultiplyPoint(vertices[v]) * bw.weight1;
                if (bw.weight2 > 0f)
                    p += boneMats[bw.boneIndex2].MultiplyPoint(vertices[v]) * bw.weight2;
                if (bw.weight3 > 0f)
                    p += boneMats[bw.boneIndex3].MultiplyPoint(vertices[v]) * bw.weight3;
                outV[v] = p;
            }
            return outV;
        }

        private static Vector3[] SkinPoseNormals(Vector3[] normals, BoneWeight[] weights,
            Transform[] bones, Matrix4x4[] bindposes, Matrix4x4 meshInv)
        {
            int count = normals.Length;
            var outN = new Vector3[count];
            var boneMats = new Matrix4x4[bones.Length];
            for (int i = 0; i < bones.Length; i++)
            {
                if (bones[i] == null) { boneMats[i] = Matrix4x4.identity; continue; }
                Matrix4x4 boneMesh = meshInv * bones[i].localToWorldMatrix;
                boneMats[i] = i < bindposes.Length ? boneMesh * bindposes[i] : boneMesh;
            }
            for (int v = 0; v < count; v++)
            {
                BoneWeight bw = weights[v];
                Vector3 n = Vector3.zero;
                n += BoneNormal(boneMats[bw.boneIndex0], normals[v]) * bw.weight0;
                if (bw.weight1 > 0f)
                    n += BoneNormal(boneMats[bw.boneIndex1], normals[v]) * bw.weight1;
                if (bw.weight2 > 0f)
                    n += BoneNormal(boneMats[bw.boneIndex2], normals[v]) * bw.weight2;
                if (bw.weight3 > 0f)
                    n += BoneNormal(boneMats[bw.boneIndex3], normals[v]) * bw.weight3;
                outN[v] = n.normalized;
            }
            return outN;
        }

        private static Vector3 BoneNormal(Matrix4x4 m, Vector3 n)
        {
            Matrix4x4 m3 = new Matrix4x4();
            m3.SetColumn(0, new Vector4(m.m00, m.m10, m.m20, 0f));
            m3.SetColumn(1, new Vector4(m.m01, m.m11, m.m21, 0f));
            m3.SetColumn(2, new Vector4(m.m02, m.m12, m.m22, 0f));
            Matrix4x4 it = m3.transpose.inverse;
            return new Vector3(it.m00 * n.x + it.m01 * n.y + it.m02 * n.z,
                                it.m10 * n.x + it.m11 * n.y + it.m12 * n.z,
                                it.m20 * n.x + it.m21 * n.y + it.m22 * n.z);
        }

        // ── Definizione dei morph ──
        private static List<MorphDef> BuildMorphDefs()
        {
            var defs = new List<MorphDef>();

            // ALTEZZA
            MorphDef tall = new MorphDef { name = MorphHeightTall };
            Segment(tall, GThigh, 0.12f);
            Segment(tall, GShin, 0.12f);
            Segment(tall, GFoot, 0.05f);
            Segment(tall, GUpperArm, 0.12f);
            Segment(tall, GForearm, 0.12f);
            Segment(tall, GHand, 0.04f);
            TorsoLen(tall, 0.05f);
            Segment(tall, GNeck, 0.05f);
            defs.Add(tall);

            MorphDef short_ = new MorphDef { name = MorphHeightShort };
            Segment(short_, GThigh, -0.10f);
            Segment(short_, GShin, -0.10f);
            Segment(short_, GFoot, -0.04f);
            Segment(short_, GUpperArm, -0.10f);
            Segment(short_, GForearm, -0.10f);
            Segment(short_, GHand, -0.04f);
            TorsoLen(short_, -0.05f);
            Segment(short_, GNeck, -0.05f);
            defs.Add(short_);

            // ETA'
            MorphDef young = new MorphDef { name = MorphAgeYoung };
            Segment(young, GThigh, -0.18f);
            Segment(young, GShin, -0.18f);
            Segment(young, GFoot, -0.10f);
            Segment(young, GUpperArm, -0.14f);
            Segment(young, GForearm, -0.14f);
            Segment(young, GHand, -0.10f);
            TorsoLen(young, -0.08f);
            Segment(young, GNeck, -0.12f);
            Op headY = new Op { contains = "Head", headScale = 1.24f };
            young.ops.Add(headY);
            // "paffutello" infantile: giro arti e busto.
            young.ops.Add(new Op { contains = "LeftUpLeg", girthXZ = 1.10f });
            young.ops.Add(new Op { contains = "RightUpLeg", girthXZ = 1.10f });
            young.ops.Add(new Op { contains = "LeftArm", girthXZ = 1.10f });
            young.ops.Add(new Op { contains = "RightArm", girthXZ = 1.10f });
            young.ops.Add(new Op { contains = "Spine", girthXZ = 1.14f });
            young.ops.Add(new Op { contains = "Spine1", girthXZ = 1.12f });
            young.ops.Add(new Op { contains = "Neck", girthXZ = 0.92f });
            defs.Add(young);

            MorphDef old = new MorphDef { name = MorphAgeOld };
            Segment(old, GThigh, -0.06f);
            Segment(old, GShin, -0.06f);
            Segment(old, GUpperArm, -0.04f);
            Segment(old, GForearm, -0.04f);
            TorsoLen(old, -0.06f);
            // Stoop: leggera flessione in avanti della colonna (segno da
            // verificare sul modello; i delta saranno riportati nel log).
            old.ops.Add(new Op { contains = "Spine", eulerX = 4f });
            old.ops.Add(new Op { contains = "Spine1", eulerX = 5f });
            old.ops.Add(new Op { contains = "Spine2", eulerX = 4f });
            old.ops.Add(new Op { contains = "Head", headScale = 1.03f });
            old.ops.Add(new Op { contains = "LeftUpLeg", girthXZ = 0.97f });
            old.ops.Add(new Op { contains = "RightUpLeg", girthXZ = 0.97f });
            defs.Add(old);

            // PROPORZIONI
            MorphDef legsLong = new MorphDef { name = MorphLegsLong };
            Segment(legsLong, GThigh, 0.14f);
            Segment(legsLong, GShin, 0.14f);
            Segment(legsLong, GFoot, 0.03f);
            TorsoLen(legsLong, -0.06f);
            defs.Add(legsLong);

            MorphDef legsShort = new MorphDef { name = MorphLegsShort };
            Segment(legsShort, GThigh, -0.14f);
            Segment(legsShort, GShin, -0.14f);
            Segment(legsShort, GFoot, -0.03f);
            TorsoLen(legsShort, 0.07f);
            defs.Add(legsShort);

            MorphDef armsLong = new MorphDef { name = MorphArmsLong };
            Segment(armsLong, GUpperArm, 0.16f);
            Segment(armsLong, GForearm, 0.14f);
            TorsoLen(armsLong, -0.04f);
            defs.Add(armsLong);

            MorphDef armsShort = new MorphDef { name = MorphArmsShort };
            Segment(armsShort, GUpperArm, -0.16f);
            Segment(armsShort, GForearm, -0.14f);
            TorsoLen(armsShort, 0.04f);
            defs.Add(armsShort);

            MorphDef headBig = new MorphDef { name = MorphHeadBig };
            headBig.ops.Add(new Op { contains = "Head", headScale = 1.22f });
            headBig.ops.Add(new Op { contains = "Neck", lengthFactor = -0.10f });
            defs.Add(headBig);

            MorphDef headSmall = new MorphDef { name = MorphHeadSmall };
            headSmall.ops.Add(new Op { contains = "Head", headScale = 0.82f });
            headSmall.ops.Add(new Op { contains = "Neck", lengthFactor = 0.06f });
            defs.Add(headSmall);

            // CORPORATURA
            MorphDef lean = new MorphDef { name = MorphShapeLean };
            Girth(lean, GThigh, 0.90f);
            Girth(lean, GShin, 0.90f);
            Girth(lean, GUpperArm, 0.88f);
            Girth(lean, GForearm, 0.88f);
            Girth(lean, GTorso, 0.90f);
            Girth(lean, GShoulder, 0.88f);
            Girth(lean, GNeck, 0.90f);
            defs.Add(lean);

            MorphDef stout = new MorphDef { name = MorphShapeStout };
            // Le scale di ossi in catena si MOLTIPLICANO lungo il ramo: se
            // gonfiamo Spine, Spine1 e Spine2 insieme, la mano subisce il
            // prodotto e la corporatura esplode. Quindi girth localizzato:
            // ventre su Spine (groppa), gambe su UpLeg/Leg, braccia leggere.
            stout.ops.Add(new Op { contains = "Spine", girthXZ = 1.25f });
            Girth(stout, GShoulder, 1.06f);
            Girth(stout, GThigh, 1.18f);
            Girth(stout, GShin, 1.10f);
            Girth(stout, GUpperArm, 1.06f);
            Girth(stout, GForearm, 1.04f);
            Girth(stout, GNeck, 1.03f);
            stout.ops.Add(new Op { contains = "Head", headScale = 1.02f });
            TorsoLen(stout, -0.02f);
            defs.Add(stout);

            return defs;
        }

        private static MorphDef Segment(MorphDef d, string[] bones, float lenFactor)
        {
            for (int i = 0; i < bones.Length; i++)
                d.ops.Add(new Op { contains = bones[i], lengthFactor = lenFactor });
            return d;
        }

        private static MorphDef Girth(MorphDef d, string[] bones, float girth)
        {
            for (int i = 0; i < bones.Length; i++)
                d.ops.Add(new Op { contains = bones[i], girthXZ = girth });
            return d;
        }

        private static MorphDef TorsoLen(MorphDef d, float lenFactor)
        {
            // Lunghezza busto = spostamento dei giunti della colonna.
            d.ops.Add(new Op { contains = "Spine", lengthFactor = lenFactor });
            d.ops.Add(new Op { contains = "Spine1", lengthFactor = lenFactor });
            d.ops.Add(new Op { contains = "Spine2", lengthFactor = lenFactor });
            return d;
        }

        // ── Applicazione operazioni ──
        private static bool ApplyOps(MorphDef def, Dictionary<string, Transform> boneByName,
            out string missing)
        {
            missing = "";
            bool applied = false;
            foreach (Op op in def.ops)
            {
                Transform bone = FindBone(boneByName, op.contains);
                if (bone == null)
                {
                    if (missing.Length > 0) missing += ", ";
                    missing += op.contains;
                    continue;
                }
                applied = true;
                if (op.lengthFactor != 0f)
                    bone.localPosition = bone.localPosition * (1f + op.lengthFactor);
                if (op.girthXZ > 0f && Mathf.Abs(op.girthXZ - 1f) > 0.001f)
                {
                    Vector3 ls = bone.localScale;
                    bone.localScale = new Vector3(ls.x * op.girthXZ, ls.y, ls.z * op.girthXZ);
                }
                if (op.headScale > 0f && Mathf.Abs(op.headScale - 1f) > 0.001f)
                    bone.localScale = bone.localScale * op.headScale;
                if (op.eulerX != 0f)
                {
                    bone.localRotation = bone.localRotation *
                        Quaternion.Euler(op.eulerX, 0f, 0f);
                }
            }
            return applied;
        }

        private static Transform FindBone(Dictionary<string, Transform> boneByName, string bone)
        {
            foreach (var kv in boneByName)
            {
                if (BoneShortName(kv.Key) == bone) return kv.Value;
            }
            return null;
        }

        /// <summary>Nome osso senza il prefisso Mixamo ("mixamorig:LeftUpLeg"
        /// -> "LeftUpLeg"): il matching nei morph usa i nomi brevi.</summary>
        private static string BoneShortName(string full)
        {
            int idx = full.IndexOf(':');
            return idx >= 0 ? full.Substring(idx + 1) : full;
        }

        private static void ResetToRest(Dictionary<Transform, BoneState> restMap)
        {
            foreach (var kv in restMap)
            {
                if (kv.Key == null) continue;
                kv.Key.localPosition = kv.Value.localPosition;
                kv.Key.localScale = kv.Value.localScale;
                kv.Key.localRotation = kv.Value.localRotation;
            }
        }
    }
}