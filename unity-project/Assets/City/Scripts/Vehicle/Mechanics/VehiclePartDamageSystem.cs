using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Mechanics
{
    /// <summary>
    /// Sistema di danni per singola parte meccanica. Gestisce l'insieme di
    /// VehicleComponent di un veicolo, applica i danni in base alla zona
    /// colpita (il colpo danneggia LA parte esatta in quel punto), e fornisce
    /// diagnostiche per l'officina (cosa è rotto, costo di riparazione).
    ///
    /// Ogni parte ha effetto reale sul comportamento di guida tramite i
    /// fattori esposti con GetEffectFactor(partType).
    /// </summary>
    [System.Serializable]
    public class VehiclePartDamageSystem
    {
        [SerializeField] private List<VehicleComponent> parts =
            new List<VehicleComponent>();

        // Indici per accesso rapido (per cardinalità delle parti multiple)
        private readonly Dictionary<VehiclePartType, List<VehicleComponent>>
            byType = new Dictionary<VehiclePartType, List<VehicleComponent>>();

        public List<VehicleComponent> Components { get { return parts; } }

        /// <summary>Crea lo schema standard per un'autovettura a 4 ruote.</summary>
        public void SetupStandardCar(string label)
        {
            parts.Clear();
            byType.Clear();
            float w = 0.9f, l = 1.8f;

            Add(new VehicleComponent(VehiclePartType.Engine,        "Motore",           new Vector3(0f, 0.6f, 0.4f)));
            Add(new VehicleComponent(VehiclePartType.Gearbox,       "Cambio",           new Vector3(0f, 0.6f, 0.1f)));
            Add(new VehicleComponent(VehiclePartType.Driveshaft,    "Giunto/Cardano",   new Vector3(0f, 0.45f, 0f)));
            Add(new VehicleComponent(VehiclePartType.Differential,  "Differenziale",    new Vector3(0f, 0.45f, -0.2f)));
            Add(new VehicleComponent(VehiclePartType.Suspension,    "Sospensioni",      new Vector3(0f, 0.6f, 0f)));
            Add(new VehicleComponent(VehiclePartType.ShockAbsorber, "Ammortizzatori",   new Vector3(0f, 0.35f, 0f)));
            Add(new VehicleComponent(VehiclePartType.Steering,      "Sterzo",           new Vector3(0f, 0.5f, 0.7f)));
            Add(new VehicleComponent(VehiclePartType.Battery,       "Batteria",         new Vector3(-w, 0.5f, 0.3f)));
            Add(new VehicleComponent(VehiclePartType.Electrical,    "Impianto elettrico", new Vector3(0f, 0.5f, 0.2f)));
            Add(new VehicleComponent(VehiclePartType.Fuel,          "Serbatoio",        new Vector3(w, 0.5f, -0.5f)));
            Add(new VehicleComponent(VehiclePartType.Exhaust,       "Scarico",          new Vector3(0f, 0.3f, -0.9f)));
            Add(new VehicleComponent(VehiclePartType.Radiator,      "Radiatore",        new Vector3(0f, 0.55f, 0.8f)));
            Add(new VehicleComponent(VehiclePartType.Oil,           "Olio motore",      new Vector3(0f, 0.55f, 0.5f)));
            Add(new VehicleComponent(VehiclePartType.Chassis,       "Telaio",           new Vector3(0f, 0.6f, 0f)));
            Add(new VehicleComponent(VehiclePartType.Bodywork,      "Carrozzeria",      new Vector3(0f, 0.9f, 0f)));
            Add(new VehicleComponent(VehiclePartType.Bumper,        "Paraurti anteriore", new Vector3(0f, 0.5f, l * 0.5f)));

            // Ruote: 4 gomme + 4 freni (perdona la nomenclatura: order matters)
            Add(new VehicleComponent(VehiclePartType.Tire,  "Gomma anteriore destra", new Vector3(w, 0.35f, 0.6f)));
            Add(new VehicleComponent(VehiclePartType.Tire,  "Gomma anteriore sinistra", new Vector3(-w, 0.35f, 0.6f)));
            Add(new VehicleComponent(VehiclePartType.Tire,  "Gomma posteriore destra", new Vector3(w, 0.35f, -0.6f)));
            Add(new VehicleComponent(VehiclePartType.Tire,  "Gomma posteriore sinistra", new Vector3(-w, 0.35f, -0.6f)));
            Add(new VehicleComponent(VehiclePartType.Brake, "Freno anteriore destro", new Vector3(w, 0.35f, 0.6f)));
            Add(new VehicleComponent(VehiclePartType.Brake, "Freno anteriore sinistro", new Vector3(-w, 0.35f, 0.6f)));
            Add(new VehicleComponent(VehiclePartType.Brake, "Freno posteriore destro", new Vector3(w, 0.35f, -0.6f)));
            Add(new VehicleComponent(VehiclePartType.Brake, "Freno posteriore sinistro", new Vector3(-w, 0.35f, -0.6f)));
        }

        private void Add(VehicleComponent c)
        {
            parts.Add(c);
            if (!byType.TryGetValue(c.type, out var list))
            {
                list = new List<VehicleComponent>();
                byType[c.type] = list;
            }
            list.Add(c);
        }

        /// <summary>
        /// Fattore di effetto (0..1) di una data parte sul comportamento di
        /// guida: 1 = perfetto, 0 = rotto. Fa la media se ci sono più parti
        /// dello stesso tipo (es. 2 freni anteriori).
        /// </summary>
        public float GetEffectFactor(VehiclePartType type)
        {
            if (!byType.TryGetValue(type, out var list) || list.Count == 0)
                return 1f;
            float sum = 0f;
            for (int i = 0; i < list.Count; i++) sum += list[i].IntegrityFactor;
            return sum / list.Count;
        }

        /// <summary>Tutte le parti di un tipo (es. per mostra ruote).</summary>
        public List<VehicleComponent> GetParts(VehiclePartType type)
        {
            if (byType.TryGetValue(type, out var list)) return list;
            return new List<VehicleComponent>();
        }

        /// <summary>
        /// Applica danno a tutte le parti entro una distanza dal punto
        /// d'impatto (in coordinate LOCALI al veicolo). Il danno è max nel
        /// punto e sfuma con la distanza: così un colpo al paraurti anteriore
        /// danneggia paraurti→radiatore→motore ma NON il serbatoio posteriore.
        /// </summary>
        public void DamageAtLocalPoint(Vector3 hitLocal, float impactForce,
            float radius = 1.2f, float maxDamage = 100f)
        {
            for (int i = 0; i < parts.Count; i++)
            {
                var p = parts[i];
                float dist = Vector3.Distance(p.localPos, hitLocal);
                if (dist > radius) continue;
                float falloff = 1f - Mathf.Clamp01(dist / radius);
                float dmg = maxDamage * falloff * Mathf.Clamp01(impactForce / 20f);
                if (dmg > 0f) p.Damage(dmg);
            }
        }

        /// <summary>Danneggia direttamente tutte le parti di un tipo.</summary>
        public void DamageType(VehiclePartType type, float amount)
        {
            if (!byType.TryGetValue(type, out var list)) return;
            for (int i = 0; i < list.Count; i++) list[i].Damage(amount);
        }

        /// <summary>
        /// La parte rotta più vicina al punto, o null se nessuna rotta.
        /// Usato per il messaggio d'impatto ("S'è rotto il motore!").
        /// </summary>
        public VehicleComponent GetBrokenPartNearest(Vector3 hitLocal)
        {
            VehicleComponent best = null;
            float bestDist = float.MaxValue;
            for (int i = 0; i < parts.Count; i++)
            {
                var p = parts[i];
                if (!p.IsBroken) continue;
                float d = Vector3.Distance(p.localPos, hitLocal);
                if (d < bestDist) { bestDist = d; best = p; }
            }
            return best;
        }

        /// <summary>Diagnosi completa: tutte le parti con integrità &lt; 100.</summary>
        public List<VehicleComponent> GetDamagedParts()
        {
            var result = new List<VehicleComponent>();
            for (int i = 0; i < parts.Count; i++)
                if (parts[i].integrity < 99f) result.Add(parts[i]);
            return result;
        }

        /// <summary>Costo totale di riparazione (somma di tutte le parti danneggiate).</summary>
        public float GetTotalRepairCost(float baseCostPerPoint = 0.4f)
        {
            float total = 0f;
            for (int i = 0; i < parts.Count; i++)
                total += parts[i].GetRepairCost(baseCostPerPoint);
            return total;
        }

        /// <summary>Ripara una singola parte di un dato tipo e indice.</summary>
        public void RepairPart(VehiclePartType type, int index, float amount)
        {
            if (!byType.TryGetValue(type, out var list)) return;
            if (index < 0 || index >= list.Count) return;
            list[index].Repair(amount);
        }

        /// <summary>Ripara tutto.</summary>
        public void RepairAll()
        {
            for (int i = 0; i < parts.Count; i++) parts[i].RepairFull();
        }

        /// <summary>Percentuale integrità media del veicolo (HP complessivo).</summary>
        public float AverageIntegrity
        {
            get
            {
                if (parts.Count == 0) return 100f;
                float sum = 0f;
                for (int i = 0; i < parts.Count; i++) sum += parts[i].integrity;
                return sum / parts.Count;
            }
        }

        public bool HasBrokenPart(VehiclePartType type)
        {
            if (!byType.TryGetValue(type, out var list)) return false;
            for (int i = 0; i < list.Count; i++)
                if (list[i].IsBroken) return true;
            return false;
        }
    }
}
