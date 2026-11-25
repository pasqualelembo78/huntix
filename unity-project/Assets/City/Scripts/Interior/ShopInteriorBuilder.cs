using System;
using System.Collections.Generic;
using UnityEngine;
using TMPro;
using City.Economy;
using City.World;
using Huntix.Bridge;

namespace City.Interior
{
    /// <summary>
    /// Arreda i negozi walk-in generati da InteriorGenerator con il kit CC0 già
    /// importato (Kenney Mini Market, Furniture Kit, Car Kit, Characters,
    /// Cube Pets) e aggiunge cassiera animata, clienti, bancone vissuto e
    /// contatori stile tycoon.
    ///
    /// NON sostituisce InteriorGenerator: si limita a decorare il "Floor_0"
    /// che i builder esistenti hanno già creato, conservando guscio, porte,
    /// trigger cassa/usci­ta, uova nei contenitori e collider del pavimento.
    /// Regola inviolabile: i mobili non hanno collider; solo i volumi trigger
    /// separati gestiscono le interazioni.
    /// </summary>
    public static class ShopInteriorBuilder
    {
        public const string Tag = "ShopInteriorBuilder";

        private static readonly Dictionary<string, Material> Materials =
            new Dictionary<string, Material>();

        private static readonly string[] KindIds =
        {
            "supermercato", "bar", "abbigliamento", "concessionaria",
            "parruccheria", "lavaggiocani", "biglietteria", "autolavaggio"
        };

        /// <summary>Le attività reali supportate, in italiano per le UI.</summary>
        public static string[] Kinds()
        {
            string[] labels = new string[KindIds.Length];
            for (int i = 0; i < KindIds.Length; i++) labels[i] = ItalianName(KindIds[i]);
            return labels;
        }

        public static string ItalianName(string kind)
        {
            switch (kind)
            {
                case "bar": return "Bar / Caffetteria";
                case "abbigliamento": return "Negozio di abbigliamento";
                case "concessionaria": return "Compravendita auto";
                case "parruccheria": return "Parruccheria / Salone bellezza";
                case "lavaggiocani": return "Lavaggio cani";
                case "biglietteria": return "Biglietteria treni";
                case "autolavaggio": return "Autolavaggio";
                default: return "Supermercato";
            }
        }

        /// <summary>Classifica il tipo interior generato dal tags OSM oppure,
        /// per i negozi generici, dal nome/catalogo dello Shop.</summary>
        public static string Classify(string type, Shop shop)
        {
            string t = (type ?? "").ToLowerInvariant();
            // Ordine conta: "barber" contiene "bar", "car_wash" contiene "wash".
            if (t == "hairdresser" || t.Contains("barber") || t.Contains("hair") ||
                t.Contains("beauty") || t.Contains("salon")) return "parruccheria";
            if (t == "carwash" || t.Contains("car_wash") || t.Contains("carwash")) return "autolavaggio";
            if (t == "dogwash" || t.Contains("dog_wash") || t.Contains("dogwash") ||
                t.Contains("pet_grooming") || t.Contains("groom")) return "lavaggiocani";
            if (t == "bar" || t.Contains("cafe") || t.Contains("pub") || t.Contains("coffee")) return "bar";
            if (t.Contains("dealer") || t.Contains("showroom")) return "concessionaria";
            if (t.Contains("clothes") || t.Contains("apparel") || t.Contains("boutique")) return "abbigliamento";
            if (t.Contains("ticket") || t.Contains("train") || t.Contains("rail")) return "biglietteria";

            string name = shop != null && !string.IsNullOrEmpty(shop.shopName)
                ? shop.shopName.ToLowerInvariant() : "";
            if (name.Contains("supermercat") || name.Contains("market") || name.Contains("aliment")) return "supermercato";
            if (name.Contains("parrucch") || name.Contains("barbiere") || name.Contains("barber") ||
                name.Contains("bellezza") || name.Contains("salone")) return "parruccheria";
            if (name.Contains("autolav") || name.Contains("car wash")) return "autolavaggio";
            if (name.Contains("lavacani") || name.Contains("cane") || name.Contains("pet")) return "lavaggiocani";
            if (name.Contains("bar") || name.Contains("caff")) return "bar";
            if (name.Contains("auto") || name.Contains("concession")) return "concessionaria";
            if (name.Contains("abbigli") || name.Contains("moda")) return "abbigliamento";
            if (name.Contains("tren") || name.Contains("bigliett")) return "biglietteria";

            if (shop != null && shop.items != null)
            {
                for (int i = 0; i < shop.items.Count; i++)
                {
                    if (shop.items[i] == null || string.IsNullOrEmpty(shop.items[i].name)) continue;
                    string item = shop.items[i].name.ToLowerInvariant();
                    if (item.Contains("maglietta") || item.Contains("jeans")) return "abbigliamento";
                    if (item.Contains("caffe") || item.Contains("cornetto")) return "bar";
                }
            }
            return "supermercato";
        }

