using System.Collections.Generic;
using UnityEngine;

namespace City.Vehicle.Traffic
{
    public enum LightState { Green, Yellow, Red }

    /// <summary>
    /// Registro centrale degli incroci regolati per il traffico locale.
    ///  - Semafori: ciclo temporale deterministico per asse (NS/EW) con fase
    ///    dipendente dall'id del nodo: tutti i veicoli vedono lo stesso stato
    ///    nello stesso istante senza bisogno di coordinarsi.
    ///  - Precedenza: agli incroci NON regolati vale "primo arrivato primo
    ///    servito" con obbligo di cedere il passo a chi arriva da destra;
    ///    un antistallo libera chi attende da troppo tempo.
    /// Le auto chiedono il permesso avvicinandosi al gate (RequestGo) e lo
    /// rilasciano uscendo dall'incrocio (Release). I claim stantii vengono
    /// potati automaticamente, quindi un'auto despawnata non blocca nulla.
    /// </summary>
    public static class JunctionControl
    {
        public const float ApproachDist = 24f;    // distanza di richiesta permesso
        public const float JunctionRadius = 9f;   // raggio della zona incrocio
        public const float StopLineOffset = 3.2f; // linea di arresto prima del centro

        private const float GreenTime = 9f;
        private const float YellowTime = 3f;
        private const float AllRedTime = 1.2f;
        private const float CycleTime = (GreenTime + YellowTime + AllRedTime) * 2f;
        private const float WaitBreakSeconds = 5f;

        public sealed class ControlledJunction
        {
            public int nodeId;
            public Vector3 pos;
            public bool hasLight;
            public float cycleOffset;
        }

        private sealed class Claim
        {
            public CarAgent car;
            public int axis;          // 0 = NS, 1 = EW
            public float since;       // istante di arrivo alla richiesta
            public bool inside;       // permesso concesso / in attraversamento
        }

        private static readonly Dictionary<int, ControlledJunction> junctions =
            new Dictionary<int, ControlledJunction>();
        private static readonly Dictionary<int, List<Claim>> claims =
            new Dictionary<int, List<Claim>>();

        public static void Clear()
        {
            junctions.Clear();
            claims.Clear();
        }

        public static void RegisterJunction(int nodeId, Vector3 pos, bool hasLight)
        {
            if (junctions.ContainsKey(nodeId))
            {
                junctions[nodeId].hasLight |= hasLight;
                return;
            }
            uint h = (uint)nodeId * 2654435761u;
            junctions[nodeId] = new ControlledJunction
            {
                nodeId = nodeId,
                pos = pos,
                hasLight = hasLight,
                cycleOffset = (h >> 8) % (uint)Mathf.Max(1f, CycleTime),
            };
        }

        public static IEnumerable<ControlledJunction> AllJunctions()
        {
            return junctions.Values;
        }

        public static bool HasLight(int nodeId)
        {
            return junctions.TryGetValue(nodeId, out var j) && j.hasLight;
        }

        // ── stati semaforici ────────────────────────────────────────

        public static int AxisOf(Vector3 approachDir)
        {
            return Mathf.Abs(approachDir.x) >= Mathf.Abs(approachDir.z) ? 1 : 0;
        }

        public static LightState StateFor(int nodeId, int axis, float time)
        {
            if (!junctions.TryGetValue(nodeId, out var j)) return LightState.Green;
            float t = Mathf.Repeat(time + j.cycleOffset, CycleTime);
            float half = GreenTime + YellowTime + AllRedTime;

            // prima meta' ciclo: asse 0 in verde; seconda meta': asse 1
            float local = axis == 0 ? t : Mathf.Repeat(t - half, CycleTime);
            if (local < GreenTime) return LightState.Green;
            if (local < GreenTime + YellowTime) return LightState.Yellow;
            return LightState.Red;
        }

        public static string StateName(LightState s)
        {
            switch (s)
            {
                case LightState.Green: return "green";
                case LightState.Yellow: return "yellow";
                default: return "red";
            }
        }

        // ── richiesta di passaggio ──────────────────────────────────

        /// <summary>
        /// Chiamato ogni frame dalle auto vicine al gate dell'incrocio.
        /// Ritorna true se l'auto puo' procedere (o proseguire).
        /// </summary>
        public static bool RequestGo(int nodeId, CarAgent car, Vector3 approachDir)
        {
            if (!junctions.TryGetValue(nodeId, out var j)) return true;

            float now = Time.time;
            if (!claims.TryGetValue(nodeId, out var list))
            {
                list = new List<Claim>();
                claims[nodeId] = list;
            }
            PruneStale(list, j.pos, now);

            Claim mine = null;
            for (int i = 0; i < list.Count; i++)
                if (ReferenceEquals(list[i].car, car)) { mine = list[i]; break; }
            if (mine == null)
            {
                mine = new Claim
                {
                    car = car,
                    axis = AxisOf(approachDir),
                    since = now,
                    inside = false,
                };
                list.Add(mine);
            }
            mine.axis = AxisOf(approachDir);
            if (mine.inside) return true;

            if (j.hasLight)
            {
                var st = StateFor(nodeId, mine.axis, now);
                if (st == LightState.Red) return false;
                if (st == LightState.Yellow &&
                    Vector3.Distance(car.GetPosition(), j.pos) > JunctionRadius * 0.6f)
                    return false; // giallo: si passa solo se gia' ingaggiati
                mine.inside = true;
                return true;
            }

            // ── incrocio non regolato: primo arrivato + destra privilegiata ──
            bool occupiedConflicting = false;
            for (int i = 0; i < list.Count; i++)
            {
                var c = list[i];
                if (ReferenceEquals(c, mine) || !c.inside) continue;
                if (c.axis != mine.axis) { occupiedConflicting = true; break; }
            }

            Claim rightWaiting = null;
            if (!occupiedConflicting)
            {
                Vector3 right = new Vector3(approachDir.z, 0f, -approachDir.x);
                for (int i = 0; i < list.Count; i++)
                {
                    var c = list[i];
                    if (ReferenceEquals(c, mine) || c.inside) continue;
                    Vector3 toThem = c.car.GetPosition() - car.GetPosition();
                    if (Vector3.Dot(toThem, right) > 1.5f && c.since <= mine.since)
                    {
                        rightWaiting = c;
                        break;
                    }
                }
            }

            if (!occupiedConflicting &&
                (rightWaiting == null || now - mine.since > WaitBreakSeconds))
            {
                mine.inside = true;
                return true;
            }
            return false;
        }

        /// <summary>Rilascio esplicito quando l'auto ha lasciato l'incrocio.</summary>
        public static void Release(int nodeId, CarAgent car)
        {
            if (!claims.TryGetValue(nodeId, out var list)) return;
            list.RemoveAll(c => ReferenceEquals(c.car, car));
        }

        private static void PruneStale(List<Claim> list, Vector3 center, float now)
        {
            float waitRange = ApproachDist * 1.5f;
            float insideRange = JunctionRadius + 8f;
            for (int i = list.Count - 1; i >= 0; i--)
            {
                var c = list[i];
                if (c.car == null || !c.car.gameObject.activeInHierarchy)
                {
                    list.RemoveAt(i);
                    continue;
                }
                float d = Vector3.Distance(c.car.GetPosition(), center);
                float range = c.inside ? insideRange : waitRange;
                if (d > range) list.RemoveAt(i);
            }
        }
    }
}
