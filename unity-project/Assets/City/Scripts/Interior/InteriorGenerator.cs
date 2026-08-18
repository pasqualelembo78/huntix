using System.Collections.Generic;
using UnityEngine;
using City.World;
using TMPro;

namespace City.Interior
{
    /// <summary>
    /// Genera interni 3D procedurali per edifici della città.
    /// Tipi supportati: casa, negozio, palazzo.
    /// Usa modelli Kenney Furniture Kit caricati da Resources, altrimenti primitives.
    /// </summary>
    public class InteriorGenerator : MonoBehaviour
    {
        private const float WALL_THICK = 0.2f;

        private Dictionary<string, GameObject> _furnitureMap;
        private bool _furnitureLoaded;

        private static readonly Dictionary<string, string> FBX_NAME = new Dictionary<string, string>
        {
            ["Divano"] = "loungeSofa", ["DivanoA"] = "loungeSofa", ["DivanoB"] = "loungeSofa",
            ["Tavolino"] = "tableCoffee", ["TavoloA"] = "tableCoffee", ["TavoloB"] = "tableCoffee",
            ["TV"] = "televisionModern", ["TVA"] = "televisionModern", ["TVB"] = "televisionModern",
            ["Lampada"] = "lampRoundFloor",
            ["Frigo"] = "kitchenFridge", ["Forno"] = "kitchenStove", ["Lavello"] = "kitchenSink",
            ["Tavolo"] = "table", ["TavoloUff"] = "desk",
            ["Sedia1"] = "chair", ["Sedia2"] = "chair", ["SediaScriv"] = "chairDesk", ["SediaUff"] = "chair",
            ["LettoMat"] = "bedDouble", ["LettoA"] = "bedDouble", ["LettoB"] = "bedDouble",
            ["LettoSing"] = "bedSingle",
            ["Comodino1"] = "sideTable", ["LampCom1"] = "lampRoundTable",
            ["Armadio1"] = "bookcaseClosed", ["ArmadioA"] = "bookcaseClosed", ["ArmadioB"] = "bookcaseClosed",
            ["Scrivania"] = "desk", ["Libreria"] = "bookcaseOpen",
            ["WC"] = "toilet", ["WCA"] = "toilet", ["WCB"] = "toilet",
            ["Lavandino"] = "bathroomSink", ["LavA"] = "bathroomSink", ["LavB"] = "bathroomSink",
            ["Bancone"] = "kitchenBar", ["Panca"] = "bench",
            ["PC"] = "laptop",
        };

        // Materiali (colori semplici, nessuna dipendenza esterna)
        private Material _wallMat;
        private Material _floorMat;
        private Material _ceilingMat;
        private Material _woodMat;
        private Material _darkMat;
        private Material _glassMat;
        private Material _tileMat;
        private Material _counterMat;
        private Material _shelfMat;
        private Material _bedMat;
        private Material _sofaMat;
        private Material _rugMat;
        private Material _doorMat;

        private void EnsureMaterials()
        {
            if (_wallMat != null) return;
            _wallMat = Lit(new Color(0.92f, 0.88f, 0.82f));
            _floorMat = Lit(new Color(0.55f, 0.42f, 0.30f));
            _ceilingMat = Lit(new Color(0.88f, 0.85f, 0.80f));
            _woodMat = Lit(new Color(0.55f, 0.35f, 0.18f));
            _darkMat = Lit(new Color(0.22f, 0.22f, 0.22f));
            _glassMat = Lit(new Color(0.6f, 0.75f, 0.85f, 0.5f));
            _tileMat = Lit(new Color(0.85f, 0.85f, 0.82f));
            _counterMat = Lit(new Color(0.7f, 0.5f, 0.3f));
            _shelfMat = Lit(new Color(0.6f, 0.4f, 0.25f));
            _bedMat = Lit(new Color(0.9f, 0.88f, 0.8f));
            _sofaMat = Lit(new Color(0.5f, 0.35f, 0.25f));
            _rugMat = Lit(new Color(0.65f, 0.25f, 0.2f));
            _doorMat = Lit(new Color(0.18f, 0.14f, 0.10f));
        }

