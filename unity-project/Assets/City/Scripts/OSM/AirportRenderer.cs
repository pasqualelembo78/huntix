using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Renderizza gli aeroporti (aeroway=aerodrome) estratti dal backend.
    /// Per ogni aerodromo viene piazzato un piccolo scalo completo:
    /// una pista di asfalto con segnaletica (assale a tratteggio + soglie) e
    /// un velivolo low-poly costruito proceduralmente da primitive Unity,
    /// parcheggiato al centro della pista. Non richiede asset esterni.
    ///
    /// Il record OSM (<see cref="TileAirportRec"/>) contiene il centro in
    /// gradi (c), le dimensioni in metri lungo l'asse maggiore (d=[w,l]) e la
    /// rotazione in gradi (r) da applicare a un prefab orientato lungo X per
    /// allinearlo al lato lungo dell'impronta.
    /// </summary>
    public static class AirportRenderer
    {
        // GeoLL é classe interna: usiamo la coppia lat/lon passata da ChunkBuilder.
        // Firma chiusa sul delegato di ChunkBuilder (GeoLL -> local).
        public static int Build(ChunkData chunk, TileGeoDoc geo,
            Func<GeoLL, Vector3> toLocal, Rect bounds)
        {
            if (geo == null || geo.airports == null || geo.airports.Length == 0)
                return 0;

            chunk.airportsGo = new GameObject("Aeroporti");
            chunk.airportsGo.transform.SetParent(chunk.root.transform, false);

            int placed = 0;
            for (int i = 0; i < geo.airports.Length; i++)
            {
                var air = geo.airports[i];
                if (air == null || air.c == null || air.c.Length < 2)
                    continue;
                try
                {
                    var ll = new GeoLL { a = air.c[0], o = air.c[1] };
                    var p = toLocal(ll);
                    if (!bounds.Contains(new Vector2(p.x, p.z))) continue;
                    BuildAirport(chunk.airportsGo.transform, air, p);
                    placed++;
                }
                catch (Exception e)
                {
                    Debug.LogError("[AirportRenderer] " + chunk.key +
                        " aeroporto saltato id=" + air.id + " nm=" + air.nm + ": " + e);
                }
            }
            return placed;
        }

        private static void BuildAirport(Transform parent, TileAirportRec air, Vector3 center)
        {
            float maj = 1f;
            if (air.d != null && air.d.Length >= 2)
            {
                float w = Mathf.Abs(air.d[0]);
                float l = Mathf.Abs(air.d[1]);
                maj = Mathf.Max(w, l);
            }
            // La pista nell'impronta OSM copre tutto l'aerodromo; per la scena
            // usiamo una lunghezza di pista visivamente sensata non oltre i 1500 m.
            float runwayLen = Mathf.Clamp(maj, 800f, 1500f);
            const float runwayW = 46f;

            string nm = string.IsNullOrEmpty(air.nm) ? "Aeroporto" : air.nm;
            var go = new GameObject("Aeroporto " + nm);
            go.transform.SetParent(parent, false);
            go.transform.position = new Vector3(center.x, 0f, center.z);
            go.transform.rotation = Quaternion.Euler(0f, air.r, 0f);

            // ── pista asfalto ──
            CreateQuad(go.transform, "Pista", Vector3.zero,
                new Vector2(runwayLen, runwayW), 0.02f, AsphaltMat());

            // ── assale tratteggiato (centro pista) ──
            const float dashLen = 38f;
            const float dashW = 1.1f;
            const float gap = 26f;
            float startX = -runwayLen * 0.5f + 60f;
            float endX = runwayLen * 0.5f - 60f;
            float x = startX;
            int guard = 0;
            while (x < endX && guard++ < 200)
            {
                CreateQuad(go.transform, "Assale", new Vector3(x, 0f, 0f),
                    new Vector2(dashLen, dashW), 0.021f, LineMat());
                x += dashLen + gap;
            }

            // ── soglie di testata ──
            const float threshW = 2.0f;
            const float edgeLen = 12f;
            for (int s = -1; s <= 1; s += 2)
            {
                float tx = s * (runwayLen * 0.5f - threshW * 0.5f);
                CreateQuad(go.transform, "Soglia", new Vector3(tx, 0f, 0f),
                    new Vector2(threshW, runwayW), 0.021f, LineMat());
                // trattini di identificazione ai bordi
                for (int e = -1; e <= 1; e += 2)
                {
                    CreateQuad(go.transform, "SogliaBordo",
                        new Vector3(tx - s * edgeLen, 0f, e * (runwayW * 0.5f - 4f)),
                        new Vector2(6f, 2f), 0.021f, LineMat());
                }
            }

            // ── velivolo parcheggiato al centro ──
            BuildPlane(go.transform, new Vector3(0f, 0f, 0f));

            // ChunkData.SetLod disattiva/chiede l'aeroporto a distanza.
        }

        /// <summary>Crea un quad orizzontale (pavimentazione/segnaletica).</summary>
        private static void CreateQuad(Transform parent, string name, Vector3 localPos,
            Vector2 dims, float y, Material mat)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            q.name = name;
            q.transform.SetParent(parent, false);
            q.transform.localPosition = new Vector3(localPos.x, y, localPos.z);
            q.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
            q.transform.localScale = new Vector3(dims.x, dims.y, 1f);
            var mr = q.GetComponent<MeshRenderer>();
            if (mr != null) mr.sharedMaterial = mat;
            UnityEngine.Object.Destroy(q.GetComponent<Collider>());
        }

        /// <summary>
        /// Costruisce un velivolo low-poly da primitive Unity, orientato lungo
        /// +X locale (l'asse della pista). Livrea neutra.
        /// </summary>
        private static void BuildPlane(Transform parent, Vector3 localPos)
        {
            var plane = new GameObject("Velivolo");
            plane.transform.SetParent(parent, false);
            plane.transform.localPosition = localPos;

            Material bodyMat = PlaneBodyMat();
            Material accentMat = PlaneAccentMat();
            Material engineMat = EngineMat();

            const float fusW = 5.0f;   // larghezza fusoliera
            const float fusH = 5.2f;
            const float fusL = 46f;

            // fusoliera (una capsula allungata, asse X)
            var fus = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            fus.name = "Fusoliera";
            fus.transform.SetParent(plane.transform, false);
            fus.transform.localScale = new Vector3(fusL / (fusW * 2f), fusW, fusH);
            fus.transform.localPosition = new Vector3(0f, fusW * 0.5f, 0f);
            SetMat(fus, bodyMat);
            DestroyColliders(fus);

            // muso appuntito: cono in direzione +X
            var nose = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            nose.name = "Muso";
            nose.transform.SetParent(plane.transform, false);
            nose.transform.localScale = new Vector3(fusL * 0.14f, fusW * 0.5f, fusH * 0.7f);
            nose.transform.localPosition = new Vector3(fusL * 0.5f, fusW * 0.5f, 0f);
            nose.transform.localRotation = Quaternion.Euler(0f, 90f, 0f);
            SetMat(nose, bodyMat);
            DestroyColliders(nose);

            // ala principale (scatola lunga lungo l'asse X spazzata indietro)
            var wing = GameObject.CreatePrimitive(PrimitiveType.Cube);
            wing.name = "Ala";
            float wingSpan = 46f;
            float wingChord = 7f;
            wing.transform.SetParent(plane.transform, false);
            wing.transform.localScale = new Vector3(wingSpan, 0.7f, wingChord);
            wing.transform.localPosition = new Vector3(-4f, fusW * 0.5f + 0.35f, 0f);
            wing.transform.localRotation = Quaternion.Euler(0f, 0f, -6f);
            SetMat(wing, accentMat);
            DestroyColliders(wing);

            // motori sotto le ali (cilindri)
            for (int s = -1; s <= 1; s += 2)
            {
                var eng = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                eng.name = "Motore" + (s > 0 ? "Dx" : "Sx");
                eng.transform.SetParent(plane.transform, false);
                eng.transform.localScale = new Vector3(1.6f, 4f, 1.6f);
                eng.transform.localPosition = new Vector3(-4f, fusW * 0.5f - 1f, s * (wingSpan * 0.5f - 7f));
                eng.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
                SetMat(eng, engineMat);
                DestroyColliders(eng);
            }

            // impennaggio: deriva verticale + piano orizzontale
            var fin = GameObject.CreatePrimitive(PrimitiveType.Cube);
            fin.name = "Deriva";
            fin.transform.SetParent(plane.transform, false);
            fin.transform.localScale = new Vector3(7f, 7f, 0.8f);
            fin.transform.localPosition = new Vector3(-fusL * 0.5f + 2f, fusW * 0.5f + 3.5f, 0f);
            fin.transform.localRotation = Quaternion.Euler(0f, 0f, 14f);
            SetMat(fin, accentMat);
            DestroyColliders(fin);

            var stab = GameObject.CreatePrimitive(PrimitiveType.Cube);
            stab.name = "PianoOriz";
            stab.transform.SetParent(plane.transform, false);
            stab.transform.localScale = new Vector3(16f, 0.7f, 4.2f);
            stab.transform.localPosition = new Vector3(-fusL * 0.5f + 2f, fusW * 0.5f + 0.35f, 0f);
            SetMat(stab, bodyMat);
            DestroyColliders(stab);

            // carrello
            for (int s = -1; s <= 1; s += 2)
            {
                var mech = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                mech.name = "Carrello" + (s > 0 ? "Dx" : "Sx");
                mech.transform.SetParent(plane.transform, false);
                mech.transform.localScale = new Vector3(0.5f, fusW * 0.6f, 0.5f);
                mech.transform.localPosition = new Vector3(-2f, fusW * 0.6f, s * 3.5f);
                SetMat(mech, engineMat);
                DestroyColliders(mech);

                var twheel = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                twheel.name = "Ruota" + (s > 0 ? "Dx" : "Sx");
                twheel.transform.SetParent(plane.transform, false);
                twheel.transform.localScale = new Vector3(1.2f, 0.6f, 1.2f);
                twheel.transform.localPosition = new Vector3(-2f, fusW * 0.3f, s * 3.5f);
                twheel.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
                SetMat(twheel, WheelMat());
                DestroyColliders(twheel);
            }
        }

        private static void SetMat(GameObject go, Material m)
        {
            var mr = go.GetComponent<MeshRenderer>();
            if (mr != null) mr.sharedMaterial = m;
        }

        private static void DestroyColliders(GameObject go)
        {
            var cols = go.GetComponentsInChildren<Collider>(true);
            for (int i = 0; i < cols.Length; i++)
                UnityEngine.Object.Destroy(cols[i]);
        }

        private static Material MakeMat(Color c, float smooth = 0.5f)
        {
            var sh = Shader.Find("Universal Render Pipeline/Lit");
            if (sh == null) sh = Shader.Find("Standard");
            var m = new Material(sh);
            m.color = c;
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", smooth);
            return m;
        }

        private static Material AsphaltMat() => MakeMat(new Color(0.16f, 0.16f, 0.17f), 0.2f);
        private static Material LineMat() => MakeMat(new Color(0.93f, 0.93f, 0.90f), 0.1f);
        private static Material PlaneBodyMat() => MakeMat(new Color(0.88f, 0.88f, 0.90f), 0.5f);
        private static Material PlaneAccentMat() => MakeMat(new Color(0.10f, 0.35f, 0.62f), 0.4f);
        private static Material EngineMat() => MakeMat(new Color(0.30f, 0.30f, 0.32f), 0.6f);
        private static Material WheelMat() => MakeMat(new Color(0.08f, 0.08f, 0.08f), 0.8f);
    }
}
