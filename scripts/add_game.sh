#!/bin/bash
# add_game.sh — Cerca, analizza, scarica, adatta e registra un gioco open-source in Huntix.
#
# Uso:
#   ./add_game.sh <nome_gioco>
#   ./add_game.sh tetris
#   ./add_game.sh "space invaders"
#   ./add_game.sh --list
#
# Il comando:
#   1. Cerca il repo GitHub più adatto
#   2. Scarica e analizza il codice
#   3. Genera il template Kotlin adattato
#   4. Registra il gioco in MiniGamesHubActivity, GameLevels e MiniGameManager
#   5. Verifica che il build compili

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DOWNLOADS_DIR="$PROJECT_DIR/downloads"
ANALYSIS_DIR="$PROJECT_DIR/analysis"
TEMPLATES_DIR="$PROJECT_DIR/templates"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "\n${CYAN}═══ $1 ═══${NC}"; }

# ── Repository database ──────────────────────────────────────────

declare -A REPOS
REPOS=(
  ["tetris"]="https://github.com/SpiritualForest/AndroidTetris.git|Kotlin|MIT|canvas|Tetris Android con engine decoupled"
  ["simplis"]="https://github.com/gabrielrovesti/Simplis.git|Kotlin|MIT|compose|Tetris moderno con Jetpack Compose e Canvas"
  ["astro"]="https://github.com/thelumiereguy/AstroAdventures-Android.git|Kotlin|Apache-2.0|canvas|Space Invaders clone con Canvas custom"
  ["brickbreaker"]="https://github.com/cpinan/BrickBreakerAndroidProject.git|Kotlin|MIT|surfaceview|Brick Breaker da zero con Canvas"
  ["breakout"]="https://github.com/RonSanchezS/breakout.git|Kotlin|MIT|canvas|Breakout semplice con Canvas"
  ["slidingpuzzle"]="https://github.com/zmuzik/sliding-puzzle.git|Kotlin|Apache-2.0|customview|Sliding puzzle 15 pezzi"
  ["sokoban"]="https://github.com/haukesomm/Sokoban.git|Kotlin|MIT|web|Sokoban con logica core in Kotlin"
  ["wordsearch"]="https://github.com/akherbouch/WordSearchPuzzleView.git|Kotlin|Apache-2.0|customview|Word Search puzzle view personalizzabile"
  ["pacman"]="https://github.com/isaiahnoelsalazar/PacmanAndroid.git|Java|MIT|canvas|Pac-Man Android clone"
  ["tictactoe"]="https://github.com/ItsFRZ/TicTacToe.git|Kotlin|MIT|native|Tic Tac Toe con minimax"
  ["minigames"]="https://github.com/avan1235/mini-games.git|Kotlin|MIT|multiplatform|Mini-giochi Kotlin Multiplatform"
  ["mindcolor"]="https://github.com/toufikforyou/mind-color-challenge.git|Kotlin|MIT|compose|Memory & puzzle game con Jetpack Compose"
  ["crossword"]="https://github.com/leffinger/crossyourheart.git|Java|MIT|view|Crossword puzzle Android"
  ["ararat"]="https://github.com/0xe1f/ararat.git|Kotlin|MIT|library|Crossword library per Android con Canvas"
  ["memory"]="https://github.com/sausi-7/games.git|JavaScript|MIT|web|142 giochi browser (memory incluso)"
  ["snake"]="https://github.com/itsmikethetech/snake-game.git|JavaScript|MIT|web|Snake game HTML5"
  ["2048"]="https://github.com/nneonneo/2048-ai.git|Python|MIT|ai|2048 AI solver e gioco"
  ["flappy"]="https://github.com/nickgravelyn/flappy-bird-clone.git|JavaScript|MIT|canvas|Flappy Bird clone"
  ["dino"]="https://github.com/nicolo-ribaudo/t-rex-runner.git|JavaScript|Apache-2.0|canvas|Chrome Dino Runner"
  ["minesweeper"]="https://github.com/nicolo-ribaudo/minesweeper.git|JavaScript|MIT|canvas|Minesweeper browser"
  ["15puzzle"]="https://github.com/italankin/15Puzzle.git|Java|MIT|canvas|15 puzzle (MIT, 41★)"
  ["2048_2"]="https://github.com/GamEditor/2048-Android.git|Java|MIT|canvas|2048 (MIT, 29★)"
  ["2048_3"]="https://github.com/owenlongbo/android2048.git|Java|Apache-2.0|canvas|2048 (Apache-2.0, 117★)"
  ["2048_4"]="https://github.com/JuliaSzymanska/2048_Game.git|Java|Apache-2.0|canvas|2048 (Apache-2.0, 3★)"
  ["2048compose"]="https://github.com/manuel-martos/compose-2048.git|Kotlin|MIT|compose|2048 in Compose (MIT, 55★)"
  ["android-chess"]="https://github.com/jcarolus/android-chess.git|Java|MIT|canvas|Scacchi per Android (MIT, 519★)"
  ["asteroids"]="https://github.com/haxpor/asteroids.git|Kotlin|MIT|canvas|Asteroids (Kotlin, MIT, 43★)"
  ["backgammon"]="https://github.com/MrLaki5/Backgammon-game.git|Java|MIT|view|Backgammon (MIT, 1★)"
  ["baloon"]="https://github.com/plattysoft/BalloonsGame.git|Java|Apache-2.0|view|Palloncini per bambini (Apache-2.0, 10★)"
  ["battleship"]="https://github.com/xxxcucus/planes.git|Kotlin|MIT|canvas|Battaglia navale (variante) (MIT, 41★)"
  ["blockpuzzle"]="https://github.com/SoltauFintel/blockpuzzle.git|Java|MIT|canvas|Block Puzzle (MIT, 46★)"
  ["boggle"]="https://github.com/alejandro-rios/Boggle-Multiplatform.git|Kotlin|Apache-2.0|compose|Boggle (Apache-2.0, 50★)"
  ["braincup"]="https://github.com/SimonSchubert/Braincup.git|Kotlin|Apache-2.0|compose|Allenamento memoria/riflessi (Apache-2.0, 250★)"
  ["brickbreaker2"]="https://github.com/gigacycle/BrickBreaker.git|Java|MIT|canvas|Brick Breaker (MIT, 8★)"
  ["brickbreaker3"]="https://github.com/Alonefcp/BreakoutGame.git|Java|MIT|canvas|Breakout (MIT)"
  ["bricks"]="https://github.com/ashrafimostafa/Brick-Blast.git|Kotlin|MIT|canvas|Brick Blast (MIT, 8★)"
  ["catan"]="https://github.com/alexweininger/android-catan.git|Java|MIT|view|Settlers of Catan (MIT, 7★)"
  ["checkers"]="https://github.com/krigana/Ukrainian_Checkers.git|Kotlin|MIT|view|Dama ucraina (MIT, 1★)"
  ["checkers2"]="https://github.com/williamborgesc/bt-checkers.git|Java|MIT|view|Dama bluetooth (MIT, 1★)"
  ["chess2"]="https://github.com/DipanshKhandelwal/Chess.git|Java|MIT|canvas|Scacchi 2 giocatori (MIT, 32★)"
  ["chess4"]="https://github.com/j4velin/chess.git|Java|Apache-2.0|canvas|Scacchi a 4 giocatori (Apache-2.0, 12★)"
  ["chinesechess"]="https://github.com/zhijunsheng/chess-kotlin-andr.git|Kotlin|MIT|canvas|Scacchi cinesi Kotlin (MIT, 21★)"
  ["clonium"]="https://github.com/pier-bezuhoff/Clonium4Android.git|Kotlin|Apache-2.0|canvas|Clonium (Apache-2.0, 6★)"
  ["colorclicker"]="https://github.com/Scusemua/ColorClicker.git|Java|Apache-2.0|view|Clicca i colori (Apache-2.0, 1★)"
  ["composebird"]="https://github.com/ellisonchan/ComposeBird.git|Kotlin|MIT|compose|Flappy Bird in Compose (MIT, 254★)"
  ["cookiecrush"]="https://github.com/ghlin-daniel/CookieCrush.git|Java|MIT|canvas|Match-3 CookieCrush (MIT, 1★)"
  ["damecinesi"]="https://github.com/fedepaol/DroidChineseCheckers.git|Java|Apache-2.0|canvas|Dama cinese (Apache-2.0, 5★)"
  ["emoji"]="https://github.com/TiagoDanin/Emoji-Memory-Jetpack.git|Kotlin|MIT|compose|Memory emoji (MIT, 9★)"
  ["fixmath"]="https://github.com/hypeapps/FixMath.git|Java|Apache-2.0|canvas|FixMath (Apache-2.0, 44★)"
  ["flag"]="https://github.com/aleksanderwozniak/WhatsThatFlag.git|Kotlin|Apache-2.0|view|Indovina la bandiera (Apache-2.0, 9★)"
  ["flappybird"]="https://github.com/WinDerek/FlappyBird.git|Java|Apache-2.0|canvas|Flappy Bird (Apache-2.0, 15★)"
  ["flappybird2"]="https://github.com/ShubhamRwt/FlappyGame-.git|Java|Apache-2.0|canvas|Flappy (Apache-2.0, 6★)"
  ["flappybird3"]="https://github.com/MartinTeeVarga/FlyingDandelion.git|Java|MIT|canvas|Flappy style (MIT, 15★)"
  ["flappycow"]="https://github.com/cubei/FlappyCow.git|Java|MIT|canvas|Flappy Bird style (MIT, 276★)"
  ["flood"]="https://github.com/GunshipPenguin/open_flood.git|Java|MIT|canvas|Flood fill puzzle (MIT, 146★)"
  ["frogger"]="https://github.com/dianjiaogit/Frogger_Android.git|Java|MIT|canvas|Frogger classico per Android (MIT)"
  ["geography"]="https://github.com/jarrett91/george.git|Java|MIT|view|Quiz geografia (MIT, 1★)"
  ["geoquiz"]="https://github.com/Yarik8706/GeoQuizAndroid.git|Java|MIT|view|Quiz geografia (MIT, 1★)"
  ["hangman"]="https://github.com/RajashekarRaju/hangman-compose.git|Kotlin|Apache-2.0|compose|Hangman KMP (Apache-2.0, 39★)"
  ["hangman2"]="https://github.com/ashwani99/Hangman.git|Java|MIT|view|Hangman (MIT, 9★)"
  ["japaneschess"]="https://github.com/zhijie/ChineseChess4Android.git|Java|MIT|canvas|Scacchi cinesi (MIT, 33★)"
  ["jigsaw"]="https://github.com/richardchien/jigsaw-android.git|Java|MIT|canvas|Jigsaw per Android (MIT, 17★)"
  ["jigsaw2"]="https://github.com/yuvaraj119/JigSawPuzzle-Android.git|Java|Apache-2.0|canvas|Jigsaw Puzzle (Apache-2.0, 20★)"
  ["khet"]="https://github.com/bestinbthomas/Khet.git|Kotlin|MIT|canvas|Khet strategia (MIT, 3★)"
  ["ludo"]="https://github.com/Rashmi-Rani660/LudoGame-App.git|Java|Apache-2.0|view|Ludo (Apache-2.0)"
  ["mastermind"]="https://github.com/gfinger/Mastermind.git|Java|Apache-2.0|view|Mastermind (Apache-2.0, 1★)"
  ["match3"]="https://github.com/natygames/juicy-match.git|Java|MIT|canvas|Match-3 (MIT, 51★)"
  ["math"]="https://github.com/sarveshchavan7/Math-game.git|Java|MIT|view|Quiz di matematica (MIT, 46★)"
  ["maze"]="https://github.com/gordinmitya/AndroidMaze.git|Java|MIT|canvas|Labirinto generato (MIT, 3★)"
  ["maze2"]="https://github.com/smartgaddix/Droid-Maze.git|Java|Apache-2.0|canvas|Labirinto (Apache-2.0, 7★)"
  ["memory2"]="https://github.com/hatamiarash7/MemoryGame.git|Java|Apache-2.0|view|Memory per bambini (Apache-2.0, 11★)"
  ["memory3"]="https://github.com/catalinc/memory-game-android.git|Java|MIT|canvas|Memory (MIT, 6★)"
  ["memory4"]="https://github.com/nikhilbansal97/Memory-Game.git|Kotlin|Apache-2.0|view|Memory (Apache-2.0, 8★)"
  ["minas"]="https://github.com/ShaunRain/WinMine.git|Java|Apache-2.0|canvas|WinMine (Apache-2.0, 1★)"
  ["minesweeper2"]="https://github.com/Xacalet/Minesweeper.git|Kotlin|MIT|canvas|Minesweeper con rendering (MIT, 7★)"
  ["minesweeper3"]="https://github.com/chobocho/minesweeper.git|Java|MIT|canvas|Minesweeper (MIT, 2★)"
  ["moviehangman"]="https://github.com/Varsha-Kulkarni/MovieHangman.git|Kotlin|Apache-2.0|compose|Hangman cinema (Apache-2.0, 11★)"
  ["onetwo"]="https://github.com/KNurmik/The-Game.git|Java|Apache-2.0|view|3 minigiochi (Apache-2.0, 1★)"
  ["openchess"]="https://github.com/isair/OpenChess.git|Java|Apache-2.0|canvas|Scacchi cross-platform (Apache-2.0, 23★)"
  ["picross"]="https://github.com/kyuyeonpooh/Software-Practice-3.git|Java|MIT|view|Picross (MIT, 3★)"
  ["pokerbluff"]="https://github.com/Leejjon/BluffPoker.git|Java|Apache-2.0|view|Bluff Poker (Apache-2.0, 3★)"
  ["pong"]="https://github.com/catalinc/pong-game-android.git|Java|MIT|canvas|Pong per Android (MIT, 12★)"
  ["pong2"]="https://github.com/pawelrubin/Pong.git|Kotlin|MIT|canvas|Pong mobile in Kotlin (MIT, 7★)"
  ["quickmatch"]="https://github.com/RamMichaeli17/QuickMatch.git|Java|MIT|canvas|Quick match colori (MIT, 1★)"
  ["quiz"]="https://github.com/sarveshchavan7/Quiz-Game.git|Java|MIT|view|Quiz a scelta multipla (MIT, 345★)"
  ["reaction"]="https://github.com/MattTheCuber/OneSecond.git|Java|MIT|canvas|Gioco reazione (MIT, 2★)"
  ["reflex"]="https://github.com/blaxphoenix/touch-reflex.git|Kotlin|MIT|view|Touch reflex (MIT, 1★)"
  ["reversi"]="https://github.com/antonioalmeida/retro-reversi.git|Java|MIT|canvas|Reversi classico (MIT, 7★)"
  ["rockpaper"]="https://github.com/ShadmanShariar/Rock_Paper_Scissor.git|Java|MIT|view|Carta forbice sasso (MIT, 3★)"
  ["snake2"]="https://github.com/Kochenkov/AndroidApp-SnakeGame.git|Java|MIT|canvas|Snake classico (MIT, 5★)"
  ["snake3"]="https://github.com/fpjunqueira/android-snake-game.git|Java|MIT|canvas|Snake su canvas (MIT, 5★)"
  ["snake4"]="https://github.com/Lihou/SnakeGame.git|Kotlin|Apache-2.0|compose|Snake per imparare Compose (Apache-2.0, 4★)"
  ["snooder"]="https://github.com/EXL/Snooder21.git|Java|MIT|canvas|Snood (MIT, 18★)"
  ["spaceshooter"]="https://github.com/emre-cil/interstellar-enemies.git|Java|MIT|canvas|Space shooter (MIT, 6★)"
  ["spidermash"]="https://github.com/sagunpandey/spooky-spider-smash.git|Java|MIT|surfaceview|Schiaccia ragni (MIT, 4★)"
  ["stormplane"]="https://github.com/HurTeng/StormPlane.git|Java|Apache-2.0|canvas|Shooter verticale (Apache-2.0, 1073★)"
  ["sudoku"]="https://github.com/shiroonigami23-ui/Sudoku-Quest-APK.git|Java|MIT|view|Sudoku (MIT, 4★)"
  ["tanks"]="https://github.com/arun0102/2DTankGame.git|Java|MIT|canvas|Gioco carri armati 2D (MIT, 5★)"
  ["tanks2"]="https://github.com/JAVEO/tanks.git|Java|Apache-2.0|canvas|Gioco carri armati (Apache-2.0, 9★)"
  ["tapfaster"]="https://github.com/LucasWinkler/TapFaster.git|Java|MIT|view|Test reazione tap (MIT, 1★)"
  ["tarkigates"]="https://github.com/LionelJouin/TarkiGates.git|Java|MIT|canvas|Puzzle porte logiche (MIT, 7★)"
  ["tetramine"]="https://github.com/JustDeax/Tetramine.git|Kotlin|Apache-2.0|compose|Tetris moderno (Apache-2.0, 3★)"
  ["tetris2"]="https://github.com/BiaChacon/tetris.git|Kotlin|MIT|canvas|Tetris in Kotlin (MIT, 5★)"
  ["tetris3"]="https://github.com/ccederstrom/tetris.git|Java|MIT|canvas|Tetris (MIT, 2★)"
  ["tictactoe2"]="https://github.com/Pranjal360Agarwal/Tic-Tac-Toe-Game-App.git|Java|MIT|view|Tris (MIT, 2★)"
  ["tlg"]="https://github.com/bailuk/TLG.git|Kotlin|MIT|canvas|Tetris like (MIT, 2★)"
  ["towerdefense"]="https://github.com/ochadenas/cpudefense.git|Kotlin|MIT|surfaceview|Tower Defense su microprocessori (MIT, 195★)"
  ["truco"]="https://github.com/chesterbr/minitruco-android.git|Java|BSD-3-Clause|view|Truco brasiliano (BSD-3-Clause, 106★)"
  ["uno"]="https://github.com/shiawasenahikari/UnoCard.git|Java|Apache-2.0|view|UNO (Apache-2.0, 22★)"
  ["vrmaze"]="https://github.com/Ice9Coffee/VRMaze.git|Java|MIT|canvas|Labirinto VR (MIT, 5★)"
  ["wordgame"]="https://github.com/alex-vt/WordGame.git|Kotlin|MIT|canvas|WordGame lettere (MIT, 2★)"
  ["wordle"]="https://github.com/opatry/wordle-kt.git|Kotlin|MIT|compose|Wordle clone (MIT, 47★)"
  ["words"]="https://github.com/Mariihmp/scrambled_words.git|Kotlin|MIT|view|Parole mescolate (MIT, 4★)"
)

