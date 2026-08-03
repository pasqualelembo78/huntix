#!/usr/bin/env python3
"""
fetch_open_source_games.py — Scarica e analizza repository di giochi
open-source da GitHub per adattarli all'architettura di Huntix.

Uso:
    python3 fetch_open_source_games.py                    # Scarica i repo predefiniti
    python3 fetch_open_source_games.py --all              # Scarica tutti i repo
    python3 fetch_open_source_games.py --repo <url>       # Scarica un repo specifico
    python3 fetch_open_source_games.py --analyze <path>   # Analizza un repo scaricato
    python3 fetch_open_source_games.py --template <path>  # Genera template per un gioco

Output:
    - downloads/         — repository clonati
    - analysis/          — report di analisi per ogni repo
    - templates/         — template Kotlin per adattare i giochi
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# ── Configurazione ──────────────────────────────────────────

DOWNLOAD_DIR = Path(__file__).parent.parent / "downloads"
ANALYSIS_DIR = Path(__file__).parent.parent / "analysis"
TEMPLATES_DIR = Path(__file__).parent.parent / "templates"

DEFAULT_REPOS = [
    # Tetris
    {
        "name": "AndroidTetris",
        "url": "https://github.com/SpiritualForest/AndroidTetris.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "canvas",
        "description": "Tetris Android con engine decoupled",
    },
    {
        "name": "Simplis",
        "url": "https://github.com/gabrielrovesti/Simplis.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "compose",
        "description": "Tetris moderno con Jetpack Compose e Canvas",
    },
    # Space Invaders
    {
        "name": "AstroAdventures",
        "url": "https://github.com/thelumiereguy/AstroAdventures-Android.git",
        "language": "Kotlin",
        "license": "Apache-2.0",
        "type": "canvas",
        "description": "Space Invaders clone con Canvas custom",
    },
    # Brick Breaker
    {
        "name": "BrickBreaker",
        "url": "https://github.com/cpinan/BrickBreakerAndroidProject.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "surfaceview",
        "description": "Brick Breaker da zero con Canvas",
    },
    # Sliding Puzzle
    {
        "name": "SlidingPuzzle",
        "url": "https://github.com/zmuzik/sliding-puzzle.git",
        "language": "Kotlin",
        "license": "Apache-2.0",
        "type": "customview",
        "description": "Sliding puzzle 15 pezzi",
    },
    # Sokoban
    {
        "name": "Sokoban",
        "url": "https://github.com/haukesomm/Sokoban.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "web",
        "description": "Sokoban con logica core in Kotlin",
    },
    # Word Search
    {
        "name": "WordSearchPuzzleView",
        "url": "https://github.com/akherbouch/WordSearchPuzzleView.git",
        "language": "Kotlin",
        "license": "Apache-2.0",
        "type": "customview",
        "description": "Word Search puzzle view personalizzabile",
    },
    # Pac-Man
    {
        "name": "PacmanAndroid",
        "url": "https://github.com/isaiahnoelsalazar/PacmanAndroid.git",
        "language": "Java",
        "license": "MIT",
        "type": "canvas",
        "description": "Pac-Man Android clone",
    },
    # Memory
    {
        "name": "MemoryGame",
        "url": "https://github.com/ItsFRZ/TicTacToe.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "native",
        "description": "Tic Tac Toe con minimax (adattabile a Memory)",
    },
    # Connect Four
    {
        "name": "ConnectFour",
        "url": "https://github.com/avan1235/mini-games.git",
        "language": "Kotlin",
        "license": "MIT",
        "type": "multiplatform",
        "description": "Mini-giochi Kotlin Multiplatform (include Connect Four)",
    },
]

# ── Utility ─────────────────────────────────────────────────

def run(cmd, cwd=None, capture=True):
    """Esegue un comando shell e restituisce l'output."""
    result = subprocess.run(
        cmd, shell=True, cwd=cwd,
        capture_output=capture, text=True
    )
    return result.returncode, result.stdout, result.stderr


