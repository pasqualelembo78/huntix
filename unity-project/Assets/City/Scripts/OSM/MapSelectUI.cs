using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using City.Vehicle;
using City.UI;

namespace City.OSM
{
    /// <summary>
    /// Mappa a schermo intero aperta col tap sulla minimappa.
    /// Pan (trascina con un dito), zoom (pinch a due dita o bottoni),
    /// tap su POI per selezionarlo come destinazione di navigazione.
    /// </summary>
    public class MapSelectUI : MonoBehaviour
    {
        public static MapSelectUI Instance { get; private set; }

        public bool IsOpen { get { return root != null && root.activeSelf; } }

        private const int TexSize = 1024;
        private static readonly float[] Spans =
            { 12800f, 6400f, 3200f, 1600f };

        // I POI sulla mappa grande si vedono allo zoom di default e nei
        // livelli piu' ravvicinati; a span 12800 m (vista "satellite") ce ne
        // sarebbero troppi e creerebbero casino, quindi restano nascosti.
        private const float PoiShowSpan = 6400f;

        private GameObject root;
        private RectTransform mapRt;
        private RawImage mapImg;
        private Texture2D tex;
        private Text infoText;
        private Button goBtn;
        private int zoomIdx = 1;

        private NavigationState.Destination candidate;

        // pannello info POI (tap su un POI nella mappa grande)
        private GameObject poiPanel;
        private Text poiTitle, poiCat, poiDist, poiPhone, poiWeb;

        private float nextRedraw;
        private bool dirty = true;

        // buffer ridisegno riusato ad ogni frame (evita 4 MB di GC pressure)
        private Color32[] _buf32;

        // pan offset in metri rispetto al giocatore
        private Vector2 panOffset;

        // pinch / drag state
        private bool dragging;
        // un tocco che parte da un "Began" puo' diventare tap; un dito
        // ripreso dopo il pinch invece no (evita tap accidentali in uscita)
        private bool allowTapOnEnd;
        private Vector2 dragStartScreen;
        private Vector2 dragStartPan;
        private float pinchStartSpan;
        private float pinchStartDist;
        // punto di mappa (coordinate texture) sotto il centro del pinch:
        // lo zoom mantiene li' il punto, cosi' si ingrandisce dove si pinza
        private Vector2 pinchAnchorTex;

        private static readonly Color ColBg = new Color(0.05f, 0.07f, 0.09f);
        private static readonly Color ColMajor = new Color(0.90f, 0.92f, 0.95f);
        private static readonly Color ColMid = new Color(0.62f, 0.67f, 0.72f);
        private static readonly Color ColMinor = new Color(0.40f, 0.45f, 0.51f);
        private static readonly Color ColPlayer = new Color(0.95f, 0.15f, 0.12f);
        private static readonly Color ColDest =
            new Color(1f, 0.80f, 0.20f);

        // ── menu POI a categorie + ricerca testuale ──
        private readonly string[] _categories =
            { "hospital", "rampa", "repair", "school", "bar", "dealer", "bank" };
        private GameObject _backBtn;
        private RectTransform _resultContent;
        private ScrollRect _resultScroll;

        // ── dialogo "teletrasporta o freccetta?" ──
        private GameObject _askDialog;
        private Text _askTitle, _askSub;
        private NavigationState.Destination _askTarget;

        // ── menu hamburger della mappa ──
        private GameObject _menuPanel;
        private readonly List<GameObject> _resultItems = new List<GameObject>();
        private static readonly Color ColSearch = new Color(0.10f, 0.13f, 0.18f, 0.95f);
        private static readonly Color ColResult = new Color(0.14f, 0.18f, 0.24f, 0.92f);
        private static readonly Color ColResultHi = new Color(0.22f, 0.30f, 0.42f, 0.95f);

        public static void Ensure()
        {
            if (Instance == null)
            {
                var go = new GameObject("MapSelectUI", typeof(MapSelectUI));
                DontDestroyOnLoad(go);
            }
        }

        public static void Open()
        {
            if (Instance == null)
            {
                var go = new GameObject("MapSelectUI", typeof(MapSelectUI));
                DontDestroyOnLoad(go);
            }
            if (Instance == null)
            {
                Debug.LogError("[MapSelectUI] Open() Instance still NULL after create!");
                return;
            }
            if (Instance.root == null)
            {
                Debug.LogError("[MapSelectUI] Open() root is NULL!");
                return;
            }
            Instance.Show();
        }

        public static void Toggle()
        {
            if (Instance != null && Instance.root.activeSelf) Close();
            else Open();
        }

        public static void Close()
        {
            if (Instance != null) Instance.Hide();
        }

        private void Awake()
        {
            Instance = this;
            Build();
        }

        private void Show()
        {
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("MapSelectUI_EventSystem",
                    typeof(EventSystem), typeof(StandaloneInputModule));
            }
            root.SetActive(true);
            panOffset = Vector2.zero;
            dirty = true;
            if (goBtn != null) goBtn.gameObject.SetActive(false);
            if (_askDialog != null) _askDialog.SetActive(false);
            ShowCategories();
        }

        private void Hide()
        {
            root.SetActive(false);
            candidate = null;
            dragging = false;
            if (goBtn != null) goBtn.gameObject.SetActive(false);
        }