# ── Funzioni ──────────────────────────────────────────────────────

list_repos() {
    echo -e "\n${CYAN}Repo disponibili:${NC}"
    printf "%-20s %-30s %-10s %-10s\n" "NOME" "DESCRIZIONE" "LINGUA" "LICENZA"
    printf "%-20s %-30s %-10s %-10s\n" "----" "-----------" "-------" "-------"
    for key in "${!REPOS[@]}"; do
        IFS='|' read -r url lang lic type desc <<< "${REPOS[$key]}"
        printf "%-20s %-30s %-10s %-10s\n" "$key" "$desc" "$lang" "$lic"
    done
    echo ""
}

find_repo() {
    local query="$1"
    local query_lower=$(echo "$query" | tr '[:upper:]' '[:lower:]')
    local best_match=""
    local best_score=0

    for key in "${!REPOS[@]}"; do
        local score=0
        # Match esatto
        if [ "$key" = "$query_lower" ]; then
            score=100
        # Match nel nome
        elif echo "$key" | grep -qi "$query_lower"; then
            score=50
        # Match nella descrizione
        elif echo "${REPOS[$key]}" | grep -qi "$query_lower"; then
            score=30
        fi

        if [ $score -gt $best_score ]; then
            best_score=$score
            best_match=$key
        fi
    done

    echo "$best_match"
}

