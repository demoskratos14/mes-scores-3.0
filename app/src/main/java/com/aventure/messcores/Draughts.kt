package com.aventure.messcores

/** Identifiant des Dames parmi les jeux prédéfinis (écran à part : voir [DraughtsScreen]). */
internal const val DRAUGHTS_ID = "builtin_draughts"

/**
 * Un coup : le chemin de la pièce ([path] : sa case de départ puis chaque case où elle se pose) et les cases des
 * pièces prises ([captured], vide pour un simple déplacement). Le chemin suffit à reconnaître un coup.
 */
internal data class DraughtsMove(val path: List<Int>, val captured: List<Int>) {
    val from: Int get() = path.first()
    val to: Int get() = path.last()
    val isCapture: Boolean get() = captured.isNotEmpty()
}

/**
 * Règles du jeu de dames (françaises / internationales) : damier de 10 × 10, 20 pièces par camp.
 *
 * - Une case est un entier de 0 à 99, lu de gauche à droite et de haut en bas ; seules les cases sombres servent.
 * - Une grille est une liste de 100 valeurs : 0 = vide, 1 = pion blanc, 2 = pion noir, 3 = dame blanche, 4 = dame noire.
 * - Les blancs commencent en bas et montent ; les noirs commencent en haut et descendent.
 * - Un pion avance d'une case en diagonale et prend en avant comme en arrière ; une dame se déplace et prend de loin.
 * - La prise est obligatoire, et il faut prendre le plus de pièces possible. Les pièces prises ne sont retirées qu'à la
 *   fin du coup, et une même pièce ne se prend qu'une fois.
 * - Un pion qui finit son coup sur la dernière rangée devient dame ; s'il y passe en continuant de prendre, il reste pion.
 */
internal object Draughts {

    const val SIZE = 10
    const val CELLS = SIZE * SIZE

    const val WHITE = 1
    const val BLACK = 2

    const val MAN_WHITE = 1
    const val MAN_BLACK = 2
    const val KING_WHITE = 3
    const val KING_BLACK = 4

    /** Partie en cours (valeurs de [DraughtsMatch.result]). WHITE et BLACK : le camp qui a gagné. */
    const val ONGOING = 0
    const val DRAW = 3

    /** Demi-coups d'affilée (déplacements de dames sans prise) qui rendent la partie nulle : 25 coups par camp. */
    const val QUIET_LIMIT = 50

    private val DIRECTIONS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    /** Position de départ, sous forme de texte (se garde tel quel quand l'écran tourne). */
    val START: String = buildString {
        for (index in 0 until CELLS) {
            val row = index / SIZE
            append(
                if (isDark(index)) {
                    when {
                        row < 4 -> '2'
                        row >= SIZE - 4 -> '1'
                        else -> '0'
                    }
                } else {
                    '0'
                }
            )
        }
    }

    /** Vrai pour une case sombre, la seule où l'on joue. */
    fun isDark(index: Int): Boolean = (index / SIZE + index % SIZE) % 2 == 1

    /** Numéro officiel de la case (1 à 50, en haut à gauche du côté des noirs), pour la décrire aux lecteurs d'écran. */
    fun number(index: Int): Int = (index / SIZE) * (SIZE / 2) + (index % SIZE) / 2 + 1

    fun colorOf(piece: Int): Int = when (piece) {
        MAN_WHITE, KING_WHITE -> WHITE
        MAN_BLACK, KING_BLACK -> BLACK
        else -> 0
    }

    fun isKing(piece: Int): Boolean = piece == KING_WHITE || piece == KING_BLACK

    fun isMan(piece: Int): Boolean = piece == MAN_WHITE || piece == MAN_BLACK

    /** Grille lue depuis son texte ; un texte invalide donne la position de départ. */
    fun parse(text: String): List<Int> {
        val valid = text.length == CELLS && text.withIndex().all { (index, char) ->
            char in '0'..'4' && (char == '0' || isDark(index))
        }
        return (if (valid) text else START).map { it - '0' }
    }

    fun format(cells: List<Int>): String = cells.joinToString("")

