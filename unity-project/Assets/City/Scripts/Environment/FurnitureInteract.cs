using System;
using UnityEngine;
using City.Player;
using City.Interior;

namespace City.Environment
{
    /// <summary>
    /// Rileva gli arredi interni interattivi (letti, divani, poltrone,
    /// sedie, panche) quando si e dentro un edificio e gestisce il sonno:
    /// sdraiato sul letto energia e sonno tornano al massimo in pochi
    /// secondi reali; un tap sveglia. La seduta su divano/poltrona/sedia
    /// riusa SitController.
    /// </summary>
    public class FurnitureInteract : MonoBehaviour
    {
        private static FurnitureInteract _instance;

        public static FurnitureInteract Instance { get { return _instance; } }

        public static bool IsSleeping
        {
            get { return _instance != null && _instance._sleeping; }
        }

        public static Transform NearBedTransform
        {
            get { return _instance != null ? _instance._nearBed : null; }
        }

        public static Transform NearSitTransform
        {
            get { return _instance != null ? _instance._nearSit : null; }
        }

        public static void Ensure()
        {
            if (_instance != null) return;
            var go = new GameObject("FurnitureInteract");
            DontDestroyOnLoad(go);
            _instance = go.AddComponent<FurnitureInteract>();
        }

        private const float BedRadius = 2.6f;
        private const float SitRadius = 2.2f;
        private const float ScanEvery = 0.25f;

        private float _nextScan;
        private Transform _nearBed;
        private Transform _nearSit;

        // stato del sonno
        private bool _sleeping;
        private Transform _player;
        private MonoBehaviour _disabledCtrl;
        private Transform _bed;
        private Vector3 _prevPos;
        private Quaternion _prevRot;
        private float _restAccum;
        private float _lastUi;

        private void Update()
        {
            if (_sleeping)
            {
                UpdateSleep();
                return;
            }
            if (Time.unscaledTime < _nextScan) return;
            _nextScan = Time.unscaledTime + ScanEvery;
            Scan();
        }

        private void Scan()
        {
            _nearBed = null;
            _nearSit = null;
            Game g = Game.Instance;
            if (g == null || !g.IsInInterior) return;
            InteriorManager mgr = InteriorManager.Instance;
            if (mgr == null) return;
            Transform root = mgr.ActiveInteriorRoot;
            if (root == null) return;
            Vector3 p = g.player != null ? g.player.transform.position : root.position;

            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            float db = BedRadius;
            float ds = SitRadius;
            for (int i = 0; i < all.Length; i++)
            {
                Transform t = all[i];
                if (t == null) continue;
                string n = t.name.ToLowerInvariant();
                if (n.IndexOf("(clone)") >= 0) n = n.Replace("(clone)", "");
                float d = Vector3.Distance(p, t.position);
                bool isBed = n.Contains("bed") || n.Contains("letto");
                bool isSit = n.Contains("sofa") || n.Contains("chair") ||
                             n.Contains("divano") || n.Contains("poltrona") ||
                             n.Contains("sedia") || n.Contains("bench") ||
                             n.Contains("panca");
                if (isBed && d < db)
                {
                    db = d;
                    _nearBed = t;
                }
                else if (isSit && d < ds)
                {
                    ds = d;
                    _nearSit = t;
                }
            }
        }

        // ---- seduta su divano/poltrona/sedia/panchina interna ----

        public static void SitOnNearest()
        {
            Ensure();
            Transform t = _instance != null ? _instance._nearSit : null;
            if (t == null) return;
            Game g = Game.Instance;
            PlayerController pc = g != null ? g.player : null;
            if (pc == null) return;
            Vector3 seat = t.position + Vector3.up * 0.55f;
            SitController.Sit(pc.transform, seat, t.rotation);
        }

        // ---- sonno ----

        public static void StartSleep()
        {
            Ensure();
            if (_instance == null) return;
            _instance.BeginSleep();
        }

        public static void WakeUp()
        {
            if (_instance != null) _instance.EndSleep();
        }

        private void BeginSleep()
        {
            if (_sleeping) EndSleep();
            if (_nearBed == null) return;
            Game g = Game.Instance;
            PlayerController pc = g != null ? g.player : null;
            if (pc == null) return;

            if (SitController.IsSitting) SitController.StandUp();

            if (pc.enabled)
            {
                pc.enabled = false;
                _disabledCtrl = pc;
            }

            _prevPos = pc.transform.position;
            _prevRot = pc.transform.rotation;
            pc.transform.position = _nearBed.position + Vector3.up * 0.5f;
            pc.transform.rotation = Quaternion.LookRotation(_nearBed.forward, _nearBed.up);

            _bed = _nearBed;
            _player = pc.transform;
            _sleeping = true;
            _restAccum = 0f;
            _lastUi = 0f;
            SetOverlay(true, "Dormi... tocchi lo schermo per svegliarti");
        }

        private void EndSleep()
        {
            if (!_sleeping) return;
            _sleeping = false;

            if (_disabledCtrl != null)
            {
                _disabledCtrl.enabled = true;
                _disabledCtrl = null;
            }
            if (_player != null)
            {
                _player.position = _prevPos + Vector3.up * 0.1f;
                _player.rotation = _prevRot;
            }
            _player = null;
            _bed = null;
            SetOverlay(false, "");

            Game g = Game.Instance;
            if (g != null && g.ui != null)
            {
                g.ui.ShowToast("Ti sei svegliato: energia e sonno al massimo!");
            }
        }

        private void UpdateSleep()
        {
            if (_player == null || _bed == null)
            {
                EndSleep();
                return;
            }
            // ricarica completa in circa 12 secondi reali
            float dt = Time.unscaledDeltaTime;
            if (dt > 0.5f) dt = 0.5f;
            _restAccum += dt / 12f * 100f;
            if (_restAccum >= 1f)
            {
                int n = (int)_restAccum;
                _restAccum -= n;
                int add = Mathf.Max(1, n);
                try { EnergySystem.Restore(add); }
                catch (Exception) { }
                try { SleepSystem.Restore(add); }
                catch (Exception) { }
            }
            if (Time.unscaledTime - _lastUi > 0.4f)
            {
                _lastUi = Time.unscaledTime;
                SetOverlay(true, "Dormi... (" +
                    Mathf.Min(100, EnergySystem.Value) + "% energia)");
            }
            if (EnergySystem.Value >= EnergySystem.MaxValue &&
                SleepSystem.Value >= SleepSystem.MaxSleep)
            {
                EndSleep();
            }
        }

        private void SetOverlay(bool on, string msg)
        {
            Game g = Game.Instance;
            if (g != null && g.ui != null) g.ui.SetSleepOverlay(on, msg);
        }
    }
}