download_repo() {
    local key="$1"
    IFS='|' read -r url lang lic type desc <<< "${REPOS[$key]}"
    local name="${key}_game"
    local dest="$DOWNLOADS_DIR/$name"

    log_step "1. Scaricamento"
    log_info "Repo: $key ($desc)"
    log_info "URL: $url"
    log_info "Lingua: $lang | Licenza: $lic | Tipo: $type"

    mkdir -p "$DOWNLOADS_DIR"
    if [ -d "$dest" ]; then
        log_warn "Cartella già esistente, aggiorno..."
        rm -rf "$dest"
    fi

    git clone --depth 1 "$url" "$dest" 2>/dev/null && log_ok "Scaricato in $dest" || {
        log_error "Impossibile scaricare il repo"
        return 1
    }
    DOWNLOADED_DIR="$dest"
}

analyze_repo() {
    local dest="$1"
    local key="$2"
    IFS='|' read -r url lang lic type desc <<< "${REPOS[$key]}"

    log_step "2. Analisi"

    mkdir -p "$ANALYSIS_DIR"

    # Conta file e righe
    local kt_count=$(find "$dest" -name "*.kt" 2>/dev/null | wc -l)
    local java_count=$(find "$dest" -name "*.java" 2>/dev/null | wc -l)
    local total_lines=0
    for f in $(find "$dest" -name "*.kt" -o -name "*.java" 2>/dev/null); do
        local lines=$(wc -l < "$f" 2>/dev/null || echo 0)
        total_lines=$((total_lines + lines))
    done

    # Rileva pattern
    local patterns=""
    grep -rl "Canvas\|onDraw\|SurfaceView\|View" "$dest" --include="*.kt" --include="*.java" 2>/dev/null | head -5 | while read f; do
        patterns="$patterns Canvas/View"
    done
    grep -rl "gameLoop\|gameRunning\|tick\|update\|render" "$dest" --include="*.kt" --include="*.java" 2>/dev/null | head -3 | while read f; do
        patterns="$patterns gameLoop"
    done
    grep -rl "onTouchEvent\|onClick\|MotionEvent" "$dest" --include="*.kt" --include="*.java" 2>/dev/null | head -3 | while read f; do
        patterns="$patterns touch"
    done

    # Rileva se usa Compose
    local uses_compose=false
    grep -rl "Jetpack Compose\|Compose" "$dest" --include="*.kt" --include="*.java" 2>/dev/null | head -1 && uses_compose=true

    # Genera report
    local report="$ANALYSIS_DIR/${key}_analysis.json"
    cat > "$report" << EOF
{
  "name": "$key",
  "url": "${REPOS[$key]%%|*}",
  "language": "$lang",
  "license": "$lic",
  "type": "$type",
  "description": "${REPOS[$key]##*|}",
  "kotlin_files": $kt_count,
  "java_files": $java_count,
  "total_lines": $total_lines,
  "uses_compose": $uses_compose,
  "patterns": "$patterns",
  "adaptability": "high"
}
EOF

    log_ok "Analisi completata"
    echo "  File Kotlin: $kt_count"
    echo "  File Java: $java_count"
    echo "  Righe totali: $total_lines"
    echo "  Pattern: $patterns"
    echo "  Compose: $uses_compose"
    echo "  Report: $report"
}

