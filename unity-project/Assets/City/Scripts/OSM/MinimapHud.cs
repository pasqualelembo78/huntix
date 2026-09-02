using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using City.Vehicle;
using City.Player;

namespace City.OSM
{
    /// <summary>
    /// Minimap nord-su fissa in alto a destra: span variabile (zoom) attorno
    /// al giocatore, disegnata dalle tile gia cariche. Due livelli:
    ///  - statico (strade/edifici/parchi/cornice): ridisegnato a 0,75 s e al
    ///    cambio zoom, memorizzato in _staticBuf;
    ///  - dinamico (POI, veicoli, destinazione, player): ridisegnato a ~16 Hz
    ///    sopra il livello statico, cosi il pallino del giocatore e la tacca
    ///    di direzione scorrono fluidi senza aspettare il refresh della mappa.
    /// Toggle categorie, zoom +/- e pinch sull area mappa (mai la camera),
    /// modalita rotazione (R) con la mappa che ruota seguendo il player.
    /// Tacca di direzione = forward del PlayerController (direzione di marcia),
    /// non piu lo yaw della camera.
    /// </summary>
    public class MinimapHud : MonoBehaviour
    {
        private const int TexSize = 512;
        private static readonly float[] Spans = { 3200f, 1600f, 800f, 400f };
        private const float RefreshSeconds = 0.75f;
        private const float DynamicSeconds = 0.06f;

        /// <summary>Istanza per i blocchi di zoom della camera di gioco.</summary>
        public static MinimapHud Instance { get; private set; }

        private RawImage _img;
        private Texture2D _tex;
        private Color[] _buf;
        private Color[] _staticBuf;
        private float _nextStatic, _nextDynamic;
        private int _zoomIdx;
        private int _staticZoomIdx = -1;

        // modalita rotazione (R): mappa ruota seguendo il forward del player
        private bool _rotFollow;
        private float _rotYaw;
        private float _rotFwdX, _rotFwdZ;

        // pinch sull area minimappa: zoom della minimappa, NON della camera
        private bool _pinchOn;
        private float _pinchStartDist;
        private int _pinchStartZoom;
        private float _lastPinchTime;

        // visibilita categorie (toggle utente)
        private bool _showDealer = true;
        private bool _showRepair = true;
        private bool _showGarage = true;
        private bool _showHospital = true;
        private bool _showSchool = true;
        private bool _showBar = true;
        private bool _showRamp = true;
        private Text _lblDealer, _lblRepair, _lblGarage, _lblHospital;
        private Text _lblSchool, _lblBar, _lblRamp;

        private Text _lblDest;
        private GameObject _lblNord;

        // clustering POI: celle da 7 px, il primo POI vince, i vicini saltano
        private readonly HashSet<long> _poiCells = new HashSet<long>();
        private const int PoiCell = 7;

        // palette
        private static readonly Color ColBg = new Color(0.06f, 0.08f, 0.10f, 0.82f);
        private static readonly Color ColBorder = new Color(0.75f, 0.78f, 0.82f, 0.9f);
        private static readonly Color ColMajor = new Color(0.92f, 0.94f, 0.96f);
        private static readonly Color ColMid = new Color(0.68f, 0.73f, 0.78f);
        private static readonly Color ColMinor = new Color(0.45f, 0.50f, 0.56f);
        private static readonly Color ColPark = new Color(0.28f, 0.48f, 0.32f);
        private static readonly Color ColBuilding = new Color(0.24f, 0.28f, 0.33f);
        private static readonly Color ColPlayer = new Color(0.95f, 0.15f, 0.12f);
        private static readonly Color ColRing = new Color(1f, 1f, 1f);
        private static readonly Color ColCar = new Color(0.20f, 0.85f, 0.95f);

        public static void Create()
        {
            var go = new GameObject("MinimapHud", typeof(MinimapHud));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            Instance = this;
            _buf = new Color[TexSize * TexSize];
            _staticBuf = new Color[TexSize * TexSize];
            _tex = new Texture2D(TexSize, TexSize, TextureFormat.RGBA32, false);
            _tex.filterMode = FilterMode.Bilinear;

            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem",
                    typeof(EventSystem), typeof(StandaloneInputModule));
            }

