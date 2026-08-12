using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;

// Huntix: overlay touch per "Laugh Little Lamb" (gioco pensato per tastiera).
// Costruisce a runtime una UI di pulsanti che pilotano gli stessi eventi
// di InputController, così il gioco è giocabile da telefono.
public class MobileControls : MonoBehaviour
{
	private InputController input;
	private Canvas canvas;

	public void Configure(InputController target)
	{
		input = target;
		BuildUi();
	}

	private void BuildUi()
	{
		if (input == null) return;
		if (canvas != null) return;

		var rootGo = new GameObject("MobileControlsOverlay");
		rootGo.transform.SetParent(transform, false);

		canvas = rootGo.AddComponent<Canvas>();
		canvas.renderMode = RenderMode.ScreenSpaceOverlay;
		canvas.sortingOrder = 30000;

		var scaler = rootGo.AddComponent<CanvasScaler>();
		scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
		scaler.referenceResolution = new Vector2(1920, 1080);
		scaler.matchWidthOrHeight = 0.5f;

		var raycaster = rootGo.AddComponent<GraphicRaycaster>();

		EnsureEventSystem();

		var root = new GameObject("Root");
		root.transform.SetParent(rootGo.transform, false);
		var rect = root.AddComponent<RectTransform>();
		rect.anchorMin = Vector2.zero;
		rect.anchorMax = Vector2.one;
		rect.offsetMin = Vector2.zero;
		rect.offsetMax = Vector2.zero;

		// ── Cluster sinistro: MOVIMENTO (WASD) ─────────────────────────
		var moveCluster = CreateCluster(root.transform, new Vector2(260, 150), "MOVE");
		AddButton(moveCluster, "▲", new Vector2(0, 96), 88, input.PressMoveUp);
		AddButton(moveCluster, "▼", new Vector2(0, -96), 88, input.PressMoveDown);
		AddButton(moveCluster, "◀", new Vector2(-96, 0), 88, input.PressMoveLeft);
		AddButton(moveCluster, "▶", new Vector2(96, 0), 88, input.PressMoveRight);

		// ── Cluster destro: GUARDA (frecce) ───────────────────────────
		var lookCluster = CreateCluster(root.transform, new Vector2(-260, 150), "LOOK");
		AddButton(lookCluster, "↟", new Vector2(0, 96), 72, input.PressLookUp);
		AddButton(lookCluster, "↡", new Vector2(0, -96), 72, input.PressLookDown);
		AddButton(lookCluster, "↜", new Vector2(-96, 0), 72, input.PressLookLeft);
		AddButton(lookCluster, "↝", new Vector2(96, 0), 72, input.PressLookRight);

		// ── Barra azioni in alto ──────────────────────────────────────
		var actionBar = CreateCluster(root.transform, new Vector2(0, -330), "");
		AddButton(actionBar, "AVANZA\n(Wait)", new Vector2(0, 0), 120, input.PressWait);
		AddButton(actionBar, "ANNULLA\n(Undo)", new Vector2(150, 0), 120, input.PressUndo);
		AddButton(actionBar, "RIPETI\n(Do)", new Vector2(300, 0), 120, input.PressDo);
		AddButton(actionBar, "RIAVVIA", new Vector2(450, 0), 120, input.PressRestart);
		AddButton(actionBar, "MENU", new Vector2(600, 0), 120, input.PressMenu);
	}

	private Transform CreateCluster(Transform parent, Vector2 pos, string label)
	{
		var go = new GameObject(label + "Cluster");
		go.transform.SetParent(parent, false);
		var rt = go.AddComponent<RectTransform>();
		rt.anchorMin = new Vector2(0.5f, 0.5f);
		rt.anchorMax = new Vector2(0.5f, 0.5f);
		rt.pivot = new Vector2(0.5f, 0.5f);
		rt.anchoredPosition = pos;
		return go.transform;
	}

	private void AddButton(Transform parent, string label, Vector2 offset, float size, UnityEngine.Events.UnityAction onClick)
	{
		var go = new GameObject(label.Replace("\n", " "));
		go.transform.SetParent(parent, false);

		var img = go.AddComponent<Image>();
		img.color = new Color(0f, 0f, 0f, 0.45f);

		var btn = go.AddComponent<Button>();
		btn.targetGraphic = img;
		btn.onClick.AddListener(onClick);

		var rt = go.GetComponent<RectTransform>();
		rt.sizeDelta = new Vector2(size, size);
		rt.anchoredPosition = offset;

		var txtGo = new GameObject("Label");
		txtGo.transform.SetParent(go.transform, false);
		var txt = txtGo.AddComponent<Text>();
		txt.text = label;
		txt.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
		txt.fontSize = 26;
		txt.alignment = TextAnchor.MiddleCenter;
		txt.color = Color.white;
		txt.horizontalOverflow = HorizontalWrapMode.Overflow;
		txt.verticalOverflow = VerticalWrapMode.Overflow;
		txt.raycastTarget = false;

		var txtRt = txtGo.GetComponent<RectTransform>();
		txtRt.anchorMin = Vector2.zero;
		txtRt.anchorMax = Vector2.one;
		txtRt.offsetMin = Vector2.zero;
		txtRt.offsetMax = Vector2.zero;
	}

	private void EnsureEventSystem()
	{
		if (EventSystem.current != null) return;
		var esGo = new GameObject("EventSystem");
		esGo.AddComponent<EventSystem>();
		esGo.AddComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>();
	}
}
