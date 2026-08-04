package com.intelligame.huntix

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.intelligame.huntix.managers.MiniGameManager
import com.intelligame.huntix.managers.ResearchTaskManager
import com.intelligame.huntix.minigames.*
import com.intelligame.huntix.minigames.ar.*
import kotlinx.coroutines.launch

/**
 * MiniGamesHubActivity — hub centrale dei minigiochi.
 *
 * Ristrutturato con una levetta di filtro (Tutti / Normali / AR), una
 * griglia ordinata di schede e una mini-antePRIMA grafica per ogni gioco.
 * Ogni gioco che ha sia la versione normale che quella AR mostra una scelta
 * di modalità all'apertura.
 */
class MiniGamesHubActivity : BaseNavActivity() {

    override fun activeTab() = ""

    data class GameEntry(
        val id: String, val label: String, val emoji: String,
        val cls: Class<*>?, val arCls: Class<*>?
    ) {
        val hasNormal: Boolean get() = cls != null
        val hasAr: Boolean get() = arCls != null
    }

    private val games = listOf(
        GameEntry("battle3d", "Battaglia 3D", "\u2694\uFE0F", com.intelligame.huntix.ui.FighterSelectActivity::class.java, null),
        GameEntry(MiniGameManager.GAME_MEMORY, "Memory", "🧠", MemoryGameActivity::class.java, ARMemoryActivity::class.java),
        GameEntry(MiniGameManager.GAME_FROGGER, "Frogger", "🐸", FroggerActivity::class.java, ARFroggerActivity::class.java),
        GameEntry(MiniGameManager.GAME_NUMBER_PICK, "Scegli il Numero", "🔢", NumberPickActivity::class.java, ARNumberPickActivity::class.java),
        GameEntry(MiniGameManager.GAME_HIGH_CARD, "Carta Alta", "🃏", HighCardActivity::class.java, ARHighCardActivity::class.java),
        GameEntry(MiniGameManager.GAME_CATCH_EGG, "Prendi l'Uovo", "🥚", CatchEggActivity::class.java, ARCatchEggActivity::class.java),
        GameEntry(MiniGameManager.GAME_MATCH3, "Match 3", "💎", Match3Activity::class.java, ARMatch3Activity::class.java),
        GameEntry(MiniGameManager.GAME_2048, "2048", "🧩", Game2048Activity::class.java, AR2048Activity::class.java),
        GameEntry(MiniGameManager.GAME_SNAKE, "Snake", "🐍", SnakeActivity::class.java, ARSnakeActivity::class.java),
        GameEntry(MiniGameManager.GAME_MINESWEEPER, "Campo Minato", "💣", MinesweeperActivity::class.java, ARMinesweeperActivity::class.java),
        GameEntry(MiniGameManager.GAME_FLAPPY, "Flappy Egg", "🐣", FlappyEggActivity::class.java, ARFlappyEggActivity::class.java),
        GameEntry(MiniGameManager.GAME_CONNECT4, "Forza 4", "🔵", ConnectFourActivity::class.java, ARConnectFourActivity::class.java),
        GameEntry(MiniGameManager.GAME_HANGMAN, "Impiccato", "🙈", HangmanActivity::class.java, ARHangmanActivity::class.java),
        GameEntry(MiniGameManager.GAME_TIC_TAC_TOE, "Tris", "⭕", TicTacToeActivity::class.java, ARTicTacToeActivity::class.java),
        GameEntry(MiniGameManager.GAME_SIMON, "Simon", "🎨", SimonActivity::class.java, ARSimonActivity::class.java),
        GameEntry(MiniGameManager.GAME_DINO, "Dino Runner", "🦖", DinoGameActivity::class.java, ARDinoActivity::class.java),
        GameEntry(MiniGameManager.GAME_AR_SHOOTER, "Egg Shooter", "🔫", null, AREggShooterActivity::class.java),
        GameEntry(MiniGameManager.GAME_AR_BOMB, "Color Bomb", "💣", null, ARColorBombActivity::class.java),
        GameEntry(MiniGameManager.GAME_AR_RADAR, "Egg Radar", "📡", null, AREggRadarActivity::class.java),
        GameEntry(MiniGameManager.GAME_SLINGSHOT, "Egg Slingshot", "🎯", null, AREggSlingshotActivity::class.java),
        GameEntry(MiniGameManager.GAME_TETRIS, "Tetris", "🧱", TetrisActivity::class.java, ARTetrisActivity::class.java),
        GameEntry(MiniGameManager.GAME_FLOOD, "Flood", "🌊", FloodActivity::class.java, ARFloodActivity::class.java),
        GameEntry(MiniGameManager.GAME_ASTEROIDS, "Asteroids", "🚀", AsteroidsActivity::class.java, ARAsteroidsActivity::class.java),
        GameEntry(MiniGameManager.GAME_SUDOKU, "Sudoku", "🔢", SudokuActivity::class.java, ARSudokuActivity::class.java)
    )

