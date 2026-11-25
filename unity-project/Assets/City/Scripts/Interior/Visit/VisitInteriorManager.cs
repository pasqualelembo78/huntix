using System;
using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Coordina le case visitabili collegate a edifici OSM reali.
    ///
    /// È un innesto ADDITIVO su InteriorManager/ContainerEgg: non crea teleport,
    /// non aggiunge scene e non tocca i builder esistenti. Il suo compito è
    /// registrare la visita (con id OSM stabile) e consegnare le uova raccolte
    /// nei nascondigli dichiarati dal prefab della casa.
    ///
    /// Riflesso del Mandato: ogni ingresso in una casa configurata comunica a
    /// Android l'evento HouseVisitChanged, mentre ogni uovo dei contenitori
    /// arriva al profilo con luogo "casa→stanza→oggetto" e coordinate GPS.
    /// </summary>
    public static class VisitInteriorManager
    {
        public static bool IsConfigured(BuildingEntrance entrance)
        {
            return entrance != null && VisitInteriorRegistry.IsConfigured(entrance.osmId);
        }

        /// <summary>Chiamato da InteriorManager.MarkInside, dopo che lo stato
        /// "dentro" è stato committato. Registra la visita e mostra un indizio
        /// in italiano: i tre nascondigli restano nell'intero, non nel prompt.</summary>
        public static void OnEntered(BuildingEntrance entrance)
        {
            if (!IsConfigured(entrance)) return;
            VisitInteriorRegistry.Spec spec;
            if (!VisitInteriorRegistry.TryGet(entrance.osmId, out spec)) return;

            HouseInteriorState.MarkVisited(entrance.osmId, entrance.buildingType, spec.label);
            try
            {
                if (Game.Instance != null && Game.Instance.ui != null)
                    Game.Instance.ui.ShowToast(
                        "Sei in " + spec.label + "! Cerca le uova in cucina, camera e bagno.");
                Huntix.Bridge.UnityBridge.LogToAndroid(VisitInteriorRegistry.Tag,
                    "visita casa: " + spec.label + " (OSM " + entrance.osmId + ")");
            }
            catch (Exception) { }
        }

        /// <summary>Chiamato da InteriorManager.MarkOutside / ExitPrefabRoutine.
        /// La visita resta memorizzata: l'uscita non la rimuove dallo snapshot.</summary>
        public static void OnExited(BuildingEntrance entrance)
        {
            if (!IsConfigured(entrance)) return;
            VisitInteriorRegistry.Spec spec;
            if (!VisitInteriorRegistry.TryGet(entrance.osmId, out spec)) return;
            try
            {
                Huntix.Bridge.UnityBridge.LogToAndroid(VisitInteriorRegistry.Tag,
                    "uscita casa: " + spec.label + " (visite conservate)");
            }
            catch (Exception) { }
        }

        /// <summary>Chiamato da ContainerEgg.MarkFound per le chiavi casa-*.
        /// Dedup: un rebuild del chunk o un restore Android non fa rinascere
        /// l'uovo nello stesso cassetto/armadio/lavandino.</summary>
        public static void OnEggCollected(string featureId)
        {
            if (string.IsNullOrEmpty(featureId) ||
                !featureId.StartsWith("casa-", StringComparison.Ordinal)) return;
            HouseInteriorState.MarkEgg(featureId);
        }
    }
}
