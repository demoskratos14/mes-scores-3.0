package com.aventure.messcores

import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moteur du poker (tournoi sans relance de tapis) : blinds qui montent avec le temps, antes, tours
 * d'enchères (suivre, passer, relancer avec relance minimum, tapis), pots annexes et classement.
 *
 * Tout est immuable et sans Android : chaque action renvoie un nouvel état [PokerGame], ce qui rend
 * l'annulation triviale (on garde les anciens états) et le moteur testable sans émulateur.
 * L'appli ne distribue pas de cartes : elle suit seulement les jetons et l'ordre de parole.
 */

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
    val payoutPercents: List<Int> = emptyList()
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
                ?: emptyList()
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

enum class PokerStreet { PREFLOP, FLOP, TURN, RIVER }

enum class PokerPhase {
    /** Un tour d'enchères est en cours : c'est à [PokerGame.toAct] de parler. */
    BETTING,
    /** Les enchères sont finies, il faut dire qui remporte chaque pot. */
    SHOWDOWN,
    /** La main est terminée et payée ; on attend la main suivante (ou le tournoi est fini). */
    HAND_OVER
}

enum class PokerActionType { FOLD, CHECK, CALL, BET, RAISE }

/**
 * Une action jouée. [amount] : jetons ajoutés pour un CALL, mise totale du tour pour un BET ou
 * un RAISE (« relance à »), 0 pour FOLD et CHECK.
 */
data class PokerLogEntry(
    val player: Int,
    val type: PokerActionType,
    val amount: Int,
    val allIn: Boolean
)

/** Un joueur à la table. */
data class PokerSeat(
    val stack: Int,
    /** Couché pendant cette main. */
    val folded: Boolean = false,
    /** Éliminé du tournoi (n'a plus de jetons). */
    val out: Boolean = false,
    /** Jetons misés pendant le tour d'enchères en cours. */
    val roundBet: Int = 0,
    /** Jetons misés pendant toute la main (antes comprises). */
    val totalBet: Int = 0,
    /** A déjà parlé depuis la dernière relance complète. */
    val hasActed: Boolean = false,
    /** A déjà parlé avant une relance incomplète (tapis court) : peut suivre ou passer, pas relancer. */
    val raiseLocked: Boolean = false,
    /** Participe à la main en cours. */
    val inHand: Boolean = false
) {
    val allIn: Boolean get() = inHand && !folded && stack == 0
}

/** Un pot (principal ou annexe) et les joueurs qui peuvent le gagner. */
data class PokerPot(val amount: Int, val eligible: List<Int>)

/** Ce que le joueur dont c'est le tour a le droit de faire. */
data class PokerOptions(
    val player: Int,
    val canCheck: Boolean,
    /** Jetons à ajouter pour suivre (0 si le joueur peut checker). */
    val callAmount: Int,
    val canRaise: Boolean,
    /** Plus petite mise totale légale du tour (ou le tapis si celui-ci est plus petit). */
    val minRaiseTo: Int,
    /** Tapis : mise totale maximale du tour. */
    val maxRaiseTo: Int,
    /** Vrai s'il n'y a pas encore de mise dans ce tour (« miser » plutôt que « relancer »). */
    val isBet: Boolean
)

