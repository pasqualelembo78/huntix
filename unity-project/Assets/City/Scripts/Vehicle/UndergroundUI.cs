using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using TMPro;
using City.OSM;

namespace City.Vehicle
{
    /// <summary>
    /// Interfaccia del parcheggio sotterraneo: gestisce l'ingresso da rampa,
    /// l'acquisto/parcheggio stalli, e l'uscita. Tutto lato client, lo stato
    /// e' gestito da underground.py.
    /// </summary>
    public class UndergroundUI : MonoBehaviour
    {
        public static UndergroundUI Instance { get; private set; }

        public const int SpotPrice = 10;
        public const int Levels = 3;
        public const float LevelHeight = 4.0f;
        public const float BayWidth = 2.5f;
        public const float BayLength = 5.0f;
        public const float AisleWidth = 6.0f;
        public const int GridCols = 20;
        public const int GridRows = 30;
        public const float ColumnRadius = 0.25f;

        private GameObject panel;
        private TMP_Text titleText;
        private TMP_Text statusText;
        private TMP_FontAsset font;
        private VehiclePoiZone rampZone;
        private string cityKey;
        private bool underground;

        private Transform undergroundRoot;
        private Transform playerContainer;
        private Vector3 surfacePosition;
        private int ownedSpot = -1;
        private string ownedCity;

        private static readonly Color FloorColor = new Color(0.18f, 0.20f, 0.24f);
        private static readonly Color CeilingColor = new Color(0.22f, 0.24f, 0.28f);
        private static readonly Color ColumnColor = new Color(0.35f, 0.38f, 0.42f);
        private static readonly Color BayLineColor = new Color(0.20f, 0.40f, 0.80f);
        private static readonly Color OwnedBayColor = new Color(0.15f, 0.60f, 0.25f);
        private static readonly Color ExitColor = new Color(0.90f, 0.85f, 0.20f);

        public static void Enter(VehiclePoiZone zone)
        {
            if (Instance == null)
            {
                var go = new GameObject("UndergroundUI", typeof(UndergroundUI));
                DontDestroyOnLoad(go);
            }
            Instance.rampZone = zone;
            Instance.cityKey = zone != null ? GetCityKey(zone) : "";
            Instance.ShowEntry();
        }

        private static string GetCityKey(VehiclePoiZone zone)
        {
            GeoCoord g = WorldOrigin.ToGeo(zone.transform.position);
            int ilat = Mathf.FloorToInt((float)((g.lat - 34.0) / 0.09));
            int ilon = Mathf.FloorToInt((float)((g.lng - 5.0) / 0.121));
            return $"IT_{ilat:000}_{ilon:000}";
        }

        private void ShowEntry()
        {
            if (panel == null) BuildPanel();
            titleText.text = "PARCHEGGIO SOTTERRANEO";
            statusText.text = "Vuoi entrare nel parcheggio?\n" + cityKey;
            ClearActions();
            MakeAction("ENTRA", new Color(0.20f, 0.55f, 0.25f), EnterUnderground);
            MakeAction("Chiudi", new Color(0.40f, 0.20f, 0.18f), Close);
            panel.SetActive(true);
        }

        private void EnterUnderground()
        {
            if (rampZone == null) return;
            Close();
            surfacePosition = rampZone.transform.position;
            var api = VehicleOwnershipApi.Ensure();
            api.EnterUnderground(cityKey, rampZone.transform.position, ok =>
            {
                if (!ok) { Toast("Impossibile entrare"); return; }
                api.GetUndergroundStatus(status =>
                {
                    if (status != null)
                    {
                        ownedSpot = status.owned_spot ?? -1;
                        ownedCity = status.owned_city;
                    }
                    BuildWorld();
                    FadeIn();
                });
            });
        }

        private void BuildWorld()
        {
            if (undergroundRoot != null) Destroy(undergroundRoot);
            undergroundRoot = new GameObject("Underground").transform;

            float totalWidth = GridCols * (BayWidth + 0.3f) + AisleWidth;
            float totalDepth = GridRows * (BayLength + 0.3f) + AisleWidth;

            for (int level = 0; level < Levels; level++)
            {
                float y = -level * LevelHeight;
                BuildLevel(undergroundRoot, level, y, totalWidth, totalDepth);
            }

            var player = GameObject.FindWithTag("Player");
            if (player != null)
            {
                playerContainer = player.transform.parent;
                var controller = player.GetComponent<CharacterController>();
                if (controller != null) controller.enabled = false;

                player.transform.SetParent(undergroundRoot, false);
                player.transform.localPosition = new Vector3(
                    0f, 1.0f, -totalDepth * 0.5f - 2f);
                player.transform.localRotation = Quaternion.identity;
            }

            underground = true;
        }

