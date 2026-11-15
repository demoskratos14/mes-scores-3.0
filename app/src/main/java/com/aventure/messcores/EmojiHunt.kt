package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Identifiant de la Chasse à l'émoji parmi les jeux prédéfinis (écran à part : voir [EmojiScreen]). */
internal const val EMOJI_ID = "builtin_emoji"

/** Les réglages d'un niveau : combien de pièges, quelle part de pièges ressemblants, quelle vitesse, combien de temps. */
internal data class EmojiLevel(
    val level: Int,
    /** Nombre de dessins pièges, en plus du dessin recherché. */
    val decoys: Int,
    /** Part des pièges tirés dans la même famille que le dessin recherché (les plus ressemblants). */
    val similarShare: Float,
    /** Vitesse des dessins en part de l'écran par seconde (0 : ils restent immobiles). */
    val speed: Float,
    /** Temps maximal pour trouver le dessin, en millisecondes. */
    val timeLimitMs: Long
) {
    val moving: Boolean get() = speed > 0f
}

/** Un dessin du plateau : le symbole, sa place de départ et sa vitesse (en part de l'écran, de 0 à 1 dans chaque sens). */
internal data class EmojiItem(
    val symbol: String,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float
)

/**
 * Un plateau : tous les dessins affichés et la place de celui qu'il faut trouver ([targetIndex]). Il ne dépend que de la
 * graine et du niveau : tous les téléphones d'une partie voient exactement le même plateau, et les positions à l'instant
 * `t` se calculent sans rien garder en mémoire ([positionAt]).
 */