        // ───────────────────── UI ─────────────────────
        private void Build()
        {
            root = new GameObject("Root", typeof(Canvas),
                typeof(CanvasScaler), typeof(GraphicRaycaster));
            root.transform.SetParent(transform, false);
            var rc = root.GetComponent<Canvas>();
            rc.renderMode = RenderMode.ScreenSpaceOverlay;
            rc.sortingOrder = 100;
            var sc = root.GetComponent<UnityEngine.UI.CanvasScaler>();
            sc.uiScaleMode = UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;
            sc.referenceResolution = new Vector2(1080, 1920);

            var dim = new GameObject("Dim", typeof(Image));
            dim.transform.SetParent(root.transform, false);
            var dimg = dim.GetComponent<Image>();
            dimg.color = new Color(0f, 0f, 0f, 0.85f);
            // il fondo non deve intercettare i tocco sulla mappa
            dimg.raycastTarget = false;
            Stretch(dim.GetComponent<RectTransform>());

            int mapSize = Mathf.Min(Screen.width, Screen.height) - 60;
            var mapGo = new GameObject("Mappa", typeof(RawImage));
            mapGo.transform.SetParent(root.transform, false);
            mapImg = mapGo.GetComponent<RawImage>();
            // se la mappa fosse raycastable il tocco risulterebbe "su UI" e
            // bloccerebbe pan/tap/zoom: la rendiamo trasparente ai raycast
            mapImg.raycastTarget = false;
            tex = new Texture2D(TexSize, TexSize,
                TextureFormat.RGBA32, false);
            tex.filterMode = FilterMode.Bilinear;
            mapImg.texture = tex;
            mapRt = mapGo.GetComponent<RectTransform>();
            mapRt.anchorMin = mapRt.anchorMax = mapRt.pivot =
                new Vector2(0.5f, 0.5f);
            mapRt.sizeDelta = new Vector2(mapSize, mapSize);

            MakeBtn(mapGo.transform, "+", new Vector2(64f, -64f),
                () => { zoomIdx = Mathf.Max(0, zoomIdx - 1); dirty = true; });
            MakeBtn(mapGo.transform, "-", new Vector2(64f, -150f),
                () =>
                {
                    zoomIdx = Mathf.Min(Spans.Length - 1, zoomIdx + 1);
                    dirty = true;
                });
            MakeBtn(mapGo.transform, "X", new Vector2(-64f, -64f), Close);

            MakeHamburgerButton(mapGo.transform, new Vector2(-200f, -64f));
            BuildMenuPanel(mapGo.transform);

            infoText = NewText(mapGo.transform, "Info", 30,
                new Color(0.95f, 0.96f, 0.98f));
            infoText.raycastTarget = false;
            var irt = infoText.rectTransform;
            irt.anchorMin = new Vector2(0f, 0f);
            irt.anchorMax = new Vector2(1f, 0f);
            irt.pivot = new Vector2(0.5f, 0f);
            irt.anchoredPosition = new Vector2(0f, 14f);
            irt.sizeDelta = new Vector2(-24f, 84f);

            goBtn = MakeBtn(mapGo.transform, "VAI", new Vector2(-190f, 110f),
                ConfirmGo, new Color(0.85f, 0.62f, 0.10f));
            goBtn.gameObject.SetActive(false);

            MakeBtn(mapGo.transform, "CANCELLA DEST",
                new Vector2(-210f, 200f),
                () =>
                {
                    NavigationState.Clear();
                    dirty = true;
                },
                new Color(0.35f, 0.18f, 0.16f));

            try { BuildPoiInfo(mapGo.transform); } catch (System.Exception e) { Debug.LogException(e); Debug.LogError("[MapSelectUI] BuildPoiInfo FAILED: " + e.Message); }

            BuildAskDialog(mapGo.transform);

            root.SetActive(false);
        }

        private static Text NewText(Transform parent, string name, int size,
            Color col)
        {
            var go = new GameObject(name, typeof(Text));
            go.transform.SetParent(parent, false);
            var t = go.GetComponent<Text>();
            t.font = CompassUI.UiFont();
            t.fontSize = size;
            t.color = col;
            t.alignment = TextAnchor.MiddleCenter;
            return t;
        }

        private static Button MakeBtn(Transform parent, string caption,
            Vector2 pos, System.Action onClick)
        {
            return MakeBtn(parent, caption, pos, onClick,
                new Color(0.13f, 0.17f, 0.22f));
        }

        private static Button MakeBtn(Transform parent, string caption,
            Vector2 pos, System.Action onClick, Color col)
        {
            var go = new GameObject("Btn_" + caption, typeof(Button));
            go.transform.SetParent(parent, false);
            var img = go.AddComponent<Image>();
            img.color = col;
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos;
            float w = caption.Length > 2 ? 300f : 96f;
            rt.sizeDelta = new Vector2(w, 76f);

            var t = NewText(go.transform, "T", 32, Color.white);
            t.text = caption;
            t.rectTransform.anchorMin = Vector2.zero;
            t.rectTransform.anchorMax = Vector2.one;
            t.rectTransform.sizeDelta = Vector2.zero;

            var b = go.GetComponent<Button>();
            b.onClick.AddListener(() => onClick());
            return b;
        }

        private static void Stretch(RectTransform rt)
        {
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.sizeDelta = Vector2.zero;
        }

        // ───────────────────── menu hamburger ─────────────────────
        // Bottone col "sandwich" disegnato con tre barrette: il font UI
        // potrebbe non avere il glifo "☰", quindi niente caratteri rari.
        private void MakeHamburgerButton(Transform parent, Vector2 pos)
        {
            var go = new GameObject("Btn_Menu", typeof(Image), typeof(Button));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = new Color(0.13f, 0.17f, 0.22f);
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = pos;
            rt.sizeDelta = new Vector2(96f, 76f);
            go.GetComponent<Button>().onClick.AddListener(ToggleMenu);

            for (int i = 0; i < 3; i++)
            {
                var bar = new GameObject("Bar", typeof(Image));
                bar.transform.SetParent(go.transform, false);
                var brt = bar.GetComponent<RectTransform>();
                brt.anchorMin = brt.anchorMax = new Vector2(0.5f, 0.5f);
                brt.anchoredPosition = new Vector2(0f, 14f - i * 14f);
                brt.sizeDelta = new Vector2(44f, 6f);
                bar.GetComponent<Image>().color = Color.white;
            }
        }

        private void ToggleMenu()
        {
            if (_menuPanel == null) return;
            bool open = !_menuPanel.activeSelf;
            _menuPanel.SetActive(open);
            if (open) ShowCategories();
        }

