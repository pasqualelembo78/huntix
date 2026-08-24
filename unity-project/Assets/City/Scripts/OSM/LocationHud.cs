using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace City.OSM
{
    /// <summary>
    /// Indirizzo live in basso al centro dello schermo: via + numero civico.
    /// Il civico compare solo se OSM ne ha uno entro MaxCivicoDist metri dal
    /// giocatore (copertura OSM variabile: dove manca si mostra solo la via,
    /// presa dalla strada piu' vicina tra quelle mappate).
    /// </summary>
    public class LocationHud : MonoBehaviour
    {
        private const float PollSeconds = 0.7f;
        private const float MaxCivicoDist = 60f;

        private Text _label;
        private float _nextPoll;
        private readonly Dictionary<string, CachedDoc> _cache =
            new Dictionary<string, CachedDoc>();

        private class CachedDoc
        {
            public GeoCoord anchor;                    // centro della tile
            public readonly List<Seg> segs = new List<Seg>();
            public readonly List<Addr> addrs = new List<Addr>();
        }

        private struct Seg { public Vector2 a, b; public string name; }
        private struct Addr { public Vector2 p; public string num, street; }

        public static void Create()
        {
            var go = new GameObject("LocationHud", typeof(LocationHud));
            DontDestroyOnLoad(go);
        }

        private void Awake()
        {
            var canvasGo = new GameObject("LocCanvas", typeof(Canvas));
            canvasGo.transform.SetParent(transform, false);
            canvasGo.GetComponent<Canvas>().renderMode =
                RenderMode.ScreenSpaceOverlay;

            var textGo = new GameObject("Indirizzo", typeof(Text));
            textGo.transform.SetParent(canvasGo.transform, false);
            _label = textGo.GetComponent<Text>();
            _label.font = UiFont();
            if (_label.font == null) return;
            _label.fontSize = 34;
            _label.color = new Color(0.95f, 0.96f, 0.98f);
            _label.alignment = TextAnchor.LowerCenter;

            RectTransform rt = _label.rectTransform;
            rt.anchorMin = new Vector2(0.5f, 0f);
            rt.anchorMax = new Vector2(0.5f, 0f);
            rt.pivot = new Vector2(0.5f, 0f);
            rt.anchoredPosition = new Vector2(0f, 26f);
            rt.sizeDelta = new Vector2(1400f, 80f);
        }

        private void Update()
        {
            if (Time.unscaledTime < _nextPoll) return;
            _nextPoll = Time.unscaledTime + PollSeconds;
            Refresh();
        }

        private static Font UiFont()
        {
            try { return Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { }
            try { return Resources.GetBuiltinResource<Font>("Arial.ttf"); }
            catch { }
            return null;
        }

        private void Refresh()
        {
            if (_label == null || _label.font == null) return;
            var mgr = CityChunkedWorld.Instance != null
                ? CityChunkedWorld.Instance.Manager : null;
            Transform t = mgr != null ? mgr.target : null;
            if (t == null) { _label.text = ""; return; }

            var seen = new HashSet<TileGeoDoc>();
            string roadName = null; float roadD = float.MaxValue;
            string addrText = null; float addrD = float.MaxValue;

            foreach (var c in mgr.BuiltChunks())
            {
                if (c.geo == null || !seen.Add(c.geo)) continue;
                CachedDoc cd = GetCached(c.geo);
                Vector3 ow = WorldOrigin.ToWorld(cd.anchor);
                Vector2 pl = new Vector2(t.position.x - ow.x, t.position.z - ow.z);

                for (int i = 0; i < cd.segs.Count; i++)
                {
                    float d = SegDist(pl, cd.segs[i].a, cd.segs[i].b);
                    if (d < roadD) { roadD = d; roadName = cd.segs[i].name; }
                }
                for (int i = 0; i < cd.addrs.Count; i++)
                {
                    float d = (pl - cd.addrs[i].p).magnitude;
                    if (d < addrD)
                    {
                        addrD = d;
                        addrText = cd.addrs[i].street + " " + cd.addrs[i].num;
                    }
                }
            }

            _label.text = (addrText != null && addrD <= MaxCivicoDist)
                ? addrText
                : (roadName ?? "");
        }

        /// <summary>Punti stradali/civici relativi al CENTRO TILE (stabile nel
        /// rebase: sia i chunk roots che il player si spostano insieme).</summary>
        private CachedDoc GetCached(TileGeoDoc geo)
        {
            CachedDoc cd;
            if (_cache.TryGetValue(geo.tile, out cd)) return cd;

            cd = new CachedDoc { anchor = geo.Center() };
            Vector3 o = WorldOrigin.ToWorld(cd.anchor);

            if (geo.roads != null)
                foreach (var r in geo.roads)
                {
                    if (r?.pts == null || r.pts.Length < 2 ||
                        string.IsNullOrEmpty(r.nm)) continue;
                    for (int i = 1; i < r.pts.Length; i++)
                        cd.segs.Add(new Seg
                        {
                            a = Rel(o, r.pts[i - 1]),
                            b = Rel(o, r.pts[i]),
                            name = r.nm
                        });
                }

            if (geo.addrs != null)
                foreach (var ad in geo.addrs)
                {
                    if (ad == null || string.IsNullOrEmpty(ad.n)) continue;
                    cd.addrs.Add(new Addr
                    {
                        p = Rel(o, new GeoLL { a = ad.a, o = ad.o }),
                        num = ad.n,
                        street = ad.s
                    });
                }

            _cache[geo.tile] = cd;
            return cd;
        }

        private static Vector2 Rel(Vector3 originWorld, GeoLL ll)
        {
            Vector3 w = WorldOrigin.ToWorld(ll.a, ll.o);
            return new Vector2(w.x - originWorld.x, w.z - originWorld.z);
        }

        private static float SegDist(Vector2 p, Vector2 a, Vector2 b)
        {
            float ax = b.x - a.x, ay = b.y - a.y;
            float l2 = ax * ax + ay * ay;
            if (l2 < 1e-6f)
            {
                float dx = p.x - a.x, dy = p.y - a.y;
                return Mathf.Sqrt(dx * dx + dy * dy);
            }
            float t = ((p.x - a.x) * ax + (p.y - a.y) * ay) / l2;
            if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
            float qx = a.x + ax * t - p.x, qy = a.y + ay * t - p.y;
            return Mathf.Sqrt(qx * qx + qy * qy);
        }
    }
}