        private Material Lit(Color c)
        {
            var m = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            if (m.shader == null) m = new Material(Shader.Find("Standard"));
            m.SetColor("_BaseColor", c);
            m.SetFloat("_Smoothness", 0.3f);
            return m;
        }

        /// <summary>
        /// Costruisce l'intero interno come figli di parent.
        /// Ogni piano è un GameObject "Floor_N" attivabile/disattivabile.
        /// </summary>
        public void BuildInterior(Transform parent, string type,
            float extW, float extD, float extH, int floors, Shop shop)
        {
            EnsureMaterials();

            float w = Mathf.Clamp(extW, 4f, 14f);
            float d = Mathf.Clamp(extD, 4f, 14f);
            float floorH = 3f;

            switch (type)
            {
                case "shop":
                    BuildShop(parent, w, d, floorH, floors, shop);
                    break;
                case "apartment":
                    BuildApartment(parent, w, d, floorH, floors, shop);
                    break;
                default:
                    BuildHouse(parent, w, d, floorH, floors, shop);
                    break;
            }
        }

        // ── Stair position (used by InteriorManager for floor transitions) ──

        public Vector3 GetStairPosition(Transform interiorRoot, int floor)
        {
            Transform floorObj = interiorRoot.Find("Floor_" + floor);
            if (floorObj == null) return new Vector3(0f, 500f + 0.1f, 0f);

            Transform stair = floorObj.Find("Scala");
            if (stair != null)
            {
                return new Vector3(stair.position.x, 500f + 0.1f + floor * 3f, stair.position.z);
            }

            return new Vector3(1f, 500f + 0.1f + floor * 3f, -1f);
        }

        // ── HOUSE ──────────────────────────────────────────────────

        private void BuildHouse(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 3);

            for (int f = 0; f < totalFloors; f++)
            {
                float yBase = 500f + f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yBase, 0f),
                    new Vector3(w, 0.15f, d), _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yBase + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yBase, true);

