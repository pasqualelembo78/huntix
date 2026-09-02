using System;
using System.Collections.Generic;
using UnityEngine;
using City.Player;
using City.NPC;
using City.Vehicle;
using City.World;
using City.Economy;
using City.UI;

namespace City.UI
{
    /// <summary>
    /// Pulsante azione contestuale in basso al centro: scansiona piu volte al
    /// secondo cosa c e vicino al player e costruisce l elenco di azioni
    /// eseguibili. Il testo del pulsante centrale mostra l azione principale;
    /// toccandolo si apre il menu con tutte le azioni del contesto corrente:
    ///   - lontano da tutto : SALTO, FISCHIO, SPRINT
    ///   - vicino a un auto : SALI IN AUTO
    ///   - vicino a un pacco : RACCOGLI / LASCIA / BUTTA / SBIRCIA
    ///   - vicino a una porta : APRI PORTA / BUSSA
    ///   - vicino a una persona : PARLA / CALCIO / SPINTONA +
    ///       vita di famiglia (FIDANZATI / SPOSATI / DIVORZIO / FIGLI).
    /// </summary>
    public class ContextActionController : MonoBehaviour
    {
        public static ContextActionController Instance { get; private set; }

        private const float ProbeRadius = 3.5f;

        /// <summary>Assicura che il controller esista in scena.</summary>
        public static void Ensure()
        {
            if (Instance != null) return;
            var go = new GameObject("ContextActionController");
            go.AddComponent<ContextActionController>();
        }

        private const float ProbeEvery = 0.15f;

        private float nextProbe;
        private NPCController nearNPC;

        public class Action
        {
            public string label;
            public System.Action run;
        }