def clone_repo(repo, dest):
    """Clona un repository GitHub."""
    dest.mkdir(parents=True, exist_ok=True)
    url = repo["url"]
    name = repo["name"]
    print(f"📦 Clonando {name}...")
    rc, out, err = run(f"git clone --depth 1 {url} {dest}")
    if rc != 0:
        print(f"  ❌ Errore: {err.strip()}")
        return False
    print(f"  ✅ Clonato in {dest}")
    return True


def detect_language(root):
    """Rileva il linguaggio principale del progetto."""
    ext_counts = {}
    for f in root.rglob("*"):
        if f.is_file() and not any(part.startswith(".") for part in f.relative_to(root).parts):
            ext = f.suffix.lower()
            ext_counts[ext] = ext_counts.get(ext, 0) + 1
    if not ext_counts:
        return "unknown"
    return max(ext_counts, key=ext_counts.get)


def find_kotlin_files(root):
    """Trova tutti i file Kotlin nel progetto."""
    return list(root.rglob("*.kt"))


def find_java_files(root):
    """Trova tutti i file Java nel progetto."""
    return list(root.rglob("*.java"))


def find_game_logic(files):
    """Identifica i file che contengono logica di gioco."""
    game_keywords = [
        "game", "game loop", "update", "draw", "render",
        "onTouch", "onClick", "collision", "score", "level",
        "gameOver", "startGame", "reset", "pause", "resume",
        "SurfaceView", "Canvas", "onDraw", "Handler", "postDelayed",
        "gameState", "gameRunning", "gameLoop", "tick",
        "tetris", "tetromino", "piece", "board",
        "snake", "food", "direction", "move",
        "brick", "ball", "paddle", "block",
        "puzzle", "swap", "match", "grid",
        "memory", "card", "flip", "match",
        "hangman", "word", "guess", "letter",
        "sokoban", "push", "box", "warehouse",
        "pacman", "ghost", "maze", "dot",
        "space", "invader", "ship", "shoot",
        "connect", "four", "drop", "column",
    ]
    results = []
    for f in files:
        try:
            content = f.read_text(encoding="utf-8", errors="ignore")
            matches = [kw for kw in game_keywords if kw.lower() in content.lower()]
            if len(matches) >= 3:
                results.append({
                    "file": str(f.relative_to(f.parent.parent.parent)),
                    "matches": matches,
                    "lines": len(content.splitlines()),
                })
        except Exception:
            pass
    return results


def analyze_repo(repo, dest):
    """Analizza un repository scaricato."""
    print(f"\n🔍 Analizzando {repo['name']}...")

    analysis = {
        "name": repo["name"],
        "url": repo["url"],
        "language": repo.get("language", "unknown"),
        "license": repo.get("license", "unknown"),
        "type": repo.get("type", "unknown"),
        "description": repo.get("description", ""),
        "files": [],
        "game_logic_files": [],
        "total_lines": 0,
        "adaptability": "unknown",
        "adaptation_notes": [],
    }

    kt_files = find_kotlin_files(dest)
    java_files = find_java_files(dest)
    all_files = kt_files + java_files

    analysis["total_files"] = len(all_files)
    analysis["kotlin_files"] = len(kt_files)
    analysis["java_files"] = len(java_files)

    for f in all_files:
        try:
            lines = len(f.read_text(encoding="utf-8", errors="ignore").splitlines())
            analysis["total_lines"] += lines
            analysis["files"].append({
                "path": str(f.relative_to(dest)),
                "lines": lines,
            })
        except Exception:
            pass

    # Ordina per dimensione
    analysis["files"].sort(key=lambda x: x["lines"], reverse=True)

    # Trova logica di gioco
    analysis["game_logic_files"] = find_game_logic(all_files)

    # Valuta adattabilità
    notes = []
    if analysis["total_lines"] > 0:
        if any("Canvas" in f.get("path", "") or "canvas" in f.get("path", "") for f in analysis["files"]):
            notes.append("Usa Canvas Android — adattabile a MiniGameBase")
        if any("SurfaceView" in f.get("path", "") or "surfaceview" in f.get("path", "") for f in analysis["files"]):
            notes.append("Usa SurfaceView — adattabile a MiniGameBase")
        if any("Jetpack Compose" in f.get("path", "") or "compose" in f.get("path", "").lower() for f in analysis["files"]):
            notes.append("Usa Jetpack Compose — richiede conversione a Canvas")
        if any("game" in f.get("path", "").lower() for f in analysis["game_logic_files"]):
            notes.append("Logica di gioco identificabile — facile da adattare")
        if any("View" in f.get("path", "") for f in analysis["files"]):
            notes.append("Usa View custom — pattern compatibile con Huntix")

    if not notes:
        notes.append("Nessun pattern riconosciuto — adattamento manuale necessario")

    analysis["adaptability"] = "high" if len([n for n in notes if "adattabile" in n or "compatibile" in n]) > 0 else "medium"
    analysis["adaptation_notes"] = notes

    return analysis


