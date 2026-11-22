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
        private float _nextSoleCheckAt;

        // ultimo campione misurato (per il riassunto periodico)
        private float _lastFeetY;
        private float _lastSurfaceY;
        private string _lastSource;
        private string _lastSurfaceName;
        private float _lastDem;
        private float _lastAsphaltY;
        private float _lastVisGap;
        private bool _lastNear;

        // Stima dell'asfalto quando la mesh stradale non e' ancora stata
        // generata: Y_ROAD (0.03) sopra il DEM. NON e' una misura: la mesh
        // stradale vera ha un MeshCollider (ChunkBuilder), quindi [Chain]
        // legge le quote reali dei collider e le riporta a schermo/log.
        private const float AsphaltLift = 0.03f;

        // ── Catena visiva: SOLO lettura, nessuna correzione di posa ──
        // Serve a distinguere tre cause che nei log precedenti erano
        // indistinguibili: (a) collider del suolo PIU' ALTO della mesh visiva,
        // (b) piedi dell'avatar IK che non raggiungono il collider, (c) bacino
        // sollevato oltre lo span anatomico delle gambe.
        private const float ChainTop = 3f;
        private const float ChainDepth = 6f;
        private Animator _heroAnimator;
        private int _lastChainFrame = -1;
        private string _lastColliderStack = "";
        private float _lastFootBoneMin = float.NaN;
        private float _lastFootBoneMax = float.NaN;
        private float _lastHipsBoneY = float.NaN;
        private float _lastBodyY = float.NaN;
        private float _lastSmrMinY = float.NaN;
        private float _lastSmrMaxY = float.NaN;

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

        /// <summary>
        /// PROVA DECISIVA sul galleggiamento: misura in LateUpdate, cioe' DOPO
        /// animazione e OnAnimator IK, la suola VISIVA (vertici SKINNATI reali,
        /// non i bounds SMR) e il piede FISICO (CharacterController). I bounds
        /// SMR del modello Remy scendono 0,28 unita' modello SOTTO la scarpa:
        /// il vecchio delta calcolato sui bounds diceva 0 con la suola ancora
        /// a 7-10 cm da terra. "suolo" e' la distanza dalla superficie fisica
        /// under i piedi; "caviglia" permette di verificare che l'IK I'alzi
        /// dello spessore corretto della scarpa.
        /// </summary>
        private void LateUpdate()
        {
            if (Time.time < _nextSoleCheckAt) return;
            _nextSoleCheckAt = Time.time + LogEveryS;

            if (_cc == null)
            {
                _cc = GetComponent<CharacterController>();
                if (_cc == null) return;
            }

            float feetFromPivot = _cc.height * 0.5f - _cc.center.y;
            float feetY = transform.position.y - feetFromPivot;

            float smrMin = float.NaN;
            var smrs = GetComponentsInChildren<SkinnedMeshRenderer>(true);
            for (int i = 0; i < smrs.Length; i++)
            {
                if (smrs[i] == null || !smrs[i].gameObject.activeInHierarchy) continue;
                float mn = smrs[i].bounds.min.y;
                if (float.IsNaN(smrMin) || mn < smrMin) smrMin = mn;
            }

            // MISURA VERA: vertici skinnati. I bounds SMR sul modello Remy
            // scendono 0,28 unita' modello (~0,12 m in scala) SOTTO la suola:
            // il vecchio "delta=0" era un artefatto, non un appoggio.
            float trueSole = float.NaN;
            SoleGeometry.SoleY(transform, out trueSole);

            float hipsY = float.NaN;
            float ankleY = float.NaN;
            if (_heroAnimator == null)
                _heroAnimator = GetComponentInChildren<Animator>(true);
            if (_heroAnimator != null && _heroAnimator.avatar != null &&
                _heroAnimator.avatar.isHuman)
            {
                Transform hips = _heroAnimator.GetBoneTransform(HumanBodyBones.Hips);
                if (hips != null) hipsY = hips.position.y;
                Transform lf = _heroAnimator.GetBoneTransform(HumanBodyBones.LeftFoot);
                Transform rf = _heroAnimator.GetBoneTransform(HumanBodyBones.RightFoot);
                if (lf != null) ankleY = lf.position.y;
                if (rf != null) ankleY = float.IsNaN(ankleY)
                    ? rf.position.y : Mathf.Min(ankleY, rf.position.y);
            }

            string d = float.IsNaN(trueSole) ? "NA"
                : (trueSole - feetY).ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture);
            string vsTerrain = float.IsNaN(trueSole) || float.IsNaN(_lastSurfaceY)
                ? "NA" : (trueSole - _lastSurfaceY).ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture);
            OsmDiag.Log("[SoleCheck] frame=" + Time.frameCount +
                " trueSole=" + (float.IsNaN(trueSole) ? "NA" : trueSole.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture)) +
                " feetY=" + feetY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture) +
                " delta=" + d +
                " suolo=" + vsTerrain +
                " caviglia=" + (float.IsNaN(ankleY) ? "NA" : ankleY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture)) +
                " boundsMin=" + (float.IsNaN(smrMin) ? "NA" : smrMin.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture)) +
                " hipsY=" + (float.IsNaN(hipsY) ? "NA" : hipsY.ToString("F2",
                    System.Globalization.CultureInfo.InvariantCulture)) +
                " ccGrounded=" + _cc.isGrounded);
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

        /// <summary>Misura la catena visiva completa una volta per frame di log:
        /// tutti i collider calpestabili della colonna (nome + quota reale) e le
        /// quote vere dei piedi IK, del bacino e di bodyPosition. Nessuna scrittura
        /// su transform/bone: se il personaggio galleggia, qui si vede SE il
        /// collider e' sopra la mesh oppure se e' l'avatar a non toccarlo.</summary>
        private void MeasureVisualChain()
        {
            if (_lastChainFrame == Time.frameCount) return;
            _lastChainFrame = Time.frameCount;

            float feetFromPivot = _cc.height * 0.5f - _cc.center.y;
            Vector3 feetPos = new Vector3(transform.position.x,
                transform.position.y - feetFromPivot, transform.position.z);

            // Tutti i collider calpestabili della colonna, ordinati per
            // distanza dal livello-piedi: "Strade@0.03,Terreno@-0.05,..."
            // dice esattamente quale superficie fisica sta sotto/sopra i piedi.
            RaycastHit[] hits = Physics.RaycastAll(
                feetPos + Vector3.up * ChainTop, Vector3.down, ChainDepth,
                GroundSnapper.WalkableMask, QueryTriggerInteraction.Ignore);
            System.Array.Sort(hits, delegate(RaycastHit a, RaycastHit b)
            {
                return Mathf.Abs(a.point.y - feetPos.y)
                    .CompareTo(Mathf.Abs(b.point.y - feetPos.y));
            });
            var sb = new System.Text.StringBuilder();
            int shown = 0;
            for (int i = 0; i < hits.Length && shown < 6; i++)
            {
                RaycastHit h = hits[i];
                if (h.collider == null || h.normal.y < GroundSnapper.ShortNorm) continue;
                if (sb.Length > 0) sb.Append(',');
                sb.Append(h.collider.name).Append('@').Append(h.point.y.ToString(
                    "F2", System.Globalization.CultureInfo.InvariantCulture));
                shown++;
            }
            _lastColliderStack = sb.Length > 0 ? sb.ToString() : "(nessuno)";

            // Quote dell'avatar: osso piede (dove l'IK porta davvero la suola),
            // bacino e bodyPosition (che il secondo IK sposta in giu').
            _lastFootBoneMin = float.NaN;
            _lastFootBoneMax = float.NaN;
            _lastHipsBoneY = float.NaN;
            _lastBodyY = float.NaN;
            _lastSmrMinY = float.NaN;
            _lastSmrMaxY = float.NaN;

            // Bounds REALI degli SkinnedMeshRenderer: e' qui che LayFootOnAnchor
            // appoggia il modello (b.min.y -> feetAnchor). Confrontarli con
            // footBoneY (caviglia IK) e con i collider dice se il "galleggia"
            // nasce dal bounds (suola visiva sopra la caviglia) o dall'IK.
            var smrs = GetComponentsInChildren<SkinnedMeshRenderer>(true);
            bool anySmr = false;
            for (int i = 0; i < smrs.Length; i++)
            {
                if (smrs[i] == null || !smrs[i].gameObject.activeInHierarchy) continue;
                float mn = smrs[i].bounds.min.y;
                float mx = smrs[i].bounds.max.y;
                if (!anySmr) { _lastSmrMinY = mn; _lastSmrMaxY = mx; anySmr = true; }
                else
                {
                    if (mn < _lastSmrMinY) _lastSmrMinY = mn;
                    if (mx > _lastSmrMaxY) _lastSmrMaxY = mx;
                }
            }

            if (_heroAnimator == null)
                _heroAnimator = GetComponentInChildren<Animator>(true);
            if (_heroAnimator != null && _heroAnimator.avatar != null &&
                _heroAnimator.avatar.isHuman)
            {
                Transform lf = _heroAnimator.GetBoneTransform(HumanBodyBones.LeftFoot);
                Transform rf = _heroAnimator.GetBoneTransform(HumanBodyBones.RightFoot);
                Transform hips = _heroAnimator.GetBoneTransform(HumanBodyBones.Hips);
                float lo = lf != null ? lf.position.y : float.NaN;
                float ro = rf != null ? rf.position.y : float.NaN;
                if (!float.IsNaN(lo) && !float.IsNaN(ro))
                {
                    _lastFootBoneMin = Mathf.Min(lo, ro);
                    _lastFootBoneMax = Mathf.Max(lo, ro);
                }
                if (hips != null) _lastHipsBoneY = hips.position.y;
                _lastBodyY = _heroAnimator.bodyPosition.y;
            }
        }

        private static string F(float v)
        {
            return float.IsNaN(v) ? "NA"
                : v.ToString("F2", System.Globalization.CultureInfo.InvariantCulture);
        }

        private void LogLine(bool summary)
        {
            MeasureVisualChain();
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
            OsmDiag.Log("[GroundDiag][Chain] " + name +
                " feetY=" + F(_lastFeetY) +
                " colliders=" + _lastColliderStack +
                " footBoneY=" + F(_lastFootBoneMin) + ".." + F(_lastFootBoneMax) +
                " hipsY=" + F(_lastHipsBoneY) +
                " bodyY=" + F(_lastBodyY) +
                " smrY=" + F(_lastSmrMinY) + ".." + F(_lastSmrMaxY));
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