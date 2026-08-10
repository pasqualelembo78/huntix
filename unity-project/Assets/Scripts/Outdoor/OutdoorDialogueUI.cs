using UnityEngine;
using UnityEngine.UI;
using System.Collections;

namespace Huntix.Outdoor
{
    /// <summary>
    /// OutdoorDialogueUI — UI overlay (Unity Canvas) per dialoghi e prompt
    /// di interazione con NPC guide outdoor. Auto-costruita, nessuna dipendenza
    /// esterna (niente LeanTween). Mostra:
    /// - Prompt galleggiante quando un NPC è vicino ("[E] Parla con la guida")
    /// - Pannello dialogo con emoji, nome, testo e pulsante interagisci
    /// </summary>
    public class OutdoorDialogueUI : MonoBehaviour
    {
        public static OutdoorDialogueUI Instance { get; private set; }

        [Header("Settings")]
        public float fadeDuration = 0.25f;
        public KeyCode interactKey = KeyCode.E;

        private Canvas _canvas;
        private GameObject _promptPanel;
        private Text _promptText;
        private CanvasGroup _promptGroup;
        private GameObject _dialoguePanel;
        private Text _dialogueNameText;
        private Text _dialogueText;
        private Image _dialogueBg;
        private bool _isDialogueOpen = false;
        private OutdoorNPC _currentNPC;
        private Coroutine _fadeRoutine;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        private void Start()
        {
            BuildUI();
            HidePromptImmediate();
            HideDialogueImmediate();
        }

        private void Update()
        {
            // Chiudi dialogo con tasto
            if (_isDialogueOpen && Input.GetKeyDown(interactKey))
            {
                CloseDialogue();
            }
        }

        private void BuildUI()
        {
            var go = new GameObject("Canvas");
            go.transform.SetParent(transform);
            _canvas = go.AddComponent<Canvas>();
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _canvas.sortingOrder = 100;
            var scaler = _canvas.gameObject.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            _canvas.gameObject.AddComponent<GraphicRaycaster>();

            // --- Prompt panel (floating, bottom center) ---
            _promptPanel = new GameObject("PromptPanel");
            _promptPanel.transform.SetParent(_canvas.transform);
            var pr = _promptPanel.AddComponent<RectTransform>();
            pr.anchorMin = new Vector2(0.5f, 0);
            pr.anchorMax = new Vector2(0.5f, 0);
            pr.pivot = new Vector2(0.5f, 0);
            pr.anchoredPosition = new Vector2(0, 140);
            pr.sizeDelta = new Vector2(340, 90);
            _promptPanel.AddComponent<Image>().color = new Color(0f, 0f, 0f, 0.8f);
            _promptGroup = _promptPanel.AddComponent<CanvasGroup>();

            _promptText = new GameObject("PromptText").AddComponent<Text>();
            _promptText.transform.SetParent(_promptPanel.transform);
            var ptr = _promptText.GetComponent<RectTransform>();
            ptr.anchorMin = Vector2.zero;
            ptr.anchorMax = Vector2.one;
            ptr.offsetMin = new Vector2(12, 12);
            ptr.offsetMax = new Vector2(-12, -12);
            _promptText.alignment = TextAnchor.MiddleCenter;
            _promptText.fontSize = 18;
            _promptText.color = new Color(1f, 0.9f, 0.7f);

            // --- Dialogue panel (bottom center, larger) ---
            _dialoguePanel = new GameObject("DialoguePanel");
            _dialoguePanel.transform.SetParent(_canvas.transform);
            var dr = _dialoguePanel.AddComponent<RectTransform>();
            dr.anchorMin = new Vector2(0, 0);
            dr.anchorMax = new Vector2(1, 0);
            dr.pivot = new Vector2(0.5f, 0);
            dr.anchoredPosition = new Vector2(0, 100);
            dr.sizeDelta = new Vector2(0, 280);
            _dialogueBg = _dialoguePanel.AddComponent<Image>();
            _dialogueBg.color = new Color(0.05f, 0.05f, 0.12f, 0.92f);
            var dg = _dialoguePanel.AddComponent<CanvasGroup>();

            // NPC name (top)
            _dialogueNameText = new GameObject("NameText").AddComponent<Text>();
            _dialogueNameText.transform.SetParent(_dialoguePanel.transform);
            var nr = _dialogueNameText.GetComponent<RectTransform>();
            nr.anchorMin = new Vector2(0, 1);
            nr.anchorMax = new Vector2(1, 1);
            nr.pivot = new Vector2(0.5f, 1f);
            nr.anchoredPosition = new Vector2(0, -12);
            nr.sizeDelta = new Vector2(0, 36);
            _dialogueNameText.alignment = TextAnchor.UpperCenter;
            _dialogueNameText.fontSize = 24;
            _dialogueNameText.color = new Color(1f, 0.85f, 0.6f);
            _dialogueNameText.fontStyle = FontStyle.Bold;

            // Dialogue text (center)
            _dialogueText = new GameObject("DialogueText").AddComponent<Text>();
            _dialogueText.transform.SetParent(_dialoguePanel.transform);
            var dtr = _dialogueText.GetComponent<RectTransform>();
            dtr.anchorMin = new Vector2(0, 0);
            dtr.anchorMax = new Vector2(1, 1);
            dtr.pivot = new Vector2(0.5f, 0.5f);
            dtr.offsetMin = new Vector2(24, 50);
            dtr.offsetMax = new Vector2(-24, 60);
            _dialogueText.alignment = TextAnchor.MiddleCenter;
            _dialogueText.fontSize = 20;
            _dialogueText.color = Color.white;

            // Action hint (bottom)
            var hint = new GameObject("Hint").AddComponent<Text>();
            hint.transform.SetParent(_dialoguePanel.transform);
            var hr = hint.GetComponent<RectTransform>();
            hr.anchorMin = new Vector2(0, 0);
            hr.anchorMax = new Vector2(1, 0);
            hr.pivot = new Vector2(0.5f, 0);
            hr.anchoredPosition = new Vector2(0, 16);
            hr.sizeDelta = new Vector2(0, 24);
            hint.alignment = TextAnchor.MiddleCenter;
            hint.fontSize = 15;
            hint.color = new Color(0.6f, 0.6f, 1f);
        }