        /// <summary>
        /// Menu ad hamburger della mappa espansa: la voce principale elenca le
        /// categorie dei POI della citta'; ogni categoria apre un sotto-menu con
        /// i POI disponibili (ordinati per distanza). Il tap su un POI chiede
        /// "teletrasporto immediato" o "andiamoci con la freccetta". In fondo,
        /// le voci di sistema (chiudi mappa, note legali, esci).
        /// </summary>
        private void BuildMenuPanel(Transform parent)
        {
            _menuPanel = new GameObject("MenuPanel", typeof(Image));
            _menuPanel.transform.SetParent(parent, false);
            var mrt = _menuPanel.GetComponent<RectTransform>();
            mrt.anchorMin = new Vector2(1f, 1f);
            mrt.anchorMax = new Vector2(1f, 1f);
            mrt.pivot = new Vector2(1f, 1f);
            mrt.anchoredPosition = new Vector2(-60f, -150f);
            mrt.sizeDelta = new Vector2(420f, 640f);
            _menuPanel.GetComponent<Image>().color = ColSearch;

            // titolo del menu
            var title = NewText(_menuPanel.transform, "Titolo", 28,
                new Color(0.90f, 0.93f, 0.95f));
            title.text = "MENU POI DELLA CITTA'";
            title.alignment = TextAnchor.MiddleLeft;
            var trt = title.rectTransform;
            trt.anchorMin = new Vector2(0f, 1f);
            trt.anchorMax = new Vector2(1f, 1f);
            trt.pivot = new Vector2(0f, 1f);
            trt.anchoredPosition = new Vector2(10f, -8f);
            trt.sizeDelta = new Vector2(-20f, 44f);

            // bottone indietro (visibile solo dentro una categoria)
            _backBtn = new GameObject("Back", typeof(Image), typeof(Button));
            _backBtn.transform.SetParent(_menuPanel.transform, false);
            var brt = _backBtn.GetComponent<RectTransform>();
            brt.anchorMin = new Vector2(0f, 1f);
            brt.anchorMax = new Vector2(1f, 1f);
            brt.pivot = new Vector2(0f, 1f);
            brt.anchoredPosition = new Vector2(10f, -56f);
            brt.sizeDelta = new Vector2(-20f, 40f);
            _backBtn.GetComponent<Image>().color =
                new Color(0.22f, 0.30f, 0.42f, 0.95f);
            _backBtn.GetComponent<Button>().onClick.AddListener(ShowCategories);
            var bl = NewText(_backBtn.transform, "L", 24, Color.white);
            bl.text = "< TUTTE LE CATEGORIE";
            bl.alignment = TextAnchor.MiddleLeft;
            Stretch(bl.rectTransform);
            bl.rectTransform.offsetMin = new Vector2(14f, 0f);
            _backBtn.SetActive(false);

            // area risultati scrollabile (categorie o POI della categoria)
            var listGo = new GameObject("Results", typeof(Image));
            listGo.transform.SetParent(_menuPanel.transform, false);
            var lrt = listGo.GetComponent<RectTransform>();
            lrt.anchorMin = new Vector2(0f, 1f);
            lrt.anchorMax = new Vector2(1f, 1f);
            lrt.pivot = new Vector2(0f, 1f);
            lrt.anchoredPosition = new Vector2(0f, -104f);
            lrt.sizeDelta = new Vector2(0f, 484f);
            listGo.GetComponent<Image>().color = Color.clear;
            listGo.AddComponent<Mask>().showMaskGraphic = false;
            _resultScroll = listGo.AddComponent<ScrollRect>();

            var contentGo = new GameObject("Content", typeof(RectTransform));
            contentGo.transform.SetParent(listGo.transform, false);
            _resultContent = contentGo.GetComponent<RectTransform>();
            _resultContent.anchorMin = new Vector2(0f, 1f);
            _resultContent.anchorMax = new Vector2(1f, 1f);
            _resultContent.pivot = new Vector2(0f, 1f);
            _resultContent.anchoredPosition = Vector2.zero;
            _resultContent.sizeDelta = Vector2.zero;

            _resultScroll.content = _resultContent;
            _resultScroll.viewport = lrt;
            _resultScroll.vertical = true;
            _resultScroll.horizontal = false;
            _resultScroll.movementType = ScrollRect.MovementType.Clamped;

            _menuPanel.SetActive(false);
        }

        // Le voci "legali"/"esci" vivono nella HUD (overlay 10), sotto la
        // mappa (100): prima si chiude la mappa e poi si apre il pannello.
        private void ShowLegalIfAny()
        {
            if (UIManager.Instance != null) UIManager.Instance.ShowLegal();
        }

        private void ShowExitIfAny()
        {
            if (UIManager.Instance != null) UIManager.Instance.OnExitPressedPublic();
        }

        /// <summary>Conteggio dei POI registrati di una categoria.</summary>
        private int CountKind(string kind)
        {
            int n = 0;
            foreach (var p in VehiclePoiRegistry.All())
                if (p.kind == kind) n++;
            return n;
        }

        /// <summary>Elenca le categorie disponibili (con numero di POI).</summary>
        private void ShowCategories()
        {
            ClearResults();
            if (_backBtn != null) _backBtn.SetActive(false);
            float itemH = 66f;
            int n = _categories.Length;
            _resultContent.sizeDelta = new Vector2(0f, 8f + n * itemH);
            for (int i = 0; i < n; i++)
            {
                string kind = _categories[i];
                string caption = PoiSignpost.Caption(kind)
                    + " (" + CountKind(kind) + ")";
                var item = new GameObject("C_" + kind,
                    typeof(Image), typeof(Button));
                item.transform.SetParent(_resultContent, false);
                var irt = item.GetComponent<RectTransform>();
                irt.anchorMin = new Vector2(0f, 1f);
                irt.anchorMax = new Vector2(1f, 1f);
                irt.pivot = new Vector2(0f, 1f);
                irt.anchoredPosition = new Vector2(4f, -i * itemH);
                irt.sizeDelta = new Vector2(-8f, itemH - 4f);
                item.GetComponent<Image>().color = ColResult;
                string k = kind;
                item.GetComponent<Button>().onClick
                    .AddListener(() => ShowKind(k));
                var lbl = NewText(item.transform, "L", 25,
                    CompassUI.KindColor(kind));
                lbl.text = caption;
                lbl.alignment = TextAnchor.MiddleLeft;
                Stretch(lbl.rectTransform);
                lbl.rectTransform.offsetMin = new Vector2(14f, 0f);
                _resultItems.Add(item);
            }

            // voci di sistema in fondo al menu (dopo le categorie POI)
            int baseIdx = n;
            AddMenuActionRow("Chiudi mappa", new Color(0.9f, 0.93f, 0.95f),
                baseIdx++, itemH, Close);
            AddMenuActionRow("Note legali", new Color(0.9f, 0.93f, 0.95f),
                baseIdx++, itemH, () => { Hide(); ShowLegalIfAny(); });
            AddMenuActionRow("Esci dal gioco", new Color(0.85f, 0.45f, 0.40f),
                baseIdx, itemH, () => { Hide(); ShowExitIfAny(); });
            _resultContent.sizeDelta = new Vector2(0f, 8f + (baseIdx + 1) * itemH);
        }

        /// <summary>Riga di voce di sistema del menu hamburger.</summary>
        private void AddMenuActionRow(string caption, Color col,
            int i, float itemH, System.Action onTap)
        {
            var item = new GameObject("Sys_" + i, typeof(Image), typeof(Button));
            item.transform.SetParent(_resultContent, false);
            var irt = item.GetComponent<RectTransform>();
            irt.anchorMin = new Vector2(0f, 1f);
            irt.anchorMax = new Vector2(1f, 1f);
            irt.pivot = new Vector2(0f, 1f);
            irt.anchoredPosition = new Vector2(4f, -i * itemH);
            irt.sizeDelta = new Vector2(-8f, itemH - 4f);
            item.GetComponent<Image>().color = ColResult;
            item.GetComponent<Button>().onClick.AddListener(() => onTap());
            var lbl = NewText(item.transform, "L", 25, col);
            lbl.text = caption;
            lbl.alignment = TextAnchor.MiddleLeft;
            Stretch(lbl.rectTransform);
            lbl.rectTransform.offsetMin = new Vector2(14f, 0f);
            _resultItems.Add(item);
        }

