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
    ///
    /// Fase 3 (DEM/SRTM): il nastro e le rotonde CAMPIONANO la griglia ele
    /// ad ogni vertice -> strade che seguono il pendio invece di affondare
    /// o galleggiare. Giunzioni a cuneo miter (niente strappi ai tornanti),
    /// densificazione adattiva sulle corde che si staccano dal DEM e rotonde
    /// concentriche con TASSELLAZIONE CONDIVISA (bordi allineati, niente
    /// fessure a scatti fra carreggiata e cordolo).
    ///
    /// Fase 4 (giunzioni a grafo): le way OSM condividono i NODI agli incroci.
    /// Le strade che convergono sullo stesso nodo vengono raccordate con un
    /// poligono "fan" teso fra le bocche di TUTTE le strade del nodo: niente
    /// piu' cunei di terra ai bivi T/X e strade che si fermano con una bocca
    /// squadrata in mezzo all'incrocio. Una strada resta CHIUSA solo dove il
    /// nodo finale non e' condiviso da nessun'altra strada (vero vicolo cieco).
    ///
    /// Fase 5 (viadotti e gallerie): le strade OSM taggate bridge/tunnel non
    /// vengono piu' drappate sul dislivello (un viadotto finiva nel fondovalle,
    /// una galleria "arrampicata" sul monte). La sezione segue la retta di
    /// impalcato reale fra i capisaldi della way (h0/h1 per-vertice, frazioni
    /// s0/s1 globali per la continuita' fra tile): il viadotto la supera, la
    /// galleria resta SOTTO il rilievo (invisibile, come deve essere).
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
        // Densificazione DEM: punti intermedi alle corde stradali solo dove il
        // terreno si stacca dalla corda (vallette/dossi). Tolleranza centimetrica,
        // min passo e profondita' di bisezione per non esplodere in vertici.
        private const float DemFitTolerance = 0.05f;
        private const float DensifyMinStep = 4f;
        private const int MaxDensifyDepth = 9;

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

            // Nodi condivisi fra piu' strade (bivi T/X/incroci): le strade che
            // terminano su un nodo fermano la loro bocca SUL fan di giunzione,
            // mai piu' con un taglio squadrato in mezzo all'incrocio.
            var junctions = CollectJunctions(roads, toLocal);

            foreach (var roadRec in roads)
            {
                if (roadRec?.pts == null || roadRec.pts.Length < 2) continue;
                if (IsRoundabout(roadRec))
                {
                    AppendRoundabout(roadRec, toLocal, localBounds, road, walk,
                        labelsParent);
                    continue;
                }
                AppendRoad(roadRec, toLocal, localBounds, road, walk, labels, labelsParent);
            }

            // Fan di giunzione per tutti i nodi reali dentro i limiti del chunk.
            // I dead-end (nodo non condiviso) restano chiusi a bocca squadrata.
            foreach (var j in junctions.Values)
                EmitJunctionFan(road, j, localBounds, labelsParent);

            if (labelsParent != null && labels.Count > 0)
                CreateLabels(labels, labelsParent);

            sidewalkOut = walk.ToMesh("MarciapiediChunk");
            return road.ToMesh("StradeChunk");
        }

        // ------------------------------------------------------------------
        // Giunzioni stradali a grafo: raccordo "fan" ai nodi condivisi.
        // Senza, ogni nastro si ferma al proprio nodo con la bocca squadrata e
        // ai bivi T/X resta un cuneo di terra fra le strade (asfalto rotto).
        // Il fan chiude il nodo con un poligono teso fra le bocche di TUTTE le
        // strade che vi convergono: bocche = J +- perp(dir)*mezzalarghezza, le
        // STESSE coordinate del nastro, quindi il raccordo e' acqua-tight.

        private struct Rib
        {
            public Vector3 dir;   // direzione di avvicinamento al nodo (orizz.)
            public float half;    // mezza larghezza della carreggiata
        }

        private sealed class Junction
        {
            public GeoLL geo;
            public Vector3 local;
            public bool emitted;
            public readonly HashSet<int> ways = new HashSet<int>();
            public readonly List<Rib> ribs = new List<Rib>();
        }

        /// <summary>Chiave quantizzata (0.1 m) per far coincidere i nodi dello
        /// stesso incrocio anche se serializzati con precisione diversa.</summary>
        private static ulong NodeKeyOf(double lat, double lon)
        {
            long qlat = (long)(lat * 1e6) + 90000000L;
            long qlon = (long)(lon * 1e6) + 180000000L;
            return ((ulong)qlat << 32) | (uint)qlon;
        }

        /// <summary>Costruisce la mappa dei nodi condivisi fra minimo 2 strade.
        /// Le rotonde restano FUORI dal grafo (i loro nodi appartengono al
        /// disco, non a un raccordo). Le direzioni delle costole sono calcolate
        /// nella stessa metrica toLocal del nastro cosi' le bocche combaciano.</summary>
        private static Dictionary<ulong, Junction> CollectJunctions(
            TileRoadRec[] roads, System.Func<GeoLL, Vector3> toLocal)
        {
            var map = new Dictionary<ulong, Junction>();
            for (int r = 0; r < roads.Length; r++)
            {
                var road = roads[r];
                if (road?.pts == null || road.pts.Length < 2) continue;
                if (IsRoundabout(road)) continue;

                float half = RoadWidth(road.hw) * 0.5f;
                int n = road.pts.Length;
                for (int k = 0; k < n; k++)
                {
                    GeoLL g = road.pts[k];
                    ulong key = NodeKeyOf(g.a, g.o);
                    Junction j;
                    if (!map.TryGetValue(key, out j))
                    {
                        j = new Junction { geo = g, local = toLocal(g) };
                        map[key] = j;
                    }
                    j.ways.Add(r);

                    Vector3 d;
                    if (k == 0)       d = toLocal(road.pts[1]) - j.local;
                    else if (k == n - 1) d = j.local - toLocal(road.pts[n - 2]);
                    else              d = toLocal(road.pts[k + 1]) - toLocal(road.pts[k - 1]);
                    d.y = 0f;
                    if (d.sqrMagnitude < 0.0001f) continue;
                    d.Normalize();
                    j.ribs.Add(new Rib { dir = d, half = half });
                }
            }

            // Tiene solo i nodi davvero condivisi (>=2 strade) e con >=2 costole
            // (una bocca serve almeno a due strade, altrimenti e' un dead-end).
            var keep = new List<ulong>();
            foreach (var kv in map)
                if (kv.Value.ways.Count < 2 || kv.Value.ribs.Count < 2)
                    keep.Add(kv.Key);
            for (int i = 0; i < keep.Count; i++) map.Remove(keep[i]);
            return map;
        }

        /// <summary>Emette il poligono di raccordo del nodo: fan dal centro verso
        /// le bocche deduplicate, ordinate per angolo, quota DEM per vertice.
        /// Niente bocca: solo i nodi reali dentro i limiti stretti del chunk.</summary>
        private static void EmitJunctionFan(Acc acc, Junction j, Rect bounds,
            Transform root)
        {
            if (j.emitted) return;
            j.emitted = true;
            Vector3 c = j.local;
            if (!bounds.Contains(new Vector2(c.x, c.z))) return;

            float cDEM = SurfaceHeight(root, c, 0f);
            float cY = cDEM + Y_ROAD;

            var corners = new List<Vector3>(j.ribs.Count * 2);
            for (int i = 0; i < j.ribs.Count; i++)
            {
                var n = new Vector3(-j.ribs[i].dir.z, 0f, j.ribs[i].dir.x);
                AddCorner(corners, c + n * j.ribs[i].half, 0.2f);
                AddCorner(corners, c - n * j.ribs[i].half, 0.2f);
            }
            if (corners.Count < 3) return;   // continuazione in linea retta: niente da coprire

            corners.Sort((a, b) => Mathf.Atan2(a.z - c.z, a.x - c.x)
                .CompareTo(Mathf.Atan2(b.z - c.z, b.x - c.x)));

            int ci = acc.verts.Count;
            acc.verts.Add(new Vector3(c.x, cY, c.z));
            acc.uvs.Add(Vector2.zero);

            // Angoli in ordine DECRESCENTE -> winding orario visto dall'alto =
            // normale verso l'alto (stesso verso verificato del nastro stradale).
            for (int k = corners.Count - 1; k >= 0; k--)
            {
                Vector3 A = corners[(k - 1 + corners.Count) % corners.Count];
                Vector3 B = corners[k];
                A.y = SurfaceHeight(root, A, cDEM) + Y_ROAD;
                B.y = SurfaceHeight(root, B, cDEM) + Y_ROAD;
                int ai = acc.verts.Count;
                acc.verts.Add(A);
                acc.uvs.Add(Vector2.zero);
                int bi = acc.verts.Count;
                acc.verts.Add(B);
                acc.uvs.Add(Vector2.zero);
                acc.tris.Add(ci); acc.tris.Add(bi); acc.tris.Add(ai);
            }
        }

        /// <summary>Bocca unica per angoli quasi coincidenti (strade parallele o
        /// collineari) e cap difensivo delle dimensioni del fan.</summary>
        private static void AddCorner(List<Vector3> corners, Vector3 p, float tol)
        {
            for (int i = 0; i < corners.Count; i++)
                if (Vector3.Distance(corners[i], p) < tol) return;
            if (corners.Count < 40) corners.Add(p);
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
            Acc roadAcc, Acc walkAcc, Transform root)
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

            // Elevazione reale del terreno al centro rotonda (DEM)
            double mlat = 0, mlon = 0;
            for (int i = 0; i < n; i++) { mlat += road.pts[i].a; mlon += road.pts[i].o; }
            float cElev = TileElevation.HeightAt(mlat / n, mlon / n);
            c.y = cElev;

            float islandR = Mathf.Max(rAvg - width * 0.5f, 2.5f);
            float outerR = rAvg + width * 0.5f + 0.15f; // margine anti-fessura

            // Stesso numero di segmenti per TUTTI gli anelli: il bordo
            // condiviso tra carreggiata (outerR) e cordolo resta allineato
            // (stessi vertici, stessi angoli) -> niente fessure a scatti.
            int seg = Mathf.Clamp((int)((outerR + SIDEWALK_W) * 1.5f), 20, 72);
            EmitRing(walkAcc, c, 0f, islandR, seg, Y_SIDEWALK + 0.02f, root, cElev);   // isolotto
            EmitRing(roadAcc, c, islandR, outerR, seg, Y_ROUNDABOUT, root, cElev);     // carreggiata
            EmitRing(walkAcc, c, outerR, outerR + SIDEWALK_W, seg, Y_SIDEWALK + 0.01f, root, cElev); // cordolo
        }

        /// <summary>Anello/corona a raggio r0..r1 con l'altezza DEM campionata a
        /// OGNI vertice: la rotonda SEGUE il pendio (niente piu' disco piatto
        /// alla quota del centro, che a un dislivello affondava da un lato e
        /// galleggiava dall'altro). root null -> quota fissa di fallback.
        /// Winding come prima: normale verso l'alto.</summary>
        private static void EmitRing(Acc acc, Vector3 c, float r0, float r1, int seg,
            float lift, Transform root, float fallbackY)
        {
            if (r1 <= r0) return;
            for (int s = 0; s < seg; s++)
            {
                float a0 = s / (float)seg * Mathf.PI * 2f;
                float a1 = (s + 1) / (float)seg * Mathf.PI * 2f;
                Vector2 d0 = new Vector2(Mathf.Cos(a0), Mathf.Sin(a0));
                Vector2 d1 = new Vector2(Mathf.Cos(a1), Mathf.Sin(a1));

                Vector3 p00 = new Vector3(c.x + d0.x * r0, 0f, c.z + d0.y * r0);
                Vector3 p01 = new Vector3(c.x + d0.x * r1, 0f, c.z + d0.y * r1);
                Vector3 p10 = new Vector3(c.x + d1.x * r0, 0f, c.z + d1.y * r0);
                Vector3 p11 = new Vector3(c.x + d1.x * r1, 0f, c.z + d1.y * r1);

                p00.y = SurfaceHeight(root, p00, fallbackY) + lift;
                p01.y = SurfaceHeight(root, p01, fallbackY) + lift;
                p10.y = SurfaceHeight(root, p10, fallbackY) + lift;
                p11.y = SurfaceHeight(root, p11, fallbackY) + lift;

                int b = acc.verts.Count;
                acc.verts.Add(p00); // A interno @a0
                acc.verts.Add(p01); // B esterno @a0
                acc.verts.Add(p10); // C interno @a1
                acc.verts.Add(p11); // D esterno @a1
                acc.uvs.Add(new Vector2(0f, 0f));
                acc.uvs.Add(new Vector2(1f, 0f));
                acc.uvs.Add(new Vector2(0f, 1f));
                acc.uvs.Add(new Vector2(1f, 1f));
                acc.tris.Add(b); acc.tris.Add(b + 2); acc.tris.Add(b + 1);
                acc.tris.Add(b + 2); acc.tris.Add(b + 3); acc.tris.Add(b + 1);
            }
        }

        // ------------------------------------------------------------------
        // Strade normali: nastro centrale + due nastri marciapiede

        private static void AppendRoad(TileRoadRec road,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds,
            Acc roadAcc, Acc walkAcc, List<LabelSpec> labels, Transform root)
        {
            // Viadotti/gallerie con la retta di impalcato nota (dh): sezione
            // elevata/tunnelizzata, NON drappata sul terreno. Senza dh (dati
            // DEM mancanti ai capisaldi) resta la strada classica sul terreno.
            if ((road.br || road.tu) && road.dh)
            {
                AppendDeck(road, toLocal, bounds, roadAcc, labels, root);
                return;
            }
            // Converte e tiene solo i punti dentro i limiti espansi; spezza la
            // polyline dove esce dal chunk per non creare nastri attraverso il vuoto.
            const float margin = 60f;
            float width = RoadWidth(road.hw);
            bool hasSidewalk = SidewalkByHighway.Contains(road.hw ?? "");
            var run = new List<Vector3>(road.pts.Length);

            // Densifica ogni tratta con punti fissi SOLO dove serve: se la corda
            // fra due nodi OSM radi (50-100 m su extraurbane) passa sopra una
            // valletta o un dosso del DEM, l'asfalto affonderebbe/galleggerebbe.
            // Con DensifyTo il nastro si biseca finche' la superficie resta
            // ATTACCATA al terreno entro DemFitTolerance; trattate piatte o gia'
            // dense non aggiungono geometria (un solo campione di scarto).
            GeoLL gPrev = null;
            Vector3 pPrev = Vector3.zero;
            bool started = false;
            for (int i = 0; i < road.pts.Length; i++)
            {
                GeoLL g = road.pts[i];
                var p = toLocal(g);
                // Elevazione reale del terreno: le strade seguono il dislivello
                // DEM invece di restare piatte a y=0 (che le seppellirebbe sotto
                // un terreno rialzato). run[i].y = quota terreno.
                p.y = TileElevation.HeightAt(g.a, g.o);
                bool inside = bounds.Contains(new Vector2(p.x, p.z));
                if (!(inside || TouchesNext(road.pts, i, toLocal, bounds, margin)))
                {
                    if (run.Count > 0)
                    {
                        EmitRun(run, width, hasSidewalk, roadAcc, walkAcc, root, true);
                        TryAddLabel(road.nm, run, labels);
                        run.Clear();
                    }
                    started = false;   // niente ponti nel vuoto: i punti isolati si saltano
                    continue;
                }
                if (!started)
                {
                    run.Add(p);
                    started = true;
                }
                else
                {
                    DensifyTo(run, toLocal, gPrev, pPrev, g, p, MaxDensifyDepth);
                }
                gPrev = g;
                pPrev = p;
            }
            if (run.Count > 0)
            {
                EmitRun(run, width, hasSidewalk, roadAcc, walkAcc, root, true);
                TryAddLabel(road.nm, run, labels);
            }
        }

        /// <summary>Sezione elevata (viadotto/galleria): segue la retta di
        /// impalcato fra i due capisaldi della WAY, non il terreno. h0/h1 sono
        /// le quote s.l.m. ai portali/impalcature; s0/s1 le frazioni GLOBALI
        /// di lunghezza della way occupate da questo record, cosi' un ponte
        /// lungo che taglia piu' tile resta continuo nei punti di confine.
        /// In galleria la carreggiata passa SOTTO il rilievo (invisibile nel
        /// monte, come deve essere).</summary>
        private static void AppendDeck(TileRoadRec road,
            System.Func<GeoLL, Vector3> toLocal, Rect bounds,
            Acc roadAcc, List<LabelSpec> labels, Transform root)
        {
            const float margin = 60f;
            float width = RoadWidth(road.hw);
            int n = road.pts.Length;
            if (n < 2) return;

            // frazione locale (0..1) lungo questo record, per ciascun punto:
            // si usa la lunghezza arcata in metri (le proiezioni toLocal gia'
            // normalizzate sono quasi-planari qui sotto la scala del chunk)
            float[] floc = new float[n];
            float locLen = 0f;
            for (int i = 1; i < n; i++)
            {
                Vector3 a = toLocal(road.pts[i - 1]);
                Vector3 b = toLocal(road.pts[i]);
                locLen += Mathf.Sqrt((b.x - a.x) * (b.x - a.x) +
                                     (b.z - a.z) * (b.z - a.z));
                floc[i] = locLen;
            }
            bool globalOk = road.s1 > road.s0 && road.s1 - road.s0 > 0.0001f;
            if (locLen > 0f)
                for (int i = 1; i < n; i++) floc[i] /= locLen;

            var run = new List<Vector3>(n);
            bool started = false;
            for (int i = 0; i < n; i++)
            {
                GeoLL g = road.pts[i];
                var p = toLocal(g);
                float f = globalOk
                    ? road.s0 + (road.s1 - road.s0) * floc[i]
                    : floc[i];
                p.y = Mathf.Lerp(road.h0, road.h1, Mathf.Clamp01(f));
                bool inside = bounds.Contains(new Vector2(p.x, p.z));
                if (!(inside || TouchesNext(road.pts, i, toLocal, bounds, margin)))
                {
                    if (run.Count > 0)
                    {
                        EmitRun(run, width, false, roadAcc, null, root, false);
                        TryAddLabel(road.nm, run, labels);
                        run.Clear();
                    }
                    started = false;
                    continue;
                }
                if (!started)
                {
                    run.Add(p);
                    started = true;
                }
                else
                {
                    run.Add(p);
                }
            }
            if (run.Count > 0)
            {
                EmitRun(run, width, false, roadAcc, null, root, false);
                TryAddLabel(road.nm, run, labels);
            }
        }

        /// <summary>Appende a `run` i punti che fanno seguire all'asfalto il DEM:
        /// biseca la tratta georeferenziata (g0,p0)->(g1,p1) finche' la quota DEM
        /// al punto medio resta entro DemFitTolerance dalla corda lineare. Il
        /// primo punto viene gia' aggiunto dal chiamante; il terminale appende p1
        /// e, nei tratti piani, non scompone nulla (1 campione e via).</summary>
        private static void DensifyTo(List<Vector3> run,
            System.Func<GeoLL, Vector3> toLocal,
            GeoLL g0, Vector3 p0, GeoLL g1, Vector3 p1, int depth)
        {
            if (depth <= 0 || Vector3.Distance(p0, p1) < DensifyMinStep)
            {
                run.Add(p1);
                return;
            }
            var gm = new GeoLL { a = (g0.a + g1.a) * 0.5, o = (g0.o + g1.o) * 0.5 };
            float hMid = TileElevation.HeightAt(gm.a, gm.o);
            float chordMid = (p0.y + p1.y) * 0.5f;
            if (Mathf.Abs(hMid - chordMid) <= DemFitTolerance)
            {
                run.Add(p1);
                return;
            }
            var pm = toLocal(gm);
            pm.y = hMid;
            DensifyTo(run, toLocal, g0, p0, gm, pm, depth - 1);
            DensifyTo(run, toLocal, gm, pm, g1, p1, depth - 1);
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
            Acc roadAcc, Acc walkAcc, Transform root, bool followTerrain)
        {
            if (run.Count < 2) return;
            float half = width * 0.5f;
            Strip(run, -half, half, Y_ROAD, roadAcc, root, followTerrain);
            if (!hasSidewalk) return;
            float gap = 0.15f;                       // piccola fascia terra/asfalto
            Strip(run, half + gap, half + gap + SIDEWALK_W, Y_SIDEWALK, walkAcc, root, true);
            Strip(run, -(half + gap + SIDEWALK_W), -(half + gap), Y_SIDEWALK, walkAcc, root, true);
        }

        // Nastro generico fra gli offset orizzontali o0<o1 rispetto alla linea
        // centrale (negativo = lato sinistro guardando lungo la direzione).
        private static void Strip(List<Vector3> run, float o0, float o1, float y, Acc acc,
            Transform root, bool followTerrain)
        {
            if (run.Count < 2 || o1 <= o0) return;
            int baseIdx = acc.verts.Count;
            float stripW = o1 - o0;
            float vDist = 0f;

            for (int i = 0; i < run.Count; i++)
            {
                Vector3 left, right;
                if (i == 0 || i == run.Count - 1)
                {
                    Vector3 dir = i == 0 ? run[1] - run[0] : run[i] - run[i - 1];
                    dir.y = 0f;
                    if (dir.sqrMagnitude < 0.0001f) dir = Vector3.forward;
                    else dir.Normalize();
                    var normal = new Vector3(-dir.z, 0f, dir.x);
                    var p = run[i];
                    left = p - normal * o0;
                    right = p - normal * o1;
                }
                else
                {
                    // Giunzione a cuneo MITER: incrocia i due segmenti di bordo
                    // invece di offset semplici che, sui tornanti, si incrociano
                    // e strappano l'asfalto (bordo interno che sfora e crea il
                    // "pezzo interrotto" a ogni curva forte).
                    left = MiterPoint(run[i - 1], run[i], run[i + 1], o0);
                    right = MiterPoint(run[i - 1], run[i], run[i + 1], o1);
                }

                // Superficie stradale CONFORME AL PENDIO: campiona l'altitudine
                // alla posizione reale dei due BORDI del nastro, non al solo
                // centro. Con un unico y=centro in larghezza il nastro restava
                // orizzontale: sul dislivello il bordo a valle affondava nel
                // terreno e quello a monte ne usciva -> l'asfalto sembrava
                // spezzato/strappato. y param = offset superficie sopra il terreno.
                // Su viadotti/gallerie (followTerrain=false) il deck NON segue
                // il DEM: i bordi restano alla quota impalcato del centro, cosi'
                // la carreggiata sospesa non si accascia nella valle.
                if (followTerrain)
                {
                    left.y = SurfaceHeight(root, left, run[i].y) + y;
                    right.y = SurfaceHeight(root, right, run[i].y) + y;
                }
                else
                {
                    left.y = run[i].y + y;
                    right.y = run[i].y + y;
                }

                acc.verts.Add(left);
                acc.verts.Add(right);

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

        /// <summary>Vertice d'angolo a cuneo (miter) per un punto interno del
        /// nastro: incrocia le rette dei bordi (offset firmato `off`) dei due
        /// segmenti consecutivi. Cosi' il bordo NON si incrocia ai tornanti:
        /// l'angolo interno ripiega sul bisettrice e quello esterno allunga in
        /// modo naturale. Lunghezza limitata (maxLen) per evitare spine sui
        /// gomiti stretti; segmenti (quasi) paralleli -> niente miter.</summary>
        private static Vector3 MiterPoint(Vector3 p0, Vector3 p1, Vector3 p2, float off)
        {
            Vector3 d0 = p1 - p0; d0.y = 0f;
            Vector3 d1 = p2 - p1; d1.y = 0f;
            float l0 = d0.magnitude, l1 = d1.magnitude;
            if (l0 < 0.001f && l1 < 0.001f) return p1;
            if (l0 < 0.001f) d0 = d1;
            if (l1 < 0.001f) d1 = d0;
            d0.Normalize();
            d1.Normalize();

            // rette di bordo dei due segmenti alla distanza firmata `off`
            Vector2 n0 = new Vector2(-d0.z, d0.x);
            Vector2 n1 = new Vector2(-d1.z, d1.x);
            Vector2 a1 = new Vector2(p1.x, p1.z);
            Vector2 A = a1 - n0 * off;
            Vector2 B = a1 - n1 * off;

            float denom = d0.x * d1.z - d0.z * d1.x;
            if (Mathf.Abs(denom) < 1e-4f)
            {
                // rette (quasi) parallele: angolo piatto, nastro semplice
                return p1 - new Vector3(n0.x, 0f, n0.y) * off;
            }
            Vector2 w = B - A;
            Vector2 dv0 = new Vector2(d0.x, d0.z);
            Vector2 dv1 = new Vector2(d1.x, d1.z);
            float t = (w.x * dv1.y - w.y * dv1.x) / denom;
            Vector2 res2 = A + dv0 * t;

            float maxM = Mathf.Max(3f, Mathf.Abs(off) * 3f);
            float m = (res2 - a1).magnitude;
            if (m > maxM) res2 = a1 + (res2 - a1).normalized * maxM;

            return new Vector3(res2.x, 0f, res2.y);
        }

        /// <summary>Quota del terreno (DEM) in un punto locale del nastro.
        /// Converte local->world tramite la root del chunk. Se manca la root
        /// (build esterna senza chunks) ripiega sulla quota del centro.</summary>
        private static float SurfaceHeight(Transform root, Vector3 local,
            float centerFallback)
        {
            if (root == null) return centerFallback;
            Vector3 w = root.TransformPoint(new Vector3(local.x, 0f, local.z));
            return TileElevation.HeightAtWorld(w);
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

            // Punto a meta' LUNGHEZZA (la densita' dei vertici e' irregolare dopo
            // la densificazione DEM: l'indice medio sarebbe fuori centro).
            int mid = 1;
            float acc = 0f;
            for (int k = 1; k < run.Count; k++)
            {
                acc += Vector3.Distance(run[k - 1], run[k]);
                if (acc >= len * 0.5f) { mid = k; break; }
            }
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
