package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Qwixx jouable tour par tour : croix permises, cadenas, pénalités, fin de partie, inscription au tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class QwixxRoundTest {

    /** Une partie dont les dés sortent dans l'ordre donné (six dés par lancer) ; ensuite, toujours le dernier. */
    private fun game(players: Int, vararg dice: Int): QwixxRound {
        val queue = ArrayDeque(dice.toList())
        var last = 3
        return QwixxRound(players) { if (queue.isNotEmpty()) queue.removeFirst().also { last = it } else last }
    }

    private fun qwixxRules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_qwixx" }

    /** Masque de croix pour les nombres [numbers] (bit n − 2), sans le cadenas. */
    private fun mask(vararg numbers: Int): Int = numbers.fold(0) { acc, n -> acc or (1 shl (n - 2)) }

    private val lockBit = 1 shl 11

    /** Remplace le plateau de [player] : [masks] pour les quatre rangées (voir [QwixxRound.snapshot]). */
    private fun withBoard(round: QwixxRound, player: Int, vararg masks: Int) {
        val data = round.snapshot().toMutableList()
        val start = 6 + player * 6 + 1
        masks.forEachIndexed { row, value -> data[start + row] = value }
        round.restoreFrom(data)
    }

    // ---------- Règles de coche ----------

    @Test
    fun onCocheToujoursVersLaDroiteSurRougeEtJauneEtVersLaGaucheSurVertEtBleu() {
        val red = mask(4, 7)
        assertTrue(QwixxScoring.canMark(red, 0, 8))
        assertFalse(QwixxScoring.canMark(red, 0, 7))
        assertFalse(QwixxScoring.canMark(red, 0, 5))
        val green = mask(11, 8)
        assertTrue(QwixxScoring.canMark(green, 2, 7))
        assertFalse(QwixxScoring.canMark(green, 2, 8))
        assertFalse(QwixxScoring.canMark(green, 2, 10))
        assertTrue(QwixxScoring.canMark(0, 3, 12))
        assertTrue(QwixxScoring.canMark(0, 1, 2))
    }

    @Test
    fun ilFautCinqCroixPourCocherLeDernierNombreEtFermerLaRangee() {
        assertFalse(QwixxScoring.canMark(mask(2, 3, 4, 5), 0, 12))
        assertTrue(QwixxScoring.canMark(mask(2, 3, 4, 5, 6), 0, 12))
        assertFalse(QwixxScoring.canMark(mask(12, 11, 10, 9), 2, 2))
        assertTrue(QwixxScoring.canMark(mask(12, 11, 10, 9, 8), 3, 2))
        // Le cadenas compte comme une croix de plus ; une rangée fermée par son cadenas n'accepte plus rien.
        val closed = QwixxScoring.mark(mask(2, 3, 4, 5, 6), 0, 12)
        assertTrue(QwixxScoring.isLocked(closed))
        assertEquals(7, QwixxScoring.crosses(closed))
        assertFalse(QwixxScoring.canMark(closed, 0, 12))
        assertEquals(mask(2, 3, 4, 5, 6), QwixxScoring.unmark(closed, 0, 12))
    }

    @Test
    fun lesPointsSontLaSommeDe1AN() {
        assertEquals(listOf(0, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66, 78), (0..12).map { QwixxScoring.points(it) })
    }

    // ---------- Un tour ----------

    @Test
    fun chacunCocheLesDesBlancsPuisLeJoueurActifCocheUnDeCouleur() {
        // Dés blancs 3 + 4 = 7 ; rouge 1, jaune 2, vert 5, bleu 6.
        val round = game(2, 3, 4, 1, 2, 5, 6)
        assertEquals(QwixxStep.ROLL, round.step)
        round.roll()
        assertEquals(QwixxStep.WHITE, round.step)
        assertEquals(7, round.whiteSum)
        assertEquals(listOf(0, 1, 2, 3), round.whiteOptions(0))

        round.markWhite(0, 0)
        assertEquals(QwixxStep.WHITE, round.step)
        round.passWhite(1)
        assertEquals(QwixxStep.COLOR, round.step)
        assertEquals(1, round.crossesOf(0, 0))

        // Rouge : 3 + 1 = 4 et 4 + 1 = 5 sont à gauche du 7 ; les autres couleurs offrent chacune deux totaux.
        val options = round.colorOptions()
        assertFalse(options.any { it.row == 0 })
        assertEquals(listOf(QwixxMove(1, 5), QwixxMove(1, 6), QwixxMove(2, 8), QwixxMove(2, 9), QwixxMove(3, 9), QwixxMove(3, 10)), options)

        round.markColor(1, 6)
        assertEquals(QwixxStep.END, round.step)
        assertFalse(round.penaltyPending)
        assertFalse(round.endTurn())
        assertEquals(1, round.current)
        assertEquals(QwixxStep.ROLL, round.step)
        assertEquals(listOf(1, 1), listOf(round.crossesOf(0, 0), round.crossesOf(0, 1)))
        assertEquals(2, round.scoreOf(0))
    }

    @Test
    fun uneCroixNonPermiseEstRefusee() {
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.markWhite(0, 0)
        // Déjà choisi : une deuxième croix sur les dés blancs est ignorée.
        round.markWhite(0, 1)
        assertEquals(0, round.crossesOf(0, 1))
        round.passWhite(1)
        round.markColor(0, 4)
        assertEquals(QwixxStep.COLOR, round.step)
        assertEquals(1, round.crossesOf(0, 0))
    }

    @Test
    fun leJoueurActifQuiNeCocheRienPrendUnePenalite() {
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.passWhite(0)
        round.passWhite(1)
        round.passColor()
        assertEquals(QwixxStep.END, round.step)
        assertTrue(round.penaltyPending)
        round.endTurn()
        assertEquals(listOf(1, 0), round.penalties)
        assertEquals(-5, round.scoreOf(0))
    }

    @Test
    fun cocherAvecLesDesBlancsEvitePenaliteMemeSansCroixDeCouleur() {
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.markWhite(0, 0)
        round.passWhite(1)
        round.passColor()
        assertFalse(round.penaltyPending)
        round.endTurn()
        assertEquals(listOf(0, 0), round.penalties)
    }

    @Test
    fun onPeutDefaireUnChoixTantQueLeDeCouleurNEstPasChoisi() {
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.markWhite(0, 0)
        round.cancelWhite(0)
        assertEquals(0, round.crossesOf(0, 0))
        assertEquals(QwixxStep.WHITE, round.step)
        round.markWhite(0, 2)
        round.passWhite(1)
        round.markColor(1, 6)
        // Le joueur actif a choisi : les choix sur les dés blancs ne se défont plus avant d'avoir défait celui-ci.
        round.cancelWhite(0)
        assertEquals(1, round.crossesOf(0, 2))
        round.cancelColor()
        assertEquals(0, round.crossesOf(0, 1))
        round.cancelWhite(0)
        assertEquals(0, round.crossesOf(0, 2))
    }

    @Test
    fun unJoueurSansCroixPossiblePasseTout() {
        // Le joueur 1 a déjà coché le 12 partout ou presque : rien à faire avec 7.
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        withBoard(round, 1, mask(8), mask(8), mask(6), mask(6))
        assertEquals(emptyList<Int>(), round.whiteOptions(1))
        round.markWhite(0, 0)
        // Le joueur 1 n'a rien à choisir : l'étape passe directement au dé de couleur.
        assertEquals(QwixxStep.COLOR, round.step)
    }

    // ---------- Cadenas et fin de partie ----------

    @Test
    fun deuxRangeesFermeesTerminentLaPartieEtLesPointsSontInscrits() {
        // Dés blancs 6 + 6 = 12 : les deux joueurs ferment une rangée en même temps (rouge et jaune).
        val round = game(2, 6, 6, 1, 1, 1, 1)
        round.roll()
        withBoard(round, 0, mask(2, 3, 4, 5, 6), 0, 0, 0)
        val data = round.snapshot().toMutableList()
        // Joueur 1 : jaune avec cinq croix (rangée 1).
        data[6 + 6 + 1 + 1] = mask(2, 3, 4, 5, 6)
        round.restoreFrom(data)

        round.markWhite(0, 0)
        round.markWhite(1, 1)
        assertTrue(QwixxScoring.isLocked(round.boards[0][0]))
        assertTrue(QwixxScoring.isLocked(round.boards[1][1]))
        // Rien n'est fermé pour les autres avant la fin du tour : le 12 rouge reste permis à qui a ses cinq croix.
        assertFalse(round.isRowClosed(0))
        round.passColor()
        assertEquals(QwixxStep.END, round.step)
        assertTrue(round.endTurn())
        assertTrue(round.over)
        assertEquals(QwixxStep.OVER, round.step)
        // 6 croix (cinq + le 12) + le cadenas = 7 croix = 28 points pour chacun.
        assertEquals(28, round.scoreOf(0))
        assertEquals(28, round.scoreOf(1))

        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), qwixxRules()) }
        assertFalse(vm.isGameOver())
        round.writeTo(vm)
        assertTrue(vm.isGameOver())
        assertEquals(28, vm.totalFor(0))
        assertEquals(28, vm.totalFor(1))
    }

    @Test
    fun uneRangeeFermeeAUnTourPrecedentNEstPlusPermise() {
        val round = game(2, 6, 6, 1, 1, 1, 1)
        round.roll()
        withBoard(round, 0, mask(2, 3, 4, 5, 6), 0, 0, 0)
        val data = round.snapshot().toMutableList()
        data[6 + 6 + 1] = mask(2, 3, 4, 5, 6)
        round.restoreFrom(data)

        round.markWhite(0, 0)
        // Le joueur 1 ferme la même rangée au même moment : permis.
        assertTrue(0 in round.whiteOptions(1))
        round.markWhite(1, 0)
        assertEquals(listOf(true, true), round.boards.map { QwixxScoring.isLocked(it[0]) })
        round.passColor()
        assertFalse(round.endTurn())
        assertTrue(round.isRowClosed(0))
        // Tour suivant : la rangée est fermée pour tous.
        round.roll()
        assertFalse(0 in round.whiteOptions(0))
        assertFalse(round.colorOptions().any { it.row == 0 })
    }

    @Test
    fun quatrePenalitesTerminentLaPartie() {
        // Tous les dés à 3 : chacun a toujours une croix possible, il faut donc passer à la main.
        val round = game(2)
        var turns = 0
        while (!round.over && turns < 20) {
            round.roll()
            round.passWhite(0)
            round.passWhite(1)
            round.passColor()
            round.endTurn()
            turns += 1
        }
        // Les joueurs jouent en alternance : le joueur 0 prend sa quatrième pénalité au septième tour.
        assertTrue(round.over)
        assertEquals(7, turns)
        assertEquals(listOf(4, 3), round.penalties)
        assertEquals(-20, round.scoreOf(0))
    }

    // ---------- Sauvegarde et partage ----------

    @Test
    fun lEtatSeRetabliTelQuel() {
        val round = game(3, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.markWhite(0, 0)
        round.passWhite(1)
        round.markWhite(2, 3)
        val copy = QwixxRound(3)
        copy.restoreFrom(round.snapshot())
        assertEquals(round.snapshot(), copy.snapshot())
        assertEquals(round.step, copy.step)
        assertEquals(round.boards, copy.boards)
    }

    @Test
    fun uneListeInvalideRemetLaPartieAuDepart() {
        val round = game(2, 3, 4, 1, 2, 5, 6)
        round.roll()
        round.markWhite(0, 0)
        round.restoreFrom(listOf(1, 2, 9))
        assertEquals(QwixxStep.ROLL, round.step)
        assertEquals(0, round.crossesOf(0, 0))
        assertFalse(round.hasMarks)
        // Un plateau pour un autre nombre de joueurs est refusé.
        round.restoreFrom(QwixxRound(3).snapshot())
        assertEquals(listOf(0, 0), round.penalties)
    }

    @Test
    fun deuxTelephonesQuiCochentEnMemeTempsGardentChacunLeurPart() {
        val a = game(3, 3, 4, 1, 2, 5, 6)
        a.roll()
        val b = QwixxRound(3)
        b.restoreFrom(a.snapshot())
        // Chacun coche pour un joueur différent, au même instant.
        a.markWhite(0, 0)
        b.markWhite(1, 3)
        val fromA = a.snapshot()
        val fromB = b.snapshot()
        a.mergeFrom(fromB)
        b.mergeFrom(fromA)
        assertEquals(a.snapshot(), b.snapshot())
        assertEquals(0, a.whiteChoices[0])
        assertEquals(3, a.whiteChoices[1])
        assertEquals(1, a.crossesOf(0, 0))
        assertEquals(1, a.crossesOf(1, 3))
    }

    @Test
    fun unChoixDefaitNeRevientPasAvecUnEtatEnRetard() {
        val a = game(2, 3, 4, 1, 2, 5, 6)
        a.roll()
        a.markWhite(0, 0)
        val stale = a.snapshot()
        a.cancelWhite(0)
        a.mergeFrom(stale)
        assertEquals(QwixxScoring.NO_CHOICE, a.whiteChoices[0])
        assertEquals(0, a.crossesOf(0, 0))
    }

    @Test
    fun unTourPlusAvanceLEmporteEnEntier() {
        val a = game(2, 3, 4, 1, 2, 5, 6)
        a.roll()
        val stale = a.snapshot()
        a.passWhite(0)
        a.passWhite(1)
        a.passColor()
        a.endTurn()
        val b = QwixxRound(2)
        b.restoreFrom(stale)
        b.mergeFrom(a.snapshot())
        assertEquals(a.snapshot(), b.snapshot())
        assertEquals(1, b.current)
        a.mergeFrom(stale)
        assertEquals(1, a.current)
    }

    @Test
    fun leQwixxEstUnJeuJouableQuiSeJoueSurLaPageDesDes() {
        val qwixx = qwixxRules()
        assertTrue(qwixx.isPlayableGame())
        assertTrue(qwixx.isDiceOnly())
        assertEquals(6, DiceSetups.forGame("builtin_qwixx")!!.diceCount)
    }
}
