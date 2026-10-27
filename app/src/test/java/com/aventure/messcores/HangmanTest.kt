package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les règles du Pendu : le mot, les lettres, les erreurs, la fin de manche, et la partie sur un seul téléphone. */
class HangmanTest {

    private val settings = HangmanSettings(maxMistakes = 7)

    /** Les lettres proposées une à une pour [word] (chaque proposition reçoit sa réponse). */
    private fun play(word: String, letters: String, settings: HangmanSettings = this.settings): List<HangmanGuess> {
        var guesses = emptyList<HangmanGuess>()
        for (letter in letters) guesses = guesses + Hangman.resolve(word, settings, guesses, letter)!!
        return guesses
    }

    // ---------- Le mot ----------

    @Test
    fun leMotSeNormaliseSansAccentsNiMajuscules() {
        assertEquals("ETE", Hangman.normalize("été"))
        assertEquals("COEUR", Hangman.normalize("  cœur "))
        assertEquals("L'OEIL", Hangman.normalize("l’œil"))
        assertEquals("POMME DE TERRE", Hangman.normalize("pomme   de\tterre"))
        assertEquals("NOEL", Hangman.normalize("Noël"))
        assertEquals("FACADE", Hangman.normalize("façade"))
    }

    @Test
    fun leMotDoitEtreJouable() {
        assertEquals("PORTE-CLES", Hangman.parseWord("porte-clés"))
        assertEquals("POMME DE TERRE", Hangman.parseWord("pomme de terre"))
        assertEquals("AB", Hangman.parseWord("ab"))
        // Trop court, trop long, chiffres, symboles, séparateur au bord ou doublé : refusés.
        assertNull(Hangman.parseWord("a"))
        assertNull(Hangman.parseWord(""))
        assertNull(Hangman.parseWord("   "))
        assertNull(Hangman.parseWord("a".repeat(Hangman.MAX_LETTERS + 1)))
        assertNotNull(Hangman.parseWord("a".repeat(Hangman.MAX_LETTERS)))
        assertNull(Hangman.parseWord("abc123"))
        assertNull(Hangman.parseWord("ab!"))
        assertNull(Hangman.parseWord("-ab"))
        assertNull(Hangman.parseWord("ab-"))
        assertNull(Hangman.parseWord("ab--cd"))
        assertNull(Hangman.parseWord("ab -cd"))
    }

    @Test
    fun laFormeNeMontreQueLesBlancsEtLesSeparateurs() {
        assertEquals("_____ __ _____", Hangman.shape("POMME DE TERRE"))
        assertEquals("_____-____", Hangman.shape("PORTE-CLES"))
        assertEquals("_'____", Hangman.shape("L'OEIL"))
        assertTrue(Hangman.isValidShape("_____ __ _____"))
        assertFalse(Hangman.isValidShape("ABC"))
        assertFalse(Hangman.isValidShape("_"))
        assertFalse(Hangman.isValidShape("_ _"))
        assertFalse(Hangman.isValidShape("-__"))
    }

    // ---------- Les lettres ----------

    @Test
    fun uneLettreDonneSesPlacesDansLeMot() {
        val answer = Hangman.resolve("POMME", settings, emptyList(), 'M')!!
        assertEquals(listOf(2, 3), answer.positions)
        assertFalse(answer.isMistake)
        val miss = Hangman.resolve("POMME", settings, emptyList(), 'Z')!!
        assertTrue(miss.positions.isEmpty())
        assertTrue(miss.isMistake)
    }

    @Test
    fun uneLettreDejaProposeeOuInvalideEstRefusee() {
        val guesses = play("POMME", "P")
        assertNull(Hangman.resolve("POMME", settings, guesses, 'P'))
        assertNull(Hangman.resolve("POMME", settings, guesses, 'p'))
        assertNull(Hangman.resolve("POMME", settings, guesses, '1'))
        assertNull(Hangman.resolve("POMME", settings, guesses, ' '))
    }

