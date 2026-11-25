using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using City.OSM;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Varchi interni universali: adattamento del pattern Gateways
    /// (nmacadam/Gateways, MIT) al mondo chunked di Huntix.
    ///
    /// Gateways collegava SCENE Unity con GUID persistenti e ReturnGate qui non
    /// caricamo scene (i chunk verrebbero smontati e il rebase GPS perso): le
    /// porte collegano DUE PUNTI DELLO STESSO INTERNO con id deterministici
    /// derivati dalla chiave geo-stabile. Il varco di arrivo diventa il
    /// ReturnGate di quello di partenza, cosi' il ritorno funziona sempre.
    /// Nessun cambio di scena, nessun teletrasporto verso coordinate assolute:
    /// i target sono Transform figli del chunk e seguono il rebase col root.
    /// </summary>
    public sealed class InteriorGate : MonoBehaviour
    {
        public string gateId = "";
        public string targetGateId = "";
        public string label = "VARCO";
        public Vector3 spawnOffset = new Vector3(0f, 1f, -0.8f);

        private float _cooldownUntil;

        public void Configure(string id, string target, string labelText)
        {
            gateId = id ?? "";
            targetGateId = target ?? "";
            label = labelText ?? "VARCO";
            // AddComponent esegue Awake PRIMA di Configure: li' l'id e' ancora
            // vuoto e la porta non verrebbe registrata. Registriamo qui con
            // l'id definitivo (Assegnazione idempotente nel dizionario).
            InteriorGateRegistry.Register(this);
        }

        private void Awake()
        {
            if (!string.IsNullOrEmpty(gateId)) InteriorGateRegistry.Register(this);
        }

        private void OnDestroy()
        {
            InteriorGateRegistry.Unregister(this);
        }

        private void OnTriggerEnter(Collider other)
        {
            if (other == null || !other.CompareTag("Player")) return;
            Use();
        }

        /// <summary>Attraversa il varco. Ritorna false se il varco di destinazione
        /// non e' caricato (chunk scaricato): in quel caso non succede nulla e il
        /// giocatore resta dov'e', invece di cadere nel vuoto.</summary>
        public bool Use()
        {
            if (string.IsNullOrEmpty(targetGateId)) return false;
            InteriorGate target = InteriorGateRegistry.Find(targetGateId);
            if (target == null || target == this || Time.time < _cooldownUntil) return false;

            // Gateways: il varco di arrivo diventa la porta di ritorno.
            target.targetGateId = gateId;
            _cooldownUntil = Time.time + 2f;
            target._cooldownUntil = _cooldownUntil;

            Game g = Game.Instance;
            if (g == null || g.player == null) return false;
            StartCoroutine(Transit(target, g));
            return true;
        }

        private IEnumerator Transit(InteriorGate target, Game g)
        {
            var fader = g.fader;
            if (fader != null)
            {
                fader.gameObject.SetActive(true);
                fader.FadeToBlack(null);
            }

            Vector3 pos = target.transform.position + target.transform.rotation * target.spawnOffset;
            Quaternion rot = target.transform.rotation;
            InteriorTeleport.Move(pos, rot, g);

            try
            {
                UnityBridge.LogToAndroid("InteriorGate",
                    "varco " + label + " → " + target.label + " (" + gateId + ")");
            }
            catch (System.Exception) { }

            if (fader != null)
            {
                yield return new WaitForSeconds(fader.duration);
                fader.FadeFromBlack(null);
                yield return new WaitForSeconds(fader.duration);
                fader.gameObject.SetActive(false);
            }
        }
    }

    /// <summary>Registro dei varchi attivi. Nessun oggetto persistente tra scene: quando il
    /// chunk viene scaricato OnDestroy rimuove la porta e i riferimenti muoiono
    /// col chunk (nessun leak tra citta' diverse).</summary>
    public static class InteriorGateRegistry
    {
        private static readonly Dictionary<string, InteriorGate> Gates =
            new Dictionary<string, InteriorGate>();

        public static void Register(InteriorGate gate)
        {
            if (gate == null || string.IsNullOrEmpty(gate.gateId)) return;
            Gates[gate.gateId] = gate; // rebuild: l'istanza nuova vince
        }

        public static void Unregister(InteriorGate gate)
        {
            if (gate == null || string.IsNullOrEmpty(gate.gateId)) return;
            InteriorGate current;
            if (Gates.TryGetValue(gate.gateId, out current) && current == gate)
                Gates.Remove(gate.gateId);
        }

        public static InteriorGate Find(string gateId)
        {
            if (string.IsNullOrEmpty(gateId)) return null;
            InteriorGate gate;
            return Gates.TryGetValue(gateId, out gate) ? gate : null;
        }

        public static int Count { get { return Gates.Count; } }
    }

    /// <summary>Unico helper di teleporto interno: CharacterController spento
    /// durante lo spostamento, guard anti-NaN e camera/yaw ripristinati. Usato
    /// da BuildingEntrance, InteriorManager e dai varchi, cosi' non esistono tre
    /// copie divergenti della stessa operazione.</summary>
    public static class InteriorTeleport
    {
        public static bool Move(Vector3 pos, Quaternion rot, Game g)
        {
            if (g == null || g.player == null) return false;
            if (!WorldOrigin.IsFinite(pos)) return false;

            var cc = g.player.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            g.player.transform.position = pos;
            g.player.transform.rotation = rot;
            if (cc != null) cc.enabled = true;
            g.player.Stop();
            if (g.rig != null) g.rig.SetYaw(rot);
            return true;
        }
    }
}