        /// <summary>POI della categoria scelta, ordinati per distanza.</summary>
        private void ShowKind(string kind)
        {
            ClearResults();
            if (_backBtn != null) _backBtn.SetActive(true);
            FillKindList(kind);
        }

        private void FillKindList(string kind)
        {
            Transform t = PlayerWorld();
            float span = Spans[zoomIdx];
            float halfSpan = span * 0.5f;
            Vector3 pw = t != null ? t.position : Vector3.zero;

            var matches = new List<VehiclePoiRegistry.PoiInfo>();
            foreach (var poi in VehiclePoiRegistry.All())
            {
                if (poi.kind != kind) continue;
                if (t != null)
                {
                    Vector3 w = WorldOrigin.ToWorld(poi.lat, poi.lng);
                    float dx = w.x - pw.x;
                    float dz = w.z - pw.z;
                    if (Mathf.Abs(dx) > halfSpan || Mathf.Abs(dz) > halfSpan)
                        continue;
                }
                matches.Add(poi);
            }

            matches.Sort((a, b) =>
            {
                if (t == null) return 0;
                Vector3 wa = WorldOrigin.ToWorld(a.lat, a.lng);
                Vector3 wb = WorldOrigin.ToWorld(b.lat, b.lng);
                float da = Vector3.Distance(wa, pw);
                float db = Vector3.Distance(wb, pw);
                return da.CompareTo(db);
            });

            int maxShow = Mathf.Min(matches.Count, 40);
            float itemH = 42f;
            _resultContent.sizeDelta = new Vector2(0f, 8f + maxShow * itemH);
            if (maxShow == 0)
            {
                AddNoResultRow("NESSUNA " + PoiSignpost.Caption(kind).ToUpper()
                    + " NELL'AREA", itemH, 0);
                return;
            }

            for (int i = 0; i < maxShow; i++)
            {
                var poi = matches[i];
                string caption = PoiSignpost.Caption(poi.kind) + " " + poi.name;
                if (t != null)
                    caption += "  ·  " + FormatDist(
                        Vector3.Distance(WorldOrigin.ToWorld(poi.lat, poi.lng), pw));
                AddResultRow(caption, CompassUI.KindColor(poi.kind),
                    poi.name ?? "", poi.kind ?? "", poi.lat, poi.lng,
                    i, itemH);
            }
        }

        /// <summary>Riga di risultato: POI tappabile che chiede come andarci.</summary>
        private void AddResultRow(string caption, Color col, string name,
            string kind, double lat, double lng, int i, float itemH)
        {
            var item = new GameObject("R_" + i, typeof(Image), typeof(Button));
            item.transform.SetParent(_resultContent, false);
            var irt = item.GetComponent<RectTransform>();
            irt.anchorMin = new Vector2(0f, 1f);
            irt.anchorMax = new Vector2(1f, 1f);
            irt.pivot = new Vector2(0f, 1f);
            irt.anchoredPosition = new Vector2(4f, -i * itemH);
            irt.sizeDelta = new Vector2(-8f, itemH - 4f);
            item.GetComponent<Image>().color = ColResult;

            string nm = name ?? "", kd = kind ?? "";
            float la = (float)lat, lo = (float)lng;
            var btn = item.GetComponent<Button>();
            btn.onClick.AddListener(() => SelectCategoryPoi(nm, kd, la, lo));

            var lbl = NewText(item.transform, "L", 22, col);
            lbl.text = caption;
            lbl.alignment = TextAnchor.MiddleLeft;
            Stretch(lbl.rectTransform);
            lbl.rectTransform.offsetMin = new Vector2(12f, 0f);
            lbl.rectTransform.offsetMax = new Vector2(-12f, 0f);

            _resultItems.Add(item);
        }

        /// <summary>Riga informativa quando non c'è nessun risultato.</summary>
        private void AddNoResultRow(string msg, float itemH, int i)
        {
            var item = new GameObject("R_0", typeof(Image));
            item.transform.SetParent(_resultContent, false);
            var irt = item.GetComponent<RectTransform>();
            irt.anchorMin = new Vector2(0f, 1f);
            irt.anchorMax = new Vector2(1f, 1f);
            irt.pivot = new Vector2(0f, 1f);
            irt.anchoredPosition = new Vector2(4f, -i * itemH);
            irt.sizeDelta = new Vector2(-8f, itemH - 4f);
            item.GetComponent<Image>().color = ColResult;

            var lbl = NewText(item.transform, "L", 22,
                new Color(0.75f, 0.78f, 0.82f));
            lbl.text = msg;
            lbl.alignment = TextAnchor.MiddleCenter;
            Stretch(lbl.rectTransform);
            _resultItems.Add(item);
        }

        /// <summary>Tap su un POI della lista: chiede come raggiungerlo.</summary>
        private void SelectCategoryPoi(string name, string kind,
            double lat, double lng)
        {
            ClearResults();
            candidate = new NavigationState.Destination
            {
                name = string.IsNullOrEmpty(name)
                    ? PoiSignpost.Caption(kind) : name,
                kind = kind, lat = lat, lng = lng
            };
            AskSelectedPoi();
        }

        /// <summary>Dialogo "teletrasporto immediato o freccetta?".</summary>
        private void AskSelectedPoi()
        {
            if (candidate == null || _askDialog == null) return;
            _askTarget = candidate;
            _askTitle.text = candidate.name;
            _askTitle.color = CompassUI.KindColor(candidate.kind);
            _askSub.text = PoiSignpost.Caption(candidate.kind) + "  ·  " +
                CompassUI.FormatDist(DistToPlayer(candidate.lat, candidate.lng));
            if (goBtn != null) goBtn.gameObject.SetActive(false);
            if (poiPanel != null) poiPanel.SetActive(false);
            _askDialog.SetActive(true);
        }

        private void ClearResults()
        {
            foreach (var go in _resultItems) { if (go != null) Destroy(go); }
            _resultItems.Clear();
            if (_resultContent != null)
                _resultContent.sizeDelta = new Vector2(0f, 8f);
        }

