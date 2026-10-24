package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MastermindTest {

    private val settings = MastermindSettings(codeLength = 4, colorCount = 6, maxAttempts = 6)
    private val secret = listOf(0, 1, 2, 3)

    /** Partie à [names] joueurs, toutes les suites secrètes étant déjà saisies (phase de recherche). */
    private fun playing(vararg names: String): MastermindState {
        var state = Mastermind.newGame(names.toList(), settings)
        repeat(names.size) { state = Mastermind.withSecret(state, secret)!! }
        return state
    }

    /** Un essai qui n'est jamais la bonne suite. */
    private val wrong = listOf(5, 5, 5, 5)

    /** Cherche la suite en cours : [attempts] essais, le dernier étant le bon ; puis passe à la suivante. */
    private fun solveIn(state: MastermindState, attempts: Int): MastermindState {
        var s = state
        repeat(attempts - 1) { s = Mastermind.withGuess(s, wrong)!! }
        s = Mastermind.withGuess(s, secret)!!
        return Mastermind.nextTarget(s)
    }

    /** Épuise tous les essais sans trouver la suite en cours ; puis passe à la suivante. */
    private fun failTarget(state: MastermindState): MastermindState {
        var s = state
        repeat(settings.maxAttempts) { s = Mastermind.withGuess(s, wrong)!! }
        return Mastermind.nextTarget(s)
    }

    // ---------- Réponse à un essai ----------

    @Test
    fun laReponseCompteLesBienPlacesPuisLesMalPlaces() {
        assertEquals(MastermindFeedback(4, 0), Mastermind.feedback(listOf(1, 2, 3, 4), listOf(1, 2, 3, 4)))
        assertEquals(MastermindFeedback(2, 2), Mastermind.feedback(listOf(1, 2, 3, 4), listOf(1, 3, 2, 4)))
        assertEquals(MastermindFeedback(0, 4), Mastermind.feedback(listOf(1, 2, 3, 4), listOf(4, 3, 2, 1)))
        assertEquals(MastermindFeedback(0, 0), Mastermind.feedback(listOf(1, 2, 3, 4), listOf(5, 5, 5, 5)))
    }

    @Test
    fun uneCouleurRepeteeNeCompteQueAutantQueDansLaSuite() {
        // Suite 1 1 2 2, essai 1 2 1 1 : un pion bien placé ; parmi les autres, un 1 et un 2 sont mal placés.
        assertEquals(MastermindFeedback(1, 2), Mastermind.feedback(listOf(1, 1, 2, 2), listOf(1, 2, 1, 1)))
        // Trois pions rouges dans l'essai, un seul dans la suite, bien placé : aucun autre ne compte.
        assertEquals(MastermindFeedback(1, 0), Mastermind.feedback(listOf(0, 1, 2, 3), listOf(0, 0, 0, 5)))
        // Le même rouge, mal placé : un seul compte, les deux autres sont en trop.
        assertEquals(MastermindFeedback(0, 1), Mastermind.feedback(listOf(0, 1, 2, 3), listOf(5, 0, 0, 0)))
    }

    // ---------- Réglages ----------

    @Test
    fun lesReglagesRestentDansLeursLimites() {
        val coerced = MastermindSettings(codeLength = 1, colorCount = 20, maxAttempts = 99).coerced()
        assertEquals(MastermindSettings.LENGTH_RANGE.first, coerced.codeLength)
        assertEquals(MastermindSettings.COLOR_RANGE.last, coerced.colorCount)
        assertEquals(MastermindSettings.ATTEMPTS_RANGE.last, coerced.maxAttempts)
        assertEquals(MastermindSettings(4, 6, 10), MastermindSettings())
    }

    // ---------- Saisie des suites ----------

    @Test
    fun chaqueJoueurSaisitSaSuitePuisLaRechercheCommence() {
        var state = Mastermind.newGame(listOf("A", "B", "C"), settings)
        assertEquals(MastermindPhase.SECRETS, state.phase)
        assertEquals(0, state.secretTurn)

        state = Mastermind.withSecret(state, listOf(0, 1, 2, 3))!!
        assertEquals(1, state.secretTurn)
        state = Mastermind.withSecret(state, listOf(1, 1, 1, 1))!!
        assertEquals(MastermindPhase.SECRETS, state.phase)
        state = Mastermind.withSecret(state, listOf(5, 4, 3, 2))!!

        assertEquals(MastermindPhase.PLAYING, state.phase)
        assertEquals(0, state.target)
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(1, 1, 1, 1), listOf(5, 4, 3, 2)), state.secrets)
    }

    @Test
    fun uneSuiteInvalideEstRefusee() {
        val state = Mastermind.newGame(listOf("A", "B"), settings)
        assertNull(Mastermind.withSecret(state, listOf(0, 1, 2)))
        assertNull(Mastermind.withSecret(state, listOf(0, 1, 2, 6)))
        assertNull(Mastermind.withSecret(state, listOf(0, 1, 2, -1)))
        assertNotNull(Mastermind.withSecret(state, listOf(0, 1, 2, 5)))
    }

    // ---------- Recherche ----------

    @Test
    fun laSuiteEstTrouveeQuandTousLesPionsSontBienPlaces() {
        var state = playing("A", "B", "C")
        assertFalse(Mastermind.isSolved(state, 0))
        state = Mastermind.withGuess(state, wrong)!!
        assertEquals(1, state.guesses[0].size)
        assertFalse(Mastermind.isOver(state, 0))
        // Pas question de passer à la suite suivante tant que celle-ci n'est pas finie.
        assertEquals(0, Mastermind.nextTarget(state).target)

        state = Mastermind.withGuess(state, secret)!!
        assertTrue(Mastermind.isSolved(state, 0))
        assertEquals(2, Mastermind.attempts(state, 0))
        assertNull(Mastermind.withGuess(state, wrong))
        assertEquals(1, Mastermind.nextTarget(state).target)
    }

    @Test
    fun uneSuiteNonTrouveeCompteUnEssaiDePlusQueLaLimite() {
        var state = playing("A", "B")
        repeat(settings.maxAttempts) { state = Mastermind.withGuess(state, wrong)!! }
        assertTrue(Mastermind.isFailed(state, 0))
        assertFalse(Mastermind.isSolved(state, 0))
        assertEquals(settings.maxAttempts + 1, Mastermind.attempts(state, 0))
        assertNull(Mastermind.withGuess(state, secret))
    }

    @Test
    fun unEssaiInvalideNeChangeRien() {
        val state = playing("A", "B")
        assertNull(Mastermind.withGuess(state, listOf(0, 1)))
        assertNull(Mastermind.withGuess(state, listOf(0, 1, 2, 9)))
    }

    @Test
    fun apresLaDerniereSuiteOnVoitLesResultats() {
        var state = playing("A", "B")
        state = solveIn(state, 2)
        assertEquals(MastermindPhase.PLAYING, state.phase)
        state = solveIn(state, 3)
        assertEquals(MastermindPhase.RESULTS, state.phase)
    }

    // ---------- Moyennes et classement ----------

    @Test
    fun laPlusPetiteMoyenneDEssaisGagne() {
        var state = playing("A", "B", "C", "D")
        // Essais pour trouver la suite de A, B, C, D.
        state = solveIn(state, 3)
        state = solveIn(state, 5)
        state = solveIn(state, 2)
        state = solveIn(state, 4)

        // Chaque joueur compte les suites des autres : A = (5+2+4)/3, B = (3+2+4)/3, C = (3+5+4)/3, D = (3+5+2)/3.
        assertEquals(11.0 / 3, Mastermind.average(state, 0), 1e-9)
        assertEquals(3.0, Mastermind.average(state, 1), 1e-9)
        assertEquals(4.0, Mastermind.average(state, 2), 1e-9)
        assertEquals(10.0 / 3, Mastermind.average(state, 3), 1e-9)
        assertEquals(listOf(3, 1, 4, 2), Mastermind.ranks(state))
        // À partir de 3 joueurs, gagner = avoir créé la suite la plus difficile à trouver (ici celle de B).
        assertEquals(listOf(1), Mastermind.winners(state))
    }

    @Test
    fun uneSuiteNonTrouveeCompteDansLesMoyennes() {
        var state = playing("A", "B", "C")
        state = failTarget(state) // la suite de A : 7 essais comptés
        state = solveIn(state, 2)
        state = solveIn(state, 2)
        // A a créé la suite que personne n'a trouvée : il gagne, avec la moyenne la plus basse.
        assertEquals(listOf(0), Mastermind.winners(state))
        assertEquals((2.0 + 2.0) / 2, Mastermind.average(state, 0), 1e-9)
        assertEquals((7.0 + 2.0) / 2, Mastermind.average(state, 1), 1e-9)
    }

    @Test
    fun enCasDEgaliteTousLesExAequoGagnent() {
        var state = playing("A", "B", "C")
        repeat(3) { state = solveIn(state, 4) }
        assertEquals(listOf(1, 1, 1), Mastermind.ranks(state))
        assertEquals(listOf(0, 1, 2), Mastermind.winners(state))
    }

    @Test
    fun aDeuxJoueursGagneCeluiQuiTrouveLaSuiteDeLAutreLePlusVite() {
        var state = playing("A", "B")
        state = solveIn(state, 4) // B cherche la suite de A en 4 essais
        state = solveIn(state, 2) // A cherche la suite de B en 2 essais
        assertEquals(2.0, Mastermind.average(state, 0), 1e-9)
        assertEquals(4.0, Mastermind.average(state, 1), 1e-9)
        assertEquals(listOf(0), Mastermind.winners(state))
    }

    // ---------- Le jeu dans la liste ----------

    @Test
    fun leMastermindEstDansLaListeDesJeuxSpeciaux() {
        val game = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == MASTERMIND_ID }
        assertEquals(2, game.minPlayers)
        assertEquals(8, game.maxPlayers)
        assertEquals(GameCategory.SPECIAL, game.category())
    }

    // ---------- ViewModel ----------

    @Test
    fun uneParticipationDeBoutEnBoutDansLeViewModel() {
        val vm = MastermindViewModel()
        val rules = GameRules(id = MASTERMIND_ID, name = "Mastermind")
        vm.open(rules, listOf("A", "B"))
        assertNull(vm.state)

        vm.updateSettings(settings)
        vm.start()
        assertEquals(MastermindPhase.SECRETS, vm.state!!.phase)

        // Une suite invalide ne fait rien.
        vm.submitSecret(listOf(0, 1))
        assertEquals(0, vm.state!!.secretTurn)
        vm.submitSecret(secret)
        vm.submitSecret(secret)
        assertEquals(MastermindPhase.PLAYING, vm.state!!.phase)

        vm.submitGuess(secret)
        vm.nextTarget()
        assertEquals(1, vm.state!!.target)
        vm.submitGuess(secret)
        vm.nextTarget()
        assertEquals(MastermindPhase.RESULTS, vm.state!!.phase)

        // Rejouer : retour aux réglages avec les mêmes joueurs.
        vm.replay()
        assertNull(vm.state)
        assertEquals(listOf("A", "B"), vm.players)

        vm.close()
        assertTrue(vm.players.isEmpty())
        assertNull(vm.rules)
    }
}
