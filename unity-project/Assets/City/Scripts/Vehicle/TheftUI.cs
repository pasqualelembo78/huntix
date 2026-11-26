using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using TMPro;
using City.Economy;
using City.OSM;
using City.UI;
using City.World;

namespace City.Vehicle
{
    /// <summary>
    /// Furto di veicoli di MiCittà — ADDITIVO: la concessionaria e l'officina
    /// restano intatte, qui si aggiunge la scelta "ruba invece di comprare".
    /// Il gate è il livello di Mastro Ladro salvato su Android: bici/moto dal
    /// livello 1, utilitarie dal 2, SUV/furgoni dal 4, sportive dal 6, lusso
    /// dall'8. L'appartamento resta il livello 10 (prossimi lavori).
    /// L'auto presa diventa possesso permanente (nessun costo, nessun timer).
    /// </summary>
    public class TheftUI : MonoBehaviour
    {
        public static TheftUI Instance { get; private set; }

        private GameObject panel;
        private RectTransform listContent;
        private TMP_Text titleText;
        private TMP_FontAsset font;
        private bool theftPending;
        private static int theftSeq;

        private static readonly Color PanelBg = new Color(0.10f, 0.09f, 0.14f, 0.98f);
        private static readonly Color RowBg = new Color(0.19f, 0.16f, 0.24f, 1f);
        private static readonly Color StealColor = new Color(0.55f, 0.18f, 0.55f, 1f);
        private static readonly Color LockedColor = new Color(0.28f, 0.28f, 0.30f, 1f);

        public static void Open()
        {
            if (Instance == null)
            {
                var go = new GameObject("TheftUI");
                DontDestroyOnLoad(go);
                Instance = go.AddComponent<TheftUI>();
            }
            Instance.Show();
        }

        private void Show()
        {
            if (panel == null) BuildPanel();
            titleText.text = "RUB AUTO · Mastro Ladro Lv." + CitySkills.ThiefLevel +
                " · punti " + CitySkills.Points;
            RebuildList();
            panel.SetActive(true);
            Time.timeScale = 0f;
        }

        private void RebuildList()
        {
            foreach (Transform child in listContent) Destroy(child.gameObject);
            foreach (var def in VehicleSpawnManager.Catalogue)
                MakeVehicleRow(def);
            MakeButton("Chiudi", Close, LockedColor);
        }