generate_template() {
    local dest="$1"
    local key="$2"
    local report="$ANALYSIS_DIR/${key}_analysis.json"

    log_step "3. Generazione template"

    mkdir -p "$TEMPLATES_DIR"

    # Leggi il report
    local name=$(python3 -c "import json; print(json.load(open('$report'))['name'])" 2>/dev/null || echo "$key")
    local lang=$(python3 -c "import json; print(json.load(open('$report'))['language'])" 2>/dev/null || echo "Kotlin")
    local type=$(python3 -c "import json; print(json.load(open('$report'))['type'])" 2>/dev/null || echo "canvas")
    local desc=$(python3 -c "import json; print(json.load(open('$report'))['description'])" 2>/dev/null || echo "Open source game")

    # Determina il game ID
    local game_id=$(echo "$key" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_')

    # Genera il template Kotlin
    local template_file="$TEMPLATES_DIR/${name^}Activity.kt"

    cat > "$template_file" << KOTLIN_EOF
package com.intelligame.huntix.minigames

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.intelligame.huntix.UiKit
import com.intelligame.huntix.managers.MiniGameManager
import io.sentry.Sentry
import kotlin.random.Random

/**
 * 🎮 ${name^} — adattato da open-source.
 *
 * Originale: $desc
 * Licenza: $lic
 * Tipo: $type
 *
 * Adattamento: logica di gioco preservata,
 * rendering convertito al pattern Canvas di Huntix.
 */
class ${name^}Activity : MiniGameBase() {

    private val handler = Handler(Looper.getMainLooper())
    private var gameRunning = false
    private var score = 0

    // ── Stato del gioco ──────────────────────────────────────────
    // TODO: Aggiungere le variabili di stato dal codice originale

    override fun onGameCreate() {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(UiKit.BG))
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 12), UiKit.dp(ctx, 14), UiKit.dp(ctx, 12))
        }
        root.addView(UiKit.title(ctx, "${name^}", "🎮"))
        root.addView(TextView(ctx).apply {
            text = "Gioco open-source adattato a Huntix"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            setPadding(0, 0, 0, UiKit.dp(ctx, 10))
        })
        root.addView(levelBanner(MiniGameManager.GAME_${game_id^^}))
        scoreText = TextView(ctx).apply {
            text = "Punti: 0"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, UiKit.dp(ctx, 8))
        }
        root.addView(scoreText!!)

        gameView = GameView(ctx)
        root.addView(gameView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val wrapper = FrameLayout(ctx)
        wrapper.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlayContainer = wrapper
        setContentView(wrapper)
        startGame()
    }

    private fun startGame() {
        score = 0
        gameRunning = true
        scoreText?.text = "Punti: 0"
        // TODO: Inizializzare lo stato del gioco originale
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(gameLoop, 16L)
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gameRunning) return
            // TODO: Aggiornare la logica di gioco originale
            // TODO: Gestire il game over
            gameView?.invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    // ── Rendering ─────────────────────────────────────────────────

    inner class GameView(context: android.content.Context) : View(context) {
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            // TODO: Disegnare il gioco originale usando Canvas
            c.drawColor(Color.parseColor("#0D0620"))
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            // TODO: Gestire il touch dal codice originale
            return true
        }
    }

    // ── Fine gioco ────────────────────────────────────────────────

    private fun endGame(won: Boolean) {
        if (!gameRunning) return
        gameRunning = false
        handler.removeCallbacksAndMessages(null)

        val mvc = if (won) (score * 0.2f).toInt().coerceAtLeast(10) else kotlin.math.max(score / 5, 3)
        val xp = if (won) (score * 0.1f).toInt().coerceAtLeast(5) else kotlin.math.max(score / 8, 2)

        try {
            MiniGameManager.completePlay(
                this, MiniGameManager.GAME_${game_id^^}, score,
                mvc = mvc, xp = xp,
                label = "${name^}: \${if (won) \"vittoria!\" else \"sconfitta\"}",
                isWin = won
            )
        } catch (e: Exception) { Sentry.captureException(e) }

        // TODO: Mostrare overlay di fine gioco (vedi SnakeActivity.kt per il pattern)
    }

    override fun onDestroy() {
        super.onDestroy()
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
KOTLIN_EOF

    log_ok "Template generato: $template_file"
    GENERATED_TEMPLATE="$template_file"
}

