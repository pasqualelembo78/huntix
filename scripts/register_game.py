#!/usr/bin/env python3
"""register_game.py — registra un nuovo minigioco nei file di Huntix.

Aggiunge (se non già presenti):
  1. MiniGameManager.kt: const val GAME_* e voce in ALL_GAME_IDS
  2. GameLevels.kt:      const val GAME_* e Def(...) di default
  3. MiniGamesHubActivity.kt: GameEntry nella lista giochi

Uso:
  register_game.py --project <radice> --template <path> --id <game_id>
                   [--label <Label>] [--emoji <emoji>]
"""
import argparse
import re
from pathlib import Path


def add_to_minigame_manager(path: Path, gid: str) -> bool:
    text = path.read_text()
    if f"GAME_{gid.upper()}" in text:
        return False
    const = f'    const val GAME_{gid.upper()}      = "{gid}"'
    # Inserisci la const dopo l'ultima const val GAME_* presente
    consts = list(re.finditer(r'^(\s*)const val (GAME_\w+)\s*=.*$', text, re.M))
    if not consts:
        return False
    last = consts[-1]
    text = text[:last.end()] + "\n" + const + text[last.end():]
    # Aggiungi a ALL_GAME_IDS
    m = re.search(r'ALL_GAME_IDS\s*=\s*listOf\((.*?)\n\s*\)', text, re.S)
    if m:
        block = m.group(1)
        text = text[:m.start(1)] + block.rstrip() + f",\n        GAME_{gid.upper()}" + text[m.end(1):]
    path.write_text(text)
    return True


def add_to_game_levels(path: Path, gid: str) -> bool:
    text = path.read_text()
    if f"GAME_{gid.upper()}" in text:
        return False
    const = f'    const val GAME_{gid.upper()}      = "{gid}"'
    consts = list(re.finditer(r'^(\s*)const val (GAME_\w+)\s*=.*$', text, re.M))
    if consts:
        last = consts[-1]
        text = text[:last.end()] + "\n" + const + text[last.end():]
    # Aggiungi Def di default dopo l'ultima Def
    defs = list(re.finditer(r'^\s*Def\(GAME_\w+,.*?\),?$', text, re.M))
    if defs:
        last = defs[-1]
        line = last.group(0)
        comma = "," if not line.rstrip().endswith(",") else ""
        new_def = f'        Def(GAME_{gid.upper()},      "punti",     Mode.SCORE, base = 100, step = 100),'
        text = text[:last.end()] + "\n" + new_def + text[last.end():]
        # Assicura che la riga precedente termini con virgola
        text = text.replace(line + "\n" + new_def, line + comma + "\n" + new_def)
    path.write_text(text)
    return True


def add_to_hub(path: Path, gid: str, label: str, emoji: str) -> bool:
    text = path.read_text()
    if f"GAME_{gid.upper()}" in text:
        return False
    # Inserisci dopo l'ULTIMA GameEntry esistente
    entries = list(re.finditer(r'^(\s*)GameEntry\(MiniGameManager\.GAME_\w+.*?\)(,\s*)$', text, re.M))
    if not entries:
        entries = list(re.finditer(r'GameEntry\(.*?\)(,\s*)$', text, re.M))
    if not entries:
        return False
    last = entries[-1]
    entry = f'{last.group(1)}GameEntry(MiniGameManager.GAME_{gid.upper()}, "{label}", "{emoji}", {label}Activity::class.java, null),'
    text = text[:last.end()] + "\n" + entry + text[last.end():]
    path.write_text(text)
    return True


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True)
    ap.add_argument("--template", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--label", default="")
    ap.add_argument("--emoji", default="🎮")
    args = ap.parse_args()

    label = args.label or args.id.title()
    base = Path(args.project)
    mg = base / "app/src/main/java/com/intelligame/huntix/managers/MiniGameManager.kt"
    gl = base / "app/src/main/java/com/intelligame/huntix/managers/GameLevels.kt"
    hub = base / "app/src/main/java/com/intelligame/huntix/MiniGamesHubActivity.kt"

    results = {
        "MiniGameManager": add_to_minigame_manager(mg, args.id),
        "GameLevels": add_to_game_levels(gl, args.id),
        "MiniGamesHubActivity": add_to_hub(hub, args.id, label, args.emoji),
    }
    for f, changed in results.items():
        print(f"  {f}: {'aggiunto' if changed else 'già presente'}")


if __name__ == "__main__":
    main()