        /// <summary>Invocato una sola volta da InteriorGenerator.BuildInterior
        /// dopo i builder esistenti. Se il piano terra non c'è (tipologie non
        /// commerciali) non fa nulla.</summary>
        public static void Decorate(Transform parent, string type, Shop shop,
            float w, float d, float floorH, int floors)
        {
            // BuildInterior chiama Decorate per OGNI edificio: case, scuole e
            // ospedali non devono trasformarsi in negozi.
            string t = (type ?? "").ToLowerInvariant();
            bool commercial = t == "shop" || t == "bar" || t == "dealer" ||
                              t == "clothes" || t == "hairdresser" ||
                              t == "dogwash" || t == "carwash" || t == "ticket" ||
                              shop != null;
            if (!commercial) return;

            Transform floor = FindChild(parent, "Floor_0");
            if (floor == null) return;
            if (FindChild(floor, "Negozio3D") != null) return; // idempotente

            string kind = Classify(type, shop);
            string key = StableKey(parent, shop, kind);
            ShopState.GetOrCreate(key, kind);

            var root = new GameObject("Negozio3D");
            root.transform.SetParent(floor, false);
            root.transform.localPosition = Vector3.zero;

            AddCounterProps(root.transform, w, d, kind);
            AddCashier(root.transform, w, d, kind);

            if (kind == "supermercato") LayoutSupermarket(root.transform, w, d);
            else if (kind == "bar") LayoutBar(root.transform, w, d);
            else if (kind == "abbigliamento") LayoutClothes(root.transform, w, d);
            else if (kind == "concessionaria") LayoutAuto(root.transform, w, d);
            else if (kind == "parruccheria") LayoutHairdresser(root.transform, w, d);
            else if (kind == "lavaggiocani") LayoutDogWash(root.transform, w, d);
            else if (kind == "biglietteria") LayoutTicket(root.transform, w, d);
            else if (kind == "autolavaggio") LayoutCarWash(root.transform, w, d);

            AddCustomers(root.transform, kind, w, d);

            var label = new GameObject("CartelloAttivita");
            label.transform.SetParent(root.transform, false);
            label.transform.localPosition = new Vector3(0f, 2.35f, -d * 0.12f);
            AddText(label.transform, ItalianName(kind) + " — " + ShopState.StatusLine(key, kind));

            var go = root.AddComponent<ShopActivityDisplay>();
            go.shopKey = key;
            go.shopKind = kind;
            go.label = label.GetComponent<TextMeshPro>();

            try
            {
                UnityBridge.LogToAndroid(Tag, "negozio 3D pronto: " + kind +
                    " (" + key + ") — " + ShopState.EggHint(kind));
            }
            catch (Exception) { }
        }

        /// <summary>Avanza la simulazione del negozio che il player sta
        /// visitando: il cartello inside l'interroot porta la chiave stabile,
        /// così non serve indovinare quale dei negozi lazy è quello attivo.
        /// Chiamato da InteriorManager solo quando IsInside è true.</summary>
        public static void TickActive(Transform interiorRoot, float deltaTime)
        {
            if (interiorRoot == null) return;
            ShopActivityDisplay[] displays =
                interiorRoot.GetComponentsInChildren<ShopActivityDisplay>(true);
            if (displays == null || displays.Length == 0) return;
            ShopState.Tick(deltaTime, true, displays[0].shopKey, displays[0].shopKind);
        }

