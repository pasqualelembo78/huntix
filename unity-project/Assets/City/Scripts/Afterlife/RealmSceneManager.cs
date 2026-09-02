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
    /// viene sostituita da sola (niente UnloadScene, non present in stub).
    /// </summary>
    public class RealmSceneManager : MonoBehaviour
    {
        public static RealmSceneManager Instance { get; private set; }

        public const string CitySceneName = "City";
        public const string InfernoSceneName = "InfernoScene";
        public const string PurgatorioSceneName = "PurgatorioScene";
        public const string ParadisoSceneName = "ParadisoScene";

        public RealmSceneController ActiveRealm { get; private set; }
        public AfterlifeRealm ActiveRealmId { get; private set; }

        private AfterlifeRealm _pending = AfterlifeRealm.INFERNO;

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
                BuildRealm(_pending);
            }
        }

        /// <summary>Entra nel regno indicato: costruisce subito l'arena e carica la sua scena.</summary>
        public void EnterRealm(AfterlifeRealm realm)
        {
            _pending = realm;
            if (Camera.main != null)
                Camera.main.backgroundColor = RealmColors.Sky(realm);
            if (TryLoadScene(SceneNameFor(realm)))
                return;
            // La scena del regno non e' nelle Build Settings (es. APK installato
            // non ricompilato). Non crashare: costruisci l'arena nella scena
            // corrente, cosi' l'afterlife funziona comunque senza LoadScene.
            BuildRealm(realm);
        }

        /// <summary>Torna alla citta' (fine del ciclo afterlife: reincarnazione).</summary>
        public void ReturnToCity()
        {
            if (TryLoadScene(CitySceneName))
                return;
            LeaveRealm();
        }

        /// <summary>
        /// Carica la scena se disponibile (presente nelle Build Settings).
        /// Se LoadScene fallisce (scena non in build, es. regni non ricompilati)
        /// ritorna false senza lanciare eccezioni.
        /// </summary>
        private static bool TryLoadScene(string name)
        {
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
            if (ActiveRealm != null) { ActiveRealm.TearDown(); ActiveRealm = null; }
            ActiveRealmId = realm;
            ActiveRealm = RealmSceneController.Build(realm);
            PositionPlayerOnRealm();
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