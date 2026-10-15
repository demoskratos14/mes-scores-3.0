package com.aventure.messcores

import kotlin.random.Random

/**
 * Moteur du poker (tournoi sans relance de tapis) : blinds qui montent avec le temps, antes, tours
 * d'enchères (suivre, passer, relancer avec relance minimum, tapis), pots annexes et classement.
 *
 * Tout est immuable et sans Android : chaque action renvoie un nouvel état [PokerGame], ce qui rend
 * l'annulation triviale (on garde les anciens états) et le moteur testable sans émulateur.
 * L'appli ne distribue pas de cartes : elle suit seulement les jetons et l'ordre de parole.
 */
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
            clockMs = 0L,
            rebuys = List(playerCount) { 0 }
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

    // ---------- Recaves ----------

    /**
     * Vrai si [player], éliminé, peut racheter des jetons : entre deux mains, et tant que le niveau
     * de blinds [level] ne dépasse pas la limite des recaves (réglage > 0). Possible même quand il ne
     * reste qu'un joueur : le tournoi reprend alors avec lui.
     */
    fun canRebuy(game: PokerGame, player: Int, level: Int): Boolean =
        game.phase == PokerPhase.HAND_OVER &&
            player in game.seats.indices &&
            game.seats[player].out &&
            game.settings.rebuyUntilLevel > 0 &&
            level <= game.settings.rebuyUntilLevel

    /**
     * Recave : [player] revient à sa place avec le tapis de départ. Il n'est plus compté parmi les
     * éliminés (son rang final se joue à sa prochaine élimination) et sa recave s'ajoute à la cagnotte.
     * Sans effet si la recave n'est pas permise.
     */
    fun rebuy(game: PokerGame, player: Int, level: Int): PokerGame {
        if (!canRebuy(game, player, level)) return game
        val seats = game.seats.toMutableList()
        seats[player] = PokerSeat(stack = game.settings.startingStack)
        val rebuys = List(game.seats.size) { game.rebuys.getOrElse(it) { 0 } }.toMutableList()
        rebuys[player] += 1
        val position = game.eliminated.lastIndexOf(player)
        val eliminated = game.eliminated.filterIndexed { index, _ -> index != position }
        return game.copy(seats = seats, rebuys = rebuys, eliminated = eliminated)
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
        val rebuys = order.map { ref -> if (ref >= 0) game.rebuys.getOrElse(ref) { 0 } else 0 }
        val reseated = game.copy(
            rebuys = rebuys,
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
