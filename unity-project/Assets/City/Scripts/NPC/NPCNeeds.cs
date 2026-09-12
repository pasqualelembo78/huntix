using UnityEngine;

namespace City.NPC
{
    /// <summary>
    /// Sistema bisogni NPC stile Sims/Brookhaven (pattern LifeVerse +
    /// Unity-ECS-Starter). Ogni pedone ha fame, sete, energia, socialita':
    /// i bisogni calano col tempo, quando sono bassi il NPC cambia
    /// comportamento (va a mangiare, beve, si siede, parla con altri).
    /// </summary>
    public class NPCNeeds
    {
        // bisogni 0-100, calano col tempo
        public float Hunger { get; private set; } = 100f;
        public float Thirst { get; private set; } = 100f;
        public float Energy { get; private set; } = 100f;
        public float Social { get; private set; } = 100f;

        // tasso di decadimento al secondo (piu' alto = piu' veloce)
        private const float HungerDecay = 0.08f;   // ~20 min a vuoto
        private const float ThirstDecay = 0.12f;   // ~14 min a vuoto
        private const float EnergyDecay = 0.05f;   // ~33 min a vuoto
        private const float SocialDecay = 0.03f;   // ~55 min a vuoto

        // soglie (LifeVerse pattern: IsLow, IsCritical)
        public bool HungerLow => Hunger <= 30f;
        public bool ThirstLow => Thirst <= 30f;
        public bool EnergyLow => Energy <= 30f;
        public bool SocialLow => Social <= 25f;

        public bool HungerCritical => Hunger <= 15f;
        public bool ThirstCritical => Thirst <= 15f;
        public bool EnergyCritical => Energy <= 15f;

        // bisogno piu' urgente (per decisioni prioritarie)
        public enum Urgency { None, Social, Energy, Thirst, Hunger }
        public Urgency MostUrgent
        {
            get
            {
                if (HungerCritical) return Urgency.Hunger;
                if (ThirstCritical) return Urgency.Thirst;
                if (EnergyCritical) return Urgency.Energy;
                if (SocialLow) return Urgency.Social;
                return Urgency.None;
            }
        }

        /// <summary>Tick principale: cala tutti i bisogni.</summary>
        public void Tick(float dt)
        {
            float nightMul = 1f; // di notte cala un po' di piu'
            var dnm = City.Environment.DayNightManager.Instance;
            if (dnm != null)
            {
                float h = dnm.ClockHours;
                if (h < 7f || h >= 22f) nightMul = 1.3f;
            }

            Hunger = Mathf.Max(0f, Hunger - HungerDecay * dt * nightMul);
            Thirst = Mathf.Max(0f, Thirst - ThirstDecay * dt * nightMul);
            Energy = Mathf.Max(0f, Energy - EnergyDecay * dt);
            Social = Mathf.Max(0f, Social - SocialDecay * dt);
        }

        /// <summary>Soddisfa un bisogno.</summary>
        public void Feed(float amount) { Hunger = Mathf.Min(100f, Hunger + amount); }
        public void Drink(float amount) { Thirst = Mathf.Min(100f, Thirst + amount); }
        public void Rest(float amount) { Energy = Mathf.Min(100f, Energy + amount); }
        public void Talk(float amount) { Social = Mathf.Min(100f, Social + amount); }

        /// <summary>Resetta tutti i bisogni al massimo.</summary>
        public void ResetAll()
        {
            Hunger = 100f; Thirst = 100f; Energy = 100f; Social = 100f;
        }

        /// <summary>Ricovera energia quando dorme (tick accelerato).</summary>
        public void SleepTick(float dt)
        {
            Energy = Mathf.Min(100f, Energy + 8f * dt); // ~12 sec a pieno
            Hunger = Mathf.Max(0f, Hunger - HungerDecay * dt * 0.5f);
            Thirst = Mathf.Max(0f, Thirst - ThirstDecay * dt * 0.5f);
        }

        /// <summary>Restituisce una stringa descrittiva dello stato.</summary>
        public string StatusLabel()
        {
            if (HungerCritical) return "Ho fame...";
            if (ThirstCritical) return "Ho sete...";
            if (EnergyCritical) return "Sono stanco...";
            if (SocialLow) return "Mi manca la compagnia...";
            return "";
        }
    }
}
