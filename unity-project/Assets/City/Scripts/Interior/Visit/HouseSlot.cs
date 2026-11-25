using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.Interior.Visit
{
    /// <summary>
    /// Dato serializzato di uno slot del prefab `CasaVisitabileCC0`. La posizione
    /// è NORMALIZZATA sull'impronta OSM (-0,5 .. +0,5), così lo stesso prefab si
    /// adatta a case di dimensioni diverse senza deformare muri o arredi.
    /// `size` sono metri reali: il modello CC0 viene riscalato sullo slot al
    /// momento del build. Nessun collider sui mobili: la navigazione resta
    /// quella del guscio e delle paratie.
    /// </summary>
    [Serializable]
    public sealed class HouseSlot
    {
        public string model = "";
        public string sceneName = "";
        public Vector2 position = Vector2.zero;
        public Vector3 size = Vector3.one;
        public float yaw;
        public string room = "";
        public bool container;

        // Riflesso uova (Mandato): nascondiglio con luogo testuale stabile.
        public string featureId = "";
        public string eggLabel = "";
        public string place = "";
    }

    /// <summary>Componente unico sulla radice del prefab: contiene il layout.
    /// Un solo MonoBehaviour per prefab evita decine di componenti e rende il
    /// layout modificabile dall'editor senza rigenerare codice.</summary>
    public sealed class HouseSlots : MonoBehaviour
    {
        public List<HouseSlot> slots = new List<HouseSlot>();

        /// <summary>Layout di default: soggiorno, cucina, camera e bagno.
        /// Usato sia per creare il prefab in editor sia come fallback runtime
        /// se il prefab non è ancora stato generato (build vecchie).</summary>
        public static List<HouseSlot> DefaultLayout()
        {
            var s = new List<HouseSlot>();

            // ── SOGGIORNO (x>0, z>0) ────────────────────────────────
            Add(s, "SofaThreeSeat", "DivanoSoggiorno", 0.29f, 0.20f, 2.4f, 1.0f, 0.9f, 180f, "soggiorno");
            Add(s, "CoffeeTable", "TavolinoSoggiorno", 0.29f, 0.33f, 1.1f, 0.45f, 0.6f, 0f, "soggiorno");
            Add(s, "TVUnit", "MobileTV", 0.40f, 0.40f, 1.8f, 0.5f, 0.45f, 180f, "soggiorno");
            Add(s, "Television", "Televisore", 0.40f, 0.40f, 1.2f, 0.8f, 0.12f, 0f, "soggiorno");
            Add(s, "Armchair", "PoltronaSoggiorno", 0.14f, 0.29f, 0.9f, 1.0f, 0.8f, 90f, "soggiorno");
            Add(s, "FloorLamp", "LampadaSoggiorno", 0.44f, 0.16f, 0.5f, 1.7f, 0.5f, 0f, "soggiorno");
            Add(s, "Bookcase", "LibreriaSoggiorno", 0.16f, 0.43f, 1.0f, 1.8f, 0.35f, 0f, "soggiorno");
            Add(s, "LivingRug", "TappetoSoggiorno", 0.29f, 0.27f, 2.6f, 0.03f, 1.7f, 0f, "soggiorno");
            Add(s, "PlantLarge", "PiantaSoggiorno", 0.45f, 0.45f, 0.7f, 0.9f, 0.7f, 0f, "soggiorno");

            // ── CUCINA (x>0, z<0) ───────────────────────────────────
            Add(s, "DiningTable", "TavoloCucina", 0.27f, -0.12f, 1.6f, 0.8f, 0.9f, 0f, "cucina");
            Add(s, "DiningChair", "SediaCucina1", 0.27f, -0.23f, 0.5f, 0.9f, 0.5f, 0f, "cucina");
            Add(s, "DiningChair", "SediaCucina2", 0.27f, -0.01f, 0.5f, 0.9f, 0.5f, 180f, "cucina");
            Add(s, "BaseCabinet", "CredenzaCucina", 0.42f, -0.34f, 1.6f, 0.9f, 0.6f, 0f, "cucina");
            Add(s, "DrawerBase", "Cassetto2Cucina", 0.30f, -0.42f, 1.0f, 0.8f, 0.6f, 0f, "cucina",
                true, "casa-cucina-cassetto2", "Uovo nel cassetto della cucina",
                "casa→cucina→cassetto2");
            Add(s, "WallCabinet", "PensileCucina", 0.45f, -0.27f, 1.2f, 0.8f, 0.4f, 0f, "cucina");
            Add(s, "SinkUnit", "LavelloCucina", 0.38f, -0.20f, 1.0f, 0.9f, 0.6f, 0f, "cucina");
            Add(s, "Oven", "FornoCucina", 0.45f, -0.15f, 0.7f, 0.9f, 0.6f, 0f, "cucina");
            Add(s, "Hob", "PianoCottura", 0.34f, -0.17f, 0.8f, 0.1f, 0.6f, 0f, "cucina");
            Add(s, "Fridge", "FrigoCucina", 0.46f, -0.43f, 0.9f, 1.9f, 0.8f, 0f, "cucina");
            Add(s, "KitchenIsland", "IsolaCucina", 0.18f, -0.33f, 1.6f, 0.9f, 0.9f, 0f, "cucina");

            // ── CAMERA (x<0, z<0) ───────────────────────────────────
            Add(s, "DoubleBed", "LettoCamera", -0.26f, -0.25f, 2.0f, 0.6f, 2.0f, 0f, "camera");
            Add(s, "BedsideTable", "ComodinoCamera", -0.40f, -0.16f, 0.5f, 0.6f, 0.45f, 0f, "camera");
            Add(s, "Wardrobe", "ArmadioCamera", -0.41f, -0.39f, 1.6f, 2.1f, 0.6f, 0f, "camera",
                true, "casa-camera-armadio", "Uovo nell'armadio della camera",
                "casa→camera→armadio");
            Add(s, "Dresser", "CassettoCamera", -0.17f, -0.42f, 1.2f, 0.9f, 0.5f, 0f, "camera");
            Add(s, "BedroomRug", "TappetoCamera", -0.26f, -0.10f, 2.2f, 0.03f, 1.6f, 0f, "camera");

            // ── BAGNO (x<0, z>0) ────────────────────────────────────
            Add(s, "Bath", "VascaBagno", -0.38f, 0.35f, 1.7f, 0.6f, 0.8f, 0f, "bagno");
            Add(s, "ShowerCubicle", "DocciaBagno", -0.43f, 0.12f, 0.9f, 2.0f, 0.9f, 0f, "bagno");
            Add(s, "Toilet", "WCBagno", -0.21f, 0.38f, 0.5f, 0.8f, 0.6f, 0f, "bagno");
            Add(s, "BasinUnit", "LavandinoBagno", -0.32f, 0.45f, 0.8f, 0.9f, 0.5f, 0f, "bagno",
                true, "casa-bagno-lavandino", "Uovo nel lavandino del bagno",
                "casa→bagno→lavandino");
            Add(s, "TowelRail", "PortaAsciugamani", -0.45f, 0.42f, 0.6f, 0.8f, 0.1f, 0f, "bagno");

            return s;
        }

        private static void Add(List<HouseSlot> list, string model, string sceneName,
            float x, float z, float sx, float sy, float sz, float yaw, string room,
            bool container = false, string featureId = "", string eggLabel = "", string place = "")
        {
            var slot = new HouseSlot();
            slot.model = model;
            slot.sceneName = sceneName;
            slot.position = new Vector2(x, z);
            slot.size = new Vector3(sx, sy, sz);
            slot.yaw = yaw;
            slot.room = room;
            slot.container = container;
            slot.featureId = featureId;
            slot.eggLabel = eggLabel;
            slot.place = place;
            list.Add(slot);
        }
    }
}
