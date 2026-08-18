using System.Collections.Generic;
using UnityEngine;

namespace City.NPC
{
    /// <summary>
    /// Pool di skin per NPC. Ogni skin e' un insieme di colori (pelle, camicia, pantaloni)
    /// usati da NPCController per costruire il modello procedurale.
    /// Quando Kenney Animated Characters sara' importato, si puo' estendere con prefab reali.
    /// </summary>
    public static class NPCModelPool
    {
        public struct Skin
        {
            public Color skin;
            public Color shirt;
            public Color pants;
            public float scale;
        }

        private static readonly Skin[] skins = new Skin[]
        {
            new Skin { skin = new Color(0.87f, 0.72f, 0.58f), shirt = new Color(0.2f, 0.4f, 0.8f), pants = new Color(0.2f, 0.2f, 0.3f), scale = 1f },
            new Skin { skin = new Color(0.75f, 0.55f, 0.40f), shirt = new Color(0.8f, 0.2f, 0.2f), pants = new Color(0.15f, 0.15f, 0.2f), scale = 1f },
            new Skin { skin = new Color(0.60f, 0.42f, 0.30f), shirt = new Color(0.2f, 0.7f, 0.3f), pants = new Color(0.25f, 0.2f, 0.15f), scale = 0.95f },
            new Skin { skin = new Color(0.90f, 0.75f, 0.60f), shirt = new Color(0.9f, 0.7f, 0.1f), pants = new Color(0.3f, 0.3f, 0.35f), scale = 1.05f },
            new Skin { skin = new Color(0.82f, 0.65f, 0.50f), shirt = new Color(0.6f, 0.2f, 0.7f), pants = new Color(0.2f, 0.2f, 0.25f), scale = 1f },
            new Skin { skin = new Color(0.70f, 0.50f, 0.35f), shirt = new Color(0.1f, 0.6f, 0.7f), pants = new Color(0.18f, 0.18f, 0.22f), scale = 0.98f },
            new Skin { skin = new Color(0.85f, 0.70f, 0.55f), shirt = new Color(0.9f, 0.5f, 0.1f), pants = new Color(0.22f, 0.22f, 0.28f), scale = 1.02f },
            new Skin { skin = new Color(0.65f, 0.45f, 0.32f), shirt = new Color(0.3f, 0.3f, 0.6f), pants = new Color(0.15f, 0.15f, 0.2f), scale = 0.97f },
        };

        public static Skin GetRandom(System.Random rng)
        {
            return skins[rng.Next(skins.Length)];
        }

        public static int Count => skins.Length;
    }
}
