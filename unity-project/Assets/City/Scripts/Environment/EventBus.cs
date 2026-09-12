using System;
using System.Collections.Generic;

namespace City.Environment
{
    /// <summary>
    /// EventBus leggero per eventi decouplati (pattern LifeVerse).
    /// Qualsiasi sistema puo' pubblicare/iscriversi senza dipendenze dirette.
    /// Thread-safe: le iscrizioni avvengono solo dal main thread.
    /// </summary>
    public static class EventBus
    {
        private static readonly Dictionary<Type, List<Delegate>> _handlers =
            new Dictionary<Type, List<Delegate>>();

        /// <summary>Iscrivi un handler per un evento.</summary>
        public static void Subscribe<T>(Action<T> handler) where T : struct
        {
            var type = typeof(T);
            if (!_handlers.TryGetValue(type, out var list))
            {
                list = new List<Delegate>();
                _handlers[type] = list;
            }
            if (!list.Contains(handler))
                list.Add(handler);
        }

        /// <summary>Disiscrivi un handler.</summary>
        public static void Unsubscribe<T>(Action<T> handler) where T : struct
        {
            var type = typeof(T);
            if (_handlers.TryGetValue(type, out var list))
                list.Remove(handler);
        }

        /// <summary>Pubblica un evento a tutti gli iscritti.</summary>
        public static void Publish<T>(T evt) where T : struct
        {
            var type = typeof(T);
            if (!_handlers.TryGetValue(type, out var list)) return;
            // copia per evitare eccezioni se un handler si disiscrive durante l'invocazione
            for (int i = list.Count - 1; i >= 0; i--)
            {
                if (list[i] is Action<T> action)
                    action.Invoke(evt);
            }
        }

        /// <summary>Rimuovi tutti gli handler (chiamare al logout/destroy).</summary>
        public static void Clear()
        {
            _handlers.Clear();
        }
    }

    // ── Eventi di sistema ──

    /// <summary>Ora del giorno cambiata (ogni minuto game o al cambio di ora).</summary>
    public struct HourChangedEvent
    {
        public readonly int Hour;
        public HourChangedEvent(int hour) { Hour = hour; }
    }

    /// <summary>Nuovo giorno.</summary>
    public struct DayChangedEvent
    {
        public readonly int DayNumber;
        public DayChangedEvent(int day) { DayNumber = day; }
    }

    /// <summary>Fase giornaliera NPC cambiata.</summary>
    public struct NpcPhaseChangedEvent
    {
        public readonly string NpcName;
        public readonly string OldPhase;
        public readonly string NewPhase;
        public NpcPhaseChangedEvent(string name, string oldP, string newP)
        { NpcName = name; OldPhase = oldP; NewPhase = newP; }
    }

    /// <summary>Player è entrato/uscito da un veicolo.</summary>
    public struct VehicleEnteredEvent
    {
        public readonly bool Entered;
        public VehicleEnteredEvent(bool entered) { Entered = entered; }
    }

    /// <summary>Carburante veicolo sotto soglia.</summary>
    public struct FuelLowEvent
    {
        public readonly float Percent;
        public FuelLowEvent(float pct) { Percent = pct; }
    }
}