                if (f == 0)
                {
                    // ── Piano terra: sogorno + cucina + bagno ──

                    // Parete divisoria sogorno/cucina (a metà depth)
                    float divZ = -d * 0.1f;
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yBase + floorH * 0.5f, divZ),
                        new Vector3(w * 0.9f, floorH, WALL_THICK), _wallMat);

                    // Porta tra sogorno e cucina
                    Box(floorGo.transform, "PortaInt", new Vector3(w * 0.2f, yBase + 1.1f, divZ),
                        new Vector3(1.0f, 2.2f, WALL_THICK + 0.05f), _doorMat);

                    // ── Sogorno (fronte) ──
                    float sogornoZ = d * 0.25f;

                    // Divano
                    Furniture(floorGo.transform, "Divano", new Vector3(-w * 0.25f, yBase + 0.4f, sogornoZ + 0.3f),
                        new Vector3(2.5f, 0.8f, 1.0f));

                    // Tavolino
                    Furniture(floorGo.transform, "Tavolino", new Vector3(0f, yBase + 0.35f, sogornoZ),
                        new Vector3(1.0f, 0.3f, 0.6f));

                    // TV (parete laterale)
                    Furniture(floorGo.transform, "TV", new Vector3(w * 0.45f, yBase + 1.3f, sogornoZ),
                        new Vector3(0.6f, 0.6f, 1.0f));

                    // Lampada
                    Furniture(floorGo.transform, "Lampada", new Vector3(-w * 0.4f, yBase + 1.0f, sogornoZ),
                        new Vector3(0.5f, 1.0f, 0.5f));

                    // ── Cucina (retro) ──
                    float cucinaZ = -d * 0.3f;

                    // Frigo
                    Furniture(floorGo.transform, "Frigo", new Vector3(-w * 0.4f, yBase + 0.9f, cucinaZ),
                        new Vector3(0.8f, 1.8f, 0.7f));

                    // Piano cottura
                    Furniture(floorGo.transform, "Forno", new Vector3(0f, yBase + 0.45f, cucinaZ - d * 0.15f),
                        new Vector3(1.2f, 0.9f, 0.6f));

                    // Lavello
                    Furniture(floorGo.transform, "Lavello", new Vector3(w * 0.3f, yBase + 0.45f, cucinaZ - d * 0.15f),
                        new Vector3(0.8f, 0.5f, 0.5f));

                    // Tavolo da pranzo
                    Furniture(floorGo.transform, "Tavolo", new Vector3(w * 0.15f, yBase + 0.38f, cucinaZ + 0.5f),
                        new Vector3(1.4f, 0.7f, 0.8f));

                    // Sedie (2)
                    Furniture(floorGo.transform, "Sedia1", new Vector3(w * 0.15f - 0.5f, yBase + 0.22f, cucinaZ + 0.5f + 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));
                    Furniture(floorGo.transform, "Sedia2", new Vector3(w * 0.15f + 0.5f, yBase + 0.22f, cucinaZ + 0.5f - 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // ── Scala (angolo) ──
                    BuildStairs(floorGo.transform, w * 0.35f, yBase, floorH, d * 0.35f);

                    // ── Bagno (angolo opposto) ──
                    float bagnoX = -w * 0.35f;
                    float bagnoZ = -d * 0.35f;

                    // Parete bagno
                    Box(floorGo.transform, "PareteBagno1", new Vector3(bagnoX + 0.8f, yBase + floorH * 0.5f, bagnoZ + 0.6f),
                        new Vector3(WALL_THICK, floorH, 1.2f), _wallMat);
                    Box(floorGo.transform, "PareteBagno2", new Vector3(bagnoX, yBase + floorH * 0.5f, bagnoZ + 1.2f),
                        new Vector3(1.6f, floorH, WALL_THICK), _wallMat);

                    // Pavimento bagno (piastrelle)
                    Box(floorGo.transform, "PavBagno", new Vector3(bagnoX, yBase + 0.08f, bagnoZ + 0.6f),
                        new Vector3(1.4f, 0.08f, 1.0f), _tileMat);

                    // WC
                    Furniture(floorGo.transform, "WC", new Vector3(bagnoX - 0.3f, yBase + 0.25f, bagnoZ + 0.3f),
                        new Vector3(0.4f, 0.5f, 0.5f));

                    // Lavandino
                    Furniture(floorGo.transform, "Lavandino", new Vector3(bagnoX + 0.3f, yBase + 0.4f, bagnoZ + 0.3f),
                        new Vector3(0.5f, 0.5f, 0.4f));

                    // ── Porta d'ingresso (sul muro frontale) ──
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yBase + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.2f, 2.2f, 0.15f), _doorMat);

                    // Etichetta "USCITA"
                    AddLabel(floorGo.transform, "USCITA", new Vector3(0f, yBase + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(0.8f, 0.3f, 0.02f));

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yBase);
                }
                else
                {
                    // ── Piani superiori: 2 camere ──

                    // Parete divisoria
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yBase + floorH * 0.5f, 0f),
                        new Vector3(w * 0.9f, floorH, WALL_THICK), _wallMat);

                    // Porta camera 1
                    Box(floorGo.transform, "Porta1", new Vector3(w * 0.2f, yBase + 1.1f, 0f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Porta camera 2
                    Box(floorGo.transform, "Porta2", new Vector3(-w * 0.2f, yBase + 1.1f, 0f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // ── Camera 1 (fronte) ──
                    float cam1Z = d * 0.25f;

                    // Letto matrimoniale
                    Furniture(floorGo.transform, "LettoMat", new Vector3(-w * 0.15f, yBase + 0.3f, cam1Z),
                        new Vector3(1.8f, 0.6f, 2.0f));

                    // Comodino
                    Furniture(floorGo.transform, "Comodino1", new Vector3(-w * 0.4f, yBase + 0.35f, cam1Z + 0.5f),
                        new Vector3(0.4f, 0.5f, 0.35f));

                    // Lampada comodino
                    Furniture(floorGo.transform, "LampCom1", new Vector3(-w * 0.4f, yBase + 0.7f, cam1Z + 0.5f),
                        new Vector3(0.3f, 0.4f, 0.3f));

                    // Armadio
                    Furniture(floorGo.transform, "Armadio1", new Vector3(w * 0.4f, yBase + 0.9f, cam1Z),
                        new Vector3(1.0f, 1.8f, 0.5f));

                    // ── Camera 2 (retro) ──
                    float cam2Z = -d * 0.25f;

                    // Letto singolo
                    Furniture(floorGo.transform, "LettoSing", new Vector3(w * 0.15f, yBase + 0.25f, cam2Z),
                        new Vector3(1.0f, 0.5f, 2.0f));

                    // Scrivania
                    Furniture(floorGo.transform, "Scrivania", new Vector3(-w * 0.3f, yBase + 0.38f, cam2Z - 0.3f),
                        new Vector3(1.2f, 0.7f, 0.6f));

                    // Sedia scrivania
                    Furniture(floorGo.transform, "SediaScriv", new Vector3(-w * 0.3f, yBase + 0.22f, cam2Z + 0.3f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // Libreria
                    Furniture(floorGo.transform, "Libreria", new Vector3(-w * 0.4f, yBase + 0.9f, cam2Z + 0.4f),
                        new Vector3(0.8f, 1.8f, 0.35f));

                    // Scala (stessa posizione del piano terra)
                    BuildStairs(floorGo.transform, w * 0.35f, yBase, floorH, d * 0.35f);

                    // Trigger scale (su/giù)
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yBase, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── SHOP ───────────────────────────────────────────────────

        private void BuildShop(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 1, 3);
            float yBase = 500f;

            for (int f = 0; f < totalFloors; f++)
            {
                float yf = yBase + f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yf, 0f),
                    new Vector3(w, 0.15f, d), _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yf + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yf, true);

                if (f == 0)
                {
                    // ── Piano terra: sala vendita + bancone ──

                    // Bancone cassa (retro)
                    Furniture(floorGo.transform, "Bancone", new Vector3(0f, yf + 0.5f, -d * 0.35f),
                        new Vector3(w * 0.6f, 1.0f, 0.7f));

                    // Scaffali (lungo le pareti laterali)
                    int numShelves = Mathf.Max(2, Mathf.FloorToInt(d / 3f));
                    float shelfSpacing = d * 0.6f / numShelves;
                    for (int s = 0; s < numShelves; s++)
                    {
                        float sz = -d * 0.1f + s * shelfSpacing;
                        Box(floorGo.transform, "ScaffaleS" + s,
                            new Vector3(-w * 0.38f, yf + 0.9f, sz),
                            new Vector3(0.5f, 1.8f, 0.3f), _shelfMat);

                        // Prodotti sugli scaffali (cubetti colorati)
                        for (int p = 0; p < 3; p++)
                        {
                            float px = -w * 0.38f + (p - 1) * 0.12f;
                            float py = yf + 0.6f + p * 0.5f;
                            Material prodMat = Lit(new Color(
                                0.3f + (s * 0.15f + p * 0.1f) % 0.7f,
                                0.5f + (s * 0.1f) % 0.4f,
                                0.4f + (p * 0.2f) % 0.5f));
                            Box(floorGo.transform, "Prodotto" + s + "_" + p,
                                new Vector3(px, py, sz),
                                new Vector3(0.18f, 0.25f, 0.15f), prodMat);
                        }
                    }

                    // Vetrina (fronte, parete trasparente)
                    Box(floorGo.transform, "Vetrina", new Vector3(0f, yf + 1.5f, d * 0.49f),
                        new Vector3(w * 0.7f, 1.8f, 0.05f), _glassMat);

                    // Porta d'ingresso
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yf + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.2f, 2.2f, 0.15f), _doorMat);

                    // Etichetta nome negozio
                    string shopName = shop != null ? shop.shopName : "NEGOZIO";
                    AddLabel(floorGo.transform, shopName, new Vector3(0f, yf + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(Mathf.Max(1.5f, shopName.Length * 0.25f), 0.35f, 0.02f));

                    // Se c'è un shop, crea un counter per aprire lo shop UI
                    if (shop != null)
                    {
                        var counterGo = new GameObject("ShopCounter");
                        counterGo.transform.SetParent(floorGo.transform, false);
                        counterGo.transform.localPosition = new Vector3(0f, yf + 0.5f, -d * 0.15f);
                        var col = counterGo.AddComponent<BoxCollider>();
                        col.isTrigger = true;
                        col.size = new Vector3(2.5f, 2f, 1.5f);
                        var shopTrigger = counterGo.AddComponent<ShopCounterTrigger>();
                        shopTrigger.shop = shop;
                    }

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yf);

                    // Scala se ci sono piani superiori
                    if (totalFloors > 1)
                    {
                        BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                        BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, 0, totalFloors);
                    }
                }
                else
                {
                    // ── Piani superiori: magazzino / ufficio ──

                    // Scaffali magazzino
                    for (int s = 0; s < 4; s++)
                    {
                        float sz = -d * 0.3f + s * d * 0.2f;
                        Box(floorGo.transform, "MagScaffale" + s,
                            new Vector3(-w * 0.35f, yf + 0.9f, sz),
                            new Vector3(0.6f, 1.8f, 0.4f), _shelfMat);
                    }

                    // Tavolo ufficio
                    Furniture(floorGo.transform, "TavoloUff", new Vector3(w * 0.2f, yf + 0.38f, 0f),
                        new Vector3(1.2f, 0.7f, 0.7f));

                    // Sedia
                    Furniture(floorGo.transform, "SediaUff", new Vector3(w * 0.2f, yf + 0.22f, 0.6f),
                        new Vector3(0.5f, 0.44f, 0.5f));

                    // Computer
                    Furniture(floorGo.transform, "PC", new Vector3(w * 0.2f, yf + 0.5f, -0.15f),
                        new Vector3(0.5f, 0.35f, 0.4f));

                    // Scala
                    BuildStairs(floorGo.transform, w * 0.35f, yf, floorH, d * 0.35f);
                    BuildStairTrigger(floorGo.transform, w * 0.35f, yf, d * 0.35f, f, totalFloors);
                }
            }
        }

        // ── APARTMENT ──────────────────────────────────────────────

        private void BuildApartment(Transform parent, float w, float d, float floorH, int floors, Shop shop)
        {
            int totalFloors = Mathf.Clamp(floors, 2, 5);
            float yBase = 500f;

            for (int f = 0; f < totalFloors; f++)
            {
                float yf = yBase + f * floorH;
                var floorGo = new GameObject("Floor_" + f);
                floorGo.transform.SetParent(parent, false);
                floorGo.transform.localPosition = new Vector3(0f, f * floorH, 0f);

                // Pavimento
                Box(floorGo.transform, "Pavimento", new Vector3(0f, yf, 0f),
                    new Vector3(w, 0.15f, d), f == 0 ? _tileMat : _floorMat);

                // Soffitto
                if (f < totalFloors - 1)
                {
                    Box(floorGo.transform, "Soffitto", new Vector3(0f, yf + floorH, 0f),
                        new Vector3(w, 0.1f, d), _ceilingMat);
                }

                // Muri perimetrali
                BuildWalls(floorGo.transform, w, d, floorH, yf, true);

                // Scala al centro
                BuildStairs(floorGo.transform, 0f, yf, floorH, d * 0.2f);

                if (f == 0)
                {
                    // ── Piano terra: ingresso + portineria ──

                    // Parete interna (separa ingresso da scala)
                    Box(floorGo.transform, "PareteInterna", new Vector3(-w * 0.15f, yf + floorH * 0.5f, 0f),
                        new Vector3(WALL_THICK, floorH, d * 0.6f), _wallMat);

                    // Porta scala
                    Box(floorGo.transform, "PortaScala", new Vector3(-w * 0.15f, yf + 1.1f, d * 0.15f),
                        new Vector3(WALL_THICK + 0.05f, 2.2f, 1.0f), _doorMat);

                    // Cassetta postale
                    Box(floorGo.transform, "Poste", new Vector3(w * 0.35f, yf + 1.2f, -d * 0.35f),
                        new Vector3(0.8f, 1.2f, 0.2f), _woodMat);

                    // Panca
                    Furniture(floorGo.transform, "Panca", new Vector3(w * 0.35f, yf + 0.22f, -d * 0.1f),
                        new Vector3(1.2f, 0.44f, 0.4f));

                    // Porta d'ingresso
                    Box(floorGo.transform, "PortaIngresso", new Vector3(0f, yf + 1.1f, d * 0.5f + 0.05f),
                        new Vector3(1.4f, 2.4f, 0.15f), _doorMat);

                    // Etichetta
                    AddLabel(floorGo.transform, "INGRESSO", new Vector3(0f, yf + 2.8f, d * 0.5f + 0.1f),
                        new Vector3(1.2f, 0.3f, 0.02f));

                    // Trigger scale (solo su)
                    BuildStairTrigger(floorGo.transform, 0f, yf, d * 0.2f, 0, totalFloors);

                    // Trigger uscita
                    BuildExitTrigger(floorGo.transform, w, d, yf);
                }
                else
                {
                    // ── Piani superiori: 2 appartamenti per piano ──

                    // Parete divisoria centrale (separa appartamento A da B)
                    Box(floorGo.transform, "PareteDiv", new Vector3(0f, yf + floorH * 0.5f, 0f),
                        new Vector3(WALL_THICK, floorH, d * 0.9f), _wallMat);

                    // ── Appartamento A (destro) ──
                    float aptW = w * 0.45f;
                    float aptX = w * 0.25f;

                    // Porta ingresso A
                    Box(floorGo.transform, "PortaA", new Vector3(aptX, yf + 1.1f, d * 0.15f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Sogorno A
                    Furniture(floorGo.transform, "DivanoA", new Vector3(aptX, yf + 0.35f, -d * 0.15f),
                        new Vector3(1.8f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "TavoloA", new Vector3(aptX, yf + 0.32f, -d * 0.05f),
                        new Vector3(0.8f, 0.4f, 0.5f));
                    Furniture(floorGo.transform, "TVA", new Vector3(aptX + aptW * 0.4f, yf + 1.3f, -d * 0.15f),
                        new Vector3(0.5f, 0.5f, 0.8f));

                    // Camera A
                    Furniture(floorGo.transform, "LettoA", new Vector3(aptX, yf + 0.28f, d * 0.3f),
                        new Vector3(1.5f, 0.55f, 1.8f));
                    Furniture(floorGo.transform, "ArmadioA", new Vector3(aptX + aptW * 0.35f, yf + 0.9f, d * 0.3f),
                        new Vector3(0.8f, 1.8f, 0.4f));

                    // Bagno A
                    Furniture(floorGo.transform, "WCA", new Vector3(aptX + aptW * 0.35f, yf + 0.25f, -d * 0.35f),
                        new Vector3(0.4f, 0.5f, 0.45f));
                    Furniture(floorGo.transform, "LavA", new Vector3(aptX + aptW * 0.15f, yf + 0.4f, -d * 0.35f),
                        new Vector3(0.45f, 0.5f, 0.35f));

                    // ── Appartamento B (sinistro) ──
                    float aptBX = -w * 0.25f;

                    // Porta ingresso B
                    Box(floorGo.transform, "PortaB", new Vector3(aptBX, yf + 1.1f, d * 0.15f),
                        new Vector3(0.9f, 2.0f, WALL_THICK + 0.05f), _doorMat);

                    // Sogorno B
                    Furniture(floorGo.transform, "DivanoB", new Vector3(aptBX, yf + 0.35f, -d * 0.15f),
                        new Vector3(1.8f, 0.7f, 0.8f));
                    Furniture(floorGo.transform, "TavoloB", new Vector3(aptBX, yf + 0.32f, -d * 0.05f),
                        new Vector3(0.8f, 0.4f, 0.5f));

                    // Camera B
                    Furniture(floorGo.transform, "LettoB", new Vector3(aptBX, yf + 0.28f, d * 0.3f),
                        new Vector3(1.5f, 0.55f, 1.8f));
                    Furniture(floorGo.transform, "ArmadioB", new Vector3(aptBX - aptW * 0.35f, yf + 0.9f, d * 0.3f),
                        new Vector3(0.8f, 1.8f, 0.4f));

                    // Bagno B
                    Furniture(floorGo.transform, "WCB", new Vector3(aptBX - aptW * 0.35f, yf + 0.25f, -d * 0.35f),
                        new Vector3(0.4f, 0.5f, 0.45f));

                    // Trigger scale (su e giù)
                    BuildStairTrigger(floorGo.transform, 0f, yf, d * 0.2f, f, totalFloors);
                }
            }
        }

        // ── MURI PERIMETRALI ───────────────────────────────────────

        private void BuildWalls(Transform floorGo, float w, float d, float floorH, float yBase, bool withDoorGap)
        {
            float ht = floorH * 0.5f;
            float wallY = yBase + ht;
            float halfW = w * 0.5f;
            float halfD = d * 0.5f;
            float thick = 0.2f;

            // Parete posteriore (nord)
            Box(floorGo, "MuroN", new Vector3(0f, wallY, -halfD),
                new Vector3(w, floorH, thick), _wallMat);

            // Parete sinistra
            Box(floorGo, "MuroO", new Vector3(-halfW, wallY, 0f),
                new Vector3(thick, floorH, d), _wallMat);

            // Parete destra
            Box(floorGo, "MuroE", new Vector3(halfW, wallY, 0f),
                new Vector3(thick, floorH, d), _wallMat);

            // Parete frontale (sud) con finestre
            if (withDoorGap)
            {
                // Lato sinistro del muro frontale
                Box(floorGo, "MuroS1", new Vector3(-halfW * 0.55f, wallY, halfD),
                    new Vector3(halfW * 0.85f, floorH, thick), _wallMat);

                // Lato destro del muro frontale
                Box(floorGo, "MuroS2", new Vector3(halfW * 0.55f, wallY, halfD),
                    new Vector3(halfW * 0.85f, floorH, thick), _wallMat);

                // Finestra sopra la porta
                Box(floorGo, "Finestra", new Vector3(0f, yBase + floorH * 0.75f, halfD),
                    new Vector3(w * 0.3f, floorH * 0.35f, 0.05f), _glassMat);
            }
            else
            {
                Box(floorGo, "MuroS", new Vector3(0f, wallY, halfD),
                    new Vector3(w, floorH, thick), _wallMat);
            }
        }

        // ── SCALE ──────────────────────────────────────────────────

        private void BuildStairs(Transform floorGo, float x, float yBase, float floorH, float z)
        {
            int numSteps = 10;
            float stepH = floorH / numSteps;
            float stepD = 0.3f;
            float stairW = 0.9f;

            for (int i = 0; i < numSteps; i++)
            {
                float sy = yBase + stepH * (i + 0.5f);
                float sz = z - (i * stepD * 0.5f) + numSteps * stepD * 0.25f;

                Box(floorGo, "Step_" + i, new Vector3(x, sy, sz),
                    new Vector3(stairW, stepH, stepD), _woodMat);
            }

            // Balaustre
            float railH = 0.9f;
            float railY = yBase + floorH * 0.5f;
            Box(floorGo, "Balaustreira", new Vector3(x + stairW * 0.5f, railY, z),
                new Vector3(0.06f, railH, numSteps * stepD * 0.5f), _woodMat);
        }

        // ── TRIGGER SCALE ──────────────────────────────────────────

        private void BuildStairTrigger(Transform floorGo, float x, float yBase, float z,
            int currentFloor, int totalFloors)
        {
            var triggerGo = new GameObject("StairTrigger");
            triggerGo.transform.SetParent(floorGo.transform, false);
            triggerGo.transform.localPosition = new Vector3(x, yBase + 1f, z);

            var col = triggerGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(1.5f, 2.5f, 1.5f);

            var st = triggerGo.AddComponent<StairTrigger>();
            st.currentFloor = currentFloor;
            st.totalFloors = totalFloors;
        }

        // ── TRIGGER USCITA ─────────────────────────────────────────

        private void BuildExitTrigger(Transform floorGo, float w, float d, float yBase)
        {
            var exitGo = new GameObject("ExitTrigger");
            exitGo.transform.SetParent(floorGo.transform, false);
            exitGo.transform.localPosition = new Vector3(0f, yBase + 1f, d * 0.5f + 0.5f);

            var col = exitGo.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(2f, 2.5f, 1f);

            exitGo.AddComponent<ExitTrigger>();
        }

        // ── HELPER ──────────────────────────────────────────────────

        private void Box(Transform parent, string name, Vector3 center, Vector3 scale, Material mat)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = center;
            go.transform.localScale = scale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            // Rimuovi collider dai mobili (solo le pareti esterne e il pavimento hanno collider)
            if (name != "Pavimento" && name != "Soffitto"
                && !name.StartsWith("Muro") && !name.StartsWith("Parete"))
            {
                var c = go.GetComponent<Collider>();
                if (c != null) Destroy(c);
            }
        }

        private void Furniture(Transform parent, string name, Vector3 center, Vector3 scale)
        {
            EnsureFurniture();

            string fbxKey = name;
            if (FBX_NAME.ContainsKey(name)) fbxKey = FBX_NAME[name];

            GameObject prefab;
            _furnitureMap.TryGetValue(fbxKey, out prefab);
            if (prefab != null)
            {
                var inst = Instantiate(prefab, parent, false);
                inst.name = name;
                inst.transform.localPosition = center;
                inst.transform.localScale = Vector3.one;

                var renderers = inst.GetComponentsInChildren<Renderer>();
                if (renderers.Length > 0)
                {
                    Bounds combined = renderers[0].bounds;
                    for (int i = 1; i < renderers.Length; i++)
                        combined.Encapsulate(renderers[i].bounds);
                    Vector3 sz = combined.size;
                    if (sz.x > 0.001f && sz.y > 0.001f && sz.z > 0.001f)
                    {
                        inst.transform.localScale = new Vector3(
                            scale.x / sz.x, scale.y / sz.y, scale.z / sz.z);
                    }
                }
                else
                {
                    inst.transform.localScale = scale;
                }

                foreach (var col in inst.GetComponentsInChildren<Collider>())
                    Destroy(col);
            }
            else
            {
                Material fallback = _woodMat != null ? _woodMat : Lit(new Color(0.55f, 0.35f, 0.18f));
                Box(parent, name, center, scale, fallback);
            }
        }

        private void EnsureFurniture()
        {
            if (_furnitureLoaded) return;
            _furnitureLoaded = true;
            _furnitureMap = new Dictionary<string, GameObject>();

            // Collect unique FBX keys from the mapping
            var keys = new HashSet<string>();
            foreach (var kv in FBX_NAME)
                keys.Add(kv.Value);

            foreach (var key in keys)
            {
                var go = Resources.Load<GameObject>("Furniture/" + key);
                if (go != null)
                    _furnitureMap[key] = go;
            }

            Debug.Log("[InteriorGenerator] Loaded " + _furnitureMap.Count + " furniture models from Resources");
        }

        private void AddLabel(Transform parent, string text, Vector3 pos, Vector3 scale)
        {
            var labelGo = new GameObject("Label_" + text);
            labelGo.transform.SetParent(parent, false);
            labelGo.transform.localPosition = pos;
            labelGo.transform.localRotation = Quaternion.identity;

            var tmp = labelGo.AddComponent<TextMeshPro>();
            tmp.font = TMP_Settings.defaultFontAsset;
            tmp.text = text;
            tmp.fontSize = 2.0f;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.color = Color.white;
            tmp.outlineWidth = 0.1f;
            tmp.outlineColor = new Color(0f, 0f, 0f, 0.6f);
            tmp.enableWordWrapping = false;
            tmp.overflowMode = TextOverflowModes.Overflow;
            tmp.raycastTarget = false;
            tmp.rectTransform.sizeDelta = new Vector2(scale.x * 10f, scale.y * 10f);
        }
    }
}
