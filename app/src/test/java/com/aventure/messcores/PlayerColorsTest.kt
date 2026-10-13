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

    @Test
    fun lesCouleursChoisiesSontConservees() {
        val chosen = listOf(PLAYER_COLOR_PALETTE[3], null, PLAYER_COLOR_PALETTE[7])
        val colors = assignPlayerColors(3, chosen)
        assertEquals(PLAYER_COLOR_PALETTE[3], colors[0])
        assertEquals(PLAYER_COLOR_PALETTE[7], colors[2])
    }

    @Test
    fun lesJoueursSansChoixRecoiventUneCouleurLibreAuHasard() {
        // Sur 200 tirages : jamais la couleur choisie par le joueur 0, jamais de doublon.
        repeat(200) {
            val colors = assignPlayerColors(12, listOf(PLAYER_COLOR_PALETTE[0], PLAYER_COLOR_PALETTE[1]))
            assertEquals(12, colors.toSet().size)
            assertEquals(PLAYER_COLOR_PALETTE[0], colors[0])
            assertEquals(PLAYER_COLOR_PALETTE[1], colors[1])
        }
    }

    @Test
    fun sansAucunChoixToutEstAleatoireEtDistinct() {
        assertEquals(5, assignPlayerColors(5, listOf(null, null, null, null, null)).toSet().size)
    }

    @Test
    fun deuxJoueursNeGardentJamaisLaMemeCouleurChoisie() {
        val same = PLAYER_COLOR_PALETTE[2]
        val colors = assignPlayerColors(3, listOf(same, same, null))
        assertEquals(3, colors.toSet().size)
        assertEquals(same, colors[0])
    }

    @Test
    fun laPartieDemarreAvecLesCouleursChoisies() {
        val chosen = listOf(PLAYER_COLOR_PALETTE[5], null)
        val vm = ScoreViewModel().apply {
            initGame(listOf("A", "B"), GameRules(id = "test", name = "Test"), chosen)
        }
        assertEquals(PLAYER_COLOR_PALETTE[5], vm.playerColors[0])
        assertTrue(vm.playerColors[1] != vm.playerColors[0])
    }

    @Test
    fun chaqueCouleurDeLaPaletteALeSienCodeDistinct() {
        val codes = PLAYER_COLOR_PALETTE.map { colorCode(it) }
        assertEquals("A", codes.first())
        assertTrue(codes.all { it != null })
        assertEquals(PLAYER_COLOR_PALETTE.size, codes.toSet().size)
    }

    @Test
    fun uneCouleurHorsPalettePorteAucunCode() {
        assertEquals(null, colorCode(Color(0xFF123456)))
    }
}
