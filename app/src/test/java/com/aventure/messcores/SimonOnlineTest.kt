package com.aventure.messcores

import kotlin.random.Random
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le Simon chacun sur son téléphone, testé sans téléphones : des sessions reliées par un faux lien radio en étoile (voir
 * [FakeStar]). L'organisateur est « Anna » (place 0) ; les invités « Ben », « Cléo »… prennent les places suivantes dans
 * l'ordre d'arrivée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SimonOnlineTest {

    private class Phone(star: FakeStar, selfId: String) {
        val transport = star.end(selfId)
        val session = SyncSession(transport)
        val online = SimonOnline(session, Random(7))
    }

    private class Table(val star: FakeStar, val host: Phone) {
        val guests = mutableListOf<Phone>()
        val phones: List<Phone> get() = listOf(host) + guests

        fun join(name: String): Phone {
            val guest = Phone(star, "guest-${guests.size}-$name")
            guest.session.startJoining()
            guest.session.connectTo(guest.session.endpoints.single(), name)
            host.session.accept()
            guests.add(guest)
            return guest
        }

        /** L'identifiant sous lequel l'organisateur voit l'invité de la place [place] (1 pour le premier invité). */
        fun idOf(place: Int): String = guests[place - 1].transport.selfId
    }

    private fun open(): Table {
        val star = FakeStar()
        val host = Phone(star, "host")
        host.session.startHostingSimon("Anna")
        host.online.openLobby("Anna")
        return Table(star, host)
    }

    private fun table(vararg names: String): Table = open().also { t -> names.forEach { t.join(it) } }

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLEcranDeJeuS_ouvre() {
        val t = table("Ben")
        val (host, guest) = t.phones

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertTrue(host.online.ready)
        assertTrue(guest.online.gathering)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.SIMON, guest.session.kind)
        assertEquals(null, guest.online.mode)
    }

    @Test
    fun ilFautAuMoinsDeuxJoueursPourLancerLaPartie() {
        val t = open()
        t.host.online.start(SimonMode.SERIES)
        assertEquals(null, t.host.online.mode)
        assertTrue(t.host.online.acceptsPlayers)
    }

    @Test
    fun seulL_organisateurLanceLaPartie() {
        val t = table("Ben")
        val (host, ben) = t.phones

        ben.online.start(SimonMode.TURNS)
        assertEquals(null, host.online.mode)

        host.online.start(SimonMode.TURNS)
        for (phone in t.phones) assertEquals(SimonMode.TURNS, phone.online.mode)
    }

    @Test
    fun onNeRejointPlusLaTableUneFoisLaPartieLancee() {
        val t = table("Ben")
        t.host.online.start(SimonMode.SERIES)

        val late = t.join("Cléo")

        assertEquals(listOf("Anna", "Ben"), t.host.online.lobby)
        assertFalse(late.online.active)
    }

    @Test
    fun onNeRejointPlusUneTablePleine() {
        val t = table(*(1 until Simon.MAX_PLAYERS).map { "J$it" }.toTypedArray())
        assertEquals(Simon.MAX_PLAYERS, t.host.online.lobby.size)

        val extra = t.join("Trop")

        assertEquals(Simon.MAX_PLAYERS, t.host.online.lobby.size)
        assertFalse(extra.online.active)
    }

    // ---------- Chacun sa série ----------

    @Test
    fun enSerieTousLesTelephonesJouentEnMemeTempsLeurPropreSerie() {
        val t = table("Ben", "Cléo")

        t.host.online.start(SimonMode.SERIES)

        for (phone in t.phones) {
            assertEquals(SimonMode.SERIES, phone.online.mode)
            assertEquals(listOf(SimonOnline.PENDING, SimonOnline.PENDING, SimonOnline.PENDING), phone.online.scores)
            assertTrue(phone.online.myPlay)
            assertFalse(phone.online.over)
        }
    }

    @Test
    fun enSerieLeMeilleurScoreGagneQuandTousOntFini() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.SERIES)

        host.online.finishSeries(3)
        ben.online.finishSeries(5)
        // Tant que tout le monde n'a pas fini, la partie continue : on voit les scores déjà rendus.
        assertFalse(host.online.over)
        assertFalse(ben.online.myPlay)
        assertTrue(cleo.online.myPlay)
        assertEquals(listOf(3, 5, SimonOnline.PENDING), cleo.online.scores)

        cleo.online.finishSeries(5)

        for (phone in t.phones) {
            assertTrue(phone.online.over)
            assertEquals(listOf(3, 5, 5), phone.online.scores)
            assertEquals(listOf(1, 2), phone.online.series().winners)
            assertEquals(listOf(1, 2, 0), phone.online.series().ranking())
        }
    }

    @Test
    fun enSeriePersonneNeGagneSiPersonneNeReussitUneSuite() {
        val t = table("Ben")
        t.host.online.start(SimonMode.SERIES)
        t.host.online.finishSeries(0)
        t.guests.single().online.finishSeries(0)

        assertTrue(t.host.online.over)
        assertEquals(emptyList<Int>(), t.host.online.series().winners)
    }

    @Test
    fun unSeulScoreParJoueurEtSeulementPourLaPartieEnCours() {
        val t = table("Ben", "Cléo")
        val (host, ben) = t.phones
        host.online.start(SimonMode.SERIES)

        ben.online.finishSeries(4)
        ben.online.finishSeries(99)
        // Un score arrivé d'une autre partie est ignoré.
        host.online.onMessage(t.idOf(2), SimonMessage.SeriesDone(round = 99, score = 7))

        assertEquals(listOf(SimonOnline.PENDING, 4, SimonOnline.PENDING), host.online.scores)
    }

    @Test
    fun enSerieUnInviteQuiPartDisparaitEtLesAutresFinissent() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.SERIES)
        ben.online.finishSeries(4)

        ben.online.leave()

        assertEquals(listOf("Anna", "Cléo"), host.online.lobby)
        assertEquals(listOf(SimonOnline.PENDING, SimonOnline.PENDING), host.online.scores)
        assertEquals(SimonNotice.PLAYER_LEFT, host.online.notice)
        // Cléo prend la place 1 et peut toujours finir sa série.
        assertEquals(1, cleo.online.seat)
        assertTrue(cleo.online.myPlay)

        host.online.finishSeries(2)
        cleo.online.finishSeries(6)

        assertTrue(host.online.over)
        assertEquals(listOf(1), host.online.series().winners)
        assertTrue(cleo.online.over)
    }

    @Test
    fun enSerieSiLesAutresAvaientDejaFiniLeDepartDuDernierTermineLaPartie() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.SERIES)
        host.online.finishSeries(2)
        ben.online.finishSeries(3)

        cleo.online.leave()

        assertTrue(host.online.over)
        assertEquals(listOf(1), host.online.series().winners)
    }

    // ---------- Chacun son tour ----------

    @Test
    fun enTourUneSeuleSuiteCommuneEtSeulLePremierJoueurJoue() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones

        host.online.start(SimonMode.TURNS)

        assertEquals(1, host.online.sequence.size)
        for (phone in t.phones) {
            assertEquals(host.online.sequence, phone.online.sequence)
            assertEquals(0, phone.online.current)
        }
        assertTrue(host.online.myPlay)
        assertFalse(ben.online.myPlay)
        assertFalse(cleo.online.myPlay)
    }

    @Test
    fun enTourUneSuiteReproduiteLaRallongeEtPasseAuJoueurSuivant() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.TURNS)
        val first = host.online.sequence

        host.online.reportTurn(true)

        for (phone in t.phones) {
            assertEquals(2, phone.online.sequence.size)
            assertEquals(first, phone.online.sequence.take(1))
            assertEquals(1, phone.online.current)
            assertEquals(-1, phone.online.lastOut)
        }
        assertFalse(host.online.myPlay)
        assertTrue(ben.online.myPlay)
        assertFalse(cleo.online.myPlay)
    }

    @Test
    fun enTourUneErreurEliminePuisLeSuivantRetenteLaMemeSuiteEtLeDernierEnJeuGagne() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.TURNS)
        host.online.reportTurn(true)
        val sequence = host.online.sequence

        ben.online.reportTurn(false)

        for (phone in t.phones) {
            assertEquals(listOf(1), phone.online.eliminated)
            assertEquals(1, phone.online.lastOut)
            assertEquals(2, phone.online.current)
            assertEquals(sequence, phone.online.sequence)
            assertFalse(phone.online.over)
        }
        assertTrue(cleo.online.myPlay)

        cleo.online.reportTurn(false)

        for (phone in t.phones) {
            assertTrue(phone.online.over)
            assertEquals(0, phone.online.turns().winner)
            assertEquals(listOf(0, 2, 1), phone.online.turns().ranking())
            assertFalse(phone.online.myPlay)
        }
    }

    @Test
    fun enTourSeulLeJoueurDontC_estLeTourEtPourLeTourEnCoursEstEcoute() {
        val t = table("Ben", "Cléo")
        val (host, ben) = t.phones
        host.online.start(SimonMode.TURNS)

        // Ben n'a pas la main : ni son appel, ni un message envoyé à sa place ne comptent.
        ben.online.reportTurn(false)
        host.online.onMessage(t.idOf(1), SimonMessage.TurnResult(host.online.round, false))
        assertEquals(emptyList<Int>(), host.online.eliminated)

        host.online.reportTurn(true)
        val round = host.online.round
        // Un résultat du tour précédent, arrivé en retard, est ignoré.
        host.online.onMessage(t.idOf(1), SimonMessage.TurnResult(round - 1, false))
        assertEquals(emptyList<Int>(), host.online.eliminated)
        assertEquals(1, host.online.current)

        ben.online.reportTurn(true)
        assertEquals(3, host.online.sequence.size)
        assertEquals(2, host.online.current)
    }

    @Test
    fun enTourLeJoueurNeRejouePasAvantLeTourSuivant() {
        val t = table("Ben")
        val (host, ben) = t.phones
        host.online.start(SimonMode.TURNS)
        host.online.reportTurn(true)

        ben.online.reportTurn(true)
        // Son résultat est pris : la suite s'est allongée et c'est de nouveau à Anna.
        assertEquals(3, host.online.sequence.size)
        assertEquals(0, host.online.current)
        assertTrue(host.online.myPlay)
        assertFalse(ben.online.myPlay)
    }

    @Test
    fun enTourUnInviteQuiPartAnnuleLaPartieEtLaTableSeReforme() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(SimonMode.TURNS)
        host.online.reportTurn(true)

        cleo.online.leave()

        assertEquals(null, host.online.mode)
        assertEquals(SimonNotice.GAME_CANCELLED, host.online.notice)
        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertTrue(host.online.gathering)
        assertTrue(ben.online.gathering)
        assertEquals(SimonNotice.GAME_CANCELLED, ben.online.notice)
        assertFalse(ben.online.myPlay)

        // L'organisateur peut relancer dans le mode de son choix.
        host.online.start(SimonMode.SERIES)
        assertEquals(SimonMode.SERIES, ben.online.mode)
        assertTrue(ben.online.myPlay)
    }

    // ---------- Fin de partie et départs ----------

    @Test
    fun lOrganisateurLanceUneNouvellePartieUneFoisLaPartieFinie() {
        val t = table("Ben")
        val (host, ben) = t.phones
        host.online.start(SimonMode.SERIES)
        // Partie en cours : pas de nouvelle partie.
        host.online.newGame()
        assertEquals(SimonMode.SERIES, host.online.mode)
        host.online.finishSeries(1)
        ben.online.finishSeries(2)

        ben.online.newGame()
        assertTrue(host.online.over)

        host.online.newGame()

        for (phone in t.phones) {
            assertEquals(null, phone.online.mode)
            assertTrue(phone.online.gathering)
            assertEquals(emptyList<Int>(), phone.online.scores)
        }
        host.online.start(SimonMode.TURNS)
        assertEquals(SimonMode.TURNS, ben.online.mode)
        assertEquals(0, ben.online.current)
    }

    @Test
    fun siL_organisateurSeRetrouveSeulLaTableRepartDeZero() {
        val t = table("Ben")
        t.host.online.start(SimonMode.SERIES)

        t.guests.single().online.leave()

        assertFalse(t.host.online.ready)
        assertEquals(0, t.host.online.round)
        assertEquals(null, t.host.online.mode)
        assertTrue(t.host.online.acceptsPlayers)
        assertEquals(SimonNotice.PLAYER_LEFT, t.host.online.notice)
    }

    @Test
    fun siL_organisateurPartLInviteEnEstInforme() {
        val t = table("Ben")

        t.host.online.leave()

        assertEquals(SimonNotice.HOST_LEFT, t.guests.single().online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesSurviventAuCodage() {
        val table = SimonMessage.Table(
            names = listOf("Anna", "Ben", "Cléo"),
            seat = 2,
            mode = SimonMode.TURNS,
            round = 4,
            scores = emptyList(),
            sequence = listOf(0, 3, 2, 1),
            eliminated = listOf(1),
            current = 2,
            lastOut = 1,
            notice = SimonNotice.GAME_CANCELLED
        )
        val series = table.copy(mode = SimonMode.SERIES, scores = listOf(3, SimonOnline.PENDING, 5), sequence = emptyList(), eliminated = emptyList(), lastOut = -1)
        val gathering = table.copy(mode = null, sequence = emptyList(), eliminated = emptyList(), lastOut = -1)
        val messages = listOf(table, series, gathering, SimonMessage.SeriesDone(2, 7), SimonMessage.TurnResult(5, true))
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.SimonMsg(message)))
            assertEquals(SyncMessage.SimonMsg(message), decoded)
        }
    }

    @Test
    fun uneSuiteOuDesScoresAbsurdesSontRamenesAUneValeurPermise() {
        val json = SimonMessage.Table(
            names = listOf("Anna", "Ben"), seat = 0, mode = SimonMode.SERIES, round = 1,
            scores = listOf(1, 2), sequence = emptyList(), eliminated = emptyList(), current = 0, lastOut = -1,
            notice = SimonNotice.NONE
        ).toJson()
        json.put("scores", JSONArray(listOf(999_999, -50)))
            .put("seq", JSONArray(listOf(9, -3)))
            .put("out", JSONArray(listOf(1, 1, 7)))
            .put("cur", 12)

        val decoded = simonMessageFromJson(json) as SimonMessage.Table

        assertEquals(listOf(2_000, SimonOnline.PENDING), decoded.scores)
        assertEquals(listOf(Simon.PADS - 1, 0), decoded.sequence)
        assertEquals(listOf(1), decoded.eliminated)
        assertEquals(1, decoded.current)
    }
}
