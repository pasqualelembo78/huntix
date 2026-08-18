using System;

namespace City.OSM
{
    /// <summary>
    /// Modelli dati dell'envelope OSM inviato da Android a Unity per MiAcitma.
    /// Nomi dei campi allineati a OsmCityJsonFactory (Kotlin) per JsonUtility.
    /// </summary>

    [Serializable]
    public class GeoPoint
    {
        public double lat;
        public double lng;
    }

    [Serializable]
    public class OsmRoad
    {
        public long id;
        public string highway;
        public string name;
        public GeoPoint[] points;
    }

    [Serializable]
    public class OsmBuilding
    {
        public long id;
        public string kind;
        public string name;
        public string shop;
        public string amenity;
        public double height;
        public int levels;
        public GeoPoint[] points;
    }

    [Serializable]
    public class OsmPark
    {
        public GeoPoint[] points;
    }

    [Serializable]
    public class OsmCityEnvelope
    {
        public double centerLat;
        public double centerLng;
        public int radiusMeters;
        public bool done;
        public OsmRoad[] roads;
        public OsmBuilding[] buildings;
        public GeoPoint[] trees;
        public OsmPark[] parks;
    }

    [Serializable]
    public class BridgeLocation
    {
        public double lat;
        public double lng;
        public bool mock;
    }
}
