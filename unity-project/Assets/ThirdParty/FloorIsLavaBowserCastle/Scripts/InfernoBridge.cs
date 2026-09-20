using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem.UI;
using UnityEngine.SceneManagement;
using UnityEngine.UI;
using TMPro;

namespace FloorIsLava
{
    /// <summary>
    /// Bridge tra il gioco "Floor is Lava - Bowser's Castle" e il flusso
    /// afterlife di Huntix. Quando InfernoScene (il livello del gioco) viene
    /// caricato come regno INFERNO, questo componente osserva il PlayerController
    /// del gioco e traduce gli esiti in RealmSceneManager.InfernoFinished:
    ///   - vittoria (tag FloorVictory / isGameWon)  -> InfernoFinished(true)
    ///   - morte (tag Death / allowPlayerMovement false) -> InfernoFinished(false)
    /// Fuori dal flusso afterlife (gioco testato da solo) NON interviene:
    /// l'anima continua a ricaricare la scena alla morte come nel gioco originale.
    ///
    /// Si auto-attacca: un hook statico su SceneManager.sceneLoaded crea/regge
    /// la istanza (DontDestroyOnLoad) durante la scena Inferno e la distrugge
    /// appena si passa ad altre scene. Vive nella cartella ThirdParty del gioco
    /// quindi fuori dall'harness check.sh di Assets/City/Scripts.
    /// </summary>
    public class InfernoBridge : MonoBehaviour,
        City.Afterlife.RealmSceneManager.IInfernoJumpInput
    {
        private const string InfernoScene = "InfernoScene";

        private static InfernoBridge _self;

        private PlayerController _player;
        private bool _engaged;
        private bool _resolved;
        private float _resolveAt;
        private bool _resolveWon;
        private float _attachedAt;
        private float _retryAttachUntil;
        private JumpButton _jumpButton;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void AutoHook()
        {
            SceneManager.sceneLoaded += OnSceneLoaded;
        }

        private static void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name != InfernoScene)
            {
                if (_self != null)
                {
                    Destroy(_self.gameObject);
                    _self = null;
                }
                return;
            }

            if (_self == null)
            {
                var go = new GameObject("InfernoBridge");
                DontDestroyOnLoad(go);
                _self = go.AddComponent<InfernoBridge>();
            }
            _self.AttachToPlayer();
        }

