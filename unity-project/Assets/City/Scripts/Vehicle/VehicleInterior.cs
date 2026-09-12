using UnityEngine;

namespace City.Vehicle
{
    /// <summary>
    /// Costruisce proceduralmente un'abitacolo VISIBILE dentro la carrozzeria
    /// del veicolo: sedili (guidatore, passeggero, posteriori), cruscotto,
    /// volante e specchietto. Attaccato alla radice del veicolo; costruisce i
    /// pezzi una volta sola (tutti figli di un nodo "Interior").
    ///
    /// I materiali sono opachi e scuri per non coprire la vista dal parabrezza
    /// ma comunque visibili dalla camera in terza persona quando il tetto
    /// e' assente/traslucido, o dal onboard camera in prima persona.
    /// </summary>
    public class VehicleInterior : MonoBehaviour
    {
        [Header("Dimensioni approssimative abitacolo (locali al veicolo)")]
        public float seatWidth = 0.55f;
        public float seatHeight = 0.5f;
        public float seatDepth = 0.5f;
        public Vector3 driverSeatPos = new Vector3(-0.45f, 0.35f, 0.35f);
        public Vector3 passengerSeatPos = new Vector3(0.45f, 0.35f, 0.35f);
        public Vector3 rearLeftSeatPos = new Vector3(-0.45f, 0.1f, -0.6f);
        public Vector3 rearRightSeatPos = new Vector3(0.45f, 0.1f, -0.6f);
        public Vector3 dashboardPos = new Vector3(0f, 0.62f, 0.72f);
        public Vector3 steeringPos = new Vector3(-0.42f, 0.6f, 0.45f);

        [Header("Colori")]
        public Color seatColor = new Color(0.08f, 0.08f, 0.1f);
        public Color dashColor = new Color(0.12f, 0.12f, 0.15f);
        public Color steelColor = new Color(0.05f, 0.05f, 0.07f);

        private bool built;

        private static Material NewMat(Color c, bool transparent = false)
        {
            var shader = Shader.Find("Standard");
            if (shader == null) shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Diffuse");
            var m = new Material(shader);
            m.color = c;
            if (transparent)
            {
                m.SetFloat("_Mode", 3f);
                m.SetOverrideTag("RenderType", "Transparent");
                m.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
                m.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
                m.SetInt("_ZWrite", 0);
                m.DisableKeyword("_ALPHATEST_ON");
                m.EnableKeyword("_ALPHABLEND_ON");
                m.renderQueue = 3000;
            }
            return m;
        }

        private Transform BuildSeat(string name, Vector3 pos, bool hasBackrest,
            Material seatMat)
        {
            var go = new GameObject(name);
            go.transform.SetParent(transform, false);
            go.transform.localPosition = pos;

            // cuscino
            var pad = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(pad.GetComponent<Collider>());
            pad.name = "pad";
            pad.transform.SetParent(go.transform, false);
            pad.transform.localPosition = new Vector3(0f, seatHeight * 0.35f, 0f);
            pad.transform.localScale = new Vector3(seatWidth, seatHeight * 0.5f, seatDepth);
            var r = pad.GetComponent<Renderer>();
            if (r != null) r.sharedMaterial = seatMat;

            // schienale
            if (hasBackrest)
            {
                var back = GameObject.CreatePrimitive(PrimitiveType.Cube);
                Object.Destroy(back.GetComponent<Collider>());
                back.name = "back";
                back.transform.SetParent(go.transform, false);
                back.transform.localPosition = new Vector3(0f, seatHeight * 0.85f, -seatDepth * 0.28f);
                back.transform.localScale = new Vector3(seatWidth, seatHeight, seatDepth * 0.7f);
                var rb = back.GetComponent<Renderer>();
                if (rb != null) rb.sharedMaterial = seatMat;
            }
            return go.transform;
        }

        /// <summary>Costruisce l'abitacolo una sola volta (idempotente).</summary>
        public void BuildIfNeeded()
        {
            if (built) return;
            Build();
        }

        private void Build()
        {
            built = true;
            var interior = new GameObject("Interior");
            interior.transform.SetParent(transform, false);
            interior.transform.localPosition = Vector3.zero;
            interior.transform.localRotation = Quaternion.identity;

            var seatMat = NewMat(seatColor);
            var dashMat = NewMat(dashColor);
            var steelMat = NewMat(steelColor);

            // sedili
            BuildSeat("Seat_Driver", driverSeatPos, true, seatMat)
                .SetParent(interior.transform, false);
            BuildSeat("Seat_Passenger", passengerSeatPos, true, seatMat)
                .SetParent(interior.transform, false);
            BuildSeat("Seat_RearL", rearLeftSeatPos, true, seatMat)
                .SetParent(interior.transform, false);
            BuildSeat("Seat_RearR", rearRightSeatPos, true, seatMat)
                .SetParent(interior.transform, false);

            // cruscotto (pannello orizzontale sotto il parabrezza)
            var dash = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(dash.GetComponent<Collider>());
            dash.name = "Dashboard";
            dash.transform.SetParent(interior.transform, false);
            dash.transform.localPosition = dashboardPos;
            dash.transform.localScale = new Vector3(1.5f, 0.12f, 0.35f);
            var dR = dash.GetComponent<Renderer>();
            if (dR != null) dR.sharedMaterial = dashMat;

            // volante (toroide semplificato: due toroid sovrapposti non serve,
            // uso una ciambella via Torus primitiva)
            var wheel = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(wheel.GetComponent<Collider>());
            wheel.name = "SteeringWheel";
            wheel.transform.SetParent(interior.transform, false);
            wheel.transform.localPosition = steeringPos;
            wheel.transform.localRotation = Quaternion.Euler(85f, 0f, 0f);
            wheel.transform.localScale = new Vector3(0.4f, 0.05f, 0.4f);
            var wR = wheel.GetComponent<Renderer>();
            if (wR != null) wR.sharedMaterial = steelMat;

            // specchietto retrovisore
            var mirror = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(mirror.GetComponent<Collider>());
            mirror.name = "RearviewMirror";
            mirror.transform.SetParent(interior.transform, false);
            mirror.transform.localPosition = new Vector3(0f, 0.85f, 0.28f);
            mirror.transform.localScale = new Vector3(0.3f, 0.12f, 0.03f);
            var mR = mirror.GetComponent<Renderer>();
            if (mR != null) mR.sharedMaterial = dashMat;
        }
    }
}