    /**
     * Tous les coups permis au camp [color] : s'il peut prendre, seulement les prises qui prennent le plus de pièces ;
     * sinon les déplacements.
     */
    fun legalMoves(cells: List<Int>, color: Int): List<DraughtsMove> {
        val own = cells.indices.filter { colorOf(cells[it]) == color }
        val captures = own.flatMap { capturesFrom(cells, it, color) }
        if (captures.isNotEmpty()) {
            val most = captures.maxOf { it.captured.size }
            return captures.filter { it.captured.size == most }
        }
        return own.flatMap { quietMoves(cells, it, color) }
    }

    /** Grille après le coup [move] : pièces prises retirées, pièce posée (et couronnée si elle finit sur la dernière rangée). */
    fun afterMove(cells: List<Int>, move: DraughtsMove): List<Int> {
        val next = cells.toMutableList()
        var piece = next[move.from]
        next[move.from] = 0
        move.captured.forEach { next[it] = 0 }
        val row = move.to / SIZE
        if (piece == MAN_WHITE && row == 0) piece = KING_WHITE
        if (piece == MAN_BLACK && row == SIZE - 1) piece = KING_BLACK
        next[move.to] = piece
        return next
    }

    private fun inside(row: Int, col: Int): Boolean = row in 0 until SIZE && col in 0 until SIZE

    /** Déplacements sans prise de la pièce en [from]. */
    private fun quietMoves(cells: List<Int>, from: Int, color: Int): List<DraughtsMove> {
        val row = from / SIZE
        val col = from % SIZE
        val moves = ArrayList<DraughtsMove>()
        if (isKing(cells[from])) {
            for ((dr, dc) in DIRECTIONS) {
                var r = row + dr
                var c = col + dc
                while (inside(r, c) && cells[r * SIZE + c] == 0) {
                    moves.add(DraughtsMove(listOf(from, r * SIZE + c), emptyList()))
                    r += dr
                    c += dc
                }
            }
        } else {
            val dr = if (color == WHITE) -1 else 1
            for (dc in listOf(-1, 1)) {
                val r = row + dr
                val c = col + dc
                if (inside(r, c) && cells[r * SIZE + c] == 0) {
                    moves.add(DraughtsMove(listOf(from, r * SIZE + c), emptyList()))
                }
            }
        }
        return moves
    }

    /** Toutes les suites de prises de la pièce en [origin] (chacune prend jusqu'au bout, sans s'arrêter en route). */
    private fun capturesFrom(cells: List<Int>, origin: Int, color: Int): List<DraughtsMove> {
        val king = isKing(cells[origin])
        val enemy = 3 - color
        // La pièce qui prend a quitté sa case de départ : elle peut y repasser. Les pièces prises restent sur le damier
        // jusqu'à la fin du coup : elles bloquent le passage et ne se prennent pas deux fois.
        val board = cells.toMutableList().also { it[origin] = 0 }
        val result = ArrayList<DraughtsMove>()

        fun walk(position: Int, path: List<Int>, taken: List<Int>) {
            var continued = false
            val row = position / SIZE
            val col = position % SIZE
            for ((dr, dc) in DIRECTIONS) {
                if (king) {
                    var r = row + dr
                    var c = col + dc
                    while (inside(r, c) && board[r * SIZE + c] == 0) {
                        r += dr
                        c += dc
                    }
                    if (!inside(r, c)) continue
                    val victim = r * SIZE + c
                    if (colorOf(board[victim]) != enemy || victim in taken) continue
                    var landingRow = r + dr
                    var landingCol = c + dc
                    while (inside(landingRow, landingCol) && board[landingRow * SIZE + landingCol] == 0) {
                        val landing = landingRow * SIZE + landingCol
                        continued = true
                        walk(landing, path + landing, taken + victim)
                        landingRow += dr
                        landingCol += dc
                    }
                } else {
                    val landingRow = row + 2 * dr
                    val landingCol = col + 2 * dc
                    if (!inside(landingRow, landingCol)) continue
                    val victim = (row + dr) * SIZE + (col + dc)
                    val landing = landingRow * SIZE + landingCol
                    if (colorOf(board[victim]) == enemy && victim !in taken && board[landing] == 0) {
                        continued = true
                        walk(landing, path + landing, taken + victim)
                    }
                }
            }
            if (!continued && taken.isNotEmpty()) result.add(DraughtsMove(path, taken))
        }

        walk(origin, listOf(origin), emptyList())
        return result
    }
}

