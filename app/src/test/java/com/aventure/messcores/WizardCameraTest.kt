package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La caméra d'une prise (style sorcier) : pas de caméra pour un déplacement, à plat au début et à la fin, inclinée et rapprochée au milieu. */
class WizardCameraTest {

    // Un cavalier blanc (case 62, g1) prend un pion noir sur la case 45 (f3).
    private val capture = MoveFx(from = 62, to = 45, mover = 'N', captured = 'p', capturedSquare = 45)
    private val quiet = MoveFx(from = 52, to = 36, mover = 'P', captured = '.', capturedSquare = 36)

    @Test
    fun aSimpleMoveHasNoCamera() {
        assertNull(wizardCamera(quiet, 0.5f, flipped = false))
    }

    @Test
    fun theBoardIsFlatAtTheStartAndAtTheEnd() {
        for (progress in listOf(0f, 1f)) {
            val camera = wizardCamera(capture, progress, flipped = false)
            assertNotNull(camera)
            assertEquals(0f, camera!!.tiltDeg, 0.001f)
            assertEquals(0f, camera.orbitDeg, 0.001f)
            assertEquals(1f, camera.zoom, 0.001f)
        }
    }

    @Test
    fun theCameraIsTiltedAndZoomedBeforeTheImpact() {
        val camera = wizardCamera(capture, 0.4f, flipped = false)!!
        assertTrue(camera.tiltDeg > 0f)
        assertTrue(camera.zoom > 1f)
    }

    @Test
    fun theImpactShakesTheBoard() {
        val camera = wizardCamera(capture, 0.50f, flipped = false)!!
        assertTrue(camera.shakeX != 0f || camera.shakeY != 0f)
        val calm = wizardCamera(capture, 0.60f, flipped = false)!!
        assertEquals(0f, calm.shakeX, 0.0001f)
        assertEquals(0f, calm.shakeY, 0.0001f)
    }

    @Test
    fun theCameraZoomsVeryCloseOnAMeleeAttack() {
        // Pendant le geste, le zoom est bien plus fort qu'avant (1,45) : la zone vue ne fait guère plus de 3 cases de large.
        val camera = wizardCamera(capture, 0.40f, flipped = false)!!
        assertTrue(camera.zoom >= 2.5f)
    }

    @Test
    fun theCameraFocusesOnTheVictimSquare() {
        // Case 45 : rangée 5, colonne 5 ; la victime est dans la zone vue, plateau normal ou retourné.
        val camera = wizardCamera(capture, 0.4f, flipped = false)!!
        assertEquals(5.5f, camera.viewX * 8f, 4f / camera.zoom)
        assertEquals(5.5f, camera.viewY * 8f, 4f / camera.zoom)
        val flipped = wizardCamera(capture, 0.4f, flipped = true)!!
        assertEquals(2.5f, flipped.viewX * 8f, 4f / flipped.zoom)
        assertEquals(2.5f, flipped.viewY * 8f, 4f / flipped.zoom)
    }

    @Test
    fun theViewNeverLeavesTheBoard() {
        for (step in 0..100) {
            val camera = wizardCamera(capture, step / 100f, flipped = false)!!
            val half = 0.5f / camera.zoom
            assertTrue(camera.viewX >= half - 0.0001f && camera.viewX <= 1f - half + 0.0001f)
            assertTrue(camera.viewY >= half - 0.0001f && camera.viewY <= 1f - half + 0.0001f)
        }
    }

    // Une tour blanche en a1 (case 56) prend une tour noire en a8 (case 0) : elle tire de loin.
    private val rookShot = MoveFx(from = 56, to = 0, mover = 'R', captured = 'r', capturedSquare = 0)

    @Test
    fun theCameraGoesToTheShooterThenFollowsTheProjectile() {
        // D'abord tout près de la tour qui tire (en bas du plateau)…
        val onShooter = wizardCamera(rookShot, 0.30f, flipped = false)!!
        assertTrue(onShooter.zoom >= 2.5f)
        assertTrue(onShooter.viewY > 0.7f)
        // … puis elle accompagne le projectile, qui monte vers la victime…
        val inFlight = wizardCamera(rookShot, 0.41f, flipped = false)!!
        assertTrue(inFlight.viewY < onShooter.viewY)
        // … et arrive sur la victime (en haut du plateau) à l'impact.
        val onVictim = wizardCamera(rookShot, 0.47f, flipped = false)!!
        assertTrue(onVictim.zoom >= 2.5f)
        assertTrue(onVictim.viewY < 0.3f)
    }

    @Test
    fun anAttackFromAfarTakesLongerThanAMeleeOne() {
        assertTrue(wizardMoveDuration(rookShot) > wizardMoveDuration(capture))
    }
}
