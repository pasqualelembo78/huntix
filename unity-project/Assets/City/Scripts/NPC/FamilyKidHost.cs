using System;
using UnityEngine;
using TMPro;
using City.Economy;
using City.Player;

namespace City.NPC
{
    /// <summary>
    /// Gestisce i figli del giocatore e i percorsi di famiglia come NPC
    /// visibili in scena:
    ///   - figli nati : seguono il player (scala ridotta)
    ///   - orfani     : bambina/i da adottare, compaiono vicino al player
    ///   - genitori adottivi : una coppia adulta che puo adottare il player
    ///                         se e single (essere adottati)
    /// </summary>
    public class FamilyKidHost : MonoBehaviour
    {
        public static FamilyKidHost Instance;

        private class Npc
        {
            public FamilyManager.ChildInfo info;   // solo per i figli nati
            public string displayName;
            public string roleTag;                 // es. "Orfana", "Mamma adottiva"
            public GameObject go;
            public Transform tagT;
            public TextMeshPro tagTmp;
            public float scale = 1f;
        }

        private readonly System.Collections.Generic.List<Npc> _kids =
            new System.Collections.Generic.List<Npc>();
        private readonly System.Collections.Generic.List<Npc> _orphans =
            new System.Collections.Generic.List<Npc>();
        private System.Collections.Generic.List<Npc> _fosters =
            new System.Collections.Generic.List<Npc>();
        private GameObject _prefab;
        private float _refreshTimer;
        private float _tapTimer;
        private float _spawnCooldown;

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
        }

        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("FamilyKidHost");
            UnityEngine.Object.DontDestroyOnLoad(go);
            go.AddComponent<FamilyKidHost>();
        }

        private void Update()
        {
            _refreshTimer += Time.unscaledDeltaTime;
            if (_refreshTimer < 0.6f) return;
            _refreshTimer = 0f;

            _spawnCooldown -= 0.6f;
            RefreshKids();
            RefreshOrphans();
            RefreshFosters();
        }

        // ---- figli nati ----
        private void RefreshKids()
        {
            var list = FamilyManager.Children;
            if (list == null) return;
            while (_kids.Count < list.Count && _kids.Count < FamilyManager.MaxChildren)
            {
                var c = list[_kids.Count];
                _kids.Add(BuildNpc("Kid_" + c.charId,
                    ChildLabel(c), 0.34f, new Color(0.9f, 0.8f, 0.6f)));
            }
            foreach (var k in _kids)
            {
                if (k == null || k.go == null) continue;
                FollowAt(k, -1.2f);
                k.tagTmp.text = ChildLabel(k.info);
            }
        }

        private string ChildLabel(FamilyManager.ChildInfo c)
        {
            if (c == null) return "";
            string kind = c.adopted ? "figlio/a adottiva" : "figlio/a";
            return c.name + " (" + kind + ", " + c.ageYears + " anni)";
        }

        // ---- orfani da adottare ----
        private void RefreshOrphans()
        {
            // pulisci orfani passati da adottare (sono diventati figli)
            for (int i = _orphans.Count - 1; i >= 0; i--)
            {
                var o = _orphans[i];
                if (o == null || o.go == null) _orphans.RemoveAt(i);
            }
            if (!FamilyManager.CanAdopt() && _orphans.Count > 0)
            {
                foreach (var o in _orphans) if (o != null && o.go != null) Destroy(o.go);
                _orphans.Clear();
                return;
            }
            if (FamilyManager.CanAdopt() && _orphans.Count == 0 &&
                _spawnCooldown <= 0f)
            {
                _spawnCooldown = 30f;
                Npc o = BuildNpc("Orphan_" + UnityEpoch(),
                    "Orfano/a senza casa", 0.34f, new Color(0.6f, 0.7f, 1.0f));
                o.displayName = OrphanName();
                _orphans.Add(o);
            }
            foreach (var o in _orphans)
            {
                if (o == null || o.go == null) continue;
                FollowAt(o, 1.2f);
            }
        }

        private string OrphanName()
        {
            string[] names = { "Elena", "Davide", "Clara", "Pietro", "Mia" };
            return names[UnityEngine.Random.Range(0, names.Length)];
        }

        /// <summary>True se orfano vicino da adottare.</summary>
        public bool NearOrphan()
        {
            float d; string n;
            return NearestOrphan(out d, out n);
        }

        private bool NearestOrphan(out float dist, out string name)
        {
            name = "";
            dist = float.MaxValue;
            PlayerController pc = PlayerController.Instance;
            if (pc == null) return false;
            bool any = false;
            for (int i = 0; i < _orphans.Count; i++)
            {
                var o = _orphans[i];
                if (o == null || o.go == null) continue;
                float d = Vector3.Distance(pc.transform.position, o.go.transform.position);
                if (d <= 4f && d < dist) { dist = d; name = o.displayName; any = true; }
            }
            return any;
        }

        /// <summary>Adotta l orfano piu vicino (spende AdoptCost).</summary>
        public bool AdoptNearestOrphan()
        {
            float d; string n;
            if (!NearestOrphan(out d, out n) || !FamilyManager.CanAdopt()) return false;
            int age = UnityEngine.Random.Range(4, 13);
            var c = FamilyManager.AdoptChild(n, age);
            if (c == null) return false;
            // rimuovi l orfano adottato
            Npc toRemove = null;
            for (int i = 0; i < _orphans.Count; i++)
            {
                var o = _orphans[i];
                if (o == null || o.go == null) continue;
                if ((o.displayName == n) &&
                    (Vector3.Distance(GetPlayerPos(), o.go.transform.position) < 10f))
                { toRemove = o; break; }
            }
            if (toRemove != null)
            {
                if (toRemove.go != null) Destroy(toRemove.go);
                _orphans.Remove(toRemove);
            }
            return true;
        }

        // ---- genitori adottivi (essere adottato) ----
        private void RefreshFosters()
        {
            for (int i = _fosters.Count - 1; i >= 0; i--)
            {
                var f = _fosters[i];
                if (f == null || f.go == null) _fosters.RemoveAt(i);
            }
            if (!FamilyManager.CanBeFostered() && _fosters.Count > 0)
            {
                foreach (var f in _fosters) if (f != null && f.go != null) Destroy(f.go);
                _fosters.Clear();
                return;
            }
            if (FamilyManager.CanBeFostered() && _fosters.Count == 0 &&
                _spawnCooldown <= 0f)
            {
                _spawnCooldown = 45f;
                _fosters.Add(BuildNpc("FosterA_" + UnityEpoch(), "Mamma adottiva",
                    1f, new Color(1.0f, 0.7f, 0.7f)));
                _fosters.Add(BuildNpc("FosterB_" + UnityEpoch(), "Papa adottivo",
                    1f, new Color(0.7f, 0.8f, 1.0f)));
            }
            FollowAt2(_fosters, 2.0f, 2.6f);
        }

        public bool NearFosterParents()
        {
            PlayerController pc = PlayerController.Instance;
            if (pc == null) return false;
            for (int i = 0; i < _fosters.Count; i++)
            {
                var f = _fosters[i];
                if (f == null || f.go == null) continue;
                if (Vector3.Distance(pc.transform.position, f.go.transform.position) <= 4f)
                    return true;
            }
            return false;
        }

        /// <summary>Compila i nomi della coppia adottiva.</summary>
        public void FosterNames(out string n1, out string n2)
        {
            n1 = _fosters.Count > 0 && _fosters[0] != null ? _fosters[0].displayName : "Gianna";
            n2 = _fosters.Count > 1 && _fosters[1] != null ? _fosters[1].displayName : "Marco";
        }

        /// <summary>Il player viene adottato dalla coppia.</summary>
        public bool TriggerFoster()
        {
            if (!FamilyManager.CanBeFostered()) return false;
            string n1, n2;
            FosterNames(out n1, out n2);
            FamilyManager.SetFoster("foster_a", n1, "foster_b", n2);
            foreach (var f in _fosters)
                if (f != null && f.go != null) Destroy(f.go);
            _fosters.Clear();
            return true;
        }

        // ---- costruzione NPC ----
        private Npc BuildNpc(string objectName, string label,
            float scale, Color tagColor)
        {
            var n = new Npc
            {
                displayName = label,
                roleTag = label,
                scale = scale
            };
            if (_prefab == null)
            {
                try { _prefab = Resources.Load<GameObject>("Characters/characterMedium"); }
                catch (Exception) { }
            }
            GameObject go = _prefab != null
                ? Instantiate(_prefab, transform)
                : GameObject.CreatePrimitive(PrimitiveType.Capsule);
            go.name = objectName;
            go.transform.localScale = Vector3.one * scale;
            foreach (var c in go.GetComponentsInChildren<Collider>())
                if (c != null) c.enabled = false;
            var col = go.AddComponent<CapsuleCollider>();
            col.isTrigger = true;
            col.height = 2.1f / Mathf.Max(0.02f, scale);
            col.radius = 0.4f;

            var t = new GameObject("Tag");
            n.tagT = t.transform;
            n.tagT.SetParent(go.transform, false);
            n.tagT.localPosition = new Vector3(0f, 2.2f / Mathf.Max(0.02f, scale), 0f);
            float inv = 1f / Mathf.Max(0.01f, go.transform.lossyScale.x);
            n.tagT.localScale = Vector3.one * inv;
            var rt = t.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(6f, 2f);
            n.tagTmp = t.AddComponent<TextMeshPro>();
            n.tagTmp.fontSize = 2.4f;
            n.tagTmp.alignment = TextAlignmentOptions.Center;
            n.tagTmp.color = tagColor;
            n.tagTmp.text = label;
            n.go = go;
            return n;
        }

        private void FollowAt(Npc n, float rightOffset)
        {
            if (n == null || n.go == null) return;
            PlayerController pc = PlayerController.Instance;
            if (pc == null || pc.transform == null) return;
            Vector3 p = pc.transform.position;
            Vector3 best = p + (pc.transform.right * rightOffset);
            n.go.transform.position = Vector3.Lerp(n.go.transform.position, best, 2.2f * Time.deltaTime);
            n.go.transform.rotation = pc.transform.rotation;
            Bill(n);
        }

        private void FollowAt2(System.Collections.Generic.List<Npc> list, float a, float b)
        {
            if (list.Count < 2) return;
            if (list[0] == null || list[1] == null) return;
            if (list[0].go == null || list[1].go == null) return;
            PlayerController pc = PlayerController.Instance;
            if (pc == null || pc.transform == null) return;
            Vector3 p = pc.transform.position;
            Vector3 c0 = p + (pc.transform.right * a);
            Vector3 c1 = p + (pc.transform.right * -b);
            list[0].go.transform.position = Vector3.Lerp(list[0].go.transform.position, c0, 2.2f * Time.deltaTime);
            list[1].go.transform.position = Vector3.Lerp(list[1].go.transform.position, c1, 2.2f * Time.deltaTime);
            list[0].go.transform.rotation = pc.transform.rotation;
            list[1].go.transform.rotation = pc.transform.rotation;
            Bill(list[0]); Bill(list[1]);
        }

        private void Bill(Npc n)
        {
            var cam = Camera.main;
            if (cam != null && n.tagT != null)
                n.tagT.rotation = cam.transform.rotation;
        }

        private Vector3 GetPlayerPos()
        {
            PlayerController pc = PlayerController.Instance;
            return pc != null && pc.transform != null ? pc.transform.position : Vector3.zero;
        }

        private int UnityEpoch()
        {
            return (int)(DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond);
        }

        /// <summary>Parla con un figlio: dialoghi filiali e un po di XP.</summary>
        public void TalkTo(FamilyManager.ChildInfo info)
        {
            if (info == null) return;
            if (Time.unscaledTime < _tapTimer) return;
            _tapTimer = Time.unscaledTime + 1.5f;
            var g = Game.Instance;
            string[] lines = {
                "Grazie per essermi accanto.",
                "Mi sento al sicuro con te.",
                "Raccontami una storia.",
                "Ti voglio bene, genitore.",
                "Quando sarò grande farò grandi cose."
            };
            string line = lines[Mathf.Abs(info.name.Length) % lines.Length];
            if (g != null && g.ui != null)
                g.ui.ShowToast(info.name + ": " + line);
            if (UnityEngine.Random.Range(0f, 1f) < 0.35f)
            {
                int xp = 4 + info.ageYears;
                PlayerPrefs.SetInt("family_kid_xp",
                    PlayerPrefs.GetInt("family_kid_xp", 0) + xp);
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Complicità genitore-figlio: +" + xp + " XP");
            }
        }

        public bool NearKid()
        {
            PlayerController pc = PlayerController.Instance;
            if (pc == null) return false;
            foreach (var k in _kids)
            {
                if (k == null || k.go == null) continue;
                if (Vector3.Distance(pc.transform.position, k.go.transform.position) <= 4f)
                    return true;
            }
            return false;
        }

        public void TalkToNearest()
        {
            PlayerController pc = PlayerController.Instance;
            if (pc == null) return;
            FamilyManager.ChildInfo best = null;
            float bestD = float.MaxValue;
            foreach (var k in _kids)
            {
                if (k == null || k.go == null) continue;
                float d = Vector3.Distance(pc.transform.position, k.go.transform.position);
                if (d <= 4f && d < bestD) { bestD = d; best = k.info; }
            }
            if (best != null) TalkTo(best);
        }
    }
}