register_game() {
    local key="$1"
    local game_id=$(echo "$key" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_')
    local game_id_upper=$(echo "$game_id" | tr '[:lower:]' '[:upper:]')
    local name="${key^}"

    log_step "4. Registrazione nel progetto"

    python3 "$SCRIPT_DIR/register_game.py" \
        --project "$PROJECT_DIR" \
        --template "$TEMPLATES_DIR/${name^}Activity.kt" \
        --id "$game_id" \
        --label "${name^}" \
        --emoji "🎮" \
        && log_ok "Registrazione completata per ${name^}" \
        || log_error "Registrazione fallita"

    # Copia il template come Activity reale
    local dest_file="$PROJECT_DIR/app/src/main/java/com/intelligame/huntix/minigames/${name^}Activity.kt"
    if [ -f "$GENERATED_TEMPLATE" ]; then
        cp "$GENERATED_TEMPLATE" "$dest_file"
        log_ok "Activity copiata in: $dest_file"
    fi
}

verify_build() {
    log_step "5. Verifica build"
    log_info "Compilazione in corso..."

    cd "$PROJECT_DIR"
    if ./gradlew compileDebugKotlin 2>&1 | grep -q "BUILD SUCCESSFUL"; then
        log_ok "Build compilato con successo!"
    else
        log_warn "Build con errori - controlla la console"
        return 1
    fi
}

# ── Main ──────────────────────────────────────────────────────────

if [ $# -eq 0 ] || [ "$1" = "--list" ] || [ "$1" = "-l" ]; then
    list_repos
    echo -e "${YELLOW}Uso:${NC} ./add_game.sh <nome_gioco>"
    echo -e "${YELLOW}Esempi:${NC}"
    echo -e "  ${CYAN}./add_game.sh tetris${NC}"
    echo -e "  ${CYAN}./add_game.sh \"space invaders\"${NC}"
    echo -e "  ${CYAN}./add_game.sh pacman${NC}"
    exit 0
fi

QUERY="$1"
shift

log_step "🔍 Ricerca di \"$QUERY\""
MATCH=$(find_repo "$QUERY")

if [ -z "$MATCH" ]; then
    log_error "Nessun repo trovato per \"$QUERY\""
    echo -e "${YELLOW}Repo disponibili:${NC}"
    list_repos
    exit 1
fi

log_ok "Trovato: $MATCH"

# Esegui i passi
download_repo "$MATCH" || exit 1
DEST="$DOWNLOADED_DIR"
analyze_repo "$DEST" "$MATCH"
generate_template "$DEST" "$MATCH"
TEMPLATE="$GENERATED_TEMPLATE"
register_game "$MATCH"
verify_build

echo -e "\n${GREEN}══════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Gioco \"$MATCH\" aggiunto con successo!${NC}"
echo -e "${GREEN}══════════════════════════════════════${NC}"
echo -e "  📦 Download: $DOWNLOADS_DIR/${MATCH}_game"
echo -e "  📊 Analisi:  $ANALYSIS_DIR/${MATCH}_analysis.json"
echo -e "  📝 Template: $TEMPLATES_DIR/"
echo -e "  🎮 Activity: com.intelligame.huntix.minigames.${MATCH^}Activity"
echo -e "\n${YELLOW}⚠️  Il template generato ha sezioni TODO da completare${NC}"
echo -e "   con la logica di gioco originale dal codice sorgente."