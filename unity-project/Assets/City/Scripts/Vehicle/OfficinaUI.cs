using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.World;
using City.Vehicle.Mechanics;

namespace City.Vehicle
{
    /// <summary>
    /// Pannello dell'officina: riparazione completa (costo proporzionale ai
    /// danni e al prezzo del mezzo) e installazione antifurti. Ogni
    /// dispositivo riduce la probabilita' di furto; si possono avere tutti.
    /// </summary>
    public class OfficinaUI : MonoBehaviour
    {
        public static OfficinaUI Instance { get; private set; }

        private GameObject panel;
        private TMP_Text titleText;
        private RectTransform listContent;
        private VehiclePoiZone zone;
        private TMP_FontAsset font;

        // mirror dei prezzi server (service/catalog): tenuti sincronizzati
        public static readonly string[] DeviceIds = { "engine", "shaft", "wheels", "pedals" };
        public static readonly string[] DeviceNames = {
            "Blocco motore", "Block shaft", "Blocco ruote", "Blocco pedali" };
        public static readonly int[] DevicePrices = { 120, 90, 70, 50 };
        public static readonly float[] DeviceMults = { 0.65f, 0.75f, 0.80f, 0.85f };

        public const float RepairPriceFactor = 0.004f;

        private static readonly Color PanelBg = new Color(0.14f, 0.10f, 0.07f, 0.97f);
        private static readonly Color RowBg = new Color(0.24f, 0.20f, 0.16f, 1f);
        private static readonly Color BuyColor = new Color(0.15f, 0.65f, 0.45f, 1f);
        private static readonly Color RepairColor = new Color(0.95f, 0.55f, 0.10f, 1f);
        private static readonly Color ButtonBg = new Color(0.32f, 0.30f, 0.28f, 1f);
        private static readonly Color OwnedColor = new Color(0.3f, 0.3f, 0.3f, 0.8f);

