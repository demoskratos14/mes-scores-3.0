package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WerewolfRoundTest {

    private fun names(n: Int) = List(n) { "J${it + 1}" }

    /** Une partie à 14 joueurs : 3 loups et toutes les cartes à pouvoir. */
    private fun full(seed: Int = 7) = WerewolfRound(names(14), Random(seed))

    private fun WerewolfRound.seat(role: WerewolfRole) = roles.indexOf(role)

    private fun WerewolfRound.wolves() = roles.indices.filter { roles[it] == WerewolfRole.WEREWOLF }

    private fun WerewolfRound.ackAll() {
        for (seat in roles.indices) assertTrue(perform(seat, WerewolfAction(WerewolfActionKind.ACK)))
        assertTrue(isComplete())
    }

    /** Avance (en passant les étapes sans rien faire) jusqu'à [target]. */
    private fun WerewolfRound.skipTo(target: WerewolfStep) {
        var guard = 0
        while (step != target && guard++ < 60) advance()
        assertEquals(target, step)
    }

    private fun WerewolfRound.startNight() {
        ackAll()
        advance()
        assertEquals(WerewolfStep.NIGHT_START, step)
    }

    private fun WerewolfRound.wolvesChoose(victim: Int) {
        skipTo(WerewolfStep.WOLVES)
        wolves().forEach { assertTrue(perform(it, WerewolfAction(WerewolfActionKind.WOLF, victim))) }
        assertTrue(isComplete())
    }

    @Test
    fun lesCartesSontDistribueesSelonLeNombreDeJoueurs() {
        for (n in WEREWOLF_MIN_PLAYERS..WEREWOLF_MAX_PLAYERS) {
            val roles = dealWerewolfRoles(n, Random(n))
            assertEquals(n, roles.size)
            assertEquals(werewolfCount(n), roles.count { it == WerewolfRole.WEREWOLF })
            assertTrue("au moins un villageois à $n joueurs", roles.count { it == WerewolfRole.VILLAGER } >= 1)
            // Chaque carte à pouvoir n'existe qu'une fois.
            for (role in WerewolfRole.entries.filter { it != WerewolfRole.VILLAGER && it != WerewolfRole.WEREWOLF }) {
                assertTrue(roles.count { it == role } <= 1)
            }
        }
        assertEquals(1, werewolfCount(4))
        assertEquals(2, werewolfCount(8))
        assertEquals(4, werewolfCount(16))
    }

    @Test
    fun laPartieCommenceParLaDistributionDesCartes() {
        val r = full()
        assertEquals(WerewolfStep.REVEAL, r.step)
        assertEquals(14, r.expected().size)
        // Chacun ne confirme qu'une fois.
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.ACK)))
        assertFalse(r.perform(0, WerewolfAction(WerewolfActionKind.ACK)))
        assertEquals(13, r.expected().size)
    }

    @Test
    fun lesLoupsDevorentUneVictimeEtSaCarteSeRetourne() {
        val r = full()
        r.startNight()
        val victim = r.seat(WerewolfRole.SEER)
        r.wolvesChoose(victim)
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(listOf(victim), r.lastDeaths)
        assertFalse(r.alive[victim])
        assertTrue(r.revealed[victim])
        assertEquals(WerewolfRole.SEER.ordinal, r.viewFor(0).shown[victim])
    }

    @Test
    fun lesLoupsDoiventEtreD_accord() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.WOLVES)
        val wolves = r.wolves()
        val targets = r.roles.indices.filter { r.roles[it] != WerewolfRole.WEREWOLF }
        wolves.forEachIndexed { i, w -> assertTrue(r.perform(w, WerewolfAction(WerewolfActionKind.WOLF, targets[i]))) }
        // Désaccord : tout est à refaire.
        assertTrue(r.wolfDisagree)
        assertFalse(r.isComplete())
        // Un loup ne peut pas viser un loup.
        assertFalse(r.perform(wolves[0], WerewolfAction(WerewolfActionKind.WOLF, wolves[1])))
    }

    @Test
    fun leSalvateurProtegeLaVictime() {
        val r = full()
        r.startNight()
        val victim = r.seat(WerewolfRole.HUNTER)
        r.skipTo(WerewolfStep.SAVIOR)
        assertTrue(r.perform(r.seat(WerewolfRole.SAVIOR), WerewolfAction(WerewolfActionKind.PROTECT, victim)))
        r.wolvesChoose(victim)
        r.skipTo(WerewolfStep.DAWN)
        assertTrue(r.lastDeaths.isEmpty())
        assertTrue(r.alive[victim])
    }

    @Test
    fun laSorciereSauveOuEmpoisonne() {
        val r = full()
        r.startNight()
        val victim = r.seat(WerewolfRole.CUPID)
        val poisoned = r.seat(WerewolfRole.ELDER)
        r.wolvesChoose(victim)
        r.skipTo(WerewolfStep.WITCH)
        val witch = r.seat(WerewolfRole.WITCH)
        assertEquals(victim, r.viewFor(witch).witchVictim)
        assertTrue(r.perform(witch, WerewolfAction(WerewolfActionKind.WITCH, 1, poisoned)))
        r.skipTo(WerewolfStep.DAWN)
        assertTrue(r.alive[victim])
        assertFalse(r.alive[poisoned])
        // Les potions sont à usage unique : la nuit suivante, la Sorcière n'a plus rien à faire.
        r.skipTo(WerewolfStep.NIGHT_START)
        r.skipTo(WerewolfStep.WITCH)
        assertTrue(r.isDecoy())
    }

    @Test
    fun l_AncienSurvitALaPremiereAttaque() {
        val r = full()
        r.startNight()
        val elder = r.seat(WerewolfRole.ELDER)
        r.wolvesChoose(elder)
        r.skipTo(WerewolfStep.DAWN)
        assertTrue(r.alive[elder])
        r.skipTo(WerewolfStep.NIGHT_START)
        r.wolvesChoose(elder)
        r.skipTo(WerewolfStep.DAWN)
        assertFalse(r.alive[elder])
    }

    @Test
    fun cupidonLieDeuxJoueursQuiMeurentEnsemble() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.CUPID)
        val a = r.seat(WerewolfRole.SEER)
        val b = r.seat(WerewolfRole.IDIOT)
        assertTrue(r.perform(r.seat(WerewolfRole.CUPID), WerewolfAction(WerewolfActionKind.LOVERS, a, b)))
        assertEquals(b, r.viewFor(a).lover)
        r.wolvesChoose(a)
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(setOf(a, b), r.lastDeaths.toSet())
    }

    @Test
    fun laPetiteFilleNAAucuneActionSurSonTelephone() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.WOLVES)
        val girl = r.seat(WerewolfRole.LITTLE_GIRL)
        // Rien n'est attendu d'elle et rien ne s'affiche : elle ouvre ou non les yeux pour de bon.
        assertFalse(girl in r.expected())
        assertEquals(WerewolfPrompt.NONE, r.viewFor(girl).prompt)
        assertTrue(r.viewFor(girl).candidates.isEmpty())
    }

    @Test
    fun leTelephoneVibrePourLeRoleAppele() {
        val r = full()
        r.startNight()
        val seer = r.seat(WerewolfRole.SEER)
        val before = r.viewFor(seer).alert
        r.skipTo(WerewolfStep.SEER)
        assertTrue(r.viewFor(seer).alert > before)
        // Un autre joueur n'est pas prévenu.
        val villager = r.seat(WerewolfRole.VILLAGER)
        assertEquals(0, r.viewFor(villager).alert)
        // La Petite fille est prévenue pendant le tour des loups, sans rien devoir faire.
        val girl = r.seat(WerewolfRole.LITTLE_GIRL)
        val girlBefore = r.viewFor(girl).alert
        r.skipTo(WerewolfStep.WOLVES)
        assertTrue(r.viewFor(girl).alert > girlBefore)
        assertFalse(girl in r.expected())
    }

    @Test
    fun leVoteEliminePuisRetourneLaCarte() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        assertEquals(WerewolfStep.VOTE, r.step)
        val target = r.wolves().first()
        val voters = r.expected()
        assertEquals(14, voters.size)
        // On ne vote pas contre soi.
        assertFalse(r.perform(target, WerewolfAction(WerewolfActionKind.VOTE, target)))
        voters.forEach { seat ->
            val choice = if (seat == target) (target + 1) % 14 else target
            assertTrue(r.perform(seat, WerewolfAction(WerewolfActionKind.VOTE, choice)))
        }
        assertTrue(r.isComplete())
        r.advance()
        assertEquals(WerewolfStep.VOTE_RESULT, r.step)
        assertEquals(WerewolfVoteOutcome.EXECUTED, r.voteOutcome)
        assertEquals(target, r.executed)
        assertFalse(r.alive[target])
        assertEquals(WerewolfRole.WEREWOLF.ordinal, r.viewFor(0).shown[target])
        assertEquals(13, r.viewFor(0).tally[target])
    }

    @Test
    fun uneEgaliteNElimineLePersonne() {
        val r = WerewolfRound(names(5), Random(3))
        r.ackAll()
        r.advance()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        // 5 joueurs, pas de Bouc émissaire : A et B reçoivent chacun 2 voix, le cinquième vote pour A... on force une égalité 2-2-1.
        val order = r.roles.indices.toList()
        val votes = mapOf(order[0] to order[1], order[1] to order[0], order[2] to order[1], order[3] to order[0], order[4] to order[2])
        votes.forEach { (voter, target) -> assertTrue(r.perform(voter, WerewolfAction(WerewolfActionKind.VOTE, target))) }
        r.advance()
        assertEquals(WerewolfVoteOutcome.TIE, r.voteOutcome)
        assertEquals(5, r.aliveCount())
    }

    @Test
    fun leChasseurEmporteUnJoueurAvecLui() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        val hunter = r.seat(WerewolfRole.HUNTER)
        r.expected().forEach { seat ->
            val choice = if (seat == hunter) (hunter + 1) % 14 else hunter
            assertTrue(r.perform(seat, WerewolfAction(WerewolfActionKind.VOTE, choice)))
        }
        r.advance()
        assertEquals(WerewolfStep.VOTE_RESULT, r.step)
        r.advance()
        assertEquals(WerewolfStep.HUNTER, r.step)
        assertEquals(hunter, r.hunterToShoot())
        val victim = r.wolves().first()
        // Seul le Chasseur tire.
        assertFalse(r.perform(victim, WerewolfAction(WerewolfActionKind.SHOOT, hunter)))
        assertTrue(r.perform(hunter, WerewolfAction(WerewolfActionKind.SHOOT, victim)))
        assertFalse(r.alive[victim])
        r.advance()
        assertEquals(WerewolfStep.NIGHT_START, r.step)
        assertEquals(2, r.night)
    }

    @Test
    fun l_IdiotGracieNePerdPasLaVieMaisPerdSonVote() {
        val r = full()
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        val idiot = r.seat(WerewolfRole.IDIOT)
        r.expected().forEach { seat ->
            val choice = if (seat == idiot) (idiot + 1) % 14 else idiot
            r.perform(seat, WerewolfAction(WerewolfActionKind.VOTE, choice))
        }
        r.advance()
        assertEquals(WerewolfVoteOutcome.IDIOT_SPARED, r.voteOutcome)
        assertTrue(r.alive[idiot])
        assertTrue(r.revealed[idiot])
        // Au vote suivant, il n'est plus attendu.
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        assertFalse(idiot in r.expected())
        assertFalse(r.viewFor(idiot).canVote)
    }

    @Test
    fun leVillageGagneQuandLeDernierLoupEstElimine() {
        val r = WerewolfRound(names(4), Random(5))
        r.ackAll()
        r.advance()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        val wolf = r.roles.indexOf(WerewolfRole.WEREWOLF)
        r.expected().forEach { seat ->
            val choice = if (seat == wolf) (wolf + 1) % 4 else wolf
            r.perform(seat, WerewolfAction(WerewolfActionKind.VOTE, choice))
        }
        r.advance()
        r.advance()
        assertEquals(WerewolfStep.GAME_OVER, r.step)
        assertEquals(WerewolfWinner.VILLAGE, r.winner)
        // À la fin, toutes les cartes sont visibles.
        assertTrue(r.viewFor(0).shown.all { it >= 0 })
    }

    @Test
    fun lesLoupsGagnentQuandIlNeResteQueDesLoups() {
        val r = WerewolfRound(names(4), Random(5))
        r.ackAll()
        r.advance()
        val wolf = r.roles.indexOf(WerewolfRole.WEREWOLF)
        // Chaque nuit les loups mangent un villageois : trois nuits suffisent.
        repeat(3) {
            val victim = r.roles.indices.first { r.alive[it] && it != wolf }
            r.skipTo(WerewolfStep.WOLVES)
            assertTrue(r.perform(wolf, WerewolfAction(WerewolfActionKind.WOLF, victim)))
            r.skipTo(WerewolfStep.DAWN)
            r.advance()
            // Le village ne vote contre personne.
            if (r.step != WerewolfStep.GAME_OVER) r.skipTo(WerewolfStep.NIGHT_START)
        }
        assertEquals(WerewolfStep.GAME_OVER, r.step)
        assertEquals(WerewolfWinner.WOLVES, r.winner)
    }

    @Test
    fun unJoueurNeVoitQueCeQuIlALeDroitDeSavoir() {
        val r = full()
        val wolf = r.wolves().first()
        val villager = r.seat(WerewolfRole.VILLAGER)
        val wolfView = r.viewFor(wolf)
        val villagerView = r.viewFor(villager)
        assertEquals(WerewolfRole.WEREWOLF, wolfView.role)
        assertEquals(r.wolves(), wolfView.mates)
        assertTrue(villagerView.mates.isEmpty())
        // Aucune carte des autres n'est visible au départ.
        assertTrue(villagerView.shown.all { it == -1 })
        assertNotEquals(WerewolfRole.WEREWOLF, villagerView.role)
        // Seule la Voyante garde la trace de ce qu'elle a vu.
        r.startNight()
        r.skipTo(WerewolfStep.SEER)
        val seer = r.seat(WerewolfRole.SEER)
        assertTrue(r.perform(seer, WerewolfAction(WerewolfActionKind.SEE, wolf)))
        assertEquals(listOf(wolf, WerewolfRole.WEREWOLF.ordinal), r.viewFor(seer).seerFound)
        assertTrue(r.viewFor(villager).seerFound.isEmpty())
        assertTrue(r.viewFor(wolf).seerFound.isEmpty())
    }

    @Test
    fun unJoueurQuiPartEstElimine() {
        val r = full()
        val seat = r.seat(WerewolfRole.WITCH)
        r.forfeit(seat)
        assertFalse(r.alive[seat])
        assertTrue(r.revealed[seat])
    }

    @Test
    fun uneActionHorsDeSonTourEstRefusee() {
        val r = full()
        r.startNight()
        // C'est la nuit : on ne peut pas voter.
        assertFalse(r.perform(0, WerewolfAction(WerewolfActionKind.VOTE, 1)))
        // La Voyante ne peut pas agir pendant l'appel d'Cupidon.
        r.skipTo(WerewolfStep.CUPID)
        assertFalse(r.perform(r.seat(WerewolfRole.SEER), WerewolfAction(WerewolfActionKind.SEE, 0)))
    }

    @Test
    fun lesCartesChoisiesSontDistribuees() {
        val specials = listOf(WerewolfRole.SEER, WerewolfRole.SCAPEGOAT)
        val roles = dealWerewolfRoles(7, 2, specials, Random(1))
        assertEquals(7, roles.size)
        assertEquals(2, roles.count { it == WerewolfRole.WEREWOLF })
        assertEquals(1, roles.count { it == WerewolfRole.SEER })
        assertEquals(1, roles.count { it == WerewolfRole.SCAPEGOAT })
        assertEquals(3, roles.count { it == WerewolfRole.VILLAGER })
        // Une partie peut se jouer avec exactement ces cartes.
        val r = WerewolfRound(names(7), Random(1), roles)
        assertEquals(roles, r.roles)
    }

    @Test
    fun desCartesQuiNeTiennentPasSontRefusees() {
        assertTrue(isValidWerewolfDeal(8, 2, defaultWerewolfSpecials(8)))
        assertFalse(isValidWerewolfDeal(5, 1, WEREWOLF_SPECIALS))
        assertFalse(isValidWerewolfDeal(5, 0, emptyList()))
        assertFalse(isValidWerewolfDeal(4, 4, emptyList()))
        // Sans aucune carte à pouvoir, il reste des villageois.
        assertTrue(isValidWerewolfDeal(4, 1, emptyList()))
    }

    @Test
    fun laPartieSeJoueSansLesCartesQuOnARetirees() {
        // Seulement des loups, des villageois et la Voyante : les étapes des autres cartes n'ont pas lieu.
        val roles = dealWerewolfRoles(5, 1, listOf(WerewolfRole.SEER), Random(2))
        val r = WerewolfRound(names(5), Random(2), roles)
        r.ackAll()
        r.advance()
        r.advance()
        assertEquals(WerewolfStep.SEER, r.step)
        r.advance()
        assertEquals(WerewolfStep.WOLVES, r.step)
        r.advance()
        assertEquals(WerewolfStep.DAWN, r.step)
    }

    @Test
    fun lesJoueursSimulesDeroulentUneNuitEtUnJour() {
        val r = WerewolfRound(names(WEREWOLF_TEST_PLAYERS), Random(9))
        val random = Random(4)
        var guard = 0
        // Tous les joueurs sont simulés : jusqu'à la fin de la première journée, chaque coup doit être accepté.
        while (r.night < 2 && r.step != WerewolfStep.GAME_OVER && guard++ < 400) {
            while (!r.isComplete()) {
                val seat = r.expected().first()
                val action = WerewolfBots.action(r, seat, random)
                assertTrue("un joueur simulé doit avoir un coup à l'étape ${r.step}", action != null)
                assertTrue("coup refusé à l'étape ${r.step} : $action", r.perform(seat, action!!))
            }
            r.advance()
        }
        assertTrue(r.night >= 2 || r.step == WerewolfStep.GAME_OVER)
    }

    // ---------- Les autres rôles ----------

    private fun cards(vararg roles: WerewolfRole) = roles.toList()

    private fun roundOf(
        roles: List<WerewolfRole>,
        table: List<WerewolfRole> = emptyList(),
        captain: Boolean = false
    ) = WerewolfRound(names(roles.size), Random(1), roles, table, captain)

    private fun WerewolfRound.everyoneVotes(target: Int, otherChoice: Int, action: WerewolfActionKind = WerewolfActionKind.VOTE) {
        expected().forEach { seat ->
            assertTrue(perform(seat, WerewolfAction(action, if (seat == target) otherChoice else target)))
        }
    }

    @Test
    fun leVoleurEchangeSaCarte() {
        val r = roundOf(cards(WerewolfRole.THIEF, WerewolfRole.WEREWOLF, WerewolfRole.SEER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER),
            listOf(WerewolfRole.WITCH, WerewolfRole.VILLAGER))
        r.startNight()
        r.advance()
        assertEquals(WerewolfStep.THIEF, r.step)
        assertEquals(listOf(WerewolfRole.WITCH.ordinal, WerewolfRole.VILLAGER.ordinal), r.viewFor(0).thiefCards)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.THIEF, 0)))
        assertEquals(WerewolfRole.WITCH, r.roles[0])
    }

    @Test
    fun leVoleurDoitPrendreUnLoupSiLesDeuxCartesSontDesLoups() {
        val r = roundOf(cards(WerewolfRole.THIEF, WerewolfRole.WEREWOLF, WerewolfRole.SEER, WerewolfRole.VILLAGER),
            listOf(WerewolfRole.WEREWOLF, WerewolfRole.BIG_BAD_WOLF))
        r.startNight()
        r.advance()
        assertTrue(r.viewFor(0).thiefForced)
        assertFalse(r.perform(0, WerewolfAction(WerewolfActionKind.THIEF, -1)))
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.THIEF, 1)))
        assertTrue(r.isWolf(0))
    }

    @Test
    fun leCapitaineEstEluEtSaVoixCompteDouble() {
        val r = roundOf(cards(WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER), captain = true)
        r.startNight()
        r.skipTo(WerewolfStep.DAWN)
        r.advance()
        assertEquals(WerewolfStep.ELECT, r.step)
        r.everyoneVotes(2, 3, WerewolfActionKind.ELECT)
        r.advance()
        assertEquals(2, r.captain)
        assertEquals(WerewolfStep.DEBATE, r.step)
        assertEquals(2, r.captainAnnounce)
        r.advance()
        // 4 reçoit deux voix et la voix double du Capitaine, 5 et 0 deux voix chacun.
        val votes = mapOf(0 to 5, 1 to 5, 2 to 4, 3 to 4, 4 to 0, 5 to 0)
        votes.forEach { (voter, target) -> assertTrue(r.perform(voter, WerewolfAction(WerewolfActionKind.VOTE, target))) }
        r.advance()
        assertEquals(4, r.executed)
    }

    @Test
    fun l_AngeGagneS_IlEstVoteDesLePremierJour() {
        val r = roundOf(cards(WerewolfRole.ANGEL, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        r.everyoneVotes(0, 1)
        r.advance()
        r.advance()
        assertEquals(WerewolfWinner.ANGEL, r.winner)
        assertEquals(WerewolfStep.GAME_OVER, r.step)
    }

    @Test
    fun l_InfectPereDesLoupsInfecteLaVictime() {
        val r = roundOf(cards(WerewolfRole.INFECT_FATHER, WerewolfRole.WEREWOLF, WerewolfRole.SEER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.WOLVES)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.WOLF, 3)))
        assertTrue(r.perform(1, WerewolfAction(WerewolfActionKind.WOLF, 3)))
        r.advance()
        assertEquals(WerewolfStep.INFECT, r.step)
        assertEquals(3, r.viewFor(0).witchVictim)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.INFECT, 1)))
        r.skipTo(WerewolfStep.DAWN)
        assertTrue(r.lastDeaths.isEmpty())
        val infected = r.viewFor(3)
        assertTrue(infected.converted)
        assertEquals(listOf(0, 1, 3), infected.mates)
    }

    @Test
    fun leChevalierRouilleEmporteUnLoupLaNuitSuivante() {
        val r = roundOf(cards(WerewolfRole.RUSTY_KNIGHT, WerewolfRole.VILLAGER, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.WOLVES)
        assertTrue(r.perform(2, WerewolfAction(WerewolfActionKind.WOLF, 0)))
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(listOf(0), r.lastDeaths)
        r.skipTo(WerewolfStep.NIGHT_START)
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(listOf(2), r.lastDeaths)
    }

    @Test
    fun l_OursGronneQuandUnLoupEstVoisin() {
        val near = roundOf(cards(WerewolfRole.BEAR_TAMER, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        near.startNight()
        near.skipTo(WerewolfStep.DAWN)
        assertTrue(near.bearGrowl)
        val far = roundOf(cards(WerewolfRole.BEAR_TAMER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        far.startNight()
        far.skipTo(WerewolfStep.DAWN)
        assertFalse(far.bearGrowl)
    }

    @Test
    fun lesSoeursSeReconnaissentLaPremiereNuit() {
        val r = roundOf(cards(WerewolfRole.SISTER, WerewolfRole.SISTER, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.advance()
        assertEquals(WerewolfStep.SISTERS, r.step)
        assertEquals(listOf(1), r.viewFor(0).siblings)
        assertEquals(listOf(0), r.viewFor(1).siblings)
        assertTrue(r.viewFor(0).alert > 0)
        assertTrue(r.viewFor(2).siblings.isEmpty())
    }

    @Test
    fun lePyromaneIncendieLesJoueursArroses() {
        val r = roundOf(cards(WerewolfRole.PYROMANIAC, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.PYRO)
        assertFalse(r.perform(0, WerewolfAction(WerewolfActionKind.IGNITE)))
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.DOUSE, 2, 3)))
        r.skipTo(WerewolfStep.NIGHT_START)
        r.skipTo(WerewolfStep.PYRO)
        assertTrue(r.viewFor(0).canIgnite)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.IGNITE)))
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(setOf(2, 3), r.lastDeaths.toSet())
    }

    @Test
    fun leJugeBegueDemandeUnSecondVote() {
        val r = roundOf(cards(WerewolfRole.JUDGE, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        assertTrue(r.viewFor(0).canJudge)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.JUDGE)))
        assertFalse(r.perform(0, WerewolfAction(WerewolfActionKind.JUDGE)))
        r.everyoneVotes(5, 4)
        r.advance()
        assertTrue(r.secondVote)
        r.advance()
        assertEquals(WerewolfStep.VOTE, r.step)
        assertFalse(r.viewFor(0).canJudge)
    }

    @Test
    fun laServantePrendLaCarteDuCondamne() {
        val r = roundOf(cards(WerewolfRole.MAID, WerewolfRole.WEREWOLF, WerewolfRole.SEER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.DEBATE)
        r.advance()
        r.everyoneVotes(2, 3)
        r.advance()
        assertEquals(WerewolfStep.MAID, r.step)
        assertEquals(2, r.viewFor(0).maidTarget)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.MAID, 1)))
        r.advance()
        assertEquals(WerewolfStep.VOTE_RESULT, r.step)
        assertEquals(WerewolfRole.SEER, r.roles[0])
        assertEquals(WerewolfRole.MAID, r.roles[2])
        assertEquals(0, r.maidSwapped)
    }

    @Test
    fun leJoueurDeFluteGagneQuandTousLesSurvivantsSontCharmes() {
        val r = roundOf(cards(WerewolfRole.PIPER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        r.skipTo(WerewolfStep.PIPER)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.CHARM, 1, 2)))
        assertEquals(listOf(1, 2), r.viewFor(0).charmedSeats)
        assertEquals(listOf(1, 2), r.viewFor(1).charmedSeats)
        assertTrue(r.viewFor(3).charmedSeats.isEmpty())
        r.skipTo(WerewolfStep.NIGHT_START)
        r.skipTo(WerewolfStep.PIPER)
        // Il ne reste qu'un joueur à charmer : un seul choix suffit.
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.CHARM, 3, -1)))
        r.skipTo(WerewolfStep.DAWN)
        r.advance()
        assertEquals(WerewolfWinner.PIPER, r.winner)
    }

    @Test
    fun leLoupBlancDevoreUnLoupUneNuitSurDeux() {
        val r = roundOf(cards(WerewolfRole.WHITE_WOLF, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER))
        r.startNight()
        // La première nuit, il se joue comme un loup ordinaire : rien ne lui est demandé de plus.
        r.skipTo(WerewolfStep.WHITE_WOLF)
        assertTrue(r.isDecoy())
        r.skipTo(WerewolfStep.NIGHT_START)
        r.skipTo(WerewolfStep.WHITE_WOLF)
        assertTrue(r.viewFor(0).prompt == WerewolfPrompt.WHITE)
        assertEquals(listOf(1), r.viewFor(0).candidates)
        assertTrue(r.perform(0, WerewolfAction(WerewolfActionKind.WHITE, 1)))
        r.skipTo(WerewolfStep.DAWN)
        assertEquals(listOf(1), r.lastDeaths)
    }

    @Test
    fun chaqueRoleSeJoueSansCoupRefuse() {
        // Pour chaque carte, une partie test à 6 joueurs où tous les joueurs sont simulés : une nuit et un jour sans coup refusé.
        for (role in WEREWOLF_SPECIALS) {
            for (seed in 1..4) {
                val specials = (listOf(role) + defaultWerewolfSpecials(6).filter { it != role }).toMutableList()
                while (!isValidWerewolfDeal(6, 1, specials)) specials.removeAt(specials.lastIndex)
                val deal = dealWerewolf(6, 1, specials, Random(seed))
                val roles = deal.roles.toMutableList()
                val holder = roles.indexOf(role)
                if (holder > 0) {
                    roles[holder] = roles[0]
                    roles[0] = role
                }
                val r = WerewolfRound(names(6), Random(seed), roles, deal.table, WerewolfRole.CAPTAIN in specials)
                val random = Random(seed)
                var guard = 0
                while (r.night < 2 && r.step != WerewolfStep.GAME_OVER && guard++ < 400) {
                    var inner = 0
                    while (!r.isComplete() && inner++ < 100) {
                        val seat = r.expected().first()
                        val action = WerewolfBots.action(r, seat, random)
                        assertTrue("$role/$seed : pas de coup à l'étape ${r.step}", action != null)
                        assertTrue("$role/$seed : coup refusé à l'étape ${r.step} : $action", r.perform(seat, action!!))
                    }
                    assertTrue("$role/$seed : étape bloquée ${r.step}", r.isComplete())
                    r.advance()
                }
                assertTrue("$role/$seed : la partie n'avance pas", guard < 400)
            }
        }
    }

    @Test
    fun laTableDuTestCompletTientEtSeJoueUneNuitEtUnJour() {
        // Une carte de chaque rôle, comme dans WerewolfOnline.startFullTest : bien plus de places qu'une vraie partie.
        val cards = mutableListOf(WerewolfRole.WEREWOLF)
        for (role in WEREWOLF_SPECIALS) repeat(role.seatCount()) { cards.add(role) }
        val players = cards.size
        assertTrue(players > WEREWOLF_MAX_PLAYERS)
        assertTrue(players <= WEREWOLF_FULL_TEST_MAX_PLAYERS)
        val r = WerewolfRound(
            names(players), Random(3), cards.shuffled(Random(5)),
            listOf(WerewolfRole.VILLAGER, WerewolfRole.WEREWOLF), true
        )
        val random = Random(11)
        var guard = 0
        while (r.night < 2 && r.step != WerewolfStep.GAME_OVER && guard++ < 2000) {
            while (!r.isComplete()) {
                val seat = r.expected().first()
                val action = WerewolfBots.action(r, seat, random)
                assertTrue("un joueur doit avoir un coup à l'étape ${r.step}", action != null)
                assertTrue("coup refusé à l'étape ${r.step} : $action", r.perform(seat, action!!))
            }
            r.advance()
        }
        assertTrue(r.night >= 2 || r.step == WerewolfStep.GAME_OVER)
    }
}