        #region Prompt

        public static void ShowPrompt(string npcName, string emoji, string action)
        {
            Instance?._ShowPromptInternal($"{emoji}  {npcName}", action);
        }

        public static void HidePrompt()
        {
            Instance?._HidePromptInternal();
        }

        private void _ShowPromptInternal(string name, string action)
        {
            if (_promptPanel == null || _promptText == null) return;
            _promptText.text = $"{name}\n<u>{action}</u>";
            _promptPanel.SetActive(true);
            if (_fadeRoutine != null) StopCoroutine(_fadeRoutine);
            _fadeRoutine = StartCoroutine(FadeGroup(_promptGroup, 0f, 1f));
        }

        private void _HidePromptInternal()
        {
            if (_promptPanel == null) return;
            if (_fadeRoutine != null) StopCoroutine(_fadeRoutine);
            _fadeRoutine = StartCoroutine(FadeGroup(_promptGroup, 1f, 0f, () => _promptPanel.SetActive(false)));
        }

        private void HidePromptImmediate()
        {
            if (_promptPanel != null) _promptPanel.SetActive(false);
            if (_promptGroup != null) _promptGroup.alpha = 0f;
        }

        #endregion

        #region Dialogue

        public static void ShowDialogue(OutdoorNPC npc, string emoji, string dialogue)
        {
            Instance?._ShowDialogueInternal(npc, emoji, dialogue);
        }

        private void _ShowDialogueInternal(OutdoorNPC npc, string emoji, string dialogue)
        {
            if (_dialoguePanel == null) return;
            _currentNPC = npc;
            _dialogueNameText.text = $"{emoji}  {npc.npcName}";
            _dialogueText.text = dialogue;
            _dialoguePanel.SetActive(true);
            _isDialogueOpen = true;
            if (_fadeRoutine != null) StopCoroutine(_fadeRoutine);
            _fadeRoutine = StartCoroutine(FadeGroup(_dialoguePanel.GetComponent<CanvasGroup>(), 0f, 1f));
        }

        public static void CloseDialogue()
        {
            Instance?._CloseDialogueInternal();
        }

        private void _CloseDialogueInternal()
        {
            _isDialogueOpen = false;
            _currentNPC = null;
            if (_dialoguePanel != null) _dialoguePanel.SetActive(false);
        }

        private void HideDialogueImmediate()
        {
            if (_dialoguePanel != null) _dialoguePanel.SetActive(false);
        }

        #endregion

        private IEnumerator FadeGroup(CanvasGroup cg, float from, float to, System.Action onComplete = null)
        {
            cg.alpha = from;
            float elapsed = 0f;
            while (elapsed < fadeDuration)
            {
                elapsed += Time.unscaledDeltaTime;
                cg.alpha = Mathf.Lerp(from, to, elapsed / fadeDuration);
                yield return null;
            }
            cg.alpha = to;
            onComplete?.Invoke();
        }
    }
}