        public static void Open(VehiclePoiZone poiZone)
        {
            if (Instance == null)
            {
                var go = new GameObject("OfficinaUI");
                DontDestroyOnLoad(go);
                Instance = go.AddComponent<OfficinaUI>();
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
            titleText.text = "OFFICINA" +
                (zone != null && !string.IsNullOrEmpty(zone.poiName)
                    ? " - " + zone.poiName : "");
            RebuildList();
            panel.SetActive(true);
            Time.timeScale = 0f;
        }

        private void RebuildList()
        {
            foreach (Transform child in listContent) Destroy(child.gameObject);

            var api = VehicleOwnershipApi.Ensure();
            api.GetMyVehicle(my =>
            {
                if (this == null || panel == null || !panel.activeSelf) return;
                if (my == null)
                {
                    MakeNote("Non possedi nessun veicolo.");
                    MakeButton("Chiudi", Close, ButtonBg);
                    return;
                }

                float integrity = my.integrity;
                if (integrity <= 0f) integrity = 0f;

                // ── stato del veicolo ──
                MakeRow("Veicolo", my.model ?? my.code);
                MakeRow("Condizione", my.condition.ToString("F1") + "%");
                MakeRow("Stato", DamageCaption(my.damage));
                MakeRow("HP / integrità", Mathf.RoundToInt(
                    Mathf.Clamp(integrity, 0f, 100f)) + "%");
                MakeRow("Km percorsi",
                    (my.odometer_m / 1000f).ToString("F1") + " km");
                if (my.damage == "fire")
                    MakeNote("AUTO IN FIAMME: chiama prima i vigili del fuoco (fuori dall'officina).");
                else if (my.damage == "wrecked")
                    MakeNote("Auto devastata: riparazione disponibile.");

                // ── riparazione per zona ──
                float suspension = ClampZone(my.suspension);
                float bodywork = ClampZone(my.bodywork);
                float bumper = ClampZone(my.bumper);
                float totalZone = Mathf.Clamp(suspension + bodywork + bumper, 0f, 100f);

                if (my.damage != "fire" && totalZone > 0.05f)
                {
                    MakeNote("DANNI PER ZONA — ripara solo la parte danneggiata:");
                    ZoneRepairRow("Sospensioni (gomme)", "suspension",
                        suspension, my.code, my.price);
                    ZoneRepairRow("Carrozzeria", "bodywork",
                        bodywork, my.code, my.price);
                    ZoneRepairRow("Fascia / paraurti", "bumper",
                        bumper, my.code, my.price);
                }

                // ── diagnostica fine: single parti meccaniche ──
                LocalPartRepairSection(my.code);

                bool needsRepair = my.condition < 100f - 0.05f ||
                                   totalZone > 0.05f;
                // la riparazione completa azzera usura (condizione) E danni
                // da impatto (zone = HP mancante)
                int missingHp = (int)(100f - Mathf.Clamp(integrity, 0f, 100f));
                int completeCost = Mathf.RoundToInt(
                    (Mathf.Max(0f, 100f - my.condition) + missingHp)
                    * RepairPriceFactor * my.price);
                bool canPayFull = Wallet.CanAfford(completeCost);
                var rrt = MakeActionRow(needsRepair
                    ? (canPayFull ? "RIPARA TUTTO - \u20ac" + completeCost
                                  : "RIPARAZIONE TOTALE: \u20ac" + completeCost + " (soldi insufficienti)")
                    : "VEICOLO IN PERFETTE CONDIZIONI",
                    needsRepair && canPayFull ? RepairColor : OwnedColor,
                    () =>
                    {
                        if (!needsRepair || !canPayFull) return;
                        api.Repair(my.code, ok =>
                        {
                            if (ok)
                            {
                                Wallet.Spend(completeCost);
                                VehicleOwnershipApi.Ensure().MarkRepaired(
                                    my.code);
                                RescueDirector.SetLocalDamage(my.code,
                                    VehicleDamage.None);
                                // ripara anche le parti meccaniche locali
                                if (VehicleOwnershipApi.Instance != null &&
                                    VehicleOwnershipApi.Instance.LocalPlayerVehicle != null)
                                    VehicleOwnershipApi.Instance.LocalPlayerVehicle
                                        .RepairAllParts();
                                Toast("Veicolo riparato!");
                                Show();   // ricarica lo stato
                            }
                            else Toast("Riparazione non riuscita");
                        });
                    });

                // ── antifurti ──
                MakeNote("ANTIFURTI — ogni dispositivo riduce il rischio furto:");
                for (int i = 0; i < DeviceIds.Length; i++)
                {
                    string id = DeviceIds[i];
                    string name = DeviceNames[i];
                    int price = DevicePrices[i];
                    float mult = DeviceMults[i];
                    bool installed = my.anti_theft != null &&
                        System.Array.IndexOf(my.anti_theft, id) >= 0;

                    int reduction = Mathf.RoundToInt((1f - mult) * 100f);
                    MakeActionRow(
                        installed ? name + "  [OK] INSTALLATO"
                                  : name + " (-" + reduction + "% furto) - \u20ac" + price,
                        installed ? OwnedColor
                            : Wallet.CanAfford(price) ? BuyColor : OwnedColor,
                        () =>
                        {
                            if (installed) return;
                            if (!Wallet.CanAfford(price)) return;
                            api.InstallAntitheft(my.code, id, ok =>
                            {
                                if (ok)
                                {
                                    Wallet.Spend(price);
                                    Toast(name + " installato");
                                    Show();
                                }
                                else Toast("Installazione non riuscita");
                            });
                        });
                }
                MakeButton("Chiudi", Close, ButtonBg);
            });
        }

        private void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        /// <summary>Didascalia leggibile per il campo danno del server.</summary>
        private static string DamageCaption(string damage)
        {
            if (damage == "fire") return "IN FIAMME";
            if (damage == "wrecked") return "Incidentata (carro attrezzi)";
            if (damage == "flat") return "Gomma a terra";
            return "Intatto";
        }

        private static float ClampZone(float v)
        {
            return Mathf.Clamp(v, 0f, 100f);
        }

        /// <summary>Riga di riparazione per una singola zona: mostra la
        /// percentuale di danno della zona e il costo per azzerarla.</summary>
        private void ZoneRepairRow(string label, string zoneId,
            float zoneDamage, string code, int price)
        {
            int cost = Mathf.RoundToInt(zoneDamage * RepairPriceFactor * price);
            bool damaged = zoneDamage > 0.05f;
            bool canPay = damaged && Wallet.CanAfford(cost);
            MakeActionRow(
                damaged
                    ? label + ": " + Mathf.RoundToInt(zoneDamage) +
                      "%  - RIPARA \u20ac" + cost
                      + (canPay ? "" : "  (soldi insufficienti)")
                    : label + ": OK",
                damaged ? (canPay ? RepairColor : OwnedColor)
                        : OwnedColor,
                () =>
                {
                    if (!damaged || !canPay) return;
                    var api = VehicleOwnershipApi.Ensure();
                    api.RepairZones(code, new[] { zoneId }, ok =>
                    {
                        if (ok)
                        {
                            Wallet.Spend(cost);
                            if (RescueDirector.Instance != null)
                                RescueDirector.SetLocalDamage(code,
                                    VehicleDamage.None);
                            // ripara le parti meccaniche locali della zona
                            if (VehicleOwnershipApi.Instance != null &&
                                VehicleOwnershipApi.Instance.LocalPlayerVehicle != null)
                                VehicleOwnershipApi.Instance.LocalPlayerVehicle
                                    .RepairPartsForZone(zoneId);
                            Toast(label + " riparata");
                            Show();   // aggiorna l'integrita'/zone
                        }
                        else Toast("Riparazione non riuscita");
                    });
                });
        }

        // ── widget helpers ─────────────────────────────────────────

        /// <summary>
        /// Diagnostica fine: mostra le single parti meccaniche danneggiate
        /// (motore, cambio, gomme, freni, etc.) del veicolo locale del
        /// giocatore e permette di ripararle singolarmente a pagamento.
        /// Puro locale: il costo scala con l'integrità mancante della parte.
        /// </summary>
        private void LocalPartRepairSection(string code)
        {
            var api = VehicleOwnershipApi.Instance;
            var vc = api != null ? api.LocalPlayerVehicle : null;
            if (vc == null || !vc.HasPartDamage) return;

            var damaged = vc.damageSystem.GetDamagedParts();
            if (damaged.Count == 0) return;

            MakeNote("DIAGNOSTICA PARTI — danni meccanici specifici:");
            foreach (var p in damaged)
            {
                if (p.integrity >= 99f) continue;
                int cost = Mathf.RoundToInt(
                    (1f - p.integrity / 100f) * 30f); // ~€30 per parte a piena integrità persa
                if (cost < 1) cost = 1;
                bool canPay = Wallet.CanAfford(cost);
                float pct = Mathf.RoundToInt(p.integrity);
                bool broken = p.IsBroken;
                string status = broken ? "ROTTA" : (p.IsDegraded ? "usurata" : "leggera");
                // cattura locale per la closure (evita il foreach-capture)
                var fixPart = p;
                string fixZone = MapZoneFor(p.type);
                int fixIndex = IndexOfType(vc, p.type, p);
                MakeActionRow(
                    p.partName + " [HP " + pct + "% " + status + "]  RIPARA \u20ac" + cost
                        + (canPay ? "" : "  (soldi insufficienti)"),
                    broken ? new Color(0.7f, 0.3f, 0.2f, 1f)
                           : (canPay ? RepairColor : OwnedColor),
                    () =>
                    {
                        if (!canPay) return;
                        Wallet.Spend(cost);
                        vc.RepairPartAt(fixPart.type, fixIndex);
                        // aggiorna anche la zona corrispondente per coerenza
                        if (fixZone.Length > 0) vc.RepairPartsForZone(fixZone);
                        Toast(fixPart.partName + " riparata: " +
                              Mathf.RoundToInt(vc.damageSystem.AverageIntegrity) + "% HP");
                        Show();
                    });
            }
            MakeNote("Nota: le parti ritornano pienamente efficienti solo dopo la riparazione meccanica singola.");
        }

        private int IndexOfType(VehicleController vc, VehiclePartType type,
            VehicleComponent probe)
        {
            var list = vc.damageSystem.GetParts(type);
            for (int i = 0; i < list.Count; i++)
                if (ReferenceEquals(list[i], probe)) return i;
            return 0;
        }

        private static string MapZoneFor(VehiclePartType type)
        {
            switch (type)
            {
                case VehiclePartType.Suspension:
                case VehiclePartType.ShockAbsorber:
                case VehiclePartType.Tire:
                    return "suspension";
                case VehiclePartType.Bumper:
                case VehiclePartType.Radiator:
                case VehiclePartType.Engine:
                case VehiclePartType.Fuel:
                    return "bumper";
                case VehiclePartType.Bodywork:
                case VehiclePartType.Chassis:
                    return "bodywork";
                default:
                    return "";
            }
        }

        private void MakeRow(string label, string value)
        {
            var row = MakeRect("Row", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 40f);
            row.pivot = new Vector2(0.5f, 1f);
            row.gameObject.AddComponent<Image>().color = RowBg;
            MakeText(row, label, 22f, new Color(0.7f, 0.68f, 0.64f),
                TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(0.5f, 1f),
                new Vector2(12f, 0f), new Vector2(0f, 0f));
            MakeText(row, value, 22f, Color.white, TextAlignmentOptions.Right,
                new Vector2(0.5f, 0f), new Vector2(0.96f, 1f),
                new Vector2(0f, 0f), new Vector2(-12f, 0f));
        }

        private void MakeNote(string label)
        {
            var row = MakeRect("Note", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 34f);
            row.pivot = new Vector2(0.5f, 1f);
            MakeText(row, label, 22f, new Color(0.95f, 0.75f, 0.4f),
                TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(12f, 0f), new Vector2(-12f, 0f));
        }

        private RectTransform MakeActionRow(string label, Color bg,
            System.Action onClick)
        {
            var rt = MakeRect("Act", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 50f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bg;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(new UnityEngine.Events.UnityAction(onClick));
            MakeText(rt, label, 23f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            return rt;
        }

        private void MakeButton(string label, UnityEngine.Events.UnityAction onClick,
            Color bgColor)
        {
            var rt = MakeRect("Btn", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 50f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bgColor;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(onClick);
            MakeText(rt, label, 26f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

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
                Debug.LogError("[OfficinaUI] nessuna Canvas nella scena");
                return;
            }
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem");
                esGo.AddComponent<EventSystem>();
                esGo.AddComponent<StandaloneInputModule>();
            }

            panel = new GameObject("OfficinaPanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = new Vector2(0.5f, 0.5f);
            prt.anchorMax = new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-260f, -330f);
            prt.offsetMax = new Vector2(260f, 330f);
            panel.AddComponent<Image>().color = PanelBg;

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
    }
}
