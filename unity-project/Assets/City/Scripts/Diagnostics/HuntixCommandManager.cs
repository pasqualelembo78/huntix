// Adattato da SanAndreasUnity — Copyright (c) 2015 James King, MIT.
// Sorgente: Assets/Scripts/Commands/CommandManager.cs del repository upstream
// https://github.com/in0finite/SanAndreasUnity (LICENSE: THIRD_PARTY_NOTICES.md,
// sezione 1). NON deriva dal fork proprietario Grand-Road-Auto.
// Modifiche Huntix: rimossi Player/Mirror/NetStatus e i permessi server;
// registri locali, limiti per-comando, messaggi in italiano, comando "prof"
// collegato a PerfProbe. Nessun comando puo' cambiare il mondo o i progressi.
using System;
using System.Collections.Generic;
using UnityEngine;

namespace City.Diagnostics
{
    /// <summary>Parser di comandi locali per la console QA. Pattern MIT di
    /// SanAndreasUnity, riscritto senza dipendenze di rete e senza dati GTA.
    /// </summary>
    public sealed class HuntixCommandManager
    {
        public static HuntixCommandManager Instance { get; private set; }

        static HuntixCommandManager()
        {
            Instance = new HuntixCommandManager();
        }

        public sealed class Context
        {
            public string command;
            public bool debugPermissions;
        }

        public sealed class Result
        {
            public string response;

            public static Result Ok(string message) { return new Result { response = message }; }
            public static Result Unknown { get { return Ok("Comando sconosciuto"); } }
            public static Result Invalid { get { return Ok("Sintassi non valida"); } }
            public static Result NoPermissions { get { return Ok("Comando riservato alla debug"); } }
            public static Result Limit(float seconds)
            {
                return Ok("Comando riutilizzabile tra " + seconds.ToString("F1") + " secondi");
            }
        }

        public struct CommandInfo
        {
            public string command;
            public string description;
            public Func<Context, Result> handler;
            public bool debugOnly;
            public float limitInterval;

            public CommandInfo(string command, string description,
                Func<Context, Result> handler, bool debugOnly, float limitInterval)
            {
                this.command = command;
                this.description = description;
                this.handler = handler;
                this.debugOnly = debugOnly;
                this.limitInterval = limitInterval;
            }
        }

        private readonly Dictionary<string, CommandInfo> _commands =
            new Dictionary<string, CommandInfo>(StringComparer.OrdinalIgnoreCase);
        private readonly Dictionary<string, float> _lastUsed =
            new Dictionary<string, float>(StringComparer.OrdinalIgnoreCase);

        private HuntixCommandManager()
        {
            Register(new CommandInfo("help", "elenca i comandi", ProcessHelp, false, 0f));
            Register(new CommandInfo("prof", "snapshot dei tempi PerfProbe", ProcessProf, true, 1f));
        }

        public IEnumerable<string> RegisteredCommands { get { return _commands.Keys; } }

        public bool Register(CommandInfo info)
        {
            if (info.handler == null || string.IsNullOrWhiteSpace(info.command)) return false;
            string key = info.command.Trim();
            if (_commands.ContainsKey(key)) return false;
            _commands[key] = info;
            return true;
        }

        public bool Remove(string command)
        {
            if (string.IsNullOrWhiteSpace(command)) return false;
            return _commands.Remove(command.Trim());
        }

        public static string[] SplitCommandIntoArguments(string command)
        {
            if (string.IsNullOrWhiteSpace(command)) return new string[0];
            return command.Split(new[] { " ", "\t" }, StringSplitOptions.RemoveEmptyEntries);
        }

        public static string GetRestOfTheCommand(string command, int argumentIndex)
        {
            if (argumentIndex < 0) return "";
            string[] args = SplitCommandIntoArguments(command);
            if (argumentIndex > args.Length - 2) return "";
            return string.Join(" ", args, argumentIndex + 1, args.Length - argumentIndex - 1);
        }

        public Result Process(string command, bool debugPermissions)
        {
            if (string.IsNullOrWhiteSpace(command)) return Result.Invalid;
            string[] args = SplitCommandIntoArguments(command);
            if (args.Length == 0) return Result.Invalid;

            CommandInfo info;
            if (!_commands.TryGetValue(args[0], out info)) return Result.Unknown;
            if (info.debugOnly && !debugPermissions) return Result.NoPermissions;

            float now = Time.realtimeSinceStartup;
            float last;
            if (info.limitInterval > 0f && _lastUsed.TryGetValue(args[0], out last) &&
                now - last < info.limitInterval)
                return Result.Limit(info.limitInterval);
            _lastUsed[args[0]] = now;

            var ctx = new Context { command = command, debugPermissions = debugPermissions };
            return info.handler(ctx);
        }

        private Result ProcessHelp(Context ctx)
        {
            var names = new List<string>(_commands.Keys);
            names.Sort(StringComparer.OrdinalIgnoreCase);
            return Result.Ok("Comandi: " + string.Join(", ", names.ToArray()));
        }

        private Result ProcessProf(Context ctx)
        {
            return Result.Ok(PerfProbe.SnapshotAndReset());
        }
    }
}
