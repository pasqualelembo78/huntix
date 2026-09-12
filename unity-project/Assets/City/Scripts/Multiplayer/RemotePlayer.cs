using UnityEngine;
using City.Player;
using City.NPC;

namespace City.Multiplayer
{
    /// <summary>
    /// Un altro giocatore visibile nel mondo: stessa ricetta dei pedoni NPC
    /// (modello characterMedium + collider kinematico + nome flottante)
    /// ma pilotato dai dati realtime ricevuti dal server city_realtime.
    ///
    /// Non ha input locale, nessuna fisica propria: interpola posizione e
    /// rotazione verso il target ricevuto dal MultiplayerManager.
    /// </summary>
    public class RemotePlayer : MonoBehaviour
    {
        public string PlayerId;
        public string Username;
        public int Level;
        public string Skin;

        /// <summary>Nome del prefab del modello giocatore in Resources.</summary>
        public const string ModelPath = "Characters/characterMedium";

        private static GameObject _prefab;
        public static GameObject Prefab
        {
            get
            {
                if (_prefab == null)
                    _prefab = Resources.Load<GameObject>(ModelPath);
                return _prefab;
            }
            set { _prefab = value; }
        }

        // Stato interpolato (target)
        private Vector3 _targetPosition;
        private float _targetHeading;
        private string _animState = "idle";

        // Componenti
        private Animator _animator;

        // Targhetta nome
        private Transform _nameTag;
        private TMPro.TextMeshPro _nameTagTmp;
        private float _labelRefresh;

        public static RemotePlayer Spawn(string id, string username, int level,
                                         string skin, Vector3 pos, float heading)
        {
            if (Prefab == null)
            {
                Debug.LogWarning("[RemotePlayer] prefab non trovato: " + ModelPath);
                return null;
            }

            GameObject go = Instantiate(Prefab);
            go.name = "RemotePlayer_" + username;
            // Fase 5: tag dedicato per distinguerli dai pedoni NPC.
            // Il tag "RemotePlayer" deve esistere in ProjectSettings Tags;
            // se manca l'assegnazione e' safe (catch).
            try { go.tag = "RemotePlayer"; }
            catch (System.Exception e)
            { Debug.LogWarning("[RemotePlayer] tag mancante: " + e.Message); }

            // Rimuovi il controllo locale e la fisica del giocatore
            var pc = go.GetComponent<PlayerController>();
            if (pc != null) Destroy(pc);
            var cc = go.GetComponent<CharacterController>();
            if (cc != null) Destroy(cc);

            var rp = go.AddComponent<RemotePlayer>();
            rp.PlayerId = id;
            rp.Username = username;
            rp.Level = level;
            rp.Skin = skin;
            rp._targetPosition = pos;
            rp._targetHeading = heading;

            // Applica la skin del giocatore (stessa ricetta del player locale)
            PlayerAppearance.ApplyTo(go, skin);

            rp.SetupKinematic();
            rp.SetupAnimator();
            rp.SetupNameTag();
            rp.UpdateFromTarget(true);

            return rp;
        }

        private void SetupKinematic()
        {
            // Collider kinematico (come i pedoni): non spinto dalla fisica
            var capsule = gameObject.GetComponent<CapsuleCollider>();
            if (capsule == null)
            {
                capsule = gameObject.AddComponent<CapsuleCollider>();
                capsule.height = 1.7f;
                capsule.radius = 0.25f;
                capsule.center = new Vector3(0f, 0.85f, 0f);
            }

            var rb = gameObject.GetComponent<Rigidbody>();
            if (rb == null) rb = gameObject.AddComponent<Rigidbody>();
            rb.isKinematic = true;
            rb.useGravity = false;
        }

        private void SetupAnimator()
        {
            _animator = gameObject.GetComponentInChildren<Animator>();
        }

        public void SetTarget(Vector3 pos, float heading, string anim)
        {
            _targetPosition = pos;
            _targetHeading = heading;
            _animState = anim;
        }

        public void Interpolate(float speed)
        {
            UpdateFromTarget(false);
            if (speed <= 0f) return;

            transform.position = Vector3.Lerp(transform.position, _targetPosition,
                                              speed * Time.deltaTime);
            Quaternion targetRot = Quaternion.Euler(0f, _targetHeading, 0f);
            transform.rotation = Quaternion.Slerp(transform.rotation, targetRot,
                                                  speed * Time.deltaTime);

            bool moving = _animState == "walk" || _animState == "run";
            if (_animator != null)
                _animator.SetFloat("Speed", moving ? 1f : 0f);
        }

        /// <summary>Ritorna immediatamente alla posizione target (es. primo spawn).</summary>
        private void UpdateFromTarget(bool snap)
        {
            if (!snap) return;
            transform.position = _targetPosition;
            transform.rotation = Quaternion.Euler(0f, _targetHeading, 0f);
        }

        public void ApplyVisibility(bool visible)
        {
            if (gameObject.activeSelf != visible)
                gameObject.SetActive(visible);
        }

        private void SetupNameTag()
        {
            var go = new GameObject("NameTag");
            _nameTag = go.transform;
            _nameTag.SetParent(transform, false);
            _nameTag.localPosition = new Vector3(0f, 2.6f, 0f);
            float sc = Mathf.Max(0.01f, transform.lossyScale.x);
            _nameTag.localScale = Vector3.one * (1f / sc);

            var rt = go.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(7f, 2f);

            _nameTagTmp = go.AddComponent<TMPro.TextMeshPro>();
            _nameTagTmp.fontSize = 2.4f;
            _nameTagTmp.alignment = TMPro.TextAlignmentOptions.Center;
            _nameTagTmp.color = new Color(0.4f, 0.8f, 1f); // azzurro player
            go.SetActive(false);
        }

        private void Update()
        {
            UpdateNameTag();
        }

        private void UpdateNameTag()
        {
            if (_nameTag == null) return;
            var cam = Camera.main;
            if (cam == null)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }

            float distSqr = (cam.transform.position - transform.position).sqrMagnitude;
            bool show = distSqr < 400f;
            if (!show)
            {
                if (_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(false);
                return;
            }
            if (!_nameTag.gameObject.activeSelf) _nameTag.gameObject.SetActive(true);

            _nameTag.rotation = cam.transform.rotation;
            if (Time.unscaledTime < _labelRefresh) return;
            _labelRefresh = Time.unscaledTime + 0.5f;
            _nameTagTmp.text = PlayerName;
        }

        private string PlayerName
        {
            get
            {
                return Username + "\n<size=60%>Lv." + Level + "</size>";
            }
        }

        public void SafeDestroy()
        {
            Destroy(gameObject);
        }
    }
}