        private void MakeVehicleRow(VehicleSpawnManager.VehicleDef def)
        {
            int required = CitySkills.TheftRequiredLevel(def.name, def.price);
            bool allowed = CitySkills.ThiefLevel >= required;

            var row = MakeRect("Theft_" + def.name, listContent,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(12f, -6f), new Vector2(-12f, -92f));
            row.sizeDelta = new Vector2(0f, 92f);
            row.pivot = new Vector2(0.5f, 1f);
            row.gameObject.AddComponent<Image>().color = RowBg;

            MakeText(row, def.name + (allowed ? "" : " 🔒"), 24f, Color.white,
                TextAlignmentOptions.Left, new Vector2(0f, 1f), new Vector2(0.62f, 1f),
                new Vector2(12f, -4f), new Vector2(0f, -36f));
            MakeText(row, "GRATIS · serve " + CitySkills.TheftRequirementLabel(def.name, def.price),
                18f, allowed ? new Color(0.55f, 1f, 0.75f) : new Color(0.9f, 0.65f, 0.65f),
                TextAlignmentOptions.Left, new Vector2(0f, 0f), new Vector2(0.62f, 0f),
                new Vector2(12f, 6f), new Vector2(0f, 34f));

            var btnRt = MakeRect("Rub", row, new Vector2(0.64f, 0.5f), new Vector2(1f, 0.5f),
                new Vector2(0f, -22f), new Vector2(-12f, 22f));
            var img = btnRt.gameObject.AddComponent<Image>();
            img.color = allowed ? StealColor : LockedColor;
            var btn = btnRt.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            btn.interactable = allowed;
            btn.onClick.AddListener(() => TrySteal(def));
            MakeText(btnRt, allowed ? "RUBA" : "Lv." + required, 22f, Color.white,
                TextAlignmentOptions.Center, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
        }

        private void TrySteal(VehicleSpawnManager.VehicleDef def)
        {
            if (theftPending) return;
            if (CitySkills.ThiefLevel <
                CitySkills.TheftRequiredLevel(def.name, def.price))
            {
                Toast("Serve Mastro Ladro Lv." +
                    CitySkills.TheftRequiredLevel(def.name, def.price));
                return;
            }
            theftPending = true;

            Vector3 spawnPos;
            float heading;
            var roadNet = City.Vehicle.Traffic.TileRoadNetwork.Instance;
            Vector3 near = Game.Instance != null && Game.Instance.player != null
                ? Game.Instance.player.transform.position : Vector3.zero;
            if (roadNet != null && roadNet.Graph != null && roadNet.Graph.nodes.Count > 0)
            {
                int nodeId = roadNet.Graph.FindClosestNode(near);
                var node = roadNet.Graph.nodeMap[nodeId];
                spawnPos = node.position;
                int arcId = node.arcIds.Count > 0 ? node.arcIds[0] : -1;
                if (arcId >= 0 && roadNet.Graph.arcMap.TryGetValue(arcId, out var arc)
                    && arc.waypoints.Length >= 2)
                {
                    Vector3 dir = arc.waypoints[1] - arc.waypoints[0];
                    heading = Mathf.Atan2(dir.x, dir.z) * Mathf.Rad2Deg;
                }
                else heading = 0f;
            }
            else
            {
                spawnPos = near + Vector3.forward * 6f;
                heading = 0f;
            }

            // Codice nuovo: il veicolo non era nel registro (auto del traffico),
            // quindi diventa possesso stabile senza pagamento e senza timer.
            int seq = System.Threading.Interlocked.Increment(ref theftSeq);
            string code = "T" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                .ToString("X") + seq.ToString("X");
            GeoCoord g = WorldOrigin.ToGeo(spawnPos);

            VehicleOwnershipApi.Ensure().Buy(code, (ok, err) =>
            {
                theftPending = false;
                if (!ok)
                {
                    Toast("Furto rifiutato dal registro: " + (err ?? "?"));
                    return;
                }
                var api = VehicleOwnershipApi.Ensure();
                api.MarkOwned(code, g.lat, g.lng, heading);
                api.SetLocalState(code, def.name, def.price);
                Inventory.Add("vehicle_" + code);
                var stolen = VehicleSpawnManager.BuildVehicle(
                    null, def, spawnPos, heading, code);
                if (stolen != null)
                {
                    api.ApplyOwnedState(stolen, code);
                    VehicleSpawnManager.RegisterActiveOwned(code, stolen);
                    var vc = stolen.GetComponentInChildren<VehicleController>();
                    if (vc != null && Game.Instance != null)
                        Game.Instance.EnterVehicle(vc);
                    NavigationState.Set(def.name + " (rubata)", "",
                        g.lat, g.lng);
                }

                // Riflesso uova + abilità: Android sa dove è avvenuto il furto.
                string place = "MiCittà→garage→furto→" + def.name;
                string json = "{\"model\":\"" + def.name +
                    "\",\"skillLevel\":" + CitySkills.ThiefLevel +
                    ",\"place\":\"" + place +
                    "\",\"lat\":\"" + g.lat.ToString("F6", System.Globalization.CultureInfo.InvariantCulture) +
                    "\",\"lng\":\"" + g.lng.ToString("F6", System.Globalization.CultureInfo.InvariantCulture) +
                    "\"}";
                try
                {
                    Huntix.Bridge.UnityBridge.SendMessageToAndroid(
                        "CityVehicleStolen", json);
                }
                catch (Exception e)
                {
                    OsmDiag.Log("[TheftUI] sync Android fallita: " + e.Message);
                }
                Close();
                Toast(def.name + " rubata! È tua per sempre.");
            });
        }

        public void Close()
        {
            if (panel != null) panel.SetActive(false);
            Time.timeScale = 1f;
        }

        private void Toast(string msg)
        {
            if (UIManager.Instance != null) UIManager.Instance.ShowToast(msg);
        }

        private void BuildPanel()
        {
            font = TMP_Settings.defaultFontAsset;
            var canvas = new GameObject("TheftCanvas", typeof(Canvas),
                typeof(CanvasScaler), typeof(GraphicRaycaster));
            canvas.transform.SetParent(transform, false);
            var c = canvas.GetComponent<Canvas>();
            c.renderMode = RenderMode.ScreenSpaceOverlay;
            c.sortingOrder = 60;
            var scaler = canvas.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);

            panel = new GameObject("TheftPanel", typeof(Image));
            panel.transform.SetParent(canvas.transform, false);
            var prt = panel.GetComponent<RectTransform>();
            prt.anchorMin = Vector2.zero; prt.anchorMax = Vector2.one;
            prt.offsetMin = Vector2.zero; prt.offsetMax = Vector2.zero;
            panel.GetComponent<Image>().color = PanelBg;

            titleText = MakeText(prt, "RUB AUTO", 34f, Color.white,
                TextAlignmentOptions.Center, new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(20f, -28f), new Vector2(-20f, -86f));
            MakeText(prt, "Rubare è possibile solo con Mastro Ladro. " +
                "Ogni auto presa è tua per sempre e lascia un indizio nel diario uova.",
                20f, new Color(0.75f, 0.72f, 0.85f), TextAlignmentOptions.Center,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(40f, -94f), new Vector2(-40f, -140f));

            var scrollRt = MakeRect("Scroll", prt, Vector2.zero, Vector2.one,
                new Vector2(30f, 30f), new Vector2(-30f, -160f));
            scrollRt.gameObject.AddComponent<ScrollRect>();
            var mask = scrollRt.gameObject.AddComponent<RectMask2D>();
            var viewport = MakeRect("Viewport", scrollRt, Vector2.zero, Vector2.one,
                Vector2.zero, Vector2.zero);
            viewport.gameObject.AddComponent<Image>().color = new Color(1f, 1f, 1f, 0.01f);
            listContent = MakeRect("Content", viewport,
                new Vector2(0f, 1f), new Vector2(1f, 1f),
                new Vector2(0f, 0f), new Vector2(0f, 0f));
            listContent.pivot = new Vector2(0.5f, 1f);
            var scroll = scrollRt.GetComponent<ScrollRect>();
            scroll.viewport = viewport;
            scroll.content = listContent;
            scroll.horizontal = false;
            scroll.vertical = true;
            scroll.movementType = ScrollRect.MovementType.Clamped;
        }

        private void MakeButton(string label, UnityEngine.Events.UnityAction onClick, Color bgColor)
        {
            var rt = MakeRect("Btn", listContent, Vector2.zero, Vector2.one,
                new Vector2(14f, 0f), new Vector2(-14f, 56f));
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
            go.transform.SetParent(parent, false);
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = anchorMin; rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin; rt.offsetMax = offsetMax;
            return rt;
        }

        private TMP_Text MakeText(RectTransform parent, string content, float size,
            Color color, TextAlignmentOptions align,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var rt = MakeRect("T", parent, anchorMin, anchorMax, offsetMin, offsetMax);
            var text = rt.gameObject.AddComponent<TextMeshProUGUI>();
            text.text = content;
            text.fontSize = size;
            text.color = color;
            text.alignment = align;
            text.enableWordWrapping = true;
            if (font != null) text.font = font;
            text.raycastTarget = false;
            return text;
        }
    }
}
