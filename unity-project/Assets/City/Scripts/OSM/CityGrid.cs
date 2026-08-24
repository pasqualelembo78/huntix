using System;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Griglia geografica fissa del mondo HUNTIX: tile 10 km (server) e
    /// chunk 1 km (client). I valori DEVONO combaciare con tile_builder.py:
    ///   origine (34N, 5E), passo tile lat 0.090 deg / lon 0.121 deg.
    /// Le chiavi sono indici globali stabili: non cambiano mai con il rebase,
    /// quindi chunk e tile restano identificabili ovunque nel mondo.
    /// </summary>
    public static class CityGrid
    {
        public const double OriginLat = 34.0;
        public const double OriginLng = 5.0;
        public const double TileLatStep = 0.090;
        public const double TileLngStep = 0.121;
        public const int ChunksPerTile = 10;
        public const double ChunkLatStep = TileLatStep / ChunksPerTile; // ~1000 m
        public const double ChunkLngStep = TileLngStep / ChunksPerTile;

        public const float ChunkSizeM = 1000f;

        public static Vector2Int ChunkIndexOf(double lat, double lng)
        {
            return new Vector2Int(
                (int)Math.Floor((lat - OriginLat) / ChunkLatStep),
                (int)Math.Floor((lng - OriginLng) / ChunkLngStep));
        }

        public static GeoCoord ChunkCenter(Vector2Int c)
        {
            return new GeoCoord(
                OriginLat + (c.x + 0.5) * ChunkLatStep,
                OriginLng + (c.y + 0.5) * ChunkLngStep);
        }

        /// <summary>Angolo SW del chunk.</summary>
        public static GeoCoord ChunkCorner(Vector2Int c)
        {
            return new GeoCoord(
                OriginLat + c.x * ChunkLatStep,
                OriginLng + c.y * ChunkLngStep);
        }

        /// <summary>BBox locale (metri, rispetto al centro chunk) dei limiti del chunk.</summary>
        public static Rect ChunkLocalBounds()
        {
            return new Rect(-ChunkSizeM * 0.5f, -ChunkSizeM * 0.5f, ChunkSizeM, ChunkSizeM);
        }

        /// <summary>Divisione intera con segno corretto verso -inf (floor division).</summary>
        private static int FloorDiv(int a, int b)
        {
            int q = a / b;
            if ((a % b != 0) && ((a < 0) != (b < 0))) q--;
            return q;
        }

        /// <summary>Chiave tile server ("IT_xxx_yyy") che contiene questo chunk.</summary>
        public static string TileKeyOfChunk(Vector2Int chunk)
        {
            int ilat = FloorDiv(chunk.x, ChunksPerTile);
            int ilon = FloorDiv(chunk.y, ChunksPerTile);
            return $"IT_{ilat:000}_{ilon:000}";
        }
    }
}
