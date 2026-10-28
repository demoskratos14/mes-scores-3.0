package com.aventure.messcores

/** Identifiant de Pierre-feuille-ciseaux parmi les jeux prédéfinis (écran à part : voir [RockPaperScissorsScreen]). */
internal const val PFC_ID = "builtin_pfc"

/**
 * Règles de Pierre-feuille-ciseaux : la pierre casse les ciseaux, les ciseaux coupent la feuille, la feuille
 * enveloppe la pierre. Un choix est un entier : 1 = pierre, 2 = feuille, 3 = ciseaux, 0 = pas encore choisi.
 *
 * La version « lézard Spock » ajoute 4 = lézard et 5 = Spock : la pierre écrase le lézard, le lézard empoisonne
 * Spock, Spock casse les ciseaux, les ciseaux décapitent le lézard, le lézard mange la feuille, la feuille
 * désavoue Spock et Spock vaporise la pierre (les trois règles du jeu classique restent les mêmes).
 */
internal object RockPaperScissors {

    const val ROCK = 1
    const val PAPER = 2
    const val SCISSORS = 3
    const val LIZARD = 4
    const val SPOCK = 5

    /**
     * Valeur qui remplace le choix de l'adversaire tant que la manche n'est pas révélée : on sait qu'il a choisi,
     * pas quoi. C'est elle qui voyage sur la connexion, jamais le vrai choix de l'autre joueur.
     */
    const val HIDDEN = 6

    /** Les choix du jeu classique. */
    val CHOICES: List<Int> = listOf(ROCK, PAPER, SCISSORS)

    /** Les choix de la version « lézard Spock ». */
    val EXTENDED_CHOICES: List<Int> = listOf(ROCK, PAPER, SCISSORS, LIZARD, SPOCK)

    /** Les choix permis selon la version : classique, ou « lézard Spock » si [extended]. */
    fun choices(extended: Boolean): List<Int> = if (extended) EXTENDED_CHOICES else CHOICES

    // Pour chaque choix, ceux qu'il bat.
    private val BEATS: Map<Int, Set<Int>> = mapOf(
        ROCK to setOf(SCISSORS, LIZARD),
        PAPER to setOf(ROCK, SPOCK),
        SCISSORS to setOf(PAPER, LIZARD),
        LIZARD to setOf(SPOCK, PAPER),
        SPOCK to setOf(SCISSORS, ROCK)
    )

    /** Nombres de manches à gagner proposés pour remporter la partie. */
    val TARGETS: List<Int> = listOf(1, 2, 3, 5)

    const val DEFAULT_TARGET = 3

    /** Vrai pour n'importe quel choix, de l'une ou l'autre version. */
    fun isChoice(value: Int): Boolean = value in ROCK..SPOCK

    /** Vrai si [value] est un choix permis dans la version demandée (le lézard et Spock n'existent qu'en [extended]). */
    fun isChoice(value: Int, extended: Boolean): Boolean = value in choices(extended)

    /** Symbole affiché pour un choix ; vide si le choix n'est pas fait, « ? » s'il est caché. */
    fun symbol(value: Int): String = when (value) {
        ROCK -> "✊"
        PAPER -> "✋"
        SCISSORS -> "✌️"
        LIZARD -> "🦎"
        SPOCK -> "🖖"
        HIDDEN -> "❓"
        else -> ""
    }

    /**
     * Résultat d'une manche : 1 si [first] bat [second], 2 si [second] bat [first], 0 pour une égalité ou si l'un
     * des deux choix n'est pas valide. Chaque choix en bat deux autres (voir la description de l'objet) ; en jeu
     * classique, feuille > pierre, ciseaux > feuille, pierre > ciseaux.
     */
    fun outcome(first: Int, second: Int): Int = when {
        !isChoice(first) || !isChoice(second) || first == second -> 0
        second in BEATS.getValue(first) -> 1
        else -> 2
    }
}

/**
 * Une partie de Pierre-feuille-ciseaux entre deux joueurs : le joueur 1 (A) et le joueur 2 (B). On gagne la partie en
 * remportant [target] manches ; le score des victoires et des égalités reste affiché.
 *
 * La version « lézard Spock » ([extended]) ajoute deux choix ; elle se décide avant la première manche.
 *
 * Les deux joueurs choisissent en secret : [pickA] et [pickB] valent 0 tant que le joueur n'a pas choisi. Dès que le
 * deuxième choix est fait, la manche est révélée et le score est mis à jour. Sur deux téléphones, la copie envoyée à
 * un joueur ne contient jamais le choix de l'autre avant la révélation (voir [maskedFor]).
 */
