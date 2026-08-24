using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace City.OSM
{
    /// <summary>
    /// Minimap nord-su fissa in alto a destra: ~10 km^2 (span 3200 m) attorno
    /// al giocatore, disegnata dalle tile gia' caricate. Il personaggio e' il
    /// pallino rosso al centro, con tacca di direzione camera. Aggiornata a
    /// intervalli regolari su texture 512 px (costo CPU trascurabile).
    /// </summary>
    public class MinimapHud : MonoBehaviour
    {
        private const int TexSize = 512;
        private const float SpanMeters = 3200f;      // ≈ 10,24 km²
        private const float RefreshSeconds = 0.75f;

        private RawImage _img;
        private Texture2D _tex;
        private Color[] _buf;
        private float _next;
        private float _mPerPx = SpanMeters / TexSize;

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

        public static void Create()
        {
            var go = new GameObject("MinimapHud", typeof(MinimapHud));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            _buf = new Color[TexSize * TexSize];
            _tex = new Texture2D(TexSize, TexSize, TextureFormat.RGBA32, false);
            _tex.filterMode = FilterMode.Bilinear;

            var canvasGo = new GameObject("MiniCanvas", typeof(Canvas));
            canvasGo.transform.SetParent(transform, false);
            canvasGo.GetComponent<Canvas>().renderMode =
                RenderMode.ScreenSpaceOverlay;

            var imgGo = new GameObject("Mappa", typeof(RawImage));
            imgGo.transform.SetParent(canvasGo.transform, false);
            _img = imgGo.GetComponent<RawImage>();
            _img.texture = _tex;

            RectTransform rt = _img.rectTransform;
            rt.anchorMin = new Vector2(1f, 1f);
            rt.anchorMax = new Vector2(1f, 1f);
            rt.pivot = new Vector2(1f, 1f);
            rt.anchoredPosition = new Vector2(-14f, -14f);
            rt.sizeDelta = new Vector2(420f, 420f);

            var nGo = new GameObject("Nord", typeof(Text));
            nGo.transform.SetParent(imgGo.transform, false);
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
            if (Time.unscaledTime < _next) return;
            _next = Time.unscaledTime + RefreshSeconds;
            Redraw();
        }

        private void Redraw()
        {
            Fill(ColBg);

            Transform t = null;
            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            if (mgr != null) t = mgr.target;

            if (t == null)
            {
                DrawBorder();
                Commit();
                return;
            }

            float half = SpanMeters * 0.5f;
            var seen = new HashSet<TileGeoDoc>();

            foreach (var c in mgr.BuiltChunks())
            {
                if (c.geo == null || !seen.Add(c.geo)) continue;
                DrawParks(c.geo, t.position, half);
                DrawRoads(c.geo, t.position, half);
                DrawBuildings(c.geo, t.position, half);
            }

            DrawPlayer(t);
            DrawBorder();
            Commit();
        }

        // ── conversioni ──
        // ritorna coordinate pixel; out rel = metri relativi al player
        private bool ToPx(GeoLL ll, Vector3 pWorld, float half,
            out int x, out int y)
        {
            Vector3 w = WorldOrigin.ToWorld(ll.a, ll.o);
            float dx = w.x - pWorld.x;
            float dz = w.z - pWorld.z;
            if (dx < -half || dx > half || dz < -half || dz > half)
            { x = y = 0; return false; }
            x = Mathf.RoundToInt(TexSize * 0.5f + dx / _mPerPx);
            y = Mathf.RoundToInt(TexSize * 0.5f + dz / _mPerPx); // nord = alto
            return x >= -8 && y >= -8 && x < TexSize + 8 && y < TexSize + 8;
        }

        private void DrawRoads(TileGeoDoc geo, Vector3 pWorld, float half)
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
                bool pa = ToPx(r.pts[0], pWorld, half, out ax, out ay);
                for (int i = 1; i < r.pts.Length; i++)
                {
                    bool pb = ToPx(r.pts[i], pWorld, half, out bx, out by);
                    if (pa && pb) Line(ax, ay, bx, by, col, wpx);
                    ax = bx; ay = by; pa = pb;
                }
            }
        }

        private void DrawBuildings(TileGeoDoc geo, Vector3 pWorld, float half)
        {
            if (geo.buildings == null) return;
            foreach (var b in geo.buildings)
            {
                if (b?.c == null || b.c.Length < 2) continue;
                int x, y;
                if (!ToPx(new GeoLL { a = b.c[0], o = b.c[1] },
                        pWorld, half, out x, out y)) continue;
                Plot(x, y, ColBuilding, 2);
            }
        }

        private void DrawParks(TileGeoDoc geo, Vector3 pWorld, float half)
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
                    if (!ToPx(ll, pWorld, half, out px, out py))
                    {
                        havePrev = false;   // spezza il contorno fuori mappa
                        continue;
                    }
                    if (havePrev) Line(ax, ay, px, py, ColPark, 2);
                    ax = px; ay = py; havePrev = true;
                }
            }
        }

        private void DrawPlayer(Transform t)
        {
            const int cx = TexSize / 2, cy = TexSize / 2;
            // tacca direzione camera (nord-su): yaw 0 = verso nord = alto
            float yaw = 0f;
            var cam = Camera.main;
            if (cam != null) yaw = cam.transform.eulerAngles.y * Mathf.Deg2Rad;
            // forward Unity con yaw: dir = (sin(yaw), cos(yaw)) in (est, nord)
            int tx = cx + Mathf.RoundToInt(Mathf.Sin(yaw) * 16f);
            int ty = cy + Mathf.RoundToInt(Mathf.Cos(yaw) * 16f);
            Line(cx, cy, tx, ty, ColRing, 3);
            Circle(cx, cy, 6, ColRing);
            Circle(cx, cy, 5, ColPlayer);
        }

        private void DrawBorder()
        {
            for (int i = 0; i < TexSize; i++)
            {
                Plot(i, 0, ColBorder, 1); Plot(i, TexSize - 1, ColBorder, 1);
                Plot(0, i, ColBorder, 1); Plot(TexSize - 1, i, ColBorder, 1);
            }
        }

        // ── primitive raster ──
        private void Fill(Color c)
        {
            for (int i = 0; i < _buf.Length; i++) _buf[i] = c;
        }

        private void Commit()
        {
            _tex.SetPixels(_buf);
            _tex.Apply(false);
        }

        private void Plot(int x, int y, Color c, int w)
        {
            int h = w / 2;
            for (int dy = -h; dy <= h; dy++)
                for (int dx = -h; dx <= h; dx++)
                {
                    int px = x + dx, py = y + dy;
                    if (px < 0 || py < 0 || px >= TexSize || py >= TexSize) continue;
                    _buf[py * TexSize + px] = c;
                }
        }

        private void Line(int x0, int y0, int x1, int y1, Color c, int w)
        {
            int dx = Mathf.Abs(x1 - x0), dy = Mathf.Abs(y1 - y0);
            int steps = dx > dy ? dx : dy;
            if (steps == 0) { Plot(x0, y0, c, w); return; }
            for (int i = 0; i <= steps; i++)
            {
                int x = Mathf.RoundToInt(Mathf.Lerp(x0, x1, (float)i / steps));
                int y = Mathf.RoundToInt(Mathf.Lerp(y0, y1, (float)i / steps));
                Plot(x, y, c, w);
            }
        }

        private void Circle(int cx, int cy, int r, Color c)
        {
            int r2 = r * r;
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                {
                    if (dx * dx + dy * dy > r2) continue;
                    int px = cx + dx, py = cy + dy;
                    if (px < 0 || py < 0 || px >= TexSize || py >= TexSize) continue;
                    _buf[py * TexSize + px] = c;
                }
        }
    }
}
