using System;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.UI;
using City.World;
using City.OSM;

namespace City.Economy
{
    /// <summary>
    /// Lavori ripetibili del roleplay urbano: Tassista, Corriere e Ronda.
    /// Una stazione "LAVORI" appare vicino al player: entrandoci si apre il
    /// pannello con i lavori disponibili. Ogni lavoro genera punti obiettivo
    /// randomici attorno al player (beacon luminosi), pagati con Wallet e
    /// moltiplicati dal livello di esperienza del lavoro (XP persistiti).
    /// I beacon seguono il rebase del floating origin come i chunk.
    /// </summary>
    public class JobManager : MonoBehaviour
    {
        public static JobManager Instance;

        public enum JobType { Taxi, Consegne, Ronda }

        private class JobDef
        {
            public JobType type;
            public string title;
            public string icon;
            public string desc;
            public int payPerStep;
            public int steps;
        }

        private static readonly JobDef[] Defs =
        {
            new JobDef{ type = JobType.Taxi, title = "Tassista", icon = "\uD83D\uDE96",
                desc = "Prendi il passeggero e portalo a destinazione",
                payPerStep = 0, steps = 2 },
            new JobDef{ type = JobType.Consegne, title = "Corriere", icon = "\uD83D\uDCE6",
                desc = "Consegna 3 pacchi nei punti segnalati",
                payPerStep = 12, steps = 3 },
            new JobDef{ type = JobType.Ronda, title = "Ronda", icon = "\uD83D\uDC6E",
                desc = "Tocca i 4 punti di controllo della ronda",
                payPerStep = 10, steps = 4 },
        };

        private const float ArriveDistSq = 6f * 6f;
        private const string XpKeyPrefix = "city_job_xp_";

        private class ActiveJob
        {
            public JobDef def;
            public int step;
            public List<Vector3> pts = new List<Vector3>();
            public float totalDist;
            public string[] labels;
        }

        private ActiveJob _job;
        private GameObject _beacon;
        private TextMeshPro _beaconLabel;
        private GameObject _panel;
        private RectTransform _listContent;
        private TMP_Text _hudText;
        private float _reopenGuard;
        private readonly List<Transform> _stationSigns = new List<Transform>();
        // stazioni LAVORI presso i negozi: id entrance -> cartello istanziato
        private readonly Dictionary<int, GameObject> _stations =
            new Dictionary<int, GameObject>();
        private float _nextScan;

        private Transform Player
        {
            get { return Game.Instance != null && Game.Instance.player != null
                ? Game.Instance.player.transform : null; }
        }

        // ── ciclo di vita ────────────────────────────────────────

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            WorldOrigin.OnRebased += OnRebased;
        }

        private void OnDestroy()
        {
            if (Instance == this) Instance = null;
            WorldOrigin.OnRebased -= OnRebased;
        }

        private void Update()
        {
            if (Player != null) ScanShopStations();

            if (_job == null || Player == null) return;

            Vector3 target = _job.pts[_job.step];
            Vector3 pp = Player.position;

            if (Camera.main != null)
            {
                if (_beaconLabel != null)
                    _beaconLabel.transform.rotation = Camera.main.transform.rotation;
                for (int i = 0; i < _stationSigns.Count; i++)
                    if (_stationSigns[i] != null)
                        _stationSigns[i].rotation = Camera.main.transform.rotation;
            }

            if ((pp - target).sqrMagnitude <= ArriveDistSq) AdvanceStep(pp);
            else RefreshHud(pp);
        }

        private void OnRebased(Vector3 delta)
        {
            foreach (var kv in _stations)
                if (kv.Value != null) kv.Value.transform.position -= delta;
            if (_beacon != null) _beacon.transform.position -= delta;
            if (_job != null)
                for (int i = 0; i < _job.pts.Count; i++)
                    _job.pts[i] -= delta;
        }

        // ── XP / livelli / paga ──────────────────────────────────

        public static int Xp(JobType t)
        {
            return PlayerPrefs.GetInt(XpKeyPrefix + t, 0);
        }

        public static int Level(JobType t)
        {
            return 1 + Mathf.Min(9, Xp(t) / 60);
        }

        private static float PayMult(JobType t)
        {
            return 1f + 0.15f * (Level(t) - 1);
        }

        // ── stazione LAVORI ─────────────────────────────────────