def generate_template(analysis, output_dir):
    """Genera un template Kotlin per adattare il gioco."""
    name = analysis["name"]
    game_id = re.sub(r'[^a-zA-Z0-9]', '_', name).lower()

    template = f'''package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry

/**
 * 🎮 {name} — adattato da open-source.
 *
 * Originale: {analysis.get("description", "")}
 * Licenza: {analysis.get("license", "unknown")}
 * Fonte: {analysis.get("url", "")}
 *
 * Adattamento: logica di gioco preservata,
 * rendering convertito al pattern Canvas di Huntix.
 */
class {name}Activity : MiniGameBase() {{

    // ── Costanti del gioco ──────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private var gameRunning = false
    private var score = 0

    // ── Stato del gioco ─────────────────────────────────────

    // TODO: Aggiungere le variabili di stato del gioco originale

    override fun onGameCreate() {{
        // TODO: Inizializzare il gioco
        // Esempio:
        // val ctx = this
        // val root = LinearLayout(ctx).apply {{
        //     orientation = LinearLayout.VERTICAL
        //     setBackgroundColor(Color.parseColor(UiKit.BG))
        //     setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        // }}
        // root.addView(UiKit.title(ctx, "{name}", "🎮"))
        // ...
        // setContentView(root)
        // startGame()
    }}

    // ── Game Loop ────────────────────────────────────────────

    private fun startGame() {{
        // TODO: Reset dello stato del gioco
        score = 0
        gameRunning = true
        // TODO: Avviare il game loop
        // handler.postDelayed(gameLoop, tickMs)
    }}

    private val gameLoop = object : Runnable {{
        override fun run() {{
            if (!gameRunning) return
            // TODO: Aggiornare la logica di gioco
            // TODO: Disegnare il frame
            // handler.postDelayed(this, tickMs)
        }}
    }}

    // ── Rendering ────────────────────────────────────────────

    // TODO: Implementare onDraw() con il rendering Canvas
    // Seguire il pattern di SnakeActivity.kt o CardGameBase.kt

    // ── Input ────────────────────────────────────────────────

    // TODO: Implementare onTouchEvent() per il controllo del gioco

    // ── Fine gioco ───────────────────────────────────────────

    private fun endGame(won: Boolean) {{
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = if (won) (score * 0.2f).toInt().coerceAtLeast(10) else kotlin.math.max(score / 5, 3)
        val xp = if (won) (score * 0.1f).toInt().coerceAtLeast(5) else kotlin.math.max(score / 8, 2)

        try {{
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_{game_id.upper()}, score,
                mvc = mvc, xp = xp,
                label = "{name}: ${{if (won) "vittoria!" else "sconfitta"}}",
                isWin = won
            )
        }} catch (e: Exception) {{ Sentry.captureException(e) }}

        // TODO: Mostrare overlay di fine gioco
    }}

    override fun onDestroy() {{
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }}
}}
'''

    output_dir.mkdir(parents=True, exist_ok=True)
    output_file = output_dir / f"{name}Activity.kt.template"
    output_file.write_text(template)
    print(f"  📝 Template generato: {output_file}")
    return str(output_file)


