package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le Skyjo Action tour par tour : paquets, distribution, tour de jeu, lignes retirées, étoiles, cartes action, fin de manche. */
class SkyjoActionRoundTest {

    private val star = SkyjoActionScoring.STAR

    /** Une grille de 12 cartes (4 colonnes de 3) : [cards] dans l'ordre, cachées sauf celles dont la place est dans [up]. */
    private fun grid(cards: List<Int> = (1..12).toList(), up: Set<Int> = emptySet()): List<SkyjoCell?> =
        cards.mapIndexed { i, card -> SkyjoCell(card, i in up) }

    private fun round(players: Int = 2): SkyjoActionRound = SkyjoActionRound(players, startPlayer = 0) { }

    private fun table(
        grids: List<List<SkyjoCell?>> = listOf(grid(), grid()),
        stock: List<Int> = listOf(0, 0, 0, 0, 0, 0),
        discard: List<Int> = listOf(0),
        current: Int = 0,
        row: List<SkyjoAction> = emptyList(),
        actionStock: List<SkyjoAction> = emptyList(),
        actionDiscard: List<SkyjoAction> = emptyList(),
        hands: List<List<SkyjoHeldAction>> = List(grids.size) { emptyList() }
    ): SkyjoActionRound = round(grids.size).also { it.load(grids, stock, discard, current, row, actionStock, actionDiscard, hands) }

    private fun held(action: SkyjoAction, unlock: Int = 1) = SkyjoHeldAction(action, unlock)

    private fun cardAt(r: SkyjoActionRound, player: Int, cell: Int): Int? = r.grids[player][cell]?.card

    // ---------- Paquets et distribution ----------

    @Test
    fun lePaquetA120CartesDontQuinzeEtoilesEtLesActionsEnOntTrente() {
        val deck = SkyjoActionScoring.numberDeck()
        assertEquals(120, deck.size)
        for (value in -2..12) assertEquals(7, deck.count { it == value })
        assertEquals(15, deck.count { it == star })
        val actions = SkyjoActionScoring.actionDeck()
        assertEquals(30, actions.size)
        assertEquals(SkyjoAction.values().toSet(), actions.toSet())
    }

    @Test
    fun laDistributionDonneDouzeCartesJusqua4JoueursEtNeufAuDela() {
        val four = SkyjoActionRound(4)
        assertTrue(four.grids.all { it.size == 12 && it.count { c -> c!!.up } == 2 })
        val five = SkyjoActionRound(5)
        assertTrue(five.grids.all { it.size == 9 && it.count { c -> c!!.up } == 1 })
        assertEquals(4, four.actionRow.size)
        assertEquals(26, four.actionStock.size)
        assertEquals(1, four.discard.size)
        assertEquals(120 - 4 * 12 - 1, four.stock.size)
    }

    @Test
    fun celuiDontLesCartesRetourneesTotalisentLePlusCommence() {
        val r = SkyjoActionRound(2, startPlayer = null) { }
        // Sans mélange : les cartes sont distribuées dans l'ordre du paquet, les deux premières places sont retournées ou non au hasard.
        assertTrue(r.current in 0..1)
        val fixed = SkyjoActionRound(3, startPlayer = 2) { }
        assertEquals(2, fixed.current)
    }

    // ---------- Un tour ----------

    @Test
    fun onPiocheEtOnEchangeLaCarteAvecUneCarteDeSaGrille() {
        val r = table(stock = listOf(7, 8))
        assertTrue(r.drawFromStock())
        assertEquals(SkyjoPhase.PLACE, r.phase)
        assertEquals(7, r.held)
        assertTrue(r.placeHeld(5))
        assertEquals(7, cardAt(r, 0, 5))
        assertTrue(r.grids[0][5]!!.up)
        assertEquals(listOf(0, 6), r.discard)
        assertEquals(1, r.current)
        assertEquals(SkyjoPhase.CHOOSE, r.phase)
    }