        private void BuildLevel(Transform parent, int level, float y,
            float width, float depth)
        {
            var lv = new GameObject("Livello_" + level);
            lv.transform.SetParent(parent, false);

            var floor = GameObject.CreatePrimitive(PrimitiveType.Cube);
            floor.name = "Pavimento";
            floor.transform.SetParent(lv.transform, false);
            floor.transform.localScale = new Vector3(width, 0.3f, depth);
            floor.transform.localPosition = new Vector3(0f, y - 0.15f, 0f);
            var fmr = floor.GetComponent<MeshRenderer>();
            fmr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            fmr.material.color = FloorColor;

            var ceil = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ceil.name = "Soffitto";
            ceil.transform.SetParent(lv.transform, false);
            ceil.transform.localScale = new Vector3(width, 0.2f, depth);
            ceil.transform.localPosition = new Vector3(0f, y + LevelHeight, 0f);
            var cmr = ceil.GetComponent<MeshRenderer>();
            cmr.sharedMaterial = fmr.sharedMaterial;
            cmr.material.color = CeilingColor;

            for (int row = 0; row < GridRows; row++)
            {
                for (int col = 0; col < GridCols; col++)
                {
                    int spotNum = level * GridRows * GridCols +
                        row * GridCols + col + 1;
                    float bx = (col - GridCols * 0.5f) *
                        (BayWidth + 0.3f) + AisleWidth * 0.5f;
                    float bz = (row - GridRows * 0.5f) *
                        (BayLength + 0.3f);

                    bool owned = ownedSpot == spotNum;
                    Color bayCol = owned ? OwnedBayColor : BayLineColor;
                    DrawBayLine(lv.transform, bx, y + 0.02f, bz, bayCol);

                    if (col % 3 == 0 && row % 5 == 0)
                        BuildColumn(lv.transform, bx - BayWidth * 0.5f - 0.4f,
                            y, bz - BayLength * 0.5f - 0.4f);
                }
            }

            BuildExitRamp(lv.transform, y, width, depth);
        }

        private void DrawBayLine(Transform parent, float x, float y, float z,
            Color col)
        {
            var line = GameObject.CreatePrimitive(PrimitiveType.Cube);
            line.name = "Linea";
            line.transform.SetParent(parent, false);
            line.transform.localScale = new Vector3(BayWidth, 0.05f, 0.08f);
            line.transform.localPosition = new Vector3(x, y, z);
            var mr = line.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = col;
            mr.material.SetColor("_EmissionColor", col * 0.3f);
            mr.material.EnableKeyword("_EMISSION");
        }

        private void BuildColumn(Transform parent, float x, float y, float z)
        {
            var col = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            col.name = "Colonna";
            col.transform.SetParent(parent, false);
            col.transform.localScale =
                new Vector3(ColumnRadius * 2f, LevelHeight * 0.5f,
                    ColumnRadius * 2f);
            col.transform.localPosition =
                new Vector3(x, y + LevelHeight * 0.5f, z);
            var mr = col.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = ColumnColor;
        }

        private void BuildExitRamp(Transform parent, float y,
            float width, float depth)
        {
            var ramp = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ramp.name = "Uscita";
            ramp.transform.SetParent(parent, false);
            ramp.transform.localScale = new Vector3(5f, 0.4f, 8f);
            ramp.transform.localPosition =
                new Vector3(0f, y + 0.2f, depth * 0.5f + 4f);
            var mr = ramp.GetComponent<MeshRenderer>();
            mr.sharedMaterial = new Material(
                Shader.Find("Universal Render Pipeline/Lit")
                    ?? Shader.Find("Standard"));
            mr.material.color = ExitColor;
            mr.material.SetColor("_EmissionColor", ExitColor * 0.5f);
            mr.material.EnableKeyword("_EMISSION");

            var trig = ramp.AddComponent<BoxCollider>();
            trig.isTrigger = true;
            var ui = ramp.AddComponent<UndergroundExitTrigger>();
            ui.host = this;
        }

