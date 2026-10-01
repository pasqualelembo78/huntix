using System.Collections.Generic;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Quote del terreno di UN chunk, indicizzate per ricerca rapida.
    ///
    /// Nasce per togliere un costo O(N*M): il codice di placement cercava la
    /// quota piu' vicina con una scansione lineare di TUTTE le quote del chunk
    /// per ogni edificio. Con ~4700 voci (griglia di campionamento a 15 m su
    /// un chunk da 1 km) e fino a qualche migliaio di edifici per chunk erano
    /// centinaia di milioni di confronti per citta', dentro il loop di build
    /// con budget di 12 ms/frame: la costruzione dei chunk non finiva mai e il
    /// mondo (che non e' gia' costruito) non reggeva il player sotto i piedi.
    ///
    /// Strategia: le quote arrivano gia' su una griglia REGOLARE (e' il
    /// campionamento del DEM fatto da ChunkBuilder.CollectDemHeights, passo
    /// costante e allineato ai bordi del chunk). La griglia viene quindi
    /// ricostruita in un array 2D e interpolata bilinearmente: O(1) esatto e,
    /// a differenza del "nearest" precedente, la quota sotto l'edificio e' una
    /// vera interpolazione del DEM, non il campione piu' vicino (su un pendio
    /// il nearest sbagliava di metri e gli edifici affondavano o rimanevano
    /// sospesi di mezzo metro).
    /// </summary>
    public sealed class TerrainHeightField
    {
        private readonly float[] _v;      // griglia (nx+1)*(nz+1), row-major
        private readonly int _nx;          // celle su X
        private readonly int _nz;          // celle su Z
        private readonly float _x0, _z0;   // origine locale del chunk (bounds.xMin/yMin)
        private readonly float _sx, _sz;   // passo per cella

        /// <summary>Campi della griglia: quante celle su X e su Z.</summary>
        public int Nx { get { return _nx; } }
        public int Nz { get { return _nz; } }

        private TerrainHeightField(float[] v, int nx, int nz,
            float x0, float z0, float sx, float sz)
        {
            _v = v; _nx = nx; _nz = nz;
            _x0 = x0; _z0 = z0; _sx = sx; _sz = sz;
        }

        /// <summary>Costruisce il campo dalle quote prodotte da
        /// `ChunkBuilder.CollectDemHeights` (un dizionario {posizione locale ->
        /// quota}). Se il dizionario e' vuoto o non copre la griglia attesa
        /// ritorna null: il chiamante allora lavora a quota 0 (chunk piatto),
        /// che e' il comportamento del ramo unity.</summary>
        public static TerrainHeightField Build(Dictionary<Vector2, float> heights,
            Rect bounds)
        {
            if (heights == null || heights.Count == 0) return null;

            int nx = Mathf.Max(2, Mathf.CeilToInt(bounds.width / TerrainChunk.DemSampleStep));
            int nz = Mathf.Max(2, Mathf.CeilToInt(bounds.height / TerrainChunk.DemSampleStep));

            // La raccolta e' su una griglia (nx+1)x(nz+1): se il dizionario ha
            // meno voci di quelle, non e' la griglia che ci aspettiamo (potrebbe
            // essere un proxy sparso) e non e' interpolabile: meglio niente.
            if (heights.Count < (nx + 1) * (nz + 1)) return null;

            var v = new float[(nx + 1) * (nz + 1)];
            foreach (var kv in heights)
            {
                int i = Mathf.RoundToInt((kv.Key.x - bounds.xMin) / bounds.width * nx);
                int j = Mathf.RoundToInt((kv.Key.y - bounds.yMin) / bounds.height * nz);
                i = Mathf.Clamp(i, 0, nx);
                j = Mathf.Clamp(j, 0, nz);
                v[j * (nx + 1) + i] = kv.Value;
            }

            return new TerrainHeightField(v, nx, nz, bounds.xMin, bounds.yMin,
                bounds.width / nx, bounds.height / nz);
        }

        /// <summary>Quota del terreno nel punto locale (x, z), interpolata
        /// bilinearmente sulla griglia. Fuori dai bordi si ferma al bordo (il
        /// clamp e' quello del DEM: oltre il chunk non c'e' dato, e l'edificio
        /// non deve inventare un pendio).</summary>
        public float HeightNear(float x, float z)
        {
            float fi = (x - _x0) / _sx;
            float fj = (z - _z0) / _sz;
            if (fi < 0f) fi = 0f; else if (fi > _nx) fi = _nx;
            if (fj < 0f) fj = 0f; else if (fj > _nz) fj = _nz;

            int i0 = (int)fi; if (i0 > _nx - 1) i0 = _nx - 1;
            int j0 = (int)fj; if (j0 > _nz - 1) j0 = _nz - 1;
            int i1 = i0 + 1, j1 = j0 + 1;
            float ti = fi - i0;
            float tj = fj - j0;

            float v00 = _v[j0 * (_nx + 1) + i0];
            float v10 = _v[j0 * (_nx + 1) + i1];
            float v01 = _v[j1 * (_nx + 1) + i0];
            float v11 = _v[j1 * (_nx + 1) + i1];
            return Mathf.Lerp(Mathf.Lerp(v00, v10, ti), Mathf.Lerp(v01, v11, ti), tj);
        }
    }
}
