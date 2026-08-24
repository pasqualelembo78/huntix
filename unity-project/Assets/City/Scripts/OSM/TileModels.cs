using System;

namespace City.OSM
{
    /// <summary>
    /// Modelli dati delle tile prodotte da backend/preprocessing
    /// (osm_italy_processor.py). I nomi dei campi sono allineati al formato
    /// wire del server per JsonUtility. Le coppie geografiche viaggiano come
    /// {"a": lat, "o": lon} in double.
    /// </summary>

    [Serializable]
    public class GeoLL
    {
        public double a;   // latitudine
        public double o;   // longitudine

        public GeoCoord ToCoord() => new GeoCoord(a, o);
    }

    // ── Road graph tile ─────────────────────────────────────────

    [Serializable]
    public class TileNode
    {
        public long id;
        public double lat;
        public double lon;
        public string junction;   // DeadEnd | Simple | Real
    }

    [Serializable]
    public class TileArc
    {
        public int id;
        public long from;
        public long to;
        public GeoLL[] waypoints;
        public string road_name;
        public string highway;
        public float width;
        public float length_m;
        public bool tunnel;
        public bool bridge;
        public float maxspeed;
        public bool oneway;
        public int lanes;
    }

    [Serializable]
    public class TileGraphDoc
    {
        public string tile;
        public double[] bbox;      // latmin, lonmin, latmax, lonmax
        public TileNode[] nodes;
        public TileArc[] arcs;

        public GeoCoord Center()
        {
            if (bbox == null || bbox.Length < 4) return default(GeoCoord);
            return new GeoCoord((bbox[0] + bbox[2]) * 0.5, (bbox[1] + bbox[3]) * 0.5);
        }
    }

    // ── Geometry tile (placement per Kenney) ────────────────────

    [Serializable]
    public class TileRoadRec
    {
        public string nm;
        public string hw;
        public GeoLL[] pts;
    }

    [Serializable]
    public class TileBuildingRec
    {
        public long id;
        public double[] c;     // [lat, lon] centro impronta
        public float[] d;      // [width, depth] metri (lato lungo = d[0])
        public int r;          // rotazione gradi attorno a Y
        public string t;       // tipo OSM building ("yes", "commercial", ...)
        public string nm;      // nome opzionale
    }

    [Serializable]
    public class TileParkRec
    {
        public long id;
        public string kd;      // park | garden | wood | forest
        public string nm;
        public GeoLL[] poly;
    }

    [Serializable]
    public class TileAirportRec
    {
        public long id;
        public string nm;
        public double[] c;
        public float[] d;
        public int r;
    }

    [Serializable]
    public class TileAddrRec
    {
        public double a;   // latitudine
        public double o;   // longitudine
        public string n;   // numero civico ("126")
        public string s;   // via (addr:street OSM)
    }

    [Serializable]
    public class TilePoiRec
    {
        public long id;
        public string t;       // dealer | repair | garage
        public double[] p;     // [lat, lon]
        public string nm;      // nome OSM opzionale
    }

    [Serializable]
    public class TileGeoDoc
    {
        public string tile;
        public double[] bbox;
        public TileRoadRec[] roads;
        public TileBuildingRec[] buildings;
        public TileParkRec[] parks;
        public GeoLL[] trees;
        public GeoLL[] signals;
        public TileAirportRec[] airports;
        public TileAddrRec[] addrs;   // civici (iniezione lato server)
        public TilePoiRec[] pois;     // concessionarie/officine/garage

        public GeoCoord Center()
        {
            if (bbox == null || bbox.Length < 4) return default(GeoCoord);
            return new GeoCoord((bbox[0] + bbox[2]) * 0.5, (bbox[1] + bbox[3]) * 0.5);
        }
    }
}