        private void ExitUnderground()
        {
            if (!underground) return;
            var api = VehicleOwnershipApi.Ensure();
            api.ExitUnderground(exitPos =>
            {
                FadeOut(() =>
                {
                    var player = GameObject.FindWithTag("Player");
                    if (player != null && playerContainer != null)
                    {
                        player.transform.SetParent(playerContainer, false);
                        player.transform.localPosition = exitPos;
                        var cc = player.GetComponent<CharacterController>();
                        if (cc != null) cc.enabled = true;
                    }
                    if (undergroundRoot != null)
                        Destroy(undergroundRoot.gameObject);
                    underground = false;
                });
            });
        }

        private void FadeIn()
        {
            var fader = FindObjectOfType<City.UI.ScreenFader>();
            if (fader != null) fader.FadeFromBlack(null);
        }

        private void FadeOut(System.Action onDone)
        {
            var fader = FindObjectOfType<City.UI.ScreenFader>();
            if (fader != null)
                fader.FadeToBlack(() => { onDone?.Invoke(); });
            else onDone?.Invoke();
        }

        public void OnExitTrigger()
        {
            ExitUnderground();
        }

        private void Toast(string msg)
        {
            if (City.UI.UIManager.Instance != null)
                City.UI.UIManager.Instance.ShowToast(msg);
        }

        // ── UI helpers ─────────────────────────────────────────

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
            if (canvas == null) return;

            panel = new GameObject("UGPanel");
            var prt = panel.AddComponent<RectTransform>();
            prt.SetParent(canvas.transform, false);
            prt.anchorMin = prt.anchorMax = prt.pivot =
                new Vector2(0.5f, 0.5f);
            prt.offsetMin = new Vector2(-240f, -220f);
            prt.offsetMax = new Vector2(240f, 220f);
            panel.AddComponent<Image>().color =
                new Color(0.06f, 0.08f, 0.12f, 0.97f);

            titleText = MakeText(prt, "", 32f, Color.white,
                TextAlignmentOptions.Left, new Vector2(0f, 1f),
                new Vector2(1f, 1f), new Vector2(16f, -10f),
                new Vector2(-16f, -50f));

            statusText = MakeText(prt, "", 26f,
                new Color(0.85f, 0.88f, 0.92f),
                TextAlignmentOptions.Center, new Vector2(0f, 0.5f),
                new Vector2(1f, 1f), new Vector2(16f, -56f),
                new Vector2(-16f, 0f));

            panel.SetActive(false);
        }

        private void ClearActions()
        {
            if (panel == null) return;
            foreach (var c in panel.GetComponentsInChildren<Transform>())
                if (c != panel.transform && c.name.StartsWith("Act"))
                    Destroy(c.gameObject);
        }

        private void MakeAction(string label, Color bg,
            UnityEngine.Events.UnityAction onClick)
        {
            var go = new GameObject("Act_" + label, typeof(Image), typeof(Button));
            go.transform.SetParent(panel.transform, false);
            var img = go.GetComponent<Image>();
            img.color = bg;
            var rt = go.GetComponent<RectTransform>();
            rt.anchorMin = new Vector2(0.1f, 0f);
            rt.anchorMax = new Vector2(0.9f, 0f);
            rt.pivot = new Vector2(0.5f, 0f);
            rt.anchoredPosition = new Vector2(0f, 12f);
            rt.sizeDelta = new Vector2(0f, 52f);
            go.GetComponent<Button>().onClick.AddListener(onClick);

            var tgo = new GameObject("T", typeof(TextMeshProUGUI));
            tgo.transform.SetParent(go.transform, false);
            var txt = tgo.GetComponent<TextMeshProUGUI>();
            txt.text = label;
            txt.fontSize = 28;
            txt.color = Color.white;
            txt.alignment = TextAlignmentOptions.Center;
            txt.font = font;
            var trt = txt.rectTransform;
            trt.anchorMin = Vector2.zero;
            trt.anchorMax = Vector2.one;
            trt.sizeDelta = Vector2.zero;
        }

        private TMP_Text MakeText(RectTransform parent, string content,
            float size, Color color, TextAlignmentOptions alignment,
            Vector2 anchorMin, Vector2 anchorMax,
            Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject("T", typeof(RectTransform));
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            var text = go.AddComponent<TextMeshProUGUI>();
            text.text = content;
            text.fontSize = size;
            text.color = color;
            text.alignment = alignment;
            text.font = font;
            text.raycastTarget = false;
            return text;
        }

        public void Close()
        {
            if (panel != null) panel.SetActive(false);
            rampZone = null;
        }
    }

    public class UndergroundExitTrigger : MonoBehaviour
    {
        public UndergroundUI host;

        private void OnTriggerEnter(Collider other)
        {
            if (other.CompareTag("Player"))
                host.OnExitTrigger();
        }
    }
}