        private static string StableKey(Transform parent, Shop shop, string kind)
        {
            // La posizione world cambia con il rebase di WorldOrigin: usare le
            // coordinate geografiche, esattamente come ContainerEgg.BuildingSeed,
            // così lo stato del negozio sopravvive a chunk ricaricati e rebase.
            string lat = "0", lng = "0";
            if (City.OSM.WorldOrigin.Initialized)
            {
                var g = City.OSM.WorldOrigin.ToGeo(parent.position);
                lat = g.lat.ToString("F5", System.Globalization.CultureInfo.InvariantCulture);
                lng = g.lng.ToString("F5", System.Globalization.CultureInfo.InvariantCulture);
            }
            string name = shop != null ? shop.shopName : kind;
            return "negozio@" + lat + "," + lng + "#" + kind + "#" +
                   (name ?? "").ToLowerInvariant();
        }

        // ── LAYOUT PER TIPOLOGIA ───────────────────────────────────

        private static void LayoutSupermarket(Transform root, float w, float d)
        {
            for (int i = 0; i < 3; i++)
            {
                float x = -w * 0.28f + i * w * 0.22f;
                Prop(root, "KenneyMiniMarket/shelf-bags", "ScaffaleSacchi" + i,
                    new Vector3(x, 0.9f, -d * 0.05f), new Vector3(0.6f, 1.8f, 3.2f), 0f,
                    new Color(0.65f, 0.44f, 0.26f));
                Prop(root, "KenneyMiniMarket/shelf-boxes", "ScaffaleScatole" + i,
                    new Vector3(x, 0.9f, d * 0.22f), new Vector3(0.6f, 1.8f, 2.6f), 180f,
                    new Color(0.72f, 0.52f, 0.30f));
            }
            Prop(root, "KenneyMiniMarket/freezer", "FrigoBanco",
                new Vector3(-w * 0.42f, 0.9f, -d * 0.25f), new Vector3(2.2f, 1.9f, 0.8f), 90f,
                new Color(0.70f, 0.82f, 0.88f));
            Prop(root, "KenneyMiniMarket/display-fruit", "DisplayFrutta",
                new Vector3(w * 0.36f, 0.55f, d * 0.26f), new Vector3(1.3f, 1.1f, 0.9f), 0f,
                new Color(0.85f, 0.42f, 0.28f));
            Prop(root, "KenneyMiniMarket/display-bread", "DisplayPane",
                new Vector3(-w * 0.36f, 0.55f, d * 0.28f), new Vector3(1.3f, 1.1f, 0.9f), 0f,
                new Color(0.80f, 0.62f, 0.36f));
            Prop(root, "KenneyMiniMarket/shopping-cart", "Carrello",
                new Vector3(w * 0.30f, 0.5f, d * 0.36f), new Vector3(0.7f, 1.0f, 1.1f), 35f,
                new Color(0.30f, 0.45f, 0.65f));
            for (int i = 0; i < 6; i++)
            {
                string[] cute = { "apple_green", "bottle1_water", "can_food_blue",
                                  "donut_red", "jam_purple", "bottle_glass_green" };
                Prop(root, "CuteStore/Prefabs/" + cute[i], "Prodotto" + i,
                    new Vector3(-w * 0.28f + i * w * 0.09f, 1.15f, -d * 0.05f),
                    new Vector3(0.16f, 0.26f, 0.16f), 0f, new Color(0.9f, 0.5f, 0.3f));
            }
        }

