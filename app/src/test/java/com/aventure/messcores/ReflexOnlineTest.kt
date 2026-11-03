package com.aventure.messcores

import kotlin.random.Random
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
 * Le Duel de réflexes chacun sur son téléphone, testé sans téléphones : des sessions reliées par un faux lien radio en
 * étoile (voir [FakeStar]). L'organisateur est « Anna » (place 0) ; les invités « Ben », « Cléo »… prennent les places
 * suivantes dans l'ordre d'arrivée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ReflexOnlineTest {

    private class Phone(star: FakeStar, selfId: String) {
        val transport = star.end(selfId)
        val session = SyncSession(transport)
        val online = ReflexOnline(session, Random(7))
    }

    private class Table(val star: FakeStar, val host: Phone) {
        val guests = mutableListOf<Phone>()
        val phones: List<Phone> get() = listOf(host) + guests

        /** Un invité de plus tente de rejoindre la table. */
        fun join(name: String): Phone {
            val guest = Phone(star, "guest-$name")
            guest.session.startJoining()
            guest.session.connectTo(guest.session.endpoints.single(), name)
            host.session.accept()
            guests.add(guest)
            return guest
        }
    }

    /** Anna organise : la table est ouverte, personne n'est encore arrivé. */
    private fun open(): Table {
        val star = FakeStar()
        val host = Phone(star, "host")
        host.session.startHostingReflex("Anna")
        host.online.openLobby("Anna")
        return Table(star, host)
    }

    /** Anna organise, et chacun des [names] rejoint la table dans l'ordre. */
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
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertTrue(host.online.gathering)
        assertTrue(guest.online.gathering)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.REFLEX, guest.session.kind)
        assertEquals(ReflexGame.of(2), guest.online.match)
        assertFalse(guest.online.running)
    }

    @Test
    fun avantLArriveeDeLInviteOnNeLancePasDeManche() {
        val t = open()

        assertFalse(t.host.online.ready)
        assertTrue(t.host.online.acceptsPlayers)
        t.host.online.startRound()
        assertFalse(t.host.online.running)
    }

    @Test
    fun troisJoueursSeRetrouventALaMemeTableChacunAvecSaPlace() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones

        for (phone in t.phones) {
            assertEquals(listOf("Anna", "Ben", "Cléo"), phone.online.lobby)
            assertEquals(ReflexGame.of(3), phone.online.match)
            assertTrue(phone.online.gathering)
        }
        assertEquals(listOf(0, 1, 2), listOf(host.online.seat, ben.online.seat, cleo.online.seat))
    }

    @Test
    fun deuxJoueursPortantLeMemeNomSontDepartages() {
        val t = table("Ben", "Ben")
        assertEquals(listOf("Anna", "Ben", "Ben 2"), t.host.online.lobby)
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
            assertFalse(phone.online.gathering)
        }
    }

    @Test
    fun onNeRejointPlusLaTableUneFoisLaPartieLancee() {
        val t = table("Ben")
        t.host.online.startRound()

        val late = t.join("Cléo")

        assertEquals(listOf("Anna", "Ben"), t.host.online.lobby)
        assertFalse(t.host.online.acceptsPlayers)
        assertFalse(late.online.active)
        assertEquals(2, t.host.online.match.players)
    }

    @Test
    fun onNeRejointPlusUneTablePleine() {
        val t = table(*(1 until Reflex.MAX_PLAYERS).map { "J$it" }.toTypedArray())
        assertEquals(Reflex.MAX_PLAYERS, t.host.online.lobby.size)
        assertFalse(t.host.online.acceptsPlayers)

        val extra = t.join("Trop")

        assertEquals(Reflex.MAX_PLAYERS, t.host.online.lobby.size)
        assertFalse(extra.online.active)
        assertEquals(Reflex.MAX_PLAYERS, t.guests.last { it !== extra }.online.lobby.size)
    }

    // ---------- Une manche ----------

    @Test
    fun lOrganisateurLanceLaMancheEtTousRecoiventLeMemeDelai() {
        val t = table("Ben", "Cléo")

        t.host.online.startRound()

        for (phone in t.phones) {
            assertTrue(phone.online.running)
            assertEquals(1, phone.online.round)
            assertEquals(t.host.online.delayMs, phone.online.delayMs)
            assertFalse(phone.online.reported)
        }
        assertTrue(t.host.online.delayMs in Reflex.MIN_DELAY_MS..Reflex.MAX_DELAY_MS)
    }

    @Test
    fun unInvitePeutLancerLesManchesSuivantes() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        t.report(300, 200)

        guest.online.startRound()

        assertTrue(host.online.running)
        assertTrue(guest.online.running)
        assertEquals(2, guest.online.round)
    }

    @Test
    fun lePlusRapideGagneEtTousVoientLeMemeResultat() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.startRound()

        host.online.report(310)
        ben.online.report(240)
        // Tant que tout le monde n'a pas répondu, la manche continue.
        assertTrue(host.online.running)
        assertTrue(ben.online.running)
        assertTrue(host.online.reported)
        assertTrue(ben.online.reported)
        assertFalse(cleo.online.reported)

        cleo.online.report(275)

        for (phone in t.phones) {
            assertFalse(phone.online.running)
            assertEquals(1, phone.online.match.lastWinner)
            assertEquals(240, phone.online.match.lastReactionMs)
            assertEquals(listOf(310, 240, 275), phone.online.match.lastResults)
            assertEquals(listOf(0, 1, 0), phone.online.match.wins)
        }
    }

    @Test
    fun unInviteQuiRepondEnPremierEstAttendu() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()

        guest.online.report(200)
        assertTrue(guest.online.reported)
        assertTrue(host.online.running)

        host.online.report(250)
        assertFalse(guest.online.running)
        assertEquals(1, guest.online.match.lastWinner)
    }

    @Test
    fun unFauxDepartNePeutPasGagner() {
        val t = table("Ben", "Cléo")
        t.host.online.startRound()

        t.report(Reflex.EARLY, 500, 400)

        for (phone in t.phones) {
            assertEquals(2, phone.online.match.lastWinner)
            assertEquals(listOf(0), phone.online.match.falseStarters)
        }
    }

    @Test
    fun uneMancheNulleSeRejoueSansPoint() {
        val t = table("Ben", "Cléo")
        val (host, ben, _) = t.phones
        host.online.startRound()
        t.report(Reflex.MISS, Reflex.MISS, Reflex.MISS)

        assertFalse(host.online.running)
        assertTrue(ben.online.match.lastDraw)
        assertEquals(listOf(0, 0, 0), ben.online.match.wins)

        // On peut relancer : c'est une nouvelle manche, lancée par un invité.
        ben.online.startRound()
        assertEquals(2, host.online.round)
        assertTrue(host.online.running)
        assertFalse(ben.online.reported)
    }

    @Test
    fun unSeulResultatParManche() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()

        host.online.report(400)
        host.online.report(100)
        guest.online.report(300)
        guest.online.report(50)

        assertEquals(1, host.online.match.lastWinner)
        assertEquals(300, host.online.match.lastReactionMs)
    }

    @Test
    fun onNeDonnePasDeResultatHorsManche() {
        val t = table("Ben")
        val (host, guest) = t.phones

        host.online.report(100)
        guest.online.report(100)

        assertEquals(ReflexGame.of(2), host.online.match)
        assertFalse(host.online.reported)
        assertFalse(guest.online.reported)
    }

    @Test
    fun unResultatDUneAncienneMancheEstIgnore() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        t.report(300, 200)

        // Un message en retard de la manche 1 arrive pendant la manche 2.
        host.online.startRound()
        guest.session.sendToHost(SyncMessage.Reflex(ReflexMessage.Result(1, 10)))
        host.online.report(280)

        assertTrue(host.online.running)
        guest.online.report(250)
        assertEquals(2, host.online.round)
        assertEquals(250, host.online.match.lastReactionMs)
    }

    @Test
    fun unResultatVenuDUnInconnuEstIgnore() {
        val t = table("Ben")
        t.host.online.startRound()

        t.host.online.onMessage("inconnu", ReflexMessage.Result(1, 10))
        t.host.online.report(300)

        assertTrue(t.host.online.running)
    }

    @Test
    fun onNeLanceNiNeRelanceUneMancheEnCours() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        val delay = host.online.delayMs

        host.online.startRound()
        guest.online.startRound()

        assertEquals(1, host.online.round)
        assertEquals(delay, host.online.delayMs)
    }

    // ---------- La partie ----------

    @Test
    fun lePremierAuxManchesCiblesGagneLaPartieEtOnPeutRecommencer() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones

        repeat(Reflex.TARGET) {
            host.online.startRound()
            t.report(200, 300, 250)
        }

        for (phone in t.phones) {
            assertTrue(phone.online.match.isOver)
            assertEquals(0, phone.online.match.winner)
        }
        // Les moyennes sont connues de tous.
        assertEquals(300, ben.online.match.averageMsOf(1))
        assertEquals(250, cleo.online.match.averageMsOf(2))

        // Plus de manche une fois la partie finie.
        ben.online.startRound()
        host.online.startRound()
        assertFalse(host.online.running)

        // N'importe quel joueur peut lancer une nouvelle partie, avec les mêmes joueurs et les mêmes places.
        cleo.online.newMatch()
        for (phone in t.phones) assertEquals(ReflexGame.of(3), phone.online.match)
        assertEquals(listOf(0, 1, 2), t.phones.map { it.online.seat })

        // La table reste fermée aux nouveaux arrivants, mais les manches reprennent par n'importe qui.
        val late = t.join("Dora")
        assertFalse(late.online.active)
        cleo.online.startRound()
        assertTrue(host.online.running)
    }

    @Test
    fun onNeRelancePasUnePartieEnCours() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        t.report(200, 300)

        guest.online.newMatch()
        host.online.newMatch()

        assertEquals(1, host.online.match.winsOf(0))
    }

    // ---------- Départs ----------

    @Test
    fun unInvitePartiEnCoursDeMancheAnnuleLaMancheEtLesAutresContinuent() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.startRound()
        t.report(200, 300, 250)
        host.online.startRound()
        host.online.report(210)

        ben.online.leave()

        assertFalse(ben.online.active)
        for (phone in listOf(host, cleo)) {
            assertEquals(listOf("Anna", "Cléo"), phone.online.lobby)
            assertFalse(phone.online.running)
            assertEquals(ReflexNotice.PLAYER_LEFT, phone.online.notice)
            assertTrue(phone.online.ready)
            // Le score de ceux qui restent est gardé.
            assertEquals(listOf(1, 0), phone.online.match.wins)
        }
        // Cléo passe à la place 1.
        assertEquals(1, cleo.online.seat)

        // La partie reprend entre les deux joueurs restants.
        cleo.online.startRound()
        assertTrue(host.online.running)
        assertEquals(ReflexNotice.NONE, host.online.notice)
        host.online.report(300)
        cleo.online.report(220)
        assertEquals(1, host.online.match.lastWinner)
        assertEquals(listOf(1, 1), host.online.match.wins)
    }

    @Test
    fun unInviteQuiPartAvantLeDebutLaisseLaTableOuverte() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones

        ben.online.leave()

        assertEquals(listOf("Anna", "Cléo"), host.online.lobby)
        assertEquals(listOf("Anna", "Cléo"), cleo.online.lobby)
        assertTrue(host.online.gathering)
        assertEquals(ReflexGame.of(2), host.online.match)
        // Un autre téléphone peut encore arriver.
        val dora = t.join("Dora")
        assertEquals(listOf("Anna", "Cléo", "Dora"), host.online.lobby)
        assertEquals(2, dora.online.seat)
    }

    @Test
    fun lInviteQuiPartRemetLaTableAZeroQuandL_organisateurResteSeul() {
        val t = table("Ben")
        val (host, guest) = t.phones
        host.online.startRound()
        t.report(200, 300)
        host.online.startRound()

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertEquals(ReflexGame.of(1), host.online.match)
        assertFalse(host.online.running)
        assertEquals(0, host.online.round)
        assertEquals(ReflexNotice.PLAYER_LEFT, host.online.notice)
        assertFalse(host.online.ready)

        // La table est de nouveau ouverte : un autre téléphone peut la rejoindre.
        assertTrue(host.online.acceptsPlayers)
        val next = t.join("Cléo")
        assertTrue(next.online.ready)
        assertEquals(listOf("Anna", "Cléo"), next.online.lobby)
        assertEquals(ReflexGame.of(2), host.online.match)
    }

    @Test
    fun siL_organisateurPartLesInvitesEnSontInformes() {
        val t = table("Ben", "Cléo")
        val (host, ben, cleo) = t.phones
        host.online.startRound()

        host.online.leave()

        for (phone in listOf(ben, cleo)) {
            assertEquals(ReflexNotice.HOST_LEFT, phone.online.notice)
            assertFalse(phone.online.running)
        }
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesSontCodesPuisDecodesSansPerte() {
        val played = ReflexGame(
            wins = listOf(2, 1, 0),
            totalMs = listOf(560, 790, 300),
            timedRounds = listOf(3, 3, 1),
            lastResults = listOf(187, 264, Reflex.EARLY),
            lastWinner = 0
        )
        val messages = listOf(
            ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexGame.of(2), ReflexNotice.NONE, 0, 0L, false),
            ReflexMessage.Table(listOf("Anna", "Ben", "Cléo"), 2, played, ReflexNotice.NONE, 4, 2_345L, true),
            ReflexMessage.Table(
                listOf("Anna", "Ben"), 1,
                ReflexGame(listOf(1, 1), listOf(0, 0), listOf(0, 0), listOf(Reflex.MISS, Reflex.MISS), ReflexGame.NONE, true),
                ReflexNotice.PLAYER_LEFT, 3, 3_000L, false
            ),
            ReflexMessage.Table(listOf("Anna"), 0, ReflexGame.of(1), ReflexNotice.PLAYER_LEFT, 0, 0L, false),
            ReflexMessage.Result(3, 245),
            ReflexMessage.Result(3, Reflex.EARLY),
            ReflexMessage.Result(3, Reflex.MISS),
            ReflexMessage.Start,
            ReflexMessage.NewMatch
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Reflex(message)))
            assertEquals(message.toString(), SyncMessage.Reflex(message), decoded)
        }
    }

    @Test
    fun uneTableAHuitJoueursTientDansUnMessage() {
        val names = (1..Reflex.MAX_PLAYERS).map { "Joueuse numéro $it" }
        val full = ReflexGame(
            wins = List(8) { 2 }, totalMs = List(8) { 9_999 * 10 }, timedRounds = List(8) { 10 },
            lastResults = List(8) { 9_999 }, lastWinner = 7
        )
        val message = SyncMessage.Reflex(ReflexMessage.Table(names, 7, full, ReflexNotice.NONE, 99, 5_000L, true))
        assertEquals(message, SyncCodec.decode(SyncCodec.encode(message)))
    }

    @Test
    fun unMessageAbsurdeEstRamenePuisUnInconnuEstRefuse() {
        val json = ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexGame.of(2), ReflexNotice.NONE, 1, 2_000L, true).toJson()
        json.put("w", org.json.JSONArray(listOf(99, -3)))
            .put("last", 9).put("seat", 12)
            .put("res", org.json.JSONArray(listOf(123_456, -50)))
            .put("delay", 1_000_000L).put("round", -5)

        val decoded = reflexMessageFromJson(json) as ReflexMessage.Table
        assertEquals(listOf(Reflex.TARGET, 0), decoded.match.wins)
        assertEquals(1, decoded.match.lastWinner)
        assertEquals(1, decoded.seat)
        assertEquals(listOf(9_999, Reflex.MISS), decoded.match.lastResults)
        assertEquals(Reflex.MAX_DELAY_MS, decoded.delayMs)
        assertEquals(0, decoded.round)

        assertNotNull(reflexMessageFromJson(ReflexMessage.Start.toJson()))
        assertNull(reflexMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
    }

    @Test
    fun desTotauxAbsurdesOuAbsentsSontRamenes() {
        val json = ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexGame.of(2), ReflexNotice.NONE, 1, 2_000L, true).toJson()
        json.put("t", org.json.JSONArray(listOf(-5, Int.MAX_VALUE))).put("c", org.json.JSONArray(listOf(2_000_000_000, 1)))
        val clamped = (reflexMessageFromJson(json) as ReflexMessage.Table).match
        assertEquals(0, clamped.totalMs[0])
        assertTrue(clamped.totalMs[1] <= 10_000 * ReflexGame.MAX_REACTION_MS)
        assertTrue(clamped.timedRounds[0] <= 10_000)

        // Un tableau trop court est complété par des zéros ; sans résultats de manche, on repart d'une manche vierge.
        json.remove("t")
        json.remove("res")
        val partial = (reflexMessageFromJson(json) as ReflexMessage.Table).match
        assertEquals(listOf(0, 0), partial.totalMs)
        assertEquals(emptyList<Int>(), partial.lastResults)
    }

    @Test
    fun uneTableDeTropDeJoueursEstTronquee() {
        val names = (1..12).map { "J$it" }
        val json = ReflexMessage.Table(names.take(2), 0, ReflexGame.of(2), ReflexNotice.NONE, 0, 0L, false).toJson()
        json.put("names", org.json.JSONArray(names))
        val decoded = reflexMessageFromJson(json) as ReflexMessage.Table
        assertEquals(Reflex.MAX_PLAYERS, decoded.names.size)
        assertEquals(Reflex.MAX_PLAYERS, decoded.match.players)
    }
}
