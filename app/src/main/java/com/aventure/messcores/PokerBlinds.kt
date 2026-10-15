package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

// Réglages d'un tournoi de poker et structure des blinds (niveaux, pauses, durée). Sans Android :
// le moteur de la partie est dans PokerEngine.kt, ses types dans PokerModels.kt.

/** Réglages choisis à la création de la partie. */
data class PokerSettings(
    val startingStack: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    /** Durée d'un niveau de blinds en minutes ; 0 = blinds fixes pendant toute la partie. */
    val levelMinutes: Int,
    val anteEnabled: Boolean,
    /** Nombre d'augmentations de blinds avant l'arrivée des antes (0 = dès la première main). */
    val anteAfterIncreases: Int,
    /** Index du joueur qui a le bouton à la première main. */
    val firstDealer: Int,
    /** Vitesse de montée des blinds. */
    val pace: PokerPace = PokerPace.REGULAR,
    /** Pause programmée après ce nombre de niveaux (0 = pas de pause). */
    val breakEveryLevels: Int = 0,
    /** Durée de la pause en minutes (0 = pas de pause). */
    val breakMinutes: Int = 0,
    /** Son et vibration à chaque changement de niveau ou de pause. */
    val levelAlert: Boolean = true,
    /** Mise d'entrée par joueur (0 = pas de gains à répartir). */
    val buyIn: Int = 0,
    /** Part de la cagnotte (en %) de la 1re place, de la 2e… ; vide = pas de gains. */
    val payoutPercents: List<Int> = emptyList(),
    /** Recaves possibles tant que le niveau de blinds ne dépasse pas celui-ci (0 = pas de recave). */
    val rebuyUntilLevel: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("startingStack", startingStack)
        put("smallBlind", smallBlind)
        put("bigBlind", bigBlind)
        put("levelMinutes", levelMinutes)
        put("anteEnabled", anteEnabled)
        put("anteAfterIncreases", anteAfterIncreases)
        put("firstDealer", firstDealer)
        put("pace", pace.name)
        put("breakEveryLevels", breakEveryLevels)
        put("breakMinutes", breakMinutes)
        put("levelAlert", levelAlert)
        put("buyIn", buyIn)
        put("payoutPercents", JSONArray(payoutPercents))
        put("rebuyUntilLevel", rebuyUntilLevel)
    }

    companion object {
        fun fromJson(obj: JSONObject) = PokerSettings(
            startingStack = obj.getInt("startingStack"),
            smallBlind = obj.getInt("smallBlind"),
            bigBlind = obj.getInt("bigBlind"),
            levelMinutes = obj.optInt("levelMinutes", 0),
            anteEnabled = obj.optBoolean("anteEnabled", false),
            anteAfterIncreases = obj.optInt("anteAfterIncreases", 0),
            firstDealer = obj.optInt("firstDealer", 0),
            pace = try {
                PokerPace.valueOf(obj.optString("pace", PokerPace.REGULAR.name))
            } catch (e: IllegalArgumentException) {
                PokerPace.REGULAR
            },
            breakEveryLevels = obj.optInt("breakEveryLevels", 0),
            breakMinutes = obj.optInt("breakMinutes", 0),
            levelAlert = obj.optBoolean("levelAlert", true),
            buyIn = obj.optInt("buyIn", 0),
            payoutPercents = obj.optJSONArray("payoutPercents")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                ?: emptyList(),
            rebuyUntilLevel = obj.optInt("rebuyUntilLevel", 0)
        )
    }
}

/** Vitesse de montée des blinds : [percent] = cible de la grosse blind du niveau suivant, en % de la courante (100 = palier suivant de l'échelle). */
enum class PokerPace(val percent: Int) { REGULAR(100), FAST(150), TURBO(200) }

/**
 * Structure des blinds : niveau 1 = réglages de départ, puis une marche de l'échelle « classique »
 * à chaque niveau, avec une pause programmée de temps en temps si elle est réglée.
 */
object PokerBlinds {
    private val MANTISSAS = longArrayOf(10, 12, 15, 20, 25, 30, 40, 50, 60, 80)

    /** Valeurs « rondes » de grosse blind (1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50…). */
    private val LADDER: List<Long> by lazy {
        val values = sortedSetOf<Long>()
        var power = 1L
        repeat(10) {
            MANTISSAS.forEach { m -> if ((m * power) % 10 == 0L) values.add(m * power / 10) }
            power *= 10
        }
        values.toList()
    }