        private static void LayoutBar(Transform root, float w, float d)
        {
            Prop(root, "Furniture/kitchenCoffeeMachine", "MacchinaCaffe",
                new Vector3(w * 0.30f, 1.15f, -d * 0.30f), new Vector3(0.6f, 0.7f, 0.5f), 0f,
                new Color(0.32f, 0.30f, 0.28f));
            Prop(root, "Furniture/kitchenBlender", "Frullatore",
                new Vector3(w * 0.18f, 1.15f, -d * 0.30f), new Vector3(0.35f, 0.6f, 0.35f), 0f,
                new Color(0.75f, 0.75f, 0.78f));
            Prop(root, "KenneyMiniMarket/bottle-return", "Bottiglie",
                new Vector3(w * 0.40f, 1.0f, -d * 0.18f), new Vector3(0.7f, 1.2f, 0.5f), 0f,
                new Color(0.30f, 0.48f, 0.28f));
            for (int i = 0; i < 3; i++)
            {
                float z = -d * 0.15f + i * d * 0.28f;
                Prop(root, "Furniture/tableRound", "TavolinoBar" + i,
                    new Vector3(-w * 0.24f, 0.38f, z), new Vector3(0.9f, 0.75f, 0.9f), 0f,
                    new Color(0.55f, 0.36f, 0.20f));
                Prop(root, "Furniture/stoolBar", "SgabelloBar" + i,
                    new Vector3(-w * 0.24f + 0.7f, 0.35f, z), new Vector3(0.45f, 0.7f, 0.45f), 0f,
                    new Color(0.30f, 0.22f, 0.16f));
                Prop(root, "CuteStore/Prefabs/flask_coffee", "Caffe" + i,
                    new Vector3(-w * 0.24f, 0.82f, z), new Vector3(0.12f, 0.20f, 0.12f), 0f,
                    new Color(0.65f, 0.40f, 0.22f));
            }
        }

        private static void LayoutClothes(Transform root, float w, float d)
        {
            for (int i = 0; i < 4; i++)
            {
                float z = -d * 0.28f + i * d * 0.19f;
                Prop(root, "Furniture/coatRackStanding", "Appendino" + i,
                    new Vector3(-w * 0.36f, 0.9f, z), new Vector3(0.7f, 1.7f, 0.7f), 0f,
                    new Color(0.42f, 0.30f, 0.22f));
                Color[] colors =
                {
                    new Color(0.72f, 0.22f, 0.28f), new Color(0.22f, 0.38f, 0.68f),
                    new Color(0.28f, 0.58f, 0.38f), new Color(0.85f, 0.74f, 0.30f)
                };
                for (int c = 0; c < 3; c++)
                {
                    Box(root, "Capo" + i + "_" + c,
                        new Vector3(-w * 0.36f + (c - 1) * 0.18f, 1.25f, z),
                        new Vector3(0.15f, 0.55f, 0.08f), Mat(colors[(i + c) % 4]));
                }
            }
            Prop(root, "Furniture/bathroomMirror", "SpecchioCamerino",
                new Vector3(w * 0.40f, 1.3f, -d * 0.20f), new Vector3(1.0f, 1.9f, 0.15f), 90f,
                new Color(0.72f, 0.82f, 0.86f));
            Prop(root, "Furniture/table", "TavoloPieghi",
                new Vector3(w * 0.25f, 0.4f, d * 0.22f), new Vector3(1.5f, 0.8f, 0.8f), 0f,
                new Color(0.62f, 0.44f, 0.28f));
            Prop(root, "Furniture/cardboardBoxOpen", "DispensaAbbigliamento",
                new Vector3(w * 0.38f, 0.3f, d * 0.32f), new Vector3(0.7f, 0.6f, 0.6f), 0f,
                new Color(0.66f, 0.48f, 0.30f));
        }

        private static void LayoutAuto(Transform root, float w, float d)
        {
            string[] cars = { "sedan", "suv", "hatchback-sports" };
            for (int i = 0; i < cars.Length; i++)
            {
                float x = -w * 0.26f + i * w * 0.26f;
                Box(root, "Pedana" + i, new Vector3(x, 0.08f, d * 0.10f),
                    new Vector3(2.6f, 0.16f, 4.6f), Mat(new Color(0.82f, 0.82f, 0.80f)));
                Prop(root, "Vehicles/" + cars[i], "AutoEsposta" + i,
                    new Vector3(x, 0.18f, d * 0.10f), new Vector3(1.8f, 1.3f, 4.0f), 0f,
                    new Color(0.25f + i * 0.2f, 0.35f, 0.55f));
                var sign = new GameObject("CartellinoPrezzo" + i);
                sign.transform.SetParent(root, false);
                sign.transform.localPosition = new Vector3(x, 1.0f, d * 0.10f + 2.2f);
                AddText(sign.transform, "€ " + (9500 + i * 4300));
            }
            Prop(root, "Furniture/desk", "ScrivaniaVendite",
                new Vector3(-w * 0.32f, 0.4f, -d * 0.22f), new Vector3(1.4f, 0.8f, 0.7f), 0f,
                new Color(0.45f, 0.32f, 0.20f));
            Prop(root, "Furniture/computerScreen", "ComputerVendite",
                new Vector3(-w * 0.32f, 0.85f, -d * 0.24f), new Vector3(0.6f, 0.45f, 0.15f), 180f,
                new Color(0.12f, 0.14f, 0.18f));
        }

