using UnityEngine;
using UnityEngine.SceneManagement;

namespace City.Afterlife
{
    /// <summary>
    /// Gestore dei regni come SCENE UNITY SEPARATE. E' persistente
    /// (DontDestroyOnLoad) e reagisce a SceneManager.sceneLoaded: quando la
    /// scena di un regno finisce di caricare, costruisce l'arena del regno
    /// riusando RealmSceneController (piattaforme/pericoli/atmosfera).
    ///
    /// Flusso afterlife (orchestrato da FamilyHost):
    ///   InfernoScene -> PurgatorioScene -> ParadisoScene -> citta' (reincarn.)
    /// Ogni caricamento usa LoadScene in modalita' Single: la scena precedente
    /// viene sostituita da sola (niente UnloadScene, not present in stub).
    /// </summary>
    public class RealmSceneManager : MonoBehaviour
    {
        /// <summary>Destinatario del pulsante SALTA della HUD citta' quando il
        /// regno Inferno e' una SCENA ESTERNA (gioco FloorIsLava): il player
        /// della citta' in quel momento e' nascosto, quindi il bridge del gioco
        /// registra qui la palla e il pulsante continua a funzionare.</summary>
        public interface IInfernoJumpInput
        {
            void BeginJump();
            void EndJump();
        }

        public static RealmSceneManager Instance { get; private set; }

        /// <summary>Target del pulsante SALTA durante i regni esterni (vedi
        /// IInfernoJumpInput). Null in citta' o con l'arena procedurale.</summary>
        public static IInfernoJumpInput InfernoJumpInput { get; set; }

        public const string CitySceneName = "City";
        public const string InfernoSceneName = "InfernoScene";
        public const string PurgatorioSceneName = "PurgatorioScene";
        public const string ParadisoSceneName = "ParadisoScene";

        public RealmSceneController ActiveRealm { get; private set; }
        public AfterlifeRealm ActiveRealmId { get; private set; }

        /// <summary>Callback di fine minigioco Inferno: parametro true = portale
        /// raggiunto (vittoria), false = vite esaurite (fallimento). Viene
        /// invocato da InfernoGame e consumato da FamilyHost per avanzare.</summary>
        public System.Action<bool> InfernoFinished { get; set; }

        private AfterlifeRealm _pending = AfterlifeRealm.INFERNO;

        /// <summary>Consumato da OnSceneLoaded: quando carichiamo la scena del
        /// prossimo regno (o la citta') il fade viene tolto a scena pronta.</summary>
        private bool _fadePending;

        /// <summary>Scena regno/citta' in precaricamento (LoadSceneAsync con
        /// allowSceneActivation=false). La scena si carica in BACKGROUND
        /// mentre il fade va a nero: quando sia il fade sia il load sono pronti
        /// (Update) si attiva la scena, cosi' il cambio scena non congela
        /// il player per secondi.</summary>
        private AsyncOperation _preloadOp;
        private bool _preloadFadeDone;

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

        private void OnEnable() { SceneManager.sceneLoaded += OnSceneLoaded; }
        private void OnDisable() { SceneManager.sceneLoaded -= OnSceneLoaded; }

        /// <summary>Assicura che il gestore esista (chiamato da FamilyHost).</summary>
        public static RealmSceneManager Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("RealmSceneManager");
            DontDestroyOnLoad(go);
            return go.AddComponent<RealmSceneManager>();
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            string name = scene.name;
            if (name == CitySceneName)
            {
                LeaveRealm();
            }
            else if (name == SceneNameFor(_pending) &&
                     (name == InfernoSceneName || name == PurgatorioSceneName || name == ParadisoSceneName))
            {
                if (name == InfernoSceneName)
                    PrepareExternalInferno();
                else
                    BuildRealm(_pending);
            }
            _preloadOp = null;
            _preloadFadeDone = false;
            if (_fadePending)
            {
                _fadePending = false;
                try { City.UI.ScreenFader.FadeFromBlackGlobal(null); }
                catch (System.Exception e) { Debug.LogWarning("[RealmSceneManager] fade in: " + e.Message); }
            }
        }

        /// <summary>Polling del precaricamento: quando il fade e' completo e la
        /// scena ha finito di caricare i dati (progress >= 0.9), attiva il
        /// cambio scena. OnSceneLoaded completera' il setup e togliera' il fade.</summary>
        private void Update()
        {
            if (_preloadOp == null) return;
            if (!_preloadFadeDone) return;
            if (_preloadOp.allowSceneActivation) return;

            if (_preloadOp.isDone || _preloadOp.progress >= 0.9f)
            {
                Debug.Log("[RealmSceneManager] scena precaricata, attivazione.");
                _preloadOp.allowSceneActivation = true;
            }
        }

