package com.intelligame.huntix

import com.intelligame.huntix.managers.MiniGameManager

/**
 * GameRules — testi "Come si gioca" per ogni minigioco.
 * Spiegano di che gioco si tratta, come si gioca e l'obiettivo da raggiungere.
 */
object GameRules {

    data class Rule(
        val label: String,
        val emoji: String,
        val desc: String,   // di che gioco si tratta
        val howTo: String,  // come si gioca
        val goal: String    // obiettivo / obiettivi
    )

    private val RULES: Map<String, Rule> = mapOf(
        "battle3d" to Rule(
            "Battaglia 3D", "\u2694\uFE0F",
            "Scegli il tuo combattente e sfida l'avversario in un duello in stile Brawl Stars.",
            "Tocca il pulsante di attacco per colpire l'avversario; usa i pulsanti per schivare e curare. Ogni combattente ha punti vita (HP).",
            "Sconfiggi l'avversario riducendo i suoi HP a zero prima che lo faccia lui con i tuoi."
        ),
        MiniGameManager.GAME_MEMORY to Rule(
            "Memory", "\uD83E\uDDE0",
            "Classico gioco di memoria con le uova: trova tutte le coppie nascoste.",
            "Tocca due carte per rigirarle e cercare la coppia uguale. Meno mosse usi, più MVC guadagni.",
            "Trova tutte le 8 coppie di uova al livello 1; i livelli successivi aumentano le carte."
        ),
        MiniGameManager.GAME_FROGGER to Rule(
            "Frogger", "🐸",
            "Porta la rana dall'altra parte della strada e del fiume, come nel classico arcade.",
            "Scorri o usa le frecce per muoverti di una casella: evita le auto e salta sui tronchi che galleggiano.",
            "Attraversa la strada e il fiume senza cadere in acqua né essere investito per fare punti e superare il livello."
        ),
        MiniGameManager.GAME_NUMBER_PICK to Rule(
            "Scegli il Numero", "🔢",
            "Un numero segreto è stato scelto: indovinalo con i tuoi tentativi.",
            "Inserisci un numero a ogni turno: il gioco ti dice se il numero segreto è più alto o più basso, oppure se hai indovinato.",
            "Indovina il numero segreto nel minor numero di tentativi possibile."
        ),
        MiniGameManager.GAME_HIGH_CARD to Rule(
            "Carta Alta", "\uD83C\uDF87",
            "Sfida il banco: si gira una carta per te e una per l'avversario, vince la più alta.",
            "Tocca il mazzo per girare la tua carta. Più è alta, più punti ottieni.",
            "Batti il banco con la carta più alta per vincere la mano e avanzare di livello."
        ),
        MiniGameManager.GAME_CATCH_EGG to Rule(
            "Prendi l'Uovo", "🥚",
            "Le uova cadono dal cielo: muoviti per prenderle nel tuo cesto.",
            "Tieni il dito (o usa le frecce) per spostare il cesto a sinistra e a destra e cattura le uova che cadono.",
            "Raccogli il maggior numero di uova senza farne cadere troppe: le uova mancate fanno perdere vite."
        ),
        MiniGameManager.GAME_MATCH3 to Rule(
            "Match 3", "💎",
            "Puzzle classico: allinea tre o più gemme uguali per farle esplodere.",
            "Scambia due gemme adiacenti trascinandole per creare file di 3 o più gemme dello stesso colore.",
            "Crea combinazioni per fare punteggio e raggiungere l'obiettivo di punti del livello."
        ),
        MiniGameManager.GAME_2048 to Rule(
            "2048", "🧩",
            "Combina i numeri sulla griglia per raggiungere la fatidica tile 2048 (o oltre).",
            "Scorri in una direzione per spostare tutte le tessere: due tessere uguali si fondono nella loro somma.",
            "Unisci le tessere per ottenere valori sempre più alti e raggiungere il punteggio obiettivo del livello."
        ),
        MiniGameManager.GAME_SNAKE to Rule(
            "Snake", "🐍",
            "Il serpente cresce mangiando uova: non andare a sbattere sui bordi o su te stesso.",
            "Scorri o usa le frecce per cambiare direzione al serpente; ogni uovo mangiato lo allunga.",
            "Mangia quante più uova possibile senza scontrarti, per raggiungere il punteggio obiettivo."
        ),
        MiniGameManager.GAME_MINESWEEPER to Rule(
            "Campo Minato", "💣",
            "Classico Campo Minato: apri le celle e scova tutte le bombe senza farle esplodere.",
            "Tocca una cella per aprirla: il numero indica le mine adiacenti. Tieni premuto per piazzare una bandierina su una mina sospetta.",
            "Apri tutte le celle sicure della griglia senza toccare nessuna mina."
        ),
        MiniGameManager.GAME_FLAPPY to Rule(
            "Flappy Egg", "🐣",
            "L'uovo deve volare attraverso gli ostacoli senza toccarli.",
            "Tocca lo schermo per fare un piccolo salto; lascia cadere per scendere. Passa tra i tubi.",
            "Supera più tubi possibili per fare punteggio e raggiungere l'obiettivo del livello."
        ),
        MiniGameManager.GAME_CONNECT4 to Rule(
            "Forza 4", "🔵",
            "Gioca a quattro di fila contro la CPU o un amico.",
            "Tocca una colonna per far cadere il tuo disco: cerca di metterne 4 in fila, orizzontale, verticale o diagonale.",
            "Fai 4 in fila prima dell'avversario per vincere la partita."
        ),
        MiniGameManager.GAME_HANGMAN to Rule(
            "Impiccato", "🙈",
            "Indovina la parola segreta prima che l'ometto venga impiccato.",
            "Tocca le lettere per proporre: se la lettera è nella parola compare, altrimenti l'ometto fa un passo verso la forca.",
            "Scopri la parola segreta (il numero di lettere indica la lunghezza) prima di esaurire i tentativi."
        ),
        MiniGameManager.GAME_TIC_TAC_TOE to Rule(
            "Tris", "⭕",
            "Il gioco del Tris (X e O) contro la CPU.",
            "Tocca una casella libera per mettere il tuo simbolo; alterna i turni con l'avversario.",
            "Allinea 3 simboli in orizzontale, verticale o diagonale prima dell'avversario."
        ),
        MiniGameManager.GAME_SIMON to Rule(
            "Simon", "🎨",
            "Memorizza e ripeti la sequenza di colori che si accende, sempre più lunga.",
            "Guarda la sequenza che il gioco mostra, poi ripetila toccando i colori nello stesso ordine.",
            "Ripeti correttamente sequenze sempre più lunghe per fare punteggio e superare il livello."
        ),
        MiniGameManager.GAME_DINO to Rule(
            "Dino Runner", "🦖",
            "Il dinosauro corre e deve saltare gli ostacoli che incontra.",
            "Tocca lo schermo per saltare; doppio tocco per salti più alti o doppi. Evita i cactus e gli uccelli.",
            "Corri più lontano possibile accumulando punti senza scontrarti con gli ostacoli."
        ),
        MiniGameManager.GAME_AR_SHOOTER to Rule(
            "Egg Shooter", "🔫",
            "In Realtà Aumentata: spara alle uova dorate che fluttuano nell'aria intorno a te.",
            "Tocca le uova in AR per spazzarle via: le uova bianche valgono 10 pt, quelle dorate 100 pt. Le uova nere tolgono una vita.",
            "Segna più punti possibile prima che scada il tempo: le uova nere fanno perdere vite, se finiscono le vite è game over."
        ),
        MiniGameManager.GAME_AR_BOMB to Rule(
            "Color Bomb", "💣",
            "In Realtà Aumentata: esplodi solo le uova del colore bersaglio.",
            "Il bersaglio è indicato a schermo: tocca e fai esplodere le uova di quel colore, evita quelle di altri colori.",
            "Fai esplodere quante più uova del colore giusto possibile prima dello scadere del tempo."
        ),
        MiniGameManager.GAME_AR_RADAR to Rule(
            "Egg Radar", "📡",
            "In Realtà Aumentata: usa il radar per trovare e catturare le uova sospese nella stanza.",
            "Girati attorno: il radar ti indica la direzione delle uova. Tocca un'uovo che vedi per catturarla.",
            "Cattura tutte le uova sospese prima che scada il tempo."
        ),
        MiniGameManager.GAME_SLINGSHOT to Rule(
            "Egg Slingshot", "🎯",
            "In Realtà Aumentata: lancia l'uovo con la fionda verso i bersagli nella stanza.",
            "Inquadra una superficie piana, tira indietro la fionda e rilascia per lanciare l'uovo contro gli obiettivi.",
            "Centra il numero di bersagli richiesti dal livello (WIN_HITS) con i tuoi tiri."
        ),
        MiniGameManager.GAME_TETRIS to Rule(
            "Tetris", "🧱",
            "Il classico Tetris: i mattoncini cadono e tu li devi incastrare.",
            "Tocca per ruotare, scorri per spostare il pezzo e fallo cadere velocemente per completare le righe.",
            "Completa più righe possibili per fare punti e raggiungere l'obiettivo del livello."
        ),
        MiniGameManager.GAME_FLOOD to Rule(
            "Flood", "🌊",
            "Allaga la griglia: trasforma tutte le caselle in un unico colore in poche mosse.",
            "Scegli un colore dall'alto: la casella in alto a sinistra e le adiacenti dello stesso colore diventano del colore scelto. Ripeti per estendere il flood.",
            "Rendi la griglia tutta di un solo colore nel minor numero di mosse possibile."
        ),
        MiniGameManager.GAME_ASTEROIDS to Rule(
            "Asteroids", "🚀",
            "L'arcade spaziale: pilota la navicella e distruggi gli asteroidi.",
            "Tieni un dito a sinistra o a destra per ruotare, al centro per accelerare; tocca (o usa un secondo dito) per sparare. Dopo ogni colpo perdi temporaneamente l'invulnerabilità.",
            "Distruggi gli asteroidi per fare punti e raggiungere l'obiettivo del livello; non farti colpire, le vite sono limitate."
        ),
        MiniGameManager.GAME_SUDOKU to Rule(
            "Sudoku", "🔢",
            "Il classico Sudoku 9×9 generato al momento, con tre livelli di difficoltà.",
            "Tocca una cella vuota e poi scegli un numero dal tastierino. Il gioco ti segnala in rosso i numeri in conflitto.",
            "Completa la griglia rispettando la regola: ogni riga, colonna e riquadro 3×3 deve contenere i numeri da 1 a 9 senza ripetizioni."
        )
    )

    /** Regole per un gioco; se assenti usa un fallback generico. */
    fun rule(gameId: String): Rule =
        RULES[gameId] ?: Rule(
            gameId, "\uD83C\uDFAE",
            "Un minigioco di Huntix.",
            "Segui le indicazioni a schermo per interagire con il gioco.",
            "Raggiungi l'obiettivo mostrato nel banner del livello per superarlo e guadagnare stelle."
        )

    /** Titolo mostrato nell'anteprima della scheda, per i giochi noti. */
    fun known(id: String): Boolean = RULES.containsKey(id)
}
