package com.aventure.messcores

import kotlin.math.abs

/** Identifiant des Échecs parmi les jeux prédéfinis (écran à part : voir [ChessScreen]). */
internal const val CHESS_ID = "builtin_chess"

/**
 * Un coup : case de départ et case d'arrivée (0 = a8, 7 = h8, 56 = a1, 63 = h1 : on lit de gauche à droite et de
 * haut en bas, les blancs en bas), et la pièce choisie quand un pion arrive au bout ('q', 'r', 'b' ou 'n').
 */
internal data class ChessMove(val from: Int, val to: Int, val promotion: Char? = null) {

    /** Notation de coordonnées : « e2e4 », « e7e8q ». */
    fun toText(): String = Chess.squareName(from) + Chess.squareName(to) + promotion?.toString().orEmpty()

    companion object {
        /** Le coup écrit dans [text], ou null si le texte n'est pas une notation de coordonnées. */
        fun parse(text: String): ChessMove? {
            if (text.length != 4 && text.length != 5) return null
            val from = Chess.squareOf(text.substring(0, 2)) ?: return null
            val to = Chess.squareOf(text.substring(2, 4)) ?: return null
            if (text.length == 4) return ChessMove(from, to)
            val promotion = text[4]
            return if (promotion in "qrbn") ChessMove(from, to, promotion) else null
        }
    }
}

/**
 * Une position : les 64 cases (une lettre par case, majuscule pour les blancs : P pion, N cavalier, B fou, R tour,
 * Q dame, K roi ; « . » pour une case vide), à qui de jouer, les roques encore permis (« KQkq »), la case de la
 * prise en passant possible (-1 si aucune) et les compteurs de la règle des 50 coups et des coups.
 */
internal data class ChessPosition(
    val board: String,
    val whiteToMove: Boolean,
    val castling: String,
    val enPassant: Int,
    val halfmove: Int,
    val fullmove: Int
)

/** Comment une manche se termine. */
internal enum class ChessReason {
    CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIFTY_MOVES, REPETITION, RESIGNATION, AGREEMENT, TIMEOUT
}

/** Fin d'une manche : le camp gagnant (true : les blancs, false : les noirs, null : manche nulle) et la raison. */
internal data class ChessResult(val winnerWhite: Boolean?, val reason: ChessReason)

/** Règles des échecs : génération des coups permis, application d'un coup et fins de partie. Fonctions pures. */
internal object Chess {

    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private const val FILES = "abcdefgh"

