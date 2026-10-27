package com.aventure.messcores

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le Pendu chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0), l'invité « Ben » (place 1). Anna choisit le premier mot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class HangmanOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = HangmanOnline(session, chooseFirst = { 0 })
    }

    private fun lobby(settings: HangmanSettings = HangmanSettings()): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingHangman("Anna")
        host.online.openLobby("Anna", settings)
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    /** Une manche commencée : Anna a choisi « pomme », Ben devine. */
    private fun playing(settings: HangmanSettings = HangmanSettings()): Pair<Phone, Phone> {
        val (host, guest) = lobby(settings)
        host.online.startGame()
        host.online.submitWord("pomme")
        return host to guest
    }

    private fun guessAll(phone: Phone, letters: String) {
        for (letter in letters) phone.online.guess(letter)
    }

    @Test
    fun laSalleDAttenteReunitLesDeuxJoueurs() {
        val (host, guest) = lobby()
        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertEquals(SyncKind.HANGMAN, guest.session.kind)
        assertEquals(1, guest.online.joined)
        assertEquals("Ben", host.online.opponentName)
        assertFalse(host.online.started)
    }

    @Test
    fun lesReglagesArriventChezLInvite() {
        val (_, guest) = lobby(HangmanSettings(maxMistakes = 9))
        assertEquals(9, guest.online.settings.maxMistakes)
    }

    @Test
    fun laPartieCommenceParLeChoixDuMot() {
        val (host, guest) = lobby()
        host.online.startGame()
        assertTrue(host.online.started && guest.online.started)
        assertEquals(HangmanStage.WORD, host.online.stage)
        assertEquals(HangmanStage.WORD, guest.online.stage)
        assertTrue(host.online.iAmSetter)
        assertFalse(guest.online.iAmSetter)
        // Ben ne peut rien deviner avant que le mot soit choisi, et l'invité ne lance pas la partie.
        guest.online.guess('A')
        assertTrue(guest.online.guesses.isEmpty())
        assertFalse(guest.online.canGuess)
    }

    @Test
    fun seulCeluiQuiChoisitLeMotLeSaisitEtLeMotDoitEtreJouable() {
        val (host, guest) = lobby()
        host.online.startGame()
        assertFalse(guest.online.submitWord("chat"))
        assertFalse(host.online.submitWord("c"))
        assertFalse(host.online.submitWord("ab1"))
        assertEquals(HangmanStage.WORD, host.online.stage)
        assertTrue(host.online.submitWord("Pomme"))
        assertEquals("POMME", host.online.myWord)
        // Un deuxième mot en cours de manche est refusé.
        assertFalse(host.online.submitWord("poire"))
        assertEquals("POMME", host.online.myWord)
    }

    @Test
    fun celuiQuiDevineNeRecoitQueLaFormeDuMot() {
        val (host, guest) = playing()
        assertEquals(HangmanStage.PLAYING, host.online.stage)
        assertEquals(HangmanStage.PLAYING, guest.online.stage)
        assertEquals("_____", guest.online.shape)
        assertNull(guest.online.myWord)
        assertNull(guest.online.revealedWord)
        assertEquals("_____", guest.online.display)
        assertTrue(guest.online.canGuess)
        assertFalse(host.online.canGuess)
    }

    @Test
    fun uneLettreDuMotSAfficheSurLesDeuxTelephones() {
        val (host, guest) = playing()
        guest.online.guess('M')
        assertEquals(listOf(HangmanGuess('M', listOf(2, 3))), guest.online.guesses)
        assertEquals(guest.online.guesses, host.online.guesses)
        assertEquals("__MM_", guest.online.display)
        assertEquals("__MM_", host.online.display)
        assertNull(guest.online.pendingLetter)
        assertEquals(HangmanOutcome.PLAYING, guest.online.outcome)
    }

    @Test
    fun uneErreurEstComptee() {
        val (host, guest) = playing()
        guest.online.guess('Z')
        assertTrue(guest.online.guesses.single().isMistake)
        assertEquals(1, Hangman.mistakes(host.online.guesses))
    }

    @Test
    fun onNeProposePasDeuxFoisLaMemeLettreNiUneLettreInvalide() {
        val (host, guest) = playing()
        guest.online.guess('M')
        guest.online.guess('M')
        guest.online.guess('7')
        guest.online.guess('m')
        assertEquals(1, guest.online.guesses.size)
        assertEquals(1, host.online.guesses.size)
    }

    @Test
    fun celuiQuiAnLeMotNeJoueLettresPasLuiMeme() {
        val (host, guest) = playing()
        host.online.guess('M')
        assertTrue(host.online.guesses.isEmpty())
        assertTrue(guest.online.guesses.isEmpty())
    }

    @Test
    fun leMotTrouveTermineLaMancheEtLeMotEstDevoile() {
        val (host, guest) = playing()
        guessAll(guest, "POME")
        assertEquals(HangmanOutcome.WON, guest.online.outcome)
        assertEquals(HangmanStage.FINISHED, guest.online.stage)
        assertEquals(HangmanStage.FINISHED, host.online.stage)
        assertEquals(1, guest.online.roundWinner)
        assertEquals(1, host.online.roundWinner)
        // Chacun compte le même point.
        assertEquals(listOf(0, 1), guest.online.wins)
        assertEquals(listOf(0, 1), host.online.wins)
        assertEquals("POMME", guest.online.revealedWord)
        assertEquals("POMME", guest.online.display)
        assertEquals(true, guest.online.opponentHonest)
        // La manche est finie : plus de lettre.
        guest.online.guess('Z')
        assertEquals(4, guest.online.guesses.size)
    }

    @Test
    fun leBonhommePenduDonneLePointACeluiQuiAvaitLeMot() {
        val (host, guest) = playing(HangmanSettings(maxMistakes = 4))
        guessAll(guest, "XYZW")
        assertEquals(HangmanOutcome.LOST, guest.online.outcome)
        assertEquals(HangmanOutcome.LOST, host.online.outcome)
        assertEquals(0, host.online.roundWinner)
        assertEquals(listOf(1, 0), host.online.wins)
        assertEquals(listOf(1, 0), guest.online.wins)
        // Le mot est dévoilé même si on a perdu.
        assertEquals("POMME", guest.online.revealedWord)
        assertEquals(true, guest.online.opponentHonest)
        assertEquals("POMME", guest.online.display)
    }

    @Test
    fun leMotNeCirculeJamaisSurLeReseauPendantLaManche() {
        val (host, guest) = lobby()
        host.online.startGame()
        // On écoute tout ce que Ben reçoit du téléphone d'Anna, tel qu'il passe sur le réseau.
        val seen = mutableListOf<String>()
        val inner = guest.transport.listener!!
        guest.transport.listener = object : TransportListener by inner {
            override fun onMessage(id: String, bytes: ByteArray) {
                seen += String(GZIPInputStream(ByteArrayInputStream(bytes)).readBytes(), Charsets.UTF_8)
                inner.onMessage(id, bytes)
            }
        }
        host.online.submitWord("pomme")
        guest.online.guess('M')
        guest.online.guess('Z')
        guest.online.guess('P')
        // La forme et les places passent, jamais le mot lui-même.
        assertTrue(seen.size >= 4)
        assertTrue("le mot a fuité : $seen", seen.none { it.contains("POMME", ignoreCase = true) })
        // À la fin de la manche, il est dévoilé.
        guessAll(guest, "OE")
        assertTrue(seen.last().contains("POMME"))
    }

    @Test
    fun lesManchesSEnchainentEtLesRolesSEchangent() {
        val (host, guest) = playing()
        guessAll(guest, "POME")
        // Ben, qui a gagné, lance la manche suivante : c'est lui qui choisit.
        guest.online.nextRound()
        assertEquals(2, host.online.round)
        assertEquals(2, guest.online.round)
        assertEquals(1, host.online.setter)
        assertEquals(1, guest.online.setter)
        assertTrue(guest.online.iAmSetter)
        assertFalse(host.online.iAmSetter)
        assertEquals(HangmanStage.WORD, host.online.stage)
        assertTrue(host.online.guesses.isEmpty())
        assertNull(host.online.revealedWord)
        assertNull(guest.online.myWord)
        // Le score de la séance est gardé, des deux côtés.
        assertEquals(listOf(0, 1), host.online.wins)
        assertEquals(listOf(0, 1), guest.online.wins)
        // Ben choisit, Anna devine, et trouve : un point de plus pour elle.
        assertTrue(guest.online.submitWord("kiwi"))
        assertEquals("____", host.online.shape)
        guessAll(host, "KIW")
        assertEquals(HangmanOutcome.WON, host.online.outcome)
        assertEquals(listOf(1, 1), host.online.wins)
        assertEquals(listOf(1, 1), guest.online.wins)
        assertEquals(true, host.online.opponentHonest)
        // L'organisateur peut lancer la suivante lui aussi : les rôles repartent dans l'autre sens.
        host.online.nextRound()
        assertEquals(3, guest.online.round)
        assertEquals(0, guest.online.setter)
    }

    @Test
    fun onNePasseALaMancheSuivanteQueQuandLaMancheEstFinie() {
        val (host, guest) = playing()
        guest.online.nextRound()
        host.online.nextRound()
        assertEquals(1, host.online.round)
        assertEquals(HangmanStage.PLAYING, host.online.stage)
        assertEquals(1, guest.online.round)
    }

    @Test
    fun unAdversaireQuiMentSurLesPlacesEstDemasqueALaFin() {
        val (host, guest) = lobby()
        host.online.startGame()
        // Un téléphone truqué : il annonce cinq lettres, nie que le M soit dans le mot, puis dévoile « POMME ».
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Ready("_____"))))
        guest.online.guess('M')
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Report(HangmanGuess('M', emptyList())))))
        assertNull(guest.online.opponentHonest)
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Reveal("POMME"))))
        assertEquals(false, guest.online.opponentHonest)
    }

    @Test
    fun uneReponseFarfelueAuxLettresEstFiltreeCoteInvite() {
        val (host, guest) = lobby()
        host.online.startGame()
        // On joue à la main le rôle d'un téléphone truqué : il répond avec des places impossibles.
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Ready("_____"))))
        assertEquals(HangmanStage.PLAYING, guest.online.stage)
        guest.online.guess('M')
        // Le vrai téléphone d'Anna n'a pas de mot (elle n'en a pas choisi) : la lettre reste sans réponse.
        assertEquals('M', guest.online.pendingLetter)
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Report(HangmanGuess('M', listOf(-4, 2, 99, 2))))))
        // Seule une place possible est gardée, une seule fois.
        assertEquals(listOf(2), guest.online.guesses.single().positions)
        assertNull(guest.online.pendingLetter)
    }

    @Test
    fun uneReponseQueLInviteNAPasDemandeeEstIgnoree() {
        val (host, guest) = lobby()
        host.online.startGame()
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Ready("_____"))))
        // Pas de lettre en attente : la réponse est ignorée.
        guest.session.onMessage("host", SyncCodec.encode(SyncMessage.Hg(HgMessage.Report(HangmanGuess('M', listOf(2))))))
        assertTrue(guest.online.guesses.isEmpty())
    }

    @Test
    fun unInviteQuiPartPendantLaPartieRamenelOrganisateurALaSalleDAttente() {
        val (host, guest) = playing()
        guessAll(guest, "PZ")
        guest.online.leave()
        assertEquals(listOf("Anna"), host.online.lobby)
        assertEquals(HangmanNotice.PLAYER_LEFT, host.online.notice)
        assertFalse(host.online.started)
        assertEquals(listOf(0, 0), host.online.wins)
        assertNull(host.online.myWord)
        assertTrue(host.online.guesses.isEmpty())
        assertFalse(guest.online.active)
    }

    @Test
    fun siLOrganisateurPartLInviteLeSait() {
        val (host, guest) = playing()
        host.online.leave()
        assertEquals(HangmanNotice.HOST_LEFT, guest.online.notice)
        assertFalse(guest.online.started)
    }

    @Test
    fun lesMessagesSEncodentEtSeDecodentSansPerte() {
        val messages = listOf(
            HgMessage.Lobby(listOf("Anna", "Ben"), HangmanSettings(9), HangmanNotice.PLAYER_LEFT),
            HgMessage.Start(1, listOf(2, 3), 6),
            HgMessage.Ready("_____ __-_'__"),
            HgMessage.Guess('Q'),
            HgMessage.Report(HangmanGuess('E', listOf(1, 4, 8))),
            HgMessage.Report(HangmanGuess('Z', emptyList())),
            HgMessage.Reveal("POMME DE TERRE"),
            HgMessage.Next
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Hg(message)))
            assertEquals(SyncMessage.Hg(message), decoded)
        }
    }

    @Test
    fun unMessageIllisibleOuAbsurdeEstRamenePresDesLimites() {
        val start = hgMessageFromJson(JSONObject("""{"k":"start","setter":7,"wins":[-3],"round":0}""")) as HgMessage.Start
        assertEquals(1, start.setter)
        assertEquals(listOf(0, 0), start.wins)
        assertEquals(1, start.round)
        val lobby = hgMessageFromJson(
            JSONObject("""{"k":"lobby","names":["Anna"],"m":500,"notice":"NONE"}""")
        ) as HgMessage.Lobby
        assertEquals(10, lobby.settings.maxMistakes)
        assertNull(hgMessageFromJson(JSONObject("""{"k":"autre"}""")))
    }
}
