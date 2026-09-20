using System;
using UnityEngine;
using TMPro;
using City.Economy;
using City.Player;
using City.World;

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

        // Genitori adottivi: vivono la propria vita, non seguono sempre il player.
        public const float FosterAdultScale = 0.455f;   // statura adulta normale
        public const float FosterWalkSpeed = 1.4f;      // m/s a spasso
        public const float FollowRadius = 14f;          // restano "nel quartiere"
        public const float CheckInInterval = 30f;       // s tra un check-in e l'altro
        public const int MaxDonationsPerDay = 3;        // tetto giornaliero dei doni
        public const int MinDonation = 10;              // dono minimo
        public const int MaxDonation = 50;              // dono massimo (lavori ricchi)

        private class Npc
        {
            public FamilyManager.ChildInfo info;   // solo per i figli nati
            public string displayName;
            public string roleTag;                 // es. "Orfana", "Mamma adottiva"
            public GameObject go;
            public Transform tagT;
            public TextMeshPro tagTmp;
            public float scale = 1f;
            public CharacterWalker walker;   // animazione camminata/idle
            public int fosterIndex;          // 0 = mamma, 1 = papa
            public string job;                // professione (limita i doni)
            public Vector3 wanderTarget;     // punto dove vive/va a spasso
            public float wanderPause;        // pausa tra un giro e l'altro
            public bool checkingIn;          // sta tornando dal player
            public float moveSpeed;
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
        private float _checkInTimer = 30f;
        private int _donationsToday;
        private string _lastDonationDay = "";

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            _donationsToday = PlayerPrefs.GetInt("city_foster_donations", 0);
            _lastDonationDay = PlayerPrefs.GetString("city_foster_day", "");
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
            UpdateFostersFrame();

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
                Vector3 p = GetPlayerPos();
                _fosters.Add(BuildNpc("FosterA_" + UnityEpoch(), "Mamma adottiva",
                    FosterAdultScale, new Color(1.0f, 0.7f, 0.7f)));
                _fosters[_fosters.Count - 1].fosterIndex = 0;
                _fosters[_fosters.Count - 1].job = PickFosterJob(0);
                _fosters[_fosters.Count - 1].wanderTarget = p + RandomOffset(3f, 5f);
                _fosters.Add(BuildNpc("FosterB_" + UnityEpoch(), "Papa adottivo",
                    FosterAdultScale, new Color(0.7f, 0.8f, 1.0f)));
                _fosters[_fosters.Count - 1].fosterIndex = 1;
                _fosters[_fosters.Count - 1].job = PickFosterJob(1);
                _fosters[_fosters.Count - 1].wanderTarget = p + RandomOffset(-5f, -3f);
                foreach (var f in _fosters)
                    if (f != null && f.go != null)
                        f.go.transform.position = f.wanderTarget;
                _checkInTimer = 30f;
            }
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
                catch (Exception e)
                {
                    City.OSM.OsmDiag.Log("[FamilyKidHost] Load characterMedium FALLITO: " + e.Message);
                }
            }
            if (_prefab == null)
                City.OSM.OsmDiag.Log("[FamilyKidHost] '" + objectName +
                    "' niente prefab -> capsule fallback");
            GameObject go = _prefab != null
                ? Instantiate(_prefab, transform)
                : GameObject.CreatePrimitive(PrimitiveType.Capsule);
            go.name = objectName;
            go.transform.localScale = Vector3.one * scale;
            n.walker = CharacterWalker.AttachIfNeeded(go);
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

        // La coppia adottiva vive la propria vita: non ti segue incollata.
        // Ogni tanto (cooldown _checkInTimer) uno dei due si avvicina per
        // vedere se stai bene: ti aiuta coi soldi (limitati dal lavoro e da
        // un tetto giornaliero), col cibo o col riposo, altrimenti solo una
        // parola di affetto. Il resto del tempo va a spasso nel quartiere.
        private void UpdateFostersFrame()
        {
            if (_fosters.Count < 2) return;
            PlayerController pc = PlayerController.Instance;
            Vector3 anchor = pc != null ? pc.transform.position : GetPlayerPos();
            _checkInTimer -= Time.unscaledDeltaTime;
            for (int i = 0; i < _fosters.Count; i++)
            {
                var f = _fosters[i];
                if (f == null || f.go == null) continue;

                if (!f.checkingIn && _checkInTimer <= 0f)
                    StartCheckIn(f);

                if (!f.checkingIn)
                {
                    Vector3 toW = f.wanderTarget - f.go.transform.position;
                    toW.y = 0f;
                    float distToPlayer = Vector3.Distance(f.go.transform.position, anchor);
                    if (toW.magnitude < 0.5f || f.wanderPause > 0f)
                    {
                        if (f.wanderPause > 0f)
                            f.wanderPause -= Time.unscaledDeltaTime;
                        else
                        {
                            f.wanderPause = UnityEngine.Random.Range(1.5f, 4f);
                            f.wanderTarget = anchor + RandomOffset(3f, FollowRadius);
                        }
                    }
                    if (distToPlayer > FollowRadius)
                        f.wanderTarget = anchor + RandomOffset(3f, 6f);
                }

                MoveFoster(f);
                if (f.walker != null) f.walker.SetSpeed(f.moveSpeed);
            }
        }

        private void StartCheckIn(Npc f)
        {
            f.checkingIn = true;
            f.wanderPause = 0f;
            _checkInTimer = CheckInInterval + UnityEngine.Random.Range(0f, 8f);
        }

        private void MoveFoster(Npc f)
        {
            PlayerController pc = PlayerController.Instance;
            if (pc == null || pc.transform == null) { f.moveSpeed = 0f; return; }

            Vector3 target = f.wanderTarget;
            if (f.checkingIn)
            {
                Vector3 p = pc.transform.position;
                Vector3 side = f.fosterIndex == 0
                    ? pc.transform.right * 1.6f
                    : pc.transform.right * -1.8f;
                target = p + side;
            }

            Vector3 to = target - f.go.transform.position;
            to.y = 0f;
            float dist = to.magnitude;
            if (dist > 0.3f)
            {
                float sp = Mathf.Min(FosterWalkSpeed * Time.unscaledDeltaTime, dist);
                f.go.transform.position += to.normalized * sp;
                f.go.transform.rotation = Quaternion.Slerp(f.go.transform.rotation,
                    Quaternion.LookRotation(to.normalized, Vector3.up),
                    6f * Time.unscaledDeltaTime);
                f.moveSpeed = FosterWalkSpeed;
                if (f.checkingIn && dist <= 1.2f)
                {
                    f.checkingIn = false;
                    DoCheckIn(f);
                }
            }
            else
            {
                f.moveSpeed = 0f;
            }
        }

        // Check-in emotivo: guarda se hai bisogno di soldi, cibo, riposo o
        // semplicemente di affetto. I doni in denaro sono limitati dal lavoro
        // del genitore e da un tetto giornaliero (mai a raffica).

        // Presentazione della coppia + decisione Sì/No. Raddoppia l'intervallo
        // se il player rifiuta, così non lo riassillano subito. L'adozione può
        // comunque essere accettata in seguito avvicinandosi e scegliendo la
        // voce "FAMIGLIA ADOTTIVA".
        private void AskAdoption(Game g, Npc f)
        {
            string n1 = _fosters.Count > 0 && _fosters[0] != null ? _fosters[0].roleTag : "Mamma adottiva";
            string n2 = _fosters.Count > 1 && _fosters[1] != null ? _fosters[1].roleTag : "Papa adottivo";
            if (g == null || g.ui == null) return;
            g.ui.ShowDialog("La tua possibilità di famiglia", new string[] {
                n1 + " e " + n2 + " ti sorridono da un po'.",
                "Non abbiamo figli e vorremmo prenderci cura di te.",
                "Vorranno ogni tanto venire a vedere come stai.",
                "Vuoi che diventino la tua famiglia adottiva? (Entri = Sì)"
            }, (int choice) =>
            {
                // -1 = chiuso con X (No), finale raggiunto = Sì
                bool accepted = choice >= 0;
                if (accepted && TriggerFoster())
                {
                    if (g != null && g.ui != null)
                        g.ui.ShowToast("\ud83c\udfe0 Ti hanno adottato " + n1 +
                            " e " + n2 + "! Genitori adottivi +" +
                            FamilyManager.FosterXpBonus + " XP.");
                }
                else
                {
                    if (g != null && g.ui != null)
                        g.ui.ShowToast(n1 + ": Va bene, ti lasciamo in pace. Se cambi idea, vieni pure da noi.");
                    // allunga la prossima visita per non riassillarli
                    _checkInTimer = CheckInInterval * 2f;
                }
            });
        }

        private void DoCheckIn(Npc f)
        {
            var g = Game.Instance;
            string job = string.IsNullOrEmpty(f.job) ? "ottimo lavoro" : f.job;

            // Primo avvicinamento: la coppia si presenta e chiede se vuoi
            // diventare loro figlio/a. Sì = adozione immediata; No = si
            // ritirano con educazione (ma restano nel quartiere a dare
            // una mano, senza riproporre l'adozione).
            if (PlayerPrefs.GetInt("foster_intro_done", 0) == 0)
            {
                PlayerPrefs.SetInt("foster_intro_done", 1);
                PlayerPrefs.Save();
                AskAdoption(g, f);
                return;
            }

            bool hungryOrThirsty =
                FamilyManager.hunger <= 45 || FamilyManager.thirst <= 45;
            bool tired = FamilyManager.fatigue <= 35;
            bool broke = Wallet.Money < 80;

            string line;
            if (broke)
            {
                line = GiveMoney(f);
            }
            else if (hungryOrThirsty)
            {
                FamilyManager.hunger = Mathf.Min(FamilyManager.hunger + 35, FamilyManager.NeedsMax);
                FamilyManager.thirst = Mathf.Min(FamilyManager.thirst + 35, FamilyManager.NeedsMax);
                line = "Ti vedo affamato/a o assetato/a... ti ho portato qualcosa da mangiare e bere.";
            }
            else if (tired)
            {
                FamilyManager.fatigue = Mathf.Min(FamilyManager.fatigue + 30, FamilyManager.NeedsMax);
                line = "Sembri stanco/a, tesoro. Riposa un po', ti voglio bene.";
            }
            else
            {
                line = "Passo solo a vedere se stai bene. Mi fa contento vederti sereno/a.";
            }
            if (g != null && g.ui != null)
                g.ui.ShowToast(f.roleTag + " (" + job + "): " + line);
        }

        // Denaro dal genitore: legato alla professione e a un budget giornaliero.
        private string GiveMoney(Npc f)
        {
            string today = System.DateTime.UtcNow.ToString("yyyy-MM-dd");
            if (_lastDonationDay != today)
            {
                _lastDonationDay = today;
                _donationsToday = 0;
                PlayerPrefs.SetInt("city_foster_donations", 0);
                PlayerPrefs.SetString("city_foster_day", today);
            }
            if (_donationsToday >= MaxDonationsPerDay)
                return "Vorrei aiutarti ma questo mese non ho altro da darti. Prova a trovare un lavoro.";

            int wealth = 0;
            if (!string.IsNullOrEmpty(f.job))
                wealth = (System.Math.Abs(f.job.GetHashCode()) % 4); // 0..3
            int amount = UnityEngine.Random.Range(MinDonation, MaxDonation + 1);
            amount += wealth * 8; // i lavori "ricchi" donano di piu'
            amount = Mathf.Min(amount, MaxDonation);
            _donationsToday++;
            PlayerPrefs.SetInt("city_foster_donations", _donationsToday);
            Wallet.Earn(amount);
            string jobName = string.IsNullOrEmpty(f.job) ? "ottimo lavoro" : f.job;
            return "Hai bisogno di soldi? Ti lascio " + amount + "€ dal lavoro di " + jobName + ".";
        }

        private string PickFosterJob(int index)
        {
            string[] jobs = { "postina", "panettiera", "maestra", "infermiera",
                "cassiera", "giardiniera", "pizzaiola", "fioraia",
                "parrucchiera", "vigile" };
            int seed = System.Math.Abs(index * 7919 + UnityEpoch()) % 100;
            return jobs[seed % jobs.Length];
        }

        private static Vector3 RandomOffset(float minR, float maxR)
        {
            float lo = Mathf.Min(minR, maxR);
            float hi = Mathf.Max(minR, maxR);
            float a = UnityEngine.Random.Range(0f, Mathf.PI * 2f);
            float r = UnityEngine.Random.Range(lo, hi);
            return new Vector3(Mathf.Cos(a) * r, 0f, Mathf.Sin(a) * r);
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
