using System.Collections.Generic;
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// RIFLESSO UVOVA (Mandato): ogni nuova feature propone la sua
    /// integrazione uova. Qui gli edifici enterabili nascondono un uovo
    /// (tipo Edificio) DENTRO un contenitore della cucina/armadio
    /// (frigo, forno, armadio, credenza, lavello). L'uovo resta celato
    /// fino a quando il giocatore non si avvicina al contenitore: a quel
    /// punto appare (gli renderizzatori si riattivano) e si può raccogliere
    /// con il normale mini-gioco (ricompensa + Bestiario + profilo Huntix).
    ///
    /// Scelta del contenitore DETERMINISTICA per interni (seed dall'istanza
    /// della radice interna): stesso edificio rigenerato → stesso contenitore.
    /// Un solo gestore DontDestroyOnLoad che si aggancia all'ingresso.
    /// </summary>
    public class ContainerEgg : MonoBehaviour
    {
        private static ContainerEgg _instance;

        public static ContainerEgg Instance { get { return _instance; } }

        public static bool Active
        {
            get { return _instance != null; }
        }

        public static void Ensure()
        {
            if (_instance != null) return;
            var go = new GameObject("ContainerEgg");
            DontDestroyOnLoad(go);
            _instance = go.AddComponent<ContainerEgg>();
        }

        // Probabilita' che l'edificio abbia l'uovo in un contenitore (50%).
        private const float EggProb = 0.5f;
        private const float RevealDist = 2.4f;

        // Nomi dei contenitori riconosciuti (lowercase, prefix-match).
        private static readonly string[] ContainerNames =
        {
            "frigo", "forno", "armadio", "cassetto", "credenza",
            "lavello", "dispensa", "scrivania",
        };

        private Transform _root;
        private Transform _chosen;
        private GameObject _activeEgg;
        private bool _busy;

        private void Update()
        {
            var g = Game.Instance;
            if (g == null || g.player == null) return;
            var mgr = InteriorManager.Instance;
            bool inside = mgr != null && mgr.IsInside;
            Transform root = inside && mgr != null ? mgr.ActiveInteriorRoot : null;

            if (!inside || root == null)
            {
                if (_root != null) ResetState();
                return;
            }
            if (root != _root) BeginBuilding(root);
            if (_busy || _chosen == null || _activeEgg== null) return;

            float d = Vector3.Distance(g.player.transform.position, _chosen.position);
            if (d <= RevealDist) Reveal(_chosen, g);
        }

        private void BeginBuilding(Transform root)
        {
            ResetState();
            _root = root;

            var containers = new List<Transform>();
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
            {
                Transform t = all[i];
                if (t == null) continue;
                string n = t.name.ToLowerInvariant();
                if (n.IndexOf("(clone)") >= 0) n = n.Replace("(clone)", "");
                for (int k = 0; k < ContainerNames.Length; k++)
                {
                    if (n.IndexOf(ContainerNames[k], System.StringComparison.Ordinal) >= 0)
                    {
                        containers.Add(t);
                        break;
                    }
                }
            }
            if (containers.Count == 0)
            {
                _busy = true;
                return;
            }

            // Deterministico per radice interna: stesso edificio, stesso
            // contenitore a ogni ingresso/ricostruzione.
            System.Random rng = new System.Random(root.GetInstanceID());
            _chosen = containers[rng.Next(containers.Count)];

            if (rng.NextDouble() >= EggProb)
            {
                _busy = true;
                return;
            }

            // F4 (Riflesso uova): in stanza condivisa con un amico la rarita'
            // viene lanciata due volte e si tiene la migliore (fortuna di squadra).
            City.Economy.EggController.Rarity r = RollRarity(rng);
            var mp = City.Multiplayer.MultiplayerManager.Instance;
            if (mp != null && Game.Instance != null && Game.Instance.player != null)
            {
                if (mp.SharedRoomMate(Game.Instance.player.transform.position, 6f) != null)
                {
                    City.Economy.EggController.Rarity r2 = RollRarity(rng);
                    if (r2 > r) r = r2;
                }
            }

            Vector3 pos = _chosen.position + Vector3.up * 0.55f +
                _chosen.forward * 0.35f;
            var go = new GameObject("Egg_Container_" + _chosen.name);
            go.transform.SetParent(_chosen, true);
            var col = go.AddComponent<SphereCollider>();
            col.radius = 0.5f;
            col.isTrigger = true;
            var egg = go.AddComponent<City.Economy.EggController>();
            pos.y = Mathf.Max(pos.y, go.transform.position.y + 0.05f);
            egg.Init(pos, r, City.Economy.EggController.EggType.Edificio);
            go.SetActive(false); // nascosto fino all'avvicinamento
            _activeEgg = go;
            _busy = false;
        }

        private static City.Economy.EggController.Rarity RollRarity(System.Random rng)
        {
            double rr = rng.NextDouble();
            if (rr < 0.10) return City.Economy.EggController.Rarity.Legendary;
            if (rr < 0.30) return City.Economy.EggController.Rarity.Epic;
            if (rr < 0.75) return City.Economy.EggController.Rarity.Rare;
            return City.Economy.EggController.Rarity.Uncommon;
        }

        private void Reveal(Transform container, Game g)
        {
            if (_activeEgg == null) return;
            _activeEgg.SetActive(true);
            if (g.ui != null)
            {
                string nm = System.Threading.Thread.CurrentThread.CurrentCulture.TextInfo
                    .ToTitleCase(container.name.ToLowerInvariant().Replace("_", " "));
                g.ui.ShowToast("In " + nm + " c'e' qualcosa che luccica...");
            }
            UnityBridge.LogToAndroid("ContainerEgg", "uovo rivelato in " + container.name);
            _activeEgg = null; // una sola rivelazione: se raccolto, distrutto dal capture
            _busy = true;
        }

        private void ResetState()
        {
            _root = null;
            _chosen = null;
            _activeEgg = null;
            _busy = false;
        }
    }
}
