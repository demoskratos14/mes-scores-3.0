package com.aventure.messcores

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les Ronds chacun sur son téléphone, testés sans téléphones : des sessions reliées par un faux lien radio en étoile
 * (voir [FakeStar]). L'organisateur est « Anna » (place 0) ; les invités « Ben », « Cléo »… prennent les places suivantes
 * dans l'ordre d'arrivée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class CirclesOnlineTest {

    private class Phone(star: FakeStar, selfId: String) {
        val transport = star.end(selfId)
        val session = SyncSession(transport)
        val online = CirclesOnline(session)
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
        host.session.startHostingCircles("Anna")
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
        assertEquals(SyncKind.CIRCLES, guest.session.kind)
        assertEquals(null, guest.online.level)
        assertFalse(guest.online.myPlay)
    }

    @Test
    fun ilFautAuMoinsDeuxJoueursPourLancerLaPartie() {
        val t = open()
        t.host.online.start(CirclesLevel.NORMAL)
        assertEquals(null, t.host.online.level)
        assertEquals(0, t.host.online.round)
        assertTrue(t.host.online.acceptsPlayers)
    }

    @Test
    fun seulL_organisateurLanceLaPartie() {
        val t = table("Ben")
        val (host, ben) = t.phones

        ben.online.start(CirclesLevel.HARD)
        assertEquals(null, host.online.level)

        host.online.start(CirclesLevel.HARD)
        for (phone in t.phones) assertEquals(CirclesLevel.HARD, phone.online.level)
    }

    @Test
    fun onNeRejointPlusLaTableUneFoisLaPartieLancee() {
        val t = table("Ben")
        t.host.online.start(CirclesLevel.NORMAL)

        val late = t.join("Cléo")

        assertEquals(listOf("Anna", "Ben"), t.host.online.lobby)
        assertFalse(late.online.active)
    }

    @Test
    fun onNeRejointPlusUneTablePleine() {
        val t = table(*(1 until Circles.MAX_PLAYERS).map { "J$it" }.toTypedArray())
        assertEquals(Circles.MAX_PLAYERS, t.host.online.lobby.size)

        val extra = t.join("Trop")

        assertEquals(Circles.MAX_PLAYERS, t.host.online.lobby.size)
        assertFalse(extra.online.active)
    }

    // ---------- La partie ----------

    @Test
    fun tousLesTelephonesJouentEnMemeTempsLeMemeNiveau() {
        val t = table("Ben", "Cléo")

        t.host.online.start(CirclesLevel.HARD)

        for (phone in t.phones) {
            assertEquals(CirclesLevel.HARD, phone.online.level)
            assertEquals(listOf(CirclesOnline.PENDING, CirclesOnline.PENDING, CirclesOnline.PENDING), phone.online.scores)
            assertTrue(phone.online.myPlay)
            assertFalse(phone.online.over)
            assertEquals(1, phone.online.round)
        }
    }

    @Test
    fun laPlusLongueSerieGagneQuandTousOntFini() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(CirclesLevel.NORMAL)

        host.online.finishRun(3)
        ben.online.finishRun(8)
        // Tant que tout le monde n'a pas fini, la partie continue : on voit les séries déjà rendues.
        assertFalse(host.online.over)
        assertFalse(ben.online.myPlay)
        assertTrue(cleo.online.myPlay)
        assertEquals(listOf(3, 8, CirclesOnline.PENDING), cleo.online.scores)

        cleo.online.finishRun(5)

        for (phone in t.phones) {
            assertTrue(phone.online.over)
            assertFalse(phone.online.myPlay)
            assertEquals(listOf(3, 8, 5), phone.online.scores)
            assertEquals(listOf(1), phone.online.game().winners)
            assertEquals(listOf(1, 2, 0), phone.online.game().ranking())
        }
    }

    @Test
    fun uneEgaliteDonneDesExAequo() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(CirclesLevel.NORMAL)

        host.online.finishRun(3)
        ben.online.finishRun(5)
        cleo.online.finishRun(5)

        for (phone in t.phones) assertEquals(listOf(1, 2), phone.online.game().winners)
    }

    @Test
    fun personneNeGagneSiPersonneNeToucheUnRond() {
        val t = table("Ben")
        t.host.online.start(CirclesLevel.NORMAL)
        t.host.online.finishRun(0)
        t.guests.single().online.finishRun(0)

        assertTrue(t.host.online.over)
        assertEquals(emptyList<Int>(), t.host.online.game().winners)
    }

    @Test
    fun uneSeuleSerieParJoueurEtSeulementPourLaPartieEnCours() {
        val t = table("Ben", "Cléo")
        val (host, ben) = t.phones
        host.online.start(CirclesLevel.NORMAL)

        ben.online.finishRun(4)
        ben.online.finishRun(99)
        // Une série arrivée d'une autre partie est ignorée.
        host.online.onMessage(t.idOf(2), CirclesMessage.RunDone(round = 99, streak = 7))

        assertEquals(listOf(CirclesOnline.PENDING, 4, CirclesOnline.PENDING), host.online.scores)
    }

    @Test
    fun uneSerieNegativeEstRamenee() {
        val t = table("Ben")
        t.host.online.start(CirclesLevel.NORMAL)

        t.host.online.onMessage(t.idOf(1), CirclesMessage.RunDone(round = t.host.online.round, streak = -5))

        assertEquals(listOf(CirclesOnline.PENDING, 0), t.host.online.scores)
    }

    @Test
    fun onNeDonneSaSerieQueSiLaPartieEstLancee() {
        val t = table("Ben")
        val (host, ben) = t.phones

        host.online.finishRun(5)
        ben.online.finishRun(5)

        assertEquals(emptyList<Int>(), host.online.scores)
        assertTrue(host.online.gathering)
        assertFalse(ben.online.reported)
    }

    // ---------- Départs ----------

    @Test
    fun unInviteQuiPartDisparaitEtLesAutresFinissent() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(CirclesLevel.NORMAL)
        ben.online.finishRun(4)

        ben.online.leave()

        assertEquals(listOf("Anna", "Cléo"), host.online.lobby)
        assertEquals(listOf(CirclesOnline.PENDING, CirclesOnline.PENDING), host.online.scores)
        assertEquals(CirclesNotice.PLAYER_LEFT, host.online.notice)
        // Cléo prend la place 1 et peut toujours finir sa série.
        assertEquals(1, cleo.online.seat)
        assertTrue(cleo.online.myPlay)

        host.online.finishRun(2)
        cleo.online.finishRun(6)

        assertTrue(host.online.over)
        assertEquals(listOf(1), host.online.game().winners)
        assertTrue(cleo.online.over)
    }

    @Test
    fun siLesAutresAvaientDejaFiniLeDepartDuDernierTermineLaPartie() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.start(CirclesLevel.NORMAL)
        host.online.finishRun(2)
        ben.online.finishRun(3)

        cleo.online.leave()

        assertTrue(host.online.over)
        assertEquals(listOf(1), host.online.game().winners)
        assertTrue(ben.online.over)
    }

    @Test
    fun siL_organisateurSeRetrouveSeulLaTableRepartDeZero() {
        val t = table("Ben")
        t.host.online.start(CirclesLevel.HARD)

        t.guests.single().online.leave()

        assertFalse(t.host.online.ready)
        assertEquals(0, t.host.online.round)
        assertEquals(null, t.host.online.level)
        assertTrue(t.host.online.acceptsPlayers)
        assertEquals(CirclesNotice.PLAYER_LEFT, t.host.online.notice)
    }

    @Test
    fun siL_organisateurPartLInviteEnEstInforme() {
        val t = table("Ben")

        t.host.online.leave()

        assertEquals(CirclesNotice.HOST_LEFT, t.guests.single().online.notice)
    }

    // ---------- Nouvelle partie ----------

    @Test
    fun lOrganisateurLanceUneNouvellePartieUneFoisLaPartieFinie() {
        val t = table("Ben")
        val (host, ben) = t.phones
        host.online.start(CirclesLevel.NORMAL)
        // Partie en cours : pas de nouvelle partie.
        host.online.newGame()
        assertEquals(CirclesLevel.NORMAL, host.online.level)
        host.online.finishRun(1)
        ben.online.finishRun(2)

        // Un invité ne relance pas la partie.
        ben.online.newGame()
        assertTrue(host.online.over)

        host.online.newGame()

        for (phone in t.phones) {
            assertEquals(null, phone.online.level)
            assertTrue(phone.online.gathering)
            assertEquals(emptyList<Int>(), phone.online.scores)
        }
    }

    @Test
    fun onRejoueUneFoisLaTableReformeEtL_organisateurChangeDeNiveau() {
        val t = table("Ben")
        val (host, ben) = t.phones
        host.online.start(CirclesLevel.NORMAL)
        host.online.finishRun(1)
        ben.online.finishRun(2)
        host.online.newGame()

        host.online.start(CirclesLevel.HARD)

        for (phone in t.phones) {
            assertEquals(CirclesLevel.HARD, phone.online.level)
            assertEquals(2, phone.online.round)
            // Chacun peut rejouer : la série de la partie d'avant n'empêche pas la suivante.
            assertTrue(phone.online.myPlay)
            assertFalse(phone.online.reported)
            assertFalse(phone.online.over)
        }
        host.online.finishRun(7)
        ben.online.finishRun(3)
        assertEquals(listOf(7, 3), ben.online.scores)
        assertEquals(listOf(0), ben.online.game().winners)
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesSurviventAuCodage() {
        val table = CirclesMessage.Table(
            names = listOf("Anna", "Ben", "Cléo"),
            seat = 2,
            level = CirclesLevel.HARD,
            round = 4,
            scores = listOf(3, CirclesOnline.PENDING, 5),
            notice = CirclesNotice.PLAYER_LEFT
        )
        val gathering = table.copy(level = null, scores = emptyList(), notice = CirclesNotice.NONE)
        val messages = listOf(table, gathering, CirclesMessage.RunDone(2, 17))
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.CirclesMsg(message)))
            assertEquals(SyncMessage.CirclesMsg(message), decoded)
        }
    }

    @Test
    fun desSeriesOuUnNiveauAbsurdesSontRamenesAUneValeurPermise() {
        val json = CirclesMessage.Table(
            names = listOf("Anna", "Ben"), seat = 0, level = CirclesLevel.NORMAL, round = 1,
            scores = listOf(1, 2), notice = CirclesNotice.NONE
        ).toJson()
        json.put("scores", JSONArray(listOf(999_999_999, -50, 4)))
            .put("seat", 9)
            .put("level", "IMPOSSIBLE")
            .put("notice", "???")

        val decoded = circlesMessageFromJson(json) as CirclesMessage.Table

        // Trop de séries : on garde une place par joueur ; une série énorme ou négative est ramenée à une valeur permise.
        assertEquals(listOf(CirclesOnline.MAX_STREAK, CirclesOnline.PENDING), decoded.scores)
        assertEquals(1, decoded.seat)
        assertEquals(null, decoded.level)
        assertEquals(CirclesNotice.NONE, decoded.notice)
    }

    @Test
    fun uneTableAvecTropDeJoueursEstTronquee() {
        val names = (1..20).map { "J$it" }
        val json = CirclesMessage.Table(
            names = names.take(2), seat = 0, level = null, round = 0, scores = emptyList(), notice = CirclesNotice.NONE
        ).toJson()
        json.put("names", JSONArray(names))

        val decoded = circlesMessageFromJson(json) as CirclesMessage.Table

        assertEquals(Circles.MAX_PLAYERS, decoded.names.size)
    }
}