            var canvasGo = new GameObject("MiniCanvas", typeof(Canvas),
                typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvasGo.transform.SetParent(transform, false);
            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            // sopra la HUD principale (UIManager a sortingOrder 10) cosi il
            // tap sulla minimappa non viene rubato dalla orbit-zone (destra
            // 45%, pieno schermo, raycastTarget=true) che cattura il tocco.
            canvas.sortingOrder = 20;
            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080, 1920);

            var imgGo = new GameObject("Mappa", typeof(RawImage));
            imgGo.transform.SetParent(canvasGo.transform, false);
            _img = imgGo.GetComponent<RawImage>();
            _img.raycastTarget = true;
            _img.texture = _tex;

            RectTransform rt = _img.rectTransform;
            rt.anchorMin = new Vector2(1f, 1f);
            rt.anchorMax = new Vector2(1f, 1f);
            rt.pivot = new Vector2(1f, 1f);
            // spostata piu in basso per non sovrapporsi ai pulsanti ESCI e
            // "GUARDA VIDEO" (in alto a destra sulla HUD a sortingOrder 10).
            rt.anchoredPosition = new Vector2(-14f, -150f);
            rt.sizeDelta = new Vector2(420f, 420f);

            var nGo = new GameObject("Nord", typeof(Text));
            nGo.transform.SetParent(imgGo.transform, false);
            _lblNord = nGo;
            var nl = nGo.GetComponent<Text>();
            nl.font = UiFont();
            if (nl.font != null)
            {
                nl.text = "N";
                nl.fontSize = 30;
                nl.color = new Color(0.9f, 0.93f, 0.95f);
                nl.alignment = TextAnchor.UpperCenter;
                RectTransform nrt = nl.rectTransform;
                nrt.anchorMin = new Vector2(0.5f, 1f);
                nrt.anchorMax = new Vector2(0.5f, 1f);
                nrt.pivot = new Vector2(0.5f, 1f);
                nrt.anchoredPosition = new Vector2(0f, -4f);
                nrt.sizeDelta = new Vector2(60f, 40f);
            }