        private static void LayoutHairdresser(Transform root, float w, float d)
        {
            for (int i = 0; i < 3; i++)
            {
                float x = -w * 0.28f + i * w * 0.26f;
                Prop(root, "Furniture/chairModernCushion", "PoltronaSalone" + i,
                    new Vector3(x, 0.35f, d * 0.12f), new Vector3(0.9f, 0.7f, 0.9f), 180f,
                    new Color(0.35f, 0.22f, 0.40f));
                Prop(root, "Furniture/bathroomMirror", "SpecchioSalone" + i,
                    new Vector3(x, 1.3f, -d * 0.32f), new Vector3(1.1f, 2.0f, 0.12f), 0f,
                    new Color(0.75f, 0.85f, 0.90f));
                Prop(root, "Furniture/lampSquareTable", "LuceSpecchio" + i,
                    new Vector3(x, 1.9f, -d * 0.28f), new Vector3(0.5f, 0.4f, 0.3f), 0f,
                    new Color(1f, 0.94f, 0.76f));
                Prop(root, "Furniture/pillow", "Asciugamano" + i,
                    new Vector3(x + 0.5f, 0.9f, -d * 0.20f), new Vector3(0.35f, 0.12f, 0.45f), 0f,
                    new Color(0.92f, 0.94f, 0.96f));
            }
            Prop(root, "Furniture/bathroomSink", "Lavatesta",
                new Vector3(w * 0.34f, 0.55f, -d * 0.30f), new Vector3(0.9f, 1.1f, 0.6f), 90f,
                new Color(0.88f, 0.90f, 0.92f));
            Prop(root, "Furniture/credenza", "CredenzaNegozio",
                new Vector3(w * 0.36f, 0.45f, d * 0.25f), new Vector3(1.4f, 0.9f, 0.6f), 0f,
                new Color(0.58f, 0.40f, 0.26f));
        }

        private static void LayoutDogWash(Transform root, float w, float d)
        {
            Prop(root, "Furniture/bathtub", "VascaCani",
                new Vector3(-w * 0.20f, 0.45f, d * 0.05f), new Vector3(1.5f, 0.9f, 1.0f), 0f,
                new Color(0.85f, 0.88f, 0.90f));
            Prop(root, "Furniture/showerRound", "DocciaCani",
                new Vector3(-w * 0.20f, 1.2f, d * 0.05f), new Vector3(1.2f, 1.4f, 1.2f), 0f,
                new Color(0.72f, 0.82f, 0.88f));
            Prop(root, "Furniture/washer", "AsciugatriceCani",
                new Vector3(w * 0.30f, 0.55f, -d * 0.22f), new Vector3(0.9f, 1.1f, 0.8f), 0f,
                new Color(0.90f, 0.90f, 0.86f));
            Prop(root, "Furniture/rugRound", "TappetinoCani",
                new Vector3(w * 0.18f, 0.03f, d * 0.25f), new Vector3(1.2f, 0.06f, 1.2f), 0f,
                new Color(0.35f, 0.52f, 0.42f));
            Prop(root, "Pets/animal-dog", "CaneClientele",
                new Vector3(-w * 0.20f, 0.55f, d * 0.05f), new Vector3(0.5f, 0.6f, 0.8f), 25f,
                new Color(0.72f, 0.52f, 0.30f));
            Prop(root, "Furniture/cardboardBoxClosed", "DispensaCani",
                new Vector3(w * 0.38f, 0.3f, d * 0.30f), new Vector3(0.7f, 0.6f, 0.6f), 0f,
                new Color(0.66f, 0.48f, 0.30f));
        }