    @Test
    fun leMotSAfficheAuFurEtAMesure() {
        val shape = Hangman.shape("POMME DE TERRE")
        assertEquals("_____ __ _____", Hangman.masked(shape, emptyList()))
        val guesses = play("POMME DE TERRE", "ER")
        assertEquals("____E _E _ERRE", Hangman.masked(shape, guesses))
        assertEquals(0, Hangman.mistakes(guesses))
    }

    @Test
    fun lesErreursSeComptentEtLeBonhommeEstPendu() {
        val guesses = play("AB", "XYZWVUT")
        assertEquals(7, Hangman.mistakes(guesses))
        assertEquals(0, Hangman.mistakesLeft(settings, guesses))
        assertEquals(HangmanOutcome.LOST, Hangman.outcome("__", settings, guesses))
        // La manche est finie : plus aucune lettre n'est acceptée.
        assertNull(Hangman.resolve("AB", settings, guesses, 'A'))
    }

    @Test
    fun leMotTrouveGagneLaManche() {
        val guesses = play("POMME", "POME")
        assertTrue(Hangman.isSolved("_____", guesses))
        assertEquals(HangmanOutcome.WON, Hangman.outcome("_____", settings, guesses))
        assertNull(Hangman.resolve("POMME", settings, guesses, 'Z'))
    }

    @Test
    fun trouverLeMotAuDernierEssaiLEmportesurLaPendaison() {
        // Six erreurs sur sept permises, puis la dernière lettre trouvée : gagné.
        val guesses = play("AB", "XYZWVUAB")
        assertEquals(6, Hangman.mistakes(guesses))
        assertEquals(HangmanOutcome.WON, Hangman.outcome("__", settings, guesses))
    }

    @Test
    fun leDessinSeCompleteExactementALaDerniereErreur() {
        for (max in HangmanSettings.MISTAKES_RANGE) {
            val s = HangmanSettings(max)
            assertEquals(0, Hangman.drawingParts(s, 0))
            assertEquals(Hangman.DRAWING_PARTS, Hangman.drawingParts(s, max))
            // Une pièce de plus à chaque erreur au moins tous les (10 / max) pas, jamais de recul, jamais complet avant la fin.
            var previous = 0
            for (m in 1 until max) {
                val parts = Hangman.drawingParts(s, m)
                assertTrue("max=$max m=$m", parts >= previous && parts in 1 until Hangman.DRAWING_PARTS)
                previous = parts
            }
            assertTrue(Hangman.drawingParts(s, 1) >= 1)
        }
    }

    @Test
    fun lesReglagesRestentDansLesLimites() {
        assertEquals(4, HangmanSettings(1).coerced().maxMistakes)
        assertEquals(10, HangmanSettings(99).coerced().maxMistakes)
        assertEquals(7, HangmanSettings().coerced().maxMistakes)
    }

    // ---------- L'honnêteté de celui qui a le mot ----------

    @Test
    fun desReponsesHonnetesSontCoherentesAvecLeMot() {
        val guesses = play("POMME", "MZP")
        assertTrue(Hangman.isConsistent("POMME", "_____", guesses))
    }

    @Test
    fun desReponsesTruqueesOuUnAutreMotSontDetectes() {
        val shape = "_____"
        // Il prétend que le M n'est pas dans le mot.
        assertFalse(Hangman.isConsistent("POMME", shape, listOf(HangmanGuess('M', emptyList()))))
        // Il donne de mauvaises places.
        assertFalse(Hangman.isConsistent("POMME", shape, listOf(HangmanGuess('M', listOf(0)))))
        // Il dévoile un autre mot que celui annoncé (autre forme).
        assertFalse(Hangman.isConsistent("POMMES", shape, play("POMMES", "M")))
        // Même forme mais les réponses ne viennent pas de ce mot.
        assertFalse(Hangman.isConsistent("POIRE", shape, play("POMME", "M")))
        // Un mot injouable.
        assertFalse(Hangman.isConsistent("PO1ME", shape, emptyList()))
        // Une lettre proposée deux fois.
        assertFalse(Hangman.isConsistent("POMME", shape, listOf(HangmanGuess('Z', emptyList()), HangmanGuess('Z', emptyList()))))
    }