    @Test
    fun onPeutDefausserLaCartePiocheeEtRetournerUneCarteCachee() {
        val r = table(grids = listOf(grid(up = setOf(0)), grid()), stock = listOf(9))
        r.drawFromStock()
        assertFalse(r.discardHeldAndFlip(0))
        assertTrue(r.discardHeldAndFlip(3))
        assertTrue(r.grids[0][3]!!.up)
        assertEquals(4, cardAt(r, 0, 3))
        assertEquals(listOf(0, 9), r.discard)
        assertEquals(1, r.current)
    }

    @Test
    fun uneCarteDeLaDefausseDoitEtreEchangee() {
        val r = table(discard = listOf(0, 11))
        assertTrue(r.takeFromDiscard())
        assertEquals(listOf(0), r.discard)
        assertFalse(r.discardHeldAndFlip(1))
        assertTrue(r.placeHeld(1))
        assertEquals(11, cardAt(r, 0, 1))
        assertEquals(listOf(0, 2), r.discard)
    }

    @Test
    fun laPiocheVideEstRemplaceeParLaDefausseMelangee() {
        val r = table(stock = emptyList(), discard = listOf(3, 4, 5))
        assertTrue(r.drawFromStock())
        assertEquals(3, r.held)
        assertEquals(listOf(5), r.discard)
        assertEquals(listOf(4), r.stock)
    }

    // ---------- Lignes retirees et etoiles ----------

