package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Uno jouable tour par tour : paquet, cartes jouables, cartes action, pioche, passe, fin de manche, tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class UnoRoundTest {

    private val red = 0
    private val yellow = 1
    private val green = 2
    private val blue = 3

    /** Un chiffre de 0 à 9 de la couleur [color] (première des deux cartes). */
    private fun n(color: Int, number: Int): Int = color * UnoScoring.PER_COLOR + if (number == 0) 0 else 1 + (number - 1) * 2

    private fun skip(color: Int) = color * UnoScoring.PER_COLOR + 19
    private fun reverse(color: Int) = color * UnoScoring.PER_COLOR + 21
    private fun plusTwo(color: Int) = color * UnoScoring.PER_COLOR + 23
    private val wild = UnoScoring.FIRST_WILD
    private val wildFour = UnoScoring.FIRST_WILD_FOUR

    /** Une manche à [players] joueurs dont le paquet n'est pas mélangé : les cartes 0, 1, 2… sont distribuées dans l'ordre. */
    private fun round(players: Int = 2, startPlayer: Int = 0): UnoRound = UnoRound(players, startPlayer) { }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == UNO_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Table : un joueur par main de [hands], la dernière carte posée est [top]. */
    private fun table(
        vararg hands: List<Int>,
        top: Int,
        stock: List<Int> = emptyList(),
        discard: List<Int> = listOf(top),
        current: Int = 0,
        direction: Int = 1
    ): UnoRound = round(players = hands.size).also {
        it.load(hands.toList(), stock, discard, current, direction = direction)
        it.reveal()
    }

    // ---------- Le paquet et les points ----------

    @Test
    fun lePaquetA108CartesDontLaCompositionOfficielle() {
        val kinds = (0 until UnoScoring.DECK).groupingBy { UnoScoring.kind(it) }.eachCount()
        for (number in 0..9) assertEquals(if (number == 0) 4 else 8, kinds[number])
        assertEquals(8, kinds[UnoScoring.SKIP])
        assertEquals(8, kinds[UnoScoring.REVERSE])
        assertEquals(8, kinds[UnoScoring.DRAW_TWO])
        assertEquals(4, kinds[UnoScoring.WILD])
        assertEquals(4, kinds[UnoScoring.WILD_FOUR])
        assertEquals(108, UnoScoring.DECK)
        // 25 cartes dans chacune des 4 couleurs, et les 8 jokers sans couleur.
        for (color in 0..3) assertEquals(25, (0 until UnoScoring.DECK).count { UnoScoring.color(it) == color })
        assertEquals(8, (0 until UnoScoring.DECK).count { UnoScoring.color(it) == -1 })
    }

    @Test
    fun lesCartesValentLeursPointsEnMain() {
        assertEquals(0, UnoScoring.points(n(red, 0)))
        assertEquals(7, UnoScoring.points(n(blue, 7)))
        assertEquals(20, UnoScoring.points(skip(green)))
        assertEquals(20, UnoScoring.points(reverse(yellow)))
        assertEquals(20, UnoScoring.points(plusTwo(red)))
        assertEquals(50, UnoScoring.points(wild))
        assertEquals(50, UnoScoring.points(wildFour))
        assertEquals(7 + 20 + 50, UnoScoring.handPoints(listOf(n(blue, 7), skip(red), wildFour)))
    }

    // ---------- Cartes jouables ----------

    @Test
    fun uneCartePoseSurLaMemeCouleurLeMemeChiffreOuLeMemeSymbole() {
        val top = n(red, 5)
        val hand = listOf(n(blue, 1))
        assertTrue(UnoScoring.isPlayable(n(red, 9), top, red, hand))
        assertTrue(UnoScoring.isPlayable(n(blue, 5), top, red, hand))
        assertFalse(UnoScoring.isPlayable(n(blue, 9), top, red, hand))
        // Même symbole d'une autre couleur.
        assertTrue(UnoScoring.isPlayable(skip(blue), skip(red), red, hand))
        assertTrue(UnoScoring.isPlayable(plusTwo(green), plusTwo(red), red, hand))
        assertFalse(UnoScoring.isPlayable(skip(blue), reverse(red), red, hand))
        assertTrue(UnoScoring.isPlayable(wild, top, red, hand))
    }

    @Test
    fun apresUnJokerSeuleLaCouleurChoisieCompte() {
        val hand = listOf(n(blue, 1))
        assertTrue(UnoScoring.isPlayable(n(green, 4), wild, green, hand))
        assertFalse(UnoScoring.isPlayable(n(red, 4), wild, green, hand))
        assertTrue(UnoScoring.isPlayable(wild, wild, green, hand))
    }

    @Test
    fun leJokerPlusQuatreNeSeJoueQueSansCarteDeLaCouleurDemandee() {
        val top = n(red, 5)
        assertFalse(UnoScoring.isPlayable(wildFour, top, red, listOf(wildFour, n(red, 2))))
        assertTrue(UnoScoring.isPlayable(wildFour, top, red, listOf(wildFour, n(blue, 5), wild)))
    }

    // ---------- Distribution ----------

    @Test
    fun laDistributionDonneSeptCartesEtRetourneUnChiffre() {
        val round = round()
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), round.hands[0])
        assertEquals(listOf(7, 8, 9, 10, 11, 12, 13), round.hands[1])
        assertEquals(UnoScoring.DECK - 14 - 1, round.stock.size)
        assertTrue(UnoScoring.kind(round.top) <= 9)
        assertEquals(UnoScoring.color(round.top), round.askedColor)
        assertEquals(0, round.current)
        assertEquals(1, round.direction)
        assertFalse(round.revealed)
        assertFalse(round.roundOver)
    }

    @Test
    fun laPremiereCarteRetourneeEstToujoursUnChiffre() {
        // Les cartes 14 et 15 (un 2 et un 3 rouges) sont remplacées en tête de pioche par un +2 puis un joker +4.
        val round = UnoRound(2, 0) { deck ->
            deck.remove(plusTwo(red))
            deck.add(14, plusTwo(red))
            deck.remove(wildFour)
            deck.add(15, wildFour)
        }
        assertTrue(UnoScoring.kind(round.top) <= 9)
        assertEquals(UnoScoring.DECK - 14 - 1, round.stock.size)
    }

    @Test
    fun dixJoueursSontServisSansEpuiserLePaquet() {
        val round = round(players = 10)
        assertEquals(List(10) { 7 }, round.hands.map { it.size })
        assertEquals(UnoScoring.DECK - 70 - 1, round.stock.size)
    }

    // ---------- Poser une carte ----------

    @Test
    fun onNePoseQueSaCarteAuBonMomentEtLaMainSeCache() {
        val round = table(listOf(n(red, 9), n(blue, 2)), listOf(n(green, 3)), top = n(red, 2))
        assertTrue(round.play(n(red, 9)))
        assertEquals(n(red, 9), round.top)
        assertEquals(listOf(n(blue, 2)), round.hands[0])
        assertEquals(1, round.current)
        assertFalse(round.revealed)
        assertNull(round.lastEvent)
        // Pas encore de main dévoilée : aucun coup.
        assertFalse(round.play(n(green, 3)))
        round.reveal()
        // Le 3 vert ne va ni sur le rouge ni sur le 9.
        assertFalse(round.play(n(green, 3)))
        // Une carte qu'on n'a pas.
        assertFalse(round.play(n(red, 1)))
    }

    @Test
    fun unJokerDemandeUneCouleurSaufEnDerniereCarte() {
        val round = table(listOf(wild, n(blue, 2)), listOf(n(green, 3), n(green, 4)), top = n(red, 2))
        assertFalse(round.play(wild))
        assertFalse(round.play(wild, 7))
        assertTrue(round.play(wild, green))
        assertEquals(green, round.askedColor)
        assertEquals(wild, round.top)
        assertEquals(1, round.current)
        round.reveal()
        assertTrue(round.isPlayable(n(green, 3)))
        assertFalse(round.isPlayable(n(red, 3)))

        val last = table(listOf(wild), listOf(n(green, 3)), top = n(red, 2))
        assertTrue(last.play(wild))
        assertTrue(last.roundOver)
    }

    // ---------- Cartes action ----------

    @Test
    fun passeTonTourFaitSauterLeJoueurSuivant() {
        val round = table(
            listOf(skip(red), n(red, 1)), listOf(n(red, 2)), listOf(n(red, 3)),
            top = n(red, 9)
        )
        assertTrue(round.play(skip(red)))
        assertEquals(2, round.current)
        assertEquals(UnoEvent(player = 0, card = skip(red), victim = 1, drawn = 0), round.lastEvent)
        assertFalse(round.revealed)
    }

    @Test
    fun linversionChangeLeSensEtLeJoueurPrecedentJoue() {
        val round = table(
            listOf(reverse(red), n(red, 1)), listOf(n(red, 2)), listOf(n(red, 3), n(red, 4)),
            top = n(red, 9)
        )
        assertTrue(round.play(reverse(red)))
        assertEquals(-1, round.direction)
        assertEquals(2, round.current)
        assertEquals(UnoEvent(player = 0, card = reverse(red), victim = -1, drawn = 0), round.lastEvent)
        round.reveal()
        assertTrue(round.play(n(red, 3)))
        // Le sens inverse continue : après le joueur 2 vient le joueur 1.
        assertEquals(1, round.current)
    }

    @Test
    fun aDeuxJoueursLinversionEtLePasseTonTourFontRejouer() {
        val a = table(listOf(reverse(red), n(red, 1)), listOf(n(red, 2)), top = n(red, 9))
        assertTrue(a.play(reverse(red)))
        assertEquals(0, a.current)
        assertTrue(a.revealed)
        assertEquals(1, a.lastEvent?.victim)

        val b = table(listOf(skip(red), n(red, 1)), listOf(n(red, 2)), top = n(red, 9))
        assertTrue(b.play(skip(red)))
        assertEquals(0, b.current)
        assertTrue(b.revealed)
    }

    @Test
    fun unPlusDeuxFaitPiocherDeuxCartesAuSuivantQuiPasseSonTour() {
        val round = table(
            listOf(plusTwo(red), n(red, 1)), listOf(n(blue, 2)), listOf(n(blue, 3)),
            top = n(red, 9),
            stock = listOf(n(green, 1), n(green, 2), n(green, 3))
        )
        assertTrue(round.play(plusTwo(red)))
        assertEquals(listOf(n(blue, 2), n(green, 1), n(green, 2)), round.hands[1])
        assertEquals(listOf(n(green, 3)), round.stock)
        assertEquals(2, round.current)
        assertEquals(UnoEvent(player = 0, card = plusTwo(red), victim = 1, drawn = 2), round.lastEvent)
    }

    @Test
    fun unJokerPlusQuatreFaitPiocherQuatreCartesEtChoisitLaCouleur() {
        val round = table(
            listOf(wildFour, n(yellow, 1)), listOf(n(blue, 2)), listOf(n(blue, 3)),
            top = n(red, 9),
            stock = listOf(n(green, 1), n(green, 2), n(green, 3), n(green, 4), n(green, 5))
        )
        // Une carte de la couleur demandée en main interdit le +4 : ici le joueur 0 n'a que du jaune.
        assertTrue(round.play(wildFour, blue))
        assertEquals(5, round.hands[1].size)
        assertEquals(1, round.stock.size)
        assertEquals(blue, round.askedColor)
        assertEquals(2, round.current)
        assertEquals(4, round.lastEvent?.drawn)
    }

    @Test
    fun laPiocheSeRemelangeEnPleinPlusDeux() {
        // Pioche d'une seule carte : la seconde vient des cartes posées remélangées (sauf la dernière).
        val round = table(
            listOf(plusTwo(red), n(red, 1)), listOf(n(blue, 2)),
            top = n(red, 9),
            stock = listOf(n(green, 1)),
            discard = listOf(n(yellow, 7), n(yellow, 8), n(red, 9))
        )
        assertTrue(round.play(plusTwo(red)))
        assertEquals(3, round.hands[1].size)
        assertEquals(plusTwo(red), round.top)
        assertEquals(1, round.discard.size)
    }

    // ---------- Piocher et passer ----------

    @Test
    fun sansCarteJouableOnPiocheUneSeuleCarte() {
        val round = table(
            listOf(n(blue, 2)), listOf(n(green, 3), n(green, 4)),
            top = n(red, 9),
            stock = listOf(n(green, 1), n(red, 4))
        )
        assertFalse(round.hasPlayable)
        assertTrue(round.canDraw)
        assertEquals(n(green, 1), round.draw())
        // Injouable : il ne peut plus que passer.
        assertFalse(round.canDraw)
        assertTrue(round.mustPass)
        assertFalse(round.canKeep)
        assertNull(round.draw())
        assertTrue(round.pass())
        assertEquals(1, round.current)
        assertEquals(2, round.hands[0].size)
    }

    @Test
    fun uneCartePiocheeJouablePeutEtrePoseeOuGardee() {
        val round = table(
            listOf(n(blue, 2)), listOf(n(green, 3), n(green, 4)),
            top = n(red, 9),
            stock = listOf(n(red, 4), n(red, 5))
        )
        assertEquals(n(red, 4), round.draw())
        assertTrue(round.canKeep)
        assertFalse(round.mustPass)
        // Seule la carte piochée est jouable, pas une autre carte de la main.
        assertTrue(round.isPlayable(n(red, 4)))
        assertTrue(round.pass())
        assertEquals(1, round.current)
        assertEquals(2, round.hands[0].size)

        val again = table(
            listOf(n(blue, 2)), listOf(n(green, 3), n(green, 4)),
            top = n(red, 9),
            stock = listOf(n(red, 4), n(red, 5))
        )
        again.draw()
        assertTrue(again.play(n(red, 4)))
        assertEquals(n(red, 4), again.top)
        assertEquals(listOf(n(blue, 2)), again.hands[0])
    }

    @Test
    fun onNePeutPasPiocherQuandOnPeutJouer() {
        val round = table(listOf(n(red, 2), n(blue, 2)), listOf(n(green, 3)), top = n(red, 9), stock = listOf(n(green, 1)))
        assertTrue(round.hasPlayable)
        assertFalse(round.canDraw)
        assertNull(round.draw())
        assertFalse(round.pass())
    }

    @Test
    fun laPiocheVideSeRemelangeAvantDePiocher() {
        val round = table(
            listOf(n(blue, 2)), listOf(n(green, 3)),
            top = n(red, 9),
            stock = emptyList(),
            discard = listOf(n(yellow, 7), n(yellow, 8), n(red, 9))
        )
        assertTrue(round.canDraw)
        assertEquals(n(yellow, 7), round.draw())
        assertEquals(1, round.discard.size)
        assertEquals(n(red, 9), round.top)
        assertEquals(1, round.stock.size)
    }

    @Test
    fun siToutLeMondePasseFauteDePiocheLaMancheEstBloqueeEtLaMainLaPlusLegereGagne() {
        val round = table(
            listOf(n(blue, 2), n(blue, 3)), listOf(n(green, 1)), listOf(n(green, 7), n(green, 8)),
            top = n(red, 9)
        )
        assertTrue(round.mustPass)
        assertTrue(round.pass())
        round.reveal()
        assertTrue(round.pass())
        round.reveal()
        assertFalse(round.roundOver)
        assertTrue(round.pass())
        assertTrue(round.roundOver)
        assertTrue(round.blocked)
        assertEquals(1, round.winner)
        // Le gagnant marque les points des autres mains : (2 + 3) + (7 + 8).
        assertEquals(listOf(0, 20, 0), round.roundPoints())
    }

    // ---------- Fin de manche ----------

    @Test
    fun lePremierSansCarteGagneEtMarqueLesPointsDesAutres() {
        val round = table(
            listOf(n(red, 9)), listOf(wild, n(blue, 4)), listOf(skip(green)),
            top = n(red, 2)
        )
        assertTrue(round.hasUno(0))
        assertTrue(round.play(n(red, 9)))
        assertTrue(round.roundOver)
        assertFalse(round.blocked)
        assertEquals(0, round.winner)
        assertEquals(listOf(50 + 4 + 20, 0, 0), round.roundPoints())
        assertFalse(round.hasUno(1))
        assertFalse(round.revealed)
        assertFalse(round.play(n(blue, 4)))
    }

    @Test
    fun lAnnonceUnoSeFaitQuandIlNeResteQuUneCarte() {
        val round = table(listOf(n(red, 9), n(blue, 2)), listOf(n(green, 3), n(green, 4)), top = n(red, 2))
        assertFalse(round.hasUno(0))
        round.play(n(red, 9))
        assertTrue(round.hasUno(0))
        assertFalse(round.hasUno(1))
    }

    @Test
    fun unPlusDeuxEnDerniereCarteFaitPiocherAvantLeComptageDesPoints() {
        val round = table(
            listOf(plusTwo(red)), listOf(n(blue, 2)),
            top = n(red, 9),
            stock = listOf(n(green, 5), n(green, 6))
        )
        assertTrue(round.play(plusTwo(red)))
        assertTrue(round.roundOver)
        assertEquals(3, round.hands[1].size)
        assertEquals(listOf(2 + 5 + 6, 0), round.roundPoints())
    }

    // ---------- Tableau des scores ----------

    @Test
    fun lesPointsDeLaMancheSontInscritsAuTableauUneSeuleFois() {
        val vm = viewModel()
        val round = table(listOf(n(red, 9)), listOf(wild, n(blue, 4)), top = n(red, 2))
        assertFalse(round.settle(vm))
        round.play(n(red, 9))
        assertTrue(round.settle(vm))
        assertTrue(round.settled)
        assertEquals(listOf(54, 0), vm.totals)
        assertFalse(round.settle(vm))
        assertEquals(listOf(54, 0), vm.totals)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laMancheSuivanteNeSeDistribuePasAvantLInscriptionEtCommenceParLeJoueurSuivant() {
        val vm = viewModel()
        val round = table(listOf(n(red, 9)), listOf(n(blue, 4)), top = n(red, 2))
        round.play(n(red, 9))
        assertFalse(round.nextRound())
        assertTrue(round.roundOver)
        round.settle(vm)
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertEquals(1, round.current)
        assertFalse(round.roundOver)
        assertEquals(List(2) { 7 }, round.hands.map { it.size })
    }

    @Test
    fun laPartieSArreteAuPremierJoueurQuiAtteint500Points() {
        val vm = viewModel()
        vm.setCell(0, 0, 480, false)
        vm.setCell(0, 1, 100, false)
        assertFalse(vm.isGameOver())
        val round = table(listOf(n(red, 9)), listOf(n(blue, 4), n(blue, 5)), top = n(red, 2))
        round.play(n(red, 9))
        assertTrue(round.settle(vm))
        // Anna gagne la manche : 480 + (4 + 5) = 489, pas encore 500.
        assertEquals(489, vm.totals[0])
        assertFalse(vm.isGameOver())
        val next = table(listOf(n(red, 9)), listOf(wild), top = n(red, 2))
        next.play(n(red, 9))
        assertTrue(next.settle(vm))
        // 489 + 50 : le plus grand total gagne.
        assertEquals(539, vm.totals[0])
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    // ---------- Progression et reprise ----------

    @Test
    fun uneManchePartielleReprendApresLesManchesDejaInscrites() {
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        vm.setCell(0, 0, 10, false)
        vm.setCell(0, 1, 0, false)
        vm.setCell(0, 2, 0, false)
        vm.setCell(1, 0, 0, false)
        vm.setCell(1, 1, 0, false)
        vm.setCell(1, 2, 3, false)
        val resumed = vm.unoRound()
        assertEquals(3, resumed.roundNumber)
        assertEquals(2, resumed.current)
        assertTrue(resumed === vm.unoRound())
    }

    @Test
    fun uneNouvellePartieEffaceLaMancheEnCours() {
        val vm = viewModel()
        val first = vm.unoRound()
        assertFalse(vm.hasProgress())
        first.load(listOf(listOf(n(red, 9), n(red, 3)), listOf(n(blue, 4))), emptyList(), listOf(n(red, 2)))
        first.reveal()
        assertTrue(first.play(n(red, 9)))
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben"), rules())
        assertFalse(first === vm.unoRound())
        assertFalse(vm.hasProgress())
    }

    // ---------- Jeu jouable ----------

    @Test
    fun leUnoEstUnJeuJouableSurUnSeulTelephoneAvecSaFeuilleDeScores() {
        val uno = rules()
        assertTrue(uno.isPlayableGame())
        assertFalse(uno.isMultiPhone())
        assertTrue(uno.hasScoreSheet())
        assertFalse(uno.isDiceOnly())
        assertEquals(GameCategory.SPECIAL, uno.category())
        assertEquals(2, uno.minPlayers)
        assertEquals(10, uno.maxPlayers)
        assertTrue(UNO_ID in GAME_ICON_IDS)
    }
}