        private static void LayoutTicket(Transform root, float w, float d)
        {
            Prop(root, "KenneyMiniMarket/cash-register", "CassaBiglietti",
                new Vector3(0f, 1.05f, -d * 0.33f), new Vector3(0.7f, 0.5f, 0.6f), 0f,
                new Color(0.28f, 0.34f, 0.42f));
            Prop(root, "Furniture/computerScreen", "TotemOrari",
                new Vector3(-w * 0.35f, 0.85f, -d * 0.30f), new Vector3(0.7f, 1.3f, 0.15f), 0f,
                new Color(0.10f, 0.18f, 0.28f));
            Prop(root, "KenneyMiniMarket/column", "ColonnaStazione",
                new Vector3(w * 0.38f, 1.3f, -d * 0.25f), new Vector3(0.5f, 2.6f, 0.5f), 0f,
                new Color(0.65f, 0.65f, 0.62f));
            for (int i = 0; i < 2; i++)
            {
                float x = -1.1f + i * 2.2f;
                Box(root, "Tornello" + i, new Vector3(x, 0.55f, d * 0.18f),
                    new Vector3(0.16f, 1.1f, 0.9f), Mat(new Color(0.35f, 0.40f, 0.45f)));
                var sign = new GameObject("Tabellone" + i);
                sign.transform.SetParent(root, false);
                sign.transform.localPosition = new Vector3(x, 1.35f, d * 0.18f);
                AddText(sign.transform, "BINARIO " + (i + 1));
            }
            Prop(root, "Furniture/desk", "ScrivaniaBiglietteria",
                new Vector3(0f, 0.4f, -d * 0.38f), new Vector3(2.0f, 0.8f, 0.7f), 0f,
                new Color(0.48f, 0.36f, 0.24f));
            Prop(root, "Furniture/cabinetBedDrawer", "CassettoBiglietteria",
                new Vector3(w * 0.32f, 0.4f, d * 0.28f), new Vector3(1.0f, 0.8f, 0.6f), 0f,
                new Color(0.55f, 0.40f, 0.26f));
        }

        private static void LayoutCarWash(Transform root, float w, float d)
        {
            // Auto in sosta sul nastro, lavatrice/tappetini e scatola degli
            // asciugamani: nessun collider, la fisica resta quella del guscio.
            Box(root, "NastroLavaggio", new Vector3(0f, 0.06f, d * 0.08f),
                new Vector3(Mathf.Min(4.6f, w * 0.8f), 0.12f, Mathf.Min(6f, d * 0.8f)),
                Mat(new Color(0.42f, 0.46f, 0.50f)));
            Prop(root, "Vehicles/sedan", "AutoDaLavare",
                new Vector3(0f, 0.18f, d * 0.08f), new Vector3(1.8f, 1.3f, 4.0f), 0f,
                new Color(0.28f, 0.42f, 0.58f));
            Prop(root, "Furniture/washer", "ImpiantoLavaggio",
                new Vector3(-w * 0.38f, 0.55f, -d * 0.20f), new Vector3(0.9f, 1.1f, 0.8f), 0f,
                new Color(0.88f, 0.90f, 0.92f));
            for (int i = 0; i < 2; i++)
            {
                Prop(root, "Furniture/rugRound", "Tappetino" + i,
                    new Vector3(w * 0.32f, 0.03f, -d * 0.14f + i * 0.8f),
                    new Vector3(1.1f, 0.06f, 1.1f), 0f, new Color(0.32f, 0.50f, 0.42f));
            }
            Prop(root, "Furniture/cardboardBoxClosed", "DispensaAutolavaggio",
                new Vector3(w * 0.38f, 0.3f, d * 0.30f), new Vector3(0.7f, 0.6f, 0.6f), 0f,
                new Color(0.66f, 0.48f, 0.30f));
        }

        private static void AddCounterProps(Transform root, float w, float d,
            string kind)
        {
            Prop(root, "KenneyMiniMarket/cash-register", "RegistratoreCassa",
                new Vector3(0f, 1.05f, -d * 0.29f), new Vector3(0.8f, 0.55f, 0.6f), 0f,
                new Color(0.32f, 0.36f, 0.42f));
            Box(root, "CassettoCassa", new Vector3(0f, 0.45f, -d * 0.31f),
                new Vector3(0.7f, 0.22f, 0.5f), Mat(new Color(0.48f, 0.32f, 0.18f)));
            Box(root, "Dispensa", new Vector3(-w * 0.42f, 0.45f, -d * 0.10f),
                new Vector3(0.8f, 0.9f, 0.6f), Mat(new Color(0.58f, 0.42f, 0.26f)));
            if (kind == "bar" || kind == "parruccheria")
            {
                Box(root, "Credenza", new Vector3(w * 0.38f, 0.5f, -d * 0.10f),
                    new Vector3(1.2f, 1.0f, 0.6f), Mat(new Color(0.60f, 0.44f, 0.30f)));
            }
        }