/**
 * Une séance de dames : la partie en cours, qui joue les blancs et le score. Sert aux dames sur un téléphone comme à
 * celles chacun sur son téléphone (voir [DraughtsOnline]). Les places sont celles de la table : 0 le premier joueur
 * (l'organisateur en ligne), 1 le deuxième. Les blancs commencent ; ils sont joués par la place [starter], et les
 * couleurs s'échangent à chaque partie.
 */
internal data class DraughtsMatch(
    val board: String = Draughts.START,
    /** Place du joueur qui a les blancs dans cette partie (0 ou 1). */
    val starter: Int = 0,
    /** Camp à qui c'est de jouer ([Draughts.WHITE] ou [Draughts.BLACK]). */
    val turn: Int = Draughts.WHITE,
    /** Demi-coups d'affilée sans prise ni mouvement de pion (voir [Draughts.QUIET_LIMIT]). */
    val quiet: Int = 0,
    /** [Draughts.ONGOING], le camp gagnant ([Draughts.WHITE] ou [Draughts.BLACK]) ou [Draughts.DRAW]. */
    val result: Int = Draughts.ONGOING,
    /** Chemin du dernier coup joué, pour le montrer à l'adversaire. */
    val last: List<Int> = emptyList(),
    val winsA: Int = 0,
    val winsB: Int = 0,
    val draws: Int = 0
) {
    val cells: List<Int> get() = Draughts.parse(board)

    val isOver: Boolean get() = result != Draughts.ONGOING

    /** Camp (blanc ou noir) du joueur assis à la place [seat]. */
    fun colorOf(seat: Int): Int = (seat + starter) % 2 + 1

    /** Place du joueur qui joue le camp [color]. */
    fun seatOf(color: Int): Int = (color - 1 + starter) % 2

    /** Place du joueur à qui c'est de jouer. */
    val currentSeat: Int get() = seatOf(turn)

    /** Place du joueur qui a gagné la partie, ou -1 si elle continue ou si elle est nulle. */
    val winnerSeat: Int
        get() = if (result == Draughts.WHITE || result == Draughts.BLACK) seatOf(result) else -1

    /** Les coups permis au joueur dont c'est le tour (aucun une fois la partie finie). */
    val legalMoves: List<DraughtsMove> get() = if (isOver) emptyList() else Draughts.legalMoves(cells, turn)

    /**
     * La séance après le coup du joueur assis à [seat] qui suit le chemin [path], ou null si ce n'est pas son tour ou
     * si le coup n'est pas permis. Celui qui ne peut plus jouer a perdu ; 25 coups de dames par camp sans prise ni
     * mouvement de pion rendent la partie nulle.
     */
    fun play(path: List<Int>, seat: Int): DraughtsMatch? {
        if (isOver || seat != currentSeat) return null
        val move = legalMoves.firstOrNull { it.path == path } ?: return null
        val before = cells
        val after = Draughts.afterMove(before, move)
        val nextQuiet = if (move.isCapture || Draughts.isMan(before[move.from])) 0 else quiet + 1
        val nextTurn = 3 - turn
        val moved = copy(board = Draughts.format(after), turn = nextTurn, quiet = nextQuiet, last = move.path)
        return when {
            Draughts.legalMoves(after, nextTurn).isEmpty() -> moved.finished(turn)
            nextQuiet >= Draughts.QUIET_LIMIT -> moved.finished(Draughts.DRAW)
            else -> moved
        }
    }

    /** La séance après l'abandon du joueur assis à [seat], ou null si la partie est déjà finie. */
    fun resign(seat: Int): DraughtsMatch? {
        if (isOver) return null
        return finished(3 - colorOf(seat))
    }

    /** Partie suivante (les couleurs s'échangent), ou null tant que la partie n'est pas finie. */
    fun newRound(): DraughtsMatch? =
        if (isOver) copy(board = Draughts.START, starter = 1 - starter, turn = Draughts.WHITE, quiet = 0, result = Draughts.ONGOING, last = emptyList()) else null

    // Termine la partie avec ce résultat et le compte au score.
    private fun finished(outcome: Int): DraughtsMatch {
        val ended = copy(result = outcome)
        return when {
            outcome == Draughts.DRAW -> ended.copy(draws = draws + 1)
            seatOf(outcome) == 0 -> ended.copy(winsA = winsA + 1)
            else -> ended.copy(winsB = winsB + 1)
        }
    }
}