        private const float ScanEvery = 3f;
        private const float StationRadius = 250f;
        private const float StationForgetDist = 420f;
        private const int MaxStations = 8;

        /// <summary>Le stazioni LAVORI non nascono accanto al player:
        /// compaiono solo presso i locali che assumono (buildingType shop)
        /// quando il player e' in zona, e spariscono se ci si allontana.</summary>
        private void ScanShopStations()
        {
            if (Time.unscaledTime < _nextScan) return;
            _nextScan = Time.unscaledTime + ScanEvery;
            Transform p = Player;
            if (p == null) return;

            var forget = new List<int>();
            foreach (var kv in _stations)
            {
                if (kv.Value == null ||
                    (p.position - kv.Value.transform.position).sqrMagnitude >
                    StationForgetDist * StationForgetDist)
                    forget.Add(kv.Key);
            }
            foreach (var id in forget)
            {
                if (_stations[id] != null) Destroy(_stations[id]);
                _stations.Remove(id);
            }

            var entrances = FindObjectsOfType<City.Interior.BuildingEntrance>();
            foreach (var b in entrances)
            {
                if (_stations.Count >= MaxStations) break;
                if (b.buildingType != "shop") continue;
                Vector3 pos = b.transform.position;
                if ((p.position - pos).sqrMagnitude >
                    StationRadius * StationRadius) continue;
                int id = b.GetInstanceID();
                if (_stations.ContainsKey(id)) continue;
                _stations[id] = BuildStationAt(pos);
            }
        }

        private GameObject BuildStationAt(Vector3 basePos)
        {
            RaycastHit hit;
            if (Physics.Raycast(basePos + Vector3.up * 40f, Vector3.down,
                    out hit, 100f, ~0, QueryTriggerInteraction.Ignore))
                basePos = hit.point;

            var station = new GameObject("JobStation");
            station.transform.position = basePos;

            var col = station.AddComponent<CapsuleCollider>();
            col.isTrigger = true;
            col.height = 4f;
            col.radius = 2.5f;
            col.center = new Vector3(0f, 2f, 0f);

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.name = "Pole";
            UnityEngine.Object.Destroy(pole.GetComponent<Collider>());
            pole.transform.SetParent(station.transform, false);
            pole.transform.localScale = new Vector3(0.15f, 1.25f, 0.15f);
            pole.transform.localPosition = new Vector3(0f, 1.25f, 0f);
            SetColor(pole, new Color(0.2f, 0.75f, 0.55f));

            var signGo = new GameObject("Sign");
            signGo.transform.SetParent(station.transform, false);
            signGo.transform.localPosition = new Vector3(0f, 2.9f, 0f);
            _stationSigns.Add(signGo.transform);
            var tmp = signGo.AddComponent<TextMeshPro>();
            tmp.text = "LAVORI";
            tmp.fontSize = 2.4f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            var rt = tmp.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(4f, 1.2f);

            var gate = station.AddComponent<StationGate>();
            gate.mgr = this;
            return station;
        }

        private static void SetColor(GameObject go, Color c)
        {
            var r = go.GetComponent<Renderer>();
            if (r == null) return;
            var mats = r.sharedMaterials;
            for (int i = 0; i < mats.Length; i++)
            {
                Material m = new Material(Shader.Find("Sprites/Default"));
                m.color = c;
                mats[i] = m;
            }
            r.sharedMaterials = mats;
        }

        /// <summary>Chiamato dal gate sulla stazione (i callback di trigger
        /// arrivano solo ai componenti dello stesso GameObject).</summary>
        public void StationEntered(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            if (Time.unscaledTime < _reopenGuard) return;
            _reopenGuard = Time.unscaledTime + 5f;
            OpenPanel();
        }

        /// <summary>Inoltra i trigger della stazione al manager.</summary>
        private class StationGate : MonoBehaviour
        {
            public JobManager mgr;

            private void OnTriggerEnter(Collider other)
            {
                if (mgr != null) mgr.StationEntered(other);
            }
        }

        // ── avvio / avanzamento / fine lavoro ───────────────────