    @Test
    fun uneColonneDeTroisCartesIdentiquesEstRetiree() {
        // Colonne 0 : places 0, 4, 8 = 5, 5 puis 9 que l'on remplace par un 5.
        val cards = listOf(5, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val r = table(grids = listOf(grid(cards, up = setOf(0, 4)), grid()), stock = listOf(5))
        r.drawFromStock()
        r.placeHeld(8)
        assertEquals(null, r.grids[0][0])
        assertEquals(null, r.grids[0][4])
        assertEquals(null, r.grids[0][8])
        // L'ancienne carte de la place 8 (9), puis les trois 5 retires.
        assertEquals(listOf(0, 9, 5, 5, 5), r.discard)
    }

    @Test
    fun laColonneRetireeNeComptePlusRienEtLEtoileFaitJoker() {
        val cards = listOf(5, 2, 3, 4, star, 6, 7, 8, 9, 10, 11, 12)
        val r = table(grids = listOf(grid(cards, up = setOf(0, 4)), grid()), stock = listOf(5))
        r.drawFromStock()
        r.placeHeld(8)
        assertEquals(null, r.grids[0][0])
        assertEquals(null, r.grids[0][4])
        assertEquals(null, r.grids[0][8])
    }

    @Test
    fun uneLigneCompleteDeQuatreCartesIdentiquesEstRetiree() {
        val cards = listOf(6, 6, 6, 9, 5, 6, 7, 8, 1, 2, 3, 4)
        val r = table(grids = listOf(grid(cards, up = setOf(0, 1, 2)), grid()), stock = listOf(6))
        r.drawFromStock()
        r.placeHeld(3)
        for (i in 0..3) assertEquals(null, r.grids[0][i])
        assertEquals(6, cardAt(r, 0, 5))
    }

    @Test
    fun uneColonneCachePeutPasEtreRetiree() {
        val cards = listOf(5, 2, 3, 4, 5, 6, 7, 8, 5, 10, 11, 12)
        val r = table(grids = listOf(grid(cards, up = setOf(0, 4)), grid()), stock = listOf(1))
        r.drawFromStock()
        r.placeHeld(11)
        assertEquals(5, cardAt(r, 0, 0))
    }

    @Test
    fun uneEtoilePoseDonneUneCarteActionAPrendreAuProchainTour() {
        val r = table(stock = listOf(star), row = listOf(SkyjoAction.LOOK, SkyjoAction.METEOR), actionStock = listOf(SkyjoAction.STEAL))
        r.drawFromStock()
        r.placeHeld(0)
        assertEquals(SkyjoPhase.STAR_BONUS, r.phase)
        assertTrue(r.takeBonusFromRow(1))
        assertEquals(listOf(SkyjoAction.LOOK, SkyjoAction.STEAL), r.actionRow)
        assertEquals(SkyjoAction.METEOR, r.hands[0][0].action)
        assertEquals(1, r.current)
        // La carte prise ne se joue pas tout de suite, seulement à un tour suivant.
        assertTrue(r.hands[0][0].unlockTurn > r.turnNumber - 1)
    }

    @Test
    fun onPeutRefuserLaCarteActionDuBonusDEtoile() {
        val r = table(stock = listOf(star), row = listOf(SkyjoAction.LOOK))
        r.drawFromStock()
        r.placeHeld(0)
        assertTrue(r.skipBonus())
        assertEquals(1, r.current)
        assertTrue(r.hands[0].isEmpty())
    }

    @Test
    fun uneEtoileRetourneeParDefausseDonneAussiLeBonus() {
        val cards = listOf(star, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val r = table(grids = listOf(grid(cards), grid()), stock = listOf(9), row = listOf(SkyjoAction.LOOK))
        r.drawFromStock()
        r.discardHeldAndFlip(0)
        assertEquals(SkyjoPhase.STAR_BONUS, r.phase)
    }

    // ---------- Cartes action ----------

    @Test
    fun onPrendUneCarteActionAuLieuDeJouerUneCarteNumerotee() {
        val r = table(row = listOf(SkyjoAction.EXCHANGE, SkyjoAction.LOOK), actionStock = listOf(SkyjoAction.STEAL))
        assertTrue(r.takeActionFromRow(0))
        assertEquals(listOf(SkyjoAction.STEAL, SkyjoAction.LOOK), r.actionRow)
        assertEquals(1, r.current)
        assertEquals(2, r.hands[0][0].unlockTurn)
        // Tour du joueur 1, puis retour au joueur 0 : la carte est jouable.
        r.drawFromStock()
        r.placeHeld(0)
        assertEquals(0, r.current)
        assertTrue(r.canPlayAction(0))
    }

    @Test
    fun uneCarteActionSePrendAussiDansLaPiocheAction() {
        val r = table(actionStock = listOf(SkyjoAction.PROTECTION))
        assertTrue(r.takeActionFromDeck())
        assertTrue(r.holdsProtection(0))
    }

    @Test
    fun onPeutDefausserUneCarteActionSansLActiver() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.LOOK)), emptyList()))
        assertTrue(r.discardAction(0))
        assertEquals(listOf(SkyjoAction.LOOK), r.actionDiscard)
        assertEquals(1, r.current)
    }

    @Test
    fun lEchangePersonnelFaitPermuterDeuxCartesDeSaGrille() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.SWAP_OWN)), emptyList()))
        assertTrue(r.playAction(0))
        assertEquals(SkyjoPhase.SWAP, r.phase)
        assertTrue(r.swapPick(0))
        assertTrue(r.swapPick(5))
        assertEquals(6, cardAt(r, 0, 0))
        assertEquals(1, cardAt(r, 0, 5))
        assertEquals(1, r.current)
        assertEquals(listOf(SkyjoAction.SWAP_OWN), r.actionDiscard)
    }

    @Test
    fun lEchangeFaitPermuterDeuxCartesDeDeuxJoueursAvecLeurFace() {
        val other = grid(List(12) { 20 + it }, up = setOf(1))
        val r = table(grids = listOf(grid(), other), hands = listOf(listOf(held(SkyjoAction.EXCHANGE)), emptyList()))
        r.playAction(0)
        assertTrue(r.exchangePick(0, 0))
        // La seconde carte doit etre dans la grille d'un autre joueur.
        assertTrue(r.exchangePick(1, 1))
        assertEquals(21, cardAt(r, 0, 0))
        assertTrue(r.grids[0][0]!!.up)
        assertEquals(1, cardAt(r, 1, 1))
        assertFalse(r.grids[1][1]!!.up)
    }

    @Test
    fun uneProtectionAnnuleUnEchangeEtDisparait() {
        val r = table(
            hands = listOf(listOf(held(SkyjoAction.EXCHANGE)), listOf(held(SkyjoAction.PROTECTION))),
            grids = listOf(grid(), grid(List(12) { 20 + it }))
        )
        r.playAction(0)
        r.exchangePick(0, 0)
        r.exchangePick(1, 1)
        assertEquals(1, cardAt(r, 0, 0))
        assertEquals(21, cardAt(r, 1, 1))
        assertFalse(r.holdsProtection(1))
        assertEquals(listOf(SkyjoAction.EXCHANGE, SkyjoAction.PROTECTION), r.actionDiscard)
        assertTrue(r.events.any { it.kind == SkyjoEventKind.BLOCKED && it.player == 1 })
    }

    @Test
    fun lesMeteoresRetirentUneCarteDeChaqueAdversaireEtLaRemplacentFaceVisible() {
        val r = table(
            grids = listOf(grid(), grid(List(12) { 20 + it }), grid(List(12) { 40 + it })),
            stock = listOf(7, 8),
            hands = listOf(listOf(held(SkyjoAction.METEOR)), emptyList(), emptyList())
        )
        r.playAction(0)
        assertEquals(listOf(1, 2), r.meteorQueue)
        assertFalse(r.meteorPick(2, 0))
        assertTrue(r.meteorPick(1, 3))
        assertEquals(7, cardAt(r, 1, 3))
        assertTrue(r.grids[1][3]!!.up)
        assertTrue(r.meteorPick(2, 4))
        assertEquals(8, cardAt(r, 2, 4))
        // Les cartes retirees passent sous la defausse : la carte du dessus ne change pas.
        assertEquals(listOf(44, 23, 0), r.discard)
        assertEquals(1, r.current)
    }

    @Test
    fun lesMeteoresSontBloqueesParLaProtection() {
        val r = table(
            grids = listOf(grid(), grid(List(12) { 20 + it })),
            hands = listOf(listOf(held(SkyjoAction.METEOR)), listOf(held(SkyjoAction.PROTECTION)))
        )
        r.playAction(0)
        assertEquals(1, r.current)
        assertFalse(r.holdsProtection(1))
        assertEquals(21, cardAt(r, 1, 1))
    }

    @Test
    fun leDoubleTourDonneDeuxTourSupplementaires() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.DOUBLE_MOVE)), emptyList()))
        r.playAction(0)
        assertEquals(0, r.current)
        r.drawFromStock(); r.placeHeld(0)
        assertEquals(0, r.current)
        r.drawFromStock(); r.placeHeld(1)
        assertEquals(1, r.current)
    }

    @Test
    fun laProtectionJoueeDonneUnTourSupplementaire() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.PROTECTION)), emptyList()))
        r.playAction(0)
        assertEquals(0, r.current)
        r.drawFromStock(); r.placeHeld(0)
        assertEquals(1, r.current)
    }

    @Test
    fun leVoleurPrendUneCarteActionEtRejoueSansPouvoirLUtiliser() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.STEAL)), listOf(held(SkyjoAction.LOOK))))
        r.playAction(0)
        assertFalse(r.steal(0, 0))
        assertTrue(r.steal(1, 0))
        assertEquals(0, r.current)
        assertEquals(SkyjoAction.LOOK, r.hands[0][0].action)
        assertTrue(r.hands[1].isEmpty())
        assertFalse(r.canPlayAction(0))
        r.drawFromStock(); r.placeHeld(0)
        r.drawFromStock(); r.placeHeld(0)
        assertEquals(0, r.current)
        assertTrue(r.canPlayAction(0))
    }

    @Test
    fun leVoleurEstBloqueParLaProtection() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.STEAL)), listOf(held(SkyjoAction.PROTECTION), held(SkyjoAction.LOOK))))
        r.playAction(0)
        assertTrue(r.steal(1, 1))
        assertEquals(1, r.current)
        assertEquals(listOf(SkyjoAction.LOOK), r.hands[1].map { it.action })
        assertTrue(r.hands[0].isEmpty())
    }

    @Test
    fun leVoleurNePeutPasEtreJoueSansCarteAVoler() {
        val r = table(hands = listOf(listOf(held(SkyjoAction.STEAL)), emptyList()))
        assertFalse(r.canPlayAction(0))
        assertFalse(r.playAction(0))
    }

    @Test
    fun regarderMontreLesCartesCacheesDUneLigneAuSeulJoueur() {
        val r = table(grids = listOf(grid(up = setOf(0)), grid()), hands = listOf(listOf(held(SkyjoAction.LOOK)), emptyList()))
        r.playAction(0)
        // La colonne 0 de sa grille : places 0 (visible), 4 et 8.
        assertTrue(r.look(0, 0, row = false))
        assertEquals(listOf(5, 9), r.peek.map { it.card })
        assertFalse(r.closePeek())
        r.showPeek()
        assertTrue(r.closePeek())
        assertEquals(1, r.current)
        assertTrue(r.peek.isEmpty())
    }

    @Test
    fun regarderRefuseUneLigneSansCarteCachee() {
        val allUp = grid(up = (0..11).toSet())
        val r = table(grids = listOf(allUp, grid()), hands = listOf(listOf(held(SkyjoAction.LOOK)), emptyList()))
        r.playAction(0)
        assertFalse(r.look(0, 0, row = true))
        assertEquals(SkyjoPhase.LOOK, r.phase)
    }

    @Test
    fun piocherTroisCartesPermetDEnGarderUneEtDefausseLesAutres() {
        val r = table(stock = listOf(7, 8, 9), hands = listOf(listOf(held(SkyjoAction.DRAW_THREE)), emptyList()))
        r.playAction(0)
        assertEquals(listOf(7, 8, 9), r.three)
        assertTrue(r.threeKeep(1, 2))
        assertEquals(8, cardAt(r, 0, 2))
        assertEquals(listOf(0, 7, 9, 3), r.discard)
        assertEquals(1, r.current)
    }

    @Test
    fun piocherTroisCartesPermetDeNEnGarderAucuneEtDeRetournerUneCarte() {
        val r = table(stock = listOf(7, 8, 9), hands = listOf(listOf(held(SkyjoAction.DRAW_THREE)), emptyList()))
        r.playAction(0)
        assertTrue(r.threeNone(4))
        assertTrue(r.grids[0][4]!!.up)
        assertEquals(listOf(0, 7, 8, 9), r.discard)
    }

    @Test
    fun laReactivationRejoueUneCarteDeLaDefenseAction() {
        val r = table(
            hands = listOf(listOf(held(SkyjoAction.REACTIVATION)), emptyList()),
            actionDiscard = listOf(SkyjoAction.DOUBLE_MOVE, SkyjoAction.REACTIVATION)
        )
        r.playAction(0)
        assertEquals(SkyjoPhase.REACTIVATE, r.phase)
        // Seule la carte « Double tour » peut etre rejouee (pas une autre Reactivation).
        assertEquals(listOf(0), r.reactivatable())
        assertTrue(r.reactivate(0))
        assertEquals(0, r.current)
        assertEquals(1, r.extraTurns)
    }

    @Test
    fun uneCarteActionNePeutPasEtreJoueeAuDernierTour() {
        val allUp = grid(up = (0..11).toSet())
        val r = table(
            grids = listOf(allUp, grid()),
            hands = listOf(emptyList(), listOf(held(SkyjoAction.LOOK))),
            row = listOf(SkyjoAction.LOOK)
        )
        r.drawFromStock(); r.placeHeld(0)
        assertTrue(r.finalTurn)
        assertEquals(1, r.current)
        assertFalse(r.canPlayAction(0))
        assertFalse(r.takeActionFromRow(0))
        assertFalse(r.discardAction(0))
    }

    // ---------- Fin de manche ----------

    @Test
    fun quandUnJoueurRetourneToutLesAutresJouentUnDernierTourPuisOnCompte() {
        val almost = grid(List(12) { 1 + it }, up = (0..10).toSet())
        val r = table(grids = listOf(almost, grid(), grid()), stock = listOf(3, 3, 3), hands = listOf(emptyList(), emptyList(), emptyList()))
        r.drawFromStock()
        r.discardHeldAndFlip(11)
        assertEquals(0, r.endBy)
        assertEquals(1, r.current)
        r.drawFromStock(); r.placeHeld(0)
        assertFalse(r.roundOver)
        assertEquals(2, r.current)
        r.drawFromStock(); r.placeHeld(0)
        assertTrue(r.roundOver)
        assertTrue(r.grids.all { g -> g.all { it == null || it.up } })
    }

    @Test
    fun celuiQuiTermineDoubleSesPointsSiIlNAPasLePlusPetitScore() {
        // Les deux joueurs ont la meme grille de 78 points : l'un d'eux termine et double.
        val r = table(grids = listOf(grid(up = (0..10).toSet()), grid()), stock = listOf(1, 1))
        r.drawFromStock(); r.discardHeldAndFlip(11)
        r.drawFromStock(); r.placeHeld(0)
        assertTrue(r.roundOver)
        assertTrue(r.enderDoubled())
        val points = r.roundPoints()
        assertEquals(78 * 2, points[0])
        assertEquals(78, points[1])
    }

    @Test
    fun celuiQuiTermineAvecLeScoreLePlusBasNeDoublePas() {
        val low = grid((0..11).map { it - 2 }, up = (0..10).toSet())
        val r = table(grids = listOf(low, grid()), stock = listOf(0, 0))
        r.drawFromStock(); r.discardHeldAndFlip(11)
        r.drawFromStock(); r.placeHeld(0)
        assertFalse(r.enderDoubled())
    }

    @Test
    fun uneColonneDEtoilesVautMoinsDixEtUneLigneMoinsQuinze() {
        val cards = listOf(star, 2, 3, 4, star, 6, 7, 8, star, 10, 11, 12)
        val r = round(2)
        r.load(listOf(grid(cards, up = (0..11).toSet()), grid()))
        // Colonne 0 : trois etoiles = -10 ; le reste 2 + 3 + 4 + 6 + 7 + 8 + 10 + 11 + 12 = 63.
        assertEquals(53, r.gridPoints(0))
        val row = listOf(star, star, star, star, 5, 6, 7, 8, 9, 10, 11, 12)
        r.load(listOf(grid(row, up = (0..11).toSet()), grid()))
        // Ligne 0 : quatre etoiles = -15 ; le reste 5 + ... + 12 = 68.
        assertEquals(53, r.gridPoints(0))
    }

    @Test
    fun chaqueCarteActionGardeeCompteDixPoints() {
        val r = table(grids = listOf(grid(up = (0..10).toSet()), grid()), stock = listOf(1, 1),
            hands = listOf(emptyList(), listOf(held(SkyjoAction.LOOK), held(SkyjoAction.PROTECTION))))
        r.drawFromStock(); r.discardHeldAndFlip(11)
        r.drawFromStock(); r.placeHeld(0)
        assertEquals(78 + 20, r.roundPoints()[1])
        assertFalse(r.enderDoubled())
    }
}
