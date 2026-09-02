using System.Collections.Generic;
using UnityEngine;
using City.Player;

namespace City.NPC
{
    /// <summary>
    /// Fabbrica di "persone vere" (modello Kenney characterMedium con skin
    /// PNG) per i momenti in cui servono singoli passanti fuori dal sistema
    /// di popolamento NPC: i passeggeri dei taxi (sale/scende) e la
    /// trasformazione del passeggero sceso in un cittadino che continua a
    /// camminare per strada (non sparisce piu').
    /// </summary>
    public static class CityCharacterFactory
    {
        private static GameObject _prefab;

        // Tutte le skin cittadine disponibili (24M + 24F): i passeggeri dei
        // taxi e i cittadini liberati raramente condividono lo stesso PNG.
        private static readonly string[] Skins =
        {
            "citizenM01","citizenM02","citizenM03","citizenM04","citizenM05",
            "citizenM06","citizenM07","citizenM08","citizenM09","citizenM10",
            "citizenM11","citizenM12","citizenM13","citizenM14","citizenM15",
            "citizenM16","citizenM17","citizenM18","citizenM19","citizenM20",
            "citizenM21","citizenM22","citizenM23","citizenM24",
            "citizenF01","citizenF02","citizenF03","citizenF04","citizenF05",
            "citizenF06","citizenF07","citizenF08","citizenF09","citizenF10",
            "citizenF11","citizenF12","citizenF13","citizenF14","citizenF15",
            "citizenF16","citizenF17","citizenF18","citizenF19","citizenF20",
            "citizenF21","citizenF22","citizenF23","citizenF24",
        };

        // tetto al numero di "orfani" (passeggeri lasciati in strada): una
        // sessione lunga non deve accumulare pedoni all'infinito
        private const int MaxOrphans = 50;
        private static readonly List<GameObject> Orphans =
            new List<GameObject>();

        /// <summary>Istanzi un personaggio "passeggero" sotto parent.
        /// Ritorna il modello e, se richiesto, il camminatore procedurale.</summary>
        public static PassengerModel SpawnPassengerModel(Transform parent,
            bool walker, System.Random rng)
        {
            if (rng == null) rng = new System.Random();
            GameObject prefab = LoadPrefab();
            if (prefab == null) return null;
            GameObject go = Object.Instantiate(prefab, parent);
            if (go == null) return null;
            Cleanup(go, rng);
            ApplySkin(go, Skins[rng.Next(Skins.Length)]);
            CharacterWalker w = null;
            if (walker) w = CharacterWalker.AttachIfNeeded(go);
            return new PassengerModel { go = go, walker = w };
        }

        /// <summary>
        /// Rilascia un passeggero appena sceso: resta nel mondo e cammina
        /// per la strada come cittadino.
        /// </summary>
        public static void ReleaseToStreet(Transform modelRoot)
        {
            if (modelRoot == null) return;
            modelRoot.SetParent(null, true);
            modelRoot.localScale = Vector3.one * 0.455f;
            Vector3 pos = modelRoot.position;
            pos.y = 0.12f;
            modelRoot.position = pos;
            modelRoot.rotation = Quaternion.identity;

            RegisterOrphan(modelRoot.gameObject);
            if (modelRoot.GetComponent<CharacterWalker>() == null)
                CharacterWalker.AttachIfNeeded(modelRoot.gameObject);

            NPCController npc = modelRoot.GetComponent<NPCController>();
            if (npc == null)
                npc = modelRoot.gameObject.AddComponent<NPCController>();
            if (npc == null) return;

            // breve pattuglia: si allontana dalla portiera e torna
            Vector3 fwd = modelRoot.right;
            fwd.y = 0f;
            if (fwd.sqrMagnitude < 0.01f) fwd = Vector3.right;
            fwd.Normalize();
            Vector3 side = new Vector3(fwd.z, 0f, -fwd.x);

            Vector3 p0 = modelRoot.position;
            Vector3 p1 = p0 + fwd * 8f + side * 1.2f;
            Vector3 p2 = p0 + fwd * 16f + side * (-0.6f);
            npc.Init(new[] { p0, p1, p2, p1, p0 },
                new System.Random(), "pass_" + (int)(Time.time * 1000f), 0);
            // il cittadino appena sceso dirige anche verso i POI vicini
            npc.EnablePoiWander(true);
        }

        private static GameObject LoadPrefab()
        {
            if (_prefab == null)
                _prefab = Resources.Load<GameObject>(
                    "Characters/characterMedium");
            return _prefab;
        }

        private static void Cleanup(GameObject go, System.Random rng)
        {
            float h = 0.455f + (rng != null ? (float)(rng.NextDouble() - 0.5f) * 0.05f : 0f);
            go.transform.localScale = Vector3.one * h;
            go.transform.localRotation = Quaternion.identity;
            go.transform.localPosition = Vector3.zero;
            Collider[] cols = go.GetComponentsInChildren<Collider>();
            for (int i = 0; i < cols.Length; i++)
                if (cols[i] != null) cols[i].enabled = false;
        }

        private static void ApplySkin(GameObject go, string skinName)
        {
            PlayerAppearance.ApplyTo(go, skinName);
        }

        private static void RegisterOrphan(GameObject go)
        {
            Orphans.Add(go);
            if (Orphans.Count <= MaxOrphans) return;
            GameObject oldest = Orphans[0];
            Orphans.RemoveAt(0);
            if (oldest != null) Object.Destroy(oldest);
        }
    }

    /// <summary>Modello "passeggero" con eventuale camminatore procedurale.</summary>
    public class PassengerModel
    {
        public GameObject go;
        public CharacterWalker walker;
    }
}
