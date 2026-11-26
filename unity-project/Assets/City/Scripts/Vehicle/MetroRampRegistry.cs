using System.Globalization;

namespace City.Vehicle
{
    /// <summary>
    /// Rampe METROPOLITANA di Foggia, vicine alla casa visitabile di Via
    /// Silvio Pellico 53. Sono POI di GIOCO deterministici (come le rampe
    /// `SOTTERRANEO` già generate nelle tile): non dichiarano ingressi metro
    /// reali verificati, ma danno un accesso sotterraneo stabile vicino a casa.
    ///
    /// Vivono in codice, non nelle geo: così non cambia il formato tile e
    /// nessun cache version deve essere alzato (niente ri-download di mappe).
    /// </summary>
    public static class MetroRampRegistry
    {
        public sealed class Spec
        {
            public string id;
            public string name;
            public double lat;
            public double lng;
        }

        private static readonly Spec[] Configured =
        {
            new Spec
            {
                id = "foggia-metro-pellico",
                name = "Metro Foggia · Via Silvio Pellico",
                lat = 41.4530955,
                lng = 15.5341185
            },
            new Spec
            {
                id = "foggia-metro-biondi",
                name = "Metro Foggia · Via Fratelli Biondi",
                lat = 41.4533700,
                lng = 15.5346000
            },
            new Spec
            {
                id = "foggia-metro-turati",
                name = "Metro Foggia · Piazza Filippo Turati",
                lat = 41.4526000,
                lng = 15.5357000
            }
        };

        public static Spec[] All { get { return Configured; } }

        /// <summary>Rampa dal nome del cartello/POI (serve all'uovo una-tantum
        /// in UndergroundUI, che riceve solo il VehiclePoiZone).</summary>
        public static Spec FindByName(string name)
        {
            if (string.IsNullOrEmpty(name)) return null;
            for (int i = 0; i < Configured.Length; i++)
            {
                if (Configured[i] != null && Configured[i].name == name)
                    return Configured[i];
            }
            return null;
        }

        /// <summary>Id numerico stabile per il cartello/la zona (il wire dei
        /// POI usa long). Distinto dagli id sintetici delle geo (-1 miliardo).</summary>
        public static long NumericId(int index)
        {
            return -2_000_000_000L - index;
        }

        public static bool IsMetroName(string name)
        {
            return !string.IsNullOrEmpty(name) &&
                name.StartsWith("Metro", System.StringComparison.OrdinalIgnoreCase);
        }

        public static string CoordinateLabel(Spec spec)
        {
            return spec.lat.ToString("F6", CultureInfo.InvariantCulture) + "," +
                spec.lng.ToString("F6", CultureInfo.InvariantCulture);
        }
    }
}
