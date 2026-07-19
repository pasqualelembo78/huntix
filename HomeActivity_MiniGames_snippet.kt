// ══════════════════════════════════════════════════════════════
// SNIPPET DA AGGIUNGERE IN HomeActivity.kt
// ══════════════════════════════════════════════════════════════
//
// 1. Aggiungi l'import in cima al file:
import com.intelligame.huntix.MiniGamesHubActivity
import com.intelligame.huntix.managers.MiniGameManager
//
// 2. Aggiungi nella sezione "Slim extras" di buildUI():
//    (dopo l'ultima riga slimCard, prima di setContentView)

// ─── Mini Giochi (Badge con MVC guadagnati oggi)
val miniGamesLabel = buildMiniGamesLabel()
root.addView(slimCard("🎮", miniGamesLabel, "#E91E63") {
    startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
})

// Oppure come CircleItem nel grid (7° slot):
// CircleItem("🎮", "Giochi",  "#E91E63") {
//     startActivity(Intent(this@HomeActivity, MiniGamesHubActivity::class.java))
// }

// ══════════════════════════════════════════════════════════════
// Funzione helper da aggiungere in HomeActivity:
// ══════════════════════════════════════════════════════════════

private fun buildMiniGamesLabel(): String {
    val mvcToday = MiniGameManager.totalMvcEarnedToday(this)
    val gamesLeft = MiniGameManager.ALL_GAME_IDS.count { 
        MiniGameManager.remainingPlays(this, it) > 0 
    }
    return buildString {
        append("Mini Giochi")
        if (gamesLeft > 0) append(" ($gamesLeft disponibili)")
        if (mvcToday > 0) append(" • +$mvcToday MVC oggi")
    }
}

// ══════════════════════════════════════════════════════════════
// OPZIONE: mostra badge notifica se ci sono giochi disponibili
// Aggiungi in onResume() di HomeActivity:
// ══════════════════════════════════════════════════════════════

override fun onResume() {
    super.onResume()
    // Aggiorna il label Mini Giochi in tempo reale se visibile
    // (solo se hai salvato il riferimento al slimCard)
}