    // ---------- Une séance sur un seul téléphone ----------

    @Test
    fun laManchePassePuisLesRolesSEchangent() {
        var game = Hangman.newGame(listOf("Anna", "Ben"), settings, firstSetter = 0)
        assertEquals(1, game.guesser)
        assertNull(Hangman.guess(game, 'A'))
        assertNull(Hangman.nextRound(game))
        assertNull(Hangman.withWord(game, "x"))
        game = Hangman.withWord(game, "Chat")!!
        assertEquals("CHAT", game.word)
        // Le mot ne se change pas en cours de manche.
        assertNull(Hangman.withWord(game, "chien"))
        for (letter in "CHAT") game = Hangman.guess(game, letter)!!
        assertEquals(HangmanOutcome.WON, game.outcome)
        assertEquals(1, game.roundWinner)
        assertEquals(listOf(0, 1), game.wins)
        game = Hangman.nextRound(game)!!
        assertEquals(1, game.setter)
        assertEquals(0, game.guesser)
        assertEquals(2, game.round)
        assertNull(game.word)
        assertTrue(game.guesses.isEmpty())
        // Le score reste.
        assertEquals(listOf(0, 1), game.wins)
    }

    @Test
    fun leBonhommePenduDonneLePointACeluiQuiAvaitLeMot() {
        var game = Hangman.withWord(Hangman.newGame(listOf("Anna", "Ben"), settings, firstSetter = 1), "ab")!!
        for (letter in "XYZWVUT") game = Hangman.guess(game, letter)!!
        assertEquals(HangmanOutcome.LOST, game.outcome)
        assertEquals(1, game.roundWinner)
        assertEquals(listOf(0, 1), game.wins)
        assertNull(Hangman.guess(game, 'A'))
    }

    @Test
    fun leViewModelFaitPasserLeTelephoneEntreLesJoueurs() {
        val viewModel = HangmanViewModel()
        viewModel.open(GameRules(id = HANGMAN_ID, name = "Pendu"), listOf("Anna", "Ben"))
        viewModel.updateSettings(HangmanSettings(99))
        assertEquals(10, viewModel.settings.maxMistakes)
        viewModel.start(Random(0))
        val setter = viewModel.game!!.setter
        // Le téléphone va d'abord à celui qui choisit le mot ; rien ne peut être joué avant qu'il l'ait en main.
        assertEquals(setter, viewModel.handoffTo)
        assertFalse(viewModel.submitWord("chat"))
        viewModel.takePhone()
        assertFalse(viewModel.submitWord("c"))
        assertNull(viewModel.game!!.word)
        assertTrue(viewModel.submitWord("chat"))
        // Puis à celui qui devine.
        assertEquals(1 - setter, viewModel.handoffTo)
        viewModel.guess('C')
        assertTrue(viewModel.game!!.guesses.isEmpty())
        viewModel.takePhone()
        viewModel.guess('C')
        assertEquals(1, viewModel.game!!.guesses.size)
        for (letter in "HAT") viewModel.guess(letter)
        assertEquals(HangmanOutcome.WON, viewModel.game!!.outcome)
        assertNull(viewModel.handoffTo)
        // Manche suivante : les rôles s'échangent et le téléphone passe au nouveau choisisseur.
        viewModel.nextRound()
        assertEquals(1 - setter, viewModel.game!!.setter)
        assertEquals(1 - setter, viewModel.handoffTo)
        viewModel.close()
        assertNull(viewModel.game)
    }
}
