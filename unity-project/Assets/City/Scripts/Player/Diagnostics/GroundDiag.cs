using UnityEngine;
using City.OSM;

namespace City.Player
{
    /// <summary>
    /// Diagnostica verticale del player, OVERLAY FISSO (debug temporaneo).
    /// Collocato sul player root, mostra a schermo la catena completa delle
    /// quote che decidono dove stanno i piedi rispetto alla superficie, e
    /// replica la stessa riga su logcat ("OSM") ogni ~2 s per il confronto
    /// anche offline (OsmDiag scrive direttamente su AppLog Android).
    ///
    /// Numeri:
    ///   PIEDI/groundRef = transform.position.y - feetFromPivot
    ///     NB: feetFromPivot = cc.height*0.5 - cc.center.y (~1 m con hero rig)
    ///   sonda (GroundSnapper.SampleGround) = superficie FISICA sotto i piedi:
    ///     h (quota), source (ColliderShort/ColliderLong/DEM), near, nome collider
    ///   DEM locale (TileElevation.HeightAtWorld)
    ///   grounded = controller.isGrounded
    ///
    /// Se groundRef > strada visibile di metri ma la sonda dice h=groundRef e
    /// near=true, il collider sotto e' PIU' ALTO della superficie che vedi
    /// (strada drappata su registro DEM diverso / terrain da geo.ele).
    /// Se h balla mentre cammini e source=DEM/Fallback, e' la quota altimetrica
    /// a cambiare. Se h e' costante ma il modello sembra in aria, e' offset
    /// rig/modello (LayFootOnAnchor).
    /// </summary>
    public class GroundDiag : MonoBehaviour
    {
        private const float LogEveryS = 2f;
        private const int SummaryEveryS = 10;   // log riassuntivo piu' raro

        private CharacterController _cc;
        private float _nextLogAt;
        private int _logCount;

        // ultimo campione misurato (per il riassunto periodico)
        private float _lastFeetY;
        private float _lastSurfaceY;
        private string _lastSource;
        private string _lastSurfaceName;
        private float _lastDem;
        private float _lastAsphaltY;
        private float _lastVisGap;
        private bool _lastNear;

        // l'asfalto (roadsGo) non ha collider: e' drappato su TileElevation
        // + Y_ROAD (0.03), mentre il collider fisico e' il Terreno geo.ele.
        private const float AsphaltLift = 0.03f;

        public static GroundDiag Ensure(GameObject root)
        {
            if (root == null) return null;
            var d = root.GetComponent<GroundDiag>();
            if (d == null) d = root.AddComponent<GroundDiag>();
            return d;
        }

        private void Start()
        {
            _cc = GetComponent<CharacterController>();
            _nextLogAt = Time.time;   // primo log immediato
            _logCount = 0;
            OsmDiag.Log("[GroundDiag] overlay attivo su " + gameObject.name +
                ": DIAGNOSTICA TEMPORANEA");
        }

        private void Update()
        {
            Sample();

            if (City.Game.Instance != null &&
                !City.Game.Instance.IsDriving &&
                Time.time >= _nextLogAt)
            {
                _nextLogAt = Time.time + LogEveryS;
                _logCount++;
                LogLine(_logCount % SummaryEveryS == 0);
            }
        }

        private void Sample()
        {
            if (_cc == null)
            {
                _cc = GetComponent<CharacterController>();
                if (_cc == null) return;
            }

            float feetFromPivot = _cc.height * 0.5f - _cc.center.y;
            float feetY = transform.position.y - feetFromPivot;
            Vector3 feetPos = new Vector3(transform.position.x, feetY,
                transform.position.z);

            GroundSample s = GroundSnapper.SampleGround(feetPos, transform);
            _lastFeetY = feetY;
            _lastSurfaceY = s.found ? s.height : float.NaN;
            _lastSource = s.found ? s.source.ToString() : "NONE";
            _lastSurfaceName = s.found && !string.IsNullOrEmpty(s.surfaceName)
                ? s.surfaceName : "";
            _lastDem = TileElevation.HeightAtWorld(transform.position);
            _lastAsphaltY = _lastDem + AsphaltLift;
            _lastVisGap = feetY - _lastAsphaltY;
            _lastNear = s.near;
        }

        private void LogLine(bool summary)
        {
            string name = gameObject.name;
            string demStr = float.IsNaN(_lastDem)
                ? "NA" : _lastDem.ToString("F1",
                    System.Globalization.CultureInfo.InvariantCulture);
            OsmDiag.Log("[GroundDiag] " + name +
                " feetY=" + _lastFeetY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture) +
                " surf='" + _lastSurfaceName + "'" +
                " h=" + (float.IsNaN(_lastSurfaceY)
                    ? "NA" : _lastSurfaceY.ToString("F2",
                        System.Globalization.CultureInfo.InvariantCulture)) +
                " src=" + _lastSource +
                " near=" + _lastNear +
                " dem=" + demStr +
                " asfY=" + _lastAsphaltY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture) +
                " visGap=" + _lastVisGap.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture) +
                " gcdiff=" + (float.IsNaN(_lastSurfaceY)
                    ? "NA" : (_lastSurfaceY - _lastFeetY).ToString("F2",
                        System.Globalization.CultureInfo.InvariantCulture)) +
                (summary ? " [SUMMARY]" : ""));
        }

        private void OnGUI()
        {
            if (_cc == null)
            {
                _cc = GetComponent<CharacterController>();
                if (_cc == null) return;
            }

            float feetFromPivot = _cc.height * 0.5f - _cc.center.y;
            string sName = _lastSurfaceName.Length > 24
                ? _lastSurfaceName.Substring(0, 24) : _lastSurfaceName;
            string hStr = float.IsNaN(_lastSurfaceY)
                ? "NA" : _lastSurfaceY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture);
            string demStr = float.IsNaN(_lastDem)
                ? "NA" : _lastDem.ToString("F1",
                    System.Globalization.CultureInfo.InvariantCulture);

            string vg = _lastVisGap.ToString("F2",
                System.Globalization.CultureInfo.InvariantCulture);
            string asf = _lastAsphaltY.ToString("F2",
                System.Globalization.CultureInfo.InvariantCulture);
            string txt =
                "PIEDI y=" + _lastFeetY.ToString(
                    "F2", System.Globalization.CultureInfo.InvariantCulture) +
                "  (fromPivot " + feetFromPivot.ToString(
                    "F2", System.Globalization.CultureInfo.InvariantCulture) + ")\n" +
                "SOLLA [" + _lastSource + "] h=" + hStr +
                "  " + sName + "\n" +
                "  near=" + _lastNear +
                "  gcdiff=" + (float.IsNaN(_lastSurfaceY)
                    ? "NA" : (_lastSurfaceY - _lastFeetY).ToString(
                        "F2", System.Globalization.CultureInfo.InvariantCulture)) + "\n" +
                "ASFALTO y=" + asf +
                "  visGap=" + vg + "\n" +
                "DEM locale=" + demStr +
                "  grounded=" + _cc.isGrounded;

            // Stub-safe per cscheck: solo GUI.Label + GUIStyle.fontSize.
            // Doppio disegno (ombra + testo) per leggibilita' su sfondo chiaro.
            var shadow = new GUIStyle();
            shadow.fontSize = 20;
            GUI.Label(new Rect(11f, 11f, 430f, 130f), txt, shadow);
            var style = new GUIStyle();
            style.fontSize = 20;
            GUI.Label(new Rect(8f, 8f, 430f, 130f), txt, style);
        }
    }
}