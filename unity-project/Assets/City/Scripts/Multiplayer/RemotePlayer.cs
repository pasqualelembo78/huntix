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
        public string Pet;

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

        // B1: estrapolazione tra due campioni ricevuti (polling ~1s): senza
        // predizione il giocatore remoto sembrerebbe muoversi "a scatti". Si
        // tiene la velocita' di deriva fra l'ultimo e il penultimo campione.
        private Vector3 _sampleVelocity;
        private float _lastSampleTime = -10f;
        private string _lastAnim = "";

        // Componenti
        private Animator _animator;

        // Targhetta nome
        private Transform _nameTag;
        private TMPro.TextMeshPro _nameTagTmp;
        private float _labelRefresh;

        // Pet visibile accanto al giocatore remoto (stato condiviso)
        private Transform _petRoot;

        // Bolla di chat sopra la testa (messaggi P2P in tempo reale)
        private Transform _bubble;
        private TMPro.TextMeshPro _bubbleTmp;
        private float _bubbleUntil;

        public static RemotePlayer Spawn(string id, string username, int level,
                                         string skin, string pet, Vector3 pos, float heading)
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
            rp.Pet = pet ?? "none";
            rp._targetPosition = pos;
            rp._targetHeading = heading;

            // Applica la skin del giocatore (stessa ricetta del player locale)
            PlayerAppearance.ApplyTo(go, skin);

            rp.SetupKinematic();
            rp.SetupAnimator();
            rp.SetupNameTag();
            rp.SpawnPetVisual(rp.Pet);
            rp.SetupBubble();
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
            if (_animator != null)
            {
                // Il modello condiviso non porta il controller Mixamo: assegnarlo
                // (stessa ricetta del player e degli NPC) rende leggibili i
                // trigger Act_* delle emote e il blend Speed della locomozione.
                try
                {
                    RuntimeAnimatorController ctrl =
                        Resources.Load<RuntimeAnimatorController>("Mixamo/PlayerLocomotion");
                    if (ctrl != null && _animator.runtimeAnimatorController == null)
                        _animator.runtimeAnimatorController = ctrl;
                }
                catch (System.Exception e)
                {
                    Debug.LogWarning("[RemotePlayer] controller anim: " + e.Message);
                }
            }
        }

        /// <summary>Mappa un codice di animazione condivisa (anim_state) sulla
        /// clip Mixamo: le emote viaggiano come stringhe corte nell'heartbeat.
        /// Palette accordata con l'HUD locale del MultiplayerManager.</summary>
        public static string MapEmoteClip(string code)
        {
            switch (code)
            {
                case "yes": return "Yes";
                case "no": return "Idle_No_Loop";           // scambio di testa
                case "hit": return "Hit_Knockback";
                case "melee": return "Melee_Hook";
                case "throw": return "OverhandThrow";
                case "consume": return "Consume";           // mangia/snack
                case "phone": return "Idle_TalkingPhone_Loop";
                case "wave": return "Yes";                  // Mixamo senza "Wave"
                default: return "";
            }
        }

        public void SetTarget(Vector3 pos, float heading, string anim)
        {
            // deriva fra il penultimo campione e l'ultimo = velocita' istantanea
            float now = Time.time;
            float dt = now - _lastSampleTime;
            if (dt > 0.02f && dt < 5f)
            {
                _sampleVelocity = (pos - _targetPosition) / dt;
                if (_sampleVelocity.magnitude < 0.05f) _sampleVelocity = Vector3.zero;
            }
            else
            {
                _sampleVelocity = Vector3.zero;
            }
            _targetPosition = pos;
            _targetHeading = heading;
            _lastSampleTime = now;

            if (anim != _lastAnim)
            {
                _lastAnim = anim;
                // B3: emote ricevute ("yes", "hit", "consume"...) → trigger Mixamo.
                if (_animator != null)
                {
                    string clip = MapEmoteClip(anim);
                    if (clip.Length > 0)
                        try { City.Player.PlayerActions.Trigger(_animator, clip); }
                        catch (System.Exception e)
                        { Debug.LogWarning("[RemotePlayer] emote " + clip + ": " + e.Message); }
                }
            }
            _animState = anim;
        }

        public void Interpolate(float speed)
        {
            UpdateFromTarget(false);
            if (speed <= 0f) return;

            // B1: predici la posizione fra un campione e il prossimo, con tetto
            // anti-overshoot; se i dati sono troppo datati/lontani, snap diretto.
            Vector3 dest = _targetPosition;
            float since = Time.time - _lastSampleTime;
            if (_sampleVelocity.sqrMagnitude > 0.0001f && since > 0.02f && since < 2f)
            {
                Vector3 ext = _sampleVelocity * since;
                if (ext.magnitude > 3.5f) ext = ext.normalized * 3.5f;
                dest += ext;
            }
            if (Vector3.Distance(transform.position, dest) > 8f)
            {
                transform.position = dest; // teleport/nascente: snap senza inseguire
            }
            else
            {
                transform.position = Vector3.Lerp(transform.position, dest,
                                                  speed * Time.deltaTime);
            }
            Quaternion targetRot = Quaternion.Euler(0f, _targetHeading, 0f);
            transform.rotation = Quaternion.Slerp(transform.rotation, targetRot,
                                                  speed * Time.deltaTime);

            bool moving = _animState == "walk" || _animState == "run";
            if (_animator != null)
                _animator.SetFloat("Speed", moving ? 1f : 0f);
        }

        /// <summary>Spawna il pet scelto dall'altro giocatore (stato condiviso).
        /// Fissa il modello come figlio dell'avatar: lo segue senza bisogno di
        /// fisica o del seguimento singleton del player locale.</summary>
        private void SpawnPetVisual(string petId)
        {
            if (string.IsNullOrEmpty(petId) || petId == City.Player.PetController.NoneId) return;
            try
            {
                GameObject prefab = Resources.Load<GameObject>("Pets/" + petId);
                if (prefab == null)
                {
                    Debug.LogWarning("[RemotePlayer] pet non trovato: " + petId);
                    return;
                }
                GameObject go = Instantiate(prefab);
                go.name = "RemotePet_" + petId;
                _petRoot = go.transform;
                _petRoot.SetParent(transform, false);
                _petRoot.localPosition = new Vector3(0.55f, 0.35f, -1.3f);
                _petRoot.localScale = Vector3.one * 0.9f;
                _petRoot.localRotation = Quaternion.identity;
                City.Player.PetController.ApplyColormap(go);
                foreach (Collider col in go.GetComponentsInChildren<Collider>())
                    if (col != null) col.enabled = false;
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[RemotePlayer] pet remoto: " + e.Message);
            }
        }

        /// <summary>Mostra un messaggio di chat come bolla sopra la testa
        /// (stile social): compare per qualche secondo accanto al nome.</summary>
        public void ShowChatBubble(string text, float seconds = 4f)
        {
            if (_bubbleTmp == null) return;
            if (string.IsNullOrEmpty(text)) { _bubbleUntil = 0f; return; }
            if (text.Length > 84) text = text.Substring(0, 84) + "\u2026";
            _bubbleTmp.text = text;
            _bubbleUntil = Time.time + seconds;
        }

        private void SetupBubble()
        {
            var go = new GameObject("ChatBubble");
            _bubble = go.transform;
            _bubble.SetParent(transform, false);
            _bubble.localPosition = new Vector3(0f, 3.05f, 0f);
            float sc = Mathf.Max(0.01f, transform.lossyScale.x);
            _bubble.localScale = Vector3.one * (1f / sc);

            var rt = go.AddComponent<RectTransform>();
            rt.sizeDelta = new Vector2(8f, 2.4f);

            _bubbleTmp = go.AddComponent<TMPro.TextMeshPro>();
            _bubbleTmp.fontSize = 2.2f;
            _bubbleTmp.alignment = TMPro.TextAlignmentOptions.Center;
            _bubbleTmp.color = new Color(1f, 1f, 1f);
            go.SetActive(false);
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
            UpdateBubble();
        }

        private void UpdateBubble()
        {
            if (_bubble == null) return;
            bool show = Time.time < _bubbleUntil;
            var cam = Camera.main;
            if (!show || cam == null)
            {
                if (_bubble.gameObject.activeSelf) _bubble.gameObject.SetActive(false);
                return;
            }
            float distSqr = (cam.transform.position - transform.position).sqrMagnitude;
            if (distSqr > 400f || !gameObject.activeSelf)
            {
                if (_bubble.gameObject.activeSelf) _bubble.gameObject.SetActive(false);
                return;
            }
            if (!_bubble.gameObject.activeSelf) _bubble.gameObject.SetActive(true);
            _bubble.rotation = cam.transform.rotation;
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
            if (_petRoot != null) Destroy(_petRoot.gameObject);
            Destroy(gameObject);
        }
    }
}