        // ── dialogo teletrasporto / freccetta ──
        private void BuildAskDialog(Transform parent)
        {
            _askDialog = new GameObject("AskDialog", typeof(Image));
            _askDialog.transform.SetParent(parent, false);
            var rt = _askDialog.GetComponent<RectTransform>();
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(0.5f, 0.5f);
            rt.anchoredPosition = new Vector2(0f, 0f);
            rt.sizeDelta = new Vector2(700f, 440f);
            _askDialog.GetComponent<Image>().color =
                new Color(0.08f, 0.11f, 0.16f, 0.98f);

            _askTitle = NewText(_askDialog.transform, "AskTitle", 38,
                Color.white);
            UseCenter(_askTitle, 150f, 52f);

            _askSub = NewText(_askDialog.transform, "AskSub", 26,
                new Color(1f, 0.85f, 0.30f));
            UseCenter(_askSub, 96f, 36f);

            MakeBtn(_askDialog.transform, "TELEPORTA",
                new Vector2(-170f, -110f), AskTeleport,
                new Color(0.16f, 0.48f, 0.30f));
            MakeBtn(_askDialog.transform, "FRECCETTA",
                new Vector2(170f, -110f), AskNavigate,
                new Color(0.85f, 0.62f, 0.10f));
            MakeBtn(_askDialog.transform, "ANNULLA",
                new Vector2(0f, -165f), CloseAskDialog,
                new Color(0.35f, 0.18f, 0.16f));

            _askDialog.SetActive(false);
        }

        private static void UseCenter(Text t, float y, float h)
        {
            t.rectTransform.anchorMin = t.rectTransform.anchorMax =
                t.rectTransform.pivot = new Vector2(0.5f, 0.5f);
            t.rectTransform.anchoredPosition = new Vector2(0f, y);
            t.rectTransform.sizeDelta = new Vector2(660f, h);
            t.alignment = TextAnchor.MiddleCenter;
        }

        private void CloseAskDialog()
        {
            if (_askDialog != null) _askDialog.SetActive(false);
        }

        private void AskTeleport()
        {
            if (_askTarget == null) return;
            var d = _askTarget;
            CloseAskDialog();
            TeleportTo(d);
        }

        private void AskNavigate()
        {
            if (_askTarget == null) return;
            var d = _askTarget;
            CloseAskDialog();
            NavigateTo(d);
        }

        /// <summary>Teletrasporto immediato sul POI (con fade nero).</summary>
        private void TeleportTo(NavigationState.Destination d)
        {
            var game = Game.Instance;
            if (game == null) { NavigateTo(d); return; }
            Vector3 pos = WorldOrigin.ToWorld(d.lat, d.lng);
            pos.y = 0f;
            Quaternion rot = Quaternion.identity;
            if (Camera.main != null)
                rot = Quaternion.Euler(0f, Camera.main.transform.eulerAngles.y, 0f);
            NavigationState.Clear();
            Hide();
            game.TeleportPlayer(pos, rot);
        }

        /// <summary>Navigazione: la freccetta rossa punta al POI.</summary>
        private void NavigateTo(NavigationState.Destination d)
        {
            NavigationState.Set(d.name, d.kind, d.lat, d.lng);
            Hide();
        }

        private static string FormatDist(float meters)
        {
            if (meters < 950f) return Mathf.RoundToInt(meters) + " m";
            return meters.ToString("0.0") + " km";
        }

        // ───────────────────── pannello info POI ─────────────────────
        private void BuildPoiInfo(Transform parent)
        {
            var panel = new GameObject("PoiInfo", typeof(Image));
            panel.transform.SetParent(parent, false);
            var prt = panel.GetComponent<RectTransform>();
            prt.anchorMin = prt.anchorMax = prt.pivot = new Vector2(0.5f, 0.5f);
            prt.anchoredPosition = new Vector2(0f, 150f);
            prt.sizeDelta = new Vector2(660f, 400f);
            panel.GetComponent<Image>().color = new Color(0.08f, 0.11f, 0.16f, 0.97f);

            System.Action<Text, float, float> place = (txt, y, h) =>
            {
                txt.rectTransform.anchorMin = txt.rectTransform.anchorMax =
                    txt.rectTransform.pivot = new Vector2(0.5f, 0.5f);
                txt.rectTransform.anchoredPosition = new Vector2(0f, y);
                txt.rectTransform.sizeDelta = new Vector2(620f, h);
                txt.alignment = TextAnchor.MiddleCenter;
            };

            poiTitle = NewText(panel.transform, "PoiTitle", 36, Color.white);
            place(poiTitle, 150f, 48f);
            poiCat = NewText(panel.transform, "PoiCat", 26, CompassUI.ColDealer);
            place(poiCat, 100f, 34f);
            poiDist = NewText(panel.transform, "PoiDist", 26,
                new Color(1f, 0.85f, 0.30f));
            place(poiDist, 58f, 30f);
            poiPhone = NewText(panel.transform, "PoiPhone", 24, Color.white);
            place(poiPhone, 14f, 28f);
            poiWeb = NewText(panel.transform, "PoiWeb", 22,
                new Color(0.45f, 0.72f, 1f));
            place(poiWeb, -18f, 28f);

            MakeBtn(panel.transform, "RAGGIUNGI", new Vector2(-170f, -155f),
                () => { if (poiPanel != null) poiPanel.SetActive(false); AskSelectedPoi(); },
                new Color(0.85f, 0.62f, 0.10f));
            MakeBtn(panel.transform, "CHIUDI", new Vector2(170f, -155f),
                () => { if (poiPanel != null) poiPanel.SetActive(false); },
                new Color(0.35f, 0.18f, 0.16f));

            poiPanel = panel;
            panel.SetActive(false);
        }

        /// <summary>Mostra il pannello informazioni di un POI: nome, categoria,
        /// distanza, contatti (se presenti) e il pulsante per raggiungerlo.
        /// Il giocatore decide solo qui se impostarlo come destinazione.</summary>
        private void ShowPoiInfo(VehiclePoiRegistry.PoiInfo poi)
        {
            if (poi == null) return;
            candidate = new NavigationState.Destination
            {
                name = string.IsNullOrEmpty(poi.name)
                    ? PoiSignpost.Caption(poi.kind) : poi.name,
                kind = poi.kind,
                lat = poi.lat,
                lng = poi.lng
            };

            poiTitle.text = candidate.name;
            poiCat.text = PoiSignpost.Caption(poi.kind);
            poiCat.color = CompassUI.KindColor(poi.kind);

            float dist = DistToPlayer(poi.lat, poi.lng);
            poiDist.text = "Distanza: " + CompassUI.FormatDist(dist);

            if (!string.IsNullOrEmpty(poi.phone))
            {
                poiPhone.gameObject.SetActive(true);
                poiPhone.text = "Tel: " + poi.phone;
            }
            else poiPhone.gameObject.SetActive(false);

            if (!string.IsNullOrEmpty(poi.website))
            {
                poiWeb.gameObject.SetActive(true);
                poiWeb.text = poi.website;
            }
            else poiWeb.gameObject.SetActive(false);

            poiPanel.SetActive(true);
        }

        private float DistToPlayer(double lat, double lng)
        {
            Transform t = PlayerWorld();
            if (t == null) return 0f;
            Vector3 w = WorldOrigin.ToWorld(lat, lng);
            return Vector3.Distance(w, t.position);
        }