    private val KNIGHT_STEPS = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1
    )
    private val KING_STEPS = listOf(
        -1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1
    )
    private val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val STRAIGHTS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    // Une tour qui bouge ou qui est prise fait perdre le roque de son côté.
    private val CORNERS = listOf(63 to "K", 56 to "Q", 7 to "k", 0 to "q")

    private val PROMOTIONS = listOf('q', 'r', 'b', 'n')

    // ---------- Cases ----------

    /** Nom d'une case : « e4 ». */
    fun squareName(square: Int): String = "${FILES[square % 8]}${8 - square / 8}"

    /** La case nommée [name] (« e4 »), ou null si le nom n'est pas valide. */
    fun squareOf(name: String): Int? {
        if (name.length != 2) return null
        val file = FILES.indexOf(name[0])
        val rank = name[1] - '0'
        if (file < 0 || rank !in 1..8) return null
        return (8 - rank) * 8 + file
    }

    fun isWhite(piece: Char): Boolean = piece.isUpperCase()

    // ---------- Positions ----------

    fun startPosition(): ChessPosition = fromFen(START_FEN)!!

    /** Position lue depuis une notation FEN, ou null si elle est illisible. Sert aux tests. */
    fun fromFen(fen: String): ChessPosition? {
        val parts = fen.trim().split(" ")
        if (parts.size < 4) return null
        val board = StringBuilder()
        for (ch in parts[0]) {
            when {
                ch == '/' -> Unit
                ch in '1'..'8' -> repeat(ch - '0') { board.append('.') }
                ch in "pnbrqkPNBRQK" -> board.append(ch)
                else -> return null
            }
        }
        if (board.length != 64 || board.indexOf("K") < 0 || board.indexOf("k") < 0) return null
        val enPassant = if (parts[3] == "-") -1 else squareOf(parts[3]) ?: return null
        return ChessPosition(
            board = board.toString(),
            whiteToMove = parts[1] == "w",
            castling = if (parts[2] == "-") "" else parts[2],
            enPassant = enPassant,
            halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1
        )
    }

    // ---------- Échecs au roi ----------

    /** Vrai si la case [target] est attaquée par une pièce des blancs ([byWhite]) ou des noirs. */
    fun isAttacked(board: String, target: Int, byWhite: Boolean): Boolean {
        val row = target / 8
        val col = target % 8
        val pawnRow = if (byWhite) row + 1 else row - 1
        val pawn = if (byWhite) 'P' else 'p'
        if (pawnRow in 0..7) {
            if (col > 0 && board[pawnRow * 8 + col - 1] == pawn) return true
            if (col < 7 && board[pawnRow * 8 + col + 1] == pawn) return true
        }
        val knight = if (byWhite) 'N' else 'n'
        for ((dr, dc) in KNIGHT_STEPS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == knight) return true
        }
        val king = if (byWhite) 'K' else 'k'
        for ((dr, dc) in KING_STEPS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == king) return true
        }
        return slidesOnto(board, row, col, DIAGONALS, byWhite, 'B') ||
            slidesOnto(board, row, col, STRAIGHTS, byWhite, 'R')
    }

    // Une pièce du camp [byWhite] (le fou ou la tour [slider], ou la dame) voit-elle la case, dans ces directions ?
    private fun slidesOnto(
        board: String,
        row: Int,
        col: Int,
        directions: List<Pair<Int, Int>>,
        byWhite: Boolean,
        slider: Char
    ): Boolean {
        for ((dr, dc) in directions) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val piece = board[r * 8 + c]
                if (piece != '.') {
                    val kind = piece.uppercaseChar()
                    if (isWhite(piece) == byWhite && (kind == slider || kind == 'Q')) return true
                    break
                }
                r += dr
                c += dc
            }
        }
        return false
    }

    /** Vrai si le roi du camp [white] est en échec. */
    fun inCheck(position: ChessPosition, white: Boolean): Boolean {
        val king = position.board.indexOf(if (white) 'K' else 'k')
        return king >= 0 && isAttacked(position.board, king, !white)
    }

    // ---------- Coups ----------

    /** Les coups du camp qui joue, sans vérifier que son roi n'est pas laissé en échec. */
    private fun pseudoLegalMoves(position: ChessPosition): List<ChessMove> {
        val board = position.board
        val white = position.whiteToMove
        val moves = ArrayList<ChessMove>()
        for (from in 0 until 64) {
            val piece = board[from]
            if (piece == '.' || isWhite(piece) != white) continue
            val row = from / 8
            val col = from % 8
            when (piece.uppercaseChar()) {
                'P' -> addPawnMoves(position, from, moves)
                'N' -> addSteps(board, from, KNIGHT_STEPS, white, moves)
                'K' -> {
                    addSteps(board, from, KING_STEPS, white, moves)
                    addCastling(position, from, moves)
                }
                'B' -> addSlides(board, row, col, DIAGONALS, white, moves)
                'R' -> addSlides(board, row, col, STRAIGHTS, white, moves)
                else -> {
                    addSlides(board, row, col, DIAGONALS, white, moves)
                    addSlides(board, row, col, STRAIGHTS, white, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(position: ChessPosition, from: Int, moves: MutableList<ChessMove>) {
        val board = position.board
        val white = position.whiteToMove
        val row = from / 8
        val col = from % 8
        val step = if (white) -1 else 1
        val startRow = if (white) 6 else 1
        val lastRow = if (white) 0 else 7
        val nextRow = row + step
        if (nextRow !in 0..7) return

        fun add(to: Int) {
            if (nextRow == lastRow) PROMOTIONS.forEach { moves.add(ChessMove(from, to, it)) } else moves.add(ChessMove(from, to))
        }

        val one = nextRow * 8 + col
        if (board[one] == '.') {
            add(one)
            val two = one + step * 8
            if (row == startRow && board[two] == '.') moves.add(ChessMove(from, two))
        }
        for (dc in intArrayOf(-1, 1)) {
            if (col + dc !in 0..7) continue
            val to = nextRow * 8 + col + dc
            val target = board[to]
            if (target != '.' && isWhite(target) != white) add(to)
            else if (target == '.' && to == position.enPassant) moves.add(ChessMove(from, to))
        }
    }

    private fun addSteps(
        board: String,
        from: Int,
        steps: List<Pair<Int, Int>>,
        white: Boolean,
        moves: MutableList<ChessMove>
    ) {
        val row = from / 8
        val col = from % 8
        for ((dr, dc) in steps) {
            val r = row + dr
            val c = col + dc
            if (r !in 0..7 || c !in 0..7) continue
            val target = board[r * 8 + c]
            if (target == '.' || isWhite(target) != white) moves.add(ChessMove(from, r * 8 + c))
        }
    }

    private fun addSlides(
        board: String,
        row: Int,
        col: Int,
        directions: List<Pair<Int, Int>>,
        white: Boolean,
        moves: MutableList<ChessMove>
    ) {
        val from = row * 8 + col
        for ((dr, dc) in directions) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val target = board[r * 8 + c]
                if (target == '.') {
                    moves.add(ChessMove(from, r * 8 + c))
                } else {
                    if (isWhite(target) != white) moves.add(ChessMove(from, r * 8 + c))
                    break
                }
                r += dr
                c += dc
            }
        }
    }

    // Le roque : roi et tour n'ont pas bougé, rien entre eux, et le roi ne part pas d'une case attaquée,
    // ne passe pas par une case attaquée et n'arrive pas sur une case attaquée.
    private fun addCastling(position: ChessPosition, from: Int, moves: MutableList<ChessMove>) {
        val board = position.board
        val white = position.whiteToMove
        val home = if (white) 60 else 4
        if (from != home || isAttacked(board, from, !white)) return
        val rook = if (white) 'R' else 'r'
        val kingSide = if (white) 'K' else 'k'
        val queenSide = if (white) 'Q' else 'q'
        if (kingSide in position.castling && board[from + 1] == '.' && board[from + 2] == '.' &&
            board[from + 3] == rook && !isAttacked(board, from + 1, !white) && !isAttacked(board, from + 2, !white)
        ) {
            moves.add(ChessMove(from, from + 2))
        }
        if (queenSide in position.castling && board[from - 1] == '.' && board[from - 2] == '.' &&
            board[from - 3] == '.' && board[from - 4] == rook &&
            !isAttacked(board, from - 1, !white) && !isAttacked(board, from - 2, !white)
        ) {
            moves.add(ChessMove(from, from - 2))
        }
    }

    /** Les coups permis au camp qui joue : ceux qui ne laissent pas son roi en échec. */
    fun legalMoves(position: ChessPosition): List<ChessMove> =
        pseudoLegalMoves(position).filter { !inCheck(apply(position, it), position.whiteToMove) }

    /**
     * La position après [move], sans vérifier qu'il est permis : prise en passant, déplacement de la tour au roque,
     * promotion, droits de roque et compteurs sont mis à jour.
     */
    fun apply(position: ChessPosition, move: ChessMove): ChessPosition {
        val board = position.board.toCharArray()
        val white = position.whiteToMove
        val piece = board[move.from]
        val kind = piece.uppercaseChar()
        var captured = board[move.to] != '.'
        if (kind == 'P' && move.to == position.enPassant && board[move.to] == '.' && move.from % 8 != move.to % 8) {
            // Prise en passant : le pion pris n'est pas sur la case d'arrivée mais juste derrière.
            board[move.to + if (white) 8 else -8] = '.'
            captured = true
        }
        board[move.to] = piece
        board[move.from] = '.'
        val promotion = move.promotion
        if (promotion != null) board[move.to] = if (white) promotion.uppercaseChar() else promotion
        if (kind == 'K' && abs(move.to - move.from) == 2) {
            if (move.to > move.from) {
                board[move.from + 1] = board[move.from + 3]
                board[move.from + 3] = '.'
            } else {
                board[move.from - 1] = board[move.from - 4]
                board[move.from - 4] = '.'
            }
        }
        var rights = position.castling
        if (piece == 'K') rights = rights.replace("K", "").replace("Q", "")
        if (piece == 'k') rights = rights.replace("k", "").replace("q", "")
        for ((square, right) in CORNERS) {
            if (move.from == square || move.to == square) rights = rights.replace(right, "")
        }
        return ChessPosition(
            board = String(board),
            whiteToMove = !white,
            castling = rights,
            enPassant = if (kind == 'P' && abs(move.to - move.from) == 16) (move.from + move.to) / 2 else -1,
            halfmove = if (kind == 'P' || captured) 0 else position.halfmove + 1,
            fullmove = position.fullmove + if (white) 0 else 1
        )
    }

    /** Nombre de parties de [depth] demi-coups possibles depuis [position] : sert à vérifier les règles. */
    fun perft(position: ChessPosition, depth: Int): Long {
        if (depth == 0) return 1
        return legalMoves(position).sumOf { perft(apply(position, it), depth - 1) }
    }

    // ---------- Fins de partie ----------

    /**
     * Vrai si aucun mat n'est plus possible : les deux rois seuls, ou avec un seul fou ou un seul cavalier, ou
     * avec des fous qui sont tous sur des cases de la même couleur.
     */
    fun insufficientMaterial(board: String): Boolean {
        val minors = ArrayList<Int>()
        for (square in 0 until 64) {
            when (board[square].uppercaseChar()) {
                'P', 'R', 'Q' -> return false
                'B', 'N' -> minors.add(square)
            }
        }
        if (minors.size <= 1) return true
        if (minors.any { board[it].uppercaseChar() != 'B' }) return false
        return minors.map { (it / 8 + it % 8) % 2 }.toSet().size == 1
    }

    /**
     * Identifie une position pour la triple répétition : les pièces, le camp qui joue, les roques permis et la prise
     * en passant seulement si elle est vraiment possible.
     */
    fun repetitionKey(position: ChessPosition): String {
        val enPassant = if (
            position.enPassant >= 0 &&
            legalMoves(position).any { it.to == position.enPassant && position.board[it.from].uppercaseChar() == 'P' }
        ) position.enPassant else -1
        return position.board + (if (position.whiteToMove) 'w' else 'b') + position.castling + enPassant
    }
}

/**
 * Une partie d'échecs en cours : la position, les coups joués et les positions déjà vues (pour la triple
 * répétition). Immuable : jouer un coup donne une nouvelle partie.
 */
internal class ChessGame private constructor(
    val position: ChessPosition,
    val moves: List<ChessMove>,
    private val keys: List<String>
) {

    /** Les coups permis au camp qui joue. */
    val legalMoves: List<ChessMove> by lazy { Chess.legalMoves(position) }

    /** Vrai si le roi du camp qui joue est en échec. */
    val inCheck: Boolean by lazy { Chess.inCheck(position, position.whiteToMove) }

    val lastMove: ChessMove? get() = moves.lastOrNull()

    /**
     * La fin de la partie, ou null si elle continue. Mat et pat passent avant les nulles automatiques :
     * un mat au 100e demi-coup reste un mat.
     */
    val result: ChessResult? by lazy {
        when {
            legalMoves.isEmpty() ->
                if (inCheck) ChessResult(!position.whiteToMove, ChessReason.CHECKMATE)
                else ChessResult(null, ChessReason.STALEMATE)
            Chess.insufficientMaterial(position.board) -> ChessResult(null, ChessReason.INSUFFICIENT_MATERIAL)
            keys.count { it == keys.last() } >= 3 -> ChessResult(null, ChessReason.REPETITION)
            position.halfmove >= 100 -> ChessResult(null, ChessReason.FIFTY_MOVES)
            else -> null
        }
    }

    /** La partie après [move], ou null si le coup n'est pas permis ou si la partie est finie. */
    fun play(move: ChessMove): ChessGame? {
        if (result != null) return null
        val legal = legalMoves.firstOrNull { it == move } ?: return null
        val next = Chess.apply(position, legal)
        return ChessGame(next, moves + legal, keys + Chess.repetitionKey(next))
    }

    companion object {
        /** Une partie qui commence à [position] (la position de départ par défaut). */
        fun start(position: ChessPosition = Chess.startPosition()): ChessGame =
            ChessGame(position, emptyList(), listOf(Chess.repetitionKey(position)))

        /**
         * La partie obtenue en rejouant [text] (coups en notation de coordonnées séparés par des espaces).
         * Un coup illisible ou interdit arrête la lecture : on garde ce qui précède.
         */
        fun fromMoves(text: String): ChessGame {
            var game = start()
            for (token in text.split(" ")) {
                if (token.isEmpty()) continue
                val move = ChessMove.parse(token) ?: break
                game = game.play(move) ?: break
            }
            return game
        }
    }
}
