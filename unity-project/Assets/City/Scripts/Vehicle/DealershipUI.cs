using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.World;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Pannello della concessionaria: elenco del catalogo veicoli con
    /// prezzi e statistiche. E' l'UNICO posto dove comprare un'auto;
    /// la vendita dell'auto propria avviene qui al 60% avvicinandosi
    /// all'auto con l'interazione (o guidandoci dentro).
    /// L'auto comprata viene consegnata sul piazzale e ci si sale subito.
    /// </summary>
    public class DealershipUI : MonoBehaviour
    {
        public static DealershipUI Instance { get; private set; }

        private GameObject panel;
        private TMP_Text titleText;
        private RectTransform listContent;
        private VehiclePoiZone zone;
        private TMP_FontAsset font;

        // contatore anti-collisione dei codici di acquisto (vedi TryPurchase)
        private static int purchaseSeq;

        // debounce: evita doppio tap su "COMPRA E RITIRA"
        private bool buyPending;

        private static readonly Color PanelBg = new Color(0.09f, 0.11f, 0.16f, 0.97f);
        private static readonly Color RowBg = new Color(0.20f, 0.22f, 0.26f, 1f);
        private static readonly Color BuyColor = new Color(0.15f, 0.65f, 0.45f, 1f);
        private static readonly Color ButtonBg = new Color(0.28f, 0.30f, 0.34f, 1f);
        private static readonly Color OwnedColor = new Color(0.3f, 0.3f, 0.3f, 0.8f);

        public static void Open(VehiclePoiZone poiZone)
        {
            if (Instance == null)
            {
                var go = new GameObject("DealershipUI");
                DontDestroyOnLoad(go);
                Instance = go.AddComponent<DealershipUI>();
            }
            Instance.zone = poiZone;
            Instance.Show();
        }

        public void Close()
        {
            if (panel != null) panel.SetActive(false);
            zone = null;
            Time.timeScale = 1f;
        }

        private void Show()
        {
            if (panel == null) BuildPanel();
            titleText.text = "CONCESSIONARIA" +
                (zone != null && !string.IsNullOrEmpty(zone.poiName)
                    ? " - " + zone.poiName : "");
            RebuildList();
            panel.SetActive(true);
            Time.timeScale = 0f;
        }

        private void RebuildList()
        {
            foreach (Transform child in listContent) Destroy(child.gameObject);

            // sezione vendita: l'auto in possesso si vende SOLO qui (60%)
            var api = VehicleOwnershipApi.Ensure();
            api.GetMyVehicle(my =>
            {
                if (my != null && panel != null && panel.activeSelf)
                    MakeSellRow(api, my);
            });

            var defs = VehicleSpawnManager.Catalogue;
            foreach (var def in defs)
            {
                MakeCarRow(def);
            }
            MakeButton("Chiudi", Close, ButtonBg);
        }

        /// <summary>Riga di vendita dell'auto propria al 60% del prezzo di
        /// listino. Se l'auto e' rubata o ricoverata non e' presente qui:
        /// niente vendita finche' non torna fisicamente in concessionaria.</summary>
        private void MakeSellRow(VehicleOwnershipApi api, VehicleOwnershipApi.MyVehicle my)
        {
            if (listContent == null) return;

            var row = MakeRect("Sell_" + my.code, listContent,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -8f), new Vector2(-14f, -40f));
            row.sizeDelta = new Vector2(0f, 118f);
            row.pivot = new Vector2(0.5f, 1f);
            row.gameObject.AddComponent<Image>().color =
                new Color(0.45f, 0.28f, 0.10f, 0.95f);

            string status;
            bool sellable = !my.stolen && !my.in_garage && !my.found_abandoned;
            if (my.stolen) status = "RUBATA! Nessuna vendita.";
            else if (my.in_garage) status = "Ricoverata nel garage.";
            else if (my.found_abandoned) status = "Abbandonata: recuperala prima.";
            else status = "Condizione " + my.condition.ToString("F0") + "%";

            MakeText(row, "LA TUA AUTO: " + my.model, 26f, Color.white,
                TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -8f), new Vector2(-14f, -40f));
            MakeText(row, status, 22f, new Color(0.85f, 0.8f, 0.7f),
                TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -42f), new Vector2(-14f, -70f));

            int offer = Mathf.Max(1, Mathf.RoundToInt(my.price * 0.6f));
            var btnRt = MakeRect("Vendi", row, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -74f), new Vector2(-14f, -110f));
            var btnImg = btnRt.gameObject.AddComponent<Image>();
            btnImg.color = sellable ? BuyColor : OwnedColor;
            var btn = btnRt.gameObject.AddComponent<Button>();
            btn.targetGraphic = btnImg;
            string code = my.code;
            btn.onClick.AddListener(() =>
            {
                if (!sellable) { Toast(status); return; }
                if (Game.Instance != null && Game.Instance.IsDriving)
                {
                    Toast("Scendi dall'auto prima di venderla!");
                    return;
                }
                api.Sell(code, (ok, err) =>
                {
                    if (!ok) { Toast("Vendita rifiutata: " + (err ?? "?")); return; }
                    Inventory.Remove("vehicle_" + code);
                    Wallet.Earn(offer);
                    api.MarkSold(code);
                    DestroySceneVehicle(code);
                    Toast("Auto venduta a \u20ac" + offer);
                    RebuildList();
                });
            });
            MakeText(btnRt, "VENDI PER \u20ac" + offer + " (60%)", 24f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
        }

        /// <summary>Rimuove dalla scena il veicolo fisico appena venduto
        /// (se presente): il popolatore non lo fara' piu' comparire.</summary>
        private static void DestroySceneVehicle(string code)
        {
            foreach (var vi in FindObjectsOfType<VehicleInteract>())
            {
                if (vi.vehicleCode != code) continue;
                var root = vi.transform.parent != null
                    ? vi.transform.parent.gameObject : vi.gameObject;
                if (root.scene.IsValid()) Destroy(root);
            }
        }

        private void MakeCarRow(VehicleSpawnManager.VehicleDef def)
        {
            var row = MakeRect("Car_" + def.name, listContent,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 118f);
            row.anchorMin = new Vector2(0f, 1f);
            row.anchorMax = new Vector2(1f, 1f);
            row.pivot = new Vector2(0.5f, 1f);
            var img = row.gameObject.AddComponent<Image>();
            img.color = RowBg;

            MakeText(row, def.name, 28f, Color.white, TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -8f), new Vector2(-14f, -40f));
            MakeText(row,
                "Vel max " + (def.maxSpeed * 3.6f).ToString("F0") +
                " km/h   Acc " + def.accel.ToString("F0") +
                "   \u20ac" + def.price,
                22f, new Color(0.72f, 0.75f, 0.78f), TextAlignmentOptions.Left,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -44f), new Vector2(-14f, -74f));

            bool canBuy = Wallet.CanAfford(def.price);
            var btnRt = MakeRect("Buy", row, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(14f, -76f), new Vector2(-14f, -110f));
            var btnImg = btnRt.gameObject.AddComponent<Image>();
            btnImg.color = canBuy ? BuyColor : OwnedColor;
            var btn = btnRt.gameObject.AddComponent<Button>();
            btn.targetGraphic = btnImg;
            btn.onClick.AddListener(() => TryPurchase(def));
            MakeText(btnRt, canBuy ? "COMPRA E RITIRA" : "SOLDI INSUFFICIENTI",
                24f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        private void TryPurchase(VehicleSpawnManager.VehicleDef def)
        {
            if (buyPending) return;
            if (zone == null || zone.deliveryPoint == null) return;
            if (!Wallet.CanAfford(def.price))
            {
                Toast("Soldi insufficienti");
                return;
            }
            buyPending = true;

            // codice univoco generato dal client: il registro server rende
            // comunque esclusivo il possesso (stesso modello di /buy esistente)
            // codice univoco generato dal client: prima usava il solo timestamp
            // in secondi in HEX -> due acquisti nello stesso secondo producevano
            // lo STESSO codice (possessi ambigui sul server). Ora millisecondi
            // + contatore di sessione: univoco per acquisto.
            string code;
            {
                int seq = System.Threading.Interlocked.Increment(ref purchaseSeq);
                code = "D" + zone.poiId + "_" +
                    System.DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                        .ToString("X") + seq.ToString("X");
            }

            // Trova il nodo stradale piu' vicino alla concessionaria per
            // far spawnare l'auto SULLA STRADA (non nel piazzale).
            var roadNet = City.Vehicle.Traffic.TileRoadNetwork.Instance;
            Vector3 spawnPos;
            float spawnHeading;
            if (roadNet != null && roadNet.Graph != null && roadNet.Graph.nodes.Count > 0)
            {
                int nodeId = roadNet.Graph.FindClosestNode(zone.transform.position);
                var node = roadNet.Graph.nodeMap[nodeId];
                spawnPos = node.position;
                // orienta l'auto lungo l'arco uscente (o entrante se non ce n'e')
                int arcId = node.arcIds.Count > 0 ? node.arcIds[0] : -1;
                if (arcId >= 0 && roadNet.Graph.arcMap.TryGetValue(arcId, out var arc)
                    && arc.waypoints.Length >= 2)
                {
                    Vector3 dir = arc.waypoints[1] - arc.waypoints[0];
                    spawnHeading = Mathf.Atan2(dir.x, dir.z) * Mathf.Rad2Deg;
                }
                else
                {
                    spawnHeading = zone.transform.eulerAngles.y;
                }
                // usiamo il deliveryPoint solo come riferimento per l'offset Y
                // (il nodo stradale e' gia' a quota terra)
            }
            else
            {
                // fallback: usa il deliveryPoint
                spawnPos = zone.deliveryPoint != null ? zone.deliveryPoint.position : zone.transform.position;
                spawnHeading = zone.deliveryPoint != null ? zone.deliveryPoint.eulerAngles.y : zone.transform.eulerAngles.y;
            }

            GeoCoord g = WorldOrigin.ToGeo(spawnPos);
            float heading = spawnHeading;

            var api = VehicleOwnershipApi.Ensure();
            api.Buy(code, (ok, err) =>
            {
                if (!ok)
                {
                    buyPending = false;
                    Toast("Acquisto rifiuto dal server: " + (err ?? "?"));
                    return;
                }
                Wallet.Spend(def.price);
                Inventory.Add("vehicle_" + code);
                api.MarkOwned(code, g.lat, g.lng, heading);
                api.SetLocalState(code, def.name, def.price);

                // consegna: l'auto nasce SULLA STRADA davanti alla concessionaria
                var delivered = VehicleSpawnManager.BuildVehicle(
                    null, def, spawnPos, heading, code);
                api.ApplyOwnedState(delivered, code);
                // Blocca il doppione: il popolatore a chunk salta i veicoli
                // gia' materializzati, ma la consegna non lo registrava ->
                // copia doppia sul piazzale al rebuild del chunk.
                VehicleSpawnManager.RegisterActiveOwned(code, delivered);

                // Indicatore visivo: segnalino arancione lampeggiante sopra l'auto
                // cosi' il giocatore vede subito dove e' finita la macchina.
                if (delivered != null)
                {
                    SpawnDeliveryMarker(delivered.transform, def.name);
                }

                buyPending = false;
                Close();

                // freccia rossa della bussola verso l'auto appena consegnata:
                var vi = delivered != null
                    ? delivered.GetComponentInChildren<VehicleInteract>() : null;
                if (vi != null) vi.NotifyStateChanged();

                if (delivered != null)
                {
                    GeoCoord cg = WorldOrigin.ToGeo(delivered.transform.position);
                    NavigationState.Set(
                        def.name + " (la tua auto)", "", cg.lat, cg.lng);
                }

                Toast(def.name +
                    " \u00e8 tua! Segui la freccia rossa sul piazzale.");
            });
        }

        private void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        /// <summary>Crea un indicatore visivo (sfera arancione pulsante)
        /// sopra il veicolo appena consegnato, per aiutare il giocatore
        /// a trovarlo sulla strada. Dura 15 secondi.</summary>
        private void SpawnDeliveryMarker(Transform vehicleRoot, string vehicleName)
        {
            var marker = new GameObject("DeliveryMarker_" + vehicleName);
            marker.transform.SetParent(vehicleRoot, false);
            marker.transform.localPosition = new Vector3(0f, 3.5f, 0f);

            // Sfera arancione come segnalino (solo API stubbed)
            var sphere = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            sphere.transform.SetParent(marker.transform, false);
            sphere.transform.localScale = Vector3.one * 0.6f;
            var mr = sphere.GetComponent<MeshRenderer>();
            if (mr != null)
            {
                var mat = new Material(Shader.Find("Sprites/Default"));
                mat.color = new Color(1f, 0.55f, 0f, 0.9f); // arancione
                mr.material = mat;
            }
            // Rimuovi collider della primitiva
            var col = sphere.GetComponent<Collider>();
            if (col != null) Destroy(col);

            // Animazione: pulsa e ruota
            var anim = marker.AddComponent<DeliveryMarkerAnimator>();
            anim.vehicleName = vehicleName;

            // Auto-distruzione dopo 15 secondi
            Destroy(marker, 15f);
        }

        // ── costruzione pannello runtime ───────────────────────────

        private void BuildPanel()
        {
            font = TMP_Settings.defaultFontAsset;
            if (font == null) font = Resources.Load<TMP_FontAsset>(
                "Fonts & Materials/LiberationSans SDF");

            Canvas canvas = null;
            foreach (var c in FindObjectsOfType<Canvas>())
            {
                if (c.renderMode != RenderMode.ScreenSpaceOverlay) continue;
                if (c.GetComponent<GraphicRaycaster>() == null) continue;
                if (canvas == null || c.sortingOrder > canvas.sortingOrder)
                    canvas = c;
            }
            if (canvas == null)
            {
                Debug.LogError("[DealershipUI] nessuna Canvas nella scena");
                return;
            }
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem");
                esGo.AddComponent<EventSystem>();
                esGo.AddComponent<StandaloneInputModule>();
            }

            panel = new GameObject("DealershipPanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = new Vector2(0.5f, 0.5f);
            prt.anchorMax = new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-270f, -330f);
            prt.offsetMax = new Vector2(270f, 330f);
            var bg = panel.AddComponent<Image>();
            bg.color = PanelBg;

            titleText = MakeText(prt, "", 34f, Color.white,
                TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(18f, -12f), new Vector2(-18f, -56f));

            var scrollRt = MakeRect("Scroll", prt, new Vector2(0f, 0f),
                new Vector2(1f, 1f), new Vector2(10f, 60f), new Vector2(-10f, -6f));
            var sr = scrollRt.gameObject.AddComponent<ScrollRect>();
            scrollRt.gameObject.AddComponent<Mask>().showMaskGraphic = true;
            scrollRt.gameObject.AddComponent<Image>().color =
                new Color(0f, 0f, 0f, 0.15f);

            listContent = MakeRect("Content", scrollRt, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            listContent.pivot = new Vector2(0.5f, 1f);
            var vlg = listContent.gameObject.AddComponent<VerticalLayoutGroup>();
            vlg.childControlWidth = true;
            vlg.childControlHeight = false;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            vlg.spacing = 6f;
            vlg.padding = new RectOffset(6, 6, 6, 6);
            listContent.gameObject.AddComponent<ContentSizeFitter>()
                .verticalFit = ContentSizeFitter.FitMode.PreferredSize;
            sr.content = listContent;
            sr.viewport = scrollRt;
            sr.vertical = true;
            sr.horizontal = false;

            panel.SetActive(false);
        }

        private void MakeButton(string label, UnityEngine.Events.UnityAction onClick, Color bgColor)
        {
            var rt = MakeRect("Btn", listContent, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 52f);
            rt.anchorMin = new Vector2(0f, 1f);
            rt.anchorMax = new Vector2(1f, 1f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bgColor;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(onClick);
            MakeText(rt, label, 26f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        private RectTransform MakeRect(string name, Transform parent,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject(name, typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        private TMP_Text MakeText(RectTransform parent, string content, float size,
            Color color, TextAlignmentOptions alignment, Vector2 anchorMin,
            Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect("T", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var text = rt.gameObject.AddComponent<TextMeshProUGUI>();
            text.text = content;
            text.fontSize = size;
            text.color = color;
            text.alignment = alignment;
            text.font = font;
            text.raycastTarget = false;
            return text;
        }

    /// <summary>Animatore per il marker di consegna: pulsa, ruota,
    /// e mostra il nome del veicolo via click.</summary>
    private class DeliveryMarkerAnimator : MonoBehaviour
    {
        public string vehicleName;
        private float _pulseT;

        private void Update()
        {
            _pulseT += Time.unscaledDeltaTime * 3f;
            float scale = 1f + Mathf.Sin(_pulseT) * 0.15f;
            transform.localScale = Vector3.one * scale;
            transform.Rotate(0f, Time.unscaledDeltaTime * 60f, 0f);
        }

        private void OnMouseDown()
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(
                    "La tua " + vehicleName + " e' qui!");
        }
    }
}
}
