package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class WerewolfProtocolTest {

    @Test
    fun laTableVoyageSansRienPerdre() {
        val round = WerewolfRound(List(8) { "J${it + 1}" }, Random(11))
        for (seat in 0 until 8) round.perform(seat, WerewolfAction(WerewolfActionKind.ACK))
        round.advance()
        round.advance()
        for (seat in 0 until 8) {
            val message = WerewolfMessage.Table(round.names, seat, WerewolfNotice.NONE, round.viewFor(seat))
            val back = werewolfMessageFromJson(message.toJson())
            assertEquals(message, back)
        }
    }

    @Test
    fun laTableDAttenteNAPasDeVue() {
        val message = WerewolfMessage.Table(listOf("Anna", "Ben"), 1, WerewolfNotice.NONE, null)
        val back = werewolfMessageFromJson(message.toJson()) as WerewolfMessage.Table
        assertNull(back.view)
        assertEquals(listOf("Anna", "Ben"), back.names)
        assertEquals(1, back.seat)
    }

    @Test
    fun uneActionVoyageSansRienPerdre() {
        val message = WerewolfMessage.Act(WerewolfAction(WerewolfActionKind.WITCH, 1, 4))
        assertEquals(message, werewolfMessageFromJson(message.toJson()))
    }
}