data class PokerGame(
    val settings: PokerSettings,
    val seats: List<PokerSeat>,
    val dealer: Int,
    val smallBlindSeat: Int,
    val bigBlindSeat: Int,
    /** Numéro de la main en cours (1 = première). */
    val handNumber: Int,
    /** Niveau de blinds appliqué à la main en cours. */
    val handLevel: Int,
    val street: PokerStreet,
    val phase: PokerPhase,
    /** Plus forte mise du tour en cours. */
    val currentBet: Int,
    /** Taille de la dernière relance complète (au moins la grosse blind). */
    val minRaise: Int,
    /** Joueur dont c'est le tour, ou -1. */
    val toAct: Int,
    /** Actions de la main en cours. */
    val log: List<PokerLogEntry>,
    /** Jetons gagnés par chaque joueur à la dernière main payée (même index que [seats]). */
    val winnings: List<Int>,
    /** Joueurs éliminés, du premier au dernier éliminé. */
    val eliminated: List<Int>,
    /** Nombre de mains entièrement jouées. */
    val handsPlayed: Int,
    /** Temps de jeu écoulé, en millisecondes, au moment de l'enregistrement (voir [ScoreViewModel]). */
    val clockMs: Long
) {
    /** Tous les jetons au milieu de la table : mises des tours précédents et du tour en cours. */
    val pot: Int get() = seats.sumOf { it.totalBet }

    /** Gagnant du tournoi quand il ne reste qu'un joueur en lice, sinon null. */
    val champion: Int? get() {
        val alive = seats.indices.filter { !seats[it].out }
        return if (alive.size == 1) alive.first() else null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("settings", settings.toJson())
        put("seats", JSONArray().also { array ->
            seats.forEach { s ->
                array.put(JSONObject().apply {
                    put("stack", s.stack)
                    put("folded", s.folded)
                    put("out", s.out)
                    put("roundBet", s.roundBet)
                    put("totalBet", s.totalBet)
                    put("hasActed", s.hasActed)
                    put("raiseLocked", s.raiseLocked)
                    put("inHand", s.inHand)
                })
            }
        })
        put("dealer", dealer)
        put("smallBlindSeat", smallBlindSeat)
        put("bigBlindSeat", bigBlindSeat)
        put("handNumber", handNumber)
        put("handLevel", handLevel)
        put("street", street.name)
        put("phase", phase.name)
        put("currentBet", currentBet)
        put("minRaise", minRaise)
        put("toAct", toAct)
        put("log", JSONArray().also { array ->
            log.forEach { e ->
                array.put(JSONObject().apply {
                    put("player", e.player)
                    put("type", e.type.name)
                    put("amount", e.amount)
                    put("allIn", e.allIn)
                })
            }
        })
        put("winnings", JSONArray(winnings))
        put("eliminated", JSONArray(eliminated))
        put("handsPlayed", handsPlayed)
        put("clockMs", clockMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): PokerGame {
            val seatsArray = obj.getJSONArray("seats")
            val seats = (0 until seatsArray.length()).map { i ->
                val s = seatsArray.getJSONObject(i)
                PokerSeat(
                    stack = s.getInt("stack"),
                    folded = s.optBoolean("folded", false),
                    out = s.optBoolean("out", false),
                    roundBet = s.optInt("roundBet", 0),
                    totalBet = s.optInt("totalBet", 0),
                    hasActed = s.optBoolean("hasActed", false),
                    raiseLocked = s.optBoolean("raiseLocked", false),
                    inHand = s.optBoolean("inHand", false)
                )
            }
            val logArray = obj.optJSONArray("log")
            val log = if (logArray == null) emptyList() else (0 until logArray.length()).mapNotNull { i ->
                val e = logArray.getJSONObject(i)
                val type = try {
                    PokerActionType.valueOf(e.getString("type"))
                } catch (ex: IllegalArgumentException) {
                    return@mapNotNull null
                }
                PokerLogEntry(e.getInt("player"), type, e.optInt("amount", 0), e.optBoolean("allIn", false))
            }
            fun ints(name: String): List<Int> = obj.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                ?: emptyList()
            val winnings = ints("winnings").let { if (it.size == seats.size) it else List(seats.size) { 0 } }
            return PokerGame(
                settings = PokerSettings.fromJson(obj.getJSONObject("settings")),
                seats = seats,
                dealer = obj.getInt("dealer"),
                smallBlindSeat = obj.optInt("smallBlindSeat", -1),
                bigBlindSeat = obj.optInt("bigBlindSeat", -1),
                handNumber = obj.optInt("handNumber", 1),
                handLevel = obj.optInt("handLevel", 1),
                street = try {
                    PokerStreet.valueOf(obj.optString("street", PokerStreet.PREFLOP.name))
                } catch (e: IllegalArgumentException) {
                    PokerStreet.PREFLOP
                },
                phase = try {
                    PokerPhase.valueOf(obj.optString("phase", PokerPhase.BETTING.name))
                } catch (e: IllegalArgumentException) {
                    PokerPhase.BETTING
                },
                currentBet = obj.optInt("currentBet", 0),
                minRaise = obj.optInt("minRaise", obj.getJSONObject("settings").optInt("bigBlind", 1)),
                toAct = obj.optInt("toAct", -1),
                log = log,
                winnings = winnings,
                eliminated = ints("eliminated"),
                handsPlayed = obj.optInt("handsPlayed", 0),
                clockMs = obj.optLong("clockMs", 0L)
            )
        }
    }
}

