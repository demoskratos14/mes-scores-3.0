package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La Chasse à l'émoji chacun sur son téléphone, testée sans téléphones : des sessions reliées par un faux lien radio en
 * étoile (voir [FakeStar]). L'organisateur est « Anna » (place 0) ; les invités « Ben », « Cléo »… prennent les places
 * suivantes dans l'ordre d'arrivée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class EmojiOnlineTest {

    private class Phone(star: FakeStar, selfId: String) {
        val transport = star.end(selfId)
        val session = SyncSession(transport)
        val online = EmojiOnline(session, Random(7))
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
    }

    private fun open(): Table {
        val star = FakeStar()
        val host = Phone(star, "host")
        host.session.startHostingEmoji("Anna")
        host.online.openLobby("Anna")
        return Table(star, host)
    }

    private fun table(vararg names: String): Table = open().also { t -> names.forEach { t.join(it) } }

    /** Chaque téléphone donne son résultat, dans l'ordre des places. */
    private fun Table.report(vararg results: Int) {
        phones.forEachIndexed { i, phone -> phone.online.report(results[i]) }
    }

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
        assertEquals(SyncKind.EMOJI, guest.session.kind)
        assertEquals(EmojiGame.of(2), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteOnNeLancePasDeManche() {
        val t = open()
        t.host.online.startRound()
        assertFalse(t.host.online.running)
        assertTrue(t.host.online.acceptsPlayers)
    }

    @Test
    fun seulL_organisateurLanceLaPremiereManche() {
        val t = table("Ben", "Cléo")
        val (host, ben) = t.phones

        ben.online.startRound()
        assertFalse(host.online.running)

        host.online.startRound()
        for (phone in t.phones) {
            assertTrue(phone.online.running)
            assertEquals(1, phone.online.round)
        }
    }

    @Test
    fun onNeRejointPlusLaTableUneFoisLaPartieLancee() {
        val t = table("Ben")
        t.host.online.startRound()

        val late = t.join("Cléo")

        assertEquals(listOf("Anna", "Ben"), t.host.online.lobby)
        assertFalse(late.online.active)
    }

    @Test
    fun onNeRejointPlusUneTablePleine() {
        val t = table(*(1 until EmojiHunt.MAX_PLAYERS).map { "J$it" }.toTypedArray())
        assertEquals(EmojiHunt.MAX_PLAYERS, t.host.online.lobby.size)
        val extra = t.join("Trop")
        assertEquals(EmojiHunt.MAX_PLAYERS, t.host.online.lobby.size)
        assertFalse(extra.online.active)
    }

    // ---------- Une manche ----------

    @Test
    fun tousLesTelephonesRecoiventLaMemeGraineEtLeMemeDelai() {
        val t = table("Ben", "Cléo")

        t.host.online.startRound()

        val seed = t.host.online.seed
        for (phone in t.phones) {
            assertEquals(seed, phone.online.seed)
            assertEquals(t.host.online.delayMs, phone.online.delayMs)
            assertFalse(phone.online.reported)
        }
        assertTrue(t.host.online.delayMs in EmojiHunt.MIN_DELAY_MS..EmojiHunt.MAX_DELAY_MS)
        // Même graine, même niveau : le même plateau partout.
        val boards = t.phones.map { EmojiHunt.generate(it.online.seed, it.online.round) }
        assertTrue(boards.all { it.items == boards.first().items && it.targetIndex == boards.first().targetIndex })
    }

    @Test
    fun chaqueMancheTireUneNouvelleGraine() {
        val t = table("Ben")
        t.host.online.startRound()
        val first = t.host.online.seed
        t.report(300, 200)

        t.host.online.startRound()

        assertEquals(2, t.host.online.round)
        assertNotEquals(first, t.host.online.seed)
        assertEquals(t.host.online.seed, t.guests.single().online.seed)
    }

    @Test
    fun unInvitePeutLancerLesManchesSuivantes() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        t.report(300, 200)

        guest.online.startRound()

        assertTrue(host.online.running)
        assertEquals(2, guest.online.round)
    }

    @Test
    fun lePlusRapideGagneEtTousVoientLeMemeResultat() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.startRound()

        host.online.report(2_310)
        ben.online.report(1_240)
        // Tant que tout le monde n'a pas répondu, la manche continue.
        assertTrue(host.online.running)
        assertTrue(ben.online.reported)
        assertFalse(cleo.online.reported)

        cleo.online.report(EmojiHunt.MISS)

        for (phone in t.phones) {
            assertFalse(phone.online.running)
            assertEquals(listOf(0, 1, 0), phone.online.match.wins)
            assertEquals(1, phone.online.match.lastWinner)
            assertEquals(listOf(2_310, 1_240, EmojiHunt.MISS), phone.online.match.lastResults)
        }
    }

    @Test
    fun unSeulResultatParMancheEtSeulementPourLaMancheEnCours() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()

        guest.online.report(500)
        guest.online.report(100)
        host.online.report(900)

        assertEquals(listOf(0, 1), host.online.match.wins)
        assertEquals(500, host.online.match.lastResults[1])
        // La manche est finie : un résultat de plus est ignoré.
        host.online.report(50)
        assertEquals(listOf(0, 1), host.online.match.wins)
    }

    @Test
    fun uneEgaliteOuPersonneNeTrouveRejoueLaManche() {
        val t = table("Ben")
        t.host.online.startRound()
        t.report(700, 700)

        assertTrue(t.host.online.match.lastDraw)
        assertEquals(listOf(0, 0), t.host.online.match.wins)
        assertTrue(t.guests.single().online.match.lastDraw)

        t.host.online.startRound()
        t.report(EmojiHunt.MISS, EmojiHunt.MISS)
        assertTrue(t.host.online.match.lastDraw)
    }

    @Test
    fun lePremierAuNombreDeManchesGagneEtUneNouvellePartieRepartDeZero() {
        val t = table("Ben")
        val (host, guest) = t.phones
        repeat(EmojiHunt.TARGET) {
            host.online.startRound()
            t.report(900, 400)
        }
        for (phone in t.phones) {
            assertTrue(phone.online.match.isOver)
            assertEquals(1, phone.online.match.winner)
        }
        // La partie est finie : plus de manche.
        host.online.startRound()
        assertFalse(host.online.running)

        guest.online.newMatch()

        for (phone in t.phones) assertEquals(EmojiGame.of(2), phone.online.match)
    }

    // ---------- Départs ----------

    @Test
    fun unInviteQuiPartAnnuleLaMancheEtLesAutresContinuent() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.startRound()
        host.online.report(800)

        cleo.online.leave()

        assertFalse(host.online.running)
        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(EmojiNotice.PLAYER_LEFT, host.online.notice)
        assertEquals(2, host.online.match.players)
        assertEquals(EmojiNotice.PLAYER_LEFT, ben.online.notice)
        assertTrue(host.online.ready)
    }

    @Test
    fun siL_organisateurSeRetrouveSeulLaTableRepartDeZero() {
        val t = table("Ben")
        t.host.online.startRound()

        t.guests.single().online.leave()

        assertFalse(t.host.online.ready)
        assertEquals(0, t.host.online.round)
        assertTrue(t.host.online.acceptsPlayers)
    }

    @Test
    fun siL_organisateurPartLInviteEnEstInforme() {
        val t = table("Ben")

        t.host.online.leave()

        assertEquals(EmojiNotice.HOST_LEFT, t.guests.single().online.notice)
        assertFalse(t.guests.single().online.running)
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesSurviventAuCodage() {
        val table = EmojiMessage.Table(
            names = listOf("Anna", "Ben"),
            seat = 1,
            match = EmojiGame.of(2).resolve(listOf(900, 400))!!,
            notice = EmojiNotice.PLAYER_LEFT,
            round = 3,
            seed = -123456789012345L,
            delayMs = 2_500L,
            running = true
        )
        for (message in listOf(table, EmojiMessage.Result(2, 750), EmojiMessage.Start, EmojiMessage.NewMatch)) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Emoji(message)))
            assertEquals(SyncMessage.Emoji(message), decoded)
        }
    }

    @Test
    fun uneAttenteOuDesScoresAbsurdesSontRamenesAUneValeurPermise() {
        val json = EmojiMessage.Table(
            names = listOf("Anna", "Ben"), seat = 0, match = EmojiGame.of(2), notice = EmojiNotice.NONE,
            round = 1, seed = 5L, delayMs = 2_000L, running = true
        ).toJson()
        json.put("delay", 9_999_999L).put("w", org.json.JSONArray(listOf(99, -4)))

        val decoded = emojiMessageFromJson(json) as EmojiMessage.Table

        assertEquals(EmojiHunt.MAX_DELAY_MS, decoded.delayMs)
        assertEquals(listOf(EmojiHunt.TARGET, 0), decoded.match.wins)
    }
}
