package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le Mastermind chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0), l'invité « Ben » (place 1). Chacun cherche seul la suite de l'autre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MastermindOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = MastermindOnline(session)
    }

    private val settings = MastermindSettings(codeLength = 4, colorCount = 6, maxAttempts = 6)
    private val annaSecret = listOf(0, 1, 2, 3)
    private val benSecret = listOf(3, 2, 1, 0)
    private val wrong = listOf(5, 5, 5, 5)

    /** Anna organise, Ben rejoint : tous deux sont dans la salle d'attente. */
    private fun lobby(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingMastermind("Anna")
        host.online.openLobby("Anna", settings)
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    /** La partie est lancée et les deux suites sont saisies : Anna cherche celle de Ben, et Ben celle d'Anna. */
    private fun playing(): Pair<Phone, Phone> {
        val (host, guest) = lobby()
        host.online.startGame()
        host.online.submitSecret(annaSecret)
        guest.online.submitSecret(benSecret)
        return host to guest
    }

    // ---------- Salle d'attente ----------

    @Test
    fun laSalleDAttenteReunitLesJoueursDansLOrdreDeConnexion() {
        val (host, guest) = lobby()

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(guest.online.active)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.MASTERMIND, guest.session.kind)
        assertNull(guest.online.state)
        assertEquals(settings, guest.online.settings)
    }

    @Test
    fun ilFautAuMoinsDeuxJoueursPourLancerLaPartie() {
        val host = Phone("host")
        host.session.startHostingMastermind("Anna")
        host.online.openLobby("Anna", settings)

        host.online.startGame()

        assertNull(host.online.state)
    }

    @Test
    fun seulLOrganisateurLanceLaPartie() {
        val (host, guest) = lobby()
        guest.online.startGame()
        assertNull(host.online.state)
        assertNull(guest.online.state)
    }

    // ---------- Suites secrètes ----------

    @Test
    fun laRechercheCommenceQuandTousSontPretsEtChacunCherchelaSuiteDeLAutre() {
        val (host, guest) = lobby()
        host.online.startGame()
        assertEquals(MastermindPhase.SECRETS, guest.online.state!!.phase)
        assertEquals(listOf(false, false), guest.online.state!!.ready)

        host.online.submitSecret(annaSecret)
        assertEquals(listOf(true, false), guest.online.state!!.ready)
        assertEquals(MastermindPhase.SECRETS, host.online.state!!.phase)
        assertFalse(host.online.searchStarted)

        guest.online.submitSecret(benSecret)
        assertEquals(MastermindPhase.PLAYING, host.online.state!!.phase)
        assertEquals(MastermindPhase.PLAYING, guest.online.state!!.phase)
        assertEquals(listOf(1), host.online.order)
        assertEquals(listOf(0), guest.online.order)
        assertEquals(1, host.online.currentTarget)
        assertEquals(0, guest.online.currentTarget)
    }

    @Test
    fun lesSuitesNeSontJamaisDansLEtatPublicAvantLesResultats() {
        val (host, guest) = playing()

        assertEquals(annaSecret, host.online.mySecret)
        assertEquals(benSecret, guest.online.mySecret)
        assertEquals(listOf(null, null), host.online.state!!.secrets)
        assertEquals(listOf(null, null), guest.online.state!!.secrets)
    }

    @Test
    fun uneSuiteInvalideOuDejaSaisieEstRefusee() {
        val (host, guest) = lobby()
        host.online.startGame()
        guest.online.submitSecret(listOf(0, 1))
        assertNull(guest.online.mySecret)
        guest.online.submitSecret(benSecret)
        guest.online.submitSecret(listOf(4, 4, 4, 4))
        assertEquals(benSecret, guest.online.mySecret)
        assertEquals(listOf(false, true), host.online.state!!.ready)
    }

    // ---------- Recherche ----------

    @Test
    fun laReponseUnEssaiEstCalculeeSurLeTelephoneDuChercheur() {
        val (host, guest) = playing()

        guest.online.submitGuess(listOf(0, 1, 5, 5))

        assertEquals(MastermindFeedback(2, 0), guest.online.guesses.single().feedback)
        // Rien n'est envoyé tant que la suite n'est pas terminée.
        assertTrue(host.online.state!!.results.all { row -> row.all { it == null } })
        assertEquals(host.online.state, guest.online.state)
        assertTrue(host.online.guesses.isEmpty())
    }

    @Test
    fun unEssaiInvalideEstIgnore() {
        val (_, guest) = playing()
        guest.online.submitGuess(listOf(0, 1))
        guest.online.submitGuess(listOf(0, 1, 2, 9))
        assertTrue(guest.online.guesses.isEmpty())
    }

    @Test
    fun uneSuiteTrouveeEstEnvoyeeALOrganisateurEtCompteeSurLesDeuxTelephones() {
        val (host, guest) = playing()

        guest.online.submitGuess(wrong)
        guest.online.submitGuess(annaSecret)

        assertTrue(guest.online.runSolved)
        assertTrue(guest.online.runOver)
        assertEquals(2, host.online.state!!.results[1][0])
        assertEquals(1, Mastermind.searchedCount(guest.online.state!!, 1))
        assertEquals(0, Mastermind.searchedCount(guest.online.state!!, 0))
        assertEquals(host.online.state, guest.online.state)
        // Anna n'a pas fini : les suites ne sont pas encore dévoilées.
        assertEquals(listOf(null, null), guest.online.state!!.secrets)
    }

    @Test
    fun chacunCherchePourLuiSansAttendreLAutre() {
        val (host, guest) = playing()

        // Anna joue plusieurs essais, Ben aucun : personne n'attend personne.
        host.online.submitGuess(wrong)
        host.online.submitGuess(wrong)
        host.online.submitGuess(benSecret)

        assertEquals(3, host.online.state!!.results[0][1])
        assertTrue(guest.online.guesses.isEmpty())
        assertEquals(MastermindPhase.PLAYING, guest.online.state!!.phase)
    }

    @Test
    fun onNePeutPlusEssayerUneFoisLaSuiteTermineeEtOnPasseALaSuivante() {
        val (_, guest) = playing()

        // Tant que la suite n'est pas terminée, on ne passe pas à la suivante.
        guest.online.next()
        assertEquals(0, guest.online.position)

        guest.online.submitGuess(annaSecret)
        guest.online.submitGuess(wrong)
        assertEquals(1, guest.online.guesses.size)

        guest.online.next()
        assertEquals(1, guest.online.position)
        assertNull(guest.online.currentTarget)
        assertTrue(guest.online.guesses.isEmpty())
    }

    /** Joue la recherche de [phone] : trouve la suite [secret] à l'essai numéro [foundAt] (null : jamais trouvée). */
    private fun search(phone: Phone, secret: List<Int>, foundAt: Int?) {
        for (number in 1..settings.maxAttempts) {
            if (phone.online.runOver) break
            phone.online.submitGuess(if (number == foundAt) secret else wrong)
        }
    }

    @Test
    fun uneParticipationCompleteDonneLesMemesResultatsAuxDeuxTelephones() {
        val (host, guest) = playing()

        // Ben trouve la suite d'Anna au 3e essai, Anna celle de Ben au 2e.
        search(guest, annaSecret, 3)
        assertEquals(MastermindPhase.PLAYING, host.online.state!!.phase)
        search(host, benSecret, 2)

        for (phone in listOf(host, guest)) {
            val state = phone.online.state!!
            assertEquals(MastermindPhase.RESULTS, state.phase)
            assertEquals(listOf(annaSecret, benSecret), state.secrets)
            assertEquals(3, state.results[1][0])
            assertEquals(2, state.results[0][1])
            assertEquals(2, Mastermind.attemptsSum(state, 0))
            assertEquals(3, Mastermind.attemptsSum(state, 1))
            assertEquals(listOf(0), Mastermind.winners(state))
        }
        assertEquals(host.online.state, guest.online.state)
    }

    @Test
    fun uneSuiteNonTrouveeCompteUnEssaiDePlusQueLaLimiteEtEstRevelee() {
        val (host, guest) = playing()

        search(guest, annaSecret, null)

        assertTrue(guest.online.runOver)
        assertFalse(guest.online.runSolved)
        assertEquals(annaSecret, guest.online.currentSecret)
        assertEquals(settings.maxAttempts + 1, host.online.state!!.results[1][0])

        search(host, benSecret, 1)
        val state = guest.online.state!!
        assertEquals(MastermindPhase.RESULTS, state.phase)
        assertEquals(listOf(annaSecret, benSecret), state.secrets)
        assertEquals(listOf(0), Mastermind.winners(state))
    }

    @Test
    fun deuxJoueursAuMemeNombreDEssaisSontExAequo() {
        val (host, guest) = playing()
        search(guest, annaSecret, 4)
        search(host, benSecret, 4)
        assertEquals(listOf(0, 1), Mastermind.winners(host.online.state!!))
    }

    // ---------- Essais truqués ----------

    private fun sendRun(guest: Phone, target: Int, run: List<List<Int>>) =
        guest.session.sendToHost(SyncMessage.Mm(MmMessage.Run(target, run)))

    @Test
    fun lOrganisateurRejoueLesEssaisContreLaVraieSuite() {
        val (host, guest) = playing()

        // Essais qui ne trouvent pas la suite, mais annoncés comme terminés trop tôt.
        sendRun(guest, 0, listOf(wrong))
        sendRun(guest, 0, listOf(wrong, wrong, wrong))
        // Des essais après la suite trouvée.
        sendRun(guest, 0, listOf(annaSecret, wrong))
        // Un essai invalide.
        sendRun(guest, 0, listOf(listOf(0, 1)))
        // Aucun essai, ou une suite qui n'existe pas, ou la sienne.
        sendRun(guest, 0, emptyList())
        sendRun(guest, 7, listOf(annaSecret))
        sendRun(guest, 1, listOf(benSecret))
        assertNull(host.online.state!!.results[1][0])
        assertNull(host.online.state!!.results[1][1])

        // Des essais qui trouvent la suite à la fin sont acceptés, une seule fois.
        sendRun(guest, 0, listOf(wrong, annaSecret))
        assertEquals(2, host.online.state!!.results[1][0])
        sendRun(guest, 0, listOf(annaSecret))
        assertEquals(2, host.online.state!!.results[1][0])
    }

    // ---------- Fin de partie et connexions ----------

    @Test
    fun rejouerRamenePartoutALaSalleDAttente() {
        val (host, guest) = playing()
        search(guest, annaSecret, 2)
        search(host, benSecret, 2)

        host.online.replay()

        assertNull(host.online.state)
        assertNull(guest.online.state)
        assertNull(guest.online.mySecret)
        assertFalse(guest.online.searchStarted)
        assertTrue(guest.online.guesses.isEmpty())
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(1, guest.online.joined)
        // L'invité ne peut pas relancer à la place de l'organisateur.
        guest.online.replay()
        assertNull(guest.online.state)
    }

    @Test
    fun unJoueurQuiPartEnPleinePartieInterromptLaPartie() {
        val (host, guest) = playing()
        guest.online.submitGuess(wrong)

        guest.online.leave()

        assertEquals(MastermindNotice.PLAYER_LEFT, host.online.notice)
        assertNull(host.online.state)
        assertFalse(host.online.searchStarted)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(guest.online.active)
    }

    @Test
    fun siLOrganisateurPartLInviteEstPrevenu() {
        val (host, guest) = playing()

        host.online.leave()

        assertEquals(MastermindNotice.HOST_LEFT, guest.online.notice)
        assertNull(guest.online.state)
        assertFalse(guest.online.searchStarted)
        assertFalse(host.online.active)
    }

    @Test
    fun unJoueurQuiPartDansLaSalleDAttenteNInterromptRien() {
        val (host, guest) = lobby()

        guest.online.leave()

        assertEquals(MastermindNotice.NONE, host.online.notice)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertTrue(host.online.active)
    }

    // ---------- Règles du moteur ----------

    private fun onlineState(players: Int = 3) =
        Mastermind.newOnlineGame((1..players).map { "J$it" }, settings)

    @Test
    fun laRechercheCommenceQuandTousLesJoueursSontPrets() {
        var state = onlineState()
        state = Mastermind.markReady(state, 0)!!
        state = Mastermind.markReady(state, 2)!!
        assertEquals(MastermindPhase.SECRETS, state.phase)
        assertEquals(listOf(true, false, true), state.ready)
        state = Mastermind.markReady(state, 1)!!
        assertEquals(MastermindPhase.PLAYING, state.phase)
        assertNull(Mastermind.markReady(state, 1))
        assertNull(Mastermind.markReady(onlineState(), 5))
    }

    @Test
    fun chacunCommenceParLeJoueurSuivantEtSautePropreSuite() {
        assertEquals(listOf(1, 2), Mastermind.searchOrder(3, 0))
        assertEquals(listOf(2, 0), Mastermind.searchOrder(3, 1))
        assertEquals(listOf(0, 1), Mastermind.searchOrder(3, 2))
        assertEquals(listOf(1, 2, 3, 0), Mastermind.searchOrder(4, 0))
    }

    @Test
    fun lesEssaisCompterSontCeuxQuiTrouventLaSuiteOuQuiEpuisentLaLimite() {
        val secret = annaSecret
        assertEquals(1, Mastermind.countRun(secret, listOf(secret), settings))
        assertEquals(3, Mastermind.countRun(secret, listOf(wrong, wrong, secret), settings))
        assertEquals(settings.maxAttempts + 1, Mastermind.countRun(secret, List(settings.maxAttempts) { wrong }, settings))
        // Trouvée au dernier essai permis : elle compte comme trouvée.
        assertEquals(
            settings.maxAttempts,
            Mastermind.countRun(secret, List(settings.maxAttempts - 1) { wrong } + listOf(secret), settings)
        )
        assertNull(Mastermind.countRun(secret, emptyList(), settings))
        assertNull(Mastermind.countRun(secret, listOf(wrong, wrong), settings))
        assertNull(Mastermind.countRun(secret, listOf(secret, wrong), settings))
        assertNull(Mastermind.countRun(secret, List(settings.maxAttempts + 1) { wrong }, settings))
        assertNull(Mastermind.countRun(secret, listOf(listOf(0, 1, 2)), settings))
    }

    @Test
    fun laPartieSeTermineQuandChacunATermineToutesLesSuitesDesAutres() {
        var state = onlineState()
        for (seat in 0..2) state = Mastermind.markReady(state, seat)!!

        // Un joueur ne cherche pas sa propre suite, ni deux fois la même.
        assertNull(Mastermind.withResult(state, 0, 0, 3))
        assertNull(Mastermind.withResult(state, 5, 0, 3))
        assertNull(Mastermind.withResult(state, 0, 9, 3))
        state = Mastermind.withResult(state, 0, 1, 3)!!
        assertNull(Mastermind.withResult(state, 0, 1, 1))

        val rest = listOf(0 to 2, 1 to 0, 1 to 2, 2 to 0)
        for ((searcher, target) in rest) {
            state = Mastermind.withResult(state, searcher, target, 4)!!
            assertEquals(MastermindPhase.PLAYING, state.phase)
        }
        state = Mastermind.withResult(state, 2, 1, 2)!!

        assertEquals(MastermindPhase.RESULTS, state.phase)
        assertEquals(7, Mastermind.attemptsSum(state, 0))
        assertEquals(8, Mastermind.attemptsSum(state, 1))
        assertEquals(6, Mastermind.attemptsSum(state, 2))
        assertEquals(listOf(2, 3, 1), Mastermind.ranks(state))
        assertEquals(listOf(2), Mastermind.winners(state))
        assertEquals(3.5, Mastermind.average(state, 0), 0.0001)
        // Plus rien à enregistrer une fois la partie finie.
        assertNull(Mastermind.withResult(state, 0, 1, 1))
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesDuMastermindSurviventALEnvoi() {
        var state = onlineState()
        for (seat in 0..2) state = Mastermind.markReady(state, seat)!!
        val partial = Mastermind.withResult(state, 1, 0, 4)!!
        val done = partial.copy(secrets = listOf(annaSecret, benSecret, annaSecret))

        val messages = listOf(
            MmMessage.Lobby(listOf("Anna", "Ben"), settings, 1, MastermindNotice.PLAYER_LEFT),
            MmMessage.State(onlineState()),
            MmMessage.State(partial),
            MmMessage.State(done),
            MmMessage.Secret(annaSecret),
            MmMessage.Secrets(listOf(null, benSecret, annaSecret)),
            MmMessage.Run(1, listOf(wrong, benSecret)),
            MmMessage.Run(0, emptyList())
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Mm(message)))
            assertEquals(message.toString(), SyncMessage.Mm(message), decoded)
        }
        assertNotNull(SyncCodec.decode(SyncCodec.encode(SyncMessage.Mm(MmMessage.Secret(annaSecret)))))
    }
}
