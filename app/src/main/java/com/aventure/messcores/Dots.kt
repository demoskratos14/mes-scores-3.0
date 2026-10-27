package com.aventure.messcores

/** Identifiant de la Pipopipette parmi les jeux prédéfinis (écran à part : voir [DuoGameScreen]). */
internal const val DOTS_ID = "builtin_dots"

/**
 * Règles de la Pipopipette (jeu des points et des carrés) : une grille de 6 × 6 points forme 5 × 5 carrés. À tour de
 * rôle, chacun trace un trait entre deux points voisins ; celui qui ferme le quatrième côté d'un carré le gagne et
 * rejoue. Quand tous les carrés sont pris, celui qui en a le plus gagne la manche.
 *
 * Les 60 traits sont numérotés : d'abord les 30 traits horizontaux (6 lignes de 5, de gauche à droite et de haut en
 * bas), puis les 30 traits verticaux (5 lignes de 6). Les 25 carrés sont lus comme une grille de gauche à droite et de
 * haut en bas.
 */
internal object Dots {

    /** Nombre de carrés sur un côté. */
    const val SIZE = 5

    /** Nombre de traits horizontaux (et de traits verticaux). */
    const val HORIZONTAL = (SIZE + 1) * SIZE

    const val EDGES = 2 * HORIZONTAL
    const val BOXES = SIZE * SIZE

    val EMPTY_EDGES: String = "0".repeat(EDGES)
    val EMPTY_BOXES: String = "0".repeat(BOXES)

    /** Le trait horizontal de la ligne de points [row] (0 à [SIZE]) qui part de la colonne [col] (0 à [SIZE] - 1). */
    fun horizontal(row: Int, col: Int): Int = row * SIZE + col

    /** Le trait vertical qui part de la ligne de points [row] (0 à [SIZE] - 1), sur la colonne de points [col] (0 à [SIZE]). */
    fun vertical(row: Int, col: Int): Int = HORIZONTAL + row * (SIZE + 1) + col

    fun isHorizontal(edge: Int): Boolean = edge < HORIZONTAL

    /** Les quatre traits qui entourent le carré [box]. */
    fun edgesOf(box: Int): List<Int> {
        val row = box / SIZE
        val col = box % SIZE
        return listOf(horizontal(row, col), horizontal(row + 1, col), vertical(row, col), vertical(row, col + 1))
    }

    /** Les carrés (un ou deux) que touche le trait [edge]. */
    fun boxesOf(edge: Int): List<Int> {
        if (isHorizontal(edge)) {
            val row = edge / SIZE
            val col = edge % SIZE
            return listOfNotNull(if (row > 0) (row - 1) * SIZE + col else null, if (row < SIZE) row * SIZE + col else null)
        }
        val row = (edge - HORIZONTAL) / (SIZE + 1)
        val col = (edge - HORIZONTAL) % (SIZE + 1)
        return listOfNotNull(if (col > 0) row * SIZE + col - 1 else null, if (col < SIZE) row * SIZE + col else null)
    }

    /** Vrai si [text] est un texte de [length] chiffres de 0 à 2. */
    fun isValid(text: String, length: Int): Boolean = text.length == length && text.all { it in '0'..'2' }
}

/**
 * Une séance de Pipopipette : les traits tracés (0 : libre, 1 ou 2 : tracé par ce joueur), le propriétaire de chaque
 * carré, le joueur à qui c'est de jouer (celui qui vient de fermer un carré rejoue), qui a commencé la manche
 * (0 : le premier joueur, 1 : le deuxième), le score et le dernier trait tracé (-1 : aucun), mis en valeur à l'écran.
 */
internal data class DotsMatch(
    val edges: String = Dots.EMPTY_EDGES,
    val boxes: String = Dots.EMPTY_BOXES,
    override val current: Int = 1,
    val starter: Int = 0,
    override val winsA: Int = 0,
    override val winsB: Int = 0,
    override val draws: Int = 0,
    val last: Int = -1
) : DuoMatch<DotsMatch> {

    /** Carrés pris par le premier joueur dans la manche en cours. */
    val boxesA: Int get() = boxes.count { it == '1' }

    /** Carrés pris par le deuxième joueur dans la manche en cours. */
    val boxesB: Int get() = boxes.count { it == '2' }

    override val isOver: Boolean get() = boxes.none { it == '0' }

    override val winner: Int
        get() = when {
            !isOver -> 0
            boxesA > boxesB -> 1
            boxesB > boxesA -> 2
            else -> 0
        }

    override fun play(move: Int, player: Int): DotsMatch? {
        if (player != current || isOver || move !in 0 until Dots.EDGES || edges[move] != '0') return null
        val newEdges = StringBuilder(edges).also { it[move] = '0' + player }.toString()
        val newBoxes = StringBuilder(boxes)
        var closed = 0
        for (box in Dots.boxesOf(move)) {
            if (newBoxes[box] == '0' && Dots.edgesOf(box).all { newEdges[it] != '0' }) {
                newBoxes[box] = '0' + player
                closed++
            }
        }
        // Fermer un carré donne la main une deuxième fois ; sinon, c'est à l'autre.
        val next = copy(edges = newEdges, boxes = newBoxes.toString(), current = if (closed > 0) player else 3 - player, last = move)
        if (!next.isOver) return next
        return when (next.winner) {
            1 -> next.copy(winsA = winsA + 1)
            2 -> next.copy(winsB = winsB + 1)
            else -> next.copy(draws = draws + 1)
        }
    }

    override fun newRound(): DotsMatch? =
        if (isOver) copy(edges = Dots.EMPTY_EDGES, boxes = Dots.EMPTY_BOXES, starter = 1 - starter, current = 2 - starter, last = -1)
        else null

    override fun encode(): String = listOf(edges, boxes, current, starter, winsA, winsB, draws, last).joinToString(";")

    companion object {
        /** La séance lue depuis son texte, ou null s'il est illisible ; un joueur ou un score absurde est ramené à une valeur permise. */
        fun decode(text: String): DotsMatch? {
            val parts = text.split(";")
            if (parts.size != 8) return null
            if (!Dots.isValid(parts[0], Dots.EDGES) || !Dots.isValid(parts[1], Dots.BOXES)) return null
            return DotsMatch(
                edges = parts[0],
                boxes = parts[1],
                current = parseDuoInt(parts[2], 1, 2),
                starter = parseDuoInt(parts[3], 0, 1),
                winsA = parseDuoInt(parts[4], 0),
                winsB = parseDuoInt(parts[5], 0),
                draws = parseDuoInt(parts[6], 0),
                last = parseDuoInt(parts[7], -1, Dots.EDGES - 1)
            )
        }
    }
}