/** Chiffres de la table : joueurs en lice, tapis moyen, plus gros tapis (en jetons et en grosses blinds). */
data class PokerTableInfo(
    val alive: Int,
    val total: Int,
    val averageStack: Int,
    val averageBigBlinds: Int,
    val leader: Int,
    val leaderChips: Int,
    val leaderBigBlinds: Int
)

/**
 * Résultat de [PokerEngine.reseat] : la partie avec ses nouvelles places, la nouvelle place de chaque
 * ancien joueur ([oldToNew], -1 s'il a quitté la table) et celle de chaque arrivant ([arrivals], dans
 * l'ordre où ils ont été donnés).
 */
data class PokerReseat(val game: PokerGame, val oldToNew: List<Int>, val arrivals: List<Int>)

object PokerEngine {

    // ---------- Création et début de main ----------

    /** Nouvelle partie : tous les joueurs ont le tapis de départ, la première main est lancée au niveau 1. */
    fun newGame(settings: PokerSettings, playerCount: Int): PokerGame {
        val dealer = settings.firstDealer.coerceIn(0, playerCount - 1)
        val game = PokerGame(
            settings = settings,
            seats = List(playerCount) { PokerSeat(stack = settings.startingStack) },
            dealer = dealer,
            smallBlindSeat = -1,
            bigBlindSeat = -1,
            handNumber = 0,
            handLevel = 1,
            street = PokerStreet.PREFLOP,
            phase = PokerPhase.HAND_OVER,
            currentBet = 0,
            minRaise = settings.bigBlind,
            toAct = -1,
            log = emptyList(),
            winnings = List(playerCount) { 0 },
            eliminated = emptyList(),
            handsPlayed = 0,
            clockMs = 0L
        )
        return startHand(game, level = 1, keepDealer = true)
    }

    /**
     * Lance une main au niveau de blinds [level] : le bouton avance d'un joueur encore en lice
     * (sauf pour la toute première main, [keepDealer]), puis antes et blinds sont postées.
     * Ne fait rien si le tournoi est terminé.
     */
    fun startHand(game: PokerGame, level: Int, keepDealer: Boolean = false): PokerGame {
        val n = game.seats.size
        val alive = game.seats.indices.filter { !game.seats[it].out }
        if (alive.size < 2) return game
        val dealer = if (keepDealer && !game.seats[game.dealer].out) {
            game.dealer
        } else {
            nextWhere(n, game.dealer) { !game.seats[it].out }
        }
        val headsUp = alive.size == 2
        val sbSeat = if (headsUp) dealer else nextWhere(n, dealer) { !game.seats[it].out }
        val bbSeat = nextWhere(n, sbSeat) { !game.seats[it].out }

        val settings = game.settings
        val bb = PokerBlinds.bigBlindAt(settings, level)
        val sb = PokerBlinds.smallBlindAt(settings, level)
        val ante = PokerBlinds.anteAt(settings, level)

        val seats = game.seats.map { s ->
            if (s.out) {
                s.copy(folded = false, roundBet = 0, totalBet = 0, hasActed = false, raiseLocked = false, inHand = false)
            } else {
                s.copy(folded = false, roundBet = 0, totalBet = 0, hasActed = false, raiseLocked = false, inHand = true)
            }
        }.toMutableList()

        // Antes : tout le monde paie, sans que cela compte comme une mise du tour.
        if (ante > 0) {
            alive.forEach { i ->
                val pay = minOf(ante, seats[i].stack)
                seats[i] = seats[i].copy(stack = seats[i].stack - pay, totalBet = pay)
            }
        }
        fun postBlind(i: Int, amount: Int) {
            val pay = minOf(amount, seats[i].stack)
            seats[i] = seats[i].copy(
                stack = seats[i].stack - pay,
                roundBet = seats[i].roundBet + pay,
                totalBet = seats[i].totalBet + pay
            )
        }
        postBlind(sbSeat, sb)
        postBlind(bbSeat, bb)

        val started = game.copy(
            seats = seats,
            dealer = dealer,
            smallBlindSeat = sbSeat,
            bigBlindSeat = bbSeat,
            handNumber = game.handNumber + 1,
            handLevel = level,
            street = PokerStreet.PREFLOP,
            phase = PokerPhase.BETTING,
            currentBet = bb,
            minRaise = bb,
            toAct = -1,
            log = emptyList(),
            winnings = List(n) { 0 }
        )
        // Premier à parler : le joueur après la grosse blind (en tête à tête : le bouton, petite blind).
        return settle(started, lastActor = bbSeat)
    }

