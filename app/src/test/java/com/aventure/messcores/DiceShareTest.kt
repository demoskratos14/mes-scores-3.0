package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le tour de dés partagé entre deux téléphones, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur joue « Anna » (joueur 0), l'invité « Ben » (joueur 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DiceShareTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
    }

    private fun phones(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.game.initGame(listOf("Anna", "Ben"), GameRules(id = "t", name = "T"))
        return host to guest
    }

    private fun connect(host: Phone, guest: Phone) {
        assertTrue(host.session.startHosting("Téléphone d'Anna"))
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Téléphone de Ben")
        host.session.accept()
    }

    private val table = DiceTable(player = 1, turn = listOf(2, 5, 3, 1, 4, 6, 2, 3), pigTotal = 0, pigBust = false)

    @Test
    fun leTourPublieParLOrganisateurArriveChezLInvite() {
        val (host, guest) = phones()
        connect(host, guest)

        host.session.publishDice(table)

        assertEquals(table, host.session.diceTable)
        assertEquals(table, guest.session.diceTable)
    }

    @Test
    fun leTourPublieParUnInviteArriveChezLOrganisateur() {
        val (host, guest) = phones()
        connect(host, guest)

        val cochon = DiceTable(player = 1, turn = listOf(1, 1, 4), pigTotal = 0, pigBust = true)
        guest.session.publishDice(cochon)

        assertEquals(cochon, host.session.diceTable)
        assertEquals(cochon, guest.session.diceTable)
    }

    @Test
    fun leDernierTourPublieRemplaceLePrecedent() {
        val (host, guest) = phones()
        connect(host, guest)

        host.session.publishDice(DiceTable(0, emptyList(), 0, false))
        guest.session.publishDice(table)

        assertEquals(table, host.session.diceTable)
        assertEquals(table, guest.session.diceTable)
    }

    @Test
    fun sansConnexionRienNEstPartage() {
        val (host, _) = phones()

        host.session.publishDice(table)

        assertNull(host.session.diceTable)
    }

    @Test
    fun onChoisitLeJoueurDeCeTelephoneEtOnLOublieALaDeconnexion() {
        val (host, guest) = phones()
        connect(host, guest)
        assertEquals(DICE_UNSET, guest.session.dicePlayer)

        guest.session.chooseDicePlayer(1)
        host.session.chooseDicePlayer(DICE_ALL)
        assertEquals(1, guest.session.dicePlayer)
        assertEquals(DICE_ALL, host.session.dicePlayer)

        host.session.publishDice(table)
        guest.session.disconnect()

        assertEquals(DICE_UNSET, guest.session.dicePlayer)
        assertNull(guest.session.diceTable)
    }

    @Test
    fun lesMessagesDeDesSurviventALEnvoi() {
        val messages = listOf(
            SyncMessage.Dice(table),
            SyncMessage.Dice(DiceTable(0, emptyList(), 0, false)),
            SyncMessage.Dice(DiceTable(2, listOf(1, 1, 4), 12, true))
        )
        for (message in messages) {
            assertEquals(message.toString(), message, SyncCodec.decode(SyncCodec.encode(message)))
        }
    }

    @Test
    fun leTourDeDesPartageSeRejoueSurLeTelephoneQuiLeRecoit() {
        val (host, guest) = phones()
        connect(host, guest)
        val setup = DiceSetup(diceCount = 5, maxRolls = 3)
        val activeTurn = DiceTurn(setup, rollDie = sequenceOf(3, 3, 6, 1, 2).iterator()::next)

        activeTurn.roll()
        host.session.publishDice(DiceTable(0, activeTurn.snapshot(), 0, false))

        // Le téléphone de Ben reprend exactement les dés d'Anna.
        val mirror = DiceTurn(setup)
        mirror.restoreFrom(guest.session.diceTable!!.turn)
        assertEquals(listOf(3, 3, 6, 1, 2), mirror.values)
        assertEquals(activeTurn.rollsUsed, mirror.rollsUsed)
    }
}
