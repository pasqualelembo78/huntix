using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Genera la mesh stradale di un chunk come "ribbon" (nastri) lungo le
    /// polyline delle tile, con larghezza per classe highway. Una sola mesh per
    /// chunk = pochi draw call, niente migliaia di prefab Kenney.
    ///
    /// Fase 2: aggiunge
    ///  - marciapiedi sopraelevati (12 cm) su entrambi i lati delle strade
    ///    urbane, in una mesh separata (materiale chiaro + MeshCollider:
    ///    fa da cordolo per le auto e gradino calpestabile per il player);
    ///  - rotonde: le way ad anello chiuso di raggio piccolo diventano un
    ///    disco circolare vero (asfalto ad anello + isolotto centrale
    ///    rialzato), invece del pentagono storto che veniva dal ribbon.
    /// </summary>
    public static class RoadRenderer
    {
        private const float Y_ROAD = 0.03f;
        private const float Y_ROUNDABOUT = 0.05f;   // sopra l'asfalto: no z-fighting
        private const float Y_SIDEWALK = 0.12f;     // cordolo ~12 cm
        private const float SIDEWALK_W = 1.8f;
        private const float Y_LABEL = Y_ROAD + 0.06f;
        private const int MaxLabelsPerChunk = 80;
        private const float MinRunForLabel = 18f;

        private static Font _uiFont;

        /// <summary>Targhetta col nome via: posizione locale, yaw, testo.</summary>
        private struct LabelSpec
        {
            public Vector3 pos;
            public float yawDeg;
            public string name;
            public float runLen;
        }

        private static readonly Dictionary<string, float> WidthByHighway =
            new Dictionary<string, float>
            {
                {"motorway", 12f}, {"trunk", 11f}, {"primary", 10f},
                {"secondary", 8f}, {"tertiary", 7f}, {"residential", 6f},
                {"living_street", 5f}, {"service", 4f}, {"pedestrian", 3f},
                {"unclassified", 6f},
            };

        // Classi urbane che hanno marciapiede ai lati.
        private static readonly HashSet<string> SidewalkByHighway =
            new HashSet<string>
            {
                "primary", "secondary", "tertiary",
                "residential", "living_street", "unclassified",
            };

        public static float RoadWidth(string highway)
        {
            float w;
            return WidthByHighway.TryGetValue(highway ?? "", out w) ? w : 5f;
        }

        /// <summary>Mesh prodotte dalla build: asfalto e marciapiedi.</summary>
        public struct BuiltMeshes
        {
            public Mesh road;
            public Mesh sidewalk;
        }

        // Accumulatori separati per asfalto e marciapiedi: due mesh, due
        // materiali, e il collider dei cordoli vive solo sulla seconda.
        private sealed class Acc
        {
            public readonly List<Vector3> verts = new List<Vector3>(4096);
            public readonly List<Vector2> uvs = new List<Vector2>(2048);
            public readonly List<int> tris = new List<int>(8192);

            public Mesh ToMesh(string name)
            {
                if (tris.Count == 0) return null;
                var mesh = new Mesh { name = name };
                mesh.SetVertices(verts);
                mesh.SetUVs(0, uvs);
                mesh.SetTriangles(tris, 0);
                mesh.RecalculateNormals();
                mesh.RecalculateBounds();
                return mesh;
            }
        }

        /// <summary>
        /// Costruisce le mesh stradali del chunk. toLocal converte una GeoLL in
        /// coordinate locali alla root del chunk; localBounds sono i limiti del
        /// chunk in metri locali (con margine applicato dal caller). Se
        /// labelsParent e' valorizzato crea anche i TextMesh coi nomi vie.
        /// sidewalkOut riceve la mesh dei marciapiedi (null se vuota).
        /// </summary>
        public static Mesh Build(TileRoadRec[] roads, System.Func<GeoLL, Vector3> toLocal,
            Rect localBounds, Transform labelsParent, out Mesh sidewalkOut)
        {
            var road = new Acc();
            var walk = new Acc();
            var labels = new List<LabelSpec>();

            foreach (var roadRec in roads)
            {
                if (roadRec?.pts == null || roadRec.pts.Length < 2) continue;
                if (IsRoundabout(roadRec))
                {
                    AppendRoundabout(roadRec, toLocal, localBounds, road, walk);
                    continue;
                }
                AppendRoad(roadRec, toLocal, localBounds, road, walk, labels);
            }

            if (labelsParent != null && labels.Count > 0)
                CreateLabels(labels, labelsParent);

            sidewalkOut = walk.ToMesh("MarciapiediChunk");
            return road.ToMesh("StradeChunk");
        }

        // ------------------------------------------------------------------
        // Rotonde: anelli chiusi compatti

        private static bool IsRoundabout(TileRoadRec road)
        {
            if (road.pts.Length < 6) return false;
            GeoLL first = road.pts[0];
            GeoLL last = road.pts[road.pts.Length - 1];
            bool closed = System.Math.Abs(first.a - last.a) < 1e-6 &&
                          System.Math.Abs(first.o - last.o) < 1e-6;
            if (!closed) return false;

            // raggio dal bounding box (gradi -> metri con approx a media lat)
            double minA = double.MaxValue, maxA = double.MinValue;
            double minO = double.MaxValue, maxO = double.MinValue;
            for (int i = 0; i < road.pts.Length; i++)
            {
                var p = road.pts[i];
                if (p.a < minA) minA = p.a; if (p.a > maxA) maxA = p.a;
                if (p.o < minO) minO = p.o; if (p.o > maxO) maxO = p.o;
            }
            double midLat = (minA + maxA) * 0.5;
            double hMeters = (maxA - minA) * 111320.0;
            double wMeters = (maxO - minO) * 111320.0 * System.Math.Cos(midLat * Mathf.Deg2Rad);
            double radius = System.Math.Max(hMeters, wMeters) * 0.5;
            return radius > 2.0 && radius < 60.0;
        }

        private static void AppendRoundabout(TileRoadRec road,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds,
            Acc roadAcc, Acc walkAcc)
        {
            int n = road.pts.Length;
            Vector3 c = Vector3.zero;
            for (int i = 0; i < n; i++) c += toLocal(road.pts[i]);
            c /= n;

            // raggio medio reale dei punti (piu' fedele del bbox per anelli
            // irregolari); fuori dal chunk non serve nulla
            if (!bounds.Contains(new Vector2(c.x, c.z)))
            {
                // il centro puo' cadere nel chunk vicino anche se l'anello ci
                // sfiora: controlla distanza dal rettangolo espanso
                Rect expanded = new Rect(bounds.x - 80f, bounds.y - 80f,
                    bounds.width + 160f, bounds.height + 160f);
                if (!expanded.Contains(new Vector2(c.x, c.z))) return;
            }

            float sum = 0f;
            for (int i = 0; i < n; i++) sum += Vector3.Distance(toLocal(road.pts[i]), c);
            float rAvg = sum / n;
            float width = RoadWidth(road.hw);

            float islandR = Mathf.Max(rAvg - width * 0.5f, 2.5f);
            float outerR = rAvg + width * 0.5f + 0.15f; // margine anti-fessura
            EmitRadialDisc(walkAcc, c, 0f, islandR, Y_SIDEWALK + 0.02f);      // isolotto
            EmitRadialAnnulus(roadAcc, c, islandR, outerR, Y_ROUNDABOUT);     // carreggiata
            EmitRadialAnnulus(walkAcc, c, outerR, outerR + SIDEWALK_W,
                Y_SIDEWALK + 0.01f);                                          // cordolo giro rotanda
        }

        private static void EmitRadialDisc(Acc acc, Vector3 c, float r0, float r1, float y)
        {
            int seg = Mathf.Clamp((int)(r1 * 1.5f), 20, 72);
            for (int s = 0; s < seg; s++)
            {
                float a0 = s / (float)seg * Mathf.PI * 2f;
                float a1 = (s + 1) / (float)seg * Mathf.PI * 2f;
                RadialQuad(acc, c, r0, r1, a0, a1, y);
            }
        }

        private static void EmitRadialAnnulus(Acc acc, Vector3 c, float r0, float r1, float y)
        {
            if (r1 <= r0) return;
            EmitRadialDisc(acc, c, r0, r1, y);
        }

        // Quad fra i raggi r0<r1 agli angoli a0<a1. Winding verificato per
        // normale verso l'alto: tris (A,C,B) e (C,D,B).
        private static void RadialQuad(Acc acc, Vector3 c, float r0, float r1,
            float a0, float a1, float y)
        {
            Vector2 d0 = new Vector2(Mathf.Cos(a0), Mathf.Sin(a0));
            Vector2 d1 = new Vector2(Mathf.Cos(a1), Mathf.Sin(a1));
            int b = acc.verts.Count;
            acc.verts.Add(new Vector3(c.x + d0.x * r0, y, c.z + d0.y * r0)); // A interno @a0
            acc.verts.Add(new Vector3(c.x + d0.x * r1, y, c.z + d0.y * r1)); // B esterno @a0
            acc.verts.Add(new Vector3(c.x + d1.x * r0, y, c.z + d1.y * r0)); // C interno @a1
            acc.verts.Add(new Vector3(c.x + d1.x * r1, y, c.z + d1.y * r1)); // D esterno @a1
            acc.uvs.Add(new Vector2(0f, 0f));
            acc.uvs.Add(new Vector2(1f, 0f));
            acc.uvs.Add(new Vector2(0f, 1f));
            acc.uvs.Add(new Vector2(1f, 1f));
            acc.tris.Add(b); acc.tris.Add(b + 2); acc.tris.Add(b + 1);
            acc.tris.Add(b + 2); acc.tris.Add(b + 3); acc.tris.Add(b + 1);
        }

        // ------------------------------------------------------------------
        // Strade normali: nastro centrale + due nastri marciapiede

        private static void AppendRoad(TileRoadRec road,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds,
            Acc roadAcc, Acc walkAcc, List<LabelSpec> labels)
        {
            // Converte e tiene solo i punti dentro i limiti espansi; spezza la
            // polyline dove esce dal chunk per non creare nastri attraverso il vuoto.
            const float margin = 60f;
            float width = RoadWidth(road.hw);
            bool hasSidewalk = SidewalkByHighway.Contains(road.hw ?? "");
            var run = new List<Vector3>(road.pts.Length);

            for (int i = 0; i < road.pts.Length; i++)
            {
                var p = toLocal(road.pts[i]);
                bool inside = bounds.Contains(new Vector2(p.x, p.z));
                if (inside || TouchesNext(road.pts, i, toLocal, bounds, margin))
                    run.Add(p);
                else if (run.Count > 0)
                {
                    EmitRun(run, width, hasSidewalk, roadAcc, walkAcc);
                    TryAddLabel(road.nm, run, labels);
                    run.Clear();
                }
            }
            if (run.Count > 0)
            {
                EmitRun(run, width, hasSidewalk, roadAcc, walkAcc);
                TryAddLabel(road.nm, run, labels);
            }
        }

        private static bool TouchesNext(GeoLL[] pts, int i,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds, float margin)
        {
            // un punto fuori resta utile se collega due punti utili (vicino ai bordi)
            if (i == 0 || i == pts.Length - 1) return false;
            var a = toLocal(pts[i - 1]);
            var b = toLocal(pts[i + 1]);
            Rect expanded = new Rect(bounds.x - margin, bounds.y - margin,
                bounds.width + margin * 2f, bounds.height + margin * 2f);
            return SegmentIntersects(a, b, expanded);
        }

        private static bool SegmentIntersects(Vector3 a, Vector3 b, Rect r)
        {
            return r.Overlaps(new Rect(
                Mathf.Min(a.x, b.x), Mathf.Min(a.z, b.z),
                Mathf.Abs(b.x - a.x), Mathf.Abs(b.z - a.z)));
        }

        private static void EmitRun(List<Vector3> run, float width, bool hasSidewalk,
            Acc roadAcc, Acc walkAcc)
        {
            if (run.Count < 2) return;
            float half = width * 0.5f;
            Strip(run, -half, half, Y_ROAD, roadAcc);
            if (!hasSidewalk) return;
            float gap = 0.15f;                       // piccola fascia terra/asfalto
            Strip(run, half + gap, half + gap + SIDEWALK_W, Y_SIDEWALK, walkAcc);
            Strip(run, -(half + gap + SIDEWALK_W), -(half + gap), Y_SIDEWALK, walkAcc);
        }

        // Nastro generico fra gli offset orizzontali o0<o1 rispetto alla linea
        // centrale (negativo = lato sinistro guardando lungo la direzione).
        private static void Strip(List<Vector3> run, float o0, float o1, float y, Acc acc)
        {
            if (run.Count < 2 || o1 <= o0) return;
            int baseIdx = acc.verts.Count;
            float stripW = o1 - o0;
            float vDist = 0f;

            for (int i = 0; i < run.Count; i++)
            {
                Vector3 dir;
                if (i == 0) dir = run[1] - run[0];
                else if (i == run.Count - 1) dir = run[i] - run[i - 1];
                else dir = run[i + 1] - run[i - 1];
                dir.y = 0f;
                float len = dir.magnitude;
                if (len < 0.001f) dir = Vector3.forward;
                else dir /= len;

                var normal = new Vector3(-dir.z, 0f, dir.x); // perpendicolare orizzontale
                var p = run[i];
                p.y = y;

                acc.verts.Add(p - normal * o0);
                acc.verts.Add(p - normal * o1);

                if (i > 0) vDist += Vector3.Distance(run[i - 1], run[i]);
                acc.uvs.Add(new Vector2(0f, vDist / Mathf.Max(stripW, 1f)));
                acc.uvs.Add(new Vector2(1f, vDist / Mathf.Max(stripW, 1f)));

                if (i > 0)
                {
                    int a = baseIdx + (i - 1) * 2;
                    // winding con normale VERSO L'ALTO: i vertici pari sono il
                    // lato sinistro (offset o0), i dispari il destro (o1).
                    // L'ordine (a,a+1,a+2) puntava in giu': backface culled
                    // dall'alto = strade invisibili pur esistendo la mesh.
                    acc.tris.Add(a); acc.tris.Add(a + 2); acc.tris.Add(a + 1);
                    acc.tris.Add(a + 2); acc.tris.Add(a + 3); acc.tris.Add(a + 1);
                }
            }
        }

        /// <summary>
        /// Se la via ha nome e il tratto e' abbastanza lungo, registra una
        /// targhetta al centro del run orientata lungo la direzione della via.
        /// </summary>
        private static void TryAddLabel(string nm, List<Vector3> run,
            List<LabelSpec> labels)
        {
            if (string.IsNullOrEmpty(nm) || run.Count < 2) return;

            float len = 0f;
            for (int i = 1; i < run.Count; i++)
                len += Vector3.Distance(run[i - 1], run[i]);
            if (len < MinRunForLabel) return;

            int mid = run.Count / 2;
            Vector3 dir = mid > 0 && mid < run.Count - 1
                ? run[mid + 1] - run[mid - 1]
                : mid > 0 ? run[mid] - run[mid - 1]
                          : run[1] - run[0];
            dir.y = 0f;
            if (dir.sqrMagnitude < 0.0001f) return;
            dir.Normalize();

            labels.Add(new LabelSpec
            {
                pos = run[mid],
                // Euler(90,yaw): porta la faccia del TextMesh in su; con
                // yaw = atan2(-dir.z, dir.x) la lettura segue dir.
                yawDeg = Mathf.Atan2(-dir.z, dir.x) * Mathf.Rad2Deg,
                name = nm,
                runLen = len
            });
        }

        private static void CreateLabels(List<LabelSpec> labels, Transform parent)
        {
            var font = UiFont();
            if (font == null || labels.Count == 0) return;

            // prima le vie piu' lunghe: il cap limita i TextMesh nei chunk fitti
            labels.Sort((a, b) => b.runLen.CompareTo(a.runLen));
            int n = Mathf.Min(labels.Count, MaxLabelsPerChunk);

            for (int i = 0; i < n; i++)
            {
                var s = labels[i];
                // Niente MeshRenderer nel costruttore: alcune versioni di Unity
                // lo creano gia' insieme al TextMesh e un secondo AddComponent
                // spawna warning "already added" per ogni targa.
                var go = new GameObject("Via " + s.name, typeof(TextMesh));
                go.transform.SetParent(parent, false);
                go.transform.localPosition =
                    new Vector3(s.pos.x, Y_LABEL, s.pos.z);
                go.transform.localRotation =
                    Quaternion.Euler(90f, s.yawDeg, 0f);

                var tm = go.GetComponent<TextMesh>();
                tm.text = s.name;
                tm.font = font;
                tm.fontSize = 32;
                tm.anchor = TextAnchor.MiddleCenter;
                tm.alignment = TextAlignment.Center;
                tm.color = new Color(0.92f, 0.92f, 0.95f); // chiaro sull'asfalto scuro
                // Larghezza mondo stimata: nChar * fontSize * charSize * 0.1 * 0.55
                float targetW = Mathf.Min(s.runLen * 0.9f, 50f);
                tm.characterSize = Mathf.Clamp(
                    targetW / (s.name.Length * 32f * 0.055f), 0.3f, 3f);

                // senza il materiale del font sul renderer il testo resta invisibile;
                // aggiungi il renderer SOLO se manca (mai due volte)
                var mr = go.GetComponent<MeshRenderer>();
                if (mr == null) mr = go.AddComponent<MeshRenderer>();
                mr.sharedMaterial = font.material;
            }
        }

        private static Font UiFont()
        {
            if (_uiFont != null) return _uiFont;
            try { _uiFont = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { }
            if (_uiFont == null)
            {
                try { _uiFont = Resources.GetBuiltinResource<Font>("Arial.ttf"); }
                catch { }
            }
            return _uiFont;
        }
    }
}