        // ───────────────────── gestures ─────────────────────
        private void Update()
        {
            if (root == null || !root.activeSelf) return;
            HandleGestures();
            if (!dirty && Time.unscaledTime >= nextRedraw)
                dirty = true;
            if (dirty)
            {
                dirty = false;
                nextRedraw = Time.unscaledTime + 0.15f;
                Redraw();
            }
        }

        private void HandleGestures()
        {
            if (Input.touchCount == 2)
            {
                HandlePinch();
                return;
            }

            if (Input.touchCount == 1)
            {
                var touch = Input.GetTouch(0);

                // se tocco una UI button, lascia passare
                if (IsPointerOverUI(touch.position)) return;

                if (touch.phase == TouchPhase.Began)
                {
                    if (IsInsideMap(touch.position))
                    {
                        dragging = true;
                        allowTapOnEnd = true;
                        dragStartScreen = touch.position;
                        dragStartPan = panOffset;
                    }
                    else allowTapOnEnd = false;
                }
                else if (!dragging && IsInsideMap(touch.position))
                {
                    // dito rimasto dopo un pinch (o tocco senza "Began"
                    // osservato): riprende il pan senza salti, mai un tap
                    dragging = true;
                    allowTapOnEnd = false;
                    dragStartScreen = touch.position;
                    dragStartPan = panOffset;
                }
                else if (touch.phase == TouchPhase.Moved && dragging)
                {
                    float span = Spans[zoomIdx];
                    Vector2 localStart, localCur;
                    RectTransformUtility.ScreenPointToLocalPointInRectangle(
                        mapRt, dragStartScreen, null, out localStart);
                    RectTransformUtility.ScreenPointToLocalPointInRectangle(
                        mapRt, touch.position, null, out localCur);
                    Vector2 deltaLocal = localCur - localStart;
                    float mPerUnit = span / mapRt.sizeDelta.x;
                    panOffset = dragStartPan - new Vector2(
                        deltaLocal.x * mPerUnit, deltaLocal.y * mPerUnit);
                    dirty = true;
                }
                else if (touch.phase == TouchPhase.Ended && dragging)
                {
                    float moved = Vector2.Distance(
                        touch.position, dragStartScreen);
                    dragging = false;
                    if (allowTapOnEnd && moved < 15f)
                        HandleTap(touch.position);
                }
                return;
            }

            // mouse fallback
            if (Input.GetMouseButtonDown(0)
                && !IsPointerOverUIMouse())
            {
                if (IsInsideMap(Input.mousePosition))
                {
                    dragging = true;
                    dragStartScreen = Input.mousePosition;
                    dragStartPan = panOffset;
                }
            }
            else if (Input.GetMouseButton(0) && dragging)
            {
                float span = Spans[zoomIdx];
                Vector2 localStart, localCur;
                RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    mapRt, dragStartScreen, null, out localStart);
                RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    mapRt, (Vector2)Input.mousePosition, null, out localCur);
                Vector2 deltaLocal = localCur - localStart;
                float mPerUnit = span / mapRt.sizeDelta.x;
                panOffset = dragStartPan - new Vector2(
                    deltaLocal.x * mPerUnit, deltaLocal.y * mPerUnit);
                dirty = true;
            }
            else if (Input.GetMouseButtonUp(0) && dragging)
            {
                float moved = Vector2.Distance(
                    (Vector2)Input.mousePosition, dragStartScreen);
                dragging = false;
                if (moved < 15f)
                    HandleTap(Input.mousePosition);
            }

            float scroll = Input.GetAxis("Mouse ScrollWheel");
            if (scroll != 0f && IsInsideMap(Input.mousePosition))
            {
                if (scroll > 0f)
                    zoomIdx = Mathf.Max(0, zoomIdx - 1);
                else
                    zoomIdx = Mathf.Min(Spans.Length - 1, zoomIdx + 1);
                dirty = true;
            }
        }

        private void HandlePinch()
        {
            Touch t0 = Input.GetTouch(0);
            Touch t1 = Input.GetTouch(1);

            if (t0.phase == TouchPhase.Began || t1.phase == TouchPhase.Began)
            {
                pinchStartDist = Vector2.Distance(
                    t0.position, t1.position);
                pinchStartSpan = Spans[zoomIdx];
                pinchAnchorTex = AnchorToTex(
                    (t0.position + t1.position) * 0.5f);
            }
            else if (t0.phase == TouchPhase.Moved ||
                     t1.phase == TouchPhase.Moved)
            {
                float dist = Vector2.Distance(
                    t0.position, t1.position);
                float ratio = pinchStartDist / Mathf.Max(dist, 1f);
                float newSpan = Mathf.Clamp(
                    pinchStartSpan * ratio,
                    Spans[Spans.Length - 1], Spans[0]);

                // trova lo zoomIdx piu' vicino
                int newIdx = zoomIdx;
                for (int i = 0; i < Spans.Length; i++)
                {
                    if (Spans[i] <= newSpan + 1f)
                    {
                        newIdx = i;
                        break;
                    }
                }
                if (newIdx != zoomIdx)
                {
                    zoomIdx = newIdx;
                    // mantiene fermo il punto sotto il centro del pinch:
                    // panOffset si sposta in modo che l'ancora resti li'.
                    float mOld = pinchStartSpan / TexSize;
                    float mNew = Spans[zoomIdx] / TexSize;
                    float t = TexSize * 0.5f;
                    var anchor = pinchAnchorTex;
                    panOffset += new Vector2(
                        (anchor.x - t) * (mOld - mNew),
                        (anchor.y - t) * (mOld - mNew));
                }
                dirty = true;
            }
        }

        /// <summary>Coordinate del buffer della mappa (0..TexSize) del punto di
        /// schermo: x da sinistra, y dal basso (nord in alto), coerenti con
        /// Px e con HandleTap.</summary>
        private Vector2 AnchorToTex(Vector2 screenPos)
        {
            if (mapRt == null)
                return new Vector2(TexSize * 0.5f, TexSize * 0.5f);
            Vector2 localPt;
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    mapRt, screenPos, null, out localPt))
                return new Vector2(TexSize * 0.5f, TexSize * 0.5f);
            var half = mapRt.sizeDelta * 0.5f;
            float u = (localPt.x + half.x) / (half.x * 2f);
            float v = (localPt.y + half.y) / (half.y * 2f);
            return new Vector2(u * TexSize, v * TexSize);
        }

        private bool IsInsideMap(Vector2 screenPos)
        {
            if (mapRt == null) return false;
            Vector2 localPt;
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    mapRt, screenPos, null, out localPt)) return false;
            var half = mapRt.sizeDelta * 0.5f;
            return Mathf.Abs(localPt.x) <= half.x &&
                   Mathf.Abs(localPt.y) <= half.y;
        }

        private bool IsPointerOverUI(Vector2 screenPos)
        {
            // conta solo le UI della mappa espansa (bottoni, pannelli):
            // la HUD sotto (zone joystick/orbit raycastTarget=true) non deve
            // inghiottire pan e tap sulla mappa.
            if (EventSystem.current == null || root == null) return false;
            var ped = new PointerEventData(EventSystem.current)
            {
                pointerId = -1,
                position = screenPos
            };
            var results = new List<RaycastResult>();
            EventSystem.current.RaycastAll(ped, results);
            foreach (var r in results)
                if (r.gameObject != null &&
                    r.gameObject.transform.IsChildOf(root.transform))
                    return true;
            return false;
        }

        private bool IsPointerOverUIMouse()
        {
            if (EventSystem.current == null || root == null) return false;
            var ped = new PointerEventData(EventSystem.current)
            {
                pointerId = -1,
                position = Input.mousePosition
            };
            var results = new List<RaycastResult>();
            EventSystem.current.RaycastAll(ped, results);
            foreach (var r in results)
                if (r.gameObject != null &&
                    r.gameObject.transform.IsChildOf(root.transform))
                    return true;
            return false;
        }

        // ───────────────────── tap → destination ─────────────────────
        private void HandleTap(Vector2 screenPos)
        {
            Vector2 localPt;
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    mapRt, screenPos, null, out localPt)) return;
            var half = mapRt.sizeDelta * 0.5f;
            if (Mathf.Abs(localPt.x) > half.x ||
                Mathf.Abs(localPt.y) > half.y) return;

            float span = Spans[zoomIdx];
            float mPerPx = span / TexSize;

            // u: 0 a sinistra → TexSize a destra (est+).
            // v: 0 in basso → TexSize in alto (nord+): coincide con il buffer
            // della mappa (che ha il nord in alto), cosi' il tap cade esattamente
            // dove appare il marker.
            float u = (localPt.x + half.x) / (half.x * 2f);
            float v = (localPt.y + half.y) / (half.y * 2f);

            Transform t = PlayerWorld();
            if (t == null) return;

            float dxM = ((u * TexSize) - TexSize * 0.5f) * mPerPx;
            float dzM = ((v * TexSize) - TexSize * 0.5f) * mPerPx;
            Vector3 world = t.position + new Vector3(
                panOffset.x + dxM, 0f, panOffset.y + dzM);
            GeoCoord g = WorldOrigin.ToGeo(world);

            var poi = VehiclePoiRegistry.NearestAny(g.lat, g.lng, 60.0);
            if (poi != null)
            {
                // mostra il pannello info: sceglie il giocatore se raggiungerlo
                ShowPoiInfo(poi);
            }
            else
            {
                if (poiPanel != null) poiPanel.SetActive(false);
                candidate = new NavigationState.Destination
                {
                    name = "Destinazione", kind = "",
                    lat = g.lat, lng = g.lng
                };
                goBtn.gameObject.SetActive(true);
            }
            dirty = true;
        }

        private static Transform PlayerWorld()
        {
            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            return mgr != null ? mgr.target : null;
        }

        private void ConfirmGo()
        {
            if (candidate == null) return;
            NavigationState.Set(candidate.name, candidate.kind,
                candidate.lat, candidate.lng);
            Hide();
        }

        // ───────────────────── rendering ─────────────────────
        private void Redraw()
        {
            if (_buf32 == null)
                _buf32 = new Color32[TexSize * TexSize];
            var cols = _buf32;
            var bg = (Color32)ColBg;
            for (int i = 0; i < cols.Length; i++) cols[i] = bg;

            Transform t = PlayerWorld();
            if (t == null)
            {
                tex.SetPixels32(cols);
                tex.Apply(false);
                return;
            }

            float span = Spans[zoomIdx];
            float halfSpan = span * 0.5f;
            float mPerPx = span / TexSize;
            Vector3 pw = t.position + new Vector3(
                panOffset.x, 0f, panOffset.y);

            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            if (mgr != null)
            {
                var seen = new HashSet<TileGeoDoc>();
                foreach (var c in mgr.BuiltChunks())
                {
                    if (c.geo == null || !seen.Add(c.geo)) continue;
                    DrawRoads(c.geo, pw, halfSpan, mPerPx, cols);
                    DrawBuildings(c.geo, pw, halfSpan, mPerPx, cols);
                    DrawParks(c.geo, pw, halfSpan, mPerPx, cols);
                }
            }

            DrawPois(pw, halfSpan, mPerPx, cols);
            DrawDestLine(pw, halfSpan, mPerPx, cols);
            DrawPlayer(pw, mPerPx, cols);

            tex.SetPixels32(cols);
            tex.Apply(false);

            UpdateInfo(pw);
        }

        private void UpdateInfo(Vector3 pw)
        {
            var d = NavigationState.Current;
            string dest = "";
            if (d != null)
            {
                float dist = Vector3.Distance(d.WorldPos, pw);
                dest = "\nDEST: " + d.name + " (" +
                    CompassUI.FormatDist(dist) + ")";
            }
            string cand = candidate == null ? ""
                : "\nSELEZIONATO: " + candidate.name;
            infoText.text = "Tocca la mappa per scegliere" + cand + dest;
        }

        private bool Px(Vector3 pWorld, float halfSpan, float mPerPx,
            Vector3 w, out int x, out int y)
        {
            float dx = w.x - pWorld.x;
            float dz = w.z - pWorld.z;
            x = Mathf.RoundToInt(TexSize * 0.5f + dx / mPerPx);
            y = Mathf.RoundToInt(TexSize * 0.5f + dz / mPerPx);
            return x > -20 && y > -20 && x < TexSize + 20 &&
                y < TexSize + 20;
        }

        private void DrawRoads(TileGeoDoc geo, Vector3 pWorld,
            float halfSpan, float mPerPx, Color32[] buf)
        {
            if (geo.roads == null) return;
            foreach (var r in geo.roads)
            {
                if (r?.pts == null || r.pts.Length < 2) continue;
                int wpx; Color col;
                switch (r.hw)
                {
                    case "motorway":
                    case "trunk":
                    case "primary":
                        wpx = 7; col = ColMajor; break;
                    case "secondary":
                    case "tertiary":
                        wpx = 4; col = ColMid; break;
                    default:
                        wpx = 2; col = ColMinor; break;
                }
                int ax, ay, bx, by;
                Vector3 wa = WorldOrigin.ToWorld(r.pts[0].a, r.pts[0].o);
                bool pa = Px(pWorld, halfSpan, mPerPx, wa, out ax, out ay);
                for (int i = 1; i < r.pts.Length; i++)
                {
                    Vector3 wb = WorldOrigin.ToWorld(
                        r.pts[i].a, r.pts[i].o);
                    bool pb = Px(pWorld, halfSpan, mPerPx, wb,
                        out bx, out by);
                    if (pa && pb) Line(ax, ay, bx, by, col, wpx, buf);
                    ax = bx; ay = by; pa = pb;
                }
            }
        }

        private static readonly Color ColBuilding = new Color(0.24f, 0.28f, 0.33f);
        private static readonly Color ColPark = new Color(0.28f, 0.48f, 0.32f);

        private void DrawBuildings(TileGeoDoc geo, Vector3 pWorld,
            float halfSpan, float mPerPx, Color32[] buf)
        {
            if (geo.buildings == null) return;
            foreach (var b in geo.buildings)
            {
                if (b?.c == null || b.c.Length < 2) continue;
                int x, y;
                Vector3 w = WorldOrigin.ToWorld(b.c[0], b.c[1]);
                if (!Px(pWorld, halfSpan, mPerPx, w, out x, out y)) continue;
                Fill(x - 1, y - 1, 2, 2, ColBuilding, buf);
            }
        }

        private void DrawParks(TileGeoDoc geo, Vector3 pWorld,
            float halfSpan, float mPerPx, Color32[] buf)
        {
            if (geo.parks == null) return;
            foreach (var p in geo.parks)
            {
                if (p?.poly == null || p.poly.Length < 3) continue;
                int ax = 0, ay = 0;
                bool havePrev = false;
                for (int i = 0; i <= p.poly.Length; i++)
                {
                    var ll = p.poly[i % p.poly.Length];
                    Vector3 w = WorldOrigin.ToWorld(ll.a, ll.o);
                    int px, py;
                    if (!Px(pWorld, halfSpan, mPerPx, w, out px, out py))
                    {
                        havePrev = false;
                        continue;
                    }
                    if (havePrev) Line(ax, ay, px, py, ColPark, 2, buf);
                    ax = px; ay = py; havePrev = true;
                }
            }
        }

        private void DrawPois(Vector3 pWorld, float halfSpan, float mPerPx,
            Color32[] buf)
        {
            // POI nascosti quando la mappa e' troppo "lontana": compaiono solo
            // zoomando (i due livelli di zoom piu' ravvicinati).
            if (Spans[zoomIdx] > PoiShowSpan) return;

            foreach (var poi in VehiclePoiRegistry.All())
            {
                Vector3 w = WorldOrigin.ToWorld(poi.lat, poi.lng);
                int x, y;
                if (!Px(pWorld, halfSpan, mPerPx, w, out x, out y)) continue;
                Color col = CompassUI.KindColor(poi.kind);
                Fill(x - 6, y - 6, 12, 12, col, buf);
                Fill(x - 3, y - 3, 6, 6, Color.white, buf);
            }

            if (candidate != null)
            {
                Vector3 cw = WorldOrigin.ToWorld(
                    candidate.lat, candidate.lng);
                int cx, cy;
                if (Px(pWorld, halfSpan, mPerPx, cw, out cx, out cy))
                    Ring(cx, cy, 26, ColDest, buf);
            }
        }

        private void DrawDestLine(Vector3 pWorld, float halfSpan,
            float mPerPx, Color32[] buf)
        {
            var d = NavigationState.Current;
            if (d == null) return;
            Vector3 dw = d.WorldPos;
            int ax, ay, bx, by;
            if (!Px(pWorld, halfSpan, mPerPx, dw, out bx, out by))
            {
                bx = TexSize / 2; by = TexSize / 2;
                Ring(bx, by, 20, ColDest, buf);
                return;
            }
            Ring(bx, by, 22, ColDest, buf);
            ax = TexSize / 2; ay = TexSize / 2;
            Dashed(ax, ay, bx, by, ColDest, 3, buf);
        }

        private void DrawPlayer(Vector3 pw, float mPerPx, Color32[] buf)
        {
            int cx = TexSize / 2, cy = TexSize / 2;
            Ring(cx, cy, Mathf.Max(6, Mathf.RoundToInt(8 / mPerPx)),
                Color.white, buf);
            Fill(cx - 5, cy - 5, 10, 10, ColPlayer, buf);
        }

        // ── primitive su buffer ──
        private static void Plot(int x, int y, Color c, Color32[] buf)
        {
            if (x < 0 || y < 0 || x >= TexSize || y >= TexSize) return;
            buf[y * TexSize + x] = c;
        }

        private static void Fill(int x0, int y0, int w, int h, Color c,
            Color32[] buf)
        {
            for (int j = y0; j < y0 + h; j++)
                for (int i = x0; i < x0 + w; i++)
                    Plot(i, j, c, buf);
        }

        private static void Line(int x0, int y0, int x1, int y1, Color c,
            int w, Color32[] buf)
        {
            int dx = Mathf.Abs(x1 - x0), dy = Mathf.Abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
            int err = dx - dy;
            int hw = Mathf.Max(0, w / 2);
            while (true)
            {
                for (int j = -hw; j <= hw; j++)
                    for (int i = -hw; i <= hw; i++)
                        Plot(x0 + i, y0 + j, c, buf);
                if (x0 == x1 && y0 == y1) break;
                int e2 = err * 2;
                if (e2 > -dy) { err -= dy; x0 += sx; }
                if (e2 < dx) { err += dx; y0 += sy; }
            }
        }

        private static void Dashed(int x0, int y0, int x1, int y1, Color c,
            int w, Color32[] buf)
        {
            int steps = Mathf.CeilToInt(
                Vector2.Distance(new Vector2(x0, y0), new Vector2(x1, y1)));
            if (steps < 1) return;
            for (int s = 0; s <= steps; s++)
            {
                if ((s / 14) % 2 != 0) continue;
                float f = s / (float)steps;
                int x = Mathf.RoundToInt(Mathf.Lerp(x0, x1, f));
                int y = Mathf.RoundToInt(Mathf.Lerp(y0, y1, f));
                for (int j = -w / 2; j <= w / 2; j++)
                    for (int i = -w / 2; i <= w / 2; i++)
                        Plot(x + i, y + j, c, buf);
            }
        }

        private static void Ring(int cx, int cy, int r, Color c,
            Color32[] buf)
        {
            int prevX = cx + r, prevY = cy;
            for (int a = 1; a <= 48; a++)
            {
                float ang = a / 48f * Mathf.PI * 2f;
                int nx = cx + Mathf.RoundToInt(Mathf.Cos(ang) * r);
                int ny = cy + Mathf.RoundToInt(Mathf.Sin(ang) * r);
                if (nx != prevX || ny != prevY)
                {
                    Line(prevX, prevY, nx, ny, c, 3, buf);
                    prevX = nx; prevY = ny;
                }
            }
        }
    }
}
