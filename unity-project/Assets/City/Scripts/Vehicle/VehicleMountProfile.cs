using System;
using UnityEngine;
using City.OSM;
using City.Diagnostics;
using Huntix.Bridge;

namespace City.Vehicle
{
    /// <summary>Dove sta il guidatore quando sale a bordo.</summary>
    public enum VehicleMountKind
    {
        /// <summary>Auto, furgoni, pullman: seduto dentro l'abitacolo.</summary>
        Inside,
        /// <summary>Moto, scooter, bici: a cavallo della sella.</summary>
        Straddled
    }

    /// <summary>
    /// Profilo di guida originale Huntix (24/11/2026): lo stesso personaggio
    /// sale in modi diversi a seconda del mezzo, senza toccare la fisica
    /// esistente (Rigidbody + velocità scalare) né il numero di veicoli.
    ///
    /// Non copia codice dai repository auditati: SanAndreasUnity (MIT) ha solo
    /// ispirato l'idea di uno stato di ingresso/uscita già presente in Game.
    ///
    /// Ogni salita è un evento Unity → Android (`VehicleMountChanged`) con
    /// tipo, posto, codice veicolo e coordinate; Android lo persistono e lo
    /// restituisce a Unity all'apertura successiva (`RestoreFromAndroid`).
    /// </summary>
    public static class VehicleMountProfile
    {
        public const string PrefKey = "huntix.vehicle.lastMount";
        private const string KindKey = "huntix.vehicle.lastMount.kind";

        public static string LastVehicleType { get; private set; }
        public static VehicleMountKind LastKind { get; private set; }

        /// <summary>Lettura senza effetti collaterali: niente eventi e niente uova.</summary>
        public static void Initialize()
        {
            if (LastVehicleType != null) return;
            LastVehicleType = PlayerPrefs.GetString(PrefKey, "");
            LastKind = ParseKind(PlayerPrefs.GetString(KindKey, "inside"));
        }

        public static VehicleMountKind KindOf(VehicleData data)
        {
            if (data == null) return VehicleMountKind.Inside;
            switch (data.category)
            {
                case VehicleCategory.Motorcycle:
                case VehicleCategory.Scooter:
                case VehicleCategory.EBike:
                    return VehicleMountKind.Straddled;
                default:
                    return VehicleMountKind.Inside;
            }
        }

        /// <summary>Posizione del pivot del player (i piedi) rispetto al veicolo.
        /// Per le auto si riuse il sedile già costruito da VehicleInterior; per
        /// il pullman la cabina è più alta e più avanti; sui due ruoti il
        /// personaggio sale sopra la sella, non dentro la carrozzeria.</summary>
        public static Vector3 DriverLocalPosition(VehicleController vc)
        {
            if (vc == null) return new Vector3(0f, 1.2f, 0f);

            string name = vc.data != null ? vc.data.vehicleName : "";
            if (KindOf(vc.data) == VehicleMountKind.Straddled)
            {
                float y = name == "Moto" ? 0.55f
                        : name == "Scooter" ? 0.45f : 0.40f;
                return new Vector3(0f, y, -0.05f);
            }

            if (name.IndexOf("Pullman", StringComparison.OrdinalIgnoreCase) >= 0)
                return new Vector3(-0.7f, 2.05f, 3.1f);

            VehicleInterior interior = vc.GetComponent<VehicleInterior>();
            Vector3 seat = interior != null
                ? interior.driverSeatPos
                : new Vector3(-0.45f, 0.35f, 0.35f);
            // driverSeatPos è il centro del cuscino: il pivot del player resta
            // un po' più basso, così il corpo siede invece di stare in piedi.
            return new Vector3(seat.x, Mathf.Max(0.1f, seat.y - 0.15f), seat.z);
        }

        /// <summary>Camera di guida per tipo di mezzo: i due ruoti hanno una
        /// spring arm più corta e più bassa, il pullman più alta e più larga.
        /// I valori delle auto restano quelli storici (12 / 5 / 15).</summary>
        public static void CameraProfile(VehicleMountKind kind, string vehicleName,
            out float distance, out float height, out float pitch)
        {
            if (kind == VehicleMountKind.Straddled)
            {
                distance = 7.5f; height = 2.6f; pitch = 8f;
            }
            else if (vehicleName != null && vehicleName.IndexOf(
                "Pullman", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                distance = 14f; height = 6f; pitch = 16f;
            }
            else
            {
                distance = 12f; height = 5f; pitch = 15f;
            }
        }

        /// <summary>Unity → Android + telemetria. Chiamato una volta per salita.</summary>
        public static void Notify(VehicleController vc, VehicleMountKind kind, string code)
        {
            Initialize();
            string type = vc != null && vc.data != null ? vc.data.vehicleName : "veicolo";
            LastVehicleType = type;
            LastKind = kind;
            PlayerPrefs.SetString(PrefKey, type);
            PlayerPrefs.SetString(KindKey, kind == VehicleMountKind.Straddled
                ? "straddled" : "inside");
            PlayerPrefs.Save();

            string lat = "0", lng = "0";
            if (vc != null && WorldOrigin.Initialized)
            {
                var g = WorldOrigin.ToGeo(vc.transform.position);
                lat = g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture);
            }
            string json = "{\"type\":\"" + Escape(type) +
                "\",\"mount\":\"" + (kind == VehicleMountKind.Straddled
                    ? "straddled" : "inside") +
                "\",\"code\":\"" + Escape(code ?? "") +
                "\",\"lat\":\"" + lat + "\",\"lng\":\"" + lng +
                "\",\"tsMt\":" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + "}";
            try { UnityBridge.SendMessageToAndroid("VehicleMountChanged", json); }
            catch (Exception e)
            {
                OsmDiag.Log("[Veicolo] VehicleMountChanged failed: " + e.Message);
            }

            SessionTelemetry telemetry = SessionTelemetry.Instance;
            if (telemetry != null)
                telemetry.Mark("VEICOLO salita tipo={0} posto={1} code={2}",
                    type, kind == VehicleMountKind.Straddled ? "sopra" : "dentro", code ?? "");
        }

        /// <summary>Android → Unity: ultimo mezzo noto, ripristinato all'apertura
        /// della scena City. Non sale davvero in auto e non assegna uova: è uno
        /// stato di profilo, come qualità/input touch/mondo.</summary>
        public static void RestoreFromAndroid(string type, string kind)
        {
            if (string.IsNullOrEmpty(type)) return;
            Initialize();
            LastVehicleType = type;
            LastKind = ParseKind(kind);
            PlayerPrefs.SetString(PrefKey, type);
            PlayerPrefs.SetString(KindKey, LastKind == VehicleMountKind.Straddled
                ? "straddled" : "inside");
            PlayerPrefs.Save();

            SessionTelemetry telemetry = SessionTelemetry.Instance;
            if (telemetry != null)
                telemetry.Mark("VEICOLO ripristinato da Android: {0} ({1})",
                    type, LastKind == VehicleMountKind.Straddled ? "sopra" : "dentro");
        }

        private static VehicleMountKind ParseKind(string value)
        {
            return string.Equals(value, "straddled", StringComparison.OrdinalIgnoreCase)
                ? VehicleMountKind.Straddled : VehicleMountKind.Inside;
        }

        private static string Escape(string text)
        {
            if (string.IsNullOrEmpty(text)) return "";
            return text.Replace("\\", "\\\\").Replace("\"", "\\\"");
        }
    }
}