            BuildControls(imgGo);
            _rotYaw = PlayerYaw();
        }

        // toggle categorie + zoom + rotazione
        private void BuildControls(GameObject mapImg)
        {
            // riga toggle in basso a sinistra della mappa
            _lblDealer = MakeToggle(mapImg, "C", CompassUI.ColDealer,
                new Vector2(12f, 12f), () =>
                {
                    _showDealer = !_showDealer;
                    return _showDealer;
                });
            _lblRepair = MakeToggle(mapImg, "O", CompassUI.ColRepair,
                new Vector2(64f, 12f), () =>
                {
                    _showRepair = !_showRepair;
                    return _showRepair;
                });
            _lblGarage = MakeToggle(mapImg, "G", CompassUI.ColGarage,
                new Vector2(116f, 12f), () =>
                {
                    _showGarage = !_showGarage;
                    return _showGarage;
                });
            _lblHospital = MakeToggle(mapImg, "H", CompassUI.ColHospital,
                new Vector2(168f, 12f), () =>
                {
                    _showHospital = !_showHospital;
                    return _showHospital;
                });
            _lblSchool = MakeToggle(mapImg, "S", CompassUI.ColSchool,
                new Vector2(12f, 64f), () =>
                {
                    _showSchool = !_showSchool;
                    return _showSchool;
                });
            _lblBar = MakeToggle(mapImg, "B", CompassUI.ColBar,
                new Vector2(64f, 64f), () =>
                {
                    _showBar = !_showBar;
                    return _showBar;
                });
            _lblRamp = MakeToggle(mapImg, "R",
                CompassUI.KindColor("rampa"),
                new Vector2(116f, 64f), () =>
                {
                    _showRamp = !_showRamp;
                    return _showRamp;
                });

            // tap sulla mappa = apri la mappa espansa di selezione
            var openBtn = mapImg.AddComponent<Button>();
            // la mappa e una RawImage (Graphic), non un Image: usa la
            // RawImage stessa come targetGraphic altrimenti resta null.
            openBtn.targetGraphic = _img;
            openBtn.navigation = new UnityEngine.UI.Navigation
            {
                mode = UnityEngine.UI.Navigation.Mode.None
            };
            openBtn.onClick.AddListener(() =>
            {
                // dopo un pinch (zoom con due dita) il rilascio delle dita
                // non deve aprire la mappa espansa per sbaglio
                if (Time.unscaledTime - _lastPinchTime < 0.5f) return;
                // mappa espansa gia aperta: il bottone minimappa sta sotto
                // l overlay (Dim/mappa non sono raycastable) e i suoi tocchi
                // devono passare alla mappa, non riaprirla/resettarla
                if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen) return;
                try { MapSelectUI.Open(); }
                catch (System.Exception e) { Debug.LogException(e); }
            });

            // zoom +/- in basso a destra
            MakeBtn(mapImg, "-", new Vector2(-116f, 12f), () =>
            {
                if (_zoomIdx < Spans.Length - 1) _zoomIdx++;
            });
            MakeBtn(mapImg, "+", new Vector2(-64f, 12f), () =>
            {
                if (_zoomIdx > 0) _zoomIdx--;
            });
            // rotazione: la mappa segue il forward del player (toggle R)
            MakeBtn(mapImg, "R", new Vector2(-168f, 12f), () =>
            {
                _rotFollow = !_rotFollow;
                if (_lblNord != null) _lblNord.SetActive(!_rotFollow);
            });

            // etichetta distanza destinazione fuori dallo span (sotto la N)
            var dgo = new GameObject("DestText", typeof(Text));
            dgo.transform.SetParent(mapImg.transform, false);
            _lblDest = dgo.GetComponent<Text>();
            _lblDest.font = UiFont();
            _lblDest.fontSize = 26;
            _lblDest.alignment = TextAnchor.MiddleCenter;
            _lblDest.raycastTarget = false;
            _lblDest.color = CompassUI.ColDest;
            RectTransform drt = _lblDest.rectTransform;
            drt.anchorMin = new Vector2(0.5f, 1f);
            drt.anchorMax = new Vector2(0.5f, 1f);
            drt.pivot = new Vector2(0.5f, 1f);
            drt.anchoredPosition = new Vector2(0f, -52f);
            drt.sizeDelta = new Vector2(380f, 40f);
        }

        private Text MakeToggle(GameObject parent, string caption, Color col,
            Vector2 pos, System.Func<bool> onToggle)
        {
            var go = new GameObject("Tgl_" + caption, typeof(Button));
            go.transform.SetParent(parent.transform, false);
            var img = go.AddComponent<Image>();
            img.raycastTarget = true;
            img.color = new Color(0.08f, 0.10f, 0.13f, 0.85f);

            var btn = go.GetComponent<Button>();
            bool state = true;
            btn.onClick.AddListener(() => { state = onToggle(); });

            var lgo = new GameObject("T", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            var t = lgo.GetComponent<Text>();
            t.font = UiFont();
            t.fontSize = 26;
            t.text = caption;
            t.alignment = TextAnchor.MiddleCenter;
            t.raycastTarget = false;
            t.color = col;
            var rt = t.rectTransform;
            rt.anchorMin = Vector2.zero; rt.anchorMax = Vector2.one;
            rt.sizeDelta = Vector2.zero;

            btn.onClick.AddListener(() => { t.color = state ? col : Dim(col); });
            var grt = go.GetComponent<RectTransform>();
            grt.anchorMin = new Vector2(0f, 0f);
            grt.anchorMax = new Vector2(0f, 0f);
            grt.pivot = new Vector2(0f, 0f);
            grt.anchoredPosition = pos;
            grt.sizeDelta = new Vector2(44f, 44f);
            return t;
        }

        private void MakeBtn(GameObject parent, string caption, Vector2 pos,
            System.Action onClick)
        {
            var go = new GameObject("Btn_" + caption, typeof(Button));
            go.transform.SetParent(parent.transform, false);
            var img = go.AddComponent<Image>();
            img.raycastTarget = true;
            img.color = new Color(0.08f, 0.10f, 0.13f, 0.85f);

            var lgo = new GameObject("T", typeof(Text));
            lgo.transform.SetParent(go.transform, false);
            var t = lgo.GetComponent<Text>();
            t.font = UiFont();
            t.fontSize = 30;
            t.text = caption;
            t.alignment = TextAnchor.MiddleCenter;
            t.raycastTarget = false;
            t.color = new Color(0.9f, 0.93f, 0.95f);
            var trt = t.rectTransform;
            trt.anchorMin = Vector2.zero; trt.anchorMax = Vector2.one;
            trt.sizeDelta = Vector2.zero;

            var btn = go.GetComponent<Button>();
            btn.onClick.AddListener(() => onClick());

            var grt = go.GetComponent<RectTransform>();
            grt.anchorMin = new Vector2(1f, 0f);
            grt.anchorMax = new Vector2(1f, 0f);
            grt.pivot = new Vector2(0f, 0f);
            grt.anchoredPosition = pos;
            grt.sizeDelta = new Vector2(44f, 44f);
        }

        private static Color Dim(Color c)
        {
            return new Color(c.r * 0.4f + 0.1f, c.g * 0.4f + 0.1f,
                c.b * 0.4f + 0.1f, 0.5f);
        }

        private static Font UiFont()
        {
            try { return Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { }
            try { return Resources.GetBuiltinResource<Font>("Arial.ttf"); }
            catch { }
            return null;
        }

        private void Update()
        {
            // non disegnare quando la mappa espansa e' aperta (sortingOrder
            // 100 > 20) o quando la minimappa non e' visibile
            if (_img == null || !_img.gameObject.activeInHierarchy) return;
            if (MapSelectUI.Instance != null && MapSelectUI.Instance.IsOpen) return;

            HandlePinch();
            float dt = Time.unscaledDeltaTime;
            _rotYaw = Mathf.LerpAngle(_rotYaw, PlayerYaw(), 10f * dt);
            float yawRad = _rotYaw * Mathf.Deg2Rad;
            _rotFwdX = Mathf.Sin(yawRad);
            _rotFwdZ = Mathf.Cos(yawRad);

            if (_zoomIdx != _staticZoomIdx || Time.unscaledTime >= _nextStatic)
            {
                // con modalita rotazione (R) le strade devono seguire
                // il player fluidamente: refresh statico piu' frequente
                _nextStatic = Time.unscaledTime +
                    (_rotFollow ? DynamicSeconds : RefreshSeconds);
                RedrawStatic();
            }
            if (Time.unscaledTime >= _nextDynamic)
            {
                _nextDynamic = Time.unscaledTime + DynamicSeconds;
                RedrawDynamic();
            }
        }

        /// <summary>Yaw (gradi) della direzione di marcia, forward del
        /// PlayerController; reculo sullo yaw della camera.</summary>
        private float PlayerYaw()
        {
            var pc = PlayerController.Instance;
            if (pc != null && pc.transform != null)
            {
                Vector3 fwd = pc.transform.forward;
                if (fwd.sqrMagnitude > 0.0001f)
                    return Mathf.Atan2(fwd.x, fwd.z) * Mathf.Rad2Deg;
            }
            var cam = Camera.main;
            if (cam != null) return cam.transform.eulerAngles.y;
            return 0f;
        }

        /// <summary>
        /// Pinch con entrambe le dita sull area minimappa: cambia lo zoom
        /// della minimappa (come i bottoni + e -), mai quello della camera.
        /// </summary>
        private void HandlePinch()
        {
            if (Input.touchCount != 2)
            {
                _pinchOn = false;
                return;
            }
            Touch t0 = Input.GetTouch(0), t1 = Input.GetTouch(1);
            if (t0.phase == TouchPhase.Began || t1.phase == TouchPhase.Began)
            {
                if (!RectOver(t0.position) || !RectOver(t1.position))
                {
                    _pinchOn = false;
                    return;
                }
                _pinchStartDist = Vector2.Distance(t0.position, t1.position);
                _pinchStartZoom = _zoomIdx;
                _pinchOn = true;
                _lastPinchTime = Time.unscaledTime;
                return;
            }
            if (!_pinchOn) return;
            if (t0.phase != TouchPhase.Moved && t1.phase != TouchPhase.Moved)
                return;
            _lastPinchTime = Time.unscaledTime;
            float dist = Vector2.Distance(t0.position, t1.position);
            int idx = _pinchStartZoom + Mathf.RoundToInt(
                (_pinchStartDist - dist) / (Spans.Length * 12f));
            _zoomIdx = Mathf.Clamp(idx, 0, Spans.Length - 1);
        }

        /// <summary>
        /// True se almeno un tocco del pinch cade sull area minimappa: la
        /// CameraRig lo usa per NON zoomare la camera contemporaneamente.
        /// </summary>
        public static bool SwallowPinch()
        {
            var inst = Instance;
            if (inst == null || inst._img == null) return false;
            if (Input.touchCount != 2) return false;
            return inst.RectOver(Input.GetTouch(0).position) ||
                inst.RectOver(Input.GetTouch(1).position);
        }

        private bool RectOver(Vector2 screenPos)
        {
            if (_img == null || _img.rectTransform == null) return false;
            // canvas overlay: position in pixel schermo (stessa convenzione
            // di MapSelectUI.IsInsideMap)
            var rt = _img.rectTransform;
            var c = rt.position;
            var half = rt.sizeDelta * 0.5f;
            return Mathf.Abs(screenPos.x - c.x) <= half.x &&
                Mathf.Abs(screenPos.y - c.y) <= half.y;
        }

        // livello statico: strade/edifici/parchi
        private void RedrawStatic()
        {
            _staticZoomIdx = _zoomIdx;
            Fill(_staticBuf, ColBg);

            Transform t = null;
            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            if (mgr != null) t = mgr.target;

            if (t == null)
            {
                DrawBorder(_staticBuf);
                CommitStatic();
                return;
            }

            float span = Spans[_zoomIdx];
            float half = span * 0.5f;
            float mPerPx = span / TexSize;
            var seen = new HashSet<TileGeoDoc>();

            foreach (var c in mgr.BuiltChunks())
            {
                if (c.geo == null || !seen.Add(c.geo)) continue;
                DrawParks(_staticBuf, c.geo, t.position, half, mPerPx);
                DrawRoads(_staticBuf, c.geo, t.position, half, mPerPx);
                DrawBuildings(_staticBuf, c.geo, t.position, half, mPerPx);
            }

            DrawBorder(_staticBuf);
            CommitStatic();
        }

        // livello dinamico: POI, veicoli, destinazione, player
        private void RedrawDynamic()
        {
            System.Array.Copy(_staticBuf, _buf, _buf.Length);
            _poiCells.Clear();

            Transform t = null;
            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            if (mgr != null) t = mgr.target;
            if (t == null)
            {
                DrawBorder(_buf);
                Commit();
                return;
            }

            float span = Spans[_zoomIdx];
            float half = span * 0.5f;
            float mPerPx = span / TexSize;
            Vector3 p = t.position;

            DrawPois(p, half, mPerPx);
            DrawOwnedVehicles(p, half, mPerPx);
            DrawDestination(p, half, mPerPx);
            DrawPlayer(t);
            DrawBorder(_buf);
            Commit();
        }

        private void CommitStatic()
        {
            System.Array.Copy(_staticBuf, _buf, _buf.Length);
            Commit();
        }

        // conversioni.
        // Ritorna coordinate pixel; out ref = metri relativi al player.
        // La vista-culling sulle dimensioni avviene in metri (indipendente
        // dalla rotazione); solo dopo, se _rotFollow, le coordinate vengono
        // ruotate in modo che il forward del player punti verso l alto.
        private bool ToPx(GeoLL ll, Vector3 pWorld, float half, float mPerPx,
            out int x, out int y)
        {
            Vector3 w = WorldOrigin.ToWorld(ll.a, ll.o);
            float dx = w.x - pWorld.x;
            float dz = w.z - pWorld.z;
            if (dx < -half || dx > half || dz < -half || dz > half)
            { x = y = 0; return false; }
            if (_rotFollow)
            {
                float c = _rotFwdZ, s = _rotFwdX;
                float rx = dx * c - dz * s;
                float rz = dx * s + dz * c;
                dx = rx; dz = rz;
            }
            x = Mathf.RoundToInt(TexSize * 0.5f + dx / mPerPx);
            y = Mathf.RoundToInt(TexSize * 0.5f + dz / mPerPx); // davanti = alto
            return x >= -8 && y >= -8 && x < TexSize + 8 && y < TexSize + 8;
        }

        private void DrawRoads(Color[] buf, TileGeoDoc geo, Vector3 pWorld,
            float half, float mPerPx)
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
                        wpx = 5; col = ColMajor; break;
                    case "secondary":
                    case "tertiary":
                        wpx = 3; col = ColMid; break;
                    default:
                        wpx = 2; col = ColMinor; break;
                }
                int ax, ay, bx, by;
                bool pa = ToPx(r.pts[0], pWorld, half, mPerPx, out ax, out ay);
                for (int i = 1; i < r.pts.Length; i++)
                {
                    bool pb = ToPx(r.pts[i], pWorld, half, mPerPx,
                        out bx, out by);
                    if (pa && pb) Line(buf, ax, ay, bx, by, col, wpx);
                    ax = bx; ay = by; pa = pb;
                }
            }
        }

        private void DrawBuildings(Color[] buf, TileGeoDoc geo, Vector3 pWorld,
            float half, float mPerPx)
        {
            if (geo.buildings == null) return;
            foreach (var b in geo.buildings)
            {
                if (b?.c == null || b.c.Length < 2) continue;
                int x, y;
                if (!ToPx(new GeoLL { a = b.c[0], o = b.c[1] },
                        pWorld, half, mPerPx, out x, out y)) continue;
                Plot(buf, x, y, ColBuilding, 2);
            }
        }

        private void DrawParks(Color[] buf, TileGeoDoc geo, Vector3 pWorld,
            float half, float mPerPx)
        {
            if (geo.parks == null) return;
            foreach (var p in geo.parks)
            {
                if (p?.poly == null || p.poly.Length < 3) continue;
                int ax = 0, ay = 0;
                bool havePrev = false;
                // i % len chiude il poligono tornando al primo punto valido
                for (int i = 0; i <= p.poly.Length; i++)
                {
                    var ll = p.poly[i % p.poly.Length];
                    int px, py;
                    if (!ToPx(ll, pWorld, half, mPerPx, out px, out py))
                    {
                        havePrev = false;   // spezza il contorno fuori mappa
                        continue;
                    }
                    if (havePrev) Line(buf, ax, ay, px, py, ColPark, 2);
                    ax = px; ay = py; havePrev = true;
                }
            }
        }

        /// <summary>
        /// Marker POI veicoli. La vista-culling e implicita: ToPx scarta
        /// tutto cio che cade fuori dallo span corrente. Filtro contestuale:
        /// garage solo se possiedo un veicolo, officina solo se condizione
        /// sotto soglia; concessionaria sempre. Cluster: se due POI cadono
        /// nella stessa cella da 7 px il primo disegnato vince e l altro
        /// viene saltato (niente pitoni sovrapposti).
        /// </summary>
        private void DrawPois(Vector3 pWorld, float half, float mPerPx)
        {
            DrawPoiKind("dealer", _showDealer, CompassUI.ColDealer,
                pWorld, half, mPerPx);
            bool repairOk = VehicleOwnershipApi.HasOwnedCached() &&
                VehicleOwnershipApi.WorstConditionCached()
                    < CompassUI.RepairVisThreshold;
            DrawPoiKind("repair", _showRepair && repairOk,
                CompassUI.ColRepair, pWorld, half, mPerPx);
            DrawPoiKind("garage", _showGarage &&
                VehicleOwnershipApi.HasOwnedCached(),
                CompassUI.ColGarage, pWorld, half, mPerPx);
            DrawPoiKind("hospital", _showHospital, CompassUI.ColHospital,
                pWorld, half, mPerPx);
            DrawPoiKind("school", _showSchool, CompassUI.ColSchool,
                pWorld, half, mPerPx);
            DrawPoiKind("bar", _showBar, CompassUI.ColBar,
                pWorld, half, mPerPx);
            DrawPoiKind("rampa", _showRamp, CompassUI.KindColor("rampa"),
                pWorld, half, mPerPx);
        }

        private void DrawPoiKind(string kind, bool visible, Color col,
            Vector3 pWorld, float half, float mPerPx)
        {
            if (!visible) return;
            foreach (var p in VehiclePoiRegistry.AllOf(kind))
            {
                int x, y;
                if (!ToPx(new GeoLL { a = p.lat, o = p.lng },
                        pWorld, half, mPerPx, out x, out y)) continue;
                if (!ClaimCell(x, y)) continue; // clustering
                Plot(_buf, x, y, new Color(0f, 0f, 0f, 0.9f), 6); // bordo
                Plot(_buf, x, y, col, 4);                         // punto
            }
        }

        /// <summary>Auto parcheggiate di mia proprieta (non in garage e non
        /// rubate): quadratino cyan con tacca di heading, utile per ritrovare
        /// il veicolo a span stretti.</summary>
        private void DrawOwnedVehicles(Vector3 pWorld, float half,
            float mPerPx)
        {
            foreach (var kv in VehicleOwnershipApi.SoldSnapshot())
            {
                var pv = kv.Value;
                if (pv == null || pv.owner != VehicleOwnershipApi.PlayerId)
                    continue;
                if (pv.inGarage || pv.stolen) continue;
                int x, y;
                if (!ToPx(new GeoLL { a = pv.lat, o = pv.lon },
                        pWorld, half, mPerPx, out x, out y)) continue;
                if (!ClaimCell(x, y)) continue;
                Plot(_buf, x, y, new Color(0f, 0f, 0f, 0.9f), 7);
                Plot(_buf, x, y, ColCar, 5);
                if (pv.heading != 0)
                {
                    float h = (float)(pv.heading * System.Math.PI / 180.0);
                    int hx = x + Mathf.RoundToInt(Mathf.Sin(h) * 10f);
                    int hy = y + Mathf.RoundToInt(Mathf.Cos(h) * 10f);
                    Line(_buf, x, y, hx, hy, ColCar, 2);
                }
            }
        }

        /// <summary>Prenota la cella di clustering del POI; false se gia
        /// occupata da un POI disegnato prima (priorita = ordine di disegno).</summary>
        private bool ClaimCell(int x, int y)
        {
            long key = ((long)((x + TexSize) / PoiCell)) * 4096L
                + (long)((y + TexSize) / PoiCell);
            return _poiCells.Add(key);
        }

        /// <summary>Destinazione attiva: anello oro + linea tratteggiata.
        /// Fuori dallo span: freccia sul bordo + distanza in metri.</summary>
        private void DrawDestination(Vector3 pWorld, float half, float mPerPx)
        {
            var d = NavigationState.Current;
            if (d == null)
            {
                if (_lblDest != null) _lblDest.text = "";
                return;
            }
            int bx, by;
            if (!ToPx(new GeoLL { a = d.lat, o = d.lng },
                    pWorld, half, mPerPx, out bx, out by))
            {
                Vector3 w = WorldOrigin.ToWorld(d.lat, d.lng);
                Vector2 dir = new Vector2(w.x - pWorld.x, w.z - pWorld.z);
                float dist = dir.magnitude;
                if (dist > 1e-6f) dir /= dist;
                if (_rotFollow)
                {
                    float c = _rotFwdZ, s = _rotFwdX;
                    float rx = dir.x * c - dir.y * s;
                    float ry = dir.x * s + dir.y * c;
                    dir = new Vector2(rx, ry);
                }
                int margin = 30;
                int ex = TexSize / 2 + Mathf.RoundToInt(dir.x *
                    (TexSize * 0.5f - margin));
                int ey = TexSize / 2 + Mathf.RoundToInt(dir.y *
                    (TexSize * 0.5f - margin));
                Circle(_buf, ex, ey, 9, CompassUI.ColDest);
                Circle(_buf, ex, ey, 5, new Color(0.15f, 0.12f, 0f));
                DashedLine(_buf, TexSize / 2, TexSize / 2, ex, ey,
                    CompassUI.ColDest);
                if (_lblDest != null)
                {
                    if (dist >= 1000f)
                        _lblDest.text = string.Format("{0:0.0} km",
                            dist / 1000f);
                    else
                        _lblDest.text = string.Format("{0:0} m", dist);
                }
                return;
            }
            Circle(_buf, bx, by, 8, CompassUI.ColDest);
            Circle(_buf, bx, by, 6, new Color(0.15f, 0.12f, 0f));
            DashedLine(_buf, TexSize / 2, TexSize / 2, bx, by,
                CompassUI.ColDest);
            if (_lblDest != null) _lblDest.text = "";
        }

        private void DashedLine(Color[] buf, int x0, int y0, int x1, int y1,
            Color c)
        {
            int steps = Mathf.CeilToInt(Mathf.Sqrt(
                (x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)));
            if (steps < 1) return;
            for (int s = 0; s <= steps; s += 2)
            {
                if ((s / 9) % 2 != 0) continue;
                float f = s / (float)steps;
                Plot(buf, Mathf.RoundToInt(Mathf.Lerp(x0, x1, f)),
                    Mathf.RoundToInt(Mathf.Lerp(y0, y1, f)), c, 3);
            }
        }

        private void DrawPlayer(Transform t)
        {
            const int cx = TexSize / 2, cy = TexSize / 2;
            // tacca direzione marcia: in modalita nord-su segue il forward
            // del player; in modalita rotazione e sempre verso l alto.
            float yaw = _rotFollow ? 0f : _rotYaw * Mathf.Deg2Rad;
            // forward Unity con yaw: dir = (sin(yaw), cos(yaw)) in (est, nord)
            int tx = cx + Mathf.RoundToInt(Mathf.Sin(yaw) * 16f);
            int ty = cy + Mathf.RoundToInt(Mathf.Cos(yaw) * 16f);
            Line(_buf, cx, cy, tx, ty, ColRing, 3);
            Circle(_buf, cx, cy, 6, ColRing);
            Circle(_buf, cx, cy, 5, ColPlayer);
        }

        private void DrawBorder(Color[] buf)
        {
            for (int i = 0; i < TexSize; i++)
            {
                Plot(buf, i, 0, ColBorder, 1);
                Plot(buf, i, TexSize - 1, ColBorder, 1);
                Plot(buf, 0, i, ColBorder, 1);
                Plot(buf, TexSize - 1, i, ColBorder, 1);
            }
        }

        // primitive raster
        private void Fill(Color[] buf, Color c)
        {
            for (int i = 0; i < buf.Length; i++) buf[i] = c;
        }

        private void Commit()
        {
            _tex.SetPixels(_buf);
            _tex.Apply(false);
        }

        private void Plot(Color[] buf, int x, int y, Color c, int w)
        {
            int h = w / 2;
            for (int dy = -h; dy <= h; dy++)
                for (int dx = -h; dx <= h; dx++)
                {
                    int px = x + dx, py = y + dy;
                    if (px < 0 || py < 0 || px >= TexSize || py >= TexSize) continue;
                    buf[py * TexSize + px] = c;
                }
        }

        private void Line(Color[] buf, int x0, int y0, int x1, int y1,
            Color c, int w)
        {
            int dx = Mathf.Abs(x1 - x0), dy = Mathf.Abs(y1 - y0);
            int steps = dx > dy ? dx : dy;
            if (steps == 0) { Plot(buf, x0, y0, c, w); return; }
            for (int i = 0; i <= steps; i++)
            {
                int x = Mathf.RoundToInt(Mathf.Lerp(x0, x1, (float)i / steps));
                int y = Mathf.RoundToInt(Mathf.Lerp(y0, y1, (float)i / steps));
                Plot(buf, x, y, c, w);
            }
        }

        private void Circle(Color[] buf, int cx, int cy, int r, Color c)
        {
            int r2 = r * r;
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                {
                    if (dx * dx + dy * dy > r2) continue;
                    int px = cx + dx, py = cy + dy;
                    if (px < 0 || py < 0 || px >= TexSize || py >= TexSize) continue;
                    buf[py * TexSize + px] = c;
                }
        }
    }
}