    // ---------- Ce que le joueur peut faire ----------

    fun options(game: PokerGame): PokerOptions? {
        if (game.phase != PokerPhase.BETTING || game.toAct !in game.seats.indices) return null
        val p = game.toAct
        val seat = game.seats[p]
        val call = minOf(seat.stack, maxOf(0, game.currentBet - seat.roundBet))
        val maxTo = seat.roundBet + seat.stack
        val minTo = minOf(game.currentBet + game.minRaise, maxTo)
        return PokerOptions(
            player = p,
            canCheck = seat.roundBet >= game.currentBet,
            callAmount = call,
            canRaise = !seat.raiseLocked && seat.stack > call,
            minRaiseTo = minTo,
            maxRaiseTo = maxTo,
            isBet = game.currentBet == 0
        )
    }

    // ---------- Actions ----------

    fun fold(game: PokerGame): PokerGame {
        val p = actor(game) ?: return game
        val seats = game.seats.toMutableList()
        seats[p] = seats[p].copy(folded = true, hasActed = true)
        val next = game.copy(seats = seats, log = game.log + PokerLogEntry(p, PokerActionType.FOLD, 0, false))
        return settle(next, p)
    }

    fun check(game: PokerGame): PokerGame {
        val p = actor(game) ?: return game
        if (game.seats[p].roundBet < game.currentBet) return game
        val seats = game.seats.toMutableList()
        seats[p] = seats[p].copy(hasActed = true)
        val next = game.copy(seats = seats, log = game.log + PokerLogEntry(p, PokerActionType.CHECK, 0, false))
        return settle(next, p)
    }

    /** Suivre (ou checker s'il n'y a rien à suivre). Si le tapis du joueur est trop court, il suit à tapis. */
    fun call(game: PokerGame): PokerGame {
        val p = actor(game) ?: return game
        val seat = game.seats[p]
        val pay = minOf(seat.stack, maxOf(0, game.currentBet - seat.roundBet))
        if (pay == 0) return check(game)
        val seats = game.seats.toMutableList()
        seats[p] = seat.copy(
            stack = seat.stack - pay,
            roundBet = seat.roundBet + pay,
            totalBet = seat.totalBet + pay,
            hasActed = true
        )
        val next = game.copy(
            seats = seats,
            log = game.log + PokerLogEntry(p, PokerActionType.CALL, pay, seats[p].stack == 0)
        )
        return settle(next, p)
    }

    /** Tapis : mise de tous les jetons (un simple « suivre » si cela ne dépasse pas la mise en cours). */
    fun allIn(game: PokerGame): PokerGame {
        val p = actor(game) ?: return game
        val seat = game.seats[p]
        return raiseTo(game, seat.roundBet + seat.stack)
    }