        public void StartJob(int defIndex)
        {
            if (defIndex < 0 || defIndex >= Defs.Length) return;
            Transform p = Player;
            if (p == null) return;
            if (!City.Environment.EnergySystem.CanWork)
            {
                Toast("⚡ Sei troppo stanco per lavorare: siediti su una panchina o bevi qualcosa");
                return;
            }
            JobDef d = Defs[defIndex];

            ActiveJob j = new ActiveJob();
            j.def = d;
            for (int i = 0; i < d.steps; i++) j.pts.Add(Vector3.zero);
            if (d.type == JobType.Taxi)
            {
                j.labels = new string[] { "PASSEGGERO", "DESTINAZIONE" };
                j.pts[0] = RandomPointAround(p.position, 80f, 250f);
                j.pts[1] = RandomPointAround(j.pts[0], 250f, 550f);
                j.totalDist = Vector3.Distance(j.pts[0], j.pts[1]);
            }
            else if (d.type == JobType.Consegne)
            {
                j.labels = new string[d.steps];
                Vector3 prev = p.position;
                for (int i = 0; i < d.steps; i++)
                {
                    j.pts[i] = RandomPointAround(prev, 120f, 400f);
                    prev = j.pts[i];
                    j.labels[i] = "CONSEGNA " + (i + 1) + "/" + d.steps;
                }
            }
            else
            {
                j.labels = new string[d.steps];
                for (int i = 0; i < d.steps; i++)
                {
                    j.pts[i] = RandomPointAround(p.position, 60f, 220f);
                    j.labels[i] = "CHECKPOINT " + (i + 1) + "/" + d.steps;
                }
            }

            _job = j;
            _job.step = 0;
            ShowBeacon();
            HidePanel();
            Toast(d.icon + " " + d.title + ": " + d.desc);
            RefreshHud(p.position);
        }

        public void CancelJob()
        {
            if (_job == null) return;
            Toast("Lavoro annullato");
            EndJobVisuals();
            _job = null;
            HideHud();
            HidePanel();
        }

        private void AdvanceStep(Vector3 playerPos)
        {
            _job.step++;
            City.Environment.EnergySystem.Consume(
                City.Environment.EnergySystem.JobStepCost);
            int xpGain = _job.def.type == JobType.Taxi ? 12 : 6;
            if (_job.step >= _job.def.steps)
            {
                float pay;
                if (_job.def.type == JobType.Taxi)
                    pay = 40f + 0.08f * _job.totalDist;
                else
                    pay = _job.def.payPerStep * _job.def.steps +
                        (_job.def.type == JobType.Ronda ? 15f : 0f);
                pay *= PayMult(_job.def.type);
                int paid = Mathf.RoundToInt(pay);

                PlayerPrefs.SetInt(XpKeyPrefix + _job.def.type,
                    Xp(_job.def.type) + xpGain + 10);
                PlayerPrefs.Save();
                Wallet.Earn(paid);
                Toast("\u2705 " + _job.def.title + " completato! +" + paid +
                    "\u20ac" + (PayMult(_job.def.type) > 1.01f
                        ? " (liv. " + Level(_job.def.type) + ")" : ""));
                EndJobVisuals();
                _job = null;
                HideHud();
                HidePanel();
                return;
            }
            Toast(_job.def.icon + " passo " + (_job.step + 1) + "/" +
                _job.def.steps);
            ShowBeacon();
            RefreshHud(playerPos);
        }

        // ── beacon obiettivo ─────────────────────────────────────

        private void ShowBeacon()
        {
            if (_job == null) return;
            if (_beacon == null)
            {
                _beacon = new GameObject("JobBeacon");
                var cyl = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                UnityEngine.Object.Destroy(cyl.GetComponent<Collider>());
                cyl.name = "Beam";
                cyl.transform.SetParent(_beacon.transform, false);
                cyl.transform.localScale = new Vector3(1.6f, 9f, 1.6f);
                cyl.transform.localPosition = new Vector3(0f, 4.5f, 0f);
                SetColor(cyl, new Color(0.2f, 0.85f, 0.95f, 0.35f));

                var lblGo = new GameObject("Label");
                lblGo.transform.SetParent(_beacon.transform, false);
                lblGo.transform.localPosition = new Vector3(0f, 9.8f, 0f);
                _beaconLabel = lblGo.AddComponent<TextMeshPro>();
                _beaconLabel.fontSize = 4f;
                _beaconLabel.alignment = TextAlignmentOptions.Center;
                _beaconLabel.color = Color.white;
                var rt = _beaconLabel.GetComponent<RectTransform>();
                rt.sizeDelta = new Vector2(10f, 2f);
            }
            _beacon.transform.position = _job.pts[_job.step];
            _beaconLabel.text = _job.def.icon + " " + _job.labels[_job.step];
            _beacon.SetActive(true);
        }

