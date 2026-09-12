using UnityEngine;

namespace City.OSM
{
    /// <summary>
    /// Diagnostica collider attorno al player, OFF di default. Per usarla in
    /// una sessione di debug: ColliderProbe.Enabled = true. Ogni transizione
    /// dentro/fuori degli edifici in-place dumpa in logcat ("OSM") i collider
    /// attivi entro 2 m dal giocatore (nome/layer/tipo/trigger/stato): serve a
    /// identificare quale collider blocca davvero la porta quando la fisica
    /// sembra unidirezionale. Nessun costo quando Enabled=false.
    /// NB: solo API stub-safe per il checker cscheck della pipeline.
    /// </summary>
    public static class ColliderProbe
    {
        public static bool Enabled = false;
        private const float Radius = 2.0f;

        public static void ProbeAt(Vector3 worldPos, string context)
        {
            if (!Enabled) return;
            try
            {
                Collider[] cols = Physics.OverlapSphere(worldPos, Radius);
                string msg = "ColliderProbe[" + context + "] pos=(" +
                    worldPos.x.ToString() + "," + worldPos.y.ToString() + "," +
                    worldPos.z.ToString() + ") n=" + cols.Length;
                for (int i = 0; i < cols.Length; i++)
                {
                    Collider c = cols[i];
                    if (c == null || c.gameObject == null) continue;
                    string go = c.name == null ? "?" : c.name;
                    msg += "\n  " + go + " | " + c.GetType().Name +
                        " | layer=" + c.gameObject.layer +
                        " | trig=" + c.isTrigger + " | on=" + c.enabled;
                }
                OsmDiag.Log(msg);
            }
            catch (System.Exception e)
            {
                OsmDiag.Log("ColliderProbe errore: " + e.Message);
            }
        }
    }
}