    /**
     * Miser ou relancer pour une mise totale de [to] jetons dans ce tour. Une mise inférieure à la
     * relance minimum est remontée au minimum, sauf si c'est un tapis ; au-delà du tapis, c'est un tapis.
     * Sans effet si le joueur n'a pas le droit de relancer (relance incomplète d'un tapis court avant lui).
     */
    fun raiseTo(game: PokerGame, to: Int): PokerGame {
        val p = actor(game) ?: return game
        val seat = game.seats[p]
        val maxTo = seat.roundBet + seat.stack
        if (to <= game.currentBet || maxTo <= game.currentBet) return call(game)
        if (seat.raiseLocked) return game
        val minTo = minOf(game.currentBet + game.minRaise, maxTo)
        val target = to.coerceIn(minTo, maxTo)
        val add = target - seat.roundBet
        val raiseSize = target - game.currentBet
        val fullRaise = raiseSize >= game.minRaise
        val wasBet = game.currentBet == 0

        val seats = game.seats.toMutableList()
        seats[p] = seat.copy(
            stack = seat.stack - add,
            roundBet = target,
            totalBet = seat.totalBet + add,
            hasActed = true
        )
        for (i in seats.indices) {
            if (i == p) continue
            val s = seats[i]
            if (!s.inHand || s.folded) continue
            seats[i] = if (fullRaise) {
                // Relance complète : tout le monde doit reparler, et peut de nouveau relancer.
                s.copy(hasActed = false, raiseLocked = false)
            } else {
                // Relance incomplète (tapis court) : ceux qui avaient déjà parlé doivent suivre ou passer.
                if (s.hasActed) s.copy(raiseLocked = true) else s
            }
        }
        val entry = PokerLogEntry(
            player = p,
            type = if (wasBet) PokerActionType.BET else PokerActionType.RAISE,
            amount = target,
            allIn = seats[p].stack == 0
        )
        val next = game.copy(
            seats = seats,
            currentBet = target,
            minRaise = if (fullRaise) raiseSize else game.minRaise,
            log = game.log + entry
        )
        return settle(next, p)
    }

    private fun actor(game: PokerGame): Int? =
        if (game.phase == PokerPhase.BETTING && game.toAct in game.seats.indices) game.toAct else null

    // ---------- Progression de la main ----------

    private fun isPending(game: PokerGame, i: Int): Boolean {
        val s = game.seats[i]
        return s.inHand && !s.folded && s.stack > 0 && (!s.hasActed || s.roundBet < game.currentBet)
    }

    private fun nextWhere(n: Int, from: Int, predicate: (Int) -> Boolean): Int {
        for (step in 1..n) {
            val i = (from + step) % n
            if (predicate(i)) return i
        }
        return from
    }

    /**
     * Après une action de [lastActor] : désigne le prochain joueur à parler, ou clôt le tour
     * (flop, turn, river suivants), ou passe à l'abattage, ou donne le pot au dernier joueur non couché.
     */
    private fun settle(game: PokerGame, lastActor: Int): PokerGame {
        val n = game.seats.size
        val live = game.seats.indices.filter { game.seats[it].inHand && !game.seats[it].folded }
        if (live.size <= 1) return awardUncontested(game, live.firstOrNull())
        val able = live.filter { game.seats[it].stack > 0 }
        val pending = game.seats.indices.filter { isPending(game, it) }
        // Plus personne ne peut relancer (un seul joueur avec des jetons, qui n'a rien à suivre).
        val nobodyCanBet = able.size <= 1 && able.all { game.seats[it].roundBet >= game.currentBet }
        if (pending.isNotEmpty() && !nobodyCanBet) {
            return game.copy(toAct = nextWhere(n, lastActor) { isPending(game, it) })
        }
        // Le tour d'enchères est terminé : abattage, ou tour suivant (flop, turn, river).
        if (game.street == PokerStreet.RIVER || able.size <= 1) {
            return game.copy(phase = PokerPhase.SHOWDOWN, toAct = -1)
        }
        val nextStreet = PokerStreet.entries[game.street.ordinal + 1]
        val nextGame = game.copy(
            seats = game.seats.map { it.copy(roundBet = 0, hasActed = false, raiseLocked = false) },
            street = nextStreet,
            currentBet = 0,
            minRaise = PokerBlinds.bigBlindAt(game.settings, game.handLevel),
            toAct = -1
        )
        return settleFirstToAct(nextGame)
    }

