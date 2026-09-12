using UnityEngine;

namespace City.Vehicle.Mechanics
{
    /// <summary>
    /// Tipi di componente meccanico di un veicolo. Ogni parte ha la propria
    /// integrita' e il proprio effetto sul comportamento di guida.
    /// </summary>
    public enum VehiclePartType
    {
        Engine,          // motore: potenza persa se danneggiato, blocco totale se a 0
        Gearbox,         // cambio: cambiata rumorosa/scattante se danneggiato, blocco se 0
        Driveshaft,      // giunto/cardano: trasmissione, perde potenza se danneggiato
        Differential,    // differenziale: distribuzione trazione, difficile in curva se 0
        Suspension,      // sospensioni: comfort/tenuta, instabile se 0
        ShockAbsorber,   // ammortizzatori: molleggio, sobbalza se 0
        Brake,           // freno (per ruota): frenata ridotta se danneggiato
        Tire,            // gomma (per ruota): aderenza persa, scoppia se 0
        Steering,        // sterzo: risposta ridotta se danneggiato
        Bodywork,        // carrozzeria: solo estetica/danno
        Bumper,          // paraurti: assorbe urti, protegge parti sotto
        Battery,         // batteria: avviamento, si scarica se 0
        Electrical,      // elettricità: luci/elettronica, problemi se danneggiata
        Fuel,            // serbatoio/carburante: perdita se forato
        Exhaust,         // scarico: rumore/potenza se danneggiato
        Radiator,        // radiatore: surriscaldamento se 0
        Oil,             // olio: sequestrato motore se 0
        Chassis,         // telaio: rigidità strutturale
    }

    /// <summary>
    /// Rappresenta una singola parte meccanica di un veicolo, con integrita'
    /// 0-100 (100 = perfetto, 0 = rotta) e un effetto sul comportamento.
    /// </summary>
    [System.Serializable]
    public class VehicleComponent
    {
        [Header("Identità")]
        public VehiclePartType type;
        public string partName;
        [Tooltip("Posizione locale approssimativa della parte (per direzione impatto/diagnosi)")]
        public Vector3 localPos;

        [Header("Integrità")]
        [Range(0f, 100f)]
        public float integrity = 100f;   // 100 perfetto, 0 rotto

        [Header("Soglie di funzionamento")]
        [Tooltip("Sotto questa integrità la parte funziona male (non è rotta)")]
        public float degradedThreshold = 50f;
        [Tooltip("Sotto questa integrità la parte è considerata rotta")]
        public float brokenThreshold = 20f;

        [Header("Diagnosi / Officina")]
        public string displayName;
        public float repairCostMultiplier = 1f;

        public VehicleComponent() { }

        public VehicleComponent(VehiclePartType t, string partName,
            Vector3 localPos, float integrity = 100f)
        {
            this.type = t;
            this.partName = partName;
            this.localPos = localPos;
            this.integrity = integrity;
            this.displayName = partName;
        }

        public bool IsBroken { get { return integrity <= brokenThreshold; } }
        public bool IsDegraded { get { return integrity <= degradedThreshold && integrity > brokenThreshold; } }
        public bool IsPerfect { get { return integrity >= 99f; } }
        public float IntegrityFactor { get { return Mathf.Clamp01(integrity / 100f); } }

        public void Damage(float amount)
        {
            integrity = Mathf.Clamp(integrity - amount, 0f, 100f);
        }

        public void Repair(float amount)
        {
            integrity = Mathf.Clamp(integrity + amount, 0f, 100f);
        }

        public void RepairFull()
        {
            integrity = 100f;
        }

        public float GetRepairCost(float baseCostPerPoint)
        {
            float missing = 100f - integrity;
            return missing * baseCostPerPoint * repairCostMultiplier;
        }
    }
}
