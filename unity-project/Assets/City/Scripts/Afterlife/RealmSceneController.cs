using UnityEngine;

namespace City.Afterlife
{
    /// <summary>
    /// Regni dell'aldilà. Quando il player muore attraversa questi tre mondi
    /// come mini-giochi distinti (generati a runtime), poi si reincarna.
    /// Ogni regno ha palette/atmosfera/ostacoli propri:
    ///   - INFERNO   : fuoco e oscurità, pozze laviche, pericoloso.
    ///   - PURGATORIO: ascesa a piattaforme grigie e dense.
    ///   - PARADISO  : nuvole e pace, cielo chiaro e sereno.
    /// </summary>
    public enum AfterlifeRealm
    {
        INFERNO,
        PURGATORIO,
        PARADISO
    }

    /// <summary>
    /// Palette visiva dei regni, usata per luce, cielo e materiali.
    /// </summary>
    public static class RealmColors
    {
        public static Color Sky(AfterlifeRealm realm)
        {
            switch (realm)
            {
                case AfterlifeRealm.INFERNO:    return new Color(0.12f, 0.02f, 0.02f); // rosso cupo
                case AfterlifeRealm.PURGATORIO: return new Color(0.28f, 0.28f, 0.28f); // grigio denso
                case AfterlifeRealm.PARADISO:   return new Color(0.55f, 0.85f, 1.00f); // azzurro sereno
                default:                        return new Color(0.2f, 0.2f, 0.2f);
            }
        }

        public static Color Light(AfterlifeRealm realm)
        {
            switch (realm)
            {
                case AfterlifeRealm.INFERNO:    return new Color(1.00f, 0.40f, 0.15f); // fuoco
                case AfterlifeRealm.PURGATORIO: return new Color(0.75f, 0.75f, 0.75f); // neutra
                case AfterlifeRealm.PARADISO:   return new Color(1.00f, 1.00f, 0.96f); // serena
                default:                        return new Color(0.8f, 0.8f, 0.8f);
            }
        }

        public static Color Platform(AfterlifeRealm realm)
        {
            switch (realm)
            {
                case AfterlifeRealm.INFERNO:    return new Color(0.45f, 0.05f, 0.02f); // roccia lavica
                case AfterlifeRealm.PURGATORIO: return new Color(0.50f, 0.50f, 0.50f); // pietra grigia
                case AfterlifeRealm.PARADISO:   return new Color(0.95f, 0.97f, 1.00f); // nuvola chiara
                default:                        return new Color(0.6f, 0.6f, 0.6f);
            }
        }
    }

    /// <summary>
    /// Controller dell'arena del regno corrente. Viene instanziato da
    /// FamilyHost quando il player entra in un regno e distrutto alla
    /// transizione successiva. Genera piattaforme/pericoli con primitive Unity
    /// e applica cielo/luce del regno. La dipendenza da RenderSettings e'
    /// volutamente assente: si usano solo API compilabili dalla harness
    /// (Camera.main.backgroundColor + Light + primitive).
    /// </summary>
    public class RealmSceneController : MonoBehaviour
    {
        public AfterlifeRealm Realm { get; private set; }

        private GameObject _root;
        private Color _cameraBefore;

        /// <summary>Crea l'arena procedurale per il regno dato.</summary>
        public static RealmSceneController Build(AfterlifeRealm realm)
        {
            var go = new GameObject("Realm_" + realm);
            go.transform.position = new Vector3(0, 0, 0);
            var ctrl = go.AddComponent<RealmSceneController>();
            ctrl.Realm = realm;
            ctrl.Init();
            return ctrl;
        }

        private void Init()
        {
            if (Camera.main != null)
            {
                _cameraBefore = Camera.main.backgroundColor;
                Camera.main.backgroundColor = RealmColors.Sky(Realm);
            }

            _root = new GameObject("Root");

            var lightGo = new GameObject("RealmLight");
            lightGo.transform.parent = _root.transform;
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = RealmColors.Light(Realm);
            lightGo.transform.rotation = Quaternion.Euler(50f, -30f, 0f);

            BuildPlatforms();
        }

        private void BuildPlatforms()
        {
            int count = Realm == AfterlifeRealm.INFERNO ? 7
                      : Realm == AfterlifeRealm.PURGATORIO ? 9 : 6;

            BuildPlatform(new Vector3(0, -1f, 0), new Vector3(14f, 1f, 14f));

            for (int i = 0; i < count; i++)
            {
                float x = Mathf.Sin(i * 1.7f) * 6f;
                float z = Mathf.Cos(i * 1.3f) * 6f;
                float y = 0.5f + i * 0.7f;
                BuildPlatform(new Vector3(x, y, z), new Vector3(2f, 0.4f, 2f));

                if (Realm == AfterlifeRealm.INFERNO)
                    BuildHazard(x * 0.7f, -0.4f, z * 0.6f);
            }
        }

        private void BuildPlatform(Vector3 pos, Vector3 size)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = "Platform";
            go.transform.parent = _root.transform;
            go.transform.position = pos;
            go.transform.localScale = size;
            var rend = go.GetComponent<Renderer>();
            if (rend != null) rend.material.color = RealmColors.Platform(Realm);

            // Purgatorio: le piattaforme si muovono su/giu (ascesa), cosi' il
            // mini-gioco richiede tempismo (3.4).
            if (Realm == AfterlifeRealm.PURGATORIO)
            {
                var mp = go.AddComponent<MovingPlatform>();
                mp.amplitude = 1.2f;
                mp.period = 2.4f;
                mp.phase = pos.x * 0.5f;
                mp.basePos = pos;
            }
        }

        private void BuildHazard(float x, float y, float z)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            go.name = "LavaPuddle";
            go.transform.parent = _root.transform;
            go.transform.position = new Vector3(x, y, z);
            go.transform.localScale = new Vector3(0.8f, 0.4f, 0.8f);
            var rend = go.GetComponent<Renderer>();
            if (rend != null) rend.material.color = new Color(1f, 0.4f, 0.05f);
            var coll = go.GetComponent<Collider>();
            if (coll != null) coll.isTrigger = true;
            go.AddComponent<LavaDamage>();
        }

        /// <summary>Ripristina il cielo e distrugge l'arena.</summary>
        public void TearDown()
        {
            if (Camera.main != null)
                Camera.main.backgroundColor = _cameraBefore;
            if (_root != null) Object.Destroy(_root);
        }
    }
}