        private Action Make(string label, System.Action run)
        {
            return new Action { label = label, run = run };
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        /// <summary>Aggiorna lo stato interno ogni qualche frame.</summary>
        private void Update()
        {
            if (Time.unscaledTime < nextProbe) return;
            nextProbe = Time.unscaledTime + ProbeEvery;
            ProbeNearby();
        }

        /// <summary>Trova la persona (NPC) piu vicina nel raggio di azione.</summary>
        private void ProbeNearby()
        {
            nearNPC = null;
            PlayerController pc = PlayerController.Instance;
            if (pc == null) return;
            Vector3 pos = pc.transform.position;

            NPCController best = null;
            float bestD = float.MaxValue;
            var all = NPCController.Active;
            for (int i = 0; i < all.Count; i++)
            {
                var n = all[i];
                if (n == null) continue;
                float d = Vector3.Distance(pos, n.transform.position);
                if (d <= ProbeRadius && d < bestD)
                {
                    bestD = d;
                    best = n;
                }
            }
            nearNPC = best;
        }

        /// <summary>Il titolo breve per il pulsante centrale (azione primaria).</summary>
        public string PrimaryLabel()
        {
            Game g = Game.Instance;
            bool interior = g != null && g.IsInInterior;
            if (!interior && g != null && g.CurrentMissionNPC != null)
                return "PARLA";
            if (nearNPC != null)
            {
                string cid = nearNPC.CharacterId;
                if (FamilyManager.IsSpouse(cid)) return "CONIUGE";
                if (FamilyManager.CanMarry(cid)) return "SPOSATI";
                if (FamilyManager.CanEngage(cid)) return "FIDANZATI";
                return "PARLA";
            }
            if (g != null && g.CurrentVehicleFocus != null &&
                g.CurrentVehicleFocus.IsOwned())
                return "SALI IN AUTO";
            if (g != null && g.CurrentPoiZone != null)
                return "NEGOZIO";
            if (g != null && g.CurrentDoor != null)
                return "APRI PORTA";
            if (NearPackage())
                return CurrentJobAction();
            return "AZIONI";
        }

        /// <summary>L elenco completo delle azioni per il menu contestuale.</summary>
        public void BuildActions(List<Action> outList)
        {
            outList.Clear();
            Game g = Game.Instance;
            bool interior = g != null && g.IsInInterior;

            if (interior)
            {
                AddInteriorActions(outList);
                return;
            }

            if (nearNPC != null)
            {
                outList.Add(Make("PARLA", () => DoTalk()));
                AddFamilyActions(outList);
                outList.Add(Make("CALCIO", () => DoKick(true)));
                outList.Add(Make("SPINTONA", () => DoKick(false)));
            }

            if (FamilyKidHost.Instance != null && FamilyKidHost.Instance.NearKid())
                outList.Add(Make("PARLA FIGLIO",
                    () => FamilyKidHost.Instance.TalkToNearest()));

            if (FamilyManager.CanHaveChild())
                outList.Add(Make("\ud83d\udc76 AUMENTA FAMIGLIA",
                    () => TryHaveChild()));

            if (FamilyKidHost.Instance != null && FamilyKidHost.Instance.NearOrphan()
                && FamilyManager.CanAdopt())
                outList.Add(Make("\ud83e\udd34 ADOTTA ORFANO",
                    () => DoAdoptOrphan()));

            if (FamilyKidHost.Instance != null && FamilyKidHost.Instance.NearFosterParents()
                && FamilyManager.CanBeFostered())
                outList.Add(Make("\ud83c\udfe0 FAMIGLIA ADOTTIVA",
                    () => DoBeFostered()));

            if (g != null && g.CurrentVehicleFocus != null &&
                g.CurrentVehicleFocus.IsOwned())
                outList.Add(Make("SALI IN AUTO",
                    () => g.EnterVehicle(g.CurrentVehicleFocus.controller)));

            if (g != null && g.CurrentPoiZone != null)
                outList.Add(Make("NEGOZIO", () => g.CurrentPoiZone.Interact()));

            if (g != null && g.CurrentDoor != null)
            {
                outList.Add(Make("APRI PORTA", () => g.CurrentDoor.Interact()));
                outList.Add(Make("BUSSA", () => KnockDoor()));
            }

            if (NearPackage())
            {
                outList.Add(Make(CurrentJobAction(), () => DoPackageAction()));
                outList.Add(Make("BUTTA", () => DoPackageAction()));
                outList.Add(Make("SBIRCIA", () => PeekPackage()));
            }

            if (outList.Count == 0)
            {
                outList.Add(Make("\ud83d\udc80 MORI", () => DoDie()));
                outList.Add(Make("SALTO", () => DoJump()));
                outList.Add(Make("FISCHIO", WhistleTaxi));
                outList.Add(Make("SPRINT", () => DoSprint()));
                if (City.NPC.FamilyManager.IsDead)
                    outList.Add(Make("\ud83e\udd35 REINCARNA", () => ReincarnateNow()));
            }
            else if (g != null && g.IsDriving)
            {
                outList.Add(Make("FISCHIO", WhistleTaxi));
            }

            AddRealmActions(outList);
        }

        /// <summary>Aggiunge le azioni contestuali del regno afterlife in cui ci
        /// si trova (3.4): DASH in Inferno, VOLO in Paradiso.</summary>
        private void AddRealmActions(List<Action> outList)
        {
            var pc = PlayerController.Instance;
            var realm = pc != null ? pc.Realm : null;
            if (realm == null) return;

            switch (realm)
            {
                case City.Afterlife.AfterlifeRealm.INFERNO:
                    if (ShouldOfferIdleAction(outList))
                        AddUnique(outList, Make("DASH", () => DoDash()));
                    break;
                case City.Afterlife.AfterlifeRealm.PARADISO:
                    if (ShouldOfferIdleAction(outList))
                    {
                        AddUnique(outList, Make("VOLO", () => ToggleFlight()));
                        AddUnique(outList, Make("SALI", () => SetFlightVertical(1)));
                        AddUnique(outList, Make("SCENDI", () => SetFlightVertical(-1)));
                        AddUnique(outList, Make("STABILIZZA", () => SetFlightVertical(0)));
                    }
                    break;
            }
        }

        private bool ShouldOfferIdleAction(List<Action> outList)
        {
            // nelle arene i regni sono mini-giochi: mostra le azioni di
            // movimento solo quando non ci sono contesti piu' rilevanti
            Game g = Game.Instance;
            bool interior = g != null && g.IsInInterior;
            return !interior && nearNPC == null &&
                (g == null || (g.CurrentVehicleFocus == null && g.CurrentPoiZone == null &&
                 g.CurrentDoor == null && g.CurrentMissionNPC == null));
        }

        private void AddUnique(List<Action> outList, Action action)
        {
            for (int i = 0; i < outList.Count; i++)
                if (outList[i].label == action.label) return;
            outList.Add(action);
        }

        private void AddInteriorActions(List<Action> outList)
        {
            outList.Add(Make("FISCHIO", WhistleTaxi));
            outList.Add(Make("SALTO", () => DoJump()));
        }

        // ---- famiglia ----

        private void AddFamilyActions(List<Action> outList)
        {
            string cid = NPCCharId();
            if (string.IsNullOrEmpty(cid)) return;

            if (FamilyManager.IsSpouse(cid))
            {
                outList.Add(Make("\ud83d\udc8d CONIUGE", () => ShowSpouse()));
                outList.Add(Make("\ud83d\udc94 DIVORZIO CONSENSUALE",
                    () => DoDivorce(true)));
                outList.Add(Make("\ud83d\udc94 DIVORZIO CONTESTATO",
                    () => DoDivorce(false)));
            }
            else if (FamilyManager.CanMarry(cid))
            {
                outList.Add(Make("\ud83d\udc90 SPOSATI", () => DoMarry()));
            }
            else if (FamilyManager.CanEngage(cid))
            {
                outList.Add(Make("\ud83d\udc8d FIDANZATI", () => DoEngage()));
            }
        }

        private string NPCCharId()
        {
            return nearNPC != null ? nearNPC.CharacterId : null;
        }

        private void DoEngage()
        {
            var g = Game.Instance;
            string cid = NPCCharId();
            if (string.IsNullOrEmpty(cid)) return;
            if (!FamilyManager.CanEngage(cid))
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Non puoi fidanzarti ora (servono piu punti amicizia)");
                return;
            }
            string name = nearNPC.DisplayName;
            FamilyManager.Engage(cid, name);
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udc8d Ora sei fidanzato/a con " + name + "!");
        }

