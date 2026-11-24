using UnityEngine;

namespace City.Diagnostics
{
    /// <summary>
    /// Profiler interno che gira dentro la build e scrive nel viewer dei log
    /// Android (tramite SessionTelemetry). Non sostituisce Unity Profiler,
    /// ma rende visibili sul device le voci che interessano la giocabilita':
    /// tempo di movimento, camera, IK, pompa dei chunk.
    ///
    /// Ogni misura e' una media e un picco sull'intervallo del beacon (2 s).
    /// Lo scope e' uno struct: nessuna allocazione per frame, nessun marker
    /// Unity Profiler (che nella build non sarebbe leggibile dal viewer).
    /// </summary>
    public static class PerfProbe
    {
        private const int SlotCount = 21;
        // "spawn" = istanziazione NPC/veicoli, "roads" = meshing asfalto e
        // marciapiedi. Aggiunti dopo il log 23/11: move/camera/ik/chunk/input
        // sommavano 6-13 ms mentre il frame era a 266-300 ms, quindi il tempo
        // mancante era fuori dagli scope misurati. "traffic" misura la sonda
        // ostacoli già esistente, senza cambiarne il comportamento. "mount"
        // copre il positioning del guidatore dentro/sopra il veicolo (24/11).
        // "menu" e "console" misurano gli innesti MIT dell'audit (24/11): setup
        // dell'animazione d'ingresso e costruzione dei comandi della console QA.
        // "companion" copre l'aggiornamento del bersaglio seguito dal pedone.
        // "diary" copre il ripristino del contatore del registro attività.
        // "bait" copre il ripristino dell'esca attiva e il consumo indoor
        // (Fase 2 · consumabili): nessuna misura per frame, solo gli scambi.
        // "harvest" copre l'invio della raccolta risorse e il suo ripristino
        // passivo (Fase 2 · voce 14): solo eventi espliciti del giocatore.
        // "craft" copre la conferma della fusione uova (Fase 2 · voce 15):
        // nessun lavoro per frame, solo lo scambio con Android.
        // "inventory" copre lo specchio della collezione (Fase 2 · voce 11).
        // "recipe" copre la scoperta delle ricette di fusione (voce 16).
        // "egg-hint" copre la voce unica del diario al primo indizio uovo
        // (adattamento EventBus.SubscribeOnce da rob1997/Controller, MIT).
        private static readonly string[] Names =
            { "move", "camera", "ik", "chunk", "anim", "input", "spawn", "roads", "traffic", "fictional", "mount", "menu", "console", "companion", "diary", "bait", "harvest", "craft", "inventory", "recipe", "egg-hint" };
        private static readonly float[] Sum = new float[SlotCount];
        private static readonly float[] Peak = new float[SlotCount];
        private static readonly int[] Samples = new int[SlotCount];
        private static float _lastInputAt = -1f;

        /// <summary>Segna l'istante in cui l'utente ha cominciato a muovere lo
        /// stick. Chiamato da PlayerController.SetMoveInput.</summary>
        public static void MarkInput()
        {
            _lastInputAt = Time.realtimeSinceStartup;
        }

        /// <summary>Chiude la misura al primo frame in cui il movimento viene
        /// davvero consumato: e' la latenza percepita input -> risposta.</summary>
        public static void InputConsumed()
        {
            if (_lastInputAt < 0f) return;
            Add("input", (Time.realtimeSinceStartup - _lastInputAt) * 1000f);
            _lastInputAt = -1f;
        }

        private static int IndexOf(string name)
        {
            for (int i = 0; i < Names.Length; i++)
                if (Names[i] == name) return i;
            return -1;
        }

        public static void Add(string name, float ms)
        {
            int i = IndexOf(name);
            if (i < 0 || ms < 0f || ms > 2000f) return;
            Sum[i] += ms;
            Samples[i]++;
            if (ms > Peak[i]) Peak[i] = ms;
        }

        /// <summary>Misura uno scope con using; chiude anche in caso di return.</summary>
        public struct Scope : System.IDisposable
        {
            private readonly string _name;
            private readonly float _t0;

            public Scope(string name)
            {
                _name = name;
                _t0 = Time.realtimeSinceStartup;
            }

            public void Dispose()
            {
                Add(_name, (Time.realtimeSinceStartup - _t0) * 1000f);
            }
        }

        public static Scope Measure(string name)
        {
            return new Scope(name);
        }

        /// <summary>Riepilogo medio/picco e azzera i contatori.</summary>
        public static string SnapshotAndReset()
        {
            var sb = new System.Text.StringBuilder(160);
            sb.Append("prof=");
            bool first = true;
            for (int i = 0; i < SlotCount; i++)
            {
                if (Samples[i] == 0) continue;
                if (!first) sb.Append(' ');
                first = false;
                sb.Append(Names[i]).Append(':')
                  .Append((Sum[i] / Samples[i]).ToString("F2"))
                  .Append('/').Append(Peak[i].ToString("F1"));
                Sum[i] = 0f;
                Peak[i] = 0f;
                Samples[i] = 0;
            }
            if (first) sb.Append("-");
            return sb.ToString();
        }
    }
}
