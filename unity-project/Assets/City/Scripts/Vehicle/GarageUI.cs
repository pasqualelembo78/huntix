using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.World;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Pannello del garage. Due modi per proteggere l'auto:
    ///   • affitto giornaliero (€10): valido fino alla mezzanotte REALE
    ///     d'Italia del giorno corrente (affittare alle 23:55 non regala nulla)
    ///   • acquisto permanente (€500): un solo garage per giocatore, per sempre
    /// Auto ricoverata = furto impossibile. RICOVERA richiede di possedere
    /// l'auto parcheggiata vicina al garage; RITIRA la fa riapparire sul
    /// piazzale del garage.
    /// </summary>
    public class GarageUI : MonoBehaviour
    {
        public static GarageUI Instance { get; private set; }

        public const int RentPriceEur = 10;
        public const int BuyPriceEur = 500;

        private GameObject panel;
        private TMP_Text titleText;
        private RectTransform listContent;
        private VehiclePoiZone zone;
        private TMP_FontAsset font;

        private static readonly Color PanelBg = new Color(0.08f, 0.12f, 0.10f, 0.97f);
        private static readonly Color RowBg = new Color(0.18f, 0.24f, 0.20f, 1f);
        private static readonly Color BuyColor = new Color(0.15f, 0.65f, 0.45f, 1f);
        private static readonly Color RentColor = new Color(0.20f, 0.45f, 0.80f, 1f);
        private static readonly Color ButtonBg = new Color(0.28f, 0.32f, 0.30f, 1f);
        private static readonly Color OwnedColor = new Color(0.3f, 0.3f, 0.3f, 0.8f);

        public static void Open(VehiclePoiZone poiZone)
        {
            if (Instance == null)
            {
                var go = new GameObject("GarageUI");
                DontDestroyOnLoad(go);
                Instance = go.AddComponent<GarageUI>();
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
            titleText.text = "GARAGE" +
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
            api.GetGarageStatus(status =>
            {
                if (this == null || panel == null || !panel.activeSelf) return;

                bool ownsThis = status != null && status.owned != null &&
                    status.owned.garage_id == (zone != null ? zone.poiId : "");
                bool rentedHereToday = status != null && status.rental != null &&
                    status.rental.valid &&
                    status.rental.garage_id == (zone != null ? zone.poiId : "");

                MakeRow("Affitto giornata", "\u20ac" + RentPriceEur +
                    " (fino a mezzanotte)");
                MakeRow("Acquisto garage", "\u20ac" + BuyPriceEur +
                    " (tuo per sempre, 1 solo)");

                // ── stato auto personale ──
                api.GetMyVehicle(my =>
                {
                    if (this == null || panel == null || !panel.activeSelf) return;
                    if (my != null && my.stolen)
                        MakeNote("La tua auto \u00e8 stata RUBATA!");
                    else if (my != null && my.in_garage)
                        MakeNote("La tua auto \u00e8 al sicuro in garage.");

                    // ── azioni ──
                    if (!ownsThis && !rentedHereToday)
                    {
                        bool hasOwnedGarageElsewhere =
                            status != null && status.owned != null;
                        MakeActionRow("AFFITTA PER OGGI - \u20ac" + RentPriceEur,
                            Wallet.CanAfford(RentPriceEur) ? RentColor : OwnedColor,
                            () => TryRent(api));
                        if (!hasOwnedGarageElsewhere)
                            MakeActionRow("COMPRA QUESTO GARAGE - \u20ac" + BuyPriceEur,
                                Wallet.CanAfford(BuyPriceEur) ? BuyColor : OwnedColor,
                                () => TryBuyGarage(api));
                        else
                            MakeNote("Possiedi gi\u00e0 un altro garage.");
                    }
                    else
                    {
                        MakeNote(ownsThis
                            ? "Questo garage \u00e8 TUO."
                            : "Affittato per oggi.");
                        if (my != null && !my.stolen && my.in_garage)
                            MakeActionRow("RITIRA L'AUTO", BuyColor,
                                () => TryRetrieve(api));
                        else if (my != null && !my.stolen)
                            MakeActionRow("RICOVERA LA TUA AUTO", RentColor,
                                () => TryParkInside(api, ownsThis || rentedHereToday));
                    }
                    MakeButton("Chiudi", Close, ButtonBg);
                });
            });
        }

        private void TryRent(VehicleOwnershipApi api)
        {
            if (zone == null) return;
            if (!Wallet.CanAfford(RentPriceEur)) { Toast("Soldi insufficienti"); return; }
            api.RentGarage(zone.poiId, ok =>
            {
                if (ok)
                {
                    Wallet.Spend(RentPriceEur);
                    Toast("Garage affittato fino a mezzanotte");
                    Show();
                }
                else Toast("Affitto non riuscito (forse pagato altrove oggi)");
            });
        }

        private void TryBuyGarage(VehicleOwnershipApi api)
        {
            if (zone == null) return;
            if (!Wallet.CanAfford(BuyPriceEur)) { Toast("Soldi insufficienti"); return; }
            GeoCoord gz = WorldOrigin.ToGeo(zone.transform.position);
            api.BuyGarage(zone.poiId, gz.lat, gz.lng, ok =>
            {
                if (ok)
                {
                    Wallet.Spend(BuyPriceEur);
                    Toast("Garage comprato! \u00c8 tuo per sempre.");
                    Show();
                }
                else Toast("Acquisto non riuscito");
            });
        }

        private void TryParkInside(VehicleOwnershipApi api, bool hasAccess)
        {
            if (zone == null) return;
            api.GetMyVehicle(my =>
            {
                if (my == null) { Toast("Non hai un'auto"); return; }
                if (my.stolen) { Toast("L'auto \u00e8 stata rubata!"); return; }

                // l'auto deve essere parcheggiata vicino a questo garage
                VehicleOwnershipApi.ParkedVehicle parked = api.GetParkedInfo(my.code);
                if (parked != null)
                {
                    GeoCoord here = WorldOrigin.ToGeo(
                        zone.transform.position);
                    double dlat = (parked.lat - here.lat) * 111320.0;
                    double dlon = (parked.lon - here.lng) * 111320.0 *
                        Mathf.Cos(Mathf.Deg2Rad * (float)here.lat);
                    double distSq = dlat * dlat + dlon * dlon;
                    const double maxDistM = 200.0;
                    if (distSq > maxDistM * maxDistM)
                    {
                        Toast("Porta l'auto pi\u00f9 vicina al garage (" +
                            Mathf.Sqrt((float)distSq).ToString("F0") + " m)");
                        return;
                    }
                }

                GeoCoord gz = WorldOrigin.ToGeo(zone.transform.position);
                api.SetCurrentGarageId(zone.poiId);
                api.GaragePark(my.code, gz.lat, gz.lng, ok2 =>
                {
                    if (ok2)
                    {
                        Toast("Auto al sicuro: nessun furto possibile");
                        api.SetInGarageLocal(my.code, true);
                        // l'auto entra fisicamente nel box: sparisce dalla
                        // strada (il popolatore non la fa piu' rinascere)
                        DestroySceneVehicle(my.code);
                        Show();
                    }
                    else Toast("Ricovero non riuscito: serve affitto o propriet\u00e0");
                });
            });
        }

        private void TryRetrieve(VehicleOwnershipApi api)
        {
            if (zone == null || zone.deliveryPoint == null) return;
            api.GetMyVehicle(my =>
            {
                if (my == null || !my.in_garage) { Toast("Nessuna auto nel garage"); return; }

                api.GarageExit(ok2 =>
                {
                    if (!ok2) { Toast("Recupero non riuscito"); return; }
                    api.SetInGarageLocal(my.code, false);

                    // fa comparire l'auto sul piazzale del garage
                    VehicleSpawnManager.VehicleDef def;
                    if (!VehicleSpawnManager.TryGetDef(my.model, out def))
                        def = VehicleSpawnManager.Catalogue[0];
                    float heading = zone.deliveryPoint.eulerAngles.y;
                    var go = VehicleSpawnManager.BuildVehicle(
                        zone.deliveryPoint.parent, def,
                        zone.deliveryPoint.localPosition, heading, my.code);
                    api.ApplyOwnedState(go, my.code);
                    api.UpdateParkedPosition(my.code,
                        WorldOrigin.ToGeo(zone.deliveryPoint.position).lat,
                        WorldOrigin.ToGeo(zone.deliveryPoint.position).lng,
                        heading);
                    Close();
                    Toast("Ecco la tua auto sul piazzale");
                });
            });
        }

        private void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        /// <summary>Rimuove dalla scena il veicolo con quel codice (ricovero
        /// nel garage, vendita): resta solo nello stato server.</summary>
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

        // ── widget helpers ─────────────────────────────────────────

        private void MakeRow(string label, string value)
        {
            var row = MakeRect("Row", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            row.sizeDelta = new Vector2(0f, 40f);
            row.pivot = new Vector2(0.5f, 1f);
            row.gameObject.AddComponent<Image>().color = RowBg;
            MakeText(row, label, 22f, new Color(0.68f, 0.74f, 0.70f),
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
            MakeText(row, label, 22f, new Color(0.5f, 0.95f, 0.65f),
                TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(12f, 0f), new Vector2(-12f, 0f));
        }

        private void MakeActionRow(string label, Color bg, System.Action onClick)
        {
            var rt = MakeRect("Act", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 52f);
            rt.pivot = new Vector2(0.5f, 1f);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = bg;
            var btn = rt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.onClick.AddListener(new UnityEngine.Events.UnityAction(onClick));
            MakeText(rt, label, 25f, Color.white, TextAlignmentOptions.Center,
                Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
        }

        private void MakeButton(string label, UnityEngine.Events.UnityAction onClick,
            Color bgColor)
        {
            var rt = MakeRect("Btn", listContent, new Vector2(0f, 1f),
                new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            rt.sizeDelta = new Vector2(0f, 52f);
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
                Debug.LogError("[GarageUI] nessuna Canvas nella scena");
                return;
            }
            if (EventSystem.current == null)
            {
                var esGo = new GameObject("EventSystem");
                esGo.AddComponent<EventSystem>();
                esGo.AddComponent<StandaloneInputModule>();
            }

            panel = new GameObject("GaragePanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = new Vector2(0.5f, 0.5f);
            prt.anchorMax = new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-260f, -280f);
            prt.offsetMax = new Vector2(260f, 280f);
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
