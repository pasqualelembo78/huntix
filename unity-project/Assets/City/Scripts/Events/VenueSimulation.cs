using System.Collections.Generic;
using UnityEngine;
using City.Environment;
using City.UI;
using Huntix.Bridge;

namespace City.Events
{
    /// <summary>
    /// Simulazione leggera di una partita o di una corsa. Nessun CharacterController,
    /// nessuna fisica e nessun cambio scena: gli attori seguono traiettorie
    /// deterministiche e vengono aggiornati solo quando il player è vicino.
    /// </summary>
    public class VenueSimulation : MonoBehaviour
    {
        private class Actor
        {
            public Transform Tr;
            public Animator Anim;
            public Animation Legacy;
            public string Clip = "";
            public float Phase;
            public float Speed;
            public string Label;
            public float Progress;
        }

        public VenueKind Kind;
        public string SiteId;
        public Vector3 Center;
        public Transform Ball;
        public VenueAudio Audio;
        public float SpeedFactor = 1f;

        private readonly List<Actor> _actors = new List<Actor>();
        private VenuePhase _phase = VenuePhase.Inattivo;
        private float _timer;
        private float _clock;
        private float _weatherTimer;
        private int _frame;
        private int _goalsHome;
        private int _goalsAway;
        private int _half;

        public VenuePhase Phase { get { return _phase; } }

        public void Configure(VenueKind kind, Vector3 center, string siteId)
        {
            Kind = kind;
            Center = center;
            SiteId = siteId;
            _timer = 5f;
            _weatherTimer = 20f;
        }

        public void AddActor(GameObject go, bool horse, int index, int count)
        {
            if (go == null) return;
            var a = new Actor
            {
                Tr = go.transform,
                Anim = go.GetComponentInChildren<Animator>(true),
                // I cavalli usano le clip legacy del FBX; i giocatori usano
                // l'Animator del PlayerHero (controller Mixamo già nel progetto).
                Legacy = go.GetComponent<Animation>(),
                Phase = (Mathf.PI * 2f * index) / Mathf.Max(1, count),
                Speed = horse ? 0.075f + 0.006f * index : 0.11f,
                Label = horse ? ("Cavallo " + (index + 1)) : ("Giocatore " + (index + 1))
            };
            _actors.Add(a);
        }

        public void Begin()
        {
            ChangePhase(VenuePhase.Programmato, Kind == VenueKind.Stadio
                ? "Partita in programma" : "Corsa in programma", true);
        }

        /// <summary>Avvia o ferma l'evento dal pulsante contestuale.</summary>
        public void Toggle()
        {
            if (_phase == VenuePhase.InCorso || _phase == VenuePhase.Intervallo)
            {
                ChangePhase(VenuePhase.Inattivo, "Evento fermato", false);
                if (Audio != null) Audio.SetCrowd(false);
            }
            else Begin();
        }

        private void Update()
        {
            var game = Game.Instance;
            if (game == null || game.player == null || _phase == VenuePhase.Inattivo) return;
            float dist = Vector3.Distance(game.player.transform.position, Center);
            // Culling: oltre 210 m nessun aggiornamento, nessuna allocazione.
            if (dist > 210f) return;

            float dt = Mathf.Min(Time.deltaTime, 0.05f);
            _clock += dt;
            _frame++;
            _timer -= dt * SpeedFactor;
            _weatherTimer -= dt;
            if (_weatherTimer <= 0f) ApplyWeather(dist);

            if (_timer <= 0f) Advance(dist);
            if (dist < 130f && (_frame % 2 == 0 || dist < 40f)) MoveActors(dt, dist);
            if (Audio != null)
            {
                Audio.SetCrowd(dist < 95f && _phase == VenuePhase.InCorso);
                Audio.TickHooves(dt, _phase == VenuePhase.InCorso && Kind == VenueKind.Ippodromo, dist);
            }
        }

        private void ApplyWeather(float dist)
        {
            _weatherTimer = 24f + (SiteId.Length % 7) * 2f;
            string[] meteo = { "sereno", "pioggia leggera", "vento" };
            int pick = (Mathf.FloorToInt(_clock) + SiteId.Length) % meteo.Length;
            SpeedFactor = pick == 1 ? 0.82f : (pick == 2 ? 1.08f : 1f);
            if (dist > 120f) return;
            EventBus.Publish(new EnvironmentalEventAppliedEvent(meteo[pick], SiteId));
            try
            {
                UnityBridge.SendMessageToAndroid("EnvironmentalEventApplied",
                    "{\"name\":\"" + meteo[pick] + "\",\"place\":\"" + SiteId +
                    "\",\"speed\":" + SpeedFactor.ToString("F2", System.Globalization.CultureInfo.InvariantCulture) + "}");
            }
            catch (System.Exception) { }
        }

