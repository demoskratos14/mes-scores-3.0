package com.aventure.messcores

/** Règles de calcul du total d'un joueur propres à certains jeux du mode TABLE. */
object ScoreRules {

    /**
     * Total d'un jeu où il faut atteindre [target] pile (Mölkky) : si une manche fait dépasser
     * [target], le score retombe à [reset]. Les cases vides (null) sont ignorées.
     */
    fun exactTarget(values: List<Int?>, target: Int, reset: Int): Int {
        var total = 0
        for (value in values) {
            if (value == null) continue
            total += value
            if (total > target) total = reset
        }
        return total
    }

    /**
     * Score restant d'un jeu qui se décompte depuis [start] jusqu'à 0 pile (fléchettes 301 / 501).
     * Un tour qui ferait passer sous 0, ou s'arrêter à 1 (impossible à finir sur un double), est
     * annulé : on reste au score d'avant.
     */
    fun countdown(values: List<Int?>, start: Int): Int {
        var remaining = start
        for (value in values) {
            if (value == null) continue
            val after = remaining - value
            if (after >= 0 && after != 1) remaining = after
        }
        return remaining
    }
}

/** Total d'un joueur en mode TABLE : [values] donne le score de chaque ligne (null = case vide). */
fun GameRules.tableTotal(values: List<Int?>): Int = when {
    sheet == ScoreSheets.BOWLING -> BowlingScoring.total(values)
    countdownFrom != null -> ScoreRules.countdown(values, countdownFrom)
    exactTarget != null -> ScoreRules.exactTarget(values, exactTarget, exactReset ?: 0)
    else -> values.sumOf { it ?: 0 } + ScoreSheets.bonus(sheet, values)
}

/**
 * Bowling : chaque ligne du tableau est une frame, dont les lancers sont enregistrés dans un seul
 * nombre (base 12, 11 = « pas de lancer »). Les strikes et les spares sont comptés avec les lancers
 * suivants, comme à la règle.
 */
object BowlingScoring {
    private const val NONE = 11

    fun encode(rolls: List<Int>): Int {
        var code = 0
        var multiplier = 1
        repeat(3) { i ->
            code += (rolls.getOrNull(i) ?: NONE) * multiplier
            multiplier *= 12
        }
        return code
    }

    fun decode(code: Int): List<Int> {
        val rolls = mutableListOf<Int>()
        var rest = code
        repeat(3) {
            val roll = rest % 12
            rest /= 12
            if (roll != NONE) rolls.add(roll)
        }
        return rolls
    }

    /** Nombre maximum de quilles pour le prochain lancer de la [frame] (0 à 9), ou null si elle est finie. */
    fun maxNextRoll(frame: Int, rolls: List<Int>): Int? {
        if (frame < 9) {
            return when (rolls.size) {
                0 -> 10
                1 -> if (rolls[0] == 10) null else 10 - rolls[0]
                else -> null
            }
        }
        return when (rolls.size) {
            0 -> 10
            1 -> if (rolls[0] == 10) 10 else 10 - rolls[0]
            2 -> when {
                rolls[0] == 10 -> if (rolls[1] == 10) 10 else 10 - rolls[1]
                rolls[0] + rolls[1] == 10 -> 10
                else -> null
            }
            else -> null
        }
    }

    /** Notation classique d'une frame : X (strike), / (spare), - (zéro), sinon le nombre de quilles. */
    fun notation(rolls: List<Int>): String {
        var standing = 10
        var rollsInRack = 0
        return rolls.joinToString(" ") { roll ->
            val fresh = rollsInRack == 0
            if (roll == standing) {
                // Toutes les quilles restantes tombent : strike sur un râtelier neuf, sinon spare.
                standing = 10
                rollsInRack = 0
                if (fresh) "X" else "/"
            } else {
                standing -= roll
                rollsInRack++
                if (roll == 0) "-" else roll.toString()
            }
        }
    }

    /**
     * Score cumulé après chaque frame, ou null tant que les lancers suivants (bonus de strike ou
     * de spare) ne sont pas connus. [codes] donne la frame de chaque ligne (null = pas encore jouée).
     */
    fun cumulative(codes: List<Int?>): List<Int?> {
        val frames = (0 until 10).map { codes.getOrNull(it)?.let(::decode).orEmpty() }
        val flat = frames.flatten()
        var position = 0
        var total = 0
        var known = true
        return frames.mapIndexed { frame, rolls ->
            if (rolls.isEmpty()) {
                known = false
                return@mapIndexed null
            }
            val start = position
            position += rolls.size
            val score: Int? = when {
                frame == 9 -> rolls.sum()
                rolls[0] == 10 -> if (flat.size > start + 2) 10 + flat[start + 1] + flat[start + 2] else null
                rolls.size >= 2 && rolls[0] + rolls[1] == 10 -> if (flat.size > start + 2) 10 + flat[start + 2] else null
                rolls.size >= 2 -> rolls[0] + rolls[1]
                else -> null
            }
            if (known && score != null) {
                total += score
                total
            } else {
                known = false
                null
            }
        }
    }

    /** Score du joueur : dernier cumul connu (0 avant le premier lancer). */
    fun total(codes: List<Int?>): Int = cumulative(codes).lastOrNull { it != null } ?: 0

    /** Texte d'une case : notation de la frame et, quand il est connu, le score cumulé sur une seconde ligne. */
    fun display(code: Int?, cumulative: Int?): String? =
        code?.let { notation(decode(it)) + (cumulative?.let { score -> "\n$score" } ?: "") }
}