    private fun settleFirstToAct(game: PokerGame): PokerGame {
        val n = game.seats.size
        val pending = game.seats.indices.filter { isPending(game, it) }
        if (pending.isEmpty()) return game.copy(phase = PokerPhase.SHOWDOWN, toAct = -1)
        return game.copy(toAct = nextWhere(n, game.dealer) { isPending(game, it) })
    }

    // ---------- Fin de main ----------

    /** Pots de la main : le principal puis les annexes, chacun avec les joueurs qui peuvent le gagner. */
    fun pots(game: PokerGame): List<PokerPot> {
        val inHand = game.seats.indices.filter { game.seats[it].inHand }
        val live = inHand.filter { !game.seats[it].folded }
        val levels = live.map { game.seats[it].totalBet }.filter { it > 0 }.distinct().sorted()
        val result = mutableListOf<PokerPot>()
        var previous = 0
        for (level in levels) {
            val amount = inHand.sumOf { minOf(game.seats[it].totalBet, level) - minOf(game.seats[it].totalBet, previous) }
            val eligible = live.filter { game.seats[it].totalBet >= level }
            if (amount > 0) result.add(PokerPot(amount, eligible))
            previous = level
        }
        // Mises de joueurs couchés au-delà du plus haut niveau (ne devrait pas arriver) : au dernier pot.
        val leftover = inHand.sumOf { maxOf(0, game.seats[it].totalBet - previous) }
        if (leftover > 0 && result.isNotEmpty()) {
            val last = result.removeAt(result.lastIndex)
            result.add(last.copy(amount = last.amount + leftover))
        }
        return result
    }

    /** Vrai si le pot [pot] n'a qu'un seul gagnant possible (mise non suivie rendue à son auteur, par exemple). */
    fun isAutomatic(pot: PokerPot): Boolean = pot.eligible.size <= 1

    private fun awardUncontested(game: PokerGame, winner: Int?): PokerGame {
        val total = game.pot
        val winnings = MutableList(game.seats.size) { 0 }
        if (winner != null) winnings[winner] = total
        return finishHand(game, winnings)
    }

    /**
     * Distribue les pots de l'abattage. [winners] donne, pour chaque pot de [pots] (même ordre), les
     * joueurs qui le remportent ; plusieurs joueurs se partagent le pot, le jeton en trop allant au
     * premier à gauche du bouton. Les pots à un seul gagnant possible sont attribués d'office.
     */
    fun resolveShowdown(game: PokerGame, winners: List<List<Int>>): PokerGame {
        if (game.phase != PokerPhase.SHOWDOWN) return game
        val n = game.seats.size
        val pots = pots(game)
        val winnings = MutableList(n) { 0 }
        pots.forEachIndexed { index, pot ->
            val picked = if (isAutomatic(pot)) pot.eligible else winners.getOrNull(index).orEmpty().filter { it in pot.eligible }
            val chosen = if (picked.isEmpty()) pot.eligible.take(1) else picked
            if (chosen.isEmpty()) return@forEachIndexed
            val share = pot.amount / chosen.size
            var remainder = pot.amount % chosen.size
            // Ordre à partir de la gauche du bouton pour les jetons indivisibles.
            val ordered = chosen.sortedBy { (it - game.dealer - 1 + n) % n }
            ordered.forEach { w ->
                val extra = if (remainder > 0) 1 else 0
                if (remainder > 0) remainder--
                winnings[w] += share + extra
            }
        }
        return finishHand(game, winnings)
    }

