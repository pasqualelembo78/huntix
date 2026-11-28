using UnityEngine;

namespace City.Interior
{
    /// <summary>
    /// Elemento discreto DENTRO l'abitazione per decidere l'acquisto dopo
    /// averla visitata. Non è un riquadro giallo esterno, non apre menu e non
    /// blocca l'esplorazione: solo un piccolo avviso sulla parete con
    /// un'azione contestuale quando il giocatore gli si avvicina.
    /// La logica economica resta tutta in HomeSystem.
    /// </summary>
    public class HousePurchaseSpot : MonoBehaviour
    {
        private BuildingEntrance _entrance;
        private bool _focused;
        private bool _infoShown;

        /// <summary>Installa l'avviso una sola volta per interno. Chiamato
        /// quando il player entra in una casa (inclusa la tasca vittoriana).</summary>
        public static void Install(Transform root, BuildingEntrance entrance)
        {
            if (root == null || entrance == null) return;
            if (!HouseAccess.IsResidential(entrance)) return;
            if (root.GetComponentInChildren<HousePurchaseSpot>(true) != null) return;
            if (HouseAccess.RoleFor(entrance) == HouseAccess.Role.Owner) return;
            if (entrance.reservedByOther) return;

            var go = new GameObject("AvvisoVenditaCasa");
            go.transform.SetParent(root, false);
            // Lato opposto alla porta d'ingresso: non interferisce con la soglia.
            Vector3 f = entrance.transform.forward;
            Vector3 localDir = root.InverseTransformDirection(-f);
            go.transform.localPosition = new Vector3(
                localDir.x * 1.5f, 1.35f, localDir.z * 1.5f);
            go.transform.localRotation = Quaternion.LookRotation(localDir, Vector3.up);

            var plaque = GameObject.CreatePrimitive(PrimitiveType.Cube);
            plaque.name = "TarghettaCasa";
            plaque.transform.SetParent(go.transform, false);
            plaque.transform.localScale = new Vector3(0.55f, 0.34f, 0.04f);
            Object.Destroy(plaque.GetComponent<Collider>());
            Material mat = FindInteriorMaterial(root);
            if (mat != null) plaque.GetComponent<Renderer>().sharedMaterial = mat;

            var trigger = go.AddComponent<BoxCollider>();
            trigger.isTrigger = true;
            trigger.size = new Vector3(1.6f, 2f, 1.6f);

            var spot = go.AddComponent<HousePurchaseSpot>();
            spot._entrance = entrance;
        }

        private static Material FindInteriorMaterial(Transform root)
        {
            var rends = root.GetComponentsInChildren<Renderer>(true);
            for (int i = 0; i < rends.Length; i++)
            {
                if (rends[i] == null || rends[i].sharedMaterial == null) continue;
                return rends[i].sharedMaterial;
            }
            return null;
        }

        private void OnTriggerEnter(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            var mgr = InteriorManager.Instance;
            if (mgr == null || !mgr.IsInside) return;
            if (_entrance == null || HouseAccess.RoleFor(_entrance) == HouseAccess.Role.Owner) return;

            _focused = true;
            if (mgr != null)
                mgr.RegisterInteriorAction(Buy, HouseAccess.BuyLabel(_entrance));
            if (!_infoShown)
            {
                _infoShown = true;
                var g = Game.Instance;
                if (g != null && g.ui != null)
                {
                    string price = City.Diagnostics.CityGlobalAreasBridge.FirstHomeFreeAvailable
                        ? "prima casa gratuita"
                        : City.Environment.HomeSystem.HomeCost + " euro";
                    g.ui.ShowToast("Casa in vendita: " + _entrance.buildingName +
                                   " - " + price + ". Puoi comprarla da qui.");
                }
            }
        }

        private void OnTriggerExit(Collider other)
        {
            if (!other.CompareTag("Player")) return;
            _focused = false;
            var mgr = InteriorManager.Instance;
            if (mgr != null) mgr.UnregisterInteriorAction(Buy);
        }

        /// <summary>Azione contestuale: acquista solo se la casa è libera e
        /// il portafoglio lo consente. Visitare non comporta mai un acquisto.</summary>
        public void Buy()
        {
            if (!_focused) return;
            if (HouseAccess.TryBuy(_entrance))
            {
                var mgr = InteriorManager.Instance;
                if (mgr != null) mgr.UnregisterInteriorAction(Buy);
                Destroy(gameObject);
            }
        }
    }
}