/// <summary>Rileva l'anima del gioco (PlayerController) e, se siamo nel
        /// flusso afterlife, sopprime il reset di scena del gioco (la
        /// transizione la fa il RealmSceneManager) e prepara l'input mobile.</summary>
        private void AttachToPlayer()
        {
            _player = FindObjectOfType<PlayerController>();
            if (_player == null)
            {
                _retryAttachUntil = Time.unscaledTime + 6f;
                return;
            }

            var rsm = City.Afterlife.RealmSceneManager.Instance;
            bool inAfterlife = rsm != null && rsm.InfernoFinished != null;
            if (!inAfterlife) return;

            _engaged = true;
            _resolved = false;
            _attachedAt = Time.unscaledTime;
            _player.suppressSceneReload = true;
            City.Afterlife.RealmSceneManager.InfernoJumpInput = this;
            SetupMobileInput();
        }

        /// <summary>IInfernoJumpInput: il pulsante SALTA della HUD citta'
        /// (che persiste in scena) agisce sull'anima del gioco.</summary>
        public void BeginJump()
        {
            if (_player != null) _player.BeginJump();
        }

        public void EndJump()
        {
            if (_player != null) _player.EndJump();
        }

        /// <summary>Garantisce un EventSystem compatibile con l'input system
        /// attivo (activeInputHandler=2, solo il nuovo input). La scena del
        /// gioco ha un EventSystem classico (StandaloneInputModule) che con il
        /// nuovo input NON riceve i touch: senza eventi il joystick resta
        /// morto. Disabilitiamo il modulo classico e installiamo il modulo
        /// input nuovo con le action di default (stesso pattern della città).</summary>
        private static void EnsureEventSystem()
        {
            var es = EventSystem.current;
            if (es == null)
            {
                var go = new GameObject("EventSystem");
                es = go.AddComponent<EventSystem>();
            }

            if (es.GetComponent<InputSystemUIInputModule>() != null) return;

            var old = es.GetComponent<StandaloneInputModule>();
            if (old != null) old.enabled = false;

            var uiModule = es.gameObject.AddComponent<InputSystemUIInputModule>();
            uiModule.AssignDefaultActions();
            Debug.Log("[InfernoBridge] EventSystem predisposto per il nuovo input su InfernoScene.");
        }

        private void SetupMobileInput()
        {
            if (_player.joystick != null && _jumpButton != null) return;
            if (!Application.isMobilePlatform) return;

            try
            {
                EnsureEventSystem();

                var prefab = Resources.Load<GameObject>("Fixed Joystick");
                if (prefab == null)
                {
                    Debug.LogWarning("[InfernoBridge] prefab 'Fixed Joystick' non trovato in Resources!");
                    return;
                }

                var canvasGo = new GameObject("InfernoJoystickCanvas",
                    typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
                var canvas = canvasGo.GetComponent<Canvas>();
                canvas.renderMode = RenderMode.ScreenSpaceOverlay;
                canvas.sortingOrder = 2000;
                var scaler = canvasGo.GetComponent<CanvasScaler>();
                scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
                scaler.referenceResolution = new Vector2(1080f, 1920f);

                var joyGo = Instantiate(prefab, canvasGo.transform, false);
                var joy = joyGo.GetComponent<Joystick>();
                if (joy != null)
                {
                    _player.joystick = joy;
                    Debug.Log("[InfernoBridge] joystick mobile creato (EventSystem ok).");
                }

                if (_jumpButton == null)
                    _jumpButton = CreateJumpButton(canvasGo.transform);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[InfernoBridge] segnaposto mobile: " + e.Message);
            }
        }

        /// <summary>Pulsante SALTA per l'anima: preme/rilascia l'input di salto
        /// del PlayerController del gioco (BeginJump/EndJump). Necessario perche'
        /// il vecchio Input.GetButton("Jump") col nuovo input system non arriva
        /// mai su Android e il pulsante SALTA della citta' (che persiste in
        /// scena) agisce sul PlayerController della citta', nascosto qui.</summary>
        private JumpButton CreateJumpButton(Transform parent)
        {
            var rt = new GameObject("JumpButton", typeof(RectTransform)).GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            rt.anchorMin = new Vector2(1f, 0f);
            rt.anchorMax = new Vector2(1f, 0f);
            rt.pivot = new Vector2(1f, 0f);
            rt.anchoredPosition = new Vector2(-110f, 150f);
            rt.sizeDelta = new Vector2(150f, 150f);

            var bg = rt.gameObject.AddComponent<Image>();
            bg.color = new Color(1f, 1f, 1f, 0.22f);
            bg.raycastTarget = true;

            var label = new GameObject("Label", typeof(RectTransform)).GetComponent<RectTransform>();
            label.SetParent(rt, false);
            label.anchorMin = Vector2.zero;
            label.anchorMax = Vector2.one;
            label.offsetMin = Vector2.zero;
            label.offsetMax = Vector2.zero;
            var tmp = label.gameObject.AddComponent<TextMeshProUGUI>();
            tmp.text = "SALTA";
            tmp.fontSize = 34f;
            tmp.color = Color.white;
            tmp.alignment = TextAlignmentOptions.Center;
            tmp.raycastTarget = false;

            var button = rt.gameObject.AddComponent<JumpButton>();
            button.onDown = _player.BeginJump;
            button.onUp = _player.EndJump;
            Debug.Log("[InfernoBridge] pulsante SALTA mobile creato.");
            return button;
        }

        /// <summary>Registra pressione/rilascio del dito per il salto touch.
        /// internal (non private): piu' affidabile con lo stripping IL2CPP che
        /// aggiunge a runtime AddComponent{T} con tipi annidati privati.</summary>
        internal sealed class JumpButton : MonoBehaviour,
            IPointerDownHandler, IPointerUpHandler, IPointerExitHandler
        {
            public System.Action onDown;
            public System.Action onUp;

            public void OnPointerDown(PointerEventData eventData)
            {
                if (onDown != null) onDown();
            }

            public void OnPointerUp(PointerEventData eventData)
            {
                if (onUp != null) onUp();
            }

            public void OnPointerExit(PointerEventData eventData)
            {
                if (onUp != null) onUp();
            }
        }

        private void Update()
        {
            if (!_engaged && !_resolved && _retryAttachUntil > Time.unscaledTime)
            {
                AttachToPlayer();
                if (!_engaged) return;
            }
            if (!_engaged || _resolved) return;
            if (_player == null) return;

            if (_player.isGameWon && !_resolveWon)
            {
                StartResolve(true, 1.2f);
            }
            else if (!_player.allowPlayerMovement && Time.unscaledTime - _attachedAt >= 1.5f)
            {
                StartResolve(false, 0.6f);
            }

            if (_resolved && Time.unscaledTime >= _resolveAt)
                Fire();
        }

        private void StartResolve(bool won, float delay)
        {
            _resolved = true;
            _resolveWon = won;
            _resolveAt = Time.unscaledTime + delay;
        }

        private void Fire()
        {
            _engaged = false;
            var rsm = City.Afterlife.RealmSceneManager.Instance;
            if (rsm != null && rsm.InfernoFinished != null)
                rsm.InfernoFinished(_resolveWon);
        }

        private void OnDestroy()
        {
            if (_self == this)
            {
                _self = null;
                // libera l'hook del pulsante SALTA della citta': quando la
                // scena esce dal gioco il riferimento non deve restare appeso
                // (e' sempre questo bridge a registrarlo in AttachToPlayer).
                City.Afterlife.RealmSceneManager.InfernoJumpInput = null;
            }
        }
    }
}