using UnityEngine;
using City.Vehicle.Mechanics;

namespace City.Vehicle
{
    /// <summary>
    /// Motore per mezzi su ROTAIA (treno, tram, metropolitana).
    /// Il veicolo segue binari: posso usare una guida a percorso spline
    /// oppure, in assenza di grafo ferroviario, una guida "fisica" che
    /// mantiene il mezzo sulla traiettoria (rallenta nelle curve secondo il
    /// raggio, come un treno reale — il corpo tenderà naturalmente a
    /// deragliare a velocità eccessiva in curva, rientrando se non si frena).
    ///
    /// Input: acceleratore/retromarcia, freno. Il mezzo accelera/decelera
    /// lungo il proprio forward e NIENT'altro (binario = nessun drifting).
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public class TrainMotor : MonoBehaviour
    {
        [Header("Prestazioni")]
        public float maxSpeedRoll = 28f;      // m/s (~100 km/h, metropolitana/tram)
        public float maxSpeedRail = 36f;      // m/s binario ad alta velocità
        public float acceleration = 3.5f;
        public float brakeForce = 8f;

        [Header("Assetto binario (sforzo laterale)")]
        public float railLateralDamp = 3f;    // quanto il binario tiene il mezzo dritto
        public float derailLateralForce = 18f;// oltre questo spinta laterale → deraglio
        public float maxDerailMomentum = 55f; // prodotto spinta×velocità che fa deragliare
        public bool derailed = false;

        [Header("Parti meccaniche (opzionale Via Sistema)")]
        public VehiclePartDamageSystem damage;

        // addestramento: se il mezzo eredita da VehicleController con danno
        // per parti, usiamo lo stesso EffectiveGrip per sforzo degradato.

        private Rigidbody rb;
        private float throttle;
        private float brake;
        private float currentSpeed;

        private void Awake()
        {
            rb = GetComponent<Rigidbody>();
            rb.mass = Mathf.Max(2000f, rb.mass);   // treno pesante
            rb.angularDrag = 6f;
        }

        public void SetInput(float throttle, float brake)
        {
            this.throttle = throttle;
            this.brake = brake;
        }

        public float Speed => currentSpeed;

        public bool IsDerailed { get { return derailed; } }

        private float Eff(VehiclePartType t)
        {
            if (damage == null) return 1f;
            return damage.GetEffectFactor(t);
        }

        private void FixedUpdate()
        {
            if (rb == null) return;

            float engEff = Eff(VehiclePartType.Engine) * Eff(VehiclePartType.Gearbox);
            bool engineDead = engEff <= 0f;

            float v = currentSpeed;

            // accelerazione lungo il forward
            if (!engineDead && throttle > 0f)
            {
                float target = throttle * maxSpeedRail * Eff(VehiclePartType.Driveshaft);
                v = Mathf.MoveTowards(v, target, acceleration * engEff * Time.fixedDeltaTime);
            }

            // freno (marca anche l'asse)
            if (brake > 0f)
            {
                float brakeEff = Eff(VehiclePartType.Brake);
                v = Mathf.MoveTowards(v, 0f, brakeForce * brakeEff * Time.fixedDeltaTime);
            }
            else
            {
                // frenatura naturale (attrito di rotolamento)
                v = Mathf.MoveTowards(v, 0f, 0.4f * Time.fixedDeltaTime);
            }

            // limite di velocità in curva: spezza la frenata se troppo veloce
            float maxCurve = CurveSpeedLimit();
            if (v > maxCurve)
                v = Mathf.Lerp(v, maxCurve, 1.2f * Time.fixedDeltaTime);

            currentSpeed = v;

            // muovi il corpo SOLO lungo forward (binario: niente deriva)
            Vector3 vel = transform.forward * currentSpeed;
            vel.y = rb.velocity.y;   // gravità
            rb.velocity = vel;

            // sforzo laterale → deraglio se troppo grande
            Vector3 latVel = rb.transform.InverseTransformDirection(rb.velocity);
            float lateral = latVel.x;   // componente laterale locale
            if (currentSpeed > 0.5f && Mathf.Abs(lateral) > derailLateralForce * Time.fixedDeltaTime)
            {
                // trasferisci lo sforzo laterale → deraglio
                if (lateral > 0f) Derail(true);
            }
        }

        /// <summary>Limite di velocità dettato dal raggio di curva (se il
        /// mezzo ha un follow-path opzionale); altrimenti costo di default.</summary>
        private float CurveSpeedLimit()
        {
            // se non conosciamo il percorso, usiamo il limite da binario
            if (followPath == null) return maxSpeedRail;
            float curve = followPath.CurrentCurvature();
            if (curve <= 0.01f) return maxSpeedRail;
            // limite = sqrt(aderenza * g * raggio): semplificato
            float radius = 1f / curve;
            return Mathf.Min(maxSpeedRail, Mathf.Sqrt(0.5f * 9.81f * radius) * 1.4f);
        }

        private RailFollowPath followPath;

        /// <summary>Collega un percorso ferroviario (facoltativo: il treno lo
        /// segue; senza segue semplicemente il proprio forward limitato).</summary>
        public void SetPath(RailFollowPath path)
        {
            followPath = path;
        }

        private void Derail(bool toLeft)
        {
            derailed = true;
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(
                    "DERAGLIAMENTO! Il treno si e' ribaltato fuori dai binari.");
            Stop();
        }

        public void Stop()
        {
            currentSpeed = 0f;
            throttle = 0f;
            brake = 0f;
            if (rb != null) rb.velocity = Vector3.zero;
        }

        public void ResetTrack()
        {
            derailed = false;
        }
    }

    /// <summary>Endpoint minimo per il follow del percorso ferroviario.</summary>
    public abstract class RailFollowPath : MonoBehaviour
    {
        public abstract float CurrentCurvature();
    }
}