        private void EndJobVisuals()
        {
            if (_beacon != null) _beacon.SetActive(false);
        }

        // ── HUD obiettivo corrente ───────────────────────────────

        private void RefreshHud(Vector3 playerPos)
        {
            if (_job == null) return;
            EnsureHud();
            if (_hudText == null) return;
            float d = Vector3.Distance(playerPos, _job.pts[_job.step]);
            _hudText.gameObject.SetActive(true);
            _hudText.text = _job.def.icon + " <b>" + _job.def.title + "</b> \u00b7 passo " +
                (_job.step + 1) + "/" + _job.def.steps + " \u00b7 " +
                Mathf.RoundToInt(d) + "m";
        }

        private void HideHud()
        {
            if (_hudText != null) _hudText.gameObject.SetActive(false);
        }

        private void EnsureHud()
        {
            if (_hudText != null) return;
            var canvas = FindObjectOfType<Canvas>();
            if (canvas == null) return;
            var go = new GameObject("JobHud");
            var rt = go.AddComponent<RectTransform>();
            rt.SetParent(canvas.transform, false);
            rt.anchorMin = new Vector2(0.5f, 1f);
            rt.anchorMax = new Vector2(0.5f, 1f);
            rt.pivot = new Vector2(0.5f, 1f);
            rt.anchoredPosition = new Vector2(0f, -8f);
            rt.sizeDelta = new Vector2(700f, 44f);
            _hudText = go.AddComponent<TextMeshProUGUI>();
            _hudText.fontSize = 26f;
            _hudText.alignment = TextAlignmentOptions.Center;
            _hudText.color = Color.white;
            _hudText.raycastTarget = false;
            var font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>(
                "Fonts & Materials/LiberationSans SDF");
            _hudText.font = font;
            go.SetActive(false);
        }

        // ── pannello scelta lavoro ───────────────────────────────

        public void TogglePanel()
        {
            if (_panel != null && _panel.activeSelf) HidePanel();
            else OpenPanel();
        }

        public void OpenPanel()
        {
            if (Instance == null) return;
            if (_panel == null) BuildPanel();
            if (_panel == null) return;
            RebuildRows();
            _panel.SetActive(true);
        }

        public void HidePanel()
        {
            if (_panel != null) _panel.SetActive(false);
        }

        private void BuildPanel()
        {
            var canvas = FindObjectOfType<Canvas>();
            if (canvas == null) return;

            _panel = new GameObject("JobsPanel");
            var prt = _panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = prt.anchorMax = prt.pivot =
                new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-260f, -230f);
            prt.offsetMax = new Vector2(260f, 230f);
            var bg = _panel.AddComponent<Image>();
            bg.color = new Color(0.11f, 0.12f, 0.14f, 0.97f);

            var title = MakeText(prt, "\uD83D\uDCBC LAVORI IN CITT\u00c0", 30f,
                Color.white, TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(18f, -14f), new Vector2(-70f, -58f));
            title.font = PanelFont();

            var closeBtn = MakeButton(prt, "X", () => HidePanel(),
                new Color(0.28f, 0.30f, 0.34f, 1f),
                new Vector2(1f, 1f), new Vector2(1f, 1f),
                new Vector2(-62f, -56f), new Vector2(-16f, -14f));
            closeBtn.sizeDelta = Vector2.zero;

            var scroll = MakeRect("Scroll", prt,
                new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(14f, 66f), new Vector2(-14f, -64f));
            var maskBg = scroll.gameObject.AddComponent<Image>();
            maskBg.color = new Color(0f, 0f, 0f, 0.15f);
            var sr = scroll.gameObject.AddComponent<ScrollRect>();

            var content = MakeRect("Content", scroll,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                Vector2.zero, Vector2.zero);
            content.pivot = new Vector2(0.5f, 1f);
            var vlg = content.gameObject.AddComponent<VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 8f;
            vlg.padding = new RectOffset(4, 4, 4, 4);
            content.gameObject.AddComponent<ContentSizeFitter>()
                .verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            sr.content = content;
            sr.viewport = scroll;
            sr.vertical = true;
            sr.horizontal = false;

