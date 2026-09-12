namespace City.Death
{
    /// <summary>Modalita' di morte che il giocatore puo' scegliere dal menu
    /// dedicato. Ognuna corrisponde a una rappresentazione scenica diversa
    /// e a un FamilyManager.DeathType dell'esito.</summary>
    public enum DeathMode
    {
        INVESTIMENTO, // un'auto spunta e ti investe
        SPARI,        // un NPC spunta e ti spara
        PESTAGGIO,    // piu' NPC spuntano e ti picchiano
        CADUTA        // cadi da un palazzo
    }

    public static class DeathModeInfo
    {
        public static string Label(DeathMode m)
        {
            switch (m)
            {
                case DeathMode.INVESTIMENTO: return "MORTO INVESTITO";
                case DeathMode.SPARI:        return "MORTO SPARATO";
                case DeathMode.PESTAGGIO:    return "MORTO PESTATO";
                case DeathMode.CADUTA:       return "CADUTA DAL PALAZZO";
                default:                     return "MORTE";
            }
        }

        public static string Description(DeathMode m)
        {
            switch (m)
            {
                case DeathMode.INVESTIMENTO: return "Appare un'auto dal nulla e ti mette sotto.";
                case DeathMode.SPARI:        return "Un malvivente ti spara da vicino.";
                case DeathMode.PESTAGGIO:    return "Una banda ti circonda e ti picchia a morte.";
                case DeathMode.CADUTA:       return "Ti ritrovi sul tetto e cadi nel vuoto.";
                default:                     return "";
            }
        }

        public static NPC.FamilyManager.DeathType Outcome(DeathMode m)
        {
            switch (m)
            {
                case DeathMode.CADUTA: return NPC.FamilyManager.DeathType.CADUTA;
                case DeathMode.INVESTIMENTO:
                case DeathMode.SPARI:
                case DeathMode.PESTAGGIO:
                default:              return NPC.FamilyManager.DeathType.INCIDENTE;
            }
        }
    }
}