    private var filter = 0 // 0 = Tutti, 1 = Normali, 2 = AR
    private lateinit var gridBox: GridLayout
    private val filterButtons = mutableListOf<TextView>()
    private lateinit var roomsBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val filterRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 8))
        }
        val labels = listOf("Tutti", "Normali", "AR")
        for (i in 0 until 3) {
            val btn = TextView(c).apply {
                text = labels[i]
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(UiKit.dp(c, 16), UiKit.dp(c, 7), UiKit.dp(c, 16), UiKit.dp(c, 7))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { setFilter(i) }
            }
            filterButtons.add(btn)
            filterRow.addView(btn)
        }
        content.addView(filterRow)

        content.addView(UiKit.section(c, "🏠 Stanze gioco"))
        roomsBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        content.addView(roomsBox)
        renderRooms()
        syncRooms()

        gridBox = GridLayout(c).apply {
            columnCount = 2
        }
        content.addView(gridBox)

        setContentView(UiKit.scroll(c, UiKit.title(c, "Minigiochi", "🎮"), UiKit.section(c, "Scegli un gioco"), content))
        render()
    }

    private fun setFilter(i: Int) {
        if (filter == i) return
        filter = i
        render()
    }

    private fun visibleGames(): List<GameEntry> = when (filter) {
        1 -> games.filter { it.hasNormal }
        2 -> games.filter { it.hasAr }
        else -> games
    }

    // ── Stanze gioco (AR) ─────────────────────────────────────────

    /** Ricostruisce la lista delle stanze AR salvate. */
    private fun renderRooms() {
        val c = this
        roomsBox.removeAllViews()
        roomsBox.addView(TextView(c).apply {
            text = "➕  Nuova stanza"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(UiKit.GREEN))
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 9), UiKit.dp(c, 12), UiKit.dp(c, 9))
            background = GradientDrawableCompat("#1A2F1F")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 6) }
            isClickable = true
            isFocusable = true
            setOnClickListener { requestNewRoom() }
        })
        val rooms = ArArenaStore.loadRooms(c)
        if (rooms.isEmpty()) {
            roomsBox.addView(TextView(c).apply {
                text = "Nessuna stanza salvata: si creano da sole quando piazzi un gioco AR in un posto nuovo."
                textSize = 12f
                setTextColor(Color.parseColor(UiKit.TEXT_DIM))
                setPadding(UiKit.dp(c, 14), UiKit.dp(c, 10), UiKit.dp(c, 14), UiKit.dp(c, 10))
                background = GradientDrawableCompat("#20224A")
            })
            return
        }
        rooms.forEach { room -> roomsBox.addView(roomRow(room)) }
    }

    /**
     * Predispone la creazione di una nuova stanza: il prossimo gioco AR
     * ancorato che viene aperto salta il selettore e salva la posizione come
     * nuova stanza.
     */
    private fun requestNewRoom() {
        ArArenaStore.setPendingNewRoom(this)
        Toast.makeText(this, "✅ Apri un gioco AR ancorato: la posizione sarà salvata come nuova stanza.", Toast.LENGTH_LONG).show()
    }

    private fun roomRow(room: ArRoom): View {
        val c = this
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(c, 12), UiKit.dp(c, 8), UiKit.dp(c, 12), UiKit.dp(c, 8))
            background = GradientDrawableCompat("#20224A")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 6) }
        }
        row.addView(TextView(c).apply {
            text = "🏠  ${room.name}"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(smallAction("✏️") { renameRoom(room) })
        row.addView(smallAction("🗑️") { deleteRoom(room) })
        return row
    }

    private fun smallAction(emoji: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = emoji
            textSize = 18f
            isClickable = true
            isFocusable = true
            setPadding(UiKit.dp(this@MiniGamesHubActivity, 10), UiKit.dp(this@MiniGamesHubActivity, 2), UiKit.dp(this@MiniGamesHubActivity, 8), UiKit.dp(this@MiniGamesHubActivity, 2))
            setOnClickListener { onClick() }
        }

    private fun renameRoom(room: ArRoom) {
        val input = EditText(this).apply {
            setText(room.name)
            hint = "Nome stanza"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        AlertDialog.Builder(this)
            .setTitle("✏️ Rinomina stanza")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != room.name) {
                    val updated = room.copy(name = newName)
                    ArArenaStore.saveRoom(this, updated, setLast = false)
                    lifecycleScope.launch { ArArenaStore.pushRoom(updated) }
                    renderRooms()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteRoom(room: ArRoom) {
        AlertDialog.Builder(this)
            .setTitle("🗑️ Elimina stanza")
            .setMessage("Eliminare la stanza '${room.name}'? La posizione salvata per i giochi AR andrà persa.")
            .setPositiveButton("Elimina") { _, _ ->
                ArArenaStore.deleteRoom(this, room.roomId)
                lifecycleScope.launch { ArArenaStore.deleteRoomCloud(room.roomId) }
                renderRooms()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Fondo arrotondato colorato per le righe delle stanze. */
    private fun GradientDrawableCompat(hex: String): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = UiKit.dp(this@MiniGamesHubActivity, 12).toFloat()
            setColor(Color.parseColor(hex))
        }

    /** Unisce in locale le stanze presenti sul cloud (fire-and-forget). */
    private fun syncRooms() {
        lifecycleScope.launch {
            val remote = ArArenaStore.pullRooms()
            if (remote.isEmpty()) return@launch
            val localIds = ArArenaStore.loadRooms(this@MiniGamesHubActivity).map { it.roomId }.toSet()
            val newRooms = remote.filter { it.roomId !in localIds }
            if (newRooms.isNotEmpty()) {
                newRooms.forEach { ArArenaStore.saveRoom(this@MiniGamesHubActivity, it, setLast = false) }
                renderRooms()
            }
        }
    }

    private fun render() {
        val c = this
        for (i in 0 until 3) {
            val active = i == filter
            val btn = filterButtons[i]
            btn.setTextColor(Color.WHITE)
            btn.setBackgroundColor(Color.parseColor(if (active) UiKit.ACCENT else "#20224A"))
        }
        gridBox.removeAllViews()
        val list = visibleGames()
        list.forEach { g ->
            gridBox.addView(buildCard(g), GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(UiKit.dp(c, 4), UiKit.dp(c, 4), UiKit.dp(c, 4), UiKit.dp(c, 4))
            })
        }
    }

    private fun buildCard(g: GameEntry): View {
        val c = this
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 8), UiKit.dp(c, 8), UiKit.dp(c, 8), UiKit.dp(c, 6))
            isClickable = true
            setBackgroundColor(Color.parseColor("#161838"))
            setOnClickListener { openGame(g) }
        }

        val preview = GamePreviewView(c).apply {
            setGame(g.id, g.hasAr)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(c, 78)
            )
        }
        card.addView(preview)

        val nameRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiKit.dp(c, 6), 0, 0)
        }
        nameRow.addView(TextView(c).apply {
            text = "${g.emoji}  ${g.label}"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        nameRow.addView(TextView(c).apply {
            text = "⭐ ${MiniGameManager.getTotalStars(c, g.id)}  •  Lv ${MiniGameManager.getLevel(c, g.id)}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(UiKit.GREEN))
        })
        card.addView(nameRow)

        val chips = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, UiKit.dp(c, 4), 0, 0)
        }
        if (g.hasNormal) chips.addView(chip(c, "📱 Normale"))
        if (g.hasAr) chips.addView(chip(c, "🔮 AR"))
        chips.addView(chip(c, "🎯 ${MiniGameManager.getLevelTarget(c, g.id)}"))
        card.addView(chips)
        card.addView(UiKit.button(c, "📖 Come si gioca", UiKit.TEXT_DIM) { showGameRules(g) })

        return card
    }

    /**
     * Mostra le meccaniche del gioco: di che gioco si tratta, come si gioca
     * e gli obiettivi da raggiungere.
     */
    private fun showGameRules(g: GameEntry) {
        val c = this
        val r = GameRules.rule(g.id)
        val inner = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 22), UiKit.dp(c, 20), UiKit.dp(c, 22), UiKit.dp(c, 10))
        }
        inner.addView(TextView(c).apply {
            text = "${r.emoji}  ${r.label}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        inner.addView(rulesSection(c, "🎮 Di che gioco si tratta", r.desc))
        inner.addView(rulesSection(c, "🕹️ Come si gioca", r.howTo))
        inner.addView(rulesSection(c, "🎯 Obiettivi", r.goal))
        inner.addView(UiKit.button(c, "✓  Ho capito", UiKit.ACCENT) {
            openGame(g)
        })
        inner.addView(UiKit.button(c, "✕  Chiudi", UiKit.TEXT_DIM) { rulesDialog?.dismiss() })

        val scroll = NestedScrollView(c).apply {
            isFillViewport = false
            addView(inner)
        }
        val dialog = Dialog(c)
        dialog.setContentView(scroll)
        if (dialog.window != null) {
            dialog.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#120D26")))
            dialog.window!!.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)
        rulesDialog = dialog
        dialog.show()
    }

    private var rulesDialog: Dialog? = null

    private fun rulesSection(c: MiniGamesHubActivity, title: String, body: String): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(c, 10), 0, 0)
        }
        box.addView(TextView(c).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setPadding(0, 0, 0, UiKit.dp(c, 3))
        })
        box.addView(TextView(c).apply {
            text = body
            textSize = 13f
            setTextColor(Color.WHITE)
            setLineSpacing(UiKit.dp(c, 2).toFloat(), 1f)
        })
        return box
    }

    private fun chip(c: MiniGamesHubActivity, text: String): TextView =
        TextView(c).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(UiKit.ACCENT))
            setBackgroundColor(0x33A78BFA.toInt())
            setPadding(UiKit.dp(c, 7), UiKit.dp(c, 2), UiKit.dp(c, 7), UiKit.dp(c, 2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = UiKit.dp(c, 5) }
        }

    private fun openGame(g: GameEntry) {
        val normal = g.cls
        val ar = g.arCls
        when {
            normal != null && ar != null -> showModeDialog(g)
            normal != null -> launch(normal, g)
            ar != null -> launch(ar, g)
        }
    }

    private fun launch(cls: Class<*>, g: GameEntry) {
        try { ResearchTaskManager.trackProgress(this, "play_minigame") } catch (_: Exception) {}
        // Il consumo della giocata è gestito dall'activity stessa.
        startActivity(Intent(this, cls))
    }

    private fun showModeDialog(g: GameEntry) {
        val c = this
        val dialog = Dialog(c)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(c, 22), UiKit.dp(c, 22), UiKit.dp(c, 22), UiKit.dp(c, 18))
        }
        box.addView(TextView(c).apply {
            text = "${g.emoji}  ${g.label}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(c).apply {
            text = "Scegli la modalità"
            textSize = 12f
            setTextColor(Color.parseColor(UiKit.TEXT_DIM))
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(c, 4), 0, UiKit.dp(c, 12))
        })
        box.addView(UiKit.button(c, "📱  Normale", UiKit.ACCENT) {
            dialog.dismiss()
            launch(g.cls!!, g)
        })
        box.addView(UiKit.button(c, "🔮  Realtà Aumentata", UiKit.GREEN) {
            dialog.dismiss()
            launch(g.arCls!!, g)
        })
        box.addView(UiKit.button(c, "📖  Come si gioca", "#4B3B7A") {
            dialog.dismiss()
            showGameRules(g)
        })
        box.addView(UiKit.button(c, "✕  Annulla", UiKit.TEXT_DIM) { dialog.dismiss() })

        dialog.setContentView(box)
        if (dialog.window != null) {
            dialog.window!!.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#120D26")))
            dialog.window!!.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)
        dialog.show()
    }
}
