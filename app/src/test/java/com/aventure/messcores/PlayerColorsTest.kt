package com.aventure.messcores

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerColorsTest {

    private fun viewModelWith(count: Int) = ScoreViewModel().apply {
        initGame(List(count) { "J${it + 1}" }, GameRules(id = "test", name = "Test"))
    }

    @Test
    fun laPaletteDepasseLeNombreMaximalDeJoueurs() {
        assertTrue(PLAYER_COLOR_PALETTE.size > GameRules.DEFAULT_MAX_PLAYERS)
        assertEquals(PLAYER_COLOR_PALETTE.size, PLAYER_COLOR_PALETTE.toSet().size)
    }

    @Test
    fun douzeJoueursOntDouzeCouleursDistinctes() {
        assertEquals(12, assignPlayerColors(12).toSet().size)
    }

    @Test
    fun uneCouleurLibreEstAcceptee() {
        val vm = viewModelWith(3)
        val free = PLAYER_COLOR_PALETTE.first { it !in vm.playerColors }
        assertTrue(vm.setPlayerColor(1, free))
        assertEquals(free, vm.playerColors[1])
    }

    @Test
    fun uneCouleurDejaPriseParUnAutreJoueurEstRefusee() {
        val vm = viewModelWith(3)
        val before = vm.playerColors
        assertFalse(vm.setPlayerColor(0, before[2]))
        assertEquals(before, vm.playerColors)
    }

    @Test
    fun reprendreSaPropreCouleurNEstPasUnConflit() {
        val vm = viewModelWith(3)
        assertTrue(vm.setPlayerColor(1, vm.playerColors[1]))
    }

    @Test
    fun unIndexInconnuEstRefuse() {
        val vm = viewModelWith(2)
        assertFalse(vm.setPlayerColor(5, Color.Red))
    }

    @Test
    fun laCouleurChoisieEstEnregistreeDansLaPartie() {
        val vm = viewModelWith(2)
        val free = PLAYER_COLOR_PALETTE.first { it !in vm.playerColors }
        vm.setPlayerColor(0, free)
        assertEquals(free, Color(vm.snapshot().playerColorsArgb[0]))
    }
}
