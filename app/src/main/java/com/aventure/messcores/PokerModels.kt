package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

// Types de la partie de poker : tour, phase, joueurs, pots et état complet [PokerGame] (immuable,
// avec sa sauvegarde en JSON). Les règles qui les font évoluer sont dans PokerEngine.kt.

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
    val clockMs: Long,
    /** Nombre de recaves de chaque joueur (même index que [seats]) ; vide pour une ancienne partie. */
    val rebuys: List<Int> = emptyList()
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
        put("rebuys", JSONArray(rebuys))
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
                clockMs = obj.optLong("clockMs", 0L),
                rebuys = ints("rebuys").let { if (it.size == seats.size) it else List(seats.size) { 0 } }
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