        private void Advance(float dist)
        {
            if (Kind == VenueKind.Stadio)
            {
                switch (_phase)
                {
                    case VenuePhase.Programmato:
                        _goalsHome = 0; _goalsAway = 0; _half = 1;
                        ChangePhase(VenuePhase.InCorso, "Partita 0-0 — primo tempo", dist < 90f);
                        _timer = 45f;
                        break;
                    case VenuePhase.InCorso:
                        // Un gol ogni tanto, deterministico e senza premio.
                        if ((Mathf.FloorToInt(_clock) / 17) % 3 == 0) _goalsHome++;
                        else _goalsAway++;
                        if (_half == 1)
                        {
                            ChangePhase(VenuePhase.Intervallo,
                                "Intervallo " + _goalsHome + "-" + _goalsAway, dist < 90f);
                            _timer = 8f;
                        }
                        else
                        {
                            ChangePhase(VenuePhase.Terminato,
                                "Terminata " + _goalsHome + "-" + _goalsAway, dist < 90f);
                            _timer = 12f;
                        }
                        break;
                    case VenuePhase.Intervallo:
                        _half = 2;
                        ChangePhase(VenuePhase.InCorso,
                            "Secondo tempo " + _goalsHome + "-" + _goalsAway, dist < 90f);
                        _timer = 35f;
                        break;
                    case VenuePhase.Terminato:
                        ChangePhase(VenuePhase.Programmato, "Nuova partita in programma", dist < 90f);
                        _timer = 8f;
                        break;
                }
            }
            else
            {
                if (_phase == VenuePhase.Programmato)
                {
                    for (int i = 0; i < _actors.Count; i++) _actors[i].Progress = 0f;
                    ChangePhase(VenuePhase.InCorso, "Corsa partita", dist < 90f);
                    _timer = 38f;
                }
                else if (_phase == VenuePhase.InCorso)
                {
                    var order = new List<Actor>(_actors);
                    order.Sort((a, b) => b.Progress.CompareTo(a.Progress));
                    string rank = "";
                    for (int i = 0; i < Mathf.Min(3, order.Count); i++)
                        rank += (i + 1) + ". " + order[i].Label + " ";
                    ChangePhase(VenuePhase.Terminato, "Classifica: " + rank.Trim(), dist < 90f);
                    _timer = 15f;
                }
                else if (_phase == VenuePhase.Terminato)
                {
                    ChangePhase(VenuePhase.Programmato, "Nuova corsa in programma", dist < 90f);
                    _timer = 8f;
                }
            }
        }

        private void ChangePhase(VenuePhase p, string detail, bool toast)
        {
            _phase = p;
            VenueEventState.Set(Kind, p, detail, Center);
            if (!toast) return;
            try
            {
                if (UIManager.Instance != null) UIManager.Instance.ShowToast(detail);
                if (Audio != null && (p == VenuePhase.InCorso || p == VenuePhase.Terminato))
                    Audio.Whistle();
            }
            catch (System.Exception) { }
        }

        private void MoveActors(float dt, float dist)
        {
            bool race = Kind == VenueKind.Ippodromo;
            float rx = race ? 62f : 24f;
            float rz = race ? 31f : 16f;
            for (int i = 0; i < _actors.Count; i++)
            {
                Actor a = _actors[i];
                if (a.Tr == null) continue;
                if (race)
                {
                    if (_phase == VenuePhase.InCorso)
                        a.Progress += a.Speed * dt * SpeedFactor;
                    float ang = a.Phase + a.Progress;
                    Vector3 p = Center + new Vector3(Mathf.Cos(ang) * rx, 0.08f, Mathf.Sin(ang) * rz);
                    Vector3 dir = new Vector3(-Mathf.Sin(ang) * rx, 0f, Mathf.Cos(ang) * rz).normalized;
                    a.Tr.position = p;
                    if (dir.sqrMagnitude > 0.01f) a.Tr.rotation = Quaternion.LookRotation(dir);
                }
                else
                {
                    float ang = a.Phase + _clock * a.Speed * SpeedFactor;
                    Vector3 p = Center + new Vector3(Mathf.Cos(ang) * rx,
                        0.05f + Mathf.Abs(Mathf.Sin(_clock * 6f + a.Phase)) * 0.06f,
                        Mathf.Sin(ang) * rz);
                    Vector3 dir = new Vector3(-Mathf.Sin(ang) * rx, 0f, Mathf.Cos(ang) * rz).normalized;
                    a.Tr.position = p;
                    if (dir.sqrMagnitude > 0.01f) a.Tr.rotation = Quaternion.LookRotation(dir);
                }
                if (dist < 70f)
                {
                    if (a.Anim != null)
                        a.Anim.SetFloat("Speed", _phase == VenuePhase.InCorso ? 1f : 0.25f);
                    // CrossFade solo al cambio clip: non riavvia l'animazione
                    // a ogni frame e non alloca nuovi stati.
                    if (a.Legacy != null)
                    {
                        string clip = race
                            ? (_phase == VenuePhase.InCorso ? "Gallop" : "Idle")
                            : (_phase == VenuePhase.InCorso ? "Run" : "Idle");
                        if (a.Clip != clip)
                        {
                            a.Legacy.CrossFade(clip, 0.2f);
                            a.Clip = clip;
                        }
                    }
                }
            }
            if (Ball != null && _actors.Count >= 2)
            {
                float t = 0.5f + 0.5f * Mathf.Sin(_clock * 0.7f);
                Vector3 a = _actors[0].Tr == null ? Center : _actors[0].Tr.position;
                Vector3 b = _actors[1].Tr == null ? Center : _actors[1].Tr.position;
                Ball.position = Vector3.Lerp(a, b, t) + Vector3.up * 0.12f;
            }
        }
    }
}