internal class EmojiBoard(
    val level: EmojiLevel,
    val items: List<EmojiItem>,
    val targetIndex: Int,
    /** Nombre de colonnes et de lignes de la grille de départ, qui sert aussi à choisir la taille des dessins. */
    val columns: Int,
    val rows: Int
) {
    val target: EmojiItem get() = items[targetIndex]

    /** La position (en part de l'écran) du dessin [index] [elapsedMs] millisecondes après l'affichage. */
    fun positionAt(index: Int, elapsedMs: Long): Pair<Float, Float> {
        val item = items[index]
        if (!level.moving) return item.x to item.y
        val seconds = elapsedMs.coerceAtLeast(0L) / 1000f
        return EmojiHunt.bounce(item.x, item.vx * seconds) to EmojiHunt.bounce(item.y, item.vy * seconds)
    }

    /**
     * Le dessin touché par un doigt posé en ([tapX], [tapY]) (en part de l'écran), ou -1. Le plus proche gagne quand
     * plusieurs se recouvrent ; [reachX] et [reachY] sont la portée du doigt, en part de l'écran. Les dessins de
     * [ignored] (déjà essayés) ne comptent plus.
     */
    fun hit(tapX: Float, tapY: Float, elapsedMs: Long, reachX: Float, reachY: Float, ignored: Set<Int> = emptySet()): Int {
        if (reachX <= 0f || reachY <= 0f) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (index in items.indices) {
            if (index in ignored) continue
            val (x, y) = positionAt(index, elapsedMs)
            val dx = (tapX - x) / reachX
            val dy = (tapY - y) / reachY
            val distance = hypot(dx, dy)
            if (distance <= 1f && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }
}

/** Réglages et calculs de la Chasse à l'émoji. */
internal object EmojiHunt {

    /** Seul sur un téléphone (le record compte) ; à plusieurs, chacun sur le sien. */
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 8

    /** Nombre de manches à gagner pour remporter une partie à plusieurs téléphones. */
    const val TARGET = 5

    /** Un mauvais dessin touché coûte une seconde : elle s'ajoute au temps du joueur (ou est retirée du temps restant en solo). */
    const val PENALTY_MS = 1_000L

    /** Attente avant l'affichage du plateau, à plusieurs téléphones : on ne peut pas l'anticiper. */
    const val MIN_DELAY_MS = 1_500L
    const val MAX_DELAY_MS = 4_000L

    /** Résultat d'un joueur pour une manche : pas trouvé à temps. Un temps est positif. */
    const val MISS = -1

    /** Au-delà de cette durée le temps n'a plus de sens : on le plafonne. */
    const val MAX_RESULT_MS = 99_999

    // Difficulté : plus le niveau monte, plus il y a de pièges, plus ils se ressemblent, plus le temps est court, et
    // à partir de MOVE_FROM_LEVEL ils se déplacent.
    private const val FIRST_DECOYS = 3
    private const val DECOYS_STEP = 3
    const val MAX_DECOYS = 40
    private const val FIRST_SIMILAR_SHARE = 0.25f
    private const val SIMILAR_SHARE_STEP = 0.08f
    private const val MAX_SIMILAR_SHARE = 0.9f
    const val MOVE_FROM_LEVEL = 6
    private const val FIRST_SPEED = 0.05f
    private const val SPEED_STEP = 0.025f
    private const val MAX_SPEED = 0.3f
    private const val FIRST_TIME_MS = 8_000L
    private const val TIME_STEP_MS = 400L
    const val MIN_TIME_MS = 3_000L

    /** Marge de rebond : le centre d'un dessin ne s'approche jamais plus près d'un bord (part de l'écran). */
    private const val MARGIN = 0.07f

    /** Les réglages du niveau [level] (le premier niveau est le 1). */
    fun level(level: Int): EmojiLevel {
        val n = level.coerceAtLeast(1)
        val steps = n - 1
        return EmojiLevel(
            level = n,
            decoys = (FIRST_DECOYS + steps * DECOYS_STEP).coerceAtMost(MAX_DECOYS),
            similarShare = (FIRST_SIMILAR_SHARE + steps * SIMILAR_SHARE_STEP).coerceAtMost(MAX_SIMILAR_SHARE),
            speed = if (n < MOVE_FROM_LEVEL) 0f else (FIRST_SPEED + (n - MOVE_FROM_LEVEL) * SPEED_STEP).coerceAtMost(MAX_SPEED),
            timeLimitMs = (FIRST_TIME_MS - steps * TIME_STEP_MS).coerceAtLeast(MIN_TIME_MS)
        )
    }

    /** Tire au hasard l'attente avant l'affichage du plateau, en millisecondes. */
    fun randomDelayMs(random: Random = Random.Default): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)

    /**
     * Les familles de dessins qui se ressemblent : visages, drapeaux, horloges, lunes… Tous les symboles existent depuis
     * Unicode 9 au plus, pour s'afficher aussi sur un vieux téléphone.
     */
    val FAMILIES: List<List<String>> = listOf(
        listOf("😀", "😃", "😄", "😁", "😆", "😊", "🙂", "😉", "😋", "😎", "😍", "😘"),
        listOf("😢", "😭", "😞", "😟", "😥", "😰", "😓", "😔", "😩", "😫"),
        listOf("😠", "😡", "😤", "😖", "😣", "😒", "😑", "😐", "😶"),
        listOf("🇫🇷", "🇳🇱", "🇱🇺", "🇷🇺", "🇸🇰", "🇸🇮", "🇭🇷", "🇨🇿"),
        listOf("🇮🇹", "🇮🇪", "🇲🇽", "🇭🇺", "🇧🇬", "🇨🇮"),
        listOf("🇩🇰", "🇸🇪", "🇳🇴", "🇫🇮", "🇮🇸"),
        listOf("🇩🇪", "🇧🇪", "🇪🇸", "🇬🇧", "🇺🇸", "🇯🇵", "🇧🇷", "🇨🇦", "🇨🇭", "🇦🇹"),
        listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷"),
        listOf("🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗"),
        listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"),
        listOf("❤️", "💛", "💚", "💙", "💜", "🖤", "💔"),
        listOf("🍎", "🍏", "🍅", "🍒", "🍓", "🍑", "🍊", "🍋"),
        listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱"),
        listOf("👍", "👎", "👌", "👊", "✊", "✋", "👏", "🙌"),
        listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🚓", "🚑", "🚒"),
        listOf("🌲", "🌳", "🌴", "🌵", "🌿", "🍀", "🌱"),
        listOf("🐟", "🐠", "🐡", "🐬", "🐳", "🐋", "🦈", "🐙"),
        listOf("🐝", "🐞", "🐜", "🦋", "🐛", "🐌"),
        listOf("🐔", "🐓", "🐣", "🐤", "🐥", "🐦", "🐧", "🦆", "🦅", "🦉")
    )

    /** Le plateau du niveau [level] pour la graine [seed] : toujours le même pour les mêmes valeurs. */
    fun generate(seed: Long, level: Int): EmojiBoard {
        val settings = level(level)
        val random = Random(seed)

        // Le dessin recherché et sa famille.
        val family = FAMILIES[random.nextInt(FAMILIES.size)]
        val target = family[random.nextInt(family.size)]

        // Les pièges qui lui ressemblent d'abord, puis ceux d'autres familles.
        val similarPool = family.filter { it != target }.shuffled(random)
        val similarCount = minOf(similarPool.size, (settings.decoys * settings.similarShare).roundToInt())
        val others = FAMILIES.filter { it !== family }.flatten().shuffled(random)
        val decoys = mutableListOf<String>()
        decoys += similarPool.take(similarCount)
        var next = 0
        while (decoys.size < settings.decoys) {
            decoys += others[next % others.size]
            next++
        }

        val symbols = (listOf(target) + decoys).shuffled(random)
        val targetIndex = symbols.indexOf(target)

        // Une grille aux cases mélangées, pour que rien ne se recouvre au départ ; chaque dessin est un peu décalé dans sa case.
        val count = symbols.size
        val columns = ceil(sqrt(count * GRID_ASPECT)).toInt().coerceAtLeast(1)
        val rows = ceil(count.toFloat() / columns).toInt().coerceAtLeast(1)
        val cells = (0 until columns * rows).shuffled(random).take(count)
        val items = symbols.mapIndexed { i, symbol ->
            val column = cells[i] % columns
            val row = cells[i] / columns
            val x = (column + 0.5f + (random.nextFloat() - 0.5f) * JITTER) / columns
            val y = (row + 0.5f + (random.nextFloat() - 0.5f) * JITTER) / rows
            val angle = random.nextFloat() * (2f * Math.PI.toFloat())
            EmojiItem(
                symbol = symbol,
                x = x.coerceIn(MARGIN, 1f - MARGIN),
                y = y.coerceIn(MARGIN, 1f - MARGIN),
                vx = cos(angle) * settings.speed,
                vy = sin(angle) * settings.speed
            )
        }
        return EmojiBoard(settings, items, targetIndex, columns, rows)
    }

    /** Largeur sur hauteur supposée du plateau pour dessiner la grille de départ (un téléphone tenu droit). */
    private const val GRID_ASPECT = 0.75f

    /** Part de la case dont un dessin peut s'écarter de son centre. */
    private const val JITTER = 0.3f

    /**
     * Où est un point parti de [start] (entre 0 et 1) après avoir parcouru [distance] (positive ou négative), s'il rebondit
     * sur les deux bords du plateau : un va-et-vient régulier, qui ne dépend que de la distance parcourue.
     */
    fun bounce(start: Float, distance: Float): Float {
        val length = 1f - 2f * MARGIN
        val period = 2f * length
        var u = (start - MARGIN + distance) % period
        if (u < 0f) u += period
        if (u > length) u = period - u
        return MARGIN + u.coerceIn(0f, length)
    }

    /** Le temps d'un joueur qui a trouvé le dessin : sa durée de recherche plus les pénalités, plafonné. */
    fun resultMs(elapsedMs: Long, mistakes: Int): Int =
        (elapsedMs.coerceAtLeast(1L) + mistakes.coerceAtLeast(0) * PENALTY_MS).coerceIn(1L, MAX_RESULT_MS.toLong()).toInt()

    /** Temps restant quand on a cherché [elapsedMs] millisecondes en se trompant [mistakes] fois ; 0 ou moins : le temps est écoulé. */
    fun remainingMs(level: EmojiLevel, elapsedMs: Long, mistakes: Int): Long =
        level.timeLimitMs - elapsedMs - mistakes.coerceAtLeast(0) * PENALTY_MS

    /** Distance (en part de l'écran) dont deux dessins du plateau de départ sont écartés au minimum : sert aux tests. */
    internal fun minStartGap(board: EmojiBoard): Float {
        var gap = Float.MAX_VALUE
        for (a in board.items.indices) for (b in a + 1 until board.items.size) {
            val dx = abs(board.items[a].x - board.items[b].x)
            val dy = abs(board.items[a].y - board.items[b].y)
            gap = minOf(gap, maxOf(dx * board.columns, dy * board.rows))
        }
        return gap
    }
}

/**
 * La séance de la Chasse à l'émoji chacun sur son téléphone, de [EmojiHunt.MIN_PLAYERS] à [EmojiHunt.MAX_PLAYERS] joueurs.
 *
 * Les joueurs sont repérés par leur place à la table : 0 pour l'organisateur, puis les invités dans l'ordre d'arrivée.
 * Chaque téléphone mesure le temps de son joueur depuis l'affichage du plateau ; l'organisateur compare les résultats
 * d'une manche avec [resolve]. Un résultat est un temps en millisecondes (positif, pénalités comprises) ou
 * [EmojiHunt.MISS] (pas trouvé à temps). Le premier à [target] manches gagnées remporte la partie.
 */
internal data class EmojiGame(
    val wins: List<Int>,
    val totalMs: List<Int>,
    val timedRounds: List<Int>,
    val lastResults: List<Int> = emptyList(),
    val lastWinner: Int = NONE,
    val lastDraw: Boolean = false,
    val target: Int = EmojiHunt.TARGET
) {
    val players: Int get() = wins.size

    /** La place du vainqueur de la partie, ou [NONE] si elle continue. */
    val winner: Int get() = wins.indexOfFirst { it >= target }.let { if (it < 0) NONE else it }

    val isOver: Boolean get() = winner != NONE

    /** Vrai quand une manche a été jouée et résolue (gagnée ou nulle). */
    val roundPlayed: Boolean get() = lastWinner != NONE || lastDraw

    /** Le temps du vainqueur de la dernière manche (0 s'il n'y en a pas). */
    val lastTimeMs: Int get() = lastResults.getOrNull(lastWinner)?.coerceAtLeast(0) ?: 0

    fun winsOf(seat: Int): Int = wins.getOrElse(seat) { 0 }

    fun timedRoundsOf(seat: Int): Int = timedRounds.getOrElse(seat) { 0 }

    /** Temps moyen de la place [seat] sur les manches où elle a trouvé le dessin, ou `null` si elle n'en a aucune. */
    fun averageMsOf(seat: Int): Int? {
        val count = timedRoundsOf(seat)
        if (count == 0) return null
        return (totalMs.getOrElse(seat) { 0 } + count / 2) / count
    }

    /** Vrai quand au moins un joueur a deux temps ou plus : c'est là qu'une moyenne apporte quelque chose. */
    val hasAverages: Boolean get() = timedRounds.any { it >= 2 }

    /**
     * Compare les résultats de la manche, un par place : le plus rapide gagne. Égalité exacte au meilleur temps, ou
     * personne n'a trouvé : la manche est nulle, personne ne marque. `null` si la partie est finie ou si le nombre de
     * résultats ne correspond pas aux joueurs.
     */
    fun resolve(results: List<Int>): EmojiGame? {
        if (isOver || players < MIN_PLAYERS || results.size != players) return null
        val r = results.map { if (it <= 0) EmojiHunt.MISS else it.coerceAtMost(EmojiHunt.MAX_RESULT_MS) }
        val found = r.indices.filter { r[it] > 0 }
        val winnerSeat = if (found.isEmpty()) NONE else {
            val best = found.minOf { r[it] }
            found.filter { r[it] == best }.singleOrNull() ?: NONE
        }
        if (winnerSeat == NONE) return copy(lastResults = r, lastWinner = NONE, lastDraw = true)
        return copy(
            wins = wins.mapIndexed { seat, n -> if (seat == winnerSeat) n + 1 else n },
            totalMs = totalMs.mapIndexed { seat, ms -> ms + r[seat].coerceAtLeast(0) },
            timedRounds = timedRounds.mapIndexed { seat, n -> if (r[seat] > 0) n + 1 else n },
            lastResults = r,
            lastWinner = winnerSeat,
            lastDraw = false
        )
    }

    /** Une nouvelle partie avec les mêmes joueurs. */
    fun newMatch(): EmojiGame = of(players, target)

    /** Un joueur quitte la table : sa place disparaît, les autres gardent leur score. */
    fun removePlayer(seat: Int): EmojiGame {
        if (seat !in 0 until players) return this
        fun List<Int>.without() = filterIndexed { index, _ -> index != seat }
        return copy(
            wins = wins.without(),
            totalMs = totalMs.without(),
            timedRounds = timedRounds.without(),
            lastResults = emptyList(),
            lastWinner = NONE,
            lastDraw = false
        )
    }

    companion object {
        const val NONE = -1
        private const val MIN_PLAYERS = EmojiHunt.MIN_PLAYERS

        fun of(players: Int, target: Int = EmojiHunt.TARGET) = EmojiGame(
            wins = List(players) { 0 },
            totalMs = List(players) { 0 },
            timedRounds = List(players) { 0 },
            target = target
        )
    }
}