    private fun finishHand(game: PokerGame, winnings: List<Int>): PokerGame {
        val seats = game.seats.mapIndexed { i, s ->
            s.copy(stack = s.stack + winnings[i], roundBet = 0)
        }.toMutableList()
        // Éliminations : sans jetons après la main. Les éliminés simultanés sont classés selon leur mise
        // (celui qui avait le plus gros tapis de départ finit mieux).
        val busted = seats.indices
            .filter { seats[it].inHand && seats[it].stack == 0 && !seats[it].out }
            .sortedBy { game.seats[it].totalBet }
        busted.forEach { seats[it] = seats[it].copy(out = true) }
        return game.copy(
            seats = seats,
            phase = PokerPhase.HAND_OVER,
            toAct = -1,
            currentBet = 0,
            winnings = winnings,
            eliminated = game.eliminated + busted,
            handsPlayed = game.handsPlayed + 1
        )
    }

    /** Lance la main suivante au niveau [level] (le niveau ne baisse jamais). */
    fun nextHand(game: PokerGame, level: Int): PokerGame {
        if (game.phase != PokerPhase.HAND_OVER || game.champion != null) return game
        return startHand(game, maxOf(level, game.handLevel))
    }

    // ---------- Changement de table ----------

    /**
     * Fait partir et/ou arriver des joueurs entre deux mains (réunion de tables d'un tournoi).
     *
     * - [remove] : places des joueurs qui quittent la table.
     * - [arrivalStacks] : tapis des joueurs qui arrivent. Chacun s'assoit dans un écart tiré au sort
     *   entre deux joueurs déjà assis : l'ordre des joueurs présents ne change pas, et le bouton
     *   reste chez le même joueur (s'il part, il passe au joueur assis juste avant lui).
     *
     * Renvoie null si ce n'est pas le bon moment (hors fin de main, tournoi terminé), si plus personne
     * ne reste assis ou s'il y aurait moins de deux joueurs en lice. Les places changent : le résultat
     * donne la nouvelle place de chacun pour que l'appelant mette ses propres listes à jour.
     */
    fun reseat(
        game: PokerGame,
        remove: Set<Int>,
        arrivalStacks: List<Int>,
        random: Random = Random.Default
    ): PokerReseat? {
        if (game.phase != PokerPhase.HAND_OVER || game.champion != null) return null
        val n = game.seats.size
        val kept = game.seats.indices.filter { it !in remove }
        if (kept.isEmpty() || arrivalStacks.any { it <= 0 }) return null
        if (kept.count { !game.seats[it].out } + arrivalStacks.size < 2) return null

        // Ordre de la table : un entier >= 0 est une ancienne place, -(k + 1) le k-ième arrivant.
        val order = kept.toMutableList()
        arrivalStacks.indices.forEach { k ->
            // Écart tiré parmi ceux du cercle (après chaque joueur déjà assis, le dernier écart étant avant le premier).
            order.add(random.nextInt(order.size) + 1, -(k + 1))
        }

        val oldToNew = MutableList(n) { -1 }
        val arrivals = MutableList(arrivalStacks.size) { -1 }
        order.forEachIndexed { newIndex, ref ->
            if (ref >= 0) oldToNew[ref] = newIndex else arrivals[-ref - 1] = newIndex
        }

        // Bouton : le même joueur, ou à défaut le plus proche avant lui (la main suivante le fera avancer).
        var dealerOld = game.dealer
        var step = 0
        while (dealerOld in remove && step < n) {
            dealerOld = (dealerOld - 1 + n) % n
            step++
        }

        fun mapped(old: Int): Int = if (old in 0 until n) oldToNew[old] else -1

        val seats = order.map { ref ->
            if (ref >= 0) game.seats[ref] else PokerSeat(stack = arrivalStacks[-ref - 1])
        }
        val winnings = order.map { ref -> if (ref >= 0) game.winnings.getOrElse(ref) { 0 } else 0 }
        val log = game.log.mapNotNull { entry ->
            mapped(entry.player).takeIf { it >= 0 }?.let { entry.copy(player = it) }
        }
        val reseated = game.copy(
            seats = seats,
            dealer = oldToNew[dealerOld],
            smallBlindSeat = mapped(game.smallBlindSeat),
            bigBlindSeat = mapped(game.bigBlindSeat),
            toAct = -1,
            log = log,
            winnings = winnings,
            eliminated = game.eliminated.map { mapped(it) }.filter { it >= 0 }
        )
        return PokerReseat(reseated, oldToNew, arrivals)
    }