# ── Main ────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Scarica e analizza repository di giochi open-source per Huntix"
    )
    parser.add_argument("--all", action="store_true", help="Scarica tutti i repo")
    parser.add_argument("--repo", type=str, help="URL di un repo specifico")
    parser.add_argument("--analyze", type=str, help="Analizza un repo già scaricato")
    parser.add_argument("--template", type=str, help="Genera template per un gioco")
    parser.add_argument("--list", action="store_true", help="Lista i repo disponibili")
    args = parser.parse_args()

    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    ANALYSIS_DIR.mkdir(parents=True, exist_ok=True)
    TEMPLATES_DIR.mkdir(parents=True, exist_ok=True)

    if args.list:
        print("📋 Repository disponibili:\n")
        for i, repo in enumerate(DEFAULT_REPOS, 1):
            print(f"  {i}. {repo['name']} ({repo['language']}) - {repo['description']}")
            print(f"     URL: {repo['url']}")
            print(f"     Licenza: {repo['license']} | Tipo: {repo['type']}")
            print()
        return

    if args.analyze:
        path = Path(args.analyze)
        if not path.exists():
            print(f"❌ Percorso non trovato: {path}")
            sys.exit(1)
        # Cerca il repo info
        repo_info = next((r for r in DEFAULT_REPOS if r["name"].lower() in path.name.lower()), None)
        if repo_info is None:
            repo_info = {"name": path.name, "url": "local", "language": "unknown", "license": "unknown", "type": "unknown", "description": "Local repo"}
        analysis = analyze_repo(repo_info, path)
        out_file = ANALYSIS_DIR / f"{path.name}_analysis.json"
        out_file.write_text(json.dumps(analysis, indent=2, default=str))
        print(f"\n📊 Analisi salvata in: {out_file}")
        print(f"   File: {analysis['total_files']}")
        print(f"   Righe: {analysis['total_lines']}")
        print(f"   Logica di gioco: {len(analysis['game_logic_files'])} file")
        print(f"   Adattabilità: {analysis['adaptability']}")
        for note in analysis["adaptation_notes"]:
            print(f"   • {note}")
        return

    if args.template:
        path = Path(args.template)
        if not path.exists():
            print(f"❌ Percorso non trovato: {path}")
            sys.exit(1)
        repo_info = next((r for r in DEFAULT_REPOS if r["name"].lower() in path.name.lower()), None)
        if repo_info is None:
            repo_info = {"name": path.name, "url": "local", "description": "Local repo", "license": "unknown"}
        analysis = analyze_repo(repo_info, path)
        generate_template(analysis, TEMPLATES_DIR)
        return

    if args.repo:
        repo = next((r for r in DEFAULT_REPOS if args.repo in r["url"]), None)
        if repo is None:
            repo = {"name": args.repo, "url": args.repo, "language": "unknown", "license": "unknown", "type": "unknown", "description": "Custom repo"}
        dest = DOWNLOAD_DIR / repo["name"]
        if clone_repo(repo, dest):
            analysis = analyze_repo(repo, dest)
            out_file = ANALYSIS_DIR / f"{repo['name']}_analysis.json"
            out_file.write_text(json.dumps(analysis, indent=2, default=str))
            generate_template(analysis, TEMPLATES_DIR)
        return

    # Default: scarica tutti i repo
    print("🚀 Download di tutti i repository di giochi open-source...\n")
    for repo in DEFAULT_REPOS:
        dest = DOWNLOAD_DIR / repo["name"]
        if dest.exists():
            print(f"  ⏭️  {repo['name']} già scaricato, saltato")
            continue
        clone_repo(repo, dest)
        analysis = analyze_repo(repo, dest)
        out_file = ANALYSIS_DIR / f"{repo['name']}_analysis.json"
        out_file.write_text(json.dumps(analysis, indent=2, default=str))
        generate_template(analysis, TEMPLATES_DIR)
        print()

    print("✅ Completato!")
    print(f"   📦 Downloads: {DOWNLOAD_DIR}")
    print(f"   📊 Analisi: {ANALYSIS_DIR}")
    print(f"   📝 Template: {TEMPLATES_DIR}")


if __name__ == "__main__":
    main()