        /// <summary>Avvia il precaricamento della scena in background:
        /// la carica senza attivarla (allowSceneActivation=false) cosi' la
        /// scena attuale resta visibile e il cambio non blocca i frame.
        /// Restituisce false se la scena non e' nelle Build Settings o il
        /// load asincrono non e' disponibile: in quel caso si ripiega sul
        /// flusso storico (LoadScene sincrono o arena inline).</summary>
        private bool StartPreload(string sceneName)
        {
            if (string.IsNullOrEmpty(sceneName)) return false;
            if (!Application.CanStreamedLevelBeLoaded(sceneName)) return false;
            try
            {
                var op = SceneManager.LoadSceneAsync(sceneName, LoadSceneMode.Single);
                if (op == null) return false;
                op.allowSceneActivation = false;
                _preloadOp = op;
                _preloadFadeDone = false;
                Debug.Log("[RealmSceneManager] precaricamento scena '" + sceneName + "' in background.");
                return true;
            }
            catch (System.Exception)
            {
                return false;
            }
        }

        /// <summary>Entra nel regno indicato: costruisce subito l'arena e carica la sua scena.
        /// La transizione e' coperta da un fade a nero (ScreenFader globale) cosi'
        /// il cambio scena (citta->Inferno, regno->regno) non e' uno stacco secco.
        /// La scena del regno viene PRECARICATA in background durante il fade
        /// (LoadSceneAsync): niente freeze del player a schermo nero.</summary>
        public void EnterRealm(AfterlifeRealm realm)
        {
            _pending = realm;
            string scene = SceneNameFor(realm);
            try
            {
                if (Camera.main != null)
                    Camera.main.backgroundColor = RealmColors.Sky(realm);

                // Precaricamento: la scena del regno parte a caricarsi subito,
                // in parallelo al fade. All'attivazione (Update) si fa il setup.
                if (StartPreload(scene))
                {
                    _fadePending = true;
                    City.UI.ScreenFader.FadeToBlackGlobal(() => { _preloadFadeDone = true; });
                    return;
                }

                // Scena non precaricabile (es. APK installato non ricompilato):
                // comportamento storico -> fade, load sincrono o arena inline.
                City.UI.ScreenFader.FadeToBlackGlobal(() =>
                {
                    try
                    {
                        if (TryLoadScene(scene))
                        {
                            _fadePending = true;
                            return;
                        }
                    }
                    catch (System.Exception e)
                    {
                        Debug.LogWarning("[RealmSceneManager] EnterRealm: " + e.Message);
                    }
                    City.UI.ScreenFader.FadeFromBlackGlobal(null);
                    // La scena del regno non e' nelle Build Settings... costruisci
                    // l'arena nella scena corrente, cosi' l'afterlife funziona comunque.
                    try { BuildRealm(realm); }
                    catch (System.Exception e) { Debug.LogWarning("[RealmSceneManager] BuildRealm: " + e.Message); }
                });
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[RealmSceneManager] fade out: " + e.Message);
                try { BuildRealm(realm); }
                catch (System.Exception e2) { Debug.LogWarning("[RealmSceneManager] BuildRealm: " + e2.Message); }
            }
        }

        /// <summary>Torna alla citta' (fine del ciclo afterlife: reincarnazione).
        /// Transizione coperta dal fade a nero e precaricamento come per i regni.</summary>
        public void ReturnToCity()
        {
            InfernoFinished = null;
            try
            {
                if (StartPreload(CitySceneName))
                {
                    _fadePending = true;
                    City.UI.ScreenFader.FadeToBlackGlobal(() => { _preloadFadeDone = true; });
                    return;
                }
                City.UI.ScreenFader.FadeToBlackGlobal(() =>
                {
                    _fadePending = true;
                    if (TryLoadScene(CitySceneName)) return;
                    _fadePending = false;
                    City.UI.ScreenFader.FadeFromBlackGlobal(null);
                    LeaveRealm();
                });
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[RealmSceneManager] ReturnToCity: " + e.Message);
                if (TryLoadScene(CitySceneName)) return;
                LeaveRealm();
            }
        }