            _listContent = content;
            _panel.SetActive(false);
        }

        private void RebuildRows()
        {
            if (_listContent == null) return;
            foreach (Transform child in _listContent) Destroy(child.gameObject);

            for (int i = 0; i < Defs.Length; i++)
            {
                JobDef d = Defs[i];
                bool busy = _job != null;
                bool mine = busy && _job.def.type == d.type;

                var row = MakeRect("Job" + i, _listContent,
                    Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
                row.sizeDelta = new Vector2(0f, 92f);
                row.anchorMin = new Vector2(0f, 1f);
                row.anchorMax = new Vector2(1f, 1f);
                row.pivot = new Vector2(0.5f, 1f);
                var img = row.gameObject.AddComponent<Image>();
                img.color = mine
                    ? new Color(0.13f, 0.35f, 0.26f, 1f)
                    : new Color(0.20f, 0.22f, 0.25f, 1f);

                var txt = MakeText(row,
                    d.icon + " <b>" + d.title + "</b>  \u00b7 Liv. " +
                        Level(d.type) + "  \u00b7 paga x" +
                        PayMult(d.type).ToString("F2") + "\n<size=70%>" + d.desc +
                        " \u00b7 \u26a1-" + City.Environment.EnergySystem.JobStepCost +
                        "/passo</size>",
                    24f, Color.white, TextAlignmentOptions.Left,
                    new Vector2(0f, 0f), new Vector2(0.72f, 1f),
                    new Vector2(12f, 8f), new Vector2(0f, -8f));
                txt.font = PanelFont();

                string btnLabel = mine ? "ANNULLA"
                    : busy ? "\u2014" : "INIZIA";
                Color c = mine ? new Color(0.85f, 0.45f, 0.15f, 1f)
                    : busy ? new Color(0.3f, 0.3f, 0.3f, 0.8f)
                    : new Color(0.15f, 0.65f, 0.45f, 1f);
                int captured = i;
                var brt = MakeButton(row, btnLabel, () =>
                {
                    if (_job != null && _job.def.type == Defs[captured].type)
                        CancelJob();
                    else if (_job == null)
                        StartJob(captured);
                    RebuildRows();
                }, c,
                new Vector2(0.74f, 0.5f), new Vector2(0.98f, 0.92f),
                Vector2.zero, Vector2.zero);
                brt.sizeDelta = Vector2.zero;
            }
        }

        // ── helper UI (pattern VehicleShopUI) ────────────────────

        private static TMP_FontAsset _font;

        private static TMP_FontAsset PanelFont()
        {
            if (_font == null)
            {
                _font = TMP_Settings.defaultFontAsset;
                if (_font == null) _font = Resources.Load<TMP_FontAsset>(
                    "Fonts & Materials/LiberationSans SDF");
            }
            return _font;
        }

        private static RectTransform MakeRect(string name, Transform parent,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject(name, typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        private static TMP_Text MakeText(RectTransform parent, string content,
            float size, Color color, TextAlignmentOptions alignment,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect("T", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var text = rt.gameObject.AddComponent<TextMeshProUGUI>();
            text.text = content;
            text.fontSize = size;
            text.color = color;
            text.alignment = alignment;
            text.font = PanelFont();
            text.raycastTarget = false;
            return text;
        }

        private static RectTransform MakeButton(RectTransform parent, string label,
            Action onClick, Color bgColor,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect("Btn", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bgColor;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(() => onClick());
            var t = MakeText(rt, label, 24f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            t.alignment = TextAlignmentOptions.Center;
            return rt;
        }

        // ── utilita' ─────────────────────────────────────────────

        private static Vector3 RandomPointAround(Vector3 center, float minD, float maxD)
        {
            for (int attempt = 0; attempt < 8; attempt++)
            {
                float ang = UnityEngine.Random.Range(0f, 360f);
                float dist = UnityEngine.Random.Range(minD, maxD);
                Vector3 p = center + Quaternion.Euler(0f, ang, 0f) *
                    Vector3.forward * dist;
                RaycastHit hit;
                if (Physics.Raycast(p + Vector3.up * 40f, Vector3.down,
                        out hit, 100f, ~0, QueryTriggerInteraction.Ignore))
                    return hit.point;
            }
            return center;
        }

        private void Toast(string msg)
        {
            if (Game.Instance != null && Game.Instance.ui != null)
                Game.Instance.ui.ShowToast(msg);
        }
    }
}
