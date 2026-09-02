using UnityEngine;

namespace City.Player
{
    /// <summary>
    /// Pet di compagnia (kit Kenney cube-pets) scelto nel profilo Android.
    /// Segue il giocatore nella scena City (miacitta') mantenendosi a un
    /// offset laterale/indietro e facendo un piccolo hop quando il player
    /// si muove. Il modello (FBX con colormap condivisa, come i veicoli)
    /// viene caricato da Resources/Pets/ e la texture colormap applicata
    /// ai materiali URP/Lit se mancante.
    /// </summary>
    public class PetController : MonoBehaviour
    {
        public const string PrefKey = "pet_skin";
        public const string DefaultPet = "dog";
        // "none" = nessun pet selezionato (off).
        public const string NoneId = "none";

        /// <summary>Id del pet attualmente salvato sul device.</summary>
        public static string SavedPet
        {
            get
            {
                string s = PlayerPrefs.GetString(PrefKey, "");
                return string.IsNullOrEmpty(s) ? NoneId : s;
            }
        }

        [Header("Follow")]
        public float followDistance = 1.6f;
        public float followHeight = 0.4f;
        public float sideOffset = 0.55f;
        public float moveSpeed = 6f;
        public float turnSpeed = 10f;
        public float hopDuration = 0.42f;
        public float hopHeight = 0.18f;
        public float modelScale = 0.9f;

        private Transform followTarget;
        private Vector3 _prevPlayerPos;
        private float _hopT;
        private Vector3 _baseLocalPos;

        private static GameObject _petRoot;
        private static PetController _instance;
        public static PetController Instance { get { return _instance; } }

        private void Awake()
        {
            _instance = this;
        }

        private void Start()
        {
            _baseLocalPos = transform.localPosition;
            RefreshTarget();
        }

        /// <summary>Aggiorna il riferimento al player da seguire.</summary>
        public void RefreshTarget()
        {
            if (PlayerController.Instance != null)
                followTarget = PlayerController.Instance.transform;
        }

        private void Update()
        {
            if (followTarget == null)
            {
                if (PlayerController.Instance != null)
                    followTarget = PlayerController.Instance.transform;
                return;
            }

            bool moving = (followTarget.position - _prevPlayerPos).magnitude > 0.005f;
            _prevPlayerPos = followTarget.position;

            // Posizione "ancora" dietro e di lato al player, proiettata sul piano
            Vector3 fwd = followTarget.forward;
            fwd.y = 0f;
            if (fwd.sqrMagnitude < 0.001f) fwd = Vector3.forward;
            fwd.Normalize();
            Vector3 right = Vector3.Cross(Vector3.up, fwd);
            Vector3 anchor = followTarget.position
                + fwd * -followDistance
                + right * sideOffset;
            anchor.y = followTarget.position.y + followHeight;

            Vector3 toAnchor = anchor - transform.position;
            toAnchor.y = 0f;
            Vector3 targetPos = transform.position;
            if (toAnchor.magnitude > 0.05f)
            {
                Vector3 step = toAnchor.normalized * Mathf.Min(moveSpeed * Time.deltaTime, toAnchor.magnitude);
                targetPos += step;
            }
            targetPos.y = Mathf.Lerp(targetPos.y, anchor.y, 8f * Time.deltaTime);
            transform.position = targetPos;

            // Orientamento verso il player
            Vector3 look = followTarget.position - transform.position;
            look.y = 0f;
            if (look.sqrMagnitude > 0.001f)
            {
                Quaternion targetRot = Quaternion.LookRotation(look, Vector3.up);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRot, turnSpeed * Time.deltaTime);
            }

            // Piccolo hop quando il player si muove
            if (moving)
            {
                _hopT += Time.deltaTime;
                if (_hopT >= hopDuration) _hopT = 0f;
            }
            else
            {
                _hopT = 0f;
            }
            float y = Mathf.Sin(_hopT / hopDuration * Mathf.PI) * hopHeight;
            Vector3 lp = transform.localPosition;
            lp.y = _baseLocalPos.y + y;
            transform.localPosition = lp;
        }

        /// <summary>
        /// Applica la colormap condivisa ai materiali del modello pet
        /// (stessa ricetta di FixVehicleMaterials: URP/Lit con _BaseMap).
        /// </summary>
        public static void ApplyColormap(GameObject root)
        {
            if (root == null) return;
            Texture2D colormap = Resources.Load<Texture2D>("Pets/Textures/colormap");
            var urp = Shader.Find("Universal Render Pipeline/Lit");
            if (urp == null) urp = Shader.Find("Standard");
            foreach (Renderer r in root.GetComponentsInChildren<Renderer>())
            {
                if (r == null) continue;
                Material[] mats = r.sharedMaterials;
                for (int i = 0; i < mats.Length; i++)
                {
                    Material m = mats[i];
                    if (m == null) { if (urp != null) { mats[i] = new Material(urp); m = mats[i]; } else continue; }
                    else if (m.shader == null || (!m.shader.name.Contains("Universal") && !m.shader.name.Contains("URP")))
                    {
                        if (urp != null) m.shader = urp;
                    }
                    if (colormap != null && m.HasProperty("_BaseMap") && m.GetTexture("_BaseMap") == null)
                        m.SetTexture("_BaseMap", colormap);
                    else if (colormap != null && m.HasProperty("_MainTex") && m.GetTexture("_MainTex") == null)
                        m.SetTexture("_MainTex", colormap);
                }
                r.sharedMaterials = mats;
            }
        }

        /// <summary>
        /// Spawna il modello del pet scelto (o rimuove il precedente) sotto
        /// root. Resta in vita seguendo il player finche' il giocatore non
        /// cambia pet o esce dalla scena. Ritorna il PetController creato.
        /// </summary>
        public static PetController Spawn(string petId, Transform parent)
        {
            Despawn();
            if (string.IsNullOrEmpty(petId) || petId == NoneId) return null;
            if (parent == null && PlayerController.Instance != null)
                parent = PlayerController.Instance.transform;

            GameObject prefab = Resources.Load<GameObject>("Pets/" + petId);
            if (prefab == null)
            {
                Debug.LogWarning("[PetController] pet non trovato in Resources/Pets/: " + petId);
                return null;
            }
            GameObject go = Object.Instantiate(prefab);
            go.name = "Pet_" + petId;
            go.transform.localScale = Vector3.one * 0.9f;
            _petRoot = go;

            // Posiziona accanto al player (o in un punto sensato se non c'e')
            Vector3 basePos = parent != null ? parent.position : Vector3.zero;
            basePos.y += 0.5f;
            go.transform.position = basePos;
            go.transform.rotation = Quaternion.identity;

            ApplyColormap(go);

            // Stacca collider per non interferire coi pedoni/veicoli
            foreach (Collider col in go.GetComponentsInChildren<Collider>())
            {
                if (col != null) col.enabled = false;
            }

            // Sopravvive ai reload di scena e rifolgia il player ovunque
            Object.DontDestroyOnLoad(go);

            PetController pc = go.AddComponent<PetController>();
            return pc;
        }

        /// <summary>Rimuove il pet se presente.</summary>
        public static void Despawn()
        {
            if (_petRoot != null)
            {
                Object.Destroy(_petRoot);
                _petRoot = null;
            }
            _instance = null;
        }
    }
}
