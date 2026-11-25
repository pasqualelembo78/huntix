using System;
using System.Globalization;
using UnityEngine;
using City.World;
using City.OSM;

namespace City.Interior
{
    /// <summary>
    /// Catalogo degli interni universali: classificazione, etichette italiane e
    /// chiavi geografiche stabili. Non crea GameObject e non tocca la fisica:
    /// e' il contratto dati condiviso da builder, porte (Gateways adattato) e
    /// snapshot Android.
    ///
    /// Le tipologie derivano dai tag OSM gia' presenti nel tile stream e dai
    /// nomi delle attivita': nessun nuovo campo geo/graph, quindi
    /// TileClient.CacheVersion resta 5.
    /// </summary>
    public static class UniversalInteriorRegistry
    {
        public const string Tag = "UniversalInterior";

        /// <summary>Tipologie universali supportate (italiano nei cartelli).</summary>
        public static readonly string[] Kinds =
        {
            "casa", "appartamento", "negozio", "supermercato", "bar",
            "ristorante", "abbigliamento", "concessionaria", "farmacia",
            "ufficio", "parruccheria", "lavaggiocani", "autolavaggio",
            "biglietteria", "officina", "banca", "scuola", "ospedale", "hotel"
        };

        /// <summary>Classifica un interno generato dal tags OSM. Riutilizza la
        /// classificazione dei negozi walk-in per le attivita' commerciali e la
        /// specializza quando il nome dell'attivita' e' noto (ristorante,
        /// farmacia, ufficio). Nessun builder esistente viene rimosso.</summary>
        public static string Classify(string interiorType, Shop shop, string buildingName)
        {
            string t = (interiorType ?? "").ToLowerInvariant();
            string n = buildingName ?? "";
            if (shop != null && !string.IsNullOrEmpty(shop.shopName)) n = shop.shopName;
            n = n.ToLowerInvariant();

            // I nomipiu' specifici vincono sul tipo generico OSM: il tile
            // stream espone "building=bar" anche per pizzerie e "hospital" per
            // le farmacie, quindi senza questo passo non si distinguono.
            if (ContainsAny(n, "farmacia", "pharmacy", "drugstore")) return "farmacia";
            if (ContainsAny(n, "ristorante", "trattoria", "pizzeria", "pizza",
                               "restaurant", "sushi", "tavola")) return "ristorante";
            if (ContainsAny(n, "ufficio", "office", "studio", "notaio", "agenzia")) return "ufficio";

            switch (t)
            {
                case "fuel": case "repair": case "garage": return "officina";
                case "dealer": return "concessionaria";
                case "clothes": return "abbigliamento";
                case "hairdresser": return "parruccheria";
                case "dogwash": return "lavaggiocani";
                case "carwash": return "autolavaggio";
                case "ticket": return "biglietteria";
                case "hospital": return "ospedale";
                case "school": return "scuola";
                case "bank": return "banca";
                case "hotel": return "hotel";
                case "apartment": return "appartamento";
                case "shop": case "bar":
                    // Stessa classificazione dei negozi walk-in: bar/concessionaria/
                    // parruccheria restano coerenti con ShopInteriorBuilder.
                    return ShopInteriorBuilder.Classify(t, shop);
                default: return "casa";
            }
        }

        public static string ItalianName(string kind)
        {
            switch (kind)
            {
                case "appartamento": return "Appartamento";
                case "negozio": return "Negozio";
                case "supermercato": return "Supermercato";
                case "bar": return "Bar / Caffetteria";
                case "ristorante": return "Ristorante / Pizzeria";
                case "abbigliamento": return "Negozio di abbigliamento";
                case "concessionaria": return "Compravendita auto";
                case "farmacia": return "Farmacia";
                case "ufficio": return "Ufficio";
                case "parruccheria": return "Parruccheria";
                case "lavaggiocani": return "Lavaggio cani";
                case "autolavaggio": return "Autolavaggio";
                case "biglietteria": return "Biglietteria";
                case "officina": return "Officina meccanica";
                case "banca": return "Banca";
                case "scuola": return "Scuola";
                case "ospedale": return "Ospedale / Clinica";
                case "hotel": return "Hotel";
                default: return "Casa";
            }
        }

        /// <summary>Indizio uovo in italiano: il contenitore reale e' creato dal
        /// builder universale e resta coerente con ContainerEgg.</summary>
        public static string EggHint(string kind)
        {
            switch (kind)
            {
                case "farmacia": return "Uovo nel cassetto del banco farmacia.";
                case "ufficio": return "Uovo nella scrivania dello studio.";
                case "ristorante": return "Uovo nel cassetto della cucina.";
                case "ospedale": return "Uovo nell'armadietto dei medicinali.";
                case "scuola": return "Uovo nel cassetto della cattedra.";
                case "hotel": return "Uovo nel comodino della camera.";
                case "officina": return "Uovo nella cassetta degli attrezzi.";
                case "banca": return "Uovo nel cassetto dello sportello.";
                case "appartamento": case "casa": return "Uovo nel cassetto della cucina.";
                default: return "Uovo in uno dei contenitori del locale.";
            }
        }

        public static bool IsKnown(string kind)
        {
            if (string.IsNullOrEmpty(kind)) return false;
            for (int i = 0; i < Kinds.Length; i++)
                if (Kinds[i] == kind) return true;
            return false;
        }

        /// <summary>Chiave stabile per edificio/tipologia. Usa coordinate
        /// geografiche (WorldOrigin.ToGeo) come ContainerEgg e ShopInteriorBuilder,
        /// cosi' sopravvive al rebase del mondo e al rebuild dei chunk.</summary>
        public static string StableKey(Vector3 worldPos, string kind, string name)
        {
            string lat = "0", lng = "0";
            if (WorldOrigin.Initialized)
            {
                var g = WorldOrigin.ToGeo(worldPos);
                lat = g.lat.ToString("F5", CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F5", CultureInfo.InvariantCulture);
            }
            string k = string.IsNullOrEmpty(kind) ? "casa" : kind;
            string n = string.IsNullOrEmpty(name) ? k : name.ToLowerInvariant();
            return "interno@" + lat + "," + lng + "#" + k + "#" + n;
        }

        /// <summary>Id stabile di un varco interno (pattern Gateways adattato:
        /// GUID di porta deterministici invece di numeri progressivi).</summary>
        public static string GateId(string interiorKey, string gateName)
        {
            return interiorKey + "#varco-" + (gateName ?? "ingresso");
        }

        private static bool ContainsAny(string s, params string[] tokens)
        {
            if (string.IsNullOrEmpty(s)) return false;
            for (int i = 0; i < tokens.Length; i++)
                if (s.IndexOf(tokens[i], StringComparison.OrdinalIgnoreCase) >= 0) return true;
            return false;
        }
    }
}