        // ── PERSONAGGIO / ATTIVITÀ ──────────────────────────────────

        private static void AddCashier(Transform root, float w, float d, string kind)
        {
            GameObject go = SpawnCharacter(root, "Cassiera",
                new Vector3(0f, 0f, -d * 0.43f), 1.65f);
            var idle = Resources.Load<AnimationClip>("Characters/idle");
            if (idle != null && go != null)
            {
                try
                {
                    idle.legacy = true;
                    var anim = go.GetComponent<Animation>();
                    if (anim == null) anim = go.AddComponent<Animation>();
                    anim.AddClip(idle, "idle");
                    anim.wrapMode = WrapMode.Loop;
                    anim.Play("idle");
                }
                catch (Exception) { }
            }
            var cashier = go != null ? go.GetComponent<ShopCashier>() : null;
            if (cashier == null && go != null) cashier = go.AddComponent<ShopCashier>();
            if (cashier != null)
            {
                cashier.role = kind == "bar" ? "BARISTA" : "CASSIERA";
                cashier.baseY = go.transform.localPosition.y;
            }
            var tag = new GameObject("EtichettaCassiera");
            tag.transform.SetParent(root, false);
            tag.transform.localPosition = new Vector3(0f, 1.85f, -d * 0.43f);
            AddText(tag.transform, kind == "bar" ? "BARISTA" : "CASSIERA");
        }

        private static void AddCustomers(Transform root, string kind, float w, float d)
        {
            int count = kind == "concessionaria" ? 2 : 3;
            for (int i = 0; i < count; i++)
            {
                float x = -w * 0.20f + i * w * 0.20f;
                GameObject go = SpawnCharacter(root, "Cliente" + i,
                    new Vector3(x, 0f, d * 0.15f), 1.55f);
                if (go == null) continue;
                var c = go.AddComponent<ShopCustomer>();
                c.fromZ = d * 0.12f;
                c.toZ = -d * 0.28f;
                c.speed = 0.35f + i * 0.08f;
                c.phase = i * 1.7f;
            }
        }

        private static GameObject SpawnCharacter(Transform parent, string name,
            Vector3 pos, float height)
        {
            GameObject prefab = Resources.Load<GameObject>("Characters/characterMedium");
            GameObject go = prefab != null ? (GameObject)UnityEngine.Object.Instantiate(prefab, parent, false)
                                           : GameObject.CreatePrimitive(PrimitiveType.Capsule);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localRotation = Quaternion.identity;

            Bounds b = new Bounds(go.transform.position, Vector3.zero);
            Renderer[] renderers = go.GetComponentsInChildren<Renderer>();
            if (renderers != null && renderers.Length > 0)
            {
                b = renderers[0].bounds;
                for (int i = 1; i < renderers.Length; i++) b.Encapsulate(renderers[i].bounds);
            }
            float h = b.size.y > 0.01f ? b.size.y : 1f;
            go.transform.localScale = Vector3.one * (height / h);
            foreach (var col in go.GetComponentsInChildren<Collider>())
                UnityEngine.Object.Destroy(col);
            return go;
        }

        // ── HELPER GEOMETRICI (nessun collider sui mobili) ──────────

