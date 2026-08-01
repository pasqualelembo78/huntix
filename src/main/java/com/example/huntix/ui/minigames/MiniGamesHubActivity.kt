package com.example.huntix.ui.minigames

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.huntix.R
import com.example.huntix.model.GameInfo

class MiniGamesHubActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GamesAdapter
    private lateinit var manager: com.example.huntix.data.MiniGameManager

    private val allGames = listOf(
        GameInfo("game_2048", "2048", "Game2048Activity", false, note = "griglia 4x4"),
        GameInfo("game_snake", "Snake", "SnakeActivity", false, note = "serpente"),
        GameInfo("game_minesweeper", "Campo Minato", "MinesweeperActivity", false, note = "9x9"),
        GameInfo("game_flappy_egg", "Flappy Egg", "FlappyEggActivity", false, note = "tubi"),
        GameInfo("game_connect4", "Forza 4", "ConnectFourActivity", false, note = "7x6"),
        GameInfo("game_hangman", "Impiccato", "HangmanActivity", false, note = "parole IT"),
        GameInfo("game_tic_tac_toe", "Tris", "TicTacToeActivity", false, note = "3x3"),
        GameInfo("game_simon", "Simon", "SimonActivity", false, note = "colori"),

        GameInfo("ar_2048", "AR 2048", "AR2048Activity", true),
        GameInfo("ar_snake", "AR Snake", "ARSnakeActivity", true),
        GameInfo("ar_minesweeper", "AR Campo Minato", "ARMinesweeperActivity", true),
        GameInfo("ar_flappy_egg", "AR Flappy Egg", "ARFlappyEggActivity", true),
        GameInfo("ar_connect4", "AR Forza 4", "ARConnectFourActivity", true),
        GameInfo("ar_hangman", "AR Impiccato", "ARHangmanActivity", true),
        GameInfo("ar_tic_tac_toe", "AR Tris", "ARTicTacToeActivity", true),
        GameInfo("ar_simon", "AR Simon", "ARSimonActivity", true),
        GameInfo("ar_egg_shooter", "AR Egg Shooter", "AREggShooterActivity", true, isExclusive = true),
        GameInfo("ar_color_bomb", "AR Color Bomb", "ARColorBombActivity", true, isExclusive = true),
        GameInfo("ar_egg_radar", "AR Egg Radar", "AREggRadarActivity", true, isExclusive = true),
        GameInfo("ar_egg_slingshot", "AR Egg Slingshot", "AREggSlingshotActivity", true, isExclusive = true, note = "showcase")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minigames_hub)

        recyclerView = findViewById(R.id.recyclerViewGames)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = GamesAdapter(allGames) { game -> launchGame(game) }
        recyclerView.adapter = adapter
        manager = com.example.huntix.data.MiniGameManager(this)

        // Toggle listener
        val toggle = findViewById<RadioGroup>(R.id.toggleMode)
        toggle.setOnCheckedChangeListener { _, checkedId ->
            adapter.filterByMode(
                when (checkedId) {
                    R.id.radioNormal -> false
                    R.id.radioAr -> true
                    else -> false
                }
            )
        }
    }

    private fun launchGame(game: GameInfo) {
        if (!manager.canPlay(game.id)) {
            showLimitMessage()
            return
        }
        manager.incrementPlay(game.id)
        val intent = Intent(this, Class.forName("com.example.huntix.${game.className}"))
        intent.putExtra("game_id", game.id)
        intent.putExtra("score", 0)
        startActivityForResult(intent, 1001)
    }

    private fun showLimitMessage() {
        android.widget.Toast.makeText(this, "Limite giornaliero raggiunto!", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Adapter semplice con ViewHolder e preview
class GamesAdapter(
    private var allGames: List<GameInfo>,
    private val onClick: (GameInfo) -> Unit
) : RecyclerView.Adapter<GamesAdapter.GameVH>() {

    private var filteredGames: List<GameInfo> = allGames

    fun filterByMode(isArMode: Boolean) {
        filteredGames = if (isArMode) {
            allGames.filter { it.isAr }
        } else {
            allGames.filter { !it.isAr }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): GameVH {
        val preview = GamePreviewView(parent.context)
        preview.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 8, 8, 8)
        }
        return GameVH(preview)
    }

    override fun onBindViewHolder(holder: GameVH, position: Int) {
        val game = filteredGames[position]
        holder.preview.gameId = game.id
        holder.preview.isAr = game.isAr
        holder.itemView.setOnClickListener { onClick(game) }
    }

    override fun getItemCount(): Int = filteredGames.size

    class GameVH(val preview: GamePreviewView) : RecyclerView.ViewHolder(preview)
}