internal data class RpsMatch(
    val target: Int = RockPaperScissors.DEFAULT_TARGET,
    val winsA: Int = 0,
    val winsB: Int = 0,
    val draws: Int = 0,
    val pickA: Int = 0,
    val pickB: Int = 0,
    val extended: Boolean = false
) {
    /** Vrai quand les deux joueurs ont choisi : la manche est révélée et comptée. */
    val revealed: Boolean get() = RockPaperScissors.isChoice(pickA) && RockPaperScissors.isChoice(pickB)

    /** Vainqueur de la manche révélée : 1, 2, ou 0 pour une égalité (ou si la manche n'est pas révélée). */
    val roundWinner: Int get() = if (revealed) RockPaperScissors.outcome(pickA, pickB) else 0

    /** Vrai quand un joueur a remporté assez de manches. */
    val isOver: Boolean get() = winsA >= target || winsB >= target

    /** Vainqueur de la partie : 1, 2, ou 0 tant qu'elle n'est pas finie. */
    val winner: Int get() = when {
        winsA >= target -> 1
        winsB >= target -> 2
        else -> 0
    }

    /** Vrai tant que rien n'a été joué : le nombre de manches peut encore changer et on peut passer à deux téléphones. */
    val isFresh: Boolean get() = this == RpsMatch(target = target, extended = extended)

    /** Vrai si le joueur [player] (1 ou 2) a déjà fait son choix de la manche en cours. */
    fun hasPicked(player: Int): Boolean = when (player) {
        1 -> pickA != 0
        2 -> pickB != 0
        else -> false
    }

    /**
     * La partie après le choix [choice] du joueur [player], ou null si ce n'est pas permis : choix invalide, partie
     * finie, ou joueur qui a déjà choisi. Le deuxième choix révèle la manche et la compte.
     */
    fun pick(player: Int, choice: Int): RpsMatch? {
        if (!RockPaperScissors.isChoice(choice, extended) || isOver || hasPicked(player)) return null
        val next = when (player) {
            1 -> copy(pickA = choice)
            2 -> copy(pickB = choice)
            else -> return null
        }
        if (!next.revealed) return next
        return when (next.roundWinner) {
            1 -> next.copy(winsA = winsA + 1)
            2 -> next.copy(winsB = winsB + 1)
            else -> next.copy(draws = draws + 1)
        }
    }

    /** Manche suivante, ou null tant que la manche n'est pas révélée ou si la partie est finie. */
    fun nextRound(): RpsMatch? = if (revealed && !isOver) copy(pickA = 0, pickB = 0) else null

    /** Nouvelle partie (mêmes règles, scores à zéro), ou null tant que la partie n'est pas finie. */
    fun newMatch(): RpsMatch? = if (isOver) RpsMatch(target = target, extended = extended) else null

    /** Manche suivante, ou nouvelle partie si celle-ci est finie ; null si la manche n'est pas encore révélée. */
    fun advance(): RpsMatch? = if (isOver) newMatch() else nextRound()

    /** Même partie avec un autre nombre de manches à gagner ; refusé (null) dès que quelque chose a été joué. */
    fun withTarget(value: Int): RpsMatch? =
        if (isFresh && value in RockPaperScissors.TARGETS) copy(target = value) else null

    /** Même partie dans l'autre version (classique ou « lézard Spock ») ; refusé (null) dès que quelque chose a été joué. */
    fun withExtended(value: Boolean): RpsMatch? = if (isFresh) copy(extended = value) else null

    /**
     * La copie à envoyer au joueur [player] : tant que la manche n'est pas révélée, le choix de l'autre joueur est
     * remplacé par [RockPaperScissors.HIDDEN] s'il a choisi, par 0 sinon.
     */
    fun maskedFor(player: Int): RpsMatch {
        if (revealed) return this
        fun hide(pick: Int) = if (pick != 0) RockPaperScissors.HIDDEN else 0
        return when (player) {
            1 -> copy(pickB = hide(pickB))
            2 -> copy(pickA = hide(pickA))
            else -> copy(pickA = hide(pickA), pickB = hide(pickB))
        }
    }

    /** La partie sous forme de texte (se garde tel quel quand l'écran tourne, et voyage sur la connexion). */
    fun encode(): String = listOf(target, winsA, winsB, draws, pickA, pickB, if (extended) 1 else 0).joinToString(",")

    companion object {
        /**
         * Partie relue depuis son texte. Un texte illisible redonne une partie neuve ; un nombre de manches, un score
         * ou un choix absurde est ramené à une valeur permise.
         */
        fun decode(text: String): RpsMatch {
            val parts = text.split(",").map { it.trim().toIntOrNull() ?: return RpsMatch() }
            // Six nombres : texte écrit avant l'arrivée de la version « lézard Spock » (version classique).
            if (parts.size !in 6..7) return RpsMatch()
            val extended = parts.getOrNull(6) == 1
            fun pick(value: Int): Int = value.takeIf {
                it in 0..RockPaperScissors.HIDDEN && (it == 0 || it == RockPaperScissors.HIDDEN || RockPaperScissors.isChoice(it, extended))
            } ?: 0
            return RpsMatch(
                target = parts[0].takeIf { it in RockPaperScissors.TARGETS } ?: RockPaperScissors.DEFAULT_TARGET,
                winsA = parts[1].coerceAtLeast(0),
                winsB = parts[2].coerceAtLeast(0),
                draws = parts[3].coerceAtLeast(0),
                pickA = pick(parts[4]),
                pickB = pick(parts[5]),
                extended = extended
            )
        }
    }
}