        private static void Prop(Transform parent, string resourcePath, string name,
            Vector3 pos, Vector3 size, float yaw, Color fallback)
        {
            GameObject prefab = Resources.Load<GameObject>(resourcePath);
            if (prefab == null)
            {
                Box(parent, name, pos, size, Mat(fallback));
                return;
            }
            GameObject inst = (GameObject)UnityEngine.Object.Instantiate(prefab, parent, false);
            inst.name = name;
            inst.transform.SetParent(parent, false);
            inst.transform.localPosition = pos;
            inst.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            inst.transform.localScale = Vector3.one;

            Renderer[] renderers = inst.GetComponentsInChildren<Renderer>();
            if (renderers != null && renderers.Length > 0)
            {
                Bounds b = renderers[0].bounds;
                for (int i = 1; i < renderers.Length; i++) b.Encapsulate(renderers[i].bounds);
                Vector3 s = b.size;
                if (s.x > 0.001f && s.y > 0.001f && s.z > 0.001f)
                    inst.transform.localScale = new Vector3(size.x / s.x, size.y / s.y, size.z / s.z);
            }
            else inst.transform.localScale = size;

            foreach (var col in inst.GetComponentsInChildren<Collider>())
                UnityEngine.Object.Destroy(col);
        }

        private static void Box(Transform parent, string name, Vector3 pos,
            Vector3 size, Material mat)
        {
            GameObject go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = size;
            if (mat != null) go.GetComponent<Renderer>().sharedMaterial = mat;
            var col = go.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.Destroy(col);
        }

        private static Material Mat(Color c)
        {
            string key = c.r.ToString("F2") + "_" + c.g.ToString("F2") + "_" + c.b.ToString("F2");
            Material m;
            if (Materials.TryGetValue(key, out m) && m != null) return m;
            Shader shader = Shader.Find("Universal Render Pipeline/Lit");
            if (shader == null) shader = Shader.Find("Standard");
            if (shader == null) return null;
            m = new Material(shader);
            if (shader.name.Contains("Universal")) m.SetColor("_BaseColor", c);
            else m.SetColor("_Color", c);
            m.SetFloat("_Smoothness", 0.25f);
            Materials[key] = m;
            return m;
        }

        private static void AddText(Transform parent, string text)
        {
            GameObject go = new GameObject("Testo_" + text);
            go.transform.SetParent(parent, false);
            go.transform.localRotation = Quaternion.identity;
            TextMeshPro tmp = go.AddComponent<TextMeshPro>();
            tmp.font = TMP_Settings.defaultFontAsset;
            tmp.text = text;
            tmp.fontSize = 1.4f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            tmp.enableWordWrapping = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
        }

        private static Transform FindChild(Transform root, string name)
        {
            if (root == null) return null;
            Transform[] all = root.GetComponentsInChildren<Transform>(true);
            for (int i = 0; i < all.Length; i++)
                if (all[i] != null && all[i].name == name) return all[i];
            return null;
        }
    }

    /// <summary>Cassiera ferma dietro il bancone, con leggero bobbing: nessun
    /// collider e nessun NavMeshAgent, così non interferisce con la fisica.</summary>
    public sealed class ShopCashier : MonoBehaviour
    {
        public string role = "CASSIERA";
        public float baseY;

        private void Update()
        {
            Vector3 p = transform.localPosition;
            p.y = baseY + Mathf.Sin(Time.time * 1.6f) * 0.015f;
            transform.localPosition = p;
        }
    }

    /// <summary>Cliente che passeggia tra scaffali e cassa, senza fisica.</summary>
    public sealed class ShopCustomer : MonoBehaviour
    {
        public float fromZ;
        public float toZ;
        public float speed = 0.4f;
        public float phase;

        private void Update()
        {
            float t = (Mathf.Sin(Time.time * speed + phase) + 1f) * 0.5f;
            Vector3 p = transform.localPosition;
            p.z = Mathf.Lerp(fromZ, toZ, t);
            transform.localPosition = p;
            transform.localRotation = Quaternion.Euler(0f, t > 0.5f ? 180f : 0f, 0f);
        }
    }

    /// <summary>Aggiorna il cartello del negozio con incasso/clienti correnti.</summary>
    public sealed class ShopActivityDisplay : MonoBehaviour
    {
        public string shopKey = "";
        public string shopKind = "";
        public TextMeshPro label;

        private float _timer;

        private void Update()
        {
            _timer += Time.deltaTime;
            if (_timer < 2f) return;
            _timer = 0f;
            if (label != null)
                label.text = ShopInteriorBuilder.ItalianName(shopKind) + " — " +
                             ShopState.StatusLine(shopKey, shopKind);
        }
    }
}