        /// <summary>
        /// Carica la scena se disponibile (presente nelle Build Settings).
        /// Controlla prima con CanStreamedLevelBeLoaded per non far loggare a
        /// Unity l'errore "scena non nelle build settings" a ogni morte/regno
        /// (i regni Inferno/Purgatorio/Paradiso possono non essere ricompilati
        /// in un APK installato). Se la scena non e' caricabile ritorna false
        /// senza eccezioni ne' log d'errore: il flusso ripiega sull'arena
        /// costruita inline (BuildRealm).
        /// </summary>
        private static bool TryLoadScene(string name)
        {
            if (string.IsNullOrEmpty(name)) return false;
            if (!Application.CanStreamedLevelBeLoaded(name)) return false;
            try
            {
                SceneManager.LoadScene(name);
                return true;
            }
            catch (System.Exception)
            {
                return false;
            }
        }

        private void BuildRealm(AfterlifeRealm realm)
        {
            RestoreCityPlayer();
            if (ActiveRealm != null) { ActiveRealm.TearDown(); ActiveRealm = null; }
            ActiveRealmId = realm;
            ActiveRealm = RealmSceneController.Build(realm);
            PositionPlayerOnRealm();
            InfernusKitInstaller.Apply(ActiveRealm.ArenaRoot.gameObject);
        }

        /// <summary>
        /// Riposiziona il player (persistente DontDestroyOnLoad) sul pavimento
        /// dell'arena appena costruita, cosi' non resta sospeso/inside nel vuoto
        /// al cambio scena regno. Il pavimento del regno e' a y=-0.5 (slab 14x1x14).
        /// </summary>
        private void PositionPlayerOnRealm()
        {
            var pc = City.Player.PlayerController.Instance;
            if (pc == null) return;
            var cc = pc.GetComponent<CharacterController>();
            if (cc != null) cc.enabled = false;
            pc.transform.position = new Vector3(0f, 0.2f, 0f);
            pc.transform.rotation = Quaternion.identity;
            if (cc != null) cc.enabled = true;
            pc.Stop();
        }

        private void LeaveRealm()
        {
            if (ActiveRealm != null) { ActiveRealm.TearDown(); ActiveRealm = null; }
            RestoreCityPlayer();
        }

        /// <summary>
        /// L'InfernoScene ora e' il GIOCO COMPLETO third-party "Floor is Lava -
        /// Bowser's Castle": niente arena procedurale. Il bridge del gioco
        /// (FloorIsLava.InfernoBridge, fuori harness) rileva vittoria (tag
        /// FloorVictory) e morte (lava) e chiama InfernoFinished per avanzare
        /// al Purgatorio. Il player/camera della citta' vengono nascosti cosi'
        /// non restano fantasma dentro il livello del gioco.
        /// </summary>
        private void PrepareExternalInferno()
        {
            if (ActiveRealm != null) { ActiveRealm.TearDown(); ActiveRealm = null; }
            ActiveRealmId = _pending;
            HideCityPlayer();
        }

        /// <summary>Nasconde il player e la camera persistenti della citta':
        /// nella scena esterna del gioco il 'Mario Ball' e la sua camera sono i
        /// soli attori visibili. Riaperti quando si passa a Purgatorio/Citta'.</summary>
        private void HideCityPlayer()
        {
            try
            {
                var pc = City.Player.PlayerController.Instance;
                if (pc != null) pc.gameObject.SetActive(false);
            }
            catch (System.Exception) { }
            try
            {
                var rig = City.Player.CameraRig.Instance;
                if (rig != null) rig.gameObject.SetActive(false);
            }
            catch (System.Exception) { }
        }

        private void RestoreCityPlayer()
        {
            try
            {
                var pc = City.Player.PlayerController.Instance;
                if (pc != null) pc.gameObject.SetActive(true);
            }
            catch (System.Exception) { }
            try
            {
                var rig = City.Player.CameraRig.Instance;
                if (rig != null) rig.gameObject.SetActive(true);
            }
            catch (System.Exception) { }
        }

        public static string SceneNameFor(AfterlifeRealm realm)
        {
            switch (realm)
            {
                case AfterlifeRealm.INFERNO:    return InfernoSceneName;
                case AfterlifeRealm.PURGATORIO: return PurgatorioSceneName;
                case AfterlifeRealm.PARADISO:   return ParadisoSceneName;
                default:                        return CitySceneName;
            }
        }

        private void OnDestroy() { if (Instance == this) Instance = null; }
    }
}