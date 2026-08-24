using System;
using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Floating origin geografico: il punto (0,0) del mondo Unity corrisponde a
    /// OriginLat/OriginLng. Le conversioni geo->world avvengono in double e
    /// producono float SOLO dopo la sottrazione dall'origine, cosi' la precisione
    /// resta costante ovunque (Roma, Milano, un aereo sopra le Alpi).
    /// Quando il giocatore si allontana > RebaseDistanceM dal centro, Rebase()
    /// sposta l'origine su di lui e notifica tutti i sistemi via OnRebased:
    /// ogni root deve shiftarsi di -delta per restare coerente.
    /// </summary>
    public static class WorldOrigin
    {
        public const double MetersPerDegLat = GeoCoord.MetersPerDegLat;
        public const float RebaseDistanceM = 5000f;

        private static double _lat = 41.9028;   // default: Roma
        private static double _lng = 12.4964;
        private static double _mPerDegLon = GeoCoord.MetersPerDegLon(_lat);
        private static bool _initialized;

        /// <summary>Chiamato DOPO lo shift dell'origine con il delta world
        /// (vecchio - nuovo). Chi riceve deve fare position -= delta.</summary>
        public static event Action<Vector3> OnRebased;

        public static double OriginLat => _lat;
        public static double OriginLng => _lng;
        public static bool Initialized => _initialized;

        /// <summary>
        /// Incrementato a ogni Init() (cambio città / fix GPS definitivo).
        /// Il ChunkManager lo campiona quando accoda un chunk: se cambia
        /// mentre la tile è in download o la build è sospesa, il chunk è
        /// nato attorno a un'altra origine e va buttato senza costruirlo.
        /// NB: il rebase NON tocca l'epoca (stessa città, solo shift).
        /// </summary>
        public static int Epoch { get; private set; }

        public static void Init(double lat, double lng)
        {
            // l'epoca cambia solo se l'origine si sposta DAVVERO (primo init,
            // fix GPS su altra citta'): un Init ripetuto sulle stesse coordinate
            // (reload scena, CoordinateConverter) non deve invalidare i chunk
            bool moved = !_initialized ||
                System.Math.Abs(lat - _lat) > 1e-9 ||
                System.Math.Abs(lng - _lng) > 1e-9;
            _lat = lat;
            _lng = lng;
            _mPerDegLon = GeoCoord.MetersPerDegLon(lat);
            _initialized = true;
            if (moved) Epoch++;
        }

        public static Vector3 ToWorld(double lat, double lng)
        {
            return new Vector3(
                (float)((lng - _lng) * _mPerDegLon),
                0f,
                (float)((lat - _lat) * MetersPerDegLat));
        }

        public static Vector3 ToWorld(GeoCoord c) => ToWorld(c.lat, c.lng);

        public static GeoCoord ToGeo(Vector3 world)
        {
            return new GeoCoord(
                _lat + world.z / MetersPerDegLat,
                _lng + world.x / _mPerDegLon);
        }

        /// <summary>Distanza del punto world dall'origine corrente.</summary>
        public static float DistanceFromOrigin(Vector3 world)
        {
            return new Vector2(world.x, world.z).magnitude;
        }

        /// <summary>
        /// Sposta l'origine sul punto indicato (world coords correnti).
        /// Ritorna true se effettuato. Il chiamante tipico e' il ChunkManager,
        /// che poi shifta chunk roots e player usando il delta dell'evento.
        /// </summary>
        public static bool TryRebase(Vector3 playerWorld)
        {
            if (DistanceFromOrigin(playerWorld) < RebaseDistanceM)
                return false;

            GeoCoord g = ToGeo(playerWorld);
            // delta da applicare agli oggetti: pos_nuova = pos_vecchia - delta
            Vector3 delta = new Vector3(
                (float)((g.lng - _lng) * _mPerDegLon),
                0f,
                (float)((g.lat - _lat) * MetersPerDegLat));

            _lat = g.lat;
            _lng = g.lng;
            _mPerDegLon = GeoCoord.MetersPerDegLon(_lat);
            OnRebased?.Invoke(delta);
            Debug.Log($"[WorldOrigin] rebase su {g} (delta {delta})");
            return true;
        }
    }
}
