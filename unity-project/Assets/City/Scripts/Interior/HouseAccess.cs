using UnityEngine;

namespace City.Interior
{
    /// <summary>
    /// Punto unico per accesso, ruolo e permessi delle abitazioni.
    ///
    /// L'accesso fisico alla casa e la proprietà sono due cose distinte:
    /// chiunque può visitare una casa accessibile; solo il proprietario può
    /// modificare arredi o stato persistente. Il sistema è volutamente
    /// estendibile: <see cref="HouseEntryKind"/> distingue già ingresso
    /// principale, finestra, garage, grondaie e accesso furtivo senza
    /// implementare ora furtività, furti o polizia.
    /// </summary>
    public static class HouseAccess
    {
        public enum Role
        {
            Visitor,
            Owner
        }

        /// <summary>Vie d'accesso già discriminate dal modello. Solo MainDoor
        /// è attiva oggi; le altre sono riservate a future meccaniche.</summary>
        public enum EntryKind
        {
            MainDoor,
            Window,
            Garage,
            Gutter,
            Stealth
        }

        /// <summary>True per gli edifici che rappresentano un'abitazione
        /// (esclusi negozi, bar e zone veicolo aperte).</summary>
        public static bool IsResidential(BuildingEntrance e)
        {
            if (e == null) return false;
            string t = e.buildingType ?? "";
            return t == "house" || t == "apartment" ||
                   (t == "hotel" && e.osmId != 0L);
        }

        /// <summary>Ruolo del giocatore rispetto all'ingresso indicato.
        /// La proprietà resta quella del sistema esistente (HomeSystem):
        /// nessun secondo registro, nessuna seconda valuta.</summary>
        public static Role RoleFor(BuildingEntrance e)
        {
            if (e == null) return Role.Visitor;
            Vector3 door = e.transform.position;
            if (City.Environment.HomeSystem.IsThisHome(door)) return Role.Owner;
            // Durante lo spawn iniziale la casa è appena stata assegnata ma WorldOrigin
            // potrebbe non essere ancora inizializzato: la porta dell'ingresso corrente
            // è comunque di proprietà se HomeSystem la riconosce per coordinate.
            return Role.Visitor;
        }

        /// <summary>True se il giocatore può modificare l'interior corrente.
        /// Chiamato dalle logiche che modificano davvero gli oggetti, non solo
        /// dalla UI. Fuori da una casa (o dentro un negozio) il comportamento
        /// precedente resta invariato.</summary>
        public static bool CanModifyCurrentInterior()
        {
            var mgr = InteriorManager.Instance;
            if (mgr == null) return true;
            if (!mgr.IsInside) return true;
            var e = mgr.CurrentEntrance;
            if (!IsResidential(e)) return true;
            return RoleFor(e) == Role.Owner;
        }

        /// <summary>Verifica i permessi prima di collocare un arredo.</summary>
        public static bool CanPlaceFurniture()
        {
            if (!CanModifyCurrentInterior())
            {
                Notify("Sei in visita: puoi esplorare la casa ma non modificarla.");
                return false;
            }
            return true;
        }

        /// <summary>Etichetta contestuale dell'acquisto: prezzo e disponibilità
        /// vengono dal sistema economico esistente, non da un nuovo listino.</summary>
        public static string BuyLabel(BuildingEntrance e)
        {
            if (e == null) return "";
            if (e.reservedByOther) return "CASA NON DISPONIBILE";
            if (City.Environment.HomeSystem.OwnsHome) return "HAI GIA UNA CASA";
            return City.Environment.HomeSystem.BuyLabel + " - " +
                   City.Environment.HomeSystem.HomeCost + " EUR";
        }

        /// <summary>Acquisto dalla casa in cui ci si trova. Nessun teletrasporto,
        /// nessuna agenzia e nessun NPC: riutilizza HomeSystem.Buy (che a sua
        /// volta usa Wallet.TrySpend e il beneficio prima casa gratuita).</summary>
        public static bool TryBuy(BuildingEntrance e)
        {
            if (e == null) return false;
            if (!IsResidential(e)) return false;
            if (e.reservedByOther)
            {
                Notify("Questa casa non è disponibile per l'acquisto.");
                return false;
            }
            if (RoleFor(e) == Role.Owner)
            {
                Notify("Questa casa è già tua.");
                return false;
            }
            if (City.Environment.HomeSystem.OwnsHome)
            {
                Notify("Possiedi già una casa. Vendi prima di comprarne un'altra.");
                return false;
            }
            if (!City.Diagnostics.CityGlobalAreasBridge.FirstHomeFreeAvailable &&
                !City.World.Wallet.CanAfford(City.Environment.HomeSystem.HomeCost))
            {
                Notify("Non hai abbastanza soldi per la casa (" +
                       City.Environment.HomeSystem.HomeCost + " euro).");
                return false;
            }
            return City.Environment.HomeSystem.Buy(e.buildingName, e.transform.position);
        }

        /// <summary>Registra l'ingresso nel canale uova del Mandato senza
        /// generare valuta: luogo testuale + coordinate reali della porta.</summary>
        public static void NoteEntry(BuildingEntrance e, EntryKind kind)
        {
            if (e == null || !IsResidential(e)) return;
            City.Diagnostics.CityGlobalAreasBridge.Record(
                "città", kind == EntryKind.MainDoor ? "visita casa" : "accesso casa",
                e.buildingName, "città→casa→" + kind + "→" + e.buildingName,
                e.transform.position);
        }

        private static void Notify(string msg)
        {
            var g = Game.Instance;
            if (g != null && g.ui != null) g.ui.ShowToast(msg);
        }
    }
}
