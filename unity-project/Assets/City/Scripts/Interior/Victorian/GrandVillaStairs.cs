// Mandato §4 · Riflesso della caccia alle uova: salire di piano significa
// raggiungere nuove stanze con nuovi nascondigli, non una nuova economia.
// File dedicato: Unity serializza i MonoBehaviour nei prefab solo quando il
// nome del file coincide con il nome della classe (qui GrandVillaStairs).
using UnityEngine;
using Huntix.Bridge;

namespace City.Interior.Victorian
{
    /// <summary>Scala fra i piani della villa da 300 mq. Usa lo stesso
    /// teleporto helper di tutti gli altri gate dell'interior: nessuna nuova
    /// fisica, nessun sistema rimosso, solo il cambio di livello.</summary>
    public sealed class GrandVillaStairs : MonoBehaviour
    {
        public int targetFloor = 1;
        public bool down;

        private void OnTriggerEnter(Collider other)
        {
            if (other == null || !other.CompareTag("Player")) return;
            if (!VictorianGrandInterior.IsInsidePocket) return;
            VictorianPocket pocket = GetComponentInParent<VictorianPocket>();
            Game g = Game.Instance;
            if (pocket == null || g == null) return;
            Vector3 local = new Vector3(0f,
                targetFloor * VictorianGrandInterior.GrandFloorHeight + 1.15f, 3.2f);
            InteriorTeleport.Move(pocket.transform.TransformPoint(local),
                Quaternion.Euler(0f, down ? 180f : 0f, 0f), g);
            if (g.ui != null)
                g.ui.ShowToast(down ? "Sei sceso al piano " + (targetFloor + 1)
                                    : "Sei salito al piano " + (targetFloor + 1));
            try
            {
                UnityBridge.LogToAndroid(VictorianGrandInterior.Tag,
                    "villa 300 mq → piano " + (targetFloor + 1));
            }
            catch (System.Exception) { }
        }
    }
}