        private void DoMarry()
        {
            var g = Game.Instance;
            string cid = NPCCharId();
            if (string.IsNullOrEmpty(cid)) return;
            if (!FamilyManager.CanMarry(cid))
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Devi prima fidanzarti e rafforzare il legame");
                return;
            }
            string name = nearNPC.DisplayName;
            FamilyManager.Marry(cid, name);
            FamilyKidHost.Ensure();
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udc90 Ti sei sposato/a con " + name +
                    "! Bonus +25% missioni e +15 XP");
        }

        private void DoDivorce(bool consensual)
        {
            var g = Game.Instance;
            string cid = NPCCharId();
            if (string.IsNullOrEmpty(cid)) return;
            if (!FamilyManager.IsSpouse(cid)) return;
            string what = consensual ? "consensuale" : "contestato";
            FamilyManager.Divorce(consensual);
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udc94 Divorzio " + what + " eseguito.");
        }

        private void ShowSpouse()
        {
            var g = Game.Instance;
            var p = FamilyManager.Partner;
            if (p == null) { if (g != null && g.ui != null) g.ui.ShowToast("Nessun coniuge"); return; }
            int days = FamilyManager.DaysMarried();
            int kids = FamilyManager.ChildrenCount;
            if (g != null && g.ui != null)
                g.ui.ShowToast("Coniuge: " + p.name + " da " + days + " giorni, figli " + kids);
        }

        private void TryHaveChild()
        {
            var g = Game.Instance;
            if (!FamilyManager.CanHaveChild())
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Aspetta almeno " + FamilyManager.DaysToKidMin +
                        " giorni di matrimonio");
                return;
            }
            var c = FamilyManager.AddChild(NPCCharId());
            FamilyKidHost.Ensure();
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udc76 E nato/a " + c.name + "! E ora un NPC in citta.");
        }

        // ---- esecuzione azioni ----

        private void DoAdoptOrphan()
        {
            var g = Game.Instance;
            if (!FamilyManager.CanAdopt())
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Non puoi adottare ora (serve denaro o posti liberi)");
                return;
            }
            if (FamilyKidHost.Instance != null &&
                FamilyKidHost.Instance.AdoptNearestOrphan())
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("\ud83e\udd34 Hai adottato un bambino! Ora e tuo/a figlio/a.");
            }
            else if (g != null && g.ui != null)
            {
                g.ui.ShowToast("Nessun orfano vicino da adottare");
            }
        }

        private void DoBeFostered()
        {
            var g = Game.Instance;
            if (!FamilyManager.CanBeFostered())
            {
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Non puoi essere adottato ora");
                return;
            }
            if (FamilyKidHost.Instance != null &&
                FamilyKidHost.Instance.TriggerFoster())
            {
                string n1; string n2;
                FamilyKidHost.Instance.FosterNames(out n1, out n2);
                if (g != null && g.ui != null)
                    g.ui.ShowToast("\ud83c\udfe0 Sei stato/a adottato da " + n1 +
                        " e " + n2 + "! Genitori adottivi +" +
                        FamilyManager.FosterXpBonus + " XP.");
            }
            else if (g != null && g.ui != null)
            {
                g.ui.ShowToast("Nessuna coppia adottiva vicina");
            }
        }

        // ---- movimenti e interazione di base ----

        private void DoJump()
        {
            if (PlayerController.Instance != null)
                PlayerController.Instance.DoJump();
        }

        private void DoSprint()
        {
            if (PlayerController.Instance != null)
                PlayerController.Instance.DoSprint();
        }

        private void DoDash()
        {
            if (PlayerController.Instance != null)
                PlayerController.Instance.DoDash();
        }

        private void ToggleFlight()
        {
            var pc = PlayerController.Instance;
            if (pc == null) return;
            // se sta volando si atterra, altrimenti si spicca il volo
            if (pc.IsFlying)
            {
                pc.StopFlight();
                if (Game.Instance != null && Game.Instance.ui != null)
                    Game.Instance.ui.ShowToast("Atterri.");
            }
            else
            {
                pc.StartFlight();
                if (Game.Instance != null && Game.Instance.ui != null)
                    Game.Instance.ui.ShowToast("\ud83e\udd81 Stai volando! Tieni \"SALI\" per salire.");
            }
        }

        private void SetFlightVertical(int input)
        {
            if (PlayerController.Instance != null)
                PlayerController.Instance.SetFlightVertical(input);
        }

        private void DoTalk()
        {
            Game g = Game.Instance;
            if (nearNPC == null)
            {
                if (g != null && g.ui != null) g.ui.ShowToast("Nessuno da salutare");
                return;
            }
            var m = nearNPC.GetComponent<City.Economy.NPCMission>();
            if (m != null)
            {
                m.OnPlayerInteract();
            }
            else
            {
                string name = nearNPC.DisplayName;
                if (string.IsNullOrEmpty(name)) name = "qualcuno";
                if (g != null && g.ui != null)
                    g.ui.ShowToast("Parlare con " + name + " ...");
            }
        }

        private void DoKick(bool hard)
        {
            if (nearNPC == null) return;
            Vector3 pos = PlayerController.Instance != null
                ? PlayerController.Instance.transform.position : transform.position;
            Vector3 dir = nearNPC.transform.position - pos;
            dir.y = 0f;
            if (dir.sqrMagnitude > 0.0001f) dir.Normalize();
            if (hard) dir *= 2.2f;
            nearNPC.KnockDown(dir);
        }

        private void KnockDoor()
        {
            Game g = Game.Instance;
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\ude44 Bussare... nessuno risponde");
            if (g != null && g.CurrentDoor != null)
                g.CurrentDoor.Interact();
        }

        private void WhistleTaxi()
        {
            if (City.Vehicle.TaxiService.Instance != null)
                City.Vehicle.TaxiService.Instance.Whistle(Game.Instance);
        }

        // ---- pacchi (lavoro Consegne) ----

        private bool NearPackage()
        {
            return City.Economy.JobManager.CargoActive &&
                City.Economy.JobManager.NearCargo;
        }

        private string CurrentJobAction()
        {
            return City.Economy.JobManager.CargoStepIsPickup
                ? "RACCOGLI PACCO" : "LASCIA";
        }

        private void DoPackageAction()
        {
            City.Economy.JobManager.TriggerContextPackageAction();
        }

        private void PeekPackage()
        {
            Game g = Game.Instance;
            if (g != null && g.ui != null)
                g.ui.ShowToast("Scatole impilate. Non si vede cosa c e dentro.");
        }

        private void DoDie()
        {
            Game g = Game.Instance;
            City.NPC.FamilyManager.Die(City.NPC.FamilyManager.DeathType.INCIDENTE);
            if (g != null && g.ui != null)
                g.ui.ShowToast("\ud83d\udc80 Scegli di morire. Fine di questa vita...");
        }

        private void ReincarnateNow()
        {
            if (!City.NPC.FamilyManager.IsDead) return;
            City.NPC.FamilyManager.ResetPlayerState();
            // se ci si trova ancora dentro un regno (REINCARNA libero), torna
            // subito in citta' invece di restare nell'arena
            var rsm = City.Afterlife.RealmSceneManager.Instance;
            if (rsm != null && rsm.ActiveRealm != null)
                rsm.ReturnToCity();
            bool isMale = !City.NPC.FamilyManager.IsFemale;
            string newName = isMale ? "Marco" : "Giulia";
            Game g = Game.Instance;
            if (g != null && g.ui != null)
                g.ui.ShowToast("Nasci nuovamente come " + newName + ". Nuova vita!");
        }
    }
}
