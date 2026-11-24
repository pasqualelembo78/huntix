// Comandi QA per le migliorie addittive dell'audit open source. Non sono
// collegati a valute, drop o Firestore: solo preferenze, report e uova-indizio.
using System;

namespace City.Diagnostics
{
    /// <summary>
    /// Comandi della console QA (chiusa di default) per il controllo fine delle
    /// singole opzioni. L'attivazione di tutte in una volta avviene dal menu
    /// hamburger con "Opzioni audit open source". Ogni comando passa dal ponte
    /// condiviso, quindi stato, uovo e telemetria sono identici per tutti.
    /// </summary>
    public static class OpenSourceFeatureCommands
    {
        private const string Place = "città→audit open source";

        public static void Register()
        {
            HuntixCommandManager.Instance.Register(new HuntixCommandManager.CommandInfo(
                "osfeature", "osfeature <id> on|off — opzioni audit open source",
                Toggle, true, 0f));
            HuntixCommandManager.Instance.Register(new HuntixCommandManager.CommandInfo(
                "osaudit", "osaudit <asset-handles|resource-defs|load-lifecycle|asset-packs>",
                Audit, true, 1f));
        }

        private static HuntixCommandManager.Result Toggle(HuntixCommandManager.Context ctx)
        {
            string[] args = HuntixCommandManager.SplitCommandIntoArguments(ctx.command);
            if (args.Length != 3)
                return HuntixCommandManager.Result.Ok("Uso: osfeature <id> on|off");
            bool on = args[2].Equals("on", StringComparison.OrdinalIgnoreCase) ||
                      args[2] == "1";
            string label = OpenSourceFeatureBridge.LabelFor(args[1]);
            if (string.IsNullOrEmpty(label))
                return HuntixCommandManager.Result.Ok("Id sconosciuto: " + args[1]);
            OpenSourceFeatureBridge.SetToggle(args[1], on, label, Place);
            return HuntixCommandManager.Result.Ok(args[1] + " -> " + (on ? "on" : "off"));
        }

        private static HuntixCommandManager.Result Audit(HuntixCommandManager.Context ctx)
        {
            string[] args = HuntixCommandManager.SplitCommandIntoArguments(ctx.command);
            if (args.Length != 2)
                return HuntixCommandManager.Result.Ok("Uso: osaudit <sorgente>");
            string report = OpenSourceAudit.Run(args[1]);
            return HuntixCommandManager.Result.Ok(report);
        }
    }
}