    /**
     * Grosse blind du niveau suivant : la valeur ronde la plus proche de la cible (palier suivant, +50 % ou
     * +100 % selon [pace]), et toujours strictement plus grande que la blind actuelle.
     */
    fun nextBigBlind(bigBlind: Int, pace: PokerPace = PokerPace.REGULAR): Int {
        val target = (bigBlind.toLong() * pace.percent + 50) / 100
        val lower = LADDER.lastOrNull { it <= target }
        val upper = LADDER.firstOrNull { it >= target }
        val nearest = when {
            lower == null -> upper
            upper == null -> lower
            target - lower < upper - target -> lower
            else -> upper
        } ?: target
        val chosen = if (nearest > bigBlind) nearest else (LADDER.firstOrNull { it > bigBlind } ?: (bigBlind + 1L))
        return chosen.coerceAtMost(Int.MAX_VALUE.toLong() / 4).toInt()
    }

    /** Grosse blind du niveau [level] (1 = départ). */
    fun bigBlindAt(settings: PokerSettings, level: Int): Int {
        var bb = settings.bigBlind
        repeat((level - 1).coerceAtLeast(0)) { bb = nextBigBlind(bb, settings.pace) }
        return bb
    }

    /** Petite blind du niveau [level] : même proportion que celle du départ. */
    fun smallBlindAt(settings: PokerSettings, level: Int): Int {
        if (level <= 1) return settings.smallBlind
        val bb = bigBlindAt(settings, level)
        return ((bb.toLong() * settings.smallBlind + settings.bigBlind / 2) / settings.bigBlind).toInt().coerceIn(1, bb)
    }

    /** Ante par joueur au niveau [level] : environ un dixième de la grosse blind, une fois l'heure des antes venue. */
    fun anteAt(settings: PokerSettings, level: Int): Int {
        if (!settings.anteEnabled || level - 1 < settings.anteAfterIncreases) return 0
        return maxOf(1, bigBlindAt(settings, level) / 10)
    }

    // ---------- Temps : niveaux et pauses ----------

    private fun levelMs(s: PokerSettings) = s.levelMinutes * 60_000L

    private fun breaksOn(s: PokerSettings) = s.levelMinutes > 0 && s.breakEveryLevels > 0 && s.breakMinutes > 0

    private fun playMs(s: PokerSettings) = s.breakEveryLevels * levelMs(s)

    private fun cycleMs(s: PokerSettings) = playMs(s) + s.breakMinutes * 60_000L

    /**
     * Niveau atteint après [elapsedMs] millisecondes (1 si les blinds sont fixes). Pendant une pause,
     * c'est le dernier niveau joué : le niveau suivant arrive à la fin de la pause.
     */
    fun levelAt(settings: PokerSettings, elapsedMs: Long): Int {
        if (settings.levelMinutes <= 0) return 1
        val e = elapsedMs.coerceAtLeast(0)
        if (!breaksOn(settings)) return 1 + (e / levelMs(settings)).toInt()
        val cycle = cycleMs(settings)
        val rest = e % cycle
        val inBlock = if (rest < playMs(settings)) (rest / levelMs(settings)).toInt() else settings.breakEveryLevels - 1
        return 1 + ((e / cycle) * settings.breakEveryLevels).toInt() + inBlock
    }

    /** Vrai si une pause programmée est en cours après [elapsedMs] millisecondes. */
    fun isBreak(settings: PokerSettings, elapsedMs: Long): Boolean =
        breaksOn(settings) && elapsedMs.coerceAtLeast(0) % cycleMs(settings) >= playMs(settings)

    /** Vrai si la fin du niveau en cours déclenche une pause (et non un nouveau niveau). */
    fun breakStartsNext(settings: PokerSettings, elapsedMs: Long): Boolean {
        if (!breaksOn(settings) || isBreak(settings, elapsedMs)) return false
        val rest = elapsedMs.coerceAtLeast(0) % cycleMs(settings)
        return (rest / levelMs(settings)).toInt() == settings.breakEveryLevels - 1
    }

    /** Vrai si une pause est programmée après le niveau [level] (pour l'aperçu de la structure). */
    fun breakAfterLevel(settings: PokerSettings, level: Int): Boolean =
        breaksOn(settings) && level >= 1 && level % settings.breakEveryLevels == 0

    /** Temps de jeu écoulé au prochain changement : fin du niveau, ou fin de la pause si elle est en cours. */
    fun elapsedAtNextLevel(settings: PokerSettings, elapsedMs: Long): Long {
        if (settings.levelMinutes <= 0) return elapsedMs
        val e = elapsedMs.coerceAtLeast(0)
        if (!breaksOn(settings)) return (e / levelMs(settings) + 1) * levelMs(settings)
        val cycle = cycleMs(settings)
        val base = (e / cycle) * cycle
        val rest = e - base
        return if (rest >= playMs(settings)) base + cycle else base + (rest / levelMs(settings) + 1) * levelMs(settings)
    }

    /** Millisecondes restantes avant le prochain changement (niveau, début ou fin de pause), ou null si les blinds sont fixes. */
    fun remainingMs(settings: PokerSettings, elapsedMs: Long): Long? {
        if (settings.levelMinutes <= 0) return null
        return elapsedAtNextLevel(settings, elapsedMs) - elapsedMs.coerceAtLeast(0)
    }
}