    // ---------- Infos de table ----------

    /**
     * Joueurs restants, tapis moyen et plus gros tapis. Pendant une main, les jetons déjà misés
     * comptent pour leur propriétaire (on compare les tapis du début de main). Les grosses blinds
     * sont celles de la main en cours. Null si plus personne n'est en lice.
     */
    fun tableInfo(game: PokerGame): PokerTableInfo? {
        val alive = game.seats.indices.filter { !game.seats[it].out }
        if (alive.isEmpty()) return null
        val midHand = game.phase != PokerPhase.HAND_OVER
        fun chips(i: Int) = game.seats[i].stack + if (midHand) game.seats[i].totalBet else 0
        val bb = maxOf(1, PokerBlinds.bigBlindAt(game.settings, game.handLevel))
        val average = (alive.sumOf { chips(it).toLong() } / alive.size).toInt()
        val leader = alive.maxByOrNull { chips(it) } ?: alive.first()
        return PokerTableInfo(
            alive = alive.size,
            total = game.seats.size,
            averageStack = average,
            averageBigBlinds = average / bb,
            leader = leader,
            leaderChips = chips(leader),
            leaderBigBlinds = chips(leader) / bb
        )
    }

    // ---------- Classement ----------

    /**
     * Rang de chaque joueur (1 = premier) : les joueurs encore en lice d'après leurs jetons (ex æquo
     * possibles), puis les éliminés, le dernier éliminé devant le premier.
     */
    fun ranks(game: PokerGame): List<Int> {
        val n = game.seats.size
        val ranks = MutableList(n) { n }
        val alive = game.seats.indices.filter { !game.seats[it].out }
        alive.forEach { i ->
            ranks[i] = 1 + alive.count { game.seats[it].stack > game.seats[i].stack }
        }
        game.eliminated.forEachIndexed { order, i -> ranks[i] = n - order }
        return ranks
    }
}

/** Gains du tournoi : cagnotte = buy-in × nombre de joueurs, répartie selon le classement final. */
object PokerPrizes {
    /** Répartitions proposées à la création (en % de la cagnotte, de la 1re place vers les suivantes). */
    val PRESETS: List<List<Int>> = listOf(listOf(100), listOf(65, 35), listOf(50, 30, 20))

    /** Nombre minimal de joueurs pour qu'une répartition à [places] places ait un sens. */
    fun minPlayers(places: Int): Int = places + 1

    fun pool(settings: PokerSettings, playerCount: Int): Int =
        (settings.buyIn.toLong() * playerCount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * Gain de chaque joueur (même ordre que les places), 0 si rien n'est à gagner. À appeler une fois
     * le tournoi fini : les ex æquo se partagent les gains des places qu'ils occupent, et les jetons
     * qui ne se divisent pas vont au joueur placé en premier dans la liste.
     */
    fun payouts(game: PokerGame): List<Int> {
        val n = game.seats.size
        val settings = game.settings
        val result = MutableList(n) { 0 }
        val percents = settings.payoutPercents.take(n)
        if (settings.buyIn <= 0 || percents.isEmpty()) return result
        val pool = pool(settings, n).toLong()
        val prizes = percents.map { pool * it / 100 }.toMutableList()
        prizes[0] += pool - prizes.sum() // reste des arrondis : à la première place
        val ranks = PokerEngine.ranks(game)
        ranks.indices.groupBy { ranks[it] }.forEach { (rank, members) ->
            val total = (rank - 1 until rank - 1 + members.size).sumOf { prizes.getOrElse(it) { 0L } }
            val share = total / members.size
            var rest = total % members.size
            members.sorted().forEach { i ->
                result[i] = (share + if (rest > 0) 1 else 0).toInt()
                if (rest > 0) rest--
            }
        }
        return result
    }
}
